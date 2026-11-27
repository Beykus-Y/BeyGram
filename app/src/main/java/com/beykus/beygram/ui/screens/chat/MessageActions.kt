package com.beykus.beygram.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddReaction
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.BeyGramPopover
import com.beykus.beygram.ui.components.BeyGramPopoverItem
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.ReactionOption
import com.beykus.beygram.ui.components.emojiPresentation
import com.beykus.beygram.ui.design.GlassPanel
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.SurfaceTreatment
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.design.styledTopShape
import com.beykus.beygram.ui.motion.fadeEnter
import com.beykus.beygram.ui.motion.fadeExit
import com.beykus.beygram.ui.motion.rememberMotionSpec
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import com.beykus.beygram.ui.components.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageCapabilities
import com.beykus.beygram.domain.model.MessageSpecialContent
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.motionEntrance
import androidx.compose.foundation.layout.width

/** Replaces the chat top bar while a message is selected (long press). */
@Composable
internal fun MessageActionsBar(
    capabilities: MessageCapabilities?,
    error: String?,
    hasText: Boolean,
    isMedia: Boolean,
    canPin: Boolean,
    isPinned: Boolean,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onReply: () -> Unit,
    onQuote: (() -> Unit)? = null,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onCopyLink: () -> Unit = {},
    onCopyEntity: (() -> Unit)? = null,
    onReactions: (() -> Unit)? = null,
    onForward: () -> Unit = {},
    onForwardCopy: () -> Unit = {},
    onSelect: () -> Unit = {},
    onTranslate: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onEnqueue: (() -> Unit)? = null
) {
    val palette = beyGramPalette()
    var moreOpen by remember(capabilities, canPin) { mutableStateOf(false) }
    val translate = onTranslate
    val react = onReactions
    Column(Modifier.fillMaxWidth().chatChromeBackground(palette.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = palette.textPrimary) }
            Text(stringResource(R.string.message_title), color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            when {
                capabilities != null -> {
                    if (react != null) ActionIcon(Icons.Rounded.AddReaction, stringResource(R.string.message_action_react), palette.accent, react)
                    if (capabilities.canReply) ActionIcon(Icons.AutoMirrored.Rounded.Reply, stringResource(R.string.message_action_reply), palette.accent, onReply)
                    if (capabilities.canCopy && hasText) ActionIcon(Icons.Rounded.ContentCopy, stringResource(R.string.message_action_copy_text), palette.accent, onCopy)
                    if (capabilities.canEdit && react == null) ActionIcon(Icons.Rounded.Edit, if (isMedia) stringResource(R.string.message_action_edit_caption) else stringResource(R.string.message_action_edit), palette.accent, onEdit)
                    if (capabilities.canDeleteForSelf || capabilities.canDeleteForAll) ActionIcon(Icons.Rounded.Delete, stringResource(R.string.common_delete), palette.danger, onDelete)
                    Box {
                        IconButton(onClick = { moreOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, stringResource(R.string.message_action_more), tint = palette.accent)
                        }
                        BeyGramPopover(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            if (react != null && capabilities.canEdit) BeyGramPopoverItem(
                                text = stringResource(if (isMedia) R.string.message_action_edit_caption else R.string.message_action_edit),
                                leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = palette.accent) },
                                onClick = { moreOpen = false; onEdit() }
                            )
                            react?.let { pick ->
                                BeyGramPopoverItem(
                                    text = stringResource(R.string.message_action_react),
                                    leadingIcon = { Icon(Icons.Rounded.AddReaction, null, tint = palette.accent) },
                                    onClick = { moreOpen = false; pick() }
                                )
                            }
                            onCopyEntity?.let { copy ->
                                BeyGramPopoverItem(
                                    text = stringResource(R.string.message_action_copy_entity),
                                    onClick = { moreOpen = false; copy() }
                                )
                            }
                            if (canPin) BeyGramPopoverItem(
                                text = stringResource(if (isPinned) R.string.message_action_unpin else R.string.message_action_pin),
                                onClick = { moreOpen = false; onTogglePin() }
                            )
                            if (capabilities.canGetLink) BeyGramPopoverItem(
                                text = stringResource(R.string.message_action_copy_link),
                                onClick = { moreOpen = false; onCopyLink() }
                            )
                            if (capabilities.canForward) BeyGramPopoverItem(
                                text = stringResource(R.string.message_action_forward),
                                onClick = { moreOpen = false; onForward() }
                            )
                            if (capabilities.canForward) BeyGramPopoverItem(
                                text = stringResource(R.string.message_action_forward_copy),
                                onClick = { moreOpen = false; onForwardCopy() }
                            )
                            BeyGramPopoverItem(
                                text = stringResource(R.string.message_action_select),
                                onClick = { moreOpen = false; onSelect() }
                            )
                            if (translate != null) BeyGramPopoverItem(
                                text = stringResource(R.string.message_action_translate),
                                leadingIcon = { Icon(Icons.Rounded.Translate, null, tint = palette.accent) },
                                onClick = { moreOpen = false; translate() }
                            )
                            onPlayNext?.let { next -> BeyGramPopoverItem(
                                text = stringResource(R.string.queue_play_next),
                                onClick = { moreOpen = false; next() }
                            ) }
                            onEnqueue?.let { enqueue -> BeyGramPopoverItem(
                                text = stringResource(R.string.queue_add_to_end),
                                onClick = { moreOpen = false; enqueue() }
                            ) }
                            onAddToPlaylist?.let { add -> BeyGramPopoverItem(
                                text = stringResource(R.string.music_add_to_playlist),
                                leadingIcon = { Icon(Icons.Rounded.Add, null, tint = palette.accent) },
                                onClick = { moreOpen = false; add() }
                            ) }
                            onReport?.let { report -> BeyGramPopoverItem(
                                text = stringResource(R.string.message_action_report),
                                onClick = { moreOpen = false; report() }
                            ) }
                        }
                    }
                }
                error != null -> TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry), color = palette.accent) }
                else -> CircularProgressIndicator(Modifier.padding(end = 14.dp).size(22.dp), color = palette.accent, strokeWidth = 2.dp)
            }
        }
        error?.let { Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp).errorAnnouncement()) }
        HorizontalDivider(color = palette.divider)
    }
}

