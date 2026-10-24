package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatNotificationPreferencesTest {
    @Test fun defaultSettingsMeanGlobalSoundAndPreview() {
        val settings = TdApi.ChatNotificationSettings()
        settings.useDefaultSound = true
        settings.useDefaultShowPreview = true
        settings.useDefaultDisableMentionNotifications = true

        val preferences = settings.toChatNotificationPreferences()
        assertTrue(preferences.useDefaultSound)
        assertEquals(0L, preferences.soundId)
        assertTrue(preferences.showPreview)
        assertFalse(preferences.disableMentionNotifications)
    }

    @Test fun explicitOverridesAreMappedWithoutTouchingMute() {
        val settings = TdApi.ChatNotificationSettings()
        settings.useDefaultSound = false
        settings.soundId = 42L
        settings.useDefaultShowPreview = false
        settings.showPreview = false
        settings.useDefaultDisableMentionNotifications = false
        settings.disableMentionNotifications = true
        settings.useDefaultMuteFor = false
        settings.muteFor = 3600

        val preferences = settings.toChatNotificationPreferences()
        assertFalse(preferences.useDefaultSound)
        assertEquals(42L, preferences.soundId)
        assertFalse(preferences.showPreview)
        assertTrue(preferences.disableMentionNotifications)
        // The sheet does not expose or overwrite mute.
        assertEquals(3600, settings.muteFor)
    }
}
