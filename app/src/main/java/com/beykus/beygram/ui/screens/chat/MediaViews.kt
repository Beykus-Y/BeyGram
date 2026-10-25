package com.beykus.beygram.ui.screens.chat

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.content.ClipData
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.LruCache
import android.webkit.MimeTypeMap
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.beykus.beygram.R
import com.beykus.beygram.BeyGramApplication
import com.beykus.beygram.data.media.AutoDownloadNetworkStatus
import com.beykus.beygram.data.media.AutoDownloadRuntime
import com.beykus.beygram.data.media.Mp4StreamingProbe
import com.beykus.beygram.data.media.Mp4StreamingReason
import com.beykus.beygram.data.settings.AutoDownloadSettingsStore
import com.beykus.beygram.domain.model.AutoDownloadRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.LocalPlaybackAccountId
import com.beykus.beygram.playback.GrowingPlaybackSource
import com.beykus.beygram.playback.canPlayAudioProgressively
import com.beykus.beygram.playback.growingAudioSource
import com.beykus.beygram.playback.isDirectProgressiveAudio
import com.beykus.beygram.playback.isMp4AudioMime
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackOrigin
import com.beykus.beygram.playback.PlaybackState
import com.beykus.beygram.playback.playbackItem
import com.beykus.beygram.playback.playbackKey
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.screens.playback.TrackArtwork
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.TrafficUsageBadge
import com.beykus.beygram.ui.screens.playback.NowPlayingControls
import com.beykus.beygram.ui.screens.playback.ViewerNowPlayingColors
import com.beykus.beygram.ui.text.resolve
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

internal fun MediaKind.label(context: Context): String = when (this) {
    MediaKind.Photo -> context.getString(R.string.media_kind_photo)
    MediaKind.Video -> context.getString(R.string.media_kind_video)
    MediaKind.Animation -> context.getString(R.string.media_kind_animation)
    MediaKind.Sticker -> context.getString(R.string.media_kind_sticker)
    MediaKind.Audio -> context.getString(R.string.media_kind_audio)
    MediaKind.Document -> context.getString(R.string.media_kind_file)
    MediaKind.Voice -> context.getString(R.string.media_kind_voice)
    MediaKind.VideoNote -> context.getString(R.string.media_kind_video_note)
}

@Composable internal fun formatMediaSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> stringResource(R.string.media_size_mb, bytes / 1048576.0)
    bytes > 0 -> stringResource(R.string.media_size_kb, (bytes / 1024).coerceAtLeast(1))
    else -> ""
}

