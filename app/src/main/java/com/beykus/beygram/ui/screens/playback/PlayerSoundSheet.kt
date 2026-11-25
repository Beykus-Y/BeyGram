package com.beykus.beygram.ui.screens.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.media.bgrhythm.BgRhythmMode
import com.beykus.beygram.media.bgrhythm.BgRhythmSettingsStore
import com.beykus.beygram.playback.PLAYBACK_SPEED_OPTIONS
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.SLEEP_TIMER_MAX_MINUTES
import com.beykus.beygram.playback.speedLabel
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.BeyGramSheet
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.styledShape

/** Sleep timer presets shown as chips; the full list of durations stays in the playback model. */
private val SLEEP_PRESET_MINUTES = listOf(15, 30, 60)

/**
 * The sound sheet of the full player: playback speed, the sleep timer and a line to the equalizer. It stays open after a
 * speed or timer pick, so both can be set in one visit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSoundSheet(
    player: MusicPlayerUi,
    onOpenEqualizer: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sleepTimer by PlaybackEngine.sleepTimer.collectAsStateWithLifecycle()
    val rhythmModeFlow = remember(context) { BgRhythmSettingsStore.flow(context) }
    val rhythmMode by rhythmModeFlow.collectAsStateWithLifecycle()
    val minutesRemaining = rememberSleepMinutes(sleepTimer)
    var customOpen by remember { mutableStateOf(false) }
    // BG Mix has no playback speed, so the chips stay off while it plays instead of changing only the saved value.
    val speedEnabled = !player.bgMix
    BeyGramSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // The sound controls scroll on their own, so the sheet must not add a second scroll around them.
        scrollableContent = false,
        containerColor = palette.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Text(
                stringResource(R.string.player_sound),
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            SoundSectionTitle(stringResource(R.string.player_speed))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .alpha(if (speedEnabled) 1f else 0.4f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PLAYBACK_SPEED_OPTIONS.forEach { speed ->
                    SoundChip(
                        text = stringResource(R.string.player_speed_option, speedLabel(speed)),
                        selected = player.speed == speed,
                        enabled = speedEnabled,
                        onClick = { PlaybackEngine.setMusicSpeed(speed) }
                    )
                }
            }
            if (player.bgMix) Text(
                stringResource(R.string.player_speed_bgmix_hint),
                color = palette.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            SoundSectionTitle(stringResource(R.string.player_sleep_timer))
            sleepTimer?.let { timer ->
                Text(sleepTimerStatusText(timer, minutesRemaining), color = palette.textPrimary, fontSize = 13.sp)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SoundChip(stringResource(R.string.player_sleep_extend, SLEEP_EXTEND_MINUTES), false, true) {
                        PlaybackEngine.extendSleepTimer(SLEEP_EXTEND_MINUTES)
                    }
                    SoundChip(stringResource(R.string.player_sleep_cancel_plain), false, true) {
                        PlaybackEngine.cancelSleepTimer()
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SLEEP_PRESET_MINUTES.forEach { minutes ->
                    SoundChip(stringResource(R.string.player_sleep_minutes_short, minutes), false, true) {
                        PlaybackEngine.setSleepTimer(minutes)
                    }
                }
                SoundChip(
                    stringResource(R.string.player_sleep_until_track_end),
                    selected = sleepTimer?.untilTrackEnd == true,
                    enabled = true
                ) {
                    PlaybackEngine.setSleepTimerUntilTrackEnd()
                }
                SoundChip(stringResource(R.string.player_sleep_custom), false, true) { customOpen = true }
            }
            SoundSectionTitle(stringResource(R.string.rhythm_section_title))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SoundChip(stringResource(R.string.rhythm_mode_off), rhythmMode == BgRhythmMode.Off, true) {
                    BgRhythmSettingsStore.setMode(context, BgRhythmMode.Off)
                }
                SoundChip(stringResource(R.string.rhythm_mode_calm), rhythmMode == BgRhythmMode.Calm, true) {
                    BgRhythmSettingsStore.setMode(context, BgRhythmMode.Calm)
                }
                SoundChip(stringResource(R.string.rhythm_mode_dynamic), rhythmMode == BgRhythmMode.Dynamic, true) {
                    BgRhythmSettingsStore.setMode(context, BgRhythmMode.Dynamic)
                }
            }
            if (onOpenEqualizer != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(top = 16.dp)
                        .clip(styledShape(14.dp))
                        .clickable(role = Role.Button) {
                            onDismiss()
                            onOpenEqualizer()
                        }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Equalizer, null, tint = palette.accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.player_equalizer),
                        color = palette.textPrimary,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Rounded.KeyboardArrowRight, null, tint = palette.textSecondary)
                }
            }
        }
    }
    if (customOpen) SleepCustomDialog(
        onDismiss = { customOpen = false },
        onSet = { minutes ->
            PlaybackEngine.setSleepTimer(minutes)
            customOpen = false
        }
    )
}

@Composable
private fun SoundSectionTitle(text: String) {
    Text(
        text,
        color = beyGramPalette().textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp)
    )
}

@Composable
private fun SoundChip(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    val shape = styledShape(24.dp)
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) palette.accent else Color.Transparent, shape)
            .border(1.dp, palette.divider, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) palette.accentInk else palette.textPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun SleepCustomDialog(onDismiss: () -> Unit, onSet: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val minutes = text.toIntOrNull()
    val valid = minutes != null && minutes in 1..SLEEP_TIMER_MAX_MINUTES
    BeyGramDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.player_sleep_custom_title),
        content = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter { it.isDigit() }.take(3) },
                singleLine = true,
                label = { Text(stringResource(R.string.player_sleep_custom_label, SLEEP_TIMER_MAX_MINUTES)) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )
            )
        },
        confirmText = stringResource(R.string.player_sleep_custom_set),
        confirmEnabled = valid,
        onConfirm = { minutes?.let(onSet) },
        dismissText = stringResource(R.string.common_cancel),
        onDismiss = onDismiss
    )
}
