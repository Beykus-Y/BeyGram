package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.media.AutoDownloadNetworkStatus
import com.beykus.beygram.data.settings.AutoDownloadSettingsStore
import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageReply
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.domain.model.ReactionOption
import com.beykus.beygram.domain.model.reactionMessage
import com.beykus.beygram.domain.model.showSenderHeader
import com.beykus.beygram.playback.LocalPlaybackAccountId
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.resolve
import kotlinx.coroutines.flow.StateFlow

internal data class ChatMessageListItem(
    val key: String,
    val messages: List<ChatMessage>,
    val mediaAlbumId: Long = 0L,
    /** Автоматический кружок, который ещё не принят TDLib; в ленте показывается вместо сообщения. */
    val pendingVideoNote: PendingVideoNote? = null,
    /** Разделитель «Непрочитанные»; в ленте он без сообщений. */
    val unreadSeparator: Boolean = false
)

/** Combines messages only when TDLib supplied the same nonzero mediaAlbumId. */
internal fun groupTelegramMediaAlbums(messages: List<ChatMessage>): List<ChatMessageListItem> {
    val albumMembers = messages.asSequence().filter { it.mediaAlbumId != 0L && it.media != null }.groupBy { it.mediaAlbumId }
    val emittedAlbums = mutableSetOf<Long>()
    return messages.asReversed().mapNotNull { message ->
        val albumId = message.mediaAlbumId
        if (albumId != 0L && message.media != null) {
            if (emittedAlbums.add(albumId)) ChatMessageListItem("album:$albumId", albumMembers[albumId].orEmpty(), albumId) else null
        } else ChatMessageListItem("message:${message.id}", listOf(message))
    }
}

/**
 * Sender headers for the newest-first feed. The list is walked chronologically so consecutive messages from one author
 * share one header, even when an album groups several of them. Service rows and separators do not belong to an author,
 * so they start a fresh block. Pending video notes are outgoing content for the same reason.
 */
internal fun senderHeaderVisibility(chat: ChatPreview, items: List<ChatMessageListItem>): Map<String, Boolean> {
    val headers = HashMap<String, Boolean>(items.size)
    var previous: ChatMessage? = null
    for (item in items.asReversed()) {
        val chronological = item.messages.sortedWith(compareBy<ChatMessage> { it.dateSeconds }.thenBy { it.id })
        if (chronological.isEmpty()) {
            previous = null
            continue
        }
        val first = chronological.first()
        headers[item.key] = showSenderHeader(chat, first, previous)
        previous = chronological.last()
    }
    return headers
}

