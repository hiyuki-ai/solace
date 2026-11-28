package com.solace.app.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
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
import com.solace.app.data.work.WorkStatus
import com.solace.app.data.work.WorkRepository
import com.solace.app.data.work.WorkWithMedia
import com.solace.app.feature.douyin.DouyinLinkDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    val works by repository.observeAll().collectAsState(initial = emptyList())
    var showDouyinDialog by rememberSaveable { mutableStateOf(false) }
    var showCreationActions by rememberSaveable { mutableStateOf(false) }
    var batchSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedWorkIds by rememberSaveable(
        stateSaver = listSaver(
            save = { it.toList() },
            restore = { it.toSet() },
        ),
    ) { mutableStateOf(emptySet<String>()) }
    var showBatchDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val creationActionsSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val scope = rememberCoroutineScope()
    val allSelected = works.isNotEmpty() && works.all { it.work.id in selectedWorkIds }

    fun exitBatchSelection() {
        batchSelectionMode = false
        selectedWorkIds = emptySet()
    }

    fun toggleWorkSelection(workId: String) {
        selectedWorkIds = if (workId in selectedWorkIds) {
            selectedWorkIds - workId
        } else {
            selectedWorkIds + workId
        }
    }

    BackHandler(enabled = batchSelectionMode, onBack = ::exitBatchSelection)

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(
                title = stringResource(R.string.profile_works),
                count = works.size,
                selectionMode = batchSelectionMode,
                selectedCount = selectedWorkIds.size,
                allSelected = allSelected,
                canDelete = works.isNotEmpty(),
                onAddClick = {
                    showCreationActions = true
                },
                onDeleteClick = {
                    if (batchSelectionMode) {
                        if (selectedWorkIds.isNotEmpty()) {
                            showBatchDeleteDialog = true
                        }
                    } else {
                        batchSelectionMode = true
                    }
                },
                onToggleAll = {
                    selectedWorkIds = if (allSelected) {
                        emptySet()
                    } else {
                        works.mapTo(mutableSetOf()) { it.work.id }
                    }
                },
                onCancelSelection = ::exitBatchSelection,
            )
        }

        if (works.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyHint(text = stringResource(R.string.profile_empty_works))
            }
        } else {
            items(items = works, key = { it.work.id }) { work ->
                val isDraft = work.work.status == WorkStatus.DRAFT
                WorkCard(
                    work = work,
                    repository = repository,
                    badge = if (isDraft) stringResource(R.string.work_draft_badge) else null,
                    selectionState = if (batchSelectionMode) {
                        work.work.id in selectedWorkIds
                    } else {
                        null
                    },
                    onClick = {
                        if (batchSelectionMode) {
                            toggleWorkSelection(work.work.id)
                        } else if (isDraft) {
                            onOpenDraft(work.work.id)
                        } else {
                            onOpenWork(work.work.id)
                        }
                    },
                )
            }
        }
    }

    if (showCreationActions) {
        ModalBottomSheet(
            onDismissRequest = { showCreationActions = false },
            sheetState = creationActionsSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        showCreationActions = false
                        onCreate()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(text = stringResource(R.string.profile_start_creation))
                }
                OutlinedButton(
                    onClick = {
                        showCreationActions = false
                        showDouyinDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(text = stringResource(R.string.profile_copy_work))
                }
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

    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text(text = stringResource(R.string.profile_batch_delete_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.profile_batch_delete_message,
                        selectedWorkIds.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val workIds = selectedWorkIds.toList()
                        showBatchDeleteDialog = false
                        scope.launch {
                            workIds.forEach { workId ->
                                repository.delete(workId)
                            }
                            exitBatchSelection()
                        }
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(
    title: String,
    count: Int,
    selectionMode: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    canDelete: Boolean,
    onAddClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onToggleAll: () -> Unit,
    onCancelSelection: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 6.dp, end = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (selectionMode) {
                stringResource(R.string.profile_selected_count, selectedCount)
            } else {
                "$title ($count)"
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (selectionMode) {
            TextButton(onClick = onToggleAll) {
                Text(
                    text = stringResource(
                        if (allSelected) {
                            R.string.profile_clear_selection
                        } else {
                            R.string.profile_select_all
                        },
                    ),
                )
            }
            FilledTonalIconButton(
                onClick = onDeleteClick,
                enabled = selectedCount > 0,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.profile_batch_delete),
                )
            }
            TextButton(onClick = onCancelSelection) {
                Text(text = stringResource(R.string.action_cancel))
            }
        } else {
            FilledTonalIconButton(
                onClick = onDeleteClick,
                enabled = canDelete,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.profile_batch_delete),
                )
            }
            FilledTonalIconButton(onClick = onAddClick) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.profile_open_creation_actions),
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
fun WorkCard(
    work: WorkWithMedia,
    repository: WorkRepository,
    badge: String?,
    selectionState: Boolean? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = repository.coverFile(work.work.id, work.coverRelativePath),
            contentDescription = work.work.caption,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (selectionState != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (selectionState) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        } else {
                            Color.Black.copy(alpha = 0.20f)
                        },
                    ),
            )
        }
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
        if (selectionState != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (selectionState) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Black.copy(alpha = 0.38f)
                        },
                    )
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selectionState) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
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
