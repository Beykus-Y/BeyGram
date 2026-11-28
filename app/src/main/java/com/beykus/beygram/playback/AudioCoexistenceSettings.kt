package com.beykus.beygram.playback

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class CoexistenceAction(val isRecording: Boolean) {
    RecordVoice(true), RecordVideoNote(true), PlayVoice(false), PlayVideoNote(false)
}

internal enum class MusicInterference { Continue, Duck, Pause }

internal enum class ExternalAudioInterference { Continue, Duck, Pause }

internal data class CoexistencePolicy(
    val music: MusicInterference,
    val musicPercent: Int = 20,
    val external: ExternalAudioInterference = ExternalAudioInterference.Continue,
) {
    init {
        require(musicPercent in 0..100)
    }

    val musicAttenuation: Float
        get() = if (music == MusicInterference.Duck) musicPercent / 100f else 1f
}

internal data class AudioCoexistencePreferences(
    val policies: Map<CoexistenceAction, CoexistencePolicy> = emptyMap(),
    val musicPercent: Int = 100,
    val speechPercent: Int = 100,
) {
    fun policy(action: CoexistenceAction): CoexistencePolicy =
        policies[action] ?: CoexistencePolicy(
            if (action.isRecording) MusicInterference.Continue else MusicInterference.Duck
        )
}

/** Device-local audio choices. Call load and update on the main thread. */
internal object AudioCoexistenceSettings {
    private const val PREFS_NAME = "audio_coexistence"
    private val current = MutableStateFlow(AudioCoexistencePreferences())
    val state = current.asStateFlow()
    private var loaded = false

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        val prefs = preferences(context)
        val defaults = AudioCoexistencePreferences()
        current.value = AudioCoexistencePreferences(
            policies = CoexistenceAction.entries.associateWith { action ->
                val default = defaults.policy(action)
                val key = action.name
                CoexistencePolicy(
                    MusicInterference.entries.firstOrNull { it.name == prefs.getString("$key.music", null) }
                        ?: default.music,
                    prefs.getInt("$key.percent", default.musicPercent).coerceIn(0, 100),
                    ExternalAudioInterference.entries.firstOrNull { it.name == prefs.getString("$key.external", null) }
                        ?: default.external,
                )
            },
            musicPercent = prefs.getInt("music", 100).coerceIn(0, 100),
            speechPercent = prefs.getInt("speech", 100).coerceIn(0, 100),
        )
        loaded = true
    }

    fun update(context: Context, transform: (AudioCoexistencePreferences) -> AudioCoexistencePreferences) {
        load(context)
        val next = transform(current.value)
        val edit = preferences(context).edit()
        for (action in CoexistenceAction.entries) {
            val policy = next.policy(action)
            val key = action.name
            edit.putString("$key.music", policy.music.name)
                .putInt("$key.percent", policy.musicPercent)
                .putString("$key.external", policy.external.name)
        }
        edit.putInt("music", next.musicPercent).putInt("speech", next.speechPercent).apply()
        current.value = next
    }
}
