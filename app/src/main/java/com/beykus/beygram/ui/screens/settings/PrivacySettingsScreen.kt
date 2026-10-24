package com.beykus.beygram.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ContactPreview
import com.beykus.beygram.domain.model.PrivacyRuleChoice
import com.beykus.beygram.domain.model.PrivacyRules
import com.beykus.beygram.domain.model.PrivacySetting
import com.beykus.beygram.domain.model.PrivacySettingState
import com.beykus.beygram.ui.components.Avatar
import com.beykus.beygram.ui.components.SearchField
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val visibleSettings = listOf(
    PrivacySetting.PhoneNumber,
    PrivacySetting.ProfilePhoto,
    PrivacySetting.LastSeen,
    PrivacySetting.Bio,
    PrivacySetting.ForwardedMessages
)

private val contactSettings = listOf(
    PrivacySetting.Calls,
    PrivacySetting.Messages,
    PrivacySetting.GroupInvites,
    PrivacySetting.FindByPhoneNumber
)

private data class ExceptionEntry(val userId: Long, val name: String)

private data class PrivacyExceptionsDraft(
    val setting: PrivacySetting,
    val choice: PrivacyRuleChoice,
    val always: List<ExceptionEntry>,
    val never: List<ExceptionEntry>
)

private fun PrivacySettingState.toExceptionsDraft() = PrivacyExceptionsDraft(
    setting = setting,
    choice = rules.choice,
    always = rules.alwaysAllowUserIds.map { ExceptionEntry(it, exceptionNames[it].orEmpty()) },
    never = rules.neverAllowUserIds.map { ExceptionEntry(it, exceptionNames[it].orEmpty()) }
)

/**
 * Telegram stores these rules on the server, so saving here changes the account on every
 * device. The screen deliberately does not read or write LastSeenSettingsStore: that store
 * only controls how BeyGram formats a contact's last-seen time.
 */
