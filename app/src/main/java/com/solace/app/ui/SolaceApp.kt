package com.solace.app.ui

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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.solace.app.ui.navigation.SolaceDestination
import com.solace.app.ui.screens.GalleryScreen
import com.solace.app.ui.screens.LibraryScreen
import com.solace.app.ui.screens.ProfileScreen

@Composable
fun SolaceApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                SolaceDestination.entries.forEach { destination ->
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(destination.route) {
                                    // 切换 tab 时保留各页面状态，且不堆积返回栈。
                                    popUpTo(navController.graph.findStartDestination().id) {
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
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = SolaceDestination.Library.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(SolaceDestination.Library.route) { LibraryScreen() }
            composable(SolaceDestination.Gallery.route) { GalleryScreen() }
            composable(SolaceDestination.Profile.route) { ProfileScreen() }
        }
    }
}
