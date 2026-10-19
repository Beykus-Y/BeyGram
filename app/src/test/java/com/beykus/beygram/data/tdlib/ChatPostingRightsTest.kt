package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPostingRightsTest {
    private fun chat(isChannel: Boolean = false, canSend: Boolean = true) = TdApi.Chat().apply {
        type = TdApi.ChatTypeSupergroup(1L, isChannel)
        permissions = TdApi.ChatPermissions().apply { canSendBasicMessages = canSend }
    }

    private fun admin(canPost: Boolean) = TdApi.ChatMemberStatusAdministrator().apply {
        rights = TdApi.ChatAdministratorRights().apply { canPostMessages = canPost }
    }

    @Test
    fun channelPostsOnlyForOwnerAndAdministratorsWithTheRight() {
        val channel = chat(isChannel = true, canSend = true)
        assertTrue(channel.canPostMessages(TdApi.ChatMemberStatusCreator()))
        assertTrue(channel.canPostMessages(admin(canPost = true)))
        assertFalse(channel.canPostMessages(admin(canPost = false)))
        assertFalse(channel.canPostMessages(TdApi.ChatMemberStatusMember()))
        assertFalse(channel.canPostMessages(TdApi.ChatMemberStatusLeft()))
        assertFalse(channel.canPostMessages(TdApi.ChatMemberStatusBanned()))
    }

    @Test
    fun groupMemberFollowsDefaultPermissions() {
        assertTrue(chat(canSend = true).canPostMessages(TdApi.ChatMemberStatusMember()))
        assertFalse(chat(canSend = false).canPostMessages(TdApi.ChatMemberStatusMember()))
        assertTrue(chat(canSend = false).canPostMessages(admin(canPost = false)))
    }

    @Test
    fun restrictedMemberNeedsMembershipAndPermission() {
        val allowed = TdApi.ChatPermissions().apply { canSendBasicMessages = true }
        val denied = TdApi.ChatPermissions().apply { canSendBasicMessages = false }
        assertTrue(chat().canPostMessages(TdApi.ChatMemberStatusRestricted(true, 0, allowed)))
        assertFalse(chat().canPostMessages(TdApi.ChatMemberStatusRestricted(true, 0, denied)))
        assertFalse(chat().canPostMessages(TdApi.ChatMemberStatusRestricted(false, 0, allowed)))
    }

    @Test
    fun unknownStatusKeepsComposer() {
        assertTrue(chat(isChannel = true, canSend = false).canPostMessages(null))
    }
}
