package com.beykus.beygram.ui.screens.chat

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.music.provider.MusicProviderState
import com.beykus.beygram.data.music.provider.MusicProviders
import com.beykus.beygram.data.music.provider.ProviderAlbum
import com.beykus.beygram.data.music.provider.ProviderArtwork
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTrack
import com.beykus.beygram.deeplink.MessageEntityLink
import com.beykus.beygram.deeplink.YandexMusicTarget
import com.beykus.beygram.deeplink.messageEntityLinks
import com.beykus.beygram.deeplink.yandexHasSubscription
import com.beykus.beygram.deeplink.yandexMusicEntityLink
import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MessageLinkPreview
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackState
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.screens.music.musicCollectionSummary
import com.beykus.beygram.ui.screens.music.rememberProviderCover
import kotlinx.coroutines.flow.StateFlow

/** What the music card under a message shows once the service has answered. */
private sealed interface MusicLinkData {
    data class Track(val track: ProviderTrack) : MusicLinkData
    data class Album(val album: ProviderAlbum, val tracks: List<ProviderTrack>) : MusicLinkData
    data object Failed : MusicLinkData
}

/**
 * Data already loaded for a link, so a card that scrolls back into view does not ask the service again. Failures are
 * not kept, so the next time the card appears it asks again. Bounded by [MAX_ENTRIES]; cleared on sign-out.
 */
private object MusicLinkCache {
    private const val MAX_ENTRIES = 200

    private val entries = object : LinkedHashMap<String, MusicLinkData>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MusicLinkData>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(url: String): MusicLinkData? = entries[url]

