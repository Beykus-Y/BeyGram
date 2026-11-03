package com.beykus.beygram.ui.design

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import com.beykus.beygram.data.appearance.DesignStyle

/**
 * The content a glass panel can sample. Whatever is drawn inside [Modifier.glassBackdropSource] is recorded into an
 * offscreen layer every frame; a [GlassPanel] draws that layer, offset to its own position, behind its own tint.
 * This is a real backdrop sample: blurring an element's own children (Modifier.blur) would only smear the panel.
 */
@Stable
class GlassBackdrop {
    internal var layer: GraphicsLayer? = null
    internal var originInWindow: Offset = Offset.Zero
    internal var hasContent: Boolean = false

    /**
     * True while [glassBackdropSource] is recording this frame. A panel inside the recorded subtree would ask the very
     * layer that is being written to be drawn into itself, which is a native crash in the render thread, so the panel
     * falls back to its tint while this is set. It is also the guard that keeps a nested panel from ever doing that.
     */
    internal var recording: Boolean = false
}

/** The backdrop of the current screen, provided next to the content that should be visible through glass. */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** Creates the recorder of one screen's backdrop. */
@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) {
        GlassBackdrop().apply { this.layer = layer }
    }
}

/**
 * Records everything drawn inside this modifier into the backdrop of [backdrop]. Place it on the scrolling content, not
 * on the panel itself, so the panel samples the content behind it and never itself.
 */
fun Modifier.glassBackdropSource(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { coordinates ->
        backdrop.originInWindow = coordinates.positionInWindow()
    }
    .drawWithContent {
        val layer = backdrop.layer
        if (layer == null) {
            drawContent()
            return@drawWithContent
        }
        // Record this frame's content once and composite the recorded layer: the glass panels above read it, and the
        // on-screen result stays exactly the same content that was recorded.
        // `recording` makes a panel inside this subtree skip sampling: drawing a layer into itself crashes the
        // render thread natively, so the guard is what makes the whole material safe.
        backdrop.recording = true
        try {
            layer.record(size.toIntSize()) {
                this@drawWithContent.drawContent()
            }
        } finally {
            backdrop.recording = false
        }
        drawLayer(layer)
        backdrop.hasContent = true
    }

/**
 * A translucent panel with depth. With [GlassStyle.samplesBackdrop] it draws the recorded backdrop blurred by a real
 * render effect, then a tint that keeps text readable over any photo, a light rim and a specular highlight. Without it
 * (Balanced, Lite, an old Android version or reduced transparency) the same panel is drawn with a denser tint and no
 * blur pass, so the interface never depends on a hardware effect being available.
 */
/** How a pane of glass meets what is around it. */
enum class GlassEdge {
    /** A floating pane: one light rim around the whole shape. */
    Rim,
    /** Full-width chrome at the top of a screen: square window edges and one hairline where it meets the content. */
    BottomHairline
}

@Composable
fun GlassPanel(
    design: DesignSystem,
    tint: Color,
    rim: Color,
    /**
     * The colour under the blurred sample, drawn opaque first. Unspecified means the tint at full opacity. A screen whose
     * list has no background of its own passes its screen colour here, so no sharp content shows through the pane.
     */
    backdropBase: Color = Color.Unspecified,
    shape: Shape,
    modifier: Modifier = Modifier,
    edge: GlassEdge = GlassEdge.Rim,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val glass = design.surfaces.glass
    val backdrop = LocalGlassBackdrop.current
    var originInWindow by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    // The panel is always composed with its sampling layer when the style asks for it; whether the backdrop is ready
    // (or is being written right now) is decided at draw time, because both flags change during the frame and a plain
    // Boolean does not invalidate composition.
    val samplesBackdrop = glass != null && glass.samplesBackdrop &&
        backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(
        modifier
            .onGloballyPositioned { originInWindow = it.positionInWindow() }
            .clip(shape)
    ) {
        if (samplesBackdrop) {
            val blurPx = with(density) { glass!!.blurRadiusDp.toPx() }
            // The opaque floor of the sampled pane: a list without its own background records transparent gaps, and
            // those must show this colour, not the sharp content drawn underneath.
            val base = if (backdropBase != Color.Unspecified) backdropBase else tint.copy(alpha = 1f)
            Box(
                Modifier.matchParentSize()
                    .graphicsLayerWithBlur(blurPx, shape)
                    .drawBehind {
                        drawRect(base)
                        val sampled = backdrop
                        val source = sampled?.layer
                        // Sampling the layer that is being recorded right now would draw it into itself, which kills
                        // the render thread; a frame without a finished sample gets a dense mask instead, so the
                        // lighter tint of real glass never leaves sharp content showing through the panel.
                        if (sampled == null || source == null || !sampled.hasContent || sampled.recording) {
                            drawRect(tint.copy(alpha = GlassUnsampledMaskAlpha))
                            return@drawBehind
                        }
                        val delta = sampled.originInWindow - originInWindow
                        withTransform({ translate(delta.x, delta.y) }) { drawLayer(source) }
                    }
            )
        }
        // The tint is what makes glass readable: it is always drawn, blurred or not. Over a real blurred sample the
        // style's own tint is enough; without one the panel stays dense, so labels never fight sharp content below.
        val tintAlpha = glass?.tintAlpha?.coerceIn(0f, 1f) ?: 0f
        val baseAlpha = if (samplesBackdrop) tintAlpha else maxOf(tintAlpha, 0.93f)
        Box(Modifier.matchParentSize().background(tint.copy(alpha = baseAlpha)))
        if (glass != null) {
            // One light edge, brightest where the light falls and fading towards the bottom. A uniform frame plus a
            // heavy shadow read as a plastic tray rather than a pane of glass.
            if (edge == GlassEdge.BottomHairline) Box(
                Modifier.matchParentSize().drawBehind {
                    val stroke = 1.dp.toPx()
                    drawRect(
                        color = rim.copy(alpha = glass.rimAlpha * 0.6f),
                        topLeft = Offset(0f, size.height - stroke),
                        size = Size(size.width, stroke)
                    )
                }
            ) else Box(
                // The one outer stroke of a floating pane: thin and low contrast, so the pane reads as glass without a
                // bold frame. The light inside (the highlight above) is the second layer and carries no stroke of its own.
                Modifier.matchParentSize().border(width = 1.dp, brush = glassOutlineBrush(glass, rim), shape = shape)
            )
            if (glass.highlightAlpha > 0f) {
                Box(Modifier.matchParentSize().glassHighlight(glass.highlightAlpha))
            }
        }
        // Content sizes the panel; the decorative layers above match whatever height it ends up with.
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

/** Render-effect wrapper, kept separate so an unsupported API level simply draws nothing instead of failing. */
private fun Modifier.graphicsLayerWithBlur(radiusPx: Float, shape: Shape): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        graphicsLayer {
            clip = true
            this.shape = shape
            renderEffect = RenderEffect
                .createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
    } else {
        this
    }

/**
 * The specular highlight of the glass: a soft white wash across the top that fades out before the middle. Shared by the
 * floating panel and the full-width chrome so both read as the same material.
 */
fun Modifier.glassHighlight(alpha: Float): Modifier = drawWithCache {
    val brush = Brush.verticalGradient(
        colors = listOf(Color.White.copy(alpha = alpha), Color.Transparent),
        endY = size.height * 0.62f
    )
    onDrawBehind { drawRect(brush) }
}

/**
 * Background of a screen in the Liquid Glass style: the screen colour with two large soft glows of the accent, so
 * translucent cards and chrome above it read as glass. Other styles get the plain screen colour.
 */
fun Modifier.glassScreenBackground(design: DesignSystem, screen: Color, accent: Color): Modifier =
    if (!design.usesGlassPanels) background(screen) else drawWithCache {
        val glowA = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0f)),
            center = Offset(size.width * 0.12f, size.height * 0.10f),
            radius = size.maxDimension * 0.55f
        )
        val glowB = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.20f), accent.copy(alpha = 0f)),
            center = Offset(size.width * 0.95f, size.height * 0.62f),
            radius = size.maxDimension * 0.50f
        )
        onDrawBehind {
            drawRect(screen)
            drawRect(glowA)
            drawRect(glowB)
        }
    }

