package com.solace.app.feature.category

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.solace.app.R
import com.solace.app.data.work.UserEntity
import com.solace.app.data.work.WorkRepository
import kotlinx.coroutines.launch

@Composable
fun CustomUserEditorDialog(
    user: UserEntity?,
    repository: WorkRepository,
    onDismiss: () -> Unit,
    onSaved: (UserEntity) -> Unit,
) {
    var nickname by remember(user?.id) { mutableStateOf(user?.nickname.orEmpty()) }
    var customId by remember(user?.id) { mutableStateOf(user?.uniqueId.orEmpty()) }
    var selectedAvatar by remember(user?.id) { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) selectedAvatar = uri
    }
    val existingAvatar = remember(user?.avatarFile) {
        repository.userAvatarFile(user?.avatarFile)
    } ?: user?.avatarUrl
    val avatarModel = selectedAvatar ?: existingAvatar
    val fallback = nickname.trim().firstOrNull()?.toString().orEmpty()
    val saveFailedMessage = stringResource(R.string.user_editor_save_failed)

    fun pickAvatar() {
        avatarPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    fun save() {
        if (saving) return
        saving = true
        error = null
        scope.launch {
            try {
                val saved = if (user == null) {
                    repository.createCustomUser(
                        nickname = nickname,
                        customId = customId,
                        avatarUri = selectedAvatar,
                    )
                } else {
                    repository.updateCustomUser(
                        userId = user.id,
                        nickname = nickname,
                        customId = customId,
                        avatarUri = selectedAvatar,
                    )
                }
                onSaved(saved)
            } catch (e: Exception) {
                error = e.message ?: saveFailedMessage
            } finally {
                saving = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Text(
                text = stringResource(
                    if (user == null) R.string.user_editor_create_title
                    else R.string.user_editor_edit_title,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { pickAvatar() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarModel != null) {
                        AsyncImage(
                            model = avatarModel,
                            contentDescription = stringResource(R.string.user_editor_avatar),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = fallback,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                }
                TextButton(onClick = ::pickAvatar) {
                    Text(text = stringResource(R.string.user_editor_choose_avatar))
                }
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.user_editor_nickname)) },
                    placeholder = { Text(text = stringResource(R.string.user_editor_nickname_hint)) },
                )
                OutlinedTextField(
                    value = customId,
                    onValueChange = { customId = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.user_editor_id)) },
                    placeholder = { Text(text = stringResource(R.string.user_editor_id_hint)) },
                )
                error?.let {
                    Text(
                        text = it,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::save, enabled = !saving) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(text = stringResource(R.string.user_editor_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}
