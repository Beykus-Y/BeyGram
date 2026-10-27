package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.StickerFileFormat
import com.beykus.beygram.domain.model.StickerItem
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun StickerGrid(
    stickers: List<StickerItem>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    /** Null makes the grid read-only, for a set opened from a view without a send target. */
    onClick: ((StickerItem) -> Unit)?,
    onLongClick: ((StickerItem) -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(76.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(stickers, key = { it.key }) { item ->
            StickerCell(
                item, observeFile, downloadFile,
                onClick = onClick?.let { action -> { action(item) } },
                onLongClick = onLongClick?.let { action -> { action(item) } }
            )
        }
        if (footer != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "footer") { footer() }
        }
    }
}

@Composable
private fun StickerCell(
    item: StickerItem,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?
) {
    val palette = beyGramPalette()
    val emoji = item.emoji.ifBlank { stringResource(R.string.media_kind_sticker) }
    val description = stringResource(R.string.sticker_picker_item_description, emoji)
    val clickModifier = if (onClick == null && onLongClick == null) {
        Modifier
    } else {
        Modifier.combinedClickable(
            role = Role.Button,
            onClickLabel = if (onClick != null) stringResource(R.string.common_send) else null,
            onLongClickLabel = if (onLongClick != null) stringResource(R.string.sticker_picker_send_options) else null,
            onClick = onClick ?: {},
            onLongClick = onLongClick
        )
    }
    Box(
        Modifier.size(68.dp).clip(RoundedCornerShape(12.dp))
            .then(clickModifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        StickerArtwork(item, Modifier.fillMaxSize(), observeFile, downloadFile)
        if (item.premium) {
            Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.sticker_picker_premium_badge),
                tint = palette.accent, modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(14.dp))
        }
    }
}

/** Draws one sticker and downloads only its own preview. The caller owns the clickable surface. */
@Composable
internal fun StickerArtwork(
    item: StickerItem,
    modifier: Modifier,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit
) {
    val palette = beyGramPalette()
    val motion = systemMotionEnabled()
    val file by remember(item.fileId) { observeFile(item.fileId) }.collectAsStateWithLifecycle()
    val thumbnail = rememberOptionalFile(item.thumbnailFileId, observeFile)
    LaunchedEffect(item.fileId, item.thumbnailFileId, item.format) {
        item.thumbnailFileId?.let(downloadFile)
        // WebM stickers cannot be drawn by the picker; their still thumbnail is enough and avoids a video decoder.
        if (item.format != StickerFileFormat.Webm) downloadFile(item.fileId)
    }
    val emoji = item.emoji.ifBlank { stringResource(R.string.media_kind_sticker) }
    // A WebM/TGS thumbnail is not an image an ImageDecoder can open; only static thumbnails are drawn.
    val thumbnailPath = thumbnail.localPath.takeIf { item.thumbnailIsStatic }
    val fullPath = file.localPath
    Box(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val fallback: @Composable () -> Unit = {
            if (thumbnailPath != null) LocalMediaImage(thumbnailPath, Modifier.fillMaxSize(), fit = true, maxDimension = 256)
            else Text(emoji, fontSize = 24.sp, textAlign = TextAlign.Center)
        }
        when {
            fullPath != null && item.format == StickerFileFormat.Tgs ->
                TgsSticker(fullPath, Modifier.fillMaxSize(), playing = motion, fallback = fallback)
            fullPath != null && item.format != StickerFileFormat.Webm ->
                LocalMediaImage(fullPath, Modifier.fillMaxSize(), fit = true, maxDimension = 256)
            thumbnailPath != null ->
                LocalMediaImage(thumbnailPath, Modifier.fillMaxSize(), fit = true, maxDimension = 256)
            file.isDownloading || thumbnail.isDownloading ->
                CircularProgressIndicator(Modifier.size(18.dp), color = palette.accent, strokeWidth = 2.dp)
            else -> Text(emoji, fontSize = 24.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun PickerCenter(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
internal fun PickerEmpty(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = beyGramPalette().textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun PickerError(text: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp).errorAnnouncement(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(text, color = beyGramPalette().danger, fontSize = 14.sp, textAlign = TextAlign.Center)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
    }
}

/** Collects a nullable TDLib file; a missing thumbnail never changes the number of remembered states. */
@Composable
private fun rememberOptionalFile(fileId: Int?, observeFile: (Int) -> StateFlow<MediaFileState>): MediaFileState {
    val placeholder = remember { MutableStateFlow(MediaFileState(0)) }
    val flow = remember(fileId) { fileId?.let(observeFile) ?: placeholder }
    val state by flow.collectAsStateWithLifecycle()
    return state
}
