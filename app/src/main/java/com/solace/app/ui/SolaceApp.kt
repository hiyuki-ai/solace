package com.solace.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.solace.app.ui.navigation.SolaceDestination
import com.solace.app.ui.screens.GalleryScreen
import com.solace.app.ui.screens.ProfileScreen
import com.solace.app.ui.screens.library.FolderScreen
import com.solace.app.ui.screens.library.LibraryScreen
import com.solace.app.ui.screens.player.PlayerScreen

private const val ROUTE_FOLDER = "folder/{bucketId}"
private const val ROUTE_PLAYER = "player/{bucketId}/{videoId}"

private const val ARG_BUCKET_ID = "bucketId"
private const val ARG_VIDEO_ID = "videoId"

@Composable
fun SolaceApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTabRoute = SolaceDestination.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (isTabRoute) {
                NavigationBar {
                    SolaceDestination.entries.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
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
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) {
                                        destination.selectedIcon
                                    } else {
                                        destination.unselectedIcon
                                    },
                                    contentDescription = stringResource(destination.labelRes),
                                )
                            },
                            label = {
                                Text(text = stringResource(destination.labelRes))
                            },
                        )
                    }
                }
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
                GalleryScreen(modifier = Modifier.padding(innerPadding))
            }
            composable(SolaceDestination.Profile.route) {
                ProfileScreen(modifier = Modifier.padding(innerPadding))
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
        }
    }
}
