package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.AnimationItem
import com.beykus.beygram.domain.model.AnimationPage
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.StickerFileFormat
import com.beykus.beygram.domain.model.StickerItem
import com.beykus.beygram.domain.model.StickerSearchPage
import com.beykus.beygram.domain.model.StickerSetContent
import com.beykus.beygram.domain.model.StickerSetSummary
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val PANEL_MIN_HEIGHT = 220.dp
private val PANEL_MAX_HEIGHT = 420.dp
private val MIN_PANEL_WITH_KEYBOARD = 96.dp
private const val SEARCH_DEBOUNCE_MS = 350L

/**
 * Unified emoji, sticker and GIF panel opened from the composer. Emoji are inserted into the draft and the panel stays
 * open. Stickers are sent on tap; a long press opens a preview with explicit send options, favorites and pack navigation.
 * GIFs are sent on tap and can be staged with a caption from the preview. The panel is inline (not an attach-menu page),
 * so the conversation stays visible and insets keep working.
 */

/** Draws the sticker/GIF entry next to the composer and keeps the keyboard out of the way when the panel opens. */
@Composable
internal fun StickerPanelEntryButton(open: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val label = stringResource(R.string.sticker_panel_entry)
    IconButton(
        onClick = {
            focus.clearFocus()
            keyboard?.hide()
            onClick()
        },
        enabled = enabled,
        modifier = Modifier.size(48.dp)
            .clip(CircleShape)
            .background(if (open) palette.accentSoft else Color.Transparent)
            .semantics {
                contentDescription = label
                selected = open
            }
    ) {
        Icon(
            Icons.Rounded.EmojiEmotions,
            contentDescription = null,
            tint = when {
                !enabled -> palette.disabled
                open -> palette.accent
                else -> palette.textSecondary
            },
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Owns the direct-send calls of the unified picker. Stickers and GIFs are sent immediately, like Telegram; a long press
 * still offers scheduling and silent delivery for stickers. The screen scope keeps an in-flight send alive if the panel
 * is closed right after a tap.
 */
@Composable
internal fun StickerPanelHost(
    conversation: ConversationRepository,
    chatId: Long,
    replyId: Long?,
    forumTopicId: Long,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    scope: CoroutineScope,
    composerReserve: androidx.compose.ui.unit.Dp,
    onReplyConsumed: (Long?) -> Unit,
    onAnimationAttach: (AnimationItem) -> Unit,
    onStickerOptions: (StickerItem) -> Unit,
    onSendError: (String) -> Unit,
    onInsertEmoji: (String) -> Unit,
    onDeleteBackward: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    key(conversation, chatId) {
        var sending by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        StickerPanel(
            conversation = conversation,
            observeFile = observeFile,
            downloadFile = downloadFile,
            sending = sending,
            error = error,
            scope = scope,
            composerReserve = composerReserve,
            onSendSticker = { item ->
                if (!sending) {
                    val targetReplyId = replyId
                    sending = true
                    error = null
                    scope.launch {
                        try {
                            val result = conversation.sendSticker(
                                chatId = chatId,
                                sticker = item.toOutgoingMedia(),
                                replyToMessageId = targetReplyId,
                                forumTopicId = forumTopicId
                            )
                            if (result.isSuccess) onReplyConsumed(targetReplyId)
                            else {
                                val message = result.exceptionOrNull().userMessage(context, R.string.sticker_panel_send_failed)
                                error = message
                                onSendError(message)
                            }
                        } finally {
                            sending = false
                        }
                    }
                }
            },
            onStickerOptions = onStickerOptions,
            onSendAnimation = { item ->
                if (!sending) {
                    val targetReplyId = replyId
                    sending = true
                    error = null
                    scope.launch {
                        try {
                            val result = conversation.sendMedia(
                                chatId = chatId,
                                media = item.toOutgoingMedia(),
                                caption = "",
                                replyToMessageId = targetReplyId,
                                forumTopicId = forumTopicId
                            )
                            if (result.isSuccess) onReplyConsumed(targetReplyId)
                            else {
                                val message = result.exceptionOrNull().userMessage(context, R.string.gif_panel_send_failed)
                                error = message
                                onSendError(message)
                            }
                        } finally {
                            sending = false
                        }
                    }
                }
            },
            onAttachAnimation = onAnimationAttach,
            onInsertEmoji = onInsertEmoji,
            onDeleteBackward = onDeleteBackward,
            onClose = onClose
        )
    }
}

@Composable
private fun StickerPanel(
    conversation: ConversationRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    sending: Boolean,
    error: String?,
    scope: CoroutineScope,
    composerReserve: androidx.compose.ui.unit.Dp,
    onSendSticker: (StickerItem) -> Unit,
    onStickerOptions: (StickerItem) -> Unit,
    onSendAnimation: (AnimationItem) -> Unit,
    onAttachAnimation: (AnimationItem) -> Unit,
    onInsertEmoji: (String) -> Unit,
    onDeleteBackward: () -> Unit,
    onClose: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var view by rememberSaveable(stateSaver = StickerPanelViewSaver) { mutableStateOf(StickerPanelView()) }

    var recentState by remember { mutableStateOf<PickerLoad<List<StickerItem>>>(PickerLoad.Loading) }
    var recentRetry by remember { mutableIntStateOf(0) }
    var favoritesState by remember { mutableStateOf<PickerLoad<List<StickerItem>>>(PickerLoad.Loading) }
    var favoritesRetry by remember { mutableIntStateOf(0) }
    var setsState by remember { mutableStateOf<PickerLoad<List<StickerSetSummary>>>(PickerLoad.Loading) }
    var setsRetry by remember { mutableIntStateOf(0) }
    var packState by remember { mutableStateOf<PickerLoad<StickerSetContent>>(PickerLoad.Loading) }
    var packRetry by remember { mutableIntStateOf(0) }
    var stickerSearchState by remember { mutableStateOf<PickerLoad<StickerSearchPage>>(PickerLoad.Ready(StickerSearchPage())) }
    var stickerSearchRetry by remember { mutableIntStateOf(0) }
    var stickerSearchLoadingMore by remember { mutableStateOf(false) }
    var stickerSearchMoreError by remember { mutableStateOf<String?>(null) }
    var gifRecentState by remember { mutableStateOf<PickerLoad<AnimationPage>>(PickerLoad.Loading) }
    var gifRecentRetry by remember { mutableIntStateOf(0) }
    var gifRecentLoadedRetry by remember { mutableIntStateOf(-1) }
    var gifRecentLoadedConversation by remember { mutableStateOf<ConversationRepository?>(null) }
    var gifRecentGeneration by remember { mutableIntStateOf(0) }
    var gifRecentLoadingMore by remember { mutableStateOf(false) }
    var gifRecentMoreError by remember { mutableStateOf<String?>(null) }
    var gifSavedState by remember { mutableStateOf<PickerLoad<List<AnimationItem>>>(PickerLoad.Loading) }
    var gifSavedRetry by remember { mutableIntStateOf(0) }
    var gifSavedLoadedRetry by remember { mutableIntStateOf(-1) }
    var gifSavedLoadedConversation by remember { mutableStateOf<ConversationRepository?>(null) }
    var gifSearchState by remember { mutableStateOf<PickerLoad<AnimationPage>>(PickerLoad.Ready(AnimationPage())) }
    var gifSearchRetry by remember { mutableIntStateOf(0) }
    var gifSearchLoadingMore by remember { mutableStateOf(false) }
    var gifSearchMoreError by remember { mutableStateOf<String?>(null) }
    var premium by remember { mutableStateOf<Boolean?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var favoriteBusyKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchGeneration by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<StickerPanelOverlay?>(null) }
    // Recent emoji are shared by the whole app; the list is read again each time the panel is opened.
    val emojiStore = remember(context) { RecentEmojiStore(context) }
    var recentEmoji by remember(emojiStore) { mutableStateOf(emojiStore.load()) }

    val premiumNotice = stringResource(R.string.sticker_panel_premium_locked)
    val favoriteKeys: Set<String> = remember(favoritesState) {
        favoritesState.readyValue()?.mapTo(hashSetOf()) { it.key } ?: emptySet()
    }
    val packTitle = packState.readyValue()?.takeIf { it.id == view.selectedSetId }?.title
        ?: setsState.readyValue()?.firstOrNull { it.id == view.selectedSetId }?.title.orEmpty()
    val title = when {
        view.searchActive -> stringResource(R.string.sticker_panel_search_results)
        view.section == StickerPanelSection.Pack -> packTitle.ifBlank { stringResource(R.string.sticker_panel_title) }
        view.section == StickerPanelSection.Favorites -> stringResource(R.string.sticker_panel_favorites)
        view.tab == StickerPanelTab.Emoji -> stringResource(R.string.sticker_panel_tab_emoji)
        view.tab == StickerPanelTab.Gifs -> stringResource(R.string.sticker_panel_tab_gifs)
        else -> stringResource(R.string.sticker_panel_title)
    }
    val searchPlaceholder = stringResource(
        if (view.tab == StickerPanelTab.Gifs) R.string.gif_panel_search else R.string.sticker_panel_search
    )
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    // composerReserve is the measured height of the composer column below the panel. It already includes the IME and the
    // navigation bar padding. 0.dp means it has not been measured yet, so the older estimate with the IME height is used.
    val imeHeight = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val availableHeight = (
        if (composerReserve > 0.dp) windowHeight - composerReserve
        else windowHeight - imeHeight - 80.dp
    ).coerceAtLeast(MIN_PANEL_WITH_KEYBOARD)
    val panelHeight = (windowHeight * 0.62f)
        .coerceIn(PANEL_MIN_HEIGHT, PANEL_MAX_HEIGHT)
        .coerceAtMost(availableHeight)
    val displayError = notice ?: error

    LaunchedEffect(conversation) {
        notice = null
        overlay = null
        favoriteBusyKeys = emptySet()
    }
    LaunchedEffect(conversation, recentRetry) {
        recentState = PickerLoad.Loading
        conversation.recentStickers().fold(
            onSuccess = { recentState = PickerLoad.Ready(it.distinctBy { sticker -> sticker.key }) },
            onFailure = { recentState = PickerLoad.Failed(it.userMessage(context, R.string.sticker_panel_load_failed)) }
        )
    }
    LaunchedEffect(conversation, favoritesRetry) {
        favoritesState = PickerLoad.Loading
        conversation.favoriteStickers().fold(
            onSuccess = { favoritesState = PickerLoad.Ready(it.distinctBy { sticker -> sticker.key }) },
            onFailure = { favoritesState = PickerLoad.Failed(it.userMessage(context, R.string.sticker_panel_load_failed)) }
        )
    }
    LaunchedEffect(conversation, setsRetry) {
        setsState = PickerLoad.Loading
        conversation.installedStickerSets().fold(
            onSuccess = { setsState = PickerLoad.Ready(it) },
            onFailure = { setsState = PickerLoad.Failed(it.userMessage(context, R.string.sticker_panel_load_failed)) }
        )
    }
    LaunchedEffect(conversation) {
        conversation.isPremiumAccount().onSuccess { premium = it }
    }
    LaunchedEffect(conversation, view.selectedSetId, packRetry) {
        val setId = view.selectedSetId ?: return@LaunchedEffect
        packState = PickerLoad.Loading
        conversation.stickerSet(setId).fold(
            onSuccess = { packState = PickerLoad.Ready(it.copy(stickers = it.stickers.distinctBy { sticker -> sticker.key })) },
            onFailure = { packState = PickerLoad.Failed(it.userMessage(context, R.string.sticker_panel_load_failed)) }
        )
    }
    LaunchedEffect(conversation, view.tab, gifRecentRetry) {
        if (view.tab != StickerPanelTab.Gifs) return@LaunchedEffect
        if (gifRecentLoadedRetry == gifRecentRetry && gifRecentLoadedConversation === conversation && gifRecentState.readyValue() != null) return@LaunchedEffect
        gifRecentGeneration++
        gifRecentLoadingMore = false
        gifRecentMoreError = null
        gifRecentState = PickerLoad.Loading
        conversation.recentAnimations().fold(
            onSuccess = {
                gifRecentState = PickerLoad.Ready(it.copy(animations = it.animations.distinctBy { animation -> animation.key }))
                gifRecentLoadedRetry = gifRecentRetry
                gifRecentLoadedConversation = conversation
            },
            onFailure = { gifRecentState = PickerLoad.Failed(it.userMessage(context, R.string.gif_panel_load_failed)) }
        )
    }
    LaunchedEffect(conversation, view.tab, gifSavedRetry) {
        if (view.tab != StickerPanelTab.Gifs) return@LaunchedEffect
        if (gifSavedLoadedRetry == gifSavedRetry && gifSavedLoadedConversation === conversation && gifSavedState.readyValue() != null) return@LaunchedEffect
        gifSavedState = PickerLoad.Loading
        conversation.savedAnimations().fold(
            onSuccess = {
                gifSavedState = PickerLoad.Ready(it.distinctBy { animation -> animation.key })
                gifSavedLoadedRetry = gifSavedRetry
                gifSavedLoadedConversation = conversation
            },
            onFailure = { gifSavedState = PickerLoad.Failed(it.userMessage(context, R.string.gif_panel_load_failed)) }
        )
    }
    LaunchedEffect(conversation, view.query, view.tab, stickerSearchRetry, gifSearchRetry) {
        val needle = view.query.trim()
        searchGeneration++
        stickerSearchLoadingMore = false
        gifSearchLoadingMore = false
        stickerSearchMoreError = null
        gifSearchMoreError = null
        stickerSearchState = PickerLoad.Ready(StickerSearchPage())
        gifSearchState = PickerLoad.Ready(AnimationPage())
        if (needle.isEmpty()) return@LaunchedEffect
        stickerSearchState = PickerLoad.Loading
        gifSearchState = PickerLoad.Loading
        delay(SEARCH_DEBOUNCE_MS)
        when (view.tab) {
            StickerPanelTab.Emoji -> Unit
            StickerPanelTab.Stickers -> conversation.searchStickers(needle, 0).fold(
                onSuccess = { stickerSearchState = PickerLoad.Ready(it.copy(stickers = it.stickers.distinctBy { sticker -> sticker.key })) },
                onFailure = { stickerSearchState = PickerLoad.Failed(it.userMessage(context, R.string.sticker_panel_load_failed)) }
            )
            StickerPanelTab.Gifs -> conversation.searchAnimations(needle).fold(
                onSuccess = { gifSearchState = PickerLoad.Ready(it.copy(animations = it.animations.distinctBy { animation -> animation.key })) },
                onFailure = { gifSearchState = PickerLoad.Failed(it.userMessage(context, R.string.gif_panel_load_failed)) }
            )
        }
    }
    LaunchedEffect(view.searchOpen) {
        if (view.searchOpen) runCatching { focusRequester.requestFocus() } else keyboard?.hide()
    }

    fun closePanel() {
        keyboard?.hide()
        onClose()
    }

    fun openOverlay(next: StickerPanelOverlay) {
        focusManager.clearFocus()
        keyboard?.hide()
        notice = null
        overlay = next
    }

    fun navigateBack() {
        notice = null
        val next = view.back()
        if (next == null) closePanel() else {
            view = next
            if (!next.searchActive) keyboard?.hide()
        }
    }

    fun insertEmoji(emoji: String) {
        notice = null
        recentEmoji = emojiStore.record(emoji)
        onInsertEmoji(emoji)
    }

    fun requestSend(item: StickerItem) {
        if (item.premium && premium == false) {
            notice = premiumNotice
            return
        }
        notice = null
        onSendSticker(item)
    }

    fun requestOptions(item: StickerItem) {
        if (item.premium && premium == false) {
            notice = premiumNotice
            return
        }
        notice = null
        overlay = null
        onStickerOptions(item)
    }

    fun selectPack(setId: Long) {
        notice = null
        if (view.selectedSetId != setId || view.section != StickerPanelSection.Pack) packState = PickerLoad.Loading
        view = view.openPack(setId)
    }

    fun toggleFavorite(item: StickerItem, favorite: Boolean) {
        if (item.key in favoriteBusyKeys) return
        favoriteBusyKeys = favoriteBusyKeys + item.key
        scope.launch {
            conversation.setStickerFavorite(item, favorite)
                .onSuccess {
                    val current = favoritesState.readyValue()
                    if (current != null) {
                        favoritesState = PickerLoad.Ready(updateFavoriteStickers(current, item, favorite))
                    } else {
                        // The initial list was unavailable; reconcile from TDLib so the preview can offer removal.
                        favoritesRetry++
                    }
                }
                .onFailure { notice = it.userMessage(context, R.string.sticker_panel_favorite_failed) }
            favoriteBusyKeys = favoriteBusyKeys - item.key
        }
    }

    fun loadMoreStickerSearch() {
        val needle = view.query.trim()
        val page = stickerSearchState.readyValue() ?: return
        val offset = page.nextOffset ?: return
        if (needle.isEmpty() || stickerSearchLoadingMore) return
        val generation = searchGeneration
        stickerSearchLoadingMore = true
        stickerSearchMoreError = null
        scope.launch {
            conversation.searchStickers(needle, offset)
                .onSuccess { next ->
                    if (generation == searchGeneration) {
                        stickerSearchState = PickerLoad.Ready(
                            StickerSearchPage(mergeStickerPages(page.stickers, next), next.nextOffset)
                        )
                    }
                }
                .onFailure {
                    if (generation == searchGeneration) {
                        stickerSearchMoreError = it.userMessage(context, R.string.sticker_panel_load_failed)
                    }
                }
            if (generation == searchGeneration) stickerSearchLoadingMore = false
        }
    }

    fun loadMoreGifRecent() {
        val page = gifRecentState.readyValue() ?: return
        val offset = page.nextOffset.takeIf { it.isNotBlank() } ?: return
        if (gifRecentLoadingMore) return
        val generation = gifRecentGeneration
        gifRecentLoadingMore = true
        gifRecentMoreError = null
        scope.launch {
            conversation.recentAnimations(offset)
                .onSuccess { next ->
                    if (generation == gifRecentGeneration) {
                        gifRecentState = PickerLoad.Ready(
                            AnimationPage(mergeAnimationPages(page.animations, next), next.nextOffset)
                        )
                    }
                }
                .onFailure {
                    if (generation == gifRecentGeneration) {
                        gifRecentMoreError = it.userMessage(context, R.string.gif_panel_load_failed)
                    }
                }
            if (generation == gifRecentGeneration) gifRecentLoadingMore = false
        }
    }

    fun loadMoreGifSearch() {
        val needle = view.query.trim()
        val page = gifSearchState.readyValue() ?: return
        val offset = page.nextOffset.takeIf { it.isNotBlank() } ?: return
        if (needle.isEmpty() || gifSearchLoadingMore) return
        val generation = searchGeneration
        gifSearchLoadingMore = true
        gifSearchMoreError = null
        scope.launch {
            conversation.searchAnimations(needle, offset)
                .onSuccess { next ->
                    if (generation == searchGeneration) {
                        gifSearchState = PickerLoad.Ready(
                            AnimationPage(mergeAnimationPages(page.animations, next), next.nextOffset)
                        )
                    }
                }
                .onFailure {
                    if (generation == searchGeneration) {
                        gifSearchMoreError = it.userMessage(context, R.string.gif_panel_load_failed)
                    }
                }
            if (generation == searchGeneration) gifSearchLoadingMore = false
        }
    }

    // Nested back handling: search closes first, then a pack/favorites list, and only the root delegates to the chat screen.
    // A preview sheet owns Back itself while it is open.
    BackHandler(enabled = view.canGoBack && overlay == null) { navigateBack() }

    Column(Modifier.fillMaxWidth().heightIn(max = panelHeight).background(palette.surface)) {
        if (view.searchActive) {
            StickerPanelSearchHeader(
                query = view.query,
                placeholder = searchPlaceholder,
                focusRequester = focusRequester,
                onQueryChange = { notice = null; view = view.updateQuery(it) },
                onBack = {
                    notice = null
                    view = view.closeSearch()
                    keyboard?.hide()
                },
                onClose = ::closePanel
            )
        } else {
            StickerPanelHeader(
                title = title,
                canGoBack = view.canGoBack,
                showSearch = view.tab != StickerPanelTab.Emoji,
                onBack = ::navigateBack,
                onSearch = { notice = null; view = view.openSearch() },
                onClose = ::closePanel
            )
        }
        if (!view.searchActive) {
            StickerPanelTabs(
                selected = view.tab,
                onSelect = { next -> if (next != view.tab) { notice = null; view = view.switchTab(next) } }
            )
        }
        if (sending) LinearProgressIndicator(
            Modifier.fillMaxWidth().height(2.dp),
            color = palette.accent,
            trackColor = palette.surface2
        )
        if (displayError != null) PanelNotice(displayError)
        when (view.tab) {
            StickerPanelTab.Emoji -> EmojiPanel(
                recents = recentEmoji,
                onEmoji = ::insertEmoji,
                onBackspace = onDeleteBackward,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            StickerPanelTab.Stickers -> {
                if (!view.searchActive) {
                    StickerSectionStrip(
                        view = view,
                        setsState = setsState,
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        onRecent = { notice = null; view = view.showRecent() },
                        onFavorites = { notice = null; view = view.showFavorites() },
                        onPack = { summary -> selectPack(summary.id) },
                        onPackMenu = { summary -> openOverlay(StickerPanelOverlay.Pack(summary)) },
                        onRetrySets = { setsRetry++ }
                    )
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    StickerContent(
                        view = view,
                        recentState = recentState,
                        favoritesState = favoritesState,
                        packState = packState,
                        searchState = stickerSearchState,
                        searchLoadingMore = stickerSearchLoadingMore,
                        searchMoreError = stickerSearchMoreError,
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        onRetryRecent = { recentRetry++ },
                        onRetryFavorites = { favoritesRetry++ },
                        onRetryPack = { packRetry++ },
                        onRetrySearch = { stickerSearchRetry++ },
                        onLoadMoreSearch = ::loadMoreStickerSearch,
                        onSend = ::requestSend,
                        onOptions = { item -> openOverlay(StickerPanelOverlay.Sticker(item)) }
                    )
                }
            }
            StickerPanelTab.Gifs -> {
                if (!view.searchActive) {
                    GifSectionStrip(
                        favorites = view.section == StickerPanelSection.Favorites,
                        onRecent = { notice = null; view = view.showRecent() },
                        onFavorites = { notice = null; view = view.showFavorites() }
                    )
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    GifContent(
                        view = view,
                        recentState = gifRecentState,
                        savedState = gifSavedState,
                        searchState = gifSearchState,
                        recentLoadingMore = gifRecentLoadingMore,
                        recentMoreError = gifRecentMoreError,
                        searchLoadingMore = gifSearchLoadingMore,
                        searchMoreError = gifSearchMoreError,
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        onRetryRecent = { gifRecentRetry++ },
                        onRetrySaved = { gifSavedRetry++ },
                        onRetrySearch = { gifSearchRetry++ },
                        onLoadMoreRecent = ::loadMoreGifRecent,
                        onLoadMoreSearch = ::loadMoreGifSearch,
                        onSend = onSendAnimation,
                        onPreview = { item -> openOverlay(StickerPanelOverlay.Gif(item)) }
                    )
                }
            }
        }
    }

    val currentOverlay = overlay
    if (currentOverlay != null) {
        val overlayPackTitle = (currentOverlay as? StickerPanelOverlay.Sticker)?.item?.let { item ->
            packState.readyValue()?.takeIf { it.id == item.setId }?.title
                ?: setsState.readyValue()?.firstOrNull { it.id == item.setId }?.title
        }
        StickerPanelOverlayContent(
            overlay = currentOverlay,
            packTitle = overlayPackTitle,
            sending = sending,
            observeFile = observeFile,
            downloadFile = downloadFile,
            favoriteKeys = favoriteKeys,
            favoriteBusyKeys = favoriteBusyKeys,
            premium = premium,
            onDismiss = { overlay = null },
            onSendSticker = { item ->
                overlay = null
                requestSend(item)
            },
            onStickerOptions = ::requestOptions,
            onToggleFavorite = ::toggleFavorite,
            onSendAnimation = { item ->
                overlay = null
                onSendAnimation(item)
            },
            onAttachAnimation = { item ->
                overlay = null
                onAttachAnimation(item)
            },
            onOpenPack = { setId ->
                overlay = null
                selectPack(setId)
            }
        )
    }
}

@Composable
private fun StickerPanelHeader(
    title: String,
    canGoBack: Boolean,
    showSearch: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).padding(start = 4.dp, end = 4.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (canGoBack) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), tint = palette.textSecondary)
            }
        } else Spacer(Modifier.width(8.dp))
        Text(
            title,
            color = palette.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (showSearch) {
            IconButton(onClick = onSearch, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Search, stringResource(R.string.sticker_panel_search), tint = palette.textSecondary)
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Close, stringResource(R.string.sticker_panel_close), tint = palette.textSecondary)
        }
    }
}

