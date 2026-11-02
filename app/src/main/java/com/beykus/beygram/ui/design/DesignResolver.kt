package com.beykus.beygram.ui.design

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.data.appearance.NavigationPlacement

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
 * The capabilities, kept current while the screen is composed: battery saver and the system animation scale can change
 * without the Activity being recreated, and the automatic glass level has to follow them.
 */
@Composable
private fun rememberGlassCapabilities(): GlassCapabilities {
    val context = LocalContext.current
    val capabilities by produceState(glassCapabilities(context), context) {
        val updater = Runnable { value = glassCapabilities(context) }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = updater.run()
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = updater.run()
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer
            )
        }
        awaitDispose {
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
    }
    return capabilities
}

/**
 * The resolved design for a saved appearance choice. It is the single place where the style meets the brightness and
 * the device capabilities; screens read the result from [LocalDesignSystem] and never resolve a style themselves.
 */
@Composable
fun rememberDesign(config: AppearanceConfig): DesignSystem {
    val capabilities = rememberGlassCapabilities()
    return remember(config.designStyle, config.glassQuality, config.navigationPlacement, capabilities) {
        designFor(
            config.designStyle,
            effectiveGlassQuality(config.glassQuality, capabilities),
            config.navigationPlacement
        )
    }
}

/** The design for a style alone, with the quality choice only used by Liquid Glass. */
@Composable
fun rememberDesign(
    style: DesignStyle,
    glassQuality: GlassQuality = GlassQuality.Auto,
    navigationPlacement: NavigationPlacement = NavigationPlacement.Auto
): DesignSystem {
    val capabilities = rememberGlassCapabilities()
    return remember(style, glassQuality, navigationPlacement, capabilities) {
        designFor(
            style,
            effectiveGlassQuality(glassQuality, capabilities),
            navigationPlacement
        )
    }
}
