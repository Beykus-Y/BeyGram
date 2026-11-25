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
 * The real gift sticker in a square of [size]. A TGS plays as an animation and a WebP shows as an image once the file
 * is on the device. Until then, and for a WebM (which is not drawn), the sticker's thumbnail shows, and with no
 * thumbnail the emoji [fallback] does. The sticker downloads on demand under the emoji auto-download policy; the
 * thumbnail downloads straight away because it is small.
 */
@Composable
internal fun GiftVisualImage(visual: GiftVisual?, size: Dp, fallback: @Composable () -> Unit) {
    val access = LocalGiftFileAccess.current
    if (visual == null || access == null) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) { fallback() }
        return
    }
    val autoDownload = LocalAutoDownloadFile.current
    val file by remember(visual.fileId, access) { access.observeFile(visual.fileId) }.collectAsStateWithLifecycle()
    // Without a thumbnail id the observed state is the sticker itself, which is never used as a thumbnail below.
    val thumbnailId = visual.thumbnailFileId
    val thumbnail by remember(thumbnailId, visual.fileId, access) {
        access.observeFile(thumbnailId ?: visual.fileId)
    }.collectAsStateWithLifecycle()
    val isWebm = visual.format == StickerFileFormat.Webm
    LaunchedEffect(visual.fileId, file.size, file.localPath, isWebm) {
        if (!isWebm && !file.isDownloading && file.localPath == null) {
            val request = AutoDownloadRequest(fileId = visual.fileId, kind = MediaKind.Sticker, sizeBytes = file.size)
            if (autoDownload != null) autoDownload(request) else access.downloadFile(visual.fileId)
        }
    }
    LaunchedEffect(thumbnailId, thumbnail.size, thumbnail.localPath) {
        val id = thumbnailId ?: return@LaunchedEffect
        if (!thumbnail.isDownloading && thumbnail.localPath == null) access.downloadFile(id)
    }
    val animated = visual.format == StickerFileFormat.Tgs || visual.format == StickerFileFormat.Webp
    val autoplay = rememberAutoplayEligible(enabled = animated)
    val path = file.localPath
    val thumbnailPath = if (thumbnailId != null) thumbnail.localPath else null
    Box(Modifier.size(size).then(autoplay.modifier), contentAlignment = Alignment.Center) {
        when {
            path != null && visual.format == StickerFileFormat.Tgs ->
                TgsSticker(path, Modifier.fillMaxSize(), playing = autoplay.eligible.value, fallback = fallback)
            path != null && visual.format == StickerFileFormat.Webp ->
                LocalMediaImage(path, Modifier.fillMaxSize(), fit = true, animate = autoplay.eligible.value)
            thumbnailPath != null -> LocalMediaImage(thumbnailPath, Modifier.fillMaxSize(), fit = true, animate = false)
            else -> fallback()
        }
    }
}
