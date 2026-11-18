package com.beykus.beygram.ui.screens.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.settings.ForumNavigationMode
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.CustomEmojiSetBrief
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.domain.model.ForumTopicDefaultColor
import com.beykus.beygram.domain.model.ForumTopicIcon
import com.beykus.beygram.domain.model.ForumTopicsPage
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.ProfileInfo
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.BeyGramSheet
import com.beykus.beygram.ui.components.ChatAvatar
import com.beykus.beygram.ui.components.LocalWindowLayout
import com.beykus.beygram.ui.components.SearchField
import com.beykus.beygram.ui.components.SheetActionRow
import com.beykus.beygram.ui.components.TextButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.topAppBarSurface
import com.beykus.beygram.ui.design.ChipSelectionTone
import com.beykus.beygram.ui.design.HairlineEdge
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.PanelFloor
import com.beykus.beygram.ui.design.TopBarVariant
import com.beykus.beygram.ui.design.panelFill
import com.beykus.beygram.ui.design.signatureChrome
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.rememberMotionSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** Debounce of the topic search; typing a name must not fire a TDLib request per keystroke. */
private const val ForumTopicSearchDelayMillis = 280L

private sealed interface TopicSearchState {
    data object Idle : TopicSearchState
    data object Loading : TopicSearchState
    data object Failed : TopicSearchState
    data class Loaded(val topics: List<ForumTopic>) : TopicSearchState
}

/** Pure list layout: only the pinned group is moved ahead of other Telegram-ordered topics. */
internal fun orderedForumTopics(topics: List<ForumTopic>): List<ForumTopic> {
    val unique = topics.distinctBy(ForumTopic::id)
    return unique.filter(ForumTopic::isPinned) + unique.filterNot(ForumTopic::isPinned)
}

/** The All/Unread chip filter; unknown unread data never becomes a zero count. */
internal fun filteredForumTopics(topics: List<ForumTopic>, unreadOnly: Boolean): List<ForumTopic> =
    if (unreadOnly) topics.filter(ForumTopic::hasUnread) else topics

/**
 * Shows a dropped pinned order over the server order. [ordered] is [orderedForumTopics] output, so the pinned topics
 * come first. Pinned topics that [pinnedOrder] does not name keep their server order after the named ones; a name that is
 * no longer pinned is ignored. The regular topics are untouched.
 */
internal fun applyPinnedOrder(ordered: List<ForumTopic>, pinnedOrder: List<Long>?): List<ForumTopic> {
    if (pinnedOrder == null) return ordered
    val pinned = ordered.filter(ForumTopic::isPinned)
    val byId = pinned.associateBy(ForumTopic::id)
    val named = pinnedOrder.mapNotNull { byId[it] }
    val namedIds = named.mapTo(HashSet(), ForumTopic::id)
    return named + pinned.filterNot { it.id in namedIds } + ordered.filterNot(ForumTopic::isPinned)
}

/** The order after the item at [fromIndex] moves to [toIndex], which is its final index. Equal or out-of-range indices keep [order]. */
internal fun moveForumTopicId(order: List<Long>, fromIndex: Int, toIndex: Int): List<Long> {
    if (fromIndex !in order.indices || toIndex !in order.indices || fromIndex == toIndex) return order
    return order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
}

/**
 * The pinned index a dragged row drops to. [baseRank] is the rank of the first pinned row on screen, and [otherCentres]
 * are the vertical centres of the other pinned rows on screen. Every row whose centre is above the dragged row's centre
 * stays before it, so the row lands after those rows.
 */
internal fun pinnedDropIndex(baseRank: Int, otherCentres: List<Float>, draggedCentre: Float): Int =
    baseRank + otherCentres.count { it < draggedCentre }

/**
 * The pre-first-page shell. It keeps the parent group in the header and paints row geometry instead of a full-screen
 * spinner, so Back and the back gesture remain available while TDLib is slow.
 */
@Composable
fun ForumTopicsLoadingScreen(
    chat: ChatPreview,
    onBack: () -> Unit,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit
) {
    val palette = beyGramPalette()
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        ForumTopicsHeader(
            chat = chat,
            subtitle = stringResource(R.string.forum_topics_loading),
            onBack = onBack,
            onOpenInfo = null,
            observeFile = observeFile,
            downloadFile = downloadFile,
            actions = {}
        )
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        ForumTopicSkeletonList(
            rows = 7,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }
}

/** The first topic page failed: keep the parent header, Back and a retry instead of stranding the user on a blank screen. */
@Composable
fun ForumTopicsFailureScreen(
    chat: ChatPreview,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit
) {
    val palette = beyGramPalette()
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        ForumTopicsHeader(
            chat = chat,
            subtitle = stringResource(R.string.forum_topics_load_failed_hint),
            onBack = onBack,
            onOpenInfo = null,
            observeFile = observeFile,
            downloadFile = downloadFile,
            actions = {}
        )
        ForumStateMessage(
            icon = Icons.Rounded.ErrorOutline,
            title = stringResource(R.string.forum_topics_load_failed),
            body = stringResource(R.string.forum_topics_load_failed_hint),
            actionLabel = stringResource(R.string.common_retry),
            onAction = onRetry,
            modifier = Modifier.weight(1f)
        )
    }
}

