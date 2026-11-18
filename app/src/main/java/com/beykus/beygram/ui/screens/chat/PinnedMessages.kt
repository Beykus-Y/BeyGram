package com.beykus.beygram.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.pluralStringResource
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.AvatarPhoto
import com.beykus.beygram.domain.model.ProfileMember
import com.beykus.beygram.domain.model.MessageCapabilities
import com.beykus.beygram.ui.components.ChatAvatar
import com.beykus.beygram.ui.components.TelegramAvatar
import java.text.SimpleDateFormat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageSpecialContent
import com.beykus.beygram.domain.model.PinnedMessagePage
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.BeyGramPalette
import com.beykus.beygram.ui.components.BeyGramPopover
import com.beykus.beygram.ui.components.BeyGramPopoverItem
import com.beykus.beygram.ui.components.SearchField
import com.beykus.beygram.ui.components.TextButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.navigationBarColor
import com.beykus.beygram.ui.components.navigationSpecFor
import com.beykus.beygram.ui.components.topAppBarSurface
import com.beykus.beygram.ui.design.GlassEdge
import com.beykus.beygram.ui.design.GlassPanel
import com.beykus.beygram.ui.design.DesignSystem
import com.beykus.beygram.ui.design.HairlineEdge
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.PanelFloor
import com.beykus.beygram.ui.design.TopBarVariant
import com.beykus.beygram.ui.design.panelFill
import com.beykus.beygram.ui.design.signatureChrome
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.design.usesGlassPanels
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.fadeEnter
import com.beykus.beygram.ui.motion.fadeExit
import com.beykus.beygram.ui.motion.rememberMotionSpec
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import java.text.DateFormat
import java.util.Date

/** Captured from the actual one-line preview; no guessed screen position. */
internal data class PinnedPreviewSource(val id: Long, val text: String, val bounds: Rect, val screenWidth: Int, val screenHeight: Int)

internal fun LayoutCoordinates.pinnedBoundsOnScreen(view: View): Rect? {
    if (!isAttached) return null
    val rect = boundsInWindow()
    if (rect.width <= 0f || rect.height <= 0f) return null
    val screen = IntArray(2)
    val window = IntArray(2)
    view.getLocationOnScreen(screen)
    view.getLocationInWindow(window)
    val x = (screen[0] - window[0]).toFloat()
    val y = (screen[1] - window[1]).toFloat()
    return Rect(rect.left + x, rect.top + y, rect.right + x, rect.bottom + y)
}

private fun Rect.containsBounds(other: Rect): Boolean =
    other.left >= left && other.right <= right && other.top >= top && other.bottom <= bottom

/** Which pin is relevant to the message window currently visible in chat history. */
internal fun currentPinnedIndex(pins: List<ChatMessage>, visibleOldestMessageId: Long?): Int {
    if (pins.isEmpty()) return -1
    if (visibleOldestMessageId == null || visibleOldestMessageId <= 0L) return 0
    // Telegram's message IDs grow with time. Prefer the closest pinned message at/before the viewport.
    return pins.indexOfFirst { it.id <= visibleOldestMessageId }.takeIf { it >= 0 } ?: pins.lastIndex
}

/**
 * True when the viewport is older than every loaded pin, so the pin it needs is on a page that is not loaded yet.
 * Pins are listed newest first, so the next page holds older pins only.
 */
internal fun needsOlderPinnedPage(pins: List<ChatMessage>, visibleOldestMessageId: Long?): Boolean =
    pins.isNotEmpty() && visibleOldestMessageId != null && visibleOldestMessageId > 0L && pins.none { it.id <= visibleOldestMessageId }

/**
 * The pinned messages of one chat, newest first. The holder outlives the bar that draws it: the keyboard, the search
 * and the selection mode hide the row for a while, and a hidden bar must not lose its loaded pages or ask for them
 * again when it comes back. The chat screen owns one holder per chat and topic; the pinned list screen reads the same
 * pages and pages further on its own.
 */
@Stable
internal class PinnedMessagesState(
    val chatId: Long,
    val forumTopicId: Long,
    /** One page of pins, newest first, from the server: [fromMessageId] 0 asks for the newest page. */
    private val loadPage: suspend (fromMessageId: Long) -> Result<PinnedMessagePage>
) {
    /** Newest first, as the server pages them; duplicates from overlapping pages are dropped. */
    var pins by mutableStateOf<List<ChatMessage>>(emptyList()); private set
    var total by mutableIntStateOf(0); private set
    /** The next older page's cursor, or 0 when every page is loaded. */
    var nextId by mutableLongStateOf(0L); private set
    var loading by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    /** Bumped by the retry affordance so the owner re-runs [refresh] with the same inputs. */
    var retryTick by mutableIntStateOf(0); private set
    /** The last (version, connection) pair this list was loaded for; an unchanged pair never re-requests. */
    private var loadedKey: Any? = null
    /** A page requested before the list was reloaded must not be added to the new list. */
    private var generation = 0

    /** The counter shown to the user: TDLib's total, never smaller than what is actually loaded. */
    val count: Int get() = total.coerceAtLeast(pins.size)

    /** Asks for the initial page again after a failure; the owner's effect picks the tick up. */
    fun requestRetry() {
        retryTick++
        loadedKey = null
    }

    /**
     * Loads the first page when the stored pins are stale: the first show, a pin added or removed elsewhere, the
     * connection coming back, or an explicit retry. A request answered by a newer generation is dropped, so a slow
     * answer from another chat or an older reload never reaches this list.
     */
    suspend fun refresh(version: Long, connected: Boolean) {
        val key = listOf(version, connected)
        if (key == loadedKey) return
        loadedKey = key
        val requestedGeneration = ++generation
        loading = true
        failed = false
        try {
            loadPage(0L)
                .onSuccess { page ->
                    if (generation == requestedGeneration) {
                        pins = page.messages.distinctBy(ChatMessage::id).sortedByDescending(ChatMessage::id)
                        total = page.totalCount
                        nextId = page.nextFromMessageId
                    }
                }
                .onFailure { if (generation == requestedGeneration) failed = true }
        } finally {
            if (generation == requestedGeneration) loading = false
        }
    }

    /**
     * Adds the next, older page. A call while another page loads, or with no page left, does nothing, so a retry of
     * the initial request can never turn into a useless loadMore. A failure keeps the loaded pages and only marks the
     * paging as failed, so the bar keeps showing its last valid preview.
     */
    suspend fun loadOlder() {
        if (loading || nextId == 0L) return
        val requestedId = nextId
        val requestedGeneration = generation
        loading = true
        failed = false
        try {
            loadPage(requestedId)
                .onSuccess { page ->
                    if (generation == requestedGeneration) {
                        pins = (pins + page.messages).distinctBy(ChatMessage::id).sortedByDescending(ChatMessage::id)
                        total = page.totalCount
                        nextId = if (page.nextFromMessageId == requestedId) 0L else page.nextFromMessageId
                    }
                }
                .onFailure { if (generation == requestedGeneration) failed = true }
        } finally {
            if (generation == requestedGeneration) loading = false
        }
    }
}