@Composable
private fun StickerPanelSearchHeader(
    query: String,
    placeholder: String,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(start = 4.dp, end = 4.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), tint = palette.textSecondary)
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            placeholder = { Text(placeholder, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            stringResource(R.string.sticker_panel_clear_search),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = palette.divider,
                focusedTextColor = palette.textPrimary,
                unfocusedTextColor = palette.textPrimary,
                cursorColor = palette.accent,
                focusedContainerColor = palette.surface2,
                unfocusedContainerColor = palette.surface2
            )
        )
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Close, stringResource(R.string.sticker_panel_close), tint = palette.textSecondary)
        }
    }
}

@Composable
private fun StickerPanelTabs(selected: StickerPanelTab, onSelect: (StickerPanelTab) -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface2)
            .padding(4.dp)
    ) {
        PanelTab(
            label = stringResource(R.string.sticker_panel_tab_emoji),
            selected = selected == StickerPanelTab.Emoji,
            onClick = { onSelect(StickerPanelTab.Emoji) },
            modifier = Modifier.weight(1f)
        )
        PanelTab(
            label = stringResource(R.string.sticker_panel_tab_stickers),
            selected = selected == StickerPanelTab.Stickers,
            onClick = { onSelect(StickerPanelTab.Stickers) },
            modifier = Modifier.weight(1f)
        )
        PanelTab(
            label = stringResource(R.string.sticker_panel_tab_gifs),
            selected = selected == StickerPanelTab.Gifs,
            onClick = { onSelect(StickerPanelTab.Gifs) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PanelTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val palette = beyGramPalette()
    Box(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) palette.accent else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) palette.onAccent else palette.textPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun StickerSectionStrip(
    view: StickerPanelView,
    setsState: PickerLoad<List<StickerSetSummary>>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onRecent: () -> Unit,
    onFavorites: () -> Unit,
    onPack: (StickerSetSummary) -> Unit,
    onPackMenu: (StickerSetSummary) -> Unit,
    onRetrySets: () -> Unit
) {
    val palette = beyGramPalette()
    LazyRow(
        Modifier.fillMaxWidth().heightIn(min = 68.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "recent") {
            SectionPill(
                label = stringResource(R.string.sticker_panel_recent),
                icon = Icons.Rounded.History,
                selected = view.section == StickerPanelSection.Recent,
                onClick = onRecent
            )
        }
        item(key = "favorites") {
            SectionPill(
                label = stringResource(R.string.sticker_panel_favorites),
                icon = Icons.Rounded.Star,
                selected = view.section == StickerPanelSection.Favorites,
                onClick = onFavorites
            )
        }
        when (setsState) {
            PickerLoad.Loading -> item(key = "sets_loading") {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = palette.accent, strokeWidth = 2.dp)
                }
            }
            is PickerLoad.Failed -> item(key = "sets_retry") {
                TextButton(onClick = onRetrySets, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.common_retry))
                }
            }
            else -> {
                val sets = setsState.readyValue().orEmpty()
                rowItems(sets, key = { "pack-${it.id}" }) { summary ->
                    PackChip(
                        summary = summary,
                        selected = view.section == StickerPanelSection.Pack && view.selectedSetId == summary.id,
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        onOpen = { onPack(summary) },
                        onLongPress = { onPackMenu(summary) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GifSectionStrip(favorites: Boolean, onRecent: () -> Unit, onFavorites: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionPill(
            label = stringResource(R.string.gif_panel_recent),
            icon = Icons.Rounded.History,
            selected = !favorites,
            onClick = onRecent
        )
        SectionPill(
            label = stringResource(R.string.gif_panel_saved),
            icon = Icons.Rounded.Star,
            selected = favorites,
            onClick = onFavorites
        )
    }
}

@Composable
private fun SectionPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50))
            .background(if (selected) palette.accentSoft else palette.surface2)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) palette.accent else palette.textSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = if (selected) palette.accentInk else palette.textPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun PackChip(
    summary: StickerSetSummary,
    selected: Boolean,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onOpen: () -> Unit,
    onLongPress: () -> Unit
) {
    val palette = beyGramPalette()
    val description = stringResource(R.string.sticker_panel_pack_description, summary.title, summary.size)
    Column(
        Modifier.width(72.dp).heightIn(min = 64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) palette.accentSoft else palette.surface2)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.sticker_panel_open_pack),
                onLongClickLabel = stringResource(R.string.sticker_panel_pack_menu),
                onClick = onOpen,
                onLongClick = onLongPress
            )
            .semantics {
                contentDescription = description
                this.selected = selected
            }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val cover = summary.cover
        if (cover != null) {
            // Covers are downloaded by the chip itself only while the strip is visible.
            StickerPanelArtwork(
                item = cover,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                observeFile = observeFile,
                downloadFile = downloadFile,
                maxDimension = 128,
                thumbnailOnly = true
            )
        } else {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(palette.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Text(summary.title.take(1), color = palette.accentInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            summary.title,
            color = if (selected) palette.accentInk else palette.textPrimary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun StickerContent(
    view: StickerPanelView,
    recentState: PickerLoad<List<StickerItem>>,
    favoritesState: PickerLoad<List<StickerItem>>,
    packState: PickerLoad<StickerSetContent>,
    searchState: PickerLoad<StickerSearchPage>,
    searchLoadingMore: Boolean,
    searchMoreError: String?,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onRetryRecent: () -> Unit,
    onRetryFavorites: () -> Unit,
    onRetryPack: () -> Unit,
    onRetrySearch: () -> Unit,
    onLoadMoreSearch: () -> Unit,
    onSend: (StickerItem) -> Unit,
    onOptions: (StickerItem) -> Unit
) {
    val emptyRecent = stringResource(R.string.sticker_panel_empty_recent)
    val emptyFavorites = stringResource(R.string.sticker_panel_empty_favorites)
    val emptyPack = stringResource(R.string.sticker_panel_empty_pack)
    val emptySearch = stringResource(R.string.sticker_panel_empty_search)
    when {
        view.query.isNotBlank() -> PickerLoadContent(searchState, onRetrySearch) { page ->
            if (page.stickers.isEmpty()) PanelEmpty(emptySearch)
            else {
                val footer: (@Composable () -> Unit)? = when {
                    searchMoreError != null -> {
                        { PaginationError(searchMoreError, onLoadMoreSearch) }
                    }
                    page.nextOffset != null -> {
                        { PickerMore(searchLoadingMore, onLoadMoreSearch) }
                    }
                    else -> null
                }
                StickerGrid(
                    stickers = page.stickers,
                    observeFile = observeFile,
                    downloadFile = downloadFile,
                    onSend = onSend,
                    onOptions = onOptions,
                    footer = footer
                )
            }
        }
        view.section == StickerPanelSection.Favorites -> PickerLoadContent(favoritesState, onRetryFavorites) { stickers ->
            if (stickers.isEmpty()) PanelEmpty(emptyFavorites)
            else StickerGrid(stickers, observeFile, downloadFile, onSend, onOptions)
        }
        view.section == StickerPanelSection.Pack -> PickerLoadContent(packState, onRetryPack) { content ->
            if (content.stickers.isEmpty()) PanelEmpty(emptyPack)
            else StickerGrid(content.stickers, observeFile, downloadFile, onSend, onOptions)
        }
        else -> PickerLoadContent(recentState, onRetryRecent) { stickers ->
            if (stickers.isEmpty()) PanelEmpty(emptyRecent)
            else StickerGrid(stickers, observeFile, downloadFile, onSend, onOptions)
        }
    }
}

@Composable
private fun GifContent(
    view: StickerPanelView,
    recentState: PickerLoad<AnimationPage>,
    savedState: PickerLoad<List<AnimationItem>>,
    searchState: PickerLoad<AnimationPage>,
    recentLoadingMore: Boolean,
    recentMoreError: String?,
    searchLoadingMore: Boolean,
    searchMoreError: String?,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onRetryRecent: () -> Unit,
    onRetrySaved: () -> Unit,
    onRetrySearch: () -> Unit,
    onLoadMoreRecent: () -> Unit,
    onLoadMoreSearch: () -> Unit,
    onSend: (AnimationItem) -> Unit,
    onPreview: (AnimationItem) -> Unit
) {
    val emptyRecent = stringResource(R.string.gif_panel_empty_recent)
    val emptySaved = stringResource(R.string.gif_panel_empty_saved)
    val emptySearch = stringResource(R.string.gif_panel_empty_search)
    Column(Modifier.fillMaxSize()) {
        if (view.searchActive && view.query.isNotBlank()) {
            Text(
                stringResource(R.string.gif_panel_search_note),
                color = beyGramPalette().textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                view.query.isNotBlank() -> PickerLoadContent(searchState, onRetrySearch) { page ->
                    if (page.animations.isEmpty()) PanelEmpty(emptySearch)
                    else {
                        val footer: (@Composable () -> Unit)? = when {
                            searchMoreError != null -> {
                                { PaginationError(searchMoreError, onLoadMoreSearch) }
                            }
                            page.nextOffset.isNotBlank() -> {
                                { PickerMore(searchLoadingMore, onLoadMoreSearch) }
                            }
                            else -> null
                        }
                        GifGrid(
                            animations = page.animations,
                            observeFile = observeFile,
                            downloadFile = downloadFile,
                            onSend = onSend,
                            onPreview = onPreview,
                            footer = footer
                        )
                    }
                }
                view.section == StickerPanelSection.Favorites -> PickerLoadContent(savedState, onRetrySaved) { animations ->
                    if (animations.isEmpty()) PanelEmpty(emptySaved)
                    else GifGrid(animations, observeFile, downloadFile, onSend, onPreview)
                }
                else -> PickerLoadContent(recentState, onRetryRecent) { page ->
                    if (page.animations.isEmpty()) PanelEmpty(emptyRecent)
                    else {
                        val footer: (@Composable () -> Unit)? = when {
                            recentMoreError != null -> {
                                { PaginationError(recentMoreError, onLoadMoreRecent) }
                            }
                            page.nextOffset.isNotBlank() -> {
                                { PickerMore(recentLoadingMore, onLoadMoreRecent) }
                            }
                            else -> null
                        }
                        GifGrid(
                            animations = page.animations,
                            observeFile = observeFile,
                            downloadFile = downloadFile,
                            onSend = onSend,
                            onPreview = onPreview,
                            footer = footer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerGrid(
    stickers: List<StickerItem>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onSend: (StickerItem) -> Unit,
    onOptions: (StickerItem) -> Unit,
    footer: (@Composable () -> Unit)? = null
) {
    val gridState = rememberLazyGridState()
    // Search pages and recents can repeat an item; a duplicate lazy key would crash the grid.
    val uniqueStickers = remember(stickers) { stickers.distinctBy { it.key } }
    LaunchedEffect(stickers.firstOrNull()?.key) { if (stickers.isNotEmpty()) gridState.scrollToItem(0) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(82.dp),
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        gridItems(uniqueStickers, key = { it.key }) { item ->
            StickerPanelCell(item, observeFile, downloadFile, onSend = { onSend(item) }, onOptions = { onOptions(item) })
        }
        if (footer != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "footer") { footer() }
        }
    }
}

@Composable
private fun StickerPanelCell(
    item: StickerItem,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onSend: () -> Unit,
    onOptions: () -> Unit
) {
    val palette = beyGramPalette()
    val label = stringResource(R.string.sticker_panel_sticker_label)
    val emoji = item.emoji.ifBlank { label }
    val baseDescription = stringResource(R.string.sticker_panel_item_description, emoji)
    val description = if (item.premium) {
        "$baseDescription, ${stringResource(R.string.sticker_panel_premium_badge)}"
    } else baseDescription
    Box(
        Modifier.size(76.dp).clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.common_send),
                onLongClickLabel = stringResource(R.string.sticker_panel_preview),
                onClick = onSend,
                onLongClick = onOptions
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        StickerPanelArtwork(item, Modifier.fillMaxSize().padding(3.dp), observeFile, downloadFile)
        if (item.premium) {
            Icon(
                Icons.Rounded.Star,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(13.dp)
            )
        }
    }
}

/** Draws one sticker and downloads only its preview; the caller owns the 48dp clickable surface. */
@Composable
private fun StickerPanelArtwork(
    item: StickerItem,
    modifier: Modifier,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    maxDimension: Int = 256,
    thumbnailOnly: Boolean = false
) {
    val palette = beyGramPalette()
    val motion = systemMotionEnabled()
    val file by remember(item.fileId) { observeFile(item.fileId) }.collectAsStateWithLifecycle()
    val thumbnail = rememberPanelFile(item.thumbnailFileId, observeFile)
    LaunchedEffect(item.fileId, item.thumbnailFileId, item.format, thumbnailOnly) {
        item.thumbnailFileId?.let(downloadFile)
        // WebM stickers and strip covers cannot be drawn by the picker; their still thumbnail is enough.
        if (!thumbnailOnly && item.format != StickerFileFormat.Webm) downloadFile(item.fileId)
    }
    val emoji = item.emoji.ifBlank { "🙂" }
    val thumbnailPath = thumbnail.localPath.takeIf { item.thumbnailIsStatic }
    val fullPath = file.localPath
    Box(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val fallback: @Composable () -> Unit = {
            if (thumbnailPath != null) LocalMediaImage(thumbnailPath, Modifier.fillMaxSize(), fit = true, maxDimension = maxDimension)
            else Text(emoji, fontSize = 24.sp, textAlign = TextAlign.Center)
        }
        when {
            fullPath != null && item.format == StickerFileFormat.Tgs ->
                TgsSticker(fullPath, Modifier.fillMaxSize(), playing = motion, fallback = fallback)
            fullPath != null && item.format != StickerFileFormat.Webm ->
                LocalMediaImage(fullPath, Modifier.fillMaxSize(), fit = true, maxDimension = maxDimension)
            thumbnailPath != null ->
                LocalMediaImage(thumbnailPath, Modifier.fillMaxSize(), fit = true, maxDimension = maxDimension)
            file.isDownloading || thumbnail.isDownloading ->
                CircularProgressIndicator(Modifier.size(18.dp), color = palette.accent, strokeWidth = 2.dp)
            else -> Text(emoji, fontSize = 24.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun GifGrid(
    animations: List<AnimationItem>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onSend: (AnimationItem) -> Unit,
    onPreview: (AnimationItem) -> Unit,
    footer: (@Composable () -> Unit)? = null
) {
    val gridState = rememberLazyGridState()
    // Search pages and recents can repeat an item; a duplicate lazy key would crash the grid.
    val uniqueAnimations = remember(animations) { animations.distinctBy { it.key } }
    LaunchedEffect(animations.firstOrNull()?.key) { if (animations.isNotEmpty()) gridState.scrollToItem(0) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        gridItems(uniqueAnimations, key = { it.key }) { item ->
            GifPanelCell(
                item = item,
                observeFile = observeFile,
                downloadFile = downloadFile,
                onSend = { onSend(item) },
                onPreview = { onPreview(item) }
            )
        }
        if (footer != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "footer") { footer() }
        }
    }
}

@Composable
private fun GifPanelCell(
    item: AnimationItem,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onSend: () -> Unit,
    onPreview: () -> Unit
) {
    val palette = beyGramPalette()
    val name = item.fileName.ifBlank { stringResource(R.string.gif_panel_label) }
    val description = stringResource(R.string.gif_panel_item_description, name)
    Box(
        Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp))
            .background(palette.surface2)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.gif_panel_send),
                onLongClickLabel = stringResource(R.string.gif_panel_preview_title),
                onClick = onSend,
                onLongClick = onPreview
            )
            .semantics { contentDescription = description }
    ) {
        GifPanelArtwork(item, Modifier.fillMaxSize(), observeFile, downloadFile)
        Text(
            stringResource(R.string.gif_panel_label),
            color = palette.onAccent,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                .clip(RoundedCornerShape(6.dp)).background(palette.accent.copy(alpha = 0.88f))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        )
        if (item.durationSeconds > 0) {
            Text(
                stringResource(R.string.gif_panel_duration, item.durationSeconds),
                color = palette.textPrimary,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .clip(RoundedCornerShape(6.dp)).background(palette.surface.copy(alpha = 0.84f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
    }
}

/** Only the small thumbnail is downloaded; an animated preview is intentionally not started here. */
@Composable
private fun GifPanelArtwork(
    item: AnimationItem,
    modifier: Modifier,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit
) {
    val palette = beyGramPalette()
    val thumbnail = rememberPanelFile(item.thumbnailFileId, observeFile)
    LaunchedEffect(item.thumbnailFileId) { item.thumbnailFileId?.let(downloadFile) }
    val path = thumbnail.localPath
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            path != null -> LocalMediaImage(path, Modifier.fillMaxSize(), fit = true, maxDimension = 320)
            item.minithumbnail != null -> MinithumbnailImage(item.minithumbnail, Modifier.fillMaxSize())
            thumbnail.isDownloading -> CircularProgressIndicator(Modifier.size(20.dp), color = palette.accent, strokeWidth = 2.dp)
            else -> Icon(Icons.Rounded.Animation, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(28.dp))
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> PickerLoad<T>.readyValue(): T? = (this as? PickerLoad.Ready<T>)?.value

@Composable
private fun <T> PickerLoadContent(
    state: PickerLoad<T>,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit
) {
    when (val current = state) {
        PickerLoad.Loading -> PanelLoading()
        is PickerLoad.Failed -> PanelError(current.message, onRetry)
        is PickerLoad.Ready<*> -> content(current.value as T)
        else -> PanelLoading()
    }
}

@Composable
private fun PanelLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), color = beyGramPalette().accent, strokeWidth = 2.dp)
    }
}

@Composable
private fun PanelEmpty(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = beyGramPalette().textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PanelError(text: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp).errorAnnouncement(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text, color = beyGramPalette().danger, fontSize = 14.sp, textAlign = TextAlign.Center)
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.common_retry))
        }
    }
}

@Composable
private fun PanelNotice(text: String) {
    Text(
        text,
        color = beyGramPalette().danger,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp).errorAnnouncement()
    )
}

@Composable
private fun PickerMore(loading: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = beyGramPalette().accent, strokeWidth = 2.dp)
        else TextButton(onClick = onClick) { Text(stringResource(R.string.sticker_panel_load_more)) }
    }
}

@Composable
private fun PaginationError(text: String, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).errorAnnouncement(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = beyGramPalette().danger, fontSize = 12.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.common_retry))
        }
    }
}

