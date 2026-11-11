package com.beykus.beygram.ui.screens.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Whatshot
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.music.provider.ProviderArtwork
import com.beykus.beygram.data.music.MUSIC_FAVORITES_PLAYLIST_ID
import com.beykus.beygram.data.music.MusicLibraries
import com.beykus.beygram.data.music.MusicOutcome
import com.beykus.beygram.data.music.PlaylistTrack
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.components.SearchFieldButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.topAppBarSurface
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassCard
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.rememberMotionSpec
import com.beykus.beygram.ui.screens.playback.TrackArtwork
import kotlinx.coroutines.launch

/**
 * The music screen: one home for music, opened by the note in Chats, Settings and the player. A search field on top
 * opens the music search. Builds with an optional music source get a Telegram / source switch under it; Stable has
 * only the Telegram content.
 *
 * Telegram content: favorites, recently played, downloaded tracks, playlists and smart collections. A track plays the
 * section it is listed in, from its own position. The section titles open the full list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MyMusicScreen(
    accountId: String,
    telegram: TelegramRepository,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onProviderSignIn: () -> Unit = {}
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { MusicLibraries.get(context) }
    // Set while the library file cannot be read: changes made now are not saved.
    val storageError by repository.storageError.collectAsStateWithLifecycle()
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

    // Optional music source (DEV build only). It gets its own tab; without it, or when this build cannot reach it,
    // the screen shows Telegram only.
    val providerHost = remember(context) { ProviderMusicHosts.yandex()?.takeIf { it.isAvailable(context) } }
    var source by remember(context) { mutableStateOf(MusicHomeSourceStore.read(context)) }
    val shownSource = if (providerHost == null) MusicHomeSource.Telegram else source
    // The source keeps its library fresh only while its tab is shown.
    val providerAccount = if (shownSource == MusicHomeSource.Provider) providerHost?.rememberLibraryAccount() else null

    Scaffold(
        containerColor = palette.screen,
        topBar = {
            TopAppBar(
                modifier = Modifier.topAppBarSurface(),
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface)
            )
        }
    ) { padding ->
        // The source tab reloads its library with a pull; the Telegram library is local and needs none.
        val providerTab = shownSource == MusicHomeSource.Provider && providerHost != null
        val refreshing = providerHost?.isRefreshing() == true
        MusicRefreshBox(
            enabled = providerTab,
            refreshing = refreshing,
            onRefresh = { providerHost?.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gridColumns = coverColumns(maxWidth)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "search") {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SearchFieldButton(placeholder = stringResource(R.string.music_search_action), onClick = onSearch)
                    }
                }
                if (providerHost != null) item(key = "source-switch") {
                    MusicSourceSwitch(
                        selected = shownSource,
                        providerLabel = providerHost.tabLabel(),
                        onSelect = { next ->
                            source = next
                            MusicHomeSourceStore.write(context, next)
                        }
                    )
                }
                if (shownSource == MusicHomeSource.Provider && providerHost != null) {
                    with(providerHost) {
                        libraryItems(
                            account = providerAccount,
                            columns = gridColumns,
                            onPlay = { tracks, index -> ProviderArtwork.playTracks(context, tracks, index) },
                            onSignIn = onProviderSignIn,
                            onOpenCollection = null
                        )
                    }
                } else if (library == null) {
                    // The library of this account is still being read: gray stand-ins keep the shape of the screen.
                    item(key = "library-loading") { MusicLoadingSkeleton() }
                } else {
                    if (storageError) item(key = "storage-error") { StorageErrorBanner() }
                    item(key = "quick-tiles") {
                        val tiles = buildList {
                            add(MusicTile(Icons.Rounded.Star, stringResource(R.string.music_favorites_title), favorites.size, MUSIC_FAVORITES_PLAYLIST_ID))
                            add(MusicTile(Icons.Rounded.History, stringResource(R.string.music_section_recent), recent.size, MusicCollectionKind.Recent.id))
                            add(MusicTile(Icons.Rounded.DownloadDone, stringResource(R.string.music_section_downloaded), downloaded?.size, MusicCollectionKind.Downloaded.id))
                            collections.forEach { (kind, count) ->
                                add(MusicTile(collectionIcon(kind), stringResource(musicCollectionTitleRes(kind)), count, kind.id))
                            }
                        }
                        Column(Modifier.padding(top = 8.dp)) {
                            MusicTileGrid(count = tiles.size) { index ->
                                val tile = tiles[index]
                                MusicQuickTile(
                                    icon = tile.icon,
                                    title = tile.title,
                                    count = tile.count?.let { pluralStringResource(R.plurals.music_track_count, it, it) },
                                    onClick = { onOpenPlaylist(tile.routeId) }
                                )
                            }
                        }
                    }

                    item(key = "playlists-heading") {
                        Spacer(Modifier.height(8.dp))
                        MusicGroupHeading(title = stringResource(R.string.music_section_playlists))
                    }
                    // The first card creates a playlist, so an empty library still shows where playlists go. Each row is its
                    // own lazy item, so a library of hundreds of playlists keeps only the covers on screen.
                    musicGridRows(key = "playlists", count = playlists.size + 1, columns = gridColumns) { index ->
                        if (index == 0) {
                            MusicCoverCard(
                                title = stringResource(R.string.music_playlist_create),
                                subtitle = null,
                                onClick = { creating = true }
                            ) { modifier ->
                                Box(
                                    modifier.clip(styledShape(14.dp)).background(palette.accentSoft),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Add, null, tint = palette.accentInk, modifier = Modifier.size(32.dp))
                                }
                            }
                        } else {
                            val playlist = playlists[index - 1]
                            MusicCoverCard(
                                title = playlist.title,
                                subtitle = pluralStringResource(R.plurals.music_track_count, playlist.tracks.size, playlist.tracks.size),
                                onClick = { onOpenPlaylist(playlist.id) }
                            ) { modifier ->
                                TrackArtwork(
                                    artworkPath = playlist.coverPath,
                                    audioPath = null,
                                    modifier = modifier,
                                    background = palette.accentSoft,
                                    tint = palette.accentInk,
                                    symbolSize = 32.dp,
                                    cornerRadius = 14.dp
                                )
                            }
                        }
                    }

                    item(key = "recent-heading") {
                        Spacer(Modifier.height(8.dp))
                        MusicGroupHeading(
                            title = stringResource(R.string.music_section_recent),
                            actionLabel = if (recent.isEmpty()) null else stringResource(R.string.music_show_all),
                            onAction = { onOpenPlaylist(MusicCollectionKind.Recent.id) }
                        )
                    }
                    item(key = "recent-card") {
                        MusicCardColumn {
                            if (recent.isEmpty()) {
                                MusicEmptyText(stringResource(R.string.music_empty_recent))
                            } else {
                                recent.take(MUSIC_PREVIEW_ROWS).forEachIndexed { index, track ->
                                    MusicTrackRow(
                                        track = track,
                                        unavailable = false,
                                        onClick = { playSection(MusicCollectionKind.Recent.id, recent, index) },
                                        coverPath = rememberTelegramTrackCover(telegram, accountId, track.origin)
                                    )
                                }
                            }
                        }
                    }
                    item(key = "local-only-note") {
                        Text(
                            stringResource(R.string.music_local_only_note),
                            color = palette.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)
                        )
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

/** One quick-access tile of the Telegram tab and the list it opens. [count] is null while it is looked up. */
private data class MusicTile(val icon: ImageVector, val title: String, val count: Int?, val routeId: String)

