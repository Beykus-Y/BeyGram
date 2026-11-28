package com.beykus.beygram.data.media

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * The performance tier of the process, kept current while the app runs.
 *
 * [detectMediaPerformanceTier] treats battery saver as the low tier, but the emoji scheduler and the GIF budget used to
 * read it once, when their process-wide instance was created: turning battery saver on or off afterwards left the old
 * budget and frame rate in place until the process was killed. The tier is now recomputed on
 * [PowerManager.ACTION_POWER_SAVE_MODE_CHANGED] and every consumer follows it.
 *
 * The device facts (low-RAM flag, memory class, core count) do not change for a running process, so only the power
 * state is followed.
 */
internal class MediaPerformanceTierSource(
    private val isLowRam: Boolean,
    private val memoryClassMb: Int,
    private val cpuCores: Int,
    powerSaveMode: Boolean,
    private val onChanged: (MediaPerformanceTier) -> Unit = {}
) {
    @Volatile
    var tier: MediaPerformanceTier = tierFor(powerSaveMode)
        private set

    private var receiver: BroadcastReceiver? = null

    /**
     * Applies a new power-save state and reports the tier when it actually changed. Returns the current tier, so a
     * caller can use the result without reading [tier] again.
     */
    fun updatePowerSaveMode(powerSaveMode: Boolean): MediaPerformanceTier {
        val next = tierFor(powerSaveMode)
        val previous = tier
        if (next != previous) {
            tier = next
            onChanged(next)
        }
        return tier
    }

    /**
     * Follows battery saver for the rest of the process. Registering twice is a no-op, so the singletons that own a
     * source can call this on every `default(context)`. Returns whether this call registered the receiver.
     *
     * The receiver is never unregistered: it belongs to a process-wide object that lives as long as the process, and it
     * ignores the intent it receives beyond re-reading the current state.
     */
    fun follow(context: Context, powerManager: PowerManager?): Boolean {
        if (receiver != null) return false
        val application = context.applicationContext ?: return false
        val created = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updatePowerSaveMode(powerManager?.isPowerSaveMode == true)
            }
        }
        val registered = runCatching {
            // The same registration the design system uses for this broadcast: a protected system signal, never
            // exported, and a no-op failure (a preview or a test context without a receiver registry).
            ContextCompat.registerReceiver(
                application,
                created,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }.isSuccess
        if (registered) receiver = created
        return registered
    }

    private fun tierFor(powerSaveMode: Boolean): MediaPerformanceTier = detectMediaPerformanceTier(
        isLowRam = isLowRam,
        memoryClassMb = memoryClassMb,
        cpuCores = cpuCores,
        powerSaveMode = powerSaveMode
    )

    companion object {
        /** Builds a source from the running device. */
        fun of(context: Context, onChanged: (MediaPerformanceTier) -> Unit = {}): MediaPerformanceTierSource {
            val application = context.applicationContext ?: context
            val activityManager = application.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val powerManager = application.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return MediaPerformanceTierSource(
                isLowRam = activityManager?.isLowRamDevice == true,
                memoryClassMb = activityManager?.memoryClass ?: 0,
                cpuCores = Runtime.getRuntime().availableProcessors(),
                powerSaveMode = powerManager?.isPowerSaveMode == true,
                onChanged = onChanged
            ).also { it.follow(application, powerManager) }
        }
    }
}

/** The number of GIF / animated-WebP tiles that may animate at once in a tier. */
internal fun maxConcurrentGifAnimations(tier: MediaPerformanceTier): Int = when (tier) {
    MediaPerformanceTier.Low -> 2
    MediaPerformanceTier.Mid -> 3
    MediaPerformanceTier.High -> 4
}