@Composable
fun PrivacySettingsScreen(
    loadPrivacy: suspend () -> Result<List<PrivacySettingState>>,
    savePrivacy: suspend (PrivacySetting, PrivacyRules) -> Result<Unit>,
    loadContacts: suspend () -> Result<List<ContactPreview>>,
    onBlockedSenders: () -> Unit,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var states by remember { mutableStateOf<List<PrivacySettingState>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf<PrivacySetting?>(null) }
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    var exceptionsDraft by remember { mutableStateOf<PrivacyExceptionsDraft?>(null) }
    var exceptionsError by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    LaunchedEffect(refresh) {
        loading = true
        loadError = null
        try {
            loadPrivacy().fold(
                { loaded -> states = loaded.sortedBy { PrivacySetting.entries.indexOf(it.setting) } },
                { loadError = it.userMessage(context, R.string.privacy_load_failed) }
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            loadError = failure.userMessage(context, R.string.privacy_load_failed)
        } finally {
            loading = false
        }
    }

    fun saveChoice(state: PrivacySettingState, choice: PrivacyRuleChoice) {
        if (saving != null || !state.rules.isEditable) return
        if (state.setting == PrivacySetting.FindByPhoneNumber && choice == PrivacyRuleChoice.Nobody) return
        val rules = state.rules.withChoice(choice)
        if (rules == state.rules) return
        val previous = states
        states = states.map { if (it.setting == state.setting) it.copy(rules = rules) else it }
        saving = state.setting
        actionError = null
        scope.launch {
            try {
                savePrivacy(state.setting, rules).fold(
                    {},
                    {
                        actionError = it.userMessage(context, R.string.privacy_save_failed)
                        states = previous
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                actionError = failure.userMessage(context, R.string.privacy_save_failed)
                states = previous
            } finally {
                saving = null
            }
        }
    }

    fun saveExceptions(draft: PrivacyExceptionsDraft) {
        if (saving != null || draft.setting == PrivacySetting.FindByPhoneNumber) return
        val current = states.firstOrNull { it.setting == draft.setting }
        if (current?.rules?.isEditable == false) return
        val rules = PrivacyRules(
            choice = draft.choice,
            alwaysAllowUserIds = draft.always.map { it.userId },
            neverAllowUserIds = draft.never.map { it.userId }
        ).normalized()
        val names = (draft.always + draft.never)
            .filter { it.name.isNotBlank() }
            .associate { it.userId to it.name }
        val previous = states
        states = states.map {
            if (it.setting == draft.setting) it.copy(rules = rules, exceptionNames = it.exceptionNames + names) else it
        }
        saving = draft.setting
        exceptionsError = null
        scope.launch {
            try {
                savePrivacy(draft.setting, rules).fold(
                    { exceptionsDraft = null },
                    {
                        exceptionsError = it.userMessage(context, R.string.privacy_save_failed)
                        states = previous
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                exceptionsError = failure.userMessage(context, R.string.privacy_save_failed)
                states = previous
            } finally {
                saving = null
            }
        }
    }

    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.privacy_title), onBack)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        Column(
            Modifier.fillMaxWidth().weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.privacy_intro), color = palette.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
            loadError?.let { message ->
                Surface(shape = RoundedCornerShape(16.dp), color = palette.warningSurface) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(message, color = palette.warningText)
                        TextButton(enabled = !loading, onClick = { refresh++ }) { Text(stringResource(R.string.common_retry), color = palette.accentInk) }
                    }
                }
            }
            actionError?.let { message -> Text(message, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
            if (states.isNotEmpty()) {
                val visibilityStates = states.filter { it.setting in visibleSettings }
                val contactStates = states.filter { it.setting in contactSettings }
                if (visibilityStates.isNotEmpty()) {
                    PrivacyGroup(
                        title = stringResource(R.string.privacy_group_visible),
                        states = visibilityStates,
                        saving = saving,
                        onChoice = ::saveChoice,
                        onManageExceptions = { exceptionsError = null; exceptionsDraft = it.toExceptionsDraft() }
                    )
                }
                if (contactStates.isNotEmpty()) {
                    PrivacyGroup(
                        title = stringResource(R.string.privacy_group_contact),
                        states = contactStates,
                        saving = saving,
                        onChoice = ::saveChoice,
                        onManageExceptions = { exceptionsError = null; exceptionsDraft = it.toExceptionsDraft() }
                    )
                }
            }
            BlockedUsersLink(onBlockedSenders)
            Text(stringResource(R.string.privacy_last_seen_hint), color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }

    PrivacyExceptionsDialog(
        draft = exceptionsDraft,
        saving = saving == exceptionsDraft?.setting,
        error = exceptionsError,
        loadContacts = loadContacts,
        onDismiss = { if (saving == null) { exceptionsDraft = null; exceptionsError = null } },
        onSave = ::saveExceptions
    )
}

@Composable
private fun PrivacyGroup(
    title: String,
    states: List<PrivacySettingState>,
    saving: PrivacySetting?,
    onChoice: (PrivacySettingState, PrivacyRuleChoice) -> Unit,
    onManageExceptions: (PrivacySettingState) -> Unit
) {
    val palette = beyGramPalette()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = palette.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface).padding(vertical = 4.dp)
        ) {
            states.forEachIndexed { index, state ->
                if (index > 0) HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 16.dp))
                PrivacySettingRow(
                    state = state,
                    saving = saving == state.setting,
                    onChoice = { onChoice(state, it) },
                    onManageExceptions = { onManageExceptions(state) }
                )
            }
        }
    }
}

