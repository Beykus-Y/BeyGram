package com.beykus.beygram.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.media.VideoMessageSettingsStore
import com.beykus.beygram.data.media.VideoProcessingMode
import com.beykus.beygram.data.media.VideoQualityPreset
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar

@Composable
fun VideoMessageSettingsScreen(store: VideoMessageSettingsStore, onBack: () -> Unit) {
    val palette = beyGramPalette()
    val settings by store.settings.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.settings_video_recording_title), onBack)
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(vertical = 4.dp)) {
                Text(stringResource(R.string.settings_video_quality), color = palette.textSecondary, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                PresetRow(VideoQualityPreset.Standard, settings.preset, stringResource(R.string.settings_video_standard), stringResource(R.string.settings_video_standard_detail)) { store.updateSettings { it.copy(preset = VideoQualityPreset.Standard) } }
                PresetRow(VideoQualityPreset.High, settings.preset, stringResource(R.string.settings_video_high), stringResource(R.string.settings_video_high_detail)) { store.updateSettings { it.copy(preset = VideoQualityPreset.High) } }
                PresetRow(VideoQualityPreset.Maximum, settings.preset, stringResource(R.string.settings_video_maximum), stringResource(R.string.settings_video_maximum_detail)) { store.updateSettings { it.copy(preset = VideoQualityPreset.Maximum) } }
                PresetRow(VideoQualityPreset.Custom, settings.preset, stringResource(R.string.settings_video_custom), stringResource(R.string.settings_video_custom_detail)) { store.updateSettings { it.copy(preset = VideoQualityPreset.Custom) } }
            }

            if (settings.preset == VideoQualityPreset.Custom) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceSection(stringResource(R.string.settings_video_resolution), listOf(384, 480, 640), settings.customSide,
                        { stringResource(R.string.settings_video_side, it) }) { value -> store.updateSettings { it.copy(customSide = value) } }
                    ChoiceSection(stringResource(R.string.settings_video_fps), listOf(24, 30, 60), settings.customFps,
                        { stringResource(R.string.settings_video_fps_value, it) }) { value -> store.updateSettings { it.copy(customFps = value) } }
                    ChoiceSection(stringResource(R.string.settings_video_bitrate), listOf(1000, 1500, 2000, 2500), settings.customVideoBitrateKbps,
                        { stringResource(R.string.settings_video_kbps, it) }) { value -> store.updateSettings { it.copy(customVideoBitrateKbps = value) } }
                    ChoiceSection(stringResource(R.string.settings_video_audio_bitrate), listOf(64, 96, 128), settings.customAudioBitrateKbps,
                        { stringResource(R.string.settings_video_kbps, it) }) { value -> store.updateSettings { it.copy(customAudioBitrateKbps = value) } }
                    Text(stringResource(R.string.settings_video_capability_note), color = palette.textSecondary, fontSize = 13.sp)
                }
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_video_processing), color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                ProcessingRow(VideoProcessingMode.Automatic, settings.processing, stringResource(R.string.settings_video_processing_auto)) { store.updateSettings { it.copy(processing = VideoProcessingMode.Automatic) } }
                ProcessingRow(VideoProcessingMode.Direct, settings.processing, stringResource(R.string.settings_video_processing_direct)) { store.updateSettings { it.copy(processing = VideoProcessingMode.Direct) } }
                ProcessingRow(VideoProcessingMode.Transcode, settings.processing, stringResource(R.string.settings_video_processing_transcode)) { store.updateSettings { it.copy(processing = VideoProcessingMode.Transcode) } }
                Text(stringResource(R.string.settings_video_processing_note), color = palette.textSecondary, fontSize = 13.sp)
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(16.dp)) {
                Row(Modifier.fillMaxWidth().toggleable(settings.longRecordingEnabled, role = Role.Switch) { checked ->
                    store.updateSettings { current -> current.copy(longRecordingEnabled = checked) }
                }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(stringResource(R.string.settings_video_long_recording), color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(stringResource(R.string.settings_video_long_recording_detail), color = palette.textSecondary, fontSize = 13.sp)
                    }
                    Switch(checked = settings.longRecordingEnabled, onCheckedChange = null)
                }
            }
        }
    }
}

@Composable
private fun PresetRow(value: VideoQualityPreset, selected: VideoQualityPreset, title: String, detail: String, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected == value, onClick = onClick)
        Column(Modifier.padding(start = 6.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = palette.textSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ProcessingRow(value: VideoProcessingMode, selected: VideoProcessingMode, label: String, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected == value, onClick = onClick)
        Text(label, color = palette.textPrimary, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun ChoiceSection(title: String, values: List<Int>, selected: Int, label: @Composable (Int) -> String, onSelected: (Int) -> Unit) {
    val palette = beyGramPalette()
    Column {
        Text(title, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            values.forEach { value -> FilterChip(selected == value, onClick = { onSelected(value) }, label = { Text(label(value)) }) }
        }
    }
}
