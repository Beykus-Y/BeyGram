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

    @Test fun unsentSecondaryStagingIsNotChargedToLegacyAndIsRecoverableOnDelete() {
        val staging = folder.newFolder("unsent-secondary")
        markUploadDirectoryOwner(staging, "a-second")
        File(staging, "large-video.mp4").writeText("payload")
        assertTrue(uploadFolderBelongsToAccount(staging, "a-second"))
        assertFalse(uploadFolderBelongsToAccount(staging, "legacy"))
        assertFalse(uploadFolderBelongsToAccount(staging, "a-other"))
        markSentUpload(staging, 19, "a-second")
        assertTrue(uploadFolderBelongsToAccount(staging, "a-second"))
        try {
            markSentUpload(staging, 22, "a-other")
            org.junit.Assert.fail("Foreign TDLib client claimed someone else's staged media")
        } catch (_: IllegalStateException) { }
        try {
            markUploadDirectoryOwner(staging, "a-other")
            org.junit.Assert.fail("Cross-account staging ownership was overwritten")
        } catch (_: IllegalStateException) { }
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

    @Test fun videoNoteSessionLeftWithOnlyItsOwnerMarkIsRemovedAfterLastSegment() {
        val session = folder.newFolder("video-note-session")
        markUploadDirectoryOwner(session, "a-second")
        val segment = File(session, "video-note-1.mp4").apply { writeText("segment") }
        assertFalse(uploadFolderHasOnlyOwnerMarker(session))
        assertFalse(removeUploadFolderIfOnlyOwnerMarker(session))
        assertTrue(segment.exists())
        segment.delete()
        assertTrue(uploadFolderHasOnlyOwnerMarker(session))
        assertTrue(removeUploadFolderIfOnlyOwnerMarker(session))
        assertFalse(session.exists())
    }

    @Test fun emptyFolderWithoutOwnerMarkIsNotOwnerOnly() {
        val empty = folder.newFolder("empty-upload")
        assertFalse(uploadFolderHasOnlyOwnerMarker(empty))
        assertFalse(removeUploadFolderIfOnlyOwnerMarker(empty))
        assertTrue(empty.exists())
    }
}
