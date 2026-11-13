package com.beykus.beygram.data.music.provider

import kotlinx.coroutines.flow.StateFlow

/**
 * Contract of an external music source (Yandex Music in the DEV build). Screens and the player use only this
 * interface, so the Stable build contains no implementation and the UI works with whatever is available.
 *
 * Rules every implementation follows:
 * - Calls are suspending, run off the main thread, and never throw for expected failures: they return
 *   [ProviderResult.Failure] with a [MusicProviderFailure] that the UI maps to a localized string.
 * - Tokens, signed URLs and raw server payloads never leave the implementation: they are not logged,
 *   not returned in models and not stored in playback sessions. [ProviderAudio.url] is short-lived; request it
 *   right before playback and never persist it.
 * - Tracks from a provider are never forwarded, exported or added to a profile by the app. The UI decides this
 *   from [ProviderTrack.key] prefix, see [YANDEX_TRACK_KEY_PREFIX]. Only a link to the track's page on the service
 *   may be shared, as text (see [ProviderTracks.shareLink]).
 */
interface MusicProvider {
    /** Current sign-in state. Updated by the implementation after sign-in, sign-out and token expiry. */
    val state: StateFlow<MusicProviderState>

    suspend fun search(query: String, limit: Int = 20): ProviderResult<List<ProviderTrack>>

    suspend fun likedTracks(): ProviderResult<List<ProviderTrack>>

    /** Ids of the liked tracks, newest first. Cheap: the tracks themselves come from [tracks], a page at a time. */
    suspend fun likedTrackIds(): ProviderResult<List<String>> = ProviderResult.Failure(MusicProviderFailure.Unavailable)

    /** Tracks by id, in the order given. */
    suspend fun tracks(ids: List<String>): ProviderResult<List<ProviderTrack>> =
        ProviderResult.Failure(MusicProviderFailure.Unavailable)

    /** Playlists of other users the account liked. */
    suspend fun likedPlaylists(): ProviderResult<List<ProviderPlaylist>> =
        ProviderResult.Failure(MusicProviderFailure.Unavailable)

    /** Albums the account liked, newest first. */
    suspend fun likedAlbums(): ProviderResult<List<ProviderAlbum>> = ProviderResult.Failure(MusicProviderFailure.Unavailable)

    /** One album with its tracks, in disc and track order. */
    suspend fun album(albumId: String): ProviderResult<ProviderAlbumTracks> =
        ProviderResult.Failure(MusicProviderFailure.Unavailable)

    suspend fun setLiked(trackId: String, liked: Boolean): ProviderResult<Unit>

    suspend fun playlists(): ProviderResult<List<ProviderPlaylist>>

    /**
     * Tracks of one playlist. [ProviderPlaylist.kind] is the playlist id inside the provider; [ownerUid] is its owner,
     * null for the account's own playlists.
     */
    suspend fun playlistTracks(kind: String, ownerUid: String? = null): ProviderResult<List<ProviderTrack>>

    /**
     * The playlist with this uuid, when the account owns it or follows it. A playlist of another user that the account
     * does not follow is not found, and the caller opens it in the browser instead.
     */
    suspend fun playlistByUuid(uuid: String): ProviderResult<ProviderPlaylist> =
        ProviderResult.Failure(MusicProviderFailure.Unavailable)

    suspend fun lyrics(trackId: String): ProviderResult<ProviderLyrics>

    /**
     * A temporary direct link to the audio of a full track. Fails with [MusicProviderFailure.SubscriptionRequired]
     * when the account has no subscription, so the player never plays 30-second previews as full tracks.
     */
    suspend fun audio(trackId: String): ProviderResult<ProviderAudio>
}

/** Key prefix of tracks of the Yandex Music provider. The full key is `ym:<trackId>`. */
const val YANDEX_TRACK_KEY_PREFIX = "ym:"

sealed interface MusicProviderState {
    /** No sign-in, or the stored session was removed. */
    object SignedOut : MusicProviderState

    data class SignedIn(val account: MusicProviderAccount) : MusicProviderState
}

