package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.PaidReactionUncertainException
import com.beykus.beygram.domain.model.StarBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PaidReactionStoreTest {
    private val owner = Any()
    private val other = Any()
    private val target = PaidReactionTarget(chatId = 5L, messageId = 9L)
    private val opened = PaidReactionState(owner = owner, target = target, balance = StarBalance(starCount = 100), starCount = 10L)
    private val sending = opened.copy(sending = true)
    private val uncertain = opened.copy(uncertain = true, failure = UiText.Res(R.string.err_paid_reaction_uncertain))

    @Test fun `closing an idle confirmation releases it entirely`() {
        assertEquals(PaidReactionState(), opened.released(owner))
    }

    @Test fun `an in-flight send outlives its screen without an owner`() {
        val released = sending.released(owner)
        assertNull(released.owner)
        assertTrue(released.sending)
        assertEquals(target, released.target)
    }

    @Test fun `an uncertain result outlives its screen until another screen takes it over`() {
        val released = uncertain.released(owner)
        assertNull(released.owner)
        assertTrue(released.uncertain)
        assertEquals(UiText.Res(R.string.err_paid_reaction_uncertain), released.failure)
    }

    @Test fun `a screen that does not own the confirmation leaves it alone`() {
        assertSame(opened, opened.released(other))
        assertSame(sending, sending.released(other))
    }

    @Test fun `another screen takes over a send that no screen owns`() {
        val orphan = sending.released(owner)
        assertSame(other, orphan.claimedBy(other).owner)
        assertTrue(orphan.claimedBy(other).sending)
    }

    @Test fun `a screen cannot take over a confirmation that another screen still owns`() {
        assertSame(opened, opened.claimedBy(other))
    }

    @Test fun `an idle confirmation without an owner is not claimed`() {
        val unowned = opened.copy(owner = null)
        assertSame(unowned, unowned.claimedBy(other))
    }

    @Test fun `a confirmed send closes the confirmation`() {
        assertEquals(PaidReactionState(), sending.sendFinished(Result.success(Unit)))
    }

    @Test fun `a refused send keeps the confirmation open with its reason`() {
        val finished = sending.sendFinished(Result.failure(UserMessageException(UiText.Res(R.string.err_paid_reaction_balance_low))))
        assertFalse(finished.sending)
        assertFalse(finished.uncertain)
        assertEquals(UiText.Res(R.string.err_paid_reaction_balance_low), finished.failure)
        assertSame(owner, finished.owner)
        assertEquals(target, finished.target)
    }

    @Test fun `a send whose outcome is unknown is uncertain until it is closed`() {
        val finished = sending.sendFinished(
            Result.failure(PaidReactionUncertainException(UiText.Res(R.string.err_paid_reaction_uncertain)))
        )
        assertTrue(finished.uncertain)
        assertFalse(finished.sending)
        assertEquals(UiText.Res(R.string.err_paid_reaction_uncertain), finished.failure)
    }

    @Test fun `a send that finishes after its screen left keeps no owner and stays uncertain`() {
        val finished = sending.released(owner).sendFinished(
            Result.failure(PaidReactionUncertainException(UiText.Res(R.string.err_paid_reaction_uncertain)))
        )
        assertNull(finished.owner)
        assertTrue(finished.uncertain)
    }

    @Test fun `a balance that is read is kept for the dialog`() {
        val read = opened.copy(balance = null, balanceLoading = true).balanceRead(Result.success(StarBalance(starCount = 42)))
        assertEquals(StarBalance(starCount = 42), read.balance)
        assertFalse(read.balanceLoading)
        assertNull(read.balanceFailure)
    }

    @Test fun `a balance that cannot be read is shown as a failure`() {
        val failed = opened.copy(balance = null, balanceLoading = true).balanceRead(Result.failure(IllegalStateException("offline")))
        assertFalse(failed.balanceLoading)
        assertEquals(UiText.Res(R.string.stars_load_error), failed.balanceFailure)
    }
}
