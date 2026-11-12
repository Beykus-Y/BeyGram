package com.beykus.beygram.ui.screens.music

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.data.music.provider.ProviderTracks
import com.beykus.beygram.ui.components.beyGramPalette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R
import com.beykus.beygram.data.music.provider.ProviderArtwork
import com.beykus.beygram.data.music.provider.ProviderCovers
import com.beykus.beygram.data.music.provider.ProviderTrack
import com.beykus.beygram.data.music.provider.ProviderWaves

/** The cached file of a source cover, or null while it downloads or when there is none. */
@Composable
internal fun rememberProviderCover(url: String?): String? {
    val context = LocalContext.current
    val path by produceState<String?>(null, url) {
        value = null
        if (url.isNullOrBlank()) return@produceState
        ProviderArtwork.watch(context)
        value = ProviderCovers.file(context, url)?.absolutePath
    }
    return path
}

/** A track of an optional music source in the shared row: its cover (or [number] in an album), title, artists and length. */
@Composable
internal fun ProviderTrackRow(
    track: ProviderTrack,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit = {},
    number: Int? = null
) {
    MusicTrackRowLayout(
        title = track.title,
        subtitle = if (track.isAvailable) track.performer else stringResource(R.string.music_unavailable),
        durationSeconds = (track.durationMs / 1_000L).toInt(),
        coverPath = if (number == null) rememberProviderCover(track.coverUrl) else null,
        unavailable = !track.isAvailable,
        current = rememberIsCurrentItem(track.key),
        onClick = onClick,
        trailing = trailing,
        number = number
    )
}

/** Opens the album screen of a source track by its album id, for the player menu. Null where no album can open. */
val LocalOpenProviderAlbum = compositionLocalOf<((String) -> Unit)?> { null }

/**
 * Menu entries for one source track, shared by the collection screens and the player: play next, like or unlike,
 * open its album and share its link. The link is the only way a source track leaves the app; the file never does.
 * [onDismiss] closes the menu before the action runs. [showPlayNext] is off in the player, whose track already plays.
 */
@Composable
internal fun ProviderTrackMenuItems(
    track: ProviderTrack,
    onDismiss: () -> Unit,
    showPlayNext: Boolean,
    onOpenAlbum: ((String) -> Unit)?
) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val likes by ProviderTracks.liked.collectAsStateWithLifecycle()
    val listKnown by ProviderTracks.likedListKnown.collectAsStateWithLifecycle()
    // Unknown until the liked list of the account was read: no toggle is offered that could do the opposite.
    val liked = likes[track.trackId] ?: track.liked ?: false.takeIf { listKnown }
    val failed = stringResource(R.string.music_provider_like_failed)
    val chooser = stringResource(R.string.music_provider_share_link)
    if (showPlayNext && track.isAvailable) DropdownMenuItem(
        text = { Text(stringResource(R.string.queue_play_next)) },
        leadingIcon = { Icon(Icons.Rounded.QueueMusic, null, tint = palette.textSecondary) },
        onClick = {
            onDismiss()
            ProviderTracks.playNext(context, track)
        }
    )
    if (liked != null) DropdownMenuItem(
        text = { Text(stringResource(if (liked) R.string.music_provider_unlike else R.string.music_provider_like)) },
        leadingIcon = {
            Icon(
                if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                null,
                tint = if (liked) palette.accent else palette.textSecondary
            )
        },
        onClick = {
            onDismiss()
            // Runs outside the menu, which leaves the composition at once; a failure still rolls back and is shown.
            ProviderTracks.setLikedInBackground(context, track, !liked) {
                Toast.makeText(context.applicationContext, failed, Toast.LENGTH_SHORT).show()
            }
        }
    )
    val startWave = ProviderWaves.fromTrack
    if (startWave != null && track.isAvailable) DropdownMenuItem(
        text = { Text(stringResource(R.string.music_provider_wave_track)) },
        leadingIcon = { Icon(Icons.Rounded.Radio, null, tint = palette.textSecondary) },
        onClick = {
            onDismiss()
            startWave(context, track)
        }
    )
    val albumId = track.albumId
    if (onOpenAlbum != null && !albumId.isNullOrBlank()) DropdownMenuItem(
        text = { Text(stringResource(R.string.music_provider_open_album)) },
        leadingIcon = { Icon(Icons.Rounded.Album, null, tint = palette.textSecondary) },
        onClick = {
            onDismiss()
            onOpenAlbum(albumId)
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.music_provider_share_link)) },
        leadingIcon = { Icon(Icons.Rounded.Share, null, tint = palette.textSecondary) },
        onClick = {
            onDismiss()
            ProviderTracks.shareLink(context, track, chooser)
        }
    )
}

/**
 * The menu entries of the source track the player holds, or nothing for a Telegram track. [onLeavePlayer] closes the
 * full player before the album screen opens under it.
 */
@Composable
internal fun PlayerProviderTrackMenuItems(key: String, onDismiss: () -> Unit, onLeavePlayer: () -> Unit) {
    if (!ProviderTracks.isProviderKey(key)) return
    val track = ProviderTracks.byKey(key) ?: return
    val openAlbum = LocalOpenProviderAlbum.current
    ProviderTrackMenuItems(
        track = track,
        onDismiss = onDismiss,
        showPlayNext = false,
        onOpenAlbum = openAlbum?.let { open -> { albumId -> onLeavePlayer(); open(albumId) } }
    )
}
