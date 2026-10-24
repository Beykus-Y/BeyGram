package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.BlockedSenderKind
import com.beykus.beygram.domain.model.PrivacyRuleChoice
import com.beykus.beygram.domain.model.PrivacyRules
import com.beykus.beygram.domain.model.PrivacySetting
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyRulesTest {
    @Test
    fun `every editable setting maps to its verified TDLib privacy type`() {
        assertTrue(PrivacySetting.PhoneNumber.toTdSetting() is TdApi.UserPrivacySettingShowPhoneNumber)
        assertTrue(PrivacySetting.ProfilePhoto.toTdSetting() is TdApi.UserPrivacySettingShowProfilePhoto)
        assertTrue(PrivacySetting.LastSeen.toTdSetting() is TdApi.UserPrivacySettingShowStatus)
        assertTrue(PrivacySetting.Bio.toTdSetting() is TdApi.UserPrivacySettingShowBio)
        assertTrue(PrivacySetting.ForwardedMessages.toTdSetting() is TdApi.UserPrivacySettingShowLinkInForwardedMessages)
        assertTrue(PrivacySetting.Calls.toTdSetting() is TdApi.UserPrivacySettingAllowCalls)
        assertTrue(PrivacySetting.Messages.toTdSetting() is TdApi.UserPrivacySettingAllowUnpaidMessages)
        assertTrue(PrivacySetting.GroupInvites.toTdSetting() is TdApi.UserPrivacySettingAllowChatInvites)
        assertTrue(PrivacySetting.FindByPhoneNumber.toTdSetting() is TdApi.UserPrivacySettingAllowFindingByPhoneNumber)
    }

    @Test
    fun `get request uses the mapped setting`() {
        val request = privacyRulesRequest(PrivacySetting.Bio)

        assertTrue(request.setting is TdApi.UserPrivacySettingShowBio)
    }

    @Test
    fun `everyone without exceptions is one allow all rule`() {
        val request = privacyRulesRequest(PrivacySetting.PhoneNumber, PrivacyRules(PrivacyRuleChoice.Everyone))

        assertTrue(request.setting is TdApi.UserPrivacySettingShowPhoneNumber)
        assertEquals(1, request.rules.rules.size)
        assertTrue(request.rules.rules.single() is TdApi.UserPrivacySettingRuleAllowAll)
    }

    @Test
    fun `contacts puts always and never exceptions before the base rule`() {
        val rules = PrivacyRules(
            choice = PrivacyRuleChoice.Contacts,
            alwaysAllowUserIds = listOf(2L, 1L),
            neverAllowUserIds = listOf(3L)
        ).toTdRules().rules

        assertEquals(3, rules.size)
        assertTrue(rules[0] is TdApi.UserPrivacySettingRuleAllowUsers)
        assertEquals(listOf(1L, 2L), (rules[0] as TdApi.UserPrivacySettingRuleAllowUsers).userIds.toList())
        assertTrue(rules[1] is TdApi.UserPrivacySettingRuleRestrictUsers)
        assertEquals(listOf(3L), (rules[1] as TdApi.UserPrivacySettingRuleRestrictUsers).userIds.toList())
        assertTrue(rules[2] is TdApi.UserPrivacySettingRuleAllowContacts)
    }

    @Test
    fun `nobody puts always allowed users before restrict all`() {
        val rules = PrivacyRules(
            choice = PrivacyRuleChoice.Nobody,
            alwaysAllowUserIds = listOf(8L)
        ).toTdRules().rules

        assertEquals(2, rules.size)
        assertTrue(rules[0] is TdApi.UserPrivacySettingRuleAllowUsers)
        assertTrue(rules[1] is TdApi.UserPrivacySettingRuleRestrictAll)
    }

    @Test
    fun `everyone puts never allowed users before allow all`() {
        val rules = PrivacyRules(
            choice = PrivacyRuleChoice.Everyone,
            neverAllowUserIds = listOf(8L)
        ).toTdRules().rules

        assertEquals(2, rules.size)
        assertTrue(rules[0] is TdApi.UserPrivacySettingRuleRestrictUsers)
        assertTrue(rules[1] is TdApi.UserPrivacySettingRuleAllowAll)
    }

    @Test
    fun `redundant exceptions are not sent to TDLib`() {
        val everyone = PrivacyRules(
            choice = PrivacyRuleChoice.Everyone,
            alwaysAllowUserIds = listOf(5L),
            neverAllowUserIds = listOf(6L)
        ).toTdRules().rules
        assertEquals(2, everyone.size)
        assertTrue(everyone[0] is TdApi.UserPrivacySettingRuleRestrictUsers)
        assertTrue(everyone[1] is TdApi.UserPrivacySettingRuleAllowAll)

        val nobody = PrivacyRules(
            choice = PrivacyRuleChoice.Nobody,
            alwaysAllowUserIds = listOf(5L),
            neverAllowUserIds = listOf(6L)
        ).toTdRules().rules
        assertEquals(2, nobody.size)
        assertTrue(nobody[0] is TdApi.UserPrivacySettingRuleAllowUsers)
        assertTrue(nobody[1] is TdApi.UserPrivacySettingRuleRestrictAll)
    }

    @Test
    fun `parsing TDLib rules recovers the base choice and both exception lists`() {
        val parsed = TdApi.UserPrivacySettingRules(arrayOf<TdApi.UserPrivacySettingRule>(
            TdApi.UserPrivacySettingRuleAllowUsers(longArrayOf(9L)),
            TdApi.UserPrivacySettingRuleRestrictUsers(longArrayOf(7L)),
            TdApi.UserPrivacySettingRuleAllowContacts()
        )).toPrivacyRules()

        assertEquals(PrivacyRuleChoice.Contacts, parsed.choice)
        assertEquals(listOf(9L), parsed.alwaysAllowUserIds)
        assertEquals(listOf(7L), parsed.neverAllowUserIds)
    }

    @Test
    fun `empty TDLib rules mean nobody because no rule matches`() {
        assertEquals(PrivacyRuleChoice.Nobody, TdApi.UserPrivacySettingRules().toPrivacyRules().choice)
    }

    @Test
    fun `unsupported restricted-contacts rules are read-only and never rewritten as everyone`() {
        val parsed = TdApi.UserPrivacySettingRules(
            arrayOf<TdApi.UserPrivacySettingRule>(
                TdApi.UserPrivacySettingRuleRestrictContacts(),
                TdApi.UserPrivacySettingRuleAllowAll()
            )
        ).toPrivacyRules()

        assertFalse(parsed.isEditable)
        assertEquals(parsed, parsed.withChoice(PrivacyRuleChoice.Nobody))
    }

    @Test
    fun `premium and member rules also keep the setting read-only`() {
        val premium = TdApi.UserPrivacySettingRules(
            arrayOf<TdApi.UserPrivacySettingRule>(
                TdApi.UserPrivacySettingRuleAllowPremiumUsers(),
                TdApi.UserPrivacySettingRuleAllowAll()
            )
        ).toPrivacyRules()
        assertFalse(premium.isEditable)

        val members = TdApi.UserPrivacySettingRules(
            arrayOf<TdApi.UserPrivacySettingRule>(
                TdApi.UserPrivacySettingRuleAllowChatMembers(),
                TdApi.UserPrivacySettingRuleRestrictAll()
            )
        ).toPrivacyRules()
        assertFalse(members.isEditable)
    }

    @Test
    fun `overlapping exceptions are denied and invalid ids are ignored`() {
        val normalized = PrivacyRules(
            choice = PrivacyRuleChoice.Contacts,
            alwaysAllowUserIds = listOf(2L, 2L, 0L),
            neverAllowUserIds = listOf(2L, 3L)
        ).normalized()

        assertEquals(emptyList<Long>(), normalized.alwaysAllowUserIds)
        assertEquals(listOf(2L, 3L), normalized.neverAllowUserIds)
    }

    @Test
    fun `changing audience drops exceptions the new choice already implies`() {
        val contacts = PrivacyRules(
            choice = PrivacyRuleChoice.Contacts,
            alwaysAllowUserIds = listOf(1L),
            neverAllowUserIds = listOf(2L)
        )

        assertEquals(emptyList<Long>(), contacts.withChoice(PrivacyRuleChoice.Everyone).alwaysAllowUserIds)
        assertEquals(listOf(2L), contacts.withChoice(PrivacyRuleChoice.Everyone).neverAllowUserIds)
        assertEquals(listOf(1L), contacts.withChoice(PrivacyRuleChoice.Nobody).alwaysAllowUserIds)
        assertEquals(emptyList<Long>(), contacts.withChoice(PrivacyRuleChoice.Nobody).neverAllowUserIds)
    }

    @Test
    fun `block list request targets the main list and moves the server offset`() {
        val request = blockedSendersRequest(offset = 50, limit = 25)

        assertTrue(request.blockList is TdApi.BlockListMain)
        assertEquals(50, request.offset)
        assertEquals(25, request.limit)
    }

    @Test
    fun `block list request rejects an invalid server cursor`() {
        assertThrows(IllegalArgumentException::class.java) { blockedSendersRequest(offset = -1, limit = 50) }
        assertThrows(IllegalArgumentException::class.java) { blockedSendersRequest(offset = 0, limit = 0) }
        assertThrows(IllegalArgumentException::class.java) {
            setMessageSenderBlockListRequest(BlockedSenderKind.User, 0L, blocked = true)
        }
    }

    @Test
    fun `block and unblock keep user and chat senders distinct`() {
        val blockUser = setMessageSenderBlockListRequest(BlockedSenderKind.User, 42L, blocked = true)
        assertTrue(blockUser.senderId is TdApi.MessageSenderUser)
        assertEquals(42L, (blockUser.senderId as TdApi.MessageSenderUser).userId)
        assertTrue(blockUser.blockList is TdApi.BlockListMain)

        val unblockUser = setMessageSenderBlockListRequest(BlockedSenderKind.User, 42L, blocked = false)
        assertNull(unblockUser.blockList)

        val blockChat = setMessageSenderBlockListRequest(BlockedSenderKind.Chat, -100777L, blocked = true)
        assertTrue(blockChat.senderId is TdApi.MessageSenderChat)
        assertEquals(-100777L, (blockChat.senderId as TdApi.MessageSenderChat).chatId)
    }

    @Test
    fun `spam report starts empty and selects only the spam option`() {
        val initial = reportChatRequest(chatId = 42L)
        assertEquals(42L, initial.chatId)
        assertEquals(0, initial.optionId.size)
        assertEquals(0, initial.messageIds.size)
        assertEquals("", initial.text)

        val options = arrayOf(
            TdApi.ReportOption("violence".toByteArray(), "Violence"),
            TdApi.ReportOption("spam".toByteArray(), "Spam")
        )
        assertEquals("spam", chooseSpamReportOption(options)?.id?.let { String(it, Charsets.UTF_8) })
        assertNull(chooseSpamReportOption(arrayOf(TdApi.ReportOption("violence".toByteArray(), "Violence"))))
    }
}
