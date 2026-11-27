package com.beykus.beygram.playback

import android.app.Application
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PlaybackServiceRegistrationTest {
    @Test
    fun directStartRegistersOneSessionAndDestroyReleasesItBeforeRecreation() {
        val firstController = Robolectric.buildService(PlaybackService::class.java).create()
        val firstService = firstController.get()
        val firstSession = try {
            // No MediaController binding or media button intent: the normal application start path.
            val start = Intent(firstService, PlaybackService::class.java)
            firstService.onStartCommand(start, 0, 1)
            firstService.onStartCommand(start, 0, 2)
            assertEquals(1, firstService.sessions.size)
            firstService.sessions.single().also {
                assertTrue(firstService.isSessionAdded(it))
                assertSame(PlaybackEngine.player(firstService), it.player)
            }
        } finally {
            firstController.destroy()
        }
        assertTrue(firstService.sessions.isEmpty())

        val secondController = Robolectric.buildService(PlaybackService::class.java).create()
        val secondService = secondController.get()
        try {
            assertEquals(1, secondService.sessions.size)
            val secondSession = secondService.sessions.single()
            assertNotSame(firstSession, secondSession)
            assertSame(PlaybackEngine.player(secondService), secondSession.player)
        } finally {
            secondController.destroy()
        }
        assertTrue(secondService.sessions.isEmpty())
    }
}
