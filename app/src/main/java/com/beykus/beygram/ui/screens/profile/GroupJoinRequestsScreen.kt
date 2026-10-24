package com.beykus.beygram.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.GroupJoinRequest
import com.beykus.beygram.domain.model.GroupManagementInfo
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Заявки на вступление: принять или отклонить. Список доступен только роли с правом приглашать:
 * обычный участник не видит ни этот экран, ни счётчик в обзоре.
 */
@Composable
internal fun GroupJoinRequestsScreen(
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
    var requests by remember(chatId) { mutableStateOf<List<GroupJoinRequest>>(emptyList()) }
    var loading by remember(chatId) { mutableStateOf(true) }
    var error by remember(chatId) { mutableStateOf<String?>(null) }
    var notice by remember(chatId) { mutableStateOf<String?>(null) }
    var reloadToken by remember(chatId) { mutableIntStateOf(0) }
    var busyUserId by remember(chatId) { mutableStateOf<Long?>(null) }

    LaunchedEffect(chatId, reloadToken) {
        loading = true
        error = null
        repository.loadGroupJoinRequests(chatId)
            .onSuccess { requests = it }
            .onFailure { error = it.userMessage(context, R.string.group_requests_load_failed) }
        loading = false
    }

    fun process(request: GroupJoinRequest, approve: Boolean) {
        if (busyUserId != null) return
        busyUserId = request.userId
        error = null
        scope.launch {
            repository.processGroupJoinRequest(chatId, request.userId, approve)
                .onSuccess {
                    requests = requests.filterNot { it.userId == request.userId }
                    notice = context.getString(if (approve) R.string.group_requests_approved else R.string.group_requests_declined)
                    onChanged()
                }
                .onFailure { error = it.userMessage(context, R.string.err_group_action_failed) }
            busyUserId = null
        }
    }

    // Пока заявка обрабатывается, Back не должен закрывать всё приложение.
    BackHandler { if (busyUserId == null) onBack() }
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.group_requests_title), { if (busyUserId == null) onBack() }) {
            IconButton(onClick = { reloadToken++ }, enabled = !loading) {
                Icon(Icons.Rounded.Refresh, stringResource(R.string.group_manage_refresh), tint = palette.textPrimary)
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        GroupManagementMessage(error, error = true)
        GroupManagementMessage(notice)
        if (info.pendingJoinRequestCount > 0) Text(
            stringResource(R.string.group_requests_count, maxOf(info.pendingJoinRequestCount, requests.size)),
            color = palette.textSecondary, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        if (error != null && requests.isEmpty() && !loading) TextButton(
            onClick = { reloadToken++ },
            modifier = Modifier.padding(horizontal = 12.dp)
        ) { Text(stringResource(R.string.common_retry)) }
        when {
            requests.isEmpty() && !loading && error == null -> Text(
                stringResource(R.string.group_requests_empty),
                color = palette.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(24.dp)
            )
            else -> LazyColumn(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp).clip(RoundedCornerShape(20.dp)).background(palette.surface),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(requests, key = { it.userId }) { request ->
                    GroupJoinRequestRow(
                        request = request,
                        busy = busyUserId == request.userId,
                        enabled = busyUserId == null,
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        onOpenUser = { onOpenUser(request.userId) },
                        onApprove = { process(request, true) },
                        onDecline = { process(request, false) }
                    )
                    HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

@Composable
private fun GroupJoinRequestRow(
    request: GroupJoinRequest,
    busy: Boolean,
    enabled: Boolean,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onOpenUser: () -> Unit,
    onApprove: () -> Unit,
    onDecline: () -> Unit
) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clickable(enabled = enabled, role = Role.Button, onClick = onOpenUser)
                    .heightIn(min = 48.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelegramAvatar(request.initials, Color(request.avatarColorArgb), 44.dp, request.photo, observeFile, downloadFile)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        request.name.ifBlank { stringResource(R.string.group_requests_unknown_user) },
                        color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.group_requests_date, formatGroupDate(request.dateSeconds)),
                        color = palette.textSecondary, fontSize = 12.sp
                    )
                }
            }
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = palette.accent, strokeWidth = 2.dp)
        }
        if (request.bio.isNotBlank()) Text(
            request.bio,
            color = palette.textSecondary, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDecline, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.group_requests_decline), color = palette.danger)
            }
            TextButton(onClick = onApprove, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.group_requests_approve), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
