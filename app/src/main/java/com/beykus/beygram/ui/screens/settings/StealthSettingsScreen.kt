package com.beykus.beygram.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.foundation.text.KeyboardOptions
import com.beykus.beygram.ui.components.BeyGramDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import com.beykus.beygram.ui.components.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.settings.ChatStealthMode
import com.beykus.beygram.data.settings.DelayedSendChoices
import com.beykus.beygram.data.settings.DelayedSendSettingsStore
import com.beykus.beygram.data.settings.StealthSettings
import com.beykus.beygram.data.settings.StealthScheduleDelayChoices
import com.beykus.beygram.data.settings.StealthScheduleDelayRange
import com.beykus.beygram.data.settings.StealthSettingsStore
import com.beykus.beygram.data.settings.stealthDelayUsesServer
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.groupSurfaceColor
import com.beykus.beygram.ui.design.groupCardShape
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassScreenBackground
import com.beykus.beygram.ui.design.glassCard
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Stealth mode for the open account: the status, the protection levels and the switches behind them, what the mode
 * cannot hide, the server-side last-seen rule, the chat exceptions and the delay before sending. [store] is null while the
 * account has no store; [delayStore] is null in the same case.
 */
@Composable
fun StealthSettingsScreen(
    store: StealthSettingsStore?,
    onServerPrivacy: () -> Unit,
    onBack: () -> Unit,
    delayStore: DelayedSendSettingsStore? = null,
    accountName: String? = null,
    exceptionChats: List<ChatPreview> = emptyList()
) {
    var showExceptions by rememberSaveable(store) { mutableStateOf(false) }
    if (store != null && showExceptions) {
        StealthExceptionsScreen(store, exceptionChats, onBack = { showExceptions = false })
        return
    }
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().glassScreenBackground(design, palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.stealth_title), onBack)
        if (store == null) {
            Text(
                stringResource(R.string.stealth_unavailable),
                color = palette.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        } else {
            StealthSettingsBody(
                store = store,
                onServerPrivacy = onServerPrivacy,
                delayStore = delayStore,
                accountName = accountName,
                onExceptions = { showExceptions = true }
            )
        }
    }
}

/** The account's switches for the composition, or the defaults while the account has no stealth store. */
@Composable
internal fun rememberStealthSettings(store: StealthSettingsStore?): State<StealthSettings> {
    val defaults = remember { MutableStateFlow(StealthSettings()) }
    return (store?.state ?: defaults).collectAsStateWithLifecycle()
}

