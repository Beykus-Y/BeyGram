package com.beykus.beygram.ui.screens.playback

import androidx.compose.foundation.background
import com.beykus.beygram.ui.components.topAppBarSurface
import com.beykus.beygram.ui.design.styledShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import com.beykus.beygram.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.runtime.key
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.data.music.provider.ProviderTrack
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.screens.chats.FilterChip
import com.beykus.beygram.ui.screens.music.MUSIC_SEARCH_PREVIEW_ROWS
import com.beykus.beygram.ui.screens.music.MusicCardColumn
import com.beykus.beygram.ui.screens.music.MusicGroupHeading
import com.beykus.beygram.ui.screens.music.MusicSearchMessage
import com.beykus.beygram.ui.screens.music.MusicSearchSection
import com.beykus.beygram.ui.screens.music.MusicSearchSkeletonRows
import com.beykus.beygram.ui.screens.music.MusicTrackRowLayout
import com.beykus.beygram.ui.screens.music.rememberIsCurrentItem
import com.beykus.beygram.ui.screens.music.rememberIsCurrentTrack
import com.beykus.beygram.R
import com.beykus.beygram.data.music.provider.ProviderArtwork
import com.beykus.beygram.data.music.PlaylistTrack
import com.beykus.beygram.data.music.toPlaylistTrack
import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.GlobalSearchFilter
import com.beykus.beygram.domain.model.InlineBotResult
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageExtraDraft
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.model.SharedMediaKind
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.playback.MusicBotCache
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackOrigin
import com.beykus.beygram.playback.PlaybackQueueSource
import com.beykus.beygram.ui.screens.music.reportMusicOutcome
import com.beykus.beygram.data.music.newMusicItemId
import com.beykus.beygram.data.music.MusicLibraries
import com.beykus.beygram.playback.BotTrackResolver
import com.beykus.beygram.playback.playbackItem
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.SearchField
import com.beykus.beygram.ui.screens.chat.LocalAutoDownloadFile
import com.beykus.beygram.ui.screens.music.AddToPlaylistSheet
import com.beykus.beygram.ui.screens.music.ProviderMusicHosts
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** A cancelled or superseded request must never repopulate another search's UI. */
internal fun isCurrentMusicSearchResult(
    requestGeneration: Long,
    latestGeneration: Long,
    requestedQuery: String,
    activeQuery: String
): Boolean = requestGeneration == latestGeneration && requestedQuery == activeQuery.trim()

