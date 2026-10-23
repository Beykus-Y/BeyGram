package com.beykus.beygram.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.tdlib.TdAccountAuthPhase
import com.beykus.beygram.data.tdlib.TdAccountChatKey
import com.beykus.beygram.data.tdlib.TdSecondaryAuthSession
import com.beykus.beygram.data.tdlib.TdSecondarySessionCoordinator
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * An account-scoped preview rather than the legacy ChatsScreen:
 * no legacy repository, chat cache, notifications, drafts, or media player.
 * Until a full secondary TelegramRepository exists this view is READ-ONLY.
 */
@Composable
internal fun SecondaryChatsScreen(
    accountId: String,
    coordinator: TdSecondarySessionCoordinator,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    val accounts by coordinator.snapshot.collectAsStateWithLifecycle()
    var client by remember(accountId) { mutableStateOf<TdSecondaryAuthSession?>(null) }
    var selectedChat by remember(accountId) { mutableStateOf<TdAccountChatKey?>(null) }
    var error by remember(accountId) { mutableStateOf(false) }
    var loading by remember(accountId) { mutableStateOf(true) }

    // Closing the client or a route mismatch may occur while this screen is open.
    val account = accounts.accounts.firstOrNull { it.accountId == accountId }
    val available = account?.connectedInThisProcess == true &&
        account.phase == TdAccountAuthPhase.READY && !account.networkSuspended

    // Hardware Back inside a secondary conversation returns to its own list.
    // The legacy Chats tab and chat IDs must never be involved.
    BackHandler(enabled = selectedChat != null) { selectedChat = null }

    LaunchedEffect(accountId, available) {
        if (!available) {
            client = null
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = false
        try {
            val opened = coordinator.chatSession(accountId)
            client = opened
            opened.loadChats()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
        finally { loading = false }
    }

    val chats = client?.chatStore?.chats?.collectAsStateWithLifecycle()?.value.orEmpty()
    val histories = client?.chatStore?.history?.collectAsStateWithLifecycle()?.value.orEmpty()
    val selectedSummary = chats.firstOrNull { it.key == selectedChat }

    fun openChat(chat: TdAccountChatKey) {
        if (chat.accountId != accountId || !available) return
        selectedChat = chat
        error = false
        scope.launch {
            try { coordinator.chatSession(accountId).loadHistory(chat) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
        }
    }

    Column(Modifier.fillMaxSize().background(palette.screen).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            IconButton(onClick = {
                if (selectedChat != null) selectedChat = null else onBack()
            }) { Icon(Icons.Rounded.ArrowBack, null, tint = palette.textPrimary) }
            Column(Modifier.weight(1f).padding(start = 8.dp, top = 10.dp)) {
                Text(selectedSummary?.title ?: stringResource(R.string.secondary_chats_title),
                    fontWeight = FontWeight.SemiBold, fontSize = 21.sp,
                    color = palette.textPrimary)
                Text(stringResource(R.string.secondary_chats_readonly),
                    fontSize = 12.sp, color = palette.textSecondary)
            }
        }
        if (!available) {
            Text(stringResource(R.string.secondary_chats_unavailable),
                color = palette.danger, modifier = Modifier.padding(20.dp))
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.secondary_chats_go_back))
            }
            return@Column
        }
        if (error) {
            Text(stringResource(R.string.secondary_chats_load_error),
                color = palette.danger, modifier = Modifier.padding(horizontal = 20.dp))
        }

        if (selectedChat == null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    scope.launch {
                        try { coordinator.chatSession(accountId).loadChats(); error = false }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { error = true }
                    }
                }) { Text(stringResource(R.string.secondary_chats_refresh)) }
            }
            if (chats.isEmpty()) {
                Text(
                    if (loading) stringResource(R.string.secondary_chats_loading)
                    else stringResource(R.string.secondary_chats_empty),
                    color = palette.textSecondary, modifier = Modifier.padding(20.dp))
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(chats, key = { it.key.chatId }) { chat ->
                    Column(Modifier.fillMaxWidth().clickable { openChat(chat.key) }
                        .padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(chat.title, modifier = Modifier.weight(1f),
                                color = palette.textPrimary, fontWeight = FontWeight.Medium)
                            if (chat.unreadCount > 0) Text(chat.unreadCount.toString(),
                                color = palette.accent)
                        }
                        if (chat.preview.isNotBlank()) Text(chat.preview,
                            color = palette.textSecondary, maxLines = 2, fontSize = 13.sp)
                    }
                }
            }
        } else {
            val chat = selectedChat!!
            val messages = histories[chat].orEmpty()
            if (messages.isEmpty()) {
                Text(stringResource(R.string.secondary_chats_history_empty),
                    color = palette.textSecondary, modifier = Modifier.padding(20.dp))
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(messages, key = { it.key.messageId }) { message ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
                        horizontalArrangement = if (message.outgoing)
                            Arrangement.End else Arrangement.Start) {
                        Text(message.text,
                            color = palette.textPrimary, fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth(0.85f)
                                .background(palette.surface)
                                .padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                }
            }
        }
    }
}
