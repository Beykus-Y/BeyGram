package com.beykus.beygram.ui.screens.music

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.beykus.beygram.data.music.MUSIC_FAVORITES_PLAYLIST_ID
import com.beykus.beygram.data.music.MusicLibraries
import com.beykus.beygram.data.music.MusicOutcome
import com.beykus.beygram.data.music.MusicPlaylist
import com.beykus.beygram.data.music.PlaylistTrack
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.launch

/**
 * The user's music: favorites, recently played, downloaded tracks, playlists and smart collections. A track plays the
 * section it is listed in, from its own position. The section titles open the full list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MyMusicScreen(
    accountId: String,
    telegram: TelegramRepository,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onOpenPlaylist: (String) -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { MusicLibraries.get(context) }
    val library = rememberMusicLibrary(accountId)
    val nowMs = remember(library) { System.currentTimeMillis() }
    val favorites = remember(library) { library?.favorites?.tracks.orEmpty() }
    val recent = remember(library) { library?.let { recentTracks(it) }.orEmpty() }
    val playlists = remember(library) { library?.let { userPlaylists(it) }.orEmpty() }
    val collections = remember(library, nowMs) {
        library?.let { lib ->
            visibleSmartCollections(lib, nowMs).map { kind -> kind to kind.baseTracks(lib, nowMs).size }
        }.orEmpty()
    }
    val downloadCandidates = remember(library, nowMs) {
        library?.let { MusicCollectionKind.Downloaded.baseTracks(it, nowMs) }.orEmpty()
    }
    val downloaded = rememberDownloadedTracks(telegram, accountId, downloadCandidates)
    var creating by remember { mutableStateOf(false) }

    fun playSection(collectionId: String, tracks: List<PlaylistTrack>, index: Int) {
        scope.launch {
            playMusicPlaylist(context, telegram, accountId, syntheticPlaylist(collectionId, tracks), index, null)
        }
    }

    Scaffold(
        containerColor = palette.screen,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.music_library_title),
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold
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
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Rounded.Search, stringResource(R.string.music_search_action), tint = palette.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface)
            )
        }
    ) { padding ->
        if (library == null) {
            // The library of this account is still being read; no placeholder is shown in its place.
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "favorites") {
                    MusicSectionHeader(
                        title = stringResource(R.string.music_favorites_title),
                        actionLabel = if (favorites.isEmpty()) null else stringResource(R.string.music_show_all),
                        onAction = { onOpenPlaylist(MUSIC_FAVORITES_PLAYLIST_ID) }
                    )
                }
                if (favorites.isEmpty()) item(key = "favorites-empty") {
                    MusicEmptyText(stringResource(R.string.music_empty_favorites))
                }
                itemsIndexed(
                    favorites.take(MUSIC_PREVIEW_ROWS),
                    key = { index, track -> "favorites:$index:${track.itemId}" }
                ) { index, track ->
                    MusicTrackRow(
                        track = track,
                        unavailable = false,
                        onClick = { playSection(MUSIC_FAVORITES_PLAYLIST_ID, favorites, index) }
                    )
                }

                item(key = "recent") {
                    MusicSectionHeader(
                        title = stringResource(R.string.music_section_recent),
                        actionLabel = if (recent.isEmpty()) null else stringResource(R.string.music_show_all),
                        onAction = { onOpenPlaylist(MusicCollectionKind.Recent.id) }
                    )
                }
                if (recent.isEmpty()) item(key = "recent-empty") {
                    MusicEmptyText(stringResource(R.string.music_empty_recent))
                }
                itemsIndexed(
                    recent.take(MUSIC_PREVIEW_ROWS),
                    key = { index, track -> "recent:$index:${track.itemId}" }
                ) { index, track ->
                    MusicTrackRow(
                        track = track,
                        unavailable = false,
                        onClick = { playSection(MusicCollectionKind.Recent.id, recent, index) }
                    )
                }

                item(key = "downloaded") {
                    MusicSectionHeader(
                        title = stringResource(R.string.music_section_downloaded),
                        actionLabel = if (downloaded.isNullOrEmpty()) null else stringResource(R.string.music_show_all),
                        onAction = { onOpenPlaylist(MusicCollectionKind.Downloaded.id) }
                    )
                }
                when {
                    downloaded == null -> item(key = "downloaded-loading") { MusicLoadingRow() }
                    downloaded.isEmpty() -> item(key = "downloaded-empty") {
                        MusicEmptyText(stringResource(R.string.music_empty_downloaded))
                    }
                    else -> itemsIndexed(
                        downloaded.take(MUSIC_PREVIEW_ROWS),
                        key = { index, track -> "downloaded:$index:${track.itemId}" }
                    ) { index, track ->
                        MusicTrackRow(
                            track = track,
                            unavailable = false,
                            onClick = { playSection(MusicCollectionKind.Downloaded.id, downloaded, index) }
                        )
                    }
                }

                item(key = "playlists") {
                    MusicSectionHeader(
                        title = stringResource(R.string.music_section_playlists),
                        actionLabel = null,
                        onAction = {}
                    )
                }
                if (playlists.isEmpty()) item(key = "playlists-empty") {
                    MusicEmptyText(stringResource(R.string.music_empty_playlists))
                }
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistRow(playlist = playlist, onClick = { onOpenPlaylist(playlist.id) })
                }
                item(key = "playlists-create") {
                    MusicActionRow(
                        icon = Icons.Rounded.Add,
                        label = stringResource(R.string.music_playlist_create),
                        onClick = { creating = true }
                    )
                }

                if (collections.isNotEmpty()) {
                    item(key = "collections") {
                        MusicSectionHeader(
                            title = stringResource(R.string.music_section_collections),
                            actionLabel = null,
                            onAction = {}
                        )
                    }
                    item(key = "collections-row") {
                        LazyRow(
                            Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(collections, key = { it.first.id }) { (kind, count) ->
                                CollectionCard(
                                    title = stringResource(musicCollectionTitleRes(kind)),
                                    count = count,
                                    onClick = { onOpenPlaylist(kind.id) }
                                )
                            }
                        }
                    }
                }
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
                    is MusicOutcome.Done -> onOpenPlaylist(created.value.id)
                }
            }
        },
        onDismiss = { creating = false }
    )
}

/** A stored playlist in the list: cover, name and track count. */
@Composable
private fun PlaylistRow(playlist: MusicPlaylist, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MusicPlaylistCover(coverPath = playlist.coverPath, size = 44.dp, cornerRadius = 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.title, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.music_track_count, playlist.tracks.size, playlist.tracks.size),
                color = palette.textSecondary,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.textSecondary)
    }
}

/** A row with an icon and a label that starts something, such as creating a playlist. */
@Composable
private fun MusicActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(palette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = palette.accentInk, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = palette.accent, fontWeight = FontWeight.SemiBold)
    }
}

/** A smart collection in the horizontal row: its title and how many tracks it holds. */
@Composable
private fun CollectionCard(title: String, count: Int, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Column(
        Modifier.size(width = 150.dp, height = 96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.accentSoft)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            color = palette.accentInk,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            pluralStringResource(R.plurals.music_track_count, count, count),
            color = palette.textSecondary,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}
