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
class UserRangeCoordinator(
    private val ranges: PlaybackRanges,
    private val clock: () -> Long = System::currentTimeMillis,
    /** How long to wait after [maxAttemptsWithoutProgress] fruitless requests before trying again. */
    private val retryBackoffMs: Long = 2_000,
    private val maxAttemptsWithoutProgress: Int = 4,
) {
    private class FileState {
        var window: RangeWindow? = null

        /** True while the window belongs to a user playback; background preloads must not replace it. */
        var userOwned = false
        var attemptsWithoutProgress = 0
        var lastRequestAt = 0L
        var lastAvailable = -1L

        fun onAvailable(available: Long) {
            // Any growth is progress, so the retry budget is restored.
            if (available > lastAvailable) attemptsWithoutProgress = 0
            lastAvailable = available
        }

        fun waitingForBackoff(now: Long, limit: Int, backoffMs: Long): Boolean =
            attemptsWithoutProgress >= limit && now - lastRequestAt < backoffMs

        fun startWindow(window: RangeWindow, now: Long, userOwned: Boolean) {
            this.window = window
            this.userOwned = userOwned
            this.lastRequestAt = now
            this.attemptsWithoutProgress += 1
        }
    }

    private val states = HashMap<Int, FileState>()

    /** Bytes readable from [offset], without asking TDLib for anything. */
    suspend fun readableFrom(fileId: Int, offset: Long): Long = ranges.readableFrom(fileId, offset)

    /**
     * Makes sure [needed] bytes starting at [offset] are readable, requesting a window when they are not.
     * Returns the number of bytes readable at [offset] right now; the caller polls and calls this again.
     */
    suspend fun ensureReadable(fileId: Int, offset: Long, needed: Long, urgent: Boolean): Long {
        val state = states.getOrPut(fileId) { FileState() }
        val available = ranges.readableFrom(fileId, offset)
        state.onAvailable(available)
        if (available >= needed) return available

        val wanted = RangeWindow(offset, 0L, urgent)
        val pending = state.window
        if (pending != null && pending.urgent == urgent && pendingServes(pending, offset, needed)) {
            // The pending window is expected to deliver these bytes; re-sending would only restart it.
            return available
        }
        if (!urgent && state.userOwned) {
            // A background preload must not steal the user's window.
            return available
        }
        val now = clock()
        if (state.waitingForBackoff(now, maxAttemptsWithoutProgress, retryBackoffMs)) return available
        if (state.attemptsWithoutProgress >= maxAttemptsWithoutProgress) {
            // Backoff elapsed: allow a fresh series of attempts.
            state.attemptsWithoutProgress = 0
        }
        state.startWindow(wanted, now, urgent)
        ranges.requestWindow(fileId, offset, 0L, urgent)
        return available
    }

    /**
     * Asks for a bounded tail window, used to read an index that sits at the end of the file. The window replaces the
     * playback one; the caller must request the playback position again afterwards.
     */
    suspend fun ensureTail(fileId: Int, start: Long, length: Long, urgent: Boolean = true): Long {
        if (start < 0L || length <= 0L) return 0L
        val state = states.getOrPut(fileId) { FileState() }
        val available = ranges.readableFrom(fileId, start)
        state.onAvailable(available)
        if (available >= length) return available

        val pending = state.window
        if (pending != null && pending.offset == start && pending.limit == length && pending.urgent == urgent) {
            return available
        }
        val now = clock()
        if (state.waitingForBackoff(now, maxAttemptsWithoutProgress, retryBackoffMs)) return available
        if (state.attemptsWithoutProgress >= maxAttemptsWithoutProgress) state.attemptsWithoutProgress = 0
        state.startWindow(RangeWindow(start, length, urgent), now, urgent)
        ranges.requestWindow(fileId, start, length, urgent)
        return available
    }

    /** The file is fully local now: no window is needed and the state is dropped. */
    fun onCompleted(fileId: Int) {
        states.remove(fileId)
    }

    /**
     * A bounded window serves only its own range. An unlimited window started at [window.offset] keeps downloading
     * forward, so sequential reads are covered; a jump much further ahead is a seek that deserves its own window,
     * because waiting for the download to walk there would stall playback for minutes.
     */
    private fun pendingServes(window: RangeWindow, offset: Long, needed: Long): Boolean {
        if (window.limit > 0L) return window.covers(offset, needed)
        if (offset < window.offset) return false
        return offset - window.offset <= SEEK_TOLERANCE_BYTES
    }

    /** The page was closed or TDLib moved the file: forget the window and cancel it. */
    fun reset(fileId: Int) {
        val state = states.remove(fileId) ?: return
        if (state.window != null) ranges.cancelWindow(fileId)
    }

    /** Drops every tracked file; used when the account or the session changes. */
    fun resetAll() {
        val ids = states.keys.toList()
        states.clear()
        ids.forEach(ranges::cancelWindow)
    }

    companion object {
        /**
         * How far ahead of its start an unlimited window may serve without being restarted. Sequential playback reads
         * within a few hundred kilobytes of the window start, so a larger jump is a user seek, not a sequential read.
         */
        const val SEEK_TOLERANCE_BYTES = 4L * 1024 * 1024
    }
}