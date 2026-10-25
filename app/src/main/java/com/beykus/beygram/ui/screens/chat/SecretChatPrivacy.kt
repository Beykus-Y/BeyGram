package com.beykus.beygram.ui.screens.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import java.util.WeakHashMap

/**
 * Sets FLAG_SECURE while secret content is visible. This keeps secret chat screens out of Recents screenshots and
 * screen recordings. It cannot stop a second camera or a modified OS; it only removes the ordinary Android capture path.
 * The flag is reference-counted per window because a secret chat, a playing video note and the media viewer can overlap.
 */
@Composable
internal fun SecureWindowWhileVisible(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        if (!enabled) return@DisposableEffect onDispose {}
        val window = view.context.hostActivity()?.window
        if (window == null) return@DisposableEffect onDispose {}
        SecureWindowFlags.acquire(window)
        onDispose { SecureWindowFlags.release(window) }
    }
}

internal object SecureWindowFlags {
    private val holds = WeakHashMap<Window, Int>()

    fun acquire(window: Window) = synchronized(this) {
        val count = holds[window] ?: 0
        if (count == 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        holds[window] = count + 1
    }

    fun release(window: Window) = synchronized(this) {
        val count = holds[window] ?: return@synchronized
        if (count <= 1) {
            holds.remove(window)
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            holds[window] = count - 1
        }
    }
}

private fun Context.hostActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
