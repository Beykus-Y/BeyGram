package com.beykus.beygram.data.update

import com.beykus.beygram.data.update.ApkRetentionPolicy.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkRetentionPolicyTest {
    private val now = 100L * 24 * 60 * 60 * 1000
    private val hour = 60L * 60 * 1000
    private val day = 24 * hour

    private fun entry(name: String, ageMs: Long, isProtected: Boolean = false) =
        Entry(name, size = 1_000L, lastModifiedMs = now - ageMs, isProtected = isProtected)

    @Test fun protectedFilesAreNeverDeletedHoweverOld() {
        val stale = ApkRetentionPolicy.staleNames(
            listOf(
                entry("ready.apk", 30 * day, isProtected = true),
                entry("handed-off.apk", 30 * day, isProtected = true),
                entry("resume.part", 30 * day, isProtected = true),
            ),
            now,
        )
        assertTrue(stale.isEmpty())
    }

    @Test fun onlyTheTwoNewestUnprotectedApksAreKept() {
        val stale = ApkRetentionPolicy.staleNames(
            listOf(
                entry("a.apk", 1 * hour),
                entry("b.apk", 2 * hour),
                entry("c.apk", 3 * hour),
                entry("d.apk", 4 * hour),
            ),
            now,
        )
        assertEquals(setOf("c.apk", "d.apk"), stale.toSet())
    }

    @Test fun protectedApksDoNotCountTowardsTheLimit() {
        val stale = ApkRetentionPolicy.staleNames(
            listOf(
                entry("ready.apk", 9 * hour, isProtected = true),
                entry("a.apk", 1 * hour),
                entry("b.apk", 2 * hour),
            ),
            now,
        )
        assertTrue(stale.isEmpty())
    }

    @Test fun apksOlderThanSevenDaysGoEvenWithinTheLimit() {
        val stale = ApkRetentionPolicy.staleNames(
            listOf(entry("fresh.apk", 1 * hour), entry("old.apk", 8 * day)),
            now,
        )
        assertEquals(listOf("old.apk"), stale)
    }

    @Test fun unprotectedPartialsOlderThanADayGo() {
        val stale = ApkRetentionPolicy.staleNames(
            listOf(entry("fresh.part", 2 * hour), entry("abandoned.part", 2 * day)),
            now,
        )
        assertEquals(listOf("abandoned.part"), stale)
    }

    @Test fun otherFilesAreNeverCandidates() {
        val stale = ApkRetentionPolicy.staleNames(
            listOf(entry("tdlib.db", 30 * day), entry("notes.json", 30 * day), entry("photo.jpg", 30 * day)),
            now,
        )
        assertTrue(stale.isEmpty())
    }
}
