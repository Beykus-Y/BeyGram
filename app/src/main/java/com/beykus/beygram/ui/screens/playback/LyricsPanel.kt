package com.beykus.beygram.ui.screens.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.MusicProviders
import com.beykus.beygram.data.music.provider.ProviderLyrics
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTracks
import com.beykus.beygram.data.music.provider.YANDEX_TRACK_KEY_PREFIX
import com.beykus.beygram.data.yandex.YandexConfig
import com.beykus.beygram.domain.lyrics.SyncedLyrics
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.ui.components.TextButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.fadeEnter
import com.beykus.beygram.ui.motion.fadeExit
import com.beykus.beygram.ui.motion.rememberMotionInstant
import com.beykus.beygram.ui.motion.rememberMotionSpec
import com.beykus.beygram.ui.motion.scrollToItemRespectingMotion
import com.beykus.beygram.ui.screens.music.rememberProviderCover
import com.beykus.beygram.domain.lyrics.LyricLine as TimedLyricLine

/** What the lyrics view shows for one track. Failures are mapped to reasons, never to raw provider text. */
internal sealed interface YandexLyricsState {
    object Loading : YandexLyricsState
    data class Ready(val lyrics: ProviderLyrics) : YandexLyricsState
    data class Unavailable(val reason: LyricsUnavailableReason) : YandexLyricsState
}

internal enum class LyricsUnavailableReason { NoText, NotSignedIn, Failed }

internal enum class LyricLinePhase { Past, Active, Upcoming }

internal fun lyricLinePhase(index: Int, activeIndex: Int): LyricLinePhase = when {
    activeIndex < 0 -> LyricLinePhase.Upcoming
    index < activeIndex -> LyricLinePhase.Past
    index == activeIndex -> LyricLinePhase.Active
    else -> LyricLinePhase.Upcoming
}

/**
 * Switch for the lyrics button of Yandex tracks. On when this build has the lyrics signing key (SIGN_KEY secret);
 * without the key the button stays hidden.
 */
internal val YANDEX_LYRICS_ENABLED: Boolean get() = YandexConfig.lyricsSigningAvailable

/**
 * Loads lyrics for [trackId] and resets immediately to [YandexLyricsState.Loading] whenever [trackId] changes,
 * so rapid track switching can never show stale lyrics from the previous track.
 */
@Composable
internal fun rememberLyricsState(
    trackId: String,
    retryNonce: Int = 0,
    fetch: suspend (String) -> YandexLyricsState
): YandexLyricsState {
    var state by remember(trackId) { mutableStateOf<YandexLyricsState>(YandexLyricsState.Loading) }
    LaunchedEffect(trackId, retryNonce) {
        state = YandexLyricsState.Loading
        state = fetch(trackId)
    }
    return state
}

