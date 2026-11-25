package com.beykus.beygram.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateBannerStateTest {
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

    private fun state(status: UpdateStatus, known: StableRelease? = null, stale: Boolean = false) =
        UpdateUiState(status = status, knownRelease = known, checkStale = stale)

    @Test
    fun `a confirmed newer build is shown`() {
        val banner = updateBannerState(state(UpdateStatus.Available(release)), 63L, 1_000L, null, null)
        assertEquals("0.0.6.4", banner?.versionName)
        assertEquals(false, banner?.stale)
    }

    @Test
    fun `the same build is not shown once installed`() {
        assertNull(updateBannerState(state(UpdateStatus.Available(release)), 64L, 1_000L, null, null))
        assertNull(updateBannerState(state(UpdateStatus.Available(release)), 65L, 1_000L, null, null))
    }

    @Test
    fun `an offer kept after a failed check is marked unconfirmed`() {
        val banner = updateBannerState(
            state(UpdateStatus.Failure(UpdateStep.Check, StableUpdateReason.Network, null), known = release, stale = true),
            63L, 1_000L, null, null,
        )
        assertTrue(banner?.stale == true)
    }

    @Test
    fun `later hides only the same build and only until the window ends`() {
        val snoozedUntil = 10_000L
        assertNull(updateBannerState(state(UpdateStatus.Available(release)), 63L, 5_000L, 64L, snoozedUntil))
        // After the window the same build may return.
        assertEquals("0.0.6.4", updateBannerState(state(UpdateStatus.Available(release)), 63L, snoozedUntil, 64L, snoozedUntil)?.versionName)
        // A newer build is never hidden by an older snooze.
        val newer = release.copy(tag = "v0.0.6.5", versionName = "0.0.6.5", versionCode = 65L)
        assertEquals("0.0.6.5", updateBannerState(state(UpdateStatus.Available(newer)), 63L, 5_000L, 64L, snoozedUntil)?.versionName)
    }

    @Test
    fun `an active download owns the screen`() {
        val downloading = UpdateStatus.Downloading(release, DownloadProgress(0L, release.size, null, null))
        assertNull(updateBannerState(state(downloading), 63L, 1_000L, null, null))
    }

    @Test
    fun `an installed build and a check in progress without an offer show nothing`() {
        assertNull(updateBannerState(state(UpdateStatus.Installed(63L)), 63L, 1_000L, null, null))
        assertNull(updateBannerState(state(UpdateStatus.Checking), 63L, 1_000L, null, null))
        assertNull(updateBannerState(state(UpdateStatus.Current), 63L, 1_000L, null, null))
    }
}