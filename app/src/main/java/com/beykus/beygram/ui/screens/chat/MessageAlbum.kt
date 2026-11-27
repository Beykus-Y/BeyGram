package com.beykus.beygram.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import com.beykus.beygram.ui.design.styledShape
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.playback.PlaybackOrigin
import com.beykus.beygram.ui.accessibility.systemReducedMotion
import com.beykus.beygram.data.media.AutoDownloadNetworkStatus
import com.beykus.beygram.data.settings.AutoDownloadSettingsStore
import com.beykus.beygram.deeplink.MessageEntityLink
import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.ChatMemoryVersion
import com.beykus.beygram.domain.model.ProfileMember
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
import com.beykus.beygram.ui.motion.rememberAutoplayEligible
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

/** contentType для LazyColumn: строки одного типа переиспользуют композицию друг друга при прокрутке. */
internal fun chatListContentType(item: ChatMessageListItem): String = when {
    item.unreadSeparator -> "unread"
    item.pendingVideoNote != null -> "pendingVideoNote"
    item.mediaAlbumId != 0L -> "album"
    else -> item.messages.firstOrNull()?.let(::chatMessageContentType) ?: "empty"
}

internal fun chatMessageContentType(message: ChatMessage): String = when {
    message.serviceText != null -> "service"
    message.media != null -> "media:${message.media.kind}"
    else -> "text"
}

/** Combines messages only when TDLib supplied the same nonzero mediaAlbumId. */
internal fun groupTelegramMediaAlbums(messages: List<ChatMessage>): List<ChatMessageListItem> {
    val albumMembers = HashMap<Long, MutableList<ChatMessage>>()
    for (message in messages) {
        if (message.mediaAlbumId != 0L && message.media != null) albumMembers.getOrPut(message.mediaAlbumId) { mutableListOf() }.add(message)
    }
    val items = ArrayList<ChatMessageListItem>(messages.size)
    for (index in messages.lastIndex downTo 0) {
        val message = messages[index]
        val albumId = message.mediaAlbumId
        if (albumId != 0L && message.media != null) {
            // The newest member emits the album with all its members; the older ones find it already taken.
            val members = albumMembers.remove(albumId) ?: continue
            items.add(ChatMessageListItem("album:$albumId", members, albumId))
        } else {
            items.add(ChatMessageListItem("message:${message.id}", listOf(message)))
        }
    }
    return items
}

private val chronologicalMessages = compareBy<ChatMessage> { it.dateSeconds }.thenBy { it.id }

/**
 * Sender headers for the newest-first feed. The list is walked chronologically so consecutive messages from one author
 * share one header, even when an album groups several of them. Service rows and separators do not belong to an author,
 * so they start a fresh block. Pending video notes are outgoing content for the same reason. [topicOf] is set only by the
 * all-messages feed of a forum: a change of topic starts a fresh block there too, because its topic label opens above the row.
 */
