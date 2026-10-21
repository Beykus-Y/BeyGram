package com.beykus.beygram.ui.screens.chats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.TelegramChatFolders
import com.beykus.beygram.domain.model.TelegramFolderDraft
import com.beykus.beygram.domain.model.movedTelegramFolderIds
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.launch

private enum class FolderChoice { Include, Exclude }

@Composable
internal fun FolderManagerScreen(
    snapshot: TelegramChatFolders,
    knownChats: List<ChatPreview>,
    loadFolder: suspend (Int) -> Result<TelegramFolderDraft>,
    saveFolder: suspend (TelegramFolderDraft) -> Result<Int>,
    deleteFolder: suspend (Int) -> Result<Unit>,
    reorderFolders: suspend (List<Int>, Int) -> Result<Unit>,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<TelegramFolderDraft?>(null) }
    var picking by remember { mutableStateOf<FolderChoice?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var uncertainCreateBaseline by remember { mutableStateOf<Set<Int>?>(null) }
    fun back() {
        when {
            busy -> Unit
            picking != null -> picking = null
            draft != null -> { draft = null; error = null; uncertainCreateBaseline = null }
            else -> onBack()
        }
    }
    BackHandler(onBack = ::back)
    // A timed-out create may already have succeeded. Reconcile a NEW server folder ID;
    // don't resubmit the same ID-less draft while its result is uncertain.
    LaunchedEffect(snapshot.folders, uncertainCreateBaseline) {
        val before = uncertainCreateBaseline ?: return@LaunchedEffect
        val name = draft?.title?.trim() ?: return@LaunchedEffect
        if (snapshot.folders.any { it.id !in before && it.title == name }) {
            uncertainCreateBaseline = null
            draft = null
            error = null
        }
    }

    Column(
        Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
    ) {
        ProfileToolbar(stringResource(R.string.folder_manage_title), ::back)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text(it, color = palette.danger, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }

        val editing = draft
        when {
            editing != null && picking != null -> {
                val choice = requireNotNull(picking)
                FolderChatPicker(
                    chats = knownChats,
                    selected = if (choice == FolderChoice.Include) editing.includedChatIds else editing.excludedChatIds,
                    onToggle = { id, checked ->
                        draft = if (choice == FolderChoice.Include) editing.copy(
                            includedChatIds = if (checked) editing.includedChatIds + id else editing.includedChatIds - id,
                            excludedChatIds = if (checked) editing.excludedChatIds - id else editing.excludedChatIds
                        ) else editing.copy(
                            excludedChatIds = if (checked) editing.excludedChatIds + id else editing.excludedChatIds - id,
                            includedChatIds = if (checked) editing.includedChatIds - id else editing.includedChatIds
                        )
                    },
                    onDone = { picking = null }
                )
            }
            editing != null -> {
                FolderEditor(
                    draft = editing,
                    disabled = busy || uncertainCreateBaseline != null,
                    onUpdate = { draft = it; error = null },
                    onPickIncluded = { picking = FolderChoice.Include },
                    onPickExcluded = { picking = FolderChoice.Exclude },
                    onDelete = { confirmDelete = true },
                    onSave = {
                        if (!editing.valid()) {
                            error = context.getString(R.string.folder_validation_error)
                        } else {
                            scope.launch {
                                busy = true; error = null
                                try {
                                    saveFolder(editing).fold(
                                        { draft = null; uncertainCreateBaseline = null },
                                        {
                                            if (editing.id == null) {
                                                uncertainCreateBaseline = snapshot.folders.map { f -> f.id }.toSet()
                                                error = context.getString(R.string.folder_create_unconfirmed)
                                            } else {
                                                error = it.userMessage(context, R.string.feature_action_failed)
                                            }
                                        }
                                    )
                                } finally { busy = false }
                            }
                        }
                    }
                )
            }
            else -> {
                Text(
                    stringResource(R.string.folder_manage_hint),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    color = palette.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = { draft = TelegramFolderDraft(); error = null; uncertainCreateBaseline = null },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    enabled = !busy
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.folder_create))
                }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(snapshot.folders, key = { it.id }) { folder ->
                        Surface(shape = RoundedCornerShape(16.dp), color = palette.surface) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(folder.title, fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
                                    Text(stringResource(R.string.folder_server_sync),
                                        style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                                }
                                IconButton(enabled = !busy && snapshot.folders.firstOrNull()?.id != folder.id,
                                    onClick = {
                                        val ids = movedTelegramFolderIds(snapshot, folder.id, -1) ?: return@IconButton
                                        scope.launch {
                                            busy = true; error = null
                                            try {
                                                reorderFolders(ids, snapshot.mainChatListPosition).onFailure {
                                                    error = it.userMessage(context, R.string.feature_action_failed)
                                                }
                                            } finally { busy = false }
                                        }
                                    }) {
                                    Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.folder_move_up), tint = palette.textPrimary)
                                }
                                IconButton(enabled = !busy && snapshot.folders.lastOrNull()?.id != folder.id,
                                    onClick = {
                                        val ids = movedTelegramFolderIds(snapshot, folder.id, 1) ?: return@IconButton
                                        scope.launch {
                                            busy = true; error = null
                                            try {
                                                reorderFolders(ids, snapshot.mainChatListPosition).onFailure {
                                                    error = it.userMessage(context, R.string.feature_action_failed)
                                                }
                                            } finally { busy = false }
                                        }
                                    }) {
                                    Icon(Icons.Rounded.ArrowDownward, stringResource(R.string.folder_move_down), tint = palette.textPrimary)
                                }
                                IconButton(enabled = !busy, onClick = {
                                    scope.launch {
                                        busy = true; error = null
                                        try {
                                            loadFolder(folder.id).fold(
                                                { draft = it; uncertainCreateBaseline = null },
                                                { error = it.userMessage(context, R.string.feature_action_failed) }
                                            )
                                        } finally { busy = false }
                                    }
                                }) {
                                    Icon(Icons.Rounded.Edit, stringResource(R.string.folder_edit), tint = palette.accent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && draft?.id != null) {
        val id = requireNotNull(draft?.id)
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDelete = false },
            title = { Text(stringResource(R.string.folder_delete)) },
            text = { Text(stringResource(R.string.folder_delete_confirm)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true; error = null
                        try {
                            deleteFolder(id).fold(
                                { draft = null; confirmDelete = false },
                                { error = it.userMessage(context, R.string.feature_action_failed); confirmDelete = false }
                            )
                        } finally { busy = false }
                    }
                }) { Text(stringResource(R.string.folder_delete), color = palette.danger) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun FolderEditor(
    draft: TelegramFolderDraft,
    disabled: Boolean,
    onUpdate: (TelegramFolderDraft) -> Unit,
    onPickIncluded: () -> Unit,
    onPickExcluded: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit
) {
    val palette = beyGramPalette()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = draft.title,
            onValueChange = { value -> onUpdate(draft.copy(title = value)) },
            label = { Text(stringResource(R.string.folder_name)) },
            supportingText = { Text(stringResource(R.string.folder_name_hint)) },
            singleLine = true,
            enabled = !disabled,
            modifier = Modifier.fillMaxWidth()
        )
        if (draft.isShareable) Text(
            stringResource(R.string.folder_shareable_hint), color = palette.textSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Text(stringResource(R.string.folder_include_categories),
            style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
        FolderSwitch(stringResource(R.string.folder_contacts), draft.includeContacts, !disabled && !draft.isShareable) { onUpdate(draft.copy(includeContacts = it)) }
        FolderSwitch(stringResource(R.string.folder_non_contacts), draft.includeNonContacts, !disabled && !draft.isShareable) { onUpdate(draft.copy(includeNonContacts = it)) }
        FolderSwitch(stringResource(R.string.folder_bots), draft.includeBots, !disabled && !draft.isShareable) { onUpdate(draft.copy(includeBots = it)) }
        FolderSwitch(stringResource(R.string.folder_groups), draft.includeGroups, !disabled && !draft.isShareable) { onUpdate(draft.copy(includeGroups = it)) }
        FolderSwitch(stringResource(R.string.folder_channels), draft.includeChannels, !disabled && !draft.isShareable) { onUpdate(draft.copy(includeChannels = it)) }
        Text(stringResource(R.string.folder_exclusions), style = MaterialTheme.typography.titleMedium,
            color = palette.textPrimary, modifier = Modifier.padding(top = 12.dp))
        FolderSwitch(stringResource(R.string.folder_exclude_muted), draft.excludeMuted, !disabled && !draft.isShareable) { onUpdate(draft.copy(excludeMuted = it)) }
        FolderSwitch(stringResource(R.string.folder_exclude_read), draft.excludeRead, !disabled && !draft.isShareable) { onUpdate(draft.copy(excludeRead = it)) }
        FolderSwitch(stringResource(R.string.folder_exclude_archived), draft.excludeArchived, !disabled && !draft.isShareable) { onUpdate(draft.copy(excludeArchived = it)) }
        OutlinedButton(onClick = onPickIncluded, enabled = !disabled, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.folder_manual_include, draft.includedChatIds.size))
        }
        OutlinedButton(onClick = onPickExcluded, enabled = !disabled && !draft.isShareable, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.folder_manual_exclude, draft.excludedChatIds.size))
        }
        Text(stringResource(R.string.folder_manual_note), color = palette.textSecondary,
            style = MaterialTheme.typography.bodySmall)
        Button(onClick = onSave, enabled = !disabled, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.common_save))
        }
        if (draft.id != null) TextButton(onClick = onDelete, enabled = !disabled,
            modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.DeleteOutline, null, tint = palette.danger)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.folder_delete), color = palette.danger)
        }
    }
}

@Composable
private fun FolderSwitch(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun FolderChatPicker(
    chats: List<ChatPreview>,
    selected: Set<Long>,
    onToggle: (Long, Boolean) -> Unit,
    onDone: () -> Unit
) {
    val palette = beyGramPalette()
    var query by remember { mutableStateOf("") }
    val visible = remember(chats, query) {
        chats.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.folder_picker_hint), color = palette.textSecondary,
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = query, onValueChange = { query = it },
            label = { Text(stringResource(R.string.share_search)) },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.folder_picker_done))
        }
        LazyColumn(Modifier.weight(1f)) {
            items(visible, key = { it.id }) { chat ->
                Row(Modifier.fillMaxWidth().clickable { onToggle(chat.id, chat.id !in selected) }
                    .padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f), color = palette.textPrimary)
                    Checkbox(checked = chat.id in selected,
                        onCheckedChange = { onToggle(chat.id, it) })
                }
            }
        }
    }
}
