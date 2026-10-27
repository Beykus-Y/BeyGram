package com.beykus.beygram.ui.screens.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.GlobalSearchFilter
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.SharedMediaKind
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.components.SearchField
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import kotlinx.coroutines.delay

/** First iteration of #506. Searches accessible Telegram audio messages, not paid public posts. */
@Composable
fun MusicSearchScreen(
    accountId: String,
    conversation: ConversationRepository,
    onOpenMessage: (chatId: Long, forumTopicId: Long, messageId: Long) -> Unit,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    var query by remember(accountId) { mutableStateOf("") }
    // Query-scoped state makes late responses from a previous query/account invisible immediately.
    var results by remember(accountId, query) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var chats by remember(accountId, query) { mutableStateOf<Map<Long, ChatPreview>>(emptyMap()) }
    var requestedOffset by remember(accountId, query) { mutableStateOf("") }
    var nextOffset by remember(accountId, query) { mutableStateOf("") }
    var loading by remember(accountId, query) { mutableStateOf(query.isNotBlank()) }
    var failed by remember(accountId, query) { mutableStateOf(false) }
    var retry by remember(accountId, query) { mutableIntStateOf(0) }

    LaunchedEffect(accountId, conversation, query, requestedOffset, retry) {
        if (query.isBlank()) return@LaunchedEffect
        loading = true
        failed = false
        // Only the first page waits for the user to finish typing.
        if (requestedOffset.isEmpty()) delay(400L)
        conversation.searchGlobalMessages(
            query, requestedOffset, GlobalSearchFilter(kind = SharedMediaKind.Music)
        ).onSuccess { page ->
            val audio = page.messages.filter { message ->
                val media = message.media
                media != null && media.kind == MediaKind.Audio &&
                    !media.isSecret && !media.isSecretChat && !message.isViewOnce
            }
            results = if (requestedOffset.isEmpty()) audio else (results + audio).distinctBy { it.chatId to it.id }
            chats = chats + page.chats
            nextOffset = page.nextOffset.takeUnless { it == requestedOffset }.orEmpty()
        }.onFailure { failed = true }
        loading = false
    }

    Column(
        Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
    ) {
        ProfileToolbar(stringResource(R.string.music_search_title), onBack)
        SearchField(
            query = query,
            onChange = { query = it },
            placeholder = stringResource(R.string.music_search_hint)
        )

        if (query.isBlank()) {
            Text(
                stringResource(R.string.music_search_idle),
                color = palette.textSecondary,
                modifier = Modifier.padding(20.dp)
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                if (results.isEmpty() && !loading && !failed) {
                    item("empty") {
                        Text(stringResource(R.string.global_search_empty), color = palette.textSecondary,
                            modifier = Modifier.padding(20.dp))
                    }
                }
                items(results, key = { "${it.chatId}:${it.id}" }) { message ->
                    val media = message.media ?: return@items
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clickable(role = Role.Button) {
                                onOpenMessage(message.chatId, message.forumTopicId, message.id)
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.MusicNote, null, tint = palette.accent, modifier = Modifier.size(26.dp))
                        Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                media.title.ifBlank { media.fileName.ifBlank { stringResource(R.string.music_search_unknown) } },
                                color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            if (media.performer.isNotBlank()) {
                                Text(media.performer, color = palette.textSecondary, fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            val source = chats[message.chatId]?.title
                                ?: stringResource(R.string.global_search_chat_unknown)
                            val duration = media.durationSeconds.takeIf { it > 0 }
                                ?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }
                            Text(
                                listOfNotNull(source, duration).joinToString(" · "),
                                color = palette.textSecondary, fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = palette.textSecondary)
                    }
                }
                if (loading) {
                    item("loading") {
                        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
                if (failed) {
                    item("error") {
                        Column(Modifier.padding(20.dp)) {
                            Text(stringResource(R.string.global_search_failed), color = palette.textSecondary)
                            TextButton(onClick = { retry++ }) { Text(stringResource(R.string.music_search_retry)) }
                        }
                    }
                }
                if (!loading && !failed && nextOffset.isNotEmpty()) {
                    item("more") {
                        TextButton(
                            onClick = { requestedOffset = nextOffset },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) { Text(stringResource(R.string.global_search_more)) }
                    }
                }
            }
        }
    }
}
