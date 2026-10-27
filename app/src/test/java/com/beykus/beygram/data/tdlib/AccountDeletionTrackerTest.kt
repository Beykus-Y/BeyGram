package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDeletionTrackerTest {
    @After
    fun reset() {
        AccountDeletionTracker.clearTerminal()
        AccountDeletionTracker.clearError()
    }

    @Test
    fun `one deletion may run and progress survives until the UI consumes it`() {
        assertTrue(AccountDeletionTracker.begin("secondary-1"))
        assertFalse(AccountDeletionTracker.begin("secondary-2"))

        val running = AccountDeletionTracker.state.value
        assertTrue(running.running)
        assertEquals("secondary-1", running.accountId)

        AccountDeletionTracker.finish("secondary-1", serverDeleted = true)

        val finished = AccountDeletionTracker.state.value
        assertFalse(finished.running)
        assertTrue(finished.serverDeleted)
        assertEquals("secondary-1", finished.accountId)
        assertNull(finished.error)
    }

    @Test
    fun `cleanup error is retained after terminal navigation is consumed`() {
        AccountDeletionTracker.begin("secondary-1")
        val error = UiText.Res(R.string.account_security_delete_cleanup_failed)
        AccountDeletionTracker.finish("secondary-1", serverDeleted = true, error = error)

        AccountDeletionTracker.clearTerminal()

        val state = AccountDeletionTracker.state.value
        assertNull(state.accountId)
        assertFalse(state.serverDeleted)
        assertEquals(error, state.error)
    }
}
