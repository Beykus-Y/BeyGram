package com.beykus.beygram.ui.screens.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.GlobalSearchFilter
import com.beykus.beygram.domain.model.InlineBotResult
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.SharedMediaKind
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackOrigin
import com.beykus.beygram.playback.PlaybackQueueSource
import com.beykus.beygram.playback.playbackItem
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.LocalAutoDownloadFile
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * «Настройки → Звук и воспроизведение → Музыка». Ищет музыкальные аудиосообщения во всех доступных аккаунту чатах
 * через TDLib-фильтр аудио; публичный поиск треков здесь не подключён. Нажатие на трек ставит в очередь общего
 * плеера все найденные треки, начиная с выбранного, без пересылки и без отправки в чат.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicSearchScreen(
    accountId: String,
    conversation: ConversationRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val automaticDownload = LocalAutoDownloadFile.current
    var query by rememberSaveable(accountId) { mutableStateOf("") }
    var results by remember(accountId) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var chats by remember(accountId) { mutableStateOf<Map<Long, ChatPreview>>(emptyMap()) }
    var nextOffset by remember(accountId) { mutableStateOf("") }
    var searchedQuery by remember(accountId) { mutableStateOf<String?>(null) }
    var loading by remember(accountId) { mutableStateOf(false) }
    var error by remember(accountId) { mutableStateOf<String?>(null) }
    val searchJob = remember(accountId) { mutableStateOf<Job?>(null) }
    // Public results come from the optional music bot. They have their own job and state, so a bot failure
    // never touches the account's own results.
    var publicResults by remember(accountId) { mutableStateOf<List<InlineBotResult>>(emptyList()) }
    var publicAvailable by remember(accountId) { mutableStateOf(true) }
    val publicJob = remember(accountId) { mutableStateOf<Job?>(null) }
    DisposableEffect(accountId) { onDispose { searchJob.value?.cancel(); publicJob.value?.cancel() } }

    fun runPublicSearch(needle: String) {
        publicJob.value?.cancel()
        publicResults = emptyList()
        publicAvailable = true
        publicJob.value = scope.launch {
            conversation.musicBotSearch(needle).onSuccess { page ->
                publicAvailable = page != null
                publicResults = page?.results.orEmpty()
            }.onFailure {
                publicAvailable = false
                publicResults = emptyList()
            }
        }
    }

    fun runSearch(text: String, offset: String) {
        val needle = text.trim()
        if (needle.isEmpty()) return
        searchJob.value?.cancel()
        if (offset.isEmpty()) runPublicSearch(needle)
        loading = true
        error = null
        searchJob.value = scope.launch {
            val result = conversation.searchGlobalMessages(needle, offset, GlobalSearchFilter(kind = SharedMediaKind.Music))
            loading = false
            result.onSuccess { page ->
                val audio = page.messages.filter { it.media?.kind == MediaKind.Audio }
                results = if (offset.isEmpty()) audio else results + audio
                chats = if (offset.isEmpty()) page.chats else chats + page.chats
                nextOffset = page.nextOffset
                searchedQuery = needle
            }.onFailure {
                error = it.userMessage(context, R.string.music_search_failed)
            }
        }
    }

    // Debounce: each keystroke restarts this effect, so only a pause in typing reaches the server.
    LaunchedEffect(query) {
        // A changed query drops the old request, results and offset at once, so nothing from it can publish
        // or paginate during the debounce.
        searchJob.value?.cancel()
        publicJob.value?.cancel()
        publicResults = emptyList()
        publicAvailable = true
        results = emptyList()
        chats = emptyMap()
        nextOffset = ""
        searchedQuery = null
        loading = false
        error = null
        if (query.isBlank()) return@LaunchedEffect
        delay(350)
        runSearch(query, "")
    }

    // Titles for the rows only. Playback rebuilds the queue at tap time so file paths are current.
    val rows = remember(results, chats) {
        results.mapNotNull { message -> musicItem(message, chats, observeFile, accountId) }
    }

    fun play(message: ChatMessage) {
        val queue = results.mapNotNull { musicItem(it, chats, observeFile, accountId) }
        val index = queue.indexOfFirst { it.origin?.chatId == message.chatId && it.origin?.messageId == message.id }
        if (index < 0) return
        PlaybackEngine.playQueue(
            context,
            queue,
            index,
            PlaybackQueueSource(accountId, observeFile, downloadFile, automaticDownload)
        )
    }

    Scaffold(
        containerColor = palette.screen,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_music_title),
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), tint = palette.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.music_search_hint)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (query.isBlank()) Text(
                stringResource(R.string.music_search_intro),
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            error?.let {
                Text(it, color = palette.warningText, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            if (loading && results.isEmpty()) Row(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) { CircularProgressIndicator() }
            if (!loading && searchedQuery != null && results.isEmpty() && error == null) Text(
                stringResource(R.string.music_search_empty),
                color = palette.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            LazyColumn(Modifier.weight(1f)) {
                items(results, key = { "${it.chatId}:${it.id}" }) { message ->
                    val item = rows.firstOrNull { it.origin?.messageId == message.id && it.origin?.chatId == message.chatId }
                    if (item != null) MusicRow(
                        title = item.title,
                        subtitle = chats[message.chatId]?.title.orEmpty(),
                        onClick = { play(message) }
                    )
                }
                if (searchedQuery != null) {
                    item(key = "public_header") {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(stringResource(R.string.music_public_title), color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.music_public_note), color = palette.textSecondary, fontSize = 13.sp)
                        }
                    }
                    if (!publicAvailable) item(key = "public_unavailable") {
                        Text(
                            stringResource(R.string.music_public_unavailable),
                            color = palette.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(publicResults, key = { "public:${it.queryId}:${it.id}" }) { result ->
                        MusicRow(title = result.title, subtitle = result.description, onClick = null)
                    }
                }
                if (nextOffset.isNotEmpty() && !loading) item(key = "more") {
                    Button(
                        onClick = { searchedQuery?.let { runSearch(it, nextOffset) } },
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) { Text(stringResource(R.string.music_load_more)) }
                }
            }
        }
    }
}

@Composable
private fun MusicRow(title: String, subtitle: String, onClick: (() -> Unit)?) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(palette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = palette.accentInk, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = palette.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Трек для плеера из сообщения. Секретные и одноразовые сообщения не попадают в очередь; защищённый чат играет
 * как секретный, то есть без повтора после конца, как и в общем списке медиа.
 */
private fun musicItem(
    message: ChatMessage,
    chats: Map<Long, ChatPreview>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    accountId: String
): PlaybackItem? {
    if (message.isViewOnce) return null
    val media = message.media?.takeUnless { it.isSecret || it.isSecretChat } ?: return null
    if (media.kind != MediaKind.Audio) return null
    val chat = chats[message.chatId]
    val file = observeFile(media.fileId).value
    // Private playback starts from a local file only, so a protected track that is not downloaded is left out.
    if (chat?.hasProtectedContent == true && file.localPath.isNullOrEmpty()) return null
    val item = media.playbackItem(
        file.localPath.orEmpty(),
        chat?.title.orEmpty(),
        PlaybackOrigin(message.chatId, message.id, accountId),
        accountId = accountId,
        sizeBytes = file.size
    )
    return item.copy(secret = item.secret || chat?.hasProtectedContent == true)
}
