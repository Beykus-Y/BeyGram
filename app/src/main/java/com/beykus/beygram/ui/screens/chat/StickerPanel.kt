package com.beykus.beygram.ui.screens.chat

import androidx.compose.animation.core.Animatable
import com.beykus.beygram.ui.design.GlassEdge
import com.beykus.beygram.ui.design.GlassPanel
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassSheet
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.design.styledTopShape
import com.beykus.beygram.ui.design.usesGlassPanels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import com.beykus.beygram.playback.LocalPlaybackAccountId
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import com.beykus.beygram.ui.components.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.beykus.beygram.ui.motion.rememberMotionInstant
import com.beykus.beygram.ui.motion.scrollToItemRespectingMotion
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.beykus.beygram.ui.components.BeyGramSheet
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.StickerShelfVersions
import com.beykus.beygram.domain.model.CustomEmoji
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
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.rememberAutoplayEligible
import com.beykus.beygram.ui.motion.rememberMotionSpec
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

/** Draws the single sticker/GIF/emoji action inside the composer TextField trailing slot. */
@Composable
internal fun StickerPanelEntryButton(open: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val label = stringResource(if (open) R.string.sticker_panel_open_keyboard else R.string.sticker_panel_entry)
    IconButton(
        onClick = {
            onClick()
            if (!open) {
                focus.clearFocus()
                keyboard?.hide()
            }
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
            if (open) Icons.Rounded.Keyboard else Icons.Rounded.EmojiEmotions,
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
    keyboardHeightHint: androidx.compose.ui.unit.Dp,
    isTransitioning: Boolean = false,
    onReplyConsumed: (Long?) -> Unit,
    onAnimationAttach: (AnimationItem) -> Unit,
    onStickerOptions: (StickerItem) -> Unit,
    onSendError: (String) -> Unit,
    onInsertEmoji: (String) -> Unit,
    onInsertCustomEmoji: (CustomEmoji) -> Unit,
    savedMessages: Boolean,
    onDeleteBackward: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val accountId = LocalPlaybackAccountId.current
    val packUsage = remember(context, accountId) { PackUsageStore(context, accountId, PackKind.Sticker) }
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
            keyboardHeightHint = keyboardHeightHint,
            isTransitioning = isTransitioning,
            stickerUsage = packUsage,
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
                            if (result.isSuccess) {
                                packUsage.record(item.setId)
                                onReplyConsumed(targetReplyId)
                            } else {
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
            onInsertCustomEmoji = onInsertCustomEmoji,
            savedMessages = savedMessages,
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
    keyboardHeightHint: androidx.compose.ui.unit.Dp,
    isTransitioning: Boolean = false,
    stickerUsage: PackUsageStore,
    onSendSticker: (StickerItem) -> Unit,
    onStickerOptions: (StickerItem) -> Unit,
    onSendAnimation: (AnimationItem) -> Unit,
    onAttachAnimation: (AnimationItem) -> Unit,
    onInsertEmoji: (String) -> Unit,
    onInsertCustomEmoji: (CustomEmoji) -> Unit,
    savedMessages: Boolean,
    onDeleteBackward: () -> Unit,
    onClose: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val tabState = rememberSaveableStateHolder()
    var view by rememberSaveable(stateSaver = StickerPanelViewSaver) { mutableStateOf(StickerPanelView()) }

    val accountId = LocalPlaybackAccountId.current
    val cachedRecentStickers = remember(accountId, conversation) {
        MediaPickerSessionCache.getRecentStickers(accountId, conversation)
    }
    var recentState by remember(accountId, conversation) {
        mutableStateOf<PickerLoad<List<StickerItem>>>(
            cachedRecentStickers?.let { PickerLoad.Ready(it.value) } ?: PickerLoad.Loading
        )
    }
    var recentLoadedVersion by remember(accountId, conversation) {
        mutableStateOf(cachedRecentStickers?.version)
    }
    var recentRetry by remember { mutableIntStateOf(0) }
    val cachedFavoriteStickers = remember(accountId, conversation) {
        MediaPickerSessionCache.getFavoriteStickers(accountId, conversation)
    }
    var favoritesState by remember(accountId, conversation) {
        mutableStateOf<PickerLoad<List<StickerItem>>>(
            cachedFavoriteStickers?.let { PickerLoad.Ready(it.value) } ?: PickerLoad.Loading
        )
    }
    var favoritesLoadedVersion by remember(accountId, conversation) {
        mutableStateOf(cachedFavoriteStickers?.version)
    }
    var favoritesRetry by remember { mutableIntStateOf(0) }
    val cachedSets = remember(accountId, conversation) {
        MediaPickerSessionCache.getInstalledStickerSets(accountId, conversation)
    }
    var setsState by remember(accountId, conversation) {
        mutableStateOf<PickerLoad<List<StickerSetSummary>>>(
            cachedSets?.let { PickerLoad.Ready(it.value) } ?: PickerLoad.Loading
        )
    }
    var setsLoadedVersion by remember(accountId, conversation) {
        mutableStateOf(cachedSets?.version)
    }
    var setsRetry by remember { mutableIntStateOf(0) }
    val packContents = remember(accountId, conversation) {
        androidx.compose.runtime.mutableStateMapOf<Long, PickerLoad<StickerSetContent>>().apply {
            MediaPickerSessionCache.snapshotStickerPacks(accountId, conversation).forEach { (id, pack) ->
                put(id, PickerLoad.Ready(pack))
            }
        }
    }
    var packState by remember { mutableStateOf<PickerLoad<StickerSetContent>>(PickerLoad.Loading) }
    var packRetry by remember { mutableIntStateOf(0) }
    var stickerSearchState by remember { mutableStateOf<PickerLoad<StickerSearchPage>>(PickerLoad.Ready(StickerSearchPage())) }
    var stickerSearchRetry by remember { mutableIntStateOf(0) }
    var stickerSearchLoadingMore by remember { mutableStateOf(false) }
    var stickerSearchMoreError by remember { mutableStateOf<String?>(null) }
    val cachedGifRecent = remember(accountId, conversation) {
        MediaPickerSessionCache.getGifRecent(accountId, conversation)
    }
    var gifRecentState by remember(accountId, conversation) {
        mutableStateOf<PickerLoad<AnimationPage>>(
            cachedGifRecent?.let { PickerLoad.Ready(it) } ?: PickerLoad.Loading
        )
    }
    var gifRecentRetry by remember { mutableIntStateOf(0) }
    var gifRecentLoadedRetry by remember(accountId, conversation) {
        mutableIntStateOf(if (cachedGifRecent != null) 0 else -1)
    }
    var gifRecentLoadedConversation by remember(accountId, conversation) {
        mutableStateOf<ConversationRepository?>(if (cachedGifRecent != null) conversation else null)
    }
    var gifRecentGeneration by remember { mutableIntStateOf(0) }
    var gifRecentLoadingMore by remember { mutableStateOf(false) }
    var gifRecentMoreError by remember { mutableStateOf<String?>(null) }
    val cachedGifSaved = remember(accountId, conversation) {
        MediaPickerSessionCache.getGifSaved(accountId, conversation)
    }
    var gifSavedState by remember(accountId, conversation) {
        mutableStateOf<PickerLoad<List<AnimationItem>>>(
            cachedGifSaved?.let { PickerLoad.Ready(it.value) } ?: PickerLoad.Loading
        )
    }
    var gifSavedRetry by remember { mutableIntStateOf(0) }
    var gifSavedLoadedRetry by remember(accountId, conversation) {
        mutableIntStateOf(if (cachedGifSaved != null) 0 else -1)
    }
    var gifSavedLoadedVersion by remember(accountId, conversation) {
        mutableStateOf(cachedGifSaved?.version ?: -1L)
    }
    var gifSavedLoadedConversation by remember(accountId, conversation) {
        mutableStateOf<ConversationRepository?>(if (cachedGifSaved != null) conversation else null)
    }
    var gifSearchState by remember { mutableStateOf<PickerLoad<AnimationPage>>(PickerLoad.Ready(AnimationPage())) }
    var gifSearchRetry by remember { mutableIntStateOf(0) }
    var gifSearchLoadingMore by remember { mutableStateOf(false) }
    var gifSearchMoreError by remember { mutableStateOf<String?>(null) }
    var premium by remember(accountId, conversation) {
        mutableStateOf(MediaPickerSessionCache.getIsPremium(accountId, conversation))
    }
    var notice by remember { mutableStateOf<String?>(null) }
    var reorderingStickers by remember { mutableStateOf(false) }
    var savingGifKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmClearRecent by remember { mutableStateOf(false) }
    var favoriteBusyKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchGeneration by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<StickerPanelOverlay?>(null) }
    val emojiUsage = remember(context, accountId) { PackUsageStore(context, accountId, PackKind.CustomEmoji) }
    var dynamicPacks by remember(stickerUsage) { mutableStateOf(stickerUsage.peekDynamic() ?: true) }
    var packOrderDialog by remember { mutableStateOf(false) }
    val emojiStore = remember(context, accountId) { RecentEmojiStore(context, accountId) }
    val shelfVersions by remember(conversation) { conversation.observeStickerShelfVersions() }
        .collectAsStateWithLifecycle(StickerShelfVersions())
    var recentEmoji by remember(emojiStore) { mutableStateOf(emojiStore.peekUnicode().orEmpty()) }
    var recentCustomEmoji by remember(emojiStore) { mutableStateOf(emojiStore.peekCustom().orEmpty()) }
    var emojiClearGeneration by remember(emojiStore) { mutableIntStateOf(0) }
    var confirmClearEmoji by remember { mutableStateOf(false) }

    LaunchedEffect(stickerUsage, emojiUsage, emojiStore) {
        stickerUsage.warmUp()
        emojiUsage.warmUp()
        dynamicPacks = stickerUsage.isDynamic()
        val (unicode, custom) = emojiStore.warmUp()
        recentEmoji = unicode
        recentCustomEmoji = custom
    }

    val premiumNotice = stringResource(R.string.sticker_panel_premium_locked)
    val favoriteKeys: Set<String> = remember(favoritesState) {
        favoritesState.readyValue()?.mapTo(hashSetOf()) { it.key } ?: emptySet()
    }
    val packTitle = packState.readyValue()?.takeIf { it.id == view.selectedSetId }?.title
        ?: setsState.readyValue()?.firstOrNull { it.id == view.selectedSetId }?.title.orEmpty()
    val searchPlaceholder = stringResource(
        if (view.tab == StickerPanelTab.Gifs) R.string.gif_panel_search else R.string.sticker_panel_search
    )
    val density = LocalDensity.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val windowHeight = with(density) { windowHeightPx.toDp() }
    // Everything below the panel (reply banner, typing row, mini player, input row, IME and navigation bar), measured from
    // the panel's own bottom edge in the window. Until the first layout, [composerReserve] and the IME give an estimate.
    var belowPanelPx by remember { mutableIntStateOf(0) }
    val imeHeight = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val belowPanel = if (belowPanelPx > 0) with(density) { belowPanelPx.toDp() } else imeHeight + composerReserve
    val availableHeight = (windowHeight - belowPanel).coerceAtLeast(MIN_PANEL_WITH_KEYBOARD)
    val preferredHeight = keyboardHeightHint.takeIf { it > 0.dp }?.let { minOf(it, windowHeight * 0.62f) }
        ?: (windowHeight * 0.62f)
    val panelHeight = preferredHeight.coerceIn(PANEL_MIN_HEIGHT, PANEL_MAX_HEIGHT)
        .coerceAtMost(availableHeight)
    val displayError = notice ?: error

    LaunchedEffect(conversation) {
        notice = null
        overlay = null
        favoriteBusyKeys = emptySet()
    }
    LaunchedEffect(conversation, view.tab, recentRetry, shelfVersions.recentStickers) {
        if (view.tab != StickerPanelTab.Stickers) return@LaunchedEffect
        val targetVersion = shelfVersions.recentStickers
        if (recentRetry == 0 && recentLoadedVersion == targetVersion && recentState is PickerLoad.Ready) {
            return@LaunchedEffect
        }
        recentState = recentState.beginRefresh()
        conversation.recentStickers().fold(
            onSuccess = {
                val deduped = it.distinctBy { sticker -> sticker.key }
                recentState = PickerLoad.Ready(deduped)
                recentLoadedVersion = targetVersion
                MediaPickerSessionCache.putRecentStickers(accountId, conversation, deduped, targetVersion)
            },
            onFailure = {
                val msg = it.userMessage(context, R.string.sticker_panel_load_failed)
                if (recentState is PickerLoad.Ready) notice = msg else recentState = PickerLoad.Failed(msg)
            }
        )
    }
    LaunchedEffect(conversation, view.tab, favoritesRetry, shelfVersions.favoriteStickers) {
        if (view.tab != StickerPanelTab.Stickers) return@LaunchedEffect
        val targetVersion = shelfVersions.favoriteStickers
        if (favoritesRetry == 0 && favoritesLoadedVersion == targetVersion && favoritesState is PickerLoad.Ready) {
            return@LaunchedEffect
        }
        favoritesState = favoritesState.beginRefresh()
        conversation.favoriteStickers().fold(
            onSuccess = {
                val deduped = it.distinctBy { sticker -> sticker.key }
                favoritesState = PickerLoad.Ready(deduped)
                favoritesLoadedVersion = targetVersion
                MediaPickerSessionCache.putFavoriteStickers(accountId, conversation, deduped, targetVersion)
            },
            onFailure = {
                val msg = it.userMessage(context, R.string.sticker_panel_load_failed)
                if (favoritesState is PickerLoad.Ready) notice = msg else favoritesState = PickerLoad.Failed(msg)
            }
        )
    }
    LaunchedEffect(conversation, view.tab, setsRetry, shelfVersions.stickerPacks) {
        if (view.tab != StickerPanelTab.Stickers) return@LaunchedEffect
        val targetVersion = shelfVersions.stickerPacks
        if (setsRetry == 0 && setsLoadedVersion == targetVersion && setsState is PickerLoad.Ready) {
            return@LaunchedEffect
        }
        setsState = setsState.beginRefresh()
        conversation.installedStickerSets().fold(
            onSuccess = {
                setsState = PickerLoad.Ready(it)
                setsLoadedVersion = targetVersion
                MediaPickerSessionCache.putInstalledStickerSets(accountId, conversation, it, targetVersion)
            },
            onFailure = {
                val msg = it.userMessage(context, R.string.sticker_panel_load_failed)
                if (setsState is PickerLoad.Ready) notice = msg else setsState = PickerLoad.Failed(msg)
            }
        )
    }
    LaunchedEffect(conversation) {
        // The one-shot resolves the state even before the profile screen has loaded it; the flow then keeps locked
        // marks correct when Premium is bought or expires in another client.
        if (premium == null) {
            conversation.isPremiumAccount().onSuccess {
                premium = it
                MediaPickerSessionCache.putIsPremium(accountId, conversation, it)
            }
        }
        conversation.observeIsPremium().collect { observed ->
            if (observed != null) {
                premium = observed
                MediaPickerSessionCache.putIsPremium(accountId, conversation, observed)
            }
        }
    }
    // Telegram keeps recent emoji locally, so a fresh install can only rebuild them from own sent messages. The scan
    // starts only when the Emoji tab is actually opened, is bounded in the repository, and is suppressed for good by
    // an explicit clear. A cancelled/failed attempt is retried after the store's pause, not on every dock opening.
    LaunchedEffect(conversation, accountId, view.tab) {
        if (view.tab != StickerPanelTab.Emoji) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (!emojiStore.mayAttemptRestore(now)) return@LaunchedEffect
        val generation = emojiClearGeneration
        emojiStore.markRestoreAttempt(now)
        conversation.recoverRecentEmoji().onSuccess { history ->
            if (generation != emojiClearGeneration || emojiStore.restoreSuppressed()) return@LaunchedEffect
            recentEmoji = emojiStore.mergeRecovered(history)
            recentCustomEmoji = emojiStore.mergeRecoveredCustom(history.customEmojiIds)
            emojiStore.markRestoreDone()
        }
    }
    LaunchedEffect(conversation, view.selectedSetId, packRetry) {
        val setId = view.selectedSetId ?: return@LaunchedEffect
        val cachedPack = if (packRetry == 0) {
            (packContents[setId] as? PickerLoad.Ready)?.value
                ?: MediaPickerSessionCache.getStickerPack(accountId, conversation, setId)
        } else null
        if (cachedPack != null) {
            packState = PickerLoad.Ready(cachedPack)
            packContents[setId] = PickerLoad.Ready(cachedPack)
            return@LaunchedEffect
        }
        if (packState.readyValue()?.id != setId) {
            packState = PickerLoad.Loading
        }
        conversation.stickerSet(setId).fold(
            onSuccess = {
                val clean = it.copy(stickers = it.stickers.distinctBy { sticker -> sticker.key })
                packState = PickerLoad.Ready(clean)
                packContents[setId] = PickerLoad.Ready(clean)
                MediaPickerSessionCache.putStickerPack(accountId, conversation, clean)
            },
            onFailure = {
                val msg = it.userMessage(context, R.string.sticker_panel_load_failed)
                if (packState.readyValue()?.id == setId) notice = msg else packState = PickerLoad.Failed(msg)
            }
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
                val page = it.copy(animations = it.animations.distinctBy { animation -> animation.key })
                gifRecentState = PickerLoad.Ready(page)
                gifRecentLoadedRetry = gifRecentRetry
                gifRecentLoadedConversation = conversation
                MediaPickerSessionCache.putGifRecent(accountId, conversation, page)
            },
            onFailure = { gifRecentState = PickerLoad.Failed(it.userMessage(context, R.string.gif_panel_load_failed)) }
        )
    }
    LaunchedEffect(conversation, view.tab, gifSavedRetry, shelfVersions.savedAnimations) {
        if (view.tab != StickerPanelTab.Gifs) return@LaunchedEffect
        val savedVersion = shelfVersions.savedAnimations
        if (gifSavedLoadedRetry == gifSavedRetry && gifSavedLoadedVersion == savedVersion &&
            gifSavedLoadedConversation === conversation && gifSavedState.readyValue() != null
        ) return@LaunchedEffect
        gifSavedState = PickerLoad.Loading
        conversation.savedAnimations().fold(
            onSuccess = {
                val deduped = it.distinctBy { animation -> animation.key }
                gifSavedState = PickerLoad.Ready(deduped)
                gifSavedLoadedRetry = gifSavedRetry
                gifSavedLoadedVersion = savedVersion
                gifSavedLoadedConversation = conversation
                MediaPickerSessionCache.putGifSaved(accountId, conversation, deduped, savedVersion)
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
                        val updated = updateFavoriteStickers(current, item, favorite)
                        favoritesState = PickerLoad.Ready(updated)
                        MediaPickerSessionCache.putFavoriteStickers(
                            accountId,
                            conversation,
                            updated,
                            shelfVersions.favoriteStickers
                        )
                    } else {
                        // The initial list was unavailable; reconcile from TDLib so the preview can offer removal.
                        favoritesRetry++
                    }
                }
                .onFailure { notice = it.userMessage(context, R.string.sticker_panel_favorite_failed) }
            favoriteBusyKeys = favoriteBusyKeys - item.key
        }
    }

    fun loadPackContent(setId: Long, force: Boolean = false) {
        val current = packContents[setId]
        if (!force && (current is PickerLoad.Loading || current is PickerLoad.Ready)) return
        packContents[setId] = PickerLoad.Loading
        scope.launch {
            conversation.stickerSet(setId).fold(
                onSuccess = {
                    val clean = it.copy(stickers = it.stickers.distinctBy { sticker -> sticker.key })
                    packContents[setId] = PickerLoad.Ready(clean)
                    MediaPickerSessionCache.putStickerPack(accountId, conversation, clean)
                },
                onFailure = {
                    packContents[setId] = PickerLoad.Failed(it.userMessage(context, R.string.sticker_panel_load_failed))
                }
            )
        }
    }

    fun updateSavedGif(item: AnimationItem, saved: Boolean) {
        if (item.key in savingGifKeys) return
        savingGifKeys = savingGifKeys + item.key
        scope.launch {
            try {
                conversation.setAnimationSaved(item, saved)
                    .onSuccess { gifSavedRetry++ }
                    .onFailure { notice = it.userMessage(context, R.string.gif_panel_save_failed) }
            } finally {
                savingGifKeys = savingGifKeys - item.key
            }
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

    // Tab body fades in on every tab change. The header and tab strip stay put; an instant spec keeps alpha at 1.
    val pickerSwitch = rememberMotionSpec(MotionToken.PickerSwitch)
    val tabSwitch = remember { Animatable(1f) }
    LaunchedEffect(view.tab, pickerSwitch) {
        if (pickerSwitch.isInstant) {
            tabSwitch.snapTo(1f)
        } else {
            tabSwitch.snapTo(0f)
            tabSwitch.animateTo(1f, pickerSwitch.animationSpec())
        }
    }

    val design = LocalDesignSystem.current
    val onSharedGlass = LocalComposerOnGlassPlane.current || LocalChatChromeOnGlass.current
    val panelBaseModifier = Modifier
        .fillMaxWidth()
        .onGloballyPositioned { coordinates ->
            if (!isTransitioning) {
                val below = (windowHeightPx - coordinates.boundsInWindow().bottom).roundToInt().coerceAtLeast(0)
                if (below != belowPanelPx) belowPanelPx = below
            }
        }
        .heightIn(max = panelHeight)
    val panelBody: @Composable ColumnScope.() -> Unit = {
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
                selectedTab = view.tab,
                canGoBack = view.canGoBack,
                showSearch = true,
                onBack = ::navigateBack,
                onSearch = { notice = null; view = view.openSearch() },
                onClose = ::closePanel,
                onSelectTab = { next -> if (next != view.tab) { notice = null; view = view.switchTab(next) } }
            )
            if (view.section == StickerPanelSection.Pack && packTitle.isNotBlank()) {
                Text(
                    packTitle, color = palette.textSecondary, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }
        }
        if (sending) LinearProgressIndicator(
            Modifier.fillMaxWidth().height(2.dp),
            color = palette.accent,
            trackColor = palette.surface2
        )
        if (displayError != null) PanelNotice(displayError)
        val panelCovered = overlay != null || confirmClearEmoji || confirmClearRecent || packOrderDialog
        val parentEmojiPolicy = LocalEmojiAnimationPolicy.current
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer { alpha = tabSwitch.value }
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalEmojiAnimationPolicy provides parentEmojiPolicy.copy(paused = parentEmojiPolicy.paused || panelCovered)
            ) {
                tabState.SaveableStateProvider(view.tab.name) {
                when (view.tab) {
                    StickerPanelTab.Emoji -> {
                        val customEmojiAvailable = customEmojiAllowed(premium, savedMessages)
                        val dock = rememberDockCustomEmoji(
                            conversation = conversation,
                            observeFile = observeFile,
                            downloadFile = downloadFile,
                            packsVersion = shelfVersions.emojiPacks,
locked = customEmojiLocked(premium, savedMessages),
                            recentIds = recentCustomEmoji,
                            packUsage = emojiUsage,
                            dynamicOrder = dynamicPacks,
                            onPackOrder = { packOrderDialog = true },
                            onReorderError = { notice = it },
                            onPick = { emoji ->
                                if (premium == null && !savedMessages) {
                                    scope.launch { conversation.isPremiumAccount().onSuccess { premium = it } }
                                }
                                if (customEmojiAvailable) onInsertCustomEmoji(emoji)
                                else notice = premiumNotice
                                customEmojiAvailable
                            },
                            onPicked = { id -> recentCustomEmoji = emojiStore.recordCustom(id) }
                        )
                        if (view.searchActive && view.query.isNotBlank()) {
                            DockEmojiSearch(conversation, view.query, dock, ::insertEmoji, Modifier.fillMaxWidth().weight(1f))
                        } else {
                            EmojiPanel(
                                recents = recentEmoji,
                                onEmoji = ::insertEmoji,
                                customEmoji = dock,
                                onBackspace = onDeleteBackward,
                                onClearRecent = { confirmClearEmoji = true },
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            )
                        }
                    }
                    StickerPanelTab.Stickers -> {
                        val stickerFeedState = rememberLazyGridState()
                        val listMotionInstant = rememberMotionInstant(MotionToken.ListReorder)
                        val favList = favoritesState.readyValue().orEmpty()
                        val recList = remember(recentState, favList) {
                            deduplicateRecentStickers(recentState.readyValue().orEmpty(), favList)
                        }
                        // Usage order is applied once when the list is available, never on a send or a tab switch: the pack under the
                        // finger must not move. With the mode off the server/manual order is used unchanged.
                        val orderedSetsState = remember(setsState, dynamicPacks) {
                            val ready = setsState.readyValue()
                            if (ready == null) setsState
                            else PickerLoad.Ready(if (dynamicPacks) stickerUsage.ordered(ready) { it.id } else ready)
                        }
                        val setsList = orderedSetsState.readyValue().orEmpty()
                        val favIndex = if (favList.isNotEmpty()) 0 else null
                        val recIndex = if (recList.isNotEmpty()) (if (favList.isNotEmpty()) 1 + favList.size else 0) else null
                        val packStartIndexes = remember(favList, recList, setsList, packContents.toMap()) {
                            val map = LinkedHashMap<Long, Int>()
                            var idx = (if (favList.isNotEmpty()) 1 + favList.size else 0) +
                                (if (recList.isNotEmpty()) 1 + recList.size else 0)
                            for (set in setsList) {
                                map[set.id] = idx
                                val loaded = (packContents[set.id] as? PickerLoad.Ready)?.value?.stickers.orEmpty()
                                idx += 1 + if (loaded.isNotEmpty()) loaded.size else 1
                            }
                            map
                        }
                        val activeFeedSection by remember(favIndex, recIndex, packStartIndexes) {
                            derivedStateOf {
                                val top = stickerFeedState.firstVisibleItemIndex
                                val matchedPack = packStartIndexes.entries.lastOrNull { it.value <= top }?.key
                                when {
                                    matchedPack != null -> StickerPanelSection.Pack to matchedPack
                                    recIndex != null && top >= recIndex -> StickerPanelSection.Recent to null
                                    favIndex != null && top >= favIndex -> StickerPanelSection.Favorites to null
                                    else -> view.section to view.selectedSetId
                                }
                            }
                        }
                        val stripView = if (!view.searchActive) {
                            view.copy(section = activeFeedSection.first, selectedSetId = activeFeedSection.second)
                        } else view
                        if (!view.searchActive) {
                            StickerSectionStrip(
                                view = stripView,
                                setsState = orderedSetsState,
                                observeFile = observeFile,
                                downloadFile = downloadFile,
                                onRecent = {
                                    notice = null
                                    view = view.showRecent()
                                    recIndex?.let { idx ->
                                        scope.launch { stickerFeedState.scrollToItemRespectingMotion(idx, motionInstant = listMotionInstant) }
                                    }
                                },
                                onFavorites = {
                                    notice = null
                                    view = view.showFavorites()
                                    favIndex?.let { idx ->
                                        scope.launch { stickerFeedState.scrollToItemRespectingMotion(idx, motionInstant = listMotionInstant) }
                                    }
                                },
                                onPack = { summary ->
                                    selectPack(summary.id)
                                    packStartIndexes[summary.id]?.let { idx ->
                                        scope.launch { stickerFeedState.scrollToItemRespectingMotion(idx, motionInstant = listMotionInstant) }
                                    }
                                },
                                onPackMenu = { summary -> openOverlay(StickerPanelOverlay.Pack(summary)) },
                                onRetrySets = { setsRetry++ }
                            )
                        }
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            if (view.query.isNotBlank()) {
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
                            } else {
                                StickerUnifiedFeed(
                                    gridState = stickerFeedState,
                                    favorites = favList,
                                    recent = recList,
                                    sets = setsList,
                                    setsState = orderedSetsState,
                                    packContents = packContents,
                                    packOrder = setsList.map { it.id },
                                    dynamicOrder = dynamicPacks,
                                    reordering = reorderingStickers,
                                    onReorder = { order ->
                                        if (!reorderingStickers) {
                                            reorderingStickers = true
                                            scope.launch {
                                                try {
                                                    conversation.reorderStickerSets(customEmoji = false, orderedIds = order)
                                                        .onSuccess { setsRetry++ }
                                                        .onFailure { notice = it.userMessage(context, R.string.sticker_panel_load_failed) }
                                                } finally {
                                                    reorderingStickers = false
                                                }
                                            }
                                        }
                                    },
                                    onLoadPack = { id -> loadPackContent(id) },
                                    onRetryPack = { id -> loadPackContent(id, force = true) },
                                    onRetrySets = { setsRetry++ },
                                    onClearRecent = { confirmClearRecent = true },
                                    onPackMenu = { summary -> openOverlay(StickerPanelOverlay.Pack(summary)) },
                                    observeFile = observeFile,
                                    downloadFile = downloadFile,
                                    onSend = ::requestSend,
                                    onOptions = { item -> openOverlay(StickerPanelOverlay.Sticker(item)) }
                                )
                            }
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
            }
        }
    }
    if (design.usesGlassPanels && !onSharedGlass) {
        GlassPanel(
            design = design,
            tint = palette.surface,
            rim = palette.textSecondary,
            backdropBase = palette.screen,
            shape = styledTopShape(20.dp),
            edge = GlassEdge.TopHairline,
            lensOnFreeEdge = true,
            modifier = panelBaseModifier
        ) {
            Column(Modifier.fillMaxWidth(), content = panelBody)
        }
    } else {
        Column(
            if (design.usesGlassPanels) panelBaseModifier.background(palette.surface.copy(alpha = 0.25f))
            else panelBaseModifier.glassSheet(design, palette.surface, palette.textSecondary),
            content = panelBody
        )
    }

    if (confirmClearRecent) {
        BeyGramDialog(
            onDismissRequest = { confirmClearRecent = false },
            title = stringResource(R.string.sticker_panel_clear_recent),
            message = stringResource(R.string.sticker_panel_clear_recent_confirm),
            confirmText = stringResource(R.string.sticker_panel_clear_recent),
            dismissText = stringResource(R.string.common_cancel),
            onDismiss = { confirmClearRecent = false },
            onConfirm = {
                confirmClearRecent = false
                scope.launch {
                    conversation.clearRecentStickers()
                        .onSuccess { recentRetry++ }
                        .onFailure { notice = it.userMessage(context, R.string.sticker_panel_load_failed) }
                }
            }
        )
    }
if (confirmClearEmoji) {
        BeyGramDialog(
            onDismissRequest = { confirmClearEmoji = false },
            title = stringResource(R.string.emoji_clear_recent),
            message = stringResource(R.string.emoji_clear_recent_confirm),
            confirmText = stringResource(R.string.emoji_clear_recent),
            dismissText = stringResource(R.string.common_cancel),
            onDismiss = { confirmClearEmoji = false },
            onConfirm = {
                confirmClearEmoji = false
                emojiClearGeneration++
                val (unicode, custom) = emojiStore.clearAll()
                recentEmoji = unicode
                recentCustomEmoji = custom
            }
        )
    }
    if (packOrderDialog) {
        PackOrderDialog(
            dynamic = dynamicPacks,
            onToggle = { enabled ->
                dynamicPacks = enabled
                stickerUsage.setDynamic(enabled)
                emojiUsage.setDynamic(enabled)
            },
            onDismiss = { packOrderDialog = false }
        )
    }
    val currentOverlay = overlay
    if (currentOverlay != null) {
        val overlayPackTitle = (currentOverlay as? StickerPanelOverlay.Sticker)?.item?.let { item ->
            packState.readyValue()?.takeIf { it.id == item.setId }?.title
                ?: setsState.readyValue()?.firstOrNull { it.id == item.setId }?.title
        }
        androidx.compose.runtime.CompositionLocalProvider(
            LocalEmojiAnimationPolicy provides EmojiAnimationPolicy(paused = false, priority = EmojiAnimationPolicy.PRIORITY_PREVIEW)
        ) {
            StickerPanelOverlayContent(
            overlay = currentOverlay,
            packTitle = overlayPackTitle,
            sending = sending,
            observeFile = observeFile,
            downloadFile = downloadFile,
            favoriteKeys = favoriteKeys,
            favoriteBusyKeys = favoriteBusyKeys,
            savedGifs = gifSavedState.readyValue()?.mapTo(HashSet()) { it.key },
            savingGifKeys = savingGifKeys,
            onSetSavedGif = ::updateSavedGif,
            regularPackOrder = setsState.readyValue().orEmpty().map { it.id },
            dynamicOrder = dynamicPacks,
            onPackOrder = { packOrderDialog = true },
            reorderingStickers = reorderingStickers,
            reorderStickers = { order ->
                if (!reorderingStickers) {
                    reorderingStickers = true
                    scope.launch {
                        try {
                            conversation.reorderStickerSets(customEmoji = false, orderedIds = order)
                                .onSuccess { setsRetry++ }
                                .onFailure { notice = it.userMessage(context, R.string.sticker_panel_load_failed) }
                        } finally {
                            reorderingStickers = false
                        }
                    }
                }
            },
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
}

@Composable
private fun StickerPanelHeader(
    selectedTab: StickerPanelTab,
    canGoBack: Boolean,
    showSearch: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    onSelectTab: (StickerPanelTab) -> Unit
) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (canGoBack) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), tint = palette.textSecondary)
            }
        }
        StickerPanelTabs(selectedTab, onSelectTab, Modifier.weight(1f))
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
            shape = styledShape(14.dp),
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
private fun StickerPanelTabs(selected: StickerPanelTab, onSelect: (StickerPanelTab) -> Unit, modifier: Modifier) {
    val palette = beyGramPalette()
    Row(
        modifier.padding(vertical = 2.dp)
            .clip(styledShape(14.dp))
            .background(palette.surface2)
            .padding(2.dp)
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
        modifier.heightIn(min = 48.dp).clip(styledShape(12.dp))
            .background(if (selected) palette.accent else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) palette.onAccent else palette.textPrimary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            textAlign = TextAlign.Center
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
        Modifier.fillMaxWidth().heightIn(min = 54.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "recent") {
            SectionPill(
                label = stringResource(R.string.sticker_panel_recent),
                icon = Icons.Rounded.History,
                selected = view.section == StickerPanelSection.Recent,
                onClick = onRecent,
                iconOnly = true
            )
        }
        item(key = "favorites") {
            SectionPill(
                label = stringResource(R.string.sticker_panel_favorites),
                icon = Icons.Rounded.Star,
                selected = view.section == StickerPanelSection.Favorites,
                onClick = onFavorites,
                iconOnly = true
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
internal fun SectionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    iconOnly: Boolean = false
) {
    val palette = beyGramPalette()
    Row(
        Modifier.heightIn(min = 48.dp)
            .then(if (iconOnly) Modifier.width(48.dp) else Modifier)
            .clip(RoundedCornerShape(50))
            .background(if (selected) palette.accentSoft else palette.surface2)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .then(if (iconOnly) Modifier.semantics { contentDescription = label } else Modifier.padding(horizontal = 14.dp)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) palette.accent else palette.textSecondary,
            modifier = Modifier.size(18.dp)
        )
        if (!iconOnly) {
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
    Box(
        Modifier.size(48.dp)
            .clip(styledShape(14.dp))
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
        contentAlignment = Alignment.Center
    ) {
        val cover = summary.cover
        if (cover != null) {
            // Covers are downloaded by the chip itself only while the strip is visible.
            StickerPanelArtwork(
                item = cover,
                modifier = Modifier.size(36.dp).clip(styledShape(10.dp)),
                observeFile = observeFile,
                downloadFile = downloadFile,
                maxDimension = 128,
                thumbnailOnly = true
            )
        } else {
            Box(
                Modifier.size(36.dp).clip(styledShape(10.dp)).background(palette.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Text(summary.title.take(1), color = palette.accentInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }

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
            val favorites = favoritesState.readyValue()
            val visible = remember(stickers, favorites) {
                val keys = favorites.orEmpty().mapTo(HashSet()) { it.key }
                stickers.filterNot { it.key in keys }
            }
            if (visible.isEmpty()) PanelEmpty(emptyRecent)
            else StickerGrid(visible, observeFile, downloadFile, onSend, onOptions)
        }
    }
}

@Composable
private fun StickerUnifiedFeed(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    favorites: List<StickerItem>,
    recent: List<StickerItem>,
    sets: List<StickerSetSummary>,
    setsState: PickerLoad<List<StickerSetSummary>>,
    packContents: Map<Long, PickerLoad<StickerSetContent>>,
    packOrder: List<Long>,
    /** Usage order is controlled by its own switch; the manual arrows would lie while it is on. */
    dynamicOrder: Boolean,
    reordering: Boolean,
    onReorder: (List<Long>) -> Unit,
    onLoadPack: (Long) -> Unit,
    onRetryPack: (Long) -> Unit,
    onRetrySets: () -> Unit,
    onClearRecent: () -> Unit,
    onPackMenu: (StickerSetSummary) -> Unit,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onSend: (StickerItem) -> Unit,
    onOptions: (StickerItem) -> Unit
) {
    val palette = beyGramPalette()
    if (favorites.isEmpty() && recent.isEmpty() && sets.isEmpty()) {
        when (setsState) {
            PickerLoad.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), color = palette.accent, strokeWidth = 2.dp)
            }
            is PickerLoad.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TextButton(onClick = onRetrySets) { Text(stringResource(R.string.common_retry)) }
            }
            is PickerLoad.Ready -> PanelEmpty(stringResource(R.string.sticker_panel_empty_recent))
        }
        return
    }
    val scrolling by remember(gridState) { derivedStateOf { gridState.isScrollInProgress } }
    val parentPolicy = LocalEmojiAnimationPolicy.current
    androidx.compose.runtime.CompositionLocalProvider(
        LocalEmojiAnimationPolicy provides parentPolicy.copy(
            paused = parentPolicy.paused || scrolling,
            priority = EmojiAnimationPolicy.PRIORITY_GRID
        )
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(StickerPickerMetrics.columnMin),
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            contentPadding = PaddingValues(StickerPickerMetrics.contentPadding),
            horizontalArrangement = Arrangement.spacedBy(StickerPickerMetrics.gap),
            verticalArrangement = Arrangement.spacedBy(StickerPickerMetrics.gap)
        ) {
        if (favorites.isNotEmpty()) {
            item(key = "header-favorites", span = { GridItemSpan(maxLineSpan) }) {
                EmojiGroupHeader(stringResource(R.string.sticker_panel_favorites))
            }
            gridItems(favorites, key = { "fav-${it.key}" }) { item ->
                StickerPanelCell(item, observeFile, downloadFile, onSend = { onSend(item) }, onOptions = { onOptions(item) })
            }
        }
        if (recent.isNotEmpty()) {
            item(key = "header-recent", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { EmojiGroupHeader(stringResource(R.string.sticker_panel_recent)) }
                    TextButton(onClick = onClearRecent) {
                        Text(stringResource(R.string.sticker_panel_clear_recent))
                    }
                }
            }
            gridItems(recent, key = { "rec-${it.key}" }) { item ->
                StickerPanelCell(item, observeFile, downloadFile, onSend = { onSend(item) }, onOptions = { onOptions(item) })
            }
        }
        for (summary in sets) {
            item(key = "header-pack-${summary.id}", span = { GridItemSpan(maxLineSpan) }) {
                LaunchedEffect(summary.id) { onLoadPack(summary.id) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).clickable { onPackMenu(summary) }
                    ) {
                        EmojiGroupHeader(summary.title)
                    }
                    if (!dynamicOrder) {
                            PickerPackOrderButtons(packOrder, summary.id, reordering, onReorder)
                        }
                }
            }
            when (val content = packContents[summary.id]) {
                is PickerLoad.Ready -> {
                    val stickers = content.value.stickers
                    if (stickers.isEmpty()) {
                        item(key = "pack-empty-${summary.id}", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(R.string.sticker_panel_empty_pack),
                                color = palette.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        gridItems(stickers, key = { "pack-${summary.id}-${it.key}" }) { item ->
                            StickerPanelCell(item, observeFile, downloadFile, onSend = { onSend(item) }, onOptions = { onOptions(item) })
                        }
                    }
                }
                is PickerLoad.Failed -> item(key = "pack-fail-${summary.id}", span = { GridItemSpan(maxLineSpan) }) {
                    TextButton(onClick = { onRetryPack(summary.id) }) { Text(stringResource(R.string.common_retry)) }
                }
                else -> item(key = "pack-load-${summary.id}", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = palette.accent, strokeWidth = 2.dp)
                    }
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
    val firstKey = uniqueStickers.firstOrNull()?.key
    var previousFirst by rememberSaveable { mutableStateOf(firstKey) }
    LaunchedEffect(firstKey) {
        if (firstKey != null && previousFirst != firstKey) {
            gridState.scrollToItem(0)
            previousFirst = firstKey
        }
    }
    val scrolling by remember(gridState) { derivedStateOf { gridState.isScrollInProgress } }
    val parentPolicy = LocalEmojiAnimationPolicy.current
    androidx.compose.runtime.CompositionLocalProvider(
        LocalEmojiAnimationPolicy provides parentPolicy.copy(
            paused = parentPolicy.paused || scrolling,
            priority = EmojiAnimationPolicy.PRIORITY_GRID
        )
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(StickerPickerMetrics.columnMin),
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            contentPadding = PaddingValues(StickerPickerMetrics.contentPadding),
            horizontalArrangement = Arrangement.spacedBy(StickerPickerMetrics.gap),
            verticalArrangement = Arrangement.spacedBy(StickerPickerMetrics.gap)
        ) {
        gridItems(uniqueStickers, key = { it.key }) { item ->
            StickerPanelCell(item, observeFile, downloadFile, onSend = { onSend(item) }, onOptions = { onOptions(item) })
        }
        if (footer != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "footer") { footer() }
        }
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
        Modifier.size(StickerPickerMetrics.cell).clip(styledShape(12.dp))
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
    val animPolicy = LocalEmojiAnimationPolicy.current
    val canAnimate = !thumbnailOnly && !animPolicy.paused && item.format != StickerFileFormat.Webm
    // Animated stickers play only after they have been on screen for the autoplay dwell time.
    val autoplay = rememberAutoplayEligible(enabled = canAnimate)
    val playing = canAnimate && autoplay.eligible.value
    val effectivePriority = if (thumbnailOnly) EmojiAnimationPolicy.PRIORITY_COVER else animPolicy.priority
    val file by remember(item.fileId) { observeFile(item.fileId) }.collectAsStateWithLifecycle()
    val thumbnail = rememberPanelFile(item.thumbnailFileId, observeFile)
    LaunchedEffect(item.fileId, item.thumbnailFileId, item.format, thumbnailOnly) {
        item.thumbnailFileId?.let(downloadFile)
        // WebM stickers and strip covers cannot be drawn by the picker; their still thumbnail is enough.
        if (!thumbnailOnly && item.format != StickerFileFormat.Webm) downloadFile(item.fileId)
    }
    val emoji = item.emoji.ifBlank { "\uD83D\uDE42" }
    val thumbnailPath = thumbnail.localPath.takeIf { item.thumbnailIsStatic }
    val fullPath = file.localPath
    Box(modifier.clipToBounds().then(autoplay.modifier), contentAlignment = Alignment.Center) {
        val fallback: @Composable () -> Unit = {
            if (thumbnailPath != null) LocalMediaImage(thumbnailPath, Modifier.fillMaxSize(), fit = true, maxDimension = maxDimension)
            else Text(emoji, fontSize = 24.sp, textAlign = TextAlign.Center)
        }
        when {
            fullPath != null && item.format == StickerFileFormat.Tgs ->
                SharedTgsMedia(
                    path = fullPath,
                    sizePx = maxDimension.coerceIn(64, 256),
                    playing = playing,
                    priority = effectivePriority,
                    modifier = Modifier.fillMaxSize(),
                    fallback = if (thumbnailPath != null) fallback else fallback
                )
            fullPath != null && item.format != StickerFileFormat.Webm ->
                LocalMediaImage(fullPath, Modifier.fillMaxSize(), fit = true, maxDimension = maxDimension, animate = playing)
            thumbnailPath != null ->
                LocalMediaImage(thumbnailPath, Modifier.fillMaxSize(), fit = true, maxDimension = maxDimension)
            file.isDownloading || thumbnail.isDownloading ->
                CircularProgressIndicator(Modifier.size(18.dp), color = palette.accent, strokeWidth = 2.dp)
            else -> Text(emoji, fontSize = 24.sp, textAlign = TextAlign.Center)
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun <T> PickerLoad<T>.readyValue(): T? = (this as? PickerLoad.Ready<T>)?.value

internal fun <T> PickerLoad<T>.beginRefresh(): PickerLoad<T> =
    if (this is PickerLoad.Ready) this else PickerLoad.Loading

internal fun <T> PickerLoad<T>.failRefresh(message: String): PickerLoad<T> =
    if (this is PickerLoad.Ready) this else PickerLoad.Failed(message)

@Composable
internal fun <T> PickerLoadContent(
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
internal fun PanelLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), color = beyGramPalette().accent, strokeWidth = 2.dp)
    }
}

@Composable
internal fun PanelEmpty(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = beyGramPalette().textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun PanelError(text: String, onRetry: () -> Unit) {
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
internal fun PickerMore(loading: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = beyGramPalette().accent, strokeWidth = 2.dp)
        else TextButton(onClick = onClick) { Text(stringResource(R.string.sticker_panel_load_more)) }
    }
}

@Composable
internal fun PaginationError(text: String, onRetry: () -> Unit) {
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
internal fun rememberPanelFile(fileId: Int?, observeFile: (Int) -> StateFlow<MediaFileState>): MediaFileState {
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
    savedGifs: Set<String>?,
    savingGifKeys: Set<String>,
    onSetSavedGif: (AnimationItem, Boolean) -> Unit,
    regularPackOrder: List<Long>,
    dynamicOrder: Boolean,
    onPackOrder: () -> Unit,
    reorderingStickers: Boolean,
    reorderStickers: (List<Long>) -> Unit,
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
    BeyGramSheet(onDismissRequest = onDismiss, sheetState = sheetState, scrollableContent = false) {
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
                onAttach = { onAttachAnimation(overlay.item) },
                saved = savedGifs?.contains(overlay.item.key),
                saving = overlay.item.key in savingGifKeys,
                onSetSaved = { onSetSavedGif(overlay.item, it) }
            )
            is StickerPanelOverlay.Pack -> PackPreviewContent(
                summary = overlay.summary,
                observeFile = observeFile,
                downloadFile = downloadFile,
                onOpen = { onOpenPack(overlay.summary.id) },
                dynamicOrder = dynamicOrder,
                onPackOrder = onPackOrder,
                orderButtons = {
                    if (!dynamicOrder) {
                        PickerPackOrderButtons(regularPackOrder, overlay.summary.id, reorderingStickers, reorderStickers)
                    }
                }
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
                .clip(styledShape(18.dp)).background(palette.surface2.copy(alpha = 0.45f)),
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
            listOfNotNull(format, dimensions).joinToString(" Р’В· "),
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
    onAttach: () -> Unit,
    saved: Boolean?,
    saving: Boolean,
    onSetSaved: (Boolean) -> Unit
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
            Modifier.fillMaxWidth().height(200.dp).clip(styledShape(18.dp)).background(palette.surface2),
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
            listOfNotNull(duration, dimensions).joinToString(" Р’В· "),
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
        if (saved != null) {
            TextButton(
                onClick = { onSetSaved(!saved) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(stringResource(if (saved) R.string.gif_panel_remove_saved else R.string.gif_panel_save))
            }
        }
    }
}

@Composable
private fun PackPreviewContent(
    summary: StickerSetSummary,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onOpen: () -> Unit,
    dynamicOrder: Boolean,
    onPackOrder: () -> Unit,
    orderButtons: @Composable () -> Unit
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
        if (!dynamicOrder) Row { orderButtons() }
        TextButton(
            onClick = onPackOrder,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.pack_order_settings))
        }
        Spacer(Modifier.height(12.dp))
        val cover = summary.cover
        if (cover != null) {
            StickerPanelArtwork(
                item = cover,
                modifier = Modifier.size(96.dp).clip(styledShape(20.dp)),
                observeFile = observeFile,
                downloadFile = downloadFile,
                maxDimension = 256
            )
        } else {
            Box(
                Modifier.size(96.dp).clip(styledShape(20.dp)).background(palette.accentSoft),
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
