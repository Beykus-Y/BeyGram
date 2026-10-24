package com.beykus.beygram.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.GroupManagementInfo
import com.beykus.beygram.domain.model.ManagedChatKind
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.flow.StateFlow

private enum class GroupManagementPage { Overview, Members, Invites, Requests, Settings }

/**
 * Центр управления группой или каналом. Экран сам загружает права с сервера, поэтому после
 * понижения роли административные разделы исчезают при следующем открытии. Разделы открываются
 * внутри этого маршрута, а не отдельными пунктами навигации: назад всегда ведёт в обзор.
 */
@Composable
fun GroupManagementScreen(
    chatId: Long,
    repository: TelegramRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onOpenUser: (Long) -> Unit,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    var info by remember(chatId) { mutableStateOf<GroupManagementInfo?>(null) }
    var loading by remember(chatId) { mutableStateOf(true) }
    var error by remember(chatId) { mutableStateOf<String?>(null) }
    var reloadToken by remember(chatId) { mutableIntStateOf(0) }
    var page by remember(chatId) { mutableStateOf(GroupManagementPage.Overview) }

    fun reload() { reloadToken++ }
    fun leaveSubpage() { page = GroupManagementPage.Overview; reload() }

    LaunchedEffect(chatId, reloadToken) {
        loading = true
        repository.loadGroupManagement(chatId)
            .onSuccess { info = it; error = null }
            .onFailure { error = it.userMessage(context, R.string.group_manage_load_failed) }
        loading = false
    }
    // В подразделах работают их собственные обработчики «назад»; здесь закрывается только обзор.
    BackHandler(enabled = page == GroupManagementPage.Overview) { onBack() }

    val loaded = info
    when {
        loaded == null -> Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
            ProfileToolbar(stringResource(R.string.group_manage_title_group), onBack)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
            error?.let { message ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(message, color = palette.danger, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = ::reload, enabled = !loading) { Text(stringResource(R.string.common_retry)) }
                }
            }
        }
        else -> when (page) {
            GroupManagementPage.Overview -> GroupManagementOverview(
                info = loaded, loading = loading, error = error,
                observeFile = observeFile, downloadFile = downloadFile,
                onRefresh = ::reload, onBack = onBack,
                onMembers = { page = GroupManagementPage.Members },
                onInvites = { page = GroupManagementPage.Invites },
                onRequests = { page = GroupManagementPage.Requests },
                onSettings = { page = GroupManagementPage.Settings }
            )
            GroupManagementPage.Members -> GroupMembersScreen(
                chatId = chatId, info = loaded, repository = repository,
                observeFile = observeFile, downloadFile = downloadFile, onOpenUser = onOpenUser,
                onBack = ::leaveSubpage, onChanged = ::reload
            )
            GroupManagementPage.Invites -> GroupInvitesScreen(
                chatId = chatId, info = loaded, repository = repository,
                onBack = ::leaveSubpage, onChanged = ::reload
            )
            GroupManagementPage.Requests -> GroupJoinRequestsScreen(
                chatId = chatId, info = loaded, repository = repository,
                observeFile = observeFile, downloadFile = downloadFile, onOpenUser = onOpenUser,
                onBack = ::leaveSubpage, onChanged = ::reload
            )
            GroupManagementPage.Settings -> GroupSettingsScreen(
                info = loaded, repository = repository, observeFile = observeFile, downloadFile = downloadFile,
                onBack = ::leaveSubpage,
                onChanged = ::reload
            )
        }
    }
}

/** Обзор: только разделы, разрешённые текущей роли. */
@Composable
private fun GroupManagementOverview(
    info: GroupManagementInfo,
    loading: Boolean,
    error: String?,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onMembers: () -> Unit,
    onInvites: () -> Unit,
    onRequests: () -> Unit,
    onSettings: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val rights = info.rights
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(managementTitle(info.kind), onBack) {
            IconButton(onClick = onRefresh, enabled = !loading) {
                Icon(Icons.Rounded.Refresh, stringResource(R.string.group_manage_refresh), tint = palette.textPrimary)
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp)) {
            item(key = "header") {
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TelegramAvatar(
                        info.initials, palette.accent, 64.dp, info.photo, observeFile, downloadFile,
                        Modifier.clearAndSetSemantics {}
                    )
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(
                            info.title.ifBlank { context.getString(R.string.user_name_unknown) },
                            color = palette.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() }
                        )
                        val role = groupViewerRole(rights)
                        if (role.isNotBlank()) Text(role, color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        info.memberCount?.let {
                            Text(
                                stringResource(
                                    if (info.kind == ManagedChatKind.Channel) R.string.profile_channel_subscribers_count
                                    else R.string.profile_group_members_count,
                                    it
                                ),
                                color = palette.textSecondary, fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            if (error != null) item(key = "error") {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error, color = palette.danger, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRefresh, enabled = !loading) { Text(stringResource(R.string.common_retry)) }
                }
            }
            if (!rights.canManageAnything) {
                item(key = "no_rights") {
                    Text(
                        stringResource(R.string.group_manage_no_rights),
                        color = palette.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                    )
                }
            } else item(key = "sections") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (rights.canManageMembers) GroupManagementSection(
                        title = stringResource(R.string.group_manage_overview_members),
                        subtitle = info.memberCount?.let {
                            context.getString(
                                if (info.kind == ManagedChatKind.Channel) R.string.profile_channel_subscribers_count
                                else R.string.profile_group_members_count,
                                it
                            )
                        } ?: context.getString(R.string.group_manage_overview_members_hint),
                        icon = Icons.Rounded.Group, onClick = onMembers
                    )
                    if (rights.canManageInviteLinks) GroupManagementSection(
                        title = stringResource(R.string.group_manage_overview_invites),
                        subtitle = info.primaryInviteLink.takeIf { it.isNotBlank() }
                            ?: context.getString(R.string.group_manage_overview_invites_hint),
                        icon = Icons.Rounded.Link, onClick = onInvites
                    )
                    if (rights.canManageJoinRequests) GroupManagementSection(
                        title = stringResource(R.string.group_manage_overview_requests),
                        subtitle = if (info.pendingJoinRequestCount > 0) {
                            context.getString(R.string.group_manage_overview_requests_count, info.pendingJoinRequestCount)
                        } else context.getString(R.string.group_manage_overview_requests_none),
                        icon = Icons.Rounded.PersonAdd, onClick = onRequests
                    )
                    if (rights.canChangeInfo || rights.canChangeUsername || rights.canManageSlowMode ||
                        rights.canToggleHistory || rights.canToggleJoinByRequest
                    ) GroupManagementSection(
                        title = stringResource(
                            if (info.kind == ManagedChatKind.Channel) R.string.group_manage_overview_settings_channel
                            else R.string.group_manage_overview_settings_group
                        ),
                        subtitle = stringResource(R.string.group_manage_overview_settings_hint),
                        icon = Icons.Rounded.Settings, onClick = onSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun managementTitle(kind: ManagedChatKind): String = stringResource(
    if (kind == ManagedChatKind.Channel) R.string.group_manage_title_channel else R.string.group_manage_title_group
)
