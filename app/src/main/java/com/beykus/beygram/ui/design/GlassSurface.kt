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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
        layer.record(size.toIntSize()) {
            this@drawWithContent.drawContent()
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
@Composable
fun GlassPanel(
    design: DesignSystem,
    tint: Color,
    rim: Color,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val glass = design.surfaces.glass
    val backdrop = LocalGlassBackdrop.current
    var originInWindow by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    Box(
        modifier
            .onGloballyPositioned { originInWindow = it.positionInWindow() }
            .clip(shape)
    ) {
        if (glass != null && glass.samplesBackdrop && backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurPx = with(density) { glass.blurRadiusDp.toPx() }
            Box(
                Modifier.matchParentSize()
                    .graphicsLayerWithBlur(blurPx, shape)
                    .drawBehind {
                        val source = backdrop.layer ?: return@drawBehind
                        if (!backdrop.hasContent) return@drawBehind
                        val delta = backdrop.originInWindow - originInWindow
                        withTransform({ translate(delta.x, delta.y) }) { drawLayer(source) }
                    }
            )
        }
        // The tint is what makes glass readable: it is always drawn, blurred or not. The base alpha keeps the content
        // behind the panel muted even on hardware that cannot blur, so the labels never fight the list underneath.
        val tintAlpha = glass?.tintAlpha?.coerceIn(0f, 1f) ?: 0f
        val baseAlpha = if (backdrop != null && glass != null && glass.samplesBackdrop) {
            maxOf(tintAlpha, 0.62f)
        } else {
            maxOf(tintAlpha, 0.78f)
        }
        Box(Modifier.matchParentSize().background(tint.copy(alpha = baseAlpha)))
        if (glass != null) {
            Box(
                Modifier.matchParentSize()
                    .border(1.dp, rim.copy(alpha = glass.rimAlpha), shape)
            )
            if (glass.highlightAlpha > 0f) {
                Box(
                    Modifier.matchParentSize().drawWithCache {
                        val brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = glass.highlightAlpha),
                                Color.Transparent
                            ),
                            endY = size.height * 0.62f
                        )
                        onDrawBehind { drawRect(brush) }
                    }
                )
            }
        }
        // Content sizes the panel; the decorative layers above match whatever height it ends up with.
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

/** Render-effect wrapper, kept separate so an unsupported API level simply draws nothing instead of failing. */
private fun Modifier.graphicsLayerWithBlur(radiusPx: Float, shape: RoundedCornerShape): Modifier =
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

/** True when the style draws glass at all (any quality), which decides whether a panel is framed like glass. */
val DesignSystem.usesGlassPanels: Boolean get() = style == DesignStyle.LiquidGlass && surfaces.glass != null
