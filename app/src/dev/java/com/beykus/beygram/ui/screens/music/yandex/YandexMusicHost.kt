package com.beykus.beygram.ui.screens.music.yandex

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.music.provider.MusicProvider
import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.MusicProviderState
import com.beykus.beygram.data.music.provider.MusicProviders
import com.beykus.beygram.data.music.provider.ProviderPlaylist
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTrack
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.music.ProviderMusicHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Yandex Music results inside the music search. DEV only; loaded by name from [com.beykus.beygram.ui.screens.music.ProviderMusicHosts]. */
class YandexMusicHost : ProviderMusicHost {
    /** Searches run here, not in the composition, so a lazy item leaving the screen does not cancel or drop them. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var searchJob: Job? = null
    private var searchText by mutableStateOf("")
    private var searchTracks by mutableStateOf<List<ProviderTrack>?>(null)
    private var searchFailure by mutableStateOf<MusicProviderFailure?>(null)
    private var searchLoading by mutableStateOf(false)

    /** Starts a search for [text] unless the same one is already running or finished. */
    private fun ensureSearch(provider: MusicProvider, text: String) {
        if (text == searchText && (searchLoading || searchTracks != null || searchFailure != null)) return
        searchJob?.cancel()
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

    private var libraryLoaded = false
    private var libraryLiked by mutableStateOf<List<ProviderTrack>>(emptyList())
    private var libraryPlaylists by mutableStateOf<List<ProviderPlaylist>>(emptyList())
    private var libraryLoading by mutableStateOf(false)
    private var libraryFailure by mutableStateOf<MusicProviderFailure?>(null)

    /** Loads liked tracks and playlists once per sign-in. */
    private fun ensureLibrary(provider: MusicProvider) {
        if (libraryLoaded || libraryLoading) return
        libraryLoading = true
        scope.launch {
            val liked = withContext(Dispatchers.IO) { provider.likedTracks() }
            val lists = withContext(Dispatchers.IO) { provider.playlists() }
            libraryFailure = (liked as? ProviderResult.Failure)?.failure ?: (lists as? ProviderResult.Failure)?.failure
            libraryLiked = (liked as? ProviderResult.Success)?.value.orEmpty()
            libraryPlaylists = (lists as? ProviderResult.Success)?.value.orEmpty()
            libraryLoaded = libraryFailure == null
            libraryLoading = false
        }
    }

    private fun clearLibrary() {
        libraryLoaded = false
        libraryLiked = emptyList()
        libraryPlaylists = emptyList()
        libraryFailure = null
        libraryLoading = false
    }

    /** Plays the whole playlist from its first track. */
    private fun playPlaylist(provider: MusicProvider, kind: String, onPlay: (List<ProviderTrack>, Int) -> Unit) {
        scope.launch {
            val tracks = withContext(Dispatchers.IO) { provider.playlistTracks(kind) }
            when (tracks) {
                is ProviderResult.Success -> if (tracks.value.isNotEmpty()) onPlay(tracks.value, 0)
                is ProviderResult.Failure -> libraryFailure = tracks.failure
            }
        }
    }

    @Composable
    override fun LibrarySection(onPlay: ((List<ProviderTrack>, Int) -> Unit)?) {
        val context = LocalContext.current
        // Every hook runs on every composition; the early exits come after them, so the slots never change shape.
        val provider = remember(context) { MusicProviders.yandex(context) }
        val state by (provider?.state ?: SignedOutState).collectAsStateWithLifecycle()
        val signedIn = state is MusicProviderState.SignedIn
        val palette = beyGramPalette()

        LaunchedEffect(signedIn) {
            if (provider == null || !signedIn) clearLibrary() else ensureLibrary(provider)
        }

        if (provider == null || !signedIn) return
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(
                stringResource(R.string.yandex_library_title),
                color = palette.textPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            if (libraryLoading) Column(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) { CircularProgressIndicator() }
            libraryFailure?.let {
                Text(
                    stringResource(failureText(it)),
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (!libraryLoading && libraryLoaded && libraryLiked.isEmpty() && libraryPlaylists.isEmpty()) Text(
                stringResource(R.string.yandex_library_empty),
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (libraryLiked.isNotEmpty()) {
                Text(
                    stringResource(R.string.yandex_library_liked),
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                libraryLiked.take(LIBRARY_PREVIEW_ROWS).forEachIndexed { index, track ->
                    val playable = track.isAvailable && onPlay != null
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .then(if (playable) Modifier.clickable { onPlay?.invoke(libraryLiked, index) } else Modifier)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            track.title,
                            color = if (track.isAvailable) palette.textPrimary else palette.textSecondary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            track.performer,
                            color = palette.textSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (libraryPlaylists.isNotEmpty()) {
                Text(
                    stringResource(R.string.yandex_library_playlists),
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                libraryPlaylists.forEach { playlist ->
                    Text(
                        playlist.title,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (onPlay != null) Modifier.clickable { playPlaylist(provider, playlist.kind, onPlay) }
                                else Modifier
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }

    @Composable
    override fun SearchResults(query: String, onPlay: ((List<ProviderTrack>, Int) -> Unit)?) {
        val context = LocalContext.current
        // Every hook runs on every composition; the early exits come after them, so the slots never change shape.
        val provider = remember(context) { MusicProviders.yandex(context) }
        val state by (provider?.state ?: SignedOutState).collectAsStateWithLifecycle()
        val signedIn = state is MusicProviderState.SignedIn
        val palette = beyGramPalette()
        val text = query.trim()

        LaunchedEffect(text, signedIn) {
            if (provider == null || !signedIn) clearSearch() else ensureSearch(provider, text)
        }

        if (provider == null || !signedIn || text.isEmpty() || searchText != text) return
        val found = searchTracks
        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.yandex_search_title),
                color = palette.textPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            if (searchLoading) Column(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) { CircularProgressIndicator() }
            searchFailure?.let {
                Text(
                    stringResource(failureText(it)),
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (found != null && found.isEmpty()) Text(
                stringResource(R.string.yandex_search_empty),
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            found?.forEachIndexed { index, track ->
                val playable = track.isAvailable && onPlay != null
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(if (playable) Modifier.clickable { onPlay?.invoke(found, index) } else Modifier)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        track.title,
                        color = if (track.isAvailable) palette.textPrimary else palette.textSecondary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track.performer,
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
        const val LIBRARY_PREVIEW_ROWS = 20
        /** Stands in for the provider state when the provider is absent, so the collection stays unconditional. */
        val SignedOutState = MutableStateFlow<MusicProviderState>(MusicProviderState.SignedOut)
    }
}
