package com.beykus.beygram.ui.screens.settings

import com.beykus.beygram.data.update.StableRelease
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

    @Test fun cannotCheckOrDownloadWhilePreparingInstall() {
        val installing = UpdateStatus.PreparingInstall(release)
        assertFalse(StableUpdateActionPolicy.canCheck(installing))
        assertFalse(StableUpdateActionPolicy.canDownload(installing, release))
    }

    @Test fun repeatedChecksAreBlockedWhileNetworkOrFileOperationsAreRunning() {
        assertFalse(StableUpdateActionPolicy.canCheck(UpdateStatus.Checking))
        assertFalse(StableUpdateActionPolicy.canCheck(UpdateStatus.Downloading(release, 32)))
        assertTrue(StableUpdateActionPolicy.canCheck(UpdateStatus.Ready(release, File("immutable.apk"))))
    }

    @Test fun staleDownloadActionsAndParallelDownloadsAreRejected() {
        assertTrue(StableUpdateActionPolicy.canDownload(UpdateStatus.Available(release), release))
        assertFalse(StableUpdateActionPolicy.canDownload(UpdateStatus.Available(other), release))
        assertFalse(StableUpdateActionPolicy.canDownload(UpdateStatus.Downloading(release, 60), release))
        assertFalse(StableUpdateActionPolicy.canDownload(UpdateStatus.Ready(release, File("immutable.apk")), release))
        assertFalse(StableUpdateActionPolicy.canDownload(UpdateStatus.Failure("network"), release))
        assertTrue(StableUpdateActionPolicy.canDownload(UpdateStatus.Failure("network", release), release))
    }
}
