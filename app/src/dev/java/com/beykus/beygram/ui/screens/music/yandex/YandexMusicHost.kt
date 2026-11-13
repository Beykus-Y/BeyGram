package com.beykus.beygram.ui.screens.music.yandex

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import com.beykus.beygram.playback.PlaybackEngine
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import com.beykus.beygram.ui.screens.music.WaveBackdrop
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.music.provider.MusicProvider
import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.MusicProviderState
import com.beykus.beygram.data.music.provider.MusicProviderAccount
import com.beykus.beygram.data.music.provider.MusicProviders
import com.beykus.beygram.data.music.provider.ProviderAlbum
import com.beykus.beygram.data.music.provider.ProviderPlaylist
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTrack
import com.beykus.beygram.data.music.provider.ProviderTracks
import com.beykus.beygram.data.yandex.YandexMusicProvider
import com.beykus.beygram.data.yandex.YandexWave
import com.beykus.beygram.data.yandex.YandexWaveSettings
import com.beykus.beygram.data.yandex.YandexWaveSettingsStore
import com.beykus.beygram.data.yandex.YandexWaveLauncher
import com.beykus.beygram.data.yandex.YandexWaveFeedback
import com.beykus.beygram.data.yandex.YandexWaveResume
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.styledShape
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import com.beykus.beygram.ui.screens.music.LocalOpenProviderAlbum
import com.beykus.beygram.ui.screens.music.MusicSearchMessage
import com.beykus.beygram.ui.screens.music.MusicSearchSection
import com.beykus.beygram.ui.screens.music.MusicSearchSkeletonRows
import com.beykus.beygram.ui.screens.music.ProviderTrackMenuItems
import com.beykus.beygram.ui.screens.music.MusicCardColumn
import com.beykus.beygram.ui.screens.music.MusicCoverCard
import com.beykus.beygram.ui.screens.music.MusicEmptyText
import com.beykus.beygram.ui.screens.music.MusicGroupHeading
import com.beykus.beygram.ui.screens.music.MusicLoadingSkeleton
import com.beykus.beygram.ui.screens.music.MusicScreenGutter
import com.beykus.beygram.ui.screens.music.ProviderMusicHost
import com.beykus.beygram.ui.screens.music.ProviderTrackRow
import com.beykus.beygram.ui.screens.music.musicCard
import com.beykus.beygram.ui.screens.music.musicGridRows
import com.beykus.beygram.ui.screens.music.rememberProviderCover
import com.beykus.beygram.ui.screens.playback.TrackArtwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val WaveCardHeight = 168.dp

/**
 * Yandex Music in the music screens: its tab (likes, playlists, albums) and its search results. DEV only; loaded by
 * name from [com.beykus.beygram.ui.screens.music.ProviderMusicHosts].
 */
class YandexMusicHost : ProviderMusicHost {
    /** Requests run here, not in the composition, so a lazy item leaving the screen does not cancel or drop them. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var searchJob: Job? = null
    private var searchText by mutableStateOf("")
    private var searchTracks by mutableStateOf<List<ProviderTrack>?>(null)
    private var searchFailure by mutableStateOf<MusicProviderFailure?>(null)
    private var searchLoading by mutableStateOf(false)

    /** Starts a search for [text] unless the same one is already running or finished. */
    private var searchUid: String? = null