    @Synchronized
    fun put(url: String, data: MusicLinkData) {
        entries[url] = data
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}

/**
 * The card under a message that holds a Yandex Music link: a track, or an album with its tracks. Until the service
 * answers, the usual link card shows (or a plain placeholder when the message has none); then the Yandex data takes
 * its place. It is drawn only in the DEV build with a signed-in account, and only where [enabled] is true, which the
 * chat sets off for secret chats. In every other case the usual link card stays, as before.
 */
@Composable
internal fun MessageMusicLinkCard(
    message: ChatMessage,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onDownloadFile: (Int) -> Unit,
    autoDownloadFile: ((AutoDownloadRequest) -> Unit)?,
    onOpenEntity: ((MessageEntityLink) -> Unit)?,
    enabled: Boolean
) {
    val preview = message.linkPreview
    val musicLink = remember(message.text, message.entities, preview) { musicLinkOf(message) }
    val signedIn = rememberYandexSignedIn()
    // Secret chats and signed-out accounts never reach the service: the link keeps its usual card.
    if (!enabled || !signedIn || musicLink == null) {
        preview?.let { MessageLinkPreviewCard(it, observeFile, onDownloadFile, autoDownloadFile = autoDownloadFile, onOpenEntity = onOpenEntity) }
        return
    }
    // Keyed by the link, so an edited message that links elsewhere starts again with that link's own data.
    key(musicLink.url) {
        MusicLinkBody(musicLink, preview, observeFile, onDownloadFile, autoDownloadFile, onOpenEntity)
    }
}

/**
 * Loads the data of one link and draws its card. It is composed only when cards are on and the account is signed in,
 * so no lookup starts in a secret chat, and leaving that state cancels a lookup that is still running.
 */
@Composable
private fun MusicLinkBody(
    link: MessageEntityLink.YandexMusic,
    preview: MessageLinkPreview?,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onDownloadFile: (Int) -> Unit,
    autoDownloadFile: ((AutoDownloadRequest) -> Unit)?,
    onOpenEntity: ((MessageEntityLink) -> Unit)?
) {
    val context = LocalContext.current
    val data by produceState<MusicLinkData?>(initialValue = MusicLinkCache.get(link.url), link) {
        if (value != null) return@produceState
        val provider = MusicProviders.yandex(context)
        if (provider == null) {
            value = MusicLinkData.Failed
            return@produceState
        }
        val loaded: MusicLinkData = when (val target = link.target) {
            is YandexMusicTarget.Track -> when (val result = provider.tracks(listOf(target.trackId))) {
                is ProviderResult.Success -> result.value.firstOrNull()?.let { MusicLinkData.Track(it) } ?: MusicLinkData.Failed
                is ProviderResult.Failure -> MusicLinkData.Failed
            }
            is YandexMusicTarget.Album -> when (val result = provider.album(target.albumId)) {
                is ProviderResult.Success -> MusicLinkData.Album(result.value.album, result.value.tracks)
                is ProviderResult.Failure -> MusicLinkData.Failed
            }
        }
        if (loaded != MusicLinkData.Failed) MusicLinkCache.put(link.url, loaded)
        value = loaded
    }
    // Opens the link's own screen in the chat's handler: the album screen, or the browser for a track without one.
    val onOpen: (() -> Unit)? = onOpenEntity?.let { open -> { open(link) } }
    when (val shown = data) {
        null -> if (preview != null) {
            MessageLinkPreviewCard(preview, observeFile, onDownloadFile, autoDownloadFile = autoDownloadFile, onOpenEntity = onOpenEntity)
        } else {
            MusicLinkRow(
                kindLine = "",
                title = stringResource(R.string.player_source_provider_yandex),
                subtitle = "",
                coverUrl = null,
                current = false,
                playing = false,
                progress = 0f,
                onBodyClick = null,
                onPlay = null
            )
        }
        MusicLinkData.Failed -> preview?.let {
            MessageLinkPreviewCard(it, observeFile, onDownloadFile, autoDownloadFile = autoDownloadFile, onOpenEntity = onOpenEntity)
        }
        is MusicLinkData.Track -> MusicTrackCard(shown.track, onOpen)
        is MusicLinkData.Album -> MusicAlbumCard(album = shown.album, tracks = shown.tracks, onOpen = onOpen)
    }
}

/** The Yandex Music link of a message: the first one in its text, otherwise the one its link card carries. */
private fun musicLinkOf(message: ChatMessage): MessageEntityLink.YandexMusic? =
    messageEntityLinks(message.text, message.entities).firstNotNullOfOrNull { it as? MessageEntityLink.YandexMusic }
        ?: message.linkPreview?.let { preview -> yandexMusicEntityLink(preview.url.ifBlank { preview.displayUrl }) }

/**
 * Whether the app is signed in to Yandex Music. Always false outside the DEV build. Signing out clears the cached
 * data of the cards, so no card shows a track of the removed account.
 */
@Composable
private fun rememberYandexSignedIn(): Boolean {
    val context = LocalContext.current
    val signedIn by produceState(initialValue = false, context) {
        val state = MusicProviders.yandex(context)?.state
        if (state == null) {
            value = false
            return@produceState
        }
        state.collect { current ->
            value = current is MusicProviderState.SignedIn
            if (current is MusicProviderState.SignedOut) MusicLinkCache.clear()
        }
    }
    return signedIn
}

@Composable
private fun MusicTrackCard(track: ProviderTrack, onOpen: (() -> Unit)?) {
    val context = LocalContext.current
    val playback by PlaybackEngine.state.collectAsStateWithLifecycle()
    val current = playback.item?.key == track.key
    val onPlay: (() -> Unit)? =
        if (track.isAvailable) ({ playTrackOrRoute(context, track, current, onOpen) }) else null
    MusicLinkRow(
        kindLine = "${stringResource(R.string.music_link_track)} · ${formatLength(track.durationMs)}",
        title = track.title,
        subtitle = if (track.isAvailable) track.performer else stringResource(R.string.music_unavailable),
        coverUrl = track.coverUrl,
        current = current,
        playing = current && playback.playing,
        progress = progressOf(playback, current),
        onBodyClick = onPlay,
        onPlay = onPlay
    )
}

@Composable
private fun MusicAlbumCard(album: ProviderAlbum, tracks: List<ProviderTrack>, onOpen: (() -> Unit)?) {
    val context = LocalContext.current
    val playback by PlaybackEngine.state.collectAsStateWithLifecycle()
    val keys = remember(tracks) { tracks.mapTo(HashSet<String>()) { it.key } }
    val currentKey = playback.item?.key
    val current = currentKey != null && currentKey in keys
    val onPlay: (() -> Unit)? =
        if (tracks.isEmpty()) null else ({ playAlbumOrRoute(context, tracks, current, onOpen) })
    MusicLinkRow(
        kindLine = "${stringResource(R.string.music_link_album)} · " +
            musicCollectionSummary(tracks.size, tracks.sumOf { it.durationMs } / 1_000L),
        title = album.title,
        subtitle = album.performer,
        coverUrl = album.coverUrl,
        current = current,
        playing = current && playback.playing,
        progress = progressOf(playback, current),
        onBodyClick = onOpen,
        onPlay = onPlay
    )
}

/** One card row: cover, a kind line, the title and a subtitle, a play or pause button, and a bar while it is current. */
@Composable
private fun MusicLinkRow(
    kindLine: String,
    title: String,
    subtitle: String,
    coverUrl: String?,
    current: Boolean,
    playing: Boolean,
    progress: Float,
    onBodyClick: (() -> Unit)?,
    onPlay: (() -> Unit)?
) {
    val palette = beyGramPalette()
    val coverPath = rememberProviderCover(coverUrl)
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp)
            .clip(styledShape(8.dp))
            .background(palette.accent.copy(alpha = 0.08f))
            .padding(6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().then(if (onBodyClick == null) Modifier else Modifier.clickable(onClick = onBodyClick)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(52.dp).clip(styledShape(6.dp)).background(palette.surface2),
                contentAlignment = Alignment.Center
            ) {
                if (coverPath != null) {
                    LocalMediaImage(coverPath, Modifier.fillMaxSize(), contentDescriptionText = title)
                } else {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = palette.accent, modifier = Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                if (kindLine.isNotBlank()) Text(kindLine, color = palette.accentInk, fontSize = 11.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(title, color = palette.messageText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, color = palette.textSecondary, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onPlay != null) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(palette.accent).clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (playing) R.string.common_pause else R.string.music_play),
                        tint = palette.onAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        if (current) {
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp).clip(CircleShape)
                .background(palette.textSecondary.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxWidth(progress).height(3.dp).background(palette.accent))
            }
        }
    }
}

