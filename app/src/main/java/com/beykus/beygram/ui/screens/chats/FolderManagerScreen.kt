package com.beykus.beygram.ui.screens.chats

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.TelegramChatFolder
import com.beykus.beygram.domain.model.TelegramChatFolders
import com.beykus.beygram.domain.model.TelegramFolderDraft
import com.beykus.beygram.domain.model.TelegramFolderProblem
import com.beykus.beygram.domain.model.movedTelegramFolderIds
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.BeyGramPopover
import com.beykus.beygram.ui.components.BeyGramPopoverItem
import com.beykus.beygram.ui.components.Button
import com.beykus.beygram.ui.components.OutlinedButton
import com.beykus.beygram.ui.components.Switch
import com.beykus.beygram.ui.components.TextButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.settingsIconTint
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassScreenBackground
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.screens.settings.SettingsGroup
import com.beykus.beygram.ui.screens.settings.SettingsRowDivider
import com.beykus.beygram.ui.screens.settings.SettingsRowInset
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

private enum class FolderChoice { Include, Exclude }

/** Where a folder row's divider starts: the 16 dp row padding, the 40 dp folder tile and its 14 dp gap. */
private val FolderRowDividerInset: Dp = 70.dp

// An unsaved folder survives rotation and language or theme changes.
private val FolderDraftSaver = Saver<TelegramFolderDraft?, Bundle>(
    save = { d ->
        d?.let {
            Bundle().apply {
                it.id?.let { id -> putInt("id", id) }
                putString("title", it.title)
                putBoolean("shareable", it.isShareable)
                putString("baseline", it.baselineFingerprint)
                putBooleanArray("flags", booleanArrayOf(
                    it.includeContacts, it.includeNonContacts, it.includeBots, it.includeGroups,
                    it.includeChannels, it.excludeMuted, it.excludeRead, it.excludeArchived
                ))
                putLongArray("pinned", it.pinnedChatIds.toLongArray())
                putLongArray("included", it.includedChatIds.toLongArray())
                putLongArray("excluded", it.excludedChatIds.toLongArray())
            }
        }
    },
    restore = { b ->
        val f = b.getBooleanArray("flags") ?: BooleanArray(8)
        TelegramFolderDraft(
            id = if (b.containsKey("id")) b.getInt("id") else null,
            title = b.getString("title").orEmpty(),
            isShareable = b.getBoolean("shareable"),
            baselineFingerprint = b.getString("baseline"),
            includeContacts = f[0], includeNonContacts = f[1], includeBots = f[2], includeGroups = f[3],
            includeChannels = f[4], excludeMuted = f[5], excludeRead = f[6], excludeArchived = f[7],
            pinnedChatIds = b.getLongArray("pinned")?.toList().orEmpty(),
            includedChatIds = b.getLongArray("included")?.toSet().orEmpty(),
            excludedChatIds = b.getLongArray("excluded")?.toSet().orEmpty()
        )
    }
)

private val FolderIdsSaver = Saver<Set<Int>?, IntArray>(
    save = { it?.toIntArray() },
    restore = { it.toSet() }
)

