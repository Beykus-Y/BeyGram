package com.beykus.beygram.data.music

import com.beykus.beygram.playback.PlaybackItem
import java.util.UUID

/** A fresh id for one entry of a playlist. */
fun newMusicItemId(): String = UUID.randomUUID().toString()

/**
 * A playable music item as a playlist entry. Null for anything that is not music, for secret or view-once media, and for
 * items without a Telegram origin, because such an item could not be found again.
 */
fun PlaybackItem.toPlaylistTrack(
    itemId: String = newMusicItemId(),
    addedAt: Long = System.currentTimeMillis()
): PlaylistTrack? {
    if (!isMusic || systemPrivate) return null
    val itemOrigin = origin ?: return null
    return PlaylistTrack(
        itemId = itemId,
        // The real tag title, not the "Artist — Title" join that the player displays.
        title = trackTitle.ifBlank { title },
        performer = performer,
        album = album,
        durationSeconds = durationSeconds.coerceAtLeast(0),
        origin = itemOrigin,
        addedAt = addedAt
    )
}

/** Track metadata for listen history and stats. Same rules as [toPlaylistTrack]: music only, with an origin. */
fun PlaybackItem.toTrackRef(): MusicTrackRef? {
    if (!isMusic || systemPrivate) return null
    val itemOrigin = origin ?: return null
    return MusicTrackRef(
        trackKey = musicTrackKey(itemOrigin, null, occurrenceId),
        title = trackTitle.ifBlank { title },
        performer = performer,
        album = album,
        durationSeconds = durationSeconds.coerceAtLeast(0),
        origin = itemOrigin
    )
}

fun PlaylistTrack.toTrackRef(): MusicTrackRef = MusicTrackRef(
    trackKey = trackKey,
    title = title,
    performer = performer,
    album = album,
    durationSeconds = durationSeconds,
    origin = origin,
    audioHash = audioHash
)
