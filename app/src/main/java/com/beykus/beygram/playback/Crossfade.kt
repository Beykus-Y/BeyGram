package com.beykus.beygram.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Length of the overlap between the end of one track and the start of the next. */
internal const val CROSSFADE_MS = 4_000L

/** The second player for the outgoing tail is prepared this long before the switch point, so the switch never waits for buffering. */
internal const val CROSSFADE_PREPARE_LEAD_MS = 3_000L

/** Closer than this to the switch point, a tail is too late to prepare, so the transition stays a plain cut. */
internal const val CROSSFADE_PREPARE_MIN_MS = 300L + CROSSFADE_PREROLL_SETTLE_MS + CROSSFADE_HANDOFF_MS

/** How often the position is checked while the switch point is close. */
internal const val CROSSFADE_ARM_STEP_MS = 20L

/** How often the position is checked while the switch point is still far away. */
internal const val CROSSFADE_IDLE_STEP_MS = 250L

/** How often the gains are updated during the overlap. */
internal const val CROSSFADE_STEP_MS = 40L

/**
 * The outgoing track is never cut. Shortly before the switch point the second player joins the same track in step with
 * the primary, silent for [CROSSFADE_PREROLL_SETTLE_MS] so it is already running when it is needed. Then, over
 * [CROSSFADE_HANDOFF_MS], the two players trade volume while playing identical audio (complementary gains keep the
 * level), and only after that the primary jumps to the next track while the second player carries the outgoing one.
 */
internal const val CROSSFADE_PREROLL_SETTLE_MS = 300L
internal const val CROSSFADE_HANDOFF_MS = 120L
internal const val CROSSFADE_PREROLL_MS = CROSSFADE_PREROLL_SETTLE_MS + CROSSFADE_HANDOFF_MS

/** The tail is started a little ahead of the primary's position to cover the time an output track needs to start. */
internal const val CROSSFADE_TAIL_START_COMPENSATION_MS = 40L

/** A tail whose position differs from the primary's by more than this after the pre-roll is out of step: the cut stays plain. */
internal const val CROSSFADE_SYNC_TOLERANCE_MS = 150L

/** How long before the switch point the pre-roll starts. */

/** Complementary gains for two players that play the same audio: they sum to 1, so the level stays steady. */
internal fun handoffPrimaryGain(progress: Float): Float = 1f - progress.coerceIn(0f, 1f)

internal fun handoffTailGain(progress: Float): Float = progress.coerceIn(0f, 1f)

/** How far the switch may run late, or the tail may run early, before the overlap is given up. */
internal const val CROSSFADE_TOLERANCE_MS = 250L

/**
 * Position at which the overlap starts, in the outgoing track: its audible end minus the overlap. [trailingMs] is the
 * measured trailing silence; an unknown value counts as none, so the overlap then ends with the file.
 * Null when the track is too short to hold the overlap.
 */
internal fun crossfadeSwitchMs(durationMs: Long, trailingMs: Long): Long? {
    val contentEnd = durationMs - trailingMs.coerceAtLeast(0L)
    val switchAt = contentEnd - CROSSFADE_MS
    return if (switchAt > 0L) switchAt else null
}

/** How long to wait before checking again, given the time left before the switch point. */
internal fun crossfadeWaitMs(remainingMs: Long): Long =
    if (remainingMs > CROSSFADE_PREPARE_LEAD_MS) CROSSFADE_IDLE_STEP_MS else CROSSFADE_ARM_STEP_MS

/**
 * True when the position went back by more than the tolerance since the last check: a seek back. Forward playback,
 * including small jitter, never does. The first check has nothing to compare with.
 */
internal fun isBackwardSeek(previousMs: Long?, positionMs: Long): Boolean =
    previousMs != null && positionMs < previousMs - CROSSFADE_TOLERANCE_MS

/** 0..1 progress of the overlap after [elapsedMs] have passed. */
internal fun crossfadeProgress(elapsedMs: Long): Float = (elapsedMs.toFloat() / CROSSFADE_MS).coerceIn(0f, 1f)

/**
 * Equal-power gains: the squares of the two gains add up to 1 at every point, so the loudness stays steady through the
 * overlap of two unrelated tracks. The outgoing gain falls from 1 to 0, the incoming gain rises from 0 to 1.
 */
internal fun outgoingGain(progress: Float): Float = cos(progress.coerceIn(0f, 1f) * PI.toFloat() / 2f)

internal fun incomingGain(progress: Float): Float = sin(progress.coerceIn(0f, 1f) * PI.toFloat() / 2f)
