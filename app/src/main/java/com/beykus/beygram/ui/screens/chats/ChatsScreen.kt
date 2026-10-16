package com.beykus.beygram.ui.screens.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.ui.components.Avatar
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.domain.model.ProfileInfo
import com.beykus.beygram.domain.model.MediaFileState
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.beyGramTextFieldColors

private enum class ChatFilter(val title: String) {
    All("Все"), Unread("Непрочитанные"), People("Личные"), Groups("Группы")
}

@Composable
fun ChatsScreen(
    chats: List<ChatPreview>,
    myProfile: ProfileInfo?,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onMyProfile: () -> Unit,
    onNotifications: () -> Unit,
    onChatClick: (ChatPreview) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilterName by rememberSaveable { mutableStateOf(ChatFilter.All.name) }
    val selectedFilter = ChatFilter.valueOf(selectedFilterName)
    val palette = beyGramPalette()
    val filteredChats = remember(query, chats, selectedFilter) {
        chats.filter { chat ->
            val matchesQuery = query.isBlank() || chat.title.contains(query, true) || chat.lastMessage.contains(query, true)
            val matchesFilter = when (selectedFilter) {
                ChatFilter.All -> true
                ChatFilter.Unread -> chat.unreadCount > 0
                ChatFilter.People -> !chat.isGroup
                ChatFilter.Groups -> chat.isGroup
            }
            matchesQuery && matchesFilter
        }
    }
    val pinnedChats = if (selectedFilter == ChatFilter.All && query.isBlank()) filteredChats.filter { it.isPinned } else emptyList()
    val regularChats = filteredChats.filterNot { it in pinnedChats }

    Column(
        modifier = Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().background(palette.surface).padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("BeyGram", color = palette.telegramBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                    Text("Сообщения", color = palette.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
                }
                androidx.compose.material3.IconButton(onClick = onNotifications, modifier = Modifier.semantics { contentDescription = "Уведомления" }) { Text("🔔", fontSize = 22.sp, color = palette.telegramBlue) }
                TelegramAvatar(myProfile?.initials ?: "Я", palette.telegramBlue, 44.dp, myProfile?.photo, observeFile, downloadFile,
                    Modifier.semantics { contentDescription = "Мой профиль" }.clickable(onClick = onMyProfile))
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Поиск сообщений и чатов") },
                leadingIcon = { Text("⌕", color = palette.textSecondary, fontSize = 23.sp) },
                trailingIcon = { if (query.isNotEmpty()) Text("×", color = palette.textSecondary, modifier = Modifier.clickable { query = "" }.padding(8.dp), fontSize = 20.sp) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = beyGramTextFieldColors()
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChatFilter.entries.forEach { filter ->
                    FilterChip(
                        title = filter.title,
                        selected = selectedFilter == filter,
                        count = if (filter == ChatFilter.Unread) chats.sumOf { it.unreadCount } else null,
                        onClick = { selectedFilterName = filter.name }
                    )
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
            if (pinnedChats.isNotEmpty()) {
                item(key = "pinned_header") {
                    Text("ЗАКРЕПЛЁННЫЕ", modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 9.dp), color = palette.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                }
                items(pinnedChats, key = { "pinned_${it.id}" }) { chat ->
                    ChatListItem(chat, observeFile, downloadFile, onClick = { onChatClick(chat) })
                    HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 86.dp))
                }
                if (regularChats.isNotEmpty()) {
                    item(key = "all_header") {
                        Text("ВСЕ ЧАТЫ", modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 9.dp), color = palette.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    }
                }
            }
            if (regularChats.isEmpty() && pinnedChats.isEmpty()) {
                item(key = "empty_state") { EmptyState(selectedFilter, query) }
            } else {
                items(regularChats, key = { it.id }) { chat ->
                    ChatListItem(chat, observeFile, downloadFile, onClick = { onChatClick(chat) })
                    HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 86.dp))
                }
            }
        }
    }
}

@Composable
private fun FilterChip(title: String, selected: Boolean, count: Int?, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) palette.telegramBlue else palette.screen)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = if (selected) Color.White else palette.textSecondary, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, letterSpacing = 0.sp)
        if (count != null && count > 0) {
            Spacer(Modifier.width(6.dp))
            Text(count.toString(), color = if (selected) Color.White else palette.telegramBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyState(filter: ChatFilter, query: String) {
    val palette = beyGramPalette()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (query.isNotBlank()) "⌕" else "☁", color = palette.telegramBlue, fontSize = 38.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                query.isNotBlank() -> "Ничего не найдено"
                filter == ChatFilter.Unread -> "Всё прочитано"
                filter == ChatFilter.Groups -> "Пока нет групп"
                else -> "Здесь пока тихо"
            },
            color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(5.dp))
        Text("Попробуйте изменить запрос или выбрать другой фильтр", color = palette.textSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun ChatListItem(chat: ChatPreview, observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        modifier = Modifier.fillMaxWidth().background(palette.surface).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            TelegramAvatar(chat.initials, Color(chat.avatarColorArgb), 54.dp, chat.photo, observeFile, downloadFile)
            if (chat.unreadCount > 0) Box(modifier = Modifier.align(Alignment.BottomEnd).padding(1.dp).size(12.dp).clip(CircleShape).background(palette.telegramBlue))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(chat.title, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), letterSpacing = 0.sp)
                Text(chat.lastMessageTime, color = if (chat.unreadCount > 0) palette.telegramBlue else palette.textSecondary, fontSize = 12.sp, fontWeight = if (chat.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal, letterSpacing = 0.sp)
            }
            Spacer(Modifier.height(5.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val prefix = when { chat.isPinned -> "⌖  "; chat.isMuted -> "🔕  "; else -> "" }
                Text(prefix + chat.lastMessage.ifBlank { if (chat.isGroup) "Группа" else "Начните общение" }, color = palette.textSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), letterSpacing = 0.sp)
                if (chat.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(palette.telegramBlue).padding(horizontal = 8.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
                        Text(if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
