package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ProfileInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatPresenceTest {
    private fun chat(isGroup: Boolean) = ChatPreview(1L, "Команда", "К", "", "", 0, 0L, isGroup = isGroup)

    @Test fun groupShowsOnlineCount() {
        assertEquals("12 в сети", chatPresenceLine(chat(true), ProfileInfo(1L, "Команда", onlineMemberCount = 12)))
    }

    @Test fun groupWithoutKnownOnlineCountFallsBack() {
        assertEquals("Группа", chatPresenceLine(chat(true), ProfileInfo(1L, "Команда")))
        assertEquals("Группа", chatPresenceLine(chat(true), ProfileInfo(1L, "Команда", onlineMemberCount = 0)))
    }

    @Test fun privateChatShowsLowercaseStatus() {
        assertEquals("в сети", chatPresenceLine(chat(false), ProfileInfo(2L, "Анна", status = "В сети")))
        assertEquals("был(а) недавно", chatPresenceLine(chat(false), ProfileInfo(2L, "Анна", status = "Был(а) недавно")))
    }

    @Test fun privateChatWithoutStatusFallsBack() {
        assertEquals("Личный чат", chatPresenceLine(chat(false), null))
        assertEquals("Личный чат", chatPresenceLine(chat(false), ProfileInfo(2L, "Анна")))
    }

    @Test fun botAndDeletedAccountKeepTheirLabels() {
        assertEquals("бот", chatPresenceLine(chat(false), ProfileInfo(3L, "Помощник", status = "Бот")))
        assertEquals("удалённый аккаунт", chatPresenceLine(chat(false), ProfileInfo(4L, "Аноним", status = "Удалённый аккаунт")))
    }

    @Test fun typingNamesKeepTheOrderTheyStartedIn() {
        assertEquals("Дима печатает…", typingLabel(listOf("Дима")))
        assertEquals("Анна и Дима печатают…", typingLabel(listOf("Анна", "Дима")))
        assertEquals("Анна, Мама и Дима печатают…", typingLabel(listOf("Анна", "Мама", "Дима")))
    }

    @Test fun longTypingListIsCollapsedIntoACount() {
        assertEquals("Анна, Мама и ещё 2 печатают…", typingLabel(listOf("Анна", "Мама", "Дима", "Лена")))
    }
}
