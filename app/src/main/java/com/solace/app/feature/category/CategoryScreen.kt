package com.solace.app.feature.category

import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.solace.app.R
import com.solace.app.data.work.UserEntity
import com.solace.app.data.work.UserKind
import com.solace.app.data.work.UserWithWorkCount
import com.solace.app.data.work.WorkRepository
import com.solace.app.feature.profile.WorkCard

private enum class CategoryTab(@param:StringRes val labelRes: Int) {
    Users(R.string.category_user_tab),
}

private enum class UserSort(@param:StringRes val labelRes: Int) {
    Name(R.string.category_sort_name),
    WorkCount(R.string.category_sort_work_count),
}

/**
 * 分类页。当前只有「用户」Tab，后续可以在 [CategoryTab] 中继续扩展。
 */
@Composable
fun CategoryScreen(
    onOpenWork: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
            CategoryTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(text = stringResource(tab.labelRes)) },
                )
            }
        }
        when (CategoryTab.entries[selectedTabIndex]) {
            CategoryTab.Users -> UserCategory(
                onOpenWork = onOpenWork,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun UserCategory(
    onOpenWork: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) { WorkRepository(context) }
    val users by repository.observeUsers().collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var sortIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedUserId by rememberSaveable { mutableStateOf<Long?>(null) }
    var createUserVisible by rememberSaveable { mutableStateOf(false) }
    var editingUserId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedUser = selectedUserId?.let { id -> users.firstOrNull { it.user.id == id } }
    val editingUser = editingUserId?.let { id -> users.firstOrNull { it.user.id == id }?.user }

    BackHandler(enabled = selectedUserId != null && editingUserId == null) {
        selectedUserId = null
    }

    if (selectedUser != null) {
        UserWorks(
            user = selectedUser.user,
            repository = repository,
            onOpenWork = onOpenWork,
            onEditUser = { editingUserId = it.id },
            modifier = modifier,
        )
    } else {
        UserDirectory(
            users = users,
            query = query,
            searchActive = searchActive,
            sort = UserSort.entries[sortIndex],
            onQueryChange = { query = it },
            onSearchActiveChange = { searchActive = it },
            onSortChange = { sortIndex = it.ordinal },
            onCreateUser = { createUserVisible = true },
            onSelectUser = {
                searchActive = false
                query = ""
                selectedUserId = it.user.id
            },
            repository = repository,
            modifier = modifier,
        )
    }

    if (createUserVisible || editingUser != null) {
        CustomUserEditorDialog(
            user = editingUser,
            repository = repository,
            onDismiss = {
                createUserVisible = false
                editingUserId = null
            },
            onSaved = {
                createUserVisible = false
                editingUserId = null
            },
        )
    }
}

/** 独立用户详情路由，供作品播放页等入口复用。 */
@Composable
fun UserDetailScreen(
    userId: Long,
    onOpenWork: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) { WorkRepository(context) }
    val user by repository.observeUser(userId).collectAsState(initial = null)
    var editingUserId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editingUser = user?.takeIf { it.id == editingUserId }

    Box(modifier = modifier.fillMaxSize()) {
        val current = user
        when {
            current != null -> UserWorks(
                user = current,
                repository = repository,
                onOpenWork = onOpenWork,
                onEditUser = { editingUserId = it.id },
            )

            else -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }

    if (editingUser != null) {
        CustomUserEditorDialog(
            user = editingUser,
            repository = repository,
            onDismiss = { editingUserId = null },
            onSaved = { editingUserId = null },
        )
    }
}

@Composable
private fun UserDirectory(
    users: List<UserWithWorkCount>,
    query: String,
    searchActive: Boolean,
    sort: UserSort,
    onQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSortChange: (UserSort) -> Unit,
    onCreateUser: () -> Unit,
    onSelectUser: (UserWithWorkCount) -> Unit,
    repository: WorkRepository,
    modifier: Modifier = Modifier,
) {
    val filteredUsers = remember(users, query) {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            users
        } else {
            users.filter { item ->
                val user = item.user
                user.nickname?.contains(keyword, ignoreCase = true) == true ||
                    user.uniqueId?.contains(keyword, ignoreCase = true) == true ||
                    user.uid?.contains(keyword, ignoreCase = true) == true ||
                    user.secUid?.contains(keyword, ignoreCase = true) == true
            }
        }
    }
    val displayedUsers = remember(filteredUsers, sort) {
        when (sort) {
            UserSort.Name -> filteredUsers.sortedWith(
                compareBy(
                    { it.user.nickname.isNullOrBlank() },
                    { it.user.nickname.orEmpty().lowercase() },
                ),
            )

            UserSort.WorkCount -> filteredUsers.sortedWith(
                compareByDescending<UserWithWorkCount> { it.workCount }
                    .thenBy { it.user.nickname.orEmpty().lowercase() },
            )
        }
    }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    fun closeSearch() {
        onQueryChange("")
        onSearchActiveChange(false)
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    LaunchedEffect(searchActive) {
        if (searchActive) {
            withFrameNanos { }
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    BackHandler(enabled = searchActive) {
        closeSearch()
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (searchActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 10.dp, end = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text(text = stringResource(R.string.category_search_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.category_clear_search),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { keyboardController?.hide() },
                    ),
                )
                IconButton(onClick = ::closeSearch) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.category_close_search),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 6.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UserSort.entries.forEach { option ->
                FilterChip(
                    selected = sort == option,
                    onClick = { onSortChange(option) },
                    label = { Text(text = stringResource(option.labelRes)) },
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (!searchActive) {
                FilledTonalIconButton(
                    onClick = { onSearchActiveChange(true) },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.category_open_search),
                    )
                }
                FilledTonalIconButton(onClick = onCreateUser) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.user_editor_add),
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                users.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    CategoryEmptyMessage(stringResource(R.string.category_empty_users))
                }

                displayedUsers.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    CategoryEmptyMessage(stringResource(R.string.category_empty_search))
                }

                else -> items(items = displayedUsers, key = { it.user.id }) { item ->
                    UserCard(
                        item = item,
                        repository = repository,
                        onClick = { onSelectUser(item) },
                    )
                }
            }
        }
    }

}

