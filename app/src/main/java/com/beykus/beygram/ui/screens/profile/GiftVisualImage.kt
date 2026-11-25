package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.media.AutoDownloadNetworkStatus
import com.beykus.beygram.data.settings.AutoDownloadSettingsStore
import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.GiftVisual
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.StickerFileFormat
import com.beykus.beygram.playback.LocalPlaybackAccountId
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
 * The real gift sticker in a square of [size]. The states, in order of what the cell shows:
 * - a TGS plays as an animation once it is on the device, and only while the cell may animate;
 * - a WebP shows as an image once it is on the device;
 * - until then, while the file downloads, after a failed decode, and for a WebM (which is not drawn), the sticker's
 *   thumbnail shows, and the emoji [fallback] only when the gift has no thumbnail.
 * The sticker downloads on demand under the emoji auto-download policy and again after a network change; the
 * thumbnail downloads straight away because it is small.
 */
@Composable
internal fun GiftVisualImage(visual: GiftVisual?, size: Dp, fallback: @Composable () -> Unit) {
    val access = LocalGiftFileAccess.current
    if (visual == null || access == null) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) { fallback() }
        return
    }
    val context = LocalContext.current
    val accountId = LocalPlaybackAccountId.current
    val policySettings by remember(context, accountId) {
        AutoDownloadSettingsStore.getInstance(context.applicationContext, accountId).settings
    }.collectAsStateWithLifecycle()
    val policyNetwork by AutoDownloadNetworkStatus.network.collectAsStateWithLifecycle()
    LaunchedEffect(context) { AutoDownloadNetworkStatus.ensureStarted(context) }
    val autoDownload = LocalAutoDownloadFile.current
    val file by remember(visual.fileId, access) { access.observeFile(visual.fileId) }.collectAsStateWithLifecycle()
    // Without a thumbnail id the observed state is the sticker itself, which is never used as a thumbnail below.
    val thumbnailId = visual.thumbnailFileId
    val thumbnail by remember(thumbnailId, visual.fileId, access) {
        access.observeFile(thumbnailId ?: visual.fileId)
    }.collectAsStateWithLifecycle()
    val isWebm = visual.format == StickerFileFormat.Webm
    LaunchedEffect(visual.fileId, file.size, file.localPath, isWebm, policySettings, policyNetwork) {
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
    val description = stringResource(R.string.gifts_gift_generic)
    val path = file.localPath
    val thumbnailPath = if (thumbnailId != null) thumbnail.localPath else null
    // What the cell shows until the sticker can be drawn: the thumbnail, or the emoji when there is none.
    val preview: @Composable () -> Unit = {
        if (thumbnailPath != null) {
            LocalMediaImage(thumbnailPath, Modifier.fillMaxSize(), fit = true, animate = false)
        } else {
            fallback()
        }
    }
    Box(
        Modifier.size(size).then(autoplay.modifier).semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        when {
            // With animation off, TgsSticker shows the poster and never parses the file.
            path != null && visual.format == StickerFileFormat.Tgs ->
                TgsSticker(path, Modifier.fillMaxSize(), playing = autoplay.eligible.value, poster = preview, fallback = preview)
            path != null && visual.format == StickerFileFormat.Webp ->
                LocalMediaImage(path, Modifier.fillMaxSize(), fit = true, animate = autoplay.eligible.value)
            else -> preview()
        }
    }
}
