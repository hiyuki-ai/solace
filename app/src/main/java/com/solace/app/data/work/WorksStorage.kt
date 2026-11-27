package com.solace.app.data.work

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/**
 * 作品文件布局：
 * filesDir/works/<workId>/cover.jpg
 * filesDir/works/<workId>/media/000.mp4
 * filesDir/staging/<workId>/        创作中的临时目录，保存时原子重命名
 * filesDir/users/<hash>/            按用户身份复用的头像
 */
class WorksStorage(context: Context) {

    private val appContext = context.applicationContext

    val worksRoot: File
        get() = File(appContext.filesDir, "works").apply { mkdirs() }

    private val stagingRoot: File
        get() = File(appContext.filesDir, "staging").apply { mkdirs() }

    private val usersRoot: File
        get() = File(appContext.filesDir, "users").apply { mkdirs() }

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

    /**
     * 把下载好的头像放进用户级目录，返回相对 filesDir 的路径。
     * 文件名使用 uid/sec_uid 的摘要，避免把抖音 ID 直接暴露在文件路径里。
     */
    fun storeUserAvatar(
        uid: String?,
        secUid: String?,
        source: File,
        extension: String,
    ): String? {
        val identity = uid?.takeIf { it.isNotBlank() }
            ?: secUid?.takeIf { it.isNotBlank() }
            ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        val dir = File(usersRoot, digest).apply { mkdirs() }
        dir.listFiles()
            ?.filter { it.name.startsWith("avatar.") }
            ?.forEach { it.delete() }
        val target = File(dir, "avatar.$extension")
        source.copyTo(target, overwrite = true)
        source.delete()
        return target.relativeTo(appContext.filesDir).invariantSeparatorsPath
    }

    /** 从系统图片选择器导入自定义用户头像，返回相对 filesDir 的路径。 */
    fun importUserAvatar(uri: Uri): String {
        val mimeType = appContext.contentResolver.getType(uri).orEmpty()
        val extension = when {
            mimeType.contains("png") -> "png"
            mimeType.contains("webp") -> "webp"
            mimeType.contains("heic") || mimeType.contains("heif") -> "heic"
            else -> "jpg"
        }
        val dir = File(usersRoot, "custom-${UUID.randomUUID()}").apply { mkdirs() }
        val target = File(dir, "avatar.$extension")
        try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("无法读取头像")
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
        return target.relativeTo(appContext.filesDir).invariantSeparatorsPath
    }

    fun deleteUserAvatar(relativePath: String?) {
        if (relativePath.isNullOrBlank()) return
        val file = File(appContext.filesDir, relativePath)
        val usersPath = usersRoot.canonicalFile.toPath()
        val targetPath = file.canonicalFile.toPath()
        if (targetPath.startsWith(usersPath)) {
            targetPath.parent?.toFile()?.deleteRecursively()
        }
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
