package com.solace.app.feature.douyin

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.solace.app.data.douyin.DouyinImporter
import com.solace.app.data.douyin.DouyinMedia
import com.solace.app.data.douyin.DouyinWork
import com.solace.app.data.work.OriginPlatform
import com.solace.app.data.work.WorkMediaEntity
import com.solace.app.data.work.WorkOriginEntity
import com.solace.app.data.work.WorkRepository
import com.solace.app.data.work.WorkStatus
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DouyinImportUiState(
    val work: DouyinWork? = null,
    val caption: String = "",
    val media: List<DouyinMedia> = emptyList(),
    val saving: Boolean = false,
    val downloaded: Int = 0,
    val total: Int = 0,
    val error: String? = null,
    val done: Boolean = false,
    val expired: Boolean = false,
)

/**
 * 复制作品的确认页：先改文案、删素材，确认时才下载并入库。
 */
class DouyinImportViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WorkRepository(application)
    private val importer = DouyinImporter()
    private val parsed: DouyinWork? = DouyinImportHandoff.consume()

    var uiState by mutableStateOf(
        if (parsed == null) {
            DouyinImportUiState(expired = true)
        } else {
            DouyinImportUiState(
                work = parsed,
                caption = parsed.caption,
                media = parsed.media,
            )
        },
    )
        private set

    fun onCaptionChange(value: String) {
        uiState = uiState.copy(caption = value)
    }

    fun removeAt(index: Int) {
        val state = uiState
        if (state.saving) return
        val remaining = state.media.filterIndexed { current, _ -> current != index }
        uiState = state.copy(
            media = remaining,
            error = if (remaining.isEmpty()) "请至少保留一个素材" else state.error,
        )
    }

    fun saveDraft() = save(WorkStatus.DRAFT)

    fun publish() = save(WorkStatus.PUBLISHED)

    private fun save(status: String) {
        val state = uiState
        val work = state.work ?: return
        if (state.saving) return
        if (state.media.isEmpty()) {
            uiState = state.copy(error = "请至少保留一个素材")
            return
        }

        viewModelScope.launch {
            uiState = state.copy(
                saving = true,
                error = null,
                downloaded = 0,
                total = state.media.size + if (work.audio != null) 1 else 0,
            )
            val workId = UUID.randomUUID().toString()
            try {
                val result = withContext(Dispatchers.IO) {
                    importer.importInto(
                        workId = workId,
                        storage = repository.storage,
                        coverUrl = work.coverUrl,
                        media = state.media,
                        audio = work.audio,
                    ) { downloaded, total ->
                        viewModelScope.launch {
                            uiState = uiState.copy(downloaded = downloaded, total = total)
                        }
                    }
                }

                repository.save(
                    workId = workId,
                    caption = state.caption.trim(),
                    status = status,
                    media = result.media.mapIndexed { index, item ->
                        WorkMediaEntity(
                            workId = workId,
                            orderIndex = index,
                            kind = item.kind,
                            fileName = item.relativePath,
                            width = item.width,
                            height = item.height,
                            durationMs = item.durationMs,
                            sizeBytes = item.sizeBytes,
                        )
                    },
                    coverFile = result.coverFile,
                    commitStaging = true,
                    origin = WorkOriginEntity(
                        workId = workId,
                        platform = OriginPlatform.DOUYIN,
                        authorName = work.author?.nickname,
                        authorId = work.author?.displayId,
                        authorSecUid = work.author?.secUid,
                        awemeId = work.awemeId,
                        publishedAt = work.publishedAt,
                    ),
                )
                uiState = uiState.copy(saving = false, done = true)
            } catch (e: Exception) {
                withContext(Dispatchers.IO) { repository.storage.deleteWork(workId) }
                uiState = uiState.copy(
                    saving = false,
                    error = e.message ?: "保存失败，请重试",
                )
            }
        }
    }
}
