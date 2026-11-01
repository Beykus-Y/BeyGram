package com.beykus.beygram.data.music

import com.beykus.beygram.playback.PlaybackOrigin

/**
 * Pure rules of the library. Each function returns the changed library together with the value to report. A change that
 * does nothing returns the very same library instance, which the repository uses to skip the write.
 */
internal class MusicEdit<out T>(val library: MusicLibrary, val value: T)

internal typealias MusicEditOutcome<T> = MusicOutcome<MusicEdit<T>>

/** Keeps at most [maxCodePoints] code points, so an emoji is never cut in half. */
internal fun truncateCodePoints(value: String, maxCodePoints: Int): String {
    if (value.codePointCount(0, value.length) <= maxCodePoints) return value
    return value.substring(0, value.offsetByCodePoints(0, maxCodePoints))
}

/** The trimmed title within the limit, or null when nothing is left of it. */
internal fun normalizeMusicTitle(raw: String): String? =
    truncateCodePoints(raw.trim(), MUSIC_MAX_TITLE_CHARS).trim().takeIf { it.isNotEmpty() }

internal fun normalizeMusicDescription(raw: String): String =
    truncateCodePoints(raw.trim(), MUSIC_MAX_DESCRIPTION_CHARS).trim()

/**
 * Title of a copy: the base title plus a short suffix supplied by the caller (the suffix text is a UI string). The base is
 * shortened first, so the whole title stays within the limit.
 */
internal fun titleWithCopySuffix(base: String, suffix: String): String {
    val cleanSuffix = suffix.trim()
    if (cleanSuffix.isEmpty()) return base
    val tail = " " + truncateCodePoints(cleanSuffix, COPY_SUFFIX_MAX_CHARS)
    val room = MUSIC_MAX_TITLE_CHARS - tail.codePointCount(0, tail.length)
    return truncateCodePoints(base, room).trimEnd() + tail
}

private const val COPY_SUFFIX_MAX_CHARS = 32

/** Every playlist mutation bumps its revision and its update time. */
private fun MusicPlaylist.touched(now: Long): MusicPlaylist = copy(updatedAt = now, revision = revision + 1)

/** Replaces the playlist that has the same id as [updated]. */
private fun MusicLibrary.replacing(updated: MusicPlaylist): MusicLibrary =
    copy(playlists = playlists.map { if (it.id == updated.id) updated else it })

internal fun createPlaylistEdit(
    library: MusicLibrary,
    title: String,
    description: String,
    playlistId: String,
    now: Long
): MusicEditOutcome<MusicPlaylist> {
    val cleanTitle = normalizeMusicTitle(title) ?: return MusicOutcome.Failed(MusicFailure.InvalidTitle)
    if (library.playlists.size >= MUSIC_MAX_PLAYLISTS) return MusicOutcome.Failed(MusicFailure.LimitReached)
    val playlist = MusicPlaylist(
        id = playlistId,
        title = cleanTitle,
        description = normalizeMusicDescription(description),
        createdAt = now,
        updatedAt = now,
        revision = 0L
    )
    return MusicOutcome.Done(MusicEdit(library.copy(playlists = library.playlists + playlist), playlist))
}

internal fun renamePlaylistEdit(
    library: MusicLibrary,
    playlistId: String,
    title: String,
    now: Long
): MusicEditOutcome<Unit> {
    val cleanTitle = normalizeMusicTitle(title) ?: return MusicOutcome.Failed(MusicFailure.InvalidTitle)
    val target = library.playlist(playlistId) ?: return MusicOutcome.Failed(MusicFailure.NotFound)
    // The favorites title is shown in the localized UI, so a stored custom name would never be seen.
    if (target.isFavorites) return MusicOutcome.Failed(MusicFailure.ProtectedPlaylist)
    if (target.title == cleanTitle) return MusicOutcome.Done(MusicEdit(library, Unit))
    val updated = target.copy(title = cleanTitle).touched(now)
    return MusicOutcome.Done(MusicEdit(library.replacing(updated), Unit))
}

internal fun updateDescriptionEdit(
    library: MusicLibrary,
    playlistId: String,
    description: String,
    now: Long
): MusicEditOutcome<Unit> {
    val target = library.playlist(playlistId) ?: return MusicOutcome.Failed(MusicFailure.NotFound)
    val cleanDescription = normalizeMusicDescription(description)
    if (target.description == cleanDescription) return MusicOutcome.Done(MusicEdit(library, Unit))
    val updated = target.copy(description = cleanDescription).touched(now)
    return MusicOutcome.Done(MusicEdit(library.replacing(updated), Unit))
}

