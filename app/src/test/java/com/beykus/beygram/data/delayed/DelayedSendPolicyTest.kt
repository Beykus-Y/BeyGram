package com.beykus.beygram.data.delayed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DelayedSendPolicyTest {
    private fun entry(
        id: String,
        createdAtMillis: Long = 0L,
        notBeforeMillis: Long = createdAtMillis + 5_000L,
        status: DelayedSendStatus = DelayedSendStatus.Queued
    ) = DelayedSendEntry(
        id = id,
        accountId = "account-1",
        chatId = 1L,
        topicId = 0L,
        replyToMessageId = 0L,
        text = "текст",
        entities = emptyList(),
        disableNotification = false,
        createdAtMillis = createdAtMillis,
        notBeforeMillis = notBeforeMillis,
        status = status
    )

    private fun eligible(
        delaySeconds: Int = 5,
        hasAttachments: Boolean = false,
        isEdit: Boolean = false,
        scheduled: Boolean = false,
        hasSticker: Boolean = false,
        text: String = "привет"
    ): Boolean = delayedSendEligible(delaySeconds, hasAttachments, isEdit, scheduled, hasSticker, text)

    @Test fun plainTextWithADelayIsEligible() {
        assertTrue(eligible())
    }

    @Test fun noDelayMeansNoQueue() {
        assertFalse(eligible(delaySeconds = 0))
    }

    @Test fun attachmentsEditsScheduledAndStickersGoOutAtOnce() {
        assertFalse(eligible(hasAttachments = true))
        assertFalse(eligible(isEdit = true))
        assertFalse(eligible(scheduled = true))
        assertFalse(eligible(hasSticker = true))
    }

    @Test fun blankTextIsNotQueued() {
        assertFalse(eligible(text = ""))
        assertFalse(eligible(text = " \n\t"))
    }

    @Test fun dueEntriesAreQueuedOnesWhoseTimeHasComeOldestFirst() {
        val early = entry("early", createdAtMillis = 1L, notBeforeMillis = 100L)
        val onTime = entry("on-time", createdAtMillis = 2L, notBeforeMillis = 500L)
        val later = entry("later", createdAtMillis = 3L, notBeforeMillis = 900L)
        assertEquals(listOf(early, onTime), dueEntries(listOf(later, onTime, early), nowMillis = 500L))
    }

    @Test fun inFlightOrFinishedEntriesAreNeverDue() {
        val dispatching = entry("dispatching", notBeforeMillis = 0L, status = DelayedSendStatus.Dispatching)
        val failed = entry("failed", notBeforeMillis = 0L, status = DelayedSendStatus.Failed)
        val uncertain = entry("uncertain", notBeforeMillis = 0L, status = DelayedSendStatus.Uncertain)
        assertTrue(dueEntries(listOf(dispatching, failed, uncertain), nowMillis = 1_000L).isEmpty())
    }

    @Test fun inFlightAtRestartBecomesUncertainAndIsNotResent() {
        val inFlight = entry("in-flight", notBeforeMillis = 0L, status = DelayedSendStatus.Dispatching)
        val load = reconcileOnLoad(listOf(inFlight), nowMillis = 1_000L, hideOnline = false)
        assertEquals(DelayedSendStatus.Uncertain, load.entries.single().status)
        assertTrue(load.heldAtLoad.isEmpty())
        assertTrue(dueEntries(load.entries, nowMillis = 1_000L).isEmpty())
    }

    @Test fun failedAndUncertainEntriesKeepTheirStatusOnLoad() {
        val failed = entry("failed", status = DelayedSendStatus.Failed)
        val uncertain = entry("uncertain", status = DelayedSendStatus.Uncertain)
        val load = reconcileOnLoad(listOf(failed, uncertain), nowMillis = 1_000L, hideOnline = true)
        assertEquals(listOf(failed, uncertain), load.entries)
        assertTrue(load.heldAtLoad.isEmpty())
    }

    @Test fun withHideOnlineDueEntriesWaitForTheUser() {
        val due = entry("due", createdAtMillis = 0L, notBeforeMillis = 1_000L)
        val notYet = entry("not-yet", createdAtMillis = 0L, notBeforeMillis = 9_000L)
        val load = reconcileOnLoad(listOf(due, notYet), nowMillis = 5_000L, hideOnline = true)
        assertEquals(setOf("due"), load.heldAtLoad)
        assertEquals(listOf(due, notYet), load.entries)
    }

    @Test fun withoutHideOnlineDueEntriesAreNotHeld() {
        val due = entry("due", createdAtMillis = 0L, notBeforeMillis = 1_000L)
        val load = reconcileOnLoad(listOf(due), nowMillis = 5_000L, hideOnline = false)
        assertTrue(load.heldAtLoad.isEmpty())
        assertEquals(listOf(due), dueEntries(load.entries, nowMillis = 5_000L))
    }

    @Test fun clockSetBackBringsAFarFutureTimeForward() {
        // Queued at 1_000_000 with a 10 s delay, then the wall clock was set back to 100_000.
        val queued = entry("clock", createdAtMillis = 1_000_000L, notBeforeMillis = 1_010_000L)
        assertEquals(110_000L, clampNotBefore(queued, nowMillis = 100_000L).notBeforeMillis)
        assertEquals(
            110_000L,
            reconcileOnLoad(listOf(queued), nowMillis = 100_000L, hideOnline = false).entries.single().notBeforeMillis
        )
    }

    @Test fun normalClockLeavesTheTimeAlone() {
        val queued = entry("normal", createdAtMillis = 1_000_000L, notBeforeMillis = 1_010_000L)
        // Exactly two delays ahead is still accepted; one millisecond further back is clamped to one delay from now.
        assertEquals(queued, clampNotBefore(queued, nowMillis = 990_000L))
        assertEquals(queued, clampNotBefore(queued, nowMillis = 1_005_000L))
        assertEquals(999_999L, clampNotBefore(queued, nowMillis = 989_999L).notBeforeMillis)
    }
}
