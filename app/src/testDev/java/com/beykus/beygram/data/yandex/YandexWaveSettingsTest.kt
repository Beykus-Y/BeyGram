package com.beykus.beygram.data.yandex

import org.junit.Assert.assertEquals
import org.junit.Test

class YandexWaveSettingsTest {
    @Test
    fun defaultsAddNoSeeds() {
        assertEquals(listOf("user:onyourwave"), YandexWaveSettings().seeds("user:onyourwave"))
    }

    @Test
    fun everyGroupAddsItsSeedInFixedOrder() {
        val settings = YandexWaveSettings(
            activity = YandexWaveSettings.Activity.WORK,
            mood = YandexWaveSettings.Mood.CALM,
            character = YandexWave.Diversity.DISCOVER,
            language = YandexWaveSettings.Language.RUSSIAN,
        )
        assertEquals(
            listOf("user:onyourwave", "activity:work", "settingMoodEnergy:calm", "settingDiversity:discover", "settingLanguage:russian"),
            settings.seeds("user:onyourwave"),
        )
    }

    @Test
    fun encodeAndDecodeRoundTrip() {
        val settings = YandexWaveSettings(mood = YandexWaveSettings.Mood.ENERGETIC, language = YandexWaveSettings.Language.FOREIGN)
        assertEquals(settings, YandexWaveSettings.decode(settings.encode()))
    }

    @Test
    fun unknownOrBrokenValuesFallBackToDefaults() {
        assertEquals(YandexWaveSettings(), YandexWaveSettings.decode(null))
        assertEquals(YandexWaveSettings(), YandexWaveSettings.decode(""))
        assertEquals(YandexWaveSettings(), YandexWaveSettings.decode("garbage"))
        val partial = YandexWaveSettings.decode("mood=CALM;language=ZZZ")
        assertEquals(YandexWaveSettings.Mood.CALM, partial.mood)
        assertEquals(YandexWaveSettings.Language.DEFAULT, partial.language)
    }
}
