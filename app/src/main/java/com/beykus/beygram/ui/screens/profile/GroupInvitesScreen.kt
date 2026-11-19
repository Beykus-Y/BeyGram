package com.beykus.beygram.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.accessibility.accessibleDescription
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.accessibility.statusAnnouncement
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.BeyGramSheet
import com.beykus.beygram.ui.components.Button
import com.beykus.beygram.ui.components.OutlinedButton
import com.beykus.beygram.ui.components.Switch
import com.beykus.beygram.ui.components.TextButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.beyGramTextFieldColors
import com.beykus.beygram.ui.components.groupSurfaceColor
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassCard
import com.beykus.beygram.ui.design.glassScreenBackground
import com.beykus.beygram.ui.design.groupCardShape
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.launch

/** Форма: [InviteEditor.link] = null — новая ссылка; иначе изменение или переименование существующей. */
private data class InviteEditor(val link: GroupInviteLink?)

/** Раздел списка. Основная ссылка идёт первой без заголовка, остальные группируются по фактическому состоянию. */
private enum class InviteSection { Primary, Active, Paid, Inactive }

/** Ключевой статус по данным TDLib. Обычная действующая ссылка статуса не имеет и без него не подписывается. */
private enum class InviteStatus { Primary, Revoked, Expired, Paid, Approval }

/**
 * Список ссылок-приглашений. Карточка короткая: название, ключевой статус, статистика и копирование одним касанием.
 * Адрес, ограничения, заявки, изменение и отзыв открываются в листе подробностей; отзыв там отделён и требует
 * подтверждения. Основная ссылка показывается всегда, остальные приходят из TDLib.
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
    val design = LocalDesignSystem.current
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
    // Открытая ссылка хранится ключом: после обновления списка лист берёт её свежие данные.
    var detailKey by remember(chatId) { mutableStateOf<String?>(null) }

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
        if (info.primaryInviteLink.isNotBlank() && links.none { it.isPrimary || it.link == info.primaryInviteLink }) {
            listOf(GroupInviteLink(link = info.primaryInviteLink, isPrimary = true)) + links
        } else links
    }
    // Счётчики и срок есть только у ссылок, которые прислал TDLib: подставленной основной ссылке их не выдумываем.
    val loadedKeys = remember(links) { links.mapTo(HashSet<String>()) { it.link } }
    val nowSeconds = System.currentTimeMillis() / 1000
    val sections = groupInviteSections(shown, nowSeconds)
    val openLink = detailKey?.let { key -> shown.firstOrNull { it.link == key } }

    LaunchedEffect(shown) {
        // Лист не остаётся открытым на ссылке, которой больше нет в списке (например, после отзыва).
        val open = detailKey
        if (open != null && shown.none { it.link == open }) detailKey = null
    }

    BackHandler { if (!dialogBusy && !revokeBusy) onBack() }
    Column(
        Modifier.fillMaxSize()
            .glassScreenBackground(design, palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
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
                sections.forEach { (section, sectionLinks) ->
                    section.titleRes()?.let { titleRes ->
                        item(key = "section-${section.name}") { InviteSectionHeading(stringResource(titleRes)) }
                    }
                    items(sectionLinks, key = { it.link }) { link ->
                        GroupInviteLinkCard(
                            link = link,
                            known = link.link in loadedKeys,
                            nowSeconds = nowSeconds,
                            onOpen = { detailKey = link.link },
                            onCopy = {
                                copyInviteLink(context, link.link)
                                notice = context.getString(R.string.group_invites_copied)
                            }
                        )
                    }
                }
            }
        }
    }

    openLink?.let { link ->
        GroupInviteLinkDetailsSheet(
            link = link,
            title = inviteLinkTitle(link),
            known = link.link in loadedKeys,
            nowSeconds = nowSeconds,
            permissions = groupInviteLinkPermissions(rights, link),
            onDismiss = { detailKey = null },
            onCopy = {
                copyInviteLink(context, link.link)
                notice = context.getString(R.string.group_invites_copied)
            },
            onShare = {
                shareInviteLink(context, context.getString(R.string.group_invites_share_text, info.title, link.link))
            },
            onEdit = {
                detailKey = null
                dialogError = null
                editor = InviteEditor(link)
            },
            onRevoke = {
                detailKey = null
                revoking = link
            }
        )
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
                    val target = current.link
                    val result = if (target == null) {
                        repository.createGroupInviteLink(chatId, draft)
                    } else if (target.isSubscription) {
                        repository.renameGroupSubscriptionInviteLink(chatId, target.link, draft.name)
                    } else {
                        repository.editGroupInviteLink(chatId, target.link, draft)
                    }
                    dialogBusy = false
                    result.fold(
                        onSuccess = {
                            notice = context.getString(
                                if (target == null) R.string.group_invites_created
                                else if (target.isSubscription) R.string.group_invites_renamed
                                else R.string.group_invites_updated
                            )
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
        val busyBar: @Composable () -> Unit = { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp), color = palette.accent) }
        BeyGramDialog(
            onDismissRequest = { if (!revokeBusy) revoking = null },
            title = stringResource(R.string.group_invites_revoke_title),
            message = stringResource(R.string.group_invites_revoke_warning),
            content = if (revokeBusy) busyBar else null,
            confirmText = stringResource(R.string.group_invites_revoke),
            confirmEnabled = !revokeBusy,
            destructive = true,
            onConfirm = {
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
            },
            dismissText = stringResource(R.string.common_cancel),
            dismissEnabled = !revokeBusy,
            onDismiss = { revoking = null }
        )
    }
}

/**
 * Карточка ссылки в списке: название, ключевой статус, одна строка статистики и копирование. Нажатие на текст
 * открывает подробности. Копирование отделено своей кнопкой и ничего не отзывает.
 */
