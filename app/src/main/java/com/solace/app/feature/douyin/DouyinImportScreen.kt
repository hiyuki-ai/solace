package com.solace.app.feature.douyin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.solace.app.R
import com.solace.app.data.douyin.DouyinMedia
import com.solace.app.data.douyin.DouyinKind
import com.solace.app.data.douyin.DouyinWork
import com.solace.app.data.work.MediaKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 复制抖音作品的确认页：作者与发布时间只读，文案和素材可以改。
 *
 * 真正下载素材发生在点「存草稿 / 发布」之后，改主意退出不会浪费流量。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DouyinImportScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val viewModel: DouyinImportViewModel = viewModel()
    val state = viewModel.uiState

    LaunchedEffect(state.done) {
        if (state.done) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.douyin_import_title)) },
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
            if (!state.expired) {
                Surface(shadowElevation = 8.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.saving) {
                            Text(
                                text = stringResource(
                                    R.string.douyin_import_saving,
                                    state.downloaded,
                                    state.total,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                }
            }
        },
    ) { innerPadding ->
        if (state.expired) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.douyin_import_expired),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OriginCard(work = state.work)

                Text(
                    text = stringResource(R.string.douyin_import_media_title, state.media.size),
                    style = MaterialTheme.typography.titleSmall,
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(state.media) { index, media ->
                        MediaThumb(
                            media = media,
                            // 视频用封面预览，别让 Coil 去下载整个 mp4 解码首帧。
                            previewUrl = if (media.kind == MediaKind.VIDEO) {
                                state.work?.coverUrl
                            } else {
                                media.url
                            },
                            onRemove = { viewModel.removeAt(index) },
                        )
                    }
                }

                if (state.work?.kind == DouyinKind.IMAGES) {
                    val hasAudio = state.work.audio != null
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (hasAudio) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                        Text(
                            text = stringResource(
                                if (hasAudio) {
                                    R.string.douyin_import_audio_included
                                } else {
                                    R.string.douyin_import_audio_missing
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasAudio) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }

                OutlinedTextField(
                    value = state.caption,
                    onValueChange = viewModel::onCaptionChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                    placeholder = { Text(text = stringResource(R.string.creation_caption_hint)) },
                    minLines = 5,
                )

                state.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(modifier = Modifier.size(8.dp))
            }
        }
    }
}

@Composable
private fun OriginCard(work: DouyinWork?) {
    val author = work?.author
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = author?.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = author?.nickname
                        ?: stringResource(R.string.douyin_import_unknown_author),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                author?.displayId?.let { id ->
                    Text(
                        text = stringResource(R.string.douyin_import_author_id, id),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                work?.publishedAt?.let { publishedAt ->
                    Text(
                        text = stringResource(
                            R.string.douyin_import_published_at,
                            formatDateTime(publishedAt),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaThumb(media: DouyinMedia, previewUrl: String?, onRemove: () -> Unit) {
    Box(modifier = Modifier.size(104.dp)) {
        AsyncImage(
            model = previewUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        if (media.kind == MediaKind.VIDEO) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_delete),
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun formatDateTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
