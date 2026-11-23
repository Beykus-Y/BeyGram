package com.beykus.beygram.perf

import android.content.Intent
import android.os.Trace
import android.util.Log
import com.beykus.beygram.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PROF-only diagnostic A/B switches for issue #1109 (chats <-> chat navigation jank).
 * Every flag defaults to false so default UX is completely unchanged.
 *
 * Non-prof builds never activate any toggle.
 */
data class ProfAbFlags(
    val navInstant: Boolean = false,
    val bubbleNoIntrinsic: Boolean = false,
    val glassChromeOnly: Boolean = false,
    val underChromeBypass: Boolean = false,
    val keepTabsMounted: Boolean = false
) {
    val glassBypass: Boolean get() = glassChromeOnly && underChromeBypass
}

object ProfDiagnostics {
    private const val TAG = "ProfDiagnostics"
    private const val EXTRA_RESET = "prof_reset"
    private const val EXTRA_NAV_INSTANT = "prof_nav_instant"
    private const val EXTRA_BUBBLE_NO_INTRINSIC = "prof_bubble_no_intrinsic"
    private const val EXTRA_GLASS_BYPASS = "prof_glass_bypass"
    private const val EXTRA_GLASS_CHROME_ONLY = "prof_glass_chrome_only"
    private const val EXTRA_UNDER_CHROME_BYPASS = "prof_under_chrome_bypass"
    private const val EXTRA_KEEP_TABS_MOUNTED = "prof_keep_tabs_mounted"

    val isProfBuild: Boolean = BuildConfig.APPLICATION_ID == "com.beykus.beygram.prof"

    private val _flags = MutableStateFlow(ProfAbFlags())
    val flags: StateFlow<ProfAbFlags> = _flags.asStateFlow()

    @Volatile
    var navInstant: Boolean = false
        private set

    @Volatile
    var bubbleNoIntrinsic: Boolean = false
        private set

    @Volatile
    var glassChromeOnly: Boolean = false
        private set

    @Volatile
    var underChromeBypass: Boolean = false
        private set

    @Volatile
    var keepTabsMounted: Boolean = false
        private set

    val glassBypass: Boolean get() = glassChromeOnly && underChromeBypass

    fun update(newFlags: ProfAbFlags) {
        if (!isProfBuild) return
        navInstant = newFlags.navInstant
        bubbleNoIntrinsic = newFlags.bubbleNoIntrinsic
        glassChromeOnly = newFlags.glassChromeOnly
        underChromeBypass = newFlags.underChromeBypass
        keepTabsMounted = newFlags.keepTabsMounted
        _flags.value = newFlags
        runCatching {
            Log.i(TAG, "version=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) flags=$newFlags")
        }
    }

    fun applyIntent(intent: Intent?) {
        if (!isProfBuild || intent == null) return
        val extras = intent.extras ?: return
        val hasAny = extras.containsKey(EXTRA_RESET) ||
            extras.containsKey(EXTRA_NAV_INSTANT) ||
            extras.containsKey(EXTRA_BUBBLE_NO_INTRINSIC) ||
            extras.containsKey(EXTRA_GLASS_BYPASS) ||
            extras.containsKey(EXTRA_GLASS_CHROME_ONLY) ||
            extras.containsKey(EXTRA_UNDER_CHROME_BYPASS) ||
            extras.containsKey(EXTRA_KEEP_TABS_MOUNTED)
        if (!hasAny) return

        val base = if (extras.getBoolean(EXTRA_RESET, false)) ProfAbFlags() else _flags.value
        val combinedGlass = if (extras.containsKey(EXTRA_GLASS_BYPASS)) extras.getBoolean(EXTRA_GLASS_BYPASS, false) else null
        val next = base.copy(
            navInstant = if (extras.containsKey(EXTRA_NAV_INSTANT)) extras.getBoolean(EXTRA_NAV_INSTANT, false) else base.navInstant,
            bubbleNoIntrinsic = if (extras.containsKey(EXTRA_BUBBLE_NO_INTRINSIC)) extras.getBoolean(EXTRA_BUBBLE_NO_INTRINSIC, false) else base.bubbleNoIntrinsic,
            glassChromeOnly = when {
                extras.containsKey(EXTRA_GLASS_CHROME_ONLY) -> extras.getBoolean(EXTRA_GLASS_CHROME_ONLY, false)
                combinedGlass != null -> combinedGlass
                else -> base.glassChromeOnly
            },
            underChromeBypass = when {
                extras.containsKey(EXTRA_UNDER_CHROME_BYPASS) -> extras.getBoolean(EXTRA_UNDER_CHROME_BYPASS, false)
                combinedGlass != null -> combinedGlass
                else -> base.underChromeBypass
            },
            keepTabsMounted = if (extras.containsKey(EXTRA_KEEP_TABS_MOUNTED)) extras.getBoolean(EXTRA_KEEP_TABS_MOUNTED, false) else base.keepTabsMounted
        )
        update(next)
    }

    inline fun <T> traceSection(name: String, block: () -> T): T {
        if (!isProfBuild) return block()
        val traced = try {
            Trace.beginSection(name)
            true
        } catch (_: RuntimeException) {
            false
        }
        return try {
            block()
        } finally {
            if (traced) {
                try {
                    Trace.endSection()
                } catch (_: RuntimeException) {
                }
            }
        }
    }
}
