package com.beykus.beygram.data.yandex

import com.beykus.beygram.data.music.provider.MusicProviderAccount
import com.beykus.beygram.data.music.provider.MusicProviderState
import com.beykus.beygram.data.music.provider.ProviderTrack
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
        assertTrue(YandexWaveAccountGuard.shouldEndWave(1L, null))
        assertFalse(YandexWaveAccountGuard.canSave(1L, null))
    }

    @Test
    fun activeWaveEndsWhenTheSignInChangesEvenForTheSameUid() {
        // Sign-out and a quick sign-in as the same account may skip SignedOut, but the generation still differs.
        assertTrue(YandexWaveAccountGuard.shouldEndWave(1L, 2L))
        assertFalse(YandexWaveAccountGuard.canSave(1L, 2L))
    }

    @Test
    fun waveOfTheSameSignInKeepsPlayingAndSaving() {
        assertFalse(YandexWaveAccountGuard.shouldEndWave(1L, 1L))
        assertTrue(YandexWaveAccountGuard.canSave(1L, 1L))
    }

    @Test
    fun noWaveNeverEndsAndNeverSaves() {
        assertFalse(YandexWaveAccountGuard.shouldEndWave(null, null))
        assertFalse(YandexWaveAccountGuard.canSave(null, 1L))
        assertFalse(YandexWaveAccountGuard.canSave(1L, null))
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
    fun uidOfReadsOnlyASignedInState() {
        assertNull(YandexWaveAccountGuard.uidOf(signedOut))
        assertEquals("A", YandexWaveAccountGuard.uidOf(signedIn("A")))
    }
}
