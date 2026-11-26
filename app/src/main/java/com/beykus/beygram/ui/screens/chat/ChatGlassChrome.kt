package com.beykus.beygram.ui.screens.chat

import android.os.Build

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
internal val LocalComposerOnGlassPlane = staticCompositionLocalOf { false }

/** A fill for a chat bar that gives way to the glass pane it is drawn on. */
@Composable
internal fun Modifier.chatChromeBackground(color: Color): Modifier =
    if (LocalChatChromeOnGlass.current) this else background(color)

/**
 * The chat's header stack (top) or composer stack (bottom). In the Liquid Glass style it is one pane of glass that the
 * message list scrolls under, sampling [backdrop] (wallpaper and bubbles, recorded for this screen only). In every other
 * style it is a plain column, so nothing changes for them. The top stack covers the status bar and pads its content below it.
 *
 * [floatingContent] is drawn under the pane as detached surfaces with their own backgrounds (the pinned bar in the
 * Floating panels placement). The pane ends above it, so the card is a surface of its own and the list keeps showing
 * through the gaps around it; the reported height still includes the whole stack, so the list content stays below it.
 */
@Composable
internal fun ChatChromeHost(
    glass: Boolean,
    edge: GlassEdge,
    backdrop: GlassBackdrop?,
    onHeight: (Int) -> Unit,
    paneContent: @Composable ColumnScope.() -> Unit,
    floatingContent: @Composable ColumnScope.() -> Unit = {},
    floatingComposer: Boolean = false
) {
    val profFlags by com.beykus.beygram.perf.ProfDiagnostics.flags.collectAsStateWithLifecycle()
    val effectiveGlass = glass && !profFlags.glassChromeOnly
    val palette = beyGramPalette()
    // The top stack's background covers the status bar; its content starts below the top safe inset.
    val statusBarSpacer: @Composable ColumnScope.() -> Unit = {
        if (edge == GlassEdge.BottomHairline) Spacer(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)))
    }
    if (!effectiveGlass && edge == GlassEdge.TopHairline && floatingComposer) {
        Column(Modifier.fillMaxWidth().zIndex(1f).onSizeChanged { onHeight(it.height) }
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }, content = paneContent)
        return
    }
    if (!effectiveGlass) {
        // The top stack is drawn above the message list, so a header shadow (Material 2) is not painted over by it.
        Column(Modifier.fillMaxWidth().zIndex(if (edge == GlassEdge.BottomHairline) 1f else 0f)) {
            statusBarSpacer()
            paneContent()
            floatingContent()
        }
        return
    }
    val design = LocalDesignSystem.current
    val glassStyle = design.surfaces.glass
    val sharedComposer = edge == GlassEdge.TopHairline && !floatingComposer && backdrop != null &&
        glassStyle?.lensRefraction == true && glassStyle.samplesBackdrop && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    if (edge == GlassEdge.TopHairline && !sharedComposer) {
        // The attached Liquid Glass composer uses a soft scrim; the floating composer has none. The
        // field and round buttons carry their own surfaces. Bars drawn in this stack (read-only notice, search, selection,
        // delayed send) have no pane behind them any more, so they paint their own opaque fill again.
        CompositionLocalProvider(LocalChatChromeOnGlass provides false, LocalGlassBackdrop provides backdrop) {
            Column(
                Modifier.fillMaxWidth().zIndex(1f).onSizeChanged { onHeight(it.height) }
                    .then(if (floatingComposer) Modifier else Modifier.background(Brush.verticalGradient(listOf(Color.Transparent, palette.screen.copy(alpha = 0.72f)))))
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
                content = paneContent
            )
        }
        return
    }
    CompositionLocalProvider(LocalChatChromeOnGlass provides true, LocalGlassBackdrop provides backdrop) {
        // One measured block: the pane plus the floating cards below it, so the list reserves space for both and
        // scrolls under the gaps around the cards.
        Column(Modifier.fillMaxWidth().zIndex(1f).onSizeChanged { onHeight(it.height) }) {
            GlassPanel(
                design = design,
                tint = palette.surface,
                rim = palette.textSecondary,
                // The recorded list and wallpaper may leave gaps; the pane starts from the screen colour, not from sharp content.
                backdropBase = palette.screen,
                shape = RectangleShape,
                edge = edge,
                lensOnFreeEdge = true,
                // A tap on the pane must not reach a message scrolled underneath; its own buttons still get input first.
                modifier = Modifier.fillMaxWidth()
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            ) {
                Column(Modifier.fillMaxWidth()) {
                    statusBarSpacer()
                    if (sharedComposer) {
                        CompositionLocalProvider(
                            LocalComposerOnGlassPlane provides true,
                            LocalGlassBackdrop provides null
                        ) { paneContent() }
                    } else {
                        paneContent()
                    }
                }
            }
            // The floating cards sample the same backdrop as the pane and stay out of its rectangle, so they read as
            // their own material rather than a strip of the header. The margins around them are not swallowed: the
            // messages behind them stay reachable.
            Column(Modifier.fillMaxWidth()) { floatingContent() }
        }
    }
}

/**
 * Lets the message list and wallpaper run under the glass header and composer: the node reports its own height to the
 * parent (so layout is unchanged) but measures and draws [topPx] higher and [bottomPx] lower. Touches outside the
 * reported bounds are not delivered to the list; those areas belong to the chrome drawn over them.
 */
internal fun Modifier.underChatChrome(topPx: () -> Int, bottomPx: () -> Int): Modifier = layout { measurable, constraints ->
    com.beykus.beygram.perf.ProfDiagnostics.traceSection("BG:UnderChatChrome") {
        if (com.beykus.beygram.perf.ProfDiagnostics.underChromeBypass) {
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        } else {
            val top = topPx()
            val bottom = bottomPx()
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val placeable = measurable.measure(Constraints.fixed(width, height + top + bottom))
            layout(width, height) { placeable.place(0, -top) }
        }
    }
}
