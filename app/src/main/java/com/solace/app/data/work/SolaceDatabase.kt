package com.solace.app.data.work

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WorkEntity::class,
        WorkMediaEntity::class,
        WorkOriginEntity::class,
        UserEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class SolaceDatabase : RoomDatabase() {

    abstract fun workDao(): WorkDao

    companion object {
        private const val NAME = "solace.db"

        /** v1 → v2：删除已不再使用的 works.type 分类字段，保留全部作品和素材。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `works` RENAME TO `works_old`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `works` (" +
                        "`id` TEXT NOT NULL, " +
                        "`caption` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`coverFile` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`publishedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "INSERT INTO `works` (`id`, `caption`, `status`, `coverFile`, " +
                        "`createdAt`, `updatedAt`, `publishedAt`) " +
                        "SELECT `id`, `caption`, `status`, `coverFile`, " +
                        "`createdAt`, `updatedAt`, `publishedAt` FROM `works_old`",
                )

                // 外键指向 works，重建子表前先拷贝，再删旧表，避免删父表时级联清空。
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `work_media_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`workId` TEXT NOT NULL, " +
                        "`orderIndex` INTEGER NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`fileName` TEXT NOT NULL, " +
                        "`width` INTEGER, " +
                        "`height` INTEGER, " +
                        "`durationMs` INTEGER, " +
                        "`sizeBytes` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`workId`) REFERENCES `works`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "INSERT INTO `work_media_new` (`id`, `workId`, `orderIndex`, `kind`, " +
                        "`fileName`, `width`, `height`, `durationMs`, `sizeBytes`) " +
                        "SELECT `id`, `workId`, `orderIndex`, `kind`, `fileName`, " +
                        "`width`, `height`, `durationMs`, `sizeBytes` FROM `work_media`",
                )
                db.execSQL("DROP TABLE `work_media`")
                db.execSQL("DROP TABLE `works_old`")
                db.execSQL("ALTER TABLE `work_media_new` RENAME TO `work_media`")

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_works_status_updatedAt` " +
                        "ON `works` (`status`, `updatedAt`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_work_media_workId` " +
                        "ON `work_media` (`workId`)",
                )
            }
        }

        /**
         * v2 → v3：新增 work_origins，保存复制作品的来源信息。
         * 本地原创作品没有对应的行，因此不需要回填。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `work_origins` (" +
                        "`workId` TEXT NOT NULL, " +
                        "`platform` TEXT NOT NULL, " +
                        "`authorName` TEXT, " +
                        "`authorId` TEXT, " +
                        "`authorSecUid` TEXT, " +
                        "`awemeId` TEXT, " +
                        "`publishedAt` INTEGER, " +
                        "PRIMARY KEY(`workId`), " +
                        "FOREIGN KEY(`workId`) REFERENCES `works`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
            }
        }

        /**
         * v3 → v4：作者资料从 work_origins 拆到通用 users 表。
         *
         * 旧版本只持久化过 authorSecUid，因此迁移时按 sec_uid 合并历史作者；
         * 没有 sec_uid 的旧作品仍保留作品 ID / 发布时间，但用户关联为空。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `works` ADD COLUMN `userId` INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_works_userId` " +
                        "ON `works` (`userId`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `users` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`uid` TEXT, " +
                        "`secUid` TEXT, " +
                        "`uniqueId` TEXT, " +
                        "`nickname` TEXT, " +
                        "`avatarFile` TEXT, " +
                        "`avatarUrl` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_users_kind_uid` " +
                        "ON `users` (`kind`, `uid`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_users_kind_secUid` " +
                        "ON `users` (`kind`, `secUid`)",
                )

                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO `users` " +
                        "(`kind`, `uid`, `secUid`, `uniqueId`, `nickname`, `avatarFile`, `avatarUrl`, " +
                        "`createdAt`, `updatedAt`) " +
                        "SELECT '${UserKind.DOUYIN}', NULL, `authorSecUid`, MAX(`authorId`), " +
                        "MAX(`authorName`), " +
                        "NULL, NULL, $now, $now " +
                        "FROM `work_origins` " +
                        "WHERE `authorSecUid` IS NOT NULL AND TRIM(`authorSecUid`) <> '' " +
                        "GROUP BY `authorSecUid`",
                )
                db.execSQL(
                    "UPDATE `works` SET `userId` = (" +
                        "SELECT u.`id` FROM `work_origins` o " +
                        "INNER JOIN `users` u ON u.`kind` = '${UserKind.DOUYIN}' " +
                        "AND u.`secUid` = o.`authorSecUid` " +
                        "WHERE o.`workId` = `works`.`id` LIMIT 1)",
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `work_origins_new` (" +
                        "`workId` TEXT NOT NULL, " +
                        "`platform` TEXT NOT NULL, " +
                        "`awemeId` TEXT, " +
                        "`publishedAt` INTEGER, " +
                        "PRIMARY KEY(`workId`), " +
                        "FOREIGN KEY(`workId`) REFERENCES `works`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "INSERT INTO `work_origins_new` " +
                        "(`workId`, `platform`, `awemeId`, `publishedAt`) " +
                        "SELECT o.`workId`, o.`platform`, o.`awemeId`, o.`publishedAt` " +
                        "FROM `work_origins` o",
                )
                db.execSQL("DROP TABLE `work_origins`")
                db.execSQL("ALTER TABLE `work_origins_new` RENAME TO `work_origins`")
            }
        }

        @Volatile
        private var instance: SolaceDatabase? = null

        fun get(context: Context): SolaceDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SolaceDatabase::class.java,
                    NAME,
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
