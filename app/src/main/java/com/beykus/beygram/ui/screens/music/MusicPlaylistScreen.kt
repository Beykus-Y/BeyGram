package com.beykus.beygram.ui.screens.music

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import com.beykus.beygram.ui.components.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.beykus.beygram.ui.components.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.beykus.beygram.ui.components.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.beykus.beygram.data.music.MusicLibraryRepository
import com.beykus.beygram.data.music.MusicOutcome
import com.beykus.beygram.data.music.MusicPlaylist
import com.beykus.beygram.data.music.MusicSortMode
import com.beykus.beygram.data.music.PlaylistTrack
import com.beykus.beygram.data.music.fromChat
import com.beykus.beygram.data.music.resolvePlaylistTracks
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.bgmusic.BgMusicExportSheet
import kotlinx.coroutines.launch

/** The dialog of the playlist screen that is open, if any. */
private enum class PlaylistDialog { Rename, Description, Sort, Delete }

/**
 * A stored playlist, or a smart collection built from the library (see [MusicCollectionKind]). Stored playlists can be
 * edited; collections are read-only lists. The favorites playlist keeps its localized name and can be neither renamed nor
 * deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicPlaylistScreen(
    accountId: String,
    telegram: TelegramRepository,
    playlistId: String,
    onBack: () -> Unit,
    /** Opens the search, for an unavailable track that has no replacement in the library. */
    onSearch: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { MusicLibraries.get(context) }
    val library = rememberMusicLibrary(accountId)
    val collection = remember(playlistId) { MusicCollectionKind.fromId(playlistId) }
    // The tracks of the library that came from one chat, shown as a collection of their own.
    val chatId: Long? = remember(playlistId) { chatIdOfCollection(playlistId) }
    val nowMs = remember(library) { System.currentTimeMillis() }
    val stored: MusicPlaylist? = library?.playlist(playlistId)
    val candidates = remember(library, collection, chatId) {
        when {
            library == null -> emptyList<PlaylistTrack>()
            chatId != null -> fromChat(library, chatId).map { it.toPlaylistEntry() }
            collection != null -> collection.baseTracks(library, nowMs)
            else -> emptyList<PlaylistTrack>()
        }
    }
    // Only the downloaded collection needs the file lookup; its candidates are checked once per change of the list.
    val downloaded = rememberDownloadedTracks(
        telegram,
        accountId,
        if (collection == MusicCollectionKind.Downloaded) candidates else emptyList()
    )
    val tracks: List<PlaylistTrack> = when {
        stored != null -> stored.tracks
        collection == MusicCollectionKind.Downloaded -> downloaded.orEmpty()
        else -> candidates
    }
    val loadingTracks = collection == MusicCollectionKind.Downloaded && downloaded == null
    val resolved by produceState<List<PlaybackItem?>?>(null, telegram, accountId, tracks) {
        value = null
        value = resolvePlaylistTracks(telegram, accountId, tracks)
    }
    val unavailable = unavailableFlags(tracks.size, resolved)
    val title = when {
        stored?.isFavorites == true -> stringResource(R.string.music_favorites_title)
        stored != null -> stored.title
        collection != null -> stringResource(musicCollectionTitleRes(collection))
        chatId != null -> stringResource(R.string.music_chat_collection_title)
        // The library is loaded and holds no such playlist, for example because it was deleted.
        library != null -> stringResource(R.string.music_playlist_not_found)
        else -> ""
    }
    // Playback works on the playlist as it is stored. A collection is played as a list of its own.
    val playlist: MusicPlaylist? = stored ?: if (collection != null || chatId != null) syntheticPlaylist(playlistId, tracks) else null

    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<PlaylistDialog?>(null) }
    var trackMenu by remember { mutableStateOf<String?>(null) }
    // The unavailable track whose replacement is being looked for, if the sheet is open.
    var replacing by remember { mutableStateOf<PlaylistTrack?>(null) }
    var exportOpen by remember { mutableStateOf(false) }
    val copySuffix = stringResource(R.string.music_copy_suffix)
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { setPlaylistCover(context, repository, playlistId, uri) }
    }

    fun playAt(startIndex: Int, shuffle: Boolean?) {
        val target = playlist ?: return
        if (target.tracks.isEmpty()) return
        scope.launch { playMusicPlaylist(context, telegram, accountId, target, startIndex, shuffle) }
    }

    fun runEdit(change: suspend () -> MusicOutcome<*>) {
        scope.launch { context.reportMusicOutcome(change()) }
    }

    if (exportOpen && stored != null) BgMusicExportSheet(playlist = stored, onDismiss = { exportOpen = false })

    Scaffold(
        containerColor = palette.screen,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            stringResource(R.string.common_back),
                            tint = palette.textPrimary
                        )
                    }
                },
                actions = {
                    if (stored != null) Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, stringResource(R.string.music_playlist_menu), tint = palette.textPrimary)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (!stored.isFavorites) DropdownMenuItem(
                                text = { Text(stringResource(R.string.music_rename)) },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = palette.accent) },
                                onClick = { menuOpen = false; dialog = PlaylistDialog.Rename }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.music_edit_description)) },
                                leadingIcon = { Icon(Icons.Rounded.Info, null, tint = palette.accent) },
                                onClick = { menuOpen = false; dialog = PlaylistDialog.Description }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.music_change_cover)) },
                                leadingIcon = { Icon(Icons.Rounded.Image, null, tint = palette.accent) },
                                onClick = {
                                    menuOpen = false
                                    coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            )
                            if (stored.coverPath != null) DropdownMenuItem(
                                text = { Text(stringResource(R.string.music_remove_cover)) },
                                leadingIcon = { Icon(Icons.Rounded.Close, null, tint = palette.accent) },
                                onClick = { menuOpen = false; runEdit { repository.setCover(playlistId, null) } }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.music_sort)) },
                                leadingIcon = { Icon(Icons.Rounded.Tune, null, tint = palette.accent) },
                                onClick = { menuOpen = false; dialog = PlaylistDialog.Sort }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.music_duplicate)) },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, tint = palette.accent) },
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        val outcome = repository.duplicatePlaylist(playlistId, copySuffix)
                                        if (outcome is MusicOutcome.Done) {
                                            context.showMusicToast(context.getString(R.string.music_duplicated))
                                        } else {
                                            context.reportMusicOutcome(outcome)
                                        }
                                    }
                                }
                            )
                            if (stored.tracks.isNotEmpty()) DropdownMenuItem(
                                text = { Text(stringResource(R.string.bgmusic_menu_share_collection)) },
                                leadingIcon = { Icon(Icons.Rounded.Share, null, tint = palette.accent) },
                                onClick = { menuOpen = false; exportOpen = true }
                            )
                            if (!stored.isFavorites) DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_delete)) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = palette.danger) },
                                onClick = { menuOpen = false; dialog = PlaylistDialog.Delete }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface)
            )
        }
    ) { padding ->
        if (library == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (playlist == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                MusicEmptyText(stringResource(R.string.music_playlist_not_found))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "header") {
                    PlaylistHeader(
                        title = title,
                        description = stored?.description.orEmpty(),
                        coverPath = stored?.coverPath,
                        tracks = tracks
                    )
                }
                item(key = "actions") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { playAt(0, null) },
                            enabled = tracks.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.music_play)) }
                        OutlinedButton(
                            onClick = { playAt(0, true) },
                            enabled = tracks.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.player_shuffle)) }
                    }
                }
                if (loadingTracks) item(key = "loading") { MusicLoadingRow() }
                if (tracks.isEmpty() && !loadingTracks) item(key = "empty") {
                    MusicEmptyText(stringResource(emptyTextRes(stored, collection)))
                }
                itemsIndexed(tracks, key = { index, track -> "$index:${track.itemId}" }) { index, track ->
                    val isUnavailable = unavailable.getOrElse(index) { false }
                    MusicTrackRow(
                        track = track,
                        unavailable = isUnavailable,
                        onClick = if (isUnavailable) null else ({ playAt(index, null) }),
                        trailing = {
                            if (stored != null) Box {
                                IconButton(onClick = { trackMenu = track.itemId }) {
                                    Icon(
                                        Icons.Rounded.MoreVert,
                                        stringResource(R.string.music_track_menu),
                                        tint = palette.textSecondary
                                    )
                                }
                                DropdownMenu(
                                    expanded = trackMenu == track.itemId,
                                    onDismissRequest = { trackMenu = null }
                                ) {
                                    if (canMoveUp(index)) DropdownMenuItem(
                                        text = { Text(stringResource(R.string.music_move_up)) },
                                        leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null, tint = palette.accent) },
                                        onClick = {
                                            trackMenu = null
                                            runEdit { repository.moveTrack(playlistId, index, index - 1) }
                                        }
                                    )
                                    if (canMoveDown(index, tracks.size)) DropdownMenuItem(
                                        text = { Text(stringResource(R.string.music_move_down)) },
                                        leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null, tint = palette.accent) },
                                        onClick = {
                                            trackMenu = null
                                            runEdit { repository.moveTrack(playlistId, index, index + 1) }
                                        }
                                    )
                                    if (isUnavailable) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.music_find_replacement)) },
                                            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = palette.accent) },
                                            onClick = {
                                                trackMenu = null
                                                replacing = track
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.common_delete)) },
                                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = palette.danger) },
                                            onClick = {
                                                trackMenu = null
                                                runEdit { repository.removeTrack(playlistId, track.itemId) }
                                            }
                                        )
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.music_remove_from_playlist)) },
                                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = palette.danger) },
                                            onClick = {
                                                trackMenu = null
                                                runEdit { repository.removeTrack(playlistId, track.itemId) }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (dialog == PlaylistDialog.Rename && stored != null) MusicTextDialog(
        title = stringResource(R.string.music_rename),
        initial = stored.title,
        label = stringResource(R.string.music_playlist_name),
        confirmText = stringResource(R.string.common_save),
        singleLine = true,
        allowBlank = false,
        onConfirm = { name ->
            dialog = null
            runEdit { repository.renamePlaylist(playlistId, name) }
        },
        onDismiss = { dialog = null }
    )
    if (dialog == PlaylistDialog.Description && stored != null) MusicTextDialog(
        title = stringResource(R.string.music_edit_description),
        initial = stored.description,
        label = stringResource(R.string.music_playlist_description),
        confirmText = stringResource(R.string.common_save),
        singleLine = false,
        allowBlank = true,
        onConfirm = { text ->
            dialog = null
            runEdit { repository.updateDescription(playlistId, text) }
        },
        onDismiss = { dialog = null }
    )
    if (dialog == PlaylistDialog.Sort && stored != null) AlertDialog(
        onDismissRequest = { dialog = null },
        title = { Text(stringResource(R.string.music_sort)) },
        text = {
            Column {
                MusicSortMode.entries.forEach { mode ->
                    SortOption(
                        label = stringResource(sortLabelRes(mode)),
                        selected = stored.sortMode == mode,
                        onClick = {
                            dialog = null
                            runEdit { repository.sortPlaylist(playlistId, mode) }
                        }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.common_close)) } }
    )
    if (dialog == PlaylistDialog.Delete && stored != null) AlertDialog(
        onDismissRequest = { dialog = null },
        title = { Text(stringResource(R.string.music_delete_playlist_title)) },
        text = { Text(stringResource(R.string.music_delete_playlist_text)) },
        confirmButton = {
            TextButton(onClick = {
                dialog = null
                scope.launch {
                    val outcome = repository.deletePlaylist(playlistId)
                    if (outcome is MusicOutcome.Failed) context.reportMusicOutcome(outcome) else onBack()
                }
            }) { Text(stringResource(R.string.common_delete), color = palette.danger) }
        },
        dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.common_cancel)) } }
    )
    // Open while the user looks for a replacement for the unavailable entry held in `replacing`.
    val missing = replacing
    if (missing != null && stored != null) ReplacementSheet(
        accountId = accountId,
        telegram = telegram,
        playlistId = playlistId,
        missing = missing,
        onSearch = onSearch,
        onDismiss = { replacing = null }
    )
}