internal fun duration(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

/** Декодированные картинки до 1024 px держатся в памяти, пока их не вытеснят: повторный вход в композицию (прокрутка, возврат в чат) не декодирует файл заново. Полноразмерные картинки просмотрщика сюда не попадают. */
private object DecodedImageCache {
    const val MAX_CACHED_DIMENSION = 1024
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}

@Composable
internal fun LocalMediaImage(path: String, modifier: Modifier = Modifier, fit: Boolean = false, maxDimension: Int = 1024, contentDescriptionText: String? = null) {
    var failed by remember(path) { mutableStateOf(false) }
    val context = LocalContext.current
    val cacheKey = "$path#$maxDimension"
    val cacheable = maxDimension <= DecodedImageCache.MAX_CACHED_DIMENSION
    // Картинка из кэша показывается сразу, без индикатора загрузки.
    val cached = remember(cacheKey) { if (cacheable) DecodedImageCache.get(cacheKey)?.let { BitmapDrawable(context.resources, it) } else null }
    val drawable by produceState<Drawable?>(cached, cacheKey) {
        if (cached != null) { value = cached; return@produceState }
        val decoded = withContext(Dispatchers.IO) { runCatching {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > maxDimension) decoder.setTargetSize((info.size.width.toLong() * maxDimension / longest).toInt().coerceAtLeast(1), (info.size.height.toLong() * maxDimension / longest).toInt().coerceAtLeast(1))
            }
        } }
        failed = decoded.isFailure
        val result = decoded.getOrNull()
        if (cacheable && result is BitmapDrawable) DecodedImageCache.put(cacheKey, result.bitmap)
        value = result
    }
    DisposableEffect(drawable) {
        (drawable as? AnimatedImageDrawable)?.start()
        onDispose { (drawable as? AnimatedImageDrawable)?.stop() }
    }
    Box(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        when {
            drawable != null -> AndroidView(
                factory = { ImageView(it).apply { contentDescription = contentDescriptionText } },
                update = { view ->
                    view.contentDescription = contentDescriptionText
                    view.scaleType = if (fit) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
                    if (view.drawable !== drawable) view.setImageDrawable(drawable)
                }, modifier = Modifier.fillMaxSize()
            )
            failed -> Text(stringResource(R.string.media_image_read_failed), color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            else -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

/** Минипревью TDLib: крошечный JPEG, растянутый на весь кадр, поэтому выглядит размытым. */
@Composable
internal fun MinithumbnailImage(data: ByteArray, modifier: Modifier = Modifier) {
    val bitmap = remember(data) { BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap() }
    bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier) }
}

/** Заглушка до загрузки полного файла: миниатюра, уменьшенная до [BLUR_PREVIEW_DIMENSION] px, остаётся только цвет и форма. */
@Composable
private fun BlurredMediaPreview(media: MessageMedia, thumbnailPath: String?, modifier: Modifier) {
    val minithumbnail = media.minithumbnail
    when {
        thumbnailPath != null -> LocalMediaImage(thumbnailPath, modifier, maxDimension = BLUR_PREVIEW_DIMENSION)
        minithumbnail != null -> MinithumbnailImage(minithumbnail, modifier)
    }
}

/** Кнопка загрузки поверх заглушки; пока файл грузится, вместо стрелки крутится индикатор. */
@Composable
private fun DownloadBadge(downloading: Boolean, contentDescription: String, onClick: () -> Unit) {
    val loadingLabel = stringResource(R.string.a11y_state_loading)
    Box(
        Modifier.size(52.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape)
            .clickable(enabled = !downloading, role = Role.Button, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                if (downloading) stateDescription = loadingLabel
            },
        contentAlignment = Alignment.Center
    ) {
        if (downloading) CircularProgressIndicator(Modifier.size(26.dp), color = Color.White, strokeWidth = 2.dp)
        else Icon(Icons.Rounded.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

private const val BLUR_PREVIEW_DIMENSION = 24

/** Строка, которая ждёт загрузки после нажатия на музыку или голосовое. Ждёт только последнее нажатое, и начинает играть только её строка, даже если такой же файл стоит в другом сообщении. */
internal val pendingPlaybackRow = mutableStateOf<Any?>(null)

/**
 * Repository-backed automatic download for shared UI. Provided once at the app root so every viewer
 * fallback routes automatic requests through the account's repository and keeps them cancellable.
 */
internal val LocalAutoDownloadFile = compositionLocalOf<((AutoDownloadRequest) -> Unit)?> { null }

private data class AudioPlaybackSource(val path: String, val growing: GrowingPlaybackSource?)

/**
 * Chooses a completed path or a growing prefix. Direct audio formats are accepted immediately;
 * MP4/M4A must be probed off the composition thread and only a positive result is retained.
 */
@Composable
private fun rememberAudioPlaybackSource(
    media: MessageMedia,
    file: MediaFileState,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit
): AudioPlaybackSource? {
    if (media.kind != MediaKind.Audio && media.kind != MediaKind.Voice) return null
    file.localPath?.let { return AudioPlaybackSource(it, null) }
    val path = file.downloadPath ?: return null
    if (file.size <= 0L || file.downloadedPrefixBytes <= 0L) return null

    val build: (String) -> AudioPlaybackSource? = { selected ->
        growingAudioSource(
            state = file,
            fileId = media.fileId,
            mimeType = media.mimeType,
            observeFile = observeFile,
            onNeedMore = { downloadFile(media.fileId) },
            assumeSupported = true
        )?.let { AudioPlaybackSource(selected, it) }
    }
    if (isDirectProgressiveAudio(media.mimeType)) return build(path)
    if (!isMp4AudioMime(media.mimeType)) return null

    var probedPath by remember(media.fileId, path) { mutableStateOf<String?>(null) }
    LaunchedEffect(path, file.size, file.downloadedPrefixBytes) {
        if (probedPath != null) return@LaunchedEffect
        val streamable = withContext(Dispatchers.IO) {
            runCatching {
                canPlayAudioProgressively(media.mimeType, path, file.downloadedPrefixBytes)
            }.getOrDefault(false)
        }
        if (streamable) probedPath = path
    }
    val selected = probedPath ?: return null
    return build(selected)
}

/** Track keys whose cover is still being awaited. Process-wide, so repeated taps share one observer per track. */
private val artworkWaits: MutableSet<String> = ConcurrentHashMap.newKeySet()

@Composable
internal fun MediaPreview(
    media: MessageMedia,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onPlayed: () -> Unit = {},
    origin: PlaybackOrigin? = null,
    transcriptionRequestPending: Boolean = false,
    transcriptionRequestError: String? = null,
    transcriptionRated: Boolean = false,
    onTranscribe: (() -> Unit)? = null,
    onRateTranscription: ((Boolean) -> Unit)? = null,
    /** A per-message view-once attachment: conceal it until the viewer is opened, even outside secret chats. */
    viewOnce: Boolean = false,
    /** Repository-backed automatic download; when absent the same local policy gates [downloadFile]. */
    autoDownloadFile: ((AutoDownloadRequest) -> Unit)? = null
) {
    val file by remember(media.fileId) { observeFile(media.fileId) }.collectAsStateWithLifecycle()
    val thumbId = media.thumbnailFileId ?: media.fileId
    val thumb by remember(thumbId) { observeFile(thumbId) }.collectAsStateWithLifecycle()
    var revealed by remember(media.fileId) { mutableStateOf(!media.hasSpoiler) }
    val palette = beyGramPalette()
    val context = LocalContext.current
    val accountId = LocalPlaybackAccountId.current
    val localAutoDownload = LocalAutoDownloadFile.current
    val policySettings by remember(context, accountId) {
        AutoDownloadSettingsStore.getInstance(context.applicationContext, accountId).settings
    }.collectAsStateWithLifecycle()
    val policyNetwork by AutoDownloadNetworkStatus.network.collectAsStateWithLifecycle()
    LaunchedEffect(context) { AutoDownloadNetworkStatus.ensureStarted(context) }
    val autoDownload: (AutoDownloadRequest) -> Unit = autoDownloadFile ?: localAutoDownload ?: remember(context, downloadFile, accountId) {
        { request: AutoDownloadRequest ->
            AutoDownloadRuntime.request(context, request, downloadFile, accountId)
            Unit
        }
    }
    val isVisual = media.kind in listOf(MediaKind.Photo, MediaKind.Video, MediaKind.Animation)
    // Секретное вложение в ленте не грузим и не показываем до открытия: превью иначе видно раньше просмотрщика.
    val concealed = media.isSecret || viewOnce
    val imageFile = if (concealed) null else if (media.mimeType.startsWith("image/")) file.localPath ?: thumb.localPath else if (thumbId != media.fileId) thumb.localPath else null
    LaunchedEffect(thumbId, revealed, thumb.size, file.size, concealed, media.isSecret, media.isSecretChat, media.hasSpoiler, policySettings, policyNetwork) {
        if (concealed || media.isSecretChat) return@LaunchedEffect
        fun request(id: Int, kind: MediaKind, size: Long) = autoDownload(
            AutoDownloadRequest(
                fileId = id,
                kind = kind,
                sizeBytes = size,
                isSecret = media.isSecret,
                isSecretChat = media.isSecretChat,
                hasSpoiler = media.hasSpoiler && !revealed
            )
        )
        if (revealed && isVisual) {
            // The preview is a small photo of its own; the original photo follows the photo limit.
            if (thumbId != media.fileId) request(thumbId, MediaKind.Photo, thumb.size)
            if (media.mimeType.startsWith("image/")) request(media.fileId, MediaKind.Photo, file.size)
        }
        if (media.kind == MediaKind.Audio) {
            if (thumbId != media.fileId) request(thumbId, MediaKind.Photo, thumb.size)
            request(media.fileId, MediaKind.Audio, file.size)
        }
    }
    LaunchedEffect(media.fileId, thumb.localPath) {
        if (media.kind == MediaKind.Audio && thumbId != media.fileId) {
            thumb.localPath?.let { PlaybackEngine.updateArtwork(media.playbackKey(), it) }
        }
    }
    // Музыка играет в общем плеере. Тап запускает файл или ставит на паузу; если файл ещё не загружен, сначала грузит его.
    val audioTitle = media.kind.label(context)
    val audioSource = rememberAudioPlaybackSource(media, file, observeFile, downloadFile)
    val audioPath = audioSource?.path
    val partialGrowing = audioSource?.growing
    // Голосовое ждёт загрузки в своём пузыре, поэтому здесь ожидание относится только к музыке.
    val row = remember(media.fileId) { Any() }
    val waitingForAudio = media.kind == MediaKind.Audio && pendingPlaybackRow.value === row
    fun playAudio() {
        if (media.kind == MediaKind.Audio && thumbId != media.fileId && thumb.localPath == null && !media.isSecret) {
            // One wait per track: taps while the cover downloads must not stack observers. The process-scoped
            // observer keeps the player cover updated after this row leaves the composition.
            val key = media.playbackKey()
            if (artworkWaits.add(key)) {
                (context.applicationContext as BeyGramApplication).applicationScope.launch {
                    try {
                        val downloaded = withTimeoutOrNull(90_000L) {
                            observeFile(thumbId).first { it.localPath != null }.localPath
                        }
                        downloaded?.let { PlaybackEngine.updateArtwork(key, it) }
                    } finally {
                        artworkWaits.remove(key)
                    }
                }
            }
            downloadFile(thumbId)
        }
        if (audioPath == null) { pendingPlaybackRow.value = row; downloadFile(media.fileId) }
        else startAudio(context, media.playbackItem(audioPath, audioTitle, origin,
            artworkPath = if (thumbId != media.fileId) thumb.localPath else null, growing = partialGrowing))
    }
    // Нажатие до окончания загрузки запоминается: как только файл готов, он начинает играть. Ждёт только последнее нажатое.
    LaunchedEffect(audioPath, waitingForAudio) {
        if (waitingForAudio && audioPath != null) {
            pendingPlaybackRow.value = null
            startAudio(context, media.playbackItem(audioPath, audioTitle, origin, artworkPath = if (thumbId != media.fileId) thumb.localPath else null, growing = partialGrowing))
        }
    }
    DisposableEffect(row) {
        onDispose { if (pendingPlaybackRow.value === row) pendingPlaybackRow.value = null }
    }
    if (media.kind == MediaKind.Voice) {
        VoiceBubble(media, file, downloadFile, onPlayed, onLongPress, origin,
            transcriptionRequestPending = transcriptionRequestPending,
            transcriptionRequestError = transcriptionRequestError,
            transcriptionRated = transcriptionRated,
            onTranscribe = onTranscribe,
            onRateTranscription = onRateTranscription,
            growing = partialGrowing)
        return
    }
    if (media.kind == MediaKind.VideoNote) {
        VideoNoteBubble(media, observeFile, downloadFile, onPlayed, onLongPress, origin = origin,
            transcriptionRequestPending = transcriptionRequestPending,
            transcriptionRequestError = transcriptionRequestError,
            transcriptionRated = transcriptionRated,
            onTranscribe = onTranscribe,
            onRateTranscription = onRateTranscription,
            autoDownloadFile = autoDownload)
        return
    }
    if (media.kind == MediaKind.Sticker) {
        StickerPreview(media, file, thumb, downloadFile, onOpen, onLongPress, autoDownloadFile = autoDownload)
        return
    }
    if (isVisual) {
        val ratio = if (media.width > 0 && media.height > 0) media.width.toFloat() / media.height else 1.5f
        val mediaLabel = media.kind.label(context)
        val openLabel = stringResource(R.string.a11y_media_open, mediaLabel)
        val downloadLabel = stringResource(R.string.a11y_media_download, mediaLabel)
        val spoilerLabel = stringResource(R.string.media_spoiler_hint)
        val longPressLabel = stringResource(R.string.a11y_message_actions)
        val viewOnceLabel = stringResource(R.string.media_view_once_label)
        val viewOnceHint = stringResource(R.string.media_view_once_hint, mediaLabel)
        // Полного файла ещё нет: вместо растянутой миниатюры показываем размытую заглушку и кнопку загрузки.
        val awaitingFile = revealed && !concealed && file.localPath == null
        Box(
            Modifier.width(244.dp).height((244 / ratio).coerceIn(120f, 280f).dp)
                .background(palette.disabled)
                .combinedClickable(
                    onClickLabel = if (viewOnce) viewOnceLabel else if (revealed) openLabel else spoilerLabel,
                    onLongClickLabel = longPressLabel,
                    onClick = { if (viewOnce || revealed) onOpen() else revealed = true },
                    onLongClick = onLongPress
                ),
            contentAlignment = Alignment.Center
        ) {
            if (viewOnce) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Text(
                        viewOnceHint,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else if (awaitingFile) BlurredMediaPreview(media, thumb.localPath, Modifier.fillMaxSize())
            else if (revealed && imageFile != null) LocalMediaImage(imageFile, Modifier.fillMaxSize())
            if (!viewOnce) {
                if (!revealed) Text(spoilerLabel, color = palette.textPrimary, modifier = Modifier.padding(16.dp))
                else if (awaitingFile) DownloadBadge(downloading = file.isDownloading, contentDescription = downloadLabel, onClick = { downloadFile(media.fileId) })
                else if (imageFile == null || media.kind == MediaKind.Video || media.kind == MediaKind.Animation || media.mimeType.startsWith("video/")) {
                    val isVideo = media.kind == MediaKind.Video || media.kind == MediaKind.Animation || media.mimeType.startsWith("video/")
                    Icon(if (isVideo) Icons.Rounded.PlayArrow else Icons.Rounded.Download, contentDescription = null, tint = Color.White,
                        modifier = Modifier.size(52.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape).padding(10.dp))
                }
                if (revealed) Text(
                    media.kind.label(context) + if (media.durationSeconds > 0) " · ${duration(media.durationSeconds)}" else "",
                    color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            if (file.isDownloading || file.isUploading) LinearProgressIndicator(
                progress = { if (file.isUploading) file.uploadProgress else file.downloadProgress },
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            )
        }
    } else if (media.kind == MediaKind.Audio) {
        val playingFlow = remember(media.fileId) {
            PlaybackEngine.state.map { it.item?.key == media.playbackKey() && it.playing }.distinctUntilChanged()
        }
        val musicPlaying by playingFlow.collectAsStateWithLifecycle(initialValue = false)
        val title = media.title.ifBlank { media.fileName.ifBlank { stringResource(R.string.media_kind_audio) } }
        val cover = if (thumbId != media.fileId) thumb.localPath else null
        val playLabel = stringResource(if (musicPlaying) R.string.common_pause else R.string.common_play)
        val longPressLabel = stringResource(R.string.a11y_message_actions)
        Row(
            Modifier.width(280.dp).clip(RoundedCornerShape(12.dp))
                .combinedClickable(onClickLabel = playLabel, onLongClickLabel = longPressLabel, onClick = { playAudio() }, onLongClick = onLongPress)
                .padding(top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackArtwork(artworkPath = cover, audioPath = audioPath, modifier = Modifier.size(44.dp),
                background = palette.accentSoft, tint = palette.accentInk)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(media.performer.ifBlank {
                    formatMediaSize(file.size) + if (media.durationSeconds > 0) " · " + duration(media.durationSeconds) else ""
                }, color = palette.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (media.performer.isNotBlank() && media.durationSeconds > 0) {
                    Text(duration(media.durationSeconds), color = palette.textSecondary, fontSize = 11.sp)
                }
                if (file.isDownloading || file.isUploading) {
                    LinearProgressIndicator(progress = { if (file.isUploading) file.uploadProgress else file.downloadProgress },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
            IconButton(onClick = { playAudio() }, modifier = Modifier.size(48.dp)) {
                if (file.isDownloading || waitingForAudio) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = palette.accent)
                } else {
                    Icon(if (musicPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (musicPlaying) R.string.common_pause else R.string.common_play),
                        tint = palette.accentInk)
                }
            }
            IconButton(onClick = onOpen, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.MoreVert, stringResource(R.string.media_more_actions), tint = palette.textSecondary)
            }
        }
    } else {
        val mediaLabel = media.kind.label(context)
        val openLabel = stringResource(R.string.a11y_media_open, mediaLabel)
        val longPressLabel = stringResource(R.string.a11y_message_actions)
        Row(Modifier.width(244.dp).clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClickLabel = openLabel, onLongClickLabel = longPressLabel, onClick = onOpen, onLongClick = onLongPress)
            .padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.InsertDriveFile, contentDescription = null, tint = palette.accentInk,
                modifier = Modifier.size(44.dp).background(palette.accentSoft, CircleShape).padding(10.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(media.fileName.ifBlank { stringResource(R.string.media_kind_file) }, color = palette.textPrimary, fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(formatMediaSize(file.size) + if (media.durationSeconds > 0) " · " + duration(media.durationSeconds) else "",
                    color = palette.textSecondary, fontSize = 13.sp)
                if (file.isDownloading || file.isUploading) LinearProgressIndicator(
                    progress = { if (file.isUploading) file.uploadProgress else file.downloadProgress },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
    }
}

/** Действия над сообщением чата в просмотрщике; не передаётся, если вложение открыто не из чата. */
internal class MediaViewerActions(
    val canForward: Boolean, val canEdit: Boolean, val canDelete: Boolean,
    val onForward: () -> Unit, val onEdit: () -> Unit, val onDelete: () -> Unit
)

/** Вложение в листаемом просмотрщике; [messageId] нужен для отметки «просмотрено» и действий над сообщением. */
internal data class MediaViewerItem(val messageId: Long, val media: MessageMedia, val viewOnce: Boolean = false)

/** Фото и видео листаются вместе, и счётчик «N из M» считает их тоже вместе. */
internal val viewerSwipeKinds = setOf(MediaKind.Photo, MediaKind.Video)

/**
 * [pages] листаются слева направо, от самого раннего; [selectedId] — вложение, которое показано сейчас.
 * [canExport] разрешает сохранить файл и передать его наружу; Telegram запрещает это в защищённых чатах.
 * [confirmExport] заново проверяет право на сообщение перед каждым действием с файлом: кнопки могут показывать прошлый ответ.
 * Без него проверки нет, так открывается фото профиля, которое не относится к сообщению.
 * [onShowInChat] не передаётся, если просмотр открыт не из чата.
 */
@Composable
internal fun MediaViewer(
    pages: List<MediaViewerItem>, selectedId: Long, onSelect: (Long) -> Unit,
    observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit, onViewed: (Long) -> Unit, onClose: () -> Unit,
    title: String? = null, subtitle: String? = null, canExport: Boolean = true, confirmExport: suspend (Long) -> Boolean = { true },
    actions: MediaViewerActions? = null, onShowInChat: ((Long) -> Unit)? = null,
    extraFooter: (@Composable () -> Unit)? = null,
    /** For profile galleries prefetch only thumbnails of adjacent pages, not full originals. */
    fullFileOnlyWhenActive: Boolean = false,
    /** Repository-backed automatic download; when absent the same local policy gates [downloadFile]. */
    autoDownloadFile: ((AutoDownloadRequest) -> Unit)? = null
) {
    // The source can shrink asynchronously while LazyLayout still holds indices from the previous frame.
    // Keep the count, keys and content aligned to the same immutable snapshot for this composition.
    val currentPages = pages.toList()
    if (currentPages.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val pageIds = currentPages.map { it.messageId }
    val selectedIndex = mediaViewerSelectedIndex(pageIds, selectedId)
    val shown = currentPages[selectedIndex]
    // A spoiler page stays hidden until the user reveals that page; swiping to a neighbour does not reveal it.
    var revealedSpoilers by remember { mutableStateOf(emptySet<Long>()) }
    val pagerState = rememberPagerState(initialPage = selectedIndex) { currentPages.size }
    // Pager keys already preserve media identity across gallery page inserts/reorders.
    // Never scroll on pageIds changes: currentPage can advance during a drag while
    // selectedId still describes settledPage, and forcing it back cancels the gesture.
    LaunchedEffect(pageIds) {
        if (selectedId !in pageIds) {
            val survivingId = pageIds.getOrNull(pagerState.settledPage) ?: pageIds.first()
            onSelect(survivingId)
        }
    }
    // Only an actual selection change (e.g. opening a different thumbnail) may
    // correct the page. Do not override an active user gesture.
    LaunchedEffect(selectedId) {
        val target = mediaViewerSelectedIndex(pageIds, selectedId)
        if (mediaViewerShouldScrollToSelection(pagerState.isScrollInProgress, pagerState.settledPage, target)) {
            pagerState.scrollToPage(target)
        }
    }
    // Changes to pageIds are *not* user selection events: older media may prepend
    // pages while the pager is still restoring its anchor by stable item key.
    // Publish a new selection only after an actual scrolling session ends.
    var wasScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) {
            wasScrolling = true
        } else if (wasScrolling) {
            wasScrolling = false
            val settled = pageIds.getOrNull(pagerState.settledPage)
            if (settled != null && settled != selectedId && selectedId in pageIds) onSelect(settled)
        }
    }
    val safeSettledPage = pagerState.settledPage.coerceIn(0, currentPages.lastIndex)
    // The secure flag must correspond to the actually visible media, not to a stale position.
    val settledMedia = currentPages[safeSettledPage].media
    val settledViewOnce = currentPages[safeSettledPage].viewOnce
    val shownFile by remember(shown.media.fileId) { observeFile(shown.media.fileId) }.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val accountId = LocalPlaybackAccountId.current
    val localAutoDownload = LocalAutoDownloadFile.current
    val autoDownload: (AutoDownloadRequest) -> Unit = autoDownloadFile ?: localAutoDownload ?: remember(context, downloadFile, accountId) {
        { request: AutoDownloadRequest ->
            AutoDownloadRuntime.request(context, request, downloadFile, accountId)
            Unit
        }
    }
    val scope = rememberCoroutineScope()
    var externalError by remember { mutableStateOf<String?>(null) }
    var fileNotice by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    // Панель возвращается при каждом переходе: иначе после скрытия на фото нельзя вернуть действия на видео или файле.
    LaunchedEffect(pagerState.settledPage) { controlsVisible = true }
    // A spoiler that is still hidden cannot be shared, saved or opened in another app, even when its file is already on disk.
    val shownHidden = shown.media.hasSpoiler && !shown.media.isSecret && shown.messageId !in revealedSpoilers
    val exportPath = if (canExport && !shown.media.isSecret && !shown.viewOnce && !shownHidden) shownFile.localPath else null
    // Кнопки могут показывать право, пришедшее для соседнего вложения, поэтому каждое действие сверяет право заново.
    fun exportIfAllowed(messageId: Long, action: suspend () -> String?) {
        scope.launch { fileNotice = if (confirmExport(messageId)) action() else context.getString(R.string.media_export_denied) }
    }
    fun openExternal(item: MediaViewerItem, path: String) {
        scope.launch {
            if (!confirmExport(item.messageId)) { externalError = context.getString(R.string.media_export_denied); return@launch }
            externalError = openExternalMedia(context, path, item.media.mimeType)
            if (externalError == null) onViewed(item.messageId)
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, securePolicy = if (MediaViewerPolicy.secureWhileShowing(settledMedia, settledViewOnce)) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit)) {
        // Верхняя панель лежит поверх страницы: тап по картинке её прячет, крестик закрытия виден всегда.
        // Панель действий и сообщения стоят под страницей, потому что поверх неё лежит управление видео и аудио.
        Column(Modifier.fillMaxSize().background(Color(0xFF070B10)).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { index -> mediaViewerPageKey(pageIds, index) }) { page ->
                    // Compose may request an index belonging to the preceding page count during an update.
                    // Never index the backing list until this page exists in our current snapshot.
                    currentPages.getOrNull(page)?.let { item ->
                        MediaViewerPage(item, observeFile, downloadFile, autoDownload, canExport = canExport, active = page == safeSettledPage,
                            fullFileOnlyWhenActive = fullFileOnlyWhenActive,
                            revealed = item.messageId in revealedSpoilers, onReveal = { revealedSpoilers += item.messageId },
                            onOpenFile = { path -> openExternal(item, path) }, onViewed = onViewed,
                            onDismiss = onClose, onToggleControls = { controlsVisible = !controlsVisible }, controlsVisible = controlsVisible)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter).background(if (controlsVisible) Color(0xB3070B10) else Color.Transparent).padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = Color.White) }
                    if (controlsVisible) {
                        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                            Text(title ?: shown.media.fileName.ifBlank { shown.media.kind.label(context) }, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            subtitle?.let { Text(it, color = Color(0xFFA39C95), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            // Read-only traffic line for the selected file. Secret, view-once and hidden-spoiler
                            // pages keep their existing presentation and never show download details.
                            if (shownFile.isDownloading && !shown.media.isSecret && !shown.media.isSecretChat && !shown.viewOnce && !shownHidden) {
                                TrafficUsageBadge(
                                    accountId = accountId,
                                    fileId = shown.media.fileId,
                                    downloadedBytes = shownFile.downloadedBytes,
                                    totalBytes = shownFile.size,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        if (exportPath != null) IconButton(onClick = { exportIfAllowed(shown.messageId) { shareMedia(context, exportPath, shown.media.mimeType) } }) { Icon(Icons.Rounded.Share, stringResource(R.string.common_share), tint = Color.White) }
                        if (exportPath != null || onShowInChat != null) Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.media_more_actions), tint = Color.White) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (exportPath != null) DropdownMenuItem(text = { Text(stringResource(R.string.media_open_in_other_app)) }, onClick = { menuOpen = false; openExternal(shown, exportPath) })
                                if (onShowInChat != null) DropdownMenuItem(text = { Text(stringResource(R.string.media_show_in_chat)) }, onClick = { menuOpen = false; onShowInChat(shown.messageId) })
                            }
                        }
                    } else Spacer(Modifier.weight(1f))
                }
                // Кнопки листания нужны тем, кто не листает пальцем, например TalkBack. Кнопка у края неактивна, если соседней страницы нет.
                if (currentPages.size > 1) {
                    IconButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(safeSettledPage - 1) } },
                        enabled = safeSettledPage > 0,
                        modifier = Modifier.align(Alignment.CenterStart).padding(8.dp).background(Color(0x80070B10), CircleShape)
                    ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.media_previous), tint = Color.White.copy(alpha = if (safeSettledPage > 0) 1f else 0.3f)) }
                    IconButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(safeSettledPage + 1) } },
                        enabled = safeSettledPage < currentPages.lastIndex,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp).background(Color(0x80070B10), CircleShape)
                    ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.media_next), tint = Color.White.copy(alpha = if (safeSettledPage < currentPages.lastIndex) 1f else 0.3f)) }
                }
            }
            // Строка всегда занимает место, где может появиться действие: иначе страница прыгает, когда докачанный файл добавляет «Сохранить».
            if (actions != null || canExport) Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                if (actions != null && actions.canForward) ViewerAction(stringResource(R.string.media_action_forward), Icons.AutoMirrored.Rounded.Forward, onClick = actions.onForward)
                if (exportPath != null) ViewerAction(stringResource(R.string.common_save), Icons.Rounded.Download) {
                    exportIfAllowed(shown.messageId) { withContext(Dispatchers.IO) { saveToDevice(context, exportPath, shown.media) } }
                }
                if (actions != null && actions.canEdit) ViewerAction(stringResource(R.string.common_edit), Icons.Rounded.Edit, onClick = actions.onEdit)
                if (actions != null && actions.canDelete) ViewerAction(stringResource(R.string.common_delete), Icons.Rounded.Delete, Color(0xFFF97066), actions.onDelete)
            }
            externalError?.let { Text(it, color = Color(0xFFFFBE9D), modifier = Modifier.padding(16.dp).errorAnnouncement()) }
            fileNotice?.let { Text(it, color = Color.LightGray, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).errorAnnouncement()) }
            extraFooter?.invoke()
        }
    }
}

