package com.beykus.beygram.data.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Who started the transfer: the automatic policy or the user's Download button. */
internal enum class TrafficOrigin { Auto, Manual }

/**
 * Immutable traffic counters for one account during the current app process.
 * [activeOrigins] maps a file id to the origin of its running transfer; finished transfers are removed,
 * so the map never grows with old media.
 */
internal data class TrafficUsageSnapshot(
    val autoBytes: Long = 0L,
    val manualBytes: Long = 0L,
    val activeOrigins: Map<Int, TrafficOrigin> = emptyMap()
) {
    val totalBytes: Long get() = autoBytes + manualBytes
    val autoActive: Int get() = activeOrigins.values.count { it == TrafficOrigin.Auto }
    val manualActive: Int get() = activeOrigins.values.count { it == TrafficOrigin.Manual }
    val hasActiveDownloads: Boolean get() = activeOrigins.isNotEmpty()
}

/**
 * Pure, thread-safe byte accounting. Progress callbacks from TDLib repeat values and may arrive after
 * a retry, so each attempt only counts bytes above the highest value it has already seen. A manual tap
 * that takes over an automatic transfer keeps the bytes already counted as automatic and attributes
 * only later bytes to the manual total.
 */
internal class TrafficUsageAccumulator {
    private data class Transfer(var origin: TrafficOrigin, var highWater: Long)

    private val transfers = LinkedHashMap<Int, Transfer>()
    private var autoBytes = 0L
    private var manualBytes = 0L

    /**
     * Starts a new attempt. [alreadyDownloadedBytes] is TDLib's last known size for this file: a resumed
     * transfer starts above it and must not be counted again, while progress below it is ignored.
     */
    @Synchronized
    fun begin(fileId: Int, origin: TrafficOrigin, alreadyDownloadedBytes: Long = 0L): TrafficUsageSnapshot {
        val existing = transfers[fileId]
        if (existing == null) {
            transfers[fileId] = Transfer(origin, alreadyDownloadedBytes.coerceAtLeast(0L))
        } else if (origin == TrafficOrigin.Manual) {
            // A manual tap takes ownership of a transfer that is already running, but not the bytes
            // that have already been counted for the automatic policy.
            existing.origin = TrafficOrigin.Manual
        }
        return snapshot()
    }

    @Synchronized
    fun promote(fileId: Int): TrafficUsageSnapshot {
        transfers[fileId]?.let { if (it.origin == TrafficOrigin.Auto) it.origin = TrafficOrigin.Manual }
        return snapshot()
    }

    /**
     * Adds only the newly observed bytes. A decrease is ignored instead of being subtracted: TDLib may
     * briefly report an older value while reconnecting, and counting that range twice would overstate
     * the session totals.
     */
    @Synchronized
    fun progress(fileId: Int, downloadedBytes: Long): TrafficUsageSnapshot {
        val bytes = downloadedBytes.coerceAtLeast(0L)
        val transfer = transfers.getOrPut(fileId) { Transfer(TrafficOrigin.Auto, 0L) }
        if (bytes > transfer.highWater) {
            val delta = bytes - transfer.highWater
            if (transfer.origin == TrafficOrigin.Auto) autoBytes += delta else manualBytes += delta
            transfer.highWater = bytes
        }
        return snapshot()
    }

    @Synchronized
    fun finish(fileId: Int): TrafficUsageSnapshot {
        transfers.remove(fileId)
        return snapshot()
    }

    @Synchronized
    fun isTracking(fileId: Int): Boolean = transfers.containsKey(fileId)

    @Synchronized
    fun snapshot(): TrafficUsageSnapshot = TrafficUsageSnapshot(
        autoBytes = autoBytes,
        manualBytes = manualBytes,
        activeOrigins = transfers.mapValues { it.value.origin }
    )
}

/** Observable wrapper used by the repository and by the UI. All updates happen on the caller's thread. */
internal class TrafficUsageSession {
    private val accumulator = TrafficUsageAccumulator()
    private val _snapshot = MutableStateFlow(accumulator.snapshot())
    val snapshot: StateFlow<TrafficUsageSnapshot> = _snapshot.asStateFlow()

    @Synchronized
    fun begin(fileId: Int, origin: TrafficOrigin, alreadyDownloadedBytes: Long = 0L) =
        publish(accumulator.begin(fileId, origin, alreadyDownloadedBytes))

    @Synchronized
    fun promote(fileId: Int) = publish(accumulator.promote(fileId))

    @Synchronized
    fun progress(fileId: Int, downloadedBytes: Long) = publish(accumulator.progress(fileId, downloadedBytes))

    @Synchronized
    fun finish(fileId: Int) = publish(accumulator.finish(fileId))

    @Synchronized
    fun isTracking(fileId: Int): Boolean = accumulator.isTracking(fileId)

    private fun publish(value: TrafficUsageSnapshot) {
        if (_snapshot.value != value) _snapshot.value = value
    }
}

/**
 * Per-account sessions. File ids and traffic counters belong to one Telegram account, so switching
 * accounts never mixes their totals.
 */
internal object TrafficUsageStore {
    private const val LEGACY_ACCOUNT_ID = "legacy"
    private val sessions = LinkedHashMap<String, TrafficUsageSession>()

    fun session(accountId: String): TrafficUsageSession {
        val id = accountId.ifBlank { LEGACY_ACCOUNT_ID }
        return synchronized(sessions) { sessions.getOrPut(id) { TrafficUsageSession() } }
    }
}
