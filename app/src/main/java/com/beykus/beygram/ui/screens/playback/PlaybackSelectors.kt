package com.beykus.beygram.ui.screens.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.playback.LazyTrackProgress
import com.beykus.beygram.playback.LazyTrackRegistry
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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

/** Telegram rows near the playing track that still wait for their file. The engine prefetches only the first of them. */
private const val TELEGRAM_WATCH_AHEAD = 4

private data class TelegramWatchRow(val key: String, val fileId: Int)

/** Upcoming rows of the queue that come from Telegram and have no file yet, in playback order. */
private fun telegramWatchRows(state: PlaybackState): List<TelegramWatchRow> {
    if (state.queueIndex !in state.queue.indices) return emptyList()
    return state.queue.asSequence()
        .drop(state.queueIndex + 1)
        .filter { !it.systemPrivate && it.growing == null && it.path.isBlank() }
        .mapNotNull { item -> item.fileId?.let { TelegramWatchRow(item.key, it) } }
        .take(TELEGRAM_WATCH_AHEAD)
        .toList()
}

private fun LazyTrackProgress.merged(other: LazyTrackProgress): LazyTrackProgress =
    LazyTrackProgress(fetching = fetching + other.fetching, ready = ready + other.ready)

@Composable
internal fun rememberMusicQueueUi(): MusicQueueUi? {
    // Telegram transfers do not pass through LazyTrackRegistry. While the queue is open, the file observers of its own
    // source report them, so a row that is downloading shows as Downloading and a finished file as Ready.
    val telegram = remember { MutableStateFlow(LazyTrackProgress()) }
    LaunchedEffect(telegram) {
        PlaybackEngine.state
            .map { state -> PlaybackEngine.activeQueueSource() to telegramWatchRows(state) }
            .distinctUntilChanged()
            .collectLatest { (source, rows) ->
                if (source == null || rows.isEmpty()) {
                    telegram.value = LazyTrackProgress()
                } else {
                    combine(rows.map { row -> source.observeFile(row.fileId).map { row.key to it } }) { pairs ->
                        LazyTrackProgress(
                            fetching = pairs.filter { it.second.isDownloading }.map { it.first }.toSet(),
                            ready = pairs.filter { it.second.localPath != null }.map { it.first }.toSet()
                        )
                    }.collect { telegram.value = it }
                }
            }
    }
    val flow = remember {
        combine(PlaybackEngine.state, LazyTrackRegistry.progress, telegram) { state, lazy, watched ->
            state.musicQueueUi(progress = lazy.merged(watched))
        }.distinctUntilChanged()
    }
    val base = flow.collectAsStateWithLifecycle(
        initialValue = PlaybackEngine.state.value.musicQueueUi(
            progress = LazyTrackRegistry.progress.value.merged(telegram.value)
        )
    ).value
    val context = LocalContext.current
    // The actual shuffle sequence lives in Media3. It only needs to be read when the queue snapshot changes,
    // never on the 250 ms progress stream; MusicQueueUi has no position fields.
    val playbackOrder = remember(base, context) {
        if (base?.shuffleEnabled == true) media3PlaybackOrder(context) else null
    }
    return remember(base, playbackOrder) { base?.withPlaybackOrder(playbackOrder) }
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

/** The position as a State object: reading `.value` only where it is drawn keeps the rest of the screen from recomposing on every tick. */
@Composable
internal fun rememberPlaybackPositionState(): androidx.compose.runtime.State<PlaybackPositionUi> {
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
    return flow.collectAsStateWithLifecycle(initialValue = PlaybackEngine.state.value.positionUi())
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