/** The forum topic list: a dense chat-like list with in-place search, unread filtering and long-press management. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumTopicsScreen(
    chatId: Long,
    accountId: String = "legacy",
    chat: ChatPreview,
    chatInfo: ProfileInfo?,
    topics: List<ForumTopic>,
    totalCount: Int,
    topicsComplete: Boolean,
    loadingTopics: Boolean = false,
    loadError: Boolean = false,
    onRetryLoad: () -> Unit = {},
    searchTopics: suspend (String, (List<ForumTopic>) -> Unit) -> Result<List<ForumTopic>>,
    canCreate: Boolean,
    busy: Boolean,
    actionError: String?,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>,
    onBack: () -> Unit,
    onOpenGroupInfo: () -> Unit,
    onTopic: (ForumTopic) -> Unit,
    /** Reports null on success or a user-visible error while keeping the editor and its input open. */
    onCreate: (String, ForumTopicIcon, (String?) -> Unit) -> Unit,
    /** The custom emoji id is null when the icon is left as it is; 0 removes the custom emoji. */
    onEdit: (ForumTopic, String, Long?, (String?) -> Unit) -> Unit,
    loadDefaultIcons: suspend () -> Result<List<Long>>,
    /** Premium accounts get their installed custom emoji sets in the icon editor; other accounts get a hint. */
    premium: Boolean = false,
    loadCustomEmojiSets: suspend () -> Result<List<CustomEmojiSetBrief>> = { Result.success(emptyList()) },
    loadCustomEmojiSetIds: suspend (Long) -> Result<List<Long>> = { Result.success(emptyList()) },
    onClosed: (ForumTopic, Boolean) -> Unit,
    onPinned: (ForumTopic, Boolean) -> Unit,
    /**
     * The whole new order of the pinned topics, first on top. The reply is null on success or a user-visible error; a
     * failure also puts the server order back on screen.
     */
    onPinnedOrder: (List<Long>, (String?) -> Unit) -> Unit,
    /** Null hands the topic back to the group's notification setting. */
    onMuted: (ForumTopic, Boolean?) -> Unit = { _, _ -> },
    onDelete: (ForumTopic) -> Unit,
    onDismissActionError: () -> Unit = {},
    /** The forum's view of its topics. Classic is this list; the other views open a topic at once and keep the list one tap away. */
    navigationMode: ForumNavigationMode = ForumNavigationMode.Classic,
    /** Null hides the view button; the button and its sheet appear only when the host can store the choice. */
    onNavigationMode: ((ForumNavigationMode) -> Unit)? = null,
    /** Opens the feed of every topic's messages from the view sheet; null leaves that action out. */
    onAllMessages: (() -> Unit)? = null
) {
    val palette = beyGramPalette()
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }
    var searchOpen by rememberSaveable(accountId, chatId) { mutableStateOf(false) }
    var query by rememberSaveable(accountId, chatId) { mutableStateOf("") }
    var unreadOnly by rememberSaveable(accountId, chatId) { mutableStateOf(false) }
    var creating by rememberSaveable(accountId, chatId) { mutableStateOf(false) }
    var name by rememberSaveable(accountId, chatId) { mutableStateOf("") }
    var colorArgb by rememberSaveable(accountId, chatId) { mutableStateOf(ForumTopicDefaultColor) }
    var iconEmojiId by rememberSaveable(accountId, chatId) { mutableStateOf(0L) }
    var editing by remember(accountId, chatId) { mutableStateOf<ForumTopic?>(null) }
    var deleting by remember(accountId, chatId) { mutableStateOf<ForumTopic?>(null) }
    var managing by remember(accountId, chatId) { mutableStateOf<ForumTopic?>(null) }
    var modeSheetOpen by remember(accountId, chatId) { mutableStateOf(false) }
    var submitting by remember(accountId, chatId) { mutableStateOf(false) }
    var editorError by remember(accountId, chatId) { mutableStateOf<String?>(null) }
    var searchAttempt by remember(accountId, chatId) { mutableIntStateOf(0) }
    var searchState by remember(accountId, chatId) { mutableStateOf<TopicSearchState>(TopicSearchState.Idle) }
    var searchDebouncing by remember(accountId, chatId) { mutableStateOf(false) }
    var searchPaging by remember(accountId, chatId) { mutableStateOf(false) }
    // Reorder mode drags pinned topics only; the filter and search are out of the way while it lasts.
    var reordering by rememberSaveable(accountId, chatId) { mutableStateOf(false) }
    // The pinned order the user dropped. It stays on screen until the server list arrives or the request fails.
    var pinnedDraft by remember(accountId, chatId) { mutableStateOf<List<Long>?>(null) }
    val normalizedQuery = query.trim()
    val searchActive = searchOpen && normalizedQuery.isNotEmpty()
    val page = remember(topics, totalCount, topicsComplete) {
        ForumTopicsPage(topics = topics, totalCount = totalCount, isComplete = topicsComplete)
    }
    val orderedLocal = remember(page.topics, pinnedDraft) { applyPinnedOrder(orderedForumTopics(page.topics), pinnedDraft) }
    val visibleLocal = remember(orderedLocal, unreadOnly) { filteredForumTopics(orderedLocal, unreadOnly) }
    val searchResult = searchState as? TopicSearchState.Loaded
    val orderedSearch = remember(searchResult) { searchResult?.let { orderedForumTopics(it.topics) }.orEmpty() }
    val visibleSearch = remember(orderedSearch, unreadOnly) { filteredForumTopics(orderedSearch, unreadOnly) }
    val headerBaseTopics = if (searchActive) orderedSearch else orderedLocal
    val unreadTopicCount = remember(headerBaseTopics) { headerBaseTopics.count(ForumTopic::hasUnread) }
    val subtitle = forumTopicsSubtitle(page, topicsComplete, loadingTopics, chatInfo?.memberCount)
    val savedPosition: ChatScrollPositionViewModel = viewModel(key = "forum-scroll-$accountId-$chatId")
    val listState = key(accountId, chatId) {
        rememberLazyListState(savedPosition.firstVisibleItemIndex, savedPosition.firstVisibleItemScrollOffset)
    }
    val searchListState = key(accountId, chatId) { rememberLazyListState() }
    val listSpec = rememberMotionSpec(MotionToken.ListReorder)
    val listFadeSpec = if (listSpec.isInstant) snap<Float>() else listSpec.animationSpec<Float>()
    val listPlacementSpec = if (listSpec.isInstant) snap<IntOffset>() else listSpec.animationSpec<IntOffset>()

    DisposableEffect(listState) {
        onDispose {
            savedPosition.firstVisibleItemIndex = listState.firstVisibleItemIndex
            savedPosition.firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
        }
    }
    LaunchedEffect(searchActive, normalizedQuery) {
        // A new query starts at the top; keeping the old index would land the user in the middle of new results.
        if (searchActive) searchListState.scrollToItem(0)
    }
    // The dropped order stays until the server list shows it, or until the server's pinned set changes (a topic was
    // pinned or unpinned elsewhere). An unrelated update in between, such as a new message, keeps the dropped order.
    LaunchedEffect(topics) {
        val draft = pinnedDraft ?: return@LaunchedEffect
        val serverPinned = orderedForumTopics(topics).filter(ForumTopic::isPinned).map(ForumTopic::id)
        if (serverPinned == draft || serverPinned.toSet() != draft.toSet()) pinnedDraft = null
    }
    LaunchedEffect(reordering) {
        // The pinned block sits at the top of the list, and a reorder needs all of it on screen.
        if (reordering && listState.firstVisibleItemIndex > 0) listState.animateScrollToItem(0)
    }
    LaunchedEffect(accountId, chatId, searchActive, normalizedQuery, searchAttempt) {
        if (!searchActive) {
            searchState = TopicSearchState.Idle
            searchDebouncing = false
            searchPaging = false
            return@LaunchedEffect
        }
        // Keep the previous results visible while the user is still typing; only a real request shows the skeleton.
        searchDebouncing = true
        delay(ForumTopicSearchDelayMillis)
        searchDebouncing = false
        searchState = TopicSearchState.Loading
        searchPaging = true
        try {
            searchState = searchTopics(normalizedQuery) { progressive ->
                // Show the first server page while the rest of a broad query is still being fetched.
                searchState = TopicSearchState.Loaded(progressive)
            }.fold(
                onSuccess = { TopicSearchState.Loaded(it) },
                onFailure = { TopicSearchState.Failed }
            )
        } finally {
            searchPaging = false
        }
    }
    LaunchedEffect(searchOpen) {
        if (searchOpen) searchFocus.requestFocus()
    }
    fun closeSearch() {
        searchOpen = false
        query = ""
        searchState = TopicSearchState.Idle
        searchDebouncing = false
        searchPaging = false
        keyboard?.hide()
        focusManager.clearFocus()
    }
    // Back leaves reorder mode first, so the list the user was reordering stays on screen.
    val back: () -> Unit = { if (reordering) reordering = false else if (searchOpen || normalizedQuery.isNotEmpty()) closeSearch() else onBack() }
    val openCreate: () -> Unit = {
        name = ""
        colorArgb = ForumTopicDefaultColor
        iconEmojiId = 0L
        editing = null
        editorError = null
        creating = true
    }
    val manageTopic: (ForumTopic) -> Unit = { topic ->
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        managing = topic
    }
    // Reorder mode takes the whole list: search and the unread filter would show only part of the pinned order.
    val startReorder: () -> Unit = {
        if (searchOpen) closeSearch()
        unreadOnly = false
        reordering = true
    }
    // A drop or an accessibility move shows the new pinned order at once and sends the whole order. A move while the
    // screen is busy is ignored, so the shown order never gets ahead of the server without a request behind it.
    val movePinned: (Int, Int) -> Unit = { fromIndex, toIndex ->
        val current = orderedLocal.filter(ForumTopic::isPinned).map(ForumTopic::id)
        val next = moveForumTopicId(current, fromIndex, toIndex)
        if (!busy && next != current) {
            pinnedDraft = next
            onPinnedOrder(next) { error -> if (error != null) pinnedDraft = null }
        }
    }
    val openModeSheet: () -> Unit = { modeSheetOpen = true }
    BackHandler(onBack = back)

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        ForumTopicsHeader(
            chat = chat,
            subtitle = subtitle,
            onBack = back,
            onOpenInfo = onOpenGroupInfo,
            observeFile = observeFile,
            downloadFile = downloadFile,
            actions = {
                ForumHeaderActions(
                    searchOpen = searchOpen,
                    canCreate = canCreate,
                    busy = busy,
                    onToggleSearch = {
                        reordering = false
                        if (searchOpen) closeSearch() else searchOpen = true
                    },
                    onCreate = openCreate,
                    onViewMode = if (onNavigationMode != null) openModeSheet else null
                )
            }
        ) {
            if (searchOpen) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    SearchField(
                        query = query,
                        onChange = { query = it },
                        placeholder = stringResource(R.string.forum_topics_search_placeholder),
                        onSearch = { keyboard?.hide() },
                        focusRequester = searchFocus
                    )
                }
            }
            if (reordering) {
                ForumReorderBar(onDone = { reordering = false })
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ForumFilterChip(
                        title = stringResource(R.string.forum_topics_filter_all),
                        selected = !unreadOnly,
                        count = null,
                        onClick = { unreadOnly = false }
                    )
                    ForumFilterChip(
                        title = stringResource(R.string.forum_topics_filter_unread),
                        selected = unreadOnly,
                        count = unreadTopicCount.takeIf { it > 0 },
                        onClick = { unreadOnly = true }
                    )
                }
            }
            if (busy || loadingTopics || searchDebouncing || searchPaging ||
                searchState is TopicSearchState.Loading
            ) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
            }
        }
        if (actionError != null) {
            ForumInlineBanner(
                text = actionError,
                actionLabel = null,
                onAction = null,
                danger = true,
                onDismiss = onDismissActionError
            )
        }
        if (loadError && page.topics.isNotEmpty()) {
            ForumInlineBanner(
                text = stringResource(R.string.forum_topics_partial_load_failed),
                actionLabel = stringResource(R.string.common_retry),
                actionEnabled = !loadingTopics,
                onAction = onRetryLoad,
                danger = false
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                searchActive -> SearchContent(
                    state = searchState,
                    visible = visibleSearch,
                    listState = searchListState,
                    unreadOnly = unreadOnly,
                    parentMuted = chat.isMuted,
                    busy = busy,
                    listFadeSpec = listFadeSpec,
                    listPlacementSpec = listPlacementSpec,
                    observeFile = observeFile,
                    downloadFile = downloadFile,
                    observeCustomEmoji = observeCustomEmoji,
                    onTopic = onTopic,
                    onManage = manageTopic,
                    onRetry = { searchAttempt++ },
                    onShowAll = { unreadOnly = false }
                )
                page.topics.isEmpty() -> when {
                    loadingTopics -> ForumTopicSkeletonList(6)
                    loadError -> ForumStateMessage(
                        icon = Icons.Rounded.ErrorOutline,
                        title = stringResource(R.string.forum_topics_load_failed),
                        body = stringResource(R.string.forum_topics_load_failed_hint),
                        actionLabel = stringResource(R.string.common_retry),
                        actionEnabled = !loadingTopics,
                        onAction = onRetryLoad,
                        announce = true
                    )
                    else -> ForumStateMessage(
                        icon = Icons.Rounded.Forum,
                        title = stringResource(R.string.forum_topics_empty),
                        body = stringResource(R.string.forum_topics_empty_hint),
                        actionLabel = stringResource(R.string.forum_create).takeIf { canCreate },
                        actionEnabled = !busy,
                        onAction = openCreate.takeIf { canCreate }
                    )
                }
                unreadOnly && visibleLocal.isEmpty() -> ForumStateMessage(
                    icon = Icons.Rounded.Forum,
                    title = stringResource(R.string.forum_topics_no_unread),
                    body = stringResource(R.string.forum_topics_no_unread_hint),
                    actionLabel = stringResource(R.string.forum_topics_filter_all),
                    onAction = { unreadOnly = false }
                )
                else -> ForumTopicList(
                    topics = visibleLocal,
                    listState = listState,
                    showSectionLabels = true,
                    showSectionCounts = !unreadOnly,
                    parentMuted = chat.isMuted,
                    busy = busy,
                    loadingMore = loadingTopics,
                    listFadeSpec = listFadeSpec,
                    listPlacementSpec = listPlacementSpec,
                    observeFile = observeFile,
                    downloadFile = downloadFile,
                    observeCustomEmoji = observeCustomEmoji,
                    onTopic = onTopic,
                    onManage = manageTopic,
                    reordering = reordering,
                    onMovePinned = movePinned
                )
            }
        }
    }

    if (creating || editing != null) {
        val target = editing
        val dismissEditor = {
            if (!busy && !submitting) {
                creating = false
                editing = null
            }
        }
        ForumTopicEditorSheet(
            topic = target,
            name = name,
            onNameChange = {
                name = it
                editorError = null
            },
            colorArgb = colorArgb,
            onColorChange = { colorArgb = it },
            iconEmojiId = iconEmojiId,
            onIconChange = { iconEmojiId = it },
            loadDefaultIcons = loadDefaultIcons,
            premium = premium,
            loadCustomEmojiSets = loadCustomEmojiSets,
            loadCustomEmojiSetIds = loadCustomEmojiSetIds,
            observeFile = observeFile,
            downloadFile = downloadFile,
            observeCustomEmoji = observeCustomEmoji,
            busy = busy,
            submitting = submitting,
            error = editorError,
            onSave = {
                editorError = null
                submitting = true
                val done: (String?) -> Unit = { error ->
                    submitting = false
                    if (error == null) {
                        // An edit changes the row in an open search result; reload it once the server accepted it.
                        if (target != null) searchAttempt++
                        creating = false
                        editing = null
                    } else {
                        // Keep the editor and the typed input: a failed save must not throw away the user's work.
                        editorError = error
                    }
                }
                if (target == null) {
                    onCreate(name.trim(), ForumTopicIcon(colorArgb = colorArgb, customEmojiId = iconEmojiId), done)
                } else {
                    onEdit(target, name.trim(), forumTopicIconUpdate(target, iconEmojiId), done)
                }
            },
            onDismiss = dismissEditor
        )
    }

    deleting?.let { topic ->
        BeyGramDialog(
            onDismissRequest = { deleting = null },
            title = stringResource(R.string.forum_delete),
            message = stringResource(R.string.forum_delete_confirm, topic.title),
            confirmText = stringResource(R.string.common_delete),
            confirmEnabled = !busy,
            destructive = true,
            onConfirm = {
                deleting = null
                searchAttempt++
                onDelete(topic)
            },
            dismissText = stringResource(R.string.common_cancel),
            onDismiss = { deleting = null }
        )
    }

    managing?.let { topic ->
        BeyGramSheet(
            onDismissRequest = { managing = null },
            title = forumTopicDisplayTitle(topic)
        ) {
            if (topic.canManage) {
                SheetActionRow(
                    icon = Icons.Rounded.Edit,
                    title = stringResource(R.string.forum_edit),
                    enabled = !busy,
                    onClick = {
                        managing = null
                        name = topic.title
                        iconEmojiId = topic.icon?.customEmojiId ?: 0L
                        editorError = null
                        creating = false
                        editing = topic
                    }
                )
                SheetActionRow(
                    icon = if (topic.isClosed) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                    title = stringResource(if (topic.isClosed) R.string.forum_reopen else R.string.forum_close),
                    enabled = !busy,
                    onClick = {
                        managing = null
                        searchAttempt++
                        onClosed(topic, !topic.isClosed)
                    }
                )
            }
            if (topic.canPin) {
                SheetActionRow(
                    icon = Icons.Rounded.PushPin,
                    title = stringResource(if (topic.isPinned) R.string.forum_unpin else R.string.forum_pin),
                    enabled = !busy,
                    onClick = {
                        managing = null
                        searchAttempt++
                        onPinned(topic, !topic.isPinned)
                    }
                )
                // The order only means something between two or more pinned topics, and only from the full list.
                if (topic.isPinned && !searchActive && orderedLocal.count(ForumTopic::isPinned) >= 2) {
                    SheetActionRow(
                        icon = Icons.Rounded.DragHandle,
                        title = stringResource(R.string.forum_topics_reorder),
                        enabled = !busy,
                        onClick = {
                            managing = null
                            startReorder()
                        }
                    )
                }
            }
            // Mute works for every topic. A topic that inherits the group reads the group's state; the reset row hands it back.
            val topicMuted = topic.isMutedIn(chat.isMuted)
            SheetActionRow(
                icon = if (topicMuted) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff,
                title = stringResource(if (topicMuted) R.string.feature_unmute_chat else R.string.feature_mute_chat),
                enabled = !busy,
                onClick = {
                    managing = null
                    searchAttempt++
                    onMuted(topic, !topicMuted)
                }
            )
            if (!topic.useDefaultMuteFor) {
                SheetActionRow(
                    icon = Icons.Rounded.People,
                    title = stringResource(R.string.forum_topic_mute_default),
                    enabled = !busy,
                    onClick = {
                        managing = null
                        searchAttempt++
                        onMuted(topic, null)
                    }
                )
            }
            if (canDeleteForumTopic(topic)) {
                SheetActionRow(
                    icon = Icons.Rounded.Delete,
                    title = stringResource(R.string.forum_delete),
                    enabled = !busy,
                    danger = true,
                    onClick = {
                        managing = null
                        deleting = topic
                    }
                )
            }
        }
    }

    val setNavigationMode = onNavigationMode
    val openAllMessagesFromSheet: (() -> Unit)? = onAllMessages?.let { open ->
        val action: () -> Unit = {
            modeSheetOpen = false
            open()
        }
        action
    }
    if (modeSheetOpen && setNavigationMode != null) {
        ForumNavigationModeSheet(
            current = navigationMode,
            onSelect = { mode ->
                modeSheetOpen = false
                setNavigationMode(mode)
            },
            onDismiss = { modeSheetOpen = false },
            onAllMessages = openAllMessagesFromSheet
        )
    }
}

