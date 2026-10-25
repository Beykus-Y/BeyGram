package com.beykus.beygram.ui.screens.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.media.AutoDownloadNetworkStatus
import com.beykus.beygram.data.settings.AutoDownloadSettingsStore
import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageLinkPreview
import com.beykus.beygram.playback.LocalPlaybackAccountId
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.flow.StateFlow
import java.net.URI

/** Link cards open web pages only; unlike message entities they never pass other schemes to the system. */
internal fun safeLinkPreviewUrl(value: String): String? =
    safeMessageUrl(value)?.takeIf { runCatching { URI(it).scheme?.lowercase() }.getOrNull() in setOf("http", "https") }

/** Host shown when TDLib did not supply a site name; empty when the display URL has no parseable host. */
internal fun linkPreviewHost(value: String): String {
    val url = value.trim().let { if (!it.contains(":")) "https://$it" else it }
    return runCatching { URI(url).host }.getOrNull().orEmpty().removePrefix("www.")
}

@Composable
internal fun MessageLinkPreviewCard(
    preview: MessageLinkPreview,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onDownloadFile: (Int) -> Unit,
    modifier: Modifier = Modifier,
    autoDownloadFile: ((AutoDownloadRequest) -> Unit)? = null
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    // Navigate to TDLib's original URL; displayUrl is a shortened human-readable form that may not be navigable.
    val target = safeLinkPreviewUrl(preview.url.ifBlank { preview.displayUrl })
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier.fillMaxWidth().padding(top = 6.dp)
            .clip(shape)
            .background(palette.accent.copy(alpha = 0.08f))
            .then(if (target == null) Modifier else Modifier.clickable {
                runCatching { uriHandler.openUri(target) }.onFailure {
                    Toast.makeText(context, R.string.chat_link_failed, Toast.LENGTH_SHORT).show()
                }
            })
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        preview.photoFileId?.let { fileId ->
            val width = if (preview.photoWidth > 0 && preview.photoHeight > 0) {
                (64f * preview.photoWidth / preview.photoHeight).coerceIn(48f, 96f).dp
            } else 64.dp
            LinkPreviewPhoto(fileId, observeFile, onDownloadFile, autoDownloadFile, Modifier.width(width).height(64.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 2.dp, vertical = 1.dp)) {
            val site = preview.siteName.ifBlank { linkPreviewHost(preview.displayUrl) }
            if (site.isNotBlank()) Text(site, color = palette.accentInk, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val title = preview.title
            if (title.isNotBlank()) Text(title, color = palette.messageText, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (preview.description.isNotBlank()) Text(preview.description, color = palette.textSecondary, fontSize = 12.sp,
                lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The image is requested only when the card actually reaches composition, never for hidden or secret previews. */
@Composable
private fun LinkPreviewPhoto(
    fileId: Int,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onDownloadFile: (Int) -> Unit,
    autoDownloadFile: ((AutoDownloadRequest) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val accountId = LocalPlaybackAccountId.current
    val policySettings by remember(context, accountId) {
        AutoDownloadSettingsStore.getInstance(context.applicationContext, accountId).settings
    }.collectAsStateWithLifecycle()
    val policyNetwork by AutoDownloadNetworkStatus.network.collectAsStateWithLifecycle()
    LaunchedEffect(context) { AutoDownloadNetworkStatus.ensureStarted(context) }
    val autoDownload = autoDownloadFile ?: LocalAutoDownloadFile.current
    val file by remember(fileId) { observeFile(fileId) }.collectAsStateWithLifecycle()
    LaunchedEffect(fileId, file.localPath, file.size, file.isDownloading, policySettings, policyNetwork) {
        if (file.localPath == null && !file.isDownloading) {
            val request = AutoDownloadRequest(
                fileId = fileId,
                kind = MediaKind.Photo,
                sizeBytes = file.size,
                hasSpoiler = false
            )
            if (autoDownload != null) autoDownload(request) else onDownloadFile(fileId)
        }
    }
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(palette.surface2), contentAlignment = Alignment.Center) {
        val path = file.localPath
        if (path != null) LocalMediaImage(path, Modifier.fillMaxSize(), contentDescriptionText = stringResource(R.string.media_image_description))
        else if (file.isDownloading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}
