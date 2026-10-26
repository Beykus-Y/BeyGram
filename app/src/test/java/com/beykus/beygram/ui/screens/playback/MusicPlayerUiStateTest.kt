package com.beykus.beygram.ui.screens.playback

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackRepeatMode
import com.beykus.beygram.playback.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicPlayerUiStateTest {
    private fun track(
        key: String,
        title: String = "Song $key",
        performer: String = "Artist $key",
        path: String = "/music/$key.mp3",
        fileId: Int? = null
    ) = PlaybackItem(
        key = key,
        kind = MediaKind.Audio,
        path = path,
        title = "$performer — $title",
        durationSeconds = 180,
        performer = performer,
        trackTitle = title,
        album = "Album $key",
        fileId = fileId
    )

    private fun playback(
        item: PlaybackItem?,
        queue: List<PlaybackItem> = emptyList(),
        index: Int = -1,
        playing: Boolean = false,
        intent: Boolean = false,
        buffering: Boolean = false,
        failed: Boolean = false,
        ended: Boolean = false,
        shuffle: Boolean = false,
        repeat: PlaybackRepeatMode = PlaybackRepeatMode.Off
    ) = PlaybackState(
        item = item,
        playing = playing,
        intentPlaying = intent,
        failed = failed,
        ended = ended,
        queue = queue,
        queueIndex = index,
        shuffleEnabled = shuffle,
        repeatMode = repeat,
        isBuffering = buffering,
        durationMs = 180_000L,
        positionMs = 0L
    )

    @Test
    fun miniPlayerExistsOnlyWhileAnItemIsLoaded() {
        assertFalse(playback(null).miniPlayerVisible())
        assertTrue(playback(track("a")).miniPlayerVisible())
        assertNull(playback(null).miniPlayerUi())
    }

    @Test
    fun miniPlayerKeepsTitleAndArtistSeparate() {
        val ui = playback(track("a", title = "Nightfall", performer = "Aurora")).miniPlayerUi()!!
        assertEquals("Nightfall", ui.title)
        assertEquals("Aurora", ui.artist)
        assertEquals("Album a", ui.album)
        assertTrue(ui.isMusic)
    }

    @Test
    fun miniPlayerNextIsVisibleOnlyWhenThePlayerCanAdvance() {
        val first = track("a", path = "/a.mp3")
        val second = track("b", path = "/b.mp3")
        assertTrue(playback(first, listOf(first, second), 0).miniPlayerUi()!!.canNext)
        assertFalse(playback(second, listOf(first, second), 1).miniPlayerUi()!!.canNext)
        assertTrue(
            playback(second, listOf(first, second), 1, repeat = PlaybackRepeatMode.All)
                .miniPlayerUi()!!.canNext
        )
        assertTrue(playback(second, listOf(first, second), 1, shuffle = true).miniPlayerUi()!!.canNext)
        assertFalse(playback(first).miniPlayerUi()!!.canNext)
    }

    @Test
    fun fullPlayerIsOnlyForMusic() {
        assertNull(playback(track("a").copy(kind = MediaKind.Voice)).musicPlayerUi())
        assertEquals("Nightfall", playback(track("a", title = "Nightfall")).musicPlayerUi()!!.item.musicTitle())
    }

    @Test
    fun currentCardStatusFollowsTransportBeforeDownloadFlags() {
        val current = MusicCardPlayback(isCurrent = true, playing = false, intentPlaying = true, buffering = true)
        assertEquals(
            MusicPlaybackStatus.Buffering,
            musicCardStatus(current, isDownloading = true, waitingForDownload = false, hasPlayableSource = true, downloadFailed = false)
        )
        assertEquals(
            MusicPlaybackStatus.Playing,
            musicCardStatus(current.copy(playing = true), true, false, true, false)
        )
        assertEquals(
            MusicPlaybackStatus.Failed,
            musicCardStatus(current.copy(failed = true), true, false, true, false)
        )
        assertEquals(
            MusicPlaybackStatus.Paused,
            musicCardStatus(current.copy(intentPlaying = false, buffering = false), true, false, true, false)
        )
    }

    @Test
    fun otherCardsDistinguishDownloadLoadingAndUnavailable() {
        val idle = MusicCardPlayback()
        assertEquals(
            MusicPlaybackStatus.Downloading,
            musicCardStatus(idle, isDownloading = true, waitingForDownload = false, hasPlayableSource = false, downloadFailed = false)
        )
        assertEquals(
            MusicPlaybackStatus.Buffering,
            musicCardStatus(idle, isDownloading = false, waitingForDownload = true, hasPlayableSource = false, downloadFailed = false)
        )
        assertEquals(
            MusicPlaybackStatus.NotDownloaded,
            musicCardStatus(idle, isDownloading = false, waitingForDownload = false, hasPlayableSource = false, downloadFailed = false)
        )
        assertEquals(
            MusicPlaybackStatus.Unavailable,
            musicCardStatus(idle, isDownloading = false, waitingForDownload = false, hasPlayableSource = false, downloadFailed = true)
        )
        assertEquals(
            MusicPlaybackStatus.Ready,
            musicCardStatus(idle, isDownloading = false, waitingForDownload = false, hasPlayableSource = true, downloadFailed = false)
        )
    }

    @Test
    fun queueShowsOriginalOrderWithCurrentAndNextSections() {
        val first = track("a", path = "/a.mp3")
        val second = track("b", path = "/b.mp3")
        val third = track("c", path = "", fileId = 3)
        val ui = playback(second, listOf(first, second, third), 1, playing = true).musicQueueUi()!!
        assertEquals(1, ui.currentIndex)
        assertEquals(second, ui.current!!.item)
        assertEquals(QueueItemStatus.CurrentPlaying, ui.current!!.status)
        assertEquals(listOf(third), ui.upcoming.map { it.item })
        assertEquals(QueueItemStatus.Loading, ui.upcoming.single().status)
        assertEquals(1, ui.loadingCount)
        assertEquals(2, ui.upcoming.single().index)
    }

    @Test
    fun shuffleKeepsTheVisibleSourceOrder() {
        val first = track("a", path = "/a.mp3")
        val second = track("b", path = "/b.mp3")
        val third = track("c", path = "/c.mp3")
        val ui = playback(second, listOf(first, second, third), 1, playing = true, shuffle = true).musicQueueUi()!!
        assertEquals(listOf(first, second, third), ui.rows.map { it.item })
        assertEquals(second, ui.current!!.item)
    }

    @Test
    fun shuffledNextFollowsTheActualMedia3Timeline() {
        val first = track("a", path = "/a.mp3")
        val second = track("b", path = "/b.mp3")
        val third = track("c", path = "/c.mp3")
        val fourth = track("d", path = "/d.mp3")
        // Media3 plays A, D, B, C while the source list stays A, B, C, D. The owner-reported case: current D, next B.
        val ui = playback(fourth, listOf(first, second, third, fourth), 3, playing = true, shuffle = true)
            .musicQueueUi(playbackOrder = listOf(0, 3, 1, 2))!!

        assertTrue(ui.playbackOrderApplied)
        assertEquals(listOf(first, second, third, fourth), ui.rows.map { it.item })
        assertEquals(fourth, ui.current!!.item)
        assertEquals(listOf(second, third), ui.upcoming.map { it.item })
        // The source positions remain available for the "No. N" marker even though the list is in playback order.
        assertEquals(listOf(2, 3), ui.upcoming.map { it.index + 1 })
    }

    @Test
    fun shuffledRepeatAllWrapsThePlaybackOrder() {
        val first = track("a", path = "/a.mp3")
        val second = track("b", path = "/b.mp3")
        val third = track("c", path = "/c.mp3")
        val fourth = track("d", path = "/d.mp3")
        val ui = playback(
            fourth,
            listOf(first, second, third, fourth),
            3,
            shuffle = true,
            repeat = PlaybackRepeatMode.All
        ).musicQueueUi(playbackOrder = listOf(0, 3, 1, 2))!!

        assertEquals(listOf(second, third, first), ui.upcoming.map { it.item })
    }

    @Test
    fun invalidPlaybackOrderFallsBackToTheSourceTail() {
        val first = track("a", path = "/a.mp3")
        val second = track("b", path = "/b.mp3")
        val third = track("c", path = "/c.mp3")
        val fourth = track("d", path = "/d.mp3")
        val ui = playback(fourth, listOf(first, second, third, fourth), 3, shuffle = true)
            .musicQueueUi(playbackOrder = listOf(0, 0, 1, 2))!!

        assertFalse(ui.playbackOrderApplied)
        assertTrue(ui.upcoming.isEmpty())
    }

    @Test
    fun singleTrackHasAQueuePanelWithoutUpcomingItems() {
        val only = track("a")
        val ui = playback(only).musicQueueUi()!!
        assertEquals(1, ui.rows.size)
        assertEquals(-1, ui.current!!.index)
        assertTrue(ui.upcoming.isEmpty())
    }

    @Test
    fun dragCannotMoveTheCurrentTrackOrLeaveTheQueue() {
        assertEquals(-1, queueDropTarget(from = 1, to = 0, currentIndex = 1, size = 4))
        assertEquals(1, queueDropTarget(from = 2, to = 0, currentIndex = 0, size = 4))
        assertEquals(3, queueDropTarget(from = 1, to = 9, currentIndex = 0, size = 4))
        assertEquals(-1, queueDropTarget(from = 3, to = 3, currentIndex = 2, size = 3))
        assertEquals(-1, queueDropTarget(from = 0, to = 1, currentIndex = -1, size = 0))
    }

    @Test
    fun dragDistanceMapsToQueueRows() {
        assertEquals(4, queueDragTarget(from = 2, dragPx = 140f, rowHeightPx = 68f, currentIndex = 1, size = 5))
        assertEquals(1, queueDragTarget(from = 2, dragPx = -140f, rowHeightPx = 68f, currentIndex = 0, size = 5))
        assertEquals(2, queueDragTarget(from = 2, dragPx = 10f, rowHeightPx = 0f, currentIndex = 0, size = 5))
    }

    @Test
    fun sourceKindUsesSavedChannelGroupAndChatInThatOrder() {
        assertEquals(PlaybackSourceKind.Local, playbackSourceKind(false, false, false, false))
        assertEquals(PlaybackSourceKind.SavedMessages, playbackSourceKind(true, true, true, true))
        assertEquals(PlaybackSourceKind.Channel, playbackSourceKind(true, false, true, false))
        assertEquals(PlaybackSourceKind.Group, playbackSourceKind(true, false, false, true))
        assertEquals(PlaybackSourceKind.Chat, playbackSourceKind(true, false, false, false))
    }

    @Test
    fun playerClockIsSharedByPlayerAndQueue() {
        assertEquals("0:00", playbackClock(0L))
        assertEquals("1:05", playbackClock(65_000L))
        assertEquals("-2:00", playbackRemaining(65_000L, 185_000L))
        assertEquals("0:00", playbackRemaining(0L, 0L))
    }
}
