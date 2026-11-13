package com.beykus.beygram.ui.screens.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Text
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.components.BeyGramPalette
import com.beykus.beygram.ui.components.ChatAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only preview card opened by a long press on the chat avatar. It shows the newest messages as bubbles of the current
 * theme, on a dimmed background. [load] reads history only, so the chat is not opened and stays unread. A failed load shows
 * a neutral line. The card fades and scales in, and closes on a tap outside it or on the back gesture.
 */
@Composable
internal fun ChatPeekSheet(
    chat: ChatPreview,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    load: suspend (ChatPreview) -> Result<List<ChatMessage>>,
    onClose: () -> Unit
) {
    val palette = beyGramPalette()
    var loaded by remember(chat.id) { mutableStateOf<Result<List<ChatMessage>>?>(null) }
    LaunchedEffect(chat.id) { loaded = load(chat) }

    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    var closing by remember { mutableStateOf(false) }
    // Leave only after the exit animation has finished, so the card does not vanish mid-fade.
    LaunchedEffect(closing) {
        if (closing) {
            visibleState.targetState = false
            snapshotFlow { visibleState.isIdle && !visibleState.currentState }.filter { it }.first()
            onClose()
        }
    }
    val dismiss: () -> Unit = { closing = true }

    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Dimmed backdrop: a tap anywhere outside the card closes it.
            AnimatedVisibility(visibleState, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismiss)
                )
            }
            AnimatedVisibility(
                visibleState,
                enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.92f),
                exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.92f)
            ) {
                PeekCard(chat, palette, loaded, observeFile, downloadFile)
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
    // Start at the newest message: wait for the content to be measured, then scroll to its end.
    LaunchedEffect(loaded) {
        if (messages != null && messages.isNotEmpty()) {
            snapshotFlow { scroll.maxValue }.filter { it > 0 }.first()
            scroll.scrollTo(scroll.maxValue)
        }
    }
    Column(
        Modifier
            .fillMaxWidth(0.9f)
            .widthIn(max = 420.dp)
            .heightIn(max = 560.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(palette.surface)
            // Taps on the card itself must not reach the backdrop behind it.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChatAvatar(chat, 42.dp, observeFile, downloadFile)
            Spacer(Modifier.width(12.dp))
            Text(
                chat.title,
                color = palette.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
        Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
            val result = loaded
            when {
                result == null -> Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.accent)
                }
                messages == null -> Centered(stringResource(R.string.chat_peek_failed), palette)
                messages.isEmpty() -> Centered(stringResource(R.string.chat_peek_empty), palette)
                else -> Column(
                    Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    messages.forEach { PeekBubble(it, palette) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
        Text(
            stringResource(R.string.chat_peek_hint),
            color = palette.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun Centered(text: String, palette: BeyGramPalette) {
    Box(Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
        Text(text, color = palette.textSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun PeekBubble(message: ChatMessage, palette: BeyGramPalette) {
    val mediaLabel = stringResource(R.string.chat_peek_media)
    val body = message.text.trim().ifBlank { message.serviceText?.trim().orEmpty() }.ifBlank { mediaLabel }
    val bubble = if (message.isOutgoing) palette.outgoingBubble else palette.incomingBubble
    Column(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    ) {
        Column(
            Modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bubble)
                .padding(horizontal = 12.dp, vertical = 8.dp)
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
            Text(body, color = palette.messageText, fontSize = 15.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
    }
}