/** Одна страница просмотрщика. [active] — страница, которую сейчас видно целиком: только её видео и аудио играют, только она отмечается просмотренной и только на ней открывается секретное вложение. */
@Composable
private fun MediaViewerPage(
    item: MediaViewerItem, observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit,
    autoDownloadFile: (AutoDownloadRequest) -> Unit,
    canExport: Boolean, active: Boolean, fullFileOnlyWhenActive: Boolean, revealed: Boolean, onReveal: () -> Unit, onOpenFile: (String) -> Unit, onViewed: (Long) -> Unit,
    onDismiss: () -> Unit, onToggleControls: () -> Unit, controlsVisible: Boolean
) {
    val media = item.media
    val context = LocalContext.current
    val accountId = LocalPlaybackAccountId.current
    val policySettings by remember(context, accountId) {
        AutoDownloadSettingsStore.getInstance(context.applicationContext, accountId).settings
    }.collectAsStateWithLifecycle()
    val policyNetwork by AutoDownloadNetworkStatus.network.collectAsStateWithLifecycle()
    LaunchedEffect(context) { AutoDownloadNetworkStatus.ensureStarted(context) }
    val file by remember(media.fileId) { observeFile(media.fileId) }.collectAsStateWithLifecycle()
    val tgs = media.mimeType == "application/x-tgsticker"
    val previewId = media.thumbnailFileId
    // Миниатюра показывается вместо оригинала, пока тот грузится.
    val thumbId = previewId ?: media.fileId
    val thumb by remember(thumbId) { observeFile(thumbId) }.collectAsStateWithLifecycle()
    val exportable = canExport && !media.isSecret
    // Спойлер не грузится и не показывается, пока пользователь не раскроет именно эту страницу.
    val spoilerHidden = media.hasSpoiler && !media.isSecret && !revealed
    // Соседняя секретная страница при свайпе не грузится и ничего не показывает, даже если файл уже на диске.
    val concealed = MediaViewerPolicy.concealed(media, active) || (item.viewOnce && !active) || spoilerHidden
    val currentOnViewed by rememberUpdatedState(onViewed)
    // A spoiler page counts as opened only once the user reveals it, even when its file is already on disk.
    LaunchedEffect(media.fileId, file.localPath, active, spoilerHidden) {
        if (active && !spoilerHidden && file.localPath != null && media.mimeType.startsWith("image/")) currentOnViewed(item.messageId)
    }
    LaunchedEffect(media.fileId, concealed, active, fullFileOnlyWhenActive, thumb.size, file.size, media.isSecret, media.isSecretChat, media.hasSpoiler, policySettings, policyNetwork) {
        if (concealed) return@LaunchedEffect
        fun request(id: Int, kind: MediaKind, size: Long) = autoDownloadFile(
            AutoDownloadRequest(
                fileId = id,
                kind = kind,
                sizeBytes = size,
                isSecret = media.isSecret,
                isSecretChat = media.isSecretChat,
                hasSpoiler = spoilerHidden
            )
        )
        // The profile gallery downloads adjacent previews; the full file waits for an active page.
        if (tgs || media.mimeType.startsWith("image/") || media.mimeType.startsWith("video/") || media.kind == MediaKind.Audio) {
            previewId?.let { request(it, MediaKind.Photo, thumb.size) }
        }
        // The page the user opened is an explicit request, so only neighbouring pages go through the auto-download policy.
        if (active) downloadFile(media.fileId)
        else if (!fullFileOnlyWhenActive) request(media.fileId, media.kind, file.size)
    }
    // A fast-start MP4 can be played from the contiguous prefix that TDLib has already written.
    // The sender-provided supportsStreaming flag is only a hint; the local probe proves the index
    // position and codecs. If the index is at the end or the container is not proven streamable,
    // we keep the complete-file fallback and show the thumbnail/progress instead.
    val partialPath = file.downloadPath
    var progressiveUnlocked by remember(media.fileId) { mutableStateOf(false) }
    var progressivePath by remember(media.fileId) { mutableStateOf<String?>(null) }
    var progressiveRejected by remember(media.fileId, partialPath) { mutableStateOf(false) }
    LaunchedEffect(media.fileId, partialPath, file.downloadedPrefixBytes, file.localPath, progressiveRejected) {
        if (progressiveUnlocked || progressiveRejected) return@LaunchedEffect
        if (file.localPath != null || partialPath == null || file.size <= 0L || file.downloadedPrefixBytes <= 0L) return@LaunchedEffect
        val info = withContext(Dispatchers.IO) {
            runCatching { Mp4StreamingProbe.inspect(File(partialPath), file.downloadedPrefixBytes) }.getOrNull()
        }
        val reason = info?.reason
        when {
            info?.streamable == true -> {
                progressivePath = partialPath
                progressiveUnlocked = true
            }
            reason != null && reason in setOf(
                Mp4StreamingReason.NotMp4,
                Mp4StreamingReason.MoovAfterMediaData,
                Mp4StreamingReason.MoovTooLarge,
                Mp4StreamingReason.NoVideoTrack,
                Mp4StreamingReason.UnsupportedVideoCodec
            ) -> progressiveRejected = true
        }
    }
    val unlockedPath = progressivePath
    // The player may keep an earlier source instance, so the lambda reads the latest page state.
    val activeNow by rememberUpdatedState(active)
    val progressive = if (progressiveUnlocked && unlockedPath != null) {
        ProgressiveVideoSource(
            fileId = media.fileId,
            path = unlockedPath,
            totalSize = file.size,
            completed = { observeFile(media.fileId).value.localPath != null },
            downloading = { observeFile(media.fileId).value.isDownloading },
            readablePrefix = { observeFile(media.fileId).value.downloadedPrefixBytes },
            // The opened page is user-initiated and keeps downloading on any network. A neighbouring
            // page that the player prepares in the background stays under the auto-download policy.
            onNeedMore = {
                if (activeNow) downloadFile(media.fileId)
                else autoDownloadFile(
                    AutoDownloadRequest(
                        fileId = media.fileId,
                        kind = media.kind,
                        sizeBytes = observeFile(media.fileId).value.size,
                        isSecret = media.isSecret,
                        isSecretChat = media.isSecretChat,
                        hasSpoiler = spoilerHidden
                    )
                )
            }
        )
    } else null
    val audioSource = rememberAudioPlaybackSource(media, file, observeFile, downloadFile)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val path = file.localPath
        // Keep the already playing progressive source when TDLib completes the same file path.
        // If TDLib reports a different final path, the normal completed-file source is used.
        val streamSource = progressive?.takeIf { path == null || it.path == path }
        val audioPath = audioSource?.path
        val growingAudio = audioSource?.growing
        when {
            spoilerHidden -> Text(stringResource(R.string.media_spoiler_hint), color = Color.White, fontSize = 16.sp,
                modifier = Modifier.padding(16.dp).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onReveal))
            concealed -> Unit
            tgs -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val preview: @Composable () -> Unit = {
                    if (previewId != null) {
                        val previewFile by remember(previewId) { observeFile(previewId) }.collectAsStateWithLifecycle()
                        previewFile.localPath?.let { LocalMediaImage(it, Modifier.size(240.dp), fit = true) }
                    }
                }
                if (path != null) {
                    // Если TGS не открылся, показываем превью и подпись, а не пустое место.
                    TgsSticker(path, Modifier.size(240.dp), playing = active) {
                        preview()
                        Text(stringResource(R.string.media_animated_sticker_failed), color = Color.LightGray, modifier = Modifier.padding(16.dp))
                    }
                } else {
                    preview()
                    if (file.isDownloading) CircularProgressIndicator()
                    Text(file.error?.resolve() ?: stringResource(R.string.media_animated_sticker_preview), color = Color.LightGray, modifier = Modifier.padding(16.dp))
                    if (!file.isDownloading) TextButton(onClick = { downloadFile(media.fileId) }) { Text(stringResource(R.string.common_download)) }
                }
            }
            streamSource != null -> LocalVideo(
                streamSource.path, loop = media.kind == MediaKind.Animation, active = active,
                onStarted = { currentOnViewed(item.messageId) }, progressive = streamSource,
                onManualDownload = { downloadFile(media.fileId) }
            )
            path == null && media.mimeType.startsWith("image/") -> PhotoPreview(file, thumb.localPath) { downloadFile(media.fileId) }
            path == null && media.mimeType.startsWith("video/") && thumb.localPath != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()
            ) {
                LocalMediaImage(thumb.localPath!!, Modifier.weight(1f).fillMaxWidth(), fit = true)
                Text(file.error?.resolve() ?: stringResource(R.string.media_download_progress, formatMediaSize(file.downloadedBytes), formatMediaSize(file.size)),
                    color = Color.LightGray, modifier = Modifier.padding(8.dp))
                if (file.isDownloading) LinearProgressIndicator(Modifier.fillMaxWidth())
                else TextButton(onClick = { downloadFile(media.fileId) }) { Text(stringResource(R.string.common_download)) }
            }
            (media.kind == MediaKind.Audio || media.kind == MediaKind.Voice) && audioPath != null ->
                AudioInPlayer(media, audioPath, if (previewId != null) thumb.localPath else null, active, growingAudio) { currentOnViewed(item.messageId) }
            path == null -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                if (file.isDownloading) CircularProgressIndicator()
                Text(file.error?.resolve() ?: stringResource(R.string.media_download_progress, formatMediaSize(file.downloadedBytes), formatMediaSize(file.size)), color = Color.LightGray, modifier = Modifier.padding(16.dp))
                if (!file.isDownloading) TextButton(onClick = { downloadFile(media.fileId) }) { Text(stringResource(R.string.common_download)) }
            }
            media.mimeType.startsWith("image/") -> ZoomableImage(path, onDismiss, onToggleControls, showZoomControls = controlsVisible && active,
                contentDescription = media.kind.label(context))
            media.kind == MediaKind.Video || media.kind == MediaKind.Animation || media.mimeType.startsWith("video/") ->
                LocalVideo(path, media.kind == MediaKind.Animation, active, onStarted = { currentOnViewed(item.messageId) })
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatMediaSize(file.size), color = Color.LightGray)
                if (exportable) TextButton(onClick = { onOpenFile(path) }) { Text(stringResource(R.string.media_open_file)) }
            }
        }
    }
}

