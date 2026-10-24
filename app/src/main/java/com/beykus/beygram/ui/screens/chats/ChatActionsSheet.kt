package com.beykus.beygram.ui.screens.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.launch

/** Actions from the chat list. Destructive operations are impossible without an explicit confirmation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatActionsSheet(
    chat: ChatPreview,
    onClose: () -> Unit,
    setPinned: suspend (Boolean) -> Result<Unit>,
    setMuted: suspend (Boolean) -> Result<Unit>,
    setUnread: suspend () -> Result<Unit>,
    delete: suspend () -> Result<Unit>
) {
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    var confirmDelete by remember(chat.id) { mutableStateOf(false) }
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun perform(action: suspend () -> Result<Unit>) {
        if (busy) return
        scope.launch {
            busy = true; error = null
            try { action().fold({ onClose() }, { error = it.userMessage(context, R.string.feature_action_failed) }) }
            finally { busy = false }
        }
    }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = palette.surface) {
        Text(chat.title, color = palette.textPrimary, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp))
        TextButton(onClick = { perform { setPinned(!chat.isPinned) } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (chat.isPinned) R.string.feature_unpin_chat else R.string.feature_pin_chat))
        }
        TextButton(onClick = { perform { setMuted(!chat.isMuted) } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (chat.isMuted) R.string.feature_unmute_chat else R.string.feature_mute_chat))
        }
        if (!chat.isMarkedAsUnread && chat.unreadCount == 0) {
            TextButton(onClick = { perform(setUnread) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.chat_mark_unread))
            }
        }
        TextButton(onClick = { confirmDelete = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_delete_chat), color = palette.danger)
        }
        error?.let { Text(it, color = palette.danger, modifier = Modifier.padding(horizontal = 20.dp)) }
        Spacer(Modifier.height(20.dp))
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.feature_delete_chat)) },
        text = { Text(stringResource(R.string.feature_delete_chat_warning, chat.title)) },
        confirmButton = {
            TextButton(onClick = { confirmDelete = false; perform(delete) }) {
                Text(stringResource(R.string.feature_delete_chat), color = palette.danger)
            }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } }
    )
}