internal fun senderHeaderVisibility(
    chat: ChatPreview,
    items: List<ChatMessageListItem>,
    topicOf: ((ChatMessage) -> Long)? = null
): Map<String, Boolean> {
    val headers = HashMap<String, Boolean>(items.size)
    var previous: ChatMessage? = null
    for (item in items.asReversed()) {
        // A row with one message, which is nearly every row, is already in order.
        val chronological = if (item.messages.size > 1) item.messages.sortedWith(chronologicalMessages) else item.messages
        if (chronological.isEmpty()) {
            previous = null
            continue
        }
        val first = chronological.first()
        val topicBreak = topicOf != null && previous != null && topicOf(previous) != topicOf(first)
        headers[item.key] = showSenderHeader(chat, first, if (topicBreak) null else previous)
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
    onPlayed: (Long) -> Unit = {},
    onReply: ((Long) -> Unit)? = null,
    onReaction: (Long, ReactionOption) -> Unit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>,
    onRetry: () -> Unit = {},
    commentsBar: (@Composable () -> Unit)? = null,
    /** Repository-backed automatic preview download; null falls back to the manual callback for previews. */
    onAutoDownloadFile: ((AutoDownloadRequest) -> Unit)? = null,
    /** Whether this album shows its author above the media; callers decide it from the chat type and the previous row. */
    showSenderHeader: Boolean = true,
    senderMember: ProfileMember? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onOpenMessageEntity: ((MessageEntityLink) -> Unit)? = null,
    /** Reports the layout of the album bubble; every member's viewer opens from the same rectangle. */
    onMediaBounds: ((LayoutCoordinates) -> Unit)? = null,
    /** Loads the earlier texts that Chat Memory kept for a member; without it the album shows no versions link. */
    onLoadVersions: (suspend (chatId: Long, messageId: Long) -> List<ChatMemoryVersion>)? = null,
    composerMediaLaunches: Map<Long, ComposerMediaLaunch> = emptyMap(),
    loadQuickReactions: (suspend (Long, Long) -> Result<List<ReactionOption>>)? = null,
    onComposerMediaLaunchFinished: (ComposerMediaLaunch) -> Unit = {}
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

    // The latest member that kept earlier texts; the album's versions link opens its history.
    val versionsOwner = chronological.lastOrNull { (it.memory?.versionCount ?: 0) > 0 }
    val visualMosaic = chronological.size in 2..10 && chronological.all {
        val kind = it.media?.kind
        kind == MediaKind.Photo || kind == MediaKind.Video || kind == MediaKind.Animation
    }
    val fileList = chronological.all { it.media?.kind == MediaKind.Document || it.media?.kind == MediaKind.Audio }
    val retryAvailable = latest.isOutgoing && chronological.any { it.sendState == MessageSendState.Failed }
    val timeOverLastTile = visualMosaic && captions.isEmpty() && reply == null && !retryAvailable && versionsOwner == null
    val loadVersions = onLoadVersions
    var showVersions by remember(latest.id) { mutableStateOf(false) }
    val openVersions: () -> Unit = { showVersions = true }
    if (showVersions && loadVersions != null && versionsOwner != null) {
        ChatMemoryVersionsSheet(versionsOwner, loadVersions, onDismiss = { showVersions = false })
    }

    val quickReaction = if (!selectionMode && loadQuickReactions != null && reactionOwner.sendState == MessageSendState.Sent) {
        rememberBubbleQuickReaction(reactionOwner, loadQuickReactions, onReaction)
    } else null
    Column(
        Modifier.fillMaxWidth()
            .quickReactionOnFreeSpace(reactionOwner.chatId, reactionOwner.id, quickReaction)
            .padding(horizontal = metrics.sideInset),
        horizontalAlignment = if (latest.isOutgoing) Alignment.End else Alignment.Start
    ) {
        SwipeToReply(enabled = onReply != null && captionMessage.sendState == MessageSendState.Sent, outgoing = latest.isOutgoing,
            onReply = { onReply?.invoke(captionMessage.id) }) { swipe ->
            Box(swipe) {
                BubbleLayout(
                    Modifier.widthIn(max = 292.dp).then(if (composerMediaLaunches.isEmpty()) Modifier.clip(shape) else Modifier)
                        .then(if (onMediaBounds != null) Modifier.onGloballyPositioned { onMediaBounds(it) } else Modifier)
                        .then(if (selected || highlighted) Modifier.border(2.dp, palette.accent, shape) else Modifier)
                        .background(if (latest.isOutgoing) palette.outgoingBubble else palette.incomingBubble, shape)
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
                                ChatGroupAuthorName(
                                    latest.senderName, senderMember,
                                    Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
                                )
                            }
                            chronological.firstOrNull { it.isForwarded }?.let { ForwardedMessageHeader(it) }
                            if (visualMosaic) {
                                val aspects = chronological.map { message ->
                                    val media = requireNotNull(message.media)
                                    if (media.width > 0 && media.height > 0) media.width.toDouble() / media.height else 1.0
                                }
                                VisualAlbumMosaic(
                                    aspects = aspects,
                                    tile = { tileIndex ->
                                        val message = chronological[tileIndex]
                                        AlbumMediaTile(
                                            requireNotNull(message.media), observeFile, onDownloadFile, onAutoDownloadFile,
                                            onOpen = { onOpen(message.id) }, onLongPress = { onActions(message.id) },
                                            selectionMode = selectionMode, selectionLabel = selectionLabel,
                                            onToggleSelection = onToggleSelection,
                                            composerMediaLaunch = composerMediaLaunches[message.id],
                                            mosaicTile = true,
                                            reserveEndForTime = timeOverLastTile && tileIndex == chronological.lastIndex,
                                            onComposerMediaLaunchFinished = onComposerMediaLaunchFinished
                                        )
                                    },
                                    metadata = if (timeOverLastTile) {
                                        { BubbleMeta(latest, isRead, retryAvailable = false, overlay = true, onRetry = onRetry) }
                                    } else null
                                )
                            } else if (fileList) {
                                val accountId = LocalPlaybackAccountId.current
                                Column(Modifier.fillMaxWidth().padding(horizontal = metrics.textInset)) {
                                    chronological.forEach { message ->
                                        key(message.id) {
                                            MediaPreview(
                                                requireNotNull(message.media), observeFile, onDownloadFile,
                                                onOpen = { if (selectionMode) onToggleSelection() else onOpen(message.id) },
                                                onLongPress = { if (selectionMode) onToggleSelection() else onActions(message.id) },
                                                onPlayed = { onPlayed(message.id) },
                                                origin = if (message.sendState == MessageSendState.Sent) {
                                                    PlaybackOrigin(message.chatId, message.id, accountId)
                                                } else null,
                                                viewOnce = message.isViewOnce,
                                                autoDownloadFile = onAutoDownloadFile,
                                                selectionActive = selectionMode
                                            )
                                        }
                                    }
                                }
                            } else {
                                val rows = chronological.chunked(columns)
                                rows.forEachIndexed { index, rowMessages ->
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        rowMessages.forEach { message ->
                                            val media = message.media ?: return@forEach
                                            AlbumMediaTile(media, observeFile, onDownloadFile, onAutoDownloadFile, onOpen = { onOpen(message.id) }, onLongPress = { onActions(message.id) },
                                                modifier = Modifier.weight(1f), selectionMode = selectionMode, selectionLabel = selectionLabel,
                                                onToggleSelection = onToggleSelection,
                                                composerMediaLaunch = composerMediaLaunches[message.id],
                                                onComposerMediaLaunchFinished = onComposerMediaLaunchFinished)
                                        }
                                        repeat(columns - rowMessages.size) { SpacerTile(Modifier.weight(1f)) }
                                    }
                                    if (index < rows.lastIndex) Spacer(Modifier.height(2.dp))
                                }
                            }
                            if (reply != null) {
                                ReplyQuote(
                                    reply = reply,
                                    modifier = Modifier.padding(horizontal = metrics.textInset, vertical = 5.dp),
                                    onClick = { if (selectionMode) onToggleSelection() else onReplyClick(reply) },
                                    onLongClick = { if (selectionMode) onToggleSelection() else onActions(captionMessage.id) },
                                    onClickLabel = selectionLabel,
                                    onLongClickLabel = selectionLabel,
                                )
                            }
                            if (captions.isNotEmpty()) {
                                Column(Modifier.fillMaxWidth().combinedClickable(
                                    onClickLabel = selectionLabel,
                                    onLongClickLabel = selectionLabel,
                                    onClick = { if (selectionMode) onToggleSelection() },
                                    onLongClick = { if (selectionMode) onToggleSelection() else onActions(captionMessage.id) }
                                ).padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 2.dp)) {
                                    captions.forEach { FormattedMessageText(it, observeCustomEmoji, observeFile, onDownloadFile, onOpenEntity = onOpenMessageEntity, entityClicksEnabled = !selectionMode) }
                                }
                            }
                        }
                    },
                    meta = {
                        if (!timeOverLastTile) BubbleMeta(latest, isRead, retryAvailable = retryAvailable,
                            overlay = false, onRetry = onRetry,
                            onShowVersions = if (loadVersions != null && versionsOwner != null) openVersions else null,
                            versionCount = versionsOwner?.memory?.versionCount ?: 0)
                    },
                    bottomBar = { commentsBar?.invoke() },
                    footer = {
                        // One reaction owner for the whole Telegram album, anchored inside its
                        // shared bubble instead of a detached row underneath the media.
                        if (!selectionMode) MessageReactionChips(
                            reactionOwner.reactions, observeCustomEmoji, observeFile, onDownloadFile,
                            onToggle = { onReaction(reactionOwner.id, it) },
                            modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 2.dp)
                        )
                    }
                )
                if (selectionMode) SelectionCheckBadge(selected, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
    }
}

