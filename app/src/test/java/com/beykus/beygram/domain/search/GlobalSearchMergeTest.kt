package com.beykus.beygram.domain.search

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ContactPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchMergeTest {
    private fun chat(id: Long, title: String): ChatPreview =
        ChatPreview(id, title, title.take(2), UiText.Raw(""), "", 0, id)

    private fun contact(id: Long, title: String): ContactPreview =
        ContactPreview(id, title, "@$title", null, id)

    @Test
    fun chatPagesMergeByIdAndKeepTheFirstOccurrenceOrder() {
        val firstPage = listOf(chat(1L, "Anna"), chat(2L, "Project"))
        val incoming = listOf(chat(2L, "Project updated"), chat(3L, "News"), chat(3L, "News duplicate"))

        val merged = mergeGlobalChatResults(firstPage, incoming)

        assertEquals(listOf(1L, 2L, 3L), merged.map { it.id })
        assertEquals("Project", merged[1].title)
    }

    @Test
    fun contactPagesMergeByUserId() {
        val firstPage = listOf(contact(10L, "Alice"), contact(20L, "Bob"))
        val incoming = listOf(contact(20L, "Bob duplicate"), contact(30L, "Carol"), contact(10L, "Alice duplicate"))

        val merged = mergeGlobalContactResults(firstPage, incoming)

        assertEquals(listOf(10L, 20L, 30L), merged.map { it.userId })
        assertEquals("Bob", merged[1].title)
        assertEquals("Alice", merged[0].title)
    }

    @Test
    fun requestForAnotherQueryIsStale() {
        val request = GlobalSearchRequest("anna", generation = 4L)

        assertTrue(isStaleGlobalSearchRequest(request, currentQuery = "bob", currentGeneration = 4L))
    }

    @Test
    fun requestFromAnotherGenerationIsStaleEvenForTheSameQuery() {
        val request = GlobalSearchRequest("anna", generation = 4L)

        assertTrue(isStaleGlobalSearchRequest(request, currentQuery = "anna", currentGeneration = 5L))
    }

    @Test
    fun currentRequestIsAcceptedAndTrimsQueryWhitespace() {
        val request = GlobalSearchRequest(" anna ", generation = 4L)

        assertFalse(isStaleGlobalSearchRequest(request, currentQuery = "anna", currentGeneration = 4L))
    }
}