/** [coverPath] has already been checked by the repository to be a file inside the app's cover directory. */
internal fun setCoverEdit(
    library: MusicLibrary,
    playlistId: String,
    coverPath: String?,
    now: Long
): MusicEditOutcome<Unit> {
    val target = library.playlist(playlistId) ?: return MusicOutcome.Failed(MusicFailure.NotFound)
    if (target.coverPath == coverPath) return MusicOutcome.Done(MusicEdit(library, Unit))
    val updated = target.copy(coverPath = coverPath).touched(now)
    return MusicOutcome.Done(MusicEdit(library.replacing(updated), Unit))
}

internal fun deletePlaylistEdit(library: MusicLibrary, playlistId: String): MusicEditOutcome<Unit> {
    val target = library.playlist(playlistId) ?: return MusicOutcome.Failed(MusicFailure.NotFound)
    if (target.isFavorites) return MusicOutcome.Failed(MusicFailure.ProtectedPlaylist)
    val remaining = library.playlists.filterNot { it.id == target.id }
    return MusicOutcome.Done(MusicEdit(library.copy(playlists = remaining), Unit))
}

/**
 * A copy gets its own id and its own entry ids, so the two playlists never share a track entry. Import links
 * ([MusicPlaylist.sourcePlaylistId]) are not copied; a duplicate is a local playlist. [copiedCover] is the copy's own cover
 * file, prepared by the repository, so the source's cover file is never shared.
 */
internal fun duplicatePlaylistEdit(
    library: MusicLibrary,
    sourceId: String,
    copySuffix: String,
    copyId: String,
    now: Long,
    newItemId: () -> String,
    copiedCover: String?
): MusicEditOutcome<MusicPlaylist> {
    val source = library.playlist(sourceId) ?: return MusicOutcome.Failed(MusicFailure.NotFound)
    if (library.playlists.size >= MUSIC_MAX_PLAYLISTS) return MusicOutcome.Failed(MusicFailure.LimitReached)
    val copy = MusicPlaylist(
        id = copyId,
        title = titleWithCopySuffix(source.title, copySuffix),
        description = source.description,
        coverPath = copiedCover,
        tracks = source.tracks.map { it.copy(itemId = newItemId()) },
        createdAt = now,
        updatedAt = now,
        revision = 0L,
        sourcePlaylistId = null,
        sortMode = source.sortMode
    )
    return MusicOutcome.Done(MusicEdit(library.copy(playlists = library.playlists + copy), copy))
}

/**
 * Adds tracks to the end of a playlist. A track whose origin is already in the playlist, or appeared earlier in the same
 * batch, is skipped. Tracks beyond the per-playlist limit and tracks from another account are rejected. An entry id that
 * is blank or already used in the playlist gets a fresh id. Appending breaks any previous sort, so the mode returns to
 * [MusicSortMode.Manual].
 */
internal fun addTracksEdit(
    library: MusicLibrary,
    playlistId: String,
    tracks: List<PlaylistTrack>,
    now: Long,
    newItemId: () -> String
): MusicEditOutcome<AddTracksResult> {
    val target = library.playlist(playlistId) ?: return MusicOutcome.Failed(MusicFailure.NotFound)
    val knownOrigins = HashSet<PlaybackOrigin>()
    for (existing in target.tracks) {
        existing.origin?.let { knownOrigins.add(it) }
    }
    val takenIds = HashSet<String>(target.tracks.map { it.itemId })
    var room = MUSIC_MAX_TRACKS_PER_PLAYLIST - target.tracks.size
    var duplicates = 0
    var rejected = 0
    val accepted = ArrayList<PlaylistTrack>()
    for (track in tracks) {
        val origin = track.origin
        if (origin != null && origin.accountId != library.accountId) {
            rejected++
        } else if (origin != null && !knownOrigins.add(origin)) {
            duplicates++
        } else if (room <= 0) {
            rejected++
        } else {
            room--
            var itemId = track.itemId
            if (itemId.isBlank() || !takenIds.add(itemId)) {
                do {
                    itemId = newItemId()
                } while (!takenIds.add(itemId))
            }
            val addedAt = if (track.addedAt > 0L) track.addedAt else now
            accepted += track.copy(itemId = itemId, addedAt = addedAt)
        }
    }
    val result = AddTracksResult(added = accepted.size, duplicates = duplicates, rejected = rejected)
    if (accepted.isEmpty()) return MusicOutcome.Done(MusicEdit(library, result))
    val updated = target.copy(
        tracks = target.tracks + accepted,
        sortMode = MusicSortMode.Manual
    ).touched(now)
    val withStats = library.copy(stats = statsAfterAdding(library.stats, accepted))
    val next = withStats.replacing(updated)
    return MusicOutcome.Done(MusicEdit(next.copy(stats = boundedStats(next.stats, next.playlists)), result))
}