@Composable
private fun ColumnScope.StealthSettingsBody(
    store: StealthSettingsStore,
    onServerPrivacy: () -> Unit,
    delayStore: DelayedSendSettingsStore?,
    accountName: String?,
    onExceptions: () -> Unit
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val settings by store.state.collectAsStateWithLifecycle()
    var saveFailed by remember { mutableStateOf(false) }
    fun update(transform: (StealthSettings) -> StealthSettings) {
        saveFailed = !store.update(transform)
    }
    val preset = matchingPreset(settings)
    val exceptions = settings.chats.count { it.value != ChatStealthMode.Inherit }

    Column(
        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusCard(settings, preset, exceptions, saveFailed, accountName) { choice ->
            update { stealthPreset(choice, it) }
        }

        GroupTitle(stringResource(R.string.stealth_group_reading))
        Column(Modifier.fillMaxWidth().glassCard(design, groupSurfaceColor(), palette.textSecondary, groupCardShape()).padding(vertical = 4.dp)) {
            SwitchRow(
                title = stringResource(R.string.stealth_hide_typing_title),
                detail = stringResource(R.string.stealth_hide_typing_detail),
                checked = settings.hideTyping
            ) { value -> update { it.copy(hideTyping = value) } }
            SettingsRowDivider(startInset = SettingsRowInset)
            SwitchRow(
                title = stringResource(R.string.stealth_no_read_receipts_title),
                detail = stringResource(R.string.stealth_no_read_receipts_detail, stringResource(R.string.chats_action_read)),
                checked = settings.noReadReceipts
            ) { value -> update { it.copy(noReadReceipts = value) } }
            SettingsRowDivider(startInset = SettingsRowInset)
            SwitchRow(
                title = stringResource(R.string.stealth_no_content_open_title),
                detail = stringResource(R.string.stealth_no_content_open_detail),
                checked = settings.noContentOpen
            ) { value -> update { it.copy(noContentOpen = value) } }
            SettingsRowDivider(startInset = SettingsRowInset)
            SwitchRow(
                title = stringResource(R.string.stealth_no_story_views_title),
                detail = stringResource(R.string.stealth_no_story_views_detail),
                checked = settings.noStoryViews
            ) { value -> update { it.copy(noStoryViews = value) } }
        }

        GroupTitle(stringResource(R.string.stealth_group_activity))
        Column(Modifier.fillMaxWidth().glassCard(design, groupSurfaceColor(), palette.textSecondary, groupCardShape()).padding(vertical = 4.dp)) {
            SwitchRow(
                title = stringResource(R.string.stealth_hide_online_title),
                detail = stringResource(R.string.stealth_hide_online_detail),
                checked = settings.hideOnline
            ) { value -> update { it.copy(hideOnline = value) } }
            SettingsRowDivider(startInset = SettingsRowInset)
            SwitchRow(
                title = stringResource(R.string.stealth_warn_risky_title),
                detail = stringResource(R.string.stealth_warn_risky_detail),
                checked = settings.warnRiskyActions
            ) { value -> update { it.copy(warnRiskyActions = value) } }
        }

        Row(
            Modifier.fillMaxWidth().glassCard(design, groupSurfaceColor(), palette.textSecondary, groupCardShape())
                .heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onExceptions)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.stealth_exceptions_count, exceptions),
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
        }

        ScheduledSendSection(settings = settings, update = ::update)

        Row(
            Modifier.fillMaxWidth().glassCard(design, groupSurfaceColor(), palette.textSecondary, groupCardShape())
                .clickable(role = Role.Button, onClick = onServerPrivacy)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.stealth_server_last_seen_title), color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.stealth_server_last_seen_detail), color = palette.textSecondary, fontSize = 13.sp)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
        }

        if (delayStore != null) DelayedSendSection(delayStore)
    }
}

/**
 * Sending through Telegram's scheduled messages (#541): the server publishes the message after the chosen lead, so the
 * app does not have to be open then. The presets start at 15 seconds because Telegram sends anything due within 10 at once;
 * «Своё время» accepts 15 seconds to an hour; a legacy short value must be corrected before sending.
 */
@Composable
private fun ScheduledSendSection(settings: StealthSettings, update: ((StealthSettings) -> StealthSettings) -> Unit) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    var editingCustom by remember { mutableStateOf(false) }
    GroupTitle(stringResource(R.string.stealth_schedule_group_title))
    Column(Modifier.fillMaxWidth().glassCard(design, groupSurfaceColor(), palette.textSecondary, groupCardShape()).padding(vertical = 4.dp)) {
        SwitchRow(
            title = stringResource(R.string.stealth_schedule_title),
            detail = stringResource(R.string.stealth_schedule_detail),
            checked = settings.serverScheduledSend
        ) { value -> update { it.copy(serverScheduledSend = value) } }
        if (settings.serverScheduledSend) {
            StealthScheduleDelayChoices.forEach { seconds ->
                SettingsRowDivider(startInset = SettingsRowInset)
                ChoiceRow(scheduleDelayLabel(seconds), selected = settings.scheduledSendDelaySeconds == seconds) {
                    update { it.copy(scheduledSendDelaySeconds = seconds) }
                }
            }
            val custom = settings.scheduledSendDelaySeconds !in StealthScheduleDelayChoices
            SettingsRowDivider(startInset = SettingsRowInset)
            ChoiceRow(
                if (custom) stringResource(R.string.stealth_schedule_custom_value, scheduleDelayLabel(settings.scheduledSendDelaySeconds))
                else stringResource(R.string.stealth_schedule_custom),
                selected = custom
            ) { editingCustom = true }
        }
    }
    if (editingCustom) {
        CustomDelayDialog(
            initial = settings.scheduledSendDelaySeconds,
            onDismiss = { editingCustom = false },
            onConfirm = { seconds ->
                editingCustom = false
                update { it.copy(scheduledSendDelaySeconds = seconds) }
            }
        )
    }
    Column(
        Modifier.fillMaxWidth().glassCard(design, groupSurfaceColor(), palette.textSecondary, groupCardShape()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (settings.serverScheduledSend && !stealthDelayUsesServer(settings.scheduledSendDelaySeconds)) {
            Text(stringResource(R.string.stealth_schedule_short_warning), color = palette.warningText, fontSize = 13.sp)
        }
        Text(stringResource(R.string.stealth_schedule_footnote), color = palette.textSecondary, fontSize = 13.sp)
        Text(stringResource(R.string.stealth_schedule_limits), color = palette.textSecondary, fontSize = 13.sp)
    }
}