@Composable
private fun SearchContent(
    state: TopicSearchState,
    visible: List<ForumTopic>,
    listState: LazyListState,
    unreadOnly: Boolean,
    parentMuted: Boolean,
    busy: Boolean,
    listFadeSpec: FiniteAnimationSpec<Float>,
    listPlacementSpec: FiniteAnimationSpec<IntOffset>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>,
    onTopic: (ForumTopic) -> Unit,
    onManage: (ForumTopic) -> Unit,
    onRetry: () -> Unit,
    onShowAll: () -> Unit
) {
    when (state) {
        TopicSearchState.Idle, TopicSearchState.Loading -> ForumTopicSkeletonList(5)
        TopicSearchState.Failed -> ForumStateMessage(
            icon = Icons.Rounded.ErrorOutline,
            title = stringResource(R.string.forum_topics_search_failed),
            body = stringResource(R.string.forum_topics_search_failed_hint),
            actionLabel = stringResource(R.string.common_retry),
            onAction = onRetry,
            announce = true
        )
        is TopicSearchState.Loaded -> when {
            state.topics.isEmpty() -> ForumStateMessage(
                icon = Icons.Rounded.Search,
                title = stringResource(R.string.forum_topics_search_empty),
                body = stringResource(R.string.forum_topics_search_empty_hint)
            )
            visible.isEmpty() && unreadOnly -> ForumStateMessage(
                icon = Icons.Rounded.Forum,
                title = stringResource(R.string.forum_topics_no_unread),
                body = stringResource(R.string.forum_topics_no_unread_hint),
                actionLabel = stringResource(R.string.forum_topics_filter_all),
                onAction = onShowAll
            )
            else -> ForumTopicList(
                topics = visible,
                listState = listState,
                showSectionLabels = false,
                showSectionCounts = false,
                parentMuted = parentMuted,
                busy = busy,
                loadingMore = false,
                listFadeSpec = listFadeSpec,
                listPlacementSpec = listPlacementSpec,
                observeFile = observeFile,
                downloadFile = downloadFile,
                observeCustomEmoji = observeCustomEmoji,
                onTopic = onTopic,
                onManage = onManage
            )
        }
    }
}

