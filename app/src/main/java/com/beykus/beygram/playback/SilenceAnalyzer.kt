package com.beykus.beygram.playback

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.beykus.beygram.data.media.AutoDownloadNetwork
import com.beykus.beygram.data.media.currentAutoDownloadNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Measures silence at the start and end of downloaded music in the background. Files are measured one at a time,
 * and only while the device is on Wi-Fi or charging. Secret, view-once and streamed media are never measured.
 * Every file is measured once and then answered from [SilenceEdgeStore].
 */
internal object SilenceAnalyzer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** One measurement at a time, so requests from quick track changes never decode in parallel. */
    private val gate = Mutex()

    /** Measures the items that are complete public music files and have no stored result yet. Never blocks the caller. */
    fun request(context: Context, items: List<PlaybackItem>) {
        val app = context.applicationContext
        val candidates = items.filter { isMeasurable(it) }
        if (candidates.isEmpty()) return
        scope.launch {
            gate.withLock {
                SilenceEdgeStore.warm(app)
                for (item in candidates) {
                    val file = File(item.path)
                    if (!file.isFile) continue
                    val key = cacheKey(item)
                    val length = file.length()
                    // Only the complete file: a partial download has fewer bytes than the message says it has.
                    if (item.sizeBytes <= 0L || length != item.sizeBytes) continue
                    if (SilenceEdgeStore.lookup(key, length) != null) continue
                    // A closed gate only postpones the work: the next queue change asks again.
                    if (!allowedInBackground(app)) return@launch
                    // A null result is a failed or timed-out decode. It is not stored, so a later request tries again.
                    val edges = SilenceEdgeDecoder.analyze(file.path) ?: continue
                    SilenceEdgeStore.put(app, key, edges, length, System.currentTimeMillis())
                }
            }
        }
    }

    /** Only complete music from a Telegram file with a stable id. Secret and view-once media are excluded. */
    internal fun isMeasurable(item: PlaybackItem): Boolean =
        item.isMusic &&
            !item.systemPrivate &&
            item.growing == null &&
            item.path.isNotBlank() &&
            item.fileId != null

    internal fun cacheKey(item: PlaybackItem): String = "${item.accountId}:${item.fileId}"

    /**
     * The stored measurement of a complete file. Null when the item is not measurable, the file is partial or changed,
     * or it has not been measured yet. Reads only memory and one file length, so it is cheap enough for playback.
     */
    fun cachedEdges(item: PlaybackItem): SilenceEdges? {
        if (!isMeasurable(item)) return null
        val length = File(item.path).length()
        if (item.sizeBytes <= 0L || length != item.sizeBytes) return null
        return SilenceEdgeStore.lookup(cacheKey(item), length)
    }

    /** Wi-Fi or charging. A signal that cannot be read counts as absent. */
    internal fun allowedInBackground(context: Context): Boolean {
        val network = runCatching { currentAutoDownloadNetwork(context) }.getOrDefault(AutoDownloadNetwork.None)
        if (network == AutoDownloadNetwork.Wifi || network == AutoDownloadNetwork.WifiMetered) return true
        return isCharging(context)
    }

    private fun isCharging(context: Context): Boolean {
        val status = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        }.getOrNull() ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }
}
