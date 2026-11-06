package com.beykus.beygram.ui.screens.notifications

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import java.util.UUID

/** Trigger words: the rules, the scope of their alerts, and the way into the mentions feed. */
@Composable
fun TriggerWordsScreen(
    store: TriggerWordsStore,
    onBack: () -> Unit,
    onOpenMentions: () -> Unit
) {
    val palette = beyGramPalette()
    val rules by store.rules.collectAsStateWithLifecycle()
    val scopeChoice by store.scope.collectAsStateWithLifecycle()
    val mentions by store.mentions.collectAsStateWithLifecycle()
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TriggerRule?>(null) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.trigger_title), onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.trigger_intro), color = palette.textSecondary)
            Text(stringResource(R.string.trigger_scope_title), color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            ChoiceOption(
                selected = scopeChoice == TriggerScope.MUTED_CHATS,
                title = stringResource(R.string.trigger_scope_muted),
                hint = stringResource(R.string.trigger_scope_muted_hint),
                onSelect = { store.setScope(TriggerScope.MUTED_CHATS) }
            )
            ChoiceOption(
                selected = scopeChoice == TriggerScope.ALL_CHATS,
                title = stringResource(R.string.trigger_scope_all),
                hint = stringResource(R.string.trigger_scope_all_hint),
                onSelect = { store.setScope(TriggerScope.ALL_CHATS) }
            )
            HorizontalDivider(color = palette.divider)
            Text(stringResource(R.string.trigger_list_title), color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (rules.isEmpty()) {
                Text(stringResource(R.string.trigger_empty), color = palette.textSecondary)
            }
            rules.forEach { rule ->
                RuleRow(
                    rule = rule,
                    onEdit = {
                        editing = rule
                        editorOpen = true
                    },
                    onEnabledChange = { store.setRuleEnabled(rule.id, it) }
                )
            }
            Button(
                shape = styledShape(16.dp),
                enabled = rules.size < TriggerWordLimits.MAX_RULES,
                onClick = {
                    editing = null
                    editorOpen = true
                }
            ) { Text(stringResource(R.string.trigger_add)) }
            HorizontalDivider(color = palette.divider)
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpenMentions), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.trigger_feed_open), color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.trigger_feed_count, mentions.size), color = palette.textSecondary)
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.textSecondary)
            }
        }
    }
    if (editorOpen) {
        TriggerEditorDialog(
            initial = editing,
            store = store,
            onDismiss = { editorOpen = false }
        )
    }
}

@Composable
private fun RuleRow(rule: TriggerRule, onEdit: () -> Unit, onEnabledChange: (Boolean) -> Unit) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().clickable(onClick = onEdit), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                rule.pattern,
                color = if (rule.enabled) palette.textPrimary else palette.textSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(stringResource(modeTitleRes(rule.mode)), color = palette.textSecondary, fontSize = 13.sp)
        }
        Switch(checked = rule.enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
private fun ChoiceOption(selected: Boolean, title: String, hint: String, onSelect: () -> Unit) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().clickable(onClick = onSelect), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, color = palette.textSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun FlagRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, hint: String? = null) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 15.sp)
            hint?.let { Text(it, color = palette.textSecondary, fontSize = 13.sp) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TriggerEditorDialog(initial: TriggerRule?, store: TriggerWordsStore, onDismiss: () -> Unit) {
    val palette = beyGramPalette()
    val newId = remember { UUID.randomUUID().toString() }
    var pattern by remember { mutableStateOf(initial?.pattern.orEmpty()) }
    var mode by remember { mutableStateOf(initial?.mode ?: TriggerMatchMode.WHOLE_WORD) }
    var ignoreCase by remember { mutableStateOf(initial?.ignoreCase ?: true) }
    var ignoreDiacritics by remember { mutableStateOf(initial?.ignoreDiacritics ?: false) }
    // A save refused by the store (for example the list is full) is shown until the user edits the form again.
    var rejected by remember { mutableStateOf<TriggerProblem?>(null) }
    val draft = TriggerRule(
        id = initial?.id ?: newId,
        pattern = pattern,
        mode = mode,
        ignoreCase = ignoreCase,
        ignoreDiacritics = ignoreDiacritics && mode != TriggerMatchMode.REGEX,
        enabled = initial?.enabled ?: true
    )
    val problem = if (pattern.isBlank()) null else validateTriggerRule(draft)
    val shown = rejected ?: problem
    val canSave = pattern.isNotBlank() && problem == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.trigger_editor_new else R.string.trigger_editor_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = {
                        pattern = it
                        rejected = null
                    },
                    label = { Text(stringResource(R.string.trigger_field_pattern)) },
                    supportingText = {
                        Text(shown?.let { problemText(it) } ?: stringResource(R.string.trigger_field_pattern_hint))
                    },
                    isError = shown != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.trigger_mode_title), color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                TriggerMatchMode.entries.forEach { option ->
                    ChoiceOption(
                        selected = mode == option,
                        title = stringResource(modeTitleRes(option)),
                        hint = stringResource(modeHintRes(option)),
                        onSelect = {
                            mode = option
                            rejected = null
                        }
                    )
                }
                FlagRow(
                    title = stringResource(R.string.trigger_flag_case),
                    checked = ignoreCase,
                    onChange = { ignoreCase = it }
                )
                if (mode != TriggerMatchMode.REGEX) {
                    FlagRow(
                        title = stringResource(R.string.trigger_flag_diacritics),
                        checked = ignoreDiacritics,
                        onChange = { ignoreDiacritics = it },
                        hint = stringResource(R.string.trigger_flag_diacritics_hint)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = {
                val result = store.saveRule(draft)
                if (result == null) {
                    onDismiss()
                } else {
                    rejected = result
                }
            }) { Text(stringResource(R.string.trigger_editor_save)) }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (initial != null) {
                    TextButton(onClick = {
                        store.deleteRule(initial.id)
                        onDismiss()
                    }) { Text(stringResource(R.string.trigger_editor_delete), color = palette.danger) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    )
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
