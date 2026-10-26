package com.beykus.beygram.ui.screens.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.playback.PLAYBACK_SPEED_OPTIONS
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.inlineVoiceSpeed
import com.beykus.beygram.playback.speedLabel
import com.beykus.beygram.ui.components.beyGramPalette

/** Inline voice/circular-video speed choice; it always edits the voice preference and never musicSpeed. */
@Composable
internal fun PlaybackSpeedMenu(modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    val playback by PlaybackEngine.state.collectAsStateWithLifecycle()
    val selectedSpeed = playback.inlineVoiceSpeed()
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Text(
            stringResource(R.string.player_speed_option, speedLabel(selectedSpeed)),
            color = palette.accentInk,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(palette.accentSoft)
                .clickable { expanded = true }.padding(horizontal = 8.dp, vertical = 5.dp)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PLAYBACK_SPEED_OPTIONS.forEach { speed ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.player_speed_option, speedLabel(speed)),
                            color = if (selectedSpeed == speed) palette.accent else palette.textPrimary,
                            fontWeight = if (selectedSpeed == speed) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        PlaybackEngine.setVoiceSpeed(speed)
                        expanded = false
                    }
                )
            }
        }
    }
}