private fun collectionIcon(kind: MusicCollectionKind): ImageVector = when (kind) {
    MusicCollectionKind.Recent -> Icons.Rounded.History
    MusicCollectionKind.Downloaded -> Icons.Rounded.DownloadDone
    MusicCollectionKind.Frequent -> Icons.Rounded.Whatshot
    MusicCollectionKind.Added -> Icons.Rounded.NewReleases
    MusicCollectionKind.Lately -> Icons.Rounded.Restore
}

/** Pull-to-refresh around [content] when [enabled]; a plain box otherwise, so the local tab has no gesture. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicRefreshBox(
    enabled: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    if (enabled) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier) { content() }
    } else {
        Box(modifier) { content() }
    }
}

/** The warning above the list when the library could not be read: changes made then are not saved. */
@Composable
private fun StorageErrorBanner() {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(styledShape(14.dp))
            .background(palette.warningSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.WarningAmber, null, tint = palette.warningText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.music_storage_error),
            color = palette.warningText,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
    }
}


/**
 * The Telegram / source switch under the search field. Two equal segments; the chosen one is filled with the accent
 * wash and read out as selected.
 */
@Composable
internal fun MusicSourceSwitch(selected: MusicHomeSource, providerLabel: String, onSelect: (MusicHomeSource) -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            // A glass pill in Liquid Glass, the sunken surface in the other styles.
            .glassCard(LocalDesignSystem.current, palette.surface2, palette.textSecondary, styledShape(14.dp))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MusicSourceSegment(
            label = stringResource(R.string.music_source_telegram),
            selected = selected == MusicHomeSource.Telegram,
            onClick = { onSelect(MusicHomeSource.Telegram) }
        )
        MusicSourceSegment(
            label = providerLabel,
            selected = selected == MusicHomeSource.Provider,
            onClick = { onSelect(MusicHomeSource.Provider) }
        )
    }
}

@Composable
private fun RowScope.MusicSourceSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    val motion = rememberMotionSpec(MotionToken.TabSelect)
    val fill by animateColorAsState(
        if (selected) palette.accentSoft else palette.accentSoft.copy(alpha = 0f),
        animationSpec = motion.animationSpec(),
        label = "music-source-fill"
    )
    Box(
        Modifier.weight(1f)
            .heightIn(min = 40.dp)
            .clip(styledShape(11.dp))
            .background(fill)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) palette.accentInk else palette.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
