package com.beykus.beygram.data.yandex

import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTrack
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexWaveRouterTest {
    private fun track(id: String) = ProviderTrack(
        trackId = id, title = "t$id", artists = emptyList(), albumTitle = null, durationMs = 1_000L,
        coverUrl = null, isAvailable = true, albumId = "a$id",
    )

    private fun ok(vararg ids: String) = ProviderResult.Success(
        YandexWaveBatch("s1", "b", ids.map { track(it) }, unknownSession = false, terminated = false)
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
        val router = YandexWaveRouter(initial) { _, given ->
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
        val router = YandexWaveRouter(initial) { _, _ -> session(ProviderResult.Failure(MusicProviderFailure.Network)) }
        initial.first()
        assertTrue(router.swap(listOf("x")) is ProviderResult.Failure)
        assertSame(initial, router.current)
    }
}
