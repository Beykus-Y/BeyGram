package com.beykus.beygram.ui.screens.playback

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackShareTest {
    private fun track() = PlaybackItem(
        key = "k", kind = MediaKind.Audio, path = "/music/a.mp3", title = "Song", durationSeconds = 200
    )

    private fun chat(id: Long, canPost: Boolean = true, secret: Boolean = false, guarded: Boolean = false) = ChatPreview(
        id = id, title = "Chat $id", initials = "C", lastMessage = UiText.Raw(""), lastMessageTime = "",
        unreadCount = 0, avatarColorArgb = 0L, order = 0L, archiveOrder = 0L,
        isSecret = secret, hasProtectedContent = guarded, canPost = canPost
    )

    @Test fun timecodeIsMinutesAndSecondsThenHours() {
        assertEquals("0:00", shareTimecode(0L))
        assertEquals("0:00", shareTimecode(-5L))
        assertEquals("1:23", shareTimecode(83_900L))
        assertEquals("1:02:05", shareTimecode(3_725_000L))
    }

    @Test fun onlyCompleteOrdinaryMusicIsShared() {
        assertTrue(canShareTrack(track(), originUnprotected = true))
        assertFalse(canShareTrack(track(), originUnprotected = false))
        assertFalse(canShareTrack(track().copy(secret = true), originUnprotected = true))
        assertFalse(canShareTrack(track().copy(isSecretChat = true), originUnprotected = true))
        assertFalse(canShareTrack(track().copy(viewOnce = true), originUnprotected = true))
        assertFalse(canShareTrack(track().copy(path = ""), originUnprotected = true))
        assertFalse(canShareTrack(track().copy(kind = MediaKind.Voice), originUnprotected = true))
    }

    @Test fun secretAndProtectedChatsAreNeverTargets() {
        val chats = listOf(
            chat(1),
            chat(2, secret = true),
            chat(3, guarded = true),
            chat(4, canPost = false)
        )
        assertEquals(listOf(1L), shareTargetChats(chats).map { it.id })
    }
}
