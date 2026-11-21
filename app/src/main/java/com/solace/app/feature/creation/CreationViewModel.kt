package com.solace.app.feature.creation

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.solace.app.R
import com.solace.app.data.work.MediaImporter
import com.solace.app.data.work.PickedMedia
import com.solace.app.data.work.WorkMediaEntity
import com.solace.app.data.work.WorkRepository
import com.solace.app.data.work.WorkStatus
import com.solace.app.data.work.WorkWithMedia
import java.util.UUID
import kotlinx.coroutines.launch

/** 一次最多可选择的素材数量；视频另有更严格的 [MAX_VIDEO_ITEMS] 上限。 */
internal const val MAX_CREATION_ITEMS = 100

private const val MAX_VIDEO_ITEMS = 10

data class CreationUiState(
    val caption: String = "",
    val picked: List<PickedMedia> = emptyList(),
    val pickedCover: Uri? = null,
    val existing: WorkWithMedia? = null,
    val saving: Boolean = false,
    val message: String? = null,
    val done: Boolean = false,
)

/**
 * 统一的创作流程：不区分作品类型，按所选素材逐条保存。
 *
 * 图片和视频可以任意混排，只校验数量：
 * - 素材总数不超过 [MAX_CREATION_ITEMS] 个；
 * - 其中视频不超过 [MAX_VIDEO_ITEMS] 个。
 */
class CreationViewModel(
    application: Application,
    private val draftId: String?,
) : AndroidViewModel(application) {

    private val repository = WorkRepository(application)
    private val importer = MediaImporter(application)
    private val resolver = application.contentResolver

    var uiState by mutableStateOf(CreationUiState())
        private set

    init {
        if (draftId != null) {
            viewModelScope.launch {
                val existing = repository.get(draftId)
                if (existing != null) {
                    uiState = uiState.copy(
                        existing = existing,
                        caption = existing.work.caption,
                    )
                }
            }
        }
    }

    fun onPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val items = uris.map { uri -> PickedMedia(uri, isVideo(uri)) }
        uiState = uiState.copy(
            picked = items,
            message = validationError(items),
        )
    }

    fun onCoverPicked(uri: Uri) {
        uiState = uiState.copy(pickedCover = uri)
    }

    fun onCaptionChange(value: String) {
        uiState = uiState.copy(caption = value)
    }

    fun clearMessage() {
        uiState = uiState.copy(message = null)
    }

    fun save(status: String) {
        val state = uiState
        if (state.saving) return

        if (state.picked.isNotEmpty()) {
            val error = validationError(state.picked)
            if (error != null) {
                uiState = state.copy(message = error)
                return
            }
        } else if (state.existing == null) {
            uiState = state.copy(message = string(R.string.creation_error_no_media))
            return
        }

        viewModelScope.launch {
            uiState = state.copy(saving = true, message = null)
            try {
                val workId = state.existing?.work?.id ?: UUID.randomUUID().toString()
                var media: List<WorkMediaEntity>
                var coverFile: String?
                var commitStaging = false

                if (state.picked.isNotEmpty()) {
                    val result = importer.importInto(
                        workId = workId,
                        storage = repository.storage,
                        items = state.picked,
                        coverUri = state.pickedCover,
                    )
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
                    }
                    coverFile = result.coverFile
                    commitStaging = true
                } else {
                    val existing = state.existing
                        ?: throw IllegalStateException("请先选择图片或视频")
                    media = existing.media
                    coverFile = existing.coverRelativePath
                    if (state.pickedCover != null) {
                        val replaced = importer.replaceCover(
                            repository.storage.workDir(workId),
                            state.pickedCover,
                        )
                        if (replaced != null) coverFile = replaced
                    }
                }

                repository.save(
                    workId = workId,
                    caption = state.caption.trim(),
                    status = status,
                    media = media,
                    coverFile = coverFile,
                    commitStaging = commitStaging,
                )
                uiState = uiState.copy(saving = false, done = true)
            } catch (e: Exception) {
                uiState = uiState.copy(
                    saving = false,
                    message = e.message ?: "保存失败，请重试",
                )
            }
        }
    }

    fun saveDraft() = save(WorkStatus.DRAFT)

    fun publish() = save(WorkStatus.PUBLISHED)

    /** 只校验数量：图片和视频可以混排，不区分作品类型。 */
    private fun validationError(items: List<PickedMedia>): String? {
        val videos = items.count { it.isVideo }
        return when {
            items.isEmpty() -> string(R.string.creation_error_no_media)
            videos > MAX_VIDEO_ITEMS -> {
                string(R.string.creation_error_video_limit, MAX_VIDEO_ITEMS)
            }

            items.size > MAX_CREATION_ITEMS -> {
                string(R.string.creation_error_item_limit, MAX_CREATION_ITEMS)
            }

            else -> null
        }
    }

    private fun isVideo(uri: Uri): Boolean =
        resolver.getType(uri)?.startsWith("video/") == true

    private fun string(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    companion object {
        fun factory(
            application: Application,
            draftId: String?,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { CreationViewModel(application, draftId) }
        }
    }
}
