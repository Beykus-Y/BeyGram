package com.beykus.beygram.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationHistoryTest {
    private val roots = listOf("Chats", "Calls", "Contacts", "Settings")
    private fun history() = NavigationHistory(
        initial = "Chats",
        isRoot = { it in roots },
        rootIndex = { roots.indexOf(it) }
    )

    @Test
    fun openingNestedRouteIsPush() {
        val history = history()
        assertEquals(NavigationMove.Push, history.visit("Chat"))
        assertEquals(NavigationMove.Push, history.visit("ChatInfo"))
    }

    @Test
    fun returningToPreviousRouteIsPop() {
        val history = history()
        history.visit("Chat")
        history.visit("ChatInfo")
        assertEquals(NavigationMove.Pop, history.visit("Chat"))
        assertEquals(NavigationMove.Pop, history.visit("Chats"))
    }

    @Test
    fun returningToAnyEarlierNestedRouteIsPop() {
        val history = history()
        history.visit("Chat")
        history.visit("MessageThread")
        history.visit("ThreadInfo")
        assertEquals(NavigationMove.Pop, history.visit("Chat"))
        // The popped intermediate routes no longer exist in history.
        assertEquals(NavigationMove.Push, history.visit("MessageThread"))
        assertEquals(NavigationMove.Pop, history.visit("Chat"))
    }

    @Test
    fun openingACompletelyNewNestedRouteIsStillPush() {
        val history = history()
        history.visit("Chat")
        history.visit("ChatInfo")
        assertEquals(NavigationMove.Push, history.visit("Gallery"))
    }

    @Test
    fun switchingBetweenTabRootsIsTab() {
        val history = history()
        assertEquals(NavigationMove.Tab, history.visit("Settings"))
        assertEquals(NavigationMove.Tab, history.visit("Chats"))
    }

    @Test
    fun repeatedVisitToSameRouteKeepsTheMove() {
        val history = history()
        assertEquals(NavigationMove.Push, history.visit("Chat"))
        assertEquals(NavigationMove.Push, history.visit("Chat"))
    }

    @Test
    fun tabRootResetsTheStackSoLaterBackIsPush() {
        val history = history()
        history.visit("Settings")
        history.visit("Profile")
        assertEquals(NavigationMove.Pop, history.visit("Settings"))
        // The stack now holds only the tab root, so a new screen is a push again.
        assertEquals(NavigationMove.Push, history.visit("Profile"))
    }

    @Test
    fun tabDirectionFollowsBarOrderForwardAndBackward() {
        val history = history()
        assertEquals(NavigationMove.Tab, history.visit("Contacts"))
        assertEquals(1, history.tabDirection)
        assertEquals(NavigationMove.Tab, history.visit("Calls"))
        assertEquals(-1, history.tabDirection)
        assertEquals(NavigationMove.Tab, history.visit("Settings"))
        assertEquals(1, history.tabDirection)
        assertEquals(NavigationMove.Tab, history.visit("Chats"))
        assertEquals(-1, history.tabDirection)
    }

    @Test
    fun rapidBackAfterMultiStepPushReturnsPopAndKeepsCleanStack() {
        val history = history()
        assertEquals(NavigationMove.Push, history.visit("Chat"))
        assertEquals(NavigationMove.Push, history.visit("ChatInfo"))
        assertEquals(NavigationMove.Push, history.visit("MemberProfile"))
        assertEquals(NavigationMove.Pop, history.visit("ChatInfo"))
        assertEquals(NavigationMove.Pop, history.visit("Chat"))
        assertEquals(NavigationMove.Pop, history.visit("Chats"))
        // Stack is back at root; pushing Chat again is a Push.
        assertEquals(NavigationMove.Push, history.visit("Chat"))
    }
}
