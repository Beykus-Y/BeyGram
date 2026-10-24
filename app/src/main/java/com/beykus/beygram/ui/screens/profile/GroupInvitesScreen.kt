package com.beykus.beygram.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.beyGramTextFieldColors
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.launch

/** Ссылка, которую нельзя редактировать через TDLib; отозвать её можно. */
private data class InviteEditor(val link: GroupInviteLink?)

/**
 * Список ссылок-приглашений. Основная ссылка показывается всегда, дополнительные приходят из
 * TDLib; у каждой есть копирование, отправка и (если роль позволяет) изменение и отзыв.
 */
@Composable
internal fun GroupInvitesScreen(
    chatId: Long,
    info: GroupManagementInfo,
    repository: TelegramRepository,
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rights = info.rights
    var links by remember(chatId) { mutableStateOf<List<GroupInviteLink>>(emptyList()) }
    var loading by remember(chatId) { mutableStateOf(true) }
    var error by remember(chatId) { mutableStateOf<String?>(null) }
    var notice by remember(chatId) { mutableStateOf<String?>(null) }
    var reloadToken by remember(chatId) { mutableIntStateOf(0) }
    var editor by remember(chatId) { mutableStateOf<InviteEditor?>(null) }
    var dialogBusy by remember(chatId) { mutableStateOf(false) }
    var dialogError by remember(chatId) { mutableStateOf<String?>(null) }
    var revoking by remember(chatId) { mutableStateOf<GroupInviteLink?>(null) }
    var revokeBusy by remember(chatId) { mutableStateOf(false) }

    fun reload() { reloadToken++ }

    LaunchedEffect(chatId, reloadToken) {
        loading = true
        error = null
        repository.loadGroupInviteLinks(chatId)
            .onSuccess { links = it }
            .onFailure { error = it.userMessage(context, R.string.group_invites_load_failed) }
        loading = false
    }

    // Основная ссылка может отсутствовать в списке TDLib, но о ней знает полная информация чата.
    val shown = remember(links, info.primaryInviteLink) {
        if (info.primaryInviteLink.isNotBlank() && links.none { it.isPrimary }) {
            listOf(GroupInviteLink(link = info.primaryInviteLink, isPrimary = true)) + links
        } else links
    }

    BackHandler { if (!dialogBusy && !revokeBusy) onBack() }
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.group_invites_title), { if (!dialogBusy && !revokeBusy) onBack() }) {
            IconButton(onClick = ::reload, enabled = !loading) {
                Icon(Icons.Rounded.Refresh, stringResource(R.string.group_manage_refresh), tint = palette.textPrimary)
            }
            if (rights.canManageInviteLinks) IconButton(onClick = { dialogError = null; editor = InviteEditor(null) }, enabled = !dialogBusy) {
                Icon(Icons.Rounded.AddLink, stringResource(R.string.group_invites_create), tint = palette.textPrimary)
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        GroupManagementMessage(error, error = true)
        GroupManagementMessage(notice)
        if (error != null && links.isEmpty() && !loading) TextButton(
            onClick = ::reload,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) { Text(stringResource(R.string.common_retry)) }
        if (shown.isEmpty() && !loading && error == null) {
            Text(
                stringResource(R.string.group_invites_empty),
                color = palette.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(24.dp)
            )
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(shown, key = { it.link }) { link ->
                    val permissions = groupInviteLinkPermissions(rights, link)
                    GroupInviteLinkCard(
                        link = link,
                        canEdit = permissions.canEdit,
                        canRevoke = permissions.canRevoke,
                        onCopy = {
                            copyInviteLink(context, link.link)
                            notice = context.getString(R.string.group_invites_copied)
                        },
                        onShare = {
                            val text = context.getString(R.string.group_invites_share_text, info.title, link.link)
                            shareInviteLink(context, text)
                        },
                        onEdit = { dialogError = null; editor = InviteEditor(link) },
                        onRevoke = { revoking = link }
                    )
                }
            }
        }
    }

    editor?.let { current ->
        GroupInviteLinkDialog(
            existing = current.link,
            busy = dialogBusy,
            error = dialogError,
            onDismiss = { if (!dialogBusy) { editor = null; dialogError = null } },
            onSave = { draft ->
                scope.launch {
                    dialogBusy = true
                    dialogError = null
                    val result = if (current.link == null) {
                        repository.createGroupInviteLink(chatId, draft)
                    } else {
                        repository.editGroupInviteLink(chatId, current.link.link, draft)
                    }
                    dialogBusy = false
                    result.fold(
                        onSuccess = {
                            notice = context.getString(if (current.link == null) R.string.group_invites_created else R.string.group_invites_updated)
                            editor = null
                            reload()
                            onChanged()
                        },
                        onFailure = { dialogError = it.userMessage(context, R.string.group_invites_save_failed) }
                    )
                }
            }
        )
    }

    revoking?.let { link ->
        AlertDialog(
            onDismissRequest = { if (!revokeBusy) revoking = null },
            title = { Text(stringResource(R.string.group_invites_revoke_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.group_invites_revoke_warning))
                    if (revokeBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp), color = palette.accent)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !revokeBusy,
                    onClick = {
                        scope.launch {
                            revokeBusy = true
                            repository.revokeGroupInviteLink(chatId, link.link)
                                .onSuccess {
                                    notice = context.getString(R.string.group_invites_revoked)
                                    revoking = null
                                    reload()
                                    onChanged()
                                }
                                .onFailure {
                                    error = it.userMessage(context, R.string.err_group_action_failed)
                                    revoking = null
                                }
                            revokeBusy = false
                        }
                    }
                ) { Text(stringResource(R.string.group_invites_revoke), color = palette.danger) }
            },
            dismissButton = { TextButton(enabled = !revokeBusy, onClick = { revoking = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

/** Карточка ссылки: адрес, условия и действия. Основную ссылку TDLib не разрешает редактировать. */
@Composable
private fun GroupInviteLinkCard(
    link: GroupInviteLink,
    canEdit: Boolean,
    canRevoke: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onRevoke: () -> Unit
) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().background(palette.surface, RoundedCornerShape(18.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        link.name.isNotBlank() -> link.name
                        link.isPrimary -> stringResource(R.string.group_invites_primary)
                        else -> stringResource(R.string.group_invites_default_name)
                    },
                    color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                )
                if (link.requiresApproval) Text(
                    stringResource(R.string.group_invites_approval),
                    color = palette.accentInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
                if (link.isSubscription) Text(
                    stringResource(R.string.group_invites_subscription, link.subscriptionStarCount),
                    color = palette.accentInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, stringResource(R.string.group_invites_copy), tint = palette.textSecondary) }
            IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, stringResource(R.string.group_invites_share), tint = palette.textSecondary) }
        }
        SelectionContainer {
            Text(link.link, color = palette.accentInk, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (link.expiresAtSeconds > 0) stringResource(R.string.group_invites_expires, formatGroupDate(link.expiresAtSeconds))
            else stringResource(R.string.group_invites_never_expires),
            color = palette.textSecondary, fontSize = 12.sp
        )
        if (link.memberLimit > 0) {
            Text(
                stringResource(R.string.group_invites_limit_used, link.memberCount, link.memberLimit),
                color = palette.textSecondary, fontSize = 12.sp
            )
        } else {
            Text(stringResource(R.string.group_invites_no_limit), color = palette.textSecondary, fontSize = 12.sp)
        }
        if (link.pendingJoinRequestCount > 0) {
            Text(
                stringResource(R.string.group_invites_requests, link.pendingJoinRequestCount),
                color = palette.textSecondary, fontSize = 12.sp
            )
        }
        if (link.isPrimary) {
            Text(
                stringResource(R.string.group_invites_primary_hint),
                color = palette.textSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (link.isSubscription) {
            Text(
                stringResource(R.string.group_invites_subscription_hint),
                color = palette.textSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (canEdit || canRevoke) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            if (canEdit) TextButton(onClick = onEdit) {
                Text(stringResource(R.string.group_invites_edit))
            }
            if (canRevoke) TextButton(onClick = onRevoke) { Text(stringResource(R.string.group_invites_revoke), color = palette.danger) }
        }
    }
}

@Composable
private fun GroupInviteLinkDialog(
    existing: GroupInviteLink?,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (GroupInviteLinkDraft) -> Unit
) {
    val palette = beyGramPalette()
    var name by rememberSaveable(existing?.link) { mutableStateOf(existing?.name.orEmpty()) }
    // -1 — сохранить исходный абсолютный срок, 0 — бессрочно, >0 — новый срок в секундах.
    var termChoice by rememberSaveable(existing?.link) {
        mutableIntStateOf(if (existing != null && existing.expiresAtSeconds > 0) -1 else 0)
    }
    var limit by rememberSaveable(existing?.link) {
        mutableStateOf(existing?.memberLimit?.takeIf { it > 0 }?.toString().orEmpty())
    }
    var approval by rememberSaveable(existing?.link) { mutableStateOf(existing?.requiresApproval == true) }
    val parsedLimit = limit.toIntOrNull() ?: if (limit.isBlank()) 0 else Int.MAX_VALUE
    val keepExistingExpiry = existing != null && existing.expiresAtSeconds > 0
    val nowSeconds = System.currentTimeMillis() / 1000
    val expiresAtSeconds = when {
        termChoice < 0 -> existing?.expiresAtSeconds ?: 0
        termChoice == 0 -> 0
        else -> inviteExpirationDate(nowSeconds, termChoice)
    }
    val draft = GroupInviteLinkDraft(
        name = name,
        expiresAtSeconds = expiresAtSeconds,
        memberLimit = if (approval) 0 else parsedLimit,
        requiresApproval = approval
    )
    val problem = groupInviteLinkProblem(draft)
    val problemText = when (problem) {
        GroupInviteLinkProblem.NameTooLong -> stringResource(R.string.err_group_invite_name_too_long)
        GroupInviteLinkProblem.MemberLimitOutOfRange -> stringResource(R.string.err_group_invite_limit)
        GroupInviteLinkProblem.LimitWithApproval -> stringResource(R.string.err_group_invite_limit_approval)
        null -> null
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(if (existing == null) R.string.group_invites_create_title else R.string.group_invites_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(GROUP_INVITE_NAME_MAX_LENGTH + 8) },
                    label = { Text(stringResource(R.string.group_invites_name_label)) },
                    supportingText = { Text(stringResource(R.string.group_invites_name_hint)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = beyGramTextFieldColors()
                )
                Text(
                    stringResource(R.string.group_invites_expiry_label),
                    color = palette.textSecondary, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (keepExistingExpiry) FilterChip(
                        selected = termChoice < 0,
                        onClick = { termChoice = -1 },
                        enabled = !busy,
                        label = { Text(stringResource(R.string.group_invites_term_keep), fontSize = 13.sp) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    GROUP_INVITE_TERM_VALUES.forEach { value ->
                        FilterChip(
                            selected = termChoice == value,
                            onClick = { termChoice = value },
                            enabled = !busy,
                            label = { Text(stringResource(value.inviteTermLabel()), fontSize = 13.sp) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = limit,
                    onValueChange = { value -> limit = value.filter(Char::isDigit).take(6) },
                    label = { Text(stringResource(R.string.group_invites_limit_label)) },
                    supportingText = { Text(stringResource(R.string.group_invites_limit_hint)) },
                    singleLine = true,
                    enabled = !busy && !approval,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = beyGramTextFieldColors()
                )
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.group_invites_approval_label), color = palette.textPrimary, fontSize = 15.sp)
                        Text(stringResource(R.string.group_invites_approval_hint), color = palette.textSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = approval,
                        enabled = !busy,
                        onCheckedChange = { checked -> approval = checked; if (checked) limit = "" }
                    )
                }
                problemText?.let { Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.errorAnnouncement()) }
                error?.let { Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp).errorAnnouncement()) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = palette.accent)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && problem == null,
                onClick = { onSave(draft.normalized()) }
            ) { Text(stringResource(if (busy) R.string.common_saving else R.string.common_save)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

private fun Int.inviteTermLabel(): Int = when (this) {
    -1 -> R.string.group_invites_term_keep
    86_400 -> R.string.group_invites_term_day
    7 * 86_400 -> R.string.group_invites_term_week
    30 * 86_400 -> R.string.group_invites_term_month
    else -> R.string.group_invites_term_never
}

private fun copyInviteLink(context: Context, link: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("BeyGram", link))
}

private fun shareInviteLink(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.group_invites_share_chooser)))
}
