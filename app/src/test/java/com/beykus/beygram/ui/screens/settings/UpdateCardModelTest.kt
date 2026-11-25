package com.beykus.beygram.ui.screens.settings

import com.beykus.beygram.data.update.DownloadProgress
import com.beykus.beygram.data.update.InstallNotice
import com.beykus.beygram.data.update.ReadyApk
import com.beykus.beygram.data.update.StableRelease
import com.beykus.beygram.data.update.StableUpdateActionPolicy
import com.beykus.beygram.data.update.StableUpdateReason
import com.beykus.beygram.data.update.UpdateStatus
import com.beykus.beygram.data.update.UpdateStep
import com.beykus.beygram.data.update.UpdateUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateCardModelTest {
    private val offer = StableRelease(
        tag = "v0.0.0.7",
        versionName = "0.0.0.7",
        versionCode = 7L,
        apkName = "BeyGram-0.0.0.7-prod-release.apk",
        size = 1000L,
        sha256 = "a".repeat(64),
        notes = "",
        publishedAt = ""
    )
    private val other = offer.copy(tag = "v0.0.0.8", versionName = "0.0.0.8", versionCode = 8L)
    private val file = File("ready.apk")

    private fun state(
        status: UpdateStatus,
        ready: ReadyApk? = null,
        notice: InstallNotice? = null,
        known: StableRelease? = null,
        checkedAt: Long? = null,
        stale: Boolean = false,
    ) = UpdateUiState(
        status = status,
        ready = ready,
        installNotice = notice,
        knownRelease = known,
        lastCheckedAt = checkedAt,
        checkStale = stale,
    )

    private fun progress(bytes: Long) = DownloadProgress(bytes, 1000L, null, null)

    @Test fun everyStatusHasItsOwnPhase() {
        val statuses = listOf(
            UpdateStatus.Idle,
            UpdateStatus.Checking,
            UpdateStatus.Current,
            UpdateStatus.Available(offer),
            UpdateStatus.Downloading(offer, progress(10L)),
            UpdateStatus.Verifying(offer),
            UpdateStatus.Ready(offer, file),
            UpdateStatus.PreparingInstall(offer),
            UpdateStatus.PermissionRequired(offer, file),
            UpdateStatus.AwaitingInstaller(offer, file),
            UpdateStatus.Installed(7L),
            UpdateStatus.Failure(UpdateStep.Check, StableUpdateReason.Network, null),
        )
        val phases = statuses.map { updateCardModel(state(it)).phase }
        assertEquals(UpdatePhase.values().toList(), phases)
    }

    @Test fun idleWithoutKnownOfferAsksForCheck() {
        val model = updateCardModel(state(UpdateStatus.Idle))
        assertEquals(UpdatePhase.Idle, model.phase)
        assertNull(model.release)
        assertEquals(UpdateAction.Check, model.primary)
        assertNull(model.secondary)
        assertFalse(model.showProgress)
        assertFalse(model.showTopCheck)
    }

    @Test fun idleAfterRestartKeepsTheKnownOfferAsStale() {
        val model = updateCardModel(state(UpdateStatus.Idle, known = offer, checkedAt = 123L, stale = true))
        assertEquals(offer, model.release)
        assertTrue(model.stale)
        assertEquals(UpdateAction.Check, model.primary)
        assertEquals(123L, model.lastCheckedAt)
        assertFalse(model.showTopCheck)
    }

    @Test fun checkingShowsNoButtonAndKeepsTheKnownOfferFresh() {
        val model = updateCardModel(state(UpdateStatus.Checking, known = offer, checkedAt = 5L, stale = false))
        assertEquals(UpdatePhase.Checking, model.phase)
        assertEquals(offer, model.release)
        assertFalse(model.stale)
        assertNull(model.primary)
        assertFalse(model.showTopCheck)
    }

    @Test fun currentOffersARecheckAndNoRelease() {
        val model = updateCardModel(state(UpdateStatus.Current))
        assertEquals(UpdatePhase.Current, model.phase)
        assertNull(model.release)
        assertEquals(UpdateAction.Check, model.primary)
        assertFalse(model.stale)
    }

    @Test fun availableOffersDownloadAndTopCheck() {
        val model = updateCardModel(state(UpdateStatus.Available(offer)))
        assertEquals(UpdatePhase.Available, model.phase)
        assertEquals(offer, model.release)
        assertEquals(UpdateAction.Download, model.primary)
        assertNull(model.secondary)
        assertTrue(model.showTopCheck)
        assertFalse(model.stale)
    }

    @Test fun verifiedAvailableOfferInstallsWithoutDownloadingAgain() {
        val model = updateCardModel(state(UpdateStatus.Available(offer), ready = ReadyApk(offer, file)))
        assertEquals(UpdateAction.Install, model.primary)
    }

    @Test fun verifiedApkOfAnotherReleaseDoesNotOfferInstall() {
        val model = updateCardModel(state(UpdateStatus.Available(other), ready = ReadyApk(offer, file)))
        assertEquals(UpdateAction.Download, model.primary)
    }

    @Test fun downloadingShowsProgressAndCancelForItsOwnRelease() {
        val bytes = DownloadProgress(500L, 1000L, 2048L, 4L)
        val model = updateCardModel(state(UpdateStatus.Downloading(offer, bytes)))
        assertEquals(UpdatePhase.Downloading, model.phase)
        assertEquals(offer, model.release)
        assertEquals(UpdateAction.Cancel, model.primary)
        assertTrue(model.showProgress)
        assertFalse(model.indeterminateProgress)
        assertEquals(bytes, model.progress)
        assertFalse(model.showTopCheck)
    }

    @Test fun fullBytesAreStillDownloadingNotReady() {
        val model = updateCardModel(state(UpdateStatus.Downloading(offer, progress(1000L))))
        assertEquals(UpdatePhase.Downloading, model.phase)
        assertEquals(UpdateAction.Cancel, model.primary)
    }

    @Test fun verifyingHasIndeterminateProgressAndNoAction() {
        val model = updateCardModel(state(UpdateStatus.Verifying(offer)))
        assertEquals(UpdatePhase.Verifying, model.phase)
        assertNull(model.primary)
        assertNull(model.secondary)
        assertTrue(model.showProgress)
        assertTrue(model.indeterminateProgress)
        assertNull(model.progress)
    }

    @Test fun readyOffersInstall() {
        val model = updateCardModel(state(UpdateStatus.Ready(offer, file)))
        assertEquals(UpdatePhase.Ready, model.phase)
        assertEquals(UpdateAction.Install, model.primary)
        assertTrue(model.showTopCheck)
        assertFalse(model.showProgress)
    }

    @Test fun preparingInstallAndAwaitingInstallerHaveNoAction() {
        val preparing = updateCardModel(state(UpdateStatus.PreparingInstall(offer)))
        assertEquals(UpdatePhase.PreparingInstall, preparing.phase)
        assertNull(preparing.primary)
        val awaiting = updateCardModel(state(UpdateStatus.AwaitingInstaller(offer, file)))
        assertEquals(UpdatePhase.AwaitingInstaller, awaiting.phase)
        assertNull(awaiting.primary)
        assertNull(awaiting.secondary)
        assertFalse(awaiting.showTopCheck)
    }

    @Test fun permissionRequiredOffersAllowThenContinue() {
        val model = updateCardModel(state(UpdateStatus.PermissionRequired(offer, file)))
        assertEquals(UpdatePhase.PermissionRequired, model.phase)
        assertEquals(UpdateAction.AllowInstall, model.primary)
        assertEquals(UpdateAction.ContinueInstall, model.secondary)
        assertFalse(model.showTopCheck)
    }

    @Test fun installNoticeAppearsOnlyNextToInstall() {
        val ready = updateCardModel(state(UpdateStatus.Ready(offer, file), ready = ReadyApk(offer, file), notice = InstallNotice.LaunchFailed))
        assertEquals(InstallNotice.LaunchFailed, ready.installNotice)
        val offered = updateCardModel(state(UpdateStatus.Available(offer), notice = InstallNotice.NotInstalledYet))
        assertNull(offered.installNotice)
    }

    @Test fun installedOffersARecheckWithoutARestart() {
        val model = updateCardModel(state(UpdateStatus.Installed(7L), known = other))
        assertEquals(UpdatePhase.Installed, model.phase)
        assertNull(model.release)
        assertEquals(UpdateAction.Check, model.primary)
        // The card button is the check, so the compact top-bar check is not shown as a second copy.
        assertFalse(model.showTopCheck)
    }

    @Test fun installedCheckMatchesTheActionPolicy() {
        val status = UpdateStatus.Installed(7L)
        val model = updateCardModel(state(status, known = other))
        assertTrue(StableUpdateActionPolicy.canCheck(status))
        assertEquals(UpdateAction.Check, model.primary)
    }

    @Test fun checkFailureWithoutOfferAsksForRetry() {
        val model = updateCardModel(state(UpdateStatus.Failure(UpdateStep.Check, StableUpdateReason.Network, null)))
        assertEquals(UpdatePhase.Failed, model.phase)
        assertNull(model.release)
        assertEquals(UpdateAction.RetryCheck, model.primary)
        assertEquals(UpdateStep.Check, model.failureStep)
        assertEquals(StableUpdateReason.Network, model.failureReason)
        assertFalse(model.showTopCheck)
    }

    @Test fun checkFailureKeepsTheLastOfferLabelledAsStale() {
        val model = updateCardModel(
            state(UpdateStatus.Failure(UpdateStep.Check, StableUpdateReason.Timeout, null), known = offer, checkedAt = 99L, stale = true)
        )
        assertEquals(offer, model.release)
        assertTrue(model.stale)
        assertEquals(UpdateAction.RetryCheck, model.primary)
        assertEquals(99L, model.lastCheckedAt)
        assertFalse(model.showTopCheck)
    }

    @Test fun checkFailureKeepsAVerifiedApkInstallable() {
        val model = updateCardModel(
            state(
                UpdateStatus.Failure(UpdateStep.Check, StableUpdateReason.Network, null),
                ready = ReadyApk(offer, file),
                known = offer,
                stale = true,
            )
        )
        assertTrue(model.stale)
        assertEquals(UpdateAction.Install, model.primary)
        assertEquals(UpdateAction.RetryCheck, model.secondary)
    }

    @Test fun downloadFailureKeepsReleaseContextAndRetriesDownload() {
        val model = updateCardModel(state(UpdateStatus.Failure(UpdateStep.Download, StableUpdateReason.Network, offer)))
        assertEquals(UpdatePhase.Failed, model.phase)
        assertEquals(offer, model.release)
        assertFalse(model.stale)
        assertEquals(UpdateAction.RetryDownload, model.primary)
        assertTrue(model.showTopCheck)
    }

    @Test fun unclassifiedDownloadFailureStillOffersRetry() {
        val model = updateCardModel(state(UpdateStatus.Failure(UpdateStep.Download, null, offer)))
        assertNull(model.failureReason)
        assertEquals(UpdateAction.RetryDownload, model.primary)
    }

    @Test fun verifyFailureDoesNotOfferAnInstallOfTheRejectedFile() {
        val model = updateCardModel(state(UpdateStatus.Failure(UpdateStep.Verify, StableUpdateReason.ChecksumMismatch, offer)))
        assertEquals(UpdateAction.RetryDownload, model.primary)
        assertNull(model.secondary)
        assertEquals(StableUpdateReason.ChecksumMismatch, model.failureReason)
    }

    @Test fun staleFlagBelongsOnlyToAKeptOffer() {
        val model = updateCardModel(state(UpdateStatus.Available(offer), known = other, stale = true))
        assertEquals(offer, model.release)
        assertFalse(model.stale)
    }
}
