package com.solace.app.data.douyin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.solace.app.data.work.ImportResult
import com.solace.app.data.work.ImportedMedia
import com.solace.app.data.work.MediaKind
import com.solace.app.data.work.WorksStorage
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把解析出来的素材逐条下载进作品的 staging 目录，并生成封面。
 *
 * 和 [com.solace.app.data.work.MediaImporter] 保持同样的目录约定：
 * media/000.mp4、media/001.jpeg、cover.jpg。
 */
class DouyinImporter {

    suspend fun importInto(
        workId: String,
        storage: WorksStorage,
        coverUrl: String?,
        media: List<DouyinMedia>,
        audio: DouyinAudio?,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportResult = withContext(Dispatchers.IO) {
        val dir = storage.stagingDir(workId)
        dir.deleteRecursively()
        dir.mkdirs()
        val mediaDir = storage.mediaDir(dir)
        val total = media.size + if (audio != null) 1 else 0

        val imported = ArrayList<ImportedMedia>(media.size)
        media.forEachIndexed { index, item ->
            onProgress(index, total)
            val temp = File(mediaDir, "%03d.download".format(index))
            val contentType = try {
                DouyinHttp.download(item.url, temp)
            } catch (e: IOException) {
                throw IOException("第 ${index + 1} 个素材下载失败：${e.message}", e)
            }

            val extension = extensionFor(contentType, item.url, item.kind)
            val target = File(mediaDir, "%03d.%s".format(index, extension))
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }

            val needsProbe = item.width == null || item.height == null ||
                (item.kind == MediaKind.VIDEO && item.durationMs == null)
            val (width, height, duration) = if (needsProbe) {
                probe(target, item.kind)
            } else {
                Triple(item.width, item.height, item.durationMs)
            }

            imported += ImportedMedia(
                relativePath = "media/${target.name}",
                kind = item.kind,
                width = width,
                height = height,
                durationMs = duration,
                sizeBytes = target.length(),
            )
        }

        if (audio != null) {
            onProgress(media.size, total)
            val index = media.size
            val temp = File(mediaDir, "%03d.download".format(index))
            val contentType = try {
                DouyinHttp.download(audio.url, temp)
            } catch (e: IOException) {
                throw IOException("作品音频下载失败：${e.message}", e)
            }

            val extension = extensionFor(contentType, audio.url, MediaKind.AUDIO)
            val target = File(mediaDir, "%03d.%s".format(index, extension))
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            imported += ImportedMedia(
                relativePath = "media/${target.name}",
                kind = MediaKind.AUDIO,
                width = null,
                height = null,
                durationMs = audio.durationMs,
                sizeBytes = target.length(),
            )
        }
        onProgress(total, total)

        ImportResult(
            media = imported,
            coverFile = coverUrl?.let { writeCover(dir, it) },
        )
    }

    private fun extensionFor(contentType: String, url: String, kind: String): String {
        val type = contentType.substringBefore(';').trim().lowercase()
        return when {
            type.contains("audio/mp4") || type.contains("m4a") -> "m4a"
            type.contains("audio/mpeg") || type.contains("audio/mp3") -> "mp3"
            type.contains("audio/aac") -> "aac"
            type.contains("audio/ogg") -> "ogg"
            type.contains("mp4") -> "mp4"
            type.contains("webm") -> "webm"
            type.contains("quicktime") -> "mov"
            type.contains("jpeg") -> "jpg"
            type.contains("png") -> "png"
            type.contains("webp") -> "webp"
            type.contains("heic") || type.contains("heif") -> "heic"
            kind == MediaKind.VIDEO -> "mp4"
            kind == MediaKind.AUDIO -> urlExtension(url) ?: "mp3"
            else -> urlExtension(url) ?: "jpg"
        }
    }

    private fun urlExtension(url: String): String? {
        val name = url.substringBefore('?').substringAfterLast('/')
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension.takeIf {
            it in setOf(
                "mp4",
                "webm",
                "mov",
                "jpg",
                "jpeg",
                "png",
                "webp",
                "heic",
                "heif",
                "mp3",
                "m4a",
                "aac",
                "ogg",
            )
        }?.let { if (it == "jpeg") "jpg" else it }
    }

    private fun probe(file: File, kind: String): Triple<Int?, Int?, Long?> =
        if (kind == MediaKind.VIDEO) probeVideo(file) else probeImage(file)

    private fun probeVideo(file: File): Triple<Int?, Int?, Long?> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            Triple(
                retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull(),
                retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull(),
                retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull(),
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
        return Triple(
            options.outWidth.takeIf { it > 0 },
            options.outHeight.takeIf { it > 0 },
            null,
        )
    }

    /** 视频封面单独下载并转成 JPEG；失败就不生成封面，等列表回退到首帧。 */
    private fun writeCover(dir: File, url: String): String? {
        val temp = File(dir, "cover.tmp")
        return try {
            DouyinHttp.download(url, temp)
            val bitmap = decodeScaled(temp, MAX_COVER_SIZE)
            if (bitmap != null) {
                try {
                    saveCover(dir, bitmap)
                } finally {
                    bitmap.recycle()
                }
            } else {
                val target = File(dir, "cover.jpg")
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                }
            }
            COVER_FILE
        } catch (e: Exception) {
            null
        } finally {
            temp.delete()
        }
    }

    private fun decodeScaled(file: File, maxSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize ||
            bounds.outHeight / (sample * 2) >= maxSize
        ) {
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

    private fun saveCover(dir: File, bitmap: Bitmap) {
        File(dir, COVER_FILE).outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
        }
    }

    private companion object {
        const val COVER_FILE = "cover.jpg"
        const val MAX_COVER_SIZE = 1080
    }
}
