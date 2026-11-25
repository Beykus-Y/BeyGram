package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.GiftVisual
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.StickerFileFormat
import com.beykus.beygram.ui.motion.rememberAutoplayEligible
import com.beykus.beygram.ui.screens.chat.LocalAutoDownloadFile
import com.beykus.beygram.ui.screens.chat.LocalMediaImage
import com.beykus.beygram.ui.screens.chat.TgsSticker
import kotlinx.coroutines.flow.StateFlow

/** File access for gift stickers. [GiftsScreen]'s caller provides it once, so every gift row can draw its own sticker. */
internal class GiftFileAccess(
    val observeFile: (Int) -> StateFlow<MediaFileState>,
    val downloadFile: (Int) -> Unit
)

internal val LocalGiftFileAccess = compositionLocalOf<GiftFileAccess?> { null }

/** Provides the file access that the gift rows below read. */
@Composable
internal fun ProvideGiftFileAccess(access: GiftFileAccess, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGiftFileAccess provides access, content = content)
}

/**
 * The real gift sticker in a square of [size]: a TGS plays as an animation, a WebP shows as an image. A WebM, a gift
 * without a visual, a sticker that is not downloaded yet and a failed file all show [fallback], the emoji. The file
 * downloads once it is needed, through the same auto-download policy the emoji stickers use.
 */
@Composable
internal fun GiftVisualImage(visual: GiftVisual?, size: Dp, fallback: @Composable () -> Unit) {
    val access = LocalGiftFileAccess.current
    if (visual == null || access == null || visual.format == StickerFileFormat.Webm) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) { fallback() }
        return
    }
    val file by remember(visual.fileId, access) { access.observeFile(visual.fileId) }.collectAsStateWithLifecycle()
    val autoDownload = LocalAutoDownloadFile.current
    LaunchedEffect(visual.fileId, file.size, file.localPath) {
        if (!file.isDownloading && file.localPath == null) {
            val request = AutoDownloadRequest(fileId = visual.fileId, kind = MediaKind.Sticker, sizeBytes = file.size)
            if (autoDownload != null) autoDownload(request) else access.downloadFile(visual.fileId)
        }
    }
    val animated = visual.format == StickerFileFormat.Tgs || visual.format == StickerFileFormat.Webp
    val autoplay = rememberAutoplayEligible(enabled = animated)
    val path = file.localPath
    val modifier = Modifier.size(size).then(autoplay.modifier)
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            path != null && visual.format == StickerFileFormat.Tgs ->
                TgsSticker(path, Modifier.size(size), playing = autoplay.eligible.value, fallback = fallback)
            path != null && visual.format == StickerFileFormat.Webp ->
                LocalMediaImage(path, Modifier.size(size), fit = true, animate = autoplay.eligible.value)
            else -> fallback()
        }
    }
}
