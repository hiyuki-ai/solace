package com.solace.app.feature.creation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.solace.app.R
import com.solace.app.data.work.WorkRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreationScreen(
    draftId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    val viewModel: CreationViewModel = viewModel(
        factory = CreationViewModel.factory(application, draftId),
    )
    val state = viewModel.uiState
    val repository = remember(context) { WorkRepository(context) }

    // 图片和视频在同一个选择器里选，可以混排，保存时逐条记录素材类型。
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_CREATION_ITEMS),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.onPicked(uris)
    }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let(viewModel::onCoverPicked) }

    fun launchMediaPicker() {
        mediaPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
        )
    }

    LaunchedEffect(Unit) {
        // 只有新建作品才自动打开选择器；编辑草稿时等待既有素材加载。
        if (draftId == null && state.picked.isEmpty()) {
            launchMediaPicker()
        }
    }

    LaunchedEffect(state.done) {
        if (state.done) onSaved()
    }

    val existingWork = state.existing
    val existingFiles = existingWork?.let { work ->
        work.media.map { repository.mediaFile(work.work.id, it.fileName) }
    }.orEmpty()

    val displayModels: List<Any> = if (state.picked.isNotEmpty()) {
        state.picked.map { it.uri }
    } else {
        existingFiles
    }

    val coverModel: Any? = when {
        state.pickedCover != null -> state.pickedCover
        state.picked.isNotEmpty() -> state.picked.first().uri
        existingWork != null -> repository.coverFile(
            existingWork.work.id,
            existingWork.coverRelativePath,
        )

        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (draftId != null) {
                            stringResource(R.string.creation_edit_draft)
                        } else {
                            stringResource(R.string.creation_new)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::saveDraft,
                        enabled = !state.saving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.creation_save_draft))
                    }
                    Button(
                        onClick = viewModel::publish,
                        enabled = !state.saving,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(text = stringResource(R.string.creation_publish))
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                displayModels.forEach { model ->
                    AsyncImage(
                        model = model,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
            }

            TextButton(onClick = { launchMediaPicker() }) {
                Text(
                    text = if (displayModels.isEmpty()) {
                        stringResource(R.string.creation_pick_media)
                    } else {
                        stringResource(R.string.creation_repick_media)
                    },
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    AsyncImage(
                        model = coverModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.creation_cover),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    TextButton(onClick = {
                        coverPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }) {
                        Text(text = stringResource(R.string.creation_change_cover))
                    }
                }
            }

            OutlinedTextField(
                value = state.caption,
                onValueChange = viewModel::onCaptionChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                placeholder = { Text(text = stringResource(R.string.creation_caption_hint)) },
                minLines = 4,
            )

            state.message?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
