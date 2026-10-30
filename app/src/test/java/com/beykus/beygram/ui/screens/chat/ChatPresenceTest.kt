package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.R
import com.beykus.beygram.data.presence.LastSeenStyle
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ProfileInfo
import com.beykus.beygram.domain.model.ProfilePresence
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatPresenceTest {
    private val now = 1_700_000_000_000L

    private fun chat(isGroup: Boolean) = ChatPreview(1L, "Команда", "К", UiText.Raw(""), "", 0, 0L, isGroup = isGroup)

    @Test fun savedMessagesNeverShowTheUsersOwnOnlineStatus() {
        val saved = ChatPreview(
            1L, "Избранное", "И", UiText.Raw(""), "", 0, 0L,
            isSavedMessages = true
        )
        assertEquals(
            UiText.Res(R.string.presence_saved_messages),
            chatPresenceLine(saved, ProfileInfo(1L, "Я", status = ProfilePresence.Online), LastSeenStyle.Relative, now)
        )
        assertEquals(
            UiText.Res(R.string.presence_saved_messages),
            chatPresenceLine(saved, null, LastSeenStyle.Relative, now)
        )
        assertEquals(
            UiText.Res(R.string.presence_saved_messages),
            chatPresenceLine(saved, ProfileInfo(1L, "Я", status = ProfilePresence.Recently), LastSeenStyle.Exact, now)
        )
    }

    @Test fun groupShowsOnlineCount() {
        assertEquals(UiText.Res(R.string.presence_online_count, listOf(12)), chatPresenceLine(chat(true), ProfileInfo(1L, "Команда", onlineMemberCount = 12), LastSeenStyle.Relative, now))
    }

    @Test fun groupWithoutKnownOnlineCountFallsBack() {
        assertEquals(UiText.Res(R.string.presence_group), chatPresenceLine(chat(true), ProfileInfo(1L, "Команда"), LastSeenStyle.Relative, now))
        assertEquals(UiText.Res(R.string.presence_group), chatPresenceLine(chat(true), ProfileInfo(1L, "Команда", onlineMemberCount = 0), LastSeenStyle.Relative, now))
    }

    @Test fun privateChatShowsTelegramStatus() {
        assertEquals(UiText.Res(R.string.presence_online), chatPresenceLine(chat(false), ProfileInfo(2L, "Анна", status = ProfilePresence.Online), LastSeenStyle.Relative, now))
        assertEquals(UiText.Res(R.string.presence_recently), chatPresenceLine(chat(false), ProfileInfo(2L, "Анна", status = ProfilePresence.Recently), LastSeenStyle.Relative, now))
    }

    @Test fun lastSeenShowsRelativeTimeAgainstTheCurrentClock() {
        val fiveMinutesAgo = now - 5 * 60_000L
        assertEquals(UiText.Res(R.string.presence_last_seen_minutes_many, listOf(5)),
            chatPresenceLine(chat(false), ProfileInfo(2L, "Анна", status = ProfilePresence.LastSeen(fiveMinutesAgo)), LastSeenStyle.Relative, now))
    }

    @Test fun lastSeenInExactStyleShowsDateAndTime() {
        val fiveMinutesAgo = now - 5 * 60_000L
        val line = chatPresenceLine(chat(false), ProfileInfo(2L, "Анна", status = ProfilePresence.LastSeen(fiveMinutesAgo)), LastSeenStyle.Exact, now)
        assertEquals(R.string.presence_last_seen_at, (line as UiText.Res).id)
    }

    @Test fun privateChatWithoutStatusFallsBack() {
        assertEquals(UiText.Res(R.string.presence_private_chat), chatPresenceLine(chat(false), null, LastSeenStyle.Relative, now))
        assertEquals(UiText.Res(R.string.presence_private_chat), chatPresenceLine(chat(false), ProfileInfo(2L, "Анна"), LastSeenStyle.Relative, now))
    }

    @Test fun botAndDeletedAccountKeepTheirLabels() {
        assertEquals(UiText.Res(R.string.presence_bot), chatPresenceLine(chat(false), ProfileInfo(3L, "Помощник", status = ProfilePresence.Bot), LastSeenStyle.Relative, now))
        assertEquals(UiText.Res(R.string.presence_deleted_account), chatPresenceLine(chat(false), ProfileInfo(4L, "Аноним", status = ProfilePresence.DeletedAccount), LastSeenStyle.Relative, now))
    }

    @Test fun typingNamesKeepTheOrderTheyStartedIn() {
        assertEquals(UiText.Res(R.string.presence_typing_one, listOf("Дима")), typingLabel(listOf("Дима")))
        assertEquals(UiText.Res(R.string.presence_typing_two, listOf("Анна", "Дима")), typingLabel(listOf("Анна", "Дима")))
        assertEquals(UiText.Res(R.string.presence_typing_three, listOf("Анна", "Мама", "Дима")), typingLabel(listOf("Анна", "Мама", "Дима")))
    }

    @Test fun longTypingListIsCollapsedIntoACount() {
        assertEquals(UiText.Res(R.string.presence_typing_more, listOf("Анна", "Мама", 2)), typingLabel(listOf("Анна", "Мама", "Дима", "Лена")))
    }
}
