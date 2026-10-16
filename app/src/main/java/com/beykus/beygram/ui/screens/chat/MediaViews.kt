package com.beykus.beygram.ui.screens.chat

import android.content.Context
import android.content.Intent
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.widget.ImageView
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

internal fun MediaKind.label(): String = when (this) {
    MediaKind.Photo -> "Фото"
    MediaKind.Video -> "Видео"
    MediaKind.Animation -> "GIF"
    MediaKind.Sticker -> "Стикер"
    MediaKind.Audio -> "Аудио"
    MediaKind.Document -> "Файл"
}

internal fun formatMediaSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f МБ", bytes / 1048576.0)
    bytes > 0 -> "${(bytes / 1024).coerceAtLeast(1)} КБ"
    else -> ""
}

private fun duration(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

@Composable
internal fun LocalMediaImage(path: String, modifier: Modifier = Modifier, fit: Boolean = false, maxDimension: Int = 1024) {
    var failed by remember(path) { mutableStateOf(false) }
    val drawable by produceState<Drawable?>(null, path, maxDimension) {
        val decoded = withContext(Dispatchers.IO) { runCatching {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > maxDimension) decoder.setTargetSize((info.size.width.toLong() * maxDimension / longest).toInt().coerceAtLeast(1), (info.size.height.toLong() * maxDimension / longest).toInt().coerceAtLeast(1))
            }
        } }
        failed = decoded.isFailure
        value = decoded.getOrNull()
    }
    DisposableEffect(drawable) {
        (drawable as? AnimatedImageDrawable)?.start()
        onDispose { (drawable as? AnimatedImageDrawable)?.stop() }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            drawable != null -> AndroidView(
                factory = { ImageView(it).apply { contentDescription = "Изображение" } },
                update = { view ->
                    view.scaleType = if (fit) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
                    if (view.drawable !== drawable) view.setImageDrawable(drawable)
                }, modifier = Modifier.fillMaxSize()
            )
            failed -> Text("Не удалось прочитать изображение", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            else -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
internal fun MediaPreview(
    media: MessageMedia,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val file by remember(media.fileId) { observeFile(media.fileId) }.collectAsStateWithLifecycle()
    val thumbId = media.thumbnailFileId ?: media.fileId
    val thumb by remember(thumbId) { observeFile(thumbId) }.collectAsStateWithLifecycle()
    var revealed by remember(media.fileId) { mutableStateOf(!media.hasSpoiler) }
    val palette = beyGramPalette()
    val isVisual = media.kind in listOf(MediaKind.Photo, MediaKind.Video, MediaKind.Animation, MediaKind.Sticker)
    val imageFile = if (media.mimeType.startsWith("image/")) file.localPath ?: thumb.localPath else if (thumbId != media.fileId) thumb.localPath else null
    LaunchedEffect(thumbId, revealed) {
        if (revealed && isVisual && (thumbId != media.fileId || !media.isSecret && media.mimeType.startsWith("image/") && file.size <= 300_000)) downloadFile(thumbId)
    }
    if (isVisual) {
        val ratio = if (media.width > 0 && media.height > 0) media.width.toFloat() / media.height else 1.5f
        Box(
            Modifier.width(244.dp).height((244 / ratio).coerceIn(120f, 280f).dp)
                .background(palette.disabled)
                .combinedClickable(onClick = { if (!revealed) revealed = true else onOpen() }, onLongClick = onLongPress),
            contentAlignment = Alignment.Center
        ) {
            if (revealed && imageFile != null) LocalMediaImage(imageFile, Modifier.fillMaxSize(), fit = media.kind == MediaKind.Sticker)
            if (!revealed) Text("Спойлер · нажмите, чтобы открыть", color = palette.textPrimary, modifier = Modifier.padding(16.dp))
            else if (imageFile == null || media.kind == MediaKind.Video || media.kind == MediaKind.Animation || media.mimeType.startsWith("video/")) {
                val isVideo = media.kind == MediaKind.Video || media.kind == MediaKind.Animation || media.mimeType.startsWith("video/")
                Icon(if (isVideo) Icons.Rounded.PlayArrow else Icons.Rounded.Download, contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(52.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape).padding(10.dp))
            }
            if (revealed) Text(
                media.kind.label() + if (media.durationSeconds > 0) " · ${duration(media.durationSeconds)}" else "",
                color = Color.White, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp)
            )
            if (file.isDownloading || file.isUploading) LinearProgressIndicator(
                progress = { if (file.isUploading) file.uploadProgress else file.downloadProgress },
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            )
        }
    } else {
        Row(Modifier.width(244.dp).clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = onOpen, onLongClick = onLongPress).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (media.kind == MediaKind.Audio) Icons.Rounded.MusicNote else Icons.Rounded.InsertDriveFile, contentDescription = null, tint = palette.accentInk,
                modifier = Modifier.size(44.dp).background(palette.accentSoft, CircleShape).padding(10.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (media.kind == MediaKind.Audio) media.fileName.ifBlank { "Аудио" } else media.fileName.ifBlank { "Файл" }, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(formatMediaSize(file.size) + if (media.durationSeconds > 0) " · ${duration(media.durationSeconds)}" else "", color = palette.textSecondary, fontSize = 13.sp)
                if (file.isDownloading || file.isUploading) LinearProgressIndicator(progress = { if (file.isUploading) file.uploadProgress else file.downloadProgress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
    }
}

@Composable
internal fun MediaViewer(media: MessageMedia, observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit, onViewed: () -> Unit, onClose: () -> Unit) {
    val file by remember(media.fileId) { observeFile(media.fileId) }.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var externalError by remember { mutableStateOf<String?>(null) }
    val tgs = media.mimeType == "application/x-tgsticker"
    val previewId = media.thumbnailFileId
    val currentOnViewed by rememberUpdatedState(onViewed)
    fun openExternal(path: String) {
        externalError = openExternalMedia(context, path, media.mimeType)
        if (externalError == null) currentOnViewed()
    }
    LaunchedEffect(media.fileId, file.localPath) {
        if (file.localPath != null && media.mimeType.startsWith("image/")) currentOnViewed()
    }
    LaunchedEffect(media.fileId) { if (!tgs) downloadFile(media.fileId) else previewId?.let(downloadFile) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, securePolicy = if (media.isSecret) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF070B10)).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(media.fileName.ifBlank { media.kind.label() }, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(8.dp))
                TextButton(onClick = onClose) { Text("Закрыть", color = Color.White) }
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val path = file.localPath
                when {
                    tgs -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (previewId != null) {
                            val preview by remember(previewId) { observeFile(previewId) }.collectAsStateWithLifecycle()
                            preview.localPath?.let { LocalMediaImage(it, Modifier.size(240.dp), fit = true) }
                        }
                        Text("Анимированный стикер: доступно превью", color = Color.LightGray, modifier = Modifier.padding(16.dp))
                    }
                    path == null -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        if (file.isDownloading) CircularProgressIndicator()
                        Text(file.error ?: "Загрузка ${formatMediaSize(file.downloadedBytes)} / ${formatMediaSize(file.size)}", color = Color.LightGray, modifier = Modifier.padding(16.dp))
                        if (!file.isDownloading) TextButton(onClick = { downloadFile(media.fileId) }) { Text("Загрузить") }
                    }
                    media.mimeType.startsWith("image/") -> ZoomableImage(path)
                    media.kind == MediaKind.Video || media.kind == MediaKind.Animation || media.mimeType.startsWith("video/") -> LocalVideo(path, media.kind == MediaKind.Animation, onViewed)
                    media.kind == MediaKind.Audio -> LocalAudio(path, onViewed)
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatMediaSize(file.size), color = Color.LightGray)
                        TextButton(onClick = { openExternal(path) }) { Text("Открыть файл") }
                    }
                }
            }
            if (file.localPath != null && !media.isSecret) TextButton(onClick = { openExternal(file.localPath!!) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Открыть в другом приложении") }
            externalError?.let { Text(it, color = Color(0xFFFFBE9D), modifier = Modifier.padding(16.dp)) }
        }
    }
}

