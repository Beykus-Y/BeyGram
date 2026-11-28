package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.beykus.beygram.R
import com.beykus.beygram.data.media.AutoDownloadNetworkStatus
import com.beykus.beygram.data.media.SharedTgsEmojiRenderer
import com.beykus.beygram.data.media.TgsCompositionCache
import com.beykus.beygram.data.media.bucketEmojiSizePx
import com.beykus.beygram.data.settings.AutoDownloadSettingsStore
import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.playback.LocalPlaybackAccountId
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.motion.rememberAutoplayEligible
import kotlinx.coroutines.flow.StateFlow

private val STICKER_SIZE = 160.dp
internal const val TGS_MIME = "application/x-tgsticker"
internal const val WEBM_MIME = "video/webm"
internal const val WEBP_MIME = "image/webp"

/**
 * Стикер без рамки фото: ни цвета пузыря, ни подписи. TGS играет через Lottie, WebP показывается картинкой (анимированный
 * WebP идёт через [LocalMediaImage]). WebM пока показывает миниатюру: платформенный плеер может не вывести альфа-канал
 * таких стикеров, а проверить это без устройства нельзя.
 */
@Composable
internal fun StickerPreview(
    media: MessageMedia, file: MediaFileState, thumbnail: MediaFileState,
    downloadFile: (Int) -> Unit, onOpen: () -> Unit, onLongPress: (() -> Unit)?,
    /** Repository-backed automatic sticker download; explicit viewer actions still use [downloadFile]. */
    autoDownloadFile: ((AutoDownloadRequest) -> Unit)? = null,
    mediaSize: DpSize? = null
) {
    val webm = media.mimeType == WEBM_MIME
    val description = stringResource(R.string.media_kind_sticker)
    val context = LocalContext.current
    val accountId = LocalPlaybackAccountId.current
    val policySettings by remember(context, accountId) {
        AutoDownloadSettingsStore.getInstance(context.applicationContext, accountId).settings
    }.collectAsStateWithLifecycle()
    val policyNetwork by AutoDownloadNetworkStatus.network.collectAsStateWithLifecycle()
    LaunchedEffect(context) { AutoDownloadNetworkStatus.ensureStarted(context) }
    val autoDownload = autoDownloadFile ?: LocalAutoDownloadFile.current
    LaunchedEffect(media.fileId, media.thumbnailFileId, thumbnail.size, file.size, policySettings, policyNetwork) {
        media.thumbnailFileId?.let { thumbnailId ->
            val thumbnailRequest = AutoDownloadRequest(
                fileId = thumbnailId,
                kind = MediaKind.Photo,
                sizeBytes = thumbnail.size,
                isSecret = media.isSecret,
                isSecretChat = media.isSecretChat
            )
            if (autoDownload != null) autoDownload(thumbnailRequest) else downloadFile(thumbnailId)
        }
        if (!webm) {
            val stickerRequest = AutoDownloadRequest(
                fileId = media.fileId,
                kind = MediaKind.Sticker,
                sizeBytes = file.size,
                isSecret = media.isSecret,
                isSecretChat = media.isSecretChat
            )
            if (autoDownload != null) autoDownload(stickerRequest) else downloadFile(media.fileId)
        }
    }
    // An animated sticker plays only after it has been on screen for the autoplay dwell time. WebM is never drawn here.
    val autoplay = rememberAutoplayEligible(enabled = !webm)
    Box(
        Modifier.size(mediaSize ?: DpSize(STICKER_SIZE, STICKER_SIZE))
            .semantics { contentDescription = description }
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .then(autoplay.modifier),
        contentAlignment = Alignment.Center
    ) {
        val path = file.localPath
        val thumbnailPath = thumbnail.localPath
        val label: @Composable () -> Unit = { Text(description, color = beyGramPalette().textSecondary, fontSize = 13.sp) }
        // Если TGS не открылся, остаётся миниатюра, а без неё подпись: пустое место не годится.
        val tgsFallback: @Composable () -> Unit = {
            if (thumbnailPath != null) LocalMediaImage(thumbnailPath, Modifier.fillMaxSize(), fit = true) else label()
        }
        when {
            // While the sticker is not allowed to animate, its thumbnail stands in and the Lottie composition is not parsed.
            path != null && media.mimeType == TGS_MIME -> TgsSticker(
                path, Modifier.fillMaxSize(), playing = autoplay.eligible.value,
                poster = if (thumbnailPath != null) tgsFallback else null, fallback = tgsFallback
            )
            path != null && !webm -> LocalMediaImage(path, Modifier.fillMaxSize(), fit = true, animate = autoplay.eligible.value)
            thumbnailPath != null -> LocalMediaImage(thumbnailPath, Modifier.fillMaxSize(), fit = true)
            file.isDownloading || thumbnail.isDownloading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            // WebM без миниатюры показать нечем, а после ошибки загрузки ждать больше нечего: подписываем стикер, чтобы место не выглядело пустым.
            (webm && media.thumbnailFileId == null) || file.error != null || thumbnail.error != null -> label()
        }
    }
}

