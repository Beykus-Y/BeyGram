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
    /**
     * Questions asked of TDLib about a file after a read created its entry, oldest first, whose answers have not arrived yet. Each
     * one is its position in [sequence]. TDLib answers the questions about one file in the order they were asked. A question stays
     * open through an eviction of its entry, because its answer may still arrive for the file.
     */
    private val openQuestions = HashMap<Int, ArrayDeque<Long>>()
    /** Position in [sequence] of the latest update of a file, while questions about it are open. */
    private val latestUpdate = HashMap<Int, Long>()
    /** Orders the questions and the updates against each other. Guarded by [lock]. */
    private var sequence = 0L
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
        onCreatedByRead = { id ->
            // Recorded before TDLib is asked, so an update that comes later is known to be newer than the answer.
            synchronized(lock) { askLocked(id) }
            refreshFile(id)
        }
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
        sequence += 1
        if (openQuestions[file.id]?.isNotEmpty() == true) latestUpdate[file.id] = sequence
        applyLocked(file)
    }

    /** Applies [file] as TDLib's state of the file. Called for an update, and for an answer that no update overtook. */
    private fun applyLocked(file: MediaFileState) {
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

    /**
     * Applies TDLib's answer to a GetFile that was sent when this store created the file's state again. TDLib produced
     * that answer before any transfer that started after it, so an answer that carries nothing new must not clear a
     * transfer in flight: the transfer's own update reports it.
     */
    fun refresh(file: MediaFileState) = synchronized(lock) {
        // The answer belongs to the oldest question still open for the file. An update that arrived after that question is newer
        // than the answer, which TDLib may have built before it. Applying the answer would roll the file back, for example from a
        // finished download to no local copy. The update already holds the state.
        val answeredAt = openQuestions[file.id]?.firstOrNull()
        val updatedSinceAsked = answeredAt != null && (latestUpdate[file.id] ?: 0L) > answeredAt
        closeQuestionLocked(file.id)
        if (updatedSinceAsked) return@synchronized
        verified += file.id
        val current = states.stateLocked(file.id).value
        val inFlight = current.isDownloading || current.isUploading
        val idle = !file.isDownloading && !file.isUploading
        if (inFlight && idle && addsNothing(file, current)) return@synchronized
        applyLocked(file)
    }

    /**
     * Closes the question about [fileId] when TDLib answered it with an error, so that the question does not stay open and
     * make the answers that come later look older than the updates.
     */
    fun refreshFailed(fileId: Int) = synchronized(lock) {
        closeQuestionLocked(fileId)
    }

    /** Records a question about [id] before it is sent, so an update that comes later is known to be newer than its answer. */
    private fun askLocked(id: Int) {
        sequence += 1
        val questions = openQuestions.getOrPut(id) { ArrayDeque() }
        if (questions.size >= MAX_OPEN_QUESTIONS) questions.removeFirst()
        questions.addLast(sequence)
    }

    /** Closes the oldest open question about [id]. Once none is left, the file's bookkeeping for questions goes too. */
    private fun closeQuestionLocked(id: Int) {
        val questions = openQuestions[id] ?: return
        questions.removeFirstOrNull()
        if (questions.isEmpty()) {
            openQuestions.remove(id)
            latestUpdate.remove(id)
        }
    }

    /** Whether [file] has no local copy and no bytes beyond what [current] already knows. */
    private fun addsNothing(file: MediaFileState, current: MediaFileState): Boolean =
        file.localPath == null &&
            (file.downloadPath == null || file.downloadPath == current.downloadPath) &&
            file.downloadedBytes <= current.downloadedBytes &&
            file.downloadedPrefixBytes <= current.downloadedPrefixBytes &&
            file.uploadedBytes <= current.uploadedBytes

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

        /** Questions about one file that may be open at once. A question whose answer never came is dropped past this count. */
        private const val MAX_OPEN_QUESTIONS = 8
    }
}
