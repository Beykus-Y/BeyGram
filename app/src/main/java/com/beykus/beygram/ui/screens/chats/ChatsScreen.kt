package com.beykus.beygram.ui.screens.chats

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.snapshotFlow
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.ui.components.LocalWindowLayout
import com.beykus.beygram.ui.design.styledShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import com.beykus.beygram.ui.components.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.ListDensity
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ChatStories
import com.beykus.beygram.domain.model.TelegramChatFolders
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.model.TelegramFolderLoadState
import com.beykus.beygram.domain.model.TelegramFolderTab
import com.beykus.beygram.domain.model.orderedTelegramFolderTabs
import com.beykus.beygram.domain.model.validTelegramFolderSelection
import com.beykus.beygram.domain.search.GlobalSearchQuery
import com.beykus.beygram.domain.search.networkSearchAction
import com.beykus.beygram.domain.search.parseGlobalSearchQuery
import com.beykus.beygram.ui.components.Avatar
import com.beykus.beygram.ui.components.ChatAvatar
import com.beykus.beygram.ui.components.SearchFieldButton
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.domain.model.ProfileInfo
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.ui.text.resolve
import com.beykus.beygram.ui.text.pluralForm
import java.text.NumberFormat
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.beykus.beygram.ui.accessibility.accessibleChatRowDescription
import com.beykus.beygram.ui.accessibility.accessibleDescription
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.LocalNavigationDock
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.createButtonPlacement
import com.beykus.beygram.ui.components.groupSurfaceColor
import com.beykus.beygram.ui.design.ArchiveTreatment
import com.beykus.beygram.ui.design.ChipSelectionTone
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.usesGlassPanels
import com.beykus.beygram.ui.design.recordsGlassBackdrop
import com.beykus.beygram.ui.design.rememberGlassBackdrop
import com.beykus.beygram.ui.design.glassBackdropSource
import com.beykus.beygram.ui.design.glassCard
import com.beykus.beygram.ui.design.LocalGlassBackdrop
import com.beykus.beygram.ui.design.GlassPanel
import com.beykus.beygram.ui.design.GlassEdge
import com.beykus.beygram.ui.design.glassScreenBackground
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.rememberMotionSpec
import com.beykus.beygram.ui.screens.stories.StoryBarSection

/** Ignore tiny scroll offsets; collapse after passing the story/archive area. */
internal fun shouldCompactChatListHeader(firstVisibleItemIndex: Int, scrollOffsetPx: Int, thresholdPx: Int): Boolean =
    firstVisibleItemIndex > 0 || scrollOffsetPx > thresholdPx

private enum class ChatFilter(val title: Int) {
    All(R.string.chats_filter_all), People(R.string.chats_filter_people), Groups(R.string.chats_filter_groups), Unread(R.string.chats_filter_unread)
}

/** Сколько протянуть список вниз, чтобы вернуть скрытый архив; подсказка занимает столько же. */
private val archivePullThreshold = 104.dp

/**
 * Высота мягкого края списка: строки, которые уходят под шапку или подходят к плавающему доку, растворяются здесь,
 * а не обрываются тонкой полосой.
 */
private val chatListEdgeFadeHeight = 24.dp

