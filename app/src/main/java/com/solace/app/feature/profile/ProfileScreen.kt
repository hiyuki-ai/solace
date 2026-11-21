package com.solace.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.solace.app.R
import com.solace.app.data.douyin.DouyinWork
import com.solace.app.data.work.WorkRepository
import com.solace.app.data.work.WorkWithMedia
import com.solace.app.feature.douyin.DouyinLinkDialog
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onCreate: () -> Unit,
    onOpenDraft: (String) -> Unit,
    onOpenWork: (String) -> Unit,
    onDouyinParsed: (DouyinWork) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) { WorkRepository(context) }
    val drafts by repository.observeDrafts().collectAsState(initial = emptyList())
    val published by repository.observePublished().collectAsState(initial = emptyList())
    var showDouyinDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WorkWithMedia?>(null) }
    val scope = rememberCoroutineScope()

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onCreate,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(text = stringResource(R.string.profile_start_creation))
                }
                OutlinedButton(
                    onClick = { showDouyinDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(text = stringResource(R.string.profile_copy_work))
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(
                title = stringResource(R.string.profile_drafts),
                count = drafts.size,
            )
        }

        if (drafts.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyHint(text = stringResource(R.string.profile_empty_drafts))
            }
        } else {
            items(items = drafts, key = { it.work.id }) { work ->
                WorkCard(
                    work = work,
                    repository = repository,
                    badge = stringResource(R.string.work_draft_badge),
                    onClick = { onOpenDraft(work.work.id) },
                    onDelete = { pendingDelete = work },
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(
                title = stringResource(R.string.profile_works),
                count = published.size,
            )
        }

        if (published.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyHint(text = stringResource(R.string.profile_empty_works))
            }
        } else {
            items(items = published, key = { it.work.id }) { work ->
                WorkCard(
                    work = work,
                    repository = repository,
                    badge = null,
                    onClick = { onOpenWork(work.work.id) },
                    onDelete = { pendingDelete = work },
                )
            }
        }
    }

    if (showDouyinDialog) {
        DouyinLinkDialog(
            onDismiss = { showDouyinDialog = false },
            onParsed = { work ->
                showDouyinDialog = false
                onDouyinParsed(work)
            },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(text = stringResource(R.string.profile_delete_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.profile_delete_message,
                        target.work.caption.ifBlank { stringResource(R.string.work_untitled) },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val workId = target.work.id
                        pendingDelete = null
                        scope.launch { repository.delete(workId) }
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    Text(
        text = "$title ($count)",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
fun WorkCard(
    work: WorkWithMedia,
    repository: WorkRepository,
    badge: String?,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Card(onClick = onClick) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            AsyncImage(
                model = repository.coverFile(work.work.id, work.coverRelativePath),
                contentDescription = work.work.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (badge != null) {
                Text(
                    text = badge,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            // 只有「我」页面传了 onDelete；画廊复用同一个卡片时不显示删除入口。
            val delete = onDelete
            if (delete != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(36.dp)
                        .clickable(onClick = delete),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
            Text(
                text = work.work.caption.ifBlank { stringResource(R.string.work_untitled) },
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}
