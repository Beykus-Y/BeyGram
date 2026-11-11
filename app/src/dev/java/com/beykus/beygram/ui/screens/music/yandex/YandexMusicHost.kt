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
import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.MusicProviderState
import com.beykus.beygram.data.music.provider.MusicProviders
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTrack
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.music.ProviderMusicHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** Yandex Music results inside the music search. DEV only; loaded by name from [com.beykus.beygram.ui.screens.music.ProviderMusicHosts]. */
class YandexMusicHost : ProviderMusicHost {

    @Composable
    override fun SearchResults(query: String, onPlay: ((List<ProviderTrack>, Int) -> Unit)?) {
        val context = LocalContext.current
        // Every hook runs on every composition; the early exits come after them, so the slots never change shape.
        val provider = remember(context) { MusicProviders.yandex(context) }
        val state by (provider?.state ?: SignedOutState).collectAsStateWithLifecycle()
        val signedIn = state is MusicProviderState.SignedIn
        val palette = beyGramPalette()
        val text = query.trim()
        var tracks by remember(text, signedIn) { mutableStateOf<List<ProviderTrack>?>(null) }
        var failure by remember(text, signedIn) { mutableStateOf<MusicProviderFailure?>(null) }
        var loading by remember(text, signedIn) { mutableStateOf(false) }

        // Restarted on every change of the query or the sign-in, so a superseded request never repopulates the list.
        LaunchedEffect(text, signedIn) {
            tracks = null
            failure = null
            if (provider == null || !signedIn || text.isEmpty()) return@LaunchedEffect
            loading = true
            delay(SEARCH_DEBOUNCE_MS)
            when (val result = withContext(Dispatchers.IO) { provider.search(text) }) {
                is ProviderResult.Success -> tracks = result.value
                is ProviderResult.Failure -> failure = result.failure
            }
            loading = false
        }

        if (provider == null || !signedIn || text.isEmpty()) return
        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.yandex_search_title),
                color = palette.textPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            if (loading) Column(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) { CircularProgressIndicator() }
            failure?.let {
                Text(
                    stringResource(failureText(it)),
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            val found = tracks
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
        /** Stands in for the provider state when the provider is absent, so the collection stays unconditional. */
        val SignedOutState = MutableStateFlow<MusicProviderState>(MusicProviderState.SignedOut)
    }
}
