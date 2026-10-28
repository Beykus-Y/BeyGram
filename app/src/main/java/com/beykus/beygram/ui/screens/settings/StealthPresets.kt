package com.beykus.beygram.ui.screens.settings

import com.beykus.beygram.data.settings.StealthSettings

/** The three protection levels, and [Custom] for switches that match none of them. */
internal enum class StealthPresetKind { Normal, HiddenReading, Maximum, Custom }

/** The six account-wide switches. A preset is defined by these values alone; chat exceptions are never part of one. */
private data class StealthSwitches(
    val hideTyping: Boolean,
    val noReadReceipts: Boolean,
    val noContentOpen: Boolean,
    val hideOnline: Boolean,
    val noStoryViews: Boolean,
    val warnRiskyActions: Boolean
)

private val presetSwitches: Map<StealthPresetKind, StealthSwitches> = mapOf(
    StealthPresetKind.Normal to StealthSwitches(
        hideTyping = false, noReadReceipts = false, noContentOpen = false,
        hideOnline = false, noStoryViews = false, warnRiskyActions = false
    ),
    StealthPresetKind.HiddenReading to StealthSwitches(
        hideTyping = true, noReadReceipts = true, noContentOpen = true,
        hideOnline = false, noStoryViews = true, warnRiskyActions = false
    ),
    StealthPresetKind.Maximum to StealthSwitches(
        hideTyping = true, noReadReceipts = true, noContentOpen = true,
        hideOnline = true, noStoryViews = true, warnRiskyActions = true
    )
)

private val StealthSettings.switches: StealthSwitches
    get() = StealthSwitches(
        hideTyping = hideTyping,
        noReadReceipts = noReadReceipts,
        noContentOpen = noContentOpen,
        hideOnline = hideOnline,
        noStoryViews = noStoryViews,
        warnRiskyActions = warnRiskyActions
    )

/**
 * Sets the six switches of [kind] and keeps the chat exceptions and everything else in [current].
 * [StealthPresetKind.Custom] names no preset, so [current] comes back unchanged.
 */
internal fun stealthPreset(kind: StealthPresetKind, current: StealthSettings): StealthSettings {
    val preset = presetSwitches[kind] ?: return current
    return current.copy(
        hideTyping = preset.hideTyping,
        noReadReceipts = preset.noReadReceipts,
        noContentOpen = preset.noContentOpen,
        hideOnline = preset.hideOnline,
        noStoryViews = preset.noStoryViews,
        warnRiskyActions = preset.warnRiskyActions
    )
}

/** The preset whose six switches equal those of [settings], or [StealthPresetKind.Custom]. Chat exceptions do not count. */
internal fun matchingPreset(settings: StealthSettings): StealthPresetKind {
    val current = settings.switches
    return presetSwitches.entries.firstOrNull { it.value == current }?.key ?: StealthPresetKind.Custom
}
