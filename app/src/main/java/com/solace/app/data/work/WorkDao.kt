package com.solace.app.data.work

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WorkDao {

    @Transaction
    @Query("SELECT * FROM works WHERE status = :status ORDER BY updatedAt DESC")
    abstract fun observeByStatus(status: String): Flow<List<WorkWithMedia>>

    @Transaction
    @Query("SELECT * FROM works ORDER BY updatedAt DESC")
    abstract fun observeAll(): Flow<List<WorkWithMedia>>

    /** 为后续作者主页准备：按通用用户 ID 查询其作品。 */
    @Transaction
    @Query(
        "SELECT works.* FROM works " +
            "LEFT JOIN work_origins ON work_origins.workId = works.id " +
            "WHERE works.userId = :userId " +
            "ORDER BY COALESCE(" +
            "work_origins.publishedAt, works.publishedAt, works.createdAt" +
            ") DESC, works.updatedAt DESC",
    )
    abstract fun observeByUser(userId: Long): Flow<List<WorkWithMedia>>

    @Query(
        "SELECT users.*, COUNT(works.id) AS workCount " +
            "FROM users LEFT JOIN works ON works.userId = users.id " +
            "GROUP BY users.id " +
            "ORDER BY users.updatedAt DESC",
    )
    abstract fun observeUsersWithWorkCount(): Flow<List<UserWithWorkCount>>

    @Transaction
    @Query("SELECT * FROM works WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: String): WorkWithMedia?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertWork(work: WorkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMedia(items: List<WorkMediaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertOrigin(origin: WorkOriginEntity)

    @Insert
    abstract suspend fun insertUser(user: UserEntity): Long

    @Update
    abstract suspend fun updateUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    abstract suspend fun getUser(userId: Long): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    abstract fun observeUser(userId: Long): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE kind = :kind AND uid = :uid LIMIT 1")
    abstract suspend fun findUserByUid(kind: String, uid: String): UserEntity?

    @Query("SELECT * FROM users WHERE kind = :kind AND secUid = :secUid LIMIT 1")
    abstract suspend fun findUserBySecUid(kind: String, secUid: String): UserEntity?

    @Query("SELECT * FROM users WHERE kind = :kind AND uniqueId = :uniqueId LIMIT 1")
    abstract suspend fun findUserByUniqueId(kind: String, uniqueId: String): UserEntity?

    @Query("DELETE FROM work_media WHERE workId = :workId")
    abstract suspend fun deleteMediaFor(workId: String)

    @Query("DELETE FROM work_origins WHERE workId = :workId")
    abstract suspend fun deleteOrigin(workId: String)

    @Query("DELETE FROM works WHERE id = :workId")
    abstract suspend fun deleteWork(workId: String)

    @Transaction
    open suspend fun save(
        work: WorkEntity,
        media: List<WorkMediaEntity>,
        origin: WorkOriginEntity?,
        user: UserEntity?,
    ) {
        val savedUser = user?.let { upsertUser(it) }
        insertWork(work.copy(userId = savedUser?.id ?: work.userId))
        deleteMediaFor(work.id)
        insertMedia(media)
        deleteOrigin(work.id)
        if (origin != null) insertOrigin(origin)
    }

    @Transaction
    open suspend fun delete(workId: String) {
        deleteWork(workId)
    }

    /**
     * uid 优先：有 uid 时先按 uid 找人；历史上只存过 sec_uid 的记录再按 sec_uid 兜底。
     * 两个抖音 ID 都缺失时无法跨作品去重，但仍可保留一份用户资料。
     */
    private suspend fun upsertUser(incoming: UserEntity): UserEntity {
        val existing = incoming.uid?.let { findUserByUid(incoming.kind, it) }
            ?: incoming.secUid?.let { findUserBySecUid(incoming.kind, it) }
        val now = System.currentTimeMillis()
        if (existing == null) {
            val id = insertUser(incoming.copy(createdAt = now, updatedAt = now))
            return incoming.copy(id = id, createdAt = now, updatedAt = now)
        }

        val updated = existing.copy(
            uid = incoming.uid ?: existing.uid,
            secUid = incoming.secUid ?: existing.secUid,
            uniqueId = incoming.uniqueId ?: existing.uniqueId,
            nickname = incoming.nickname ?: existing.nickname,
            avatarFile = incoming.avatarFile ?: existing.avatarFile,
            avatarUrl = incoming.avatarUrl ?: existing.avatarUrl,
            updatedAt = now,
        )
        updateUser(updated)
        return updated
    }
}
