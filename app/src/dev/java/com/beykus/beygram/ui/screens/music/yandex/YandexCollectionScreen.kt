package com.beykus.beygram.ui.screens.music.yandex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.data.music.provider.MusicProvider
import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.ProviderArtwork
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTrack
import com.beykus.beygram.data.music.provider.ProviderTracks
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.topAppBarSurface
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.screens.music.MusicCardColumn
import com.beykus.beygram.ui.screens.music.MusicCollectionHeader
import com.beykus.beygram.ui.screens.music.MusicEmptyText
import com.beykus.beygram.ui.screens.music.MusicLoadingRow
import com.beykus.beygram.ui.screens.music.ProviderTrackMenuItems
import com.beykus.beygram.ui.screens.music.ProviderTrackRow
import com.beykus.beygram.ui.screens.music.musicCollectionSummary
import com.beykus.beygram.ui.screens.music.rememberProviderCover
import com.beykus.beygram.ui.screens.playback.TrackArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the header of a collection shows, known before the tracks or from them. */
private data class CollectionHead(val title: String, val subtitle: String, val coverUrl: String?, val count: Int?)

/** Liked tracks are loaded a page at a time as the list scrolls. */
private const val LIKED_PAGE = 50

/** Rows from the end at which the next liked page is asked for. */
private const val LIKED_PREFETCH = 15

/**
 * One Yandex collection: a playlist, an album or the whole liked list. Header with the cover, «Слушать» and
 * «Перемешать», then the tracks; an album numbers its tracks. Each row has a menu: play next, like, its album, share
 * the link. Nothing here forwards or exports a file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun YandexCollectionScreen(
    ref: YandexCollectionRef,
    provider: MusicProvider?,
    snapshot: YandexLibrarySnapshot?,
    onBack: () -> Unit,
    onOpenCollection: (String) -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val likedTitle = stringResource(R.string.yandex_library_liked)
    val playlistTitle = stringResource(R.string.yandex_library_playlists)

    var head by remember(ref) {
        mutableStateOf(
            when (ref) {
                YandexCollectionRef.Liked -> CollectionHead(likedTitle, "", null, snapshot?.likedIds?.size)
                is YandexCollectionRef.Playlist -> snapshot?.playlists
                    ?.firstOrNull { it.kind == ref.kind && (ref.ownerUid == null && it.isOwn || it.ownerUid == ref.ownerUid) }
                    ?.let { CollectionHead(it.title, if (it.isOwn) "" else it.ownerName.orEmpty(), it.coverUrl, it.trackCount) }
                    ?: CollectionHead(playlistTitle, "", null, null)
                is YandexCollectionRef.Album -> snapshot?.albums?.firstOrNull { it.id == ref.id }
                    ?.let { album ->
                        CollectionHead(album.title, listOfNotNull(album.performer.takeIf { it.isNotBlank() }, album.year?.toString()).joinToString(" · "), album.coverUrl, album.trackCount)
                    }
                    ?: CollectionHead("", "", null, null)
            }
        )
    }
    var tracks by remember(ref) { mutableStateOf<List<ProviderTrack>>(emptyList()) }
    var loading by remember(ref) { mutableStateOf(true) }
    var failure by remember(ref) { mutableStateOf<MusicProviderFailure?>(null) }
    // The liked list: every id at once, the tracks a page at a time.
    var likedIds by remember(ref) { mutableStateOf<List<String>?>(null) }
    var attempt by remember(ref) { mutableStateOf(0) }

    LaunchedEffect(ref, provider, attempt) {
        if (provider == null) return@LaunchedEffect
        loading = true
        failure = null
        val result: ProviderResult<List<ProviderTrack>> = withContext(Dispatchers.IO) {
            when (ref) {
                YandexCollectionRef.Liked -> {
                    val ids = snapshot?.likedIds?.takeIf { attempt == 0 }
                        ?: when (val fresh = provider.likedTrackIds()) {
                            is ProviderResult.Failure -> return@withContext fresh
                            is ProviderResult.Success -> fresh.value
                        }
                    likedIds = ids
                    provider.tracks(ids.take(LIKED_PAGE))
                }
                is YandexCollectionRef.Playlist -> provider.playlistTracks(ref.kind, ref.ownerUid)
                is YandexCollectionRef.Album -> when (val album = provider.album(ref.id)) {
                    is ProviderResult.Failure -> album
                    is ProviderResult.Success -> {
                        val info = album.value.album
                        head = CollectionHead(
                            info.title,
                            listOfNotNull(info.performer.takeIf { it.isNotBlank() }, info.year?.toString()).joinToString(" · "),
                            info.coverUrl,
                            album.value.tracks.size
                        )
                        ProviderResult.Success(album.value.tracks)
                    }
                }
            }
        }
        when (result) {
            is ProviderResult.Success -> {
                val list = if (ref == YandexCollectionRef.Liked) result.value.map { it.copy(liked = true) } else result.value
                ProviderTracks.remember(list)
                tracks = list
                if (ref == YandexCollectionRef.Liked) head = head.copy(count = likedIds?.size)
            }
            is ProviderResult.Failure -> failure = result.failure
        }
        loading = false
    }

    // Next liked page once the list nears its end.
    val listState = rememberLazyListState()
    val nearEnd by remember(listState) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - LIKED_PREFETCH
        }
    }
    var pageLoading by remember(ref) { mutableStateOf(false) }
    LaunchedEffect(ref, provider) {
        if (ref != YandexCollectionRef.Liked || provider == null) return@LaunchedEffect
        snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect {
            val ids = likedIds ?: return@collect
            if (pageLoading || loading || tracks.size >= ids.size) return@collect
            pageLoading = true
            val next = withContext(Dispatchers.IO) { provider.tracks(ids.drop(tracks.size).take(LIKED_PAGE)) }
            if (next is ProviderResult.Success) {
                val page = next.value.map { it.copy(liked = true) }
                ProviderTracks.remember(page)
                tracks = tracks + page
            }
            pageLoading = false
        }
    }

    /** Plays from [index]; the liked list loads the rest of its first tracks so the queue goes on past the page. */
    fun play(index: Int, shuffle: Boolean) {
        val ids = likedIds
        if (ref == YandexCollectionRef.Liked && provider != null && ids != null && ids.size > tracks.size) {
            val startId = tracks.getOrNull(index)?.trackId
            scope.launch {
                val all = withContext(Dispatchers.IO) { provider.tracks(ids.take(LIKED_PLAY_LIMIT)) }
                val list = (all as? ProviderResult.Success)?.value ?: tracks
                val start = list.indexOfFirst { it.trackId == startId }
                // A row past the first tracks plays from the pages loaded so far.
                if (start < 0 && !shuffle) startList(context, tracks, index, false)
                else startList(context, list, start.coerceAtLeast(0), shuffle)
            }
        } else {
            startList(context, tracks, index, shuffle)
        }
    }

    Scaffold(
        containerColor = palette.screen,
        topBar = {
            TopAppBar(
                modifier = Modifier.topAppBarSurface(),
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), tint = palette.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "header") {
                val count = head.count ?: tracks.size
                MusicCollectionHeader(
                    title = head.title,
                    subtitle = head.subtitle,
                    summary = if (loading && head.count == null) "" else musicCollectionSummary(
                        count,
                        if (tracks.size >= count) tracks.sumOf { it.durationMs } / 1_000L else 0L
                    )
                ) { modifier ->
                    if (ref == YandexCollectionRef.Liked) {
                        Box(modifier.clip(styledShape(20.dp)).background(palette.accentSoft), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Favorite, null, tint = palette.accentInk, modifier = Modifier.size(44.dp))
                        }
                    } else {
                        TrackArtwork(
                            artworkPath = rememberProviderCover(head.coverUrl),
                            audioPath = null,
                            modifier = modifier,
                            background = palette.accentSoft,
                            tint = palette.accentInk,
                            symbolSize = 40.dp,
                            cornerRadius = 20.dp
                        )
                    }
                }
            }
            item(key = "actions") {
                val playable = tracks.any { it.isAvailable }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(onClick = { play(0, false) }, enabled = playable, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.yandex_library_play))
                    }
                    OutlinedButton(onClick = { play(0, true) }, enabled = playable, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.player_shuffle))
                    }
                }
            }
            val error = failure
            when {
                loading -> item(key = "loading") { MusicLoadingRow() }
                error != null -> item(key = "failure") {
                    Box(Modifier.fillMaxWidth().padding(16.dp)) {
                        Button(onClick = { attempt++ }) { Text(stringResource(R.string.yandex_library_retry)) }
                    }
                }
                tracks.isEmpty() -> item(key = "empty") { MusicEmptyText(stringResource(R.string.yandex_library_empty)) }
                else -> item(key = "tracks") {
                    MusicCardColumn {
                        tracks.forEachIndexed { index, track ->
                            TrackRowWithMenu(
                                track = track,
                                number = if (ref is YandexCollectionRef.Album) index + 1 else null,
                                inAlbum = (ref as? YandexCollectionRef.Album)?.id,
                                onClick = if (track.isAvailable) ({ play(index, false) }) else null,
                                onOpenCollection = onOpenCollection
                            )
                        }
                    }
                }
            }
            if (pageLoading) item(key = "page-loading") { MusicLoadingRow() }
        }
    }
}

