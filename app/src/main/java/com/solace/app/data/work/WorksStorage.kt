package com.solace.app.data.work

import android.content.Context
import java.io.File

/**
 * 作品文件布局：
 * filesDir/works/<workId>/cover.jpg
 * filesDir/works/<workId>/media/000.mp4
 * filesDir/staging/<workId>/        创作中的临时目录，保存时原子重命名
 */
class WorksStorage(context: Context) {

    private val appContext = context.applicationContext

    val worksRoot: File
        get() = File(appContext.filesDir, "works").apply { mkdirs() }

    private val stagingRoot: File
        get() = File(appContext.filesDir, "staging").apply { mkdirs() }

    fun workDir(workId: String): File = File(worksRoot, workId)

    fun stagingDir(workId: String): File = File(stagingRoot, workId).apply { mkdirs() }

    fun mediaDir(base: File): File = File(base, "media").apply { mkdirs() }

    fun commitStaging(workId: String) {
        val from = File(stagingRoot, workId)
        if (!from.exists()) return
        val to = workDir(workId)
        if (to.exists()) to.deleteRecursively()
        to.parentFile?.mkdirs()
        if (!from.renameTo(to)) {
            from.copyRecursively(to, overwrite = true)
            from.deleteRecursively()
        }
    }

    fun deleteWork(workId: String) {
        workDir(workId).deleteRecursively()
        File(stagingRoot, workId).deleteRecursively()
    }

    fun cleanupStaging(maxAgeMs: Long = DEFAULT_MAX_AGE_MS) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        stagingRoot.listFiles()
            ?.filter { it.lastModified() < cutoff }
            ?.forEach { it.deleteRecursively() }
    }

    fun cleanupOrphans(validIds: Set<String>, maxAgeMs: Long = DEFAULT_MAX_AGE_MS) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        worksRoot.listFiles()
            ?.filter { it.name !in validIds && it.lastModified() < cutoff }
            ?.forEach { it.deleteRecursively() }
    }

    companion object {
        private const val DEFAULT_MAX_AGE_MS = 24 * 60 * 60 * 1000L
    }
}
