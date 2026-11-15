package com.beykus.beygram.ui.screens.notifications

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.triggers.TriggerMatchMode
import com.beykus.beygram.domain.triggers.TriggerProblem
import com.beykus.beygram.domain.triggers.TriggerRule
import com.beykus.beygram.domain.triggers.TriggerScope
import com.beykus.beygram.domain.triggers.TriggerWordLimits
import com.beykus.beygram.domain.triggers.validateTriggerRule
import com.beykus.beygram.notifications.TriggerWordsStore
import com.beykus.beygram.ui.components.Button
import com.beykus.beygram.ui.components.Switch
import com.beykus.beygram.ui.components.TextButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.groupSurfaceColor
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassCard
import com.beykus.beygram.ui.design.glassScreenBackground
import com.beykus.beygram.ui.design.groupCardShape
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.screens.settings.SettingsGroup
import com.beykus.beygram.ui.screens.settings.SettingsRowDivider
import java.util.UUID

/** Trigger words: rules, alert scope and an entry into the mentions feed. */
@Composable
fun TriggerWordsScreen(
    store: TriggerWordsStore,
    onBack: () -> Unit,
    onOpenMentions: () -> Unit
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val rules by store.rules.collectAsStateWithLifecycle()
    val scopeChoice by store.scope.collectAsStateWithLifecycle()
    val mentions by store.mentions.collectAsStateWithLifecycle()
    val listScroll = rememberScrollState()
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorIsNew by rememberSaveable { mutableStateOf(false) }
    val closeEditor = { editorId = null; editorIsNew = false }
    val activeEditorId = editorId
    if (activeEditorId != null) {
        val initial = rules.firstOrNull { it.id == activeEditorId }
        // A removed rule must not be recreated by a restored editor.
        if (editorIsNew || initial != null) {
            TriggerEditorScreen(activeEditorId, initial, store, closeEditor)
            return
        }
        LaunchedEffect(activeEditorId) { closeEditor() }
    }

    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().glassScreenBackground(design, palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.trigger_title), onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(listScroll).padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.trigger_intro),
                color = palette.textSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp)
            )
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                    .glassCard(design, groupSurfaceColor(), palette.textSecondary, groupCardShape())
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .clickable(role = Role.Button, onClick = onOpenMentions)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(R.string.trigger_feed_open), color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.trigger_feed_count, mentions.size), color = palette.textSecondary, fontSize = 13.sp)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.textSecondary)
                }
            }
            Spacer(Modifier.height(16.dp))
            SettingsGroup(stringResource(R.string.trigger_scope_title)) {
                Column(Modifier.selectableGroup()) {
                    ChoiceOption(
                        selected = scopeChoice == TriggerScope.MUTED_CHATS,
                        title = stringResource(R.string.trigger_scope_muted),
                        hint = if (scopeChoice == TriggerScope.MUTED_CHATS) stringResource(R.string.trigger_scope_muted_hint) else null,
                        onSelect = { store.setScope(TriggerScope.MUTED_CHATS) }
                    )
                    SettingsRowDivider()
                    ChoiceOption(
                        selected = scopeChoice == TriggerScope.ALL_CHATS,
                        title = stringResource(R.string.trigger_scope_all),
                        hint = if (scopeChoice == TriggerScope.ALL_CHATS) stringResource(R.string.trigger_scope_all_hint) else null,
                        onSelect = { store.setScope(TriggerScope.ALL_CHATS) }
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.trigger_rule_summary, rules.count { it.enabled }, rules.size),
                    color = palette.textSecondary, fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Text("${rules.size}/${TriggerWordLimits.MAX_RULES}", color = palette.textSecondary, fontSize = 13.sp)
            }
            Button(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                shape = styledShape(16.dp),
                enabled = rules.size < TriggerWordLimits.MAX_RULES,
                onClick = {
                    editorIsNew = true
                    editorId = UUID.randomUUID().toString()
                }
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.trigger_add))
            }
            if (rules.size >= TriggerWordLimits.MAX_RULES) {
                Text(
                    stringResource(R.string.trigger_error_too_many, TriggerWordLimits.MAX_RULES),
                    color = palette.textSecondary, fontSize = 13.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                )
            }
            SettingsGroup(stringResource(R.string.trigger_list_title)) {
                if (rules.isEmpty()) {
                    Text(
                        stringResource(R.string.trigger_empty),
                        color = palette.textSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                } else {
                    rules.forEachIndexed { index, rule ->
                        key(rule.id) {
                            if (index > 0) SettingsRowDivider()
                            RuleRow(
                                rule = rule,
                                onEdit = {
                                    editorIsNew = false
                                    editorId = rule.id
                                },
                                onEnabledChange = { store.setRuleEnabled(rule.id, it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleRow(rule: TriggerRule, onEdit: () -> Unit, onEnabledChange: (Boolean) -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f).heightIn(min = 64.dp)
                .clickable(role = Role.Button, onClick = onEdit)
                .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    rule.pattern,
                    color = if (rule.enabled) palette.textPrimary else palette.textSecondary,
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(stringResource(modeTitleRes(rule.mode)), color = palette.textSecondary, fontSize = 13.sp)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
        }
        Box(
            Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .toggleable(value = rule.enabled, role = Role.Switch, onValueChange = onEnabledChange)
                .semantics { contentDescription = rule.pattern },
            contentAlignment = Alignment.Center
        ) {
            Switch(checked = rule.enabled, onCheckedChange = null)
        }
    }
}

@Composable
private fun ChoiceOption(selected: Boolean, title: String, hint: String?, onSelect: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected, onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = palette.accent, unselectedColor = palette.textSecondary)
        )
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 16.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            hint?.let { Text(it, color = palette.textSecondary, fontSize = 13.sp, lineHeight = 18.sp) }
        }
    }
}

@Composable
private fun FlagRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, hint: String? = null) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 16.sp)
            hint?.let { Text(it, color = palette.textSecondary, fontSize = 13.sp, lineHeight = 18.sp) }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Full-screen editor: the draft is local until Save, including across configuration changes. */
