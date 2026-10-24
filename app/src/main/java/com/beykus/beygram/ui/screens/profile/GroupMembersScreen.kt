package com.beykus.beygram.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.SearchField
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chats.filterNewChatContacts
import com.beykus.beygram.ui.screens.chats.toggledGroupMembers
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val MEMBERS_PAGE = 50
private const val MEMBERS_PREFETCH = 8

/** Действие, требующее подтверждения. */
private data class PendingMemberAction(val action: GroupMemberAction, val member: GroupMemberEntry)

/**
 * Участники с ролями и действиями. Список и поиск идут через TDLib, поэтому бан, повышение и
 * ограничения всегда применяются к актуальному статусу. Недоступные роли действия не показываются.
 */
@Composable
internal fun GroupMembersScreen(
    chatId: Long,
    info: GroupManagementInfo,
    repository: TelegramRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onOpenUser: (Long) -> Unit,
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rights = info.rights
    var adding by rememberSaveable(chatId) { mutableStateOf(false) }
    var filter by rememberSaveable(chatId) { mutableStateOf(GroupMemberFilter.Recent) }
    var query by rememberSaveable(chatId) { mutableStateOf("") }
    var members by remember(chatId) { mutableStateOf<List<GroupMemberEntry>>(emptyList()) }
    var total by remember(chatId) { mutableStateOf<Int?>(null) }
    var nextOffset by remember(chatId) { mutableIntStateOf(0) }
    var loading by remember(chatId) { mutableStateOf(true) }
    var loadingMore by remember(chatId) { mutableStateOf(false) }
    var error by remember(chatId) { mutableStateOf<String?>(null) }
    var notice by remember(chatId) { mutableStateOf<String?>(null) }
    var actionError by remember(chatId) { mutableStateOf<String?>(null) }
    var reloadToken by remember(chatId) { mutableIntStateOf(0) }
    var actionsFor by remember(chatId) { mutableStateOf<GroupMemberEntry?>(null) }
    var pending by remember(chatId) { mutableStateOf<PendingMemberAction?>(null) }
    var promoting by remember(chatId) { mutableStateOf<GroupMemberEntry?>(null) }
    var busy by remember(chatId) { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val filters = buildList {
        add(GroupMemberFilter.Recent)
        add(GroupMemberFilter.Administrators)
        if (info.kind == ManagedChatKind.Supergroup) {
            add(GroupMemberFilter.Restricted)
            add(GroupMemberFilter.Banned)
        } else if (info.kind == ManagedChatKind.Channel) {
            add(GroupMemberFilter.Banned)
        }
    }

    fun reload() { reloadToken++ }

    LaunchedEffect(chatId, filter, query, reloadToken) {
        loading = true
        error = null
        if (query.isNotBlank()) delay(250)
        repository.loadGroupMembers(chatId, filter, query, 0, MEMBERS_PAGE)
            .onSuccess { page ->
                members = page.members
                nextOffset = page.fetched
                total = page.total
            }
            .onFailure { error = it.userMessage(context, R.string.group_members_load_failed) }
        loading = false
    }

    fun loadMore() {
        if (loading || loadingMore || query.isNotBlank()) return
        val knownTotal = total ?: return
        if (nextOffset >= knownTotal) return
        loadingMore = true
        scope.launch {
            repository.loadGroupMembers(chatId, filter, query, nextOffset, MEMBERS_PAGE)
                .onSuccess { page ->
                    members = members + page.members
                    nextOffset += page.fetched
                    // Пустая страница означает конец списка, даже если TDLib назвал больше.
                    if (page.fetched == 0) total = nextOffset
                }
                .onFailure { error = it.userMessage(context, R.string.group_members_load_failed) }
            loadingMore = false
        }
    }

    LaunchedEffect(listState, members.size, query, filter) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { last ->
            if (last != null && last >= members.size - MEMBERS_PREFETCH) loadMore()
        }
    }

    fun applyAction(action: GroupMemberAction, member: GroupMemberEntry, adminRights: GroupAdministratorRights? = null) {
        scope.launch {
            busy = true
            actionError = null
            val result = when (action) {
                GroupMemberAction.Ban -> repository.banGroupMember(chatId, member.userId)
                GroupMemberAction.Unban -> repository.unbanGroupMember(chatId, member.userId)
                GroupMemberAction.Restrict -> repository.restrictGroupMember(chatId, member.userId)
                GroupMemberAction.Unrestrict -> repository.unrestrictGroupMember(chatId, member.userId)
                GroupMemberAction.Demote -> repository.demoteGroupMember(chatId, member.userId)
                GroupMemberAction.Promote, GroupMemberAction.EditRights ->
                    repository.setGroupMemberRights(chatId, member.userId, adminRights ?: defaultAdministratorRights(info.kind))
            }
            busy = false
            result.fold(
                onSuccess = {
                    notice = context.getString(action.successMessage(info.kind))
                    actionsFor = null
                    pending = null
                    promoting = null
                    reload()
                    onChanged()
                },
                onFailure = {
                    actionError = it.userMessage(context, R.string.err_group_action_failed)
                    pending = null
                    promoting = null
                }
            )
        }
    }

    if (adding) {
        GroupAddMembersScreen(
            loadContacts = repository::loadContacts,
            add = { ids -> repository.addGroupMembers(chatId, ids) },
            observeFile = observeFile,
            downloadFile = downloadFile,
            onBack = { if (!busy) adding = false },
            onAdded = { addition ->
                adding = false
                busy = false
                notice = when {
                    addition.addedUserIds.isNotEmpty() && addition.failedUserIds.isNotEmpty() ->
                        context.getString(R.string.group_members_added, addition.addedUserIds.size) + " · " +
                            context.getString(R.string.group_members_add_failed, addition.failedUserIds.size)
                    addition.addedUserIds.isNotEmpty() -> context.getString(R.string.group_members_added, addition.addedUserIds.size)
                    addition.failedUserIds.isNotEmpty() -> context.getString(R.string.group_members_add_failed, addition.failedUserIds.size)
                    else -> context.getString(R.string.group_members_add_none)
                }
                if (addition.premiumRequiredUserIds.isNotEmpty()) {
                    notice = listOfNotNull(notice, context.getString(R.string.group_members_add_premium)).joinToString("\n")
                }
                reload()
                onChanged()
            }
        )
        return
    }

    BackHandler(enabled = !busy) { onBack() }

    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.group_members_title), { if (!busy) onBack() }) {
            IconButton(onClick = { reload() }, enabled = !loading) {
                Icon(Icons.Rounded.Refresh, stringResource(R.string.group_manage_refresh), tint = palette.textPrimary)
            }
            if (showAddMembers(rights)) IconButton(onClick = { adding = true }, enabled = !busy) {
                Icon(Icons.Rounded.PersonAdd, stringResource(R.string.group_members_add), tint = palette.textPrimary)
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { value ->
                FilterChip(
                    selected = filter == value,
                    onClick = { filter = value; query = "" },
                    label = { Text(stringResource(value.filterLabel()), fontSize = 13.sp) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }
        SearchField(query = query, onChange = { query = it }, placeholder = stringResource(R.string.group_members_search_placeholder))
        total?.let { count ->
            Text(
                stringResource(R.string.group_members_total, count),
                color = palette.textSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        GroupManagementMessage(error ?: actionError, error = true)
        GroupManagementMessage(notice)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                members.isEmpty() && !loading -> Text(
                    stringResource(if (query.isNotBlank()) R.string.group_members_empty_search else R.string.group_members_empty),
                    color = palette.textSecondary,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp).clip(RoundedCornerShape(20.dp)).background(palette.surface),
                    state = listState, contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(members, key = { _, member -> member.userId }) { index, member ->
                        if (index > 0) HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 72.dp))
                        GroupMemberRow(
                            member = member,
                            observeFile = observeFile,
                            downloadFile = downloadFile,
                            onOpenProfile = { onOpenUser(member.userId) },
                            onActions = { actionsFor = member }
                        )
                    }
                    if (loadingMore) item(key = "loading_more") {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = palette.accent, strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }

    actionsFor?.let { member ->
        val available = groupMemberActions(rights, member)
        AlertDialog(
            onDismissRequest = { if (!busy) actionsFor = null },
            title = { Text(member.name.ifBlank { stringResource(R.string.user_name_unknown) }) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(member.status.roleLabelRes(member.isAnonymous)),
                        color = palette.textSecondary, fontSize = 13.sp
                    )
                    if (member.status == GroupMemberStatus.Administrator) member.rights?.let { adminRights ->
                        Text(
                            adminRightsSummary(adminRights),
                            color = palette.textSecondary, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (available.isEmpty()) {
                        Text(
                            stringResource(R.string.group_members_no_actions),
                            color = palette.textSecondary, fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    available.forEach { action ->
                        TextButton(
                            onClick = {
                                actionsFor = null
                                actionError = null
                                if (action == GroupMemberAction.Promote || action == GroupMemberAction.EditRights) promoting = member
                                else pending = PendingMemberAction(action, member)
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text(
                                stringResource(action.label(info.kind)),
                                color = if (action == GroupMemberAction.Ban) palette.danger else palette.textPrimary,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(enabled = !busy, onClick = { actionsFor = null }) { Text(stringResource(R.string.common_close)) } }
        )
    }

    pending?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            title = { Text(stringResource(target.action.confirmTitle(target.action == GroupMemberAction.Ban && info.kind == ManagedChatKind.BasicGroup))) },
            text = {
                Column {
                    Text(stringResource(target.action.confirmWarning(target.action == GroupMemberAction.Ban && info.kind == ManagedChatKind.BasicGroup), target.member.name.ifBlank {
                        stringResource(R.string.user_name_unknown)
                    }))
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp), color = palette.accent)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = { applyAction(target.action, target.member) }) {
                    Text(
                        stringResource(target.action.confirmButton(info.kind)),
                        color = if (target.action == GroupMemberAction.Ban) palette.danger else palette.accentInk
                    )
                }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    promoting?.let { member ->
        PromoteMemberDialog(
            member = member,
            kind = info.kind,
            busy = busy,
            error = actionError,
            onDismiss = { if (!busy) { promoting = null; actionError = null } },
            onSave = { adminRights -> applyAction(GroupMemberAction.Promote, member, adminRights) }
        )
    }
}

/** Диалог прав: для обычной группы TDLib не поддерживает гранулярные права, поэтому там одно подтверждение. */
@Composable
private fun PromoteMemberDialog(
    member: GroupMemberEntry,
    kind: ManagedChatKind,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (GroupAdministratorRights) -> Unit
) {
    val palette = beyGramPalette()
    var rights by remember(member.userId) { mutableStateOf(member.rights ?: defaultAdministratorRights(kind)) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.group_members_promote_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(member.name.ifBlank { stringResource(R.string.user_name_unknown) }, color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                if (kind == ManagedChatKind.BasicGroup) {
                    Text(
                        stringResource(R.string.group_members_promote_basic_warning, member.name),
                        color = palette.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    val toggles = buildList {
                        add(Triple(R.string.group_members_right_delete, rights.canDeleteMessages) { value: Boolean -> rights = rights.copy(canDeleteMessages = value) })
                        if (kind == ManagedChatKind.Supergroup) {
                            add(Triple(R.string.group_members_right_ban, rights.canRestrictMembers) { value: Boolean -> rights = rights.copy(canRestrictMembers = value) })
                        }
                        add(Triple(R.string.group_members_right_invite, rights.canInviteUsers) { value: Boolean -> rights = rights.copy(canInviteUsers = value) })
                        add(Triple(R.string.group_members_right_pin, rights.canPinMessages) { value: Boolean -> rights = rights.copy(canPinMessages = value) })
                        add(Triple(R.string.group_members_right_change_info, rights.canChangeInfo) { value: Boolean -> rights = rights.copy(canChangeInfo = value) })
                        if (kind == ManagedChatKind.Channel) {
                            add(Triple(R.string.group_members_right_post, rights.canPostMessages) { value: Boolean -> rights = rights.copy(canPostMessages = value) })
                        }
                        add(Triple(R.string.group_members_right_video, rights.canManageVideoChats) { value: Boolean -> rights = rights.copy(canManageVideoChats = value) })
                        add(Triple(R.string.group_members_right_anonymous, rights.isAnonymous) { value: Boolean -> rights = rights.copy(isAnonymous = value) })
                    }
                    toggles.forEach { (label, checked, onChange) ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(label), color = palette.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Switch(checked = checked, onCheckedChange = null)
                        }
                    }
                }
                Text(stringResource(R.string.group_members_promote_hint), color = palette.textSecondary, fontSize = 12.sp)
                error?.let { Text(it, color = palette.danger, modifier = Modifier.padding(top = 8.dp).errorAnnouncement()) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = palette.accent)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { onSave(rights) }) {
                Text(stringResource(if (busy) R.string.common_saving else R.string.common_save))
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

/** Добавление участников из адресной книги: список контактов и явное подтверждение. */
@Composable
private fun GroupAddMembersScreen(
    loadContacts: suspend () -> Result<List<ContactPreview>>,
    add: suspend (List<Long>) -> Result<GroupMemberAddition>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onBack: () -> Unit,
    onAdded: (GroupMemberAddition) -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<ContactPreview>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(arrayListOf<Long>()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }
    val visible = remember(contacts, query) { filterNewChatContacts(contacts, query) }

    LaunchedEffect(reloadToken) {
        loading = true
        error = null
        loadContacts()
            .onSuccess { contacts = it }
            .onFailure { error = it.userMessage(context, R.string.contacts_load_failed) }
        loading = false
    }
    BackHandler(enabled = !busy, onBack = onBack)
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        ProfileToolbar(stringResource(R.string.group_members_add_title), { if (!busy) onBack() }) {
            TextButton(
                enabled = selected.isNotEmpty() && !busy && !loading,
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        add(selected.toList())
                            .onSuccess(onAdded)
                            .onFailure { error = it.userMessage(context, R.string.err_group_action_failed) }
                        busy = false
                    }
                }
            ) { Text(stringResource(if (busy) R.string.common_saving else R.string.group_members_add_action)) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        Text(
            stringResource(R.string.group_members_add_selected, selected.size),
            color = palette.textSecondary, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        SearchField(query = query, onChange = { query = it }, placeholder = stringResource(R.string.group_members_add_search))
        GroupManagementMessage(error, error = true)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        when {
            !loading && error != null && contacts.isEmpty() -> TextButton(onClick = { reloadToken++ }) {
                Text(stringResource(R.string.common_retry))
            }
            !loading && visible.isEmpty() -> Text(
                stringResource(if (contacts.isEmpty()) R.string.group_members_no_contacts else R.string.group_members_empty_search),
                color = palette.textSecondary,
                modifier = Modifier.padding(24.dp)
            )
            else -> LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(visible, key = { it.userId }) { contact ->
                    GroupContactRow(
                        contact = contact,
                        selected = contact.userId in selected,
                        enabled = !busy,
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        onClick = { selected = ArrayList(toggledGroupMembers(selected, contact.userId)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupContactRow(
    contact: ContactPreview,
    selected: Boolean,
    enabled: Boolean,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TelegramAvatar(contact.initials, Color(contact.avatarColorArgb), 44.dp, contact.photo, observeFile, downloadFile)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                contact.title.ifBlank { stringResource(R.string.user_name_unknown) },
                color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (contact.subtitle.isNotBlank()) Text(contact.subtitle, color = palette.textSecondary, fontSize = 13.sp, maxLines = 1)
        }
        if (selected) Icon(Icons.Rounded.Check, stringResource(R.string.new_chat_contact_selected), tint = palette.accent)
    }
}

private fun GroupMemberFilter.filterLabel(): Int = when (this) {
    GroupMemberFilter.Recent -> R.string.group_members_filter_all
    GroupMemberFilter.Administrators -> R.string.group_members_filter_admins
    GroupMemberFilter.Restricted -> R.string.group_members_filter_restricted
    GroupMemberFilter.Banned -> R.string.group_members_filter_banned
}

/** Короткая сводка прав администратора для листа действий; пустые права не занимают строку. */
@Composable
private fun adminRightsSummary(rights: GroupAdministratorRights): String = listOfNotNull(
    stringResource(R.string.group_members_right_delete).takeIf { rights.canDeleteMessages },
    stringResource(R.string.group_members_right_ban).takeIf { rights.canRestrictMembers },
    stringResource(R.string.group_members_right_invite).takeIf { rights.canInviteUsers },
    stringResource(R.string.group_members_right_pin).takeIf { rights.canPinMessages },
    stringResource(R.string.group_members_right_change_info).takeIf { rights.canChangeInfo },
    stringResource(R.string.group_members_right_post).takeIf { rights.canPostMessages },
    stringResource(R.string.group_members_right_anonymous).takeIf { rights.isAnonymous }
).joinToString(" · ")

private fun GroupMemberAction.label(kind: ManagedChatKind): Int = when (this) {
    GroupMemberAction.Promote -> R.string.group_members_action_promote
    GroupMemberAction.EditRights -> R.string.group_members_action_edit_rights
    GroupMemberAction.Demote -> R.string.group_members_action_demote
    GroupMemberAction.Restrict -> R.string.group_members_action_restrict
    GroupMemberAction.Unrestrict -> R.string.group_members_action_unrestrict
    GroupMemberAction.Ban -> if (kind == ManagedChatKind.BasicGroup) R.string.group_members_action_ban_basic else R.string.group_members_action_ban
    GroupMemberAction.Unban -> R.string.group_members_action_unban
}

private fun GroupMemberAction.confirmTitle(removingFromBasicGroup: Boolean): Int = when (this) {
    GroupMemberAction.Ban -> if (removingFromBasicGroup) R.string.group_members_remove_title else R.string.group_members_ban_title
    GroupMemberAction.Unban -> R.string.group_members_unban_title
    GroupMemberAction.Demote -> R.string.group_members_demote_title
    GroupMemberAction.Restrict -> R.string.group_members_restrict_title
    GroupMemberAction.Unrestrict -> R.string.group_members_unrestrict_title
    GroupMemberAction.Promote, GroupMemberAction.EditRights -> R.string.group_members_promote_title
}

private fun GroupMemberAction.confirmWarning(removingFromBasicGroup: Boolean): Int = when (this) {
    GroupMemberAction.Ban -> if (removingFromBasicGroup) R.string.group_members_remove_warning else R.string.group_members_ban_warning
    GroupMemberAction.Unban -> R.string.group_members_unban_warning
    GroupMemberAction.Demote -> R.string.group_members_demote_warning
    GroupMemberAction.Restrict -> R.string.group_members_restrict_warning
    GroupMemberAction.Unrestrict -> R.string.group_members_unrestrict_warning
    GroupMemberAction.Promote, GroupMemberAction.EditRights -> R.string.group_members_promote_hint
}

private fun GroupMemberAction.confirmButton(kind: ManagedChatKind): Int = when (this) {
    GroupMemberAction.Ban -> if (kind == ManagedChatKind.BasicGroup) R.string.group_members_action_ban_basic else R.string.group_members_action_ban
    GroupMemberAction.Unban -> R.string.group_members_action_unban
    GroupMemberAction.Demote -> R.string.group_members_action_demote
    GroupMemberAction.Restrict -> R.string.group_members_action_restrict
    GroupMemberAction.Unrestrict -> R.string.group_members_action_unrestrict
    GroupMemberAction.Promote, GroupMemberAction.EditRights -> R.string.common_save
}

private fun GroupMemberAction.successMessage(kind: ManagedChatKind): Int = when (this) {
    GroupMemberAction.Promote, GroupMemberAction.EditRights -> R.string.group_members_promoted
    GroupMemberAction.Demote -> R.string.group_members_demoted
    GroupMemberAction.Restrict -> R.string.group_members_restricted
    GroupMemberAction.Unrestrict -> R.string.group_members_unrestricted
    GroupMemberAction.Ban -> if (kind == ManagedChatKind.BasicGroup) R.string.group_members_removed else R.string.group_members_banned
    GroupMemberAction.Unban -> R.string.group_members_unbanned
}