@Composable
private fun ZoomableImage(path: String) {
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = Offset((offset.x + pan.x).coerceIn(-size.width * (scale - 1) / 2, size.width * (scale - 1) / 2), (offset.y + pan.y).coerceIn(-size.height * (scale - 1) / 2, size.height * (scale - 1) / 2))
    }
    Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { size = it }.transformable(transform).pointerInput(path) {
        detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero })
    }) {
        LocalMediaImage(path, Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }, fit = true, maxDimension = 4096)
    }
}

@Composable
private fun LocalVideo(path: String, loop: Boolean, onStarted: () -> Unit) {
    val context = LocalContext.current
    var error by remember(path) { mutableStateOf(false) }
    val currentOnStarted by rememberUpdatedState(onStarted)
    val video = remember(path) { object : VideoView(context) {
        override fun start() { super.start(); if (runCatching { isPlaying }.getOrDefault(false)) currentOnStarted() }
    } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(video, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) video.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(video) {
        video.setMediaController(MediaController(context).apply { setAnchorView(video) })
        video.setOnPreparedListener { it.isLooping = loop; if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) video.start() }
        video.setOnErrorListener { _, _, _ -> error = true; true }
        video.setVideoPath(path)
        onDispose { video.stopPlayback() }
    }
    if (error) Text("Формат видео не поддерживается устройством. Откройте его в другом приложении.", color = Color.LightGray, modifier = Modifier.padding(24.dp))
    else AndroidView(factory = { video }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun LocalAudio(path: String, onStarted: () -> Unit) {
    val player = remember(path) { MediaPlayer() }
    var ready by remember(path) { mutableStateOf(false) }
    var playing by remember(path) { mutableStateOf(false) }
    var position by remember(path) { mutableIntStateOf(0) }
    var total by remember(path) { mutableIntStateOf(0) }
    var error by remember(path) { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { runCatching { if (player.isPlaying) player.pause() }; playing = false }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(player) {
        try {
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            player.setDataSource(path)
            player.setOnPreparedListener { ready = true; total = it.duration }
            player.setOnCompletionListener { playing = false; position = 0 }
            player.setOnErrorListener { _, _, _ -> error = true; playing = false; true }
            player.prepareAsync()
        } catch (_: Exception) { error = true }
        onDispose { player.release() }
    }
    LaunchedEffect(playing) { while (playing) { position = runCatching { player.currentPosition }.getOrDefault(0); delay(250) } }
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("♫", color = Color.White, fontSize = 56.sp)
        if (error) Text("Не удалось воспроизвести аудио", color = Color.LightGray)
        else {
            Slider(value = position.toFloat().coerceIn(0f, total.coerceAtLeast(1).toFloat()), onValueChange = { position = it.toInt(); player.seekTo(position) }, valueRange = 0f..total.coerceAtLeast(1).toFloat(), enabled = ready)
            Text("${duration(position / 1000)} / ${duration(total / 1000)}", color = Color.White)
            TextButton(enabled = ready, onClick = { if (playing) player.pause() else { player.start(); onStarted() }; playing = !playing }) { Text(if (playing) "Пауза" else "Воспроизвести") }
        }
    }
}

private fun openExternalMedia(context: Context, path: String, mime: String): String? = runCatching {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))
    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
}.exceptionOrNull()?.let { "Нет приложения для открытия этого формата" }
