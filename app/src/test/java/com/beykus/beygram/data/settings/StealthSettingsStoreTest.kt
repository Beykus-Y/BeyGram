package com.beykus.beygram.data.settings

import com.beykus.beygram.domain.model.InteractionEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StealthSettingsStoreTest {
    /** Keeps values in their SharedPreferences types; a failed commit leaves what was stored before. */
    private class FakeStealthStorage : StealthStorage {
        val stored = mutableMapOf<String, Any>()
        var commitsSucceed = true

        override fun entries(): Map<String, *> = stored.toMap()

        override fun replace(values: Map<String, Any>): Boolean {
            if (!commitsSucceed) return false
            stored.clear()
            stored.putAll(values)
            return true
        }
    }

    private val allEffects = InteractionEffect.entries

    private fun StealthSettings.switchAllows(effect: InteractionEffect): Boolean = when (effect) {
        InteractionEffect.Typing -> !hideTyping
        InteractionEffect.ReadHistory -> !noReadReceipts
        InteractionEffect.OpenContent -> !noContentOpen
        InteractionEffect.StoryView -> !noStoryViews
        InteractionEffect.OnlinePresence -> !hideOnline
    }

    @Test fun firstRunUsesNormalBehaviour() {
        val store = StealthSettingsStore(FakeStealthStorage())
        assertEquals(StealthSettings(), store.current)
        assertFalse(store.current.protectionActive)
        assertTrue(allEffects.all { store.current.allows(it, 7L) })
    }

    @Test fun writtenSettingsSurviveANewStoreOverTheSameStorage() {
        val storage = FakeStealthStorage()
        val store = StealthSettingsStore(storage)
        val chats = mapOf(7L to ChatStealthMode.Allow, -100123L to ChatStealthMode.Block)
        assertTrue(store.update { it.copy(hideTyping = true, noStoryViews = true, warnRiskyActions = true, chats = chats) })

        val reopened = StealthSettingsStore(storage).current
        assertEquals(store.current, reopened)
        assertTrue(reopened.hideTyping)
        assertFalse(reopened.noReadReceipts)
        assertEquals(ChatStealthMode.Block, reopened.chats[-100123L])
    }

    @Test fun inheritIsNeverWrittenAsAnException() {
        val storage = FakeStealthStorage()
        StealthSettingsStore(storage).update { it.copy(hideTyping = true, chats = mapOf(5L to ChatStealthMode.Inherit)) }
        assertFalse(storage.stored.keys.any { it.startsWith("chat:") })
    }

    @Test fun chatExceptionsOverrideOnlyTheirOwnChat() {
        val store = StealthSettingsStore(FakeStealthStorage())
        store.update {
            it.copy(noReadReceipts = true, noContentOpen = true,
                chats = mapOf(1L to ChatStealthMode.Allow, 2L to ChatStealthMode.Block))
        }
        assertTrue(store.current.allows(InteractionEffect.ReadHistory, 1L))
        assertTrue(store.current.allows(InteractionEffect.OpenContent, 1L))
        assertFalse(store.current.allows(InteractionEffect.ReadHistory, 2L))
        assertFalse(store.current.allows(InteractionEffect.ReadHistory, 3L))
        assertFalse(store.current.allows(InteractionEffect.ReadHistory, null))
    }

    @Test fun allowsMatrixCombinesAccountSwitchesWithChatExceptions() {
        val chatId = 42L
        for (mask in 0 until 32) {
            val global = StealthSettings(
                hideTyping = (mask and 1) != 0,
                noReadReceipts = (mask and 2) != 0,
                noContentOpen = (mask and 4) != 0,
                hideOnline = (mask and 8) != 0,
                noStoryViews = (mask and 16) != 0
            )
            for (exception in listOf<ChatStealthMode?>(null, ChatStealthMode.Inherit, ChatStealthMode.Allow, ChatStealthMode.Block)) {
                val settings = global.copy(chats = if (exception == null) emptyMap() else mapOf(chatId to exception))
                for (effect in allEffects) {
                    val expectedInChat = when {
                        effect == InteractionEffect.OnlinePresence -> !global.hideOnline
                        exception == ChatStealthMode.Allow -> true
                        exception == ChatStealthMode.Block -> false
                        else -> global.switchAllows(effect)
                    }
                    val label = "$effect, chat $exception, switches $mask"
                    assertEquals(label, expectedInChat, settings.allows(effect, chatId))
                    assertEquals("$effect without a chat, switches $mask", global.switchAllows(effect), settings.allows(effect, null))
                }
            }
        }
    }

    @Test fun failClosedRefusesEverythingIncludingChatAllowances() {
        val settings = StealthSettings.FailClosed.copy(chats = mapOf(1L to ChatStealthMode.Allow))
        assertTrue(allEffects.none { settings.allows(it, 1L) || settings.allows(it, null) })
    }

    @Test fun protectionCountsBlocksAndSwitchesButNotAllowancesOrWarnings() {
        assertFalse(StealthSettings(warnRiskyActions = true).protectionActive)
        assertFalse(StealthSettings(chats = mapOf(1L to ChatStealthMode.Allow)).protectionActive)
        assertTrue(StealthSettings(chats = mapOf(1L to ChatStealthMode.Block)).protectionActive)
        assertTrue(StealthSettings(hideOnline = true).protectionActive)
    }

    @Test fun unreadableValueWhileProtectedFailsClosed() {
        val storage = FakeStealthStorage()
        StealthSettingsStore(storage).update { it.copy(hideTyping = true) }
        storage.stored["no_read_receipts"] = "maybe"
        assertEquals(StealthSettings.FailClosed, StealthSettingsStore(storage).current)
    }

    @Test fun unreadableValueWithoutProtectionFallsBackToDefaults() {
        val storage = FakeStealthStorage()
        StealthSettingsStore(storage).update { it.copy(warnRiskyActions = true) }
        storage.stored["hide_typing"] = 1
        assertEquals(StealthSettings(), StealthSettingsStore(storage).current)
    }

    @Test fun unknownVersionFailsClosedOnlyWhenProtected() {
        val protectedStorage = FakeStealthStorage()
        StealthSettingsStore(protectedStorage).update { it.copy(noStoryViews = true) }
        protectedStorage.stored["version"] = 7
        assertEquals(StealthSettings.FailClosed, StealthSettingsStore(protectedStorage).current)

        val openStorage = FakeStealthStorage()
        StealthSettingsStore(openStorage).update { it.copy(warnRiskyActions = true) }
        openStorage.stored["version"] = 7
        assertEquals(StealthSettings(), StealthSettingsStore(openStorage).current)
    }

    @Test fun unknownChatValueIsAReadErrorNotACrash() {
        val storage = FakeStealthStorage()
        StealthSettingsStore(storage).update { it.copy(hideOnline = true) }
        storage.stored["chat:9"] = "mute"
        assertEquals(StealthSettings.FailClosed, StealthSettingsStore(storage).current)
    }

    @Test fun missingProtectionFlagNextToBrokenDataCountsAsProtected() {
        val storage = FakeStealthStorage()
        storage.stored["version"] = 1
        storage.stored["hide_online"] = "on"
        assertEquals(StealthSettings.FailClosed, StealthSettingsStore(storage).current)
    }

    @Test fun unreadableProtectionFlagCountsAsProtected() {
        val storage = FakeStealthStorage()
        storage.stored["version"] = 3
        storage.stored["protection_on"] = "yes"
        assertEquals(StealthSettings.FailClosed, StealthSettingsStore(storage).current)
    }

    @Test fun unreadableStorageFailsClosed() {
        val storage = object : StealthStorage {
            override fun entries(): Map<String, *> = throw IllegalStateException("storage is gone")
            override fun replace(values: Map<String, Any>): Boolean = false
        }
        assertEquals(StealthSettings.FailClosed, StealthSettingsStore(storage).current)
    }

    @Test fun failedCommitStillEnforcesAStricterValueButNeverALooserOne() {
        val storage = FakeStealthStorage()
        val store = StealthSettingsStore(storage)
        assertTrue(store.update { it.copy(hideTyping = true) })

        storage.commitsSucceed = false
        assertFalse(store.update { it.copy(noReadReceipts = true) })
        assertTrue(store.current.noReadReceipts)

        assertFalse(store.update { it.copy(hideTyping = false) })
        assertTrue(store.current.hideTyping)

        val reopened = StealthSettingsStore(storage).current
        assertTrue(reopened.hideTyping)
        assertFalse(reopened.noReadReceipts)
    }

    @Test fun failedCommitFromFailClosedKeepsTheRefusal() {
        val storage = FakeStealthStorage()
        storage.stored["version"] = 9
        storage.stored["protection_on"] = true
        val store = StealthSettingsStore(storage)
        assertTrue(store.current.failClosed)

        storage.commitsSucceed = false
        assertFalse(store.update { StealthSettings() })
        assertTrue(store.current.failClosed)
    }

    @Test fun aCommittedWriteClearsFailClosed() {
        val storage = FakeStealthStorage()
        storage.stored["version"] = 9
        storage.stored["protection_on"] = true
        val store = StealthSettingsStore(storage)

        assertTrue(store.update { StealthSettings(noReadReceipts = true) })
        assertFalse(store.current.failClosed)
        assertTrue(store.current.noReadReceipts)
        assertTrue(store.current.allows(InteractionEffect.Typing, null))
    }

    @Test fun clearRemovesEverythingAndResetsToDefaults() {
        val storage = FakeStealthStorage()
        val store = StealthSettingsStore(storage)
        store.update { it.copy(hideOnline = true, chats = mapOf(3L to ChatStealthMode.Block)) }

        assertTrue(store.clear())
        assertEquals(StealthSettings(), store.current)
        assertTrue(storage.stored.isEmpty())
        assertEquals(StealthSettings(), StealthSettingsStore(storage).current)
    }

    @Test fun listenersRunOnlyWhenTheStateChanges() {
        val store = StealthSettingsStore(FakeStealthStorage())
        var calls = 0
        val listener: () -> Unit = { calls++ }
        store.addListener(listener)

        store.update { it.copy(hideTyping = true) }
        store.update { it.copy(hideTyping = true) }
        assertEquals(1, calls)

        store.removeListener(listener)
        store.update { it.copy(hideTyping = false) }
        assertEquals(1, calls)
    }

    @Test fun policyReadsTheCurrentSwitchesWithoutBeingRebuilt() {
        val store = StealthSettingsStore(FakeStealthStorage())
        val policy = StealthInteractionPolicy(store)
        assertTrue(policy.allows(InteractionEffect.Typing, 1L))

        store.update { it.copy(hideTyping = true) }
        assertFalse(policy.allows(InteractionEffect.Typing, 1L))
    }

    @Test fun accountsKeepSeparateSwitches() {
        val first = StealthSettingsStore(FakeStealthStorage())
        val second = StealthSettingsStore(FakeStealthStorage())
        first.update { it.copy(hideOnline = true) }

        assertTrue(first.current.hideOnline)
        assertFalse(second.current.hideOnline)
    }

    @Test fun preferenceNamesAreAccountScoped() {
        assertEquals("stealth-mode", stealthPreferencesName("legacy"))
        assertEquals("stealth-mode_a-3f2c", stealthPreferencesName("a-3f2c"))
        assertNotEquals(stealthPreferencesName("a-1"), stealthPreferencesName("a-2"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun preferenceNamesRejectPathEscapes() {
        stealthPreferencesName("../legacy")
    }

    @Test fun serverScheduledSendAndItsLeadSurviveANewStore() {
        val storage = FakeStealthStorage()
        assertTrue(StealthSettingsStore(storage).update { it.copy(serverScheduledSend = true, scheduledSendDelaySeconds = 60) })
        val reopened = StealthSettingsStore(storage).current
        assertTrue(reopened.serverScheduledSend)
        assertEquals(60, reopened.scheduledSendDelay)
        assertTrue(reopened.protectionActive)
    }

    @Test fun versionOneDataReadsWithServerScheduledSendOff() {
        val storage = FakeStealthStorage()
        StealthSettingsStore(storage).update { it.copy(hideOnline = true) }
        storage.stored.remove("scheduled_send")
        storage.stored.remove("scheduled_send_delay")
        storage.stored["version"] = 1
        val migrated = StealthSettingsStore(storage).current
        assertFalse(migrated.failClosed)
        assertTrue(migrated.hideOnline)
        assertFalse(migrated.serverScheduledSend)
        assertEquals(null, migrated.scheduledSendDelay)
    }

    @Test fun aLeadOutsideTheRangeIsAReadError() {
        val storage = FakeStealthStorage()
        StealthSettingsStore(storage).update { it.copy(serverScheduledSend = true) }
        storage.stored["scheduled_send_delay"] = 0
        assertEquals(StealthSettings.FailClosed, StealthSettingsStore(storage).current)
        storage.stored["scheduled_send_delay"] = 3601
        assertEquals(StealthSettings.FailClosed, StealthSettingsStore(storage).current)
    }

    @Test fun aCustomLeadIsKeptAndClampedToOneSecondToAnHour() {
        val storage = FakeStealthStorage()
        val store = StealthSettingsStore(storage)
        store.update { it.copy(serverScheduledSend = true, scheduledSendDelaySeconds = 45) }
        assertEquals(45, StealthSettingsStore(storage).current.scheduledSendDelay)
        store.update { it.copy(scheduledSendDelaySeconds = 10_000) }
        assertEquals(3600, store.current.scheduledSendDelay)
        store.update { it.copy(scheduledSendDelaySeconds = 0) }
        assertEquals(1, StealthSettingsStore(storage).current.scheduledSendDelay)
    }

    @Test fun onlyLeadsFromFifteenSecondsUseTheServerQueue() {
        assertFalse(StealthSettings(serverScheduledSend = true, scheduledSendDelaySeconds = 14).schedulesOnServer)
        assertTrue(StealthSettings(serverScheduledSend = true, scheduledSendDelaySeconds = 15).schedulesOnServer)
        assertTrue(StealthSettings(serverScheduledSend = true).schedulesOnServer)
        assertFalse(StealthSettings(scheduledSendDelaySeconds = 30).schedulesOnServer)
        assertTrue(StealthSettings.FailClosed.schedulesOnServer)
    }

    @Test fun failClosedSchedulesEverySend() {
        assertEquals(DEFAULT_STEALTH_SCHEDULE_DELAY_SECONDS, StealthSettings.FailClosed.scheduledSendDelay)
        assertEquals(null, StealthSettings().scheduledSendDelay)
    }

    @Test fun failedCommitNeverTurnsServerScheduledSendOffOrShortensIt() {
        val storage = FakeStealthStorage()
        val store = StealthSettingsStore(storage)
        assertTrue(store.update { it.copy(serverScheduledSend = true, scheduledSendDelaySeconds = 30) })

        storage.commitsSucceed = false
        assertFalse(store.update { it.copy(serverScheduledSend = false) })
        assertEquals(30, store.current.scheduledSendDelay)
        assertFalse(store.update { it.copy(scheduledSendDelaySeconds = 15) })
        assertEquals(30, store.current.scheduledSendDelay)
        assertFalse(store.update { it.copy(scheduledSendDelaySeconds = 120) })
        assertEquals(120, store.current.scheduledSendDelay)
    }
}
