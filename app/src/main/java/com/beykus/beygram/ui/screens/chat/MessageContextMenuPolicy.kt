package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.geometry.Rect
import com.beykus.beygram.domain.model.MessageCapabilities

internal enum class MessageContextActionId {
    Edit,
    Reply,
    Copy,
    Forward,
    Select,
    Pin,
    CopyEntity,
    CopyLink,
    ForwardCopy,
    Translate,
    PlayNext,
    Enqueue,
    AddToPlaylist,
    Report,
    Delete
}

internal data class PlannedMessageActions(
    val primary: List<MessageContextActionId>,
    val secondary: List<MessageContextActionId>
)

internal fun planMessageContextActions(
    capabilities: MessageCapabilities,
    hasText: Boolean,
    canPin: Boolean,
    hasCopyEntity: Boolean,
    canTranslate: Boolean,
    hasPlayNext: Boolean,
    hasEnqueue: Boolean,
    hasAddToPlaylist: Boolean,
    canReport: Boolean
): PlannedMessageActions {
    val primary = buildList {
        if (capabilities.canEdit) add(MessageContextActionId.Edit)
        if (capabilities.canReply) add(MessageContextActionId.Reply)
        if (capabilities.canCopy && hasText) add(MessageContextActionId.Copy)
        if (capabilities.canForward) add(MessageContextActionId.Forward)
    }
    val secondary = buildList {
        add(MessageContextActionId.Select)
        if (canPin) add(MessageContextActionId.Pin)
        if (hasCopyEntity) add(MessageContextActionId.CopyEntity)
        if (capabilities.canGetLink) add(MessageContextActionId.CopyLink)
        if (capabilities.canForward) add(MessageContextActionId.ForwardCopy)
        if (canTranslate) add(MessageContextActionId.Translate)
        if (hasPlayNext) add(MessageContextActionId.PlayNext)
        if (hasEnqueue) add(MessageContextActionId.Enqueue)
        if (hasAddToPlaylist) add(MessageContextActionId.AddToPlaylist)
        if (canReport) add(MessageContextActionId.Report)
        if (capabilities.canDeleteForSelf || capabilities.canDeleteForAll) add(MessageContextActionId.Delete)
    }
    return PlannedMessageActions(primary = primary, secondary = secondary)
}

internal data class PopoverPlacement(
    val x: Int,
    val y: Int,
    val placedAbove: Boolean,
    val useBottomSheetFallback: Boolean,
    val maxHeightPx: Int? = null
)

/**
 * Maximum card height that fits inside [viewportBounds] with [marginPx] top and bottom clearance.
 */
internal fun computePopoverMaxHeightPx(viewportBounds: Rect, marginPx: Int): Int =
    (viewportBounds.height - marginPx * 2f).toInt().coerceAtLeast(0)

/**
 * Computes window coordinates for the message context popover within [viewportBounds] (already inset for
 * status bar, navigation bar, IME, top chrome, and split-pane bounds). Falls back to a compact bottom sheet
 * when the anchor is offscreen/invalid or neither above nor below the anchor has enough vertical room.
 */
internal fun computeMessagePopoverPlacement(
    anchorBounds: Rect?,
    viewportBounds: Rect,
    menuWidthPx: Int,
    menuHeightPx: Int,
    gapPx: Int,
    marginPx: Int,
    isOutgoing: Boolean,
    minPopoverHeightPx: Int = menuHeightPx
): PopoverPlacement {
    if (
        anchorBounds == null ||
        viewportBounds.width <= 0f ||
        viewportBounds.height <= 0f ||
        anchorBounds.width <= 0f ||
        anchorBounds.height <= 0f ||
        anchorBounds.bottom <= viewportBounds.top ||
        anchorBounds.top >= viewportBounds.bottom
    ) {
        return PopoverPlacement(0, 0, placedAbove = false, useBottomSheetFallback = true)
    }
    val anchorTop = anchorBounds.top.coerceIn(viewportBounds.top, viewportBounds.bottom)
    val anchorBottom = anchorBounds.bottom.coerceIn(viewportBounds.top, viewportBounds.bottom)
    val spaceBelow = (viewportBounds.bottom - anchorBottom - gapPx - marginPx).toInt().coerceAtLeast(0)
    val spaceAbove = (anchorTop - viewportBounds.top - gapPx - marginPx).toInt().coerceAtLeast(0)
    val maxAvailableSide = maxOf(spaceBelow, spaceAbove)
    if (maxAvailableSide < minPopoverHeightPx) {
        return PopoverPlacement(0, 0, placedAbove = false, useBottomSheetFallback = true)
    }
    val placeAbove = spaceBelow < menuHeightPx && spaceAbove > spaceBelow
    val availableOnSide = if (placeAbove) spaceAbove else spaceBelow
    val constrainedHeight = menuHeightPx.coerceAtMost(availableOnSide)
    val minY = viewportBounds.top.toInt() + marginPx
    val maxY = (viewportBounds.bottom.toInt() - constrainedHeight - marginPx).coerceAtLeast(minY)
    val rawY = if (placeAbove) {
        (anchorTop - gapPx - constrainedHeight).toInt()
    } else {
        (anchorBottom + gapPx).toInt()
    }
    val y = rawY.coerceIn(minY, maxY)

    val rawX = if (isOutgoing) (anchorBounds.right - menuWidthPx).toInt() else anchorBounds.left.toInt()
    val minX = viewportBounds.left.toInt() + marginPx
    val maxX = (viewportBounds.right.toInt() - menuWidthPx - marginPx).coerceAtLeast(minX)
    val x = rawX.coerceIn(minX, maxX)
    return PopoverPlacement(
        x = x,
        y = y,
        placedAbove = placeAbove,
        useBottomSheetFallback = false,
        maxHeightPx = availableOnSide
    )
}

