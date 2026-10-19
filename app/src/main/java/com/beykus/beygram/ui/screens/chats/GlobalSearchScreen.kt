package com.beykus.beygram.ui.screens.chats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.domain.search.appendDistinctBy
import com.beykus.beygram.ui.components.SearchField
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.MessageContextDialog
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val SEARCH_DEBOUNCE_MILLIS = 400L

/**
 * Поиск сообщений по всем чатам аккаунта. Каждое изменение текста перезапускает LaunchedEffect,
 * поэтому запрос устаревшего текста отменяется и его результат не попадает в выдачу.
 */
@Composable
fun GlobalSearchScreen(
    initialQuery: String,
    conversation: ConversationRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var hits by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var nextOffset by remember { mutableStateOf("") }
    var loadingMore by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<ChatMessage?>(null) }
    BackHandler(onBack = onBack)

    LaunchedEffect(query) {
        hits = emptyList()
        nextOffset = ""
        failed = false
        if (query.isBlank()) return@LaunchedEffect
        delay(SEARCH_DEBOUNCE_MILLIS)
        searching = true
        conversation.searchGlobalMessages(query, "")
            .onSuccess { page ->
                hits = page.messages
                nextOffset = page.nextOffset
            }
            .onFailure { failed = true }
        searching = false
    }

    Column(
        Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
    ) {
        ProfileToolbar(stringResource(R.string.global_search_title), onBack)
        SearchField(query = query, onChange = { query = it }, placeholder = stringResource(R.string.chats_search_placeholder))
        val empty = when {
            query.isBlank() -> stringResource(R.string.global_search_idle)
            searching -> stringResource(R.string.global_search_loading)
            failed -> stringResource(R.string.global_search_failed)
            hits.isEmpty() -> stringResource(R.string.global_search_empty)
            else -> null
        }
        if (empty != null) {
            Text(empty, color = palette.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(hits, key = { "${it.chatId}:${it.id}" }) { message ->
                Column(
                    Modifier.fillMaxWidth().clickable { opened = message }.padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(message.text, color = palette.textPrimary, fontSize = 15.sp, maxLines = 2)
                    Text(message.time, color = palette.textSecondary, fontSize = 12.sp)
                }
            }
            if (nextOffset.isNotEmpty() && hits.isNotEmpty()) {
                item {
                    TextButton(
                        onClick = {
                            if (loadingMore) return@TextButton
                            loadingMore = true
                            scope.launch {
                                conversation.searchGlobalMessages(query, nextOffset)
                                    .onSuccess { page ->
                                        hits = appendDistinctBy(hits, page.messages) { it.chatId to it.id }
                                        nextOffset = page.nextOffset
                                    }
                                    .onFailure { failed = true }
                                loadingMore = false
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) { Text(stringResource(R.string.global_search_more)) }
                }
            }
        }
    }

    opened?.let { message ->
        MessageContextDialog(
            chatId = message.chatId,
            targetId = message.id,
            conversation = conversation,
            observeFile = observeFile,
            downloadFile = downloadFile,
            onClose = { opened = null }
        )
    }
}
