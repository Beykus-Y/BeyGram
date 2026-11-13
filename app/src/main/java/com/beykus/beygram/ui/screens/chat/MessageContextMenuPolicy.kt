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
        maxHeightPx = availableOnSide.takeIf { it < menuHeightPx }
    )
}