/**
 * Server folders of the account. The list shows each folder as one row: a tap edits it, and the row menu moves it.
 * The editor groups the rules, shows a summary and names every reason a folder cannot be saved yet. A save or a
 * delete that gets no answer is never sent again on its own: the screen says so and waits for the user.
 */
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
    val design = LocalDesignSystem.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(stateSaver = FolderDraftSaver) { mutableStateOf<TelegramFolderDraft?>(null) }
    var picking by rememberSaveable { mutableStateOf<FolderChoice?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var uncertainCreateBaseline by rememberSaveable(stateSaver = FolderIdsSaver) { mutableStateOf<Set<Int>?>(null) }
    // A save that timed out may still have reached the server. The editor stays locked until the server copy is checked.
    var uncertainEdit by rememberSaveable { mutableStateOf(false) }

    fun discardDraft() {
        draft = null
        picking = null
        error = null
        uncertainCreateBaseline = null
        uncertainEdit = false
    }

    fun openDraft(next: TelegramFolderDraft) {
        draft = next
        picking = null
        error = null
        uncertainCreateBaseline = null
        uncertainEdit = false
    }

    fun back() {
        when {
            busy -> Unit
            picking != null -> picking = null
            draft != null -> discardDraft()
            else -> onBack()
        }
    }
    BackHandler(enabled = !com.beykus.beygram.LocalMainTabsCovered.current, onBack = ::back)

    // A timed-out create may already have succeeded. Reconcile a NEW server folder ID;
    // don't resubmit the same ID-less draft while its result is uncertain.
    LaunchedEffect(snapshot.folders, uncertainCreateBaseline) {
        val before = uncertainCreateBaseline ?: return@LaunchedEffect
        val name = draft?.title?.trim() ?: return@LaunchedEffect
        if (snapshot.folders.any { it.id !in before && it.title == name }) discardDraft()
    }

    fun openFolder(folder: TelegramChatFolder) {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                loadFolder(folder.id).fold(
                    { loaded -> openDraft(loaded) },
                    { failure -> error = failure.userMessage(context, R.string.feature_action_failed) }
                )
            } finally {
                busy = false
            }
        }
    }

    fun moveFolder(folderId: Int, delta: Int) {
        if (busy) return
        // The main-list position is sent unchanged: only the order of the folders themselves moves.
        val ids = movedTelegramFolderIds(snapshot, folderId, delta) ?: return
        scope.launch {
            busy = true
            error = null
            try {
                reorderFolders(ids, snapshot.mainChatListPosition).onFailure { failure ->
                    error = failure.userMessage(context, R.string.feature_action_failed)
                }
            } finally {
                busy = false
            }
        }
    }

    fun saveFailed(editing: TelegramFolderDraft, failure: Throwable) {
        val timedOut = failure.cause is TimeoutCancellationException
        if (editing.id == null) {
            // Only a timeout leaves the create outcome unknown: the draft stays locked until the folder shows up.
            if (timedOut) return
            uncertainCreateBaseline = null
            error = failure.userMessage(context, R.string.feature_action_failed)
        } else if (timedOut) {
            // The edit may already be on the server, so it is not sent again: the server copy is checked first.
            uncertainEdit = true
        } else {
            error = failure.userMessage(context, R.string.feature_action_failed)
        }
    }

    fun saveDraft(editing: TelegramFolderDraft) {
        if (busy || editing.problems().isNotEmpty()) return
        // A create sent before a rotation may still succeed: its baseline keeps the restored draft locked until the
        // result is known or the folder appears.
        if (editing.id == null) uncertainCreateBaseline = snapshot.folders.map { it.id }.toSet()
        scope.launch {
            busy = true
            error = null
            try {
                saveFolder(editing).fold(
                    { discardDraft() },
                    { failure -> saveFailed(editing, failure) }
                )
            } finally {
                busy = false
            }
        }
    }

    fun uncertainChecked(editing: TelegramFolderDraft, fresh: TelegramFolderDraft) {
        when {
            // The server already holds the rules that were saved, so the edit went through.
            sameRules(fresh, editing) -> discardDraft()
            // The server still holds the rules the draft started from: nothing was applied, saving again is safe.
            fresh.baselineFingerprint == editing.baselineFingerprint -> {
                uncertainEdit = false
                error = context.getString(R.string.folder_save_not_applied)
            }
            // Another device changed the folder meanwhile. The draft stays; the fingerprint refuses a blind save.
            else -> {
                uncertainEdit = false
                error = context.getString(R.string.folder_changed_while_editing)
            }
        }
    }

    fun checkUncertainEdit(editing: TelegramFolderDraft) {
        val id = editing.id ?: return
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                loadFolder(id).fold(
                    { fresh -> uncertainChecked(editing, fresh) },
                    { failure -> error = failure.userMessage(context, R.string.feature_action_failed) }
                )
            } finally {
                busy = false
            }
        }
    }

    fun deleteFailed(failure: Throwable) {
        error = if (failure.cause is TimeoutCancellationException) {
            context.getString(R.string.folder_delete_unconfirmed)
        } else {
            failure.userMessage(context, R.string.feature_action_failed)
        }
    }

    fun deleteDraft(id: Int) {
        if (busy) return
        confirmDelete = false
        scope.launch {
            busy = true
            error = null
            try {
                deleteFolder(id).fold(
                    { discardDraft() },
                    { failure -> deleteFailed(failure) }
                )
            } finally {
                busy = false
            }
        }
    }

    val editing = draft
    val choice = picking
    val toolbarTitle = when {
        editing != null && choice != null -> stringResource(
            if (choice == FolderChoice.Include) R.string.folder_picker_include_title else R.string.folder_picker_exclude_title
        )
        editing != null -> stringResource(if (editing.id == null) R.string.folder_create else R.string.folder_edit)
        else -> stringResource(R.string.folder_manage_title)
    }

    Column(
        Modifier.fillMaxSize()
            .glassScreenBackground(design, palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
    ) {
        ProfileToolbar(toolbarTitle, ::back)
        // The bar always keeps its height, so it appears and disappears without moving the content.
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        }
        when {
            editing != null && choice != null -> FolderChatPicker(
                chats = knownChats,
                selected = if (choice == FolderChoice.Include) editing.memberChatIds else editing.excludedChatIds,
                enabled = !busy,
                onToggle = { id, checked ->
                    draft = draft?.let { current -> toggleChat(current, choice, id, checked) }
                },
                onDone = { picking = null }
            )
            editing != null -> FolderEditor(
                draft = editing,
                problems = editing.problems(),
                busy = busy,
                uncertainCreate = uncertainCreateBaseline != null,
                uncertainEdit = uncertainEdit,
                error = error,
                onUpdate = { updated ->
                    draft = updated
                    error = null
                },
                onPickIncluded = { picking = FolderChoice.Include },
                onPickExcluded = { picking = FolderChoice.Exclude },
                onDelete = { confirmDelete = true },
                onSave = { saveDraft(editing) },
                onCheck = { checkUncertainEdit(editing) }
            )
            else -> FolderList(
                snapshot = snapshot,
                busy = busy,
                error = error,
                onCreate = { openDraft(TelegramFolderDraft()) },
                onOpen = { folder -> openFolder(folder) },
                onMove = { folderId, delta -> moveFolder(folderId, delta) }
            )
        }
    }

    val pendingDelete = draft?.takeIf { confirmDelete && it.id != null }
    if (pendingDelete != null) {
        val id = requireNotNull(pendingDelete.id)
        BeyGramDialog(
            onDismissRequest = { if (!busy) confirmDelete = false },
            title = stringResource(R.string.folder_delete),
            message = stringResource(R.string.folder_delete_confirm, pendingDelete.title.trim()),
            confirmText = stringResource(R.string.folder_delete),
            confirmEnabled = !busy,
            destructive = true,
            onConfirm = { deleteDraft(id) },
            dismissText = stringResource(R.string.common_cancel),
            dismissEnabled = !busy,
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun FolderList(
    snapshot: TelegramChatFolders,
    busy: Boolean,
    error: String?,
    onCreate: () -> Unit,
    onOpen: (TelegramChatFolder) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    val palette = beyGramPalette()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Text(
                stringResource(R.string.folder_manage_hint),
                color = palette.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
            )
        }
        error?.let { message ->
            item { FolderNotice(message, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
        }
        item {
            Button(
                onClick = onCreate,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.folder_create))
            }
        }
        if (!snapshot.initialized) {
            item {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.accent)
                }
            }
        } else if (snapshot.folders.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.folder_empty),
                    color = palette.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp)
                )
            }
        } else {
            item {
                SettingsGroup(stringResource(R.string.folder_list_title)) {
                    snapshot.folders.forEachIndexed { index, folder ->
                        if (index > 0) SettingsRowDivider(startInset = FolderRowDividerInset)
                        FolderRow(
                            folder = folder,
                            enabled = !busy,
                            canMoveUp = index > 0,
                            canMoveDown = index < snapshot.folders.lastIndex,
                            onOpen = { onOpen(folder) },
                            onMoveUp = { onMove(folder.id, -1) },
                            onMoveDown = { onMove(folder.id, 1) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * One folder: the name and its unread count, a tap to edit, and one menu for moving it. The tap target and the menu
 * button are separate, so TalkBack reads them as two controls.
 */
@Composable
private fun FolderRow(
    folder: TelegramChatFolder,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val palette = beyGramPalette()
    var menuOpen by remember { mutableStateOf(false) }
    val unread = folder.unreadChats ?: 0
    val subtitle = if (unread > 0) {
        stringResource(R.string.folder_unread_chats, unread)
    } else {
        stringResource(R.string.folder_no_unread)
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.folder_edit),
                    onClick = onOpen
                )
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).background(palette.accentSoft, styledShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = settingsIconTint(),
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    folder.title,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(subtitle, color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        Box {
            IconButton(enabled = enabled, onClick = { menuOpen = true }) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.folder_row_actions, folder.title),
                    tint = palette.textSecondary
                )
            }
            BeyGramPopover(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                BeyGramPopoverItem(
                    text = stringResource(R.string.folder_move_up),
                    enabled = enabled && canMoveUp,
                    leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null, tint = palette.textSecondary) },
                    onClick = {
                        menuOpen = false
                        onMoveUp()
                    }
                )
                BeyGramPopoverItem(
                    text = stringResource(R.string.folder_move_down),
                    enabled = enabled && canMoveDown,
                    leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null, tint = palette.textSecondary) },
                    onClick = {
                        menuOpen = false
                        onMoveDown()
                    }
                )
            }
        }
    }
}