data class MusicProviderAccount(
    /** Provider user id. Used only to tell accounts apart; never shown. */
    val uid: String,
    /** Name shown in the account card. */
    val displayName: String,
    val hasSubscription: Boolean,
)

data class ProviderTrack(
    /** Provider track id, stable across sessions. */
    val trackId: String,
    val title: String,
    val artists: List<String>,
    val albumTitle: String?,
    val durationMs: Long,
    /** Cover image URL for display. Covers are loaded by the UI and not stored in the library. */
    val coverUrl: String?,
    /** False for tracks removed or not available in the user's region. Such tracks cannot be played. */
    val isAvailable: Boolean,
    /** Known only where the provider reports likes (liked list, track info). Null when unknown. */
    val liked: Boolean? = null,
    /** Album of the track inside the provider, for opening the album. Null when the track has none. */
    val albumId: String? = null,
    /** Artists of the track with their provider ids, for a radio of the artist. Empty when the provider gives no ids. */
    val artistRefs: List<ProviderArtistRef> = emptyList(),
) {
    /** Stable player key for this track: `ym:<trackId>`. */
    val key: String get() = YANDEX_TRACK_KEY_PREFIX + trackId

    val performer: String get() = artists.joinToString(", ")
}

/** An artist of a track: the provider id and the name shown. */
data class ProviderArtistRef(val id: String, val name: String)

data class ProviderPlaylist(
    /** Playlist id inside the provider, passed to [MusicProvider.playlistTracks]. */
    val kind: String,
    val title: String,
    val trackCount: Int,
    val coverUrl: String?,
    /** True for playlists the account owns, false for playlists it only follows. */
    val isOwn: Boolean,
    /** Provider id of the owner. Needed to load the tracks of a playlist the account only follows. */
    val ownerUid: String? = null,
    /** Name of the owner, shown under playlists of other users. */
    val ownerName: String? = null,
    /** Uuid used in the playlist's web address, such as `/playlists/<uuid>`. Null when the service does not report it. */
    val uuid: String? = null,
)

data class ProviderAlbum(
    /** Album id inside the provider. */
    val id: String,
    val title: String,
    val artists: List<String>,
    /** Release year, null when unknown. */
    val year: Int?,
    val trackCount: Int,
    val coverUrl: String?,
) {
    val performer: String get() = artists.joinToString(", ")
}

data class ProviderAlbumTracks(val album: ProviderAlbum, val tracks: List<ProviderTrack>)

data class ProviderLyrics(
    /** Time-synced lines, empty when the track has only plain text. */
    val lines: List<LyricLine>,
    /** Plain text, used when [lines] is empty. */
    val plainText: String?,
    /** Rights holder of the text, shown under the lyrics as the provider requires. */
    val copyrightHolder: String?,
) {
    val isSynced: Boolean get() = lines.isNotEmpty()
}

data class LyricLine(val timeMs: Long, val text: String)

data class ProviderAudio(
    /** Temporary direct link. Do not store it. */
    val url: String,
    /** Codec name as reported by the provider, for example "mp3" or "aac". */
    val codec: String,
    val bitrateKbps: Int,
)

/** Failures the UI can explain. Messages are resource strings, never raw server text. */
enum class MusicProviderFailure {
    /** The action needs a signed-in account. */
    NotSignedIn,
    /** The account has no subscription that allows full tracks. */
    SubscriptionRequired,
    /** The stored session is no longer accepted. The user must sign in again. */
    SessionExpired,
    /** The service asked to slow down. Retry later, the implementation already waits. */
    RateLimited,
    /** The service is unreachable or does not serve this region. */
    Unavailable,
    /** The track, playlist or lyrics do not exist. */
    NotFound,
    /** The response did not match what the app understands. The service may have changed. */
    ServiceChanged,
    /** No network connection. */
    Network,
    Unknown,
}

sealed interface ProviderResult<out T> {
    data class Success<out T>(val value: T) : ProviderResult<T>

    data class Failure(val failure: MusicProviderFailure) : ProviderResult<Nothing>
}
