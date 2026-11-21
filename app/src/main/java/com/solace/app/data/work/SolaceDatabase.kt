package com.solace.app.data.work

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WorkEntity::class, WorkMediaEntity::class, WorkOriginEntity::class],
    version = 3,
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

        @Volatile
        private var instance: SolaceDatabase? = null

        fun get(context: Context): SolaceDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SolaceDatabase::class.java,
                    NAME,
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
