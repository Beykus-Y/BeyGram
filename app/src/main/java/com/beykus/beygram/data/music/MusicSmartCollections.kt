package com.beykus.beygram.data.music

/** Largest list a smart collection returns. */
const val MUSIC_SMART_LIMIT = 50

/** How long a track must have been silent to appear in [notPlayedLately]. */
const val MUSIC_NOT_PLAYED_LATELY_MS = 30L * 24 * 60 * 60 * 1000

/** Tracks started at least twice, most started first. */
fun frequentlyPlayed(library: MusicLibrary): List<MusicTrackRef> =
    library.stats.values
        .filter { it.playCount >= 2 }
        .sortedWith(
            compareByDescending<MusicListenStats> { it.playCount }
                .thenByDescending { it.lastPlayedAt ?: 0L }
                .thenBy { it.trackKey }
        )
        .take(MUSIC_SMART_LIMIT)
        .map { it.track }

/** Tracks added to any playlist, newest addition first. Each track appears once, at its newest addition. */
fun recentlyAdded(library: MusicLibrary): List<MusicTrackRef> =
    latestAdditions(library.playlists.asSequence().flatMap { it.tracks.asSequence() })
        .take(MUSIC_SMART_LIMIT)
        .map { it.toTrackRef() }

/**
 * Tracks that were started before, but not within the last [MUSIC_NOT_PLAYED_LATELY_MS]. Most-started tracks come first,
 * and among them the ones played longest ago.
 */
fun notPlayedLately(library: MusicLibrary, nowMs: Long): List<MusicTrackRef> =
    library.stats.values
        .filter { it.isNotPlayedLately(nowMs) }
        .sortedWith(
            compareByDescending<MusicListenStats> { it.playCount }
                .thenBy { it.lastPlayedAt ?: 0L }
                .thenBy { it.trackKey }
        )
        .take(MUSIC_SMART_LIMIT)
        .map { it.track }

/** Every playlist track that came from [chatId], newest addition first. */
fun fromChat(library: MusicLibrary, chatId: Long): List<MusicTrackRef> =
    latestAdditions(
        library.playlists.asSequence()
            .flatMap { it.tracks.asSequence() }
            .filter { it.origin?.chatId == chatId }
    ).map { it.toTrackRef() }

private fun MusicListenStats.isNotPlayedLately(nowMs: Long): Boolean {
    val last = lastPlayedAt ?: return false
    return nowMs - last > MUSIC_NOT_PLAYED_LATELY_MS
}

/** One entry per track key, the one added most recently, sorted newest first. */
private fun latestAdditions(tracks: Sequence<PlaylistTrack>): List<PlaylistTrack> {
    val newest = HashMap<String, PlaylistTrack>()
    for (track in tracks) {
        val key = track.trackKey
        val known = newest[key]
        if (known == null || track.addedAt > known.addedAt) newest[key] = track
    }
    return newest.values.sortedWith(
        compareByDescending<PlaylistTrack> { it.addedAt }.thenBy { it.trackKey }
    )
}
