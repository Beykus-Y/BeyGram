package com.beykus.beygram.data.yandex

import android.content.Context

/**
 * The four groups of «Моя волна» settings: activity, mood, character and language. Each non-default value adds one
 * extra seed to the radio; a group left at its default adds nothing. The seed values of the first three groups are
 * not yet checked on the live API. A wrong one is only a seed the service does not know, so it never breaks the wave.
 */
internal data class YandexWaveSettings(
    val activity: Activity = Activity.NONE,
    val mood: Mood = Mood.DEFAULT,
    val character: YandexWave.Diversity = YandexWave.Diversity.DEFAULT,
    val language: Language = Language.DEFAULT,
) {
    enum class Activity(val seed: String?) {
        NONE(null),
        WORK("activity:work"),
        FOCUS("activity:focus"),
        WORKOUT("activity:workout"),
        RELAX("activity:relax"),
    }

    enum class Mood(val seed: String?) {
        DEFAULT(null),
        CALM("settingMoodEnergy:calm"),
        ENERGETIC("settingMoodEnergy:energetic"),
    }

    enum class Language(val seed: String?) {
        DEFAULT(null),
        RUSSIAN("settingLanguage:russian"),
        FOREIGN("settingLanguage:not-russian"),
    }

    /** The seeds of a radio that starts from [base] with these settings, in a fixed order. */
    fun seeds(base: String): List<String> =
        listOfNotNull(base, activity.seed, mood.seed, character.seed, language.seed)

    /** The text kept per account: `group=value` pairs joined by `;`. */
    fun encode(): String = listOf(
        "activity=${activity.name}",
        "mood=${mood.name}",
        "character=${character.name}",
        "language=${language.name}",
    ).joinToString(";")

    companion object {
        val DEFAULTS = YandexWaveSettings()

        /** Reads [encode]'s text. An unknown name keeps the default of its group, so a broken value never fails. */
        fun decode(text: String?): YandexWaveSettings {
            if (text.isNullOrBlank()) return DEFAULTS
            val values = text.split(';').mapNotNull { pair ->
                pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
            }.toMap()
            return YandexWaveSettings(
                activity = enumOr(values["activity"], Activity.NONE),
                mood = enumOr(values["mood"], Mood.DEFAULT),
                character = enumOr(values["character"], YandexWave.Diversity.DEFAULT),
                language = enumOr(values["language"], Language.DEFAULT),
            )
        }

        private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: default
    }
}

/** Keeps each account's settings under its own key. Any failure reads as the defaults and never reaches playback. */
internal object YandexWaveSettingsStore {
    private const val PREFS = "yandex_wave_settings_v1"

    fun load(context: Context, uid: String): YandexWaveSettings = try {
        YandexWaveSettings.decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(uid), null))
    } catch (e: RuntimeException) {
        YandexWaveSettings.DEFAULTS
    }

    fun save(context: Context, uid: String, settings: YandexWaveSettings) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key(uid), settings.encode()).apply()
        } catch (e: RuntimeException) {
            // Settings are only a preference: a failed write keeps the previous value.
        }
    }

    private fun key(uid: String) = "settings_$uid"
}
