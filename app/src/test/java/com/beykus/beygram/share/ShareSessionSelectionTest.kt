package com.beykus.beygram.share

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareSessionSelectionTest {
    @Test
    fun replaceDestinationsKeepsOrderAndDropsDuplicates() {
        val session = ShareSession(1L, emptyList(), "")
        session.replaceDestinations(
            listOf(
                ShareDestination(1L, 0L, "A"),
                ShareDestination(1L, 0L, "A"),
                ShareDestination(2L, 5L, "B · Тема")
            )
        )
        assertEquals(
            listOf(ShareDestination(1L, 0L, "A"), ShareDestination(2L, 5L, "B · Тема")),
            session.selectedDestinations
        )
    }

    @Test
    fun replaceDestinationsAllowsEmptySelection() {
        val session = ShareSession(1L, emptyList(), "")
        session.addDestination(ShareDestination(1L, 0L, "A"))
        session.replaceDestinations(emptyList())
        assertEquals(emptyList<ShareDestination>(), session.selectedDestinations)
    }
}
