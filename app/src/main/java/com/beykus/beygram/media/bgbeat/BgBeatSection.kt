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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackState
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.playback.NowPlayingColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** The text for a reason BG Beat cannot analyse the current item. Every reason has its own string resource. */
private fun BgBeatIneligibleReason.labelRes(): Int = when (this) {
    BgBeatIneligibleReason.NotMusic -> R.string.bgbeat_reason_not_music
    BgBeatIneligibleReason.PrivateMedia -> R.string.bgbeat_reason_private_media
    BgBeatIneligibleReason.StillDownloading -> R.string.bgbeat_reason_still_downloading
    BgBeatIneligibleReason.NotDownloaded -> R.string.bgbeat_reason_not_downloaded
    BgBeatIneligibleReason.UnsupportedFormat -> R.string.bgbeat_reason_unsupported_format
}

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

    // The player shows only a finished result: a wave of the track and a small BPM caption. States that matter
    // only to the analysis (ready to start, unsupported file, an error) stay out of the player screen.
    val canRefresh = stateItemKey == null || stateItemKey == item.key
    when (val s = uiState) {
        is BgBeatUiState.Disabled, is BgBeatUiState.Idle, is BgBeatUiState.Ineligible, is BgBeatUiState.Error -> return
        is BgBeatUiState.Analyzing -> {
            LinearProgressIndicator(
                progress = { s.fraction },
                modifier = modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = accentColor,
                trackColor = accentSoftColor
            )
        }
        is BgBeatUiState.Ready -> {
            val analysis = s.analysis
            Column(modifier.fillMaxWidth()) {
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
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentSoftColor.copy(alpha = 0.2f))
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.bgbeat_bpm_caption, String.format(Locale.US, "%.0f", analysis.bpm)),
                        color = secondaryColor,
                        fontSize = 11.sp
                    )
                    IconButton(
                        onClick = { manager.reanalyze() },
                        modifier = Modifier.size(28.dp),
                        enabled = canRefresh
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.bgbeat_reanalyze),
                            tint = secondaryColor.copy(alpha = if (canRefresh) 1f else 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
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
