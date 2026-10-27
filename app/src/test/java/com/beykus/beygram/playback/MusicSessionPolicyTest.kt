package com.beykus.beygram.playback

import com.beykus.beygram.domain.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSessionPolicyTest {
    private fun song(id: Long, account: String = "a", secret: Boolean = false) = PlaybackItem(
        key = "song/$account/$id",
        kind = MediaKind.Audio,
        path = "/tmp/song$id.mp3",
        title = "Song $id",
        durationSeconds = 100,
        origin = PlaybackOrigin(3L, id, account),
        accountId = account,
        fileId = id.toInt(),
        secret = secret
    )

    @Test fun sameAccountPagesAppendOnlyNewPublicMusic() {
        val current = song(100L)
        val next = song(99L)
        val otherAccount = song(97L, "b")
        val appended = appendMusicPage(
            listOf(current),
            listOf(current, next, next, song(98L, secret = true), otherAccount),
            "a"
        )
        assertEquals(listOf(next.key), appended.map { it.key })
    }

    @Test fun queuedPositionAndModesSurviveAsMessageIdentities() {
        val queue = listOf(song(100L), song(99L), song(98L))
        val saved = saveableMusicSession(
            PlaybackState(
                item = queue[1],
                queue = queue,
                queueIndex = 1,
                positionMs = 12_350L,
                shuffleEnabled = true,
                repeatMode = PlaybackRepeatMode.All
            ), "a"
        )!!
        assertEquals(queue.map { it.origin }, saved.origins)
        assertEquals(1, saved.currentIndex)
        assertEquals(12_350L, saved.positionMs)
        assertTrue(saved.shuffle)
        assertEquals(PlaybackRepeatMode.All, saved.repeat)
    }

    @Test fun secretsAndForeignAccountsAreNeverSaved() {
        assertNull(saveableMusicSession(PlaybackState(item = song(1, secret = true)), "a"))
        assertNull(saveableMusicSession(PlaybackState(item = song(2, "b")), "a"))
        assertNull(saveableMusicSession(PlaybackState(item = song(3).copy(origin = null)), "a"))
        assertNull(saveableMusicSession(
            PlaybackState(item = song(1), queue = listOf(song(1), song(2, secret = true))),
            "a"
        ))
    }

    @Test fun playlistPersistenceIsBoundedAroundPlayingItem() {
        val queue = (1L..120L).map(::song)
        val chosen = queue[100]
        val snapshot = saveableMusicSession(
            PlaybackState(item = chosen, queue = queue, queueIndex = 100), "a"
        )!!
        assertEquals(80, snapshot.origins.size)
        assertEquals(chosen.origin, snapshot.origins[snapshot.currentIndex])
    }
}
