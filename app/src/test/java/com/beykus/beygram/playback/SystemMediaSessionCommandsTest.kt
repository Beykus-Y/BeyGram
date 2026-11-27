package com.beykus.beygram.playback

import androidx.media3.common.Player
import com.beykus.beygram.domain.model.MediaKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Commands are built from a Media3 [Player.Commands] value, which needs the Android framework. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemMediaSessionCommandsTest {
    private val item = PlaybackItem("Audio:8", MediaKind.Audio, "/8.mp3", "Eight", 10)
    private val native = PlaybackState(item = item, playing = true, intentPlaying = true, bgMixActive = true)

    @Test
    fun nativeCommandsExposeNextOnlyWhenTheQueueHasOne() {
        val base = Player.Commands.Builder().build()
        val withNext = nativeSessionCommands(base, native.copy(timelineHasNext = true))
        val atEnd = nativeSessionCommands(base, native.copy(timelineHasNext = false))
        assertTrue(withNext.contains(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(withNext.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertFalse(atEnd.contains(Player.COMMAND_SEEK_TO_NEXT))
        assertFalse(atEnd.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(atEnd.contains(Player.COMMAND_PLAY_PAUSE))
        assertTrue(atEnd.contains(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertTrue(atEnd.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
    }
}