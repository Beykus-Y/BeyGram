package com.beykus.beygram.ui.design

import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/*
 * Liquid Glass V2 (experimental): a refracting lens for one floating glass panel, written for this app from scratch.
 * It is not a port of any other client's glass code; only the optics are modelled (a circular bevel and Snell
 * refraction with an index of 1.5), the constants are this app's own and need tuning on a device.
 *
 * Per pixel near the rim the shader takes the distance to the panel outline (per-corner radii from the real shape),
 * derives the bevel surface normal, refracts the view ray through it and samples the blurred backdrop along the
 * refracted direction. A thin highlight (on dark surfaces) or a soft shade (on light ones) marks the bevel.
 * The panel's tint, rim and highlight stay on top, and every failure falls back to the plain blur.
 */

/**
 * The tunable constants of the lens, in one place. The defaults are starting values chosen from the optics, not
 * measured on a device: they are meant to be adjusted on hardware (see the manual check in the PR).
 */
data class LensTuning(
    /** Width of the refracting band along the edge, capped by a fifth of the panel's shorter side. */
    val thicknessDp: Dp = 22.dp,
    /** How far the refracted ray travels inside the glass, as a multiple of the band width. Higher bends content more. */
    val strength: Float = 0.9f,
    /** Refraction index of the glass; the ray enters air-to-glass with eta = 1 / index. */
    val refractionIndex: Float = 1.5f,
    /**
     * Blur of the lens path as a fraction of the style's blur. The refraction bends structure, so the lens reads the
     * sharp layer and only the output is softened; a full blur first would leave nothing to bend.
     */
    val blurScale: Float = 0.5f
)

internal val DefaultLensTuning = LensTuning()

/**
 * Shader that refracts the already blurred backdrop sample ("img") at the panel edge.
 * Uniforms: size (px), radii (tl, tr, br, bl in px), thickness (px), strength, lightSurface (1 on light surfaces).
 */
private val LensShaderSource = """
    uniform shader img;
    uniform float2 size;
    uniform float4 radii;
    uniform float thickness;
    uniform float strength;
    uniform float eta;
    uniform float lightSurface;

    float sdRoundedBox(float2 p, float2 halfSize, float4 r) {
        // Per-corner radius picked by the quadrant of p: top-left, top-right, bottom-right, bottom-left.
        float rr = p.y < 0.0 ? (p.x < 0.0 ? r.x : r.y) : (p.x < 0.0 ? r.w : r.z);
        float2 q = abs(p) - halfSize + float2(rr, rr);
        return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - rr;
    }

    half4 main(float2 coord) {
        float2 halfSize = size * 0.5;
        float2 p = coord - halfSize;
        float sd = min(sdRoundedBox(p, halfSize, radii), 0.0);
        // 0 at the rim, 1 where the bevel ends, deeper inside the panel the lens does nothing.
        float depth = clamp(-sd / thickness, 0.0, 1.0);
        if (depth >= 1.0) {
            return img.eval(coord);
        }
        // Outward direction of the outline from central differences of the distance field.
        float e = 0.5;
        float gx = sdRoundedBox(p + float2(e, 0.0), halfSize, radii) - sdRoundedBox(p - float2(e, 0.0), halfSize, radii);
        float gy = sdRoundedBox(p + float2(0.0, e), halfSize, radii) - sdRoundedBox(p - float2(0.0, e), halfSize, radii);
        float2 outDir = normalize(float2(gx, gy) + float2(1e-5, 1e-5));

        // Circular bevel: the in-plane part of the surface normal fades from 1 at the rim to 0 at the bevel end.
        float nCos = 1.0 - depth;
        float nSin = sqrt(max(1.0 - nCos * nCos, 0.0));
        float3 normal = float3(outDir * nCos, nSin);
        // Height of the bevel above the flat glass, used for the path length through it.
        float height = thickness * sqrt(max(depth * (2.0 - depth), 0.0));

        // Snell refraction of the view ray (0, 0, -1) through the bevel normal.
        float3 incident = float3(0.0, 0.0, -1.0);
        float cosI = dot(normal, incident);
        float k = 1.0 - eta * eta * (1.0 - cosI * cosI);
        float3 refracted = eta * incident - (eta * cosI + sqrt(max(k, 0.0))) * normal;
        float travel = (height + thickness) / max(-refracted.z, 0.2);
        float2 sampleAt = clamp(coord + refracted.xy * travel * strength, float2(0.5, 0.5), size - float2(0.5, 0.5));
        half4 c = img.eval(sampleAt);

        // Light on the faces that look up-left: a highlight on dark glass, a soft shade on light glass.
        float2 lightDir = normalize(float2(-0.6, -0.8));
        float facingLight = pow(max(dot(outDir, lightDir), 0.0), 3.0) * nCos * nCos;
        float facingShade = pow(max(dot(outDir, -lightDir), 0.0), 3.0) * nCos * nCos;
        float highlight = facingLight * mix(0.22, 0.10, lightSurface);
        float shade = facingShade * 0.12 * lightSurface;
        half3 rgb = clamp(c.rgb + half3(half(highlight - shade)), half3(0.0), half3(c.a));
        return half4(rgb, c.a);
    }
""".trimIndent()

