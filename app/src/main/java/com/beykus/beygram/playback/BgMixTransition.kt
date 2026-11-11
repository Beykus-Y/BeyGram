package com.beykus.beygram.playback

import androidx.compose.runtime.Immutable
import com.beykus.beygram.media.bgmix.BgMixStatus

/**
 * Read-only view of one running BG Mix transition, for the "Transition to …" line of the music player. It lives in its
 * own flow next to [PlaybackState], so the progress ticks of a transition never rebuild the whole player.
 *
 * [fromKey] and [target] are fixed when the transition starts and stay the same until it ends, even when the native
 * session hands the "playing" state to the incoming track halfway through. [target] is null when the incoming track
 * cannot be told from the native queue; the UI then shows a line without a title instead of a wrong one.
 */
@Immutable
data class BgMixTransitionUi(
    /** Native queue id of the track the transition started from; the identity of this pair of tracks. */
    val fromNativeId: Long?,
    val fromKey: String?,
    val target: PlaybackItem?,
    /** Progress of the mix itself, 0..1, as the native engine reports it. */
    val progress: Float,
    /** The session is paused; the decoration must stand still, as the native progress does. */
    val paused: Boolean
)

/**
 * The next transition view for one native status tick.
 *
 * Only a confirmed running transition (`transitionActive`) shows the line: preparing the next track does not. The pair of
 * tracks is taken on the first running tick, from [previousIndex] (the queue index the engine showed before this tick,
 * which is still the outgoing track) and the first native queue item after it that has not been played or failed. Later
 * ticks of the same transition keep that pair and only refresh progress and pause.
 */
internal fun nextBgMixTransition(
    previous: BgMixTransitionUi?,
    status: BgMixStatus,
    previousIndex: Int,
    indexOfNative: (Long) -> Int?,
    itemAt: (Int) -> PlaybackItem?
): BgMixTransitionUi? {
    if (!status.transitionActive) return null
    val progress = status.transitionProgress.toFloat().takeIf { !it.isNaN() }?.coerceIn(0f, 1f) ?: 0f
    // A progress that falls back to the start is a new transition that followed without an idle tick in between.
    if (previous != null && progress + RESTART_DROP >= previous.progress) {
        // The same transition: keep the pair, never let the progress run backwards on a late tick.
        return previous.copy(progress = maxOf(previous.progress, progress), paused = status.paused)
    }
    val fromNativeId = status.queue.firstOrNull { indexOfNative(it.id) == previousIndex }?.id
    val targetIndex = status.queue
        .asSequence()
        .filter { it.state != "played" && it.state != "failed" }
        .mapNotNull { indexOfNative(it.id) }
        .filter { it > previousIndex }
        .minOrNull()
    return BgMixTransitionUi(
        fromNativeId = fromNativeId,
        fromKey = itemAt(previousIndex)?.key,
        target = targetIndex?.let(itemAt),
        progress = progress,
        paused = status.paused
    )
}

/** How far the native progress must fall back for a tick to count as the start of another transition. */
private const val RESTART_DROP = 0.5f
