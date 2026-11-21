package com.solace.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.solace.app.R
import com.solace.app.ui.components.PlaceholderScreen

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.tab_profile),
        subtitle = stringResource(R.string.profile_placeholder),
        modifier = modifier,
    )
}
