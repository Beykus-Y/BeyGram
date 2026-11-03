package com.beykus.beygram.data.tdlib

import com.beykus.beygram.data.media.TrafficOrigin
import com.beykus.beygram.data.media.TrafficUsageSession
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.MediaFileState
import kotlinx.coroutines.flow.StateFlow

/**
 * File states plus their traffic accounting. [traffic] is injected by the repository so all files of
 * one Telegram account share a single session; standalone instances (tests, mapping helpers) start
 * with their own empty session.
 *
 * The number of states is bounded by [maxEntries]. Only idle files are dropped: nothing observes them, no transfer
 * or upload is running, and no partial data is kept. A dropped file is read again through [refreshFile], which the
 * repository answers from TDLib's own database.
 */
internal class MediaFileStore(
    private val traffic: TrafficUsageSession = TrafficUsageSession(),
    maxEntries: Int = MAX_ENTRIES,
    /** Asks TDLib for the current state of a file whose state was just created by a read. */
    refreshFile: (Int) -> Unit = {}
) {
    private val lock = Any()
    /** Files whose state TDLib has sent since their entry was created. A history snapshot cannot replace these. */
    private val verified = HashSet<Int>()
    /** Last explicit origin for a not-yet-finished transfer, so a TDLib pause/resume stays attributed correctly. */
    private val origins = HashMap<Int, TrafficOrigin>()
    /** Cancelled transfers whose final TDLib update may still add bytes; released after that update. */
    private val settled = HashSet<Int>()
    private val states = EvictableStateMap<Int, MediaFileState>(
        lock = lock,
        maxEntries = maxEntries,
        seed = { MediaFileState(it) },
        isIdle = { id, state -> isIdle(id, state) },
        onEvicted = { id ->
            verified.remove(id)
            origins.remove(id)
            settled.remove(id)
        },
        onCreatedByRead = refreshFile
    )

    fun observe(id: Int): StateFlow<MediaFileState> = states.observe(id)

    fun snapshot(id: Int): MediaFileState? = synchronized(lock) { states.peekLocked(id) }

    /** Number of file states held now; bounded by the store's limit. */
    internal fun entryCount(): Int = states.size()

    // History snapshots must not roll back a newer UpdateFile download/upload state.
    fun register(file: MediaFileState) = synchronized(lock) {
        if (file.id in verified) return@synchronized
        val state = states.stateLocked(file.id)
        // A request in flight owns the state until TDLib answers it.
        if (state.value.isDownloading) return@synchronized
        verified += file.id
        state.value = file
    }

    fun update(file: MediaFileState) = synchronized(lock) {
        verified += file.id
        val state = states.stateLocked(file.id)
        val previous = state.value
        state.value = file
        if (file.isDownloading) {
            // TDLib also preloads thumbnails and media without an explicit DownloadFile request.
            // Such transfers have no app origin, so they are counted as automatic.
            if (!traffic.isActive(file.id)) {
                val resuming = traffic.hasTransfer(file.id)
                traffic.begin(
                    file.id,
                    origins[file.id] ?: TrafficOrigin.Auto,
                    previous.downloadedBytes,
                    preserveWatermark = resuming
                )
            }
            traffic.progress(file.id, file.downloadedBytes)
        } else {
            // Count the last progress value before the transfer leaves the active indicator. A pause
            // keeps the watermark so a later resume does not count the same bytes again; completion
            // and the terminal update after cancellation release it.
            if (traffic.hasTransfer(file.id)) {
                traffic.progress(file.id, file.downloadedBytes)
                traffic.finish(file.id)
                if (file.localPath != null || settled.remove(file.id)) {
                    traffic.release(file.id)
                    origins.remove(file.id)
                }
            }
            if (file.localPath != null) origins.remove(file.id)
        }
    }

    fun beginDownload(id: Int): Boolean = beginDownload(id, TrafficOrigin.Manual)

    /** Starts a transfer that the auto-download policy requested; the manual button never uses this. */
    fun beginAutoDownload(id: Int): Boolean = beginDownload(id, TrafficOrigin.Auto)

    private fun beginDownload(id: Int, origin: TrafficOrigin): Boolean = synchronized(lock) {
        val state = states.stateLocked(id)
        when {
            state.value.localPath != null -> false
            state.value.isDownloading -> {
                // A manual tap while an automatic transfer is running takes it over for the user.
                if (origin == TrafficOrigin.Manual) {
                    origins[id] = TrafficOrigin.Manual
                    traffic.promote(id)
                }
                false
            }
            else -> {
                val baseline = state.value.downloadedBytes
                val resuming = traffic.hasTransfer(id)
                state.value = state.value.copy(isDownloading = true, error = null)
                origins[id] = origin
                settled.remove(id)
                // A file whose state TDLib has not sent yet may already be complete. Its bytes are counted
                // with its first update instead, so bytes that were downloaded before are not counted again.
                if (id in verified) traffic.begin(id, origin, baseline, preserveWatermark = resuming)
                true
            }
        }
    }

    fun fail(id: Int, message: UiText) = synchronized(lock) {
        val state = states.stateLocked(id)
        state.value = state.value.copy(isDownloading = false, error = message)
        origins.remove(id)
        settled.remove(id)
        traffic.release(id)
    }

    /** Stops showing an automatic download as active after TDLib cancelled it; a manual retry is still possible. */
    fun cancel(id: Int) = synchronized(lock) {
        val state = states.stateLocked(id)
        val wasDownloading = state.value.isDownloading
        state.value = state.value.copy(isDownloading = false, error = null)
        // Keep the origin and watermark: TDLib may send one more update with the bytes it managed to
        // download before the cancellation took effect. The transfer is hidden from the live indicator.
        if (wasDownloading || traffic.hasTransfer(id)) settled += id
        traffic.finish(id)
    }

    /** Only a file that nothing is doing and whose state is fully described by TDLib's answer may be dropped. */
    private fun isIdle(id: Int, state: MediaFileState): Boolean {
        if (state.isDownloading || state.isUploading || state.error != null) return false
        if (traffic.hasTransfer(id)) return false
        // A partly downloaded file keeps its resume point and its traffic watermark, so it stays.
        return state.localPath != null || (
            state.downloadedBytes == 0L && state.downloadPath == null &&
                state.downloadedPrefixBytes == 0L && state.uploadedBytes == 0L
            )
    }

    companion object {
        const val MAX_ENTRIES = 2_000
    }
}
