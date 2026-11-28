package com.beykus.beygram.data.yandex

import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTrack
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexWaveRouterTest {
    private fun track(id: String) = ProviderTrack(
        trackId = id, title = "t$id", artists = emptyList(), albumTitle = null, durationMs = 1_000L,
        coverUrl = null, isAvailable = true, albumId = "a$id",
    )

    private fun ok(vararg ids: String, session: String = "s1", batch: String = "b") = ProviderResult.Success(
        YandexWaveBatch(session, batch, ids.map { track(it) }, unknownSession = false, terminated = false)
    )

    private fun session(result: ProviderResult<YandexWaveBatch>, given: Collection<String> = emptyList()) = YandexWaveSession(
        seeds = listOf("user:onyourwave"),
        start = { _, _ -> result },
        next = { _, _ -> result },
        initialGiven = given,
    )

    @Test
    fun swapCarriesOverTheGivenTracks() = runTest {
        val initial = session(ok("1"))
        val router = YandexWaveRouter(initial) { _, given, _ ->
            assertTrue(given.contains("1:a1"))
            session(ok("1", "2"), given)
        }
        initial.first()
        val result = router.swap(listOf("user:onyourwave", "settingMoodEnergy:calm")) as ProviderResult.Success
        assertEquals(listOf("2"), result.value.map { it.trackId })
        assertTrue(router.current !== initial)
    }

    @Test
    fun failedSwapKeepsTheCurrentSession() = runTest {
        val initial = session(ok("1"))
        val router = YandexWaveRouter(initial) { _, _, _ ->
            session(ProviderResult.Failure(MusicProviderFailure.Network))
        }
        initial.first()
        assertTrue(router.swap(listOf("x")) is ProviderResult.Failure)
        assertSame(initial, router.current)
    }

    @Test
    fun swapCarriesTheSessionThatGaveEachTrack() = runTest {
        val initial = session(ok("1", session = "s1", batch = "b1"))
        val router = YandexWaveRouter(initial) { seeds, given, origins ->
            // The new session of the same radio inherits what the old one gave, with the old session and batch.
            assertEquals("1:a1" to "b1", origins["ym:1"]?.let { it.queueId to it.batchId })
            YandexWaveSession(
                seeds = seeds,
                start = { _, _ -> ok("2", session = "s2", batch = "b2") },
                next = { _, _ -> ok("2", session = "s2", batch = "b2") },
                initialGiven = given,
                initialOrigins = origins,
            )
        }
        initial.first()
        router.swap(listOf("x"))
        assertEquals(WaveTrackOrigin("1:a1", "s1", "b1"), router.current.originOf("ym:1"))
        assertEquals("2:a2" to "b2", router.current.trackOf("ym:2"))
    }
}