/** Asks for a lead in whole seconds within [StealthScheduleDelayRange]; anything else keeps the dialog open with an error. */
@Composable
private fun CustomDelayDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val palette = beyGramPalette()
    var text by remember { mutableStateOf(initial.toString()) }
    val value = text.trim().toIntOrNull()?.takeIf { it in StealthScheduleDelayRange }
    BeyGramDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.stealth_schedule_custom),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.stealth_schedule_dialog_hint), color = palette.textSecondary, fontSize = 13.sp)
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.stealth_schedule_dialog_label)) },
                    singleLine = true,
                    isError = value == null,
                    supportingText = if (value == null) {
                        { Text(stringResource(R.string.stealth_schedule_dialog_error)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmText = stringResource(R.string.common_save),
        confirmEnabled = value != null,
        onConfirm = { value?.let(onConfirm) },
        dismissText = stringResource(R.string.common_cancel),
        onDismiss = onDismiss
    )
}

@Composable
private fun scheduleDelayLabel(seconds: Int): String = when {
    seconds % 60 == 0 -> pluralStringResource(R.plurals.stealth_schedule_minutes, seconds / 60, seconds / 60)
    else -> stringResource(R.string.delayed_send_seconds, seconds)
}

/** The local delay before a plain text message is sent. It is convenience only, and the footnote says that it hides nothing. */
@Composable
private fun DelayedSendSection(store: DelayedSendSettingsStore) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val selectedDelay by store.delaySeconds.collectAsStateWithLifecycle()
    var saveFailed by remember { mutableStateOf(false) }
    GroupTitle(stringResource(R.string.delayed_send_group_title))
    Column(Modifier.fillMaxWidth().glassCard(design, groupSurfaceColor(), palette.textSecondary, groupCardShape()).padding(vertical = 4.dp)) {
        DelayedSendChoices.forEachIndexed { index, seconds ->
            if (index > 0) SettingsRowDivider(startInset = SettingsRowInset)
            ChoiceRow(delayChoiceLabel(seconds), selected = selectedDelay == seconds) { saveFailed = !store.update(seconds) }
        }
    }
    Column(
        Modifier.fillMaxWidth().glassCard(design, groupSurfaceColor(), palette.textSecondary, groupCardShape()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.delayed_send_footnote), color = palette.textSecondary, fontSize = 13.sp)
        if (saveFailed) Text(stringResource(R.string.stealth_save_failed), color = palette.danger, fontSize = 13.sp)
    }
}

@Composable
private fun delayChoiceLabel(seconds: Int): String = when (seconds) {
    0 -> stringResource(R.string.delayed_send_off)
    60 -> stringResource(R.string.delayed_send_minute)
    else -> stringResource(R.string.delayed_send_seconds, seconds)
}

