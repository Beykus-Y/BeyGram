package com.beykus.beygram.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.beykus.beygram.R
import com.beykus.beygram.data.media.CameraCaptureMode
import com.beykus.beygram.data.media.cameraRecordingProgress
import com.beykus.beygram.data.media.cameraVideoLimitOptions
import com.beykus.beygram.data.media.cameraVideoLimitSeconds
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import java.io.File

/**
 * Full-screen CameraX capture surface. It only produces a cache file; importing, editing and
 * compression happen in [AttachmentState] so the camera can close immediately after a take.
 */
@Composable
internal fun CameraCaptureScreen(
    onClose: () -> Unit,
    onPhoto: (File) -> Unit,
    onVideo: (File, Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var audioGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val cameraPreviewDescription = stringResource(R.string.camera_preview_description)
    val currentPhoto by rememberUpdatedState(onPhoto)
    val currentVideo by rememberUpdatedState(onVideo)
    val controller = remember(context, lifecycleOwner) {
        CameraCaptureController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            scope = scope,
            onPhoto = { currentPhoto(it) },
            onVideo = { file, hasAudio -> currentVideo(file, hasAudio) },
            onError = { error = it }
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        error = if (granted) null else context.getString(R.string.camera_permission_denied)
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        audioGranted = granted
    }
    LaunchedEffect(Unit) {
        if (!cameraGranted) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(cameraGranted, controller) {
        if (cameraGranted) controller.start()
    }
    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }
    BackHandler {
        controller.close()
        onClose()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraGranted) {
            val zoomIn = stringResource(R.string.camera_zoom_in)
            val zoomOut = stringResource(R.string.camera_zoom_out)
            Box(
                Modifier.fillMaxSize().pointerInput(controller) {
                    detectTransformGestures { _, _, zoom, _ -> controller.zoomBy(zoom) }
                }.semantics {
                    contentDescription = cameraPreviewDescription
                    customActions = listOf(
                        CustomAccessibilityAction(zoomIn) { controller.zoomBy(1.5f); true },
                        CustomAccessibilityAction(zoomOut) { controller.zoomBy(1f / 1.5f); true }
                    )
                }
            ) {
                AndroidView(factory = { controller.previewView }, modifier = Modifier.fillMaxSize())
            }
            CameraChrome(
                controller = controller,
                audioGranted = audioGranted,
                error = error,
                onDismissError = { error = null },
                onRequestAudio = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onClose = { controller.close(); onClose() }
            )
            if (controller.countdown > 0) CountdownOverlay(controller)
        } else {
            CameraPermissionPanel(
                error = error,
                onRequest = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                onClose = onClose
            )
        }
    }
}

@Composable
private fun CameraChrome(
    controller: CameraCaptureController,
    audioGranted: Boolean,
    error: String?,
    onDismissError: () -> Unit,
    onRequestAudio: () -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.camera_close_description), tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            if (controller.mode == CameraCaptureMode.Photo) {
                CameraPill(
                    icon = { tint -> Icon(Icons.Rounded.Timer, null, tint = tint, modifier = Modifier.size(20.dp)) },
                    text = when (controller.timerSeconds) {
                        0 -> stringResource(R.string.camera_timer_off)
                        3 -> stringResource(R.string.camera_timer_three)
                        else -> stringResource(R.string.camera_timer_ten)
                    },
                    description = stringResource(
                        R.string.camera_timer_description,
                        if (controller.timerSeconds == 0) stringResource(R.string.camera_timer_off_value)
                        else stringResource(R.string.camera_timer_seconds_value, controller.timerSeconds)
                    ),
                    onClick = controller::nextTimer
                )
                Spacer(Modifier.width(4.dp))
            }
            IconButton(
                onClick = controller::toggleFlash,
                enabled = controller.flashAvailable,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    if (controller.flashEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                    stringResource(
                        when {
                            !controller.flashAvailable -> R.string.camera_flash_unavailable
                            controller.flashEnabled -> R.string.camera_flash_off
                            else -> R.string.camera_flash_on
                        }
                    ),
                    tint = if (controller.flashAvailable) Color.White else Color.White.copy(alpha = 0.38f)
                )
            }
            if (controller.canSwitchCamera) {
                IconButton(onClick = controller::switchCamera, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Cameraswitch, stringResource(R.string.camera_switch_description), tint = Color.White)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (controller.mode == CameraCaptureMode.Video && !audioGranted) {
                Text(
                    stringResource(R.string.camera_permission_audio_missing),
                    color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            if (controller.recording || controller.startingRecording) {
                val elapsed = (controller.elapsedMs / 1000L).toInt()
                Text(
                    stringResource(R.string.camera_recording_time, duration(elapsed), duration(controller.videoLimitSeconds)),
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.errorAnnouncement()
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { cameraRecordingProgress(controller.videoLimitSeconds, controller.elapsedMs) },
                    modifier = Modifier.width(220.dp).height(4.dp),
                    color = Color(0xFFFF5A5F),
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
                Spacer(Modifier.height(10.dp))
            }
            if (!controller.recording && !controller.startingRecording && controller.mode == CameraCaptureMode.Video) {
                CameraDurationChips(controller)
                Spacer(Modifier.height(8.dp))
            }
            if (!controller.recording && !controller.startingRecording) {
                ModeSwitch(controller, audioGranted, onRequestAudio)
                Spacer(Modifier.height(10.dp))
            }
            if (controller.recording || controller.startingRecording) {
                Text(
                    stringResource(R.string.camera_recording_description),
                    color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
            }
            CameraShutter(controller)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(
                    stringResource(R.string.recorder_zoom_value, controller.zoomRatio),
                    color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.camera_limit_summary),
                    color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(8.dp))
            error?.let { message ->
                Row(
                    Modifier.fillMaxWidth().background(Color(0xCC8A1F1F), RoundedCornerShape(12.dp)).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(message, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f).errorAnnouncement())
                    IconButton(onClick = onDismissError, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionPanel(
    error: String?,
    onRequest: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.camera_permission_title), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.camera_permission_message), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, textAlign = TextAlign.Center)
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFFFB4B4), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.errorAnnouncement())
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text(stringResource(R.string.camera_permission_action)) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClose, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text(stringResource(R.string.common_close), color = Color.White) }
    }
}