/** Collects a nullable TDLib file; a missing thumbnail never changes the number of remembered states. */
@Composable
private fun rememberPanelFile(fileId: Int?, observeFile: (Int) -> StateFlow<MediaFileState>): MediaFileState {
    val placeholder = remember { MutableStateFlow(MediaFileState(0)) }
    val flow = remember(fileId) { fileId?.let(observeFile) ?: placeholder }
    val state by flow.collectAsStateWithLifecycle()
    return state
}

private sealed interface StickerPanelOverlay {
    data class Sticker(val item: StickerItem) : StickerPanelOverlay
    data class Gif(val item: AnimationItem) : StickerPanelOverlay
    data class Pack(val summary: StickerSetSummary) : StickerPanelOverlay
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StickerPanelOverlayContent(
    overlay: StickerPanelOverlay,
    packTitle: String?,
    sending: Boolean,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    favoriteKeys: Set<String>,
    favoriteBusyKeys: Set<String>,
    premium: Boolean?,
    onDismiss: () -> Unit,
    onSendSticker: (StickerItem) -> Unit,
    onStickerOptions: (StickerItem) -> Unit,
    onToggleFavorite: (StickerItem, Boolean) -> Unit,
    onSendAnimation: (AnimationItem) -> Unit,
    onAttachAnimation: (AnimationItem) -> Unit,
    onOpenPack: (Long) -> Unit
) {
    val palette = beyGramPalette()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = palette.surface) {
        when (overlay) {
            is StickerPanelOverlay.Sticker -> StickerPreviewContent(
                item = overlay.item,
                packTitle = packTitle,
                sending = sending,
                observeFile = observeFile,
                downloadFile = downloadFile,
                canSend = !(overlay.item.premium && premium == false),
                isFavorite = overlay.item.key in favoriteKeys,
                favoriteBusy = overlay.item.key in favoriteBusyKeys,
                onSend = { onSendSticker(overlay.item) },
                onOptions = { onStickerOptions(overlay.item) },
                onToggleFavorite = { onToggleFavorite(overlay.item, !(overlay.item.key in favoriteKeys)) },
                onOpenPack = { onOpenPack(overlay.item.setId) }
            )
            is StickerPanelOverlay.Gif -> GifPreviewContent(
                item = overlay.item,
                sending = sending,
                observeFile = observeFile,
                downloadFile = downloadFile,
                onSend = { onSendAnimation(overlay.item) },
                onAttach = { onAttachAnimation(overlay.item) }
            )
            is StickerPanelOverlay.Pack -> PackPreviewContent(
                summary = overlay.summary,
                observeFile = observeFile,
                downloadFile = downloadFile,
                onOpen = { onOpenPack(overlay.summary.id) }
            )
        }
    }
}