@Composable
private fun TrackRowWithMenu(
    track: ProviderTrack,
    number: Int?,
    inAlbum: String?,
    onClick: (() -> Unit)?,
    onOpenCollection: (String) -> Unit
) {
    val palette = beyGramPalette()
    var menu by remember(track.trackId) { mutableStateOf(false) }
    ProviderTrackRow(
        track = track,
        number = number,
        onClick = onClick,
        trailing = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.music_track_menu), tint = palette.textSecondary)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    ProviderTrackMenuItems(
                        track = track,
                        onDismiss = { menu = false },
                        showPlayNext = true,
                        // Inside its own album the entry would reopen the same screen.
                        onOpenAlbum = if (track.albumId != null && track.albumId != inAlbum) {
                            { albumId -> onOpenCollection(YandexCollectionRef.album(albumId)) }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    )
}

/** Starts [list] at [index], or shuffled when [shuffle] is set. */
private fun startList(context: android.content.Context, list: List<ProviderTrack>, index: Int, shuffle: Boolean) {
    val playable = list.filter { it.isAvailable }
    if (playable.isEmpty()) return
    if (shuffle) {
        ProviderArtwork.playTracks(context, playable.shuffled(), 0)
    } else {
        ProviderArtwork.playTracks(context, list, index)
    }
}

/** Liked tracks put into one queue, as on the tab. */
private const val LIKED_PLAY_LIMIT = 200
