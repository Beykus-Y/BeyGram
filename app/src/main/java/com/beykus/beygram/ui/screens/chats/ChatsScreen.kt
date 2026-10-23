package com.beykus.beygram.ui.screens.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.ListDensity
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.TelegramChatFolders
import com.beykus.beygram.domain.model.TelegramFolderLoadState
import com.beykus.beygram.domain.model.TelegramFolderTab
import com.beykus.beygram.domain.model.orderedTelegramFolderTabs
import com.beykus.beygram.domain.model.validTelegramFolderSelection
import com.beykus.beygram.ui.components.Avatar
import com.beykus.beygram.ui.components.ChatAvatar
import com.beykus.beygram.ui.components.SearchField
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.domain.model.ProfileInfo
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.ui.text.resolve
import com.beykus.beygram.ui.text.russianPlural
import kotlin.math.min
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.beykus.beygram.ui.components.beyGramPalette

private enum class ChatFilter(val title: Int) {
    All(R.string.chats_filter_all), People(R.string.chats_filter_people), Groups(R.string.chats_filter_groups), Unread(R.string.chats_filter_unread)
}

/** Сколько протянуть список вниз, чтобы вернуть скрытый архив; подсказка занимает столько же. */
private val archivePullThreshold = 104.dp

@Composable
fun ChatsScreen(
    chats: List<ChatPreview>,
    archivedChats: List<ChatPreview>,
    /** Per-account TDLib server folders and their own ordered content. */
    folderSnapshot: TelegramChatFolders,
    folderChats: Map<Int, List<ChatPreview>>,
    folderLoadStates: Map<Int, TelegramFolderLoadState>,
    onFolderLoadMore: (Int) -> Unit,
    onPinFolderChat: suspend (Int, Long, Boolean) -> Result<Unit>,
    onLoadFolder: suspend (Int) -> Result<com.beykus.beygram.domain.model.TelegramFolderDraft>,
    onSaveFolder: suspend (com.beykus.beygram.domain.model.TelegramFolderDraft) -> Result<Int>,
    onDeleteFolder: suspend (Int) -> Result<Unit>,
    onReorderFolders: suspend (List<Int>, Int) -> Result<Unit>,
    myProfile: ProfileInfo?,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onMyProfile: () -> Unit,
    onAccountPicker: () -> Unit = {},
    onArchiveClick: () -> Unit,
    onMarkRead: suspend (ChatPreview) -> Boolean,
    onMarkUnread: suspend (ChatPreview) -> Result<Unit>,
    onArchive: suspend (ChatPreview) -> Boolean,
    onPinChat: suspend (Long, Boolean) -> Result<Unit>,
    onMuteChat: suspend (Long, Boolean) -> Result<Unit>,
    onDeleteChat: suspend (Long) -> Result<Unit>,
    onNewChat: () -> Unit,
    archiveHidden: Boolean,
    onArchiveHiddenChange: (Boolean) -> Unit,
    onChatClick: (ChatPreview) -> Unit,
    onGlobalSearch: (String) -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Server folders replace the old local All / People / Groups / Unread chips.
    // Applying local filtering again beneath server folders gave users two competing tab bars.
    val selectedFilter = ChatFilter.All
    var managingFolders by rememberSaveable { mutableStateOf(false) }
    // A folder is selected by server ID, never by its mutable index or local category name.
    var selectedFolderId by rememberSaveable(myProfile?.id) { mutableIntStateOf(0) }
    val activeFolderId = validTelegramFolderSelection(folderSnapshot, selectedFolderId.takeIf { it > 0 })
    LaunchedEffect(folderSnapshot.initialized, folderSnapshot.folders.map { it.id }) {
        if (folderSnapshot.initialized && selectedFolderId != 0 && activeFolderId == null) selectedFolderId = 0
    }
    val displayedChats = activeFolderId?.let { folderChats[it].orEmpty() } ?: chats
    val folderLoad = activeFolderId?.let { folderLoadStates[it] } ?: TelegramFolderLoadState(hasMore = false)
    val palette = beyGramPalette()
    val context = LocalContext.current
    var openSwipeChatId by remember { mutableStateOf<Long?>(null) }
    var selectedActions by remember { mutableStateOf<ChatPreview?>(null) }
    val readAction = ChatSwipeAction(
        label = stringResource(R.string.chats_action_read), icon = Icons.Rounded.DoneAll, container = palette.surface2, content = palette.accentInk,
        enabled = true, perform = onMarkRead
    )
    val archiveAction = ChatSwipeAction(
        label = stringResource(R.string.chats_action_archive), icon = Icons.Rounded.Archive, container = palette.accent, content = palette.onAccent,
        enabled = true, perform = onArchive
    )
    val filteredChats = remember(query, displayedChats, selectedFilter) {
        displayedChats.filter { chat ->
            val matchesQuery = query.isBlank() || chat.title.contains(query, true) || (chat.lastMessage as? UiText.Raw)?.text?.contains(query, true) == true
            val matchesFilter = when (selectedFilter) {
                ChatFilter.All -> true
                ChatFilter.Unread -> chat.unreadCount > 0
                ChatFilter.People -> !chat.isGroup
                ChatFilter.Groups -> chat.isGroup
            }
            matchesQuery && matchesFilter
        }
    }
    val pinnedChats = remember(filteredChats, selectedFilter, query) {
        if (selectedFilter == ChatFilter.All && query.isBlank()) filteredChats.filter { it.isPinned } else emptyList()
    }
    val regularChats = remember(filteredChats, pinnedChats) { filteredChats.filterNot { it in pinnedChats } }
    val unreadTotal = remember(displayedChats) { displayedChats.sumOf { it.unreadCount } }
    val archiveAvailable = activeFolderId == null && archivedChats.isNotEmpty() && selectedFilter == ChatFilter.All && query.isBlank()
    val density = LocalDensity.current
    val listState = rememberSaveable(activeFolderId, saver = LazyListState.Saver) { LazyListState() }
    val archivePullThresholdPx = with(density) { archivePullThreshold.toPx() }
    // Сколько протянуто вниз, пока архив скрыт. Подсказка растёт до порога, а за порогом архив возвращается при отпускании.
    var pullPx by remember { mutableFloatStateOf(0f) }
    val pullCanReveal by rememberUpdatedState(archiveAvailable && archiveHidden)
    val latestOnArchiveHiddenChange by rememberUpdatedState(onArchiveHiddenChange)
    val pullConnection = remember(listState, archivePullThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Архив мог исчезнуть или вернуться посреди протяжки: без скрытого архива протяжки быть не должно.
                if (!pullCanReveal) pullPx = 0f
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                return when {
                    available.y > 0f && pullCanReveal && atTop -> {
                        pullPx = minOf(pullPx + available.y, archivePullThresholdPx)
                        Offset(0f, available.y)
                    }
                    available.y < 0f && pullPx > 0f -> {
                        val back = minOf(-available.y, pullPx)
                        pullPx -= back
                        Offset(0f, -back)
                    }
                    else -> Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullPx <= 0f) return Velocity.Zero
                val reveal = pullPx >= archivePullThresholdPx
                pullPx = 0f
                if (reveal) latestOnArchiveHiddenChange(false)
                return available
            }
        }
    }
    // Если жест прервали системой, onPreFling не придёт: сбрасываем протяжку, когда список перестал скроллиться.
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) pullPx = 0f
    }

    if (managingFolders) {
        FolderManagerScreen(
            snapshot = folderSnapshot,
            knownChats = (chats + archivedChats + folderChats.values.flatten()).distinctBy { it.id }
                .sortedBy { it.title.lowercase() },
            loadFolder = onLoadFolder,
            saveFolder = onSaveFolder,
            deleteFolder = onDeleteFolder,
            reorderFolders = onReorderFolders,
            onBack = { managingFolders = false }
        )
        return
    }

    // The root Box is bounded by MainTabsScaffold above the bottom navigation and
    // persistent player. Its FAB is never drawn under the system navigation bar.
    Box(
        modifier = Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    ) {
      Column(Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name).uppercase(), color = palette.accent, style = MaterialTheme.typography.labelMedium, letterSpacing = 0.8.sp)
                    Text(stringResource(R.string.chats_title), color = palette.textPrimary, fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                }
                (chats + archivedChats).firstOrNull { it.isSavedMessages }?.let { saved ->
                    androidx.compose.material3.IconButton(onClick = { onChatClick(saved) }, modifier = Modifier.semantics { contentDescription = context.getString(R.string.saved_messages_open_description) }) {
                        Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(24.dp))
                    }
                }
                androidx.compose.material3.IconButton(onClick = onAccountPicker) {
                    Icon(Icons.Rounded.People,
                        contentDescription = stringResource(R.string.secondary_accounts_switch),
                        tint = palette.textSecondary, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(4.dp))
                TelegramAvatar(myProfile?.initials ?: stringResource(R.string.chats_avatar_fallback), palette.accent, 40.dp, myProfile?.photo, observeFile, downloadFile,
                    Modifier.semantics { contentDescription = context.getString(R.string.chats_my_profile_description) }.clickable(onClick = onMyProfile))
            }

            Spacer(Modifier.height(14.dp))
            SearchField(query = query, onChange = { query = it }, placeholder = stringResource(R.string.chats_search_placeholder))
            Spacer(Modifier.height(12.dp))
            // Only one source of folder navigation: server-backed Telegram lists.
            // Main ('Все чаты') is a real tab even when the account has no custom folders yet.
            if (folderSnapshot.initialized) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val serverTabs = orderedTelegramFolderTabs(folderSnapshot)
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(serverTabs, key = { tab ->
                            when (tab) {
                                TelegramFolderTab.Main -> "server_main"
                                is TelegramFolderTab.Folder -> "server_folder_${tab.id}"
                            }
                        }) { tab ->
                            when (tab) {
                                TelegramFolderTab.Main -> ServerFolderChip(
                                    title = stringResource(R.string.chat_folders_all),
                                    selected = activeFolderId == null,
                                    unreadCount = null,
                                    onClick = { selectedFolderId = 0 }
                                )
                                is TelegramFolderTab.Folder -> {
                                    val folder = folderSnapshot.folders.firstOrNull { it.id == tab.id }
                                    if (folder != null) ServerFolderChip(
                                        title = folder.title,
                                        selected = activeFolderId == folder.id,
                                        unreadCount = folder.unreadChats,
                                        onClick = { selectedFolderId = folder.id }
                                    )
                                }
                            }
                        }
                    }
                    androidx.compose.material3.IconButton(onClick = { managingFolders = true }) {
                        Icon(Icons.Rounded.Tune,
                            contentDescription = stringResource(R.string.folder_manage_title),
                            tint = palette.textSecondary)
                    }
                }
            }
        }

        ArchivePullSlot(shownPx = { if (archiveAvailable && archiveHidden) min(pullPx, archivePullThresholdPx) else 0f })
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).nestedScroll(pullConnection),
            // Keep the last conversation accessible above the overlay button.
            contentPadding = PaddingValues(bottom = 104.dp)
        ) {
            if (archiveAvailable && !archiveHidden) {
                item(key = "archive_row") { ArchiveRow(archivedChats, onClick = onArchiveClick, onHide = { onArchiveHiddenChange(true) }) }
            }
            if (pinnedChats.isNotEmpty()) {
                item(key = "pinned_header") {
                    Text(stringResource(R.string.chats_section_pinned), modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp), color = palette.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
                items(pinnedChats, key = { "pinned_${it.id}" }) { chat ->
                    SwipeChatRow(
                        chat = chat,
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        openChatId = openSwipeChatId,
                        onOpenChange = { openSwipeChatId = it },
                        onClick = { onChatClick(chat) },
                        onLongClick = { selectedActions = chat },
                        read = readAction.copy(enabled = showsUnreadIndicator(chat)),
                        primary = archiveAction
                    )
                }
            }
            if (query.isNotBlank()) {
                item(key = "global_search") {
                    Text(
                        stringResource(R.string.chats_search_all, query),
                        color = palette.accent,
                        fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onGlobalSearch(query) }.padding(horizontal = 20.dp, vertical = 14.dp)
                    )
                }
            }
            if (regularChats.isEmpty() && pinnedChats.isEmpty()) {
                item(key = "empty_state") {
                    when {
                        activeFolderId != null && folderLoad.loading -> Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = palette.accent)
                        }
                        activeFolderId != null && query.isBlank() && selectedFilter == ChatFilter.All ->
                            Text(stringResource(R.string.chat_folder_empty), color = palette.textSecondary,
                                modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        else -> EmptyState(selectedFilter, query)
                    }
                }
            } else {
                // A pinned group needs a quiet separator, not a second "All chats" label
                // beneath the already selected Telegram folder tab.
                if (pinnedChats.isNotEmpty() && regularChats.isNotEmpty()) {
                    item(key = "pinned_separator") {
                        HorizontalDivider(
                            color = palette.divider,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }
                items(regularChats, key = { it.id }) { chat ->
                    SwipeChatRow(
                        chat = chat,
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        openChatId = openSwipeChatId,
                        onOpenChange = { openSwipeChatId = it },
                        onClick = { onChatClick(chat) },
                        onLongClick = { selectedActions = chat },
                        read = readAction.copy(enabled = showsUnreadIndicator(chat)),
                        primary = archiveAction
                    )
                }
            }
            if (activeFolderId != null) {
                item(key = "server_folder_load_more") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        if (folderLoad.loading) CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(26.dp))
                        if (folderLoad.error) Text(stringResource(R.string.chat_folder_load_failed),
                            color = palette.danger, fontSize = 13.sp)
                        if (folderLoad.hasMore && !folderLoad.loading) {
                            TextButton(onClick = { onFolderLoadMore(activeFolderId) }) {
                                Text(stringResource(if (folderLoad.error) R.string.common_retry else R.string.chat_folder_load_more))
                            }
                        }
                    }
                }
            }
        }
      }
      FloatingActionButton(
          onClick = onNewChat,
          modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 16.dp),
          containerColor = palette.accent,
          contentColor = palette.onAccent,
          shape = RoundedCornerShape(20.dp)
      ) {
          Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.new_chat_title), modifier = Modifier.size(24.dp))
      }
    }
    selectedActions?.let { selected ->
        ChatActionsSheet(
            chat = selected,
            onClose = { selectedActions = null },
            setPinned = { pinned ->
                if (activeFolderId != null) onPinFolderChat(activeFolderId, selected.id, pinned)
                else onPinChat(selected.id, pinned)
            },
            setMuted = { onMuteChat(selected.id, it) },
            setUnread = { onMarkUnread(selected) },
            delete = { onDeleteChat(selected.id) }
        )
    }
}

