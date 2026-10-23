package com.beykus.beygram.data.tdlib

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AccountUploadOwnershipTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun legacyKeepsUnmarkedAndOldNumericMarkersInQuota() {
        val staging = folder.newFolder("staging")
        File(staging, "clip.mp4").writeText("not sent")
        assertTrue(uploadFolderBelongsToAccount(staging, "legacy"))
        assertFalse(uploadFolderBelongsToAccount(staging, "a-secondary"))
        val legacy = folder.newFolder("legacy-sent")
        markSentUpload(legacy, 101, "legacy")
        assertTrue(uploadFolderBelongsToAccount(legacy, "legacy"))
        assertFalse(uploadFolderBelongsToAccount(legacy, "a-secondary"))
    }

    @Test fun secondaryOwnerTaggedUploadsNeverCountAgainstSibling() {
        val first = folder.newFolder("secondary-a")
        markSentUpload(first, 72, "a-first")
        val sibling = folder.newFolder("secondary-b")
        markSentUpload(sibling, 72, "a-second")
        assertTrue(uploadFolderBelongsToAccount(first, "a-first"))
        assertFalse(uploadFolderBelongsToAccount(first, "a-second"))
        assertFalse(uploadFolderBelongsToAccount(first, "legacy"))
        assertTrue(uploadFolderBelongsToAccount(sibling, "a-second"))
    }
}
