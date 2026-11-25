package com.beykus.beygram.data.update

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutomaticUpdateChecksTest {
    private class FakeStorage : UpdateNoticeStorage {
        var state = UpdateAutoCheckPolicy.Attempts()
        var notified: Long? = null
        var snoozedCode: Long? = null
        var snoozedUntil: Long? = null

        override fun attempts() = state
        override fun recordAttempt(now: Long) {
            state = state.copy(lastAttemptAt = now)
        }

        override fun recordSuccess(now: Long) {
            state = state.copy(lastSuccessAt = now, consecutiveFailures = 0, rateLimitedUntil = null)
        }

        override fun recordFailure(now: Long, rateLimited: Boolean) {
            state = state.copy(
                consecutiveFailures = state.consecutiveFailures + 1,
                rateLimitedUntil = if (rateLimited) UpdateAutoCheckPolicy.afterRateLimit(now) else state.rateLimitedUntil,
            )
        }

        override fun notifiedVersionCode() = notified
        override fun saveNotifiedVersionCode(code: Long?) { notified = code }
        override fun snoozedVersionCode() = snoozedCode
        override fun snoozedUntil() = snoozedUntil
        override fun snoozeBanner(code: Long, until: Long) { snoozedCode = code; snoozedUntil = until }
        override fun clearSnooze() { snoozedCode = null; snoozedUntil = null }
    }

    private val release = StableRelease(
        tag = "v0.0.6.4",
        versionName = "0.0.6.4",
        versionCode = 64L,
        apkName = "BeyGram-0.0.6.4-prod-release.apk",
        size = 10L,
        sha256 = "a".repeat(64),
        notes = "Notes",
        publishedAt = "2026-11-25T00:00:00Z",
    )

    private fun checks(
        storage: FakeStorage,
        scope: kotlinx.coroutines.CoroutineScope,
        now: () -> Long,
        outcome: () -> AutomaticCheckOutcome,
        announced: MutableList<StableRelease> = mutableListOf(),
        withdrawn: MutableList<Unit> = mutableListOf(),
    ) = AutomaticUpdateChecks(
        source = AutomaticCheckSource { outcome() },
        storage = storage,
        scope = scope,
        announce = { offered, _ -> announced.add(offered) },
        withdraw = { withdrawn.add(Unit) },
        statuses = kotlinx.coroutines.flow.emptyFlow(),
        clock = now,
        log = {},
    )

    @Test
    fun `an available offer is recorded and announced once`() = runTest {
        val storage = FakeStorage()
        val announced = mutableListOf<StableRelease>()
        val subject = checks(storage, this, { 1_000L }, { AutomaticCheckOutcome.Available(release) }, announced)

        subject.attempt(1_000L)
        assertEquals(1_000L, storage.state.lastAttemptAt)
        assertEquals(1_000L, storage.state.lastSuccessAt)
        assertEquals(listOf(release), announced)

        // A successful check does not reset the counts it did not touch.
        assertEquals(0, storage.state.consecutiveFailures)
    }

    @Test
    fun `a network failure raises the backoff instead of claiming the latest version`() = runTest {
        val storage = FakeStorage()
        val announced = mutableListOf<StableRelease>()
        val subject = checks(
            storage, this, { 5_000L },
            { AutomaticCheckOutcome.Failed(StableUpdateReason.Network) }, announced,
        )

        subject.attempt(1_000L)
        assertEquals(1, storage.state.consecutiveFailures)
        assertTrue(announced.isEmpty())
        assertNull(storage.state.lastSuccessAt)
    }

    @Test
    fun `a rate limit stops every trigger for an hour`() = runTest {
        val storage = FakeStorage()
        var checked = 0
        val subject = checks(
            storage, this, { 10_000L },
            { checked++; AutomaticCheckOutcome.Failed(StableUpdateReason.RateLimited) },
        )

        subject.attempt(10_000L)
        assertEquals(10_000L + 60 * 60_000L, storage.state.rateLimitedUntil)

        // A resume right afterwards must not reach the source at all.
        subject.onForeground()
        advanceUntilIdle()
        assertEquals(1, checked)
    }

    @Test
    fun `a skipped check is not counted as a failure`() = runTest {
        val storage = FakeStorage()
        val subject = checks(storage, this, { 1_000L }, { AutomaticCheckOutcome.Skipped })

        subject.attempt(1_000L)
        assertEquals(0, storage.state.consecutiveFailures)
        assertNull(storage.state.lastSuccessAt)
        // The attempt itself is still remembered, so a user action cannot turn into a request per resume.
        assertEquals(1_000L, storage.state.lastAttemptAt)
    }

    @Test
    fun `current version withdraws the notification`() = runTest {
        val storage = FakeStorage()
        storage.notified = 64L
        val withdrawn = mutableListOf<Unit>()
        val subject = checks(storage, this, { 1_000L }, { AutomaticCheckOutcome.Current }, withdrawn = withdrawn)

        subject.attempt(1_000L)
        assertEquals(1, withdrawn.size)
        assertEquals(1_000L, storage.state.lastSuccessAt)
    }

    @Test
    fun `two foreground triggers in the same second issue one request`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val storage = FakeStorage()
        var checked = 0
        val subject = checks(
            storage, kotlinx.coroutines.CoroutineScope(dispatcher), { 1_000L },
            { checked++; AutomaticCheckOutcome.Current },
        )

        subject.onForeground()
        subject.onForeground()
        advanceUntilIdle()
        assertEquals(1, checked)
        assertNotNull(storage.state.lastAttemptAt)
    }

    @Test
    fun `the periodic interval keeps a successful check from repeating sooner`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val storage = FakeStorage()
        var now = 0L
        var checked = 0
        val subject = checks(
            storage, kotlinx.coroutines.CoroutineScope(dispatcher), { now },
            { checked++; AutomaticCheckOutcome.Current },
        )

        now = 1_000L
        subject.onForeground()
        advanceUntilIdle()
        assertEquals(1, checked)

        // Ten minutes later the resume interval has not elapsed for the retry policy... but the debounce is per
        // trigger; the gate uses the stored attempt instead, so nothing happens inside the interval.
        now = 1_000L + 60_000L
        subject.onForeground()
        advanceUntilIdle()
        assertEquals(1, checked)

        now = 1_000L + UpdateAutoCheckPolicy.FOREGROUND_INTERVAL_MS
        subject.onForeground()
        advanceUntilIdle()
        assertEquals(2, checked)
    }
}