/** Renders only messages carrying Telegram's nonzero mediaAlbumId as one album. */
@Composable
internal fun MessageAlbumBubble(
    messages: List<ChatMessage>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onDownloadFile: (Int) -> Unit,
    isRead: Boolean,
    highlighted: Boolean,
    onOpen: (Long) -> Unit,
    onActions: (Long) -> Unit,
    onReplyClick: (MessageReply) -> Unit,
    onReply: ((Long) -> Unit)? = null,
    onReaction: (Long, ReactionOption) -> Unit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>,
    onRetry: () -> Unit = {},
    commentsBar: (@Composable () -> Unit)? = null,
    /** Repository-backed automatic preview download; null falls back to the manual callback for previews. */
    onAutoDownloadFile: ((AutoDownloadRequest) -> Unit)? = null,
    /** Whether this album shows its author above the media; callers decide it from the chat type and the previous row. */
    showSenderHeader: Boolean = true,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelection: () -> Unit = {}
) {
    if (messages.isEmpty()) return
    val palette = beyGramPalette()
    val chronological = remember(messages) { messages.sortedWith(compareBy<ChatMessage> { it.dateSeconds }.thenBy { it.id }) }
    val latest = chronological.last()
    val metrics = LocalBubbleMetrics.current
    val shape = bubbleShape(latest.isOutgoing, metrics)
    val columns = if (chronological.size > 4) 3 else 2
    val captions = chronological.filter { it.text.isNotBlank() }
    val captionMessage = captions.lastOrNull() ?: latest
    val reply = chronological.firstNotNullOfOrNull { it.reply }
    val reactionOwner = reactionMessage(chronological)
    val selectionLabel = if (selectionMode) {
        stringResource(if (selected) R.string.message_selection_deselect else R.string.message_selection_select)
    } else null
    val selectionState = if (selectionMode) {
        stringResource(if (selected) R.string.message_selection_selected else R.string.message_selection_not_selected)
    } else null

    Column(Modifier.fillMaxWidth().padding(horizontal = metrics.sideInset), horizontalAlignment = if (latest.isOutgoing) Alignment.End else Alignment.Start) {
        SwipeToReply(enabled = onReply != null && captionMessage.sendState == MessageSendState.Sent, outgoing = latest.isOutgoing,
            onReply = { onReply?.invoke(captionMessage.id) }) { swipe ->
            Box(swipe) {
                BubbleLayout(
                    Modifier.widthIn(max = 292.dp).clip(shape)
                        .then(if (selected || highlighted) Modifier.border(2.dp, palette.accent, shape) else Modifier)
                        .background(if (latest.isOutgoing) palette.outgoingBubble else palette.incomingBubble)
                        .then(if (selectionMode) Modifier.combinedClickable(
                            onClickLabel = selectionLabel,
                            onLongClickLabel = selectionLabel,
                            onClick = onToggleSelection,
                            onLongClick = onToggleSelection
                        ) else Modifier)
                        .semantics {
                            if (selectionMode) {
                                this.selected = selected
                                if (selectionState != null) stateDescription = selectionState
                            }
                        },
                    overlayMeta = false,
                    body = {
                        Column {
                            if (showSenderHeader && !latest.isOutgoing && latest.senderName.isNotBlank()) {
                                Text(latest.senderName, color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp))
                            }
                            val rows = chronological.chunked(columns)
                            rows.forEachIndexed { index, rowMessages ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    rowMessages.forEach { message ->
                                        val media = message.media ?: return@forEach
                                        AlbumMediaTile(media, observeFile, onDownloadFile, onAutoDownloadFile, onOpen = { onOpen(message.id) }, onLongPress = { onActions(message.id) },
                                            modifier = Modifier.weight(1f), selectionMode = selectionMode, selectionLabel = selectionLabel,
                                            onToggleSelection = onToggleSelection)
                                    }
                                    repeat(columns - rowMessages.size) { SpacerTile(Modifier.weight(1f)) }
                                }
                                if (index < rows.lastIndex) Spacer(Modifier.height(2.dp))
                            }
                            if (reply != null) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(10.dp))
                                    .background(palette.surface.copy(alpha = 0.72f)).combinedClickable(
                                        onClickLabel = selectionLabel,
                                        onLongClickLabel = selectionLabel,
                                        onClick = { if (selectionMode) onToggleSelection() else onReplyClick(reply) },
                                        onLongClick = { if (selectionMode) onToggleSelection() else onActions(captionMessage.id) }
                                    )
                                    .padding(horizontal = 9.dp, vertical = 6.dp)) {
                                    Text(reply.senderName.ifBlank { stringResource(R.string.chat_reply_to_message) }, color = palette.accentInk,
                                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(reply.text.resolve(), color = palette.textSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (captions.isNotEmpty()) {
                                Column(Modifier.fillMaxWidth().combinedClickable(
                                    onClickLabel = selectionLabel,
                                    onLongClickLabel = selectionLabel,
                                    onClick = { if (selectionMode) onToggleSelection() },
                                    onLongClick = { if (selectionMode) onToggleSelection() else onActions(captionMessage.id) }
                                ).padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 2.dp)) {
                                    captions.forEach { FormattedMessageText(it, observeCustomEmoji, observeFile, onDownloadFile) }
                                }
                            }
                        }
                    },
                    meta = {
                        BubbleMeta(latest, isRead, retryAvailable = latest.isOutgoing && chronological.any { it.sendState == MessageSendState.Failed },
                            overlay = false, onRetry = onRetry)
                    },
                    bottomBar = { commentsBar?.invoke() }
                )
                if (selectionMode) SelectionCheckBadge(selected, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        MessageReactionChips(reactionOwner.reactions, observeCustomEmoji, observeFile, onDownloadFile, onToggle = { onReaction(reactionOwner.id, it) }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
    }
}

@Composable
private fun AlbumMediaTile(
    media: MessageMedia,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onDownloadFile: (Int) -> Unit,
    onAutoDownloadFile: ((AutoDownloadRequest) -> Unit)?,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selectionLabel: String? = null,
    onToggleSelection: () -> Unit = {}
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val mediaLabel = media.kind.label(context)
    val spoilerLabel = stringResource(R.string.media_spoiler_hint)
    val openLabel = stringResource(R.string.a11y_media_open, mediaLabel)
    val longPressLabel = stringResource(R.string.a11y_message_actions)
    val file by remember(media.fileId) { observeFile(media.fileId) }.collectAsStateWithLifecycle()
    val thumbId = media.thumbnailFileId ?: media.fileId
    val thumb by remember(thumbId) { observeFile(thumbId) }.collectAsStateWithLifecycle()
    val accountId = LocalPlaybackAccountId.current
    val policySettings by remember(context, accountId) {
        AutoDownloadSettingsStore.getInstance(context.applicationContext, accountId).settings
    }.collectAsStateWithLifecycle()
    val policyNetwork by AutoDownloadNetworkStatus.network.collectAsStateWithLifecycle()
    LaunchedEffect(context) { AutoDownloadNetworkStatus.ensureStarted(context) }
    var revealed by remember(media.fileId) { mutableStateOf(!media.hasSpoiler) }
    val visual = media.kind in setOf(MediaKind.Photo, MediaKind.Video, MediaKind.Animation)
    val autoDownload = onAutoDownloadFile ?: LocalAutoDownloadFile.current
    // Секретное вложение в альбоме не грузим и не показываем до открытия, как в ленте.
    val imagePath = if (media.isSecret) null else if (media.mimeType.startsWith("image/") && file.size <= 300_000) file.localPath ?: thumb.localPath else thumb.localPath
    LaunchedEffect(thumbId, revealed, thumb.size, media.isSecret, media.isSecretChat, media.hasSpoiler, policySettings, policyNetwork) {
        if (!media.isSecret && !media.isSecretChat && revealed && visual) {
            val request = AutoDownloadRequest(
                fileId = thumbId,
                kind = MediaKind.Photo,
                sizeBytes = thumb.size,
                isSecret = media.isSecret,
                isSecretChat = media.isSecretChat,
                hasSpoiler = media.hasSpoiler && !revealed
            )
            if (autoDownload != null) autoDownload(request) else onDownloadFile(thumbId)
        }
    }

    Box(modifier.aspectRatio(1f).padding(1.dp).clip(RoundedCornerShape(5.dp)).background(palette.disabled)
        .combinedClickable(
            onClickLabel = if (selectionMode) selectionLabel else if (revealed) openLabel else spoilerLabel,
            onLongClickLabel = if (selectionMode) selectionLabel else longPressLabel,
            onClick = { if (selectionMode) onToggleSelection() else if (!revealed) revealed = true else onOpen() },
            onLongClick = { if (selectionMode) onToggleSelection() else onLongPress() }
        )
        .semantics { if (revealed) contentDescription = mediaLabel },
        contentAlignment = Alignment.Center) {
        if (revealed && imagePath != null) LocalMediaImage(imagePath, Modifier.fillMaxSize())
        when {
            !revealed -> Text(stringResource(R.string.media_spoiler_hint), color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(6.dp))
            imagePath == null && (file.isDownloading || thumb.isDownloading) -> CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            imagePath == null -> Icon(Icons.Rounded.Download, null, tint = Color.White,
                modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape).padding(8.dp))
        }
        if (revealed && (media.kind == MediaKind.Video || media.kind == MediaKind.Animation || media.mimeType.startsWith("video/"))) {
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White,
                modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(7.dp))
        }
        if (media.durationSeconds > 0) Text(duration(media.durationSeconds), color = Color.White, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(5.dp)).padding(horizontal = 4.dp, vertical = 2.dp))
        if (file.isDownloading || file.isUploading) LinearProgressIndicator(
            progress = { if (file.isUploading) file.uploadProgress else file.downloadProgress },
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun SpacerTile(modifier: Modifier) {
    Box(modifier.aspectRatio(1f))
}
