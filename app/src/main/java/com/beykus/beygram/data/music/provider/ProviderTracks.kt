package com.beykus.beygram.data.music.provider

import android.content.Context
import android.content.Intent
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import kotlinx.coroutines.Dispatchers
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
        val text = if (name.isBlank()) webLink(track) else "$name\n${webLink(track)}"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Likes or unlikes [track] in the service library. The local state changes at once and returns on failure. */
    suspend fun setLiked(context: Context, track: ProviderTrack, liked: Boolean): ProviderResult<Unit> {
        val provider = MusicProviders.yandex(context) ?: return ProviderResult.Failure(MusicProviderFailure.Unavailable)
        val before = _liked.value[track.trackId]
        _liked.update { it + (track.trackId to liked) }
        val result = withContext(Dispatchers.IO) { provider.setLiked(track.trackId, liked) }
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
