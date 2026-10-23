package com.beykus.beygram.data.tdlib

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SentUploadMarkerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun markKeepsEachFileIdOnce() {
        val folder = temporary.newFolder()
        markSentUpload(folder, 7)
        markSentUpload(folder, 9)
        markSentUpload(folder, 7)
        assertEquals(setOf(7, 9), sentUploadFileIds(folder))
    }

    @Test fun folderWithoutMarkerHasNoIds() {
        assertTrue(sentUploadFileIds(temporary.newFolder()).isEmpty())
    }

    @Test fun emptyMarkerFromOlderBuildHasNoIds() {
        val folder = temporary.newFolder()
        File(folder, SENT_UPLOAD_MARKER).createNewFile()
        assertTrue(sentUploadFileIds(folder).isEmpty())
    }
    @Test fun secondaryMarkerIsInvisibleToLegacyAndOtherAccounts() {
        val folder = temporary.newFolder()
        markSentUpload(folder, 42, "account-B")
        assertEquals(setOf(42), sentUploadFileIds(folder, "account-B"))
        assertTrue(sentUploadFileIds(folder).isEmpty())
        assertTrue(sentUploadFileIds(folder, "account-C").isEmpty())
    }

    @Test fun legacyNumericMarkersRemainOwnedByLegacy() {
        val folder = temporary.newFolder()
        File(folder, SENT_UPLOAD_MARKER).writeText("18\n20")
        assertEquals(setOf(18, 20), sentUploadFileIds(folder))
        assertTrue(sentUploadFileIds(folder, "account-B").isEmpty())
    }

    @Test fun foreignMarkerCannotBeOverwritten() {
        val folder = temporary.newFolder()
        markSentUpload(folder, 7, "account-B")
        try {
            markSentUpload(folder, 8, "account-C")
            org.junit.Assert.fail("Foreign account must not overwrite the marker")
        } catch (_: IllegalArgumentException) {
            assertEquals(setOf(7), sentUploadFileIds(folder, "account-B"))
        }
    }
}