    private fun ensureSearch(provider: MusicProvider, text: String, uid: String) {
        if (uid == searchUid && text == searchText && (searchLoading || searchTracks != null || searchFailure != null)) return
        searchJob?.cancel()
        searchUid = uid
        searchText = text
        searchTracks = null
        searchFailure = null
        searchLoading = text.isNotEmpty()
        if (text.isEmpty()) return
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            when (val result = withContext(Dispatchers.IO) { provider.search(text) }) {
                is ProviderResult.Success -> searchTracks = result.value
                is ProviderResult.Failure -> searchFailure = result.failure
            }
            searchLoading = false
        }
    }

    private fun clearSearch() {
        searchJob?.cancel()
        searchText = ""
        searchTracks = null
        searchFailure = null
        searchLoading = false
    }

    // Library ------------------------------------------------------------------------------------------------------

    private var appContext: Context? = null
    private var libraryProvider: MusicProvider? = null
    private var libraryJob: Job? = null
    private var libraryUid: String? = null
    private var library by mutableStateOf<YandexLibrarySnapshot?>(null)
    /** True while the service is asked; the snapshot, if any, stays on screen meanwhile. */
    private var libraryLoading by mutableStateOf(false)
    private var libraryFailure by mutableStateOf<MusicProviderFailure?>(null)

    /**
     * Counts playback starts from this source. A collection still loading when another row starts playing must not
     * start later and replace that choice.
     */
    private var selectionGeneration = 0L

    /** Shows the stored snapshot of [uid] at once and asks the service when it is old or [force] is set. */
    private fun ensureLibrary(context: Context, provider: MusicProvider, uid: String, force: Boolean) {
        val sameAccount = libraryUid == uid
        if (sameAccount && libraryLoading) return
        if (!sameAccount) {
            libraryJob?.cancel()
            libraryUid = uid
            library = null
            libraryFailure = null
        }
        val known = library
        if (sameAccount && !force && known != null && !isStale(known)) return
        libraryLoading = true
        libraryJob = scope.launch {
            val stored = if (known == null) withContext(Dispatchers.IO) { YandexLibrarySnapshots.read(context, uid) } else known
            if (stored != null && libraryUid == uid) {
                library = stored
                ProviderTracks.rememberLikedIds(stored.likedIds)
            }
            if (stored != null && !force && !isStale(stored)) {
                libraryLoading = false
                return@launch
            }
            val fresh = withContext(Dispatchers.IO) { loadLibrary(provider, uid) }
            if (libraryUid != uid) return@launch
            when (fresh) {
                is ProviderResult.Success -> {
                    library = fresh.value
                    ProviderTracks.rememberLikedIds(fresh.value.likedIds)
                    libraryFailure = null
                    // Not after a sign-out during the request: the snapshot of a signed-out account is not kept.
                    val stillSignedIn = (provider.state.value as? MusicProviderState.SignedIn)?.account?.uid == uid
                    if (stillSignedIn) withContext(Dispatchers.IO) { YandexLibrarySnapshots.write(context, fresh.value) }
                }
                is ProviderResult.Failure -> libraryFailure = fresh.failure
            }
            libraryLoading = false
        }
    }

    /** One pass over the service: ids of the likes, the first liked tracks, playlists and albums. */
    private suspend fun loadLibrary(provider: MusicProvider, uid: String): ProviderResult<YandexLibrarySnapshot> {
        val ids = when (val result = provider.likedTrackIds()) {
            is ProviderResult.Failure -> return result
            is ProviderResult.Success -> result.value
        }
        val preview = when (val result = provider.tracks(ids.take(LIBRARY_PREVIEW_ROWS))) {
            is ProviderResult.Failure -> return result
            is ProviderResult.Success -> result.value.map { it.copy(liked = true) }
        }
        val own = when (val result = provider.playlists()) {
            is ProviderResult.Failure -> return result
            is ProviderResult.Success -> result.value
        }
        // Liked playlists and albums are extra: a refusal of either keeps the rest of the tab.
        val followed = (provider.likedPlaylists() as? ProviderResult.Success)?.value.orEmpty()
        val albums = (provider.likedAlbums() as? ProviderResult.Success)?.value.orEmpty()
        val playlists = (own + followed).distinctBy { "${it.ownerUid}:${it.kind}" }
        return ProviderResult.Success(
            YandexLibrarySnapshot(uid, System.currentTimeMillis(), ids, preview, playlists, albums)
        )
    }

    private fun isStale(snapshot: YandexLibrarySnapshot): Boolean =
        System.currentTimeMillis() - snapshot.savedAt > LIBRARY_MAX_AGE_MS

    private fun clearLibrary(context: Context?) {
        libraryJob?.cancel()
        libraryUid = null
        library = null
        libraryFailure = null
        libraryLoading = false
        if (context != null) scope.launch(Dispatchers.IO) { YandexLibrarySnapshots.clear(context) }
    }

    /** Plays tracks that must be loaded first, unless another selection was made while they loaded. */
    private fun playLoaded(
        load: suspend () -> ProviderResult<List<ProviderTrack>>,
        onPlay: (List<ProviderTrack>, Int) -> Unit,
        startTrackId: String? = null
    ) {
        val mine = ++selectionGeneration
        scope.launch {
            when (val tracks = withContext(Dispatchers.IO) { load() }) {
                is ProviderResult.Success -> if (tracks.value.isNotEmpty() && mine == selectionGeneration) {
                    val start = tracks.value.indexOfFirst { it.trackId == startTrackId }.coerceAtLeast(0)
                    onPlay(tracks.value, start)
                }
                is ProviderResult.Failure -> libraryFailure = tracks.failure
            }
        }
    }

    private var waveStarting by mutableStateOf(false)
    /** The «Моя волна» settings of the signed-in account; read on sign-in, saved on «Слушать». */
    private var waveSettings by mutableStateOf(YandexWaveSettings.DEFAULTS)
    private var waveSettingsOpen by mutableStateOf(false)
    private var waveFailure by mutableStateOf<MusicProviderFailure?>(null)

    /**
     * Starts «Моя волна»: opens a radio session and plays its first batch; the player asks the session for more tracks
     * as the queue runs low, so it never ends. A selection made while the session opens wins over the wave.
     */
    private fun startWave(context: Context, provider: MusicProvider) {
        val yandex = provider as? YandexMusicProvider ?: return
        if (waveStarting) return
        val mine = ++selectionGeneration
        waveStarting = true
        waveFailure = null
        YandexWaveLauncher.start(
            context = context,
            provider = yandex,
            seeds = waveSettings.seeds(YandexWave.MY_WAVE_SEED),
            subtitle = context.getString(R.string.yandex_wave_source_personal),
            startTrack = null,
            isCurrent = { mine == selectionGeneration },
            onDone = { failure ->
                waveStarting = false
                waveFailure = failure
            },
        )
    }

    /** A direct selection: it supersedes any collection still loading. */
    private fun startPlayback(tracks: List<ProviderTrack>, index: Int, onPlay: (List<ProviderTrack>, Int) -> Unit) {
        selectionGeneration++
        onPlay(tracks, index)
    }

    /** Plays the whole liked list from [startTrackId], or from the top. */
    private fun playLiked(
        provider: MusicProvider,
        ids: List<String>,
        onPlay: (List<ProviderTrack>, Int) -> Unit,
        startTrackId: String? = null
    ) = playLoaded({ provider.tracks(ids) }, onPlay, startTrackId)

    private fun playPlaylist(provider: MusicProvider, playlist: ProviderPlaylist, onPlay: (List<ProviderTrack>, Int) -> Unit) =
        playLoaded({ provider.playlistTracks(playlist.kind, playlist.ownerUid.takeIf { !playlist.isOwn }) }, onPlay)

    private fun playAlbum(provider: MusicProvider, album: ProviderAlbum, onPlay: (List<ProviderTrack>, Int) -> Unit) =
        playLoaded({
            when (val result = provider.album(album.id)) {
                is ProviderResult.Success -> ProviderResult.Success(result.value.tracks)
                is ProviderResult.Failure -> result
            }
        }, onPlay)

    override fun isAvailable(context: Context): Boolean = MusicProviders.yandex(context) != null

    @Composable
    override fun tabLabel(): String = stringResource(R.string.yandex_tab_label)

    @Composable
    override fun isRefreshing(): Boolean = libraryLoading && library != null

    override fun refresh() {
        val context = appContext ?: return
        val provider = libraryProvider ?: return
        val uid = libraryUid ?: return
        ensureLibrary(context, provider, uid, force = true)
    }

    override fun albumRef(albumId: String): String = YandexCollectionRef.album(albumId)

    /**
     * Playlists opened from a link, by their screen reference. The screen's header and menu use them when the library
     * tab has not loaded the playlist yet. A few links are opened per session, so the map is cleared past 64 entries.
     */
    private val linkedPlaylists = java.util.concurrent.ConcurrentHashMap<String, ProviderPlaylist>()

    override fun playlistRef(playlist: ProviderPlaylist): String {
        val ref = YandexCollectionRef.playlist(playlist)
        if (linkedPlaylists.size >= 64) linkedPlaylists.clear()
        linkedPlaylists[ref] = playlist
        return ref
    }

    @Composable
    override fun CollectionScreen(ref: String, onBack: () -> Unit, onOpenCollection: (String) -> Unit) {
        val context = LocalContext.current
        val provider = remember(context) { MusicProviders.yandex(context) }
        val parsed = remember(ref) { YandexCollectionRef.parse(ref) }
        val linkedPlaylist = remember(ref) { linkedPlaylists[ref] }
        if (parsed == null) {
            LaunchedEffect(ref) { onBack() }
            return
        }
        YandexCollectionScreen(
            ref = parsed,
            provider = provider,
            snapshot = library,
            onBack = onBack,
            onOpenCollection = onOpenCollection,
            linkedPlaylist = linkedPlaylist
        )
    }

    @Composable
    override fun rememberLibraryAccount(): MusicProviderAccount? {
        val context = LocalContext.current
        // Set here, in the composition, so the lazy items built right after already see the provider.
        val provider = remember(context) {
            MusicProviders.yandex(context).also {
                appContext = context.applicationContext
                libraryProvider = it
            }
        }
        val state by (provider?.state ?: SignedOutState).collectAsStateWithLifecycle()
        val account = (state as? MusicProviderState.SignedIn)?.account
        val uid = account?.uid
        LaunchedEffect(uid) {
            when {
                provider == null -> clearLibrary(null)
                uid == null -> clearLibrary(context.applicationContext)
                else -> ensureLibrary(context.applicationContext, provider, uid, force = false)
            }
        }
        return account.takeIf { provider != null }
    }

    override fun LazyListScope.libraryItems(
        account: MusicProviderAccount?,
        columns: Int,
        onPlay: ((List<ProviderTrack>, Int) -> Unit)?,
        onSignIn: () -> Unit,
        onOpenCollection: ((String) -> Unit)?
    ) {
        val provider = libraryProvider
        if (provider == null || account == null) {
            item(key = "yandex-signin") { SignInCard(onSignIn) }
            return
        }
        item(key = "yandex-top") { Spacer(Modifier.height(8.dp)) }
        if (!account.hasSubscription) item(key = "yandex-no-subscription") {
            NoticeCard(stringResource(R.string.yandex_library_no_subscription))
        }
        if (account.hasSubscription) item(key = "yandex-wave") {
            val context = LocalContext.current
            val offered by YandexWaveResume.offered.collectAsStateWithLifecycle()
            val playback by PlaybackEngine.state.collectAsStateWithLifecycle()
            val waveTitle = stringResource(R.string.yandex_wave_title)
            // The radio is not a flow, so it is read on each playback change; the state flow drives recomposition.
            val waveActive = PlaybackEngine.activeRadio?.title == waveTitle && playback.item != null
            Column(Modifier.fillMaxWidth()) {
            val uid = (provider.state.value as? MusicProviderState.SignedIn)?.account?.uid.orEmpty()
            LaunchedEffect(uid) { waveSettings = YandexWaveSettingsStore.load(context, uid) }
            WaveTile(
                starting = waveStarting,
                summary = waveSummary(context, waveSettings),
                failure = waveFailure,
                resume = offered,
                active = waveActive,
                playing = waveActive && (playback.playing || playback.intentPlaying),
                onStart = if (onPlay != null) ({ startWave(context, provider) }) else null,
                onResume = { YandexWaveResume.resume() },
                onTogglePlayPause = { PlaybackEngine.togglePlayPause() },
            )
            TextButton(onClick = { waveSettingsOpen = true }, modifier = Modifier.padding(horizontal = MusicScreenGutter)) {
                Text(stringResource(R.string.yandex_wave_settings_open))
            }
            if (waveSettingsOpen) YandexWaveSettingsSheet(
                initial = waveSettings,
                onListen = { chosen ->
                    waveSettingsOpen = false
                    if (waveActive) {
                        // The playing track keeps playing; the upcoming ones change. On failure nothing is saved.
                        YandexWaveLauncher.changeSeeds(
                            context = context,
                            seeds = chosen.seeds(YandexWave.MY_WAVE_SEED),
                            subtitle = waveSummary(context, chosen),
                        ) { changed ->
                            if (changed) {
                                waveSettings = chosen
                                YandexWaveSettingsStore.save(context, uid, chosen)
                            }
                        }
                    } else {
                        waveSettings = chosen
                        YandexWaveSettingsStore.save(context, uid, chosen)
                        if (onPlay != null) startWave(context, provider)
                    }
                },
                onDismiss = { waveSettingsOpen = false },
            )
            }
        }
        val failure = libraryFailure
        if (failure != null && !libraryLoading) item(key = "yandex-failure") {
            FailureCard(
                text = stringResource(failureText(failure)),
                onRetry = { appContext?.let { ensureLibrary(it, provider, account.uid, force = true) } }
            )
        }
        val snapshot = library?.takeIf { it.uid == account.uid }
        when {
            snapshot == null && libraryLoading -> item(key = "yandex-loading") { MusicLoadingSkeleton() }
            snapshot == null -> Unit
            snapshot.likedIds.isEmpty() && snapshot.playlists.isEmpty() && snapshot.albums.isEmpty() ->
                item(key = "yandex-empty") { MusicEmptyText(stringResource(R.string.yandex_library_empty)) }
            else -> libraryContent(snapshot, provider, columns, onPlay, onOpenCollection)
        }
        item(key = "yandex-bottom") { Spacer(Modifier.height(16.dp)) }
    }

    /** Likes, playlists, albums and the newest likes. Each row of a cover grid is its own lazy item. */
    private fun LazyListScope.libraryContent(
        snapshot: YandexLibrarySnapshot,
        provider: MusicProvider,
        columns: Int,
        onPlay: ((List<ProviderTrack>, Int) -> Unit)?,
        onOpenCollection: ((String) -> Unit)?
    ) {
        val likedCount = snapshot.likedIds.size
        if (likedCount > 0) item(key = "yandex-liked-tile") {
            LikedTile(
                count = likedCount,
                onOpen = {
                    if (onOpenCollection != null) onOpenCollection(YandexCollectionRef.LIKED)
                    else if (onPlay != null) playLiked(provider, snapshot.likedIds, onPlay)
                },
                onPlay = onPlay?.let { play -> { playLiked(provider, snapshot.likedIds, play) } }
            )
        }

        if (snapshot.playlists.isNotEmpty()) {
            item(key = "yandex-playlists-heading") {
                Spacer(Modifier.height(8.dp))
                MusicGroupHeading(title = stringResource(R.string.yandex_library_playlists))
            }
            musicGridRows(key = "yandex-playlists", count = snapshot.playlists.size, columns = columns) { index ->
                val playlist = snapshot.playlists[index]
                MusicCoverCard(
                    title = playlist.title,
                    subtitle = if (playlist.isOwn || playlist.ownerName == null) {
                        pluralStringResource(R.plurals.music_track_count, playlist.trackCount, playlist.trackCount)
                    } else {
                        playlist.ownerName
                    },
                    onClick = {
                        if (onOpenCollection != null) onOpenCollection(YandexCollectionRef.playlist(playlist))
                        else if (onPlay != null) playPlaylist(provider, playlist, onPlay)
                    }
                ) { modifier -> CoverSquare(playlist.coverUrl, modifier) }
            }
        }

        if (snapshot.albums.isNotEmpty()) {
            item(key = "yandex-albums-heading") {
                Spacer(Modifier.height(8.dp))
                MusicGroupHeading(title = stringResource(R.string.yandex_library_albums))
            }
            musicGridRows(key = "yandex-albums", count = snapshot.albums.size, columns = columns) { index ->
                val album = snapshot.albums[index]
                MusicCoverCard(
                    title = album.title,
                    subtitle = listOfNotNull(album.performer.takeIf { it.isNotBlank() }, album.year?.toString())
                        .joinToString(" · ")
                        .ifBlank { null },
                    onClick = {
                        if (onOpenCollection != null) onOpenCollection(YandexCollectionRef.album(album.id))
                        else if (onPlay != null) playAlbum(provider, album, onPlay)
                    }
                ) { modifier -> CoverSquare(album.coverUrl, modifier) }
            }
        }

        if (snapshot.likedPreview.isNotEmpty()) {
            item(key = "yandex-liked-heading") {
                Spacer(Modifier.height(8.dp))
                MusicGroupHeading(
                    title = stringResource(R.string.yandex_library_liked),
                    actionLabel = if (onOpenCollection != null) stringResource(R.string.music_show_all) else null,
                    onAction = { onOpenCollection?.invoke(YandexCollectionRef.LIKED) }
                )
            }
            item(key = "yandex-liked-preview") {
                MusicCardColumn {
                    snapshot.likedPreview.forEachIndexed { index, track ->
                        ProviderTrackRow(
                            track = track,
                            onClick = if (onPlay != null && track.isAvailable) {
                                {
                                    // The preview is the head of the liked list: the queue goes on with the rest of it.
                                    if (snapshot.likedIds.size > snapshot.likedPreview.size) {
                                        playLiked(provider, snapshot.likedIds, onPlay, track.trackId)
                                    } else {
                                        startPlayback(snapshot.likedPreview, index, onPlay)
                                    }
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
    }

    /** Diversity chips under the wave card. Not verified on device yet: the seed values come from the API docs. */
    @Composable
    private fun WaveDiversityRow(selected: YandexWave.Diversity, onSelect: (YandexWave.Diversity) -> Unit) {
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = MusicScreenGutter, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val options = listOf(
                YandexWave.Diversity.DEFAULT to R.string.yandex_wave_diversity_default,
                YandexWave.Diversity.DISCOVER to R.string.yandex_wave_diversity_discover,
                YandexWave.Diversity.POPULAR to R.string.yandex_wave_diversity_popular,
            )
            options.forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(stringResource(label)) },
                )
            }
        }
    }

    /** The «Моя волна» card at the top of the tab: one tap starts an endless stream of recommendations. */
    @Composable
    private fun WaveTile(
        starting: Boolean,
        summary: String,
        failure: MusicProviderFailure?,
        resume: YandexWaveResume.Saved?,
        active: Boolean,
        playing: Boolean,
        onStart: (() -> Unit)?,
        onResume: () -> Unit,
        onTogglePlayPause: () -> Unit,
    ) {
        val palette = beyGramPalette()
        val subtitle = when {
            starting -> stringResource(R.string.yandex_wave_starting)
            active && playing -> stringResource(R.string.yandex_wave_now_playing)
            active -> stringResource(R.string.yandex_wave_paused)
            resume != null -> stringResource(R.string.yandex_wave_resume, resume.track.performer, resume.track.title)
            failure != null -> stringResource(waveFailureText(failure))
            else -> summary
        }
        val action: (() -> Unit)? = when {
            active -> onTogglePlayPause
            resume != null -> onResume
            else -> onStart
        }
        Box(
            Modifier.fillMaxWidth()
                .padding(horizontal = MusicScreenGutter)
                .height(WaveCardHeight)
                .musicCard()
                .background(palette.accentSoft.copy(alpha = 0.35f))
                .then(
                    if (action != null && !starting) {
                        Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.yandex_wave_play),
                            onClick = action
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            WaveBackdrop(
                color = palette.accent,
                secondary = palette.accentSoft,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                Modifier.align(Alignment.TopEnd).padding(14.dp).size(56.dp).clip(CircleShape).background(palette.accent),
                contentAlignment = Alignment.Center
            ) {
                if (starting) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = palette.onAccent, strokeWidth = 2.5.dp)
                } else {
                    Icon(
                        if (active && playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                        tint = palette.onAccent,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                Text(
                    stringResource(R.string.yandex_wave_title),
                    color = palette.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }


    private fun waveFailureText(failure: MusicProviderFailure): Int = when (failure) {
        MusicProviderFailure.Network -> R.string.yandex_error_network
        MusicProviderFailure.Unavailable, MusicProviderFailure.RateLimited -> R.string.yandex_error_unavailable
        MusicProviderFailure.SubscriptionRequired -> R.string.yandex_library_no_subscription
        else -> R.string.yandex_wave_failed
    }

    /** The wide «Мне нравится» tile: the count and a play button that starts the whole list. */
    @Composable
    private fun LikedTile(count: Int, onOpen: () -> Unit, onPlay: (() -> Unit)?) {
        val palette = beyGramPalette()
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = MusicScreenGutter)
                .heightIn(min = 76.dp)
                .musicCard()
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(palette.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Favorite, null, tint = palette.accentInk, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.yandex_library_liked),
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    pluralStringResource(R.plurals.music_track_count, count, count),
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
            if (onPlay != null) FilledIconButton(
                onClick = onPlay,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = palette.accent)
            ) {
                Icon(Icons.Rounded.PlayArrow, stringResource(R.string.yandex_library_play), tint = palette.onAccent)
            }
        }
    }

    @Composable
    private fun CoverSquare(url: String?, modifier: Modifier) {
        val palette = beyGramPalette()
        TrackArtwork(
            artworkPath = rememberProviderCover(url),
            audioPath = null,
            modifier = modifier,
            background = palette.accentSoft,
            tint = palette.accentInk,
            symbolSize = 32.dp,
            cornerRadius = 14.dp
        )
    }

    /** A plain note at the top of the tab, such as the missing subscription. */
    @Composable
    private fun NoticeCard(text: String) {
        val palette = beyGramPalette()
        Text(
            text,
            color = palette.textSecondary,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = MusicScreenGutter, vertical = 4.dp)
                .musicCard()
                .padding(14.dp)
        )
    }

    /** What went wrong, with a retry button. The snapshot below, if any, stays on screen. */
    @Composable
    private fun FailureCard(text: String, onRetry: () -> Unit) {
        val palette = beyGramPalette()
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = MusicScreenGutter, vertical = 4.dp)
                .musicCard()
                .padding(14.dp)
        ) {
            Text(text, color = palette.textPrimary, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.yandex_library_retry))
            }
        }
    }

    @Composable
    override fun SearchResults(
        query: String,
        onPlay: ((List<ProviderTrack>, Int) -> Unit)?,
        limit: Int?,
        onMore: (() -> Unit)?
    ) {
        val context = LocalContext.current
        // Every hook runs on every composition; the early exits come after them, so the slots never change shape.
        val provider = remember(context) { MusicProviders.yandex(context) }
        val state by (provider?.state ?: SignedOutState).collectAsStateWithLifecycle()
        val uid = (state as? MusicProviderState.SignedIn)?.account?.uid
        val signedIn = uid != null
        val text = query.trim()
        val openAlbum = LocalOpenProviderAlbum.current

        LaunchedEffect(text, uid) {
            if (provider == null || uid == null) clearSearch() else ensureSearch(provider, text, uid)
        }

        if (provider == null || !signedIn || text.isEmpty() || searchText != text || searchUid != uid) return
        val found = searchTracks
        val shown = if (limit != null) found?.take(limit) else found
        MusicSearchSection(
            title = stringResource(R.string.yandex_search_title),
            onMore = if (limit != null && found != null && found.size > limit) onMore else null,
            carded = limit != null
        ) {
            if (searchLoading) MusicSearchSkeletonRows()
            searchFailure?.let { MusicSearchMessage(stringResource(failureText(it))) }
            if (found != null && found.isEmpty()) MusicSearchMessage(stringResource(R.string.yandex_search_empty))
            shown?.forEachIndexed { index, track ->
                SearchTrackRow(
                    track = track,
                    onClick = if (onPlay != null && track.isAvailable) {
                        { startPlayback(found.orEmpty(), index, onPlay) }
                    } else {
                        null
                    },
                    onOpenAlbum = openAlbum
                )
            }
        }
    }

    /** A search result with the track menu: play next, like, its album and its link. */
    @Composable
    private fun SearchTrackRow(track: ProviderTrack, onClick: (() -> Unit)?, onOpenAlbum: ((String) -> Unit)?) {
        val palette = beyGramPalette()
        var menu by remember(track.trackId) { mutableStateOf(false) }
        ProviderTrackRow(
            track = track,
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
                            onOpenAlbum = onOpenAlbum
                        )
                    }
                }
            }
        )
    }

    /** The whole tab before sign-in: what the tab holds and a button to the sign-in screen. */
    @Composable
    private fun SignInCard(onSignIn: () -> Unit) {
        val palette = beyGramPalette()
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(styledShape(16.dp))
                .background(palette.surface2)
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.yandex_library_signin_title),
                color = palette.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.yandex_library_signin_text),
                color = palette.textSecondary,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onSignIn, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.yandex_library_signin_action))
            }
        }
    }

    private fun failureText(failure: MusicProviderFailure): Int = when (failure) {
        MusicProviderFailure.Network -> R.string.yandex_error_network
        MusicProviderFailure.Unavailable, MusicProviderFailure.RateLimited -> R.string.yandex_error_unavailable
        else -> R.string.yandex_error_generic
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
        const val LIBRARY_PREVIEW_ROWS = 5
        /** How long a stored library is shown without asking the service again. */
        const val LIBRARY_MAX_AGE_MS = 10 * 60_000L
        /** Stands in for the provider state when the provider is absent, so the collection stays unconditional. */
        val SignedOutState = MutableStateFlow<MusicProviderState>(MusicProviderState.SignedOut)
    }
}
