package com.beykus.beygram.ui.screens.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PLAYBACK_SPEED_OPTIONS
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackState
import com.beykus.beygram.playback.playbackSpeedFor
import com.beykus.beygram.playback.speedLabel
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.rememberMotionSpec
import com.beykus.beygram.ui.screens.chat.duration
import kotlinx.coroutines.flow.collect
import kotlin.math.roundToInt

/** Цвета управления плеером: на светлом листе приложения и на тёмном фоне просмотрщика. */
internal class NowPlayingColors(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentInk: Color
)

internal val ViewerNowPlayingColors = NowPlayingColors(
    primary = Color.White,
    secondary = Color(0xFFA39C95),
    accent = Color(0xFFCFC4FF),
    accentSoft = Color(0x33FFFFFF),
    accentInk = Color(0xFF1E1B2E)
)

/**
 * Opens the equalizer from the full player. The app root provides this once the Equalizer route exists; the
 * optional [NowPlayingBar] parameter stays available as an explicit override for previews/tests.
 */
val LocalOpenEqualizer = compositionLocalOf<(() -> Unit)?> { null }

/**
 * Мини-плеер 2.0: одна компактная полоса над нижним меню или composer. Слева artwork, отдельные Title/Artist,
 * затем Next (только если он реально доступен), Play/Pause и Close/Stop. Тап по основной части открывает полный
 * плеер, а кнопка Play — нет. Позиция читается отдельным листом и не перерисовывает всю полосу.
 *
 * [bottomInset] = true нужен на экранах без собственного нижнего меню или composer: тогда полоса сама держится
 * выше системной навигации и клавиатуры. В чате composer уже применяет IME inset, поэтому полоса не дублирует его.
 */
@Composable
fun NowPlayingBar(bottomInset: Boolean = false, onOpenEqualizer: (() -> Unit)? = null) {
    val providedOpenEqualizer = LocalOpenEqualizer.current
    val openEqualizer = onOpenEqualizer ?: providedOpenEqualizer
    val mini = rememberMiniPlayerUi()
    var lastMini by remember { mutableStateOf(mini) }
    SideEffect { if (mini != null) lastMini = mini }
    var fullPlayerOpen by rememberSaveable { mutableStateOf(false) }
    val enterSpec = rememberMotionSpec(MotionToken.SheetEnter)
    val exitSpec = rememberMotionSpec(MotionToken.SheetExit)

    LaunchedEffect(Unit) {
        MusicPlayerLauncher.openRequests.collect {
            if (PlaybackEngine.state.value.item != null) fullPlayerOpen = true
        }
    }
    LaunchedEffect(mini == null) {
        if (mini == null) fullPlayerOpen = false
    }

    AnimatedVisibility(
        visible = mini != null,
        // The bar only slides and fades. Instant specs (Off, reduced motion) skip both, so it appears on its first frame.
        enter = if (enterSpec.isInstant) EnterTransition.None else
            enterSpec.fadeEnter() + slideInVertically(enterSpec.animationSpec()) { (it / 2 * enterSpec.intensity).roundToInt() },
        exit = if (exitSpec.isInstant) ExitTransition.None else
            exitSpec.fadeExit() + slideOutVertically(exitSpec.animationSpec()) { (it / 2 * exitSpec.intensity).roundToInt() },
        modifier = Modifier.fillMaxWidth()
    ) {
        val shown = mini ?: lastMini
        if (shown != null) {
            MiniPlayerBar(
                ui = shown,
                bottomInset = bottomInset,
                onOpen = { fullPlayerOpen = true }
            )
        }
    }

    if (fullPlayerOpen) {
        if (mini?.isMusic == true) {
            MusicPlayerSheet(onDismiss = { fullPlayerOpen = false }, onOpenEqualizer = openEqualizer)
        } else {
            VoiceNowPlayingSheet(onDismiss = { fullPlayerOpen = false })
        }
    }
}