/**
 * Anchored contextual popover shown near the selected message bubble instead of replacing the chat top bar.
 * Falls back to a bottom-anchored sheet when viewport space around the bubble is too tight (e.g. giant bubble + IME).
 */
@Composable
internal fun MessageContextPopover(
    anchorBounds: Rect?,
    paneBounds: Rect? = null,
    foreground: (@Composable (MessageContextTextSelection?) -> Unit)? = null,
    foregroundText: String? = null,
    foregroundEntities: List<MessageTextEntity> = emptyList(),
    quoteMaxLength: Int = 1024,
    onQuoteFragment: ((SelectedMessageQuote) -> Unit)? = null,
    onCopyFragment: ((String) -> Unit)? = null,
    onForegroundVisible: (Boolean) -> Unit = {},
    isOutgoing: Boolean,
    capabilities: MessageCapabilities?,
    error: String?,
    hasText: Boolean,
    isMedia: Boolean,
    canPin: Boolean,
    isPinned: Boolean,
    reactionOptions: List<ReactionOption>,
    chosenReactions: Set<ReactionOption>,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onDownloadFile: (Int) -> Unit,
    onQuickReaction: ((ReactionOption) -> Unit)? = null,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onReply: () -> Unit,
    onQuote: (() -> Unit)? = null,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onCopyLink: () -> Unit = {},
    onCopyEntity: (() -> Unit)? = null,
    onReactions: (() -> Unit)? = null,
    onForward: () -> Unit = {},
    onForwardCopy: () -> Unit = {},
    onSelect: () -> Unit = {},
    onTranslate: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onEnqueue: (() -> Unit)? = null
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val motionSpec = rememberMotionSpec(MotionToken.SheetEnter)

    var moreOpen by remember(capabilities, canPin) { mutableStateOf(false) }
    var rootBounds by remember { mutableStateOf<Rect?>(null) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val freeReactions = remember(reactionOptions) { reactionOptions.filter { !it.isPaid }.take(6) }
    // Latch the unconstrained desired height per menu content state so clamping never feeds a smaller
    // height back into placement and causes a 600 -> 430 -> 600 measurement oscillation.
    var desiredMenuHeightPx by remember(capabilities, moreOpen, error, freeReactions.size) { androidx.compose.runtime.mutableIntStateOf(0) }
    if (menuSize.height > desiredMenuHeightPx) desiredMenuHeightPx = menuSize.height
    var visible by remember { mutableStateOf(motionSpec.isInstant) }
    var closing by remember { mutableStateOf(false) }
    val close by rememberUpdatedState(onClose)
    val reportForeground by rememberUpdatedState(onForegroundVisible)
    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(closing) {
        if (closing) {
            visible = false
            delay(motionSpec.durationMillis.toLong())
            close()
        }
    }
    val dismiss = { if (foreground == null || motionSpec.isInstant) close() else closing = true }

    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = motionSpec.animationSpec(),
        label = "messageContextPopover"
    )

    val safeLeft = WindowInsets.safeDrawing.getLeft(density, layoutDirection)
    val safeRight = WindowInsets.safeDrawing.getRight(density, layoutDirection)
    val safeTop = WindowInsets.safeDrawing.getTop(density)
    val safeBottom = maxOf(WindowInsets.safeDrawing.getBottom(density), WindowInsets.ime.getBottom(density))

    val gapPx = with(density) { 8.dp.roundToPx() }
    val marginPx = with(density) { 12.dp.roundToPx() }
    val minPopoverHeightPx = with(density) { 140.dp.roundToPx() }

    val windowBounds = rootBounds?.let { root ->
        Rect(
            left = root.left + safeLeft,
            top = root.top + safeTop,
            right = (root.right - safeRight).coerceAtLeast(root.left + safeLeft),
            bottom = (root.bottom - safeBottom).coerceAtLeast(root.top + safeTop)
        )
    } ?: Rect.Zero

    val viewportBounds = paneBounds?.intersect(windowBounds) ?: windowBounds
    val preferredSceneMenuHeight = with(density) { 280.dp.roundToPx() }
    val menuWidth = menuSize.width.coerceAtLeast(with(density) { 248.dp.roundToPx() })
    val showReactionStrip = onQuickReaction != null && (freeReactions.isNotEmpty() || onReactions != null)
    val reactionCount = freeReactions.size + if (onReactions != null) 1 else 0
    val reactionWidth = if (showReactionStrip) with(density) { (16 + reactionCount * 44).dp.roundToPx() } else 0
    val reactionHeight = if (showReactionStrip) with(density) { 56.dp.roundToPx() } else 0
    val scene = if (foreground != null && anchorBounds != null) {
        computeMessageContextScene(
            anchorBounds, viewportBounds.deflate(marginPx.toFloat()), preferredSceneMenuHeight,
            minPopoverHeightPx, menuWidth, gapPx, isOutgoing, reactionWidth, reactionHeight
        )
    } else null
    val showForeground = scene != null && foreground != null
    val selection = if (showForeground && foregroundText != null && capabilities?.canCopy == true) {
        remember(foregroundText, foregroundEntities) { MessageContextTextSelection(foregroundText, foregroundEntities) }
    } else null
    val selectedText = selection?.selectedText
    DisposableEffect(showForeground) {
        reportForeground(showForeground)
        onDispose { if (showForeground) reportForeground(false) }
    }

    val placement = scene?.menu ?: computeMessagePopoverPlacement(
        anchorBounds = anchorBounds,
        viewportBounds = viewportBounds,
        menuWidthPx = menuWidth,
        menuHeightPx = desiredMenuHeightPx.coerceAtLeast(with(density) { 200.dp.roundToPx() }),
        gapPx = gapPx,
        marginPx = marginPx,
        isOutgoing = isOutgoing,
        minPopoverHeightPx = minPopoverHeightPx
    )

    val maxCardHeightPx = computePopoverMaxHeightPx(viewportBounds, marginPx)
    val effectiveMaxHeightPx = if (!placement.useBottomSheetFallback && placement.maxHeightPx != null) {
        minOf(maxCardHeightPx, placement.maxHeightPx)
    } else {
        maxCardHeightPx
    }
    val maxCardHeightDp = if (effectiveMaxHeightPx > 0) with(density) { effectiveMaxHeightPx.toDp() } else 360.dp


    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset.Zero,
        onDismissRequest = { if (selectedText != null) selection?.clear() else dismiss() },
        properties = PopupProperties(focusable = true, clippingEnabled = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootBounds = it.boundsInWindow() }
                .background(Color.Black.copy(alpha = (if (showForeground) 0.62f else 0.28f) * progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (selectedText != null) selection?.clear() else dismiss() }
                )
        ) {
            if (scene != null && foreground != null && anchorBounds != null) {
                val root = rootBounds ?: Rect.Zero
                val target = scene.messageBounds
                Box(
                    Modifier.absoluteOffset {
                        IntOffset(
                            (anchorBounds.left + (target.left - anchorBounds.left) * progress - root.left).roundToInt(),
                            (anchorBounds.top + (target.top - anchorBounds.top) * progress - root.top).roundToInt()
                        )
                    }.size(with(density) { target.width.toDp() }, with(density) { target.height.toDp() }).clipToBounds()
                ) { foreground(selection) }
            }
            val reactionStrip: @Composable () -> Unit = {
            if (showReactionStrip && onQuickReaction != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    freeReactions.forEach { option ->
                        val chosen = option in chosenReactions
                        val label = option.emoji.ifBlank { stringResource(R.string.message_action_react) }
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (chosen) palette.accentSoft else Color.Transparent)
                                .clickable(role = Role.Button) { onQuickReaction(option) }
                                .semantics {
                                    contentDescription = label
                                    selected = chosen
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (option.customEmojiId != 0L) {
                                val emoji by observeCustomEmoji(option.customEmojiId).collectAsStateWithLifecycle()
                                val fileId = emoji?.fileId ?: 0
                                val file by remember(fileId) { observeFile(fileId) }.collectAsStateWithLifecycle()
                                androidx.compose.runtime.LaunchedEffect(fileId) {
                                    if (fileId != 0) onDownloadFile(fileId)
                                }
                                val path = file.localPath
                                if (path != null) {
                                    LocalMediaImage(path, Modifier.size(24.dp), fit = true, animate = false)
                                } else {
                                    Text(emojiPresentation(option.emoji.ifBlank { "✨" }), fontSize = 20.sp)
                                }
                            } else {
                                Text(emojiPresentation(option.emoji), fontSize = 20.sp)
                            }
                        }
                    }
                    onReactions?.let { openPicker ->
                        IconButton(
                            onClick = openPicker,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Rounded.AddReaction, stringResource(R.string.message_action_react), tint = palette.accent)
                        }
                    }
                }
                }
            }
            scene?.reactionBounds?.takeUnless { selectedText != null }?.let { bounds ->
                val root = rootBounds ?: Rect.Zero
                val reactionModifier = Modifier.absoluteOffset {
                    IntOffset((bounds.left - root.left).roundToInt(), (bounds.top - root.top).roundToInt())
                }.width(with(density) { bounds.width.toDp() })
                    .graphicsLayer { alpha = progress }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                MessageContextSurface(reactionModifier, CircleShape, reactionStrip)
            }
            val cardContent: @Composable () -> Unit = {
                Column(Modifier.fillMaxWidth()) {
                    if (selectedText != null && selection != null) {
                        val fragment = remember(selection, selection.value.selection, quoteMaxLength) {
                            selectedMessageQuote(selection.source, selection.value.selection, selection.entities, quoteMaxLength)
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (onQuoteFragment != null && capabilities?.canReply == true) {
                                TextButton(enabled = fragment != null, onClick = { fragment?.let(onQuoteFragment) }) {
                                    Text(stringResource(R.string.chat_quote_action))
                                }
                            }
                            if (onCopyFragment != null) {
                                TextButton(onClick = { onCopyFragment(selectedText) }) {
                                    Text(stringResource(R.string.message_action_copy_text))
                                }
                            }
                            IconButton(onClick = selection::clear) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.common_close))
                            }
                        }
                        return@Column
                    }

                    if (showReactionStrip && scene?.reactionBounds == null) {
                        reactionStrip()
                        HorizontalDivider(color = palette.divider)
                    }

                    when {
                        capabilities != null -> {
                            val planned = remember(
                                capabilities,
                                hasText,
                                canPin,
                                onCopyEntity != null,
                                onTranslate != null,
                                onPlayNext != null,
                                onEnqueue != null,
                                onAddToPlaylist != null,
                                onReport != null
                            ) {
                                planMessageContextActions(
                                    capabilities = capabilities,
                                    hasText = hasText,
                                    canPin = canPin,
                                    hasCopyEntity = onCopyEntity != null,
                                    canTranslate = onTranslate != null,
                                    hasPlayNext = onPlayNext != null,
                                    hasEnqueue = onEnqueue != null,
                                    hasAddToPlaylist = onAddToPlaylist != null,
                                    canReport = onReport != null
                                )
                            }
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp)
                            ) {
                                planned.primary.forEach { id ->
                                    ContextActionItem(
                                        id = id,
                                        isMedia = isMedia,
                                        isPinned = isPinned,
                                        onEdit = onEdit,
                                        onReply = onReply,
                                        onCopy = onCopy,
                                        onForward = onForward,
                                        onSelect = onSelect,
                                        onTogglePin = onTogglePin,
                                        onCopyEntity = onCopyEntity,
                                        onCopyLink = onCopyLink,
                                        onForwardCopy = onForwardCopy,
                                        onTranslate = onTranslate,
                                        onPlayNext = onPlayNext,
                                        onEnqueue = onEnqueue,
                                        onAddToPlaylist = onAddToPlaylist,
                                        onReport = onReport,
                                        onDelete = onDelete
                                    )
                                }
                                val quoteAction = if (selection != null && onQuoteFragment != null) selection::selectAll else onQuote
                                if (quoteAction != null && capabilities.canReply && capabilities.canCopy && hasText) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .clickable(role = Role.Button, onClick = quoteAction)
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.FormatQuote, null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(14.dp))
                                        Text(
                                            stringResource(R.string.chat_quote_action),
                                            color = palette.textPrimary,
                                            fontSize = 15.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                if (planned.secondary.isNotEmpty()) {
                                    HorizontalDivider(color = palette.divider.copy(alpha = 0.7f), modifier = Modifier.padding(vertical = 2.dp))
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .clickable(role = Role.Button) { moreOpen = !moreOpen }
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.MoreVert, null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(14.dp))
                                        Text(
                                            stringResource(R.string.message_action_more),
                                            color = palette.textPrimary,
                                            fontSize = 15.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            if (moreOpen) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                            null,
                                            tint = palette.textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    AnimatedVisibility(
                                        visible = moreOpen,
                                        enter = motionSpec.fadeEnter(),
                                        exit = motionSpec.fadeExit()
                                    ) {
                                        Column(Modifier.fillMaxWidth()) {
                                            planned.secondary.forEach { id ->
                                                ContextActionItem(
                                                    id = id,
                                                    isMedia = isMedia,
                                                    isPinned = isPinned,
                                                    onEdit = onEdit,
                                                    onReply = onReply,
                                                    onCopy = onCopy,
                                                    onForward = onForward,
                                                    onSelect = onSelect,
                                                    onTogglePin = onTogglePin,
                                                    onCopyEntity = onCopyEntity,
                                                    onCopyLink = onCopyLink,
                                                    onForwardCopy = onForwardCopy,
                                                    onTranslate = onTranslate,
                                                    onPlayNext = onPlayNext,
                                                    onEnqueue = onEnqueue,
                                                    onAddToPlaylist = onAddToPlaylist,
                                                    onReport = onReport,
                                                    onDelete = onDelete
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        error != null -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(error, color = palette.danger, fontSize = 14.sp, modifier = Modifier.errorAnnouncement())
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = onClose) { Text(stringResource(R.string.common_close), color = palette.textSecondary) }
                                TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry), color = palette.accent) }
                            }
                        }
                        else -> Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = palette.accent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.message_title), color = palette.textSecondary, fontSize = 14.sp)
                        }
                    }
                }
            }

            val cardScale = if (motionSpec.isInstant) 1f else 0.94f + 0.06f * progress
            val cardModifier = (if (placement.useBottomSheetFallback) {
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = with(density) { (safeBottom + marginPx).toDp() })
            } else {
                val rootLeft = rootBounds?.left ?: 0f
                val rootTop = rootBounds?.top ?: 0f
                Modifier
                    .absoluteOffset {
                        val aboveScene = scene?.takeIf { it.menu.placedAbove }
                        val y = if (aboveScene != null && menuSize.height > 0) {
                            aboveScene.messageBounds.top - gapPx - menuSize.height
                        } else placement.y.toFloat()
                        IntOffset((placement.x - rootLeft).roundToInt(), (y - rootTop).roundToInt())
                    }
                    .widthIn(min = 236.dp, max = 296.dp)
            }).heightIn(max = maxCardHeightDp)
                .onSizeChanged { menuSize = it }
                .graphicsLayer {
                    alpha = progress
                    scaleX = cardScale
                    scaleY = cardScale
                }
                // Consume clicks inside the card so tapping non-interactive padding doesn't dismiss the scrim.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}

            val shape = if (placement.useBottomSheetFallback) styledTopShape(20.dp) else styledShape(
                when (design.style) {
                    DesignStyle.Material2 -> 4.dp
                    DesignStyle.LiquidGlass -> 20.dp
                    DesignStyle.Signature -> 14.dp
                    else -> 16.dp
                }
            )
            MessageContextSurface(cardModifier, shape, cardContent)
        }
    }
}

