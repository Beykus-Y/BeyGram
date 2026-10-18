package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.airbnb.android.lottie.compose.LottieAnimation
import com.airbnb.android.lottie.compose.LottieCompositionSpec
import com.airbnb.android.lottie.compose.LottieConstants
import com.airbnb.android.lottie.compose.animateLottieCompositionAsState
import com.airbnb.android.lottie.compose.rememberLottieComposition
import com.beykus.beygram.R
import com.beykus.beygram.data.media.readTgsJson
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MessageMedia
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val STICKER_SIZE = 160.dp
private const val TGS_MIME = "application/x-tgsticker"
private const val WEBM_MIME = "video/webm"

/**
 * Стикер без рамки фото: ни цвета пузыря, ни подписи. TGS играет через Lottie, WebP показывается картинкой (анимированный
 * WebP идёт через [LocalMediaImage]). WebM пока показывает миниатюру: платформенный плеер может не вывести альфа-канал
 * таких стикеров, а проверить это без устройства нельзя.
 */
@Composable
internal fun StickerPreview(
    media: MessageMedia, file: MediaFileState, thumbnail: MediaFileState,
    downloadFile: (Int) -> Unit, onOpen: () -> Unit, onLongPress: (() -> Unit)?
) {
    val webm = media.mimeType == WEBM_MIME
    val description = stringResource(R.string.media_kind_sticker)
    LaunchedEffect(media.fileId, media.thumbnailFileId) {
        media.thumbnailFileId?.let(downloadFile)
        if (!webm) downloadFile(media.fileId)
    }
    Box(
        Modifier.size(STICKER_SIZE)
            .semantics { contentDescription = description }
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        contentAlignment = Alignment.Center
    ) {
        val path = file.localPath
        val thumbnailPath = thumbnail.localPath
        when {
            path != null && media.mimeType == TGS_MIME -> TgsSticker(path, Modifier.fillMaxSize())
            path != null && !webm -> LocalMediaImage(path, Modifier.fillMaxSize(), fit = true)
            thumbnailPath != null -> LocalMediaImage(thumbnailPath, Modifier.fillMaxSize(), fit = true)
            file.isDownloading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

/** Анимация TGS из локального файла. Пока файл не прочитан, ничего не рисуется; вне экрана композиция уходит и анимация останавливается. */
@Composable
internal fun TgsSticker(path: String, modifier: Modifier = Modifier, playing: Boolean = true) {
    val json by produceState<String?>(null, path) {
        value = withContext(Dispatchers.IO) { runCatching { readTgsJson(File(path)) }.getOrNull() }
    }
    json?.let { LottieSticker(it, modifier, playing) }
}

@Composable
private fun LottieSticker(json: String, modifier: Modifier, playing: Boolean) {
    val spec = remember(json) { LottieCompositionSpec.JsonString(json) }
    val composition by rememberLottieComposition(spec)
    val progress by animateLottieCompositionAsState(composition, isPlaying = playing, iterations = LottieConstants.IterateForever)
    LottieAnimation(composition = composition, progress = { progress }, modifier = modifier)
}