/** Air between the header pane and the floating pinned card, and between the card and the messages below it. */
private val PinnedCardTopGap = 8.dp
private val PinnedCardBottomGap = 4.dp
/** Visual height of the one preview line; the whole row is the touch target, so it never shrinks below 48 dp. */
private val PinnedBarMinHeight = 48.dp
private val PinnedPreviewSize = 32.dp

/**
 * The pinned bar: one line that links to the message the reader is looking at, never a "next pin" button. Tapping the
 * preview jumps to that message; the pin on the right opens the list of every pinned message, even when there is only
 * one. With several pins a thin segment indicator on the left and a position counter mark the shown one.
 *
 * The bar follows the resolved panels placement of the design system: attached it shares the header's fill and closes
 * the group with a single hairline; floating it is its own rounded surface over the conversation, using the same
 * margins and corner family as the bottom dock of the style.
 */
@Composable
internal fun PinnedMessagesBar(
    state: PinnedMessagesState,
    conversation: ConversationRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    visibleOldestMessageId: Long? = null,
    onOpen: (ChatMessage) -> Unit,
    onOpenList: () -> Unit,
    onOpenListFromPreview: ((PinnedPreviewSource) -> Unit)? = null,
    hiddenPreviewId: Long? = null,
    onPreviewMeasured: (PinnedPreviewSource?) -> Unit = {},
    onHideBar: () -> Unit,
    onUnpin: (ChatMessage) -> Unit,
    /** Reports whether a row is drawn, so the header can give its bottom hairline to this row. */
    onRowShownChange: (Boolean) -> Unit = {}
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val scope = rememberCoroutineScope()
    val floating = design.navigationFloating

    val needsOlder = needsOlderPinnedPage(state.pins, visibleOldestMessageId)
    val index = currentPinnedIndex(state.pins, visibleOldestMessageId)
    val shown = state.pins.getOrNull(index)
    val initialFailure = state.failed && state.pins.isEmpty()
    val rowShown = shown != null || initialFailure
    LaunchedEffect(rowShown) { onRowShownChange(rowShown) }
    DisposableEffect(Unit) { onDispose { onRowShownChange(false) } }

    // The shown pin follows the viewport, so an older viewport loads the page that holds its pin. A failed page is
    // retried by hand only; the row keeps its last valid preview in the meantime. loadOlder flips `loading` itself, so
    // the page runs on the scope: a relaunch of this effect would cancel it. A refresh or a page that ends reruns the
    // effect, so a viewport that still needs an older page loads it then.
    LaunchedEffect(needsOlder, state.nextId, state.failed, state.loading) {
        if (needsOlder && !state.failed && !state.loading) scope.launch { state.loadOlder() }
    }

    val enterSpec = rememberMotionSpec(MotionToken.BannerChange)
    val exitSpec = rememberMotionSpec(MotionToken.BannerChange)
    val expand = enterSpec.animationSpec<IntSize>()
    // The row grows and fades in when pins appear and folds away when they do not, following the motion settings
    // and reduced motion; an instant spec shows and hides it without a single alpha-0 frame.
    AnimatedVisibility(
        visible = rowShown,
        enter = if (enterSpec.isInstant) EnterTransition.None else enterSpec.fadeEnter() + expandVertically(expand),
        exit = if (exitSpec.isInstant) ExitTransition.None else exitSpec.fadeExit() + shrinkVertically(expand)
    ) {
        var menuOpen by remember { mutableStateOf(false) }
        // Unpinning follows the server's rights: a member without pin management cannot unpin either, so the action
        // waits for the capabilities answer and stays disabled until it arrives.
        var unpinAllowed by remember(state.chatId, shown?.id) { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(menuOpen, shown?.id) {
            val target = shown ?: return@LaunchedEffect
            if (!menuOpen) return@LaunchedEffect
            unpinAllowed = conversation.messageCapabilities(state.chatId, target.id).getOrNull()?.canPin
        }
        val pinTint = if (design.components.topBar == TopBarVariant.Signature) palette.textPrimary else palette.accent
        val rowFill = Modifier.fillMaxWidth()
            .let { base ->
                when {
                    floating -> base
                    // Signature: the bar shares the header's canvas and closes the group with its own hairline.
                    design.components.topBar == TopBarVariant.Signature ->
                        base.signatureChrome(palette.screen, palette.divider, HairlineEdge.Bottom)
                    // Liquid Glass: the preview takes the header's translucent fill, so the two read as one block with no seam.
                    design.components.topBar == TopBarVariant.Glass ->
                        base.chatChromeBackground(design.surfaces.panelFill(palette.surface, PanelFloor.Bar))
                    else -> base.chatChromeBackground(palette.surface)
                }
            }

        if (initialFailure) {
            val retryRow: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth().heightIn(min = PinnedBarMinHeight).then(rowFill), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.chat_pinned_failed),
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                    )
                    TextButton(onClick = state::requestRetry) { Text(stringResource(R.string.chat_pinned_retry)) }
                }
            }
            if (floating) FloatingPinnedCard(design, palette) { retryRow() } else retryRow()
            return@AnimatedVisibility
        }

        val target = shown ?: return@AnimatedVisibility
        val pinTotal = state.count
        val previewText = target.summary()
        val view = LocalView.current
        var previewBounds by remember(target.id) { mutableStateOf<Rect?>(null) }
        val latestPreviewMeasured by androidx.compose.runtime.rememberUpdatedState(onPreviewMeasured)
        DisposableEffect(target.id, previewText) {
            onDispose { latestPreviewMeasured(null) }
        }
        val positionDescription = stringResource(R.string.chat_pinned_position, index + 1, pinTotal)
        val pagingFailed = needsOlder && state.failed

        val row: @Composable () -> Unit = {
            Row(
                Modifier.fillMaxWidth().heightIn(min = PinnedBarMinHeight).then(rowFill),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "$positionDescription, $previewText" }
                        .combinedClickable(
                            role = Role.Button,
                            onClick = { onOpen(target) },
                            onLongClick = { menuOpen = true }
                        )
                        .padding(start = if (floating) 6.dp else 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pinTotal > 1) {
                        // Indicator: at most 4 segments, newest pin at the bottom. Beyond 4 pins, the shown pin
                        // picks a slot by its place in the list.
                        val segments = minOf(pinTotal, 4)
                        val slot = if (pinTotal > 4) (index * 4 / pinTotal).coerceIn(0, 3) else index
                        PinnedSegmentIndicator(
                            segments = segments,
                            highlighted = segments - 1 - slot,
                            tint = pinTint,
                            modifier = Modifier.size(width = 3.dp, height = 22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    PinnedMediaPreview(target, observeFile, size = PinnedPreviewSize)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        previewText,
                        color = palette.textPrimary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                            .onGloballyPositioned { coordinates ->
                                val bounds = coordinates.pinnedBoundsOnScreen(view)
                                previewBounds = bounds
                                val metrics = view.resources.displayMetrics
                                latestPreviewMeasured(bounds?.let { PinnedPreviewSource(target.id, previewText, it, metrics.widthPixels, metrics.heightPixels) })
                            }
                            .graphicsLayer { alpha = if (hiddenPreviewId == target.id) 0f else 1f }
                    )
                    if (pinTotal > 1) {
                        if (state.loading && !pagingFailed) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = palette.accent)
                        } else {
                            Text(
                                "${index + 1}/$pinTotal",
                                color = palette.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                }
                if (pagingFailed) {
                    // The viewport needs a pin on a page that failed to load: the preview stays, and the retry is a
                    // quiet icon instead of a row that would push the messages around.
                    IconButton(onClick = { scope.launch { state.loadOlder() } }) {
                        Icon(Icons.Rounded.Refresh, stringResource(R.string.chat_pinned_retry), tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = {
                    val bounds = previewBounds
                    val openFromPreview = onOpenListFromPreview
                    if (bounds != null && openFromPreview != null) {
                        val metrics = view.resources.displayMetrics
                        openFromPreview(PinnedPreviewSource(target.id, previewText, bounds, metrics.widthPixels, metrics.heightPixels))
                    } else onOpenList()
                }) {
                    Icon(Icons.Rounded.PushPin, stringResource(R.string.chat_pinned_list), tint = pinTint, modifier = Modifier.size(20.dp))
                }
            }
        }

        if (floating) {
            FloatingPinnedCard(design, palette) {
                Box {
                    row()
                    PinnedBarActions(
                        expanded = menuOpen,
                        unpinAllowed = unpinAllowed,
                        onDismiss = { menuOpen = false },
                        onShowAll = onOpenList,
                        onHideBar = onHideBar,
                        onUnpin = { onUnpin(target) }
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                Box {
                    row()
                    PinnedBarActions(
                        expanded = menuOpen,
                        unpinAllowed = unpinAllowed,
                        onDismiss = { menuOpen = false },
                        onShowAll = onOpenList,
                        onHideBar = onHideBar,
                        onUnpin = { onUnpin(target) }
                    )
                }
                // The group's single hairline: Signature draws its own in signatureChrome, Liquid Glass closes on the
                // pane rim, the flat styles draw exactly one line here.
                if (design.components.topBar != TopBarVariant.Signature && design.components.topBar != TopBarVariant.Glass) {
                    HorizontalDivider(color = palette.divider)
                }
            }
        }
    }
}

/** The long-press actions of the preview line: every pin, a local hide, and an unpin that respects the server rights. */
@Composable
private fun PinnedBarActions(
    expanded: Boolean,
    unpinAllowed: Boolean?,
    onDismiss: () -> Unit,
    onShowAll: () -> Unit,
    onHideBar: () -> Unit,
    onUnpin: () -> Unit
) {
    BeyGramPopover(expanded = expanded, onDismissRequest = onDismiss) {
        BeyGramPopoverItem(
            text = stringResource(R.string.chat_pinned_list),
            leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null, tint = beyGramPalette().textSecondary) },
            onClick = { onDismiss(); onShowAll() }
        )
        BeyGramPopoverItem(
            text = stringResource(R.string.chat_pinned_hide_panel),
            onClick = { onDismiss(); onHideBar() }
        )
        when (unpinAllowed) {
            // Without the right the item disappears: a disabled promise of an unpin reads as a broken action.
            false -> Unit
            // Unknown rights keep the item visible but inert until the answer arrives.
            else -> BeyGramPopoverItem(
                text = stringResource(R.string.chat_pinned_unpin),
                enabled = unpinAllowed == true,
                danger = true,
                onClick = { onDismiss(); onUnpin() }
            )
        }
    }
}

/**
 * The floating placement: the bar is a surface of its own over the conversation, not a strip of the header. It keeps
 * the margins, the corner family and the shadow of the style's bottom dock, and in Liquid Glass it samples the
 * messages scrolling behind it. The card never passes a tap through to the list, while the air around it stays live.
 */
@Composable
private fun FloatingPinnedCard(
    design: DesignSystem,
    palette: BeyGramPalette,
    content: @Composable () -> Unit
) {
    val spec = remember(design) { navigationSpecFor(design) }
    val glass = design.surfaces.glass
    val shape = RoundedCornerShape(spec.dockRadius)
    val fill = navigationBarColor(design, palette.surface, palette.screen)
    val swallowTaps = Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
    Column(
        Modifier.fillMaxWidth().padding(
            start = spec.horizontalMargin,
            end = spec.horizontalMargin,
            top = PinnedCardTopGap,
            bottom = PinnedCardBottomGap
        )
    ) {
        when {
            design.usesGlassPanels && glass != null -> GlassPanel(
                design = design,
                tint = fill,
                rim = palette.textSecondary,
                backdropBase = palette.screen,
                shape = shape,
                edge = GlassEdge.Rim,
                modifier = Modifier.fillMaxWidth().shadow(glass.shadowElevation, shape).then(swallowTaps),
                content = { Box(Modifier.fillMaxWidth()) { content() } }
            )
            else -> Box(
                Modifier.fillMaxWidth().shadow(spec.elevation, shape).clip(shape).background(fill)
                    .then(if (spec.hairline) Modifier.border(1.dp, palette.divider, shape) else Modifier)
                    .then(swallowTaps)
            ) { content() }
        }
    }
}

/** The thin multi-segment indicator: the highlighted segment marks the shown pin, newest pin at the bottom. */
@Composable
private fun PinnedSegmentIndicator(segments: Int, highlighted: Int, tint: Color, modifier: Modifier) {
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val segmentHeight = (size.height - gap * (segments - 1)) / segments
        repeat(segments) { i ->
            drawRoundRect(
                color = if (i == highlighted) tint else tint.copy(alpha = 0.3f),
                topLeft = Offset(0f, i * (segmentHeight + gap)),
                size = Size(size.width, segmentHeight),
                cornerRadius = CornerRadius(size.width / 2f)
            )
        }
    }
}