@Composable
private fun ArchiveRow(archivedChats: List<ChatPreview>, onClick: () -> Unit, onHide: () -> Unit) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    var actionsOpen by remember { mutableStateOf(false) }
    val unread = archivedChats.sumOf { it.unreadCount }
    val summary = buildString {
        append("${archivedChats.size} ${russianPlural(archivedChats.size, context.getString(R.string.chats_archive_count_one), context.getString(R.string.chats_archive_count_few), context.getString(R.string.chats_archive_count_many))}")
        if (unread > 0) append(" · $unread ${russianPlural(unread, context.getString(R.string.chats_archive_unread_one), context.getString(R.string.chats_archive_unread_few), context.getString(R.string.chats_archive_unread_few))}")
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)).background(palette.surface)
                .combinedClickable(onClick = onClick, onLongClick = { actionsOpen = true })
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(palette.surface2), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Archive, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.chats_archive_title), color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(summary, color = palette.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
        }
        if (actionsOpen) {
            // Действия открываются прямо под строкой, без всплывающего окна.
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    .clip(RoundedCornerShape(20.dp)).background(palette.surface).padding(vertical = 4.dp)
            ) {
                ArchiveAction(Icons.Rounded.VisibilityOff, stringResource(R.string.chats_archive_hide), palette.accent, palette.accentInk, FontWeight.SemiBold, onHide)
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
                ArchiveAction(Icons.Rounded.Close, stringResource(R.string.common_cancel), palette.textSecondary, palette.textSecondary, FontWeight.Normal) { actionsOpen = false }
            }
        }
    }
}

