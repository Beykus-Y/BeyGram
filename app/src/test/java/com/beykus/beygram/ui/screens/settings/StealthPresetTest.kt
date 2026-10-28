package com.beykus.beygram.ui.screens.settings

import com.beykus.beygram.data.settings.ChatStealthMode
import com.beykus.beygram.data.settings.StealthSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class StealthPresetTest {
    private val exceptions = mapOf(10L to ChatStealthMode.Allow, 20L to ChatStealthMode.Block)
    private val allSwitchesOn = StealthSettings(
        hideTyping = true, noReadReceipts = true, noContentOpen = true,
        hideOnline = true, noStoryViews = true, warnRiskyActions = true, serverScheduledSend = true
    )

    @Test fun normalPresetTurnsEverySwitchOffAndKeepsChatExceptions() {
        val result = stealthPreset(StealthPresetKind.Normal, allSwitchesOn.copy(chats = exceptions))
        assertEquals(StealthSettings(chats = exceptions), result)
    }

    @Test fun hiddenReadingPresetSetsExactlyItsFourSwitchesAndKeepsChatExceptions() {
        val result = stealthPreset(
            StealthPresetKind.HiddenReading,
            StealthSettings(hideOnline = true, warnRiskyActions = true, chats = exceptions)
        )
        assertEquals(
            StealthSettings(
                hideTyping = true, noReadReceipts = true, noContentOpen = true, noStoryViews = true,
                chats = exceptions
            ),
            result
        )
    }

    @Test fun maximumPresetTurnsEverySwitchOnAndKeepsChatExceptions() {
        val result = stealthPreset(StealthPresetKind.Maximum, StealthSettings(chats = exceptions))
        assertEquals(allSwitchesOn.copy(chats = exceptions), result)
    }

    @Test fun customNamesNoPresetSoSettingsComeBackUnchanged() {
        val current = StealthSettings(hideTyping = true, chats = exceptions)
        assertEquals(current, stealthPreset(StealthPresetKind.Custom, current))
    }

    @Test fun eachPresetIsRecognisedAfterItIsApplied() {
        listOf(StealthPresetKind.Normal, StealthPresetKind.HiddenReading, StealthPresetKind.Maximum).forEach { kind ->
            val applied = stealthPreset(kind, StealthSettings(chats = exceptions))
            assertEquals(kind, matchingPreset(applied))
        }
    }

    @Test fun chatExceptionsDoNotChangeTheMatchedPreset() {
        assertEquals(StealthPresetKind.Maximum, matchingPreset(allSwitchesOn.copy(chats = exceptions)))
        assertEquals(StealthPresetKind.Normal, matchingPreset(StealthSettings(chats = exceptions)))
    }

    @Test fun switchesThatMatchNoPresetAreCustom() {
        assertEquals(StealthPresetKind.Custom, matchingPreset(StealthSettings(hideTyping = true)))
        // Maximum without the risky-action warning matches no preset.
        assertEquals(StealthPresetKind.Custom, matchingPreset(allSwitchesOn.copy(warnRiskyActions = false)))
        // Hidden reading with presence hidden as well matches no preset.
        assertEquals(
            StealthPresetKind.Custom,
            matchingPreset(
                StealthSettings(
                    hideTyping = true, noReadReceipts = true, noContentOpen = true,
                    noStoryViews = true, hideOnline = true
                )
            )
        )
    }

    @Test fun maximumWithoutServerScheduledSendIsCustomAndPresetsKeepTheChosenLead() {
        assertEquals(StealthPresetKind.Custom, matchingPreset(allSwitchesOn.copy(serverScheduledSend = false)))
        val applied = stealthPreset(StealthPresetKind.Normal, allSwitchesOn.copy(scheduledSendDelaySeconds = 60))
        assertEquals(60, applied.scheduledSendDelaySeconds)
        assertEquals(false, applied.serverScheduledSend)
    }
}
