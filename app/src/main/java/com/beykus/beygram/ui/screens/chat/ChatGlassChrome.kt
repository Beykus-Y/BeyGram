package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import com.beykus.beygram.ui.design.GlassBackdrop
import com.beykus.beygram.ui.design.GlassEdge
import com.beykus.beygram.ui.design.GlassPanel
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.LocalGlassBackdrop
import com.beykus.beygram.ui.components.beyGramPalette

/**
 * True inside the chat's glass header or composer. The bars in there (selection, message actions, search, pinned
 * preview, now playing, reply strip) skip their own opaque fill, so the one glass pane behind them stays visible and
 * the header does not turn solid while the user searches, selects or acts on a message.
 */
internal val LocalChatChromeOnGlass = staticCompositionLocalOf { false }

/** A fill for a chat bar that gives way to the glass pane it is drawn on. */
@Composable
internal fun Modifier.chatChromeBackground(color: Color): Modifier =
    if (LocalChatChromeOnGlass.current) this else background(color)

/**
 * The chat's header stack (top) or composer stack (bottom). In the Liquid Glass style it is one pane of glass that the
 * message list scrolls under, sampling [backdrop] (wallpaper and bubbles, recorded for this screen only). In every other
 * style it is a plain column, so nothing changes for them.
 */
@Composable
internal fun ChatChromeHost(
    glass: Boolean,
    edge: GlassEdge,
    backdrop: GlassBackdrop?,
    onHeight: (Int) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!glass) {
        Column(Modifier.fillMaxWidth(), content = content)
        return
    }
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    CompositionLocalProvider(LocalChatChromeOnGlass provides true, LocalGlassBackdrop provides backdrop) {
        GlassPanel(
            design = design,
            tint = palette.surface,
            rim = palette.textSecondary,
            // The recorded list and wallpaper may leave gaps; the pane starts from the screen colour, not from sharp content.
            backdropBase = palette.screen,
            shape = RectangleShape,
            edge = edge,
            // A tap on the pane must not reach a message scrolled underneath; its own buttons still get input first.
            modifier = Modifier.fillMaxWidth().zIndex(1f).onSizeChanged { onHeight(it.height) }
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

/**
 * Lets the message list and wallpaper run under the glass header and composer: the node reports its own height to the
 * parent (so layout is unchanged) but measures and draws [topPx] higher and [bottomPx] lower. Touches outside the
 * reported bounds are not delivered to the list; those areas belong to the chrome drawn over them.
 */
internal fun Modifier.underChatChrome(topPx: () -> Int, bottomPx: () -> Int): Modifier = layout { measurable, constraints ->
    val top = topPx()
    val bottom = bottomPx()
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val placeable = measurable.measure(Constraints.fixed(width, height + top + bottom))
    layout(width, height) { placeable.place(0, -top) }
}
