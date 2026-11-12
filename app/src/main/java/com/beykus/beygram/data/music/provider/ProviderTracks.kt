package com.beykus.beygram.data.music.provider

import android.content.Context
import android.content.Intent
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Source tracks the app has shown or queued, by player key, and the actions on them that do not need their screen:
 * play next, like, open the album and share a link. The player menu reads a track from here, because a queue row
 * holds only its key.
 *
 * Sharing a source track sends only a link to its page on the service, as text. The audio file is never forwarded,
 * exported or put on a profile.
 */
object ProviderTracks {
    private const val MAX_REMEMBERED = 5_000
    private const val YANDEX_MUSIC_WEB = "https://music.yandex.ru"

    private val tracks = LinkedHashMap<String, ProviderTrack>()

    private val _liked = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /** What the app knows about likes, by track id. A track missing here has an unknown like. */
    val liked: StateFlow<Map<String, Boolean>> = _liked.asStateFlow()

    fun remember(list: List<ProviderTrack>) {
        synchronized(tracks) {
            if (tracks.size > MAX_REMEMBERED) tracks.clear()
            list.forEach { tracks[it.key] = it }
        }
        val known = list.mapNotNull { track -> track.liked?.let { track.trackId to it } }
        if (known.isNotEmpty()) _liked.update { it + known }
    }

    /** The source track behind a player key, if the app has seen it. */
    fun byKey(key: String): ProviderTrack? = synchronized(tracks) { tracks[key] }

    /** Whether [key] is a track of a source rather than a Telegram file. */
    fun isProviderKey(key: String): Boolean = key.startsWith(YANDEX_TRACK_KEY_PREFIX)

    /** Address of the track's page on the service. It opens in the service's app or site. */
    fun webLink(track: ProviderTrack): String = if (track.albumId.isNullOrBlank()) {
        "$YANDEX_MUSIC_WEB/track/${track.trackId}"
    } else {
        "$YANDEX_MUSIC_WEB/album/${track.albumId}/track/${track.trackId}"
    }

    /** Opens the system share sheet with the track name and its link; Telegram chats are among the targets. */
    fun shareLink(context: Context, track: ProviderTrack, chooserTitle: String) {
        val name = listOf(track.performer, track.title).filter { it.isNotBlank() }.joinToString(" — ")
        shareText(context, if (name.isBlank()) webLink(track) else "$name\n${webLink(track)}", chooserTitle)
    }

    /** Address of an album's page on the service. It opens in the service's app or site. */
    fun albumWebLink(albumId: String): String = "$YANDEX_MUSIC_WEB/album/$albumId"

    /** Opens the system share sheet with the album name and its link. */
    fun shareAlbumLink(context: Context, title: String, albumId: String, chooserTitle: String) {
        val link = albumWebLink(albumId)
        shareText(context, if (title.isBlank()) link else "$title\n$link", chooserTitle)
    }

    /** Address of a playlist's page on the service. It opens in the service's app or site. */
    fun playlistWebLink(uuid: String): String = "$YANDEX_MUSIC_WEB/playlists/$uuid"

    /** Opens the system share sheet with the playlist name and its link. */
    fun sharePlaylistLink(context: Context, title: String, uuid: String, chooserTitle: String) {
        val link = playlistWebLink(uuid)
        shareText(context, if (title.isBlank()) link else "$title\n$link", chooserTitle)
    }

    private fun shareText(context: Context, text: String, chooserTitle: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Marks the ids of the account's whole liked list as liked, so a track seen first in an album or playlist shows
     * its real state. Ids already known keep their state.
     */
    fun rememberLikedIds(ids: Collection<String>) {
        _liked.update { known -> ids.associateWith { true } + known }
        _likedListKnown.value = true
    }

    private val _likedListKnown = MutableStateFlow(false)

    /** True once the whole liked list of the account was read: a track missing from it is then not liked. */
    val likedListKnown: StateFlow<Boolean> = _likedListKnown.asStateFlow()

    /** Forgets every like, for example after sign-out. */
    fun clearLikes() {
        _likedListKnown.value = false
        _liked.value = emptyMap()
    }

    /** Process-wide, so a like outlives the menu or screen that asked for it. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** [setLiked] on the process scope; [onFailure] runs on the main thread when the service refused it. */
    fun setLikedInBackground(context: Context, track: ProviderTrack, liked: Boolean, onFailure: () -> Unit) {
        val app = context.applicationContext
        scope.launch {
            if (setLiked(app, track, liked) is ProviderResult.Failure) onFailure()
        }
    }

    /** Likes or unlikes [track] in the service library. The local state changes at once and returns on failure. */
    suspend fun setLiked(context: Context, track: ProviderTrack, liked: Boolean): ProviderResult<Unit> {
        val provider = MusicProviders.yandex(context) ?: return ProviderResult.Failure(MusicProviderFailure.Unavailable)
        val before = _liked.value[track.trackId]
        _liked.update { it + (track.trackId to liked) }
        val result = try {
            withContext(Dispatchers.IO) { provider.setLiked(track.trackId, liked) }
        } catch (e: CancellationException) {
            // Not known whether the service saved it: the state goes back to what it was before.
            _liked.update { if (before == null) it - track.trackId else it + (track.trackId to before) }
            throw e
        }
        if (result is ProviderResult.Failure) {
            _liked.update { if (before == null) it - track.trackId else it + (track.trackId to before) }
        }
        return result
    }

    /** The queue row of [track], as the player builds it for a source queue. */
    fun playbackItem(track: ProviderTrack): PlaybackItem = PlaybackItem(
        key = track.key,
        kind = MediaKind.Audio,
        path = "",
        title = track.title,
        durationSeconds = (track.durationMs / 1_000L).toInt(),
        performer = track.performer,
        trackTitle = track.title,
        album = track.albumTitle.orEmpty(),
    )

    /** Puts [track] right after the current track, or starts it when nothing plays. */
    fun playNext(context: Context, track: ProviderTrack) {
        if (!track.isAvailable) return
        remember(listOf(track))
        ProviderArtwork.watch(context)
        PlaybackEngine.enqueue(context, playbackItem(track), source = null, atEnd = false)
    }
}
