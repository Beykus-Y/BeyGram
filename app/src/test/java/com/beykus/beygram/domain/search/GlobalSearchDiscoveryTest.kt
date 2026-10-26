package com.beykus.beygram.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GlobalSearchDiscoveryTest {
    @Test
    fun atUsernameIsNormalizedForPublicLookup() {
        val query = parseGlobalSearchQuery("  @durov  ")

        assertEquals("@durov", query.raw)
        assertEquals("durov", query.publicText)
        assertEquals("durov", query.username)
    }

    @Test
    fun plainUsernameIsSearchedPubliclyWithoutBecomingAnExplicitTarget() {
        val query = parseGlobalSearchQuery("durov")

        assertEquals("durov", query.raw)
        assertEquals("durov", query.publicText)
        assertNull(query.username)
    }

    @Test
    fun telegramProfileLinksAreNormalizedWithSchemeWwwAndTrailingSlash() {
        listOf(
            "t.me/durov",
            "https://t.me/durov",
            "http://t.me/durov/",
            "https://www.t.me/durov",
            "telegram.me/durov",
            "https://telegram.me/durov/"
        ).forEach { input ->
            val query = parseGlobalSearchQuery(input)

            assertEquals("durov", query.publicText)
            assertEquals("Public username from $input", "durov", query.username)
        }
    }

    @Test
    fun shareSuffixIsIgnoredButMessageLinksKeepTheirRawQuery() {
        assertEquals("durov", parseGlobalSearchQuery("t.me/durov?single").username)

        val messageLink = parseGlobalSearchQuery("t.me/durov/42")
        assertEquals("t.me/durov/42", messageLink.publicText)
        assertNull(messageLink.username)
    }

    @Test
    fun blankQueryHasNoAction() {
        assertNull(networkSearchAction(""))
        assertNull(networkSearchAction("   "))
    }

    @Test
    fun networkSearchActionIsVisibleForUsernameNameAndLinkForms() {
        listOf("@durov", "durov", "t.me/durov").forEach { input ->
            assertNotNull("Find in Telegram is hidden for $input", networkSearchAction(input))
        }
        assertEquals("durov", networkSearchAction("t.me/durov")?.publicText)
    }

    @Test
    fun localAndPublicSourcesKeepTheirOwnSections() {
        assertEquals(GlobalSearchSection.Chats, GlobalSearchResultSource.LocalChat.section)
        assertEquals(GlobalSearchSection.PeopleAndBots, GlobalSearchResultSource.LocalContact.section)
        assertEquals(GlobalSearchSection.PublicResults, GlobalSearchResultSource.PublicResult.section)
        assertEquals(GlobalSearchSection.GlobalMessages, GlobalSearchResultSource.GlobalMessage.section)

        assertEquals(
            GlobalSearchSection.entries.toSet(),
            GlobalSearchResultSource.entries.map { it.section }.toSet()
        )
    }

    @Test
    fun localAndPublicRowsOpenThroughDifferentRoutes() {
        assertEquals(GlobalSearchChatTarget.Local(7L), GlobalSearchChatScope.Local.openTarget(7L))
        assertEquals(GlobalSearchChatTarget.Public(7L), GlobalSearchChatScope.Public.openTarget(7L))
    }
}
