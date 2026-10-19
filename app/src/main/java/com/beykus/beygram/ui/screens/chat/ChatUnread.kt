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
/** Where the separator sits when the chat opens: its distance from the bottom edge as a share of the viewport. The unread messages fill the space below it. */
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

/** What the chat opening does next; the screen performs it. */
internal sealed interface UnreadOpenStep {
    /** Nothing to place: no unread messages, or the opening position is already decided. */
    object Done : UnreadOpenStep
    /** The window around the read boundary is not loaded, so it is requested once. */
    object RequestWindow : UnreadOpenStep
    /** Scroll to the separator at [index] of the list that includes it. */
    data class Scroll(val index: Int) : UnreadOpenStep
}

/**
 * [items] is the list without the separator. [focusClaimed] means a message opened from outside the chat owns the position:
 * the unread opening then never requests a window or scrolls, so it cannot cancel that jump.
 */
internal fun unreadOpenStep(
    unreadCount: Int, lastReadInboxId: Long, items: List<ChatMessageListItem>, windowRequested: Boolean, focusClaimed: Boolean = false
): UnreadOpenStep {
    if (focusClaimed || unreadCount <= 0 || lastReadInboxId <= 0) return UnreadOpenStep.Done
    val index = withUnreadSeparator(items, lastReadInboxId).indexOfFirst { it.unreadSeparator }
    if (index >= 0) return UnreadOpenStep.Scroll(index)
    // No separator without a read message loaded above the unread ones. If a read message is loaded, the window shows no
    // unread message to scroll to, so the chat opens at its usual position.
    val readLoaded = items.any { item -> item.messages.any { it.id <= lastReadInboxId } }
    return if (readLoaded || windowRequested) UnreadOpenStep.Done else UnreadOpenStep.RequestWindow
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
