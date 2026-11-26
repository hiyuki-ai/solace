package com.solace.app.feature.douyin

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.solace.app.R
import com.solace.app.data.douyin.DouyinWork

/**
 * 复制抖音作品的第一步：粘贴分享链接并解析。
 */
@Composable
fun DouyinLinkDialog(
    onDismiss: () -> Unit,
    onParsed: (DouyinWork) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: DouyinParseViewModel = viewModel()
    val state = viewModel.uiState

    LaunchedEffect(state.result) {
        state.result?.let { work ->
            viewModel.clearResult()
            viewModel.clearLink()
            onParsed(work)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!state.parsing) onDismiss() },
        title = { Text(text = stringResource(R.string.douyin_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.link,
                    onValueChange = viewModel::onLinkChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = stringResource(R.string.douyin_dialog_hint)) },
                    minLines = 3,
                    maxLines = 5,
                    enabled = !state.parsing,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = viewModel::clearLink,
                        enabled = state.link.isNotEmpty() && !state.parsing,
                    ) {
                        Text(text = stringResource(R.string.douyin_dialog_clear))
                    }
                    TextButton(
                        onClick = {
                            viewModel.fillFromClipboard(clipboardText(context))
                        },
                        enabled = !state.parsing,
                    ) {
                        Text(text = stringResource(R.string.douyin_dialog_fill))
                    }
                }
                Text(
                    text = stringResource(R.string.douyin_dialog_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = viewModel::parse,
                enabled = state.link.isNotBlank() && !state.parsing,
            ) {
                if (state.parsing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(text = stringResource(R.string.douyin_dialog_parse))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.parsing) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun clipboardText(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0)?.coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}
