package com.beykus.beygram.ui.screens.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProxySelectionTest {
    @Test fun userChoiceStaysWhileNodeIsInList() {
        assertEquals("b", effectiveSelectedName("b", listOf("a", "b"), serverChoice = "a"))
    }

    @Test fun nodeMissingFromNewSubscriptionFallsBackToServerChoice() {
        assertEquals("c", effectiveSelectedName("b", listOf("a", "c"), serverChoice = "c"))
    }

    @Test fun nodeMissingAndNoServerChoiceGivesNothing() {
        assertNull(effectiveSelectedName("b", listOf("a"), serverChoice = null))
    }

    @Test fun noUserChoiceUsesServerChoice() {
        assertEquals("a", effectiveSelectedName(null, listOf("a"), serverChoice = "a"))
    }
}