@Composable
private fun MiniPlayerBar(ui: MiniPlayerUi, bottomInset: Boolean, onOpen: () -> Unit) {
    val palette = beyGramPalette()
    val openLabel = stringResource(R.string.player_mini_open)
    val textSpec = rememberMotionSpec(MotionToken.BannerChange)
    val playSpec = rememberMotionSpec(MotionToken.MicroFeedback)
    val subtitle = miniSubtitle(ui)
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .then(
                if (bottomInset) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                else Modifier
            )
    ) {
        MiniPlayerProgress()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpen)
                    .padding(start = 2.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackArtwork(
                    artworkPath = ui.artworkPath,
                    audioPath = ui.audioPath,
                    modifier = Modifier.size(42.dp),
                    background = palette.accentSoft,
                    tint = palette.accentInk,
                    symbolSize = 20.dp,
                    cornerRadius = 10.dp,
                    border = palette.divider.copy(alpha = 0.55f)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Crossfade(
                        targetState = MiniPlayerText(ui.title, subtitle),
                        animationSpec = textSpec.animationSpec(),
                        label = "miniPlayerText"
                    ) { text ->
                        Column {
                            Text(
                                text.title,
                                color = palette.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text.subtitle,
                                    color = if (ui.failed) palette.danger else palette.textSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (ui.failed) {
                                    Icon(
                                        Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = palette.danger,
                                        modifier = Modifier.padding(start = 4.dp).size(13.dp)
                                    )
                                } else if (ui.intentPlaying && ui.buffering) {
                                    CircularProgressIndicator(
                                        Modifier.padding(start = 6.dp).size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = palette.accent
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (ui.canNext) {
                IconButton(onClick = { PlaybackEngine.next() }) {
                    Icon(Icons.Rounded.SkipNext, stringResource(R.string.player_next), tint = palette.textSecondary)
                }
            }
            val playIcon = when {
                ui.failed || ui.ended -> Icons.Rounded.Replay
                ui.playing || ui.intentPlaying -> Icons.Rounded.Pause
                else -> Icons.Rounded.PlayArrow
            }
            val playLabel = when {
                ui.failed -> stringResource(R.string.player_retry)
                ui.ended -> stringResource(R.string.player_replay)
                ui.playing || ui.intentPlaying -> stringResource(R.string.common_pause)
                else -> stringResource(R.string.common_play)
            }
            IconButton(
                onClick = { PlaybackEngine.togglePlayPause() },
                modifier = Modifier.semantics { contentDescription = playLabel }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Crossfade(
                        targetState = playIcon,
                        animationSpec = playSpec.animationSpec(),
                        label = "miniPlayPause"
                    ) { icon ->
                        Icon(icon, null, tint = palette.accentInk)
                    }
                    if (ui.intentPlaying && ui.buffering) {
                        CircularProgressIndicator(
                            Modifier.size(26.dp),
                            strokeWidth = 2.dp,
                            color = palette.accent.copy(alpha = 0.55f)
                        )
                    }
                }
            }
            IconButton(onClick = { PlaybackEngine.stop() }) {
                Icon(Icons.Rounded.Close, stringResource(R.string.player_stop), tint = palette.textSecondary)
            }
        }
    }
}

@Composable
private fun miniSubtitle(ui: MiniPlayerUi): String {
    if (ui.failed) return stringResource(R.string.player_failed)
    if (!ui.isMusic) return duration(ui.durationSeconds)
    if (ui.artist.isNotBlank()) return ui.artist
    if (ui.album.isNotBlank()) return ui.album
    if (ui.durationSeconds > 0) return duration(ui.durationSeconds)
    return stringResource(R.string.media_kind_audio)
}

private data class MiniPlayerText(val title: String, val subtitle: String)

/** Real playback progress, not buffering. Buffered data is a faint underlay so the two are distinguishable. */
@Composable
private fun MiniPlayerProgress() {
    val palette = beyGramPalette()
    val position = rememberPlaybackPositionUi()
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        drawLine(palette.divider, start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f), end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f), strokeWidth = size.height, cap = StrokeCap.Butt)
        if (position.bufferedFraction > 0f) {
            drawLine(
                palette.accent.copy(alpha = 0.22f),
                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                end = androidx.compose.ui.geometry.Offset(size.width * position.bufferedFraction, size.height / 2f),
                strokeWidth = size.height,
                cap = StrokeCap.Butt
            )
        }
        if (position.fraction > 0f) {
            drawLine(
                palette.accent,
                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                end = androidx.compose.ui.geometry.Offset(size.width * position.fraction, size.height / 2f),
                strokeWidth = size.height,
                cap = StrokeCap.Butt
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceNowPlayingSheet(onDismiss: () -> Unit) {
    val state by PlaybackEngine.state.collectAsStateWithLifecycle()
    val palette = beyGramPalette()
    val item = state.item ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.surface
    ) {
        NowPlayingControls(
            state = state,
            item = item,
            colors = NowPlayingColors(palette.textPrimary, palette.textSecondary, palette.accent, palette.accentSoft, palette.accentInk),
            enabled = true,
            onPlayPause = { PlaybackEngine.togglePlayPause() }
        )
        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.Center) {
            item.origin?.let { origin ->
                TextButton(onClick = { onDismiss(); PlaybackEngine.requestShowInChat(origin) }) {
                    Text(stringResource(R.string.media_show_in_chat), color = palette.accent)
                }
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close), color = palette.textSecondary)
            }
        }
    }
}

