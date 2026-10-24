package com.beykus.beygram.ui.accessibility

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AccessibilitySupportTest {
    @Test fun smallControlsAreGrownToTheMinimumTouchTarget() {
        assertEquals(MinimumTouchTarget, accessibleTouchTarget(24.dp))
        assertEquals(MinimumTouchTarget, accessibleTouchTarget(40.dp))
        assertEquals(48.dp, accessibleTouchTarget(40.dp))
    }

    @Test fun largeControlsKeepTheirVisualSize() {
        assertEquals(56.dp, accessibleTouchTarget(56.dp))
        assertEquals(112.dp, accessibleTouchTarget(112.dp))
    }

    @Test fun aCustomMinimumIsRespected() {
        assertEquals(64.dp, accessibleTouchTarget(40.dp, minimum = 64.dp))
    }

    @Test fun statusIsNotRepeatedWhenVisibleTextAlreadyContainsIt() {
        assertEquals("Sent", accessibleStatusDescription("Sent", "Sent"))
        assertEquals("12:30 · Sent", accessibleStatusDescription("12:30 · Sent", "sent"))
    }

    @Test fun aMissingStatusLeavesTheVisibleTextUntouched() {
        assertEquals("Hello", accessibleStatusDescription("Hello", null))
        assertEquals("Hello", accessibleStatusDescription("Hello", "  "))
        assertEquals("", accessibleStatusDescription("", null))
    }

    @Test fun statusIsAppendedOnceWhenItIsNotVisible() {
        assertEquals("Hello, Sending", accessibleStatusDescription("Hello", "Sending"))
        assertEquals("Sending", accessibleStatusDescription("", "Sending"))
    }

    @Test fun chatRowDescriptionDropsBlankAndRepeatedParts() {
        val description = accessibleChatRowDescription(
            title = "Alice",
            preview = "See you at 18:00",
            time = "18:00",
            unreadLabel = null,
            mutedLabel = null,
            statusLabel = "Read"
        )
        assertEquals("Alice, See you at 18:00, 18:00, Read", description)

        val duplicated = accessibleChatRowDescription(
            title = "Alice",
            preview = "Alice",
            time = "",
            unreadLabel = "1 unread message",
            mutedLabel = null,
            statusLabel = null
        )
        assertEquals("Alice, 1 unread message", duplicated)
    }

    @Test fun descriptionNeverEndsWithSeparatorsForEmptyRows() {
        assertEquals("", accessibleDescription(null, "", "   "))
        assertFalse(accessibleDescription("Title").contains(','))
    }
}
