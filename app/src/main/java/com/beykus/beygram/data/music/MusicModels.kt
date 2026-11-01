package com.beykus.beygram.data.music

import com.beykus.beygram.playback.PlaybackOrigin

/** Schema of the per-account library file. A document written by a newer schema is never overwritten. */
const val MUSIC_LIBRARY_SCHEMA_VERSION = 1

/** Fixed id of the built-in favorites playlist. It always exists in a library and can be neither deleted nor renamed. */
const val MUSIC_FAVORITES_PLAYLIST_ID = "00000000-0000-4000-8000-00000000f4f0"

/**
 * Stored title of the favorites playlist. Screens show a localized name for [MusicPlaylist.isFavorites] instead,
 * so this is only the value kept in the data.
 */
const val MUSIC_FAVORITES_TITLE = "Любимые"

/** Limits enforced by the repository. The favorites playlist counts as one playlist. */
const val MUSIC_MAX_PLAYLISTS = 500
const val MUSIC_MAX_TRACKS_PER_PLAYLIST = 5_000
const val MUSIC_MAX_TITLE_CHARS = 128
const val MUSIC_MAX_DESCRIPTION_CHARS = 1_000
const val MUSIC_MAX_RECENT_ENTRIES = 200

/** Order in which a playlist's tracks are shown. [Manual] is the stored order; the others are applied once, when picked. */
enum class MusicSortMode { Manual, Title, Artist, AddedAt, Duration }

/**
 * One entry of a playlist. [itemId] is unique inside its playlist and never changes there, so the same Telegram
 * message may appear in several playlists as separate entries.
 */
data class PlaylistTrack(
    val itemId: String,
    val title: String,
    val performer: String,
    val album: String,
    val durationSeconds: Int,
    /** Local hint for finding the message again. Null for tracks that are unavailable or came from an import. */
    val origin: PlaybackOrigin?,
    val addedAt: Long,
    val audioHash: String? = null
) {
    /** Identity shared by playlists, listen stats and duplicate checks. */
    val trackKey: String get() = musicTrackKey(origin, audioHash, itemId)
}

/** A user playlist. Every change to it bumps [revision] and [updatedAt]. */
data class MusicPlaylist(
    val id: String,
    val title: String,
    val description: String = "",
    /** Path of an app-private cover file. Content URIs and files outside the app are never stored. */
    val coverPath: String? = null,
    val tracks: List<PlaylistTrack> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val revision: Long = 0L,
    /** Set only on copies that came from an import. */
    val sourcePlaylistId: String? = null,
    val sortMode: MusicSortMode = MusicSortMode.Manual
) {
    val isFavorites: Boolean get() = id == MUSIC_FAVORITES_PLAYLIST_ID
}

/**
 * Metadata of a track that does not depend on any playlist. Listen history, stats and smart collections keep this
 * so a track stays listable after it has been removed from every playlist.
 */
data class MusicTrackRef(
    val trackKey: String,
    val title: String,
    val performer: String = "",
    val album: String = "",
    val durationSeconds: Int = 0,
    val origin: PlaybackOrigin? = null,
    val audioHash: String? = null
)

/** Listen statistics of one track. [track] is the last metadata seen for it. */
data class MusicListenStats(
    val track: MusicTrackRef,
    val playCount: Int = 0,
    /** Epoch millis of the latest recorded start, or null when the track was never started. */
    val lastPlayedAt: Long? = null,
    /** Epoch millis of the first time the track was added to any playlist. */
    val firstAddedAt: Long? = null
) {
    val trackKey: String get() = track.trackKey
}

/** One "recently played" entry. The history holds each track once, newest first. */
data class MusicHistoryEntry(val trackKey: String, val playedAt: Long)

/** The library of one account. Nothing in it is shared with another account id. */
data class MusicLibrary(
    val accountId: String,
    val playlists: List<MusicPlaylist>,
    val stats: Map<String, MusicListenStats> = emptyMap(),
    val recent: List<MusicHistoryEntry> = emptyList()
) {
    fun playlist(id: String): MusicPlaylist? = playlists.firstOrNull { it.id == id }

    /** The built-in favorites playlist, or null only for a library that was not normalized with [withFavorites]. */
    val favorites: MusicPlaylist? get() = playlists.firstOrNull { it.isFavorites }

    companion object {
        /** A library with no user data: only the built-in favorites playlist. */
        fun empty(accountId: String, now: Long): MusicLibrary =
            MusicLibrary(accountId = accountId, playlists = listOf(favoritesPlaylist(now)))
    }
}

/** The built-in favorites playlist. */
fun favoritesPlaylist(now: Long): MusicPlaylist = MusicPlaylist(
    id = MUSIC_FAVORITES_PLAYLIST_ID,
    title = MUSIC_FAVORITES_TITLE,
    createdAt = now,
    updatedAt = now
)

/** Makes sure the favorites playlist exists, placed first. Used for libraries read from disk. */
internal fun MusicLibrary.withFavorites(now: Long): MusicLibrary =
    if (favorites != null) this else copy(playlists = listOf(favoritesPlaylist(now)) + playlists)

/**
 * Identity of a track across playlists, stats and history. The Telegram origin wins, then the file hash, and a track
 * with neither is identified by its own entry id.
 */
fun musicTrackKey(origin: PlaybackOrigin?, audioHash: String?, entryId: String): String = when {
    origin != null -> "tg:${origin.accountId}:${origin.chatId}:${origin.messageId}"
    !audioHash.isNullOrBlank() -> "hash:$audioHash"
    else -> "item:$entryId"
}

/** Why a library change was refused. A refused change leaves the stored library exactly as it was. */
enum class MusicFailure {
    NoActiveAccount,
    StorageUnavailable,
    NotFound,
    LimitReached,
    InvalidTitle,
    InvalidPosition,
    ProtectedPlaylist,
    ForeignAccount,
    InvalidCover
}

/** Result of a repository call. */
sealed interface MusicOutcome<out T> {
    data class Done<out T>(val value: T) : MusicOutcome<T>
    data class Failed(val failure: MusicFailure) : MusicOutcome<Nothing>
}

/** What [MusicLibraryRepository.addTracks] did with a batch. */
data class AddTracksResult(
    val added: Int,
    /** The same Telegram origin was already in the playlist, or appeared earlier in this batch. */
    val duplicates: Int,
    /** Over the per-playlist limit, or from another account. */
    val rejected: Int
)
