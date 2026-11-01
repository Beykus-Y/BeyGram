package com.beykus.beygram.ui.screens.music

import com.beykus.beygram.data.music.MusicLibrary
import com.beykus.beygram.data.music.MusicPlaylist
import com.beykus.beygram.data.music.MusicTrackRef
import com.beykus.beygram.data.music.PlaylistTrack
import com.beykus.beygram.data.music.frequentlyPlayed
import com.beykus.beygram.data.music.notPlayedLately
import com.beykus.beygram.data.music.recentlyAdded
import com.beykus.beygram.playback.PlaybackItem

/** Prefix of the ids of screens built from the library instead of a stored playlist. Stored playlist ids are UUIDs. */
internal const val MUSIC_COLLECTION_ID_PREFIX = "collection:"

/**
 * Most tracks the Downloaded collection checks on open. Each check looks up the track's message, so the scan is bounded
 * to the newest library tracks.
 */
internal const val MUSIC_DOWNLOADED_SCAN_LIMIT = 150

/** Rows shown in each preview section of the library screen. */
internal const val MUSIC_PREVIEW_ROWS = 5

/** Largest side of a playlist cover file, in pixels. */
internal const val MUSIC_COVER_MAX_SIDE_PX = 1024

/** Screens built from the library. Each one has an id that the playlist route accepts next to stored playlist ids. */
internal enum class MusicCollectionKind(private val storageKey: String) {
    Recent("recent"),
    Downloaded("downloaded"),
    Frequent("frequent"),
    Added("added"),
    Lately("lately");

    /** The route id of this collection. */
    val id: String get() = MUSIC_COLLECTION_ID_PREFIX + storageKey

    companion object {
        /** The collection an id names, or null when the id is a stored playlist. */
        fun fromId(id: String): MusicCollectionKind? = MusicCollectionKind.entries.firstOrNull { it.id == id }
    }
}

/** The entry a track is listed and played as when it comes from listen stats or history. */
internal fun MusicTrackRef.toPlaylistEntry(): PlaylistTrack = PlaylistTrack(
    itemId = trackKey,
    title = title,
    performer = performer,
    album = album,
    durationSeconds = durationSeconds.coerceAtLeast(0),
    origin = origin,
    addedAt = 0L,
    audioHash = audioHash
)

/**
 * Every track of the library once, newest addition first. A track listed in several playlists keeps its newest entry.
 * Tracks without a Telegram origin are left out, because they could never be looked up again.
 */
internal fun libraryTracks(library: MusicLibrary): List<PlaylistTrack> {
    val newest = HashMap<String, PlaylistTrack>()
    for (playlist in library.playlists) {
        for (track in playlist.tracks) {
            if (track.origin == null) continue
            val key = track.trackKey
            val known = newest[key]
            if (known == null || track.addedAt > known.addedAt) newest[key] = track
        }
    }
    return newest.values.sortedWith(compareByDescending<PlaylistTrack> { it.addedAt }.thenBy { it.trackKey })
}

/** The recently played tracks that still have their metadata, most recent first. */
internal fun recentTracks(library: MusicLibrary): List<PlaylistTrack> =
    library.recent.mapNotNull { entry -> library.stats[entry.trackKey]?.track?.toPlaylistEntry() }

/**
 * The tracks of a collection before any file check. [MusicCollectionKind.Downloaded] is narrowed later with
 * [downloadedTracks], once each track's file state is known.
 */
internal fun MusicCollectionKind.baseTracks(library: MusicLibrary, nowMs: Long): List<PlaylistTrack> = when (this) {
    MusicCollectionKind.Recent -> recentTracks(library)
    MusicCollectionKind.Downloaded -> libraryTracks(library).take(MUSIC_DOWNLOADED_SCAN_LIMIT)
    MusicCollectionKind.Frequent -> frequentlyPlayed(library).map { it.toPlaylistEntry() }
    MusicCollectionKind.Added -> recentlyAdded(library).map { it.toPlaylistEntry() }
    MusicCollectionKind.Lately -> notPlayedLately(library, nowMs).map { it.toPlaylistEntry() }
}

