package com.beykus.beygram.ui.screens.chats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.components.ChatAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.sheetSurfaceColor
import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only preview of the newest messages, opened by a long press on the chat avatar. It loads through [load], which
 * reads history only, so the chat is not opened and its unread state does not change. A failed load shows a neutral line.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    ModalBottomSheet(onDismissRequest = onClose, containerColor = sheetSurfaceColor()) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChatAvatar(chat, 36.dp, observeFile, downloadFile)
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
            Spacer(Modifier.height(12.dp))
            val result = loaded
            val messages = result?.getOrNull()
            when {
                result == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.accent)
                }
                messages == null -> Text(stringResource(R.string.chat_peek_failed), color = palette.textSecondary, fontSize = 14.sp)
                messages.isEmpty() -> Text(stringResource(R.string.chat_peek_empty), color = palette.textSecondary, fontSize = 14.sp)
                else -> Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    messages.forEach { PeekMessageRow(it) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.chat_peek_hint), color = palette.textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PeekMessageRow(message: ChatMessage) {
    val palette = beyGramPalette()
    val mediaLabel = stringResource(R.string.chat_peek_media)
    val body = message.text.trim().ifBlank { message.serviceText?.trim().orEmpty() }.ifBlank { mediaLabel }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    ) {
        if (!message.isOutgoing && message.senderName.isNotBlank()) {
            Text(message.senderName, color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(body, color = palette.textPrimary, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}
