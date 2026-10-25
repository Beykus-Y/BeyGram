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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.beykus.beygram.data.settings.AppLocale
import com.beykus.beygram.data.media.VideoMessageSettingsStore
import com.beykus.beygram.data.media.VideoNoteDiagnostics
import com.beykus.beygram.data.media.VideoNoteLossDiagnostics
import com.beykus.beygram.data.media.VideoNoteCameraSurvey
import com.beykus.beygram.data.media.VideoNoteFpsCheck
import com.beykus.beygram.data.media.VideoNoteHighSpeedProbe
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
import com.beykus.beygram.security.AppLockManager
import android.content.ClipData
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
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
fun VideoMessageSettingsScreen(store: VideoMessageSettingsStore, repository: TelegramRepository, accountId: String = "legacy", onBack: () -> Unit) {
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
    val lossDiagnostics = remember(repository, accountId) { VideoNoteLossDiagnostics.getInstance(context.applicationContext, repository, accountId) }
    val lossState by lossDiagnostics.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showLossPanel by remember { mutableStateOf(false) }
    var confirmLossSend by remember { mutableStateOf(false) }
    var confirmLossDelete by remember { mutableStateOf(false) }
    var lossDeleteError by remember { mutableStateOf<String?>(null) }
    val settings by store.settings.collectAsStateWithLifecycle()
    var diagnosticsPage by rememberSaveable { mutableStateOf(false) }
    val settingsScroll = rememberScrollState()
    val diagnosticsScroll = rememberScrollState()
    LaunchedEffect(diagnosticsPage) { if (diagnosticsPage) diagnosticsScroll.scrollTo(0) }
    var showFullDiagnostics by remember { mutableStateOf(false) }
    var fullExtended by remember { mutableStateOf(false) }
    var latestSurvey by remember { mutableStateOf<String?>(null) }
    var showFullReport by remember { mutableStateOf(false) }
    var highSpeedReport by remember { mutableStateOf<String?>(null) }
    var highSpeedBusy by remember { mutableStateOf(false) }
    var showHighSpeed by remember { mutableStateOf(false) }
    val runHighSpeed: () -> Unit = {
        if (!highSpeedBusy) {
            highSpeedBusy = true
            showHighSpeed = true
            scope.launch {
                highSpeedReport = try { VideoNoteHighSpeedProbe.run(AppLocale.context(context.applicationContext)) }
                catch (failure: Exception) { context.getString(R.string.camera_report_failed, failure.javaClass.simpleName) }
                finally { highSpeedBusy = false }
            }
        }
    }
    val highSpeedPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runHighSpeed()
    }
    var fullRunActive by remember { mutableStateOf(false) }
    var fullRunStarted by remember { mutableStateOf(false) }
    suspend fun surveyReport(): String {
        cameraSurveyBusy = true
        return try {
            withContext(Dispatchers.IO) { VideoNoteCameraSurvey.report(context.applicationContext, settings) }
        } catch (failure: Exception) {
            context.getString(R.string.camera_report_failed, failure.javaClass.simpleName)
        } finally { cameraSurveyBusy = false }
    }
    val runSurvey: () -> Unit = { scope.launch { val text = surveyReport(); latestSurvey = text; cameraSurvey = text } }
    LaunchedEffect(fpsCheck.running) {
        if (fpsCheck.running) {
            fullRunStarted = true
        } else if (fullRunActive && fullRunStarted && fpsCheck.report.isNotBlank()) {
            fullRunActive = false
            fullRunStarted = false
            showFpsCheck = false
            showFullReport = true
        }
    }
    fun buildFullReport(): String {
        val none = context.getString(R.string.diag_full_report_not_run)
        fun section(title: Int, body: String?) = context.getString(title) + "\n" + (body?.takeIf { it.isNotBlank() } ?: none)
        return listOf(
            section(R.string.diag_full_report_survey, latestSurvey),
            section(R.string.diag_full_report_fps, fpsCheck.report),
            section(R.string.diag_full_report_telegram, lossState.report),
            section(R.string.diag_full_report_highspeed, highSpeedReport),
            section(R.string.diag_full_report_videos, reports.take(5).joinToString("\n\n") { it.asText(context) { st -> videoNoteStatusText(context, st) } })
        ).joinToString("\n\n")
    }
    val goBack = { if (diagnosticsPage) diagnosticsPage = false else onBack() }
    BackHandler(onBack = goBack)
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(
            stringResource(if (diagnosticsPage) R.string.video_diagnostics_center_screen_title else R.string.settings_video_recording_title),
            goBack
        )
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(if (diagnosticsPage) diagnosticsScroll else settingsScroll).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!diagnosticsPage) {
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

            TextButton(
                onClick = { diagnosticsPage = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.video_diagnostics_center_open), fontSize = 15.sp)
            }
            } else {
            Text(
                stringResource(R.string.video_diagnostics_center_intro),
                color = palette.textSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
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
                Text(stringResource(R.string.diag_center_title), color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                DiagnosticsCenterRow("A", R.string.diag_center_a_title, R.string.diag_center_a_detail,
                    if (cameraSurveyBusy) R.string.loss_camera_survey_loading else R.string.diag_center_run, !cameraSurveyBusy) { runSurvey() }
                DiagnosticsCenterRow("B", R.string.diag_center_b_title, R.string.diag_center_b_detail,
                    if (fpsCheck.running) R.string.fps_check_entry_running else R.string.diag_center_run, !highSpeedBusy) { showFpsCheck = true }
                DiagnosticsCenterRow("F", R.string.diag_center_f_title, R.string.diag_center_f_detail, R.string.diag_center_run, true) { showLossPanel = true }
                DiagnosticsCenterRow("G", R.string.diag_center_g_title, R.string.diag_center_g_detail, R.string.diag_center_run, !fpsCheck.running && !cameraSurveyBusy && !highSpeedBusy) { showFullDiagnostics = true }
                DiagnosticsCenterRow("I", R.string.diag_center_i_title, R.string.diag_center_i_detail,
                    if (highSpeedBusy) R.string.fps_check_entry_running else R.string.diag_center_run, !highSpeedBusy && !fpsCheck.running) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) runHighSpeed()
                    else {
                        AppLockManager.get(context).expectExternalActivity()
                        highSpeedPermission.launch(Manifest.permission.CAMERA)
                    }
                }
                DiagnosticsCenterRow("H", R.string.diag_center_h_title, R.string.diag_center_h_detail, R.string.diag_center_open, true) { showFullReport = true }
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
    }

    // Results from asynchronous checks remain available upon returning to the center,
    // but must never open dialogs on top of the everyday recording-settings page.
    if (diagnosticsPage) {
    cameraSurvey?.let { report ->
        AlertDialog(
            onDismissRequest = { cameraSurvey = null },
            title = { Text(stringResource(R.string.loss_camera_survey_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(report, fontSize = 12.sp, lineHeight = 17.sp)
            } },
            confirmButton = { TextButton(onClick = { cameraSurvey = null }) { Text(stringResource(R.string.diagnostics_view_close)) } },
            dismissButton = { DiagnosticsReportActions("BeyGram Camera2 survey", report) }
        )
    }
    if (showHighSpeed) {
        AlertDialog(
            onDismissRequest = { showHighSpeed = false },
            title = { Text(stringResource(R.string.diag_center_i_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                if (highSpeedBusy) Text(stringResource(R.string.hs_running), fontSize = 13.sp)
                else Text(highSpeedReport.orEmpty(), fontSize = 12.sp, lineHeight = 17.sp)
            } },
            confirmButton = { TextButton(onClick = { showHighSpeed = false }) { Text(stringResource(R.string.diagnostics_view_close)) } },
            dismissButton = { if (!highSpeedBusy && !highSpeedReport.isNullOrBlank()) DiagnosticsReportActions("BeyGram high-speed check", highSpeedReport.orEmpty()) }
        )
    }
    if (showFullReport) {
        val fullText = buildFullReport()
        AlertDialog(
            onDismissRequest = { showFullReport = false },
            title = { Text(stringResource(R.string.diag_full_report_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(fullText, fontSize = 12.sp, lineHeight = 17.sp)
            } },
            confirmButton = { TextButton(onClick = { showFullReport = false }) { Text(stringResource(R.string.diagnostics_view_close)) } },
            dismissButton = { DiagnosticsReportActions("BeyGram full diagnostics", fullText) }
        )
    }
    if (showFullDiagnostics) {
        AlertDialog(
            onDismissRequest = { showFullDiagnostics = false },
            title = { Text(stringResource(R.string.diag_center_g_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.diag_full_plan), fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(!fullExtended, onClick = { fullExtended = false }, label = { Text(stringResource(R.string.diag_full_quick)) })
                        FilterChip(fullExtended, onClick = { fullExtended = true }, label = { Text(stringResource(R.string.diag_full_extended)) })
                    }
                    Text(stringResource(if (fullExtended) R.string.diag_full_extended_cost else R.string.diag_full_quick_cost), fontSize = 13.sp)
                    Text(stringResource(R.string.diag_full_consent), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showFullDiagnostics = false
                    fpsCheckScope = if (fullExtended) ProbeScope.ALL else ProbeScope.FRONT
                    scope.launch {
                        latestSurvey = surveyReport()
                        fullRunActive = true
                        fullRunStarted = false
                        showFpsCheck = true
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            VideoNoteFpsCheck.start(context, fpsCheckScope, settings)
                        } else {
                            AppLockManager.get(context).expectExternalActivity()
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    }
                }) { Text(stringResource(R.string.diag_full_start)) }
            },
            dismissButton = { TextButton(onClick = { showFullDiagnostics = false }) { Text(stringResource(R.string.common_cancel)) } }
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
                        DiagnosticsReportActions("BeyGram FPS check", fpsCheck.report)
                    }
                }
            },
            confirmButton = {
                if (fpsCheck.running) {
                    TextButton(onClick = { VideoNoteFpsCheck.cancel() }) { Text(stringResource(R.string.common_cancel)) }
                } else {
                    TextButton(enabled = !highSpeedBusy, onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            VideoNoteFpsCheck.start(context, fpsCheckScope, settings)
                        } else {
                            AppLockManager.get(context).expectExternalActivity()
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
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            VideoNoteSyntheticSource.SUPPORTED_FPS.forEach { value ->
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
                    if (lossState.report.isNotBlank()) DiagnosticsReportActions("BeyGram loss diagnostics", lossState.report)
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
                DiagnosticsReportActions("BeyGram video diagnostics", report.asText(context) { videoNoteStatusText(context, it) })
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
    } // diagnostics-page-only dialogs
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

/** One action of the diagnostics centre: a letter, what it does and what it costs, and a start button if it has one. */
@Composable
private fun DiagnosticsCenterRow(letter: String, title: Int, detail: Int, button: Int?, enabled: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(letter, color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(end = 10.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(stringResource(detail), color = palette.textSecondary, fontSize = 12.sp)
        }
        if (button != null) TextButton(onClick = onClick, enabled = enabled) { Text(stringResource(button)) }
    }
}

private const val REPORT_INLINE_SHARE_LIMIT = 60_000

@Composable
private fun DiagnosticsReportActions(label: String, report: String) {
    val context = LocalContext.current
    Row {
        TextButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            runCatching { clipboard.setPrimaryClip(ClipData.newPlainText(label, report.take(REPORT_INLINE_SHARE_LIMIT * 4))) }
        }) { Text(stringResource(R.string.settings_video_diagnostics_copy)) }
        TextButton(onClick = {
            val sent = runCatching {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, label)
                    if (report.length <= REPORT_INLINE_SHARE_LIMIT) {
                        putExtra(Intent.EXTRA_TEXT, report)
                    } else {
                        // Big reports exceed what the share sheet carries inline; send them as a file.
                        val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
                        dir.listFiles { f -> f.name.startsWith("beygram-report-") }?.forEach { it.delete() }
                        val file = File(dir, "beygram-report-${System.currentTimeMillis()}.txt").apply { writeText(report) }
                        putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, "${context.packageName}.files", file))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.settings_video_diagnostics_share)))
            }.isSuccess
            if (!sent) Toast.makeText(context, R.string.diag_share_failed, Toast.LENGTH_LONG).show()
        }) { Text(stringResource(R.string.settings_video_diagnostics_share)) }
    }
}