@Composable
private fun UserCard(
    item: UserWithWorkCount,
    repository: WorkRepository,
    onClick: () -> Unit,
) {
    val user = item.user
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.category_user_card_work_count, item.workCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 6.dp, top = 16.dp, end = 6.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                UserAvatar(
                    user = user,
                    repository = repository,
                    size = 60,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = user.nickname
                        ?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.category_user_unnamed),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun UserWorks(
    user: UserEntity,
    repository: WorkRepository,
    onOpenWork: (String) -> Unit,
    onEditUser: (UserEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val works by remember(user.id) {
        repository.observeUserWorks(user.id)
    }.collectAsState(initial = emptyList())

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        UserProfileHeader(
            user = user,
            repository = repository,
            onEdit = if (user.kind == UserKind.CUSTOM) {
                { onEditUser(user) }
            } else {
                null
            },
        )
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.category_works_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = works.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (works.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CategoryEmptyMessage(stringResource(R.string.category_empty_user_works))
                }
            } else {
                items(items = works, key = { it.work.id }) { work ->
                    WorkCard(
                        work = work,
                        repository = repository,
                        badge = null,
                        onClick = { onOpenWork(work.work.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UserProfileHeader(
    user: UserEntity,
    repository: WorkRepository,
    onEdit: (() -> Unit)?,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 56.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UserAvatar(
                user = user,
                repository = repository,
                size = 80,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = user.nickname
                        ?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.category_user_unnamed),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.category_user_detail_id,
                        userPrimaryId(user),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onEdit != null) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.user_editor_edit),
                )
            }
        }
    }
}

@Composable
private fun UserAvatar(
    user: UserEntity,
    repository: WorkRepository,
    size: Int,
) {
    val model = remember(user.avatarFile) {
        repository.userAvatarFile(user.avatarFile)
    } ?: user.avatarUrl
    val fallback = user.nickname?.trim()?.firstOrNull()?.toString().orEmpty()

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = fallback,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private fun userPrimaryId(user: UserEntity): String =
    user.uniqueId?.takeIf { it.isNotBlank() }
        ?: user.uid?.takeIf { it.isNotBlank() }
        ?: user.secUid?.takeIf { it.isNotBlank() }
        ?: user.id.toString()

@Composable
private fun CategoryEmptyMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