/**
 * Plays [track] when it is the current item already paused or not, pauses or resumes it when it plays, and plays it
 * here only with a subscription. Without one the link's screen opens instead, which shows the subscription notice.
 */
private fun playTrackOrRoute(context: Context, track: ProviderTrack, current: Boolean, onRoute: (() -> Unit)?) {
    when {
        current -> PlaybackEngine.togglePlayPause()
        yandexHasSubscription(context) -> ProviderArtwork.playTracks(context, listOf(track), 0)
        else -> onRoute?.invoke()
    }
}

/** Starts [tracks] of an album from the first one, or pauses or resumes it when the album is the current queue. */
private fun playAlbumOrRoute(context: Context, tracks: List<ProviderTrack>, current: Boolean, onRoute: (() -> Unit)?) {
    when {
        current -> PlaybackEngine.togglePlayPause()
        yandexHasSubscription(context) -> ProviderArtwork.playTracks(context, tracks, 0)
        else -> onRoute?.invoke()
    }
}

private fun progressOf(playback: PlaybackState, current: Boolean): Float =
    if (current && playback.durationMs > 0L) (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f) else 0f

/** Length as minutes and seconds, such as 4:05. */
private fun formatLength(durationMs: Long): String {
    val seconds = (durationMs / 1_000L).coerceAtLeast(0L)
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}
