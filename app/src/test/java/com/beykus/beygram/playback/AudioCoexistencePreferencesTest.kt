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
}
