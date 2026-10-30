package com.beykus.beygram.media.bgbeat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackState
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.playback.NowPlayingColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Experimental BG Beat music analysis card shown in the music player.
 * Matches analysis strictly to the displayed audio item to avoid cross-track or viewer mismatches.
 */
@Composable
internal fun BgBeatSection(
    item: PlaybackItem,
    playbackFraction: Float,
    modifier: Modifier = Modifier,
    colors: NowPlayingColors? = null
) {
    val context = LocalContext.current
    val manager = BgBeatManager.getInstance(context)
    val uiState by manager.uiState.collectAsStateWithLifecycle()
    val palette = beyGramPalette()

    val primaryColor = colors?.primary ?: palette.textPrimary
    val secondaryColor = colors?.secondary ?: palette.textSecondary
    val accentColor = colors?.accent ?: palette.accent
    val accentSoftColor = colors?.accentSoft ?: palette.accentSoft

    if (uiState is BgBeatUiState.Disabled) {
        return
    }

    // Match the analysis card strictly to the displayed audio item
    val stateItemKey = uiState.itemKey
    if (stateItemKey != null && stateItemKey != item.key) {
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accentSoftColor.copy(alpha = 0.25f))
            .padding(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "BG Beat",
                    color = primaryColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "C++20",
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(
                onClick = { manager.reanalyze() },
                modifier = Modifier.size(32.dp),
                enabled = (uiState is BgBeatUiState.Ready || uiState is BgBeatUiState.Error) &&
                    (stateItemKey == null || stateItemKey == item.key)
            ) {
                val canRefresh = (uiState is BgBeatUiState.Ready || uiState is BgBeatUiState.Error) &&
                    (stateItemKey == null || stateItemKey == item.key)
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Повторить анализ",
                    tint = if (canRefresh) secondaryColor else secondaryColor.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when (val s = uiState) {
            is BgBeatUiState.Disabled -> {}
            is BgBeatUiState.Idle -> {
                Text(
                    text = "Готов к анализу",
                    color = secondaryColor,
                    fontSize = 13.sp
                )
            }
            is BgBeatUiState.Ineligible -> {
                Text(
                    text = s.reason,
                    color = secondaryColor,
                    fontSize = 12.sp
                )
            }
            is BgBeatUiState.Analyzing -> {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = s.stage,
                            color = secondaryColor,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${(s.fraction * 100).toInt()}%",
                            color = accentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { s.fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = accentColor,
                        trackColor = accentSoftColor
                    )
                }
            }
            is BgBeatUiState.Error -> {
                Text(
                    text = s.message,
                    color = palette.danger,
                    fontSize = 12.sp
                )
            }
            is BgBeatUiState.Ready -> {
                val analysis = s.analysis
                Column(Modifier.fillMaxWidth()) {
                    // Metrics Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "BPM",
                                color = secondaryColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f", analysis.bpm),
                                color = primaryColor,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Уверенность",
                                color = secondaryColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${(analysis.bpmConfidence * 100).toInt()}%",
                                    color = primaryColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (analysis.cacheHit) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(accentColor.copy(alpha = 0.15f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "Кеш",
                                            color = accentColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Waveform & Rhythm Map Canvas
                    BgBeatWaveformCanvas(
                        waveform = analysis.waveform,
                        beats = analysis.beats,
                        durationSeconds = analysis.duration,
                        playbackFraction = playbackFraction,
                        accentColor = accentColor,
                        barColor = secondaryColor.copy(alpha = 0.45f),
                        cursorColor = primaryColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentSoftColor.copy(alpha = 0.2f))
                    )
                }
            }
        }
    }
}

@Composable
internal fun BgBeatSection(
    state: PlaybackState,
    item: PlaybackItem,
    colors: NowPlayingColors,
    modifier: Modifier = Modifier
) {
    BgBeatSection(
        item = item,
        playbackFraction = state.fraction,
        modifier = modifier,
        colors = colors
    )
}

@Composable
private fun BgBeatWaveformCanvas(
    waveform: List<BgBeatWaveformPeak>,
    beats: List<BgBeatBeat>,
    durationSeconds: Double,
    playbackFraction: Float,
    accentColor: Color,
    barColor: Color,
    cursorColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        if (waveform.isNotEmpty()) {
            val count = waveform.size
            val barWidth = max(1f, width / count)

            for (i in 0 until count) {
                val peak = waveform[i]
                val amp = max(abs(peak.min), abs(peak.max)).coerceIn(0.04f, 1.0f)
                val barHeight = amp * (height * 0.75f)

                val x = i * barWidth
                drawRect(
                    color = barColor,
                    topLeft = Offset(x, centerY - barHeight / 2f),
                    size = Size(max(1f, barWidth - 0.5f), barHeight)
                )
            }
        }

        // Beat markers along the bottom edge
        if (durationSeconds > 0.0 && beats.isNotEmpty()) {
            for (beat in beats) {
                val beatFraction = (beat.time / durationSeconds).toFloat()
                if (beatFraction in 0f..1f) {
                    val beatX = beatFraction * width
                    val markerHeight = if (beat.downbeat) 8f else 4f
                    val markerColor = if (beat.downbeat) accentColor else accentColor.copy(alpha = 0.5f)

                    drawLine(
                        color = markerColor,
                        start = Offset(beatX, height - markerHeight),
                        end = Offset(beatX, height),
                        strokeWidth = if (beat.downbeat) 2f else 1f
                    )
                }
            }
        }

        // Synchronized playback cursor
        val cursorX = (playbackFraction * width).coerceIn(0f, width)
        drawLine(
            color = cursorColor,
            start = Offset(cursorX, 0f),
            end = Offset(cursorX, height),
            strokeWidth = 2f
        )
    }
}