@Composable
internal fun rememberYandexLyrics(trackId: String, retryNonce: Int = 0): YandexLyricsState {
    val context = LocalContext.current
    val provider = remember(context) { MusicProviders.yandex(context) }
    return rememberLyricsState(trackId = trackId, retryNonce = retryNonce) { id ->
        if (provider == null) {
            YandexLyricsState.Unavailable(LyricsUnavailableReason.NotSignedIn)
        } else {
            when (val result = provider.lyrics(id)) {
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
    }
}

private fun MusicProviderFailure.toLyricsReason(): LyricsUnavailableReason = when (this) {
    MusicProviderFailure.NotSignedIn, MusicProviderFailure.SessionExpired -> LyricsUnavailableReason.NotSignedIn
    MusicProviderFailure.NotFound -> LyricsUnavailableReason.NoText
    else -> LyricsUnavailableReason.Failed
}

/**
 * Full-height lyrics panel inside the player overlay (parallel to Queue). Uses the player's own cover-adapted
 * palette ([beyGramPalette]) for guaranteed contrast, smooth frame-interpolated playhead tracking, tap-to-seek,
 * manual scroll pause with a return pill, and explicit retry on network errors.
 */
@Composable
internal fun LyricsPanel(
    player: MusicPlayerUi,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    fetchLyrics: (suspend (String) -> YandexLyricsState)? = null,
    positionMsOverride: State<Long>? = null,
    onSeekToMs: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val isYandexTrack = player.item.key.startsWith(YANDEX_TRACK_KEY_PREFIX)
    val trackId = remember(player.item.key) { player.item.key.removePrefix(YANDEX_TRACK_KEY_PREFIX) }
    var retryNonce by remember(player.item.key) { mutableIntStateOf(0) }

    val fallbackDurationMs = player.item.durationSeconds.coerceAtLeast(0) * 1000L
    val smoothPosition: State<Long> = if (positionMsOverride != null) {
        positionMsOverride
    } else {
        val pos = rememberPlaybackPositionUi()
        val duration = pos.durationMs.takeIf { it > 0L } ?: fallbackDurationMs
        rememberSmoothPositionMs(pos.positionMs, duration, player.playing, player.speed)
    }

    val seekHandler: (Long) -> Unit = onSeekToMs ?: { targetMs ->
        val duration = PlaybackEngine.state.value.durationMs.takeIf { it > 0L } ?: fallbackDurationMs
        if (duration > 0L) {
            val fraction = (targetMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            PlaybackEngine.seekTo(fraction, fallbackDurationMs = fallbackDurationMs)
        }
    }

    val state: YandexLyricsState = when {
        fetchLyrics != null -> rememberLyricsState(trackId = player.item.key, retryNonce = retryNonce, fetch = fetchLyrics)
        !isYandexTrack -> YandexLyricsState.Unavailable(LyricsUnavailableReason.NoText)
        else -> rememberYandexLyrics(trackId = trackId, retryNonce = retryNonce)
    }

    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }

    Column(modifier.fillMaxSize()) {
        LyricsHeader(
            player = player,
            onBack = onBack,
            backFocus = backFocus
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LyricsContent(
                trackKey = player.item.key,
                state = state,
                positionMs = smoothPosition,
                playing = player.playing,
                onSeekToMs = seekHandler,
                onRetry = {
                    if (isYandexTrack) {
                        MusicProviders.yandex(context)?.invalidateLyrics(trackId)
                    }
                    retryNonce += 1
                }
            )
        }
    }
}

@Composable
private fun LyricsHeader(
    player: MusicPlayerUi,
    onBack: () -> Unit,
    backFocus: FocusRequester
) {
    val palette = beyGramPalette()
    val providerCover = rememberProviderCover(ProviderTracks.byKey(player.item.key)?.coverUrl)
    val playIcon = when {
        player.failed || player.ended -> Icons.Rounded.Replay
        player.playing || player.intentPlaying -> Icons.Rounded.Pause
        else -> Icons.Rounded.PlayArrow
    }
    val playLabel = when {
        player.failed -> stringResource(R.string.player_retry)
        player.ended -> stringResource(R.string.player_replay)
        player.playing || player.intentPlaying -> stringResource(R.string.common_pause)
        else -> stringResource(R.string.common_play)
    }
    val playState = when {
        player.intentPlaying && player.buffering -> stringResource(R.string.player_buffering)
        player.playing -> stringResource(R.string.a11y_state_playing)
        else -> stringResource(R.string.a11y_state_paused)
    }
    val title = player.item.musicTitle().ifBlank { stringResource(R.string.media_kind_audio) }
    val artist = player.item.musicArtist()

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.focusRequester(backFocus)
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.queue_back_to_player),
                tint = palette.textSecondary
            )
        }
        TrackArtwork(
            artworkPath = player.item.artworkPath?.takeIf { it.isNotBlank() } ?: providerCover,
            audioPath = player.item.path.takeIf { it.isNotBlank() },
            modifier = Modifier.size(36.dp),
            background = palette.accentSoft,
            tint = palette.accentInk,
            symbolSize = 18.dp,
            cornerRadius = 8.dp,
            border = palette.divider.copy(alpha = 0.5f)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (artist.isNotBlank()) {
                Text(
                    text = artist,
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(
            onClick = { PlaybackEngine.togglePlayPause() },
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = playLabel
                    stateDescription = playState
                }
        ) {
            Icon(playIcon, contentDescription = null, tint = palette.textPrimary)
        }
    }
}

@Composable
internal fun LyricsContent(
    trackKey: String,
    state: YandexLyricsState,
    positionMs: State<Long>,
    playing: Boolean,
    onSeekToMs: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = beyGramPalette()
    when (state) {
        YandexLyricsState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = palette.accent)
        }
        is YandexLyricsState.Unavailable -> Box(
            modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(state.reason.messageRes()),
                    color = palette.textSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center
                )
                if (state.reason == LyricsUnavailableReason.Failed) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onRetry) {
                        Text(
                            text = stringResource(R.string.player_retry),
                            color = palette.accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        is YandexLyricsState.Ready -> ReadyLyrics(
            trackKey = trackKey,
            lyrics = state.lyrics,
            positionMs = positionMs,
            playing = playing,
            onSeekToMs = onSeekToMs,
            modifier = modifier
        )
    }
}

private fun LyricsUnavailableReason.messageRes(): Int = when (this) {
    LyricsUnavailableReason.NoText -> R.string.lyrics_none_for_track
    LyricsUnavailableReason.NotSignedIn -> R.string.lyrics_need_sign_in
    LyricsUnavailableReason.Failed -> R.string.lyrics_failed
}

