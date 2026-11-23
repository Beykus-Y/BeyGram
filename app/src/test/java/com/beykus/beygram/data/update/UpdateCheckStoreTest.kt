package com.beykus.beygram.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckStoreTest {
    private val now = 1_800_000_000_000L
    private val pkg = "com.beykus.beygram"
    private val day = 24L * 60 * 60 * 1000

    private fun record(
        installed: Long = 5L,
        checkedAt: Long = now - 1_000L,
        availableCode: Long? = null,
        pkgName: String = pkg,
    ) = UpdateCheckRecord(
        packageName = pkgName,
        installedCode = installed,
        channel = "stable",
        checkedAt = checkedAt,
        outcome = if (availableCode == null) UpdateCheckOutcome.Current else UpdateCheckOutcome.Available,
        availableLabel = availableCode?.let { "0.0.0.$it" },
        availableCode = availableCode,
    )

    @Test
    fun noRecordShowsNothing() {
        assertEquals(UpdateCheckView.None, UpdateCheckStore.evaluate(null, pkg, 5L, now))
    }

    @Test
    fun otherPackageShowsNothing() {
        val view = UpdateCheckStore.evaluate(record(pkgName = "com.other"), pkg, 5L, now)
        assertEquals(UpdateCheckView.None, view)
    }

    @Test
    fun recentRecordForInstalledBuildIsFresh() {
        val rec = record()
        assertEquals(UpdateCheckView.Fresh(rec), UpdateCheckStore.evaluate(rec, pkg, 5L, now))
    }

    @Test
    fun offeredNewerBuildStaysFresh() {
        val rec = record(availableCode = 9L)
        assertEquals(UpdateCheckView.Fresh(rec), UpdateCheckStore.evaluate(rec, pkg, 5L, now))
    }

    @Test
    fun newInstalledVersionMakesRecordStale() {
        val rec = record(installed = 5L)
        assertTrue(UpdateCheckStore.evaluate(rec, pkg, 6L, now) is UpdateCheckView.Stale)
    }

    @Test
    fun offeredBuildAlreadyInstalledMakesRecordStale() {
        val rec = record(installed = 5L, availableCode = 5L)
        assertTrue(UpdateCheckStore.evaluate(rec, pkg, 5L, now) is UpdateCheckView.Stale)
    }

    @Test
    fun oldRecordIsStale() {
        val rec = record(checkedAt = now - 8 * day)
        assertTrue(UpdateCheckStore.evaluate(rec, pkg, 5L, now) is UpdateCheckView.Stale)
    }

    @Test
    fun recordFromTheFutureIsStale() {
        val rec = record(checkedAt = now + 10 * 60 * 1000L)
        assertTrue(UpdateCheckStore.evaluate(rec, pkg, 5L, now) is UpdateCheckView.Stale)
    }

    @Test
    fun smallClockSkewStaysFresh() {
        val rec = record(checkedAt = now + 60 * 1000L)
        assertTrue(UpdateCheckStore.evaluate(rec, pkg, 5L, now) is UpdateCheckView.Fresh)
    }
}
