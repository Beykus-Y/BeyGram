package com.beykus.beygram.ui.screens.bgmusic

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.music.MUSIC_COVER_DIRECTORY
import com.beykus.beygram.data.music.MusicLibraries
import com.beykus.beygram.data.music.MusicLibraryRepository
import com.beykus.beygram.data.music.MusicOutcome
import com.beykus.beygram.data.music.MusicPlaylist
import com.beykus.beygram.data.music.PlaylistTrack
import com.beykus.beygram.data.music.bgmusic.BgMusicImportDecision
import com.beykus.beygram.data.music.bgmusic.BgMusicImportPlanner
import com.beykus.beygram.data.music.bgmusic.BgMusicLibraryMapping
import com.beykus.beygram.data.music.bgmusic.BgMusicParsed
import com.beykus.beygram.data.music.bgmusic.BgMusicResolvedTrack
import com.beykus.beygram.data.music.bgmusic.BgMusicSourceResolver
import com.beykus.beygram.data.music.bgmusic.BgMusicTrack
import com.beykus.beygram.data.music.toPlaylistTrack
import com.beykus.beygram.playback.LocalPlaybackAccountId
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackQueueSource
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.LocalMediaImage
import com.beykus.beygram.ui.screens.playback.LocalPlaybackRepositorySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Preview of a `.bgmusic` file in a dialog. It lists the tracks, marks the ones this account can play, and does only what the
 * user asks for: the available tracks play when the user taps «Прослушать доступное» (nothing starts on its own), the
 * collection is saved only on «Сохранить себе», and one available track is added to a local playlist per tap.
 * [onOpenLocalPlaylist] is the hook for opening a local playlist, for example when the file is already saved. Without it no
 * open action is shown.
 */