/** What the leading slot of a pinned line draws. [None] leaves the text alone, as Telegram does for plain text pins. */
private sealed interface PinnedPreviewVisual {
    /** A cached thumbnail: the id is only read, never downloaded; the minithumbnail is the blurred fallback. */
    data class Photo(val fileId: Int?, val minithumbnail: ByteArray?, val fallbackIcon: ImageVector) : PinnedPreviewVisual
    data class Sticker(val emoji: String) : PinnedPreviewVisual
    data class Icon(val icon: ImageVector) : PinnedPreviewVisual
    data object None : PinnedPreviewVisual
}

/**
 * Chooses the leading visual of a pinned line. Hidden, one-time and protected media keep their type icon: a spoiler,
 * secret-chat media, a view-once photo and an unpurchased paid bundle are never revealed from the pin.
 */
@Composable
private fun pinnedPreviewVisual(message: ChatMessage): PinnedPreviewVisual {
    val media = message.media
    val revealMedia = media != null && !message.isViewOnce && !media.hasSpoiler && !media.isSecret && !media.isSecretChat
    when (val content = message.special) {
        is MessageSpecialContent.Poll -> return PinnedPreviewVisual.Icon(Icons.Rounded.Poll)
        is MessageSpecialContent.Location -> return PinnedPreviewVisual.Icon(Icons.Rounded.LocationOn)
        is MessageSpecialContent.LiveLocation -> return PinnedPreviewVisual.Icon(Icons.Rounded.MyLocation)
        is MessageSpecialContent.Venue -> return PinnedPreviewVisual.Icon(Icons.Rounded.Map)
        is MessageSpecialContent.Contact -> return PinnedPreviewVisual.Icon(Icons.Rounded.Person)
        is MessageSpecialContent.Dice -> return PinnedPreviewVisual.Icon(Icons.Rounded.Casino)
        is MessageSpecialContent.PaidMedia ->
            // A purchased bundle shows as media; a locked one only says what it costs.
            return if (content.media.isPurchased) PinnedPreviewVisual.Icon(Icons.Rounded.Image)
            else PinnedPreviewVisual.Icon(Icons.Rounded.Star)
        is MessageSpecialContent.Invoice -> return PinnedPreviewVisual.Icon(Icons.Rounded.Description)
        is MessageSpecialContent.Unsupported, null -> Unit
    }
    if (media == null || message.expiredMediaKind != null) return PinnedPreviewVisual.None
    return when (media.kind) {
        MediaKind.Photo -> PinnedPreviewVisual.Photo(
            fileId = (media.thumbnailFileId ?: media.fileId).takeIf { revealMedia },
            minithumbnail = media.minithumbnail?.takeIf { revealMedia },
            fallbackIcon = Icons.Rounded.Image
        )
        MediaKind.Video -> PinnedPreviewVisual.Photo(
            fileId = media.thumbnailFileId?.takeIf { revealMedia },
            minithumbnail = media.minithumbnail?.takeIf { revealMedia },
            fallbackIcon = Icons.Rounded.Videocam
        )
        MediaKind.Animation -> PinnedPreviewVisual.Photo(
            fileId = media.thumbnailFileId?.takeIf { revealMedia },
            minithumbnail = media.minithumbnail?.takeIf { revealMedia },
            fallbackIcon = Icons.Rounded.Movie
        )
        MediaKind.Sticker ->
            if (revealMedia && media.stickerEmoji.isNotBlank()) PinnedPreviewVisual.Sticker(media.stickerEmoji)
            else PinnedPreviewVisual.Icon(Icons.Rounded.Mood)
        MediaKind.Audio -> PinnedPreviewVisual.Icon(Icons.Rounded.MusicNote)
        MediaKind.Voice -> PinnedPreviewVisual.Icon(Icons.Rounded.Mic)
        MediaKind.VideoNote -> PinnedPreviewVisual.Icon(Icons.Rounded.Videocam)
        MediaKind.Document -> PinnedPreviewVisual.Icon(Icons.Rounded.InsertDriveFile)
    }
}