@Composable
private fun StickerPreviewContent(
    item: StickerItem,
    packTitle: String?,
    sending: Boolean,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    canSend: Boolean,
    isFavorite: Boolean,
    favoriteBusy: Boolean,
    onSend: () -> Unit,
    onOptions: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenPack: () -> Unit
) {
    val palette = beyGramPalette()
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(
            stringResource(R.string.sticker_panel_preview_title),
            color = palette.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        val emoji = item.emoji.ifBlank { stringResource(R.string.sticker_panel_sticker_label) }
        Text(emoji, color = palette.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 240.dp)
                .clip(RoundedCornerShape(18.dp)).background(palette.surface2.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            StickerPanelArtwork(
                item = item,
                modifier = Modifier.fillMaxSize().padding(14.dp),
                observeFile = observeFile,
                downloadFile = downloadFile,
                maxDimension = 512
            )
        }
        Spacer(Modifier.height(10.dp))
        val format = stickerFormatLabel(item.format)
        val dimensions = if (item.width > 0 && item.height > 0) {
            stringResource(R.string.sticker_panel_dimensions, item.width, item.height)
        } else null
        Text(
            listOfNotNull(format, dimensions).joinToString(" · "),
            color = palette.textSecondary,
            fontSize = 12.sp
        )
        if (!packTitle.isNullOrBlank()) {
            Text(
                packTitle,
                color = palette.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!canSend) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.sticker_panel_premium_locked),
                color = palette.danger,
                fontSize = 12.sp,
                modifier = Modifier.errorAnnouncement()
            )
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onSend,
            enabled = canSend && !sending,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = palette.onAccent,
                disabledContainerColor = palette.surface2,
                disabledContentColor = palette.textSecondary
            )
        ) { Text(stringResource(R.string.common_send)) }
        TextButton(
            onClick = onOptions,
            enabled = canSend && !sending,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.sticker_panel_options))
        }
        TextButton(
            onClick = onToggleFavorite,
            enabled = !favoriteBusy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Icon(
                if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(
                    if (isFavorite) R.string.sticker_panel_favorite_remove else R.string.sticker_panel_favorite_add
                )
            )
        }
        if (item.setId != 0L) {
            TextButton(
                onClick = onOpenPack,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(Icons.Rounded.Animation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sticker_panel_open_pack))
            }
        }
    }
}