@Composable
private fun FolderEditor(
    draft: TelegramFolderDraft,
    problems: List<TelegramFolderProblem>,
    busy: Boolean,
    uncertainCreate: Boolean,
    uncertainEdit: Boolean,
    error: String?,
    onUpdate: (TelegramFolderDraft) -> Unit,
    onPickIncluded: () -> Unit,
    onPickExcluded: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    onCheck: () -> Unit
) {
    val palette = beyGramPalette()
    val editable = !busy && !uncertainCreate && !uncertainEdit
    val shared = draft.isShareable
    // A shared folder cannot gain category or exclusion rules, but a rule it already has can still be switched off.
    fun ruleEnabled(on: Boolean): Boolean = editable && (!shared || on)
    val nameInvalid = TelegramFolderProblem.EmptyName in problems
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = draft.title,
            onValueChange = { value -> onUpdate(draft.copy(title = value)) },
            label = { Text(stringResource(R.string.folder_name)) },
            supportingText = {
                Text(stringResource(if (nameInvalid) R.string.folder_error_name else R.string.folder_name_hint))
            },
            isError = nameInvalid,
            singleLine = true,
            enabled = editable,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        if (shared) FolderNote(stringResource(R.string.folder_shareable_hint))
        FolderToggleGroup(
            title = stringResource(R.string.folder_include_categories),
            rows = listOf(
                FolderToggle(stringResource(R.string.folder_contacts), draft.includeContacts, ruleEnabled(draft.includeContacts)) {
                    onUpdate(draft.copy(includeContacts = it))
                },
                FolderToggle(stringResource(R.string.folder_non_contacts), draft.includeNonContacts, ruleEnabled(draft.includeNonContacts)) {
                    onUpdate(draft.copy(includeNonContacts = it))
                },
                FolderToggle(stringResource(R.string.folder_bots), draft.includeBots, ruleEnabled(draft.includeBots)) {
                    onUpdate(draft.copy(includeBots = it))
                },
                FolderToggle(stringResource(R.string.folder_groups), draft.includeGroups, ruleEnabled(draft.includeGroups)) {
                    onUpdate(draft.copy(includeGroups = it))
                },
                FolderToggle(stringResource(R.string.folder_channels), draft.includeChannels, ruleEnabled(draft.includeChannels)) {
                    onUpdate(draft.copy(includeChannels = it))
                }
            )
        )
        FolderToggleGroup(
            title = stringResource(R.string.folder_exclusions),
            rows = listOf(
                FolderToggle(stringResource(R.string.folder_exclude_muted), draft.excludeMuted, ruleEnabled(draft.excludeMuted)) {
                    onUpdate(draft.copy(excludeMuted = it))
                },
                FolderToggle(stringResource(R.string.folder_exclude_read), draft.excludeRead, ruleEnabled(draft.excludeRead)) {
                    onUpdate(draft.copy(excludeRead = it))
                },
                FolderToggle(stringResource(R.string.folder_exclude_archived), draft.excludeArchived, ruleEnabled(draft.excludeArchived)) {
                    onUpdate(draft.copy(excludeArchived = it))
                }
            )
        )
        SettingsGroup(stringResource(R.string.folder_manual_title)) {
            FolderNavRow(
                label = stringResource(R.string.folder_manual_include, draft.memberChatIds.size),
                enabled = editable,
                onClick = onPickIncluded
            )
            SettingsRowDivider(startInset = SettingsRowInset)
            FolderNavRow(
                label = stringResource(R.string.folder_manual_exclude, draft.excludedChatIds.size),
                enabled = editable && (!shared || draft.excludedChatIds.isNotEmpty()),
                onClick = onPickExcluded
            )
            FolderNote(stringResource(R.string.folder_manual_note))
        }
        SettingsGroup(stringResource(R.string.folder_summary_title)) {
            FolderSummary(draft = draft, problems = problems)
        }
        val inset = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        if (uncertainCreate) FolderNotice(stringResource(R.string.folder_create_unconfirmed), inset)
        if (uncertainEdit) {
            FolderNotice(stringResource(R.string.folder_save_unconfirmed), inset)
            OutlinedButton(onClick = onCheck, enabled = !busy, modifier = inset) {
                Text(stringResource(R.string.folder_check))
            }
        }
        error?.let { FolderNotice(it, inset) }
        // Save stays off while a reason is listed above, so a tap never fails silently.
        Button(onClick = onSave, enabled = editable && problems.isEmpty(), modifier = inset) {
            Text(stringResource(R.string.common_save))
        }
        if (draft.id != null) TextButton(onClick = onDelete, enabled = editable, modifier = inset) {
            Icon(Icons.Rounded.DeleteOutline, null, tint = palette.danger)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.folder_delete), color = palette.danger)
        }
    }
}

