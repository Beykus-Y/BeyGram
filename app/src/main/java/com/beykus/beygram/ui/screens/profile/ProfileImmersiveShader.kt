package com.beykus.beygram.ui.screens.profile

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalContext

private const val IMMERSIVE_EDGE_SHADER_AGSL = """
uniform shader content;
uniform float2 resolution;
uniform float morphAmount;
uniform float stretchAmount;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / max(resolution, float2(1.0));
    float2 centered = uv - float2(0.5, 0.5);
    float dist = length(centered);
    float edgeWarp = morphAmount * 0.012 * sin(dist * 6.28318);
    float pullStretch = stretchAmount * 0.014 * (0.5 - uv.y);
    float2 sampleUv = clamp(uv + centered * edgeWarp + float2(0.0, pullStretch), float2(0.001), float2(0.999));
    return content.eval(sampleUv * resolution);
}
"""

/**
 * Subtle AGSL elastic edge/stretch RenderEffect for Android 13+ (API 33+) strictly during an active
 * morph or pull-down stretch. Stationary states, reduced motion, power-saver, and older APIs return null.
 */
@Composable
internal fun rememberProfileImmersiveShaderEffect(
    morph: Float,
    stretchPx: Float,
    widthPx: Float,
    heightPx: Float,
    reduceMotion: Boolean
): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || reduceMotion) return null
    val activeMorph = if (morph in 0.02f..0.98f) kotlin.math.sin(morph * Math.PI).toFloat() else 0f
    val activeStretch = (stretchPx / 120f).coerceIn(0f, 1f)
    if (activeMorph <= 0.01f && activeStretch <= 0.01f) return null
    if (widthPx < 4f || heightPx < 4f) return null

    val context = LocalContext.current
    val powerSave = remember(context) {
        runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
        }.getOrDefault(false)
    }
    if (powerSave) return null

    val shader = remember {
        runCatching { RuntimeShader(IMMERSIVE_EDGE_SHADER_AGSL) }.getOrNull()
    } ?: return null

    return runCatching {
        shader.setFloatUniform("resolution", widthPx, heightPx)
        shader.setFloatUniform("morphAmount", activeMorph)
        shader.setFloatUniform("stretchAmount", activeStretch)
        RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }.getOrNull()
}
