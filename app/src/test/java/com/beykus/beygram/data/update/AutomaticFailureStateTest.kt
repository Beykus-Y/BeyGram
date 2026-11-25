package com.beykus.beygram.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the Codex review of #1166: an unattended failure must not leave the card on "Checking".
 */
class AutomaticFailureStateTest {
    private val release = StableRelease(
        tag = "v0.0.6.4",
        versionName = "0.0.6.4",
        versionCode = 64L,
        apkName = "BeyGram-0.0.6.4-prod-release.apk",
        size = 10L,
        sha256 = "a".repeat(64),
        notes = "",
        publishedAt = "",
    )

    @Test
    fun `a failed automatic check restores the previous offer instead of a spinner`() {
        val state = UpdateUiState(status = UpdateStatus.Checking, knownRelease = release)
        val restored = restoreAfterAutomaticFailure(state, previous = UpdateStatus.Available(release))

        assertEquals(UpdateStatus.Available(release), restored.status)
        assertTrue(restored.checkStale)
    }

    @Test
    fun `a failed automatic check with no previous status falls back to idle`() {
        val restored = restoreAfterAutomaticFailure(UpdateUiState(status = UpdateStatus.Checking), previous = null)
        assertEquals(UpdateStatus.Idle, restored.status)
    }

    @Test
    fun `a previous failure state is restored as it was`() {
        val previous = UpdateStatus.Failure(UpdateStep.Check, StableUpdateReason.Network, null)
        val restored = restoreAfterAutomaticFailure(
            UpdateUiState(status = UpdateStatus.Checking, knownRelease = release),
            previous = previous,
        )
        assertEquals(previous, restored.status)
    }

    @Test
    fun `a status changed by a user action during the check is not overwritten`() {
        // A download started while the unattended check was in flight: the restore must not touch it.
        val downloading = UpdateStatus.Downloading(release, DownloadProgress(0L, release.size, null, null))
        val restored = restoreAfterAutomaticFailure(UpdateUiState(status = downloading), previous = UpdateStatus.Available(release))
        assertEquals(downloading, restored.status)
    }

    @Test
    fun `an installed status stays installed`() {
        val restored = restoreAfterAutomaticFailure(UpdateUiState(status = UpdateStatus.Installed(64L)), previous = UpdateStatus.Current)
        assertEquals(UpdateStatus.Installed(64L), restored.status)
    }
}