/** Фото до полной загрузки: миниатюра остаётся на экране, а поверх неё прогресс, ошибка и кнопка повтора. */
@Composable
private fun PhotoPreview(file: MediaFileState, thumbnailPath: String?, onDownload: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        if (thumbnailPath != null) LocalMediaImage(thumbnailPath, Modifier.fillMaxSize(), fit = true)
        Column(
            Modifier.align(Alignment.Center).padding(24.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(file.error?.resolve() ?: stringResource(R.string.media_download_progress, formatMediaSize(file.downloadedBytes), formatMediaSize(file.size)), color = Color.White)
            if (!file.isDownloading) TextButton(onClick = onDownload) { Text(stringResource(R.string.common_download)) }
        }
        if (file.isDownloading) LinearProgressIndicator(progress = { file.downloadProgress }, modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter))
    }
}

@Composable
private fun ViewerAction(label: String, icon: ImageVector, tint: Color = Color(0xFFCFC4FF), onClick: () -> Unit) {
    Column(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = tint, fontSize = 12.sp)
    }
}

@Composable
private fun ZoomableImage(path: String, onDismiss: () -> Unit, onToggleControls: () -> Unit, showZoomControls: Boolean, contentDescription: String?) {
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }
    // Протяжка вниз при масштабе 1 двигает картинку за пальцем; отпущенная дальше четверти высоты закрывает просмотр.
    var dismissY by remember(path) { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnToggleControls by rememberUpdatedState(onToggleControls)
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = clampImageOffset(offset + pan, scale, size)
    }
    val dismissDrag = rememberDraggableState { delta -> if (scale <= 1f) dismissY += delta }
    // Масштаб, ушедший выше 1 посреди протяжки, отменяет её: сдвиг не должен остаться после возврата к масштабу 1.
    LaunchedEffect(scale > 1f) { if (scale > 1f) dismissY = 0f }
    // Кнопки масштаба меняют его вокруг центра, как и жест. Нужны тем, кому неудобно сводить два пальца.
    fun zoomTo(target: Float) {
        val next = target.coerceIn(1f, 5f)
        offset = offsetAroundCenter(offset, scale, next, size)
        scale = next
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { size = it }.transformable(transform, canPan = { scale > 1f })
            .draggable(state = dismissDrag, orientation = Orientation.Vertical, enabled = scale <= 1f, onDragStopped = {
                if (dismissY > size.height / 4f) currentOnDismiss()
                dismissY = 0f
            })
            .pointerInput(path) {
                detectTapGestures(
                    onTap = { currentOnToggleControls() },
                    // Двойной тап приближает к точке касания, поэтому место под пальцем не уезжает. Второй тап возвращает масштаб 1.
                    onDoubleTap = { point ->
                        val target = if (scale > 1f) 1f else 2.5f
                        offset = clampImageOffset(offsetAroundPoint(point, Offset(size.width / 2f, size.height / 2f), offset, scale, target), target, size)
                        scale = target
                    }
                )
            }
        ) {
            LocalMediaImage(path, Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y + dismissY }, fit = true, maxDimension = 4096,
                contentDescriptionText = contentDescription)
        }
        // Кнопки стоят поверх картинки, а не внутри её жестов: тап по кнопке не должен переключать панели или сдвигать фото.
        // Внизу по центру, чтобы на низком окне не наезжать на кнопки листания у краёв.
        if (showZoomControls) Row(
            Modifier.align(Alignment.BottomCenter).padding(16.dp).background(Color(0x80070B10), RoundedCornerShape(24.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { zoomTo(scale / 1.5f) }, enabled = scale > 1f) {
                Icon(Icons.Rounded.Remove, stringResource(R.string.media_zoom_out), tint = Color.White.copy(alpha = if (scale > 1f) 1f else 0.3f))
            }
            IconButton(onClick = { zoomTo(scale * 1.5f) }, enabled = scale < 5f) {
                Icon(Icons.Rounded.Add, stringResource(R.string.media_zoom_in), tint = Color.White.copy(alpha = if (scale < 5f) 1f else 0.3f))
            }
        }
    }
}

