package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.ui.components.Avatar
import com.beykus.beygram.ui.components.beyGramPalette

/** Выбор чата для пересылки. Во время отправки список не нажимается и диалог не закрывается. */
@Composable
internal fun ForwardChatDialog(chats: List<ChatPreview>, busy: Boolean, error: String?, onPick: (ChatPreview) -> Unit, onClose: () -> Unit) {
    val palette = beyGramPalette()
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(stringResource(R.string.forward_title), color = palette.textPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                error?.let { Text(it, color = palette.warningText, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(chats, key = { it.id }) { chat ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { onPick(chat) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(chat.initials, Color(chat.avatarColorArgb), 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(chat.title, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text(stringResource(R.string.common_cancel)) } }
    )
}
