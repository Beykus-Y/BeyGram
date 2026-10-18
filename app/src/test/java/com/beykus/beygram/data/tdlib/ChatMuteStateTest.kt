package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMuteStateTest {
    private val state = ChatMuteState()

    private fun chatSettings(useDefault: Boolean, muteFor: Int) =
        TdApi.ChatNotificationSettings().apply { useDefaultMuteFor = useDefault; this.muteFor = muteFor }

    private fun scopeSettings(muteFor: Int) = TdApi.ScopeNotificationSettings().apply { this.muteFor = muteFor }

    @Test fun ownSettingDecidesWhenNotUsingDefault() {
        state.putChat(1L, NotificationScope.Private, chatSettings(useDefault = false, muteFor = 3600))
        state.putChat(2L, NotificationScope.Private, chatSettings(useDefault = false, muteFor = 0))
        assertTrue(state.isMuted(1L))
        assertFalse(state.isMuted(2L))
    }

    @Test fun defaultScopeMuteAppliesWhenChatUsesDefault() {
        state.putChat(1L, NotificationScope.Group, chatSettings(useDefault = true, muteFor = 0))
        assertFalse(state.isMuted(1L))
        val affected = state.updateScope(NotificationScope.Group, scopeSettings(muteFor = 3600))
        assertEquals(listOf(1L), affected)
        assertTrue(state.isMuted(1L))
    }

    @Test fun scopeChangeDoesNotTouchOtherScopesOrOwnSettings() {
        state.putChat(1L, NotificationScope.Private, chatSettings(useDefault = true, muteFor = 0))
        state.putChat(2L, NotificationScope.Group, chatSettings(useDefault = true, muteFor = 0))
        state.putChat(3L, NotificationScope.Group, chatSettings(useDefault = false, muteFor = 0))
        assertEquals(listOf(2L), state.updateScope(NotificationScope.Group, scopeSettings(muteFor = 3600)))
        assertFalse(state.isMuted(1L))
        assertFalse(state.isMuted(3L))
    }

    @Test fun chatUpdateReplacesDefaultWithOwnSetting() {
        state.putChat(1L, NotificationScope.Channel, chatSettings(useDefault = true, muteFor = 0))
        state.updateScope(NotificationScope.Channel, scopeSettings(muteFor = 3600))
        assertTrue(state.isMuted(1L))
        state.updateChat(1L, chatSettings(useDefault = false, muteFor = 0))
        assertFalse(state.isMuted(1L))
    }

    @Test fun unknownChatIsNotMutedAndUpdateIsIgnored() {
        state.updateChat(9L, chatSettings(useDefault = false, muteFor = 3600))
        assertFalse(state.isMuted(9L))
    }

    @Test fun defaultChatIsKnownOnlyAfterItsScopeIsRead() {
        state.putChat(1L, NotificationScope.Group, chatSettings(useDefault = true, muteFor = 0))
        state.putChat(2L, NotificationScope.Group, chatSettings(useDefault = false, muteFor = 0))
        assertFalse(state.isKnown(1L))
        assertTrue(state.isKnown(2L))
        state.updateScope(NotificationScope.Private, scopeSettings(muteFor = 0))
        assertFalse(state.isKnown(1L))
        state.updateScope(NotificationScope.Group, scopeSettings(muteFor = 0))
        assertTrue(state.isKnown(1L))
    }

    @Test fun chatLoadedAfterItsScopeTakesTheScopeSetting() {
        state.updateScope(NotificationScope.Private, scopeSettings(muteFor = 3600))
        state.putChat(1L, NotificationScope.Private, chatSettings(useDefault = true, muteFor = 0))
        assertTrue(state.isKnown(1L))
        assertTrue(state.isMuted(1L))
    }

    @Test fun unknownChatIsNotKnown() {
        assertFalse(state.isKnown(9L))
    }

    @Test fun chatTypesMapToScopes() {
        assertEquals(NotificationScope.Group, NotificationScope.ofChat(TdApi.ChatTypeBasicGroup(1L)))
        assertEquals(NotificationScope.Private, NotificationScope.ofChat(TdApi.ChatTypePrivate(1L)))
    }
}