/**
 * Fill and edge of a grouped card (settings group, archive row) or tile in the Liquid Glass style. A list card is a
 * subtle tint over the ambient glow with one thin outer stroke. It has no specular gloss and no bold rim: a gradient
 * inside a card on black reads as glossy plastic, and the rows inside keep their own fill without strokes.
 */
fun Modifier.glassCard(design: DesignSystem, fill: Color, rim: Color, shape: Shape): Modifier {
    val glass = design.surfaces.glass
    if (!design.usesGlassPanels || glass == null) return clip(shape).background(fill)
    return clip(shape)
        .background(fill.copy(alpha = GlassCardFillAlpha))
        .border(width = 1.dp, brush = glassOutlineBrush(glass, rim), shape = shape)
}

/**
 * The outer stroke of a floating pane or a list card: [rim] at the style's top alpha, fading to its bottom alpha. The
 * caller passes the palette's secondary text colour, which is light on dark themes (white-ish on AMOLED) and dark on
 * light ones, so the stroke stays low contrast on both.
 */
private fun glassOutlineBrush(glass: GlassStyle, rim: Color): Brush = Brush.verticalGradient(
    listOf(rim.copy(alpha = glass.outlineTopAlpha), rim.copy(alpha = glass.outlineBottomAlpha))
)

/** How much of the surface colour a glass card keeps: the ambient glow behind it must show through. */
private const val GlassCardFillAlpha = 0.55f

/**
 * A panel that slides up from the bottom (attachment menu, sheets) in the Liquid Glass style: a lit top edge and a
 * highlight. The fill stays dense, because the messages behind a menu must not be readable through it.
 */
fun Modifier.glassSheet(design: DesignSystem, fill: Color, rim: Color): Modifier {
    val glass = design.surfaces.glass
    if (!design.usesGlassPanels || glass == null) return background(fill)
    return background(fill.copy(alpha = 0.94f))
        .glassHighlight(glass.highlightAlpha)
        .drawBehind {
            val stroke = 1.dp.toPx()
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(rim.copy(alpha = 0f), rim.copy(alpha = glass.rimAlpha), rim.copy(alpha = 0f))
                ),
                size = Size(size.width, stroke)
            )
        }
}

/** Mask used for a frame in which the panel has no finished backdrop sample yet. */
private const val GlassUnsampledMaskAlpha = 0.65f

/**
 * True when glass panels on this device really sample a backdrop. Only then is recording the content worth its cost:
 * Balanced, Lite, other styles and Android versions without render effects never record anything.
 */
val DesignSystem.recordsGlassBackdrop: Boolean
    get() = usesGlass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** True when the style draws glass at all (any quality), which decides whether a panel is framed like glass. */
val DesignSystem.usesGlassPanels: Boolean get() = style == DesignStyle.LiquidGlass && surfaces.glass != null
