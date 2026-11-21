package com.solace.app.ui.navigation

import androidx.annotation.StringRes
import com.solace.app.R

/**
 * 底部导航的三个一级页面。
 * 只显示文字，不用图标。
 */
enum class SolaceDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
) {
    Library(
        route = "library",
        labelRes = R.string.tab_library,
    ),
    Gallery(
        route = "gallery",
        labelRes = R.string.tab_gallery,
    ),
    Profile(
        route = "profile",
        labelRes = R.string.tab_profile,
    ),
}