/**
 * Анимация TGS из локального файла. Пока файл читается, ничего не рисуется. Если TGS не открылся (битый архив или JSON,
 * который Lottie не разобрал), вместо анимации показывается [fallback]. Вне экрана композиция уходит и анимация останавливается.
 * Когда анимация не играет, вместо неё показывается [poster], если он передан: тогда TGS вообще не разбирается. Без постера
 * показывается первый кадр анимации.
 */
@Composable
internal fun TgsSticker(
    path: String,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    iterations: Int = LottieConstants.IterateForever,
    poster: (@Composable () -> Unit)? = null,
    fallback: @Composable () -> Unit
) {
    if (!playing && poster != null) {
        Box(modifier, contentAlignment = Alignment.Center) { poster() }
    } else {
        TgsAnimation(path, modifier, playing, iterations, poster, fallback)
    }
}

@Composable
private fun TgsAnimation(
    path: String,
    modifier: Modifier,
    playing: Boolean,
    iterations: Int,
    poster: (@Composable () -> Unit)?,
    fallback: @Composable () -> Unit
) {
    // produceState keeps its previous value when path changes, so the producer always asks the cache instead of trusting
    // the peeked value. A hit costs a file stat and a map lookup, and a rewritten file is parsed again.
    val loaded by produceState<Result<LottieComposition>?>(TgsCompositionCache.peek(path)?.let { Result.success(it) }, path) {
        value = TgsCompositionCache.load(path)
    }
    val result = loaded
    if (result == null) {
        if (poster != null) Box(modifier, contentAlignment = Alignment.Center) { poster() }
        return
    }
    val composition = result.getOrNull()
    if (composition != null) LottieSticker(composition, modifier, playing, iterations) else fallback()
}

@Composable
private fun LottieSticker(
    composition: LottieComposition,
    modifier: Modifier,
    playing: Boolean,
    iterations: Int
) {
    val progress by animateLottieCompositionAsState(composition, isPlaying = playing, restartOnPlay = true, iterations = iterations)
    // Not playing means the first frame, so a paused sticker never keeps the frame it had when it left the screen.
    LottieAnimation(composition = composition, progress = { if (playing) progress else 0f }, modifier = modifier)
}

/**
 * Пользовательский эмодзи как стикер: TGS играет анимацией, WebP показывается картинкой. Другие форматы и стикер, которого ещё
 * нет на устройстве, показывают [fallback], обычно текст эмодзи. WebM не рисуется, поэтому его не скачиваем.
 */
internal data class EmojiAnimationPolicy(
    val paused: Boolean = false,
    val priority: Int = PRIORITY_DEFAULT
) {
    companion object {
        const val PRIORITY_GRID = 0
        const val PRIORITY_DEFAULT = 1
        const val PRIORITY_PREVIEW = 10
    }
}

internal val LocalEmojiAnimationPolicy = androidx.compose.runtime.compositionLocalOf { EmojiAnimationPolicy() }