@Composable
private fun ArchiveAction(icon: ImageVector, label: String, iconTint: Color, labelColor: Color, labelWeight: FontWeight, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = labelColor, fontSize = 16.sp, fontWeight = labelWeight)
    }
}

/** Читает протяжку внутри себя: частые шаги жеста перерисовывают только подсказку, а не весь экран чатов. */
@Composable
private fun ArchivePullSlot(shownPx: () -> Float) {
    val density = LocalDensity.current
    val px = shownPx()
    if (px > 0f) ArchivePullHint(heightDp = with(density) { px.toDp() })
}

/** Подсказка над списком, пока архив скрыт и список тянут вниз: высота растёт вместе с протяжкой. */
@Composable
private fun ArchivePullHint(heightDp: Dp) {
    val palette = beyGramPalette()
    Column(
        modifier = Modifier.fillMaxWidth().height(heightDp).clipToBounds()
            .background(Brush.verticalGradient(listOf(palette.accentSoft, Color.Transparent))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(palette.surface), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ArrowDownward, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.chats_archive_pull_hint), color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Server tabs are horizontally scrollable; no pager gesture conflicts with SwipeChatRow. */
@Composable
private fun ServerFolderChip(title: String, selected: Boolean, unreadCount: Int?, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) palette.accent else palette.surface2)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = if (selected) palette.onAccent else palette.textPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.widthIn(max = 144.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (unreadCount != null && unreadCount > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                unreadCount.toString(),
                color = if (selected) palette.onAccent else palette.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun FilterChip(title: String, selected: Boolean, count: Int?, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        modifier = Modifier.height(34.dp).clip(RoundedCornerShape(50)).background(if (selected) palette.accent else palette.surface2)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = if (selected) palette.onAccent else palette.textPrimary, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        if (count != null && count > 0) {
            Spacer(Modifier.width(6.dp))
            Text(count.toString(), color = if (selected) palette.onAccent else palette.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyState(filter: ChatFilter, query: String) {
    val palette = beyGramPalette()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(if (query.isNotBlank()) Icons.Rounded.Search else Icons.Rounded.Cloud, contentDescription = null, tint = palette.accent, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                query.isNotBlank() -> stringResource(R.string.chats_empty_no_results)
                filter == ChatFilter.Unread -> stringResource(R.string.chats_empty_all_read)
                filter == ChatFilter.Groups -> stringResource(R.string.chats_empty_no_groups)
                else -> stringResource(R.string.chats_empty_quiet)
            },
            color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(5.dp))
        Text(stringResource(R.string.chats_empty_hint), color = palette.textSecondary, fontSize = 14.sp)
    }
}

/** Текст последнего сообщения с префиксом автора: «Вы» для своих и имя отправителя в группах. */
@Composable
private fun previewLine(chat: ChatPreview, emptyPreview: Boolean): String {
    if (emptyPreview) return stringResource(if (chat.isGroup) R.string.chats_group_preview else R.string.chats_start_conversation)
    val author = when {
        chat.lastMessageOutgoing -> stringResource(R.string.chat_you)
        chat.isGroup && chat.lastMessageSender.isNotBlank() -> chat.lastMessageSender
        else -> null
    }
    val body = chat.lastMessage.resolve()
    return if (author == null) body else stringResource(R.string.chats_preview_sender, author) + body
}

/** Статус своего последнего сообщения: отправляется, отправлено, прочитано или не отправлено. */
@Composable
private fun SendStatusIcon(chat: ChatPreview) {
    val palette = beyGramPalette()
    if (!chat.lastMessageOutgoing) return
    val modifier = Modifier.padding(start = 6.dp).size(16.dp)
    when (chat.lastMessageSendState) {
        MessageSendState.Sending -> Icon(Icons.Rounded.Schedule, stringResource(R.string.chat_sending_description), tint = palette.textSecondary, modifier = modifier)
        MessageSendState.Failed -> Icon(Icons.Rounded.ErrorOutline, stringResource(R.string.chat_status_not_sent), tint = palette.warningText, modifier = modifier)
        MessageSendState.Sent -> {
            val read = chat.lastMessageId > 0L && chat.lastMessageId <= chat.lastReadOutboxMessageId
            if (read) Icon(Icons.Rounded.DoneAll, stringResource(R.string.chat_read_description), tint = palette.accent, modifier = modifier)
            else Icon(Icons.Rounded.Done, stringResource(R.string.chat_sent_description), tint = palette.textSecondary, modifier = modifier)
        }
    }
}

/** Плотность списка чатов: сколько места отдано каждой строке. [Default] — вид, который BeyGram всегда имел. */
internal data class ChatListDensity(
    /** Отступ над и под содержимым строки чата. */
    val rowVerticalPadding: Dp = 10.dp
)

internal val LocalChatListDensity = compositionLocalOf { ChatListDensity() }

/** Строки списка чатов для выбранной плотности. [ListDensity.Comfortable] даёт прежний отступ строки. */
internal fun chatListDensityFor(density: ListDensity): ChatListDensity = when (density) {
    ListDensity.Compact -> ChatListDensity(rowVerticalPadding = 6.dp)
    ListDensity.Comfortable -> ChatListDensity()
    ListDensity.Spacious -> ChatListDensity(rowVerticalPadding = 14.dp)
}

/** Telegram shows a chat as unread either because of a server unread count or an explicit manual marker. */
internal fun showsUnreadIndicator(chat: ChatPreview): Boolean = chat.unreadCount > 0 || chat.isMarkedAsUnread

@Composable
internal fun ChatListItem(chat: ChatPreview, observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit,
    onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val palette = beyGramPalette()
    val listDensity = LocalChatListDensity.current
    val unread = showsUnreadIndicator(chat)
    val emptyPreview = (chat.lastMessage as? UiText.Raw)?.text?.isBlank() == true
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = listDensity.rowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChatAvatar(chat, 54.dp, observeFile, downloadFile)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(chat.title, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Text(chat.lastMessageTime, color = if (unread) palette.accent else palette.textSecondary, fontSize = 12.sp, fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal)
            }
            Spacer(Modifier.height(3.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (chat.isMuted) Icon(Icons.Rounded.NotificationsOff, null, tint = palette.textSecondary, modifier = Modifier.padding(end = 6.dp).size(16.dp))
                Text(previewLine(chat, emptyPreview), color = palette.textSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                SendStatusIcon(chat)
                if (unread) {
                    Spacer(Modifier.width(8.dp))
                    // Чат без звука получает серый счётчик, как в Telegram. Ручная отметка без сообщений — точка.
                    val badgeColor = if (chat.isMuted) palette.textSecondary else palette.accent
                    if (chat.unreadCount > 0) {
                        Box(modifier = Modifier.defaultMinSize(minWidth = 22.dp).height(22.dp).clip(RoundedCornerShape(11.dp)).background(badgeColor).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                            Text(if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(), color = if (chat.isMuted) palette.surface else palette.onAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(badgeColor))
                    }
                }
            }
        }
    }
}