@Composable
private fun GroupInviteLinkCard(
    link: GroupInviteLink,
    known: Boolean,
    nowSeconds: Long,
    onOpen: () -> Unit,
    onCopy: () -> Unit
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val title = inviteLinkTitle(link)
    val status = link.statusAt(nowSeconds)
    val statusText = if (status != null) inviteStatusText(link, status) else null
    val stats = if (known) inviteStatsText(link, link.isExpiredAt(nowSeconds)) else ""
    Row(
        Modifier.fillMaxWidth().glassCard(design, groupSurfaceColor(), palette.textSecondary, groupCardShape()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.weight(1f)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.group_invites_open_details), onClick = onOpen)
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibleDescription(title, statusText, stats)
                }
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Text(
                title,
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                displayInviteAddress(link.link),
                color = palette.accentInk,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (status != null || stats.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (status != null && statusText != null) {
                        InviteStatusPill(statusText, status)
                        if (stats.isNotEmpty()) Spacer(Modifier.width(8.dp))
                    }
                    if (stats.isNotEmpty()) Text(
                        stats,
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Rounded.ContentCopy, stringResource(R.string.group_invites_copy), tint = palette.textSecondary)
        }
    }
}

/**
 * Подробности ссылки: статус, полный адрес с копированием и отправкой, факты из TDLib и действия по правам. Отзыв
 * стоит отдельно внизу, красным, и подтверждается диалогом; копирование — заполненной кнопкой сверху.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupInviteLinkDetailsSheet(
    link: GroupInviteLink,
    title: String,
    known: Boolean,
    nowSeconds: Long,
    permissions: GroupInviteLinkPermissions,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onRevoke: () -> Unit
) {
    val palette = beyGramPalette()
    var copied by rememberSaveable(link.link) { mutableStateOf(false) }
    val status = link.statusAt(nowSeconds)
    BeyGramSheet(onDismissRequest = onDismiss, title = title) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            if (status != null) {
                InviteStatusPill(inviteStatusText(link, status), status)
                Spacer(Modifier.height(10.dp))
            }
            SelectionContainer {
                Text(link.link, color = palette.accentInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = {
                    copied = true
                    onCopy()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 12.dp)
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.group_invites_copy))
            }
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp)
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.group_invites_share))
            }
            if (copied) Text(
                stringResource(R.string.group_invites_copied),
                color = palette.accentInk,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp).statusAnnouncement()
            )
            if (known) {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    inviteFactLines(link, nowSeconds).forEach { line ->
                        Text(line, color = palette.textPrimary, fontSize = 15.sp, modifier = Modifier.padding(vertical = 3.dp))
                    }
                }
            }
            if (link.isPrimary) Text(
                stringResource(R.string.group_invites_primary_hint),
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (link.isSubscription) Text(
                stringResource(R.string.group_invites_subscription_hint),
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (permissions.canRename) OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 12.dp)
            ) { Text(stringResource(R.string.group_invites_rename)) }
            if (permissions.canEdit) OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 12.dp)
            ) { Text(stringResource(R.string.group_invites_edit)) }
            if (permissions.canRevoke) {
                HorizontalDivider(modifier = Modifier.padding(top = 16.dp), color = palette.divider)
                OutlinedButton(
                    onClick = onRevoke,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp)
                ) { Text(stringResource(R.string.group_invites_revoke), color = palette.danger) }
            }
        }
    }
}

/** Единая форма ссылки: создание, изменение и переименование платной подписки. Для подписки TDLib меняет только название. */
@Composable
private fun GroupInviteLinkDialog(
    existing: GroupInviteLink?,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (GroupInviteLinkDraft) -> Unit
) {
    val palette = beyGramPalette()
    // Срок, лимит и заявки платной подписки TDLib не принимает, поэтому у неё остаётся только название.
    val paidName = existing?.isSubscription == true
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
    // Ошибки показываются у своего поля: подсветка и текст в подсказке, а не отдельной строкой.
    val nameTooLong = problem == GroupInviteLinkProblem.NameTooLong
    val limitProblem = problem == GroupInviteLinkProblem.MemberLimitOutOfRange ||
        problem == GroupInviteLinkProblem.LimitWithApproval
    val nameChanged = name.trim() != existing?.name
    BeyGramDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = stringResource(
            when {
                existing == null -> R.string.group_invites_create_title
                paidName -> R.string.group_invites_rename_title
                else -> R.string.group_invites_edit_title
            }
        ),
        content = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(GROUP_INVITE_NAME_MAX_LENGTH + 8) },
                    label = { Text(stringResource(R.string.group_invites_name_label)) },
                    supportingText = {
                        Text(
                            if (nameTooLong) stringResource(R.string.err_group_invite_name_too_long)
                            else stringResource(R.string.group_invites_name_hint),
                            modifier = if (nameTooLong) Modifier.errorAnnouncement() else Modifier
                        )
                    },
                    isError = nameTooLong,
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = styledShape(14.dp),
                    colors = beyGramTextFieldColors()
                )
                if (!paidName) {
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
                        supportingText = {
                            Text(
                                when (problem) {
                                    GroupInviteLinkProblem.MemberLimitOutOfRange -> stringResource(R.string.err_group_invite_limit)
                                    GroupInviteLinkProblem.LimitWithApproval -> stringResource(R.string.err_group_invite_limit_approval)
                                    else -> stringResource(R.string.group_invites_limit_hint)
                                },
                                modifier = if (limitProblem) Modifier.errorAnnouncement() else Modifier
                            )
                        },
                        isError = limitProblem,
                        singleLine = true,
                        enabled = !busy && !approval,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                        shape = styledShape(14.dp),
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
                }
                error?.let { Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp).errorAnnouncement()) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = palette.accent)
            }
        },
        confirmText = stringResource(if (busy) R.string.common_saving else R.string.common_save),
        confirmEnabled = !busy && problem == null && (!paidName || nameChanged),
        onConfirm = { onSave(draft.normalized()) },
        dismissText = stringResource(R.string.common_cancel),
        dismissEnabled = !busy,
        onDismiss = onDismiss
    )
}