@Composable
private fun PrivacySettingRow(
    state: PrivacySettingState,
    saving: Boolean,
    onChoice: (PrivacyRuleChoice) -> Unit,
    onManageExceptions: () -> Unit
) {
    val palette = beyGramPalette()
    val editable = state.rules.isEditable
    val supportsExceptions = editable && state.setting != PrivacySetting.FindByPhoneNumber
    val choices = if (state.setting == PrivacySetting.FindByPhoneNumber) {
        listOf(PrivacyRuleChoice.Everyone, PrivacyRuleChoice.Contacts)
    } else {
        PrivacyRuleChoice.entries
    }
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = editable && !saving) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(settingTitle(state.setting), color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (editable) audienceLabel(state.rules.choice) else stringResource(R.string.privacy_audience_custom),
                    color = palette.accentInk, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)
                )
                if (!editable) {
                    Text(
                        stringResource(R.string.privacy_managed_elsewhere),
                        color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (state.setting == PrivacySetting.Messages) {
                    Text(
                        stringResource(R.string.privacy_setting_messages_hint),
                        color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (state.rules.alwaysAllowUserIds.isNotEmpty()) {
                    Text(
                        stringResource(R.string.privacy_exceptions_always, state.rules.alwaysAllowUserIds.size),
                        color = palette.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.rules.neverAllowUserIds.isNotEmpty()) {
                    Text(
                        stringResource(R.string.privacy_exceptions_never, state.rules.neverAllowUserIds.size),
                        color = palette.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            when {
                saving -> CircularProgressIndicator(Modifier.size(20.dp), color = palette.accent, strokeWidth = 2.dp)
                !editable -> Icon(Icons.Rounded.Lock, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
                else -> Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
            }
        }
        if (editable) DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(audienceLabel(choice)) },
                    leadingIcon = if (choice == state.rules.choice) {
                        { Icon(Icons.Rounded.Check, null, tint = palette.accent) }
                    } else null,
                    onClick = {
                        expanded = false
                        if (choice != state.rules.choice) onChoice(choice)
                    }
                )
            }
            if (supportsExceptions) {
                HorizontalDivider(color = palette.divider)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.privacy_exceptions_manage)) },
                    onClick = { expanded = false; onManageExceptions() }
                )
            }
        }
    }
}