/**
 * The small static leading visual of a pinned line. Local cached previews only: nothing is downloaded and no spoiler,
 * secret or one-time content is ever revealed here.
 */
@Composable
private fun PinnedMediaPreview(message: ChatMessage, observeFile: (Int) -> StateFlow<MediaFileState>, size: Dp) {
    val visual = pinnedPreviewVisual(message)
    val palette = beyGramPalette()
    when (visual) {
        is PinnedPreviewVisual.None -> Unit
        is PinnedPreviewVisual.Sticker -> {
            Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                Text(visual.emoji, fontSize = (size.value * 0.55f).sp)
            }
        }
        is PinnedPreviewVisual.Icon -> Box(
            Modifier.size(size).clip(styledShape(8.dp)).background(palette.surface2.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(visual.icon, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(size / 2))
        }
        is PinnedPreviewVisual.Photo -> {
            val fileId = visual.fileId
            val path = if (fileId != null) {
                val file by remember(fileId, observeFile) { observeFile(fileId) }.collectAsStateWithLifecycle()
                file.localPath
            } else null
            val tile = Modifier.size(size).clip(styledShape(8.dp))
            when {
                path != null -> LocalMediaImage(path, tile, maxDimension = 160, animate = false)
                visual.minithumbnail != null -> MinithumbnailImage(visual.minithumbnail, tile)
                else -> Box(tile.background(palette.surface2.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Icon(visual.fallbackIcon, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(size / 2))
                }
            }
        }
    }
}

/**
 * The pinned messages screen: a title with the count, a server-side search that covers every page, a lazy list with
 * automatic pagination, and per-message actions that respect the chat's rights. It replaces the old dialog, so a long
 * list of pins is no longer a scroll inside a small box. A locally hidden bar can be restored from here.
 */
@Composable
internal fun PinnedMessagesScreen(
    state: PinnedMessagesState,
    conversation: ConversationRepository,
    chat: ChatPreview,
    chatTitle: String,
    members: List<ProfileMember>,
    senderChats: List<ChatPreview>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    barHidden: Boolean,
    previewSource: PinnedPreviewSource? = null,
    livePreviewSource: () -> PinnedPreviewSource? = { null },
    headerSource: Rect? = null,
    liveHeaderSource: () -> Rect? = { null },
    onRestoreBar: () -> Unit,
    onOpenMessage: (ChatMessage) -> Unit,
    onClose: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chatKey = listOf(state.chatId, state.forumTopicId)
    var query by rememberSaveable(chatKey) { mutableStateOf("") }
    var searchOpen by rememberSaveable(chatKey) { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val membersById = remember(members) { members.associateBy { it.userId } }
    val directChatsById = remember(senderChats) {
        senderChats.filter { !it.isGroup && !it.isSecret && !it.isSavedMessages && !it.isChannel }.associateBy { it.id }
    }
    fun closeSearch() {
        searchOpen = false
        query = ""
    }
    val searchEnter = rememberMotionSpec(MotionToken.BannerChange)
    val searchExit = rememberMotionSpec(MotionToken.BannerChange)
    var searchResults by remember(chatKey) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var searchNextId by remember(chatKey) { mutableLongStateOf(0L) }
    var searchBusy by remember(chatKey) { mutableStateOf(false) }
    var searchFailed by remember(chatKey) { mutableStateOf(false) }
    var searchAttempt by remember(chatKey) { mutableIntStateOf(0) }
    // A request that a newer query superseded must not reach the list, and neither may a page of the previous query.
    var searchGeneration by remember(chatKey) { mutableIntStateOf(0) }
    var rowMenuFor by remember(chatKey) { mutableStateOf<ChatMessage?>(null) }
    var capabilities by remember(chatKey) { mutableStateOf<MessageCapabilities?>(null) }
    var unpinError by remember(chatKey) { mutableStateOf<String?>(null) }
    val version by remember(conversation, state.chatId) { conversation.observePinnedMessagesVersion(state.chatId) }.collectAsStateWithLifecycle()

    val searching = query.isNotBlank()
    val shown = if (searching) searchResults else state.pins
    val locale = context.resources.configuration.locales[0]
    val monthLabels = remember(shown, locale) {
        val formatter = SimpleDateFormat("LLLL yyyy", locale)
        shown.map { if (it.dateSeconds > 0L) formatter.format(Date(it.dateSeconds * 1000L)) else "" }
    }

    // The search is server-side inside the pinned filter, so it covers every page of pins, not only the loaded ones.
    suspend fun loadSearchPage(fromMessageId: Long, replace: Boolean) {
        if (searchBusy) return
        val requestedGeneration = searchGeneration
        searchBusy = true
        searchFailed = false
        try {
            conversation.pinnedMessages(state.chatId, fromMessageId, state.forumTopicId, query.trim())
                .onSuccess { page ->
                    if (requestedGeneration == searchGeneration) {
                        searchResults = (if (replace) emptyList() else searchResults)
                            .plus(page.messages).distinctBy(ChatMessage::id).sortedByDescending(ChatMessage::id)
                        searchNextId = if (page.nextFromMessageId == fromMessageId) 0L else page.nextFromMessageId
                    }
                }
                .onFailure { if (requestedGeneration == searchGeneration) searchFailed = true }
        } finally {
            if (requestedGeneration == searchGeneration) searchBusy = false
        }
    }

    // Debounced: typing cancels the previous request by relaunching the effect, and the generation guard drops any
    // answer that still comes back from the superseded query.
    LaunchedEffect(query, searchAttempt, state.chatId, state.forumTopicId) {
        val requestedGeneration = ++searchGeneration
        // A request of the superseded generation may still be in flight. Its answer is dropped and its finally leaves the
        // flags alone, so this generation starts with them cleared: a busy flag left over would turn the new search away.
        searchBusy = false
        searchNextId = 0L
        if (query.isBlank()) {
            if (requestedGeneration == searchGeneration) {
                searchResults = emptyList(); searchFailed = false
            }
            return@LaunchedEffect
        }
        delay(300)
        if (requestedGeneration != searchGeneration) return@LaunchedEffect
        loadSearchPage(0L, replace = true)
    }

    // The paged list follows the bar's holder: a pin added or removed anywhere (this screen included) bumps the
    // version, and the chat screen's own effect reloads the holder for both of us. Only the search needs a rerun here,
    // so the still-open query drops a pin that was just unpinned.
    LaunchedEffect(version) {
        if (query.isNotBlank() && searchResults.isNotEmpty()) loadSearchPage(0L, replace = true)
    }

    val initialIndex = remember(previewSource, chatKey) { state.pins.indexOfFirst { it.id == previewSource?.id }.coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val resolvedAvatars = remember(chatKey) { mutableStateMapOf<Long, AvatarPhoto?>() }
    // Resolve only senders actually visible in the pinned list, not the whole chat or every loaded history page.
    val visibleSenderIds by remember(listState, shown) {
        derivedStateOf {
            val byId = shown.associateBy { it.id }
            listState.layoutInfo.visibleItemsInfo.mapNotNull { row ->
                val messageId = row.key as? Long ?: return@mapNotNull null
                byId[messageId]?.senderId?.takeIf { it > 0L }
            }.distinct()
        }
    }
    LaunchedEffect(visibleSenderIds, conversation, chatKey) {
        val missing = visibleSenderIds.filterNot { resolvedAvatars.containsKey(it) }
        if (missing.isNotEmpty()) {
            val photos = conversation.loadPinnedSenderAvatars(missing)
            missing.forEach { id -> resolvedAvatars[id] = photos[id] }
        }
    }
    // Automatic pagination: reaching the end of the loaded pages asks for the next one, whichever list is shown.
    val nearListEnd by remember(listState, shown.size) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            shown.isNotEmpty() && last >= shown.lastIndex - 6
        }
    }
    // The holder's pages run on the scope, as the bar's do (see PinnedMessagesBar): a page asked for during a refresh
    // loads once the refresh ends, because the effect reruns on `loading`. A failed page waits for the retry instead.
    LaunchedEffect(nearListEnd, state.nextId, state.loading, state.failed, searching) {
        if (nearListEnd && !searching && !state.loading && !state.failed) scope.launch { state.loadOlder() }
    }
    LaunchedEffect(nearListEnd, searchNextId, searching) {
        // A spent cursor must not ask for the same page again: with no page left the list simply ends.
        if (nearListEnd && searching && searchNextId != 0L) loadSearchPage(searchNextId, replace = false)
    }

    val entryMotion = rememberMotionSpec(MotionToken.NavigationPush)
    val exitMotion = rememberMotionSpec(MotionToken.NavigationPop)
    val flightSource = previewSource?.takeIf { !entryMotion.isInstant && it.text.isNotBlank() }
    val progress = remember(flightSource) { Animatable(if (flightSource == null) 1f else 0f) }
    var flightActive by remember(flightSource) { mutableStateOf(flightSource != null) }
    var closing by remember(flightSource) { mutableStateOf(false) }
    var rootBounds by remember(flightSource) { mutableStateOf<Rect?>(null) }
    var targetBounds by remember(flightSource) { mutableStateOf<Rect?>(null) }
    var targetHeaderBounds by remember(flightSource) { mutableStateOf<Rect?>(null) }
    val validDisplay = flightSource?.let {
        val metrics = context.resources.displayMetrics
        it.screenWidth == metrics.widthPixels && it.screenHeight == metrics.heightPixels
    } == true
    val flightMessageText = shown.firstOrNull { it.id == flightSource?.id }?.let { it.summary() }
    fun canReturnToPreview(): Boolean {
        val source = flightSource ?: return false
        val root = rootBounds ?: return false
        val target = targetBounds ?: return false
        return livePreviewSource() == source && validDisplay && !searchOpen && !exitMotion.isInstant &&
            root.containsBounds(source.bounds) && root.containsBounds(target) &&
            listState.layoutInfo.visibleItemsInfo.any { it.key == source.id } &&
            flightMessageText == source.text
    }
    fun closeWithFlight(action: () -> Unit, messageId: Long? = null) {
        if (closing) return
        if (flightActive || !canReturnToPreview() || (messageId != null && messageId != flightSource?.id)) {
            action()
            return
        }
        closing = true
        flightActive = true
        scope.launch {
            progress.animateTo(0f, exitMotion.animationSpec())
            action()
        }
    }
    LaunchedEffect(flightSource, validDisplay, entryMotion.isInstant) {
        if (flightSource == null || !validDisplay || entryMotion.isInstant) {
            progress.snapTo(1f)
            flightActive = false
            return@LaunchedEffect
        }
        val validBounds = withTimeoutOrNull(250) {
            snapshotFlow {
                val root = rootBounds
                val target = targetBounds
                if (root != null && target != null) root.containsBounds(flightSource.bounds) && root.containsBounds(target)
                else null
            }.filterNotNull().first()
        }
        if (validBounds == true && livePreviewSource() == flightSource && flightMessageText == flightSource.text) progress.animateTo(1f, entryMotion.animationSpec())
        else progress.snapTo(1f)
        flightActive = false
    }

    fun unpin(target: ChatMessage) {
        scope.launch {
            unpinError = null
            conversation.setMessagePinned(state.chatId, target.id, false)
                .onFailure { unpinError = it.userMessage(context, R.string.err_chat_pin_failed) }
        }
    }

    Dialog(
        onDismissRequest = { if (searchOpen) closeSearch() else closeWithFlight(onClose) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        )
    ) {
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        LaunchedEffect(searchOpen) {
            if (!searchOpen) {
                focus.clearFocus()
                keyboard?.hide()
            }
        }
        val view = LocalView.current
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        DisposableEffect(dialogWindow, flightSource) {
            val previous = dialogWindow?.attributes?.dimAmount
            if (flightSource != null) dialogWindow?.setDimAmount(0f)
            onDispose { if (previous != null) dialogWindow?.setDimAmount(previous) }
        }
        Box(Modifier.fillMaxSize().onGloballyPositioned { rootBounds = it.pinnedBoundsOnScreen(view) }) {
        Column(
            Modifier.fillMaxSize().graphicsLayer { alpha = if (flightActive) progress.value else 1f }
                .background(palette.screen)
                .windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
        ) {
            Box(Modifier.fillMaxWidth().onGloballyPositioned { targetHeaderBounds = it.pinnedBoundsOnScreen(view) }) {
            Row(
                Modifier.fillMaxWidth().graphicsLayer {
                    val source = headerSource
                    val destination = targetHeaderBounds
                    val root = rootBounds
                    if (flightActive && validDisplay && source != null && destination != null && root != null &&
                        liveHeaderSource() == source && root.containsBounds(source) && root.containsBounds(destination)) {
                        val remaining = 1f - progress.value.coerceIn(0f, 1f)
                        translationX = (source.center.x - destination.center.x) * remaining
                        translationY = (source.center.y - destination.center.y) * remaining
                        scaleX = 1f + (source.width / destination.width - 1f) * remaining
                        scaleY = 1f + (source.height / destination.height - 1f) * remaining
                    } else {
                        translationX = 0f; translationY = 0f; scaleX = 1f; scaleY = 1f
                    }
                }.topAppBarSurface().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (searchOpen) closeSearch() else closeWithFlight(onClose) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), tint = palette.textPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.chat_pinned_short_title),
                        color = palette.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        pluralStringResource(R.plurals.chat_pinned_message_count, state.count, state.count) + " · " + chatTitle,
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Hiding the bar is local, so the way back in lives right here.
                if (barHidden) IconButton(onClick = onRestoreBar) {
                    Icon(Icons.Rounded.PushPin, stringResource(R.string.chat_pinned_show_bar), tint = palette.textSecondary)
                }
                IconButton(onClick = { if (searchOpen) closeSearch() else searchOpen = true }) {
                    Icon(
                        if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                        stringResource(if (searchOpen) R.string.common_close else R.string.chat_pinned_search_hint),
                        tint = palette.textSecondary
                    )
                }
            }
            }
            AnimatedVisibility(
                visible = searchOpen,
                enter = if (searchEnter.isInstant) EnterTransition.None else searchEnter.fadeEnter() + expandVertically(expandFrom = Alignment.Top),
                exit = if (searchExit.isInstant) ExitTransition.None else searchExit.fadeExit() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    SearchField(query, { query = it }, stringResource(R.string.chat_pinned_search_hint), focusRequester = searchFocus)
                    LaunchedEffect(searchOpen) {
                        if (searchOpen) {
                            searchFocus.requestFocus()
                            keyboard?.show()
                        }
                    }
                }
            }
            unpinError?.let {
                Text(
                    it,
                    color = palette.danger,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).errorAnnouncement()
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                // The list busy/failed flags are the search's while searching, the holder's otherwise.
                val listBusy = if (searching) searchBusy else state.loading
                val listFailed = if (searching) searchFailed else state.failed
                when {
                    shown.isEmpty() && listBusy -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = palette.accent, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
                    }
                    shown.isEmpty() && listFailed -> Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        Text(
                            stringResource(R.string.chat_pinned_failed),
                            color = palette.textSecondary,
                            fontSize = 14.sp
                        )
                        TextButton(onClick = {
                            // A failed search restarts the query; a failed load asks for the older page again.
                            if (searching) searchAttempt++ else state.requestRetry()
                        }) { Text(stringResource(R.string.chat_pinned_retry)) }
                    }
                    shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(if (searching) R.string.chat_pinned_search_empty else R.string.chat_pinned_empty),
                            color = palette.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        itemsIndexed(shown, key = { _, message -> message.id }) { index, message ->
                            val month = monthLabels[index]
                            if (month.isNotEmpty() && (index == 0 || monthLabels[index - 1] != month)) {
                                Text(month, color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                            }
                            PinnedMessageRow(
                                message = message,
                                chat = chat,
                                member = membersById[message.senderId],
                                directChat = directChatsById[message.senderId],
                                resolvedPhoto = resolvedAvatars[message.senderId],
                                observeFile = observeFile,
                                downloadFile = downloadFile,
                                menuExpanded = rowMenuFor?.id == message.id,
                                capabilities = if (rowMenuFor?.id == message.id) capabilities else null,
                                onMenuDismiss = { if (rowMenuFor?.id == message.id) rowMenuFor = null },
                                previewTextModifier = if (message.id == flightSource?.id) Modifier
                                    .onGloballyPositioned { targetBounds = it.pinnedBoundsOnScreen(view) }
                                    .graphicsLayer { alpha = if (flightActive) 0f else 1f } else Modifier,
                                onClick = { closeWithFlight({ onOpenMessage(message) }, message.id) },
                                onLongPress = { rowMenuFor = message; capabilities = null },
                                onJump = { closeWithFlight({ onOpenMessage(message) }, message.id) },
                                onUnpin = { unpin(message) }
                            )
                            if (index < shown.lastIndex && monthLabels[index + 1] == month) {
                                HorizontalDivider(Modifier.padding(start = 58.dp, end = 12.dp), color = palette.divider)
                            }
                        }
                        item(key = "pinned-list-footer") {
                            when {
                                listBusy -> Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = palette.accent)
                                }
                                listFailed -> Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(R.string.chat_pinned_failed),
                                        color = palette.textSecondary,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f).padding(start = 20.dp)
                                    )
                                    TextButton(onClick = {
                                        if (searching) searchAttempt++
                                        // A spent cursor means the failure was the refresh itself, not a page.
                                        else if (state.nextId != 0L) scope.launch { state.loadOlder() }
                                        else state.requestRetry()
                                    }) { Text(stringResource(R.string.chat_pinned_retry)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (flightActive && validDisplay && flightSource != null) {
            val root = rootBounds
            val target = targetBounds
            if (root != null && root.containsBounds(flightSource.bounds)) {
                val density = LocalDensity.current
                val start = flightSource.bounds
                // Animate a separate text copy between the measured source and destination.
                // If the destination is not laid out yet, keep it at the real source.
                val end = target?.takeIf { root.containsBounds(it) } ?: start
                val t = progress.value.coerceIn(0f, 1f)
                val x = start.left + (end.left - start.left) * t - root.left
                val y = start.top + (end.top - start.top) * t - root.top
                val width = start.width + (end.width - start.width) * t
                Text(flightSource.text, color = palette.textPrimary, fontSize = 14.sp,
                    maxLines = if (t < 0.5f) 1 else 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .width(with(density) { width.coerceAtLeast(1f).toDp() }).clearAndSetSemantics {})
            }
        }
        }
        // The row menu hosts its own popover (anchored to the row); this screen only resolves the rights for it.
        LaunchedEffect(rowMenuFor) {
            val target = rowMenuFor ?: return@LaunchedEffect
            capabilities = conversation.messageCapabilities(state.chatId, target.id).getOrNull()
        }
    }
}