/** Смещение, при котором точка [point] остаётся под пальцем, когда масштаб меняется с [from] на [to]. Координаты от центра картинки. */
internal fun offsetAroundPoint(point: Offset, center: Offset, offset: Offset, from: Float, to: Float): Offset {
    val relative = point - center
    return relative - (relative - offset) * (to / from)
}

/** Смещение при смене масштаба с [from] на [to] вокруг центра картинки: точка в центре остаётся на месте, пустой край не открывается. */
internal fun offsetAroundCenter(offset: Offset, from: Float, to: Float, size: IntSize): Offset =
    clampImageOffset(offset * (to / from), to, size)

/** Смещение, которое не открывает пустой край картинки при масштабе [scale]. */
internal fun clampImageOffset(offset: Offset, scale: Float, size: IntSize): Offset {
    val maxX = size.width * (scale - 1) / 2
    val maxY = size.height * (scale - 1) / 2
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

private const val VIDEO_PROGRESS_INTERVAL_MS = 250L

/** A partially downloaded fast-start video: [path] grows while TDLib downloads the rest. */
private data class ProgressiveVideoSource(
    val fileId: Int,
    val path: String,
    val totalSize: Long,
    val completed: () -> Boolean,
    val downloading: () -> Boolean,
    /** TDLib's contiguous readable prefix; bytes beyond it may be unwritten or garbage. */
    val readablePrefix: () -> Long,
    val onNeedMore: () -> Unit
)

/**
 * Reads a growing TDLib file. The extractor sees the declared total length, and [read] waits for
 * TDLib to append the next bytes instead of treating the current end of the file as the end of the
 * stream. A stall (paused download, network loss) becomes a normal playback error after a timeout so
 * the player never blocks a thread forever.
 */
@OptIn(UnstableApi::class)
private class ProgressiveFileDataSource(
    private val filePath: String,
    private val totalSize: Long,
    private val isComplete: () -> Boolean,
    private val isDownloading: () -> Boolean,
    private val readablePrefix: () -> Long,
    private val onNeedMore: () -> Unit
) : DataSource {
    private var file: RandomAccessFile? = null
    private var position = 0L
    private var uri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) = Unit

    override fun open(dataSpec: DataSpec): Long {
        close()
        val opened = RandomAccessFile(filePath, "r")
        opened.seek(dataSpec.position)
        file = opened
        position = dataSpec.position
        uri = dataSpec.uri
        // After completion the file is authoritative; before completion TDLib's declared size is.
        val length = if (isComplete()) opened.length() else totalSize
        val remaining = (length - position).coerceAtLeast(0L)
        return if (dataSpec.length == C.LENGTH_UNSET.toLong()) remaining
        else minOf(remaining, dataSpec.length)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val opened = file ?: return C.RESULT_END_OF_INPUT
        val startedAt = SystemClock.elapsedRealtime()
        var requestedAt = 0L
        while (true) {
            // TDLib documents that an incomplete file may contain unavailable/garbage regions:
            // only downloadedPrefixSize is guaranteed readable. Never use file.length() as a boundary
            // before completion.
            val readable = if (isComplete()) opened.length() else minOf(readablePrefix(), opened.length())
            val available = readable - position
            if (available > 0L) {
                val toRead = minOf(available, length.toLong()).toInt()
                val read = opened.read(buffer, offset, toRead)
                if (read > 0) {
                    position += read
                    return read
                }
            }
            if (isComplete()) return C.RESULT_END_OF_INPUT
            val now = SystemClock.elapsedRealtime()
            if (!isDownloading() && now - startedAt >= NO_TRANSFER_TIMEOUT_MS) {
                throw IOException("Automatic video download was cancelled")
            }
            if (now - requestedAt >= REQUEST_INTERVAL_MS) {
                requestedAt = now
                onNeedMore()
            }
            if (now - startedAt >= STALL_TIMEOUT_MS) {
                throw IOException("Timed out waiting for the next video chunk")
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted while waiting for video", interrupted)
            }
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        runCatching { file?.close() }
        file = null
    }

    private companion object {
        const val POLL_INTERVAL_MS = 50L
        const val REQUEST_INTERVAL_MS = 1_000L
        const val NO_TRANSFER_TIMEOUT_MS = 10_000L
        const val STALL_TIMEOUT_MS = 45_000L
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun LocalVideo(
    path: String,
    loop: Boolean,
    active: Boolean,
    onStarted: () -> Unit,
    progressive: ProgressiveVideoSource? = null,
    onManualDownload: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnStarted by rememberUpdatedState(onStarted)
    val currentActive by rememberUpdatedState(active)
    val sourceKey = progressive?.fileId ?: -1
    val player = remember(path, loop, sourceKey) {
        ExoPlayer.Builder(context.applicationContext)
            // Звук видео забирает фокус у внешних плееров. У анимации звука нет, поэтому фокус ей не нужен.
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), /* handleAudioFocus = */ !loop)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
    var video by remember(path, loop, sourceKey) { mutableStateOf(VideoPlayerState()) }
    // Просмотр отмечается, когда файл начинает играть после нажатия или открытия. Возврат из буферизации отметку не повторяет.
    var startRequested by remember(path, loop) { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                video = video.copy(playing = isPlaying)
                if (isPlaying && startRequested) {
                    startRequested = false
                    currentOnStarted()
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) { video = video.afterPlaybackState(playbackState) }
            override fun onPlayerError(error: PlaybackException) { video = video.afterFailure() }
        }
        player.addListener(listener)
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        val growth = progressive
        if (growth == null) {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        } else {
            val factory = object : DataSource.Factory {
                override fun createDataSource(): DataSource = ProgressiveFileDataSource(
                    filePath = growth.path,
                    totalSize = growth.totalSize,
                    isComplete = growth.completed,
                    isDownloading = growth.downloading,
                    readablePrefix = growth.readablePrefix,
                    onNeedMore = growth.onNeedMore
                )
            }
            player.setMediaSource(
                ProgressiveMediaSource.Factory(factory)
                    .createMediaSource(MediaItem.fromUri("beygram-progressive://${growth.fileId}"))
            )
        }
        player.prepare()
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    fun play() {
        if (video.ended) player.seekToDefaultPosition()
        // Пока файл уже играет, отметку не ставим: иначе следующий возврат из буферизации снова её отправит.
        if (!player.isPlaying) startRequested = true
        player.play()
    }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
            // Анимация без панели управления играет сама, пока страница на экране: после возврата в приложение она продолжает.
            // Возврат не ставит отметку снова: первый запуск её уже поставил.
            if (event == Lifecycle.Event.ON_START && loop && currentActive) player.play()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun seek(fraction: Float) {
        val target = seekPositionMs(fraction, video.durationMs) ?: return
        player.seekTo(target)
        video = video.copy(positionMs = target)
    }
    // Играет только текущая страница; соседнее видео, которое увели свайпом, ставим на паузу.
    // Звук видео не идёт поверх музыки из общего плеера: когда видео становится активным, музыка на паузе.
    LaunchedEffect(active, player) {
        if (active) {
            // Анимация без звука музыку не прерывает.
            if (!loop) PlaybackEngine.pause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) play()
        } else {
            player.pause()
        }
    }
    // Пока видео играет, ползунок и время обновляются. На паузе хватает одного обновления. Анимации ползунка не нужны.
    if (!loop) LaunchedEffect(player, video.playing, video.buffering) {
        do {
            video = video.copy(positionMs = player.currentPosition, durationMs = player.duration.coerceAtLeast(0L))
            if (video.playing) delay(VIDEO_PROGRESS_INTERVAL_MS)
        } while (video.playing)
    }
    if (video.failed) Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp)
    ) {
        Text(stringResource(R.string.media_video_unsupported), color = Color.LightGray)
        if (progressive != null && onManualDownload != null) {
            TextButton(onClick = {
                onManualDownload()
                video = VideoPlayerState()
                startRequested = true
                player.prepare()
                player.play()
            }) { Text(stringResource(R.string.common_retry)) }
        }
    }
    else Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    setUseController(false)
                    setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )
        if (video.buffering) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
        // Анимация играет по кругу без панели управления; обычное видео получает кнопку и ползунок.
        if (!loop) VideoControls(
            state = video,
            onAction = { action -> if (action == VideoAction.Pause) player.pause() else play() },
            onSeek = ::seek,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** Кнопка и ползунок поверх видео. Ползунок работает, только когда известна длина: перематывать без неё некуда. */
@Composable
private fun VideoControls(state: VideoPlayerState, onAction: (VideoAction) -> Unit, onSeek: (Float) -> Unit, modifier: Modifier) {
    val action = state.primaryAction()
    // Позиция во время перетаскивания живёт в ползунке, а перемотка уходит один раз, когда палец отпущен.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    Row(
        modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).navigationBarsPadding().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onAction(action) }) {
            Icon(
                imageVector = when (action) {
                    VideoAction.Play -> Icons.Rounded.PlayArrow
                    VideoAction.Pause -> Icons.Rounded.Pause
                    VideoAction.Replay -> Icons.Rounded.Replay
                },
                contentDescription = stringResource(if (action == VideoAction.Pause) R.string.common_pause else R.string.common_play),
                tint = Color.White
            )
        }
        Slider(
            value = dragFraction ?: state.fraction(),
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let(onSeek)
                dragFraction = null
            },
            enabled = state.durationMs > 0L,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
        )
        // Пока палец двигает ползунок, время показывает ту же позицию, что и ползунок.
        val shownMs = dragFraction?.let { seekPositionMs(it, state.durationMs) } ?: state.positionMs
        Text(
            "${duration((shownMs / 1000).toInt())} / ${duration((state.durationMs / 1000).toInt())}",
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** Звук в просмотрщике идёт через общий плеер. Открытие ничего не запускает, поэтому текущий трек не прерывается. */
@Composable
private fun AudioInPlayer(
    media: MessageMedia,
    path: String,
    artworkPath: String?,
    active: Boolean,
    growing: GrowingPlaybackSource?,
    onViewed: () -> Unit
) {
    val context = LocalContext.current
    val item = media.playbackItem(path, media.kind.label(context), artworkPath = artworkPath, growing = growing)
    val state by PlaybackEngine.state.collectAsStateWithLifecycle()
    val currentOnViewed by rememberUpdatedState(onViewed)
    // Файл отмечается просмотренным, когда начинает играть: открытие просмотрщика само по себе ничего не запускает.
    val playingHere = state.item?.key == item.key && state.playing
    val startedHere = state.item?.key == item.key && state.started
    LaunchedEffect(playingHere, startedHere, active) {
        if (active && playingHere && startedHere) currentOnViewed()
    }
    // Пока файл не играет в общем плеере, его перемотка и скорость относятся к другому файлу, поэтому выключены.
    val current = state.item?.key == item.key
    NowPlayingControls(
        state = if (current) state else PlaybackState(item = item),
        item = item,
        colors = ViewerNowPlayingColors,
        enabled = current,
        onPlayPause = { startAudio(context, item) }
    )
}

/** Музыка из чата и просмотрщика идёт через общий плеер. Новый файл заменяет текущий, в том числе кружок в ленте. */
private fun startAudio(context: Context, item: PlaybackItem) {
    PlaybackEngine.toggle(context, item)
}

internal fun openExternalMedia(context: Context, path: String, mime: String): String? = runCatching {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))
    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
}.exceptionOrNull()?.let { context.getString(R.string.media_no_app_for_format) }

