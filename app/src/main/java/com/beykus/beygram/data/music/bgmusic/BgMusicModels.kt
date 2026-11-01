package com.beykus.beygram.data.music.bgmusic

/**
 * A playlist snapshot stored inside a `.bgmusic` archive.
 *
 * Pure data with no dependency on the rest of the app. Values are expected in the sanitized form that
 * [BgMusicReader] returns; [BgMusicWriter] sanitizes and validates them before anything is written.
 * See docs/formats/bgmusic-v1.md.
 */
data class BgMusicManifest(
    /** Canonical lowercase UUID that identifies the playlist across exports and imports. */
    val playlistId: String,
    /** Owner-incremented change counter (>= 0). Used to detect duplicate and newer copies on import. */
    val revision: Long,
    /** Playlist title, 1..[BgMusicFormat.MAX_PLAYLIST_TITLE] code points after sanitizing. */
    val title: String,
    /** Last change time in epoch milliseconds (UTC). */
    val updatedAtMs: Long,
    /** Tracks in playlist order. Order is significant and preserved. */
    val tracks: List<BgMusicTrack>,
    /** Optional free text, at most [BgMusicFormat.MAX_DESCRIPTION] code points. Empty when absent. */
    val description: String = "",
    /** Included only when the exporter has the user's explicit consent. Null means no author is written. */
    val author: BgMusicAuthor? = null,
    /** Version of the format the file was written in. The v1 writer always writes 1. */
    val formatVersion: Int = BgMusicFormat.FORMAT_VERSION,
    /** Lowest reader version that can interpret the file correctly. The v1 writer always writes 1. */
    val minReaderVersion: Int = BgMusicFormat.FORMAT_VERSION,
)

/** One playlist entry. [id] is the stable item id, unique inside its playlist, and never reused. */
data class BgMusicTrack(
    /** Canonical lowercase UUID, unique inside the playlist. */
    val id: String,
    /** 1..[BgMusicFormat.MAX_TRACK_TITLE] code points after sanitizing. */
    val title: String,
    /** At most [BgMusicFormat.MAX_ARTIST] code points. Empty when unknown. */
    val artist: String = "",
    /** At most [BgMusicFormat.MAX_ALBUM] code points. Empty when unknown. */
    val album: String = "",
    /** Whole seconds, 0..[BgMusicFormat.MAX_DURATION_SEC]. 0 means the duration is unknown. */
    val durationSec: Long = 0L,
    /** `sha256:<64 lowercase hex>` of the audio bytes, only when the exporter actually computed it. */
    val audioHash: String? = null,
    /** Optional hints where the track can be found again. Unknown hint types are dropped on read. */
    val sources: List<BgMusicSourceHint> = emptyList(),
)

/**
 * A hint about where a track can be found again. It is never a promise of access.
 * v1 defines only [TelegramPublic]; unknown source types are ignored when reading.
 */
sealed class BgMusicSourceHint {
    /** A post in a public Telegram channel or group, addressed by its public username (without "@"). */
    data class TelegramPublic(
        val username: String,
        val messageId: Long,
    ) : BgMusicSourceHint()
}

/** The person who created the playlist. Written only with the owner's explicit consent. */
data class BgMusicAuthor(
    /** 1..[BgMusicFormat.MAX_AUTHOR_NAME] code points after sanitizing. */
    val name: String,
)