internal data class MessageContextScenePlacement(
    val messageBounds: Rect,
    val menu: PopoverPlacement,
    val reactionBounds: Rect? = null
)

/** Chooses the layout that exposes the most menu content with the least movement of the message. */
internal fun computeMessageContextScene(
    message: Rect,
    viewport: Rect,
    desiredMenuHeight: Int,
    minMenuHeight: Int,
    menuWidth: Int,
    gap: Int,
    isOutgoing: Boolean,
    reactionWidth: Int = 0,
    reactionHeight: Int = 0
): MessageContextScenePlacement? {
    if (message.width <= 0 || message.height <= 0 || menuWidth > viewport.width ||
        message.width > viewport.width || message.bottom <= viewport.top || message.top >= viewport.bottom
    ) return null

    val reactionStack = if (reactionHeight > 0 && reactionWidth > 0) reactionHeight + gap else 0
    val available = (viewport.height - message.height - gap - reactionStack).toInt()
    if (available < minMenuHeight) return null
    val menuHeight = minOf(available, maxOf(minMenuHeight, desiredMenuHeight))
    val left = message.left.coerceIn(viewport.left, viewport.right - message.width)
    val topWithMenuBelow = message.top.coerceIn(viewport.top + reactionStack, viewport.bottom - message.height - gap - menuHeight)
    val topWithMenuAbove = message.top.coerceIn(viewport.top + menuHeight + gap, viewport.bottom - message.height - reactionStack)
    val menuAbove = kotlin.math.abs(topWithMenuAbove - message.top) < kotlin.math.abs(topWithMenuBelow - message.top)
    val top = if (menuAbove) topWithMenuAbove else topWithMenuBelow
    val destination = Rect(left, top, left + message.width, top + message.height)
    val menuX = (if (isOutgoing) destination.right - menuWidth else left)
        .coerceIn(viewport.left, viewport.right - menuWidth)
    val menuY = if (menuAbove) top - gap - menuHeight else destination.bottom + gap
    val reactionBounds = if (reactionStack > 0) {
        val width = minOf(reactionWidth.toFloat(), viewport.width)
        val rx = (if (isOutgoing) destination.right - width else destination.left)
            .coerceIn(viewport.left, viewport.right - width)
        val ry = if (menuAbove) destination.bottom + gap else destination.top - reactionStack
        Rect(rx, ry, rx + width, ry + reactionHeight)
    } else null
    return MessageContextScenePlacement(
        destination,
        PopoverPlacement(menuX.toInt(), menuY.toInt(), menuAbove, false, menuHeight),
        reactionBounds
    )
}

/** Keeps a scrollable message in the same scene when its original bounds cannot fit. */
internal fun computeScrollableMessageContextScene(
    anchor: Rect?,
    viewport: Rect,
    desiredMenuHeight: Int,
    minMenuHeight: Int,
    menuWidth: Int,
    gap: Int,
    isOutgoing: Boolean,
    reactionWidth: Int,
    reactionHeight: Int
): MessageContextScenePlacement? {
    if (viewport.width <= 0 || viewport.height <= gap) return null
    val reactionStack = if (reactionWidth > 0 && reactionHeight > 0) reactionHeight + gap else 0
    val showSeparateReactions = viewport.height - reactionStack - gap >= 2 * minMenuHeight
    val available = viewport.height - gap - if (showSeparateReactions) reactionStack else 0
    val menuMinimum = minOf(minMenuHeight, (available / 2).toInt())
    if (menuMinimum <= 0) return null
    val height = minOf(anchor?.height?.takeIf { it > 0 } ?: available, available / 2)
    val width = minOf(anchor?.width?.takeIf { it > 0 } ?: menuWidth.toFloat(), viewport.width)
    val left = (anchor?.left ?: if (isOutgoing) viewport.right - width else viewport.left)
        .coerceIn(viewport.left, viewport.right - width)
    val top = (anchor?.top ?: viewport.top).coerceIn(viewport.top, viewport.bottom - height)
    return computeMessageContextScene(
        Rect(left, top, left + width, top + height), viewport, desiredMenuHeight, menuMinimum,
        minOf(menuWidth, viewport.width.toInt()), gap, isOutgoing,
        if (showSeparateReactions) reactionWidth else 0,
        if (showSeparateReactions) reactionHeight else 0
    )
}
