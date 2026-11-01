package com.beykus.beygram.data.music.bgmusic

import com.beykus.beygram.data.music.MusicPlaylist
import com.beykus.beygram.data.music.MusicSortMode
import com.beykus.beygram.data.music.PlaylistTrack
import com.beykus.beygram.playback.PlaybackOrigin
import java.util.UUID

/**
 * What the user agreed to write into an exported file. Both switches are off by default, so a plain export holds only
 * the playlist's own text and the track descriptions.
 */
data class BgMusicExportOptions(
    /** Adds `telegram_public` hints for tracks whose post is in a public channel or group. */
    val includePublicLinks: Boolean = false,
    /** Writes [authorName] as the playlist author. */
    val includeAuthor: Boolean = false,
    val authorName: String = "",
)

/** The `playlist_id` and `revision` that a playlist is written with. */
internal data class BgMusicExportIdentity(val playlistId: String, val revision: Long)

/**
 * Converts between local playlists and `.bgmusic` manifests. Every function is pure: anything that needs Telegram or
 * Android (the public usernames, the clock, the localized favorites title, the resolved origins) is passed in, so the
 * privacy rules in docs/formats/bgmusic-v1.md section 9 can be tested on the JVM.
 */
object BgMusicLibraryMapping {

    /** TDLib message ids are the public post number shifted left by this many bits. The low bits are local flags. */
    const val TDLIB_MESSAGE_ID_SHIFT = 20

    private const val TDLIB_LOCAL_BITS_MASK = (1L shl TDLIB_MESSAGE_ID_SHIFT) - 1

    /**
     * The `playlist_id` and `revision` written to a file for [playlist]:
     * - An imported copy with no local change since its last import or update ([MusicPlaylist.revisionAtSource] equals
     *   [MusicPlaylist.revision]) is written as the file it came from: its source id and its source revision. Receivers see the
     *   same collection at the same revision.
     * - An imported copy with local changes is a collection of the user's own: its local id and its local revision. Receivers
     *   get a new collection, so they never see changed content as already saved.
     * - Any other playlist is written with its own id and revision. Favorites has one fixed id on every account, so every export
     *   of it gets a new id. A local id that is not a UUID is replaced by a fresh one as well.
     */
    internal fun exportIdentity(
        playlist: MusicPlaylist,
        freshId: () -> String = { UUID.randomUUID().toString() },
    ): BgMusicExportIdentity {
        val sourceId = playlist.sourcePlaylistId?.let { BgMusicFormat.normalizeUuid(it) }
        val sourceRevision = playlist.sourceRevision
        if (sourceId != null && sourceRevision != null && playlist.revisionAtSource == playlist.revision) {
            return BgMusicExportIdentity(playlistId = sourceId, revision = sourceRevision.coerceAtLeast(0L))
        }
        val ownId = if (playlist.isFavorites) freshId() else (BgMusicFormat.normalizeUuid(playlist.id) ?: freshId())
        return BgMusicExportIdentity(playlistId = ownId, revision = playlist.revision.coerceAtLeast(0L))
    }

    /** The `playlist_id` written to a file for [playlist]; see [exportIdentity]. */
    fun exportPlaylistId(playlist: MusicPlaylist, freshId: () -> String = { UUID.randomUUID().toString() }): String =
        exportIdentity(playlist, freshId).playlistId

    /**
     * Manifest of [playlist] as seen from [accountId]. Its `playlist_id` and `revision` follow [exportIdentity]: an imported copy
     * without local changes is written as the file it came from.
     *
     * Track ids come from the app's own track ids, never from Telegram data. The audio hash is never written. Sources are
     * written only for an origin of [accountId] whose chat is in [publicChats] (chat id to public username, for channels and
     * groups only) and only when [BgMusicExportOptions.includePublicLinks] is on. The author is written only when
     * [BgMusicExportOptions.includeAuthor] is on.
     *
     * @param nowMs the export time, used as `updated_at` after clamping to the format range.
     * @param favoritesTitle the localized name of favorites, written instead of the stored title.
     * @param untitled the fallback for a blank title, because the format requires one.
     */
    fun toManifest(
        playlist: MusicPlaylist,
        accountId: String,
        options: BgMusicExportOptions,
        publicChats: Map<Long, String>,
        nowMs: Long,
        favoritesTitle: String,
        untitled: String,
        freshId: () -> String = { UUID.randomUUID().toString() },
    ): BgMusicManifest {
        val identity = exportIdentity(playlist, freshId)
        val playlistId = identity.playlistId
        val rawTitle = if (playlist.isFavorites) favoritesTitle else playlist.title
        val title = BgMusicFormat.sanitizeText(rawTitle, BgMusicFormat.MAX_PLAYLIST_TITLE)
            .ifEmpty { BgMusicFormat.sanitizeText(untitled, BgMusicFormat.MAX_PLAYLIST_TITLE) }
        return BgMusicManifest(
            playlistId = playlistId,
            revision = identity.revision,
            title = title,
            updatedAtMs = nowMs.coerceIn(0L, BgMusicFormat.MAX_UPDATED_AT_MS),
            tracks = playlist.tracks.map { track ->
                trackToBgMusic(track, playlistId, accountId, options, publicChats, untitled)
            },
            description = playlist.description,
            author = authorFor(options),
        )
    }

