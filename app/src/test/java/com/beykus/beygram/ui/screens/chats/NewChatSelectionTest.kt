package com.beykus.beygram.ui.screens.chats

import com.beykus.beygram.domain.model.ContactPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewChatSelectionTest {
    private val contacts = listOf(
        ContactPreview(101L, "Алексей Петров", "@alexey", null, 0xff2288aaL),
        ContactPreview(102L, "Юлия Новикова", "@julia", null, 0xff2288aaL),
        ContactPreview(103L, "Alice", "+79990000000", null, 0xff2288aaL)
    )

    @Test fun searchMatchesUsernameNameAndPhoneCaseInsensitively() {
        assertEquals(listOf(101L), filterNewChatContacts(contacts, " АЛЕКС ").map { it.userId })
        assertEquals(listOf(102L), filterNewChatContacts(contacts, "@juL").map { it.userId })
        assertEquals(listOf(103L), filterNewChatContacts(contacts, "999000").map { it.userId })
        assertEquals(contacts, filterNewChatContacts(contacts, "   "))
        assertTrue(filterNewChatContacts(contacts, "unknown").isEmpty())
    }

    @Test fun selectionTogglesWithoutDuplicates() {
        val first = toggledGroupMembers(emptyList(), 101L)
        assertEquals(listOf(101L), first)
        assertEquals(emptyList<Long>(), toggledGroupMembers(first, 101L))
        assertEquals(listOf(101L), toggledGroupMembers(first, 0L))
    }

    @Test fun selectionStopsAtBoundAndNeverDropsPreviousMembers() {
        val selected = (1L..200L).toList()
        assertEquals(selected, toggledGroupMembers(selected, 201L))
        assertEquals(199, toggledGroupMembers(selected, 200L).size)
    }

    @Test fun staleAndDuplicateIdsAreNotSentToTdlib() {
        val allowed = selectedNewGroupMembers(listOf(101L, 101L, 9999L, -2L, 103L), contacts)
        assertEquals(listOf(101L, 103L), allowed.toList())
    }

    @Test fun requiresRealMembersValidNameAndIdleButton() {
        assertFalse(canSubmitNewGroup("", listOf(101L), false))
        assertFalse(canSubmitNewGroup("Group", emptyList(), false))
        assertFalse(canSubmitNewGroup("Group", listOf(101L), true))
        assertFalse(canSubmitNewGroup("x".repeat(129), listOf(101L), false))
        assertTrue(canSubmitNewGroup(" New Group ", listOf(101L), false))
    }
}
