package com.beykus.beygram.ui.design

import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/*
 * Liquid Glass V2 (experimental): a refracting lens for one glass panel, written for this app from scratch. It is not
 * a port of any other client's glass code. The model is a rounded-box signed distance field: near the edge the panel
 * pulls the sample inward along the surface normal, so content just behind the rim bends, and a thin light is added
 * where the normal faces the light. It runs only on API 33+, only when the style asks for it, and the existing
 * GlassPanel tint, rim and highlight stay on top, so the fallback is the unchanged material.
 */

/** Width of the refracting band along the panel edge. */
internal val LensEdgeThickness = 14.dp

/** Corner radius of the lens; capped at half the shorter side so pill shapes stay round. */
internal val LensMaxCornerRadius = 24.dp

/** How far the edge band pulls the sample inward, in multiples of the band's thickness. */
private const val LensStrength = 0.6f

/** Shader that refracts the already blurred backdrop sample ("img") at the panel edge. */
private val LensShaderSource = """
    uniform shader img;
    uniform float2 size;
    uniform float radius;
    uniform float thickness;
    uniform float strength;

    float sdRoundedBox(float2 p, float2 halfSize, float r) {
        float2 q = abs(p) - halfSize + float2(r, r);
        return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;
    }

    half4 main(float2 coord) {
        float2 halfSize = size * 0.5;
        float2 p = coord - halfSize;
        float d = sdRoundedBox(p, halfSize, radius);
        // Outward normal from the gradient of the distance field (negative inside, positive outside).
        float e = 0.75;
        float dx = sdRoundedBox(p + float2(e, 0.0), halfSize, radius) - sdRoundedBox(p - float2(e, 0.0), halfSize, radius);
        float dy = sdRoundedBox(p + float2(0.0, e), halfSize, radius) - sdRoundedBox(p - float2(0.0, e), halfSize, radius);
        float2 n = normalize(float2(dx, dy) + float2(1e-5, 1e-5));
        // 1 at the edge, 0 once the band ends inside the panel.
        float depth = clamp(-d / thickness, 0.0, 1.0);
        float bevel = 1.0 - depth;
        bevel = bevel * bevel * bevel;
        // Sample from deeper inside the panel: the content at the rim bends towards the middle.
        float2 offset = -n * bevel * strength * thickness;
        half4 c = img.eval(coord + offset);
        // A thin light on the faces that look up-left.
        float facing = max(dot(n, normalize(float2(-0.6, -0.8))), 0.0);
        float light = pow(facing, 3.0) * bevel * 0.35;
        return half4(c.rgb + half3(light), c.a);
    }
""".trimIndent()

/** The lens shader, or null below API 33 where RuntimeShader does not exist. */
internal fun newLensShader(): RuntimeShader? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(LensShaderSource) else null

/**
 * The blurred backdrop of a panel run through the lens. The blur runs first, then the saturation lift, then the
 * refraction, so the lens bends the same sample that the plain High material shows. A panel with no size yet falls back
 * to the plain blur.
 */
internal fun Modifier.graphicsLayerWithLens(
    shader: RuntimeShader,
    sizePx: IntSize,
    thicknessPx: Float,
    maxRadiusPx: Float,
    blurPx: Float,
    shape: Shape,
    darkSurface: Boolean
): Modifier {
    if (sizePx.width <= 0 || sizePx.height <= 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return graphicsLayerWithBlur(blurPx, shape, darkSurface)
    }
    val width = sizePx.width.toFloat()
    val height = sizePx.height.toFloat()
    val radius = minOf(maxRadiusPx, minOf(width, height) / 2f)
    return graphicsLayer {
        clip = true
        this.shape = shape
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("thickness", thicknessPx)
        shader.setFloatUniform("strength", LensStrength)
        val blur = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
        val vibrancy = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(vibrancyMatrix(darkSurface)))
        val lens = RenderEffect.createRuntimeShaderEffect(shader, "img")
        renderEffect = RenderEffect.createChainEffect(
            lens,
            RenderEffect.createChainEffect(vibrancy, blur)
        ).asComposeRenderEffect()
    }
}
