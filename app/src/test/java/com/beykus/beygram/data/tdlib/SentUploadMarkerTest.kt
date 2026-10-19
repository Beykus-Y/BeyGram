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
}