@Composable
private fun ReadyLyrics(
    trackKey: String,
    lyrics: ProviderLyrics,
    positionMs: State<Long>,
    playing: Boolean,
    onSeekToMs: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = beyGramPalette()
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val timed = remember(lyrics) {
        SyncedLyrics(lyrics.lines.map { TimedLyricLine(startMs = it.timeMs, text = it.text) })
    }
    val hasIntroPause = remember(timed) {
        !timed.isEmpty && timed.lines.first().startMs >= 2_500L
    }
    // Reads the smooth playhead inside derivedStateOf, so the list recomposes only when the active line changes.
    val active by remember(timed) {
        derivedStateOf { if (timed.isEmpty) -1 else timed.activeIndex(positionMs.value) }
    }
    val listMotionInstant = rememberMotionInstant(MotionToken.ListReorder)
    val microMotion = rememberMotionSpec(MotionToken.MicroFeedback)
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var userDetached by remember(trackKey) { mutableStateOf(false) }

    LaunchedEffect(dragged) {
        if (dragged) userDetached = true
    }

    LaunchedEffect(trackKey) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(active, userDetached) {
        if (!userDetached && active >= 0) {
            val targetItem = active + if (hasIntroPause) 1 else 0
            val viewport = listState.layoutInfo.viewportSize.height
            listState.scrollToItemRespectingMotion(targetItem, -(viewport / 3), motionInstant = listMotionInstant)
        }
    }

    val topFade = remember(palette.surface) {
        Brush.verticalGradient(listOf(palette.surface, Color.Transparent))
    }
    val bottomFade = remember(palette.surface) {
        Brush.verticalGradient(listOf(Color.Transparent, palette.surface))
    }
    val plainParagraphs = remember(lyrics.plainText) {
        lyrics.plainText.orEmpty()
            .split(Regex("""\n{2,}"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
    val holder = lyrics.copyrightHolder

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (timed.isEmpty) {
                itemsIndexed(plainParagraphs) { _, block ->
                    Text(
                        text = block,
                        color = palette.textPrimary,
                        fontSize = 20.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                if (hasIntroPause) {
                    item(key = "intro-pause") {
                        InstrumentalDotsRow(
                            active = active == -1,
                            playing = playing,
                            reducedMotion = microMotion.isInstant
                        )
                    }
                }
                itemsIndexed(
                    items = timed.lines,
                    key = { index, line -> "$index:${line.startMs}" }
                ) { index, line ->
                    val phase = lyricLinePhase(index, active)
                    if (line.text.isEmpty()) {
                        InstrumentalDotsRow(
                            active = phase == LyricLinePhase.Active,
                            playing = playing,
                            reducedMotion = microMotion.isInstant
                        )
                    } else {
                        LyricRow(
                            text = line.text,
                            phase = phase,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                userDetached = false
                                onSeekToMs(line.startMs)
                            }
                        )
                    }
                }
            }
            if (!holder.isNullOrBlank()) {
                item(key = "copyright") {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.lyrics_copyright, holder),
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Lightweight edge gradients (no offscreen buffer or blur) so scrolling text dissolves cleanly.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(28.dp)
                .background(topFade)
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(40.dp)
                .background(bottomFade)
        )

        AnimatedVisibility(
            visible = !timed.isEmpty && userDetached && active >= 0,
            enter = microMotion.fadeEnter(),
            exit = microMotion.fadeExit(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Surface(
                color = palette.accent,
                contentColor = palette.onAccent,
                shape = RoundedCornerShape(50),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.Button) { userDetached = false }
            ) {
                Text(
                    text = stringResource(R.string.lyrics_return_to_current),
                    color = palette.onAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                )
            }
        }
    }
}

@Composable
private fun LyricRow(
    text: String,
    phase: LyricLinePhase,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    val motion = rememberMotionSpec(MotionToken.MicroFeedback)
    val seekLabel = stringResource(R.string.lyrics_seek_a11y, text)

    val targetAlpha = when (phase) {
        LyricLinePhase.Active -> 1f
        LyricLinePhase.Upcoming -> 0.56f
        LyricLinePhase.Past -> 0.34f
    }
    val targetScale = if (phase == LyricLinePhase.Active) 1.04f else 1f

    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = motion.animationSpec(),
        label = "lyricAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = motion.animationSpec(),
        label = "lyricScale"
    )

    // Layout metrics stay constant across phases; emphasis is applied in graphicsLayer so the list never jumps.
    Text(
        text = text,
        color = palette.textPrimary,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .clip(styledShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = seekLabel, onClick = onClick)
            .padding(vertical = 4.dp)
            .graphicsLayer {
                this.alpha = alpha
                this.scaleX = scale
                this.scaleY = scale
                this.transformOrigin = TransformOrigin(0f, 0.5f)
            }
    )
}

@Composable
private fun InstrumentalDotsRow(
    active: Boolean,
    playing: Boolean,
    reducedMotion: Boolean
) {
    val palette = beyGramPalette()
    val label = stringResource(R.string.lyrics_instrumental_a11y)
    val pulse = if (active && playing && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "instrumentalPulse")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "instrumentalPhase"
        ).value
    } else {
        -1f
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { dot ->
            val dotAlpha = when {
                !active -> 0.22f
                pulse < 0f -> 0.75f
                else -> {
                    val dist = kotlin.math.abs(pulse - dot)
                    (1f - (dist * 0.45f)).coerceIn(0.35f, 1f)
                }
            }
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer { alpha = dotAlpha }
                    .clip(CircleShape)
                    .background(palette.textPrimary)
            )
        }
    }
}
