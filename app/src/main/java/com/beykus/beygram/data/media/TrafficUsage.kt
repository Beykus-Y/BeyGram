package com.beykus.beygram.data.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Who started the transfer: the automatic policy or the user's Download button. */
internal enum class TrafficOrigin { Auto, Manual }

/**
 * Immutable traffic counters for one account during the current app process.
 * [activeOrigins] maps a file id to the origin of its running transfer; paused and finished transfers
 * are excluded, so the map only contains downloads the user is actually consuming right now.
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
    private data class Transfer(var origin: TrafficOrigin, var highWater: Long, var active: Boolean)

    private val transfers = LinkedHashMap<Int, Transfer>()
    private var autoBytes = 0L
    private var manualBytes = 0L

    /**
     * Starts a new attempt or reactivates a transfer that TDLib paused. [alreadyDownloadedBytes] is
     * TDLib's last known size for this file: a resumed transfer starts above it and must not be
     * counted again, while progress below it is ignored.
     */
    @Synchronized
    fun begin(fileId: Int, origin: TrafficOrigin, alreadyDownloadedBytes: Long = 0L): TrafficUsageSnapshot {
        val baseline = alreadyDownloadedBytes.coerceAtLeast(0L)
        val existing = transfers[fileId]
        if (existing == null) {
            transfers[fileId] = Transfer(origin, baseline, active = true)
        } else if (existing.active) {
            // A manual tap takes ownership of a transfer that is already running, but not the bytes
            // that have already been counted for the automatic policy. An automatic request must not
            // demote a manual transfer.
            if (origin == TrafficOrigin.Manual) existing.origin = TrafficOrigin.Manual
        } else {
            // Reactivating a paused transfer starts from the size TDLib already has, so resumed
            // bytes are not counted twice.
            existing.origin = origin
            existing.highWater = baseline
            existing.active = true
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
     * the session totals. Inactive transfers still account for bytes reported by their terminal update;
     * [finish] only removes them from the active indicator, and [release] forgets the watermark.
     */
    @Synchronized
    fun progress(fileId: Int, downloadedBytes: Long): TrafficUsageSnapshot {
        val bytes = downloadedBytes.coerceAtLeast(0L)
        val transfer = transfers.getOrPut(fileId) { Transfer(TrafficOrigin.Auto, 0L, active = true) }
        if (bytes > transfer.highWater) {
            val delta = bytes - transfer.highWater
            if (transfer.origin == TrafficOrigin.Auto) autoBytes += delta else manualBytes += delta
            transfer.highWater = bytes
        }
        return snapshot()
    }

    /** Hides a transfer from the live indicator but keeps its watermark for the terminal TDLib update. */
    @Synchronized
    fun finish(fileId: Int): TrafficUsageSnapshot {
        transfers[fileId]?.active = false
        return snapshot()
    }

    /** Forgets a finished or cancelled transfer after its last progress was accounted for. */
    @Synchronized
    fun release(fileId: Int): TrafficUsageSnapshot {
        transfers.remove(fileId)
        return snapshot()
    }

    @Synchronized
    fun isActive(fileId: Int): Boolean = transfers[fileId]?.active == true

    @Synchronized
    fun hasTransfer(fileId: Int): Boolean = transfers.containsKey(fileId)

    @Synchronized
    fun snapshot(): TrafficUsageSnapshot = TrafficUsageSnapshot(
        autoBytes = autoBytes,
        manualBytes = manualBytes,
        activeOrigins = transfers.filterValues { it.active }.mapValues { it.value.origin }
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
    fun release(fileId: Int) = publish(accumulator.release(fileId))

    @Synchronized
    fun isActive(fileId: Int): Boolean = accumulator.isActive(fileId)

    @Synchronized
    fun hasTransfer(fileId: Int): Boolean = accumulator.hasTransfer(fileId)

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
