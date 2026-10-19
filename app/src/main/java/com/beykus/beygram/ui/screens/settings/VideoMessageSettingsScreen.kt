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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.media.VideoMessageSettingsStore
import com.beykus.beygram.data.media.VideoNoteDiagnostics
import com.beykus.beygram.data.media.VideoNoteDiagnosticReport
import com.beykus.beygram.data.media.VideoNoteStatus
import com.beykus.beygram.data.media.VideoFrameRatePriority
import com.beykus.beygram.data.media.VideoQualityPreset
import com.beykus.beygram.data.media.videoMirrorToggleSupported
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import java.text.DateFormat
import java.util.Date

@Composable
fun VideoMessageSettingsScreen(store: VideoMessageSettingsStore, onBack: () -> Unit) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val diagnostics = remember(context) { VideoNoteDiagnostics.also { it.initialize(context) } }
    val reports by diagnostics.reports.collectAsStateWithLifecycle()
    var selectedReport by remember { mutableStateOf<VideoNoteDiagnosticReport?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val settings by store.settings.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.settings_video_recording_title), onBack)
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_autosave_note), color = palette.textSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp))
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

            if (settings.preset == VideoQualityPreset.Maximum || settings.preset == VideoQualityPreset.Custom) {
                val fpsLimit = if (settings.preset == VideoQualityPreset.Maximum) 60 else settings.customFps
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_video_fps_priority), color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    FpsPriorityRow(VideoFrameRatePriority.MaximumQuality, settings.frameRatePriority,
                        stringResource(R.string.settings_video_fps_priority_quality), fpsLimit
                    ) { store.updateSettings { it.copy(frameRatePriority = VideoFrameRatePriority.MaximumQuality) } }
                    FpsPriorityRow(VideoFrameRatePriority.Balanced, settings.frameRatePriority,
                        stringResource(R.string.settings_video_fps_priority_balanced), fpsLimit
                    ) { store.updateSettings { it.copy(frameRatePriority = VideoFrameRatePriority.Balanced) } }
                    FpsPriorityRow(VideoFrameRatePriority.MaximumSmoothness, settings.frameRatePriority,
                        stringResource(R.string.settings_video_fps_priority_smoothness), fpsLimit
                    ) { store.updateSettings { it.copy(frameRatePriority = VideoFrameRatePriority.MaximumSmoothness) } }
                    Text(stringResource(R.string.settings_video_fps_priority_note), color = palette.textSecondary, fontSize = 13.sp)
                }
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

            // До API 33 CameraX не отражает превью по setMirrorMode, поэтому переключатель там не нужен.
            if (videoMirrorToggleSupported()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(16.dp)) {
                    Row(Modifier.fillMaxWidth().toggleable(settings.mirrorFrontCamera, role = Role.Switch) { checked ->
                        store.updateSettings { current -> current.copy(mirrorFrontCamera = checked) }
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(stringResource(R.string.settings_video_mirror_front), color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(stringResource(R.string.settings_video_mirror_front_detail), color = palette.textSecondary, fontSize = 13.sp)
                        }
                        Switch(checked = settings.mirrorFrontCamera, onCheckedChange = null)
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().toggleable(settings.diagnosticsEnabled, role = Role.Switch) { enabled ->
                        store.updateSettings { it.copy(diagnosticsEnabled = enabled) }
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(stringResource(R.string.settings_video_diagnostics_title), color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(stringResource(R.string.settings_video_diagnostics_hint), color = palette.textSecondary, fontSize = 13.sp)
                    }
                    Switch(checked = settings.diagnosticsEnabled, onCheckedChange = null)
                }
                Text(stringResource(R.string.settings_video_diagnostics_privacy), color = palette.textSecondary, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_video_diagnostics_history, reports.size),
                        color = palette.textPrimary, modifier = Modifier.weight(1f), fontSize = 14.sp)
                    if (reports.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) {
                            Text(stringResource(R.string.settings_video_diagnostics_clear))
                        }
                    }
                }
                if (reports.isEmpty()) {
                    Text(stringResource(R.string.settings_video_diagnostics_empty), color = palette.textSecondary, fontSize = 13.sp)
                }
                reports.forEach { report ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { selectedReport = report }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(report.startedAt)),
                                color = palette.textPrimary, fontSize = 14.sp)
                            Text(videoNoteStatusLabel(report.status) + " · " + stringResource(R.string.settings_video_diagnostics_raw_final,
                                report.rawFps?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "—",
                                report.outputFps?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "—"),
                                color = palette.textSecondary, fontSize = 12.sp)
                        }
                        Text(stringResource(R.string.settings_video_diagnostics_open), color = palette.accent, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    selectedReport?.let { report ->
        AlertDialog(
            onDismissRequest = { selectedReport = null },
            title = { Text(stringResource(R.string.settings_video_diagnostics_report)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(report.asText(), fontSize = 12.sp, lineHeight = 17.sp)
            } },
            confirmButton = {
                TextButton(onClick = { selectedReport = null }) { Text(stringResource(R.string.diagnostics_view_close)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("BeyGram video diagnostics", report.asText()))
                    }) { Text(stringResource(R.string.settings_video_diagnostics_copy)) }
                    TextButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.settings_video_diagnostics_report))
                            putExtra(Intent.EXTRA_TEXT, report.asText())
                        }
                        context.startActivity(Intent.createChooser(share, context.getString(R.string.settings_video_diagnostics_share)))
                    }) { Text(stringResource(R.string.settings_video_diagnostics_share)) }
                }
            }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_video_diagnostics_clear)) },
            text = { Text(stringResource(R.string.settings_video_diagnostics_clear_confirm)) },
            confirmButton = { TextButton(onClick = { diagnostics.clear(); confirmClear = false; selectedReport = null }) {
                Text(stringResource(R.string.settings_video_diagnostics_clear))
            } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) {
                Text(stringResource(R.string.settings_video_diagnostics_cancel))
            } }
        )
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
private fun FpsPriorityRow(value: VideoFrameRatePriority, selected: VideoFrameRatePriority, title: String, maxFps: Int, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected == value, onClick = onClick)
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 14.sp)
            Text(stringResource(R.string.settings_video_fps_priority_range, value.minimumFps.coerceAtMost(maxFps), maxFps),
                color = palette.textSecondary, fontSize = 12.sp)
        }
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

@Composable
private fun videoNoteStatusLabel(status: String): String = when (status) {
    VideoNoteStatus.QUEUED -> stringResource(R.string.settings_video_diagnostics_status_queued)
    VideoNoteStatus.PREVIEW -> stringResource(R.string.settings_video_diagnostics_status_preview)
    VideoNoteStatus.READY -> stringResource(R.string.settings_video_diagnostics_status_ready)
    VideoNoteStatus.PROCESSING_FAILED -> stringResource(R.string.settings_video_diagnostics_status_processing_failed)
    VideoNoteStatus.SENDING -> stringResource(R.string.settings_video_diagnostics_status_sending)
    VideoNoteStatus.SENT -> stringResource(R.string.settings_video_diagnostics_status_sent)
    VideoNoteStatus.SEND_FAILED -> stringResource(R.string.settings_video_diagnostics_status_send_failed)
    VideoNoteStatus.CANCELLED -> stringResource(R.string.settings_video_diagnostics_status_cancelled)
    else -> status // Reports saved by an earlier build keep the text they were written with.
}
