package com.solace.app.ui.screens.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.solace.app.R
import com.solace.app.SolaceApplication
import kotlinx.coroutines.delay

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    bucketId: Long,
    videoId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as SolaceApplication
    val folders by app.mediaLibrary.folders.collectAsState()

    LaunchedEffect(Unit) {
        app.mediaLibrary.refreshIfEmpty()
    }

    val videos = remember(folders, bucketId) {
        folders.firstOrNull { it.bucketId == bucketId }?.videos.orEmpty()
    }

    var currentVideoId by remember { mutableLongStateOf(videoId) }
    val index = videos.indexOfFirst { it.id == currentVideoId }
    val current = if (index >= 0) videos[index] else null

    val player = remember { ExoPlayer.Builder(context).build() }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(1f) }
    var speedMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(current?.uri) {
        val item = current ?: return@LaunchedEffect
        position = 0
        duration = 0
        player.setMediaItem(MediaItem.fromUri(item.uri))
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            isPlaying = player.isPlaying
            delay(200)
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    val hasPrevious = index > 0
    val hasNext = index >= 0 && index < videos.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // 顶部：返回 + 视频名称
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                    ),
                )
                .statusBarsPadding()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = Color.White,
                )
            }
            Text(
                text = current?.displayName ?: stringResource(R.string.player_untitled),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        // 底部：进度文本 / 时间轴 / 播放控制
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                    ),
                )
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "${formatTime(position)} / ${formatTime(duration)}",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )

            TimelineSeekBar(
                position = position,
                duration = duration,
                onSeek = { player.seekTo(it) },
                modifier = Modifier.fillMaxWidth(),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            if (hasPrevious) currentVideoId = videos[index - 1].id
                        },
                        enabled = hasPrevious,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = stringResource(R.string.player_previous),
                            tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(32.dp),
                        )
                    }

                    IconButton(
                        onClick = {
                            when {
                                player.playbackState == Player.STATE_ENDED -> {
                                    player.seekTo(0)
                                    player.play()
                                }

                                player.isPlaying -> player.pause()
                                else -> player.play()
                            }
                        },
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) {
                                Icons.Filled.Pause
                            } else {
                                Icons.Filled.PlayArrow
                            },
                            contentDescription = stringResource(
                                if (isPlaying) R.string.player_pause else R.string.player_play,
                            ),
                            tint = Color.White,
                            modifier = Modifier.size(40.dp),
                        )
                    }

                    IconButton(
                        onClick = {
                            if (hasNext) currentVideoId = videos[index + 1].id
                        },
                        enabled = hasNext,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = stringResource(R.string.player_next),
                            tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    TextButton(onClick = { speedMenuExpanded = true }) {
                        Text(
                            text = formatSpeed(speed),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    DropdownMenu(
                        expanded = speedMenuExpanded,
                        onDismissRequest = { speedMenuExpanded = false },
                    ) {
                        SPEED_OPTIONS.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(formatSpeed(option)) },
                                onClick = {
                                    speed = option
                                    player.setPlaybackSpeed(option)
                                    speedMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineSeekBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: if (duration > 0) {
        (position.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }

    Canvas(
        modifier = modifier
            .height(28.dp)
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    if (duration > 0) {
                        onSeek((offset.x / size.width * duration).toLong())
                    }
                }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (duration > 0) {
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        }
                    },
                    onDragEnd = {
                        dragFraction?.let { value ->
                            if (duration > 0) onSeek((value * duration).toLong())
                        }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                    onHorizontalDrag = { change, _ ->
                        if (duration > 0) {
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    },
                )
            },
    ) {
        val centerY = size.height / 2f
        val thumbRadius = 5.dp.toPx()
        val strokeWidth = 2.dp.toPx()
        val startX = thumbRadius
        val endX = size.width - thumbRadius
        val thumbX = startX + (endX - startX) * fraction

        drawLine(
            color = Color.White.copy(alpha = 0.3f),
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = Offset(startX, centerY),
            end = Offset(thumbX, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = Color.White,
            radius = thumbRadius,
            center = Offset(thumbX, centerY),
        )
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatSpeed(speed: Float): String {
    val text = if (speed == speed.toInt().toFloat()) {
        "${speed.toInt()}.0"
    } else {
        speed.toString()
    }
    return "${text}x"
}
