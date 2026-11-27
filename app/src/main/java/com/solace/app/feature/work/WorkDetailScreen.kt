package com.solace.app.feature.work

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.annotation.OptIn
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.solace.app.R
import com.solace.app.data.work.MediaKind
import com.solace.app.data.work.WorkRepository
import com.solace.app.data.work.WorkWithMedia
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 图片自动翻页间隔。 */
private const val IMAGE_DURATION_MS = 4_000L

private const val IMAGE_DURATION_NANOS = IMAGE_DURATION_MS * 1_000_000L

/** 播放进度刷新间隔。 */
private const val PROGRESS_POLL_MS = 50L

private data class WorkFeed(
    val works: List<WorkWithMedia>,
    val initialIndex: Int,
)

/**
 * 作品浏览页：抖音式竖滑信息流。
 *
 * - 上下滑动切换作品；
 * - 点击画面暂停 / 继续；
 * - 左右滑动切换同一个作品里的视频 / 图片；
 * - 底部进度条按素材分格，可拖动跳转；进度条上方左侧是作品文案。
 */
@Composable
fun WorkDetailScreen(
    workId: String,
    onBack: () -> Unit,
    onOpenUser: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) { WorkRepository(context) }
    var feed by remember(workId) { mutableStateOf<WorkFeed?>(null) }
    var resolved by remember(workId) { mutableStateOf(false) }

    LaunchedEffect(workId) {
        repository.observePublished().collect { published ->
            val index = published.indexOfFirst { it.work.id == workId }
            feed = if (index >= 0) {
                WorkFeed(published, index)
            } else {
                // 兜底：作品不在已发布列表里（例如从其他入口跳转）时只展示它自己。
                repository.get(workId)?.let { WorkFeed(listOf(it), 0) }
            }
            resolved = true
        }
    }

    val currentFeed = feed
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            currentFeed != null -> WorkFeedPager(
                feed = currentFeed,
                repository = repository,
                onBack = onBack,
                onOpenUser = onOpenUser,
            )

            !resolved -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )

            else -> EmptyFeed(onBack = onBack)
        }
    }
}

@Composable
private fun WorkFeedPager(
    feed: WorkFeed,
    repository: WorkRepository,
    onBack: () -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = feed.initialIndex) { feed.works.size }

    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { page -> feed.works[page].work.id },
    ) { page ->
        WorkFeedPage(
            work = feed.works[page],
            repository = repository,
            active = pagerState.currentPage == page,
            onBack = onBack,
            onOpenUser = onOpenUser,
        )
    }
}

