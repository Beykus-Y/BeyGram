package com.beykus.beygram.playback

import java.io.IOException

/**
 * The data source gave up waiting for bytes of a queue file: no transfer, a stalled transfer or a file that never
 * resolved. It usually means the network is gone, so the queue waits for it instead of treating the track as broken.
 */
internal class PlaybackNetworkWaitException(message: String) : IOException(message)

/** What a music queue does after Media3 reports an error for the current track. */
internal enum class PlaybackErrorKind {
    /** The file is not reachable right now. Wait for the network and continue from the same position. */
    NetworkWait,

    /** The file is broken or cannot be opened. Skip to the next track. */
    Unplayable,
}

internal fun classifyPlaybackError(error: Throwable): PlaybackErrorKind =
    if (generateSequence(error) { it.cause }.take(8).any { it is PlaybackNetworkWaitException }) {
        PlaybackErrorKind.NetworkWait
    } else {
        PlaybackErrorKind.Unplayable
    }

/** How long a queue keeps waiting for the network before the track is reported as failed. */
internal const val NETWORK_WAIT_LIMIT_MS = 10 * 60_000L

/** Breaks that follow each other without a single track starting stop the queue, so a dead network cannot skip a playlist. */
internal const val MAX_CONSECUTIVE_SKIPS = 3

/** The pause before retrying while the network already reports itself as available. */
internal const val NETWORK_RETRY_DELAY_MS = 8_000L

internal enum class PlaybackErrorAction {
    WaitForNetwork,
    SkipToNext,
    Fail,
}

/** The decision for one error. [consecutiveSkips] counts earlier skipped tracks that never started playing. */
internal fun playbackErrorAction(
    kind: PlaybackErrorKind,
    hasNext: Boolean,
    consecutiveSkips: Int,
    networkWaitedMs: Long
): PlaybackErrorAction = when (kind) {
    PlaybackErrorKind.NetworkWait ->
        if (networkWaitedMs < NETWORK_WAIT_LIMIT_MS) PlaybackErrorAction.WaitForNetwork else PlaybackErrorAction.Fail
    PlaybackErrorKind.Unplayable ->
        if (hasNext && consecutiveSkips < MAX_CONSECUTIVE_SKIPS) PlaybackErrorAction.SkipToNext else PlaybackErrorAction.Fail
}

/** Pauses between attempts to load the next queue page while the current track is still playing: 2, 5, then 15 seconds. */
internal val QUEUE_PAGE_RETRY_DELAYS_MS = longArrayOf(2_000L, 5_000L, 15_000L)

internal fun queuePageRetryDelayMs(failedAttempts: Int): Long? = QUEUE_PAGE_RETRY_DELAYS_MS.getOrNull(failedAttempts)
