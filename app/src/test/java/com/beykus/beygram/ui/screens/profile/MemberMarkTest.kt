package com.beykus.beygram.ui.screens.profile

import com.beykus.beygram.domain.model.ProfileMember
import com.beykus.beygram.domain.model.ProfileMemberRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemberMarkTest {
    private fun member(role: ProfileMemberRole, tag: String = "") = ProfileMember(
        userId = 1L, name = "Анна", role = role, online = false, isBot = false, tag = tag, photo = null, avatarColorArgb = 0L
    )

    @Test fun ownerIsMarkedEvenWithoutTag() {
        assertEquals(MemberMark.Staff(isOwner = true, tag = ""), memberMark(member(ProfileMemberRole.Owner)))
    }

    @Test fun adminTagReplacesRoleLabel() {
        assertEquals(MemberMark.Staff(isOwner = false, tag = "Модератор"), memberMark(member(ProfileMemberRole.Admin, "Модератор")))
    }

    @Test fun adminWithoutTagKeepsRole() {
        assertEquals(MemberMark.Staff(isOwner = false, tag = ""), memberMark(member(ProfileMemberRole.Admin)))
    }

    @Test fun regularMemberShowsOnlyTag() {
        assertEquals(MemberMark.Tag("Друг"), memberMark(member(ProfileMemberRole.Member, "Друг")))
    }

    @Test fun regularMemberWithoutTagHasNoMark() {
        assertNull(memberMark(member(ProfileMemberRole.Member)))
    }
}