/**
 * «Настройки → Звук и воспроизведение → Музыка». Ищет музыкальные аудиосообщения во всех доступных аккаунту чатах
 * через TDLib-фильтр аудио, а публичный поиск обрабатывает отдельным запросом к музыкальному боту.
 * Нажатие на трек ставит в очередь общего
 * плеера все найденные треки, начиная с выбранного, без пересылки и без отправки в чат.
 *
 * С [onPick] это выбор трека для «Прикрепить → Музыка» в чате [pickChatId]: у строки появляется кнопка выбора,
 * а сам трек уходит только после обычной кнопки «Отправить» в поле ввода.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicSearchScreen(
    accountId: String,
    conversation: ConversationRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onBack: () -> Unit,
    pickChatId: Long = 0L,
    onPick: ((MessageExtraDraft) -> Unit)? = null,
    focusOnOpen: Boolean = false,
    providerFirst: Boolean = false
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val automaticDownload = LocalAutoDownloadFile.current
    var query by rememberSaveable(accountId) { mutableStateOf("") }
    // Opened from the field of the music screen: the keyboard comes up at once, but only on the first open, not when
    // the screen comes back from the background or a rotation.
    val searchFocus = remember { FocusRequester() }
    var focusRequested by rememberSaveable { mutableStateOf(false) }
    // The track a row asked to add to a playlist. Only results that are sent music with an origin qualify.
    var addTrack by remember(accountId) { mutableStateOf<PlaylistTrack?>(null) }
    var results by remember(accountId) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var chats by remember(accountId) { mutableStateOf<Map<Long, ChatPreview>>(emptyMap()) }
    var nextOffset by remember(accountId) { mutableStateOf("") }
    var searchedQuery by remember(accountId) { mutableStateOf<String?>(null) }
    var loading by remember(accountId) { mutableStateOf(false) }
    var error by remember(accountId) { mutableStateOf<String?>(null) }
    val searchJob = remember(accountId) { mutableStateOf<Job?>(null) }
    val localRequestGeneration = remember(accountId) { AtomicLong() }
    // Public results come from the optional music bot. They have their own job and state, so a bot failure
    // never touches the account's own results.
    val latestSelection = remember(accountId) { AtomicLong() }
    // The public result whose track is being fetched; the row shows a spinner, and a failed fetch says so.
    var loadingBotKey by remember(accountId) { mutableStateOf<String?>(null) }
    var publicResults by remember(accountId) { mutableStateOf<List<InlineBotResult>>(emptyList()) }
    var publicAvailable by remember(accountId) { mutableStateOf(true) }
    var publicLoading by remember(accountId) { mutableStateOf(false) }
    var publicSearchedQuery by remember(accountId) { mutableStateOf<String?>(null) }
    val publicJob = remember(accountId) { mutableStateOf<Job?>(null) }
    val publicRequestGeneration = remember(accountId) { AtomicLong() }
    // Optional music source (DEV build only). Its results sit below the local ones and play through the shared queue.
    val providerHost = remember { ProviderMusicHosts.yandex() }
    // Covers of chat tracks are small pictures fetched on their own, never a download the user asked for.
    val backgroundDownload = remember(conversation) { (conversation as? TelegramRepository)?.let { it::downloadFileInBackground } }

    /** Keeps the query in the recent list once the user did something with a result. */
    fun keepQuery() = MusicSearchHistory.remember(context, accountId, query)
    DisposableEffect(accountId, conversation) {
        onDispose {
            localRequestGeneration.incrementAndGet()
            publicRequestGeneration.incrementAndGet()
            searchJob.value?.cancel()
            publicJob.value?.cancel()
        }
    }

    fun runPublicSearch(needle: String) {
        val requestGeneration = publicRequestGeneration.incrementAndGet()
        publicJob.value?.cancel()
        publicResults = emptyList()
        publicAvailable = true
        publicLoading = true
        publicSearchedQuery = null
        publicJob.value = scope.launch {
            val result = conversation.musicBotSearch(needle, contextChatId = pickChatId)
            if (!isActive || !isCurrentMusicSearchResult(requestGeneration, publicRequestGeneration.get(), needle, query)) return@launch
            result.onSuccess { page ->
                publicAvailable = page != null
                publicResults = page?.results.orEmpty()
            }.onFailure {
                publicAvailable = false
                publicResults = emptyList()
            }
            publicLoading = false
            publicSearchedQuery = needle
        }
    }

    fun runSearch(text: String, offset: String) {
        val needle = text.trim()
        if (needle.isEmpty()) return
        val requestGeneration = localRequestGeneration.incrementAndGet()
        searchJob.value?.cancel()
        if (offset.isEmpty()) runPublicSearch(needle)
        loading = true
        error = null
        searchJob.value = scope.launch {
            val result = conversation.searchGlobalMessages(needle, offset, GlobalSearchFilter(kind = SharedMediaKind.Music))
            if (!isActive || !isCurrentMusicSearchResult(requestGeneration, localRequestGeneration.get(), needle, query)) return@launch
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
    LaunchedEffect(query, accountId, conversation) {
        // Invalidate late TDLib/bot callbacks even if their underlying operation ignores cancellation.
        localRequestGeneration.incrementAndGet()
        publicRequestGeneration.incrementAndGet()
        // A changed query drops the old request, results and offset at once, so nothing from it can publish
        // or paginate during the debounce.
        searchJob.value?.cancel()
        publicJob.value?.cancel()
        publicResults = emptyList()
        publicAvailable = true
        publicLoading = false
        publicSearchedQuery = null
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

    // A bot result in the queue is only a name: the file is fetched from the bot when the row becomes current or next.
    fun botQueueItem(result: InlineBotResult): PlaybackItem = PlaybackItem(
        key = BotTrackResolver.keyOf(result.queryId, result.id),
        kind = MediaKind.Audio,
        path = "",
        title = result.title,
        durationSeconds = 0,
        performer = result.description,
        trackTitle = result.title,
        accountId = accountId
    )

    fun saveBotTrack(result: InlineBotResult) {
        keepQuery()
        scope.launch {
            val saved = conversation.saveMusicBotTrack(result.queryId, result.id).getOrNull()
            if (saved == null) {
                Toast.makeText(context, context.getString(R.string.music_bot_failed), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val track = PlaylistTrack(
                itemId = newMusicItemId(),
                title = saved.title.ifBlank { result.title },
                performer = saved.performer,
                album = "",
                durationSeconds = saved.durationSeconds,
                origin = PlaybackOrigin(saved.chatId, saved.messageId, accountId),
                addedAt = System.currentTimeMillis()
            )
            val repository = MusicLibraries.get(context)
            // The track has a fresh message of its own, so it is never in the favorites yet and the toggle adds it.
            context.reportMusicOutcome(repository.toggleFavorite(track))
            Toast.makeText(context, context.getString(R.string.music_bot_saved_favorite), Toast.LENGTH_SHORT).show()
        }
    }

    fun playBotTrack(result: InlineBotResult) {
        keepQuery()
        val selection = latestSelection.incrementAndGet()
        // The queue is the list the user tapped in; a new query while the track downloads must not change it.
        val tappedResults = publicResults
        val resultKey = "${result.queryId}:${result.id}"
        loadingBotKey = resultKey
        scope.launch {
            val outcome = conversation.musicBotTrack(result.queryId, result.id)
            if (selection == latestSelection.get() && loadingBotKey == resultKey) loadingBotKey = null
            val notAvailable = outcome.isFailure || outcome.getOrNull() == null
            if (notAvailable && isActive && selection == latestSelection.get()) {
                Toast.makeText(context, context.getString(R.string.music_bot_failed), Toast.LENGTH_SHORT).show()
            }
            outcome.onSuccess { track ->
                if (track == null) return@onSuccess
                // The screen may have left while the cleanup ran. A cancelled caller must not start playback.
                if (!isActive) {
                    MusicBotCache.discard(track.path)
                    return@onSuccess
                }
                // A newer tap wins: an older download that finishes later must not replace what the user picked last.
                if (selection != latestSelection.get()) {
                    MusicBotCache.discard(track.path)
                    return@onSuccess
                }
                val key = "bot:${result.queryId}:${result.id}"
                val playing = PlaybackEngine.state.value.item?.takeIf { it.key == key }
                // Tapping the same result again: the player still reads the copy it has, so that copy is kept and the new one dropped.
                val playingPath = playing?.path?.takeIf { File(it).exists() }
                if (playingPath != null) MusicBotCache.discard(track.path)
                // The player rebuilds its source only when the key changes. If the old copy was evicted, the new copy gets a new key.
                val itemKey = if (playing != null && playingPath == null) "$key:${File(track.path).name}" else key
                val item = PlaybackItem(
                    key = itemKey,
                    kind = MediaKind.Audio,
                    path = playingPath ?: track.path,
                    title = track.title,
                    durationSeconds = track.durationSeconds,
                    performer = track.performer,
                    trackTitle = track.title,
                    mimeType = track.mimeType,
                    sizeBytes = track.sizeBytes,
                    accountId = accountId
                )
                // The tapped result plays now; the other visible results follow and are fetched when their turn comes.
                BotTrackResolver.configure { queryId, resultId -> conversation.musicBotTrack(queryId, resultId) }
                BotTrackResolver.reset()
                val queue = tappedResults.map { other ->
                    if (other.queryId == result.queryId && other.id == result.id) item else botQueueItem(other)
                }
                val startIndex = queue.indexOfFirst { it.key == itemKey }.coerceAtLeast(0)
                PlaybackEngine.playQueue(
                    context,
                    queue.ifEmpty { listOf(item) },
                    if (queue.isEmpty()) 0 else startIndex,
                    PlaybackQueueSource(accountId, observeFile, downloadFile, automaticDownload)
                )
                // The new copy stays pending until the prune has run; the previous copy goes once the queue no longer names it.
                MusicBotCache.prune(File(context.cacheDir, MusicBotCache.DIR_NAME), PlaybackEngine.musicPathsInUse())
                MusicBotCache.handOver(track.path)
            }
        }
    }

    fun enqueueRow(item: PlaybackItem, atEnd: Boolean) {
        keepQuery()
        PlaybackEngine.enqueue(context, item, PlaybackQueueSource(accountId, observeFile, downloadFile, automaticDownload), atEnd)
        Toast.makeText(
            context,
            context.getString(if (atEnd) R.string.queue_added_end else R.string.queue_added_next),
            Toast.LENGTH_SHORT
        ).show()
    }

    fun enqueueBot(result: InlineBotResult, atEnd: Boolean) {
        BotTrackResolver.configure { queryId, resultId -> conversation.musicBotTrack(queryId, resultId) }
        enqueueRow(botQueueItem(result), atEnd)
    }

    fun play(message: ChatMessage) {
        keepQuery()
        latestSelection.incrementAndGet()
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

    val hasProvider = providerHost != null && onPick == null
    val sources = remember(hasProvider) { musicSearchSources(hasProvider) }
    var source by rememberSaveable(accountId) {
        mutableStateOf(if (providerFirst && hasProvider) MusicSearchSource.Provider else MusicSearchSource.All)
    }
    if (source !in sources) source = MusicSearchSource.All
    val historyFlow = remember(context, accountId) { MusicSearchHistory.observe(context, accountId) }
    val history by historyFlow.collectAsStateWithLifecycle()
    val providerPlay: (List<ProviderTrack>, Int) -> Unit = { tracks, index ->
        keepQuery()
        ProviderArtwork.playTracks(context, tracks, index)
    }
    val trimmed = query.trim()
    val localDone = searchedQuery == trimmed
    val publicDone = publicSearchedQuery == trimmed

    @Composable
    fun ChatRow(message: ChatMessage) {
        val item = rows.firstOrNull { it.origin?.messageId == message.id && it.origin?.chatId == message.chatId } ?: return
        val chat = chats[message.chatId]
        val protectedChat = chat?.hasProtectedContent == true
        MusicSearchTrackRow(
            title = item.trackTitle.ifBlank { item.title },
            subtitle = listOf(item.performer, chat?.title.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
            durationSeconds = item.durationSeconds,
            coverPath = rememberSearchCover(message, protectedChat, observeFile, backgroundDownload),
            current = rememberIsCurrentTrack(item.origin),
            onClick = { play(message) },
            onPlayNext = if (onPick == null) { { enqueueRow(item, atEnd = false) } } else null,
            onEnqueue = if (onPick == null) { { enqueueRow(item, atEnd = true) } } else null,
            onSend = onPick?.let { pick -> musicFileDraft(message, chats, item.title)?.let { draft -> { keepQuery(); pick(draft) } } },
            onAddToPlaylist = if (onPick == null && !protectedChat) {
                item.toPlaylistTrack()?.let { track -> { keepQuery(); addTrack = track } }
            } else {
                null
            }
        )
    }

    @Composable
    fun PublicRow(result: InlineBotResult) {
        MusicSearchTrackRow(
            title = result.title,
            subtitle = result.description,
            durationSeconds = 0,
            coverPath = null,
            current = rememberIsCurrentItem(BotTrackResolver.keyOf(result.queryId, result.id)),
            onClick = { playBotTrack(result) },
            loading = loadingBotKey == "${result.queryId}:${result.id}",
            onPlayNext = if (onPick == null) { { enqueueBot(result, atEnd = false) } } else null,
            onEnqueue = if (onPick == null) { { enqueueBot(result, atEnd = true) } } else null,
            onSaveFavorite = if (onPick == null) { { saveBotTrack(result) } } else null,
            onSend = onPick?.let { pick ->
                { keepQuery(); pick(MessageExtraDraft.MusicBotResult(result.queryId, result.id, result.title)) }
            }
        )
    }

    /** States of the chats section above its rows: searching, failure, nothing found. */
    @Composable
    fun ChatStates() {
        error?.let { MusicSearchMessage(it) }
        // The debounce counts as searching too, so the section keeps its shape while the user types.
        if (results.isEmpty() && error == null && (loading || !localDone)) MusicSearchSkeletonRows()
        if (!loading && localDone && results.isEmpty() && error == null) {
            MusicSearchMessage(stringResource(R.string.music_local_empty))
        }
    }

    /** States of the public section above its rows. */
    @Composable
    fun PublicStates() {
        if (publicLoading || !publicDone) MusicSearchSkeletonRows()
        if (!publicLoading && publicDone && !publicAvailable) MusicSearchMessage(stringResource(R.string.music_public_unavailable))
        if (!publicLoading && publicDone && publicAvailable && publicResults.isEmpty()) {
            MusicSearchMessage(stringResource(R.string.music_public_empty))
        }
    }

    Scaffold(
        containerColor = palette.screen,
        topBar = {
            TopAppBar(
                modifier = Modifier.topAppBarSurface(),
                title = {
                    Text(
                        stringResource(if (onPick != null) R.string.music_attach_title else R.string.music_search_action),
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
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                SearchField(
                    query = query,
                    onChange = { query = it },
                    placeholder = stringResource(R.string.music_search_hint),
                    focusRequester = searchFocus
                )
                // Runs inside the Scaffold content, after the field is attached; the request counts only once it worked.
                LaunchedEffect(focusOnOpen) {
                    if (focusOnOpen && !focusRequested) {
                        focusRequested = runCatching { searchFocus.requestFocus() }.isSuccess
                    }
                }
            }
            LazyRow(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sources, key = { it.name }) { option ->
                    FilterChip(
                        title = when (option) {
                            MusicSearchSource.All -> stringResource(R.string.music_search_filter_all)
                            MusicSearchSource.Chats -> stringResource(R.string.music_search_filter_chats)
                            MusicSearchSource.Provider -> providerHost?.tabLabel().orEmpty()
                            MusicSearchSource.Public -> stringResource(R.string.music_search_filter_public)
                        },
                        selected = source == option,
                        count = null,
                        onClick = { source = option }
                    )
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (trimmed.isEmpty()) {
                    if (history.isNotEmpty()) {
                        item(key = "history") {
                            Column(Modifier.fillMaxWidth()) {
                                MusicGroupHeading(
                                    title = stringResource(R.string.music_search_recent),
                                    actionLabel = stringResource(R.string.music_search_recent_clear),
                                    onAction = { MusicSearchHistory.clear(context, accountId) }
                                )
                                MusicCardColumn {
                                    history.forEach { recent ->
                                        RecentQueryRow(
                                            text = recent,
                                            onClick = { query = recent },
                                            onRemove = { MusicSearchHistory.remove(context, accountId, recent) }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        item(key = "intro") {
                            MusicSearchIntro(
                                stringResource(if (onPick != null) R.string.music_attach_intro else R.string.music_search_intro)
                            )
                        }
                    }
                    return@LazyColumn
                }
                when (source) {
                    MusicSearchSource.All -> {
                        item(key = "chats_overview") {
                            MusicSearchSection(
                                title = stringResource(R.string.music_local_title),
                                onMore = if (results.size > MUSIC_SEARCH_PREVIEW_ROWS || nextOffset.isNotEmpty()) {
                                    { source = MusicSearchSource.Chats }
                                } else {
                                    null
                                }
                            ) {
                                ChatStates()
                                results.take(MUSIC_SEARCH_PREVIEW_ROWS).forEach { message ->
                                    key("${message.chatId}:${message.id}") { ChatRow(message) }
                                }
                            }
                        }
                        if (hasProvider) item(key = "provider_overview") {
                            providerHost?.SearchResults(
                                query = query,
                                onPlay = providerPlay,
                                limit = MUSIC_SEARCH_PREVIEW_ROWS,
                                onMore = { source = MusicSearchSource.Provider }
                            )
                        }
                        item(key = "public_overview") {
                            MusicSearchSection(
                                title = stringResource(R.string.music_public_title),
                                onMore = if (publicResults.size > MUSIC_SEARCH_PREVIEW_ROWS) {
                                    { source = MusicSearchSource.Public }
                                } else {
                                    null
                                }
                            ) {
                                PublicStates()
                                publicResults.take(MUSIC_SEARCH_PREVIEW_ROWS).forEach { result ->
                                    key("public:${result.queryId}:${result.id}") { PublicRow(result) }
                                }
                            }
                        }
                    }
                    MusicSearchSource.Chats -> {
                        item(key = "chats_header") {
                            MusicSearchSection(title = stringResource(R.string.music_local_title), onMore = null, carded = false) {
                                ChatStates()
                            }
                        }
                        items(results, key = { "${it.chatId}:${it.id}" }) { message -> ChatRow(message) }
                        if (nextOffset.isNotEmpty() && !loading) item(key = "more") {
                            Button(
                                onClick = { searchedQuery?.let { runSearch(it, nextOffset) } },
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            ) { Text(stringResource(R.string.music_load_more)) }
                        }
                    }
                    MusicSearchSource.Provider -> item(key = "provider_source") {
                        providerHost?.SearchResults(query = query, onPlay = providerPlay, limit = null, onMore = null)
                    }
                    MusicSearchSource.Public -> {
                        item(key = "public_header") {
                            MusicSearchSection(
                                title = stringResource(R.string.music_public_title),
                                onMore = null,
                                carded = false,
                                note = stringResource(R.string.music_public_note)
                            ) {
                                PublicStates()
                            }
                        }
                        items(publicResults, key = { "public:${it.queryId}:${it.id}" }) { result -> PublicRow(result) }
                    }
                }
            }
        }
    }
    addTrack?.let { track ->
        AddToPlaylistSheet(accountId = accountId, track = track, onDismiss = { addTrack = null })
    }
}

/** The sources the music search can show; the optional source only where the build has one and not when attaching. */
internal enum class MusicSearchSource { All, Chats, Provider, Public }

internal fun musicSearchSources(hasProvider: Boolean): List<MusicSearchSource> =
    MusicSearchSource.entries.filter { it != MusicSearchSource.Provider || hasProvider }

/** The small cover of a chat track, downloaded in the background. Protected and secret tracks show the placeholder. */
@Composable
private fun rememberSearchCover(
    message: ChatMessage,
    protectedChat: Boolean,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    backgroundDownload: ((Int) -> Unit)?
): String? {
    val media = message.media
    val thumb = media?.thumbnailFileId?.takeIf {
        !protectedChat && !media.isSecret && !media.isSecretChat && !message.isViewOnce && it != media.fileId && it > 0
    } ?: return null
    val flow = remember(thumb) { observeFile(thumb) }
    val file by flow.collectAsStateWithLifecycle()
    val path = file.localPath?.takeIf { it.isNotBlank() }
    LaunchedEffect(thumb, path == null) {
        if (path == null) backgroundDownload?.invoke(thumb)
    }
    return path
}

/** Shown on an empty field before anything was searched: what the search covers. */
@Composable
private fun MusicSearchIntro(text: String) {
    val palette = beyGramPalette()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(palette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = palette.accentInk, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.music_search_intro_title),
            color = palette.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(text, color = palette.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

/** A recent query: tap searches it again, the cross forgets it. */
@Composable
private fun RecentQueryRow(text: String, onClick: () -> Unit, onRemove: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.History, null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Close, stringResource(R.string.music_search_recent_remove, text), tint = palette.textSecondary)
        }
    }
}

/** A search result in the shared track row, with the queue menu and the add or send buttons of the search. */
@Composable
private fun MusicSearchTrackRow(
    title: String,
    subtitle: String,
    durationSeconds: Int,
    coverPath: String?,
    current: Boolean,
    onClick: (() -> Unit)?,
    onSend: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onEnqueue: (() -> Unit)? = null,
    onSaveFavorite: (() -> Unit)? = null,
    loading: Boolean = false
) {
    val palette = beyGramPalette()
    var menuOpen by remember { mutableStateOf(false) }
    MusicTrackRowLayout(
        title = title,
        subtitle = subtitle,
        durationSeconds = durationSeconds,
        coverPath = coverPath,
        unavailable = false,
        current = current,
        onClick = onClick,
        trailing = {
            if (loading) CircularProgressIndicator(Modifier.padding(horizontal = 12.dp).size(22.dp), strokeWidth = 2.dp, color = palette.accent)
            if (onPlayNext != null || onEnqueue != null || onSaveFavorite != null) Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.music_track_menu), tint = palette.textSecondary)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    onPlayNext?.let { next ->
                        DropdownMenuItem(text = { Text(stringResource(R.string.queue_play_next)) }, onClick = { menuOpen = false; next() })
                    }
                    onEnqueue?.let { enqueue ->
                        DropdownMenuItem(text = { Text(stringResource(R.string.queue_add_to_end)) }, onClick = { menuOpen = false; enqueue() })
                    }
                    onSaveFavorite?.let { save ->
                        DropdownMenuItem(text = { Text(stringResource(R.string.music_bot_save_favorite)) }, onClick = { menuOpen = false; save() })
                    }
                }
            }
            if (onAddToPlaylist != null) IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Add, stringResource(R.string.music_add_track_action, title), tint = palette.accent)
            }
            if (onSend != null) IconButton(onClick = onSend, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.music_attach_pick, title), tint = palette.accent)
            }
        }
    )
}

/**
 * A chat track for the attachment menu, sent again by its server file id. Secret, view-once and protected tracks
 * cannot be sent on, so they get no send button.
 */
private fun musicFileDraft(message: ChatMessage, chats: Map<Long, ChatPreview>, label: String): MessageExtraDraft.MusicFile? {
    if (message.isViewOnce || chats[message.chatId]?.hasProtectedContent == true) return null
    val media = message.media?.takeUnless { it.isSecret || it.isSecretChat } ?: return null
    if (media.kind != MediaKind.Audio || media.fileId <= 0) return null
    val outgoing = OutgoingMedia(
        path = "",
        fileName = media.fileName,
        mimeType = media.mimeType,
        kind = MediaKind.Audio,
        durationSeconds = media.durationSeconds,
        remoteFileId = media.fileId,
        title = media.title,
        performer = media.performer
    )
    return MessageExtraDraft.MusicFile(outgoing, label)
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
