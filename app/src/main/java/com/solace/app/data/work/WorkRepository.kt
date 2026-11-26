package com.solace.app.data.work

import android.content.Context
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
                ),
                media,
                origin ?: current?.origin,
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
}
