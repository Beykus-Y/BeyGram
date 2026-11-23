package com.beykus.beygram.ui.screens.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.ui.components.BeyGramPalette
import com.beykus.beygram.ui.components.ChatAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.fadeEnter
import com.beykus.beygram.ui.motion.fadeExit
import com.beykus.beygram.ui.motion.rememberMotionSpec
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Read-only preview card opened by a long press on a chat avatar or from the chat action sheet.
 * Shows the newest messages as themed chat bubbles on a real chat canvas without marking the chat read,
 * plus quick actions right below the preview card.
 */
@Composable
internal fun ChatPeekSheet(
    chat: ChatPreview,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    load: suspend (ChatPreview) -> Result<List<ChatMessage>>,
    onClose: () -> Unit,
    onOpenChat: (() -> Unit)? = null,
    onToggleMute: (suspend () -> Unit)? = null,
    onTogglePin: (suspend () -> Unit)? = null,
    onMarkRead: (suspend () -> Unit)? = null
) {
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    var loaded by remember(chat.id) { mutableStateOf<Result<List<ChatMessage>>?>(null) }
    LaunchedEffect(chat.id) { loaded = load(chat) }

    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (closing) {
            visibleState.targetState = false
            snapshotFlow { visibleState.isIdle && !visibleState.currentState }.filter { it }.first()
            onClose()
        }
    }
    val dismiss: () -> Unit = { closing = true }

    val enterSpec = rememberMotionSpec(MotionToken.SheetEnter)
    val exitSpec = rememberMotionSpec(MotionToken.SheetExit)
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                visibleState,
                enter = enterSpec.fadeEnter(),
                exit = exitSpec.fadeExit()
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.52f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismiss)
                )
            }
            AnimatedVisibility(
                visibleState,
                enter = if (enterSpec.isInstant) EnterTransition.None else
                    enterSpec.fadeEnter() + scaleIn(enterSpec.animationSpec(), initialScale = 1f - 0.08f * enterSpec.intensity),
                exit = if (exitSpec.isInstant) ExitTransition.None else
                    exitSpec.fadeExit() + scaleOut(exitSpec.animationSpec(), targetScale = 1f - 0.08f * exitSpec.intensity)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth(0.92f)
                        .widthIn(max = 420.dp)
                        .pointerInput(Unit) { detectTapGestures { } },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PeekCard(chat, palette, loaded, observeFile, downloadFile)
                    if (onOpenChat != null || onToggleMute != null || onTogglePin != null || onMarkRead != null) {
                        PeekQuickActionsBar(
                            chat = chat,
                            palette = palette,
                            onOpenChat = onOpenChat?.let { action -> { dismiss(); action() } },
                            onToggleMute = onToggleMute?.let { action -> { scope.launch { action(); dismiss() } } },
                            onTogglePin = onTogglePin?.let { action -> { scope.launch { action(); dismiss() } } },
                            onMarkRead = onMarkRead?.takeIf { chat.unreadCount > 0 || chat.isMarkedAsUnread }
                                ?.let { action -> { scope.launch { action(); dismiss() } } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeekCard(
    chat: ChatPreview,
    palette: BeyGramPalette,
    loaded: Result<List<ChatMessage>>?,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit
) {
    val scroll = rememberScrollState()
    val messages = loaded?.getOrNull()
    LaunchedEffect(loaded) {
        if (messages != null && messages.isNotEmpty()) {
            snapshotFlow { scroll.maxValue }.filter { it > 0 }.first()
            scroll.scrollTo(scroll.maxValue)
        }
    }
    val cardShape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp, max = 500.dp)
            .clip(cardShape)
            .background(palette.surface)
            .border(1.dp, palette.divider, cardShape)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChatAvatar(chat, 40.dp, observeFile, downloadFile)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    chat.title,
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.peek_no_read_hint),
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (chat.unreadCount > 0) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(palette.accent)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        chat.unreadCount.toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
        // Real chat canvas color behind bubbles so incoming/outgoing bubbles have clear contrast.
        Box(
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .background(palette.screen)
        ) {
            val result = loaded
            when {
                result == null -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.accent)
                }
                messages == null -> Centered(stringResource(R.string.chat_peek_failed), palette)
                messages.isEmpty() -> Centered(stringResource(R.string.chat_peek_empty), palette)
                else -> Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scroll)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    messages.forEach { PeekBubble(it, palette) }
                }
            }
        }
    }
}

