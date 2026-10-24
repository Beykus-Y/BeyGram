package com.beykus.beygram.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationSettingsTest {
    private class FakeStorage : TranslationPreferenceStorage {
        val values = mutableMapOf<String, Any?>()
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun getString(key: String): String? = values[key] as? String
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun putString(key: String, value: String) { values[key] = value }
    }

    @Test fun `automatic translation is off by default`() {
        assertFalse(TranslationSettings().autoTranslate)
        assertEquals("", TranslationSettings().targetLanguageCode)
        assertFalse(TranslationSettingsStore(FakeStorage()).state.value.autoTranslate)
    }

    @Test fun `auto translate and target language persist across store instances`() {
        val storage = FakeStorage()
        val store = TranslationSettingsStore(storage)
        store.setAutoTranslate(true)
        store.setTargetLanguage("de")
        val reopened = TranslationSettingsStore(storage)
        assertTrue(reopened.state.value.autoTranslate)
        assertEquals("de", reopened.state.value.targetLanguageCode)
    }

    @Test fun `invalid stored language falls back to the app language`() {
        val storage = FakeStorage().apply { values["target_language"] = "xx" }
        assertEquals("", TranslationSettingsStore(storage).state.value.targetLanguageCode)
        assertEquals("ru", translationTargetLanguage("", "ru-RU"))
        assertEquals("uk", translationTargetLanguage("", "uk"))
        assertEquals("en", translationTargetLanguage("", "en-US"))
        assertEquals("de", translationTargetLanguage("de", "ru"))
    }

    @Test fun `policy never auto-translates secret or textless messages`() {
        assertFalse(automaticTranslationEnabled(TranslationSettings(autoTranslate = false), isSecretChat = false, hasText = true))
        assertTrue(automaticTranslationEnabled(TranslationSettings(autoTranslate = true), isSecretChat = false, hasText = true))
        assertFalse(automaticTranslationEnabled(TranslationSettings(autoTranslate = true), isSecretChat = true, hasText = true))
        assertFalse(automaticTranslationEnabled(TranslationSettings(autoTranslate = true), isSecretChat = false, hasText = false))
    }

    @Test fun `translation preferences are isolated per account`() {
        assertEquals("translation-settings", translationPreferencesName("legacy"))
        assertNotEquals(translationPreferencesName("legacy"), translationPreferencesName("second"))
        assertNotEquals(translationPreferencesName("first"), translationPreferencesName("second"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `translation preference names reject path escapes`() {
        translationPreferencesName("../legacy")
    }
}
