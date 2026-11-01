package com.beykus.beygram.data.music

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackOrigin
import com.beykus.beygram.playback.PlaybackState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicHistoryRecorderTest {
    private fun item(
        message: Long,
        account: String = TEST_ACCOUNT,
        secret: Boolean = false,
        kind: MediaKind = MediaKind.Audio
    ): PlaybackItem = PlaybackItem(
        key = "tg|$account|$message",
        kind = kind,
        path = "",
        title = "Artist — Track $message",
        durationSeconds = 200,
        origin = PlaybackOrigin(chatId = 1L, messageId = message, accountId = account),
        secret = secret,
        accountId = account,
        fileId = message.toInt(),
        performer = "Artist",
        trackTitle = "Track $message"
    )

    private fun playing(item: PlaybackItem?, started: Boolean = true): PlaybackState = PlaybackState(
        item = item,
        playing = started,
        intentPlaying = started,
        started = started
    )

    private fun TestScope.emit(flow: MutableStateFlow<PlaybackState>, state: PlaybackState) {
        flow.value = state
        runCurrent()
    }

    private fun playCount(repo: MusicLibraryRepository, song: PlaybackItem): Int =
        repo.library.value.stats[song.toTrackRef()!!.trackKey]?.playCount ?: 0

    @Test
    fun aStartIsCountedOnceNotOnEveryUpdate() = runTest {
        val repo = repositoryFor(FakeMusicPersistence(), FakeClock())
        repo.setActiveAccount(TEST_ACCOUNT)
        val flow = MutableStateFlow(PlaybackState())
        val observer = launch { MusicHistoryRecorder.observe(repo, flow) }
        runCurrent()
        val song = item(1)
        emit(flow, playing(song, started = false))
        emit(flow, playing(song))
        emit(flow, playing(song).copy(positionMs = 5_000L))
        emit(flow, playing(song).copy(playing = false))
        emit(flow, playing(song))
        assertEquals(1, playCount(repo, song))
        assertEquals("Track 1", repo.library.value.stats.getValue(song.toTrackRef()!!.trackKey).track.title)
        observer.cancel()
    }

    @Test
    fun aNewItemOrARestartAfterAStopCountsAgain() = runTest {
        val repo = repositoryFor(FakeMusicPersistence(), FakeClock())
        repo.setActiveAccount(TEST_ACCOUNT)
        val flow = MutableStateFlow(PlaybackState())
        val observer = launch { MusicHistoryRecorder.observe(repo, flow) }
        runCurrent()
        val a = item(1)
        val b = item(2)
        emit(flow, playing(a))
        emit(flow, playing(b))
        emit(flow, playing(a))
        emit(flow, PlaybackState())
        emit(flow, playing(a))
        assertEquals(3, playCount(repo, a))
        assertEquals(1, playCount(repo, b))
        observer.cancel()
    }

    @Test
    fun nonMusicSecretAndForeignAccountItemsAreIgnored() = runTest {
        val repo = repositoryFor(FakeMusicPersistence(), FakeClock())
        repo.setActiveAccount(TEST_ACCOUNT)
        val flow = MutableStateFlow(PlaybackState())
        val observer = launch { MusicHistoryRecorder.observe(repo, flow) }
        runCurrent()
        emit(flow, playing(item(1, kind = MediaKind.Video)))
        emit(flow, PlaybackState())
        emit(flow, playing(item(2, secret = true)))
        emit(flow, PlaybackState())
        emit(flow, playing(item(3, account = "other")))
        assertTrue(repo.library.value.stats.isEmpty())
        assertTrue(repo.library.value.recent.isEmpty())
        observer.cancel()
    }

    @Test
    fun aStartThatWasAlreadyAudibleWhenObservationBeganIsNotCounted() = runTest {
        val repo = repositoryFor(FakeMusicPersistence(), FakeClock())
        repo.setActiveAccount(TEST_ACCOUNT)
        val song = item(1)
        val flow = MutableStateFlow(playing(song))
        val observer = launch { MusicHistoryRecorder.observe(repo, flow) }
        runCurrent()
        emit(flow, playing(song).copy(positionMs = 1_000L))
        assertTrue(repo.library.value.stats.isEmpty())
        observer.cancel()
    }

    @Test
    fun playsAreIgnoredWhileNoAccountIsActive() = runTest {
        val repo = repositoryFor(FakeMusicPersistence(), FakeClock())
        val flow = MutableStateFlow(PlaybackState())
        val observer = launch { MusicHistoryRecorder.observe(repo, flow) }
        runCurrent()
        emit(flow, playing(item(1)))
        assertTrue(repo.library.value.stats.isEmpty())
        observer.cancel()
    }
}
