package com.beykus.beygram.data.tdlib

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilesBytesInTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun countsNestedFilesAndSkipsMissingDirectories() {
        val uploads = folder.newFolder("uploads")
        File(uploads, "attachment/original/video.mp4").apply { parentFile?.mkdirs(); writeBytes(ByteArray(10)) }
        File(uploads, "voice-note.ogg").writeBytes(ByteArray(5))
        assertEquals(15L, filesBytesIn(listOf(uploads, File(folder.root, "missing"))))
    }
}
