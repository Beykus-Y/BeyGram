package com.beykus.beygram.ui.screens.archive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chats.ChatSwipeAction
import com.beykus.beygram.ui.screens.chats.SwipeChatRow
import kotlinx.coroutines.flow.StateFlow

/** Отдельный экран архива: архивные чаты в порядке Telegram, без закреплённых и фильтров. */
@Composable
fun ArchiveScreen(
    chats: List<ChatPreview>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onMarkRead: suspend (ChatPreview) -> Boolean,
    onUnarchive: suspend (ChatPreview) -> Boolean,
    onBack: () -> Unit,
    onChatClick: (ChatPreview) -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    var openSwipeChatId by remember { mutableStateOf<Long?>(null) }
    val readAction = ChatSwipeAction(
        label = stringResource(R.string.archive_action_read), icon = Icons.Rounded.DoneAll, container = palette.surface2, content = palette.accentInk,
        enabled = true, perform = onMarkRead
    )
    val unarchiveAction = ChatSwipeAction(
        label = stringResource(R.string.archive_action_unarchive), icon = Icons.Rounded.Unarchive, container = palette.accent, content = palette.onAccent,
        enabled = true, perform = onUnarchive
    )
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp, end = 20.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = context.getString(R.string.common_back) }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.archive_title), color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        if (chats.isEmpty()) {
            Text(
                text = stringResource(R.string.archive_empty),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 72.dp),
                color = palette.textSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(chats, key = { it.id }) { chat ->
                    SwipeChatRow(
                        chat = chat,
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        openChatId = openSwipeChatId,
                        onOpenChange = { openSwipeChatId = it },
                        onClick = { onChatClick(chat) },
                        read = readAction.copy(enabled = chat.unreadCount > 0),
                        primary = unarchiveAction
                    )
                }
            }
        }
    }
}
