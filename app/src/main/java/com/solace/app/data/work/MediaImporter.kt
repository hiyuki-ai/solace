package com.solace.app.data.work

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PickedMedia(val uri: Uri, val isVideo: Boolean)

data class ImportedMedia(
    val relativePath: String,
    val kind: String,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val sizeBytes: Long,
)

data class ImportResult(
    val media: List<ImportedMedia>,
    val coverFile: String?,
    /** 抖音作者头像相对 filesDir 的路径；本地创作作品为 null。 */
    val authorAvatarFile: String? = null,
)

/**
 * 把系统选择器返回的 content:// 拷贝进应用目录，并提取元数据、生成封面。
 */
class MediaImporter(context: Context) {

    private val resolver = context.contentResolver

    suspend fun importInto(
        workId: String,
        storage: WorksStorage,
        items: List<PickedMedia>,
        coverUri: Uri?,
    ): ImportResult = withContext(Dispatchers.IO) {
        val dir = storage.stagingDir(workId)
        dir.deleteRecursively()
        dir.mkdirs()
        val mediaDir = storage.mediaDir(dir)

        val imported = items.mapIndexed { index, item ->
            val target = File(mediaDir, "%03d.%s".format(index, extensionOf(item.uri)))
            resolver.openInputStream(item.uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("无法读取所选文件")

            val (width, height, duration) = if (item.isVideo) {
                probeVideo(target)
            } else {
                probeImage(target)
            }
            ImportedMedia(
                relativePath = "media/${target.name}",
                kind = if (item.isVideo) MediaKind.VIDEO else MediaKind.IMAGE,
                width = width,
                height = height,
                durationMs = duration,
                sizeBytes = target.length(),
            )
        }

        val coverFile = when {
            coverUri != null -> writeCoverFromUri(dir, coverUri)
            items.firstOrNull()?.isVideo == true -> generateCoverFromVideo(dir, imported.first())
            else -> generateCoverFromImage(dir, imported.first())
        }

        ImportResult(media = imported, coverFile = coverFile?.name)
    }

    /** 只替换封面，直接写入作品目录。 */
    suspend fun replaceCover(workDir: File, uri: Uri): String? = withContext(Dispatchers.IO) {
        writeCoverFromUri(workDir, uri)?.name
    }

    private fun extensionOf(uri: Uri): String {
        val mime = resolver.getType(uri).orEmpty()
        return when {
            mime.contains("mp4") -> "mp4"
            mime.contains("webm") -> "webm"
            mime.contains("3gp") -> "3gp"
            mime.contains("jpeg") -> "jpg"
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("heic") -> "heic"
            mime.startsWith("image/") -> "jpg"
            else -> {
                val name = queryDisplayName(uri)
                val ext = name.substringAfterLast('.', "").lowercase()
                ext.ifBlank { "bin" }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }.orEmpty()

    private fun probeVideo(file: File): Triple<Int?, Int?, Long?> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            Triple(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
            )
        } catch (e: Exception) {
            Triple(null, null, null)
        } finally {
            retriever.release()
        }
    }

    private fun probeImage(file: File): Triple<Int?, Int?, Long?> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return Triple(options.outWidth.takeIf { it > 0 }, options.outHeight.takeIf { it > 0 }, null)
    }

    private fun generateCoverFromVideo(dir: File, media: ImportedMedia): File? {
        val file = File(dir, media.relativePath)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val frame = retriever.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            saveCover(dir, frame)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun generateCoverFromImage(dir: File, media: ImportedMedia): File? {
        val file = File(dir, media.relativePath)
        val bitmap = decodeScaled(file, MAX_COVER_SIZE) ?: return null
        return try {
            saveCover(dir, bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun writeCoverFromUri(dir: File, uri: Uri): File? = try {
        resolver.openInputStream(uri)?.use { input ->
            val temp = File.createTempFile("cover", ".tmp", dir)
            temp.outputStream().use { output -> input.copyTo(output) }
            val bitmap = decodeScaled(temp, MAX_COVER_SIZE)
            temp.delete()
            bitmap?.let {
                try {
                    saveCover(dir, it)
                } finally {
                    it.recycle()
                }
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun decodeScaled(file: File, maxSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize || bounds.outHeight / (sample * 2) >= maxSize) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= maxSize) return decoded

        val ratio = maxSize.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun saveCover(dir: File, bitmap: Bitmap): File {
        val target = File(dir, "cover.jpg")
        target.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
        }
        return target
    }

    companion object {
        private const val MAX_COVER_SIZE = 1080
    }
}
