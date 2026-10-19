package com.beykus.beygram.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPagingTest {

    @Test
    fun appendSkipsOverlapAndKeepsOrder() {
        val first = listOf(1 to "a", 2 to "b")
        val second = listOf(2 to "b", 3 to "c")
        val merged = appendDistinctBy(first, second) { it.first }
        assertEquals(listOf(1 to "a", 2 to "b", 3 to "c"), merged)
    }

    @Test
    fun appendToEmptyKeepsIncoming() {
        val merged = appendDistinctBy(emptyList<Int>(), listOf(5, 6, 5)) { it }
        assertEquals(listOf(5, 6), merged)
    }

    @Test
    fun appendWithEmptyIncomingKeepsExisting() {
        val merged = appendDistinctBy(listOf(1, 2), emptyList()) { it }
        assertEquals(listOf(1, 2), merged)
    }

    @Test
    fun appendDropsDuplicatesInsideIncoming() {
        val merged = appendDistinctBy(listOf(1), listOf(2, 2, 3)) { it }
        assertEquals(listOf(1, 2, 3), merged)
    }

    @Test
    fun staleQueryIsNotCurrent() {
        assertTrue(isCurrentSearch(" котик ", "котик"))
        assertFalse(isCurrentSearch("котик", "кот"))
    }

    @Test
    fun sameMessageIdInDifferentChatsIsNotDropped() {
        // Идентификаторы сообщений уникальны только внутри чата: ключом служит пара (chatId, id).
        val first = listOf(Triple(10L, 7L, "a"))
        val second = listOf(Triple(11L, 7L, "b"), Triple(10L, 7L, "a"))
        val merged = appendDistinctBy(first, second) { it.first to it.second }
        assertEquals(listOf(Triple(10L, 7L, "a"), Triple(11L, 7L, "b")), merged)
    }
}
