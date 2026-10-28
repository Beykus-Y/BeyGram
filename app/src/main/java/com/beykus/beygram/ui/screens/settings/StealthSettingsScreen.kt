package com.beykus.beygram.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.settings.ChatStealthMode
import com.beykus.beygram.data.settings.StealthSettings
import com.beykus.beygram.data.settings.StealthSettingsStore
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Stealth mode for the open account: the status, the protection levels and the switches behind them, what the mode
 * cannot hide, the server-side last-seen rule, and the chat exceptions. [store] is null while the account has no store.
 */
@Composable
fun StealthSettingsScreen(store: StealthSettingsStore?, onServerPrivacy: () -> Unit, onBack: () -> Unit) {
    val palette = beyGramPalette()
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.stealth_title), onBack)
        if (store == null) {
            Text(
                stringResource(R.string.stealth_unavailable),
                color = palette.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        } else {
            StealthSettingsBody(store = store, onServerPrivacy = onServerPrivacy)
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
private fun ColumnScope.StealthSettingsBody(store: StealthSettingsStore, onServerPrivacy: () -> Unit) {
    val palette = beyGramPalette()
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
        StatusCard(settings = settings, saveFailed = saveFailed)

        GroupTitle(stringResource(R.string.stealth_group_presets))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface).padding(vertical = 4.dp)) {
            ChoiceRow(stringResource(R.string.stealth_preset_normal), selected = preset == StealthPresetKind.Normal) {
                update { stealthPreset(StealthPresetKind.Normal, it) }
            }
            SettingsRowDivider(startInset = SettingsRowInset)
            ChoiceRow(stringResource(R.string.stealth_preset_hidden_reading), selected = preset == StealthPresetKind.HiddenReading) {
                update { stealthPreset(StealthPresetKind.HiddenReading, it) }
            }
            SettingsRowDivider(startInset = SettingsRowInset)
            ChoiceRow(stringResource(R.string.stealth_preset_maximum), selected = preset == StealthPresetKind.Maximum) {
                update { stealthPreset(StealthPresetKind.Maximum, it) }
            }
            if (preset == StealthPresetKind.Custom) {
                SettingsRowDivider(startInset = SettingsRowInset)
                ChoiceRow(stringResource(R.string.stealth_preset_custom), selected = true, onSelect = null)
            }
        }

        GroupTitle(stringResource(R.string.stealth_group_switches))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface).padding(vertical = 4.dp)) {
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
            SettingsRowDivider(startInset = SettingsRowInset)
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

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.stealth_limits_title), color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.stealth_limits_scope), color = palette.textSecondary, fontSize = 13.sp)
            Text(stringResource(R.string.stealth_limits_actions), color = palette.textSecondary, fontSize = 13.sp)
            Text(stringResource(R.string.stealth_limits_online), color = palette.textSecondary, fontSize = 13.sp)
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface)
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

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (exceptions > 0) {
                Text(stringResource(R.string.stealth_exceptions_count, exceptions), color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Text(stringResource(R.string.stealth_exceptions_detail), color = palette.textSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StatusCard(settings: StealthSettings, saveFailed: Boolean) {
    val palette = beyGramPalette()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.stealth_title), color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(if (settings.protectionActive) R.string.stealth_status_on else R.string.stealth_status_off),
            color = if (settings.protectionActive) palette.accentInk else palette.textSecondary,
            fontSize = 14.sp
        )
        if (settings.failClosed) {
            Text(stringResource(R.string.stealth_fail_closed), color = palette.warningText, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (saveFailed) {
            Text(stringResource(R.string.stealth_save_failed), color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(text, color = beyGramPalette().textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp))
}

/** A radio row. Without [onSelect] it only shows its state, as the custom row does. */
@Composable
private fun ChoiceRow(title: String, selected: Boolean, onSelect: (() -> Unit)?) {
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
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onCheckedChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