@Composable
fun ChatsScreen(
    chats: List<ChatPreview>,
    archivedChats: List<ChatPreview>,
    /** Per-account TDLib server folders and their own ordered content. */
    folderSnapshot: TelegramChatFolders,
    folderChats: Map<Int, List<ChatPreview>>,
    folderLoadStates: Map<Int, TelegramFolderLoadState>,
    /** Состояние сессии Telegram у активного аккаунта, а не доступность интернета или прокси. */
    connectionState: TelegramConnectionState,
    /** Активный аккаунт: при переключении строка статуса не должна остаться от предыдущего. */
    accountId: String,
    onFolderLoadMore: (Int) -> Unit,
    onPinFolderChat: suspend (Int, Long, Boolean) -> Result<Unit>,
    onLoadFolder: suspend (Int) -> Result<com.beykus.beygram.domain.model.TelegramFolderDraft>,
    onSaveFolder: suspend (com.beykus.beygram.domain.model.TelegramFolderDraft) -> Result<Int>,
    onDeleteFolder: suspend (Int) -> Result<Unit>,
    onReorderFolders: suspend (List<Int>, Int) -> Result<Unit>,
    stories: List<ChatStories> = emptyList(),
    onOpenStories: (ChatStories) -> Unit = {},
    onAddStory: () -> Unit = {},
    onLoadMoreStories: () -> Unit = {},
    myProfile: ProfileInfo?,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onMyProfile: () -> Unit,
    onAccountPicker: () -> Unit = {},
    onArchiveClick: () -> Unit,
    onMarkRead: suspend (ChatPreview) -> Boolean,
    /** Runs on an owner that outlives this screen, so leaving the chat list does not cancel the bulk read. */
    onMarkAllRead: (List<ChatPreview>) -> Unit = {},
    onMarkUnread: suspend (ChatPreview) -> Result<Unit>,
    onArchive: suspend (ChatPreview) -> Boolean,
    onUnarchive: suspend (ChatPreview) -> Boolean = { false },
    onPinChat: suspend (Long, Boolean) -> Result<Unit>,
    onMuteChat: suspend (Long, Boolean) -> Result<Unit>,
    onDeleteChat: suspend (Long) -> Result<Unit>,
    onNewChat: () -> Unit,
    archiveHidden: Boolean,
    onArchiveHiddenChange: (Boolean) -> Unit,
    onChatClick: (ChatPreview) -> Unit,
    onGlobalSearch: (String) -> Unit = {},
    onOpenMusic: () -> Unit = {},
    /**
     * Чат, открытый в правой панели: его строка подсвечена и помечена для TalkBack.
     * Пока панель показывает заглушку «Выберите чат», сюда приходит null.
     */
    highlightedChatId: Long? = null,
    /**
     * Узкая левая панель двухпанельного режима. Заголовок компактнее, а менеджер папок
     * открывается поверх всего окна: в панели шириной 300 dp он был бы обрезан.
     */
    compactHeader: Boolean = false,
    /**
     * Поиск, папка и позиция списка. Владелец экрана держит это состояние выше раскладки,
     * поэтому переход между телефоном и двумя панелями не сбрасывает список.
     */
    state: ChatsScreenState = rememberChatsScreenState()
) {
    val query = state.query
    // After upgrading from the local text field, never leave the list filtered by a stale saved query.
    LaunchedEffect(accountId, state) {
        if (state.query.isNotBlank()) state.query = ""
    }
    // Server folders replace the old local All / People / Groups / Unread chips.
    // Applying local filtering again beneath server folders gave users two competing tab bars.
    val selectedFilter = ChatFilter.All
    var managingFolders by rememberSaveable { mutableStateOf(false) }
    // A folder is selected by server ID, never by its mutable index or local category name.
    val selectedFolderId = state.selectedFolderId
    val activeFolderId = validTelegramFolderSelection(folderSnapshot, selectedFolderId.takeIf { it > 0 })
    LaunchedEffect(folderSnapshot.initialized, folderSnapshot.folders.map { it.id }) {
        if (folderSnapshot.initialized && selectedFolderId != 0 && activeFolderId == null) state.selectFolder(0)
    }
    val displayedChats = activeFolderId?.let { folderChats[it].orEmpty() } ?: chats
    val folderLoad = activeFolderId?.let { folderLoadStates[it] } ?: TelegramFolderLoadState(hasMore = false)
    val palette = beyGramPalette()
    val haptics = LocalHapticFeedback.current
    // Pinning, unpinning and removal move rows through the ListReorder token. Off and reduced motion snap instead,
    // so rows never fade in or out on a frame the user did not ask for.
    val listSpec = rememberMotionSpec(MotionToken.ListReorder)
    val listFadeSpec = if (listSpec.isInstant) snap<Float>() else listSpec.animationSpec<Float>()
    val listPlacementSpec = if (listSpec.isInstant) snap<IntOffset>() else listSpec.animationSpec<IntOffset>()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    // In the two-pane layout the list stays composed beside the open chat. Tapping a chat must
    // still drop the search keyboard, as leaving the whole list does on a phone.
    val openChat: (ChatPreview) -> Unit = { chat ->
        focusManager.clearFocus()
        onChatClick(chat)
    }
    // The split list pane is narrower than a phone: the title and side paddings give room to the
    // header actions (saved messages, account switcher, avatar) without pushing them off-screen.
    val headerPadding = if (compactHeader) 14.dp else 20.dp
    // The create button follows the real navigation: it lines up with a floating capsule and keeps its air, or stays in
    // the classic bottom-end corner above a pinned bar. The list keeps enough room to scroll its last row clear of it.
    val design = LocalDesignSystem.current
    val dock = LocalNavigationDock.current
    val fabPlacement = remember(dock) { createButtonPlacement(dock) }
    val fabShape = RoundedCornerShape(design.fab.cornerRadius)
    // Material 2 shouts its section labels; the other styles keep the sentence case of the resource.
    val sectionLabel: (String) -> String = { text ->
        if (design.mainScreen.sectionLabelUppercase) text.uppercase() else text
    }
    // Статус сессии Telegram общий для телефона и двухпанельного списка: он не зависит от раскладки.
    val connectionStatus = rememberChatsConnectionStatus(accountId, connectionState)
    var openSwipeChatId by remember { mutableStateOf<Long?>(null) }
    var selectedActions by remember { mutableStateOf<ChatPreview?>(null) }
    var confirmReadAll by remember { mutableStateOf(false) }
    val swipeStore = remember(context) { ChatSwipeSettingsStore(context) }
    var swipePreferences by remember(accountId) { mutableStateOf(swipeStore.load(accountId)) }
    var showSwipeSettings by remember(accountId) { mutableStateOf(false) }
    val snackbarState = remember(accountId) { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val changeSwipePreferences: (ChatSwipePreferences) -> Unit = { updated ->
        swipePreferences = updated
        swipeStore.save(accountId, updated)
        openSwipeChatId = null
    }
    val readLabel = stringResource(R.string.chats_action_read)
    val unreadActionLabel = stringResource(R.string.chats_action_unread)
    val archiveLabel = stringResource(R.string.chats_action_archive)
    val pinLabel = stringResource(R.string.chats_swipe_pin)
    val unpinLabel = stringResource(R.string.chats_swipe_unpin)
    val muteLabel = stringResource(R.string.chats_swipe_mute)
    val unmuteLabel = stringResource(R.string.chats_swipe_unmute)
    val moreLabel = stringResource(R.string.chats_swipe_more)
    val archiveSnackbarLabel = stringResource(R.string.chats_archive_moved)
    val undoSnackbarLabel = stringResource(R.string.chats_archive_undo)
    val actionFor: (ChatPreview, ChatSwipeActionKind) -> ChatSwipeAction = { chat, kind ->
        when (kind) {
            ChatSwipeActionKind.Archive -> ChatSwipeAction(
                archiveLabel, Icons.Rounded.Archive, palette.accent, palette.onAccent, true, kind, onArchive
            )
            ChatSwipeActionKind.ReadUnread -> ChatSwipeAction(
                if (showsUnreadIndicator(chat)) readLabel else unreadActionLabel,
                Icons.Rounded.DoneAll, palette.surface2, palette.accentInk, true, kind,
                { target -> if (showsUnreadIndicator(target)) onMarkRead(target) else onMarkUnread(target).isSuccess }
            )
            ChatSwipeActionKind.Pin -> ChatSwipeAction(
                if (chat.isPinned) unpinLabel else pinLabel,
                Icons.Rounded.Bookmark, palette.surface2, palette.accentInk, true, kind,
                { target ->
                    (if (activeFolderId != null) onPinFolderChat(activeFolderId, target.id, !target.isPinned)
                    else onPinChat(target.id, !target.isPinned)).isSuccess
                }
            )
            ChatSwipeActionKind.Mute -> ChatSwipeAction(
                if (chat.isMuted) unmuteLabel else muteLabel,
                Icons.Rounded.NotificationsOff, palette.surface2, palette.accentInk, true, kind,
                { target -> onMuteChat(target.id, !target.isMuted).isSuccess }
            )
            ChatSwipeActionKind.More -> ChatSwipeAction(
                moreLabel, Icons.Rounded.MoreVert, palette.surface2, palette.accentInk, true, kind,
                { target -> selectedActions = target; true }
            )
        }
    }
    val onSwipePerformed: (ChatPreview, ChatSwipeActionKind) -> Unit = { chat, kind ->
        if (kind == ChatSwipeActionKind.Archive) {
            snackbarScope.launch {
                val result = snackbarState.showSnackbar(
                    message = archiveSnackbarLabel,
                    actionLabel = undoSnackbarLabel
                )
                if (result == SnackbarResult.ActionPerformed) onUnarchive(chat)
            }
        }
    }
    val filteredChats = remember(query, displayedChats, selectedFilter) {
        // @username and t.me links match local titles by the bare username too.
        val localQuery = parseGlobalSearchQuery(query).publicText
        displayedChats.filter { chat ->
            val preview = (chat.lastMessage as? UiText.Raw)?.text
            val matchesQuery = query.isBlank() ||
                chat.title.contains(localQuery, true) ||
                preview?.contains(query, true) == true ||
                (localQuery != query && preview?.contains(localQuery, true) == true)
            val matchesFilter = when (selectedFilter) {
                ChatFilter.All -> true
                ChatFilter.Unread -> showsUnreadIndicator(chat)
                ChatFilter.People -> !chat.isGroup
                ChatFilter.Groups -> chat.isGroup
            }
            matchesQuery && matchesFilter
        }
    }
    val pinnedChats = remember(filteredChats, selectedFilter, query) {
        if (selectedFilter == ChatFilter.All && query.isBlank()) filteredChats.filter { it.isPinned } else emptyList()
    }
    // Pinned rows are the pinned rows of filteredChats whenever they are shown, so the split reads the flag
    // instead of searching pinnedChats for every row.
    val regularChats = remember(filteredChats, pinnedChats) {
        if (pinnedChats.isEmpty()) filteredChats else filteredChats.filterNot { it.isPinned }
    }
    // The header reads these on every recomposition, so the scans over all chats run only when their lists change.
    val hasUnreadChats = remember(displayedChats) { displayedChats.any { it.unreadCount > 0 || it.isMarkedAsUnread } }
    val savedMessagesChat = remember(chats, archivedChats) {
        chats.firstOrNull { it.isSavedMessages } ?: archivedChats.firstOrNull { it.isSavedMessages }
    }
    val archiveAvailable = activeFolderId == null && archivedChats.isNotEmpty() && selectedFilter == ChatFilter.All && query.isBlank()
    val density = LocalDensity.current
    val listState = state.listState
    LaunchedEffect(state) {
        // Each scroll runs in its own child job: a drag that cancels the animation must not end the request collector.
        var scrollJob: Job? = null
        state.scrollToTopRequests.collect {
            scrollJob?.cancel()
            scrollJob = launch { state.listState.animateScrollToItem(0) }
        }
    }
    // The folder chips scroll in their own strip. When the selected chip is not whole in view, the strip moves so its name
    // is fully visible, and the chip before it stays in view as a hint.
    val folderTabsState = rememberLazyListState()
    val folderTabsForScroll = orderedTelegramFolderTabs(folderSnapshot)
    val selectedFolderTabIndex = folderTabsForScroll.indexOfFirst { tab ->
        when (tab) {
            TelegramFolderTab.Main -> activeFolderId == null
            is TelegramFolderTab.Folder -> activeFolderId == tab.id
        }
    }
    LaunchedEffect(selectedFolderTabIndex, folderTabsForScroll.size) {
        if (selectedFolderTabIndex < 0) return@LaunchedEffect
        val info = folderTabsState.layoutInfo
        val visible = info.visibleItemsInfo.firstOrNull { it.index == selectedFolderTabIndex }
        val fullyVisible = visible != null &&
            visible.offset >= info.viewportStartOffset &&
            visible.offset + visible.size <= info.viewportEndOffset
        // The selected chip goes to the start edge: its whole name is in view, even when the chip before it is wide.
        if (!fullyVisible) folderTabsState.animateScrollToItem(selectedFolderTabIndex)
    }
    val headerCollapseThresholdPx = with(density) { 96.dp.roundToPx() }
    val headerCollapsed by remember(listState, headerCollapseThresholdPx) {
        derivedStateOf {
            shouldCompactChatListHeader(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                headerCollapseThresholdPx
            )
        }
    }
    val headerMotion = rememberMotionSpec(MotionToken.BannerChange)
    // The style owns the title scale; the collapsed form keeps the same 0.8 ratio Default has always had (24 of 30).
    val headerSize = design.mainScreen.titleSize.value
    val headerTitleSize by animateFloatAsState(
        targetValue = if (compactHeader || headerCollapsed) headerSize * 0.8f else headerSize,
        animationSpec = headerMotion.animationSpec<Float>(),
        label = "chatsHeaderTitleSize"
    )
    val titleSize = headerTitleSize.sp
    // The line box stays at the expanded size: the title shrinks inside it, so collapsing never changes the header height
    // and the list below it does not move. The overline does the same: it fades out and keeps its line.
    val titleLineHeight = (headerSize + 3f).sp
    val overlineAlpha by animateFloatAsState(
        targetValue = if (headerCollapsed) 0f else 1f,
        animationSpec = headerMotion.animationSpec<Float>(),
        label = "chatsOverlineAlpha"
    )
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
    // The list's top edge is a viewport edge, not a boundary between rows: once it has moved, rows scrolled under the
    // header are cut there. Their slice (a line of the archive summary, the edge of a tonal card) then sits in the 18 dp
    // gap above the pinned label and reads as stray debris. The edge dissolves instead; at rest nothing is faded.
    val listMoved by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    val topEdgeFade by animateFloatAsState(
        targetValue = if (listMoved) 1f else 0f,
        animationSpec = headerMotion.animationSpec<Float>(),
        label = "chatsListTopEdgeFade"
    )

    val windowLayout = LocalWindowLayout.current
    val shortWindow = windowLayout.heightCompact
    // Where the create-chat action lives follows the navigation: a floating button only where there is a bottom bar.
    val createChatPlacement = createChatPlacement(
        windowLayout, compactHeader, LocalDesignSystem.current.usesGlassPanels || LocalDesignSystem.current.style == DesignStyle.Signature
    )
    var foldersHiddenByScroll by remember { mutableStateOf(false) }
    LaunchedEffect(listState, shortWindow) {
        if (!shortWindow) {
            foldersHiddenByScroll = false
            return@LaunchedEffect
        }
        var last = 0L
        snapshotFlow { listState.firstVisibleItemIndex.toLong() * 100_000L + listState.firstVisibleItemScrollOffset }
            .collect { position ->
                when {
                    position <= 0L -> foldersHiddenByScroll = false
                    position > last + 8L -> foldersHiddenByScroll = true
                    position < last - 8L -> foldersHiddenByScroll = false
                }
                last = position
            }
    }

    if (managingFolders) {
        val manager: @Composable () -> Unit = {
            // Sorting every chat by title is only needed while the manager is open, and once per list change.
            val knownChats = remember(chats, archivedChats, folderChats) {
                (chats + archivedChats + folderChats.values.flatten()).distinctBy { it.id }.sortedBy { it.title.lowercase() }
            }
            FolderManagerScreen(
                snapshot = folderSnapshot,
                knownChats = knownChats,
                loadFolder = onLoadFolder,
                saveFolder = onSaveFolder,
                deleteFolder = onDeleteFolder,
                reorderFolders = onReorderFolders,
                onBack = { managingFolders = false }
            )
        }
        if (compactHeader) {
            // The list pane is only a third of a tablet window. Folder rules need the whole window,
            // so in the two-pane layout the manager becomes a full-screen dialog instead of a pane.
            Dialog(
                onDismissRequest = { managingFolders = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                    // FolderManagerScreen owns Back: it closes a draft picker before the manager itself.
                    dismissOnBackPress = false
                )
            ) {
                // A tablet window is far wider than a folder rule needs: the manager is a 640 dp column in the middle.
                Box(Modifier.fillMaxSize().background(palette.screen), contentAlignment = Alignment.TopCenter) {
                    Surface(Modifier.fillMaxHeight().widthIn(max = 640.dp).fillMaxWidth(), color = palette.screen) { manager() }
                }
            }
        } else {
            manager()
        }
        return
    }

    // The folder chips are one row in both headers (the phone title block and the compact split-pane header).
    val folderTabsRow: @Composable (Modifier) -> Unit = { modifier ->
                    val serverTabs = orderedTelegramFolderTabs(folderSnapshot)
                    LazyRow(
                        modifier = modifier,
                        state = folderTabsState,
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
                                    onClick = { state.selectFolder(0) },
                                    onLongClick = { managingFolders = true }
                                )
                                is TelegramFolderTab.Folder -> {
                                    val folder = folderSnapshot.folders.firstOrNull { it.id == tab.id }
                                    if (folder != null) ServerFolderChip(
                                        title = folder.title,
                                        selected = activeFolderId == folder.id,
                                        unreadCount = folder.unreadChats,
                                        onClick = { state.selectFolder(folder.id) },
                                        onLongClick = { managingFolders = true }
                                    )
                                }
                            }
                        }
                    }
    }
    // The split-pane header: no title (the section is named by the navigation), one 48 dp line with the account avatar,
    // the search field and "more", and a row of folder chips under it. In a short window the chips go away while the list
    // scrolls down and return on the first move up, so the rows get the height.
    // The same overflow is used on phone and split pane. Secondary actions remain one tap away.
    val headerOverflow: @Composable () -> Unit = {
        var moreOpen by remember { mutableStateOf(false) }
                Box {
                    androidx.compose.material3.IconButton(onClick = { moreOpen = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.MoreVert, stringResource(R.string.feature_more_actions), tint = palette.textSecondary)
                    }
                    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chats_read_all)) },
                            enabled = hasUnreadChats,
                            leadingIcon = { Icon(Icons.Rounded.DoneAll, null, tint = palette.textSecondary) },
                            onClick = { moreOpen = false; confirmReadAll = true }
                        )
                        savedMessagesChat?.let { saved ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.saved_messages_open_description)) },
                                leadingIcon = { Icon(Icons.Rounded.Bookmark, null, tint = palette.textSecondary) },
                                onClick = { moreOpen = false; openChat(saved) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.account_switcher_title)) },
                            leadingIcon = { Icon(Icons.Rounded.People, null, tint = palette.textSecondary) },
                            onClick = { moreOpen = false; onAccountPicker() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.music_library_title)) },
                            // The music note is the one accent of this menu; the rest stay in ink.
                            leadingIcon = { Icon(Icons.Rounded.MusicNote, null, tint = palette.accentInk) },
                            onClick = { moreOpen = false; focusManager.clearFocus(); onOpenMusic() }
                        )
                        if (folderSnapshot.initialized) DropdownMenuItem(
                            text = { Text(stringResource(R.string.folder_manage_title)) },
                            leadingIcon = { Icon(Icons.Rounded.Tune, null, tint = palette.textSecondary) },
                            onClick = { moreOpen = false; managingFolders = true }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chats_swipe_settings)) },
                            leadingIcon = { Icon(Icons.Rounded.Tune, null, tint = palette.textSecondary) },
                            onClick = { moreOpen = false; showSwipeSettings = true }
                        )
                    }
                }

    }

    val compactChatsHeader: @Composable (Modifier) -> Unit = { modifier ->
        val connectionLabel = if (connectionStatus == ChatsConnectionStatus.None) null
        else stringResource(connectionStatus.labelResource())
        Column(modifier = modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // The avatar opens the profile; a long press opens the account switcher (the people button is gone).
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .combinedClickable(
                            role = Role.Button,
                            onLongClickLabel = stringResource(R.string.account_switcher_title),
                            onLongClick = onAccountPicker,
                            onClick = onMyProfile
                        )
                        .semantics { contentDescription = context.getString(R.string.chats_my_profile_description) },
                    contentAlignment = Alignment.Center
                ) {
                    TelegramAvatar(myProfile?.initials ?: stringResource(R.string.chats_avatar_fallback), palette.accent, 32.dp, myProfile?.photo, observeFile, downloadFile,
                        Modifier.clearAndSetSemantics {})
                }
                Box(Modifier.weight(1f)) {
                    // The connection state replaces the hint of the field instead of taking a line of its own.
                    SearchFieldButton(
                        placeholder = stringResource(R.string.chats_search_placeholder),
                        onClick = { focusManager.clearFocus(); onGlobalSearch("") }
                    )
                }
                if (createChatPlacement == CreateChatPlacement.Header) {
                    androidx.compose.material3.IconButton(onClick = onNewChat, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.new_chat_title),
                            tint = if (design.usesGlassPanels) palette.accentInk else palette.accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                headerOverflow()
            }
            ChatsConnectionLine(connectionStatus)
            if (folderSnapshot.initialized) {
                AnimatedVisibility(visible = !foldersHiddenByScroll) {
                    folderTabsRow(Modifier.fillMaxWidth().padding(top = 2.dp, start = 4.dp, end = 4.dp))
                }
            }
        }
    }
    // The header is one block shared by both layouts: in a column above the list, or as glass chrome over it.
    val chatsHeader: @Composable (Modifier) -> Unit = { modifier ->
      if (compactHeader) compactChatsHeader(modifier) else
        Column(modifier = modifier.fillMaxWidth().padding(start = headerPadding, end = headerPadding, top = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    if (design.mainScreen.showBrandOverline) {
                        Text(
                            stringResource(R.string.app_name).uppercase(),
                            modifier = Modifier.graphicsLayer { alpha = overlineAlpha },
                            color = palette.accent,
                            style = MaterialTheme.typography.labelMedium,
                            letterSpacing = 0.8.sp
                        )
                    }
                    Text(stringResource(R.string.chats_title), color = palette.textPrimary, fontSize = titleSize, lineHeight = titleLineHeight, fontWeight = design.mainScreen.titleWeight, letterSpacing = design.mainScreen.titleTracking,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { state.requestScrollToTop() }.semantics { heading() })
                }
                if (createChatPlacement == CreateChatPlacement.Header) {
                    androidx.compose.material3.IconButton(onClick = onNewChat, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Edit, stringResource(R.string.new_chat_title),
                            tint = if (design.usesGlassPanels) palette.accentInk else palette.accent,
                            modifier = Modifier.size(22.dp))
                    }
                }
                // The avatar stays 40 dp visually, while the tap area covers Android's 48 dp minimum.
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .combinedClickable(
                            role = Role.Button,
                            onLongClickLabel = stringResource(R.string.account_switcher_title),
                            onLongClick = onAccountPicker,
                            onClick = onMyProfile
                        )
                        .semantics { contentDescription = context.getString(R.string.chats_my_profile_description) },
                    contentAlignment = Alignment.Center
                ) {
                    TelegramAvatar(myProfile?.initials ?: stringResource(R.string.chats_avatar_fallback), palette.accent, 40.dp, myProfile?.photo, observeFile, downloadFile,
                        Modifier.clearAndSetSemantics {})
                }
                headerOverflow()
            }
            // Заголовок «Сообщения» остаётся названием раздела; реальное состояние TDLib
            // показывается строкой во всю ширину шапки и исчезает сразу после восстановления связи.
            ChatsConnectionLine(connectionStatus)

            Spacer(Modifier.height(design.mainScreen.headerGap))
            SearchFieldButton(
                placeholder = stringResource(R.string.chats_search_placeholder),
                onClick = { focusManager.clearFocus(); onGlobalSearch("") }
            )
            Spacer(Modifier.height(design.mainScreen.folderGap))
            // Only one source of folder navigation: server-backed Telegram lists.
            // Main ('Все чаты') is a real tab even when the account has no custom folders yet.
            if (folderSnapshot.initialized) {
                folderTabsRow(Modifier.fillMaxWidth())
            }
        }
    }
    val chatList: @Composable (Modifier, Dp) -> Unit = { modifier, topPadding ->
        LazyColumn(
            state = listState,
            // Under glass chrome the rows run on beneath the pane and are blurred there: dissolving them on a 24 dp strip
            // first would cut a row off before it reaches the glass (topPadding > 0 only in the glass layout).
            modifier = modifier.nestedScroll(pullConnection).chatListEdgeFade(topPadding, fadeBottom = dock.floating && dock.contentInset == 0.dp) {
                if (topPadding > 0.dp) 0f else topEdgeFade
            },
            // Keep the last conversation accessible above the overlay button, measured from the button itself. Under
            // glass chrome the list also starts below the header it scrolls beneath.
            contentPadding = PaddingValues(
                top = topPadding,
                bottom = if (createChatPlacement == CreateChatPlacement.Fab) fabPlacement.listClearance else 8.dp
            )
        ) {
            // Stories live on the main chat list only, not in search results or a server folder.
            if (activeFolderId == null && query.isBlank()) {
                item(key = "story_bar") {
                    StoryBarSection(
                        stories = stories,
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        onOpenStories = onOpenStories,
                        onAddStory = onAddStory,
                        onLoadMore = onLoadMoreStories
                    )
                }
            }
            if (archiveAvailable && !archiveHidden) {
                item(key = "archive_row") { ArchiveRow(archivedChats, onClick = onArchiveClick, onHide = { onArchiveHiddenChange(true) }) }
            }
            if (pinnedChats.isNotEmpty()) {
                item(key = "pinned_header") {
                    Text(
                        sectionLabel(stringResource(R.string.chats_section_pinned)),
                        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp),
                        color = palette.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = design.mainScreen.sectionLabelTracking
                    )
                }
                // Same key as the regular rows, so a pin or unpin moves the row instead of replacing it.
                items(pinnedChats, key = { it.id }) { chat ->
                    SwipeChatRow(
                        modifier = Modifier.animateItem(
                            fadeInSpec = listFadeSpec,
                            placementSpec = listPlacementSpec,
                            fadeOutSpec = listFadeSpec
                        ),
                        chat = chat,
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        openChatId = openSwipeChatId,
                        onOpenChange = { openSwipeChatId = it },
                        onClick = { openChat(chat) },
                        onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); selectedActions = chat },
                        left = actionFor(chat, swipePreferences.left),
                        right = actionFor(chat, swipePreferences.right),
                        onPerformed = onSwipePerformed,
                        actionScope = snackbarScope,
                        highlighted = chat.id == highlightedChatId
                    )
                }
            }
            if (query.isNotBlank()) {
                item(key = "local_search_header") {
                    val label = sectionLabel(stringResource(R.string.chats_search_local_section))
                    Text(
                        label,
                        color = palette.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = design.mainScreen.sectionLabelTracking,
                        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp)
                            .semantics { heading() }
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
                    val showDividers = LocalDesignSystem.current.components.showListDividers
                    Column(
                        Modifier.animateItem(
                            fadeInSpec = listFadeSpec,
                            placementSpec = listPlacementSpec,
                            fadeOutSpec = listFadeSpec
                        )
                    ) {
                        SwipeChatRow(
                            chat = chat,
                            observeFile = observeFile,
                            downloadFile = downloadFile,
                            openChatId = openSwipeChatId,
                            onOpenChange = { openSwipeChatId = it },
                            onClick = { openChat(chat) },
                            onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); selectedActions = chat },
                            left = actionFor(chat, swipePreferences.left),
                            right = actionFor(chat, swipePreferences.right),
                            onPerformed = onSwipePerformed,
                            actionScope = snackbarScope,
                            highlighted = chat.id == highlightedChatId
                        )
                        // Material 2 separates rows with a real divider; the other styles rely on spacing.
                        if (showDividers) {
                            HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 76.dp))
                        }
                    }
                }
            }
            if (activeFolderId != null) {
                item(key = "server_folder_load_more") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        if (folderLoad.loading) CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(26.dp))
                        if (folderLoad.error) Text(stringResource(R.string.chat_folder_load_failed),
                            color = palette.danger, fontSize = 13.sp, modifier = Modifier.errorAnnouncement())
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
    // Glass chrome: the chat list header becomes a pane over the list. Its own recorder captures the list only, and only
    // when the device really blurs it; Balanced and Lite draw the same pane with a dense tint and record nothing.
    val glassChrome = design.usesGlassPanels
    val chatsBackdrop = if (glassChrome && design.recordsGlassBackdrop) rememberGlassBackdrop() else null
    var glassHeaderHeightPx by remember { mutableIntStateOf(0) }

    // The root Box is bounded by MainTabsScaffold: above the bottom navigation on phones,
    // beside the navigation rail on wide screens. Its FAB is never drawn under the system navigation bar.
    // In the split layout the strip over the status bar matches the chat pane's: both take the header colour of the style
    // (Signature and glass stay on the screen colour), so the two panes do not carry two tones above them.
    val statusBarPx = WindowInsets.statusBars.getTop(density).toFloat()
    val bandFill = if (compactHeader && !glassChrome && design.style != DesignStyle.Signature) palette.surface else null
    Box(
        modifier = Modifier.fillMaxSize().glassScreenBackground(design, palette.screen)
            .then(
                if (bandFill != null) Modifier.drawBehind {
                    drawRect(bandFill, size = androidx.compose.ui.geometry.Size(size.width, statusBarPx))
                } else Modifier
            )
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    // In the two-pane layout the pane's right edge is in the middle of the window:
                    // the end inset belongs to the chat pane, not to the list.
                    // Glass chrome draws under the status bar itself (the inset moves into the header pane).
                    if (compactHeader) (if (glassChrome) WindowInsetsSides.Start else WindowInsetsSides.Top + WindowInsetsSides.Start)
                    else (if (glassChrome) WindowInsetsSides.Horizontal else WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
            )
    ) {
      if (glassChrome) {
          // Liquid Glass: the list runs the whole height and the header floats over it as one pane of glass. Only the
          // list is recorded for the pane, never the pane itself, so it cannot sample a layer it is drawn into (#623).
          val headerHeight = with(density) { glassHeaderHeightPx.toDp() }
          val pullShown = if (archiveAvailable && archiveHidden) min(pullPx, archivePullThresholdPx) else 0f
          val pullDp = with(density) { pullShown.toDp() }
          Box(Modifier.fillMaxSize().then(if (chatsBackdrop != null) Modifier.glassBackdropSource(chatsBackdrop) else Modifier)) {
              chatList(Modifier.fillMaxSize(), headerHeight + pullDp)
              // The pull hint opens the same gap the list leaves for it, right under the glass.
              Box(Modifier.padding(top = headerHeight)) { ArchivePullSlot(shownPx = { pullShown }) }
          }
          CompositionLocalProvider(LocalGlassBackdrop provides chatsBackdrop) {
              GlassPanel(
                  design = design,
                  tint = palette.surface,
                  rim = palette.textSecondary,
                  // The recorded list has no background of its own, so the pane starts from the screen colour.
                  backdropBase = palette.screen,
                  shape = RectangleShape,
                  edge = GlassEdge.BottomHairline,
                  // The pane is hit-tested as a whole: a tap on the title or a gap must not reach a chat row
                  // scrolled underneath. Its own buttons and the search field still receive input first.
                  modifier = Modifier.fillMaxWidth().onSizeChanged { glassHeaderHeightPx = it.height }
                      .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
              ) {
                  // The pane covers the status bar, so the title still starts below it and no seam shows at the edge.
                  chatsHeader(Modifier.windowInsetsPadding(WindowInsets.statusBars))
              }
          }
      } else {
      Column(Modifier.fillMaxSize()) {
        chatsHeader(Modifier)

        ArchivePullSlot(shownPx = { if (archiveAvailable && archiveHidden) min(pullPx, archivePullThresholdPx) else 0f })
        chatList(Modifier.weight(1f), 0.dp)
      }
      }
      if (createChatPlacement == CreateChatPlacement.Fab) FloatingActionButton(
          onClick = onNewChat,
          modifier = Modifier.align(Alignment.BottomEnd)
              .padding(end = fabPlacement.endPadding, bottom = fabPlacement.bottomPadding)
              .size(design.fab.size.coerceAtLeast(48.dp))
              // The glass style outlines its floating button with the same thin light stroke as the other glass panes.
              .then(
                  if (design.usesGlassPanels) Modifier.border(1.dp, palette.textSecondary.copy(alpha = 0.18f), fabShape)
                  else Modifier
              ),
          // Material 3 keeps the main action tonal (primary container); the other styles keep the accent fill.
          containerColor = if (design.fab.tonal) palette.accentSoft else palette.accent,
          contentColor = if (design.fab.tonal) palette.accentInk else palette.onAccent,
          shape = fabShape,
          elevation = FloatingActionButtonDefaults.elevation(defaultElevation = design.fab.elevation)
      ) {
          Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.new_chat_title), modifier = Modifier.size(24.dp))
      }
      SnackbarHost(
          hostState = snackbarState,
          modifier = Modifier.align(Alignment.BottomCenter)
              .padding(bottom = fabPlacement.bottomPadding +
                  if (createChatPlacement == CreateChatPlacement.Fab) design.fab.size + 16.dp else 8.dp)
      )
    }
    if (showSwipeSettings) ChatSwipeSettingsDialog(
        preferences = swipePreferences,
        onChange = changeSwipePreferences,
        onDismiss = { showSwipeSettings = false }
    )
    if (confirmReadAll) AlertDialog(
        onDismissRequest = { confirmReadAll = false },
        title = { Text(stringResource(R.string.chats_read_all_title)) },
        text = { Text(stringResource(R.string.chats_read_all_warning)) },
        confirmButton = {
            TextButton(onClick = {
                confirmReadAll = false
                onMarkAllRead(displayedChats.filter { it.unreadCount > 0 || it.isMarkedAsUnread })
            }) { Text(stringResource(R.string.chats_read_all_confirm), color = palette.accent) }
        },
        dismissButton = { TextButton(onClick = { confirmReadAll = false }) { Text(stringResource(R.string.common_cancel)) } }
    )
    selectedActions?.let { selected ->
        ChatActionsSheet(
            chat = selected,
            observeFile = observeFile,
            downloadFile = downloadFile,
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

/** The two independent assignments live in the chat overflow, never in a permanent header button. */
@Composable
private fun ChatSwipeSettingsDialog(
    preferences: ChatSwipePreferences,
    onChange: (ChatSwipePreferences) -> Unit,
    onDismiss: () -> Unit
) {
    var leftOpen by remember { mutableStateOf(false) }
    var rightOpen by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chats_swipe_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.chats_swipe_left))
                Box {
                    TextButton(onClick = { leftOpen = true }) {
                        Text(chatSwipeKindLabel(preferences.left))
                    }
                    DropdownMenu(expanded = leftOpen, onDismissRequest = { leftOpen = false }) {
                        ChatSwipeActionKind.entries.forEach { kind ->
                            DropdownMenuItem(
                                text = { Text(chatSwipeKindLabel(kind)) },
                                onClick = {
                                    leftOpen = false
                                    onChange(preferences.copy(left = kind))
                                }
                            )
                        }
                    }
                }
                Text(stringResource(R.string.chats_swipe_right))
                Box {
                    TextButton(onClick = { rightOpen = true }) {
                        Text(chatSwipeKindLabel(preferences.right))
                    }
                    DropdownMenu(expanded = rightOpen, onDismissRequest = { rightOpen = false }) {
                        ChatSwipeActionKind.entries.forEach { kind ->
                            DropdownMenuItem(
                                text = { Text(chatSwipeKindLabel(kind)) },
                                onClick = {
                                    rightOpen = false
                                    onChange(preferences.copy(right = kind))
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

@Composable
private fun chatSwipeKindLabel(kind: ChatSwipeActionKind): String = stringResource(
    when (kind) {
        ChatSwipeActionKind.Archive -> R.string.chats_action_archive
        ChatSwipeActionKind.ReadUnread -> R.string.chats_swipe_read_unread
        ChatSwipeActionKind.Pin -> R.string.chats_swipe_pin_toggle
        ChatSwipeActionKind.Mute -> R.string.chats_swipe_mute_toggle
        ChatSwipeActionKind.More -> R.string.chats_swipe_more
    }
)

/**
 * Строка состояния сессии Telegram под заголовком списка. Говорит только то, что сообщил TDLib:
 * ожидание сети, подключение или получение обновлений. Экран не крутит спиннер и ничего не опрашивает.
 */
@Composable
private fun ChatsConnectionLine(status: ChatsConnectionStatus) {
    if (status == ChatsConnectionStatus.None) return
    val palette = beyGramPalette()
    val label = stringResource(status.labelResource())
    val spokenLabel = stringResource(R.string.chats_connection_a11y, label)
    // Ожидание сети — предупреждение, остальные состояния нейтральны и не выглядят как ошибка.
    val color = if (status == ChatsConnectionStatus.WaitingForNetwork) palette.warningText else palette.textSecondary
    val icon = when (status) {
        ChatsConnectionStatus.WaitingForNetwork -> Icons.Rounded.WifiOff
        ChatsConnectionStatus.Connecting -> Icons.Rounded.Schedule
        else -> Icons.Rounded.Refresh
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 5.dp).semantics(mergeDescendants = true) {
            // Статус меняется без действия пользователя: сообщаем его, не уводя фокус с экрана.
            liveRegion = LiveRegionMode.Polite
            contentDescription = spokenLabel
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Текст строки для состояния TDLib; [ChatsConnectionStatus.None] сюда не попадает. */
private fun ChatsConnectionStatus.labelResource(): Int = when (this) {
    ChatsConnectionStatus.WaitingForNetwork -> R.string.chats_connection_waiting_network
    ChatsConnectionStatus.Connecting -> R.string.chats_connection_connecting
    ChatsConnectionStatus.Updating -> R.string.chats_connection_updating
    ChatsConnectionStatus.None -> error("The plain title has no connection label")
}

@Composable
private fun TelegramSearchAction(
    search: GlobalSearchQuery,
    offline: Boolean,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    val title = stringResource(R.string.chats_search_network_action)
    val subtitle = when {
        offline -> stringResource(R.string.chats_search_network_action_offline)
        search.username != null -> stringResource(
            R.string.chats_search_network_action_username,
            search.username
        )
        else -> stringResource(R.string.chats_search_network_action_hint)
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(styledShape(16.dp))
            .background(palette.accentSoft)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibleDescription(title, subtitle)
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(palette.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.accentInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                color = palette.textSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(22.dp))
    }
}

@Composable
internal fun ArchiveRow(archivedChats: List<ChatPreview>, onClick: () -> Unit, onHide: () -> Unit) {
    val palette = beyGramPalette()
    val main = LocalDesignSystem.current.mainScreen
    val context = LocalContext.current
    var actionsOpen by remember { mutableStateOf(false) }
    // Chats with something unread, not the sum of their messages: a seven-digit message total is noise nobody can act
    // on, while "3 chats need attention" is what the archive line is for.
    val unreadChats = archivedChats.count { showsUnreadIndicator(it) }
    val title = stringResource(R.string.chats_archive_title)
    val locale = LocalConfiguration.current.locales[0]
    val language = locale.language
    // Digits are grouped for the interface language (3 132 938 in Russian, 3,132,938 in English), as any count is.
    val numbers = NumberFormat.getIntegerInstance(locale)
    val countLine = "${numbers.format(archivedChats.size.toLong())} ${pluralForm(language, archivedChats.size, context.getString(R.string.chats_archive_count_one), context.getString(R.string.chats_archive_count_few), context.getString(R.string.chats_archive_count_many))}"
    val unreadLine = if (unreadChats > 0) "${numbers.format(unreadChats.toLong())} ${pluralForm(language, unreadChats, context.getString(R.string.chats_archive_unread_one), context.getString(R.string.chats_archive_unread_few), context.getString(R.string.chats_archive_unread_few))}" else null
    val summary = if (unreadLine != null) "$countLine · $unreadLine" else countLine
    // Archive is a navigation row, not a featured content card. Keep its touch target >= 48dp
    // while reducing the visual prominence that previously pushed conversations down.
    // Card is a raised surface, Tonal is today's quiet fill, Flat is just a navigation line inside the list.
    val archiveShape = RoundedCornerShape(main.archiveRadius)
    val design = LocalDesignSystem.current
    val groupSurface = groupSurfaceColor()
    val archiveBackground = when (main.archive) {
        ArchiveTreatment.Card, ArchiveTreatment.Tonal -> groupSurface
        ArchiveTreatment.Flat -> Color.Transparent
    }
    val compact = main.compactArchive
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (compact) 2.dp else 3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = if (compact) 48.dp else 56.dp)
                .then(
                    if (main.archiveElevation > 0.dp) Modifier.shadow(main.archiveElevation, archiveShape)
                    else Modifier
                )
                .then(if (main.archive == ArchiveTreatment.Flat) Modifier.clip(archiveShape) else Modifier.glassCard(design, groupSurface, palette.textSecondary, archiveShape))
                .combinedClickable(onClick = onClick, onLongClick = { actionsOpen = true }, role = Role.Button)
                .semantics(mergeDescendants = true) { contentDescription = accessibleDescription(title, summary) }
                .padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 6.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconBox = if (compact) 30.dp else 36.dp
            Box(modifier = Modifier.size(iconBox).clip(CircleShape).background(palette.surface2), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Archive, contentDescription = null, tint = if (compact) palette.textSecondary else palette.accentInk, modifier = Modifier.size(if (compact) 18.dp else 20.dp))
            }
            Spacer(Modifier.width(12.dp))
            if (compact) {
                // One line: the title and the chat count; unread chats become a small muted counter on the right.
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Text(countLine, color = palette.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (unreadChats > 0) {
                    Box(
                        Modifier.padding(start = 8.dp).heightIn(min = 20.dp).clip(RoundedCornerShape(main.badgeRadius))
                            .background(palette.surface2).padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (unreadChats > 99) "99+" else unreadChats.toString(), color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(summary, color = palette.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
        }
        if (actionsOpen) {
            // Действия открываются прямо под строкой, без всплывающего окна.
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    .clip(styledShape(20.dp)).background(palette.surface).padding(vertical = 4.dp)
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
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
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

/**
 * Soft edges of the chat list. The top edge dissolves under the header once the list has moved: [topProgress] scales it
 * (0 at rest, so the first row is never dimmed) and [topPadding] is where the header ends. With a floating dock
 * ([fadeBottom]) the bottom edge dissolves before the gap above the capsule, so a row cut there does not end in a
 * sliver. The list is drawn into its own layer and masked there (DstIn): the screen colour, the glows and the glass
 * backdrop record show through unchanged, and nothing is blurred.
 */
private fun Modifier.chatListEdgeFade(topPadding: Dp, fadeBottom: Boolean, topProgress: () -> Float): Modifier =
    graphicsLayer {
        compositingStrategy = if (fadeBottom || topProgress() > 0f) {
            CompositingStrategy.Offscreen
        } else {
            CompositingStrategy.Auto
        }
    }.drawWithContent {
        drawContent()
        val fade = chatListEdgeFadeHeight.toPx()
        val amount = topProgress().coerceIn(0f, 1f)
        if (amount > 0f) {
            val top = topPadding.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 1f - amount), Color.Black),
                    startY = top,
                    endY = top + fade
                ),
                topLeft = Offset(0f, top),
                size = Size(size.width, fade),
                blendMode = BlendMode.DstIn
            )
        }
        if (fadeBottom) {
            val bottom = size.height
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = bottom - fade,
                    endY = bottom
                ),
                topLeft = Offset(0f, bottom - fade),
                size = Size(size.width, fade),
                blendMode = BlendMode.DstIn
            )
        }
    }

/** Server tabs are horizontally scrollable; no pager gesture conflicts with SwipeChatRow. */
@Composable
internal fun ServerFolderChip(
    title: String,
    selected: Boolean,
    unreadCount: Int?,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    when (LocalDesignSystem.current.mainScreen.chipSelection) {
        ChipSelectionTone.Filter -> {
            Material3FolderChip(title, selected, unreadCount, onClick, onLongClick)
            return
        }
        ChipSelectionTone.Ink -> {
            InkFolderChip(title, selected, unreadCount, onClick, onLongClick)
            return
        }
        else -> Unit
    }
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val main = design.mainScreen
    val tabSpec = rememberMotionSpec(MotionToken.TabSelect).animationSpec<Color>()
    // Filled is the BeyGram accent chip, Tonal is the Material 3 filter chip, Underlined is the classic Material 2 tab:
    // the container keeps the surface and the selection is a rule under the label.
    val containerColor by animateColorAsState(
        when {
            // The underlined tab keeps the neutral surface in both states: its accent label would be unreadable on an
            // accent fill, and the rule under it is what marks the selection.
            main.chipSelection == ChipSelectionTone.Underlined -> palette.surface2
            // On a glass pane a surface2 chip is the pane's own colour; a sunken wash of the text colour stays visible.
            !selected && design.usesGlassPanels -> palette.textPrimary.copy(alpha = 0.08f)
            !selected -> palette.surface2
            main.chipSelection == ChipSelectionTone.Tonal -> palette.accentSoft
            else -> palette.accent
        },
        tabSpec
    )
    val labelColor by animateColorAsState(
        when {
            !selected -> palette.textPrimary
            main.chipSelection == ChipSelectionTone.Filled -> palette.onAccent
            else -> palette.accentInk
        },
        tabSpec
    )
    val chipShape = RoundedCornerShape(main.chipRadius)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.heightIn(min = 48.dp).clip(chipShape)
                .background(containerColor)
                .combinedClickable(role = Role.Tab, onClick = onClick, onLongClick = onLongClick)
                .semantics { this.selected = selected }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = labelColor,
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
                    color = when {
                        !selected -> palette.accent
                        main.chipSelection == ChipSelectionTone.Filled -> palette.onAccent
                        else -> palette.accentInk
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (main.chipSelection == ChipSelectionTone.Underlined) {
            // The rule is always laid out, so switching folders never shifts the row of chips by two dp.
            Box(
                Modifier.padding(top = 2.dp).height(2.dp).width(if (selected) 28.dp else 0.dp)
                    .background(if (selected) palette.accent else Color.Transparent)
            )
        }
    }
}

/**
 * The Material 3 filter chip: a 32dp chip with an outline when off, the secondary container with a leading check when
 * on. It sits in a 48dp touch target, so the row of folders looks lighter without a smaller tap area.
 */
@Composable
private fun Material3FolderChip(title: String, selected: Boolean, unreadCount: Int?, onClick: () -> Unit, onLongClick: () -> Unit) {
    val palette = beyGramPalette()
    val main = LocalDesignSystem.current.mainScreen
    val tabSpec = rememberMotionSpec(MotionToken.TabSelect).animationSpec<Color>()
    val containerColor by animateColorAsState(if (selected) palette.accentSoft else Color.Transparent, tabSpec)
    val outlineColor by animateColorAsState(if (selected) Color.Transparent else palette.divider, tabSpec)
    val labelColor = if (selected) palette.accentInk else palette.textSecondary
    val chipShape = RoundedCornerShape(main.chipRadius)
    Box(
        Modifier.heightIn(min = 48.dp)
            .combinedClickable(role = Role.Tab, onClick = onClick, onLongClick = onLongClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.heightIn(min = 32.dp).clip(chipShape)
                .background(containerColor)
                .border(1.dp, outlineColor, chipShape)
                .padding(start = if (selected) 8.dp else 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = labelColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                title,
                color = labelColor,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.widthIn(max = 144.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (unreadCount != null && unreadCount > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    unreadCount.toString(),
                    color = if (selected) palette.accentInk else palette.accent,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * The BeyGram Signature folder: a plain label when off, an ink-filled 36dp chip with the canvas colour as label when on.
 * Selection is ink, so the accent stays on the unread count; the 48dp touch target is kept around the smaller chip.
 */
/** How much accent the Signature ink chip carries; enough to warm it, little enough to keep it ink. */
private const val INK_ACCENT_SHARE = 0.18f

@Composable
private fun InkFolderChip(title: String, selected: Boolean, unreadCount: Int?, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    val palette = beyGramPalette()
    val main = LocalDesignSystem.current.mainScreen
    val tabSpec = rememberMotionSpec(MotionToken.TabSelect).animationSpec<Color>()
    // Ink still selects, but a pure white (or pure black) slab fought the warm accent of the palette (#670), so the
    // ink takes a small share of the accent. The label stays the canvas colour, so the contrast barely moves.
    val containerColor by animateColorAsState(if (selected) lerp(palette.textPrimary, palette.accent, INK_ACCENT_SHARE) else Color.Transparent, tabSpec)
    val labelColor by animateColorAsState(if (selected) palette.screen else palette.textSecondary, tabSpec)
    val chipShape = RoundedCornerShape(main.chipRadius)
    Box(
        Modifier.heightIn(min = 48.dp).clip(chipShape)
            .combinedClickable(role = Role.Tab, onClick = onClick, onLongClick = onLongClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.heightIn(min = 36.dp).clip(chipShape)
                .background(containerColor)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = labelColor,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.widthIn(max = 144.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (unreadCount != null && unreadCount > 0) {
                Spacer(Modifier.width(6.dp))
                Text(
                    unreadCount.toString(),
                    color = if (selected) palette.screen else palette.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
internal fun FilterChip(title: String, selected: Boolean, count: Int?, onClick: () -> Unit) {
    if (LocalDesignSystem.current.mainScreen.chipSelection == ChipSelectionTone.Ink) {
        InkFolderChip(title, selected, count, onClick)
        return
    }
    val palette = beyGramPalette()
    Row(
        modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).background(if (selected) palette.accent else palette.surface2)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick).padding(horizontal = 14.dp),
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
                query.isNotBlank() -> stringResource(R.string.chats_search_local_empty)
                filter == ChatFilter.Unread -> stringResource(R.string.chats_empty_all_read)
                filter == ChatFilter.Groups -> stringResource(R.string.chats_empty_no_groups)
                else -> stringResource(R.string.chats_empty_quiet)
            },
            color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(5.dp))
        Text(
            stringResource(if (query.isNotBlank()) R.string.chats_search_local_empty_hint else R.string.chats_empty_hint),
            color = palette.textSecondary, fontSize = 14.sp
        )
    }
}

/** Текст последнего сообщения с префиксом автора: «Вы» для своих и имя отправителя в группах. */
@Composable
private fun previewLine(chat: ChatPreview, emptyPreview: Boolean): String {
    // A secret chat's text must not appear in the chat list, Recents or a notification shade; only the lock label is shown.
    if (chat.isSecret) return stringResource(R.string.chats_secret_chat)
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
private fun sendStatusLabel(chat: ChatPreview): String? {
    if (!chat.lastMessageOutgoing) return null
    return when (chat.lastMessageSendState) {
        MessageSendState.Sending -> stringResource(R.string.chat_sending_description)
        MessageSendState.Failed -> stringResource(R.string.chat_status_not_sent)
        MessageSendState.Sent -> {
            val read = chat.lastMessageId > 0L && chat.lastMessageId <= chat.lastReadOutboxMessageId
            stringResource(if (read) R.string.chat_read_description else R.string.chat_sent_description)
        }
    }
}

@Composable
private fun SendStatusIcon(chat: ChatPreview) {
    val palette = beyGramPalette()
    if (!chat.lastMessageOutgoing) return
    // The gap to the neighbouring marker comes from the trailing group, not from the glyph.
    val modifier = Modifier.size(16.dp)
    // The row speaks one merged description (see ChatListItem), so the glyph itself stays decorative.
    when (chat.lastMessageSendState) {
        MessageSendState.Sending -> Icon(Icons.Rounded.Schedule, null, tint = palette.textSecondary, modifier = modifier)
        MessageSendState.Failed -> Icon(Icons.Rounded.ErrorOutline, null, tint = palette.warningText, modifier = modifier)
        MessageSendState.Sent -> {
            val read = chat.lastMessageId > 0L && chat.lastMessageId <= chat.lastReadOutboxMessageId
            if (read) Icon(Icons.Rounded.DoneAll, null, tint = palette.accent, modifier = modifier)
            else Icon(Icons.Rounded.Done, null, tint = palette.textSecondary, modifier = modifier)
        }
    }
}

/** Плотность списка чатов: сколько места отдано каждой строке. [Default] — вид, который BeyGram всегда имел. */
internal data class ChatListDensity(
    /** Отступ над и под содержимым строки чата. */
    val rowVerticalPadding: Dp = 10.dp,
    /** Comfortable/Compact keep the familiar single line; Spacious opts into two lines. */
    val previewLines: Int = 1
)

internal val LocalChatListDensity = compositionLocalOf { ChatListDensity() }

/** Строки списка чатов для выбранной плотности. [ListDensity.Comfortable] даёт прежний отступ строки. */
internal fun chatListDensityFor(density: ListDensity): ChatListDensity = when (density) {
    ListDensity.Compact -> ChatListDensity(rowVerticalPadding = 6.dp)
    ListDensity.Comfortable -> ChatListDensity()
    ListDensity.Spacious -> ChatListDensity(rowVerticalPadding = 14.dp, previewLines = 2)
}

/**
 * The fill of the row of the chat open in the right pane, per style: the Signature ink tile, the Material 3 secondary
 * container, 12 % of the main colour for Material 2, a tonal lens on glass and the accent wash of Default.
 */
@Composable
internal fun openChatRowFill(design: com.beykus.beygram.ui.design.DesignSystem, accentSoft: Color, accent: Color, ink: Color): Color = when {
    design.style == DesignStyle.Signature -> ink.copy(alpha = 0.08f)
    design.style == DesignStyle.Material3 -> MaterialTheme.colorScheme.secondaryContainer
    design.style == DesignStyle.Material2 -> accent.copy(alpha = 0.12f)
    design.usesGlassPanels -> ink.copy(alpha = 0.10f)
    else -> accentSoft
}

/** Telegram shows a chat as unread either because of a server unread count or an explicit manual marker. */
internal fun showsUnreadIndicator(chat: ChatPreview): Boolean = chat.unreadCount > 0 || chat.isMarkedAsUnread

/** Air between the preview text and the trailing markers of a chat row, and between the markers themselves. */
private val TrailingIndicatorStart = 10.dp
private val TrailingIndicatorGap = 6.dp

@Composable
internal fun ChatListItem(chat: ChatPreview, observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit,
    onClick: () -> Unit, onLongClick: (() -> Unit)? = null, highlighted: Boolean = false) {
    val palette = beyGramPalette()
    val listDensity = LocalChatListDensity.current
    val design = LocalDesignSystem.current
    val main = design.mainScreen
    // A short window fits one more row with the 44 dp avatar and 6 dp padding, unless the user chose another density.
    val shortWindow = LocalWindowLayout.current.heightCompact
    val rowPadding = if (shortWindow && listDensity.rowVerticalPadding == ChatListDensity().rowVerticalPadding) 6.dp
    else listDensity.rowVerticalPadding
    val avatarSize = if (shortWindow) minOf(main.avatarSize, 44.dp) else main.avatarSize
    val unread = showsUnreadIndicator(chat)
    val emptyPreview = (chat.lastMessage as? UiText.Raw)?.text?.isBlank() == true
    val preview = previewLine(chat, emptyPreview)
    val unreadLabel = if (unread) {
        if (chat.unreadCount > 0) stringResource(R.string.a11y_chat_unread_count, chat.unreadCount)
        else stringResource(R.string.a11y_chat_unread_marked)
    } else null
    val description = accessibleChatRowDescription(
        title = chat.title,
        preview = preview,
        time = chat.lastMessageTime,
        unreadLabel = unreadLabel,
        mutedLabel = if (chat.isMuted) stringResource(R.string.a11y_chat_muted) else null,
        statusLabel = sendStatusLabel(chat),
        secretLabel = if (chat.isSecret) stringResource(R.string.chats_secret_chat) else null
    )
    // Signature draws the row as a rounded tile inside the list: the press and the open-chat highlight stop short of the
    // screen edge. The content keeps the same 20dp start position as every other style.
    val tileShape = RoundedCornerShape(main.rowRadius)
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (main.rowInset > 0.dp) Modifier.padding(horizontal = main.rowInset).clip(tileShape) else Modifier)
            .background(if (highlighted) openChatRowFill(design, palette.accentSoft, palette.accent, palette.textPrimary) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                // TalkBack announces which conversation is already open in the right pane.
                if (highlighted) selected = true
            }
            .padding(horizontal = (20.dp - main.rowInset).coerceAtLeast(0.dp), vertical = rowPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChatAvatar(chat, avatarSize, observeFile, downloadFile, Modifier.clearAndSetSemantics {})
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(chat.title, color = palette.textPrimary, fontSize = 16.sp, fontWeight = main.rowTitleWeight, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (chat.isSecret) Icon(Icons.Rounded.Lock, null, tint = palette.accent, modifier = Modifier.padding(start = 4.dp).size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    chat.lastMessageTime,
                    color = if (unread) palette.accent else palette.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 72.dp)
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (chat.isMuted) Icon(Icons.Rounded.NotificationsOff, null, tint = palette.textSecondary, modifier = Modifier.padding(end = 6.dp).size(16.dp))
                Text(
                    preview,
                    color = when {
                        chat.lastMessageOutgoing && chat.lastMessageSendState == MessageSendState.Failed -> palette.warningText
                        unread && main.unreadPreviewEmphasis && !chat.isMuted -> palette.textPrimary
                        else -> palette.textSecondary
                    },
                    fontSize = 14.sp,
                    maxLines = listDensity.previewLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // The send status and the unread marker share one trailing group. The group keeps its own air from the
                // preview text and a fixed gap from its neighbour, so a preview that ends in a symbol never runs into a count.
                if (chat.lastMessageOutgoing || unread) {
                    Row(
                        modifier = Modifier.padding(start = TrailingIndicatorStart),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TrailingIndicatorGap)
                    ) {
                        SendStatusIcon(chat)
                        if (unread) {
                            // Чат без звука получает серый счётчик, как в Telegram. Ручная отметка без сообщений — точка.
                            val badgeColor = if (chat.isMuted) palette.textSecondary else palette.accent
                            if (chat.unreadCount > 0) {
                                // The 22dp pill grows with the system font scale, so large digits are not clipped; at 1x and
                                // below every style keeps exactly the pill it had (its text line is taller than 22dp in some
                                // styles, so a plain minimum height would change them).
                                val badgeScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
                                val badgeSize = 22.dp * badgeScale
                                Box(modifier = Modifier.defaultMinSize(minWidth = badgeSize).height(badgeSize).clip(RoundedCornerShape(main.badgeRadius * badgeScale)).background(badgeColor).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
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
    }
}
