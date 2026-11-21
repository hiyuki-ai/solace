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

    suspend fun get(workId: String): WorkWithMedia? = dao.getById(workId)

    suspend fun save(
        workId: String,
        caption: String,
        status: String,
        media: List<WorkMediaEntity>,
        coverFile: String?,
        commitStaging: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            if (commitStaging) {
                storage.commitStaging(workId)
            }
            val now = System.currentTimeMillis()
            val existing = dao.getById(workId)?.work
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
