package com.beykus.beygram.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioCoexistencePreferencesTest {
    @Test
    fun defaultsMatchEachAction() {
        val preferences = AudioCoexistencePreferences()
        for (action in CoexistenceAction.entries) {
            val policy = preferences.policy(action)
            assertEquals(
                if (action.isRecording) MusicInterference.Continue else MusicInterference.Duck,
                policy.music,
            )
            assertEquals(ExternalAudioInterference.Continue, policy.external)
            assertEquals(if (action.isRecording) 1f else 0.2f, policy.musicAttenuation, 0f)
        }
        assertEquals(100, preferences.musicPercent)
        assertEquals(100, preferences.speechPercent)
    }

    @Test
    fun policiesAndLinearDuckLevelsAreIndependent() {
        for (action in CoexistenceAction.entries) {
            for (music in MusicInterference.entries) {
                for (external in ExternalAudioInterference.entries) {
                    for (percent in listOf(0, 20, 100)) {
                        val policy = CoexistencePolicy(music, percent, external)
                        val preferences = AudioCoexistencePreferences(mapOf(action to policy))
                        assertEquals(policy, preferences.policy(action))
                        assertEquals(
                            if (music == MusicInterference.Duck) percent / 100f else 1f,
                            policy.musicAttenuation,
                            0f,
                        )
                        for (other in CoexistenceAction.entries.filter { it != action }) {
                            assertEquals(AudioCoexistencePreferences().policy(other), preferences.policy(other))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun transitionRenderersKeepTheirRatioAcrossIndependentGainChanges() {
        val voice = CoexistencePolicy(MusicInterference.Duck, 20)
        val recording = CoexistencePolicy(MusicInterference.Duck, 65)
        val envelopes = listOf(0.8f, 0.6f)
        fun render(base: Float, policies: List<CoexistencePolicy>, focus: Float, sleep: Float) =
            envelopes.map { musicOutputGain(base, coexistenceAttenuation(policies), focus, sleep, it) }

        val both = render(0.5f, listOf(voice, recording), 0.25f, 0.5f)
        assertEquals(0.01f, both[0], 0.00001f)
        assertEquals(0.0075f, both[1], 0.00001f)
        val one = render(0.4f, listOf(recording), 1f, 1f)
        assertEquals(0.208f, one[0], 0.00001f)
        assertEquals(0.156f, one[1], 0.00001f)
        val restored = render(0.4f, emptyList(), 1f, 1f)
        assertEquals(0.32f, restored[0], 0.00001f)
        assertEquals(0.24f, restored[1], 0.00001f)
    }
}
