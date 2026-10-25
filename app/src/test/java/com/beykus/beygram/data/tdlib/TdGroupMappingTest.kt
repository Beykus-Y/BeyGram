package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.GroupAdministratorRights
import com.beykus.beygram.domain.model.GroupMemberStatus
import com.beykus.beygram.domain.model.ManagedChatKind
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TdGroupMappingTest {
    @Test fun administratorRightsRoundTripKeepsRightsTheDialogDoesNotExpose() {
        val source = GroupAdministratorRights(
            canManageChat = true,
            canChangeInfo = true,
            canPostMessages = true,
            canEditMessages = true,
            canDeleteMessages = true,
            canInviteUsers = true,
            canRestrictMembers = true,
            canPinMessages = true,
            canManageTopics = true,
            canPromoteMembers = true,
            canManageVideoChats = true,
            canPostStories = true,
            canEditStories = true,
            canDeleteStories = true,
            canManageDirectMessages = true,
            canManageTags = true,
            isAnonymous = true
        )
        assertEquals(source, source.toTdAdministratorRights().toGroupAdministratorRights())
    }

    @Test fun creatorStatusIsTheOwnerRole() {
        assertEquals(GroupMemberStatus.Owner, TdApi.ChatMemberStatusCreator(true, true).toGroupMemberStatus())
        assertEquals(
            GroupMemberStatus.Administrator,
            TdApi.ChatMemberStatusAdministrator(false, TdApi.ChatAdministratorRights()).toGroupMemberStatus()
        )
        assertEquals(GroupMemberStatus.Banned, TdApi.ChatMemberStatusBanned(0).toGroupMemberStatus())
    }

    @Test fun channelOwnerCanChangeTheUsernameButNotRestrictMembers() {
        val rights = TdApi.ChatMemberStatusCreator(false, true).toGroupAdminRights(ManagedChatKind.Channel)
        assertTrue(rights.canChangeUsername)
        assertTrue(rights.canManageInviteLinks)
        assertEquals(false, rights.canAddMembers)
        assertEquals(false, rights.canRestrictMembers)
        assertEquals(false, rights.canManageSlowMode)
    }
}