/** The first time a track is added anywhere, its stats record that moment; metadata follows the latest copy. */
private fun statsAfterAdding(
    stats: Map<String, MusicListenStats>,
    added: List<PlaylistTrack>
): Map<String, MusicListenStats> {
    val next = stats.toMutableMap()
    for (track in added) {
        val key = track.trackKey
        val current = next[key] ?: MusicListenStats(track = track.toTrackRef())
        next[key] = current.copy(
            track = track.toTrackRef(),
            firstAddedAt = current.firstAddedAt ?: track.addedAt
        )
    }
    return next
}

internal fun removeTrackEdit(
    library: MusicLibrary,
    playlistId: String,
    itemId: String,
    now: Long
): MusicEditOutcome<Unit> {
    val target = library.playlist(playlistId) ?: return MusicOutcome.Failed(MusicFailure.NotFound)
    val remaining = target.tracks.filterNot { it.itemId == itemId }
    if (remaining.size == target.tracks.size) return MusicOutcome.Failed(MusicFailure.NotFound)
    val updated = target.copy(tracks = remaining).touched(now)
    return MusicOutcome.Done(MusicEdit(library.replacing(updated), Unit))
}

/**
 * Puts [track] in the place of the entry [itemId]. The entry keeps its id, its position in the playlist and the time it was
 * added; everything else comes from [track]. A track without a Telegram origin, a track from another account, and a track
 * that is already listed in this playlist under another entry are refused.
 */
internal fun replaceTrackEdit(
    library: MusicLibrary,
    playlistId: String,
    itemId: String,
    track: PlaylistTrack,
    now: Long
): MusicEditOutcome<Unit> {
    val target = library.playlist(playlistId) ?: return MusicOutcome.Failed(MusicFailure.NotFound)
    val index = target.tracks.indexOfFirst { it.itemId == itemId }
    if (index < 0) return MusicOutcome.Failed(MusicFailure.NotFound)
    val origin = track.origin ?: return MusicOutcome.Failed(MusicFailure.InvalidTrack)
    if (origin.accountId != library.accountId) return MusicOutcome.Failed(MusicFailure.ForeignAccount)
    val current = target.tracks[index]
    val replacement = track.copy(itemId = itemId, addedAt = current.addedAt)
    if (replacement == current) return MusicOutcome.Done(MusicEdit(library, Unit))
    if (target.tracks.any { it.itemId != itemId && it.trackKey == replacement.trackKey }) {
        return MusicOutcome.Failed(MusicFailure.DuplicateTrack)
    }
    val tracks = target.tracks.toMutableList()
    tracks[index] = replacement
    val updated = target.copy(tracks = tracks).touched(now)
    // The new track counts as added to a playlist, like any other track that enters one.
    val withStats = library.copy(stats = statsAfterAdding(library.stats, listOf(replacement)))
    val next = withStats.replacing(updated)
    return MusicOutcome.Done(MusicEdit(next.copy(stats = boundedStats(next.stats, next.playlists)), Unit))
}

/** Moving a track by hand makes the order manual again. */
internal fun moveTrackEdit(
    library: MusicLibrary,
    playlistId: String,
    from: Int,
    to: Int,
    now: Long
): MusicEditOutcome<Unit> {
    val target = library.playlist(playlistId) ?: return MusicOutcome.Failed(MusicFailure.NotFound)
    if (from !in target.tracks.indices || to !in target.tracks.indices) {
        return MusicOutcome.Failed(MusicFailure.InvalidPosition)
    }
    if (from == to) return MusicOutcome.Done(MusicEdit(library, Unit))
    val tracks = target.tracks.toMutableList()
    val moved = tracks.removeAt(from)
    tracks.add(to, moved)
    val updated = target.copy(tracks = tracks, sortMode = MusicSortMode.Manual).touched(now)
    return MusicOutcome.Done(MusicEdit(library.replacing(updated), Unit))
}

/**
 * Applies a sort once and stores the result as the playlist's order. Picking [MusicSortMode.Manual] keeps the current
 * order. Nothing sorts automatically later; adding tracks resets the mode as described in [addTracksEdit].
 */
internal fun sortPlaylistEdit(
    library: MusicLibrary,
    playlistId: String,
    mode: MusicSortMode,
    now: Long
): MusicEditOutcome<Unit> {
    val target = library.playlist(playlistId) ?: return MusicOutcome.Failed(MusicFailure.NotFound)
    val sorted = sortedTracks(target.tracks, mode)
    if (sorted == target.tracks && mode == target.sortMode) return MusicOutcome.Done(MusicEdit(library, Unit))
    val updated = target.copy(tracks = sorted, sortMode = mode).touched(now)
    return MusicOutcome.Done(MusicEdit(library.replacing(updated), Unit))
}

