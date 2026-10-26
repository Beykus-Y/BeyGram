package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPostingRightsTest {
    private fun chat(isChannel: Boolean = false, canSend: Boolean = true, canPolls: Boolean = true) = TdApi.Chat().apply {
        type = TdApi.ChatTypeSupergroup(1L, isChannel)
        permissions = TdApi.ChatPermissions().apply {
            canSendBasicMessages = canSend
            canSendPolls = canPolls
        }
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
    fun restrictedMemberOfChannelCannotPost() {
        val allowed = TdApi.ChatPermissions().apply { canSendBasicMessages = true }
        assertFalse(chat(isChannel = true).canPostMessages(TdApi.ChatMemberStatusRestricted(true, 0, allowed)))
    }

    @Test
    fun basicGroupFollowsTheSameRulesAsSupergroup() {
        val group = TdApi.Chat().apply {
            type = TdApi.ChatTypeBasicGroup(2L)
            permissions = TdApi.ChatPermissions().apply { canSendBasicMessages = false }
        }
        assertTrue(group.canPostMessages(admin(canPost = false)))
        assertFalse(group.canPostMessages(TdApi.ChatMemberStatusMember()))
        assertFalse(group.canPostMessages(TdApi.ChatMemberStatusLeft()))
        group.permissions = TdApi.ChatPermissions().apply { canSendBasicMessages = true }
        assertTrue(group.canPostMessages(TdApi.ChatMemberStatusMember()))
    }

    @Test
    fun unknownStatusKeepsComposer() {
        assertTrue(chat(isChannel = true, canSend = false).canPostMessages(null))
    }

    @Test
    fun groupMemberNeedsTheSeparatePollPermission() {
        assertTrue(chat(canPolls = true).canSendPolls(TdApi.ChatMemberStatusMember()))
        assertFalse(chat(canPolls = false).canSendPolls(TdApi.ChatMemberStatusMember()))
        assertTrue(chat(canPolls = false).canSendPolls(admin(canPost = false)))
    }

    @Test
    fun restrictedMemberNeedsThePollPermissionToo() {
        val allowed = TdApi.ChatPermissions().apply {
            canSendBasicMessages = true
            canSendPolls = true
        }
        val denied = TdApi.ChatPermissions().apply {
            canSendBasicMessages = true
            canSendPolls = false
        }
        assertTrue(chat().canSendPolls(TdApi.ChatMemberStatusRestricted(true, 0, allowed)))
        assertFalse(chat().canSendPolls(TdApi.ChatMemberStatusRestricted(true, 0, denied)))
    }

    @Test
    fun channelPollsFollowThePostingRightInsteadOfMemberPermissions() {
        assertTrue(chat(isChannel = true, canPolls = false).canSendPolls(TdApi.ChatMemberStatusCreator()))
        assertTrue(chat(isChannel = true, canPolls = false).canSendPolls(admin(canPost = true)))
        assertFalse(chat(isChannel = true, canPolls = true).canSendPolls(admin(canPost = false)))
        assertFalse(chat(isChannel = true, canPolls = true).canSendPolls(TdApi.ChatMemberStatusMember()))
    }
}
