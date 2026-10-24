package com.beykus.beygram.data.settings

import android.content.Context
import com.beykus.beygram.domain.model.TranslationPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Target languages offered by the translation picker, in display order. */
val TRANSLATION_LANGUAGES = listOf("en", "ru", "uk", "de", "fr", "es", "it", "tr", "zh", "ar")

/** Account-scoped translation preferences. They never touch the TDLib database. */
data class TranslationSettings(
    /**
     * Saved for the automatic-translation pipeline. Manual translation is always available and is not affected by
     * this flag; this release does not start translations in the background.
     */
    val autoTranslate: Boolean = false,
    /** Empty means "derive from the app language" so a fresh install has a sensible target. */
    val targetLanguageCode: String = ""
)

/** The language actually used when the picker has never saved an explicit choice. */
fun translationTargetLanguage(storedLanguageCode: String, appLanguageTag: String): String =
    storedLanguageCode.takeIf { it in TRANSLATION_LANGUAGES } ?: when {
        appLanguageTag.startsWith("ru") -> "ru"
        appLanguageTag.startsWith("uk") -> "uk"
        else -> "en"
    }

/**
 * Pure policy for the stored automatic-translation preference. The switch is deliberately conservative: even when it is
 * on, secret chats and text-less messages are excluded. No automatic request is made in this release; manual
 * translation stays available regardless of the switch.
 */
fun automaticTranslationEnabled(settings: TranslationSettings, isSecretChat: Boolean, hasText: Boolean): Boolean =
    settings.autoTranslate && TranslationPolicy.canTranslate(isSecretChat, hasText)

/** Small preference boundary so the store can be exercised without Android's SharedPreferences in JVM tests. */
internal interface TranslationPreferenceStorage {
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getString(key: String): String?
    fun putBoolean(key: String, value: Boolean)
    fun putString(key: String, value: String)
}

private class SharedPreferencesTranslationStorage(context: Context, accountId: String) : TranslationPreferenceStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        translationPreferencesName(accountId), Context.MODE_PRIVATE
    )

    override fun getBoolean(key: String, defaultValue: Boolean) = preferences.getBoolean(key, defaultValue)
    override fun getString(key: String): String? = preferences.getString(key, null)
    override fun putBoolean(key: String, value: Boolean) { preferences.edit().putBoolean(key, value).apply() }
    override fun putString(key: String, value: String) { preferences.edit().putString(key, value).apply() }
}

/** The original account keeps the historical un-suffixed name; every secondary account is isolated. */
internal fun translationPreferencesName(accountId: String): String {
    require(accountId.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) { "Invalid account id for translation settings" }
    return if (accountId == "legacy") "translation-settings" else "translation-settings_$accountId"
}

class TranslationSettingsStore internal constructor(
    private val storage: TranslationPreferenceStorage
) {
    constructor(context: Context, accountId: String = "legacy") : this(
        SharedPreferencesTranslationStorage(context, accountId)
    )

    private val _state = MutableStateFlow(read())
    val state: StateFlow<TranslationSettings> = _state.asStateFlow()

    @Synchronized
    fun setAutoTranslate(enabled: Boolean) {
        storage.putBoolean(KEY_AUTO_TRANSLATE, enabled)
        _state.value = _state.value.copy(autoTranslate = enabled)
    }

    @Synchronized
    fun setTargetLanguage(languageCode: String) {
        require(languageCode in TRANSLATION_LANGUAGES) { "Unsupported translation language" }
        storage.putString(KEY_TARGET_LANGUAGE, languageCode)
        _state.value = _state.value.copy(targetLanguageCode = languageCode)
    }

    private fun read() = TranslationSettings(
        autoTranslate = storage.getBoolean(KEY_AUTO_TRANSLATE, false),
        targetLanguageCode = storage.getString(KEY_TARGET_LANGUAGE)
            ?.takeIf { it in TRANSLATION_LANGUAGES }
            .orEmpty()
    )

    private companion object {
        const val KEY_AUTO_TRANSLATE = "auto_translate"
        const val KEY_TARGET_LANGUAGE = "target_language"
    }
}
