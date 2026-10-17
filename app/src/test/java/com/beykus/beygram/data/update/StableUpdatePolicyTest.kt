package com.beykus.beygram.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StableUpdatePolicyTest {
    private val checksum = "a".repeat(64)

    @Test fun encodesFourPartVersionAndPreservesOrdering() {
        assertEquals(4L, StableUpdatePolicy.versionCode("0.0.0.4"))
        assertEquals(10_000L, StableUpdatePolicy.versionCode("0.0.1.0"))
        assertEquals(1_000_000L, StableUpdatePolicy.versionCode("0.1.0.0"))
        assertTrue(StableUpdatePolicy.isNewer(1L, 4L))
        assertFalse(StableUpdatePolicy.isNewer(4L, 4L))
        assertFalse(StableUpdatePolicy.isNewer(4L, 1L))
    }

    @Test fun rejectsInvalidVersionStrings() {
        for (invalid in listOf("1.0", "v0.0.0.4", "0.0.0.-1", "0.0.0.10000",
            "0.100.0.0", "21.0.0.0", "0.0.0.0", "0.0.0.4-dev", "garbage")) {
            assertNull(invalid, StableUpdatePolicy.versionCode(invalid))
        }
    }

    @Test fun acceptsOnlyConsistentStableMetadata() {
        assertTrue(StableUpdatePolicy.valid(
            "0.0.0.4", 4L, 70_000_000L, checksum, "BeyGram-0.0.0.4-prod-release.apk"
        ))
        assertFalse(StableUpdatePolicy.valid(
            "0.0.0.4", 3L, 70_000_000L, checksum, "BeyGram-0.0.0.4-prod-release.apk"
        ))
        assertFalse(StableUpdatePolicy.valid(
            "0.0.0.4", 4L, 70_000_000L, checksum, "BeyGram-0.0.0.4-dev-debug.apk"
        ))
        assertFalse(StableUpdatePolicy.valid(
            "0.0.0.4", 4L, 0, checksum, "BeyGram-0.0.0.4-prod-release.apk"
        ))
        assertFalse(StableUpdatePolicy.valid(
            "0.0.0.4", 4L, 70_000_000L, "not-a-sha256", "BeyGram-0.0.0.4-prod-release.apk"
        ))
    }
}
