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
import com.beykus.beygram.domain.model.joinRequestTotalAfterProcessing
import com.beykus.beygram.domain.model.joinRequestsHaveMore
import com.beykus.beygram.domain.model.mergeJoinRequests
import com.beykus.beygram.domain.model.nextJoinRequestOffset
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val REQUESTS_PAGE = 50

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
    var total by remember(chatId) { mutableIntStateOf(info.pendingJoinRequestCount) }
    var nextOffset by remember(chatId) { mutableStateOf<String?>(null) }
    var hasMore by remember(chatId) { mutableStateOf(false) }
    var loading by remember(chatId) { mutableStateOf(true) }
    var loadingMore by remember(chatId) { mutableStateOf(false) }
    var error by remember(chatId) { mutableStateOf<String?>(null) }
    var notice by remember(chatId) { mutableStateOf<String?>(null) }
    var reloadToken by remember(chatId) { mutableIntStateOf(0) }
    var busyUserId by remember(chatId) { mutableStateOf<Long?>(null) }
    var processedUserIds by remember(chatId) { mutableStateOf<Set<Long>>(emptySet()) }
    var pageGeneration by remember(chatId) { mutableIntStateOf(0) }
    var loadMoreJob by remember(chatId) { mutableStateOf<Job?>(null) }

    fun reload() { reloadToken++ }

    LaunchedEffect(chatId, reloadToken) {
        // Смена поколения отменяет старую догрузку: её ответ не должен попасть в новый список.
        loadMoreJob?.cancel()
        loadMoreJob = null
        pageGeneration++
        val generation = pageGeneration
        loading = true
        loadingMore = false
        error = null
        requests = emptyList()
        nextOffset = null
        hasMore = false
        processedUserIds = emptySet()
        total = info.pendingJoinRequestCount
        repository.loadGroupJoinRequests(chatId, null, REQUESTS_PAGE)
            .onSuccess { page ->
                if (generation != pageGeneration) return@onSuccess
                val merged = mergeJoinRequests(emptyList(), page.requests, processedUserIds)
                requests = merged
                total = page.total
                nextOffset = page.nextOffset
                hasMore = joinRequestsHaveMore(merged.size, total, nextOffset)
            }
            .onFailure {
                if (generation == pageGeneration) error = it.userMessage(context, R.string.group_requests_load_failed)
            }
        if (generation == pageGeneration) loading = false
    }

    fun loadMore() {
        if (loading || loadingMore || busyUserId != null || !hasMore) return
        val offset = nextOffset ?: return
        val generation = pageGeneration
        loadingMore = true
        error = null
        loadMoreJob = scope.launch {
            val result = repository.loadGroupJoinRequests(chatId, offset, REQUESTS_PAGE)
            if (generation != pageGeneration) return@launch
            result.onSuccess { page ->
                val merged = mergeJoinRequests(requests, page.requests, processedUserIds)
                requests = merged
                total = maxOf(page.total, merged.size)
                nextOffset = page.nextOffset
                // Если сервер вернул тот же курсор, повторная догрузка зациклится.
                hasMore = page.nextOffset != offset && joinRequestsHaveMore(merged.size, total, nextOffset)
            }.onFailure { error = it.userMessage(context, R.string.group_requests_load_failed) }
            loadingMore = false
        }
    }

    fun process(request: GroupJoinRequest, approve: Boolean) {
        if (busyUserId != null || loadingMore) return
        busyUserId = request.userId
        error = null
        val generation = pageGeneration
        scope.launch {
            val result = repository.processGroupJoinRequest(chatId, request.userId, approve)
            // Пока запрос выполнялся, список могли обновить: не применяем результат к новому поколению.
            if (generation != pageGeneration) {
                busyUserId = null
                return@launch
            }
            result.onSuccess {
                // Курсор сдвигаем до удаления: если обработан последний загруженный элемент,
                // следующая страница начнётся с предыдущего, а не с исчезнувшей заявки.
                val newOffset = nextJoinRequestOffset(requests, request, nextOffset)
                processedUserIds = processedUserIds + request.userId
                requests = requests.filterNot { it.userId == request.userId }
                total = joinRequestTotalAfterProcessing(total)
                nextOffset = newOffset
                hasMore = joinRequestsHaveMore(requests.size, total, newOffset)
                notice = context.getString(if (approve) R.string.group_requests_approved else R.string.group_requests_declined)
                onChanged()
                // Если обработали всю загруженную страницу, начнём новую с начала.
                if (requests.isEmpty() && total > 0) reload()
            }.onFailure { error = it.userMessage(context, R.string.err_group_action_failed) }
            busyUserId = null
        }
    }

    // Пока заявка обрабатывается, Back не должен закрывать всё приложение; догрузку можно прервать.
    BackHandler { if (busyUserId == null) onBack() }
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.group_requests_title), { if (busyUserId == null) onBack() }) {
            IconButton(onClick = ::reload, enabled = !loading) {
                Icon(Icons.Rounded.Refresh, stringResource(R.string.group_manage_refresh), tint = palette.textPrimary)
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        GroupManagementMessage(error, error = true)
        GroupManagementMessage(notice)
        if (total > 0) Text(
            stringResource(R.string.group_requests_count, total),
            color = palette.textSecondary, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        if (error != null && requests.isEmpty() && !loading) TextButton(
            onClick = ::reload,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) { Text(stringResource(R.string.common_retry)) }
        when {
            requests.isEmpty() && !loading && error == null && total == 0 -> Text(
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
                        enabled = busyUserId == null && !loadingMore,
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        onOpenUser = { onOpenUser(request.userId) },
                        onApprove = { process(request, true) },
                        onDecline = { process(request, false) }
                    )
                    HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 72.dp))
                }
                if (hasMore || loadingMore) item(key = "more") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        if (loadingMore) CircularProgressIndicator(Modifier.size(24.dp), color = palette.accent, strokeWidth = 2.dp)
                        else TextButton(
                            onClick = ::loadMore,
                            enabled = busyUserId == null,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text(stringResource(R.string.group_requests_load_more), fontWeight = FontWeight.SemiBold) }
                    }
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