@Composable
private fun MessageContextSurface(modifier: Modifier, shape: Shape, content: @Composable () -> Unit) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    if (design.surfaces.treatment == SurfaceTreatment.Glass) {
        GlassPanel(
            design = design,
            tint = palette.surface,
            rim = palette.textSecondary,
            backdropBase = palette.screen,
            shape = shape,
            modifier = modifier.shadow(design.surfaces.glass?.shadowElevation ?: 8.dp, shape)
        ) {
            content()
        }
    } else {
        val elevation = when (design.style) {
            DesignStyle.Material2 -> 8.dp
            DesignStyle.Material3 -> 3.dp
            DesignStyle.Signature -> 1.dp
            else -> 6.dp
        }
        val borderModifier = when (design.style) {
            DesignStyle.Signature -> Modifier.border(1.dp, palette.divider, shape)
            DesignStyle.Default -> Modifier.border(1.dp, palette.divider.copy(alpha = 0.6f), shape)
            else -> Modifier
        }
        Box(
            modifier
                .shadow(elevation, shape)
                .clip(shape)
                .background(palette.surface)
                .then(borderModifier)
        ) {
            content()
        }
    }
}

@Composable
private fun ContextActionItem(
    id: MessageContextActionId,
    isMedia: Boolean,
    isPinned: Boolean,
    onEdit: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onSelect: () -> Unit,
    onTogglePin: () -> Unit,
    onCopyEntity: (() -> Unit)?,
    onCopyLink: () -> Unit,
    onForwardCopy: () -> Unit,
    onTranslate: (() -> Unit)?,
    onPlayNext: (() -> Unit)?,
    onEnqueue: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    onReport: (() -> Unit)?,
    onDelete: () -> Unit
) {
    val palette = beyGramPalette()
    val (label, icon, tint, onClick) = when (id) {
        MessageContextActionId.Edit -> Quad(
            stringResource(if (isMedia) R.string.message_action_edit_caption else R.string.message_action_edit),
            Icons.Rounded.Edit, palette.accent, onEdit
        )
        MessageContextActionId.Reply -> Quad(stringResource(R.string.message_action_reply), Icons.AutoMirrored.Rounded.Reply, palette.accent, onReply)
        MessageContextActionId.Copy -> Quad(stringResource(R.string.message_action_copy_text), Icons.Rounded.ContentCopy, palette.accent, onCopy)
        MessageContextActionId.Forward -> Quad(stringResource(R.string.message_action_forward), Icons.AutoMirrored.Rounded.Forward, palette.accent, onForward)
        MessageContextActionId.Select -> Quad(stringResource(R.string.message_action_select), Icons.Rounded.CheckBox, palette.textSecondary, onSelect)
        MessageContextActionId.Pin -> Quad(stringResource(if (isPinned) R.string.message_action_unpin else R.string.message_action_pin), Icons.Rounded.PushPin, palette.textSecondary, onTogglePin)
        MessageContextActionId.CopyEntity -> Quad(stringResource(R.string.message_action_copy_entity), Icons.Rounded.ContentCopy, palette.textSecondary, onCopyEntity ?: {})
        MessageContextActionId.CopyLink -> Quad(stringResource(R.string.message_action_copy_link), Icons.Rounded.Link, palette.textSecondary, onCopyLink)
        MessageContextActionId.ForwardCopy -> Quad(stringResource(R.string.message_action_forward_copy), Icons.AutoMirrored.Rounded.Forward, palette.textSecondary, onForwardCopy)
        MessageContextActionId.Translate -> Quad(stringResource(R.string.message_action_translate), Icons.Rounded.Translate, palette.accent, onTranslate ?: {})
        MessageContextActionId.PlayNext -> Quad(stringResource(R.string.queue_play_next), Icons.Rounded.QueueMusic, palette.accent, onPlayNext ?: {})
        MessageContextActionId.Enqueue -> Quad(stringResource(R.string.queue_add_to_end), Icons.Rounded.QueueMusic, palette.textSecondary, onEnqueue ?: {})
        MessageContextActionId.AddToPlaylist -> Quad(stringResource(R.string.music_add_to_playlist), Icons.AutoMirrored.Rounded.PlaylistAdd, palette.accent, onAddToPlaylist ?: {})
        MessageContextActionId.Report -> Quad(stringResource(R.string.message_action_report), Icons.Rounded.Flag, palette.textSecondary, onReport ?: {})
        MessageContextActionId.Delete -> Quad(stringResource(R.string.common_delete), Icons.Rounded.Delete, palette.danger, onDelete)
    }
    val textColor = if (id == MessageContextActionId.Delete) palette.danger else palette.textPrimary
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = textColor, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, description, tint = tint) }
}

