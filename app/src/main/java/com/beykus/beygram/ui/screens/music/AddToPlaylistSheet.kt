package com.beykus.beygram.ui.screens.music

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.music.MusicLibraries
import com.beykus.beygram.data.music.MusicOutcome
import com.beykus.beygram.data.music.PlaylistTrack
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.launch

/**
 * Puts [track] into the favorites or into one of the user's playlists. The favorites row toggles in place. A playlist that
 * already holds the track is shown checked and cannot be picked again. Adding to a playlist closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddToPlaylistSheet(
    accountId: String,
    track: PlaylistTrack,
    onDismiss: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { MusicLibraries.get(context) }
    val library = rememberMusicLibrary(accountId)
    var creating by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                stringResource(R.string.music_add_to_playlist),
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            if (library == null) {
                MusicLoadingRow()
            } else {
                val favorite = containsTrack(library.favorites, track)
                SheetRow(
                    icon = if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    title = stringResource(R.string.music_favorites_title),
                    subtitle = null,
                    active = favorite,
                    checked = false,
                    enabled = true,
                    onClick = {
                        scope.launch { context.reportMusicOutcome(repository.toggleFavorite(track)) }
                    }
                )
                userPlaylists(library).forEach { playlist ->
                    val contained = containsTrack(playlist, track)
                    SheetRow(
                        icon = Icons.Rounded.QueueMusic,
                        title = playlist.title,
                        subtitle = pluralStringResource(R.plurals.music_track_count, playlist.tracks.size, playlist.tracks.size),
                        active = contained,
                        checked = contained,
                        enabled = !contained,
                        onClick = {
                            scope.launch {
                                if (context.addTrackTo(playlist.id, playlist.title, track)) onDismiss()
                            }
                        }
                    )
                }
                SheetRow(
                    icon = Icons.Rounded.Add,
                    title = stringResource(R.string.music_playlist_create),
                    subtitle = null,
                    active = true,
                    checked = false,
                    enabled = true,
                    onClick = { creating = true }
                )
            }
        }
    }
    if (creating) MusicTextDialog(
        title = stringResource(R.string.music_playlist_create),
        initial = "",
        label = stringResource(R.string.music_playlist_name),
        confirmText = stringResource(R.string.music_create),
        singleLine = true,
        allowBlank = false,
        onConfirm = { name ->
            creating = false
            scope.launch {
                when (val created = repository.createPlaylist(name)) {
                    is MusicOutcome.Failed -> context.reportMusicOutcome(created)
                    is MusicOutcome.Done -> {
                        if (context.addTrackTo(created.value.id, created.value.title, track)) onDismiss()
                    }
                }
            }
        },
        onDismiss = { creating = false }
    )
}

/**
 * Adds [track] to the playlist [playlistId], called [title], and says what happened. Returns whether the track was added.
 */
private suspend fun Context.addTrackTo(playlistId: String, title: String, track: PlaylistTrack): Boolean {
    return when (val outcome = MusicLibraries.get(this).addTrack(playlistId, track)) {
        is MusicOutcome.Failed -> {
            reportMusicOutcome(outcome)
            false
        }
        is MusicOutcome.Done -> when {
            outcome.value.added > 0 -> {
                showMusicToast(getString(R.string.music_added_to, title))
                true
            }
            outcome.value.duplicates > 0 -> {
                showMusicToast(getString(R.string.music_already_in_playlist))
                false
            }
            else -> {
                showMusicToast(getString(R.string.music_error_limit))
                false
            }
        }
    }
}

/** One choice of the sheet: an icon, a title with an optional subtitle, and a check when the track is already there. */
@Composable
private fun SheetRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    active: Boolean,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (active) palette.accent else palette.accentInk, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = palette.textSecondary, fontSize = 13.sp, maxLines = 1)
        }
        if (checked) Icon(Icons.Rounded.Check, stringResource(R.string.music_already_in_playlist), tint = palette.accent)
    }
}
