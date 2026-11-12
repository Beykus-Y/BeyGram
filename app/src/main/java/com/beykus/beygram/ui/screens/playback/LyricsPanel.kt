package com.beykus.beygram.ui.screens.playback

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.MusicProviders
import com.beykus.beygram.data.music.provider.ProviderLyrics
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.YANDEX_TRACK_KEY_PREFIX
import com.beykus.beygram.domain.lyrics.LyricLine as TimedLyricLine
import com.beykus.beygram.domain.lyrics.SyncedLyrics
import com.beykus.beygram.ui.components.TextButton

/** What the lyrics view shows for one track. Failures are mapped to reasons, never to raw provider text. */
internal sealed interface YandexLyricsState {
    object Loading : YandexLyricsState
    data class Ready(val lyrics: ProviderLyrics) : YandexLyricsState
    data class Unavailable(val reason: LyricsUnavailableReason) : YandexLyricsState
}

internal enum class LyricsUnavailableReason { NoText, NotSignedIn, Failed }

/**
 * Lyrics section of the full player for a Yandex Music track. It is shown only when the DEV build has the provider
 * and the current item has a `ym:` key; everywhere else the player is unchanged.
 */
@Composable
internal fun YandexLyricsSection(
    key: String,
    positionState: State<PlaybackPositionUi>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val provider = remember(context) { MusicProviders.yandex(context) } ?: return
    val trackId = key.removePrefix(YANDEX_TRACK_KEY_PREFIX)
    var open by rememberSaveable(trackId) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = { open = !open }) {
            Text(stringResource(if (open) R.string.lyrics_hide else R.string.lyrics_show))
        }
        if (open) {
            val state = rememberYandexLyrics(trackId)
            Box(Modifier.fillMaxWidth().height(260.dp)) {
                LyricsContent(state, positionState)
            }
        }
    }
}

@Composable
internal fun rememberYandexLyrics(trackId: String): YandexLyricsState {
    val context = LocalContext.current
    val provider = remember(context) { MusicProviders.yandex(context) }
    var state by remember(trackId) { mutableStateOf<YandexLyricsState>(YandexLyricsState.Loading) }
    LaunchedEffect(provider, trackId) {
        if (provider == null) {
            state = YandexLyricsState.Unavailable(LyricsUnavailableReason.NotSignedIn)
            return@LaunchedEffect
        }
        state = YandexLyricsState.Loading
        state = when (val result = provider.lyrics(trackId)) {
            is ProviderResult.Success -> {
                val lyrics = result.value
                if (lyrics.isSynced || !lyrics.plainText.isNullOrBlank()) {
                    YandexLyricsState.Ready(lyrics)
                } else {
                    YandexLyricsState.Unavailable(LyricsUnavailableReason.NoText)
                }
            }
            is ProviderResult.Failure -> YandexLyricsState.Unavailable(result.failure.toLyricsReason())
        }
    }
    return state
}

private fun MusicProviderFailure.toLyricsReason(): LyricsUnavailableReason = when (this) {
    MusicProviderFailure.NotSignedIn, MusicProviderFailure.SessionExpired -> LyricsUnavailableReason.NotSignedIn
    MusicProviderFailure.NotFound -> LyricsUnavailableReason.NoText
    else -> LyricsUnavailableReason.Failed
}

@Composable
private fun LyricsContent(state: YandexLyricsState, positionState: State<PlaybackPositionUi>) {
    when (state) {
        YandexLyricsState.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is YandexLyricsState.Unavailable -> Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(state.reason.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        is YandexLyricsState.Ready -> ReadyLyrics(state.lyrics, positionState)
    }
}

private fun LyricsUnavailableReason.messageRes(): Int = when (this) {
    LyricsUnavailableReason.NoText -> R.string.lyrics_none_for_track
    LyricsUnavailableReason.NotSignedIn -> R.string.lyrics_need_sign_in
    LyricsUnavailableReason.Failed -> R.string.lyrics_failed
}

@Composable
private fun ReadyLyrics(lyrics: ProviderLyrics, positionState: State<PlaybackPositionUi>) {
    val listState = rememberLazyListState()
    val timed = remember(lyrics) {
        SyncedLyrics(lyrics.lines.map { TimedLyricLine(startMs = it.timeMs, text = it.text) })
    }
    // Reads the playhead inside derivedStateOf, so the list recomposes only when the highlighted line changes.
    val active by remember(timed) {
        derivedStateOf { if (timed.isEmpty) -1 else timed.activeIndex(positionState.value.positionMs) }
    }
    LaunchedEffect(active) {
        if (active >= 0) {
            val viewport = listState.layoutInfo.viewportSize.height
            listState.animateScrollToItem(active, -(viewport / 3))
        }
    }
    val holder = lyrics.copyrightHolder
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (timed.isEmpty) {
            item {
                Text(
                    text = lyrics.plainText.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            itemsIndexed(timed.lines) { index, line ->
                LyricRow(text = line.text, highlighted = index == active)
            }
        }
        if (!holder.isNullOrBlank()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.lyrics_copyright, holder),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun LyricRow(text: String, highlighted: Boolean) {
    val color by animateColorAsState(
        targetValue = if (highlighted) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        label = "lyricColor"
    )
    if (text.isEmpty()) {
        Spacer(Modifier.height(12.dp))
        return
    }
    Text(
        text = text,
        color = color,
        fontSize = if (highlighted) 22.sp else 18.sp,
        fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}
