package com.beykus.beygram.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.BlockedSender
import com.beykus.beygram.domain.model.BlockedSenderKind
import com.beykus.beygram.domain.model.BlockedSendersPage
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

private const val BLOCKED_PAGE_SIZE = 50
private const val BLOCKED_PREFETCH = 8

/**
 * Telegram's main block list. Unblocking here changes only the account-wide block list;
 * the contact book and chat history are separate resources and are left untouched.
 */
@Composable
fun BlockedSendersScreen(
    loadPage: suspend (offset: Int, limit: Int) -> Result<BlockedSendersPage>,
    setBlocked: suspend (kind: BlockedSenderKind, senderId: Long, blocked: Boolean) -> Result<Unit>,
    reportSpam: suspend (userId: Long) -> Result<Unit>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var senders by remember { mutableStateOf<List<BlockedSender>>(emptyList()) }
    var total by remember { mutableStateOf<Int?>(null) }
    var nextOffset by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var busyKey by remember { mutableStateOf<String?>(null) }
    var reportTarget by remember { mutableStateOf<BlockedSender?>(null) }
    var reportsBusy by remember { mutableStateOf(false) }
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    // A refresh invalidates in-flight pagination; its stale page must not repopulate the reset list.
    var pageGeneration by remember { mutableIntStateOf(0) }

    suspend fun loadNext() {
        val requestGeneration = pageGeneration
        if (loading || total?.let { nextOffset >= it } == true) return
        loading = true
        error = null
        try {
            loadPage(nextOffset, BLOCKED_PAGE_SIZE).fold(
                { page ->
                    if (requestGeneration != pageGeneration) return@fold
                    senders = senders + page.senders
                    nextOffset += page.fetched
                    // An empty page, or a page whose senders were all unsupported types,
                    // means no progress: stop rather than requesting the same offset forever.
                    total = if (page.fetched == 0 || page.senders.isEmpty()) nextOffset else page.totalCount
                },
                { if (requestGeneration == pageGeneration) error = it.userMessage(context, R.string.blocked_load_failed) }
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (requestGeneration == pageGeneration) error = failure.userMessage(context, R.string.blocked_load_failed)
        } finally {
            if (requestGeneration == pageGeneration) loading = false
        }
    }

    LaunchedEffect(refresh) {
        pageGeneration++
        loading = false
        senders = emptyList()
        nextOffset = 0
        total = null
        loadNext()
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { last ->
            if (last != null && last >= senders.size - BLOCKED_PREFETCH) loadNext()
        }
    }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.blocked_title), onBack)
        Text(
            stringResource(R.string.blocked_intro),
            color = palette.textSecondary, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        total?.let { count ->
            Text(
                stringResource(R.string.blocked_count, count),
                color = palette.textSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        error?.let { message ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(message, color = palette.danger)
                TextButton(enabled = !loading, onClick = { refresh++ }) {
                    Text(stringResource(R.string.common_retry), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        actionError?.let { message -> Text(message, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
        if (!loading && senders.isEmpty() && error == null) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.blocked_empty), color = palette.textSecondary, modifier = Modifier.padding(24.dp))
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(20.dp)).background(palette.surface),
                state = listState, contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(senders, key = { _, sender -> "${sender.kind}:${sender.senderId}" }) { index, sender ->
                    val key = "${sender.kind}:${sender.senderId}"
                    if (index > 0) HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 78.dp))
                    BlockedSenderRow(
                        sender = sender,
                        busy = busyKey == key,
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        onUnblock = {
                            if (busyKey == null) {
                                busyKey = key
                                actionError = null
                                scope.launch {
                                    try {
                                        setBlocked(sender.kind, sender.senderId, false).fold(
                                            {
                                                Toast.makeText(context, R.string.blocked_unblocked, Toast.LENGTH_SHORT).show()
                                                refresh++
                                            },
                                            { actionError = it.userMessage(context, R.string.feature_action_failed) }
                                        )
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        actionError = failure.userMessage(context, R.string.feature_action_failed)
                                    } finally {
                                        busyKey = null
                                    }
                                }
                            }
                        },
                        onReportSpam = { reportTarget = sender }
                    )
                }
            }
        }
    }

    reportTarget?.let { sender ->
        AlertDialog(
            onDismissRequest = { if (!reportsBusy) reportTarget = null },
            title = { Text(stringResource(R.string.blocked_report_spam)) },
            text = {
                Column {
                    Text(stringResource(R.string.blocked_report_spam_warning))
                    if (reportsBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp), color = palette.accent)
                }
            },
            confirmButton = {
                TextButton(enabled = !reportsBusy, onClick = {
                    reportsBusy = true
                    actionError = null
                    scope.launch {
                        try {
                            reportSpam(sender.senderId).fold(
                                {
                                    reportTarget = null
                                    Toast.makeText(context, R.string.blocked_report_sent, Toast.LENGTH_SHORT).show()
                                },
                                { actionError = it.userMessage(context, R.string.blocked_report_failed); reportTarget = null }
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            actionError = failure.userMessage(context, R.string.blocked_report_failed)
                            reportTarget = null
                        } finally {
                            reportsBusy = false
                        }
                    }
                }) { Text(stringResource(R.string.feature_confirm), color = palette.danger) }
            },
            dismissButton = {
                TextButton(enabled = !reportsBusy, onClick = { reportTarget = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
private fun BlockedSenderRow(
    sender: BlockedSender,
    busy: Boolean,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onUnblock: () -> Unit,
    onReportSpam: () -> Unit
) {
    val palette = beyGramPalette()
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TelegramAvatar(sender.initials, androidx.compose.ui.graphics.Color(sender.avatarColorArgb), 46.dp, sender.photo, observeFile, downloadFile)
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Text(
                sender.title.ifBlank {
                    stringResource(if (sender.kind == BlockedSenderKind.Chat) R.string.blocked_chat_type else R.string.user_name_unknown)
                },
                color = palette.textPrimary,
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(senderSubtitle(sender), color = palette.textSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = palette.accent, strokeWidth = 2.dp)
        else Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, stringResource(R.string.feature_more_actions), tint = palette.textSecondary)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.blocked_unblock)) },
                    onClick = { menuOpen = false; onUnblock() }
                )
                if (sender.kind == BlockedSenderKind.User) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.blocked_report_spam), color = palette.danger) },
                        onClick = { menuOpen = false; onReportSpam() }
                    )
                }
            }
        }
    }
}

@Composable
private fun senderSubtitle(sender: BlockedSender): String = when {
    sender.username.isNotBlank() -> "@${sender.username}"
    sender.kind == BlockedSenderKind.Chat -> stringResource(R.string.blocked_chat_type)
    sender.isBot -> stringResource(R.string.blocked_bot_type)
    else -> stringResource(R.string.blocked_user_type)
}