/**
 * Управление файлом: название, перемотка, пауза и скорость. Используется и в полном плеере, и в просмотрщике.
 * [enabled] = false, когда файл не играет в общем плеере: перемотка и скорость относятся к другому файлу, поэтому выключены.
 */
@Composable
internal fun NowPlayingControls(
    state: PlaybackState,
    item: PlaybackItem,
    colors: NowPlayingColors,
    enabled: Boolean,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TrackArtwork(
            artworkPath = item.artworkPath,
            audioPath = item.path.takeIf { item.kind == MediaKind.Audio },
            modifier = Modifier.size(144.dp),
            background = colors.accentSoft, tint = colors.accent, symbolSize = 56.dp,
            cornerRadius = 16.dp
        )
        Spacer(Modifier.height(16.dp))
        Text(item.title, color = colors.primary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (state.failed) Text(stringResource(R.string.media_audio_play_failed), color = colors.secondary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Slider(
            value = state.fraction,
            onValueChange = { PlaybackEngine.seekTo(it, fallbackDurationMs = item.durationSeconds * 1000L) },
            enabled = enabled && (state.durationMs > 0L || item.durationSeconds > 0),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(duration((state.positionMs / 1000).toInt()), color = colors.secondary, fontSize = 12.sp)
            Text(duration(totalSeconds(state)), color = colors.secondary, fontSize = 12.sp)
        }
        Box(
            Modifier.padding(top = 12.dp).size(64.dp).clip(CircleShape).background(colors.accent)
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (state.playing) R.string.common_pause else R.string.common_play),
                tint = colors.accentInk,
                modifier = Modifier.size(36.dp)
            )
        }
        Text(stringResource(R.string.player_speed), color = colors.secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
        val selectedSpeed = playbackSpeedFor(item.kind, state.musicSpeed, state.voiceSpeed)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp).alpha(if (enabled) 1f else 0.4f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PLAYBACK_SPEED_OPTIONS.forEach { speed ->
                SpeedChip(
                    speed = speed,
                    selected = selectedSpeed == speed,
                    enabled = enabled,
                    colors = colors,
                    onSelect = {
                        if (item.kind == MediaKind.Audio) PlaybackEngine.setMusicSpeed(speed) else PlaybackEngine.setVoiceSpeed(speed)
                    }
                )
            }
        }
    }
}

/** Полная длина в секундах: из плеера, если он её знает, иначе из сообщения. */
private fun totalSeconds(state: PlaybackState): Int =
    if (state.durationMs > 0) (state.durationMs / 1000).toInt() else state.item?.durationSeconds ?: 0

@Composable
private fun SpeedChip(speed: Float, selected: Boolean, enabled: Boolean, colors: NowPlayingColors, onSelect: () -> Unit) {
    Text(
        stringResource(R.string.player_speed_option, speedLabel(speed)),
        color = if (selected) colors.accentInk else colors.primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.accent else colors.accentSoft)
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
