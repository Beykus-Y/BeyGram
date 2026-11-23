package com.beykus.beygram.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StableReleaseIdentityTest {
    private val sha = "a".repeat(64)

    private fun release(
        sha256: String = sha,
        size: Long = 70_000_000L,
        versionCode: Long = StableUpdatePolicy.versionCode("0.0.6.3")!!,
        notes: String = "Original notes",
        publishedAt: String = "2026-11-01T10:00:00Z",
    ) = StableRelease(
        tag = "v0.0.6.3",
        versionName = "0.0.6.3",
        versionCode = versionCode,
        apkName = "BeyGram-0.0.6.3-prod-release.apk",
        size = size,
        sha256 = sha256,
        notes = notes,
        publishedAt = publishedAt,
    )

    @Test fun editedNotesAndPublishedAtKeepIdentity() {
        val original = release()
        val edited = original.copy(notes = "Edited on GitHub", publishedAt = "2026-11-02T12:00:00Z")
        assertEquals(original, edited)
        assertEquals(original.hashCode(), edited.hashCode())
    }

    @Test fun downloadedApkStaysInstallableAfterNotesEdit() {
        val original = release()
        val edited = original.copy(notes = "Edited on GitHub")
        assertTrue(StableUpdateActionPolicy.canInstall(UpdateStatus.Available(edited), original))
        assertTrue(
            StableUpdateActionPolicy.canInstall(
                UpdateStatus.Ready(edited, File("x.apk")),
                original,
            )
        )
    }

    @Test fun differentArtifactFieldsMakeReleasesUnequal() {
        val original = release()
        assertNotEquals(original, release(sha256 = "b".repeat(64)))
        assertNotEquals(original, release(size = 70_000_001L))
        assertNotEquals(original, release(versionCode = original.versionCode + 1))
    }
}