/** Заголовок раздела списка: тот же вид, что у заголовков групп в настройках, с учётом стиля. */
@Composable
private fun InviteSectionHeading(text: String) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    Text(
        // Material 2 shouts its headings; the other styles keep the sentence case of the resource.
        if (design.mainScreen.sectionLabelUppercase) text.uppercase() else text,
        color = palette.textSecondary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = design.mainScreen.sectionLabelTracking,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 4.dp).semantics { heading() }
    )
}

/** Ключевой статус: основная — заполненная плашка, платная и по заявкам — мягкие, истёкшая и отозванная — нейтральные. */
@Composable
private fun InviteStatusPill(text: String, status: InviteStatus) {
    val palette = beyGramPalette()
    val (container, content) = when (status) {
        InviteStatus.Primary -> palette.accent to palette.onAccent
        InviteStatus.Paid -> palette.accentSoft to palette.accentInk
        InviteStatus.Approval -> palette.surface2 to palette.textPrimary
        InviteStatus.Expired, InviteStatus.Revoked -> palette.surface2 to palette.textSecondary
    }
    Text(
        text,
        color = content,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(styledShape(8.dp)).background(container).padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** Название ссылки; у основной без названия — её собственное, у дополнительной — общее. */
@Composable
private fun inviteLinkTitle(link: GroupInviteLink): String = when {
    link.name.isNotBlank() -> link.name
    link.isPrimary -> stringResource(R.string.group_invites_primary)
    else -> stringResource(R.string.group_invites_default_name)
}

@Composable
private fun inviteStatusText(link: GroupInviteLink, status: InviteStatus): String = when (status) {
    InviteStatus.Primary -> stringResource(R.string.group_invites_status_primary)
    InviteStatus.Paid -> stringResource(R.string.group_invites_status_paid)
    InviteStatus.Approval -> stringResource(R.string.group_invites_status_approval)
    InviteStatus.Revoked -> stringResource(R.string.group_invites_status_revoked)
    InviteStatus.Expired -> stringResource(R.string.group_invites_expired_on, formatGroupDate(link.expiresAtSeconds))
}

/** Строка статистики карточки: заполненность или число вошедших, заявки и срок. Срок истёкшей ссылки уже в статусе. */
@Composable
private fun inviteStatsText(link: GroupInviteLink, expired: Boolean): String {
    val parts = mutableListOf<String>()
    if (link.memberLimit > 0) {
        parts += stringResource(R.string.group_invites_limit_used, link.memberCount, link.memberLimit)
    } else {
        parts += stringResource(R.string.group_invites_joined, link.memberCount)
    }
    if (link.pendingJoinRequestCount > 0) parts += stringResource(R.string.group_invites_requests, link.pendingJoinRequestCount)
    if (link.expiresAtSeconds > 0 && !expired) parts += stringResource(R.string.group_invites_expires, formatGroupDate(link.expiresAtSeconds))
    return parts.joinToString(" · ")
}

/** Факты для подробностей: только то, что TDLib прислал для этой ссылки. */
@Composable
private fun inviteFactLines(link: GroupInviteLink, nowSeconds: Long): List<String> {
    val lines = mutableListOf<String>()
    lines += when {
        link.expiresAtSeconds <= 0 -> stringResource(R.string.group_invites_never_expires)
        link.isExpiredAt(nowSeconds) -> stringResource(R.string.group_invites_expired_on, formatGroupDate(link.expiresAtSeconds))
        else -> stringResource(R.string.group_invites_expires, formatGroupDate(link.expiresAtSeconds))
    }
    if (link.memberLimit > 0) {
        lines += stringResource(R.string.group_invites_limit_used, link.memberCount, link.memberLimit)
    } else {
        if (!link.requiresApproval) lines += stringResource(R.string.group_invites_no_limit)
        lines += stringResource(R.string.group_invites_joined, link.memberCount)
    }
    if (link.requiresApproval) lines += stringResource(R.string.group_invites_approval)
    if (link.pendingJoinRequestCount > 0) lines += stringResource(R.string.group_invites_requests, link.pendingJoinRequestCount)
    if (link.isSubscription) lines += stringResource(R.string.group_invites_subscription, link.subscriptionStarCount)
    if (link.createdSeconds > 0) lines += stringResource(R.string.group_invites_created_on, formatGroupDate(link.createdSeconds))
    if (link.creatorIsCurrentUser) lines += stringResource(R.string.group_invites_creator_you)
    return lines
}

/** Адрес без схемы: короче, а уникальная часть ссылки остаётся видна. Полный адрес — в подробностях. */
private fun displayInviteAddress(link: String): String = link.removePrefix("https://").removePrefix("http://")

private fun GroupInviteLink.isExpiredAt(nowSeconds: Long): Boolean =
    expiresAtSeconds > 0 && expiresAtSeconds <= nowSeconds

private fun GroupInviteLink.statusAt(nowSeconds: Long): InviteStatus? = when {
    isPrimary -> InviteStatus.Primary
    isRevoked -> InviteStatus.Revoked
    isExpiredAt(nowSeconds) -> InviteStatus.Expired
    isSubscription -> InviteStatus.Paid
    requiresApproval -> InviteStatus.Approval
    else -> null
}

private fun GroupInviteLink.sectionAt(nowSeconds: Long): InviteSection = when {
    isPrimary -> InviteSection.Primary
    isRevoked || isExpiredAt(nowSeconds) -> InviteSection.Inactive
    isSubscription -> InviteSection.Paid
    else -> InviteSection.Active
}

/** Разделы в порядке показа; пустые не выводятся. Порядок ссылок внутри раздела сохраняется из TDLib. */
private fun groupInviteSections(
    links: List<GroupInviteLink>,
    nowSeconds: Long
): List<Pair<InviteSection, List<GroupInviteLink>>> {
    val bySection = links.groupBy { it.sectionAt(nowSeconds) }
    return listOf(InviteSection.Primary, InviteSection.Active, InviteSection.Paid, InviteSection.Inactive)
        .mapNotNull { section -> bySection[section]?.let { section to it } }
}

private fun InviteSection.titleRes(): Int? = when (this) {
    InviteSection.Primary -> null
    InviteSection.Active -> R.string.group_invites_section_active
    InviteSection.Paid -> R.string.group_invites_section_paid
    InviteSection.Inactive -> R.string.group_invites_section_inactive
}

/** Подпись срока в чипе формы; -1 — сохранить уже заданный срок. */
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