/** One pinned message in the list: author, the same preview text the bar shows, the media tile and the full date. */
@Composable
private fun PinnedMessageRow(
    message: ChatMessage,
    chat: ChatPreview,
    member: ProfileMember?,
    directChat: ChatPreview?,
    resolvedPhoto: AvatarPhoto?,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    menuExpanded: Boolean,
    capabilities: MessageCapabilities?,
    previewTextModifier: Modifier = Modifier,
    onMenuDismiss: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onJump: () -> Unit,
    onUnpin: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val senderName = message.senderName.ifBlank {
        when {
            message.isOutgoing -> stringResource(R.string.chat_you)
            !chat.isGroup || message.isChannelPost -> chat.title
            else -> stringResource(R.string.chat_pinned_unknown_sender)
        }
    }
    val senderInitial = if (senderName.isEmpty()) "" else senderName.substring(0, senderName.offsetByCodePoints(0, 1)).uppercase()
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongPress)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                member != null -> TelegramAvatar(member.initials, Color(member.avatarColorArgb), 36.dp, resolvedPhoto ?: member.photo, observeFile, downloadFile)
                directChat != null && resolvedPhoto == null -> ChatAvatar(directChat, 36.dp, observeFile, downloadFile)
                resolvedPhoto != null -> TelegramAvatar(senderInitial, palette.surface2, 36.dp, resolvedPhoto, observeFile, downloadFile)
                message.isChannelPost || (!chat.isGroup && !message.isOutgoing && !chat.isSavedMessages) ->
                    ChatAvatar(chat, 36.dp, observeFile, downloadFile)
                else -> Box(Modifier.size(36.dp).clip(CircleShape).background(palette.surface2), contentAlignment = Alignment.Center) {
                    if (message.senderName.isBlank() && !message.isOutgoing) {
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
                    } else {
                        Text(senderInitial, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    senderName,
                    color = palette.accentInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    message.summary(),
                    modifier = previewTextModifier.fillMaxWidth(),
                    color = palette.textPrimary,
                    fontSize = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    pinnedDateLabel(message),
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            if (pinnedPreviewVisual(message) !is PinnedPreviewVisual.None) {
                Spacer(Modifier.width(10.dp))
                PinnedMediaPreview(message, observeFile, size = 48.dp)
            }
        }
        BeyGramPopover(expanded = menuExpanded, onDismissRequest = onMenuDismiss) {
            BeyGramPopoverItem(
                text = stringResource(R.string.chat_pinned_jump),
                leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null, tint = palette.textSecondary) },
                onClick = { onMenuDismiss(); onJump() }
            )
            if (!chat.hasProtectedContent && capabilities?.canCopy == true && message.text.isNotBlank()) {
                BeyGramPopoverItem(
                    text = stringResource(R.string.message_action_copy_text),
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.chat_message), message.text))
                        onMenuDismiss()
                    }
                )
            }
            when (capabilities?.canPin) {
                // Without the right the item disappears: a disabled promise of an unpin reads as a broken action.
                false -> Unit
                // Unknown rights keep the item visible but inert until the answer arrives.
                else -> BeyGramPopoverItem(
                    text = stringResource(R.string.chat_pinned_unpin),
                    enabled = capabilities?.canPin == true,
                    danger = true,
                    onClick = { onMenuDismiss(); onUnpin() }
                )
            }
        }
    }
}

private fun pinnedDateLabel(message: ChatMessage): String =
    if (message.dateSeconds > 0L) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(message.dateSeconds * 1000L))
    } else message.time

