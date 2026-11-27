package com.beykus.beygram.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** Only a public item with actual renderer playback may attempt background FGS recovery. */
internal fun canRecoverPlaybackService(state: PlaybackState, native: Boolean, exoPlaying: Boolean): Boolean =
    state.item?.systemPrivate == false && !state.ended && if (native) state.playing else exoPlaying

internal fun nativeSessionPlaybackState(state: PlaybackState): Int = when {
    state.ended -> Player.STATE_ENDED
    !state.playing && (state.isBuffering || state.intentPlaying) -> Player.STATE_BUFFERING
    else -> Player.STATE_READY
}

/** Exo's one-item placeholder cannot describe navigation in the native queue. */
@UnstableApi
internal fun nativeSessionCommands(base: Player.Commands, state: PlaybackState): Player.Commands =
    Player.Commands.Builder().addAll(base)
        .add(Player.COMMAND_PLAY_PAUSE)
        .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
        .add(Player.COMMAND_SEEK_BACK)
        .add(Player.COMMAND_SEEK_FORWARD)
        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        .apply {
            if (state.canNext) {
                add(Player.COMMAND_SEEK_TO_NEXT)
                add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            } else {
                remove(Player.COMMAND_SEEK_TO_NEXT)
                remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            }
        }
        .build()

/** Event IDs are computed from the same before/after snapshots; routine position ticks are silent. */
internal fun nativeSessionEventFlags(before: PlaybackState, after: PlaybackState): Set<Int> = buildSet {
    val entered = !before.bgMixActive && after.bgMixActive
    val occurrenceChanged = before.item?.key != after.item?.key || before.queueIndex != after.queueIndex
    if (entered || nativeSessionPlaybackState(before) != nativeSessionPlaybackState(after)) {
        add(Player.EVENT_PLAYBACK_STATE_CHANGED)
    }
    if (entered || before.intentPlaying != after.intentPlaying) add(Player.EVENT_PLAY_WHEN_READY_CHANGED)
    if (entered || before.playing != after.playing) add(Player.EVENT_IS_PLAYING_CHANGED)
    if (entered || before.isBuffering != after.isBuffering) add(Player.EVENT_IS_LOADING_CHANGED)
    if (entered) add(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED)
    if (occurrenceChanged) add(Player.EVENT_MEDIA_ITEM_TRANSITION)
    if (entered || occurrenceChanged || before.durationMs != after.durationMs ||
        before.item?.systemMetadata() != after.item?.systemMetadata()
    ) {
        add(Player.EVENT_MEDIA_METADATA_CHANGED)
    }
    if (entered || occurrenceChanged || before.canNext != after.canNext ||
        before.timelineHasPrevious != after.timelineHasPrevious
    ) add(Player.EVENT_AVAILABLE_COMMANDS_CHANGED)
}
