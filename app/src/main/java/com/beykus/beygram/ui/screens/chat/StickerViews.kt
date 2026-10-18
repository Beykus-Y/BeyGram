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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.beykus.beygram.R
import com.beykus.beygram.data.media.readTgsJson
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.ui.components.beyGramPalette
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
        val label: @Composable () -> Unit = { Text(description, color = beyGramPalette().textSecondary, fontSize = 13.sp) }
        // Если TGS не открылся, остаётся миниатюра, а без неё подпись: пустое место не годится.
        val tgsFallback: @Composable () -> Unit = {
            if (thumbnailPath != null) LocalMediaImage(thumbnailPath, Modifier.fillMaxSize(), fit = true) else label()
        }
        when {
            path != null && media.mimeType == TGS_MIME -> TgsSticker(path, Modifier.fillMaxSize(), fallback = tgsFallback)
            path != null && !webm -> LocalMediaImage(path, Modifier.fillMaxSize(), fit = true)
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
 */
@Composable
internal fun TgsSticker(path: String, modifier: Modifier = Modifier, playing: Boolean = true, fallback: @Composable () -> Unit) {
    val decoded by produceState<Result<String>?>(null, path) {
        value = withContext(Dispatchers.IO) { runCatching { readTgsJson(File(path)) } }
    }
    val result = decoded ?: return
    val json = result.getOrNull()
    if (json != null) LottieSticker(json, modifier, playing, fallback) else fallback()
}

@Composable
private fun LottieSticker(json: String, modifier: Modifier, playing: Boolean, fallback: @Composable () -> Unit) {
    val spec = remember(json) { LottieCompositionSpec.JsonString(json) }
    val result = rememberLottieComposition(spec)
    if (result.isFailure) {
        fallback()
        return
    }
    val progress by animateLottieCompositionAsState(result.value, isPlaying = playing, iterations = LottieConstants.IterateForever)
    LottieAnimation(composition = result.value, progress = { progress }, modifier = modifier)
}
