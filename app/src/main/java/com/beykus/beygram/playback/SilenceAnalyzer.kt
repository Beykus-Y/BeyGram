package com.beykus.beygram.playback

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Measures silence at the start and end of downloaded music in the background. Files are measured one at a time,
 * and not while the battery saver is on. Measuring uses no network, because the file is already downloaded. Secret, view-once and streamed media are never measured.
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
        val length = completeFileLength(item) ?: return null
        return SilenceEdgeStore.lookup(cacheKey(item), length)
    }

    /** Lengths of files that are known to be complete. A finished file never changes, so the disk is asked once. */
    private val completeLengths = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun completeFileLength(item: PlaybackItem): Long? {
        if (item.sizeBytes <= 0L) return null
        completeLengths[item.path]?.let { return it.takeIf { length -> length == item.sizeBytes } }
        val length = File(item.path).length()
        if (length != item.sizeBytes) return null
        if (completeLengths.size >= MAX_CACHED_LENGTHS) completeLengths.clear()
        completeLengths[item.path] = length
        return length
    }

    private const val MAX_CACHED_LENGTHS = 512

    /**
     * Measuring decodes a few seconds of a file that is already on the device and uses no network, so it only yields
     * to the battery saver. A signal that cannot be read counts as allowed.
     */
    internal fun allowedInBackground(context: Context): Boolean =
        !runCatching { context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true }.getOrDefault(false)
}
