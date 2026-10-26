package com.beykus.beygram.ui.screens.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Small, focused selectors keep the 250 ms progress stream away from the player hierarchy. Only the composable that
 * actually displays the playhead reads [PlaybackPositionUi]; artwork, titles and buttons stay still.
 */
@Composable
internal fun rememberMiniPlayerUi(): MiniPlayerUi? {
    val flow = remember { PlaybackEngine.state.map { it.miniPlayerUi() }.distinctUntilChanged() }
    return flow.collectAsStateWithLifecycle(initialValue = PlaybackEngine.state.value.miniPlayerUi()).value
}

@Composable
internal fun rememberMusicPlayerUi(): MusicPlayerUi? {
    val flow = remember { PlaybackEngine.state.map { it.musicPlayerUi() }.distinctUntilChanged() }
    return flow.collectAsStateWithLifecycle(initialValue = PlaybackEngine.state.value.musicPlayerUi()).value
}

@Composable
internal fun rememberMusicQueueUi(): MusicQueueUi? {
    val flow = remember { PlaybackEngine.state.map { it.musicQueueUi() }.distinctUntilChanged() }
    return flow.collectAsStateWithLifecycle(initialValue = PlaybackEngine.state.value.musicQueueUi()).value
}

@Composable
internal fun rememberMusicCardPlayback(key: String): MusicCardPlayback {
    val initial = remember(key) { PlaybackEngine.state.value.musicCardPlayback(key) }
    val flow = remember(key) {
        PlaybackEngine.state.map { it.musicCardPlayback(key) }.distinctUntilChanged()
    }
    return flow.collectAsStateWithLifecycle(initialValue = initial).value
}

internal data class PlaybackPositionUi(
    val positionMs: Long,
    val durationMs: Long,
    val fraction: Float,
    val bufferedFraction: Float
)

@Composable
internal fun rememberPlaybackPositionUi(): PlaybackPositionUi {
    val flow = remember {
        PlaybackEngine.state.map {
            PlaybackPositionUi(
                positionMs = it.positionMs,
                durationMs = it.durationMs,
                fraction = it.fraction,
                bufferedFraction = it.bufferedFraction.coerceIn(0f, 1f)
            )
        }.distinctUntilChanged()
    }
    return flow.collectAsStateWithLifecycle(initialValue = PlaybackEngine.state.value.positionUi()).value
}

@Composable
internal fun rememberPlaybackFraction(): Float {
    val flow = remember { PlaybackEngine.state.map { it.fraction }.distinctUntilChanged() }
    return flow.collectAsStateWithLifecycle(initialValue = PlaybackEngine.state.value.fraction).value
}

private fun PlaybackState.positionUi() = PlaybackPositionUi(
    positionMs = positionMs,
    durationMs = durationMs,
    fraction = fraction,
    bufferedFraction = bufferedFraction.coerceIn(0f, 1f)
)
