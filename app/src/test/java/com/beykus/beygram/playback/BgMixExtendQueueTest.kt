package com.beykus.beygram.playback

import com.beykus.beygram.domain.model.MediaKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BgMixExtendQueueTest {
    private val item = PlaybackItem("Audio:7", MediaKind.Audio, "/7.mp3", "Seven", 10)

    @Test
    fun extensionIsRefusedWithoutARunningSession() {
        val driver = BgMixQueueDriver(File(System.getProperty("java.io.tmpdir") ?: "."))
        assertFalse(driver.extendQueue(listOf(item)))
        assertTrue(driver.queueItems.isEmpty())
    }
}