/** Передаёт файл через системное меню «Поделиться». Возвращает текст ошибки или null. */
private fun shareMedia(context: Context, path: String, mime: String): String? = runCatching {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))
    val send = Intent(Intent.ACTION_SEND).setType(mime.ifBlank { "*/*" }).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.exceptionOrNull()?.let { context.getString(R.string.media_share_failed) }

/** Копирует файл в галерею (фото и видео) или в «Загрузки» через MediaStore; без разрешения на хранилище на API 29+. */
private fun saveToDevice(context: Context, path: String, media: MessageMedia): String = runCatching {
    val resolver = context.contentResolver
    val mimeMap = MimeTypeMap.getSingleton()
    val export = resolveExportFileMetadata(
        media.fileName, media.mimeType, media.kind.label(context),
        mimeMap::getExtensionFromMimeType, mimeMap::getMimeTypeFromExtension,
        readExportSignature(File(path))
    )
    val image = export.mimeType.startsWith("image/")
    val video = export.mimeType.startsWith("video/")
    val (collection, folder) = when {
        image -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
        video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
        else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_DOWNLOADS
    }
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, export.displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, export.mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/BeyGram")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(collection, values) ?: error("MediaStore did not create a record")
    try {
        val output = resolver.openOutputStream(uri) ?: error("MediaStore did not open an output stream")
        output.use { stream -> File(path).inputStream().use { input -> input.copyTo(stream) } }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    } catch (failure: Exception) {
        resolver.delete(uri, null, null)
        throw failure
    }
    if (image || video) context.getString(R.string.media_saved_gallery) else context.getString(R.string.media_saved_downloads)
}.getOrElse { context.getString(R.string.media_save_failed) }
