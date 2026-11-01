package com.beykus.beygram.data.music.bgmusic

/**
 * What to offer the user when a `.bgmusic` file is opened. The decision is pure: the caller shows the matching
 * UI, and nothing is merged, replaced or copied silently.
 */
sealed class BgMusicImportDecision {
    /** No local playlist has this playlist_id. Import it as a new playlist. */
    data object NewPlaylist : BgMusicImportDecision()

    /** The local copy already has this revision. Nothing new to import. */
    data class SameRevision(val localRevision: Long) : BgMusicImportDecision()

    /** The file is a newer revision of a local playlist. Offer update: merge, replace, or import as a copy. */
    data class NewerRevision(val localRevision: Long) : BgMusicImportDecision()

    /** The file is older than the local copy. Warn, and offer to import it as a copy or to cancel. */
    data class OlderRevision(val localRevision: Long) : BgMusicImportDecision()

    companion object {
        /**
         * Compares an incoming file with the local playlists.
         *
         * @param playlistId the incoming playlist_id.
         * @param revision the incoming revision.
         * @param existing pairs of (playlist_id, revision) for local playlists. IDs compare case-insensitively.
         *   If an ID appears more than once, its highest revision is used.
         */
        fun decide(
            playlistId: String,
            revision: Long,
            existing: Iterable<Pair<String, Long>>,
        ): BgMusicImportDecision {
            val key = playlistId.trim().lowercase()
            val localRevision = existing
                .filter { (id, _) -> id.trim().lowercase() == key }
                .maxOfOrNull { (_, storedRevision) -> storedRevision }
                ?: return NewPlaylist
            return when {
                revision == localRevision -> SameRevision(localRevision)
                revision > localRevision -> NewerRevision(localRevision)
                else -> OlderRevision(localRevision)
            }
        }
    }
}