@Composable
private fun ForumTopicList(
    topics: List<ForumTopic>,
    listState: LazyListState,
    showSectionLabels: Boolean,
    showSectionCounts: Boolean,
    parentMuted: Boolean,
    busy: Boolean,
    loadingMore: Boolean,
    listFadeSpec: FiniteAnimationSpec<Float>,
    listPlacementSpec: FiniteAnimationSpec<IntOffset>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>,
    onTopic: (ForumTopic) -> Unit,
    onManage: (ForumTopic) -> Unit,
    /** Reorder mode: pinned rows show a drag handle, and every row stops opening or opening its menu until it ends. */
    reordering: Boolean = false,
    /** A pinned row moves from rank [fromIndex] to rank [toIndex]; the caller applies the order and sends it. */
    onMovePinned: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> }
) {
    val haptics = LocalHapticFeedback.current
    val pinned = remember(topics) { topics.filter(ForumTopic::isPinned) }
    val regular = remember(topics) { topics.filterNot(ForumTopic::isPinned) }
    // The dragged pinned row. Its position follows the finger; the order changes once, on drop.
    var drag by remember { mutableStateOf<PinnedTopicDrag?>(null) }
    LaunchedEffect(reordering) { if (!reordering) drag = null }
    fun startDrag(topicId: Long) {
        // A row that left composition mid-drag never cancels its drag, so a new drag simply replaces the old one.
        if (busy) return
        val fromRank = pinned.indexOfFirst { it.id == topicId }
        if (fromRank < 0) return
        val onScreen = listState.layoutInfo.visibleItemsInfo
        // Only rows on screen have a known position. The pinned block is contiguous, so the rows on screen are a run of
        // ranks, and the drop is worked out from their centres.
        val rows = pinned.mapIndexedNotNull { rank, topic ->
            onScreen.firstOrNull { it.key == forumTopicItemKey(topic.id) }?.let { rank to it }
        }
        val dragged = rows.firstOrNull { it.first == fromRank }?.second ?: return
        drag = PinnedTopicDrag(
            topicId = topicId,
            fromRank = fromRank,
            baseRank = rows.first().first,
            startCentre = (dragged.offset + dragged.size / 2).toFloat(),
            otherCentres = rows.filter { it.first != fromRank }.map { (_, info) -> (info.offset + info.size / 2).toFloat() }
        )
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    fun dragBy(amount: Float) {
        val session = drag ?: return
        session.offset += amount
        session.dropRank = pinnedDropIndex(session.baseRank, session.otherCentres, session.startCentre + session.offset)
    }
    fun endDrag() {
        val session = drag ?: return
        drag = null
        // The move only applies to the row that was grabbed, if it is still at the rank it was grabbed from.
        val stillThere = pinned.getOrNull(session.fromRank)?.id == session.topicId
        if (stillThere && session.dropRank != session.fromRank) onMovePinned(session.fromRank, session.dropRank)
    }
    fun cancelDrag() {
        drag = null
    }
    fun pinnedReorder(rank: Int, topic: ForumTopic): ForumTopicRowReorder {
        val session = drag
        return ForumTopicRowReorder(
            onDragStart = { startDrag(topic.id) },
            onDrag = { amount -> dragBy(amount) },
            onDragEnd = { endDrag() },
            onDragCancel = { cancelDrag() },
            onMoveUp = if (rank > 0 && !busy) ({ onMovePinned(rank, rank - 1) }) else null,
            onMoveDown = if (rank < pinned.lastIndex && !busy) ({ onMovePinned(rank, rank + 1) }) else null,
            highlighted = session != null && session.dropRank != session.fromRank && session.dropRank == rank
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
    ) {
        if (showSectionLabels) {
            if (pinned.isNotEmpty()) {
                item(key = "forum-topic-pinned-label") {
                    ForumTopicSectionLabel(
                        stringResource(R.string.forum_topics_pinned_section),
                        pinned.size.takeIf { showSectionCounts }
                    )
                }
                itemsIndexed(pinned, key = { _, topic -> forumTopicItemKey(topic.id) }) { rank, topic ->
                    ForumTopicListItem(
                        topic = topic,
                        parentMuted = parentMuted,
                        busy = busy,
                        listFadeSpec = listFadeSpec,
                        listPlacementSpec = listPlacementSpec,
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        observeCustomEmoji = observeCustomEmoji,
                        onTopic = onTopic,
                        onManage = onManage,
                        reordering = reordering,
                        reorder = if (reordering) pinnedReorder(rank, topic) else null,
                        dragging = drag?.takeIf { it.topicId == topic.id }
                    )
                }
            }
            if (regular.isNotEmpty()) {
                if (pinned.isNotEmpty()) {
                    item(key = "forum-topic-regular-label") {
                        ForumTopicSectionLabel(
                            stringResource(R.string.forum_topics_regular_section),
                            regular.size.takeIf { showSectionCounts }
                        )
                    }
                }
                items(regular, key = { "forum-topic:${it.id}" }) { topic ->
                    ForumTopicListItem(
                        topic = topic,
                        parentMuted = parentMuted,
                        busy = busy,
                        listFadeSpec = listFadeSpec,
                        listPlacementSpec = listPlacementSpec,
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        observeCustomEmoji = observeCustomEmoji,
                        onTopic = onTopic,
                        onManage = onManage,
                        reordering = reordering
                    )
                }
            }
        } else {
            items(topics, key = { "forum-topic:${it.id}" }) { topic ->
                ForumTopicListItem(
                    topic = topic,
                    parentMuted = parentMuted,
                    busy = busy,
                    listFadeSpec = listFadeSpec,
                    listPlacementSpec = listPlacementSpec,
                    observeFile = observeFile,
                    downloadFile = downloadFile,
                    observeCustomEmoji = observeCustomEmoji,
                    onTopic = onTopic,
                    onManage = onManage
                )
            }
        }
        if (loadingMore) {
            item(key = "forum-topic-loading-more") { ForumTopicSkeletonRow() }
        }
    }
}

@Composable
private fun LazyItemScope.ForumTopicListItem(
    topic: ForumTopic,
    parentMuted: Boolean,
    busy: Boolean,
    listFadeSpec: FiniteAnimationSpec<Float>,
    listPlacementSpec: FiniteAnimationSpec<IntOffset>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>,
    onTopic: (ForumTopic) -> Unit,
    onManage: (ForumTopic) -> Unit,
    reordering: Boolean = false,
    reorder: ForumTopicRowReorder? = null,
    /** The drag of this row, or null when another row or nothing is being dragged. */
    dragging: PinnedTopicDrag? = null
) {
    val palette = beyGramPalette()
    Box(
        Modifier
            // Reorder mode moves rows at once: a placement animation would race the finger and the drop.
            .then(
                if (reordering) {
                    Modifier
                } else {
                    Modifier.animateItem(
                        fadeInSpec = listFadeSpec,
                        placementSpec = listPlacementSpec,
                        fadeOutSpec = listFadeSpec
                    )
                }
            )
            // The dragged row follows the finger over its neighbours, lifted on its own background.
            .then(
                if (dragging != null) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer { translationY = dragging.offset }
                        .background(palette.surface)
                } else {
                    Modifier
                }
            )
    ) {
        ForumTopicRow(
            topic = topic,
            parentMuted = parentMuted,
            // Opening a topic is safe while another topic's rename is in flight; only management waits for busy.
            // In reorder mode rows are inert: a drag starts on the handle, and a tap would open the topic instead.
            enabled = !reordering,
            onOpen = { onTopic(topic) },
            // Every topic opens the sheet, where mute is always offered; the management rows inside still follow permissions.
            onLongPress = if (!busy && !reordering) ({ onManage(topic) }) else null,
            observeFile = observeFile,
            downloadFile = downloadFile,
            observeCustomEmoji = observeCustomEmoji,
            reorder = reorder
        )
    }
}

/** The LazyColumn key of a topic row. A stable key keeps the row's state, and its drag, on the same topic across updates. */
private fun forumTopicItemKey(topicId: Long): String = "forum-topic:$topicId"

/** One drag of a pinned row in reorder mode: how far the finger has moved and the pinned rank the row would drop to. */
private class PinnedTopicDrag(
    val topicId: Long,
    val fromRank: Int,
    /** The rank of the first pinned row on screen; rows above it are off screen and cannot be passed. */
    val baseRank: Int,
    /** The dragged row's vertical centre when the drag began, in list coordinates. */
    val startCentre: Float,
    /** The vertical centres of the other pinned rows on screen, top to bottom. */
    val otherCentres: List<Float>
) {
    var offset by mutableFloatStateOf(0f)
    var dropRank by mutableIntStateOf(fromRank)
}

/** The bar that replaces the filter chips in reorder mode: what the mode does, and the way out. */
@Composable
private fun ForumReorderBar(onDone: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.forum_topics_reorder_hint),
            color = palette.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onDone) {
            Text(
                stringResource(R.string.forum_topics_reorder_done),
                color = palette.accent,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ForumTopicsHeader(
    chat: ChatPreview,
    subtitle: String,
    onBack: () -> Unit,
    onOpenInfo: (() -> Unit)?,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    actions: @Composable RowScope.() -> Unit,
    below: @Composable ColumnScope.() -> Unit = {}
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    // In the split layout the list is already visible beside the chat; a back arrow would promise nothing new.
    val showBack = !LocalWindowLayout.current.twoPane
    val titleSize = if (design.style == DesignStyle.Default) 18.sp else 19.sp
    val titleWeight = when (design.style) {
        DesignStyle.Material2, DesignStyle.Material3 -> FontWeight.Medium
        else -> FontWeight.SemiBold
    }
    Column(forumHeaderSurface()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    stringResource(R.string.common_back),
                    tint = palette.textPrimary
                )
            }
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(styledShape(14.dp))
                    .then(
                        if (onOpenInfo != null) {
                            Modifier.clickable(
                                role = Role.Button,
                                onClickLabel = stringResource(R.string.forum_topics_open_group_info),
                                onClick = onOpenInfo
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChatAvatar(chat, 38.dp, observeFile, downloadFile, Modifier.clearAndSetSemantics {})
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        chat.title.ifBlank { stringResource(R.string.forum_topics_subtitle) },
                        color = palette.textPrimary,
                        fontSize = titleSize,
                        fontWeight = titleWeight,
                        maxLines = 2,
                        lineHeight = 22.sp,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        subtitle,
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            actions()
        }
        below()
        if (design.components.topBar == TopBarVariant.Flat) {
            HorizontalDivider(color = palette.divider)
        }
    }
}

/** The top-bar material of the current style, exactly like the profile screens: never a surface fill in every style. */
@Composable
private fun forumHeaderSurface(): Modifier {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    return Modifier.fillMaxWidth().then(
        when (design.components.topBar) {
            TopBarVariant.Elevated -> Modifier.topAppBarSurface()
            TopBarVariant.Glass -> Modifier.background(design.surfaces.panelFill(palette.surface, PanelFloor.Bar))
            TopBarVariant.Signature -> Modifier.signatureChrome(palette.screen, palette.divider, HairlineEdge.Bottom)
            TopBarVariant.Flat -> Modifier.background(palette.surface)
        }
    )
}

@Composable
private fun RowScope.ForumHeaderActions(
    searchOpen: Boolean,
    canCreate: Boolean,
    busy: Boolean,
    onToggleSearch: () -> Unit,
    onCreate: () -> Unit,
    onViewMode: (() -> Unit)? = null
) {
    val palette = beyGramPalette()
    if (onViewMode != null) IconButton(onClick = onViewMode) {
        Icon(
            Icons.Rounded.Dashboard,
            stringResource(R.string.forum_navigation_title),
            tint = palette.textPrimary
        )
    }
    IconButton(onClick = onToggleSearch) {
        Icon(
            if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
            stringResource(if (searchOpen) R.string.search_close_description else R.string.forum_topics_search),
            tint = palette.textPrimary
        )
    }
    if (canCreate) {
        IconButton(enabled = !busy, onClick = onCreate) {
            Icon(
                Icons.Rounded.Add,
                stringResource(R.string.forum_create),
                tint = if (busy) palette.disabled else palette.accent
            )
        }
    }
}

/**
 * A compact All/Unread filter. It grows with the system font instead of clipping inside the chat list's fixed-height
 * folder tab, and follows the active style's selection tone instead of forcing one palette.
 */
@Composable
private fun ForumFilterChip(
    title: String,
    selected: Boolean,
    count: Int?,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    val main = LocalDesignSystem.current.mainScreen
    val tone = main.chipSelection
    val container = when {
        !selected -> when (tone) {
            ChipSelectionTone.Ink, ChipSelectionTone.Filter, ChipSelectionTone.Underlined -> Color.Transparent
            else -> palette.surface2
        }
        tone == ChipSelectionTone.Tonal || tone == ChipSelectionTone.Filter -> palette.accentSoft
        tone == ChipSelectionTone.Ink -> lerp(palette.textPrimary, palette.accent, 0.18f)
        tone == ChipSelectionTone.Underlined -> Color.Transparent
        else -> palette.accent
    }
    val content = when {
        !selected && tone == ChipSelectionTone.Ink -> palette.textSecondary
        !selected && tone == ChipSelectionTone.Filter -> palette.textSecondary
        !selected -> palette.textPrimary
        tone == ChipSelectionTone.Tonal || tone == ChipSelectionTone.Filter -> palette.accentInk
        tone == ChipSelectionTone.Ink -> palette.screen
        tone == ChipSelectionTone.Underlined -> palette.textPrimary
        else -> palette.onAccent
    }
    val shape = styledShape(main.chipRadius)
    val outline = if (tone == ChipSelectionTone.Filter && !selected) palette.divider else Color.Transparent
    // The clickable surface is the full 48dp target; only the chip inside is visually smaller.
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier
                    .heightIn(min = 36.dp)
                    .clip(shape)
                    .then(if (outline != Color.Transparent) Modifier.border(1.dp, outline, shape) else Modifier)
                    .background(container)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tone == ChipSelectionTone.Filter && selected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    title,
                    color = content,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (count != null && count > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        count.toString(),
                        color = when {
                            !selected -> palette.accent
                            tone == ChipSelectionTone.Underlined -> palette.accent
                            else -> content
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (tone == ChipSelectionTone.Underlined) {
                // Material 2 selects with a rule, not a filled container; zero-width rules keep the row from shifting.
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .height(2.dp)
                        .width(if (selected) 28.dp else 0.dp)
                        .background(if (selected) palette.accent else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun ForumInlineBanner(
    text: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    danger: Boolean,
    actionEnabled: Boolean = true,
    onDismiss: (() -> Unit)? = null
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().background(palette.surface2)
            .errorAnnouncement()
            .padding(start = 16.dp, end = if (onDismiss != null) 0.dp else 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = if (danger) palette.danger else palette.textSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = if (danger) palette.danger else palette.textPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(enabled = actionEnabled, onClick = onAction) {
                Text(actionLabel, color = if (actionEnabled) palette.accent else palette.textSecondary)
            }
        }
        if (onDismiss != null) {
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = palette.textSecondary
                )
            }
        }
    }
}

@Composable
private fun ForumStateMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    announce: Boolean = false
) {
    val palette = beyGramPalette()
    LazyColumn(
        modifier = modifier.fillMaxSize().then(if (announce) Modifier.errorAnnouncement() else Modifier),
        contentPadding = PaddingValues(horizontal = 36.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(56.dp).clip(styledShape(18.dp)).background(palette.accentSoft).padding(14.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    title,
                    color = palette.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(7.dp))
                Text(body, color = palette.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(18.dp))
                    TextButton(enabled = actionEnabled, onClick = onAction) {
                        Text(
                            actionLabel,
                            color = if (actionEnabled) palette.accent else palette.textSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun forumTopicsSubtitle(
    page: ForumTopicsPage,
    topicsComplete: Boolean,
    loadingTopics: Boolean,
    memberCount: Int?
): String {
    val topicPart = when {
        // When the listing is complete, the rows on screen are the truth. The server count is approximate and may
        // include hidden topics, so it must not appear as an exact number.
        topicsComplete -> stringResource(R.string.forum_topics_count, page.loadedCount)
        page.effectiveTotalCount > page.loadedCount ->
            stringResource(R.string.forum_topics_loaded_of_total, page.loadedCount, page.effectiveTotalCount)
        loadingTopics -> stringResource(R.string.forum_topics_loading)
        else -> stringResource(R.string.forum_topics_subtitle)
    }
    // The window width is not the pane width in the split layout; member count only fits a wide single pane.
    val layout = LocalWindowLayout.current
    val memberPart = memberCount?.takeIf { it > 0 && !layout.twoPane && layout.widthDp >= 400f }
        ?.let { stringResource(R.string.forum_topics_members, it) }
    return if (memberPart == null) topicPart else stringResource(R.string.forum_topics_meta, topicPart, memberPart)
}
