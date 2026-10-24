package com.beykus.beygram.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLocaleSelectionTest {
    @Test fun respectsSystemLanguageChangedOutsideApp() {
        assertEquals("en", resolveAppLanguage("en-US", "ru"))
        assertEquals("ru", resolveAppLanguage("ru-RU", "en"))
        assertEquals("de", resolveAppLanguage("de-DE", "en"))
    }

    @Test fun germanIsSupportedOnOlderAndNewerDevices() {
        assertEquals("de", resolveAppLanguage("de", "ru"))
        assertEquals("de", resolveAppLanguage(null, "de"))
    }

    @Test fun explicitFollowSystemOverridesStalePreference() {
        assertEquals(AppLocale.SYSTEM, resolveAppLanguage("", "en"))
        assertEquals(AppLocale.SYSTEM, resolveAppLanguage("", "ru"))
    }

    @Test fun olderDevicesCanReadPersistedPreference() {
        assertEquals("ru", resolveAppLanguage(null, "ru"))
        assertEquals("en", resolveAppLanguage(null, "en"))
        assertEquals(AppLocale.SYSTEM, resolveAppLanguage(null, "invalid"))
    }

    @Test fun unsupportedExternalAppLocaleNeverFakesSupportedSelection() {
        assertEquals(AppLocale.SYSTEM, resolveAppLanguage("fr-FR", "ru"))
    }
}
