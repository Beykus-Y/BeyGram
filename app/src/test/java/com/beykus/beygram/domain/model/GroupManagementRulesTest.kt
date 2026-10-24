package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupManagementRulesTest {
    private fun member(
        status: GroupMemberStatus,
        canBeEdited: Boolean = false,
        self: Boolean = false
    ) = GroupMemberEntry(
        userId = 7L,
        name = "Member",
        status = status,
        online = false,
        isBot = false,
        tag = "",
        photo = null,
        avatarColorArgb = 0L,
        canBeEdited = canBeEdited,
        isCurrentUser = self
    )

    @Test fun ownerManagesEverythingInASupergroup() {
        val rights = groupAdminRights(ManagedChatKind.Supergroup, GroupMemberStatus.Owner)
        assertTrue(rights.canAddMembers)
        assertTrue(rights.canRemoveMembers)
        assertTrue(rights.canRestrictMembers)
        assertTrue(rights.canPromoteMembers)
        assertTrue(rights.canManageInviteLinks)
        assertTrue(rights.canChangeInfo)
        assertTrue(rights.canChangeUsername)
        assertTrue(rights.canManageSlowMode)
        assertTrue(rights.canToggleHistory)
        assertTrue(rights.canToggleJoinByRequest)
        assertTrue(rights.canManageAnything)
    }

    @Test fun ordinaryMemberGetsNoAdministrativeRights() {
        for (kind in ManagedChatKind.entries) {
            val rights = groupAdminRights(kind, GroupMemberStatus.Member)
            assertFalse("$kind must stay read-only", rights.canManageAnything)
            assertTrue(groupMemberActions(rights, member(GroupMemberStatus.Member)).isEmpty())
        }
    }

    @Test fun basicGroupAdminCannotPromoteOrRestrict() {
        val rights = groupAdminRights(ManagedChatKind.BasicGroup, GroupMemberStatus.Administrator)
        assertTrue(rights.canAddMembers)
        assertTrue(rights.canRemoveMembers)
        assertTrue(rights.canChangeInfo)
        assertFalse(rights.canPromoteMembers)
        assertFalse(rights.canRestrictMembers)
        assertFalse(rights.canChangeUsername)
        assertFalse(rights.canManageSlowMode)
        assertEquals(
            setOf(GroupMemberAction.Ban),
            groupMemberActions(rights, member(GroupMemberStatus.Member))
        )
        // Владелец обычной группы снимает администратора, но не редактирует несуществующие права.
        val owner = groupAdminRights(ManagedChatKind.BasicGroup, GroupMemberStatus.Owner)
        assertEquals(
            setOf(GroupMemberAction.Demote, GroupMemberAction.Ban),
            groupMemberActions(owner, member(GroupMemberStatus.Administrator))
        )
    }

    @Test fun channelAdminRightsComeFromTdlibAndNeverAllowRestricting() {
        val rights = groupAdminRights(
            ManagedChatKind.Channel,
            GroupMemberStatus.Administrator,
            GroupAdministratorRights(canInviteUsers = true, canRestrictMembers = true, canPromoteMembers = true)
        )
        assertFalse(rights.canAddMembers)
        assertTrue(rights.canManageInviteLinks)
        assertTrue(rights.canRemoveMembers)
        assertFalse(rights.canRestrictMembers)
        assertTrue(rights.canPromoteMembers)
        assertFalse(rights.canChangeUsername)
        assertFalse(rights.canManageSlowMode)
    }

    @Test fun onlyOwnerOrEditableAdministratorCanBeDemoted() {
        val owner = groupAdminRights(ManagedChatKind.Supergroup, GroupMemberStatus.Owner)
        assertEquals(
            setOf(GroupMemberAction.EditRights, GroupMemberAction.Demote, GroupMemberAction.Ban),
            groupMemberActions(owner, member(GroupMemberStatus.Administrator))
        )
        val delegated = groupAdminRights(
            ManagedChatKind.Supergroup,
            GroupMemberStatus.Administrator,
            GroupAdministratorRights(canPromoteMembers = true, canRestrictMembers = true)
        )
        assertEquals(emptySet<GroupMemberAction>(), groupMemberActions(delegated, member(GroupMemberStatus.Administrator)))
        assertEquals(
            setOf(GroupMemberAction.EditRights, GroupMemberAction.Demote),
            groupMemberActions(delegated, member(GroupMemberStatus.Administrator, canBeEdited = true))
        )
    }

    @Test fun ownerAndSelfAreNeverTargets() {
        val owner = groupAdminRights(ManagedChatKind.Supergroup, GroupMemberStatus.Owner)
        assertEquals(emptySet<GroupMemberAction>(), groupMemberActions(owner, member(GroupMemberStatus.Owner)))
        assertEquals(emptySet<GroupMemberAction>(), groupMemberActions(owner, member(GroupMemberStatus.Member, self = true)))
    }

    @Test fun bannedMembersCanBeUnbannedOnlyWhenTdlibListsThem() {
        val owner = groupAdminRights(ManagedChatKind.Supergroup, GroupMemberStatus.Owner)
        assertEquals(setOf(GroupMemberAction.Unban), groupMemberActions(owner, member(GroupMemberStatus.Banned)))
        val basic = groupAdminRights(ManagedChatKind.BasicGroup, GroupMemberStatus.Owner)
        assertEquals(emptySet<GroupMemberAction>(), groupMemberActions(basic, member(GroupMemberStatus.Banned)))
    }

    @Test fun restrictedMemberCanBeFreedFromRestrictions() {
        val admin = groupAdminRights(
            ManagedChatKind.Supergroup,
            GroupMemberStatus.Administrator,
            GroupAdministratorRights(canRestrictMembers = true)
        )
        assertEquals(
            setOf(GroupMemberAction.Unrestrict, GroupMemberAction.Ban),
            groupMemberActions(admin, member(GroupMemberStatus.Restricted))
        )
    }

    @Test fun inviteExpirationUsesTtlAndZeroStaysForever() {
        assertEquals(0, inviteExpirationDate(1_000, null))
        assertEquals(0, inviteExpirationDate(1_000, 0))
        assertEquals(1_000 + 86_400, inviteExpirationDate(1_000, 86_400))
        assertEquals(Int.MAX_VALUE, inviteExpirationDate(Int.MAX_VALUE.toLong() - 1, 86_400))
        assertEquals(86_400, inviteRemainingSeconds(1_000 + 86_400, 1_000))
        assertEquals(0, inviteRemainingSeconds(1_000, 2_000))
        assertEquals(0, inviteRemainingSeconds(0, 2_000))
    }

    @Test fun inviteDraftRejectsTooLongNamesAndLimits() {
        assertNull(groupInviteLinkProblem(GroupInviteLinkDraft()))
        assertEquals(
            GroupInviteLinkProblem.NameTooLong,
            groupInviteLinkProblem(GroupInviteLinkDraft(name = "x".repeat(GROUP_INVITE_NAME_MAX_LENGTH + 1)))
        )
        assertEquals(
            GroupInviteLinkProblem.MemberLimitOutOfRange,
            groupInviteLinkProblem(GroupInviteLinkDraft(memberLimit = -1))
        )
        assertEquals(
            GroupInviteLinkProblem.LimitWithApproval,
            groupInviteLinkProblem(GroupInviteLinkDraft(memberLimit = 10, requiresApproval = true))
        )
        assertEquals(0, GroupInviteLinkDraft(name = " name ", memberLimit = 10, requiresApproval = true).normalized().memberLimit)
        assertEquals("name", GroupInviteLinkDraft(name = " name ").normalized().name)
    }

    @Test fun inviteDraftKeepsAbsoluteExpirationWhenNormalized() {
        val draft = GroupInviteLinkDraft(name = " x ", expiresAtSeconds = 123_456, memberLimit = 10)
        assertEquals(123_456, draft.normalized().expiresAtSeconds)
        assertEquals("x", draft.normalized().name)
    }

    @Test fun settingsValidationCoversTitleDescriptionAndUsername() {
        assertNull(groupSettingsProblem(GroupSettingsEdit(title = "Group", description = "About", username = "my_group")))
        assertEquals(
            GroupSettingsProblem.EmptyTitle,
            groupSettingsProblem(GroupSettingsEdit(title = "   "))
        )
        assertEquals(
            GroupSettingsProblem.TitleTooLong,
            groupSettingsProblem(GroupSettingsEdit(title = "x".repeat(GROUP_TITLE_MAX_LENGTH + 1)))
        )
        assertEquals(
            GroupSettingsProblem.DescriptionTooLong,
            groupSettingsProblem(GroupSettingsEdit(description = "x".repeat(GROUP_DESCRIPTION_MAX_LENGTH + 1)))
        )
        assertEquals(
            GroupSettingsProblem.UsernameInvalid,
            groupSettingsProblem(GroupSettingsEdit(username = "ab"))
        )
        assertEquals(
            GroupSettingsProblem.UsernameInvalid,
            groupSettingsProblem(GroupSettingsEdit(username = "1group"))
        )
        assertNull(groupSettingsProblem(GroupSettingsEdit(username = "")))
        assertNull(groupSettingsProblem(GroupSettingsEdit(username = "@my_group")))
    }

    @Test fun defaultAdminRightsMatchTheChatKind() {
        val group = defaultAdministratorRights(ManagedChatKind.Supergroup)
        assertTrue(group.canDeleteMessages)
        assertTrue(group.canRestrictMembers)
        assertFalse(group.canPostMessages)

        val channel = defaultAdministratorRights(ManagedChatKind.Channel)
        assertTrue(channel.canPostMessages)
        assertTrue(channel.canEditMessages)
        assertFalse(channel.canRestrictMembers)
    }

    @Test fun inviteLinkEditingFollowsTheCreatorAndThePrimaryRule() {
        val admin = groupAdminRights(
            ManagedChatKind.Supergroup,
            GroupMemberStatus.Administrator,
            GroupAdministratorRights(canInviteUsers = true)
        )
        val own = GroupInviteLink(link = "https://t.me/+own", creatorUserId = 1L, creatorIsCurrentUser = true)
        val foreign = GroupInviteLink(link = "https://t.me/+foreign", creatorUserId = 2L)
        val primary = GroupInviteLink(link = "https://t.me/+primary", isPrimary = true, creatorUserId = 2L)

        assertEquals(GroupInviteLinkPermissions(canEdit = true, canRevoke = true), groupInviteLinkPermissions(admin, own))
        assertEquals(GroupInviteLinkPermissions(), groupInviteLinkPermissions(admin, foreign))
        assertEquals(GroupInviteLinkPermissions(canEdit = false, canRevoke = true), groupInviteLinkPermissions(admin, primary))

        val owner = groupAdminRights(ManagedChatKind.Supergroup, GroupMemberStatus.Owner)
        assertEquals(GroupInviteLinkPermissions(canEdit = true, canRevoke = true), groupInviteLinkPermissions(owner, foreign))

        val member = groupAdminRights(ManagedChatKind.Supergroup, GroupMemberStatus.Member)
        assertEquals(GroupInviteLinkPermissions(), groupInviteLinkPermissions(member, own))
    }
}