@Composable
private fun CountdownOverlay(controller: CameraCaptureController) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(role = Role.Button, onClick = { controller.shutter() }),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(controller.countdown.toString(), color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.camera_timer_cancel), color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
        }
    }
}

@Composable
private fun CameraDurationChips(controller: CameraCaptureController) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        cameraVideoLimitOptions.forEach { seconds ->
            val selected = controller.videoLimitSeconds == cameraVideoLimitSeconds(seconds)
            CameraValueChip(
                text = when (seconds) {
                    15 -> stringResource(R.string.camera_duration_seconds, 15)
                    60 -> stringResource(R.string.camera_duration_minutes, 1)
                    600 -> stringResource(R.string.camera_duration_minutes, 10)
                    else -> stringResource(R.string.camera_duration_minutes, 60)
                },
                selected = selected,
                description = stringResource(R.string.camera_video_limit_description, duration(seconds)),
                onClick = { controller.setVideoLimit(seconds) }
            )
        }
    }
}

@Composable
private fun ModeSwitch(controller: CameraCaptureController, audioGranted: Boolean, onRequestAudio: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        CameraValueChip(
            text = stringResource(R.string.camera_mode_photo),
            selected = controller.mode == CameraCaptureMode.Photo,
            description = stringResource(R.string.camera_mode_photo_description),
            onClick = { controller.selectMode(CameraCaptureMode.Photo) }
        )
        CameraValueChip(
            text = stringResource(R.string.camera_mode_video),
            selected = controller.mode == CameraCaptureMode.Video,
            description = stringResource(R.string.camera_mode_video_description),
            onClick = {
                controller.selectMode(CameraCaptureMode.Video)
                if (!audioGranted) onRequestAudio()
            }
        )
    }
}

@Composable
private fun CameraShutter(controller: CameraCaptureController) {
    val description = when {
        controller.countdown > 0 -> stringResource(R.string.camera_timer_cancel)
        controller.mode == CameraCaptureMode.Photo -> stringResource(R.string.camera_capture_description)
        controller.recording || controller.startingRecording -> stringResource(R.string.camera_record_stop_description)
        else -> stringResource(R.string.camera_record_start_description)
    }
    val recording = controller.recording || controller.startingRecording
    Box(
        Modifier.size(76.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.28f))
            .clickable(enabled = controller.ready || controller.recording, role = Role.Button, onClick = controller::shutter)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(60.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (controller.mode == CameraCaptureMode.Video) {
                Box(
                    Modifier
                        .size(if (recording) 26.dp else 44.dp)
                        .clip(if (recording) RoundedCornerShape(6.dp) else CircleShape)
                        .background(Color(0xFFE53935))
                )
            }
        }
    }
}

@Composable
private fun CameraPill(
    icon: @Composable (Color) -> Unit,
    text: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.defaultMinSize(minHeight = 48.dp, minWidth = 48.dp)
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon(Color.White)
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun CameraValueChip(text: String, selected: Boolean, description: String, onClick: () -> Unit) {
    val background = if (selected) Color.White else Color.Black.copy(alpha = 0.4f)
    val foreground = if (selected) Color.Black else Color.White
    Box(
        Modifier.defaultMinSize(minHeight = 48.dp, minWidth = 48.dp)
            .background(background, RoundedCornerShape(24.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = foreground, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