@Composable
fun BgMusicPreviewScreen(
    parsed: BgMusicParsed,
    onDismiss: () -> Unit,
    onOpenLocalPlaylist: ((playlistId: String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    val source = LocalPlaybackRepositorySource.current
    val repository = source?.repository
    val accountId = source?.accountId ?: LocalPlaybackAccountId.current
    val libraryRepository = remember(context) { MusicLibraries.get(context) }
    val library by libraryRepository.library.collectAsStateWithLifecycle()
    val libraryLoaded by libraryRepository.isLoaded.collectAsStateWithLifecycle()
    val favoritesName = stringResource(R.string.bgmusic_favorites_name)
    val playlist = parsed as? BgMusicParsed.Playlist
    val manifest = playlist?.manifest
    val coverPath = playlist?.coverPath

    // null while the posts are still being looked up; then a map from track id to the post that was found.
    var resolved by remember(manifest, accountId) { mutableStateOf<Map<String, BgMusicResolvedTrack>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var savedId by remember { mutableStateOf<String?>(null) }
    var messageRes by remember { mutableStateOf<Int?>(null) }
    var askChoice by remember { mutableStateOf(false) }
    var pendingTrack by remember { mutableStateOf<PlaylistTrack?>(null) }

    LaunchedEffect(manifest, repository, accountId) {
        val current = manifest ?: return@LaunchedEffect
        resolved = if (repository == null) {
            emptyMap()
        } else {
            BgMusicSourceResolver.resolve(repository, accountId, current)
        }
    }

    val found = resolved
    val decision = if (manifest != null && libraryLoaded) {
        BgMusicImportPlanner.decide(manifest.playlistId, manifest.revision, library.playlists)
    } else {
        null
    }
    val localCopy = if (manifest != null && libraryLoaded) {
        BgMusicImportPlanner.matchingCopy(manifest.playlistId, library.playlists)
    } else {
        null
    }
    val saved = savedId

    fun importFile(replaceLocalId: String?) {
        val current = manifest ?: return
        scope.launch {
            busy = true
            messageRes = null
            try {
                val origins = (resolved ?: repository?.let { BgMusicSourceResolver.resolve(it, accountId, current) }.orEmpty())
                    .mapValues { it.value.origin }
                val snapshot = BgMusicLibraryMapping.toPlaylistCopy(current, System.currentTimeMillis(), origins)
                if (replaceLocalId == null) {
                    when (val outcome = libraryRepository.importPlaylistCopy(snapshot)) {
                        is MusicOutcome.Done -> {
                            savedId = outcome.value.id
                            attachCover(context, libraryRepository, outcome.value.id, coverPath)
                            messageRes = R.string.bgmusic_import_saved
                        }
                        is MusicOutcome.Failed -> messageRes = R.string.bgmusic_import_failed
                    }
                } else {
                    when (libraryRepository.replaceImportedPlaylist(replaceLocalId, snapshot)) {
                        is MusicOutcome.Done -> {
                            savedId = replaceLocalId
                            attachCover(context, libraryRepository, replaceLocalId, coverPath)
                            messageRes = R.string.bgmusic_import_updated
                        }
                        is MusicOutcome.Failed -> messageRes = R.string.bgmusic_import_failed
                    }
                }
            } finally {
                busy = false
            }
        }
    }

    fun onSaveClicked() {
        when (decision) {
            is BgMusicImportDecision.NewPlaylist -> importFile(null)
            is BgMusicImportDecision.NewerRevision, is BgMusicImportDecision.OlderRevision -> askChoice = true
            else -> Unit
        }
    }

    fun listenAvailable() {
        val current = manifest ?: return
        val repo = repository ?: return
        val availableNow = resolved ?: return
        val queue = current.tracks.mapNotNull { availableNow[it.id]?.item }
        if (queue.isEmpty()) return
        PlaybackEngine.playQueue(
            context,
            queue,
            0,
            PlaybackQueueSource(accountId, repo::observeFile, repo::downloadFile, repo::autoDownloadFile),
        )
        onDismiss()
    }

    fun addToPlaylist(track: PlaylistTrack, target: MusicPlaylist) {
        pendingTrack = null
        scope.launch {
            val text = when (val outcome = libraryRepository.addTracks(target.id, listOf(track))) {
                is MusicOutcome.Done -> when {
                    outcome.value.added > 0 -> context.getString(
                        R.string.bgmusic_add_done,
                        if (target.isFavorites) favoritesName else target.title,
                    )
                    outcome.value.duplicates > 0 -> context.getString(R.string.bgmusic_add_duplicate)
                    else -> context.getString(R.string.bgmusic_add_rejected)
                }
                is MusicOutcome.Failed -> context.getString(R.string.bgmusic_add_failed)
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    val availableCount = found?.size ?: 0
    val totalTracks = manifest?.tracks?.size ?: 0
    val pickedTrack = pendingTrack

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 12.dp),
            shape = RoundedCornerShape(20.dp),
            color = palette.surface,
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (coverPath != null) {
                        LocalMediaImage(
                            path = coverPath,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                            maxDimension = 256,
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            manifest?.title ?: stringResource(R.string.bgmusic_preview_default_title),
                            color = palette.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (manifest != null) {
                            val trackLine = pluralStringResource(R.plurals.bgmusic_tracks_count, totalTracks, totalTracks)
                            Text(trackLine, color = palette.textSecondary, fontSize = 13.sp)
                        }
                        manifest?.author?.let { author ->
                            Text(
                                stringResource(R.string.bgmusic_author_line, author.name),
                                color = palette.textSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                manifest?.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        description,
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (manifest != null) {
                    val statusText = when {
                        repository == null -> stringResource(R.string.bgmusic_preview_no_account)
                        found == null -> stringResource(R.string.bgmusic_preview_searching)
                        else -> stringResource(R.string.bgmusic_preview_available, availableCount, totalTracks)
                    }
                    Text(statusText, color = palette.textSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        items(manifest.tracks, key = { it.id }) { track ->
                            TrackRow(
                                track = track,
                                available = found?.containsKey(track.id) == true,
                                searching = found == null,
                                onAdd = {
                                    val item = found?.get(track.id)?.item?.toPlaylistTrack()
                                    pendingTrack = item
                                },
                            )
                            HorizontalDivider(color = palette.divider)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ImportStatus(
                        decision = decision,
                        messageRes = messageRes,
                        busy = busy,
                        openId = saved ?: localCopy?.id?.takeIf { decision is BgMusicImportDecision.SameRevision },
                        onOpen = onOpenLocalPlaylist,
                    )
                    Button(
                        onClick = ::listenAvailable,
                        enabled = availableCount > 0 && repository != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.bgmusic_listen_available))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = ::onSaveClicked,
                        enabled = libraryLoaded && !busy && (
                            decision is BgMusicImportDecision.NewPlaylist ||
                                decision is BgMusicImportDecision.NewerRevision ||
                                decision is BgMusicImportDecision.OlderRevision
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.bgmusic_action_save))
                    }
                } else {
                    val unsupported = parsed is BgMusicParsed.Unsupported
                    Text(
                        stringResource(if (unsupported) R.string.bgmusic_unsupported_version else R.string.bgmusic_invalid_file),
                        color = if (unsupported) palette.warningText else palette.danger,
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.bgmusic_close))
                    }
                }
            }
        }
    }

    if (askChoice && manifest != null) {
        val newer = decision is BgMusicImportDecision.NewerRevision
        val localId = localCopy?.id
        AlertDialog(
            onDismissRequest = { askChoice = false },
            title = {
                Text(stringResource(if (newer) R.string.bgmusic_import_newer_title else R.string.bgmusic_import_older_title))
            },
            text = {
                Text(stringResource(if (newer) R.string.bgmusic_import_newer_text else R.string.bgmusic_import_older_text))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        askChoice = false
                        importFile(if (newer) localId else null)
                    },
                ) {
                    Text(stringResource(if (newer) R.string.bgmusic_action_update else R.string.bgmusic_action_save_copy))
                }
            },
            dismissButton = {
                Row {
                    if (newer) {
                        TextButton(onClick = {
                            askChoice = false
                            importFile(null)
                        }) {
                            Text(stringResource(R.string.bgmusic_action_save_copy))
                        }
                    }
                    TextButton(onClick = { askChoice = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            },
        )
    }

    if (pickedTrack != null) {
        AlertDialog(
            onDismissRequest = { pendingTrack = null },
            title = { Text(stringResource(R.string.bgmusic_add_picker_title)) },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    library.playlists.forEach { target ->
                        Text(
                            if (target.isFavorites) favoritesName else target.title,
                            color = palette.textPrimary,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { addToPlaylist(pickedTrack, target) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingTrack = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/** One track: title and artist, its length, and the add action only when this account can play it. */
@Composable
private fun TrackRow(
    track: BgMusicTrack,
    available: Boolean,
    searching: Boolean,
    onAdd: () -> Unit,
) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = if (available) palette.textPrimary else palette.textSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(
                        details,
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (track.durationSec > 0L) {
                Spacer(Modifier.width(8.dp))
                Text(bgMusicDurationText(track.durationSec), color = palette.textSecondary, fontSize = 12.sp)
            }
        }
        when {
            available -> TextButton(onClick = onAdd) {
                Text(stringResource(R.string.bgmusic_add_to_playlist), fontSize = 13.sp)
            }
            !searching -> Text(
                stringResource(R.string.bgmusic_track_unavailable),
                color = palette.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 12.dp, top = 2.dp),
            )
        }
    }
}

/** What the import found, what happened last, and the way to the local copy when there is one. */
@Composable
private fun ImportStatus(
    decision: BgMusicImportDecision?,
    messageRes: Int?,
    busy: Boolean,
    openId: String?,
    onOpen: ((playlistId: String) -> Unit)?,
) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth()) {
        if (decision is BgMusicImportDecision.SameRevision) {
            Text(
                stringResource(R.string.bgmusic_import_already_saved),
                color = palette.successText,
                fontSize = 13.sp,
            )
        }
        val result = messageRes
        if (result != null) {
            Text(stringResource(result), color = palette.textSecondary, fontSize = 13.sp)
        }
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = palette.accent)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.bgmusic_import_working), color = palette.textSecondary, fontSize = 13.sp)
            }
        }
        val target = openId
        if (onOpen != null && target != null && !busy) {
            TextButton(onClick = { onOpen(target) }) {
                Text(stringResource(R.string.bgmusic_action_open))
            }
        }
    }
}

/**
 * Copies the cached cover into the library's own cover folder. A playlist cover may only point there, so the cache file cannot
 * be used directly. A failed copy leaves the playlist without a cover.
 */
private suspend fun attachCover(
    context: Context,
    library: MusicLibraryRepository,
    playlistId: String,
    source: String?,
) {
    if (source == null) return
    val copied = withContext(Dispatchers.IO) { copyCoverIntoLibrary(context, source) } ?: return
    if (library.setCover(playlistId, copied) !is MusicOutcome.Done) File(copied).delete()
}

private fun copyCoverIntoLibrary(context: Context, source: String): String? = runCatching {
    val root = File(context.filesDir, MUSIC_COVER_DIRECTORY).apply { mkdirs() }
    val extension = File(source).extension.let { if (it.isEmpty()) "" else ".$it" }
    val target = File(root, UUID.randomUUID().toString() + extension)
    File(source).copyTo(target)
    target.absolutePath
}.getOrNull()
