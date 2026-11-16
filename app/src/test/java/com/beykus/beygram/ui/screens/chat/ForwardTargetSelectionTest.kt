package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ForwardTargetSelectionTest {
    private val a = ForwardTarget(1, "Chat")
    private val b = ForwardTarget(2, "Other")
    private val topic = ForwardTarget(1, "Chat", 42, "Topic")

    @Test fun singleRecipientReplacesPreviousChoiceAndCanBeDeselected() {
        assertEquals(listOf(b), toggleForwardTarget(listOf(a), b, true))
        assertEquals(emptyList<ForwardTarget>(), toggleForwardTarget(listOf(b), b, true))
    }

    @Test fun multipleRecipientsAndSeparateTopicsAreIndependent() {
        assertEquals(listOf(a, topic, b), toggleForwardTarget(listOf(a, topic), b, false))
        assertEquals(listOf(a, b), toggleForwardTarget(listOf(a, topic, b), topic, false))
    }

    @Test fun displayTitleChangesDoNotCreateDuplicateRecipients() {
        assertEquals(emptyList<ForwardTarget>(), toggleForwardTarget(listOf(a), a.copy(title = "Renamed"), false))
    }
}
