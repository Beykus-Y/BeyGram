package com.beykus.beygram.playback

/**
 * Keeps the single TDLib user download window in step with what the player is about to read.
 *
 * Rules, all enforced here rather than in the UI:
 * - one window per file, because TDLib replaces the user window instead of keeping several independent ranges;
 * - a request is sent only when the desired window is not already pending, so a poll loop cannot spam TDLib;
 * - seek replaces the window; bytes already downloaded stay in the sparse file but are not trusted for the new range;
 * - a background preload never takes the window away from an active user playback;
 * - repeated requests without any progress back off instead of looping;
 * - completion and a path change drop the state.
 *
 * Pure logic: everything about TDLib is behind [PlaybackRanges], so the rules are tested on the JVM.
 */
/**
 * Token of one viewer of one file. A request (or a dispose) that arrives with an epoch that is no longer current does
 * nothing: it belongs to a screen that has already been closed.
 */
class RangeOwner internal constructor(val fileId: Int, internal val epoch: Long)

class UserRangeCoordinator(
    private val ranges: PlaybackRanges,
    private val clock: () -> Long = System::currentTimeMillis,
    /** How long to wait after [maxAttemptsWithoutProgress] fruitless requests before trying again. */
    private val retryBackoffMs: Long = 2_000,
    private val maxAttemptsWithoutProgress: Int = 4,
    /** How long a window may deliver nothing and still count as pending. */
    private val windowDeadlineMs: Long = 5_000,
) {
    private class FileState {
        var window: RangeWindow? = null

        /** True while the window belongs to a user playback; background preloads must not replace it. */
        var userOwned = false
        var attemptsWithoutProgress = 0
        var lastRequestAt = 0L
        var lastProgressAt = 0L
        /** Availability at the window's own offset; -1 until the first observation of this window. */
        var windowAvailable = -1L

        /**
         * Records availability for [offset]. Only the window's own offset proves that the window is alive: bytes
         * available at some other offset (a seek target, a neighbouring read) say nothing about this download and must
         * not extend its life.
         */
        fun onAvailable(offset: Long, available: Long, now: Long) {
            val active = window ?: return
            if (offset != active.offset) return
            if (available > windowAvailable) {
                attemptsWithoutProgress = 0
                lastProgressAt = now
            }
            windowAvailable = available
        }

        fun waitingForBackoff(now: Long, limit: Int, backoffMs: Long): Boolean =
            attemptsWithoutProgress >= limit && now - lastRequestAt < backoffMs

        fun startWindow(window: RangeWindow, now: Long, userOwned: Boolean, observedAvailable: Long) {
            this.window = window
            this.userOwned = userOwned
            this.lastRequestAt = now
            this.lastProgressAt = now
            // The value seen before the request is the baseline: re-observing it is not progress.
            this.windowAvailable = observedAvailable
            this.attemptsWithoutProgress += 1
        }
    }

    private val states = HashMap<Int, FileState>()

    /**
     * Guards the state map and every decision that sends or cancels a window.
     *
     * The coordinator is reached from two threads: the page scope that asks for playback bytes and the main thread that
     * disposes the page. Without one lock, an ownership check and the following `requestWindow` could be split by a
     * `reset` from the other thread, and a `DownloadFile` would be sent for a screen that no longer exists (after its
     * `CancelDownloadFile`). The lock is never held across a suspending call, so a slow TDLib query cannot delay closing
     * the page.
     */
    private val lock = Any()

    /** Epoch of the page that currently owns each file; a request with an older epoch is ignored. */
    private val epochs = HashMap<Int, Long>()
    private var nextEpoch = 0L

    /**
     * Takes ownership of [fileId] for one viewer and returns its token.
     *
     * The token closes the last window between a closed page and a loader thread: Media3 may already be inside
     * [ensureReadable] (or about to enter it) when the screen is disposed. A plain "does the state still exist" check
     * cannot tell that late call from a newly opened page, so the call is tied to the page's epoch instead (#1195).
     */
    fun owner(fileId: Int): RangeOwner = synchronized(lock) {
        val epoch = nextEpoch++
        epochs[fileId] = epoch
        RangeOwner(fileId, epoch)
    }

    /** True when the call belongs to the page that currently owns its file; a null owner keeps the plain behaviour. */
    private fun ownsLocked(owner: RangeOwner?): Boolean = owner == null || epochs[owner.fileId] == owner.epoch

    /** Bytes readable from [offset], without asking TDLib for anything. */
    suspend fun readableFrom(fileId: Int, offset: Long): Long = ranges.readableFrom(fileId, offset)

    /**
     * Makes sure [needed] bytes starting at [offset] are readable, requesting a window when they are not.
     * Returns the number of bytes readable at [offset] right now; the caller polls and calls this again.
     */
    suspend fun ensureReadable(
        fileId: Int,
        offset: Long,
        needed: Long,
        urgent: Boolean,
        owner: RangeOwner? = null
    ): Long {
        val state = synchronized(lock) {
            // The page was closed before this call even started: no state is created for it.
            if (!ownsLocked(owner)) return 0L
            states.getOrPut(fileId) { FileState() }
        }
        val now = clock()
        val available = ranges.readableFrom(fileId, offset)
        // Everything from here on runs under the same lock as `reset`: the page may have been closed while the query was
        // in flight, and neither a request nor a state update may follow the cancellation.
        return synchronized(lock) {
            if (!ownsLocked(owner)) return 0L
            if (states[fileId] !== state) return 0L
            state.onAvailable(offset, available, now)
            if (available >= needed) return available

            val wanted = RangeWindow(offset, 0L, urgent)
            val pending = state.window
            if (pending != null && pending.urgent == urgent && pendingServes(pending, offset, needed, now, state)) {
                // The pending window is expected to deliver these bytes; re-sending would only restart it.
                return available
            }
            if (!urgent && state.userOwned) {
                // A background preload must not steal the user's window.
                return available
            }
            if (state.waitingForBackoff(now, maxAttemptsWithoutProgress, retryBackoffMs)) return available
            if (state.attemptsWithoutProgress >= maxAttemptsWithoutProgress) {
                // Backoff elapsed: allow a fresh series of attempts.
                state.attemptsWithoutProgress = 0
            }
            state.startWindow(wanted, now, urgent, available)
            ranges.requestWindow(fileId, offset, 0L, urgent)
            available
        }
    }

    /**
     * Asks for a bounded tail window, used to read an index that sits at the end of the file. The window replaces the
     * playback one; the caller must request the playback position again afterwards.
     */
    suspend fun ensureTail(
        fileId: Int,
        start: Long,
        length: Long,
        urgent: Boolean = true,
        owner: RangeOwner? = null
    ): Long {
        if (start < 0L || length <= 0L) return 0L
        val state = synchronized(lock) {
            if (!ownsLocked(owner)) return 0L
            states.getOrPut(fileId) { FileState() }
        }
        val now = clock()
        val available = ranges.readableFrom(fileId, start)
        return synchronized(lock) {
            if (!ownsLocked(owner)) return 0L
            if (states[fileId] !== state) return 0L
            state.onAvailable(start, available, now)
            if (available >= length) return available

            val pending = state.window
            // A tail window is reused only while it is still alive; a stalled or rejected one gets a new request.
            if (pending != null && pending.offset == start && pending.limit == length &&
                pending.urgent == urgent && isAlive(pending, now, state)
            ) {
                return available
            }
            if (state.waitingForBackoff(now, maxAttemptsWithoutProgress, retryBackoffMs)) return available
            if (state.attemptsWithoutProgress >= maxAttemptsWithoutProgress) state.attemptsWithoutProgress = 0
            state.startWindow(RangeWindow(start, length, urgent), now, urgent, available)
            ranges.requestWindow(fileId, start, length, urgent)
            available
        }
    }

    /** The file is fully local now: no window is needed and the state is dropped. */
    fun onCompleted(fileId: Int) {
        synchronized(lock) { states.remove(fileId) }
    }

    /**
     * A bounded window serves only its own range. An unlimited window started at [window.offset] keeps downloading
     * forward, so sequential reads are covered; a jump much further ahead is a seek that deserves its own window,
     * because waiting for the download to walk there would stall playback for minutes.
     */
    private fun pendingServes(window: RangeWindow, offset: Long, needed: Long, now: Long, state: FileState): Boolean {
        if (!isAlive(window, now, state)) return false
        if (window.limit > 0L) return window.covers(offset, needed)
        if (offset < window.offset) return false
        return offset - window.offset <= SEEK_TOLERANCE_BYTES
    }

    /**
     * A window is alive while it keeps delivering bytes. If nothing arrived within [windowDeadlineMs] it may have been
     * rejected, paused by the system or silently dropped, so the same offset must be asked for again instead of being
     * treated as "already requested" forever.
     */
    private fun isAlive(window: RangeWindow, now: Long, state: FileState): Boolean =
        now - state.lastProgressAt <= windowDeadlineMs

    /**
     * The page was closed or TDLib moved the file: forget the window and cancel it. [owner] makes the call a no-op when
     * a newer page already owns the file, so a late dispose of the previous viewer cannot cancel the current playback.
     */
    fun reset(fileId: Int, owner: RangeOwner? = null) {
        synchronized(lock) {
            if (!ownsLocked(owner)) return
            epochs.remove(fileId)
            val state = states.remove(fileId) ?: return
            if (state.window != null) ranges.cancelWindow(fileId)
        }
    }

    /** Drops every tracked file; used when the account or the session changes. */
    fun resetAll() {
        synchronized(lock) {
            val ids = states.keys.toList()
            states.clear()
            epochs.clear()
            ids.forEach(ranges::cancelWindow)
        }
    }

    companion object {
        /**
         * How far ahead of its start an unlimited window may serve without being restarted. Sequential playback reads
         * within a few hundred kilobytes of the window start, so a larger jump is a user seek, not a sequential read.
         */
        const val SEEK_TOLERANCE_BYTES = 4L * 1024 * 1024
    }
}