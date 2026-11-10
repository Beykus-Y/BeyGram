package com.beykus.beygram.data.music

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackOrigin
import com.beykus.beygram.playback.PlaybackState
import kotlinx.coroutines.CompletableDeferred
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
        val observer = launch { MusicHistoryRecorder.observe(repo, flow, ListenRule.Immediate) }
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
        val observer = launch { MusicHistoryRecorder.observe(repo, flow, ListenRule.Immediate) }
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
        val observer = launch { MusicHistoryRecorder.observe(repo, flow, ListenRule.Immediate) }
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
        val observer = launch { MusicHistoryRecorder.observe(repo, flow, ListenRule.Immediate) }
        runCurrent()
        emit(flow, playing(song).copy(positionMs = 1_000L))
        assertTrue(repo.library.value.stats.isEmpty())
        observer.cancel()
    }

    @Test
    fun aSlowWriteDoesNotMakeTheCollectorSkipStarts() = runTest {
        // Every write waits for the gate, so the later starts arrive while the first write is still on the disk.
        val gate = CompletableDeferred<Unit>()
        val inner = FakeMusicPersistence()
        val slow = object : MusicLibraryPersistence by inner {
            override suspend fun save(library: MusicLibrary) {
                gate.await()
                inner.save(library)
            }
        }
        val repo = repositoryFor(slow, FakeClock())
        repo.setActiveAccount(TEST_ACCOUNT)
        val flow = MutableStateFlow(PlaybackState())
        val observer = launch { MusicHistoryRecorder.observe(repo, flow, ListenRule.Immediate) }
        runCurrent()
        emit(flow, playing(item(1)))
        emit(flow, playing(item(2)))
        emit(flow, playing(item(3)))
        gate.complete(Unit)
        runCurrent()
        assertEquals(
            listOf(3L, 2L, 1L).map { item(it).toTrackRef()!!.trackKey },
            repo.library.value.recent.map { it.trackKey }
        )
        observer.cancel()
    }

    @Test
    fun playsAreIgnoredWhileNoAccountIsActive() = runTest {
        val repo = repositoryFor(FakeMusicPersistence(), FakeClock())
        val flow = MutableStateFlow(PlaybackState())
        val observer = launch { MusicHistoryRecorder.observe(repo, flow, ListenRule.Immediate) }
        runCurrent()
        emit(flow, playing(item(1)))
        assertTrue(repo.library.value.stats.isEmpty())
        observer.cancel()
    }

    @Test
    fun aListenCountsOnlyAfterThirtySecondsOfSound() = runTest {
        val repo = repositoryFor(FakeMusicPersistence(), FakeClock())
        repo.setActiveAccount(TEST_ACCOUNT)
        val flow = MutableStateFlow(PlaybackState())
        var now = 0L
        val observer = launch { MusicHistoryRecorder.observe(repo, flow, ListenRule.Standard) { now } }
        runCurrent()
        val song = item(1)
        emit(flow, playing(song))
        assertEquals(0, playCount(repo, song))
        // Thirty one seconds of ticks, one second apart.
        repeat(31) {
            now += 1_000L
            emit(flow, playing(song).copy(positionMs = now))
        }
        assertEquals(1, playCount(repo, song))
        observer.cancel()
    }

    @Test
    fun skippingThroughTracksCountsNothing() = runTest {
        val repo = repositoryFor(FakeMusicPersistence(), FakeClock())
        repo.setActiveAccount(TEST_ACCOUNT)
        val flow = MutableStateFlow(PlaybackState())
        var now = 0L
        val observer = launch { MusicHistoryRecorder.observe(repo, flow, ListenRule.Standard) { now } }
        runCurrent()
        for (message in 1L..10L) {
            now += 1_500L
            emit(flow, playing(item(message)))
        }
        assertEquals(0, (1L..10L).sumOf { playCount(repo, item(it)) })
        observer.cancel()
    }

    @Test
    fun thresholdIsHalfOfAShortTrack() {
        assertEquals(30_000L, listenThresholdMs(200_000L))
        assertEquals(10_000L, listenThresholdMs(20_000L))
        assertEquals(30_000L, listenThresholdMs(0L))
    }

    @Test
    fun aTrackThatBeginsAgainAfterItsEndIsANewListen() {
        assertTrue(restartedFromTheEnd(199_000L, 500L, 200_000L))
        assertTrue(!restartedFromTheEnd(60_000L, 500L, 200_000L))
        assertTrue(!restartedFromTheEnd(10_000L, 12_000L, 200_000L))
    }
}