@Composable
private fun TriggerEditorScreen(ruleId: String, initial: TriggerRule?, store: TriggerWordsStore, onDismiss: () -> Unit) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    var pattern by rememberSaveable(ruleId) { mutableStateOf(initial?.pattern.orEmpty()) }
    var mode by rememberSaveable(ruleId) { mutableStateOf(initial?.mode ?: TriggerMatchMode.WHOLE_WORD) }
    var ignoreCase by rememberSaveable(ruleId) { mutableStateOf(initial?.ignoreCase ?: true) }
    var ignoreDiacritics by rememberSaveable(ruleId) { mutableStateOf(initial?.ignoreDiacritics ?: false) }
    var rejected by remember(ruleId) { mutableStateOf<TriggerProblem?>(null) }
    var confirmDelete by rememberSaveable(ruleId) { mutableStateOf(false) }
    val draft = TriggerRule(
        id = ruleId,
        pattern = pattern,
        mode = mode,
        ignoreCase = ignoreCase,
        ignoreDiacritics = ignoreDiacritics && mode != TriggerMatchMode.REGEX,
        enabled = initial?.enabled ?: true
    )
    val problem = remember(draft) { if (pattern.isBlank()) null else validateTriggerRule(draft) }
    val shown = rejected ?: problem
    val canSave = pattern.isNotBlank() && problem == null

    BackHandler(onBack = onDismiss)
    Column(
        Modifier.fillMaxSize().glassScreenBackground(design, palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
    ) {
        ProfileToolbar(stringResource(if (initial == null) R.string.trigger_editor_new else R.string.trigger_editor_edit), onDismiss)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it; rejected = null },
                label = { Text(stringResource(if (mode == TriggerMatchMode.REGEX) R.string.trigger_mode_regex else R.string.trigger_field_pattern)) },
                supportingText = {
                    val message = shown?.let { problemText(it) }
                        ?: if (mode != TriggerMatchMode.REGEX) stringResource(R.string.trigger_field_pattern_hint) else null
                    if (message != null) Text(message, color = if (shown != null) palette.danger else palette.textSecondary)
                },
                isError = shown != null,
                singleLine = true,
                shape = styledShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = palette.textPrimary, unfocusedTextColor = palette.textPrimary,
                    focusedBorderColor = palette.accent, unfocusedBorderColor = palette.divider,
                    focusedLabelColor = palette.accent, unfocusedLabelColor = palette.textSecondary,
                    cursorColor = palette.accent, errorBorderColor = palette.danger,
                    errorLabelColor = palette.danger, errorCursorColor = palette.danger
                ),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
            )
            SettingsGroup(stringResource(R.string.trigger_mode_title)) {
                Column(Modifier.selectableGroup()) {
                    TriggerMatchMode.entries.forEachIndexed { index, option ->
                        if (index > 0) SettingsRowDivider()
                        ChoiceOption(
                            selected = mode == option,
                            title = stringResource(modeTitleRes(option)),
                            hint = if (mode == option) stringResource(modeHintRes(option)) else null,
                            onSelect = { mode = option; rejected = null }
                        )
                    }
                }
            }
            SettingsGroup(stringResource(R.string.settings_title)) {
                FlagRow(
                    title = stringResource(R.string.trigger_flag_case),
                    checked = ignoreCase,
                    onChange = { ignoreCase = it; rejected = null }
                )
                if (mode != TriggerMatchMode.REGEX) {
                    SettingsRowDivider()
                    FlagRow(
                        title = stringResource(R.string.trigger_flag_diacritics),
                        checked = ignoreDiacritics,
                        onChange = { ignoreDiacritics = it; rejected = null },
                        hint = stringResource(R.string.trigger_flag_diacritics_hint)
                    )
                }
            }
            if (initial != null) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.trigger_editor_delete), color = palette.danger)
                    }
                }
            }
        }
        Button(
            enabled = canSave,
            shape = styledShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            onClick = {
                val result = store.saveRule(draft)
                if (result == null) onDismiss() else rejected = result
            }
        ) { Text(stringResource(R.string.trigger_editor_save)) }
    }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.trigger_delete_title)) },
            text = { Text(stringResource(R.string.trigger_delete_body)) },
            containerColor = groupSurfaceColor(),
            titleContentColor = palette.textPrimary,
            textContentColor = palette.textSecondary,
            confirmButton = {
                TextButton(onClick = { store.deleteRule(ruleId); onDismiss() }) {
                    Text(stringResource(R.string.trigger_editor_delete), color = palette.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@StringRes
private fun modeTitleRes(mode: TriggerMatchMode): Int = when (mode) {
    TriggerMatchMode.WHOLE_WORD -> R.string.trigger_mode_whole
    TriggerMatchMode.CONTAINS -> R.string.trigger_mode_contains
    TriggerMatchMode.REGEX -> R.string.trigger_mode_regex
}

@StringRes
private fun modeHintRes(mode: TriggerMatchMode): Int = when (mode) {
    TriggerMatchMode.WHOLE_WORD -> R.string.trigger_mode_whole_hint
    TriggerMatchMode.CONTAINS -> R.string.trigger_mode_contains_hint
    TriggerMatchMode.REGEX -> R.string.trigger_mode_regex_hint
}

@Composable
private fun problemText(problem: TriggerProblem): String = when (problem) {
    TriggerProblem.EMPTY -> stringResource(R.string.trigger_error_empty)
    TriggerProblem.TOO_LONG -> stringResource(R.string.trigger_error_long, TriggerWordLimits.MAX_PATTERN_LENGTH)
    TriggerProblem.BAD_REGEX -> stringResource(R.string.trigger_error_regex)
    TriggerProblem.REGEX_MATCHES_EMPTY -> stringResource(R.string.trigger_error_regex_empty)
    TriggerProblem.TOO_MANY -> stringResource(R.string.trigger_error_too_many, TriggerWordLimits.MAX_RULES)
}
