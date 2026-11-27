package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ProfileMember
import com.beykus.beygram.domain.model.ProfileMemberRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatGroupAuthorNameTest {
    @Test fun serverTitleWinsAndOnlyKnownRolesGetDefaultLabels() {
        assertEquals(UiText.Raw("Moderator"), groupSenderTag(member(ProfileMemberRole.Admin, "Moderator")))
        assertEquals(UiText.Res(R.string.group_members_role_owner), groupSenderTag(member(ProfileMemberRole.Owner)))
        assertEquals(UiText.Res(R.string.group_members_role_admin), groupSenderTag(member(ProfileMemberRole.Admin)))
        assertNull(groupSenderTag(member(ProfileMemberRole.Member)))
        assertNull(groupSenderTag(null))
    }

    private fun member(role: ProfileMemberRole, tag: String = "") = ProfileMember(
        userId = 1, name = "Member", role = role, online = false, isBot = false, tag = tag,
        photo = null, avatarColorArgb = 0L
    )
}
