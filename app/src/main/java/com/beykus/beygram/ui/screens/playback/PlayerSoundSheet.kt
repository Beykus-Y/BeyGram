package com.beykus.beygram.ui.screens.playback

import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.BackHandler
import com.beykus.beygram.ui.design.styledShape
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import com.beykus.beygram.ui.components.sheetSurfaceColor
import com.beykus.beygram.ui.theme.LocalBeyGramTokens
import com.beykus.beygram.media.bgbeat.BgBeatSection
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.beykus.beygram.ui.components.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.playback.PLAYBACK_SPEED_OPTIONS
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackRepeatMode
import com.beykus.beygram.playback.SLEEP_TIMER_MAX_MINUTES
import com.beykus.beygram.playback.SleepTimerState
import com.beykus.beygram.playback.sleepTimerRemainingMinutes
import com.beykus.beygram.data.music.provider.ProviderTracks
import com.beykus.beygram.ui.screens.music.rememberProviderCover
import com.beykus.beygram.playback.speedLabel
import com.beykus.beygram.ui.accessibility.systemReducedMotion
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.beykus.beygram.data.music.MusicLibraries
import com.beykus.beygram.data.music.provider.YANDEX_TRACK_KEY_PREFIX
import com.beykus.beygram.data.music.MusicOutcome
import com.beykus.beygram.data.music.toPlaylistTrack
import com.beykus.beygram.ui.screens.music.AddToPlaylistSheet
import com.beykus.beygram.ui.screens.music.containsTrack
import com.beykus.beygram.ui.screens.music.rememberMusicLibrary
import com.beykus.beygram.ui.screens.music.reportMusicOutcome
import com.beykus.beygram.ui.screens.profile.LocalAddProfileAudio
import com.beykus.beygram.ui.screens.profile.PROFILE_AUDIO_KEY_PREFIX
import com.beykus.beygram.ui.text.userMessage
import com.beykus.beygram.ui.screens.music.PlayerProviderTrackMenuItems
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.KeyboardArrowRight

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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sleepTimer by PlaybackEngine.sleepTimer.collectAsStateWithLifecycle()
    val minutesRemaining = rememberSleepMinutes(sleepTimer)
    var customOpen by remember { mutableStateOf(false) }
    // BG Mix has no playback speed, so the chips stay off while it plays instead of changing only the saved value.
    val speedEnabled = !player.bgMix
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = palette.surface) {
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
                SoundChip(stringResource(R.string.player_sleep_until_track_end), false, true) {
                    PlaybackEngine.setSleepTimerUntilTrackEnd()
                }
                SoundChip(stringResource(R.string.player_sleep_custom), false, true) { customOpen = true }
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
    val palette = beyGramPalette()
    var text by remember { mutableStateOf("") }
    val minutes = text.toIntOrNull()
    val valid = minutes != null && minutes in 1..SLEEP_TIMER_MAX_MINUTES
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text(stringResource(R.string.player_sleep_custom_title), color = palette.textPrimary) },
        text = {
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
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = valid,
                onClick = { minutes?.let(onSet) }
            ) { Text(stringResource(R.string.player_sleep_custom_set), color = palette.accentInk) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = palette.textSecondary)
            }
        }
    )
}
