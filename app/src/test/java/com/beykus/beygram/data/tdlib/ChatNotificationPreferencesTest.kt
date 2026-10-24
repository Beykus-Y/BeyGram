package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ChatNotificationPreferences
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatNotificationPreferencesTest {
    private fun scope(preview: Boolean, mentionsDisabled: Boolean) = TdApi.ScopeNotificationSettings().apply {
        showPreview = preview
        disableMentionNotifications = mentionsDisabled
    }

    @Test fun defaultSettingsFollowTheScope() {
        val settings = TdApi.ChatNotificationSettings()
        settings.useDefaultSound = true
        settings.useDefaultShowPreview = true
        settings.useDefaultDisableMentionNotifications = true

        val preferences = settings.toChatNotificationPreferences(scope(preview = true, mentionsDisabled = false))
        assertTrue(preferences.useDefaultSound)
        assertEquals(0L, preferences.soundId)
        assertTrue(preferences.showPreview)
        assertTrue(preferences.useDefaultShowPreview)
        assertFalse(preferences.disableMentionNotifications)
        assertTrue(preferences.useDefaultDisableMentionNotifications)
    }

    @Test fun inheritedPreviewAndMentionsShowTheScopeValue() {
        val settings = TdApi.ChatNotificationSettings()
        settings.useDefaultShowPreview = true
        settings.useDefaultDisableMentionNotifications = true

        val preferences = settings.toChatNotificationPreferences(scope(preview = false, mentionsDisabled = true))
        assertFalse(preferences.showPreview)
        assertTrue(preferences.disableMentionNotifications)
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

        val preferences = settings.toChatNotificationPreferences(scope(preview = true, mentionsDisabled = false))
        assertFalse(preferences.useDefaultSound)
        assertEquals(42L, preferences.soundId)
        assertFalse(preferences.showPreview)
        assertFalse(preferences.useDefaultShowPreview)
        assertTrue(preferences.disableMentionNotifications)
        assertFalse(preferences.useDefaultDisableMentionNotifications)
        // The sheet does not expose or overwrite mute.
        assertEquals(3600, settings.muteFor)
    }

    @Test fun changingOnlyTheSoundKeepsInheritedPreviewAndMentions() {
        val settings = TdApi.ChatNotificationSettings()
        settings.useDefaultSound = true
        settings.useDefaultShowPreview = true
        settings.useDefaultDisableMentionNotifications = true
        settings.useDefaultMuteFor = false
        settings.muteFor = 3600
        // The scope hides previews and silences mentions, so a saved copy of the shown values would be wrong.
        val loaded = settings.toChatNotificationPreferences(scope(preview = false, mentionsDisabled = true))

        val saved = settings.withChatNotificationPreferences(loaded.copy(useDefaultSound = false, soundId = 42L))

        assertFalse(saved.useDefaultSound)
        assertEquals(42L, saved.soundId)
        assertTrue(saved.useDefaultShowPreview)
        assertTrue(saved.useDefaultDisableMentionNotifications)
        assertFalse(saved.useDefaultMuteFor)
        assertEquals(3600, saved.muteFor)
    }

    @Test fun explicitPreviewChangeIsStoredWhileInheritedMentionsStayInherited() {
        val settings = TdApi.ChatNotificationSettings()
        settings.useDefaultShowPreview = true
        settings.useDefaultDisableMentionNotifications = true
        val loaded = settings.toChatNotificationPreferences(scope(preview = true, mentionsDisabled = false))

        val saved = settings.withChatNotificationPreferences(loaded.copy(showPreview = false, useDefaultShowPreview = false))

        assertFalse(saved.useDefaultShowPreview)
        assertFalse(saved.showPreview)
        assertTrue(saved.useDefaultDisableMentionNotifications)
    }

    @Test fun resetReturnsSoundPreviewAndMentionsToTheScope() {
        val settings = TdApi.ChatNotificationSettings()
        settings.useDefaultSound = false
        settings.soundId = 42L
        settings.useDefaultShowPreview = false
        settings.showPreview = false
        settings.useDefaultDisableMentionNotifications = false
        settings.disableMentionNotifications = true

        val saved = settings.withChatNotificationPreferences(ChatNotificationPreferences())

        assertTrue(saved.useDefaultSound)
        assertEquals(0L, saved.soundId)
        assertTrue(saved.useDefaultShowPreview)
        assertTrue(saved.useDefaultDisableMentionNotifications)
    }
}
