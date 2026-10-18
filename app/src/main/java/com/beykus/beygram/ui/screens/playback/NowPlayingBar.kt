package com.beykus.beygram.ui.screens.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackState
import com.beykus.beygram.playback.speedLabel
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.duration

private val SPEEDS = listOf(0.5f, 1f, 1.5f, 2f)

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
 * Мини-плеер над нижним меню или полем ввода. Виден, пока в общем плеере есть файл, на любом экране приложения.
 * Тап по полоске открывает полный плеер.
 */
@Composable
fun NowPlayingBar() {
    val state by PlaybackEngine.state.collectAsStateWithLifecycle()
    val item = state.item ?: return
    val palette = beyGramPalette()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(palette.surface)) {
        LinearProgressIndicator(
            progress = { state.fraction },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = palette.accent,
            trackColor = palette.divider
        )
        Row(
            Modifier.fillMaxWidth().clickable { sheetOpen = true }.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.MusicNote, contentDescription = null, tint = palette.accentInk,
                modifier = Modifier.size(40.dp).background(palette.accentSoft, CircleShape).padding(10.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle(state), color = palette.textSecondary, fontSize = 12.sp, maxLines = 1)
            }
            IconButton(onClick = { PlaybackEngine.togglePlayPause() }) {
                Icon(
                    if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (state.playing) R.string.common_pause else R.string.common_play),
                    tint = palette.accentInk
                )
            }
            IconButton(onClick = { PlaybackEngine.stop() }) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.player_close), tint = palette.textSecondary)
            }
        }
    }
    if (sheetOpen) NowPlayingSheet(onDismiss = { sheetOpen = false })
}

@Composable
private fun subtitle(state: PlaybackState): String {
    if (state.failed) return stringResource(R.string.media_audio_play_failed)
    return "${duration((state.positionMs / 1000).toInt())} / ${duration(totalSeconds(state))}"
}

/** Полная длина в секундах: из плеера, если он её знает, иначе из сообщения. */
private fun totalSeconds(state: PlaybackState): Int =
    if (state.durationMs > 0) (state.durationMs / 1000).toInt() else state.item?.durationSeconds ?: 0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingSheet(onDismiss: () -> Unit) {
    val state by PlaybackEngine.state.collectAsStateWithLifecycle()
    val palette = beyGramPalette()
    val item = state.item ?: return
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.surface) {
        NowPlayingControls(
            state = state,
            item = item,
            colors = NowPlayingColors(palette.textPrimary, palette.textSecondary, palette.accent, palette.accentSoft, palette.accentInk)
        )
    }
}

/** Управление файлом: название, перемотка, пауза и скорость. Используется и в полном плеере, и в просмотрщике. */
@Composable
internal fun NowPlayingControls(state: PlaybackState, item: PlaybackItem, colors: NowPlayingColors, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Rounded.MusicNote, contentDescription = null, tint = colors.accent,
            modifier = Modifier.size(96.dp).background(colors.accentSoft, CircleShape).padding(28.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(item.title, color = colors.primary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (state.failed) Text(stringResource(R.string.media_audio_play_failed), color = colors.secondary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Slider(
            value = state.fraction,
            onValueChange = { PlaybackEngine.seekTo(it) },
            enabled = state.durationMs > 0,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(duration((state.positionMs / 1000).toInt()), color = colors.secondary, fontSize = 12.sp)
            Text(duration(totalSeconds(state)), color = colors.secondary, fontSize = 12.sp)
        }
        Box(
            Modifier.padding(top = 12.dp).size(64.dp).clip(CircleShape).background(colors.accent)
                .clickable { PlaybackEngine.togglePlayPause() },
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
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SPEEDS.forEach { speed -> SpeedChip(speed = speed, selected = state.speed == speed, colors = colors) }
        }
    }
}

@Composable
private fun SpeedChip(speed: Float, selected: Boolean, colors: NowPlayingColors) {
    Text(
        stringResource(R.string.player_speed_option, speedLabel(speed)),
        color = if (selected) colors.accentInk else colors.primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.accent else colors.accentSoft)
            .clickable { PlaybackEngine.setSpeed(speed) }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