@Composable
private fun BlockedUsersLink(onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Block, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(stringResource(R.string.privacy_blocked_users_title), color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(stringResource(R.string.privacy_blocked_users_detail), color = palette.textSecondary, fontSize = 13.sp)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun PrivacyExceptionsDialog(
    draft: PrivacyExceptionsDraft?,
    saving: Boolean,
    error: String?,
    loadContacts: suspend () -> Result<List<ContactPreview>>,
    onDismiss: () -> Unit,
    onSave: (PrivacyExceptionsDraft) -> Unit
) {
    if (draft == null) return
    val current = draft
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var always by remember(current) { mutableStateOf(current.always) }
    var never by remember(current) { mutableStateOf(current.never) }
    var picker by remember(current) { mutableStateOf<Boolean?>(null) }
    var contacts by remember(current) { mutableStateOf<List<ContactPreview>?>(null) }
    var contactsLoading by remember(current) { mutableStateOf(false) }
    var contactsError by remember(current) { mutableStateOf<String?>(null) }

    fun ensureContacts() {
        if (contacts != null || contactsLoading) return
        contactsLoading = true
        contactsError = null
        scope.launch {
            try {
                loadContacts().fold(
                    { contacts = it.sortedBy { contact -> contact.title.lowercase() } },
                    { contactsError = it.userMessage(context, R.string.privacy_contacts_failed) }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                contactsError = failure.userMessage(context, R.string.privacy_contacts_failed)
            } finally {
                contactsLoading = false
            }
        }
    }

    fun pick(alwaysSide: Boolean) {
        picker = alwaysSide
        ensureContacts()
    }

    fun add(contact: ContactPreview, alwaysSide: Boolean) {
        val entry = ExceptionEntry(contact.userId, contact.title)
        if (alwaysSide) {
            always = always.filterNot { it.userId == contact.userId } + entry
            never = never.filterNot { it.userId == contact.userId }
        } else {
            never = never.filterNot { it.userId == contact.userId } + entry
            always = always.filterNot { it.userId == contact.userId }
        }
        picker = null
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(settingTitle(current.setting), color = palette.textPrimary) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                if (current.choice != PrivacyRuleChoice.Everyone) {
                    ExceptionSection(
                        title = stringResource(R.string.privacy_exceptions_always_title),
                        empty = stringResource(R.string.privacy_exceptions_empty),
                        entries = always,
                        addLabel = stringResource(R.string.privacy_exceptions_add_always),
                        enabled = !saving,
                        onAdd = { pick(true) },
                        onRemove = { id -> always = always.filterNot { it.userId == id } }
                    )
                }
                if (current.choice != PrivacyRuleChoice.Nobody) {
                    ExceptionSection(
                        title = stringResource(R.string.privacy_exceptions_never_title),
                        empty = stringResource(R.string.privacy_exceptions_empty),
                        entries = never,
                        addLabel = stringResource(R.string.privacy_exceptions_add_never),
                        enabled = !saving,
                        onAdd = { pick(false) },
                        onRemove = { id -> never = never.filterNot { it.userId == id } }
                    )
                }
                if (error != null) Text(error, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = palette.accent)
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = { onSave(current.copy(always = always, never = never)) }) {
                Text(stringResource(R.string.common_save), color = palette.accentInk)
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )

    val contactSide = picker
    if (contactSide != null) {
        ContactPickerDialog(
            title = stringResource(
                if (contactSide) R.string.privacy_exceptions_add_always else R.string.privacy_exceptions_add_never
            ),
            contacts = contacts,
            loading = contactsLoading,
            error = contactsError,
            excluded = (always + never).map { it.userId }.toSet(),
            onDismiss = { picker = null },
            onPick = { add(it, contactSide) }
        )
    }
}

@Composable
private fun ExceptionSection(
    title: String,
    empty: String,
    entries: List<ExceptionEntry>,
    addLabel: String,
    enabled: Boolean,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit
) {
    val palette = beyGramPalette()
    Text(title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
    if (entries.isEmpty()) {
        Text(empty, color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    } else {
        entries.forEach { entry ->
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.name.ifBlank { "#${entry.userId}" }, color = palette.textPrimary, fontSize = 14.sp,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                IconButton(enabled = enabled, onClick = { onRemove(entry.userId) }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.common_delete), tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
    TextButton(enabled = enabled, onClick = onAdd, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
        Text(addLabel, color = palette.accentInk, fontSize = 14.sp)
    }
}

@Composable
private fun ContactPickerDialog(
    title: String,
    contacts: List<ContactPreview>?,
    loading: Boolean,
    error: String?,
    excluded: Set<Long>,
    onDismiss: () -> Unit,
    onPick: (ContactPreview) -> Unit
) {
    val palette = beyGramPalette()
    var query by rememberSaveable { mutableStateOf("") }
    val shown = contacts.orEmpty()
        .filter { it.userId !in excluded }
        .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.subtitle.contains(query, ignoreCase = true) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = palette.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(title, color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                SearchField(query, { query = it }, stringResource(R.string.privacy_exceptions_search))
                if (loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp), color = palette.accent)
                }
                error?.let { Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
                if (!loading && error == null && shown.isEmpty()) {
                    Text(stringResource(R.string.privacy_exceptions_no_contacts), color = palette.textSecondary, modifier = Modifier.padding(vertical = 20.dp))
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.userId }) { contact ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(contact) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(contact.initials, Color(contact.avatarColorArgb), 40.dp)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(contact.title.ifBlank { contact.subtitle }, color = palette.textPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (contact.title.isNotBlank() && contact.subtitle.isNotBlank()) {
                                    Text(contact.subtitle, color = palette.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                }
            }
        }
    }
}

@Composable
private fun settingTitle(setting: PrivacySetting): String = stringResource(
    when (setting) {
        PrivacySetting.PhoneNumber -> R.string.privacy_setting_phone
        PrivacySetting.ProfilePhoto -> R.string.privacy_setting_photo
        PrivacySetting.LastSeen -> R.string.privacy_setting_last_seen
        PrivacySetting.Bio -> R.string.privacy_setting_bio
        PrivacySetting.ForwardedMessages -> R.string.privacy_setting_forwarded
        PrivacySetting.Calls -> R.string.privacy_setting_calls
        PrivacySetting.Messages -> R.string.privacy_setting_messages
        PrivacySetting.GroupInvites -> R.string.privacy_setting_groups
        PrivacySetting.FindByPhoneNumber -> R.string.privacy_setting_find_by_phone
    }
)

@Composable
private fun audienceLabel(choice: PrivacyRuleChoice): String = stringResource(
    when (choice) {
        PrivacyRuleChoice.Everyone -> R.string.privacy_audience_everyone
        PrivacyRuleChoice.Contacts -> R.string.privacy_audience_contacts
        PrivacyRuleChoice.Nobody -> R.string.privacy_audience_nobody
    }
)