/** The smart collections shown in the Collections row, in display order. Collections with nothing in them are hidden. */
internal fun visibleSmartCollections(library: MusicLibrary, nowMs: Long): List<MusicCollectionKind> =
    listOf(MusicCollectionKind.Frequent, MusicCollectionKind.Added, MusicCollectionKind.Lately)
        .filter { it.baseTracks(library, nowMs).isNotEmpty() }

/**
 * Keeps the candidates whose resolved item has a local file. [resolved] lines up with [candidates]; a candidate without
 * a resolved item is not downloaded.
 */
internal fun downloadedTracks(candidates: List<PlaylistTrack>, resolved: List<PlaybackItem?>): List<PlaylistTrack> =
    candidates.filterIndexed { index, _ -> resolved.getOrNull(index)?.path?.isNotBlank() == true }

/**
 * Whether each entry of a playlist is unavailable. Nothing is unavailable before the lookup has finished ([resolved] is
 * null); after it, an entry without a resolved item is unavailable.
 */
internal fun unavailableFlags(size: Int, resolved: List<PlaybackItem?>?): List<Boolean> =
    List(size) { index -> resolved != null && resolved.getOrNull(index) == null }

/** Length of a track as m:ss, or h:mm:ss from an hour on. Empty when the length is unknown. */
internal fun formatClockDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    return if (hours > 0) "$hours:${minutes.twoDigits()}:${rest.twoDigits()}" else "$minutes:${rest.twoDigits()}"
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')

/** Total length of [tracks] in seconds. */
internal fun totalDurationSeconds(tracks: List<PlaylistTrack>): Long =
    tracks.sumOf { it.durationSeconds.coerceAtLeast(0).toLong() }

/** Hours and minutes of a total length. Partial minutes count up, so any length shows at least one minute. */
internal data class MusicDurationParts(val hours: Int, val minutes: Int)

internal fun durationParts(totalSeconds: Long): MusicDurationParts {
    val totalMinutes = (totalSeconds.coerceAtLeast(0L) + 59L) / 60L
    return MusicDurationParts(hours = (totalMinutes / 60L).toInt(), minutes = (totalMinutes % 60L).toInt())
}

/** Whether [track] is listed in [playlist]. Tracks are matched by their identity, not by the entry id. */
internal fun containsTrack(playlist: MusicPlaylist?, track: PlaylistTrack): Boolean =
    playlist?.tracks?.any { it.trackKey == track.trackKey } == true

/** Stored playlists in list order. The favorites playlist is shown on its own, so it is left out. */
internal fun userPlaylists(library: MusicLibrary): List<MusicPlaylist> = library.playlists.filterNot { it.isFavorites }

/**
 * The library as it may be shown for [accountId]. Null while the library of another account is still loaded, so the
 * previous account's tracks never appear under the new one.
 */
internal fun libraryFor(accountId: String, library: MusicLibrary): MusicLibrary? =
    library.takeIf { it.accountId == accountId }

/** A playlist that is not stored, used to play and show a collection the same way as a stored playlist. */
internal fun syntheticPlaylist(id: String, tracks: List<PlaylistTrack>): MusicPlaylist =
    MusicPlaylist(id = id, title = "", tracks = tracks)

internal fun canMoveUp(index: Int): Boolean = index > 0

internal fun canMoveDown(index: Int, size: Int): Boolean = index in 0 until size - 1

/**
 * The power-of-two decode sample that keeps the long side of a picture at least [maxSide] pixels. Bitmaps are scaled
 * down exactly after decoding, so this only has to avoid decoding far more pixels than needed.
 */
internal fun coverSampleSize(longSide: Int, maxSide: Int): Int {
    var sample = 1
    while (sample < MAX_COVER_SAMPLE && longSide / (sample * 2) >= maxSide) sample *= 2
    return sample
}

private const val MAX_COVER_SAMPLE = 1 shl 16