@Composable
private fun WorkFeedPage(
    work: WorkWithMedia,
    repository: WorkRepository,
    active: Boolean,
    onBack: () -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    val visualMedia = remember(work) { work.visualMedia }
    val files = remember(work) {
        visualMedia.map { repository.mediaFile(work.work.id, it.fileName) }
    }
    val kinds = remember(work) { visualMedia.map { it.kind } }
    val durations = remember(work) { visualMedia.map { it.durationMs ?: 0L } }
    val cover = remember(work) {
        repository.coverFile(work.work.id, work.coverRelativePath)
    }
    val audioFile = remember(work) {
        work.audioFile
            ?.let { repository.mediaFile(work.work.id, it) }
            ?.takeIf { it.exists() }
    }
    val authorAvatar = remember(work.user?.avatarFile) {
        repository.userAvatarFile(work.user?.avatarFile)
    }

    // 每个作品独立记录播放状态，切回来时从播放开始。
    var playing by remember(work.work.id) { mutableStateOf(true) }
    var scrubbing by remember(work.work.id) { mutableStateOf(false) }
    var dragFraction by remember(work.work.id) { mutableFloatStateOf(0f) }
    var playFraction by remember(work.work.id) { mutableFloatStateOf(0f) }
    var pendingSeekPage by remember(work.work.id) { mutableIntStateOf(-1) }
    var pendingSeekFraction by remember(work.work.id) { mutableFloatStateOf(0f) }

    // 同一个作品内部的素材翻页：图片和视频按选择顺序混排。
    val itemPager = rememberPagerState { files.size }
    val scope = rememberCoroutineScope()
    val handle = remember(work.work.id) { PlayerHandle() }
    val segmentCount = files.size.coerceAtLeast(1)

    // 退到后台时暂停，避免锁屏后声音继续。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) playing = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 切到别的素材时，进度从新素材的开头算起。
    LaunchedEffect(itemPager.currentPage) { playFraction = 0f }

    val totalFraction = if (scrubbing) {
        dragFraction
    } else {
        ((itemPager.currentPage + playFraction.coerceIn(0f, 1f)) / segmentCount).coerceIn(0f, 1f)
    }

    fun isVideoPage(index: Int): Boolean = kinds.getOrNull(index) == MediaKind.VIDEO

    // 拖动进度条时显示当前视频的时间；图片段只展示自动进度，不支持拖动。
    val scrubTimeLabel: String? = if (scrubbing) {
        val scaled = (dragFraction * segmentCount).coerceIn(0f, segmentCount - 0.0001f)
        val index = scaled.toInt()
        val local = scaled - index
        if (isVideoPage(index)) {
            val duration = durations.getOrElse(index) { 0L }
            if (duration > 0L) {
                "${formatProgressTime((duration * local).toLong())} / ${formatProgressTime(duration)}"
            } else {
                null
            }
        } else {
            null
        }
    } else {
        null
    }

    fun seekVideo(fraction: Float) {
        val player = handle.player ?: return
        val duration = durations.getOrNull(itemPager.currentPage)?.takeIf { it > 0L }
            ?: player.duration
        if (duration > 0L) {
            player.seekTo((duration * fraction).toLong().coerceIn(0L, duration))
        }
    }

    fun scrubTo(fraction: Float) {
        val scaled = (fraction * segmentCount).coerceIn(0f, segmentCount - 0.0001f)
        val index = scaled.toInt()
        val local = scaled - index
        // 图片段不支持拖动，只有当前视频段可以实时 seek。
        if (index == itemPager.currentPage && isVideoPage(index)) {
            seekVideo(local)
        }
    }

    fun commitScrub(fraction: Float) {
        val scaled = (fraction * segmentCount).coerceIn(0f, segmentCount - 0.0001f)
        val index = scaled.toInt()
        val local = scaled - index
        // 松手落在图片段上时保持原播放状态，图片始终从自己的开头开始计时。
        if (!isVideoPage(index)) {
            scrubbing = false
            return
        }
        if (index != itemPager.currentPage) {
            pendingSeekPage = index
            pendingSeekFraction = local
            scope.launch { itemPager.scrollToPage(index) }
        } else {
            seekVideo(local)
        }
        scrubbing = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // 点击层单独铺在画面上：底部文案/进度条是它的上层兄弟节点，
        // 按在进度条上时进度条会吃掉手势，不会误触发暂停。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { playing = !playing },
        ) {
            // 封面垫底，视频首帧出来前不会闪黑。
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            HorizontalPager(
                state = itemPager,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { page -> files[page].path },
            ) { page ->
                val current = active && page == itemPager.currentPage
                if (kinds[page] == MediaKind.VIDEO) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 当前页以外的视频用一帧画面占位，滑动时不会露黑。
                        AsyncImage(
                            model = files[page],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (current) {
                            VideoStage(
                                file = files[page],
                                durationMs = durations.getOrElse(page) { 0L },
                                initialFraction = if (pendingSeekPage == page) {
                                    pendingSeekFraction
                                } else {
                                    0f
                                },
                                playing = playing && !scrubbing,
                                handle = handle,
                                onProgress = { fraction -> playFraction = fraction },
                                onCompleted = {
                                    val count = files.size
                                    if (count <= 1) {
                                        handle.player?.apply {
                                            seekTo(0L)
                                            play()
                                        }
                                    } else {
                                        val next = (itemPager.currentPage + 1) % count
                                        scope.launch { itemPager.animateScrollToPage(next) }
                                    }
                                },
                                onInitialSeekApplied = {
                                    if (pendingSeekPage == page) pendingSeekPage = -1
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                } else {
                    ImagePage(
                        file = files[page],
                        active = current,
                        playing = current && playing && !scrubbing,
                        loop = segmentCount <= 1,
                        onProgress = { fraction -> playFraction = fraction },
                        onCompleted = {
                            val next = (itemPager.currentPage + 1) % segmentCount
                            scope.launch { itemPager.animateScrollToPage(next) }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (audioFile != null) {
            WorkAudio(
                file = audioFile,
                playing = active && playing && !scrubbing,
            )
        }

        TopBar(onBack = onBack)

        val user = work.user
        if (user != null) {
            val avatarModel = authorAvatar ?: user.avatarUrl
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable { onOpenUser(user.id) },
                contentAlignment = Alignment.Center,
            ) {
                if (avatarModel != null) {
                    AsyncImage(
                        model = avatarModel,
                        contentDescription = stringResource(R.string.work_open_user),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = user.nickname?.trim()?.firstOrNull()?.toString().orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        if (!playing) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.player_play),
                    tint = Color.White,
                    modifier = Modifier.size(44.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color.Black.copy(alpha = 0.35f),
                        1f to Color.Black.copy(alpha = 0.78f),
                    ),
                )
                // 左右不留边，让下面的进度条占满整个屏幕宽度；文案自己补 16dp。
                .padding(top = 32.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val user = work.user
            if (user != null &&
                !user.nickname.isNullOrBlank()
            ) {
                val name = user.nickname.orEmpty()
                val createdAt = work.origin?.publishedAt
                    ?: work.work.publishedAt
                    ?: work.work.createdAt
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.work_user_handle, name),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = formatWorkDate(createdAt),
                        color = Color.White.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (work.work.caption.isNotBlank()) {
                Text(
                    text = work.work.caption,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.55f),
                            offset = Offset(0f, 1f),
                            blurRadius = 12f,
                        ),
                    ),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            WorkProgressBar(
                segmentCount = segmentCount,
                totalFraction = totalFraction,
                scrubbing = scrubbing,
                timeLabel = scrubTimeLabel,
                onScrubStart = { fraction ->
                    dragFraction = fraction
                    scrubbing = true
                },
                onScrub = { fraction ->
                    dragFraction = fraction
                    scrubTo(fraction)
                },
                onScrubEnd = { fraction -> commitScrub(fraction) },
            )
        }
    }
}

/** 持有当前正在播放的播放器，供进度条拖动时 seek 用。 */
private class PlayerHandle {
    var player: ExoPlayer? = null
}

/** 图文作品没有视频容器，单独播放解析时保存的作品音轨。 */
@Composable
private fun WorkAudio(
    file: File,
    playing: Boolean,
) {
    val context = LocalContext.current
    val player = remember(file) { ExoPlayer.Builder(context).build() }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(player, file) {
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.prepare()
    }

    LaunchedEffect(player, playing) {
        if (playing) player.play() else player.pause()
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoStage(
    file: File,
    durationMs: Long,
    initialFraction: Float,
    playing: Boolean,
    handle: PlayerHandle,
    onProgress: (fraction: Float) -> Unit,
    onCompleted: () -> Unit,
    onInitialSeekApplied: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(file) { ExoPlayer.Builder(context).build() }
    val startFraction = remember(file) { initialFraction }
    val progressReporter = rememberUpdatedState(onProgress)
    val completionReporter = rememberUpdatedState(onCompleted)
    val seekAppliedReporter = rememberUpdatedState(onInitialSeekApplied)

    DisposableEffect(player) {
        handle.player = player
        onDispose {
            if (handle.player === player) handle.player = null
            player.release()
        }
    }

    LaunchedEffect(player, file) {
        val startMs = if (startFraction > 0f && durationMs > 0L) {
            (durationMs * startFraction).toLong()
        } else {
            0L
        }
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)), startMs)
        player.prepare()
        seekAppliedReporter.value()
    }

    LaunchedEffect(player, playing) {
        if (playing) player.play() else player.pause()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) completionReporter.value()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            val duration = player.duration
            val fraction = if (duration > 0L) {
                (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            }
            progressReporter.value(fraction)
            delay(PROGRESS_POLL_MS)
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun ImagePage(
    file: File,
    active: Boolean,
    playing: Boolean,
    loop: Boolean,
    onProgress: (fraction: Float) -> Unit,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var elapsedNanos by remember(file) { mutableLongStateOf(0L) }
    val progressReporter = rememberUpdatedState(onProgress)
    val completionReporter = rememberUpdatedState(onCompleted)

    // 每次翻到这张图都从开头重新计时，和视频页切回来时从 0 开始保持一致。
    LaunchedEffect(file, active) {
        if (active) elapsedNanos = 0L
    }

    // 图片进度就是这一张图自己的展示时间，从开头走到结尾；暂停时保留已走时长。
    LaunchedEffect(file, playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            elapsedNanos = (elapsedNanos + now - last).coerceAtMost(IMAGE_DURATION_NANOS)
            last = now
            if (elapsedNanos >= IMAGE_DURATION_NANOS) {
                elapsedNanos = 0L
                if (!loop) {
                    completionReporter.value()
                    break
                }
            }
        }
    }

    LaunchedEffect(file, playing) {
        if (!playing) return@LaunchedEffect
        while (true) {
            progressReporter.value(
                (elapsedNanos.toFloat() / IMAGE_DURATION_NANOS).coerceIn(0f, 1f),
            )
            delay(PROGRESS_POLL_MS)
        }
    }

    AsyncImage(
        model = file,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

/** 进度条触摸区域高度（进度条本身只有 3dp）。 */
private val PROGRESS_TOUCH_HEIGHT = 40.dp

private fun formatProgressTime(millis: Long): String {
    val seconds = (millis / 1000L).coerceAtLeast(0L).toInt()
    val minutes = seconds / 60
    return "$minutes:${(seconds % 60).toString().padStart(2, '0')}"
}

private fun formatWorkDate(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))

/**
 * 分段进度条：按素材数量分格，每格展示该素材的播放进度。
 *
 * 和抖音一样，手指落在进度条上就由进度条接管整个手势（上下左右都消费掉），
 * 这样拖动时页面不会跟着竖滑。视频段可以拖动 seek；图片段只展示从开头到结尾的
 * 自动进度，拖动不会改变图片的播放位置。
 */
@Composable
private fun WorkProgressBar(
    segmentCount: Int,
    totalFraction: Float,
    scrubbing: Boolean,
    timeLabel: String?,
    onScrubStart: (fraction: Float) -> Unit,
    onScrub: (fraction: Float) -> Unit,
    onScrubEnd: (fraction: Float) -> Unit,
) {
    val startReporter = rememberUpdatedState(onScrubStart)
    val scrubReporter = rememberUpdatedState(onScrub)
    val endReporter = rememberUpdatedState(onScrubEnd)
    var barWidthPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PROGRESS_TOUCH_HEIGHT)
            .onSizeChanged { barWidthPx = it.width }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val totalWidth = size.width.toFloat().coerceAtLeast(1f)
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 一旦按在进度条上就立刻消费事件，父级的竖滑/横滑都不再响应。
                    down.consume()
                    var fraction = (down.position.x / totalWidth).coerceIn(0f, 1f)
                    startReporter.value(fraction)
                    scrubReporter.value(fraction)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        fraction = (change.position.x / totalWidth).coerceIn(0f, 1f)
                        scrubReporter.value(fraction)
                        change.consume()
                        if (!change.pressed) {
                            endReporter.value(fraction)
                            break
                        }
                    }
                }
            },
        contentAlignment = Alignment.BottomStart,
    ) {
        val count = segmentCount.coerceAtLeast(1)
        val total = totalFraction.coerceIn(0f, 1f)
        val played = total * count

        if (scrubbing && timeLabel != null) {
            Text(
                text = timeLabel,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            repeat(count) { index ->
                val fill = (played - index).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fill)
                            .background(Color.White),
                    )
                }
            }
        }

        if (scrubbing) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset {
                        val thumbPx = 10.dp.roundToPx()
                        IntOffset(
                            x = (total * (barWidthPx - thumbPx).coerceAtLeast(0)).roundToInt(),
                            y = 3.5.dp.roundToPx(),
                        )
                    }
                    .size(10.dp)
                    .background(Color.White, CircleShape),
            )
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                ),
            )
            .statusBarsPadding()
            .padding(start = 4.dp, top = 4.dp, bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun EmptyFeed(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        TopBar(onBack = onBack)
        Text(
            text = stringResource(R.string.gallery_empty),
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
