package com.beykus.beygram.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.beykus.beygram.ui.design.GlassEdge
import com.beykus.beygram.ui.design.GlassPanel
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.LocalGlassBackdrop
import com.beykus.beygram.ui.design.glassBackdropSource
import com.beykus.beygram.ui.design.glassScreenBackground
import com.beykus.beygram.ui.design.recordsGlassBackdrop
import com.beykus.beygram.ui.design.rememberGlassBackdrop
import com.beykus.beygram.ui.design.usesGlassPanels

/**
 * True while a toolbar or a composer is drawn inside the glass pane of a screen. The bar then skips its own opaque
 * fill, so the one pane behind it stays visible — the same rule the chat chrome already follows.
 */
val LocalScreenChromeOnGlass = staticCompositionLocalOf { false }

/** The space the screen chrome takes from the scrolling body: the toolbar above and the composer below. */
@Immutable
data class ScreenChromeInsets(val top: Dp, val bottom: Dp) {
    companion object {
        val None = ScreenChromeInsets(0.dp, 0.dp)
    }
}

/**
 * The chrome of a secondary screen or a thread: a [toolbar] pinned to the top and an optional [bottomBar] pinned to the
 * bottom, both drawn as real Liquid Glass panes over the body that scrolls beneath them.
 *
 * Why a layout instead of a tint: glass needs the content that is actually behind it. The body is recorded into a
 * backdrop of its own, and the bars are siblings drawn **after** it, so a pane samples the rows scrolling under it and
 * never the layer it is being written into. [content] receives the measured height of each bar and must place it inside
 * its own scrollable (as `contentPadding` or a leading spacer) — that is what lets the rows run under the panes.
 *
 * Every other style, and every quality level that cannot sample (Lite, Balanced without a render effect, an old
 * Android version), keeps the current structure: the toolbar above the body, the composer below it, nothing recorded and
 * no extra work. Only the bars themselves decide whether they paint a fill ([LocalScreenChromeOnGlass]).
 */
@Composable
fun GlassScreenScaffold(
    modifier: Modifier = Modifier,
    toolbar: @Composable () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.(chrome: ScreenChromeInsets) -> Unit
) {
    val design = LocalDesignSystem.current
    val palette = beyGramPalette()
    val records = design.recordsGlassBackdrop
    if (!records) {
        // The structure of every other style: bars in the flow, the body between them. No recording, no extra layout.
        Column(modifier.fillMaxSize()) {
            ChromeSurface(edge = GlassEdge.BottomHairline, onGlass = false, content = toolbar)
            content(ScreenChromeInsets.None)
            if (bottomBar != null) {
                ChromeSurface(
                    edge = GlassEdge.TopHairline,
                    onGlass = false,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
                    content = bottomBar
                )
            }
        }
        return
    }

    val backdrop = rememberGlassBackdrop()
    val density = LocalDensity.current
    var toolbarHeight by remember { mutableIntStateOf(0) }
    var bottomHeight by remember { mutableIntStateOf(0) }
    Box(modifier.fillMaxSize()) {
        // Only the body is recorded. The bars are siblings drawn after it, so they can read this layer safely.
        Column(
            Modifier
                .fillMaxSize()
                .glassBackdropSource(backdrop)
        ) {
            CompositionLocalProvider(
                LocalGlassBackdrop provides null,
                LocalScreenChromeOnGlass provides false
            ) {
                content(
                    ScreenChromeInsets(
                        top = with(density) { toolbarHeight.toDp() },
                        bottom = with(density) { bottomHeight.toDp() }
                    )
                )
            }
        }
        ChromeSurface(
            edge = GlassEdge.BottomHairline,
            onGlass = true,
            backdrop = backdrop,
            tint = palette.surface,
            base = palette.screen,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { toolbarHeight = it.height }
                // The bar's blank space must not let taps through to a row scrolling underneath.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
            content = toolbar
        )
        if (bottomBar != null) {
            ChromeSurface(
                edge = GlassEdge.TopHairline,
                onGlass = true,
                backdrop = backdrop,
                tint = palette.surface,
                base = palette.screen,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { bottomHeight = it.height }
                    // The keyboard moves the composer; the navigation inset is already on the screen root.
                    .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
                content = bottomBar
            )
        }
    }
}

/**
 * One bar of a [GlassScreenScaffold]. In the Liquid Glass style it is a [GlassPanel] with a hairline on the edge that
 * meets the content; every other style, and a device that cannot sample, keeps the bar exactly as it was: the style's
 * own toolbar surface, painted by the bar itself.
 */
@Composable
private fun ChromeSurface(
    edge: GlassEdge,
    onGlass: Boolean,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: com.beykus.beygram.ui.design.GlassBackdrop? = null,
    tint: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    base: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
) {
    val design = LocalDesignSystem.current
    if (!onGlass || !design.usesGlassPanels) {
        CompositionLocalProvider(LocalScreenChromeOnGlass provides false) { content() }
        return
    }
    CompositionLocalProvider(
        LocalGlassBackdrop provides backdrop,
        LocalScreenChromeOnGlass provides true
    ) {
        GlassPanel(
            design = design,
            tint = tint,
            rim = beyGramPalette().textSecondary,
            backdropBase = base,
            shape = RectangleShape,
            edge = edge,
            lensOnFreeEdge = backdrop != null,
            modifier = modifier.fillMaxWidth().zIndex(1f)
        ) {
            content()
        }
    }
}

/** The background of a screen that uses [GlassScreenScaffold], so the Body and the panes agree on the base colour. */
@Composable
fun Modifier.glassChromeScreenBackground(): Modifier {
    val design = LocalDesignSystem.current
    val palette = beyGramPalette()
    return background(palette.screen).glassScreenBackground(design, palette.screen)
}

/**
 * The fill of a bar inside [GlassScreenScaffold]: on the glass pane the bar adds nothing, because the pane is the
 * material; every other style keeps the opaque surface it has always painted.
 */
@Composable
fun Modifier.screenChromeBar(): Modifier =
    if (LocalScreenChromeOnGlass.current) this else this.background(beyGramPalette().surface)

/**
 * The window insets of a bottom bar: on the glass pane the scaffold applies the keyboard and the screen root applies the
 * navigation bar, so a bar that would add them itself must not do it twice.
 */
@Composable
fun Modifier.screenChromeBottomInsets(withIme: Boolean = true): Modifier {
    if (LocalScreenChromeOnGlass.current) return this
    val nav = this.navigationBarsPadding()
    return if (withIme) nav.imePadding() else nav
}