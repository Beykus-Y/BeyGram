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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chats.ChatListItem
import kotlinx.coroutines.flow.StateFlow

/** Отдельный экран архива: архивные чаты в порядке Telegram, без закреплённых и фильтров. */
@Composable
fun ArchiveScreen(
    chats: List<ChatPreview>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onBack: () -> Unit,
    onChatClick: (ChatPreview) -> Unit
) {
    val palette = beyGramPalette()
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp, end = 20.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Назад" }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text("Архив", color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        if (chats.isEmpty()) {
            Text(
                text = "В архиве пока пусто",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 72.dp),
                color = palette.textSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(chats, key = { it.id }) { chat ->
                    ChatListItem(chat, observeFile, downloadFile, onClick = { onChatClick(chat) })
                }
            }
        }
    }
}