@Composable
private fun GifPreviewContent(
    item: AnimationItem,
    sending: Boolean,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit
) {
    val palette = beyGramPalette()
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(
            stringResource(R.string.gif_panel_preview_title),
            color = palette.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        item.fileName.takeIf { it.isNotBlank() }?.let {
            Text(it, color = palette.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(18.dp)).background(palette.surface2),
            contentAlignment = Alignment.Center
        ) {
            GifPanelArtwork(item, Modifier.fillMaxSize(), observeFile, downloadFile)
        }
        Spacer(Modifier.height(10.dp))
        val dimensions = if (item.width > 0 && item.height > 0) {
            stringResource(R.string.sticker_panel_dimensions, item.width, item.height)
        } else null
        val duration = item.durationSeconds.takeIf { it > 0 }?.let { stringResource(R.string.gif_panel_duration, it) }
        Text(
            listOfNotNull(duration, dimensions).joinToString(" · "),
            color = palette.textSecondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onSend,
            enabled = !sending,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = palette.onAccent,
                disabledContainerColor = palette.surface2,
                disabledContentColor = palette.textSecondary
            )
        ) { Text(stringResource(R.string.gif_panel_send)) }
        TextButton(
            onClick = onAttach,
            enabled = !sending,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text(stringResource(R.string.gif_panel_add_caption)) }
    }
}

