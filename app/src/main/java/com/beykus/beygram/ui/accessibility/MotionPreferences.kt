package com.beykus.beygram.ui.accessibility

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android has no single public "reduce motion" flag. "Remove animations" in accessibility settings sets the global
 * animator duration scale to zero, and an active screen reader (touch exploration) is treated as a request for less
 * decorative motion. Either signal makes animated emoji and message effects fall back to a static presentation.
 */
internal fun isMotionReduced(animatorDurationScale: Float, touchExplorationEnabled: Boolean): Boolean =
    animatorDurationScale <= 0f || touchExplorationEnabled

/** Reads the system signals once per composition context; a settings toggle still applies without a restart. */
@Composable
internal fun systemReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { readSystemReducedMotion(context) }
}

private fun readSystemReducedMotion(context: Context): Boolean {
    val scale = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    val touchExploration = runCatching {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        manager?.isEnabled == true && manager.isTouchExplorationEnabled
    }.getOrDefault(false)
    return isMotionReduced(scale, touchExploration)
}
