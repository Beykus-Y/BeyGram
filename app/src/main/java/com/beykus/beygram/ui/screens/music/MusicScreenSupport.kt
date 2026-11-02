package com.beykus.beygram.ui.screens.music

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.beykus.beygram.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.music.MusicFailure
import com.beykus.beygram.data.music.MusicLibraries
import com.beykus.beygram.data.music.MusicLibrary
import com.beykus.beygram.data.music.MusicOutcome
import com.beykus.beygram.data.music.MusicPlaylist
import com.beykus.beygram.data.music.PlaylistTrack
import com.beykus.beygram.data.music.playPlaylist
import com.beykus.beygram.data.music.resolvePlaylistTracks
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.playback.TrackArtwork
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The library of [accountId], or null while it is not shown: the repository is still reading an account, or the loaded
 * library belongs to another account. Screens show a loading state for null, so an empty placeholder or another account's
 * data never appears as the user's library.
 */
@Composable
internal fun rememberMusicLibrary(accountId: String): MusicLibrary? {
    val context = LocalContext.current
    val repository = remember(context) { MusicLibraries.get(context) }
    val library by repository.library.collectAsStateWithLifecycle()
    val loaded by repository.isLoaded.collectAsStateWithLifecycle()
    return if (loaded) libraryFor(accountId, library) else null
}

/**
 * The tracks of [candidates] that have a local file, or null while the lookup runs. The lookup asks Telegram about
 * every candidate, so it runs once for each change of the list, never during composition. The result then follows each
 * file as it finishes downloading.
 */
@Composable
internal fun rememberDownloadedTracks(
    telegram: TelegramRepository,
    accountId: String,
    candidates: List<PlaylistTrack>
): List<PlaylistTrack>? {
    val downloaded by produceState<List<PlaylistTrack>?>(null, telegram, accountId, candidates) {
        value = null
        val resolved = resolvePlaylistTracks(telegram, accountId, candidates)
        // Each resolved file is watched, so a download that finishes while the list is open updates it.
        val onDisk = resolved.map { item ->
            val fileId = item?.fileId
            if (fileId == null) {
                flowOf(false)
            } else {
                telegram.observeFile(fileId).map { it.localPath?.isNotBlank() == true }.distinctUntilChanged()
            }
        }
        if (onDisk.isEmpty()) {
            value = emptyList()
        } else {
            combine(onDisk) { flags -> downloadedTracks(candidates, flags.toList()) }.collect { value = it }
        }
    }
    return downloaded
}

/** Plays [playlist] from [startIndex] and says what could not be played. Call it from a coroutine. */
internal suspend fun playMusicPlaylist(
    context: Context,
    telegram: TelegramRepository,
    accountId: String,
    playlist: MusicPlaylist,
    startIndex: Int,
    shuffle: Boolean?
) {
    val result = playPlaylist(context, telegram, accountId, playlist, startIndex, shuffle)
    val unavailable = result.unavailableIndices.size
    when {
        !result.started -> context.showMusicToast(context.getString(R.string.music_play_none))
        unavailable > 0 -> context.showMusicToast(
            context.resources.getQuantityString(R.plurals.music_unavailable_count, unavailable, unavailable)
        )
    }
}

/** Shows a short message. Call it on the main thread. */
internal fun Context.showMusicToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

/** Explains a refused library change. Successful changes need no message. */
internal fun Context.reportMusicOutcome(outcome: MusicOutcome<*>) {
    if (outcome is MusicOutcome.Failed) showMusicToast(getString(musicFailureMessageRes(outcome.failure)))
}

/** The message that explains why a library change was refused. */
internal fun musicFailureMessageRes(failure: MusicFailure): Int = when (failure) {
    MusicFailure.InvalidTitle -> R.string.music_error_invalid_title
    MusicFailure.LimitReached -> R.string.music_error_limit
    MusicFailure.NotFound -> R.string.music_playlist_not_found
    MusicFailure.ProtectedPlaylist -> R.string.music_error_protected
    MusicFailure.ForeignAccount -> R.string.music_error_account
    MusicFailure.InvalidCover -> R.string.music_cover_failed
    MusicFailure.InvalidTrack -> R.string.music_error_invalid_track
    MusicFailure.DuplicateTrack -> R.string.music_already_in_playlist
    MusicFailure.InvalidPosition, MusicFailure.NoActiveAccount, MusicFailure.StorageUnavailable -> R.string.music_error_storage
}

/** Title of a smart collection. */
internal fun musicCollectionTitleRes(kind: MusicCollectionKind): Int = when (kind) {
    MusicCollectionKind.Recent -> R.string.music_section_recent
    MusicCollectionKind.Downloaded -> R.string.music_section_downloaded
    MusicCollectionKind.Frequent -> R.string.music_collection_frequent
    MusicCollectionKind.Added -> R.string.music_collection_added
    MusicCollectionKind.Lately -> R.string.music_collection_lately
}

/** A track row: a placeholder picture, title, performer and length. [unavailable] dims the row and replaces the performer. */
@Composable
internal fun MusicTrackRow(
    track: PlaylistTrack,
    unavailable: Boolean,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit = {}
) {
    val palette = beyGramPalette()
    val untitled = stringResource(R.string.music_untitled)
    val subtitle = if (unavailable) stringResource(R.string.music_unavailable) else track.performer
    val duration = formatClockDuration(track.durationSeconds)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .alpha(if (unavailable) 0.45f else 1f)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(palette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = palette.accentInk, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title.ifBlank { untitled }, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(
                subtitle,
                color = palette.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (duration.isNotEmpty()) Text(
            duration,
            color = palette.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
        trailing()
    }
}

/** Heading of a section, with an optional action on the right. */
@Composable
internal fun MusicSectionHeader(title: String, actionLabel: String?, onAction: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = palette.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null) TextButton(onClick = onAction) {
            Text(actionLabel, color = palette.accent)
        }
    }
}

/** A hint in place of a list that has nothing in it. */
@Composable
internal fun MusicEmptyText(text: String) {
    val palette = beyGramPalette()
    Text(
        text,
        color = palette.textSecondary,
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/** A spinner row for a list that is still being looked up. */
@Composable
internal fun MusicLoadingRow() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}

/** Cover of a playlist, or the generated placeholder when it has none. */
@Composable
internal fun MusicPlaylistCover(coverPath: String?, size: Dp, cornerRadius: Dp) {
    val palette = beyGramPalette()
    TrackArtwork(
        artworkPath = coverPath,
        audioPath = null,
        modifier = Modifier.size(size),
        background = palette.accentSoft,
        tint = palette.accentInk,
        symbolSize = size * 0.4f,
        cornerRadius = cornerRadius
    )
}

/**
 * A dialog with one text field. Confirming passes the text on, trimmed. Blank text is refused unless [allowBlank] is set,
 * and a single line is used unless the text may run long.
 */
@Composable
internal fun MusicTextDialog(
    title: String,
    initial: String,
    label: String,
    confirmText: String,
    singleLine: Boolean,
    allowBlank: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 3,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = allowBlank || text.isNotBlank(),
                onClick = { onConfirm(text.trim()) }
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
