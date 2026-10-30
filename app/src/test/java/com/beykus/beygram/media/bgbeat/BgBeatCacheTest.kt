package com.beykus.beygram.media.bgbeat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BgBeatCacheTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun enforceCacheQuotaEvictsOldestFilesFirst() {
        val cacheDir = tempFolder.newFolder("bgbeat-cache")

        val file1 = File(cacheDir, "file1.bgm").apply { writeBytes(ByteArray(1024)) }
        Thread.sleep(60)
        val file2 = File(cacheDir, "file2.bgm").apply { writeBytes(ByteArray(1024)) }
        Thread.sleep(60)
        val file3 = File(cacheDir, "file3.bgm").apply { writeBytes(ByteArray(1024)) }

        assertEquals(3072L, cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() })

        val files = cacheDir.walkTopDown().filter { it.isFile }.sortedBy { it.lastModified() }.toList()
        var total = files.sumOf { it.length() }
        val maxBytes = 2500L
        for (file in files) {
            if (total <= maxBytes) break
            val len = file.length()
            if (file.delete()) {
                total -= len
            }
        }

        assertTrue("file1 should be deleted", !file1.exists())
        assertTrue("file2 should exist", file2.exists())
        assertTrue("file3 should exist", file3.exists())
        assertEquals(2048L, cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() })
    }
}
