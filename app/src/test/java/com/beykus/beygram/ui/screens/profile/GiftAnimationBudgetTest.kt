package com.beykus.beygram.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GiftAnimationBudgetTest {
    @Test
    fun budgetEnforcesMaxActiveLimitAndAllowsReacquireAfterRelease() {
        val budget = GiftAnimationBudget(maxActive = 2)
        val a = Any()
        val b = Any()
        val c = Any()

        assertTrue(budget.tryAcquire(a))
        assertTrue(budget.tryAcquire(b))
        assertFalse(budget.tryAcquire(c))
        assertEquals(2, budget.activeCount)
        assertTrue(budget.isActive(a))
        assertFalse(budget.isActive(c))

        // Re-acquiring an already active key is idempotent and does not consume a new slot.
        assertTrue(budget.tryAcquire(a))
        assertEquals(2, budget.activeCount)

        // Releasing frees a slot for c.
        budget.release(a)
        assertFalse(budget.isActive(a))
        assertTrue(budget.tryAcquire(c))
        assertEquals(2, budget.activeCount)
        assertTrue(budget.isActive(c))
    }
}