/** Picks the cover: a reduced copy of the picture is stored in the app, then the playlist refers to it. */
private suspend fun setPlaylistCover(
    context: Context,
    repository: MusicLibraryRepository,
    playlistId: String,
    source: Uri
) {
    val file = importMusicCover(context, source)
    if (file == null) {
        context.showMusicToast(context.getString(R.string.music_cover_failed))
        return
    }
    val outcome = repository.setCover(playlistId, file.absolutePath)
    if (outcome is MusicOutcome.Failed) {
        // The library deletes the covers it replaces. A refused picture is not kept by it, so it is removed here.
        removeMusicCoverFile(context, file.absolutePath)
        context.reportMusicOutcome(outcome)
    }
}

/** The cover, the name, the description and the size of the list. */
@Composable
private fun PlaylistHeader(title: String, description: String, coverPath: String?, tracks: List<PlaylistTrack>) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MusicPlaylistCover(coverPath = coverPath, size = 112.dp, cornerRadius = 20.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = palette.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (description.isNotBlank()) Text(
                description,
                color = palette.textSecondary,
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                summaryText(tracks.size, totalDurationSeconds(tracks)),
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** "3 tracks · 1 h 12 min", the length left out for an empty list. */
@Composable
private fun summaryText(count: Int, totalSeconds: Long): String {
    val tracks = pluralStringResource(R.plurals.music_track_count, count, count)
    if (count == 0) return tracks
    val parts = durationParts(totalSeconds)
    val length = when {
        parts.hours > 0 && parts.minutes > 0 ->
            stringResource(R.string.music_duration_hours_minutes, parts.hours, parts.minutes)
        parts.hours > 0 -> stringResource(R.string.music_duration_hours, parts.hours)
        else -> stringResource(R.string.music_duration_minutes, parts.minutes)
    }
    return "$tracks · $length"
}

/** One sort order of the sort dialog. The chosen one is checked. */
@Composable
private fun SortOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (selected) palette.accent else palette.textPrimary,
            modifier = Modifier.weight(1f)
        )
        if (selected) Icon(Icons.Rounded.Check, null, tint = palette.accent)
    }
}

/** The hint for a list with nothing in it. */
private fun emptyTextRes(stored: MusicPlaylist?, collection: MusicCollectionKind?): Int = when {
    stored?.isFavorites == true -> R.string.music_empty_favorites
    stored != null -> R.string.music_playlist_empty
    collection == MusicCollectionKind.Recent -> R.string.music_empty_recent
    collection == MusicCollectionKind.Downloaded -> R.string.music_empty_downloaded
    else -> R.string.music_collection_empty
}

private fun sortLabelRes(mode: MusicSortMode): Int = when (mode) {
    MusicSortMode.Manual -> R.string.music_sort_manual
    MusicSortMode.Title -> R.string.music_sort_title
    MusicSortMode.Artist -> R.string.music_sort_artist
    MusicSortMode.AddedAt -> R.string.music_sort_added
    MusicSortMode.Duration -> R.string.music_sort_duration
}