/**
 * Whether the driver accepts the shader source, checked once per process. Only this check is shared: every panel still
 * creates its own RuntimeShader, because its uniforms (size, radii) belong to that panel.
 */
private val lensShaderCompiles: Boolean by lazy {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@lazy false
    try {
        RuntimeShader(LensShaderSource)
        true
    } catch (error: RuntimeException) {
        Log.w(LiquidGlassMetrics.LogTag, "Lens shader does not compile on this driver; plain blur is used", error)
        LiquidGlassMetrics.recordCompileFailure()
        false
    }
}

/**
 * A lens shader for one panel, or null when the device cannot run it. Called only for panels that really sample the
 * backdrop, so a style that never draws the lens never creates one.
 */
internal fun newLensShader(): RuntimeShader? {
    if (!lensShaderCompiles) return null
    return try {
        LiquidGlassMetrics.recordShaderCreated()
        RuntimeShader(LensShaderSource)
    } catch (error: RuntimeException) {
        LiquidGlassMetrics.recordCompileFailure()
        null
    }
}

/**
 * The render effect of one lens panel: the blur runs first, then the saturation and brightness clamp, then the
 * refraction, so the lens bends the same sample the plain High material shows. Returns null (plain blur) for a panel
 * with no size yet or when the effect cannot be built; the caller keeps the blur then.
 */
internal fun buildLensRenderEffect(
    shader: RuntimeShader,
    sizePx: IntSize,
    shape: Shape,
    thicknessPx: Float,
    blurPx: Float,
    darkSurface: Boolean,
    density: Density,
    layoutDirection: LayoutDirection,
    tuning: LensTuning
): RenderEffect? {
    if (sizePx.width <= 0 || sizePx.height <= 0) return null
    val size = Size(sizePx.width.toFloat(), sizePx.height.toFloat())
    val radii = lensCornerRadii(shape, size, density, layoutDirection)
    val thickness = lensThicknessPx(thicknessPx, size)
    return try {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("radii", radii.topLeft, radii.topRight, radii.bottomRight, radii.bottomLeft)
        shader.setFloatUniform("thickness", thickness)
        shader.setFloatUniform("strength", tuning.strength)
        shader.setFloatUniform("eta", 1f / tuning.refractionIndex)
        shader.setFloatUniform("lightSurface", if (darkSurface) 0f else 1f)
        // Order: the lens reads the sharp layer first, then the output is blurred and lifted in saturation, so the
        // bent edges stay visible under the softened glass.
        val blur = RenderEffect.createBlurEffect(blurPx * tuning.blurScale, blurPx * tuning.blurScale, Shader.TileMode.CLAMP)
        val vibrancy = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(vibrancyMatrix(darkSurface)))
        val lens = RenderEffect.createRuntimeShaderEffect(shader, "img")
        LiquidGlassMetrics.recordLensBuilt()
        RenderEffect.createChainEffect(RenderEffect.createChainEffect(vibrancy, blur), lens)
    } catch (error: RuntimeException) {
        LiquidGlassMetrics.recordCompileFailure()
        null
    }
}
