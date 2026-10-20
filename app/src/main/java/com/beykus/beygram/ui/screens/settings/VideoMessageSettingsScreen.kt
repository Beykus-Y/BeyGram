package com.beykus.beygram.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import com.beykus.beygram.data.media.VideoNoteLossDiagnostics
import com.beykus.beygram.data.media.VideoNoteCameraSurvey
import com.beykus.beygram.data.media.VideoNoteFpsCheck
import com.beykus.beygram.data.media.VideoNoteSyntheticSource
import com.beykus.beygram.data.media.ProbeScope
import com.beykus.beygram.domain.repository.TelegramRepository
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.text.DateFormat
import java.util.Date

@Composable
fun VideoMessageSettingsScreen(store: VideoMessageSettingsStore, repository: TelegramRepository, onBack: () -> Unit) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val diagnostics = remember(context) { VideoNoteDiagnostics.also { it.initialize(context) } }
    val reports by diagnostics.reports.collectAsStateWithLifecycle()
    var selectedReport by remember { mutableStateOf<VideoNoteDiagnosticReport?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var cameraSurvey by remember { mutableStateOf<String?>(null) }
    var cameraSurveyBusy by remember { mutableStateOf(false) }
    val fpsCheck by VideoNoteFpsCheck.state.collectAsStateWithLifecycle()
    var showFpsCheck by remember { mutableStateOf(false) }
    var fpsCheckScope by remember { mutableStateOf(ProbeScope.FRONT) }
    var lossFps by remember { mutableStateOf(VideoNoteSyntheticSource.FPS_30) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) VideoNoteFpsCheck.start(context, fpsCheckScope, store.settings.value)
    }
    val lossDiagnostics = remember(repository) { VideoNoteLossDiagnostics.getInstance(context.applicationContext, repository) }
    val lossState by lossDiagnostics.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showLossPanel by remember { mutableStateOf(false) }
    var confirmLossSend by remember { mutableStateOf(false) }
    var confirmLossDelete by remember { mutableStateOf(false) }
    var lossDeleteError by remember { mutableStateOf<String?>(null) }
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
                TextButton(onClick = {
                    scope.launch {
                        cameraSurveyBusy = true
                        cameraSurvey = try {
                            withContext(Dispatchers.IO) { VideoNoteCameraSurvey.report(context.applicationContext, settings) }
                        } catch (failure: Exception) {
                            context.getString(R.string.camera_report_failed, failure.javaClass.simpleName)
                        } finally { cameraSurveyBusy = false }
                    }
                }, enabled = !cameraSurveyBusy) {
                    Text(stringResource(if (cameraSurveyBusy) R.string.loss_camera_survey_loading else R.string.loss_camera_survey_start))
                }
                TextButton(onClick = { showFpsCheck = true }) {
                    Text(stringResource(if (fpsCheck.running) R.string.fps_check_entry_running else R.string.fps_check_entry))
                }
                TextButton(onClick = { showLossPanel = true }) {
                    Text(stringResource(R.string.loss_entry))
                }
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
                            Text(videoNoteStatusText(context, report.status) + " · " + stringResource(R.string.settings_video_diagnostics_raw_final,
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

    cameraSurvey?.let { report ->
        AlertDialog(
            onDismissRequest = { cameraSurvey = null },
            title = { Text(stringResource(R.string.loss_camera_survey_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(report, fontSize = 12.sp, lineHeight = 17.sp)
            } },
            confirmButton = { TextButton(onClick = { cameraSurvey = null }) { Text(stringResource(R.string.diagnostics_view_close)) } },
            dismissButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("BeyGram Camera2 survey", report))
                }) { Text(stringResource(R.string.settings_video_diagnostics_copy)) }
            }
        )
    }
    if (showFpsCheck) {
        AlertDialog(
            onDismissRequest = { showFpsCheck = false },
            title = { Text(stringResource(R.string.fps_check_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.fps_check_description), fontSize = 13.sp)
                    if (!fpsCheck.running) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                ProbeScope.FRONT to R.string.fps_check_scope_front,
                                ProbeScope.BACK to R.string.fps_check_scope_back,
                                ProbeScope.ALL to R.string.fps_check_scope_all
                            ).forEach { (value, label) ->
                                FilterChip(fpsCheckScope == value, onClick = { fpsCheckScope = value }, label = { Text(stringResource(label)) })
                            }
                        }
                    } else {
                        Text(stringResource(R.string.fps_check_progress, fpsCheck.done, fpsCheck.total), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.fps_check_running_hint), fontSize = 13.sp)
                    }
                    if (fpsCheck.report.isNotBlank()) {
                        Text(fpsCheck.report, fontSize = 12.sp, lineHeight = 17.sp)
                        Row {
                            TextButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("BeyGram FPS check", fpsCheck.report))
                            }) { Text(stringResource(R.string.settings_video_diagnostics_copy)) }
                            TextButton(onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, fpsCheck.report)
                                }
                                context.startActivity(Intent.createChooser(intent, context.getString(R.string.settings_video_diagnostics_share)))
                            }) { Text(stringResource(R.string.settings_video_diagnostics_share)) }
                        }
                    }
                }
            },
            confirmButton = {
                if (fpsCheck.running) {
                    TextButton(onClick = { VideoNoteFpsCheck.cancel() }) { Text(stringResource(R.string.common_cancel)) }
                } else {
                    TextButton(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            VideoNoteFpsCheck.start(context, fpsCheckScope, settings)
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    }) { Text(stringResource(R.string.fps_check_start)) }
                }
            },
            dismissButton = { TextButton(onClick = { showFpsCheck = false }) { Text(stringResource(R.string.common_close)) } }
        )
    }
    if (showLossPanel && !confirmLossSend && !confirmLossDelete) {
        AlertDialog(
            onDismissRequest = { showLossPanel = false },
            title = { Text(stringResource(R.string.loss_dialog_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.loss_dialog_description), fontSize = 13.sp)
                    if (!lossState.running && lossState.sentMessageId == null) {
                        Text(stringResource(R.string.loss_fps_choice), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(VideoNoteSyntheticSource.FPS_30, VideoNoteSyntheticSource.FPS_60).forEach { value ->
                                FilterChip(lossFps == value, onClick = { lossFps = value },
                                    label = { Text(stringResource(R.string.loss_fps_option, value, value * VideoNoteSyntheticSource.SECONDS)) })
                            }
                        }
                    }
                    Text(stringResource(R.string.loss_status, stringResource(lossState.phaseRes)), fontWeight = FontWeight.SemiBold)
                    if (lossState.running) {
                        // A send already accepted by Telegram cannot be recalled by
                        // canceling this screen. Keep the process-owned check running.
                        Text(stringResource(R.string.loss_running_background), fontSize = 13.sp)
                    }
                    if (lossState.report.isNotBlank()) Text(lossState.report, fontSize = 12.sp, lineHeight = 17.sp)
                    if (lossState.report.isNotBlank()) Row {
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("BeyGram loss diagnostics", lossState.report))
                        }) { Text(stringResource(R.string.settings_video_diagnostics_copy)) }
                        TextButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, lossState.report)
                            }
                            context.startActivity(Intent.createChooser(intent, context.getString(R.string.settings_video_diagnostics_share)))
                        }) { Text(stringResource(R.string.settings_video_diagnostics_share)) }
                    }
                    if (lossState.sentMessageId != null && !lossState.running) {
                        Text(stringResource(R.string.loss_previous_test_hint), fontSize = 13.sp)
                    }
                    if (lossState.sentMessageId != null && !lossState.running) {
                        TextButton(onClick = { confirmLossDelete = true }) {
                            Text(stringResource(R.string.loss_delete_test))
                        }
                    }
                    if (lossState.localDirectory != null && !lossState.running) {
                        TextButton(onClick = { lossDiagnostics.clearLocalFiles() }) {
                            Text(stringResource(R.string.loss_clear_local))
                        }
                    }
                    lossDeleteError?.let { Text(it, color = palette.danger, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmLossSend = true }, enabled = !lossState.running && lossState.sentMessageId == null) {
                    Text(stringResource(R.string.loss_start_test))
                }
            },
            dismissButton = { TextButton(onClick = { showLossPanel = false }) { Text(stringResource(R.string.common_close)) } }
        )
    }
    if (confirmLossSend) {
        AlertDialog(
            onDismissRequest = { confirmLossSend = false },
            title = { Text(stringResource(R.string.loss_send_confirm_title)) },
            text = { Text(stringResource(R.string.loss_send_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLossSend = false
                    lossDeleteError = null
                    lossDiagnostics.start(lossFps)
                }) { Text(stringResource(R.string.loss_send_confirm_button)) }
            },
            dismissButton = { TextButton(onClick = { confirmLossSend = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
    if (confirmLossDelete) {
        AlertDialog(
            onDismissRequest = { confirmLossDelete = false },
            title = { Text(stringResource(R.string.loss_delete_confirm_title)) },
            text = { Text(stringResource(R.string.loss_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLossDelete = false
                    scope.launch {
                        lossDiagnostics.deleteTestMessage()
                            .onFailure { lossDeleteError = context.getString(R.string.loss_delete_failed, it.javaClass.simpleName) }
                    }
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmLossDelete = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
    selectedReport?.let { report ->
        AlertDialog(
            onDismissRequest = { selectedReport = null },
            title = { Text(stringResource(R.string.settings_video_diagnostics_report)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(report.asText(context) { videoNoteStatusText(context, it) }, fontSize = 12.sp, lineHeight = 17.sp)
            } },
            confirmButton = {
                TextButton(onClick = { selectedReport = null }) { Text(stringResource(R.string.diagnostics_view_close)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("BeyGram video diagnostics", report.asText(context) { videoNoteStatusText(context, it) }))
                    }) { Text(stringResource(R.string.settings_video_diagnostics_copy)) }
                    TextButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.settings_video_diagnostics_report))
                            putExtra(Intent.EXTRA_TEXT, report.asText(context) { videoNoteStatusText(context, it) })
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

private fun videoNoteStatusText(context: Context, status: String): String = when (status) {
    VideoNoteStatus.RECORDING -> context.getString(R.string.settings_video_diagnostics_status_recording)
    VideoNoteStatus.PROCESSING -> context.getString(R.string.settings_video_diagnostics_status_processing)
    VideoNoteStatus.RECORDING_FAILED -> context.getString(R.string.settings_video_diagnostics_status_recording_failed)
    VideoNoteStatus.DISCARDED -> context.getString(R.string.settings_video_diagnostics_status_discarded)
    VideoNoteStatus.QUEUED -> context.getString(R.string.settings_video_diagnostics_status_queued)
    VideoNoteStatus.PREVIEW -> context.getString(R.string.settings_video_diagnostics_status_preview)
    VideoNoteStatus.READY -> context.getString(R.string.settings_video_diagnostics_status_ready)
    VideoNoteStatus.PROCESSING_FAILED -> context.getString(R.string.settings_video_diagnostics_status_processing_failed)
    VideoNoteStatus.SENDING -> context.getString(R.string.settings_video_diagnostics_status_sending)
    VideoNoteStatus.SENT -> context.getString(R.string.settings_video_diagnostics_status_sent)
    VideoNoteStatus.SEND_FAILED -> context.getString(R.string.settings_video_diagnostics_status_send_failed)
    VideoNoteStatus.CANCELLED -> context.getString(R.string.settings_video_diagnostics_status_cancelled)
    else -> status // Reports saved by an earlier build keep the text they were written with.
}
