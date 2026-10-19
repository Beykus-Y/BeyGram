package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.ui.components.beyGramPalette

internal const val UNREAD_SEPARATOR_KEY = "unread-separator"
/** Where the separator sits when the chat opens: this share of the viewport stays above it, the unread messages below. */
internal const val UNREAD_SEPARATOR_VIEWPORT_FRACTION = 0.7f

/**
 * Ставит разделитель «Непрочитанные» над самым старым входящим сообщением новее [lastReadInboxId].
 * Лента перевёрнута: старые сообщения имеют больший индекс, поэтому разделитель идёт сразу после него.
 * Без прочитанного сообщения выше разделителя не будет, и тогда он не нужен.
 */
internal fun withUnreadSeparator(items: List<ChatMessageListItem>, lastReadInboxId: Long): List<ChatMessageListItem> {
    val firstUnread = items.indexOfLast { item -> item.messages.any { !it.isOutgoing && it.id > lastReadInboxId } }
    if (firstUnread < 0 || firstUnread == items.lastIndex) return items
    return items.toMutableList().apply {
        add(firstUnread + 1, ChatMessageListItem(UNREAD_SEPARATOR_KEY, emptyList(), unreadSeparator = true))
    }
}

@Composable
internal fun UnreadMessagesSeparator() {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f).height(1.dp).background(palette.divider))
        Text(stringResource(R.string.chat_unread_separator), color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp))
        Spacer(Modifier.weight(1f).height(1.dp).background(palette.divider))
    }
}
