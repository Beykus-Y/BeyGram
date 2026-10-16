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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
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

private enum class ChatFilter(val title: String) {
    All("Все"), People("Личные"), Groups("Группы"), Unread("Непрочитанные")
}

@Composable
fun ChatsScreen(
    chats: List<ChatPreview>,
    archivedChats: List<ChatPreview>,
    myProfile: ProfileInfo?,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onMyProfile: () -> Unit,
    onNotifications: () -> Unit,
    onArchiveClick: () -> Unit,
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
    val showAllHeader = pinnedChats.isNotEmpty() || (selectedFilter == ChatFilter.All && query.isBlank())

    Column(
        modifier = Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("BEYGRAM", color = palette.accent, style = MaterialTheme.typography.labelMedium, letterSpacing = 0.8.sp)
                    Text("Сообщения", color = palette.textPrimary, fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                }
                androidx.compose.material3.IconButton(onClick = onNotifications, modifier = Modifier.semantics { contentDescription = "Уведомления" }) {
                    Icon(Icons.Rounded.Notifications, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(4.dp))
                TelegramAvatar(myProfile?.initials ?: "Я", palette.accent, 40.dp, myProfile?.photo, observeFile, downloadFile,
                    Modifier.semantics { contentDescription = "Мой профиль" }.clickable(onClick = onMyProfile))
            }

            Spacer(Modifier.height(14.dp))
            SearchField(query = query, onChange = { query = it })
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
            if (archivedChats.isNotEmpty() && selectedFilter == ChatFilter.All && query.isBlank()) {
                item(key = "archive_row") { ArchiveRow(archivedChats, onClick = onArchiveClick) }
            }
            if (pinnedChats.isNotEmpty()) {
                item(key = "pinned_header") {
                    Text("ЗАКРЕПЛЁННЫЕ", modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp), color = palette.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
                items(pinnedChats, key = { "pinned_${it.id}" }) { chat ->
                    ChatListItem(chat, observeFile, downloadFile, onClick = { onChatClick(chat) })
                }
            }
            if (regularChats.isEmpty() && pinnedChats.isEmpty()) {
                item(key = "empty_state") { EmptyState(selectedFilter, query) }
            } else {
                if (showAllHeader && regularChats.isNotEmpty()) {
                    item(key = "all_header") {
                        Text("ВСЕ ЧАТЫ", modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp), color = palette.textSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
                items(regularChats, key = { it.id }) { chat ->
                    ChatListItem(chat, observeFile, downloadFile, onClick = { onChatClick(chat) })
                }
            }
        }
    }
}

@Composable
private fun ArchiveRow(archivedChats: List<ChatPreview>, onClick: () -> Unit) {
    val palette = beyGramPalette()
    val unread = archivedChats.sumOf { it.unreadCount }
    val summary = buildString {
        append("${archivedChats.size} ${russianPlural(archivedChats.size, "чат", "чата", "чатов")}")
        if (unread > 0) append(" · $unread ${russianPlural(unread, "непрочитанное", "непрочитанных", "непрочитанных")}")
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp)).background(palette.surface).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(palette.surface2), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Archive, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Архив", color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(summary, color = palette.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
    }
}

private fun russianPlural(count: Int, one: String, few: String, many: String): String {
    val lastTwo = count % 100
    val last = count % 10
    return when {
        last == 1 && lastTwo != 11 -> one
        last in 2..4 && lastTwo !in 12..14 -> few
        else -> many
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val palette = beyGramPalette()
    BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.textPrimary),
        cursorBrush = SolidColor(palette.accent),
        modifier = Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp)).background(palette.surface2).padding(horizontal = 16.dp),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Поиск сообщений и чатов", color = palette.textSecondary, style = MaterialTheme.typography.bodyLarge)
                    inner()
                }
                if (query.isNotEmpty()) {
                    Icon(Icons.Rounded.Close, contentDescription = "Очистить", tint = palette.textSecondary,
                        modifier = Modifier.size(32.dp).clip(CircleShape).clickable { onChange("") }.padding(5.dp))
                }
            }
        }
    )
}

@Composable
private fun FilterChip(title: String, selected: Boolean, count: Int?, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        modifier = Modifier.height(34.dp).clip(RoundedCornerShape(50)).background(if (selected) palette.accent else palette.surface2)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = if (selected) palette.onAccent else palette.textPrimary, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        if (count != null && count > 0) {
            Spacer(Modifier.width(6.dp))
            Text(count.toString(), color = if (selected) palette.onAccent else palette.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyState(filter: ChatFilter, query: String) {
    val palette = beyGramPalette()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(if (query.isNotBlank()) Icons.Rounded.Search else Icons.Rounded.Cloud, contentDescription = null, tint = palette.accent, modifier = Modifier.size(44.dp))
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
internal fun ChatListItem(chat: ChatPreview, observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit, onClick: () -> Unit) {
    val palette = beyGramPalette()
    val unread = chat.unreadCount > 0
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TelegramAvatar(chat.initials, Color(chat.avatarColorArgb), 54.dp, chat.photo, observeFile, downloadFile)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(chat.title, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Text(chat.lastMessageTime, color = if (unread) palette.accent else palette.textSecondary, fontSize = 12.sp, fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal)
            }
            Spacer(Modifier.height(3.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                when {
                    chat.isPinned -> Icon(Icons.Rounded.PushPin, null, tint = palette.textSecondary, modifier = Modifier.padding(end = 6.dp).size(16.dp))
                    chat.isMuted -> Icon(Icons.Rounded.NotificationsOff, null, tint = palette.textSecondary, modifier = Modifier.padding(end = 6.dp).size(16.dp))
                }
                Text(chat.lastMessage.ifBlank { if (chat.isGroup) "Группа" else "Начните общение" }, color = palette.textSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (unread) {
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.defaultMinSize(minWidth = 22.dp).height(22.dp).clip(RoundedCornerShape(11.dp)).background(palette.accent).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                        Text(if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(), color = palette.onAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