@Composable
private fun StatusCard(
    settings: StealthSettings,
    preset: StealthPresetKind,
    exceptionCount: Int,
    saveFailed: Boolean,
    accountName: String?,
    onPresetSelected: (StealthPresetKind) -> Unit
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    var showLimits by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().glassCard(design, groupSurfaceColor(), palette.textSecondary, groupCardShape()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.stealth_client_state), color = palette.textSecondary, fontSize = 13.sp)
        accountName?.let { Text(it, color = palette.textSecondary, fontSize = 13.sp) }
        Text(
            stringResource(if (settings.failClosed) R.string.stealth_unavailable else preset.labelRes()),
            color = palette.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() }
        )
        Text(stringResource(R.string.stealth_server_visibility_unknown), color = palette.textSecondary, fontSize = 13.sp)
        if (settings.failClosed || saveFailed) {
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                if (settings.failClosed) Text(stringResource(R.string.stealth_fail_closed), color = palette.warningText, fontSize = 14.sp)
                if (saveFailed) Text(stringResource(R.string.stealth_save_failed), color = palette.danger, fontSize = 14.sp)
            }
        }

        GroupTitle(stringResource(R.string.stealth_quick_presets))
        StealthPresetChoices(preset.takeUnless { settings.failClosed }, onPresetSelected)

        GroupTitle(stringResource(R.string.stealth_active_parameters))
        val activeLabels = buildList {
            if (settings.hideTyping) add(R.string.stealth_summary_typing)
            if (settings.noReadReceipts) add(R.string.stealth_summary_reads)
            if (settings.noContentOpen) add(R.string.stealth_summary_media)
            if (settings.noStoryViews) add(R.string.stealth_summary_stories)
            if (settings.hideOnline) add(R.string.stealth_summary_presence)
            if (settings.hideOnline && settings.warnRiskyActions) add(R.string.stealth_summary_warnings)
            if (settings.serverScheduledSend) add(R.string.stealth_summary_scheduled)
        }
        if (activeLabels.isEmpty() && exceptionCount == 0) {
            Text(stringResource(R.string.stealth_no_active_parameters), color = palette.textSecondary, fontSize = 13.sp)
        }
        if (activeLabels.isNotEmpty()) {
            val activeSettings = activeLabels.map { stringResource(it) }
            Text(activeSettings.joinToString(" · "), color = palette.textSecondary, fontSize = 13.sp)
        }
        if (settings.serverScheduledSend && !stealthDelayUsesServer(settings.scheduledSendDelaySeconds)) {
            Text(stringResource(R.string.stealth_schedule_short_warning), color = palette.warningText, fontSize = 13.sp)
        }
        if (exceptionCount > 0) {
            Text(stringResource(R.string.stealth_exceptions_count, exceptionCount), color = palette.textSecondary, fontSize = 13.sp)
        }
        Text(stringResource(R.string.stealth_summary_scope), color = palette.textSecondary, fontSize = 13.sp)
        TextButton(onClick = { showLimits = !showLimits }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(if (showLimits) R.string.stealth_hide_limits else R.string.stealth_show_limits))
        }
        if (showLimits) {
            Text(stringResource(R.string.stealth_limits_scope), color = palette.textSecondary, fontSize = 13.sp)
            Text(stringResource(R.string.stealth_limits_actions), color = palette.textSecondary, fontSize = 13.sp)
            Text(stringResource(R.string.stealth_limits_online), color = palette.textSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun StealthPresetChoices(selected: StealthPresetKind?, onSelected: (StealthPresetKind) -> Unit) {
    Column(Modifier.selectableGroup()) {
        listOf(StealthPresetKind.Normal, StealthPresetKind.HiddenReading, StealthPresetKind.Maximum).forEach { choice ->
            ChoiceRow(stringResource(choice.labelRes()), selected = selected == choice) { onSelected(choice) }
        }
    }
}

internal fun StealthPresetKind.labelRes(): Int = when (this) {
    StealthPresetKind.Normal -> R.string.stealth_preset_normal
    StealthPresetKind.HiddenReading -> R.string.stealth_preset_hidden_reading
    StealthPresetKind.Maximum -> R.string.stealth_preset_maximum
    StealthPresetKind.Custom -> R.string.stealth_preset_custom
}

@Composable
private fun GroupTitle(text: String) {
    Text(text, color = beyGramPalette().textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp).semantics { heading() })
}

/** A radio row. Without [onSelect] it only shows its state, as the custom row does. */
@Composable
internal fun ChoiceRow(title: String, selected: Boolean, onSelect: (() -> Unit)?) {
    val palette = beyGramPalette()
    val base = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    val rowModifier = if (onSelect == null) base else base.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
    Row(rowModifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        // The row is the radio button for TalkBack, so the radio itself takes no clicks.
        RadioButton(selected = selected, onClick = null)
        Text(title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
