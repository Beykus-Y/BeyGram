package com.beykus.beygram.data.appearance

import android.content.Context

private const val GUARD_PREFERENCES = "design_startup"
private const val KEY_RENDERED = "rendered"
private const val KEY_UNFINISHED = "unfinished_starts"

/** How many starts may fail to draw a frame before the saved style is given up. */
internal const val MAX_UNFINISHED_STARTS = 1

/**
 * Protects the app from a design style that crashes it on every start. A start writes a marker before the first frame
 * and clears it after the interface has actually been drawn. If the previous start never got that far, the next start
 * falls back to the Default style and saves it, so the app opens again instead of looping. Nothing else is touched:
 * colours, wallpapers, messages, accounts and the TDLib database stay exactly as they are.
 */
class DesignStartupGuard(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(GUARD_PREFERENCES, Context.MODE_PRIVATE)

    /**
     * Call once per process start, before the saved appearance is applied. Returns true when the previous start never
     * drew a frame, which means the saved design style must not be used again without falling back.
     *
     * An Activity recreation (rotation, theme change, split-screen) is not a new process start and must not count as an
     * unfinished one, so only the first call in a process records the marker.
     */
    fun beginStart(): Boolean {
        if (beganThisProcess) return recoveryRequired
        beganThisProcess = true
        val previousRendered = preferences.getBoolean(KEY_RENDERED, true)
        val previousUnfinished = if (previousRendered) 0 else preferences.getInt(KEY_UNFINISHED, 0)
        preferences.edit()
            .putBoolean(KEY_RENDERED, false)
            .putInt(KEY_UNFINISHED, previousUnfinished + 1)
            .apply()
        recoveryRequired = previousUnfinished >= MAX_UNFINISHED_STARTS
        return recoveryRequired
    }

    /** Call after the recovered value has been stored, so a later recreation does not repeat the recovery. */
    fun markRecovered() {
        recoveryRequired = false
    }

    /** Call after the first frames have been drawn: the style in use is proven to work. */
    fun markRendered() {
        preferences.edit().putBoolean(KEY_RENDERED, true).putInt(KEY_UNFINISHED, 0).apply()
    }

    private companion object {
        /** Reset by the process: an Activity recreation keeps them, a new process starts from them fresh. */
        var beganThisProcess = false
        var recoveryRequired = false
    }
}

/**
 * Whether a start that never drew a frame should give up the saved style. Only a non-Default style needs a fallback:
 * Default is the style the app falls back to, so it can never be the reason to fall back again.
 */
internal fun shouldFallBackToDefault(unfinishedStarts: Int, style: DesignStyle): Boolean =
    unfinishedStarts >= MAX_UNFINISHED_STARTS && style != DesignStyle.Default
