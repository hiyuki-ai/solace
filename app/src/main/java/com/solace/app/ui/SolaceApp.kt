package com.solace.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.solace.app.feature.creation.CreationScreen
import com.solace.app.feature.douyin.DouyinImportHandoff
import com.solace.app.feature.douyin.DouyinImportScreen
import com.solace.app.feature.gallery.GalleryScreen
import com.solace.app.feature.profile.ProfileScreen
import com.solace.app.feature.work.WorkDetailScreen
import com.solace.app.ui.navigation.SolaceDestination
import com.solace.app.ui.screens.library.FolderScreen
import com.solace.app.ui.screens.library.LibraryScreen
import com.solace.app.ui.screens.player.PlayerScreen

private const val ROUTE_FOLDER = "folder/{bucketId}"
private const val ROUTE_PLAYER = "player/{bucketId}/{videoId}"
private const val ROUTE_CREATION = "creation?draftId={draftId}"
private const val ROUTE_WORK = "work/{workId}"
private const val ROUTE_DOUYIN_IMPORT = "douyin-import"

private const val ARG_BUCKET_ID = "bucketId"
private const val ARG_VIDEO_ID = "videoId"
private const val ARG_DRAFT_ID = "draftId"
private const val ARG_WORK_ID = "workId"

@Composable
fun SolaceApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val backStack by navController.currentBackStack.collectAsState()

    val isTabRoute = SolaceDestination.entries.any { it.route == currentRoute }
    // 文件夹与作品浏览页也保留底部导航栏，不做全屏，避免进度条和系统手势区打架。
    val showBottomBar = isTabRoute || currentRoute == ROUTE_FOLDER || currentRoute == ROUTE_WORK
    val isPlaybackRoute = currentRoute == ROUTE_WORK

    // 子页面（文件夹 / 作品）没有自己的 tab，高亮停留在进入它之前的那个 tab。
    val selectedTabRoute = backStack
        .lastOrNull { entry ->
            SolaceDestination.entries.any { it.route == entry.destination.route }
        }
        ?.destination
        ?.route

    // 播放作品时是深色画面，其余页面是亮色，系统栏图标跟着切换。
    SystemBarAppearance(darkBackground = isPlaybackRoute || currentRoute == ROUTE_PLAYER)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                SolaceBottomBar(
                    selectedRoute = selectedTabRoute,
                    dark = isPlaybackRoute,
                    onSelect = { destination ->
                        if (currentRoute != destination.route) {
                            // 在文件夹 / 作品等子页面点所属 tab 时直接回退，
                            // 否则 navigate + restoreState 会把刚离开的详情页又恢复出来。
                            val belongsToCurrentTab = !isTabRoute &&
                                selectedTabRoute == destination.route
                            if (belongsToCurrentTab) {
                                navController.popBackStack(destination.route, inclusive = false)
                            } else {
                                navController.navigate(destination.route) {
                                    // 切换 tab 时保留各页面状态，且不堆积返回栈。
                                    popUpTo(
                                        navController.graph.findStartDestination().id,
                                    ) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = SolaceDestination.Library.route,
            modifier = Modifier.fillMaxSize(),
            // 页面切换不做转场动画，避免新旧页面同时组合带来的掉帧。
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(SolaceDestination.Library.route) {
                LibraryScreen(
                    onOpenFolder = { bucketId -> navController.navigate("folder/$bucketId") },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            composable(SolaceDestination.Gallery.route) {
                GalleryScreen(
                    onOpenWork = { workId -> navController.navigate("work/$workId") },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            composable(SolaceDestination.Profile.route) {
                ProfileScreen(
                    onCreate = { navController.navigate("creation") },
                    onOpenDraft = { workId ->
                        navController.navigate("creation?draftId=$workId")
                    },
                    onOpenWork = { workId -> navController.navigate("work/$workId") },
                    onDouyinParsed = { work ->
                        DouyinImportHandoff.put(work)
                        navController.navigate(ROUTE_DOUYIN_IMPORT)
                    },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            composable(
                route = ROUTE_FOLDER,
                arguments = listOf(navArgument(ARG_BUCKET_ID) { type = NavType.LongType }),
            ) { entry ->
                val bucketId = entry.arguments?.getLong(ARG_BUCKET_ID) ?: 0L
                FolderScreen(
                    bucketId = bucketId,
                    onBack = { navController.popBackStack() },
                    onOpenVideo = { videoId ->
                        navController.navigate("player/$bucketId/$videoId")
                    },
                    modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                )
            }
            composable(
                route = ROUTE_PLAYER,
                arguments = listOf(
                    navArgument(ARG_BUCKET_ID) { type = NavType.LongType },
                    navArgument(ARG_VIDEO_ID) { type = NavType.LongType },
                ),
            ) { entry ->
                PlayerScreen(
                    bucketId = entry.arguments?.getLong(ARG_BUCKET_ID) ?: 0L,
                    videoId = entry.arguments?.getLong(ARG_VIDEO_ID) ?: 0L,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ROUTE_CREATION,
                arguments = listOf(
                    navArgument(ARG_DRAFT_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                CreationScreen(
                    draftId = entry.arguments?.getString(ARG_DRAFT_ID),
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(ROUTE_DOUYIN_IMPORT) {
                DouyinImportScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                route = ROUTE_WORK,
                arguments = listOf(navArgument(ARG_WORK_ID) { type = NavType.StringType }),
            ) { entry ->
                WorkDetailScreen(
                    workId = entry.arguments?.getString(ARG_WORK_ID).orEmpty(),
                    onBack = { navController.popBackStack() },
                    // 顶部仍然全屏绘制，底部让出导航栏的高度。
                    modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                )
            }
        }
    }
}

/**
 * 纯文字底部导航：默认亮色，只有浏览 / 播放作品时切成暗色（参考抖音），
 * 亮色底用深色文字，暗色底用浅色文字。
 */
@Composable
private fun SolaceBottomBar(
    selectedRoute: String?,
    dark: Boolean,
    onSelect: (SolaceDestination) -> Unit,
) {
    val colors = if (dark) DarkBottomBarColors else LightBottomBarColors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.container),
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = colors.divider,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SolaceDestination.entries.forEach { destination ->
                val selected = selectedRoute == destination.route
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(destination) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(destination.labelRes),
                        color = if (selected) colors.selectedContent else colors.unselectedContent,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/**
 * 系统栏图标跟随页面底色：深色页面（播放 / 浏览作品）用浅色图标，其余页面用深色图标。
 */
@Composable
private fun SystemBarAppearance(darkBackground: Boolean) {
    val view = LocalView.current
    val window = remember(view) { view.context.findActivity()?.window }
    DisposableEffect(window, view, darkBackground) {
        if (window == null) return@DisposableEffect onDispose {}
        val controller = WindowInsetsControllerCompat(window, view)
        val previousLightStatusBars = controller.isAppearanceLightStatusBars
        val previousLightNavigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = !darkBackground
        controller.isAppearanceLightNavigationBars = !darkBackground
        onDispose {
            controller.isAppearanceLightStatusBars = previousLightStatusBars
            controller.isAppearanceLightNavigationBars = previousLightNavigationBars
        }
    }
}

private data class BottomBarColors(
    /** 导航栏背景。 */
    val container: Color,
    /** 选中项文字颜色。 */
    val selectedContent: Color,
    /** 未选中项文字颜色。 */
    val unselectedContent: Color,
    /** 顶部分割线颜色。 */
    val divider: Color,
)

/**
 * 亮色导航栏：跟随页面底色，避免底部横条比页面更白、显得分层。
 */
private val LightBottomBarColors: BottomBarColors
    @Composable get() = BottomBarColors(
        container = MaterialTheme.colorScheme.background,
        selectedContent = Color(0xFF161823),
        unselectedContent = Color(0x99161823),
        divider = Color(0x14161823),
    )

/** 作品播放时的暗色导航栏：深灰而不是纯黑，避免整块屏幕死黑。 */
private val DarkBottomBarColors = BottomBarColors(
    container = Color(0xFF1E1E22),
    selectedContent = Color.White,
    unselectedContent = Color(0xB3FFFFFF),
    divider = Color(0x1AFFFFFF),
)

private fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
