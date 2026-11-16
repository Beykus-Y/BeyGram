package com.beykus.beygram.data.yandex

import com.beykus.beygram.data.music.provider.MusicProviderAccount
import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.MusicProviderState
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTrack
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class YandexWaveResumeAccountTest {

    private fun signedIn(uid: String): MusicProviderState =
        MusicProviderState.SignedIn(MusicProviderAccount(uid = uid, displayName = "name", hasSubscription = true))

    private val signedOut: MusicProviderState = MusicProviderState.SignedOut

    private fun track() = ProviderTrack(
        trackId = "1", title = "t", artists = listOf("a"), albumTitle = "album", durationMs = 1_000L,
        coverUrl = null, isAvailable = true, albumId = "9",
    )

    private fun saved(uid: String) = YandexWaveResume.Saved(
        uid = uid,
        seeds = listOf(YandexWave.MY_WAVE_SEED),
        subtitle = "sub",
        track = track(),
        positionMs = 5_000L,
        given = listOf("1:9", "2:9"),
        savedAtMs = 1_000L,
    )

    @Test
    fun activeWaveEndsAtSignOutAndNothingIsSaved() {
        assertTrue(YandexWaveAccountGuard.shouldEndWave("A", signedOut))
        assertFalse(YandexWaveAccountGuard.canSave("A", signedOut))
    }

    @Test
    fun activeWaveEndsWhenAnotherAccountSignsIn() {
        assertTrue(YandexWaveAccountGuard.shouldEndWave("A", signedIn("B")))
        assertFalse(YandexWaveAccountGuard.canSave("A", signedIn("B")))
    }

    @Test
    fun waveOfTheSameAccountKeepsPlayingAndSaving() {
        assertFalse(YandexWaveAccountGuard.shouldEndWave("A", signedIn("A")))
        assertTrue(YandexWaveAccountGuard.canSave("A", signedIn("A")))
    }

    @Test
    fun noWaveNeverEndsAndNeverSaves() {
        assertFalse(YandexWaveAccountGuard.shouldEndWave(null, signedOut))
        assertFalse(YandexWaveAccountGuard.canSave(null, signedIn("A")))
        assertFalse(YandexWaveAccountGuard.canSave("A", null))
    }

    @Test
    fun savedWaveOfAnotherAccountIsNotRestored() {
        assertFalse(YandexWaveAccountGuard.canRestore("A", signedIn("B")))
        assertFalse(YandexWaveAccountGuard.canRestore("A", signedOut))
        assertTrue(YandexWaveAccountGuard.canRestore("A", signedIn("A")))
    }

    @Test
    fun oldRecordWithoutAccountIsRejected() {
        val v1 = JSONObject(YandexWaveSavedCodec.serialize(saved("A"))).put("version", 1)
        v1.remove("uid")
        try {
            YandexWaveSavedCodec.parse(v1.toString())
            fail("a version 1 record must be rejected")
        } catch (expected: Exception) {
            // Rejected: the caller drops the record.
        }
        val noUid = JSONObject(YandexWaveSavedCodec.serialize(saved("A")))
        noUid.remove("uid")
        try {
            YandexWaveSavedCodec.parse(noUid.toString())
            fail("a record without uid must be rejected")
        } catch (expected: Exception) {
            // Rejected too.
        }
    }

    @Test
    fun accountRoundTrips() {
        val original = saved("42")
        val back = YandexWaveSavedCodec.parse(YandexWaveSavedCodec.serialize(original))
        assertEquals("42", back.uid)
        assertEquals(original, back)
    }

    @Test
    fun staleWaveRequestIsRefusedForAnotherAccount() = runTest {
        var calls = 0
        val call: suspend () -> ProviderResult<String> = {
            calls++
            ProviderResult.Success("ok")
        }
        val other = YandexWaveAccountGuard.request("A", signedIn("B"), call)
        assertEquals(ProviderResult.Failure(MusicProviderFailure.NotSignedIn), other)
        val out = YandexWaveAccountGuard.request("A", signedOut, call)
        assertEquals(ProviderResult.Failure(MusicProviderFailure.NotSignedIn), out)
        assertEquals(0, calls)
        val same = YandexWaveAccountGuard.request("A", signedIn("A"), call)
        assertEquals(ProviderResult.Success("ok"), same)
        assertEquals(1, calls)
        assertNull(YandexWaveAccountGuard.uidOf(signedOut))
    }
}