/** Stable sort by the chosen key; the entry id breaks ties so the result never depends on the input order. */
internal fun sortedTracks(tracks: List<PlaylistTrack>, mode: MusicSortMode): List<PlaylistTrack> = when (mode) {
    MusicSortMode.Manual -> tracks
    MusicSortMode.Title -> tracks.sortedWith(
        compareBy<PlaylistTrack> { it.title.lowercase() }
            .thenBy { it.performer.lowercase() }
            .thenBy { it.itemId }
    )
    MusicSortMode.Artist -> tracks.sortedWith(
        compareBy<PlaylistTrack> { it.performer.lowercase() }
            .thenBy { it.album.lowercase() }
            .thenBy { it.title.lowercase() }
            .thenBy { it.itemId }
    )
    MusicSortMode.AddedAt -> tracks.sortedWith(
        compareBy<PlaylistTrack> { it.addedAt }.thenBy { it.itemId }
    )
    MusicSortMode.Duration -> tracks.sortedWith(
        compareBy<PlaylistTrack> { it.durationSeconds }
            .thenBy { it.title.lowercase() }
            .thenBy { it.itemId }
    )
}

/**
 * Adds the track to favorites, or removes every favorites entry with the same identity. Returns whether the track is a
 * favorite afterwards.
 */
internal fun toggleFavoriteEdit(
    library: MusicLibrary,
    track: PlaylistTrack,
    now: Long,
    newItemId: () -> String
): MusicEditOutcome<Boolean> {
    val origin = track.origin
    if (origin != null && origin.accountId != library.accountId) return MusicOutcome.Failed(MusicFailure.ForeignAccount)
    val favorites = library.favorites ?: return MusicOutcome.Failed(MusicFailure.NotFound)
    val key = track.trackKey
    if (favorites.tracks.any { it.trackKey == key }) {
        val remaining = favorites.tracks.filterNot { it.trackKey == key }
        val updated = favorites.copy(tracks = remaining).touched(now)
        return MusicOutcome.Done(MusicEdit(library.replacing(updated), false))
    }
    return when (val added = addTracksEdit(library, favorites.id, listOf(track), now, newItemId)) {
        is MusicOutcome.Failed -> added
        is MusicOutcome.Done ->
            if (added.value.value.added > 0) {
                MusicOutcome.Done(MusicEdit(added.value.library, true))
            } else {
                MusicOutcome.Failed(MusicFailure.LimitReached)
            }
    }
}

/**
 * One listen: the count goes up, the last start moves forward, the track's metadata is refreshed, and the history gets
 * the track at the top (each track appears once in the history).
 */
internal fun recordPlayEdit(library: MusicLibrary, track: MusicTrackRef, playedAt: Long): MusicEditOutcome<Unit> {
    val origin = track.origin
    if (origin != null && origin.accountId != library.accountId) return MusicOutcome.Failed(MusicFailure.ForeignAccount)
    val key = track.trackKey
    val previous = library.stats[key]
    val stats = library.stats.toMutableMap()
    stats[key] = MusicListenStats(
        track = track,
        playCount = (previous?.playCount ?: 0) + 1,
        lastPlayedAt = maxOf(previous?.lastPlayedAt ?: playedAt, playedAt),
        firstAddedAt = previous?.firstAddedAt
    )
    val recent = (listOf(MusicHistoryEntry(trackKey = key, playedAt = playedAt)) +
        library.recent.filterNot { it.trackKey == key })
        .take(MUSIC_MAX_RECENT_ENTRIES)
    val bounded = boundedStats(stats, library.playlists, keepKey = key)
    return MusicOutcome.Done(MusicEdit(library.copy(stats = bounded, recent = recent), Unit))
}

/**
 * Keeps the stats map within [MUSIC_MAX_STATS]. Entries of tracks that are still in a playlist are always kept, and
 * [keepKey] is never evicted. The rest go least recently played first; an entry that was never played goes before any that
 * was. The map stays above the limit only when the playlists themselves hold more distinct tracks than that.
 */
internal fun boundedStats(
    stats: Map<String, MusicListenStats>,
    playlists: List<MusicPlaylist>,
    keepKey: String? = null
): Map<String, MusicListenStats> {
    if (stats.size <= MUSIC_MAX_STATS) return stats
    val inPlaylists = HashSet<String>()
    for (playlist in playlists) {
        for (track in playlist.tracks) inPlaylists.add(track.trackKey)
    }
    val candidates = stats.values
        .filter { it.trackKey !in inPlaylists && it.trackKey != keepKey }
        .sortedWith(compareBy<MusicListenStats> { it.lastPlayedAt ?: it.firstAddedAt ?: 0L }.thenBy { it.trackKey })
    val evicted = candidates.take(stats.size - MUSIC_MAX_STATS).mapTo(HashSet()) { it.trackKey }
    return stats.filterKeys { it !in evicted }
}
