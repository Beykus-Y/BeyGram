package com.beykus.beygram.ui.screens.chats

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.ActionBubble
import com.beykus.beygram.ui.components.ChatAvatar
import com.beykus.beygram.ui.components.SheetActionRow
import com.beykus.beygram.ui.components.TextButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.groupSurfaceColor
import com.beykus.beygram.ui.components.sheetSurfaceColor
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassCard
import com.beykus.beygram.ui.design.groupCardShape
import com.beykus.beygram.ui.design.usesGlassPanels
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Actions from the chat list: a header with the avatar, a row of quick actions and a card with the rest.
 * Destructive operations are impossible without an explicit confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatActionsSheet(
    chat: ChatPreview,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onClose: () -> Unit,
    setPinned: suspend (Boolean) -> Result<Unit>,
    setMuted: suspend (Boolean) -> Result<Unit>,
    setUnread: suspend () -> Result<Unit>,
    delete: suspend () -> Result<Unit>
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
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
    val canMarkUnread = !chat.isMarkedAsUnread && chat.unreadCount == 0
    ModalBottomSheet(onDismissRequest = onClose, containerColor = sheetSurfaceColor()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChatAvatar(chat, 44.dp, observeFile, downloadFile)
            Spacer(Modifier.width(12.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.title,
                    color = palette.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (chat.isPinned) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.PushPin, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                }
                if (chat.isMuted) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.NotificationsOff, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            ActionBubble(
                icon = Icons.Rounded.PushPin,
                label = stringResource(if (chat.isPinned) R.string.feature_unpin_chat else R.string.feature_pin_chat),
                onClick = { perform { setPinned(!chat.isPinned) } },
                modifier = Modifier.weight(1f),
                enabled = !busy,
                active = chat.isPinned,
                entranceIndex = 0
            )
            ActionBubble(
                icon = if (chat.isMuted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                label = stringResource(if (chat.isMuted) R.string.feature_unmute_chat else R.string.feature_mute_chat),
                onClick = { perform { setMuted(!chat.isMuted) } },
                modifier = Modifier.weight(1f),
                enabled = !busy,
                active = chat.isMuted,
                entranceIndex = 1
            )
            if (canMarkUnread) {
                ActionBubble(
                    icon = Icons.Rounded.MarkChatUnread,
                    label = stringResource(R.string.chat_mark_unread),
                    onClick = { perform(setUnread) },
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    entranceIndex = 2
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .then(
                    if (design.usesGlassPanels) Modifier.glassCard(design, palette.surface2, palette.textSecondary, groupCardShape())
                    else Modifier.clip(groupCardShape()).background(groupSurfaceColor())
                )
        ) {
            SheetActionRow(
                icon = Icons.Rounded.ContentCopy,
                title = stringResource(R.string.feature_copy_chat_title),
                onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText(chat.title, chat.title))
                    onClose()
                }
            )
            Box(Modifier.padding(start = 52.dp).fillMaxWidth().height(1.dp).background(palette.divider))
            SheetActionRow(
                icon = Icons.Rounded.DeleteOutline,
                title = stringResource(R.string.feature_delete_chat),
                onClick = { confirmDelete = true },
                enabled = !busy,
                danger = true
            )
        }
        error?.let {
            Text(
                it,
                color = palette.danger,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp).errorAnnouncement()
            )
        }
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
