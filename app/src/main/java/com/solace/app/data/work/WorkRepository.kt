package com.solace.app.data.work

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class WorkRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = SolaceDatabase.get(appContext).workDao()
    val storage = WorksStorage(appContext)

    fun observeDrafts(): Flow<List<WorkWithMedia>> = dao.observeByStatus(WorkStatus.DRAFT)

    fun observePublished(): Flow<List<WorkWithMedia>> = dao.observeByStatus(WorkStatus.PUBLISHED)

    fun observeAll(): Flow<List<WorkWithMedia>> = dao.observeAll()

    fun observeUserWorks(userId: Long): Flow<List<WorkWithMedia>> =
        dao.observeByUser(userId)

    fun observeUser(userId: Long): Flow<UserEntity?> =
        dao.observeUser(userId)

    fun observeUsers(): Flow<List<UserWithWorkCount>> =
        dao.observeUsersWithWorkCount()

    suspend fun createCustomUser(
        nickname: String,
        customId: String,
        avatarUri: Uri?,
    ): UserEntity = withContext(Dispatchers.IO) {
        val name = nickname.trim()
        val identity = customId.trim()
        require(name.isNotEmpty()) { "请填写昵称" }
        require(identity.isNotEmpty()) { "请填写 ID" }
        require(dao.findUserByUniqueId(UserKind.CUSTOM, identity) == null) {
            "该 ID 已被使用"
        }

        val avatarFile = avatarUri?.let(storage::importUserAvatar)
        val now = System.currentTimeMillis()
        val user = UserEntity(
            kind = UserKind.CUSTOM,
            uid = null,
            secUid = null,
            uniqueId = identity,
            nickname = name,
            avatarFile = avatarFile,
            avatarUrl = null,
            createdAt = now,
            updatedAt = now,
        )
        try {
            user.copy(id = dao.insertUser(user))
        } catch (e: Exception) {
            storage.deleteUserAvatar(avatarFile)
            throw e
        }
    }

    suspend fun updateCustomUser(
        userId: Long,
        nickname: String,
        customId: String,
        avatarUri: Uri?,
    ): UserEntity = withContext(Dispatchers.IO) {
        val current = dao.getUser(userId)
            ?: throw IllegalArgumentException("用户不存在")
        require(current.kind == UserKind.CUSTOM) { "抖音用户信息不能在本地修改" }

        val name = nickname.trim()
        val identity = customId.trim()
        require(name.isNotEmpty()) { "请填写昵称" }
        require(identity.isNotEmpty()) { "请填写 ID" }
        val duplicate = dao.findUserByUniqueId(UserKind.CUSTOM, identity)
        require(duplicate == null || duplicate.id == userId) { "该 ID 已被使用" }

        val newAvatarFile = avatarUri?.let(storage::importUserAvatar)
        val updated = current.copy(
            uniqueId = identity,
            nickname = name,
            avatarFile = newAvatarFile ?: current.avatarFile,
            updatedAt = System.currentTimeMillis(),
        )
        try {
            dao.updateUser(updated)
        } catch (e: Exception) {
            storage.deleteUserAvatar(newAvatarFile)
            throw e
        }
        if (newAvatarFile != null && newAvatarFile != current.avatarFile) {
            storage.deleteUserAvatar(current.avatarFile)
        }
        updated
    }

    suspend fun get(workId: String): WorkWithMedia? = dao.getById(workId)

    /**
     * 保存作品。 [origin] 为 null 时沿用已有的来源信息，避免编辑草稿时把它清掉；
     * 本地原创作品本来就没有来源行。
     */
    suspend fun save(
        workId: String,
        caption: String,
        status: String,
        media: List<WorkMediaEntity>,
        coverFile: String?,
        commitStaging: Boolean,
        origin: WorkOriginEntity? = null,
        user: UserEntity? = null,
    ) {
        withContext(Dispatchers.IO) {
            if (commitStaging) {
                storage.commitStaging(workId)
            }
            val now = System.currentTimeMillis()
            val current = dao.getById(workId)
            val existing = current?.work
            dao.save(
                WorkEntity(
                    id = workId,
                    caption = caption,
                    status = status,
                    coverFile = coverFile,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    publishedAt = if (status == WorkStatus.PUBLISHED) {
                        existing?.publishedAt ?: now
                    } else {
                        null
                    },
                    userId = existing?.userId,
                ),
                media,
                origin ?: current?.origin,
                user,
            )
        }
    }

    suspend fun delete(workId: String) {
        withContext(Dispatchers.IO) {
            dao.delete(workId)
            storage.deleteWork(workId)
        }
    }

    fun coverFile(workId: String, relativePath: String?): File? {
        if (relativePath.isNullOrBlank()) return null
        val file = File(storage.workDir(workId), relativePath)
        return file.takeIf { it.exists() }
    }

    fun mediaFile(workId: String, relativePath: String): File =
        File(storage.workDir(workId), relativePath)

    fun userAvatarFile(relativePath: String?): File? {
        if (relativePath.isNullOrBlank()) return null
        return File(appContext.filesDir, relativePath).takeIf { it.isFile }
    }
}
