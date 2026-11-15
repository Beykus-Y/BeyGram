package com.beykus.beygram.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationHistoryTest {
    @Test
    fun tabTransitionOriginIsSetOnlyForTabSwitches() {
        val history = NavigationHistory(
            initial = "Chats",
            isRoot = { it in listOf("Chats", "Calls", "Contacts", "Settings") },
            rootIndex = { listOf("Chats", "Calls", "Contacts", "Settings").indexOf(it) }
        )
        assertEquals(NavigationMove.Tab, history.visit("Settings"))
        assertEquals("Chats", history.tabTransitionOrigin)
        assertEquals(NavigationMove.Push, history.visit("ChatInfo"))
        assertNull(history.tabTransitionOrigin)
    }

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

    @Test
    fun twoPaneTabSwitchFromNestedRouteResolvesAsTabRelativeToActiveTab() {
        val history = history()
        assertEquals(NavigationMove.Push, history.visit("Chat"))
        assertEquals(NavigationMove.Push, history.visit("ChatInfo"))

        history.prepareTabSwitch("Chats")
        assertEquals(NavigationMove.Tab, history.visit("Settings"))
        assertEquals(1, history.tabDirection)

        // Switching back from a nested route inside a later tab uses that active tab's order.
        assertEquals(NavigationMove.Push, history.visit("SettingsDetail"))
        history.prepareTabSwitch("Settings")
        assertEquals(NavigationMove.Tab, history.visit("Calls"))
        assertEquals(-1, history.tabDirection)
    }

    @Test
    fun normalBackFromNestedRouteToTabRootStillProducesPop() {
        val history = history()
        assertEquals(NavigationMove.Push, history.visit("Archive"))
        assertEquals(NavigationMove.Push, history.visit("Chat"))
        assertEquals(NavigationMove.Pop, history.visit("Archive"))
        assertEquals(NavigationMove.Pop, history.visit("Chats"))

        // Opening Chat from Contacts and pressing Back without a rail tab switch still pops.
        assertEquals(NavigationMove.Tab, history.visit("Contacts"))
        assertEquals(NavigationMove.Push, history.visit("Chat"))
        assertEquals(NavigationMove.Pop, history.visit("Contacts"))
    }

    @Test
    fun prepareTabSwitchDoesNotLeakPastRepeatedOrNonRootVisit() {
        val history = history()
        assertEquals(NavigationMove.Push, history.visit("Chat"))
        history.prepareTabSwitch("Chats")
        // Visiting the same route clears the pending tab switch.
        assertEquals(NavigationMove.Push, history.visit("Chat"))
        // Subsequent normal back to Chats must still be Pop.
        assertEquals(NavigationMove.Pop, history.visit("Chats"))

        // Visiting a non-root route also clears any pending tab switch.
        assertEquals(NavigationMove.Push, history.visit("Chat"))
        history.prepareTabSwitch("Chats")
        assertEquals(NavigationMove.Push, history.visit("ChatInfo"))
        assertEquals(NavigationMove.Pop, history.visit("Chats"))
    }
}
