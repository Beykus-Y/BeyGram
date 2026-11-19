package com.beykus.beygram.ui.screens.settings

import com.beykus.beygram.data.update.DownloadProgress
import com.beykus.beygram.data.update.StableRelease
import com.beykus.beygram.data.update.StableUpdateActionPolicy
import com.beykus.beygram.data.update.StableUpdateReason
import com.beykus.beygram.data.update.UpdateStatus
import com.beykus.beygram.data.update.UpdateStep
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StableUpdateActionPolicyTest {
    private val release = StableRelease(
        tag = "v0.0.0.4",
        versionName = "0.0.0.4",
        versionCode = 4L,
        apkName = "BeyGram-0.0.0.4-prod-release.apk",
        size = 1000L,
        sha256 = "a".repeat(64),
        notes = "",
        publishedAt = ""
    )
    private val other = release.copy(tag = "v0.0.0.5", versionName = "0.0.0.5", versionCode = 5L)
    private val file = File("immutable.apk")
    private fun progress(bytes: Long) = DownloadProgress(bytes, 1000L, null, null)

    @Test fun cannotCheckOrDownloadWhilePreparingInstall() {
        val installing = UpdateStatus.PreparingInstall(release)
        assertFalse(StableUpdateActionPolicy.canCheck(installing))
        assertFalse(StableUpdateActionPolicy.canDownload(installing, release))
        assertFalse(StableUpdateActionPolicy.canInstall(installing, release))
    }

    @Test fun repeatedChecksAreBlockedWhileNetworkOrFileOperationsAreRunning() {
        assertFalse(StableUpdateActionPolicy.canCheck(UpdateStatus.Checking))
        assertFalse(StableUpdateActionPolicy.canCheck(UpdateStatus.Downloading(release, progress(320))))
        assertFalse(StableUpdateActionPolicy.canCheck(UpdateStatus.Verifying(release)))
        assertFalse(StableUpdateActionPolicy.canCheck(UpdateStatus.PermissionRequired(release, file)))
        assertFalse(StableUpdateActionPolicy.canCheck(UpdateStatus.AwaitingInstaller(release, file)))
        assertTrue(StableUpdateActionPolicy.canCheck(UpdateStatus.Ready(release, file)))
    }

    @Test fun staleDownloadActionsAndParallelDownloadsAreRejected() {
        assertTrue(StableUpdateActionPolicy.canDownload(UpdateStatus.Available(release), release))
        assertFalse(StableUpdateActionPolicy.canDownload(UpdateStatus.Available(other), release))
        assertFalse(StableUpdateActionPolicy.canDownload(UpdateStatus.Downloading(release, progress(600)), release))
        assertFalse(StableUpdateActionPolicy.canDownload(UpdateStatus.Ready(release, file), release))
        assertFalse(StableUpdateActionPolicy.canDownload(UpdateStatus.Failure(UpdateStep.Download, StableUpdateReason.Network, null), release))
        assertTrue(StableUpdateActionPolicy.canDownload(UpdateStatus.Failure(UpdateStep.Download, StableUpdateReason.Network, release), release))
    }

    @Test fun installNeedsAVerifiedFileOfTheSameRelease() {
        assertTrue(StableUpdateActionPolicy.canInstall(UpdateStatus.Ready(release, file), release))
        assertTrue(StableUpdateActionPolicy.canInstall(UpdateStatus.PermissionRequired(release, file), release))
        assertFalse(StableUpdateActionPolicy.canInstall(UpdateStatus.Ready(other, file), release))
        assertFalse(StableUpdateActionPolicy.canInstall(UpdateStatus.Verifying(release), release))
        assertFalse(StableUpdateActionPolicy.canInstall(UpdateStatus.Downloading(release, progress(1000)), release))
    }
}
