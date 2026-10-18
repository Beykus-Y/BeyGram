package com.beykus.beygram.data.tdlib

import org.junit.Assert.assertEquals
import org.junit.Test

class ListedMediaFilesTest {
    private var now = 0L
    private val listed = ListedMediaFiles { now }

    @Test fun listedFilesStayProtectedWithinTheWindow() {
        listed.remember(listOf(1, 2))
        now += 29 * 60_000L
        assertEquals(setOf(1, 2), listed.current())
    }

    @Test fun listedFilesAreForgottenAfterTheWindow() {
        listed.remember(listOf(1))
        now += 31 * 60_000L
        assertEquals(emptySet<Int>(), listed.current())
    }

    @Test fun relistingRestartsTheWindowOfThatFileOnly() {
        listed.remember(listOf(1, 2))
        now += 20 * 60_000L
        listed.remember(listOf(1))
        now += 20 * 60_000L
        assertEquals(setOf(1), listed.current())
    }
}