@Composable
internal fun CustomEmojiImage(
    emoji: CustomEmoji,
    size: Dp,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    animated: Boolean = true,
    fallback: @Composable () -> Unit
) {
    val file by remember(emoji.fileId) { observeFile(emoji.fileId) }.collectAsStateWithLifecycle()
    val path = file.localPath
    if (path == null && emoji.mimeType != WEBM_MIME) {
        CustomEmojiAutoDownload(
            fileId = emoji.fileId,
            fileSize = file.size,
            isDownloading = file.isDownloading,
            downloadFile = downloadFile
        )
    }
    val animPolicy = LocalEmojiAnimationPolicy.current
    // A custom emoji animates only after it has been on screen for the autoplay dwell time; until then its first frame shows.
    val autoplay = rememberAutoplayEligible(enabled = animated && !animPolicy.paused && (emoji.mimeType == TGS_MIME || emoji.mimeType == WEBP_MIME))
    val playing = animated && !animPolicy.paused && autoplay.eligible.value
    val maxDimensionPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        (size.roundToPx() * 2).coerceIn(48, 256)
    }
    val modifier = Modifier.size(size).then(autoplay.modifier)
    when {
        path != null && emoji.mimeType == TGS_MIME -> SharedTgsEmoji(
            path = path,
            size = size,
            playing = playing,
            priority = animPolicy.priority,
            modifier = modifier,
            fallback = fallback
        )
        path != null && emoji.mimeType == WEBP_MIME -> LocalMediaImage(path, modifier, fit = true, maxDimension = maxDimensionPx, animate = playing)
        else -> fallback()
    }
}

@Composable
private fun CustomEmojiAutoDownload(
    fileId: Int,
    fileSize: Long,
    isDownloading: Boolean,
    downloadFile: (Int) -> Unit
) {
    val context = LocalContext.current
    val accountId = LocalPlaybackAccountId.current
    val policySettings by remember(context, accountId) {
        AutoDownloadSettingsStore.getInstance(context.applicationContext, accountId).settings
    }.collectAsStateWithLifecycle()
    val policyNetwork by AutoDownloadNetworkStatus.network.collectAsStateWithLifecycle()
    LaunchedEffect(context) { AutoDownloadNetworkStatus.ensureStarted(context) }
    val autoDownload = LocalAutoDownloadFile.current
    LaunchedEffect(fileId, fileSize, isDownloading, policySettings, policyNetwork) {
        if (!isDownloading) {
            val request = AutoDownloadRequest(fileId = fileId, kind = MediaKind.Sticker, sizeBytes = fileSize)
            if (autoDownload != null) autoDownload(request) else downloadFile(fileId)
        }
    }
}

@Composable
private fun SharedTgsEmoji(
    path: String,
    size: Dp,
    playing: Boolean,
    priority: Int = EmojiAnimationPolicy.PRIORITY_DEFAULT,
    modifier: Modifier,
    fallback: @Composable () -> Unit
) {
    val context = LocalContext.current
    val accountId = LocalPlaybackAccountId.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val sizePx = remember(density, size) { bucketEmojiSizePx(with(density) { size.roundToPx() }) }
    val renderer = remember(context) { SharedTgsEmojiRenderer.default(context) }
    val initialFrame = remember(renderer, accountId, path, sizePx) {
        renderer.peekFirstFrame(accountId, path, sizePx)
    }
    val targetState = remember(renderer, accountId, path, sizePx) {
        androidx.compose.runtime.mutableStateOf<com.beykus.beygram.data.media.EmojiRenderTarget?>(null)
    }
    androidx.compose.runtime.DisposableEffect(renderer, accountId, path, sizePx) {
        val attached = renderer.attach(accountId = accountId, path = path, sizePx = sizePx, playing = playing, priority = priority)
        targetState.value = attached
        onDispose {
            targetState.value = null
            renderer.detach(attached)
        }
    }
    LaunchedEffect(renderer, targetState.value, playing, priority) {
        targetState.value?.let { renderer.updateTarget(it, playing = playing, priority = priority) }
    }
    val failed = targetState.value?.failedState?.value == true
    if (failed) {
        Box(modifier, contentAlignment = Alignment.Center) { fallback() }
        return
    }
    androidx.compose.foundation.Canvas(modifier) {
        val frame = targetState.value?.frameState?.value ?: initialFrame ?: return@Canvas
        drawImage(
            image = frame,
            dstSize = androidx.compose.ui.unit.IntSize(
                this.size.width.toInt().coerceAtLeast(1),
                this.size.height.toInt().coerceAtLeast(1)
            )
        )
    }
}