/** One switch of a rules group. [enabled] is false while the rule is locked for this folder. */
private class FolderToggle(val label: String, val checked: Boolean, val enabled: Boolean, val onChange: (Boolean) -> Unit)

@Composable
private fun FolderToggleGroup(title: String, rows: List<FolderToggle>) {
    SettingsGroup(title) {
        rows.forEachIndexed { index, row ->
            if (index > 0) SettingsRowDivider(startInset = SettingsRowInset)
            FolderSwitchRow(row)
        }
    }
}

/** A switch row that is one toggle target for TalkBack, with its on or off state announced. */
@Composable
private fun FolderSwitchRow(row: FolderToggle) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = row.checked, enabled = row.enabled, role = Role.Switch, onValueChange = row.onChange)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            row.label,
            color = if (row.enabled) palette.textPrimary else palette.textSecondary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = row.checked, onCheckedChange = null, enabled = row.enabled)
    }
}

/** A row that opens a list of chats: the label says how many are chosen, and the chevron shows it leads somewhere. */
@Composable
private fun FolderNavRow(label: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (enabled) palette.textPrimary else palette.textSecondary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.textSecondary)
    }
}

/** What the folder will show, in plain words, plus every reason it cannot be saved yet. */
@Composable
private fun FolderSummary(draft: TelegramFolderDraft, problems: List<TelegramFolderProblem>) {
    val palette = beyGramPalette()
    val includeLabels = listOfNotNull(
        stringResource(R.string.folder_contacts).takeIf { draft.includeContacts },
        stringResource(R.string.folder_non_contacts).takeIf { draft.includeNonContacts },
        stringResource(R.string.folder_bots).takeIf { draft.includeBots },
        stringResource(R.string.folder_groups).takeIf { draft.includeGroups },
        stringResource(R.string.folder_channels).takeIf { draft.includeChannels }
    )
    val excludeLabels = listOfNotNull(
        stringResource(R.string.folder_exclude_muted).takeIf { draft.excludeMuted },
        stringResource(R.string.folder_exclude_read).takeIf { draft.excludeRead },
        stringResource(R.string.folder_exclude_archived).takeIf { draft.excludeArchived }
    )
    val includeLine = when {
        includeLabels.isNotEmpty() -> stringResource(R.string.folder_summary_include, includeLabels.joinToString(", "))
        draft.memberChatIds.isNotEmpty() -> stringResource(R.string.folder_summary_include_manual)
        else -> stringResource(R.string.folder_summary_no_include)
    }
    // An empty name is already marked on the name field, so the summary lists only the other reasons.
    val summaryProblems = problems.filterNot { it == TelegramFolderProblem.EmptyName }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(includeLine, color = palette.textPrimary, style = MaterialTheme.typography.bodyMedium)
        if (excludeLabels.isNotEmpty()) {
            Text(
                stringResource(R.string.folder_summary_exclude, excludeLabels.joinToString(", ")),
                color = palette.textPrimary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (draft.memberChatIds.isNotEmpty() || draft.excludedChatIds.isNotEmpty()) {
            Text(
                stringResource(R.string.folder_summary_manual, draft.memberChatIds.size, draft.excludedChatIds.size),
                color = palette.textPrimary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        summaryProblems.forEach { problem ->
            Text(
                problemText(problem),
                color = palette.danger,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.errorAnnouncement()
            )
        }
    }
}

@Composable
private fun problemText(problem: TelegramFolderProblem): String = when (problem) {
    TelegramFolderProblem.EmptyName -> stringResource(R.string.folder_error_name)
    TelegramFolderProblem.NoSelection -> stringResource(R.string.folder_error_selection)
    TelegramFolderProblem.OverlappingChats -> stringResource(R.string.folder_error_overlap)
    TelegramFolderProblem.ShareableRestricted -> stringResource(R.string.folder_error_shareable)
}

@Composable
private fun FolderNote(text: String) {
    Text(
        text,
        color = beyGramPalette().textSecondary,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

/** A status that stays on screen until the user acts: an unconfirmed save or an error. */
@Composable
private fun FolderNotice(text: String, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    Surface(
        modifier = modifier.fillMaxWidth().errorAnnouncement(),
        shape = styledShape(16.dp),
        color = palette.warningSurface
    ) {
        Text(
            text,
            color = palette.warningText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun FolderChatPicker(
    chats: List<ChatPreview>,
    selected: Set<Long>,
    enabled: Boolean,
    onToggle: (Long, Boolean) -> Unit,
    onDone: () -> Unit
) {
    val palette = beyGramPalette()
    var query by remember { mutableStateOf("") }
    val visible = remember(chats, query) {
        chats.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            stringResource(R.string.folder_picker_hint),
            color = palette.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.share_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            items(visible, key = { it.id }) { chat ->
                val checked = chat.id in selected
                Row(
                    Modifier.fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .toggleable(
                            value = checked,
                            enabled = enabled,
                            role = Role.Checkbox,
                            onValueChange = { onToggle(chat.id, it) }
                        )
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        chat.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        color = if (enabled) palette.textPrimary else palette.textSecondary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                }
            }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.folder_picker_done))
        }
    }
}

/** Moves [id] into or out of the chosen list. A chat is never in both lists, and it leaves the pinned order when excluded. */
private fun toggleChat(current: TelegramFolderDraft, choice: FolderChoice, id: Long, checked: Boolean): TelegramFolderDraft =
    if (choice == FolderChoice.Include) {
        current.copy(
            includedChatIds = if (checked) current.includedChatIds + id else current.includedChatIds - id,
            pinnedChatIds = if (checked) current.pinnedChatIds else current.pinnedChatIds - id,
            excludedChatIds = if (checked) current.excludedChatIds - id else current.excludedChatIds
        )
    } else {
        current.copy(
            excludedChatIds = if (checked) current.excludedChatIds + id else current.excludedChatIds - id,
            includedChatIds = if (checked) current.includedChatIds - id else current.includedChatIds,
            pinnedChatIds = if (checked) current.pinnedChatIds - id else current.pinnedChatIds
        )
    }

/** Whether two drafts hold the same user-visible rules. The pinned order and the server fingerprint are not compared. */
private fun sameRules(a: TelegramFolderDraft, b: TelegramFolderDraft): Boolean =
    a.title.trim() == b.title.trim() &&
        a.isShareable == b.isShareable &&
        a.includeContacts == b.includeContacts && a.includeNonContacts == b.includeNonContacts &&
        a.includeBots == b.includeBots && a.includeGroups == b.includeGroups && a.includeChannels == b.includeChannels &&
        a.excludeMuted == b.excludeMuted && a.excludeRead == b.excludeRead && a.excludeArchived == b.excludeArchived &&
        a.memberChatIds == b.memberChatIds && a.excludedChatIds == b.excludedChatIds