@Composable
private fun PeekQuickActionsBar(
    chat: ChatPreview,
    palette: BeyGramPalette,
    onOpenChat: (() -> Unit)?,
    onToggleMute: (() -> Unit)?,
    onTogglePin: (() -> Unit)?,
    onMarkRead: (() -> Unit)?
) {
    val barShape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(barShape)
            .background(palette.surface)
            .border(1.dp, palette.divider, barShape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        onOpenChat?.let {
            PeekActionButton(
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                label = stringResource(R.string.media_open_file),
                tint = palette.accent,
                palette = palette,
                onClick = it,
                modifier = Modifier.weight(1f)
            )
        }
        onTogglePin?.let {
            PeekActionButton(
                icon = Icons.Rounded.PushPin,
                label = stringResource(if (chat.isPinned) R.string.feature_unpin_chat else R.string.feature_pin_chat),
                tint = if (chat.isPinned) palette.accent else palette.textPrimary,
                palette = palette,
                onClick = it,
                modifier = Modifier.weight(1f)
            )
        }
        onToggleMute?.let {
            PeekActionButton(
                icon = if (chat.isMuted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                label = stringResource(if (chat.isMuted) R.string.feature_unmute_chat else R.string.feature_mute_chat),
                tint = if (chat.isMuted) palette.accent else palette.textPrimary,
                palette = palette,
                onClick = it,
                modifier = Modifier.weight(1f)
            )
        }
        onMarkRead?.let {
            PeekActionButton(
                icon = Icons.Rounded.DoneAll,
                label = stringResource(R.string.chats_action_read),
                tint = palette.textPrimary,
                palette = palette,
                onClick = it,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PeekActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    palette: BeyGramPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            label,
            color = palette.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Centered(text: String, palette: BeyGramPalette) {
    Box(Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
        Text(text, color = palette.textSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun PeekBubble(message: ChatMessage, palette: BeyGramPalette) {
    val media = message.media
    val mediaIcon = when (media?.kind) {
        MediaKind.Photo -> Icons.Rounded.Image
        MediaKind.Video, MediaKind.Animation -> Icons.Rounded.Videocam
        MediaKind.VideoNote -> Icons.Rounded.SmartDisplay
        MediaKind.Voice -> Icons.Rounded.Mic
        MediaKind.Audio -> Icons.Rounded.MusicNote
        MediaKind.Document -> Icons.Rounded.InsertDriveFile
        else -> if (media != null) Icons.Rounded.GraphicEq else null
    }
    val mediaText = when (media?.kind) {
        MediaKind.Photo -> stringResource(R.string.media_kind_photo)
        MediaKind.Video -> stringResource(R.string.media_kind_video)
        MediaKind.Animation -> "GIF"
        MediaKind.VideoNote -> stringResource(R.string.media_kind_video_note)
        MediaKind.Voice -> stringResource(R.string.media_kind_voice)
        MediaKind.Audio -> media.title?.ifBlank { null } ?: stringResource(R.string.media_kind_audio)
        MediaKind.Document -> media.fileName.ifBlank { stringResource(R.string.media_kind_file) }
        else -> if (media != null) stringResource(R.string.chat_peek_media) else null
    }
    val body = message.text.trim().ifBlank { message.serviceText?.trim().orEmpty() }
    val bubble = if (message.isOutgoing) palette.outgoingBubble else palette.incomingBubble
    val corner = if (message.isOutgoing) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 5.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 5.dp, bottomEnd = 16.dp)
    }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    ) {
        Column(
            Modifier
                .widthIn(max = 296.dp)
                .clip(corner)
                .background(bubble)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (!message.isOutgoing && message.senderName.isNotBlank()) {
                Text(
                    message.senderName,
                    color = palette.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (mediaIcon != null && mediaText != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(mediaIcon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(15.dp))
                    Text(
                        mediaText,
                        color = palette.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (body.isNotBlank()) {
                Text(
                    body,
                    color = palette.messageText,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (message.time.isNotBlank()) {
                Text(
                    message.time,
                    color = palette.messageTime,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
