package com.beykus.beygram.ui.accessibility

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/**
 * Android has no single public "reduce motion" flag. "Remove animations" in accessibility settings sets the global
 * animator duration scale to zero, and an active screen reader (touch exploration) is treated as a request for less
 * decorative motion. Either signal makes animated emoji and message effects fall back to a static presentation.
 */
internal fun isMotionReduced(animatorDurationScale: Float, touchExplorationEnabled: Boolean): Boolean =
    animatorDurationScale <= 0f || touchExplorationEnabled

/**
 * The current reduced-motion state, provided once at the app root. Every animated component reads this value instead of
 * registering its own observer: changing the system setting while a chat is open updates the composition immediately.
 */
internal val LocalSystemReducedMotion = staticCompositionLocalOf { false }

/** Kept as a small accessor so existing chat code does not need to know where the value is provided. */
@Composable
internal fun systemReducedMotion(): Boolean = LocalSystemReducedMotion.current

/**
 * Observes both signals behind [isMotionReduced]: the animator duration scale and accessibility touch exploration.
 * It is refreshed on resume and whenever either system setting changes, without polling.
 */
@Composable
internal fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var reduced by remember(context) { mutableStateOf(readSystemReducedMotion(context)) }

    LaunchedEffect(context, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            reduced = readSystemReducedMotion(context)
        }
    }

    DisposableEffect(context) {
        val refresh = { reduced = readSystemReducedMotion(context) }
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val touchListener = AccessibilityManager.TouchExplorationStateChangeListener { refresh() }
        val accessibilityListener = AccessibilityManager.AccessibilityStateChangeListener { refresh() }
        manager?.addTouchExplorationStateChangeListener(touchListener)
        manager?.addAccessibilityStateChangeListener(accessibilityListener)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer
        )
        onDispose {
            manager?.removeTouchExplorationStateChangeListener(touchListener)
            manager?.removeAccessibilityStateChangeListener(accessibilityListener)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    return reduced
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
