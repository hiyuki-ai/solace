package com.solace.app.feature.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.solace.app.R
import com.solace.app.data.work.WorkRepository
import com.solace.app.feature.work.WorkDetailScreen

@Composable
fun GalleryScreen(
    refreshToken: Int,
    onOpenUser: (Long) -> Unit,
    onRefreshComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) { WorkRepository(context) }
    val loadedWorks by repository.observePublished().collectAsState(initial = null)
    val works = loadedWorks.orEmpty()
    var currentWorkId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastRefreshToken by rememberSaveable { mutableIntStateOf(Int.MIN_VALUE) }

    LaunchedEffect(loadedWorks, refreshToken) {
        val currentWorks = loadedWorks ?: return@LaunchedEffect
        if (currentWorks.isEmpty()) {
            currentWorkId = null
            onRefreshComplete()
            return@LaunchedEffect
        }

        val currentStillExists = currentWorks.any { it.work.id == currentWorkId }
        if (currentWorkId == null || !currentStillExists || lastRefreshToken != refreshToken) {
            val candidates = currentWorks.filter { it.work.id != currentWorkId }
            currentWorkId = candidates.ifEmpty { currentWorks }.random().work.id
            lastRefreshToken = refreshToken
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val selectedWorkId = currentWorkId
        when {
            selectedWorkId != null -> WorkDetailScreen(
                workId = selectedWorkId,
                shuffle = true,
                refreshKey = refreshToken,
                showBackButton = false,
                showMoreActions = false,
                onBack = {},
                onOpenUser = onOpenUser,
                onReady = onRefreshComplete,
                modifier = Modifier.fillMaxSize(),
            )

            loadedWorks == null -> CircularProgressIndicator(color = Color.White)

            works.isEmpty() -> {
                Text(
                    text = stringResource(R.string.gallery_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }

            else -> CircularProgressIndicator(color = Color.White)
        }
    }
}
