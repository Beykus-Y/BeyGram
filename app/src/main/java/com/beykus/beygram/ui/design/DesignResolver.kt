package com.beykus.beygram.ui.design

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality

/** Backdrop blur needs Android 12 (API 31) render effects. Older versions get the tinted glass without the blur pass. */
private val SystemBackdropBlurAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Reads what this device and the current system settings allow the glass material to do. Everything here is a
 * capability query, never a preference: the user's own quality choice is applied on top of the result.
 */
fun glassCapabilities(context: Context): GlassCapabilities {
    val powerManager = context.getSystemService(PowerManager::class.java)
    val activityManager = context.getSystemService(ActivityManager::class.java)
    val animationsOff = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE) == 0f
    }.getOrDefault(false)
    return GlassCapabilities(
        supportsBackdropBlur = SystemBackdropBlurAvailable,
        reducedTransparency = animationsOff || !SystemBackdropBlurAvailable,
        constrainedPerformance = powerManager?.isPowerSaveMode == true || activityManager?.isLowRamDevice == true
    )
}

/**
 * The resolved design for a saved appearance choice. It is the single place where the style meets the brightness and
 * the device capabilities; screens read the result from [LocalDesignSystem] and never resolve a style themselves.
 */
@Composable
fun rememberDesign(config: AppearanceConfig): DesignSystem {
    val context = LocalContext.current
    return remember(config.designStyle, config.glassQuality, context) {
        designFor(config.designStyle, resolveGlassQuality(config.glassQuality, autoGlassQuality(glassCapabilities(context))))
    }
}

/** The design for a style alone, with the quality choice only used by Liquid Glass. */
@Composable
fun rememberDesign(style: DesignStyle, glassQuality: GlassQuality = GlassQuality.Auto): DesignSystem {
    val context = LocalContext.current
    return remember(style, glassQuality, context) {
        designFor(style, resolveGlassQuality(glassQuality, autoGlassQuality(glassCapabilities(context))))
    }
}
