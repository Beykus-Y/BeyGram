package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ProfileInfo
import com.beykus.beygram.domain.model.ProfilePresence
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatPresenceTest {
    private fun chat(isGroup: Boolean) = ChatPreview(1L, "Команда", "К", UiText.Raw(""), "", 0, 0L, isGroup = isGroup)

    @Test fun groupShowsOnlineCount() {
        assertEquals(UiText.Res(R.string.presence_online_count, listOf(12)), chatPresenceLine(chat(true), ProfileInfo(1L, "Команда", onlineMemberCount = 12)))
    }

    @Test fun groupWithoutKnownOnlineCountFallsBack() {
        assertEquals(UiText.Res(R.string.presence_group), chatPresenceLine(chat(true), ProfileInfo(1L, "Команда")))
        assertEquals(UiText.Res(R.string.presence_group), chatPresenceLine(chat(true), ProfileInfo(1L, "Команда", onlineMemberCount = 0)))
    }

    @Test fun privateChatShowsTelegramStatus() {
        assertEquals(UiText.Res(R.string.presence_online), chatPresenceLine(chat(false), ProfileInfo(2L, "Анна", status = ProfilePresence.Online)))
        assertEquals(UiText.Res(R.string.presence_recently), chatPresenceLine(chat(false), ProfileInfo(2L, "Анна", status = ProfilePresence.Recently)))
    }

    @Test fun lastSeenPassesTheFormattedTimeAsArgument() {
        assertEquals(UiText.Res(R.string.presence_last_seen_at, listOf("12.03, 10:00")), chatPresenceLine(chat(false), ProfileInfo(2L, "Анна", status = ProfilePresence.LastSeen("12.03, 10:00"))))
    }

    @Test fun privateChatWithoutStatusFallsBack() {
        assertEquals(UiText.Res(R.string.presence_private_chat), chatPresenceLine(chat(false), null))
        assertEquals(UiText.Res(R.string.presence_private_chat), chatPresenceLine(chat(false), ProfileInfo(2L, "Анна")))
    }

    @Test fun botAndDeletedAccountKeepTheirLabels() {
        assertEquals(UiText.Res(R.string.presence_bot), chatPresenceLine(chat(false), ProfileInfo(3L, "Помощник", status = ProfilePresence.Bot)))
        assertEquals(UiText.Res(R.string.presence_deleted_account), chatPresenceLine(chat(false), ProfileInfo(4L, "Аноним", status = ProfilePresence.DeletedAccount)))
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
