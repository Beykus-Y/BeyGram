package com.beykus.beygram.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassCard
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.design.usesGlassPanels
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.fadeEnter
import com.beykus.beygram.ui.motion.fadeExit
import com.beykus.beygram.ui.motion.rememberMotionSpec

private val PopoverGap = 6.dp
private val PopoverMargin = 8.dp
private val PopoverRadius = 20.dp
private val PopoverMinWidth = 180.dp
private val PopoverMaxWidth = 280.dp
private val PopoverItemMinHeight = 48.dp
private const val PopoverScaleStep = 0.08f

/**
 * BeyGram's compact menu for quick actions, shown next to the control that opened it. Like Material's DropdownMenu it is
 * anchored to its parent layout, so put it inside the Box of the trigger. It is drawn with BeyGram's surface and colours
 * and uses the shared Motion tokens, so Motion Off and the reduced-motion setting apply to it.
 *
 * - [expanded] opens and closes the menu. The exit animation finishes before the popup leaves the screen.
 * - [onDismissRequest] handles a tap outside, Back and any other close. The caller sets [expanded] to false there.
 * - [alignEnd] lines the menu up with the end edge of the anchor, which suits an overflow button in a corner.
 * - [offset] moves the menu from its computed place, the same way as DropdownMenu's offset.
 * - The menu opens below the anchor, or above it when there is no room below. It stays inside the window and above the
 *   keyboard. Its height is limited to 60% of the screen and longer content scrolls.
 * - Put [BeyGramPopoverItem] rows in [content].
 */
@Composable
fun BeyGramPopover(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = true,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit
) {
    if (com.beykus.beygram.LocalMainTabsCovered.current) {
        if (expanded) androidx.compose.runtime.LaunchedEffect(Unit) { onDismissRequest() }
        return
    }
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = expanded
    // Keep the popup while its exit animation runs, and leave composition once the exit has finished.
    if (!visibleState.currentState && !visibleState.targetState) return

    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val fill = groupSurfaceColor()
    val shape = styledShape(PopoverRadius)
    val enterSpec = rememberMotionSpec(MotionToken.SheetEnter)
    val exitSpec = rememberMotionSpec(MotionToken.SheetExit)
    val origin = TransformOrigin(if (alignEnd) 1f else 0f, 0f)
    val enter = if (enterSpec.isInstant) EnterTransition.None else
        enterSpec.fadeEnter() + scaleIn(
            enterSpec.animationSpec(),
            initialScale = 1f - PopoverScaleStep * enterSpec.intensity,
            transformOrigin = origin
        )
    val exit = if (exitSpec.isInstant) ExitTransition.None else
        exitSpec.fadeExit() + scaleOut(
            exitSpec.animationSpec(),
            targetScale = 1f - PopoverScaleStep * exitSpec.intensity,
            transformOrigin = origin
        )
    val gapPx = with(density) { PopoverGap.roundToPx() }
    val marginPx = with(density) { PopoverMargin.roundToPx() }
    val shiftPx = with(density) { IntOffset(offset.x.roundToPx(), offset.y.roundToPx()) }
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val positionProvider = remember(alignEnd, gapPx, marginPx, imeBottomPx, shiftPx) {
        BeyGramPopoverPosition(alignEnd, gapPx, marginPx, imeBottomPx, shiftPx)
    }
    val maxHeight = (screenHeightDp * 0.6f).dp

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(visibleState = visibleState, enter = enter, exit = exit) {
            Column(
                modifier = modifier
                    .widthIn(min = PopoverMinWidth, max = PopoverMaxWidth)
                    .heightIn(max = maxHeight)
                    .then(
                        if (design.usesGlassPanels) Modifier.glassCard(design, palette.surface2, palette.textSecondary, shape)
                        else Modifier.shadow(4.dp, shape).clip(shape).background(fill)
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 6.dp)
            ) {
                content()
            }
        }
    }
}

/**
 * One row of a [BeyGramPopover]. [leadingIcon] is drawn with its own tint, as in Material's DropdownMenuItem. [danger]
 * colours the text for a destructive action. A disabled row is dimmed and does not react to taps.
 */
@Composable
fun BeyGramPopoverItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    val palette = beyGramPalette()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = PopoverItemMinHeight)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = if (danger) palette.danger else palette.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Places the popover next to its anchor. The rule itself is in [popoverOffset], which unit tests cover. */
private class BeyGramPopoverPosition(
    private val alignEnd: Boolean,
    private val gapPx: Int,
    private val marginPx: Int,
    private val imeBottomPx: Int,
    private val shift: IntOffset
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = popoverOffset(
        anchorBounds = anchorBounds,
        windowSize = windowSize,
        contentSize = popupContentSize,
        alignEnd = alignEnd,
        gapPx = gapPx,
        marginPx = marginPx,
        imeBottomPx = imeBottomPx,
        shift = shift
    )
}

/**
 * The placement rule for a popover, in window pixels. The popover is aligned to the start or end edge of the anchor and
 * opens below it. It opens above the anchor only when it does not fit below and there is room above. The bottom limit is
 * the window height minus the keyboard height and the margin. The result is moved by [shift] and then kept inside the
 * window margins.
 */
internal fun popoverOffset(
    anchorBounds: IntRect,
    windowSize: IntSize,
    contentSize: IntSize,
    alignEnd: Boolean,
    gapPx: Int,
    marginPx: Int,
    imeBottomPx: Int,
    shift: IntOffset = IntOffset.Zero
): IntOffset {
    val maxX = (windowSize.width - contentSize.width - marginPx).coerceAtLeast(marginPx)
    val preferredX = (if (alignEnd) anchorBounds.right - contentSize.width else anchorBounds.left) + shift.x
    val x = preferredX.coerceIn(marginPx, maxX)

    val bottomLimit = windowSize.height - imeBottomPx - marginPx
    val below = anchorBounds.bottom + gapPx
    val above = anchorBounds.top - gapPx - contentSize.height
    val fitsBelow = below + contentSize.height <= bottomLimit
    val fitsAbove = above >= marginPx
    val preferredY = (if (fitsBelow || !fitsAbove) below else above) + shift.y
    val maxY = (bottomLimit - contentSize.height).coerceAtLeast(marginPx)
    val y = preferredY.coerceIn(marginPx, maxY)
    return IntOffset(x, y)
}
