package com.solace.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.solace.app.R
import com.solace.app.ui.components.PlaceholderScreen

@Composable
fun GalleryScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.tab_gallery),
        subtitle = stringResource(R.string.gallery_placeholder),
        modifier = modifier,
    )
}