/**
 * A long press cannot select a single link inside a bubble without breaking message actions, so this explicit list
 * copies the exact entity the reader chose. Values are already normalized by the caller.
 */
@Composable
internal fun MessageLinkCopyDialog(links: List<String>, onCopy: (String) -> Unit, onDismiss: () -> Unit) {
    val palette = beyGramPalette()
    BeyGramDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.message_action_copy_entity),
        content = {
            Column {
                links.forEach { value ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { onCopy(value) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            value,
                            color = palette.accentInk,
                            fontSize = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Rounded.ContentCopy, null, tint = palette.accent, modifier = Modifier.size(20.dp))
                    }
                    HorizontalDivider(color = palette.divider)
                }
            }
        },
        confirmText = stringResource(R.string.common_close),
        onConfirm = onDismiss
    )
}

/** Inline delete confirmation shown above the input field. */
@Composable
internal fun DeleteConfirmPanel(
    capabilities: MessageCapabilities,
    everyone: Boolean,
    busy: Boolean,
    error: String?,
    onChoose: (everyone: Boolean) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().chatChromeBackground(palette.surface).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.message_delete_title), color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        if (capabilities.canDeleteForSelf) DeleteChoice(stringResource(R.string.message_delete_only_me), !everyone, !busy) { onChoose(false) }
        if (capabilities.canDeleteForAll) DeleteChoice(stringResource(R.string.message_delete_for_all), everyone, !busy) { onChoose(true) }
        Text(stringResource(R.string.message_delete_irreversible), color = palette.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        error?.let { Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp).errorAnnouncement()) }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            TextButton(enabled = !busy, onClick = onCancel) { Text(stringResource(R.string.common_cancel), color = palette.accent, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.width(4.dp))
            TextButton(enabled = !busy, onClick = onConfirm) { Text(stringResource(R.string.common_delete), color = palette.danger, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun DeleteChoice(text: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The whole row is the radio button, so TalkBack has one target instead of a row and a nested radio.
        RadioButton(selected, onClick = null, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = palette.accent))
        Text(text, color = palette.textPrimary, fontSize = 16.sp)
    }
}

/** Banner above the field while editing: the original text is shown and can be dismissed. */
@Composable
internal fun EditBanner(original: String, isCaption: Boolean, count: Int, limit: Int, error: String?, onClose: () -> Unit) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().motionEntrance(MotionToken.BannerChange, 8.dp).chatChromeBackground(palette.surface).padding(start = 18.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Edit, null, tint = palette.accent, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(if (isCaption) stringResource(R.string.message_edit_caption_banner) else stringResource(R.string.message_edit_banner), color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(error ?: original, color = if (error != null) palette.danger else palette.textSecondary, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = if (error != null) Modifier.errorAnnouncement() else Modifier)
        }
        Text("$count / $limit", color = if (count > limit) palette.danger else palette.textSecondary, fontSize = 12.sp)
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.message_edit_cancel_description), tint = palette.textSecondary) }
    }
}

@Composable internal fun ChatMessage.summary(): String = text.ifBlank {
    when (val content = special) {
        is MessageSpecialContent.Poll -> content.poll.question
        is MessageSpecialContent.Location -> stringResource(R.string.chat_preview_location)
        is MessageSpecialContent.LiveLocation -> stringResource(R.string.chat_preview_live_location)
        is MessageSpecialContent.Venue -> content.venue.title.ifBlank { stringResource(R.string.chat_preview_location) }
        is MessageSpecialContent.Contact -> content.contact.displayName
        is MessageSpecialContent.Dice -> stringResource(R.string.chat_preview_dice, content.dice.emoji)
        is MessageSpecialContent.PaidMedia -> stringResource(R.string.paid_content_preview_media)
        is MessageSpecialContent.Invoice -> content.invoice.title.ifBlank { stringResource(R.string.paid_content_invoice) }
        is MessageSpecialContent.Unsupported -> stringResource(R.string.chat_unsupported_message)
        null -> media?.kind?.label(LocalContext.current) ?: stringResource(R.string.message_title)
    }
}.take(180)
