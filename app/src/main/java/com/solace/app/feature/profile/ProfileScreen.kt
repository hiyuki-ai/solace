package com.solace.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.solace.app.R
import com.solace.app.data.work.WorkRepository
import com.solace.app.data.work.WorkWithMedia

@Composable
fun ProfileScreen(
    onCreate: () -> Unit,
    onOpenDraft: (String) -> Unit,
    onOpenWork: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) { WorkRepository(context) }
    val drafts by repository.observeDrafts().collectAsState(initial = emptyList())
    val published by repository.observePublished().collectAsState(initial = emptyList())

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Button(
                onClick = onCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(text = stringResource(R.string.profile_start_creation))
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
                )
            }
        }
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