    /**
     * An unsaved local copy of an imported manifest. The repository assigns the id, so [MusicPlaylist.id] is empty here.
     * [origins] maps a track id to the origin that was found in the recipient account. A track without one stays
     * unavailable (origin null). Added times rise with the position, so sorting by "added" keeps the file's order. The file's
     * revision goes to [MusicPlaylist.sourceRevision]. The copy's own revision and [MusicPlaylist.revisionAtSource] start at 0,
     * so the copy starts out equal to its file.
     */
    fun toPlaylistCopy(manifest: BgMusicManifest, now: Long, origins: Map<String, PlaybackOrigin>): MusicPlaylist =
        MusicPlaylist(
            id = "",
            title = manifest.title,
            description = manifest.description,
            tracks = manifest.tracks.mapIndexed { index, track ->
                PlaylistTrack(
                    itemId = track.id,
                    title = track.title,
                    performer = track.artist,
                    album = track.album,
                    durationSeconds = track.durationSec.toInt(),
                    origin = origins[track.id],
                    addedAt = now + index,
                )
            },
            createdAt = now,
            updatedAt = now,
            revision = 0L,
            sourcePlaylistId = manifest.playlistId,
            sourceRevision = manifest.revision,
            revisionAtSource = 0L,
            sortMode = MusicSortMode.Manual,
        )

    /**
     * Public `telegram_public` hints for [origin]. A hint needs an origin of [accountId], a chat in [publicChats] with a valid
     * username, and a message id whose low bits are zero, which is the shape of a received post. Anything else gives no hint.
     */
    internal fun publicHints(
        origin: PlaybackOrigin?,
        accountId: String,
        publicChats: Map<Long, String>,
    ): List<BgMusicSourceHint> {
        if (origin == null || origin.accountId != accountId) return emptyList()
        val username = publicChats[origin.chatId]?.let { BgMusicFormat.normalizeTelegramUsername(it) }
            ?: return emptyList()
        if ((origin.messageId and TDLIB_LOCAL_BITS_MASK) != 0L) return emptyList()
        val postNumber = origin.messageId shr TDLIB_MESSAGE_ID_SHIFT
        if (postNumber !in 1L..BgMusicFormat.MAX_TELEGRAM_MESSAGE_ID) return emptyList()
        return listOf(BgMusicSourceHint.TelegramPublic(username = username, messageId = postNumber))
    }

    /** File name for an export: the title with characters that are unsafe in file names replaced, plus the extension. */
    fun fileNameFor(title: String): String {
        val safe = BgMusicFormat.sanitizeText(title, MAX_FILE_NAME_CHARS)
            .map { ch -> if (ch in UNSAFE_FILE_NAME_CHARS) '_' else ch }
            .joinToString(separator = "")
            .trim()
            .trim('.')
            .trim()
        return "${safe.ifEmpty { DEFAULT_FILE_NAME }}.${BgMusicFormat.FILE_EXTENSION}"
    }

    private const val MAX_FILE_NAME_CHARS = 80
    private const val DEFAULT_FILE_NAME = "playlist"
    private const val UNSAFE_FILE_NAME_CHARS = "/\\:*?\"<>|"

    private fun trackToBgMusic(
        track: PlaylistTrack,
        playlistId: String,
        accountId: String,
        options: BgMusicExportOptions,
        publicChats: Map<Long, String>,
        untitled: String,
    ): BgMusicTrack {
        val title = BgMusicFormat.sanitizeText(track.title, BgMusicFormat.MAX_TRACK_TITLE)
            .ifEmpty { BgMusicFormat.sanitizeText(untitled, BgMusicFormat.MAX_TRACK_TITLE) }
        return BgMusicTrack(
            id = trackId(playlistId, track.itemId),
            title = title,
            artist = track.performer,
            album = track.album,
            durationSec = track.durationSeconds.toLong().coerceIn(0L, BgMusicFormat.MAX_DURATION_SEC),
            audioHash = null,
            sources = if (options.includePublicLinks) {
                publicHints(track.origin, accountId, publicChats)
            } else {
                emptyList()
            },
        )
    }

    /**
     * The app's own entry id when it is a UUID. Otherwise a name-based UUID derived from the app's ids only, so the file stays
     * stable across exports and never carries Telegram data.
     */
    private fun trackId(playlistId: String, itemId: String): String =
        BgMusicFormat.normalizeUuid(itemId)
            ?: UUID.nameUUIDFromBytes("$playlistId|$itemId".toByteArray(Charsets.UTF_8)).toString()

    private fun authorFor(options: BgMusicExportOptions): BgMusicAuthor? {
        if (!options.includeAuthor) return null
        val name = BgMusicFormat.sanitizeText(options.authorName, BgMusicFormat.MAX_AUTHOR_NAME)
        return name.takeIf { it.isNotEmpty() }?.let { BgMusicAuthor(it) }
    }
}
