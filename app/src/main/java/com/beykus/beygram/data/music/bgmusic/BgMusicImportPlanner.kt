package com.beykus.beygram.data.music.bgmusic

import com.beykus.beygram.data.music.MusicPlaylist

/**
 * Matches an incoming `.bgmusic` file with the local library. Pure: it only reads the library and never changes it.
 * The screens act on the result, and nothing is merged, replaced or copied without the user's choice.
 *
 * An imported copy is compared by the revision of the file it was saved from ([MusicPlaylist.fileRevision]), so edits made in the
 * copy never change the answer. Any other playlist is compared by its own revision.
 */
object BgMusicImportPlanner {

    /**
     * What to offer for a file with [playlistId] and [revision]. Favorites never take part in matching, because every
     * account has one with the same fixed id.
     */
    fun decide(playlistId: String, revision: Long, library: List<MusicPlaylist>): BgMusicImportDecision =
        BgMusicImportDecision.decide(
            playlistId = playlistId,
            revision = revision,
            existing = matchable(library).map { playlist -> matchKey(playlist) to playlist.fileRevision },
        )

    /**
     * The local playlist that a file with [playlistId] would update: the one with the same key and the highest revision when
     * several match. Null when the file is new to this library.
     */
    fun matchingCopy(playlistId: String, library: List<MusicPlaylist>): MusicPlaylist? {
        val key = playlistId.trim().lowercase()
        return matchable(library)
            .filter { matchKey(it).trim().lowercase() == key }
            .maxByOrNull { it.fileRevision }
    }

    private fun matchable(library: List<MusicPlaylist>): List<MusicPlaylist> = library.filterNot { it.isFavorites }

    /** An imported copy is identified by the playlist it came from, so both match the same file. */
    private fun matchKey(playlist: MusicPlaylist): String = playlist.sourcePlaylistId ?: playlist.id
}