@Composable
private fun PackPreviewContent(
    summary: StickerSetSummary,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onOpen: () -> Unit
) {
    val palette = beyGramPalette()
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.sticker_panel_pack_menu),
            color = palette.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        val cover = summary.cover
        if (cover != null) {
            StickerPanelArtwork(
                item = cover,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)),
                observeFile = observeFile,
                downloadFile = downloadFile,
                maxDimension = 256
            )
        } else {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)).background(palette.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Text(summary.title.take(1), color = palette.accentInk, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            summary.title,
            color = palette.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        if (summary.size > 0) {
            Text(
                stringResource(R.string.sticker_panel_pack_size, summary.size),
                color = palette.textSecondary,
                fontSize = 12.sp
            )
        }
        if (summary.isOfficial || summary.isInstalled) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (summary.isOfficial) InfoBadge(stringResource(R.string.sticker_panel_pack_official))
                if (summary.isInstalled) InfoBadge(stringResource(R.string.sticker_panel_pack_installed))
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent)
        ) { Text(stringResource(R.string.sticker_panel_open_pack)) }
    }
}

@Composable
private fun InfoBadge(text: String) {
    val palette = beyGramPalette()
    Text(
        text,
        color = palette.accentInk,
        fontSize = 11.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(palette.accentSoft)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun stickerFormatLabel(format: StickerFileFormat): String = stringResource(
    when (format) {
        StickerFileFormat.Webp -> R.string.sticker_panel_format_static
        StickerFileFormat.Tgs -> R.string.sticker_panel_format_animated
        StickerFileFormat.Webm -> R.string.sticker_panel_format_video
    }
)
