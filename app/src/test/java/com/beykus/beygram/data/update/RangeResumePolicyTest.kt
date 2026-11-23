package com.beykus.beygram.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeResumePolicyTest {
    private val size = 1_000L
    private val etag = "\"abc\""
    private val plan = RangeResumePolicy.plan(partialBytes = 400, validator = etag, expectedSize = size)!!

    @Test fun planNeedsSomeBytesNotAllOfThemAndAStrongValidator() {
        assertEquals(400L, plan.offset)
        assertEquals(mapOf("Range" to "bytes=400-", "If-Range" to etag), plan.headers())
        assertNull(RangeResumePolicy.plan(0, etag, size))
        assertNull(RangeResumePolicy.plan(size, etag, size))
        assertNull(RangeResumePolicy.plan(400, null, size))
        assertNull(RangeResumePolicy.plan(400, "W/\"weak\"", size))
    }

    @Test fun validPartialReplyIsAppended() {
        val decision = RangeResumePolicy.decide(plan, 206, "bytes 400-999/1000", etag, size)
        assertEquals(ResumeDecision.Append(400L), decision)
    }

    @Test fun wholeFileReplyRestartsFromZero() {
        assertEquals(ResumeDecision.FromStart, RangeResumePolicy.decide(plan, 200, null, etag, size))
        assertEquals(ResumeDecision.FromStart, RangeResumePolicy.decide(null, 200, null, etag, size))
    }

    @Test fun rangeNotSatisfiableDeletesThePartial() {
        assertEquals(ResumeDecision.RestartWithoutRange, RangeResumePolicy.decide(plan, 416, null, null, size))
    }

    @Test fun wrongStartOrTotalIsAMismatch() {
        assertEquals(ResumeDecision.RestartWithoutRange,
            RangeResumePolicy.decide(plan, 206, "bytes 300-999/1000", etag, size))
        assertEquals(ResumeDecision.RestartWithoutRange,
            RangeResumePolicy.decide(plan, 206, "bytes 400-999/2000", etag, size))
        assertEquals(ResumeDecision.RestartWithoutRange,
            RangeResumePolicy.decide(plan, 206, "bytes 400-998/1000", etag, size))
    }

    @Test fun missingOrMalformedContentRangeIsAMismatch() {
        assertEquals(ResumeDecision.RestartWithoutRange, RangeResumePolicy.decide(plan, 206, null, etag, size))
        assertEquals(ResumeDecision.RestartWithoutRange, RangeResumePolicy.decide(plan, 206, "bytes 400-999/*", etag, size))
        assertEquals(ResumeDecision.RestartWithoutRange, RangeResumePolicy.decide(plan, 206, "items 400-999/1000", etag, size))
        assertEquals(ResumeDecision.RestartWithoutRange,
            RangeResumePolicy.decide(plan, 206, "bytes 400-99999999999999999999999/1000", etag, size))
    }

    @Test fun changedValidatorOnAPartialReplyIsAMismatch() {
        assertEquals(ResumeDecision.RestartWithoutRange,
            RangeResumePolicy.decide(plan, 206, "bytes 400-999/1000", "\"other\"", size))
    }

    @Test fun unexpectedStatusesAreRefused() {
        assertEquals(ResumeDecision.Reject, RangeResumePolicy.decide(plan, 500, null, null, size))
        assertEquals(ResumeDecision.Reject, RangeResumePolicy.decide(null, 206, "bytes 0-999/1000", null, size))
        assertEquals(ResumeDecision.Reject, RangeResumePolicy.decide(null, 416, null, null, size))
    }

    @Test fun strongValidatorPrefersAStrongEtagThenLastModified() {
        assertEquals("\"abc\"", RangeResumePolicy.strongValidator("\"abc\"", "Wed, 01 Jan 2025 00:00:00 GMT"))
        assertEquals("Wed, 01 Jan 2025 00:00:00 GMT",
            RangeResumePolicy.strongValidator("W/\"abc\"", "Wed, 01 Jan 2025 00:00:00 GMT"))
        assertNull(RangeResumePolicy.strongValidator("W/\"abc\"", null))
        assertFalse(RangeResumePolicy.matchesRange(null, 0, size))
        assertTrue(RangeResumePolicy.matchesRange("bytes 0-999/1000", 0, size))
    }
}