/** Rectangles depend on media metadata and constraints. File progress never invalidates the cache. */
@Composable
private fun VisualAlbumMosaic(
    aspects: List<Double>,
    tile: @Composable (Int) -> Unit,
    metadata: (@Composable () -> Unit)?
) {
    val density = LocalDensity.current
    val gutter = with(density) { 2.dp.roundToPx() }
    val maxHeight = with(density) { 420.dp.roundToPx() }
    val minCell = with(density) { 54.dp.roundToPx() }
    val cache = remember(aspects, gutter, maxHeight, minCell) { mutableMapOf<Pair<Int, Int>, AlbumMosaic>() }
    Layout(
        content = {
            aspects.indices.forEach { index -> Box { tile(index) } }
            if (metadata != null) Box { metadata() }
        },
        modifier = Modifier.fillMaxWidth()
    ) { children, constraints ->
        val width = constraints.maxWidth
        val heightCap = minOf(maxHeight, constraints.maxHeight)
        val dimensions = width to heightCap
        val mosaic = cache[dimensions] ?: chooseAlbumMosaic(width, heightCap, gutter, minCell, aspects).also {
            cache.clear()
            cache[dimensions] = it
        }
        if (mosaic.tiles.isEmpty()) {
            layout(width, constraints.minHeight) {}
        } else {
            val tiles = mosaic.tiles.mapIndexed { index, rect ->
                children[index].measure(Constraints.fixed(rect.width, rect.height))
            }
            val last = mosaic.tiles.last()
            val inset = 4.dp.roundToPx()
            val time = children.getOrNull(aspects.size)?.measure(Constraints(
                maxWidth = (last.width - inset).coerceAtLeast(0),
                maxHeight = (last.height - inset).coerceAtLeast(0)
            ))
            layout(width, mosaic.height.coerceAtLeast(constraints.minHeight)) {
                tiles.zip(mosaic.tiles).forEach { (placeable, rect) -> placeable.placeRelative(rect.x, rect.y) }
                if (time != null) {
                    time.placeRelative(
                        (last.x + last.width - time.width - inset).coerceAtLeast(last.x),
                        (last.y + last.height - time.height - inset).coerceAtLeast(last.y)
                    )
                }
            }
        }
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
    onToggleSelection: () -> Unit = {},
    composerMediaLaunch: ComposerMediaLaunch? = null,
    mosaicTile: Boolean = false,
    reserveEndForTime: Boolean = false,
    onComposerMediaLaunchFinished: (ComposerMediaLaunch) -> Unit = {}
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val mediaLabel = media.kind.label(context)
    val spoilerLabel = stringResource(R.string.media_spoiler_hint)
    val reduced = systemReducedMotion()
    val openLabel = stringResource(R.string.a11y_media_open, mediaLabel)
    val longPressLabel = stringResource(R.string.a11y_message_actions)
    // Only the progress indicator reads byte counters, through the handle.
    val fileHandle = rememberMediaFile(media.fileId, observeFile)
    val file by fileHandle.content
    val thumbId = media.thumbnailFileId ?: media.fileId
    val thumbHandle = rememberMediaFile(thumbId, observeFile)
    val thumb by thumbHandle.content
    val accountId = LocalPlaybackAccountId.current
    val policySettings by remember(context, accountId) {
        AutoDownloadSettingsStore.getInstance(context.applicationContext, accountId).settings
    }.collectAsStateWithLifecycle()
    val policyNetwork by AutoDownloadNetworkStatus.network.collectAsStateWithLifecycle()
    LaunchedEffect(context) { AutoDownloadNetworkStatus.ensureStarted(context) }
    var revealed by remember(media.fileId) { mutableStateOf(!media.hasSpoiler) }
    val visual = media.kind in setOf(MediaKind.Photo, MediaKind.Video, MediaKind.Animation)
    val autoDownload = onAutoDownloadFile ?: LocalAutoDownloadFile.current
    // A GIF in an album animates only after it has been on screen for the autoplay dwell time, as it does in the bubble.
    val autoplay = rememberAutoplayEligible(enabled = media.mimeType == "image/gif")
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

    val flight = composerMediaLaunchModifier(composerMediaLaunch, onComposerMediaLaunchFinished)
    val tileShape = if (mosaicTile) Modifier.fillMaxSize() else Modifier.aspectRatio(1f).padding(1.dp)
    val tileClip = if (mosaicTile) Modifier else Modifier.clip(styledShape(5.dp))
    Box(modifier.then(tileShape).then(flight).then(tileClip).background(palette.disabled)
        .combinedClickable(
            onClickLabel = if (selectionMode) selectionLabel else if (revealed) openLabel else spoilerLabel,
            onLongClickLabel = if (selectionMode) selectionLabel else longPressLabel,
            onClick = { if (selectionMode) onToggleSelection() else if (!revealed) revealed = true else onOpen() },
            onLongClick = { if (selectionMode) onToggleSelection() else onLongPress() }
        )
        .semantics { if (revealed) contentDescription = mediaLabel }
        .then(autoplay.modifier),
        contentAlignment = Alignment.Center) {
        val minithumbnail = if (media.isSecret) null else media.minithumbnail
        val soft = softMinithumbnailSlot(minithumbnail)
        val thumbPath = thumb.localPath
        // The tile keeps the preview under the original while the original decodes, so it never flashes a spinner.
        val originalPlaceholder: (@Composable () -> Unit)? = if (imagePath != thumbPath && thumbPath != null) {
            val layer: @Composable () -> Unit = { LocalMediaImage(thumbPath, Modifier.fillMaxSize(), maxDimension = ALBUM_TILE_DECODE_DIMENSION, animate = false, placeholder = soft) }
            layer
        } else soft
        if (revealed && imagePath != null) LocalMediaImage(
            imagePath, Modifier.fillMaxSize(), maxDimension = ALBUM_TILE_DECODE_DIMENSION, animate = autoplay.eligible.value,
            placeholder = originalPlaceholder
        )
        else if (revealed && minithumbnail != null) SoftMinithumbnail(minithumbnail, Modifier.fillMaxSize())
        // Drawn after the image layers so the mask covers them; it fades out on reveal.
        AnimatedVisibility(
            visible = !revealed,
            enter = EnterTransition.None,
            exit = if (reduced) ExitTransition.None else fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            SafeSpoilerOverlay(seed = media.fileId, modifier = Modifier.fillMaxSize())
        }
        when {
            !revealed -> Unit
            imagePath == null && (file.isDownloading || thumb.isDownloading) -> CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            imagePath == null -> Icon(Icons.Rounded.Download, null, tint = Color.White,
                modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape).padding(8.dp))
        }
        if (revealed && (media.kind == MediaKind.Video || media.kind == MediaKind.Animation || media.mimeType.startsWith("video/"))) {
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White,
                modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(7.dp))
        }
        if (media.durationSeconds > 0) Text(duration(media.durationSeconds), color = Color.White, fontSize = 11.sp,
            modifier = Modifier.align(if (reserveEndForTime) Alignment.TopStart else Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.55f), styledShape(5.dp)).padding(horizontal = 4.dp, vertical = 2.dp))
        if (file.isDownloading || file.isUploading) LinearProgressIndicator(
            progress = { fileHandle.raw.value.transferProgress() },
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun SpacerTile(modifier: Modifier) {
    Box(modifier.aspectRatio(1f))
}

/** Album tiles are at most about half a bubble wide, so 768 px covers them at any density. */
private const val ALBUM_TILE_DECODE_DIMENSION = 768
