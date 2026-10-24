package com.beykus.beygram.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.MessageAnimationConfig
import com.beykus.beygram.data.appearance.MessageAnimationPreset
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.domain.search.appendDistinctBy
import com.beykus.beygram.domain.search.isCurrentChatSearch
import com.beykus.beygram.data.media.VideoMessageSettingsStore
import com.beykus.beygram.data.media.ComposerRecordingMode
import com.beykus.beygram.data.presence.LastSeenStyle
import com.beykus.beygram.data.settings.AppLocale
import com.beykus.beygram.data.settings.ChatOverridesStore
import com.beykus.beygram.data.settings.TranslationSettingsStore
import com.beykus.beygram.data.settings.translationTargetLanguage
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackOrigin
import com.beykus.beygram.playback.LocalPlaybackAccountId
import com.beykus.beygram.ui.accessibility.accessibleTouchTarget
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.ChatAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.playback.NowPlayingBar
import com.beykus.beygram.ui.components.beyGramFilledTextFieldColors
import com.beykus.beygram.ui.components.beyGramTextFieldColors
import com.beykus.beygram.ui.text.resolve
import com.beykus.beygram.ui.text.userMessage
import kotlin.math.roundToInt
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** More than one transition can be in flight, but an accidental tap storm must not create an unbounded overlay. */
private const val MAX_ACTIVE_COMPOSER_LAUNCHES = 4

/** A zero-watermark jump walks history to its beginning; this guard keeps a huge chat from loading forever. */
private const val MAX_UNREAD_HISTORY_PAGES = 50
private const val UNREAD_HISTORY_PAGE_TIMEOUT_MS = 15_000L
/** Bound on forward paging when outgoing messages separate the read boundary from the first unread incoming row. */
private const val MAX_UNREAD_FORWARD_PAGES = 20

@OptIn(FlowPreview::class)
@Composable
fun ChatScreen(
    chat: ChatPreview, info: ProfileInfo?, lastSeenStyle: LastSeenStyle, history: ChatHistory, conversation: ConversationRepository, chats: List<ChatPreview>,
    observeFile: (Int) -> StateFlow<MediaFileState>, onDownloadFile: (Int) -> Unit,
    onBack: () -> Unit, onChatInfo: () -> Unit, connectionState: TelegramConnectionState,
    recordingSettings: VideoMessageSettingsStore, translationSettings: TranslationSettingsStore,
    chatOverrides: ChatOverridesStore, focusMessageId: Long = 0L, onFocusHandled: () -> Unit = {},
    forumTopicId: Long = 0L, forumTopicTitle: String? = null, topicCanPost: Boolean = true, topicUnreadCount: Int = 0,
    onServiceUser: (Long) -> Unit = {},
    messageAnimations: MessageAnimationConfig = MessageAnimationConfig(),
    accountId: String = com.beykus.beygram.data.tdlib.TdAccountStorageLayout.LEGACY_ACCOUNT_ID,
    accountLabel: String = ""
) {
    // One persistent state source for text, rich formatting and the selected reply target.
    val canPostHere = chat.canPost && topicCanPost
    val composerDraft by remember(conversation, chat.id, forumTopicId) {
        if (forumTopicId > 0L) conversation.observeForumTopicDraft(chat.id, forumTopicId) else conversation.observeMessageDraft(chat.id)
    }.collectAsStateWithLifecycle()
    val draft = composerDraft.text
    val draftEntities = composerDraft.entities
    val replyId = composerDraft.replyToMessageId
    fun updateDraft(transform: (com.beykus.beygram.domain.model.MessageDraft) -> com.beykus.beygram.domain.model.MessageDraft) =
        if (forumTopicId > 0L) conversation.updateForumTopicDraft(chat.id, forumTopicId, transform)
        else conversation.updateMessageDraft(chat.id, transform)
    fun clearReply() = updateDraft { it.copy(replyToMessageId = null) }
    var editEntities by remember(chat.id) { mutableStateOf<List<MessageTextEntity>>(emptyList()) }
    val botKeyboard by remember(conversation, chat.id) { conversation.observeBotKeyboard(chat.id) }.collectAsStateWithLifecycle()
    // Bumped whenever a reply starts, so the composer takes focus and the keyboard opens.
    var replyFocusRequest by remember(chat.id) { mutableIntStateOf(0) }
    // The message of the latest swipe; a slower capability check for an older swipe is ignored.
    var swipedReplyId by remember(chat.id) { mutableLongStateOf(0L) }
    var actionsId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var exportOpen by rememberSaveable(chat.id) { mutableStateOf(false) }
    val exportModel: ChatExportViewModel = viewModel(key = "chat-export-$accountId-${chat.id}")
    val exportVisible by remember(exportModel) {
        exportModel.state.map { it !is ChatExportUiState.Idle }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    // Returning to this chat while an export is still running (or finished) brings its progress/result back.
    LaunchedEffect(exportVisible) {
        if (exportVisible) exportOpen = true
    }
    // Translation is manual and cached for this chat visit, per message and target language.
    var translationPickerId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var translationLanguages by remember(chat.id) { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var translationStates by remember(chat.id) { mutableStateOf<Map<Pair<Long, String>, TranslationUiState>>(emptyMap()) }
    // A cached translation is valid only for the exact source text/entities that produced it.
    var translationSources by remember(chat.id) { mutableStateOf<Map<Long, Pair<String, List<MessageTextEntity>>>>(emptyMap()) }
    val translationJobs = remember(chat.id) { mutableMapOf<Long, Job>() }
    // Local request state covers the gap before TDLib sends UpdateMessageContent with the recognition result.
    var transcribingIds by remember(chat.id) { mutableStateOf<Set<Long>>(emptySet()) }
    var transcriptionErrors by remember(chat.id) { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var ratedTranscriptions by remember(chat.id) { mutableStateOf<Set<Long>>(emptySet()) }
    var editId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var editText by rememberSaveable(chat.id) { mutableStateOf("") }
    var editBusy by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable(chat.id) { mutableStateOf(false) }
    var deleteEveryone by remember { mutableStateOf(false) }
    var deleteBusy by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var pinConfirm by rememberSaveable(chat.id) { mutableStateOf(false) }
    var capabilities by remember { mutableStateOf<MessageCapabilities?>(null) }
    var capabilitiesError by remember { mutableStateOf<String?>(null) }
    var capabilitiesAttempt by remember { mutableIntStateOf(0) }
    var selectedMediaId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var viewerRights by remember { mutableStateOf<MessageRights?>(null) }
    var viewerPosition by remember { mutableStateOf<MediaPosition?>(null) }
    var forwardId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var forwardBusy by remember { mutableStateOf(false) }
    var forwardError by remember { mutableStateOf<String?>(null) }
    // A copy hides the original author; this survives recreation together with the pending forwardId.
    var forwardAsCopy by rememberSaveable(chat.id) { mutableStateOf(false) }
    var forwardCopyConfirmId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var contextChatId by rememberSaveable(chat.id) { mutableStateOf(0L) }
    var contextMessageId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var highlighted by remember { mutableStateOf<Long?>(null) }
    // A message outside the loaded history is scrolled to once the window around it reaches the list.
    var pendingJumpId by remember(chat.id) { mutableStateOf<Long?>(null) }
    var searching by rememberSaveable(chat.id) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(chat.id) { mutableStateOf("") }
    var searchFilter by rememberSaveable(chat.id, stateSaver = ChatSearchFilterSaver) { mutableStateOf(ChatSearchFilter()) }
    var searchResults by remember(chat.id) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var searchNextId by remember(chat.id) { mutableLongStateOf(0L) }
    var searchTotalCount by remember(chat.id) { mutableIntStateOf(0) }
    var searchGeneration by remember(chat.id) { mutableIntStateOf(0) }
    var searchBusy by remember(chat.id) { mutableStateOf(false) }
    var searchError by remember(chat.id) { mutableStateOf<String?>(null) }
    fun closeSearch() {
        searching = false; searchQuery = ""; searchFilter = ChatSearchFilter()
        searchResults = emptyList(); searchNextId = 0L; searchTotalCount = 0; searchGeneration++
        searchBusy = false; searchError = null
    }
    var sending by remember { mutableStateOf(false) }
    var sendOptionsOpen by remember(chat.id) { mutableStateOf(false) }
    var scheduledMessagesOpen by remember(chat.id) { mutableStateOf(false) }
    var chatSettingsOpen by remember(chat.id) { mutableStateOf(false) }
    val chatOverrideState by chatOverrides.state.collectAsStateWithLifecycle()
    val globalWallpaper = LocalChatWallpaper.current
    val chatWallpaper = chatOverrideState[chat.id]?.wallpaper ?: globalWallpaper
    var error by remember { mutableStateOf<String?>(null) }
    var reactionOptions by remember(chat.id) { mutableStateOf<List<ReactionOption>>(emptyList()) }
    val limits by conversation.messageLimits.collectAsStateWithLifecycle()
    val typingFlow = remember(conversation, chat.id) { conversation.observeTyping(chat.id) }
    val typingNames by typingFlow.collectAsStateWithLifecycle()
    val palette = beyGramPalette()
    // Each forum topic keeps its own list position.
    val scrollPosition: ChatScrollPositionViewModel = viewModel(key = "chat-scroll-$accountId-${chat.id}-$forumTopicId")
    val listState = rememberLazyListState(scrollPosition.firstVisibleItemIndex, scrollPosition.firstVisibleItemScrollOffset)
    DisposableEffect(listState) {
        onDispose {
            scrollPosition.firstVisibleItemIndex = listState.firstVisibleItemIndex
            scrollPosition.firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
        }
    }
    DisposableEffect(chat.id) {
        onDispose {
            // Chat info keeps the visit, so coming back from it neither moves the boundary nor the separator.
            if (scrollPosition.visitContinues) scrollPosition.visitContinues = false else scrollPosition.endVisit()
        }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalContext.current.getSystemService(ClipboardManager::class.java)
    val recordingMode by recordingSettings.recordingMode.collectAsStateWithLifecycle()
    val translationPreferences by translationSettings.state.collectAsStateWithLifecycle()
    val recordingRecovery: ChatRecordingRecoveryViewModel = viewModel(key = "chat-recording-$accountId-${chat.id}")
    val recordingPreview by recordingRecovery.preview.collectAsStateWithLifecycle()
    val recordingPreviewIndex by recordingRecovery.selectedIndex.collectAsStateWithLifecycle()
    var sentRecordingPaths by remember(chat.id) { mutableStateOf(emptySet<String>()) }
    var recordingQueueSending by remember(chat.id) { mutableStateOf(false) }
    var recordingQueueProgress by remember(chat.id) { mutableIntStateOf(0) }
    // Voice messages use this local queue; video notes are owned by VideoNoteExportCenter.
    val pendingRecordingSends = remember(chat.id) { ArrayDeque<List<OutgoingMedia>>() }
    val videoExports by VideoNoteExportCenter.results.collectAsStateWithLifecycle()
    val videoPreview = videoExports.firstOrNull { it.accountId == accountId && it.chatId == chat.id && it.topicId == forumTopicId && it.requiresPreview }
    val videoPending by VideoNoteExportCenter.pendingNotes.collectAsStateWithLifecycle()
    // Автоматический кружок виден в ленте сразу: сначала обработка, потом отправка, пока TDLib не примет сообщение.
    val pendingVideoNotes = remember(videoPending, videoExports, chat.id, forumTopicId, accountId) { pendingVideoNotesFor(chat.id, videoPending, videoExports, forumTopicId, accountId) }
    fun startRecordingSendWorker() {
        if (recordingQueueSending) return
        if (pendingRecordingSends.isEmpty()) return
        recordingQueueSending = true
        // Worker живёт в scope восстановления записи: переживает recomposition и не бросает очередь на полпути.
        recordingRecovery.processingScope.launch {
            try {
                while (pendingRecordingSends.isNotEmpty()) {
                    val mediaList = pendingRecordingSends.removeFirst()
                    mediaList.forEachIndexed { index, media ->
                        if (media.path in sentRecordingPaths) return@forEachIndexed
                        recordingQueueProgress = index + 1
                        val result = conversation.sendMedia(chat.id, media, "", replyId, forumTopicId = forumTopicId)
                        if (result.isSuccess) {
                            sentRecordingPaths = sentRecordingPaths + media.path
                            clearReply()
                        } else {
                            recordingRecovery.showPreview(mediaList, index)
                            error = result.exceptionOrNull().userMessage(context, R.string.chat_video_note_send_failed)
                            pendingRecordingSends.clear()
                            return@launch
                        }
                    }
                }
                sentRecordingPaths = emptySet()
                recordingRecovery.dismissPreview()
            } finally {
                recordingQueueSending = false
            }
        }
    }
    /** Ставит запись в отправку. Пропусков нет: занятый worker сам подхватит очередь. */
    fun sendRecordedMedia(mediaList: List<OutgoingMedia>) {
        pendingRecordingSends.addLast(mediaList)
        startRecordingSendWorker()
    }
    /** Typing is shown from each keystroke; clearing the field ends it. */
    fun typedDraft(text: String) {
        updateDraft { it.copy(text = text) }
        if (text.isBlank()) conversation.endChatActivity(chat.id, ChatActivity.Typing)
        else conversation.startChatActivity(chat.id, ChatActivity.Typing)
    }
    fun setReaction(messageId: Long, reaction: ReactionOption, addToRecent: Boolean) {
        scope.launch {
            conversation.toggleReaction(chat.id, messageId, reaction, addToRecent).onFailure { error = it.userMessage(context, R.string.chat_reaction_failed) }
        }
    }
    // Chip taps leave the user's recent reactions alone; only a reaction picked from the extended menu is added there.
    fun react(messageId: Long, reaction: ReactionOption) = setReaction(messageId, reaction, addToRecent = false)
    fun retryFailedMessages(messageIds: List<Long>) {
        scope.launch {
            val result = conversation.retryMessages(chat.id, messageIds)
            result.exceptionOrNull()?.let { error = it.message ?: context.getString(R.string.chat_retry_send_failed) }
        }
    }
    fun capturedRecording(media: OutgoingMedia, previewFirst: Boolean) {
        if (previewFirst) recordingRecovery.showPreview(listOf(media))
        else sendRecordedMedia(listOf(media))
    }
    val attachments = rememberAttachmentState(chat.id) { media, caption, options ->
        val result = if (media.size > 1) conversation.sendMediaAlbum(chat.id, media, caption, replyId, draftEntities, forumTopicId = forumTopicId, options = options)
        else conversation.sendMedia(chat.id, media.single(), caption, replyId, draftEntities, forumTopicId = forumTopicId, options = options)
        if (result.isSuccess) clearReply()
        result
    }
    val videoNote = rememberVideoNoteRecorder(recordingSettings, recordingRecovery.processingScope, chat.id, forumTopicId, accountId = accountId, onError = { error = it })
    // Video delivery, including TDLib send retries, belongs to the app-wide center.
    // Only automatic notes are claimed here; preview and failed notes remain visible.
    // A note goes to the topic it was recorded in, so another topic never claims it.
    LaunchedEffect(accountId, chat.id, forumTopicId, conversation) {
        launch {
            VideoNoteExportCenter.results.collect { list ->
                list.filter { it.accountId == accountId && it.chatId == chat.id && it.topicId == forumTopicId && !it.requiresPreview && !it.sending }.forEach { item ->
                    val accepted = VideoNoteExportCenter.send(item.chatId, item.sessionName, replyId, accountId) { media, targetReply ->
                        conversation.sendMedia(item.chatId, media, "", targetReply, forumTopicId = item.topicId).map { Unit }
                    }
                    if (accepted) clearReply()
                }
            }
        }
        launch {
            VideoNoteExportCenter.failures.collect {
                if (VideoNoteExportCenter.consumeFailure(chat.id, forumTopicId, accountId)) error = context.getString(R.string.recorder_save_failed)
            }
        }
    }
    val audioNote = rememberAudioNoteRecorder(::capturedRecording) { error = it }
    // The other members see a recording while it runs; the recorder's own state ends it.
    DisposableEffect(chat.id, audioNote.active) {
        val chatId = chat.id
        val recording = audioNote.active
        if (recording) conversation.startChatActivity(chatId, ChatActivity.RecordingVoice)
        onDispose { if (recording) conversation.endChatActivity(chatId, ChatActivity.RecordingVoice) }
    }
    DisposableEffect(chat.id, videoNote.active) {
        val chatId = chat.id
        val recording = videoNote.active
        if (recording) conversation.startChatActivity(chatId, ChatActivity.RecordingVideoNote)
        onDispose { if (recording) conversation.endChatActivity(chatId, ChatActivity.RecordingVideoNote) }
    }
    val videoPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (!granted.values.all { it }) error = context.getString(R.string.chat_video_note_permission_denied)
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) Toast.makeText(context, context.getString(R.string.voice_recorder_permission_granted_retry), Toast.LENGTH_SHORT).show()
        else error = context.getString(R.string.voice_recorder_permission_denied)
    }
    fun startRecording() {
        if (videoNote.active || audioNote.active) return
        // Микрофон записывает и звук из общего плеера, поэтому музыку ставим на паузу, когда запись начинается.
        if (recordingMode == ComposerRecordingMode.Voice) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                PlaybackEngine.pause()
                audioNote.press()
            } else audioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
        } else if (!VIDEO_NOTE_RECORDING_ENABLED) {
            error = context.getString(R.string.recorder_video_note_unavailable)
        } else if (videoNoteAllowed(context)) {
            PlaybackEngine.pause()
            videoNote.press()
        } else videoPermissions.launch(videoNotePermissions)
    }
    val messages = history.messages
    // Sender picker is built from the loaded history; no extra TDLib member request is needed in the search bar.
    val searchSenders = remember(messages) { chatSearchSenders(messages) }
    // Кружки в обработке стоят в списке первыми (внизу ленты), чтобы индексы прокрутки и дат учитывали их тоже.
    val rawMessageItems = remember(messages, pendingVideoNotes) {
        pendingVideoNotes.map { ChatMessageListItem("pending-video:${it.sessionName}", emptyList(), pendingVideoNote = it) } + groupTelegramMediaAlbums(messages)
    }
    val messageListItems = remember(rawMessageItems, scrollPosition.readBoundaryId) {
        scrollPosition.readBoundaryId?.let { withUnreadSeparator(rawMessageItems, it) } ?: rawMessageItems
    }
    val messageItemsByKey = remember(messageListItems) { messageListItems.associateBy { it.key } }
    val currentMessageItemsByKey by rememberUpdatedState(messageItemsByKey)
    val currentMessages by rememberUpdatedState(messages)
    // Live insertions are claimed before the lazy list composes its rows, so only genuinely new messages animate once.
    val animationTracker = remember(chat.id, forumTopicId) { MessageArrivalTracker() }
    remember(history) { animationTracker.observe(history) }
    // Composer Launch is a spatial overlay; the real row stays hidden until the overlay lands on it.
    val composerLaunches = remember(chat.id, forumTopicId) { mutableStateListOf<ComposerLaunch>() }
    var composerFieldBounds by remember { mutableStateOf<Rect?>(null) }
    var launchOverlayBounds by remember { mutableStateOf<Rect?>(null) }
    var composerLaunchCounter by remember { mutableLongStateOf(0L) }
    var composerDraftLaunchKey by remember { mutableStateOf<Long?>(null) }
    // Hiding is bound to the exact send attempt, not to the text value: a new identical draft must stay visible.
    val hidingComposerForLaunch = shouldHideComposerDraft(
        editing = editId != null,
        draft = draft,
        activeLaunchKey = composerDraftLaunchKey,
        launch = composerLaunches.firstOrNull { it.key == composerDraftLaunchKey }
    )
    // Follow sends across the temporary-to-server id replacement and cancel transitions for failed messages.
    LaunchedEffect(composerLaunches.toList(), history.messages, history.liveArrivals, history.recentReplacements) {
        if (composerLaunches.isEmpty()) return@LaunchedEffect
        val assigned = composerLaunches.mapNotNull { launch ->
            launch.messageId?.let { resolveMessageIdThroughReplacements(it, history.recentReplacements) }
        }.toMutableSet()
        composerLaunches.sortedBy { it.key }.toList().forEach { launch ->
            val existing = launch.messageId?.let { resolveMessageIdThroughReplacements(it, history.recentReplacements) }
            if (existing != null) {
                val current = messages.firstOrNull { it.id == existing }
                when {
                    current == null -> Unit
                    current.sendState == MessageSendState.Failed -> composerLaunches.remove(launch)
                    else -> launch.message = current
                }
            } else {
                val candidate = findComposerLaunchMessage(
                    history, launch.text, launch.replyToMessageId, launch.baselineSequence, assigned
                )
                if (candidate != null) {
                    if (candidate.sendState == MessageSendState.Failed) {
                        composerLaunches.remove(launch)
                    } else {
                        launch.messageId = candidate.id
                        launch.message = candidate
                        assigned += resolveMessageIdThroughReplacements(candidate.id, history.recentReplacements)
                    }
                }
            }
        }
    }
    // Resolve the same assignment synchronously for this list frame, so a new outgoing row is hidden before the effect runs.
    val launchTargets = buildMap<Long, ComposerLaunch> {
        val assigned = mutableSetOf<Long>()
        composerLaunches.sortedBy { it.key }.forEach { launch ->
            val existing = launch.messageId?.let { resolveMessageIdThroughReplacements(it, history.recentReplacements) }
            if (existing != null) {
                assigned += existing
                put(existing, launch)
            } else {
                val candidate = findComposerLaunchMessage(
                    history, launch.text, launch.replyToMessageId, launch.baselineSequence, assigned
                )
                if (candidate != null) {
                    assigned += candidate.id
                    put(candidate.id, launch)
                }
            }
        }
    }
    val newest = messages.lastOrNull()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // A batch collected while the app was away is not a burst of entrances; keep the tracker paused through the first resume frames.
    val animationResumeJob = remember { mutableStateOf<Job?>(null) }
    // Запись кружка и голосового в фоне отменяется. Воспроизведение идёт через общий плеер и продолжается вне ленты.
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    PlaybackEngine.stopSecret()
                    videoNote.stopForRecovery()
                    audioNote.stopForRecovery()
                    animationResumeJob.value?.cancel()
                    animationTracker.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    animationResumeJob.value?.cancel()
                    animationResumeJob.value = scope.launch {
                        delay(300)
                        animationTracker.resume()
                    }
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            animationResumeJob.value?.cancel()
            PlaybackEngine.stopSecret()
        }
    }
    val window = LocalWindowInfo.current
    val minimumVisible = with(LocalDensity.current) { 64.dp.roundToPx() }
    val actionMessage = messages.firstOrNull { it.id == actionsId }
    // An album's reactions sit on one of its messages, so the picker and the chips act on that message.
    val reactionOwner = actionMessage?.let { target -> messageListItems.firstOrNull { item -> item.messages.any { it.id == target.id } }?.messages?.let(::reactionMessage) }
    val editMessage = messages.firstOrNull { it.id == editId }
    // A swipe reply needs an idle composer on screen: no search, deletion, edit, recording or open action menu, and no
    // send in flight, because a finished send clears the reply target it used.
    val swipeReplyAllowed = canPostHere && !searching && !deleting && editMessage == null && actionMessage == null && !videoNote.active && !audioNote.active &&
        !sending && !attachments.sending && !recordingQueueSending && pendingVideoNotes.isEmpty()
    LaunchedEffect(editId) { editEntities = editMessage?.entities.orEmpty() }
    LaunchedEffect(botKeyboard?.messageId) { if (botKeyboard?.forceReply == true) updateDraft { it.copy(replyToMessageId = botKeyboard?.messageId) } }
    val editLimit = if (editMessage?.media == null) limits.text else limits.caption
    fun closeActions() { actionsId = null; deleting = false; deleteError = null; capabilities = null; capabilitiesError = null; pinConfirm = false }
    // The picker remembers the last choice; a blank stored value follows the app language, then the device language.
    fun translationDefaultLanguage() = translationTargetLanguage(translationPreferences.targetLanguageCode,
        AppLocale.selected(context).ifBlank { java.util.Locale.getDefault().language })
    fun startTranslation(message: ChatMessage, language: String) {
        if (!TranslationPolicy.canTranslate(chat.isSecret, message.text.isNotBlank())) {
            translationStates = translationStates + ((message.id to language) to TranslationUiState.Failed(context.getString(R.string.err_translation_secret_chat)))
            return
        }
        val key = message.id to language
        translationSources = translationSources + (message.id to (message.text to message.entities))
        val previousLanguage = translationLanguages[message.id]
        if (previousLanguage != null && previousLanguage != language) {
            // Switching target abandons the old request and its spinner, but keeps completed translations cached.
            translationJobs.remove(message.id)?.cancel()
            translationStates = translationStates.filterKeys { (id, lang) ->
                id != message.id || translationStates[id to lang] !is TranslationUiState.Loading
            }
        }
        translationLanguages = translationLanguages + (message.id to language)
        if (translationStates[key] is TranslationUiState.Ready) return
        translationJobs.remove(message.id)?.cancel()
        translationStates = translationStates.filterKeys { it != key } + (key to TranslationUiState.Loading)
        translationJobs[message.id] = scope.launch {
            val result = conversation.translateMessage(message.chatId, message.id, language)
            // A newer target or a cancel may have replaced this request while it was in flight.
            if (translationLanguages[message.id] != language) return@launch
            translationStates = translationStates + (key to result.fold(
                onSuccess = { TranslationUiState.Ready(it) },
                onFailure = { TranslationUiState.Failed(it.userMessage(context, R.string.err_translation_failed)) }
            ))
            translationJobs.remove(message.id)
        }
    }
    fun chooseTranslationLanguage(message: ChatMessage, language: String) {
        translationPickerId = null
        translationSettings.setTargetLanguage(language)
        startTranslation(message, language)
    }
    fun cancelTranslation(messageId: Long) {
        translationJobs.remove(messageId)?.cancel()
        val language = translationLanguages[messageId]
        if (language != null) translationStates = translationStates.filterKeys { it != (messageId to language) }
        translationLanguages = translationLanguages - messageId
    }
    fun retryTranslation(message: ChatMessage) {
        val language = translationLanguages[message.id] ?: return
        translationStates = translationStates.filterKeys { it != (message.id to language) }
        startTranslation(message, language)
    }
    fun transcribe(message: ChatMessage) {
        val media = message.media ?: return
        if (!TranslationPolicy.canTranscribe(chat.isSecret, media)) return
        transcriptionErrors = transcriptionErrors - message.id
        transcribingIds = transcribingIds + message.id
        scope.launch {
            val result = conversation.recognizeSpeech(message.chatId, message.id)
            transcribingIds = transcribingIds - message.id
            result.onFailure {
                transcriptionErrors = transcriptionErrors + (message.id to it.userMessage(context, R.string.err_transcription_failed))
            }
        }
    }
    fun rateTranscription(message: ChatMessage, isGood: Boolean) {
        ratedTranscriptions = ratedTranscriptions + message.id
        scope.launch { conversation.rateSpeechRecognition(message.chatId, message.id, isGood) }
    }
    // Editing a translated message must not leave the old translation under the new source text.
    LaunchedEffect(messages) {
        val changed = translationLanguages.keys.filter { id ->
            val current = messages.firstOrNull { it.id == id }
            current == null || translationSources[id] != (current.text to current.entities)
        }
        if (changed.isNotEmpty()) {
            changed.forEach { translationJobs.remove(it)?.cancel() }
            translationStates = translationStates.filterKeys { it.first !in changed }
            translationLanguages = translationLanguages - changed.toSet()
            translationSources = translationSources - changed.toSet()
        }
    }
    fun closeEdit() { editId = null; editText = ""; editError = null }
    LaunchedEffect(actionMessage) {
        if (actionMessage == null) { deleting = false; deleteError = null; capabilities = null; capabilitiesError = null; pinConfirm = false }
    }
    LaunchedEffect(actionsId, capabilitiesAttempt) {
        val id = actionsId ?: return@LaunchedEffect
        capabilities = null; capabilitiesError = null
        val result = conversation.messageCapabilities(chat.id, id)
        capabilities = result.getOrNull()
        capabilitiesError = result.exceptionOrNull()?.userMessage(context, R.string.err_chat_actions_load_failed)
    }
    fun setPinned(message: ChatMessage, pinned: Boolean) {
        pinConfirm = false
        closeActions()
        error = null
        scope.launch {
            conversation.setMessagePinned(message.chatId, message.id, pinned)
                .onFailure { error = it.userMessage(context, R.string.err_chat_pin_failed) }
        }
    }
    LaunchedEffect(chat.id, reactionOwner?.id) {
        reactionOptions = emptyList()
        val id = reactionOwner?.id ?: return@LaunchedEffect
        reactionOptions = conversation.availableReactions(chat.id, id).getOrNull().orEmpty()
    }
    // Фото и видео листаются по всему чату: окно грузится вокруг открытого вложения, а не только по загруженной истории.
    val mediaWindow = remember(chat.id) {
        SharedMediaWindow(
            load = { from, newer -> conversation.sharedMessages(chat.id, SharedMediaKind.Media, from, newer) },
            failure = { it.userMessage(context, R.string.err_chat_history_load_failed) }
        )
    }
    LaunchedEffect(selectedMediaId) {
        val id = selectedMediaId ?: return@LaunchedEffect
        val kind = currentMessages.firstOrNull { it.id == id }?.media?.kind
        // Вложение, которое уже в окне, не перезагружаем: так просмотрщик возвращается к нему на том же месте.
        if (kind != null && kind in viewerSwipeKinds && mediaWindow.messages.none { it.id == id }) {
            mediaWindow.restart(id)
            mediaWindow.loadOlder()
        }
        // Новее догружаем от края: открытое вложение обычно стоит у правого края окна, а прерванная загрузка не оставляет дыр.
        mediaWindow.loadNear(id)
    }
    LaunchedEffect(selectedMediaId) {
        // Счётчик при свайпе держит прежнее значение до ответа, чтобы не мигал. Права так не держим: см. MediaViewerPolicy.rightsFor.
        if (selectedMediaId == null) { viewerRights = null; viewerPosition = null }
        val id = selectedMediaId ?: return@LaunchedEffect
        viewerRights = conversation.messageCapabilities(chat.id, id).getOrNull()?.let { MessageRights(id, it) }
        val kind = (currentMessages.firstOrNull { it.id == id } ?: mediaWindow.messages.firstOrNull { it.id == id })?.media?.kind
        viewerPosition = kind?.let { conversation.mediaPosition(chat.id, id, it).getOrNull() }
    }
    BackHandler(enabled = searching || actionMessage != null || editMessage != null || attachments.menuOpen || attachments.active) {
        when {
            searching -> closeSearch()
            deleting -> { deleting = false; deleteError = null }
            actionMessage != null -> closeActions()
            editMessage != null -> closeEdit()
            else -> attachments.discardAll()
        }
    }
    BackHandler(enabled = actionMessage == null && editMessage == null && !attachments.menuOpen && !attachments.active && !searching, onBack = onBack)
    BackHandler(enabled = videoNote.active || audioNote.active) {
        videoNote.stopForRecovery()
        audioNote.stopForRecovery()
    }

    // Loading/caching messages never triggers receipts; only the focused, resumed viewport does.
    LaunchedEffect(chat.id, lifecycle, conversation) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow {
                if (!window.isWindowFocused || searching || !scrollPosition.readBoundaryChecked) emptyList() else {
                    val layout = listState.layoutInfo
                    val eligible = currentMessages.filter { it.sendState == MessageSendState.Sent }.mapTo(hashSetOf()) { it.id }
                    layout.visibleItemsInfo.filter { item ->
                        val visible = minOf(item.offset + item.size, layout.viewportEndOffset) - maxOf(item.offset, layout.viewportStartOffset)
                        visible >= minOf(item.size / 2, minimumVisible).coerceAtLeast(1)
                    }.flatMap { visibleItem ->
                        currentMessageItemsByKey[visibleItem.key as? String]?.messages.orEmpty().map { it.id }
                    }.filter { it in eligible }.distinct().sorted()
                }
            }.debounce(350).distinctUntilChanged().collect { ids -> if (ids.isNotEmpty()) conversation.messagesVisible(chat.id, ids) }
        }
    }
    // LaunchedEffect lives across recompositions; rememberUpdatedState keeps the collector on fresh history/row counts.
    val autoLoadHistory by rememberUpdatedState(history)
    val autoLoadRowCount by rememberUpdatedState(messageListItems.size)
    // Reverse LazyColumn: larger visible indexes are OLDER messages. Prefetch when the user approaches
    // the loaded edge, but wait for the unread/focused opening to settle, and stop on errors until retry.
    // beginLoad() in the repository also prevents duplicate in-flight requests.
    LaunchedEffect(chat.id, forumTopicId, conversation, listState) {
        snapshotFlow {
            shouldAutoLoadOlderMessages(
                lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1,
                loadedItemCount = autoLoadRowCount,
                hasOlderMessages = autoLoadHistory.hasOlderMessages,
                isLoading = autoLoadHistory.isLoading,
                hasError = autoLoadHistory.error != null,
                openingSettled = scrollPosition.readBoundaryChecked
            )
        }.distinctUntilChanged().collect { shouldLoad ->
            if (shouldLoad) {
                if (forumTopicId > 0L) conversation.loadOlderForumTopicMessages(chat.id, forumTopicId)
                else conversation.loadOlderMessages(chat.id)
            }
        }
    }
    // Появление кружка в ленте прокручивает к низу, как и исходящее сообщение.
    LaunchedEffect(pendingVideoNotes.firstOrNull()?.sessionName) {
        if (pendingVideoNotes.isNotEmpty()) listState.animateScrollToItem(0)
    }
    LaunchedEffect(chat.id, newest?.id) {
        // A message sent while the chat opens drops the boundary: the list goes to the bottom and the boundary is not placed.
        val arrived = scrollPosition.seenNewestId != 0L && newest != null && newest.id != scrollPosition.seenNewestId
        scrollPosition.seenNewestId = newest?.id ?: 0L
        if ((if (forumTopicId > 0L) topicUnreadCount else chat.unreadCount) > 0 && !scrollPosition.readBoundaryChecked) {
            if (arrived && newest?.isOutgoing == true) scrollPosition.readBoundaryChecked = true
            else return@LaunchedEffect
        }
        if (!history.hasNewerMessages && messages.isNotEmpty() && (listState.firstVisibleItemIndex < 2 || newest?.isOutgoing == true)) listState.animateScrollToItem(0)
    }
    // Открытие с непрочитанными: экран встаёт у разделителя «Непрочитанные», а не у последнего сообщения.
    // Пока граница не зафиксирована, отметки прочтения не отправляются (см. сбор видимых сообщений выше), поэтому она не сдвигается.
    LaunchedEffect(chat.id, history.isLoading, messages, focusMessageId) {
        // Выбранное сообщение из профиля задаёт открытие. Флаг держится до конца визита: этот эффект запускается и позже, когда запроса уже нет.
        if (focusMessageId != 0L) scrollPosition.focusClaimed = true
        if (scrollPosition.readBoundaryChecked || history.isLoading || messages.isEmpty()) return@LaunchedEffect
        val boundary = history.lastReadInboxMessageId
        val step = unreadOpenStep(if (forumTopicId > 0L) topicUnreadCount else chat.unreadCount, boundary, rawMessageItems, scrollPosition.readBoundaryWindowRequested, scrollPosition.focusClaimed)
        when (step) {
            // A focused message settles the opening itself (see settleFocusedOpening), so read marks wait for its jump.
            UnreadOpenStep.Done -> if (!scrollPosition.focusClaimed) scrollPosition.readBoundaryChecked = true
            UnreadOpenStep.RequestWindow -> {
                scrollPosition.readBoundaryWindowRequested = true
                scope.launch { (if (forumTopicId > 0L) conversation.loadForumTopicMessagesAround(chat.id, forumTopicId, boundary) else conversation.loadMessagesAround(chat.id, boundary)).onFailure { scrollPosition.readBoundaryChecked = true } }
            }
            is UnreadOpenStep.Scroll -> {
                // Scrolling by an offset needs the viewport height, which exists only after the first layout.
                val viewportHeight = snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
                if (scrollPosition.readBoundaryId == null) {
                    val itemsBefore = listState.layoutInfo.totalItemsCount
                    scrollPosition.readBoundaryId = boundary
                    // The separator is one more row: the index is applied only once the list has measured it.
                    snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > itemsBefore }
                }
                // Negative offset lifts the separator above the bottom edge, so the unread messages below it fill the lower part of the screen.
                listState.scrollToItem(step.index, -(viewportHeight * UNREAD_SEPARATOR_VIEWPORT_FRACTION).roundToInt())
                scrollPosition.readBoundaryChecked = true
            }
        }
    }
    // Read marks stay held while a focused message from outside the chat is being opened; its jump decides the position.
    fun settleFocusedOpening() { if (scrollPosition.focusClaimed) scrollPosition.readBoundaryChecked = true }
    LaunchedEffect(pendingJumpId, messageListItems) {
        val target = pendingJumpId ?: return@LaunchedEffect
        val index = messageListItems.indexOfFirst { item -> item.messages.any { it.id == target } }
        if (index < 0) {
            // The window is in place but has no target (for example, a deleted message): release the opening instead of holding read marks.
            if (!history.isLoading) { settleFocusedOpening(); pendingJumpId = null }
            return@LaunchedEffect
        }
        // A scroll cancelled by the user's touch still settles the opening, or read marks would stay held for the visit.
        try {
            listState.animateScrollToItem(index)
            highlighted = target
        } finally {
            settleFocusedOpening()
        }
        // Cleared last: changing the key earlier would cancel the scroll that is still running.
        pendingJumpId = null
    }
    LaunchedEffect(highlighted) { if (highlighted != null) { delay(2200); highlighted = null } }
    fun jump(reply: MessageReply) {
        if (reply.isUnavailable || reply.messageId <= 0) { error = context.getString(R.string.chat_reply_original_unavailable); return }
        val index = messageListItems.indexOfFirst { item -> item.messages.any { it.id == reply.messageId && it.chatId == reply.chatId } }
        if (index >= 0) scope.launch { listState.animateScrollToItem(index); highlighted = reply.messageId }
        else { contextChatId = reply.chatId; contextMessageId = reply.messageId }
    }
    // The reply is checked on the server first: a channel, or a chat without reply rights, must not enter reply mode.
    // Only the latest swipe may apply its answer, so a slower check cannot reopen the composer for an older message.
    fun swipeReply(messageId: Long) {
        swipedReplyId = messageId
        scope.launch {
            val result = conversation.messageCapabilities(chat.id, messageId)
            if (swipedReplyId != messageId) return@launch
            result
                .onSuccess {
                    if (it.canReply) {
                        updateDraft { it.copy(replyToMessageId = messageId) }
                        replyFocusRequest++
                    } else {
                        error = context.getString(R.string.err_message_cannot_reply)
                    }
                }
                .onFailure { error = it.userMessage(context, R.string.err_chat_actions_load_failed) }
        }
    }
    // Results refresh after a short pause in typing; a new query or filter replaces the pending request.
    LaunchedEffect(searching, searchQuery, searchFilter) {
        val generation = ++searchGeneration
        searchResults = emptyList(); searchNextId = 0L; searchTotalCount = 0; searchError = null
        val needle = searchQuery.trim()
        val requestedFilter = searchFilter
        val active = needle.isNotEmpty() || requestedFilter.isActive
        searchBusy = searching && active
        if (!searching || !active) return@LaunchedEffect
        delay(300)
        (if (forumTopicId > 0L) conversation.searchForumTopicMessages(chat.id, forumTopicId, needle, 0L, requestedFilter)
        else conversation.searchMessages(chat.id, needle, 0L, requestedFilter))
            .onSuccess { page ->
                if (generation == searchGeneration && isCurrentChatSearch(needle, requestedFilter, searchQuery, searchFilter)) {
                    searchResults = page.messages
                    searchNextId = page.nextFromMessageId
                    // TDLib's total predates the local date filter, so the UI falls back to the loaded count then.
                    searchTotalCount = if (requestedFilter.hasDateRange) 0 else page.totalCount
                }
            }
            .onFailure { failure ->
                if (generation == searchGeneration && isCurrentChatSearch(needle, requestedFilter, searchQuery, searchFilter)) {
                    searchError = failure.userMessage(context, R.string.chat_search_failed)
                }
            }
        if (generation == searchGeneration && isCurrentChatSearch(needle, requestedFilter, searchQuery, searchFilter)) searchBusy = false
    }
    fun loadMoreResults() {
        val needle = searchQuery.trim()
        val requestedFilter = searchFilter
        val cursor = searchNextId
        val generation = ++searchGeneration
        scope.launch {
            searchBusy = true
            searchError = null
            (if (forumTopicId > 0L) conversation.searchForumTopicMessages(chat.id, forumTopicId, needle, cursor, requestedFilter)
            else conversation.searchMessages(chat.id, needle, cursor, requestedFilter))
                .onSuccess { page ->
                    if (generation == searchGeneration && isCurrentChatSearch(needle, requestedFilter, searchQuery, searchFilter)) {
                        searchResults = appendDistinctBy(searchResults, page.messages) { it.id }
                        searchNextId = page.nextFromMessageId
                    }
                }
                .onFailure { failure ->
                    if (generation == searchGeneration && isCurrentChatSearch(needle, requestedFilter, searchQuery, searchFilter)) {
                        searchError = failure.userMessage(context, R.string.chat_search_failed)
                    }
                }
            if (generation == searchGeneration && isCurrentChatSearch(needle, requestedFilter, searchQuery, searchFilter)) searchBusy = false
        }
    }
    fun openSearchResult(message: ChatMessage) {
        val index = messageListItems.indexOfFirst { item -> item.messages.any { it.id == message.id } }
        closeSearch()
        if (index >= 0) scope.launch { listState.animateScrollToItem(index); highlighted = message.id }
        else { contextChatId = chat.id; contextMessageId = message.id }
    }
    fun jumpToMessage(messageId: Long, failure: Int) {
        val index = messageListItems.indexOfFirst { item -> item.messages.any { it.id == messageId } }
        if (index >= 0) { scope.launch { try { listState.animateScrollToItem(index); highlighted = messageId } finally { settleFocusedOpening() } }; return }
        scope.launch {
            (if (forumTopicId > 0L) conversation.loadForumTopicMessagesAround(chat.id, forumTopicId, messageId) else conversation.loadMessagesAround(chat.id, messageId))
                .onSuccess { applied -> if (applied) pendingJumpId = messageId else settleFocusedOpening() }
                .onFailure { error = it.userMessage(context, failure); settleFocusedOpening() }
        }
    }
    fun jumpToPinned(message: ChatMessage) = jumpToMessage(message.id, R.string.chat_pinned_failed)
    suspend fun loadOlderUntilBeginning(): List<ChatMessage> {
        val flow = if (forumTopicId > 0L) conversation.observeForumTopicHistory(chat.id, forumTopicId)
        else conversation.observeChatHistory(chat.id)
        var pages = 0
        while (pages < MAX_UNREAD_HISTORY_PAGES && flow.value.hasOlderMessages && !flow.value.isLoading) {
            val before = flow.value.messages.size
            if (forumTopicId > 0L) conversation.loadOlderForumTopicMessages(chat.id, forumTopicId)
            else conversation.loadOlderMessages(chat.id)
            val next = withTimeoutOrNull(UNREAD_HISTORY_PAGE_TIMEOUT_MS) {
                flow.first { it.messages.size != before || !it.hasOlderMessages || it.error != null }
            } ?: break
            if (next.error != null) break
            pages++
        }
        return flow.value.messages
    }
    suspend fun findFirstUnreadAfter(boundary: Long): Long? {
        var after = boundary
        repeat(MAX_UNREAD_FORWARD_PAGES) {
            val page = conversation.newerMessages(chat.id, forumTopicId, after).getOrElse { return null }
            if (page.isEmpty()) return null
            val items = groupTelegramMediaAlbums(page)
            val index = firstUnreadItemIndex(items, boundary)
            if (index >= 0) {
                return items[index].messages.firstOrNull { !it.isOutgoing && it.id > boundary }?.id
            }
            val newest = page.maxOfOrNull { it.id } ?: return null
            if (newest <= after) return null
            after = newest
        }
        return null
    }
    fun jumpToFirstUnread() {
        val boundary = history.lastReadInboxMessageId
        // With no read watermark every incoming message is unread, so the target is the first incoming message in the
        // whole chat. Otherwise the boundary page must be loaded, or the oldest loaded row is merely the oldest fetched
        // unread message.
        val unreadBoundary = boundary.coerceAtLeast(0L)
        val readLoaded = boundary > 0L && rawMessageItems.any { item -> item.messages.any { it.id <= boundary } }
        val firstUnread = firstUnreadItemIndex(rawMessageItems, unreadBoundary)
        if (boundary <= 0L) {
            if (!history.hasOlderMessages && firstUnread >= 0) {
                val target = rawMessageItems[firstUnread].messages.firstOrNull { !it.isOutgoing }?.id
                scope.launch { listState.animateScrollToItem(firstUnread); highlighted = target }
            } else if (history.hasOlderMessages) {
                scope.launch {
                    val items = groupTelegramMediaAlbums(loadOlderUntilBeginning())
                    val index = firstUnreadItemIndex(items, 0L)
                    if (index >= 0) pendingJumpId = items[index].messages.firstOrNull { !it.isOutgoing }?.id
                }
            }
            return
        }
        if (!needsUnreadWindow(readLoaded, firstUnread)) {
            val target = rawMessageItems[firstUnread].messages.firstOrNull { !it.isOutgoing && it.id > unreadBoundary }?.id
            scope.launch { listState.animateScrollToItem(firstUnread); highlighted = target }
            return
        }
        // A zero watermark has no message to anchor GetChatHistory; extend the oldest loaded page instead.
        val anchor = boundary
        // Request the boundary page. This also covers a window that is entirely older than the boundary: readLoaded is
        // true there, but there is no unread row to scroll to. Resolve the target from the applied window itself, then
        // let the existing pendingJumpId effect wait until that row is in the list.
        scope.launch {
            val loadedWindow = if (forumTopicId > 0L) conversation.loadForumTopicMessagesAround(chat.id, forumTopicId, anchor)
            else conversation.loadMessagesAround(chat.id, anchor)
            if (loadedWindow.isFailure) {
                error = loadedWindow.exceptionOrNull().userMessage(context, R.string.err_chat_history_load_failed)
                return@launch
            }
            if (loadedWindow.getOrDefault(false)) {
                val loaded = if (forumTopicId > 0L) conversation.observeForumTopicHistory(chat.id, forumTopicId).value.messages
                else conversation.observeChatHistory(chat.id).value.messages
                val items = groupTelegramMediaAlbums(loaded)
                val index = firstUnreadItemIndex(items, unreadBoundary)
                if (index >= 0) {
                    pendingJumpId = items[index].messages.firstOrNull { !it.isOutgoing && it.id > unreadBoundary }?.id
                    return@launch
                }
                // The boundary window can contain only outgoing messages. Walk newer pages until an unread incoming
                // message appears, then open its window so the jump target is present in the list.
                val target = findFirstUnreadAfter(boundary) ?: return@launch
                val targetWindow = if (forumTopicId > 0L) conversation.loadForumTopicMessagesAround(chat.id, forumTopicId, target)
                else conversation.loadMessagesAround(chat.id, target)
                if (targetWindow.getOrDefault(false)) pendingJumpId = target
            }
        }
    }
    // Переход из профиля к вложению: чат открывается у сообщения один раз, после этого запрос сбрасывается.
    LaunchedEffect(focusMessageId) {
        if (focusMessageId == 0L) return@LaunchedEffect
        jumpToMessage(focusMessageId, R.string.chat_message_open_failed)
        onFocusHandled()
    }

    /**
     * Sends the current text draft or prepared attachments with [options]. The send-options menu is one-shot:
     * the choice applies to this send only and is not kept for the next one.
     */
    fun sendCurrentDraft(options: MessageSendOptions) {
        if (editId != null) return
        if (attachments.active) {
            attachments.send(draft, options) { value -> updateDraft { it.copy(text = value, entities = if (value.isEmpty()) emptyList() else it.entities) } }
            return
        }
        val outgoingAnimation = messageAnimations.direction(outgoing = true)
        val outgoing = composerLaunchPayload(draft, draftEntities, limits.text)
        // A scheduled message is not in the chat yet, so it must not run the Composer Launch animation.
        val launch = if (options.scheduledAtSeconds == null && messageAnimations.enabled && outgoingAnimation.preset == MessageAnimationPreset.ComposerLaunch && outgoing.text.isNotBlank()) {
            composerFieldBounds?.let { source ->
                composerLaunchCounter += 1
                ComposerLaunch(
                    key = composerLaunchCounter,
                    text = outgoing.text,
                    entities = outgoing.entities,
                    replyToMessageId = replyId,
                    sourceBounds = source,
                    baselineSequence = history.liveArrivalSequence,
                    speed = outgoingAnimation.tuning.speed
                ).also {
                    composerDraftLaunchKey = it.key
                    composerLaunches += it
                    while (composerLaunches.size > MAX_ACTIVE_COMPOSER_LAUNCHES) composerLaunches.removeAt(0)
                }
            }
        } else null
        sending = true; error = null
        scope.launch {
            var delivered = false
            try {
                val result = conversation.sendMessage(chat.id, outgoing.text, replyId, outgoing.entities, forumTopicId = forumTopicId, options = options)
                if (result.isSuccess) {
                    delivered = true
                    updateDraft { MessageDraft() }
                    launch?.awaitingSend = false
                    if (composerDraftLaunchKey == launch?.key) composerDraftLaunchKey = null
                    botKeyboard?.takeIf { it.forceReply }?.let { conversation.dismissBotKeyboard(chat.id, it.messageId) }
                    if (options.scheduledAtSeconds != null) {
                        Toast.makeText(context, context.getString(R.string.chat_schedule_success), Toast.LENGTH_SHORT).show()
                    }
                } else error = result.exceptionOrNull()?.userMessage(context, R.string.err_chat_send_failed)
            } finally {
                launch?.awaitingSend = false
                if (!delivered) {
                    launch?.let(composerLaunches::remove)
                    if (composerDraftLaunchKey == launch?.key) composerDraftLaunchKey = null
                }
                sending = false
            }
        }
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { launchOverlayBounds = it.boundsInWindow() }) {
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (actionMessage != null) MessageActionsBar(
            capabilities = capabilities, error = capabilitiesError, hasText = actionMessage.text.isNotBlank(), isMedia = actionMessage.media != null,
            canPin = canTogglePin(capabilities?.canPin == true), isPinned = actionMessage.isPinned,
            onClose = ::closeActions, onRetry = { capabilitiesAttempt++ },
            onReply = { updateDraft { it.copy(replyToMessageId = actionMessage.id) }; replyFocusRequest++; closeActions() },
            onCopy = { clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.chat_message), actionMessage.text)); closeActions() },
            onEdit = { editId = actionMessage.id; editText = actionMessage.text; closeActions() },
            onDelete = { deleteEveryone = capabilities?.canDeleteForSelf == false; deleting = true },
            onTogglePin = {
                if (chat.isChannel && !actionMessage.isPinned) pinConfirm = true
                else setPinned(actionMessage, !actionMessage.isPinned)
            },
            onCopyLink = {
                val target = actionMessage
                scope.launch {
                    conversation.messageLink(chat.id, target.id, inMessageThread = forumTopicId > 0L)
                        .onSuccess {
                            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.message_link), it))
                            Toast.makeText(context, context.getString(R.string.message_link_copied), Toast.LENGTH_SHORT).show()
                        }
                        .onFailure { error = it.userMessage(context, R.string.err_message_link_unavailable) }
                    closeActions()
                }
            },
            onForwardCopy = { forwardCopyConfirmId = actionMessage.id; closeActions() },
            onTranslate = if (actionMessage.mediaAlbumId == 0L && actionMessage.sendState == MessageSendState.Sent &&
                TranslationPolicy.canTranslate(chat.isSecret, actionMessage.text.isNotBlank())) {
                { translationPickerId = actionMessage.id; closeActions() }
            } else null
        ) else if (searching) ChatSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            filter = searchFilter,
            senders = if (chat.isSecret) emptyList() else searchSenders,
            onFilterChange = { searchFilter = it },
            onClose = ::closeSearch
        ) else ChatTopBar(
            chat, info, lastSeenStyle, onBack, { scrollPosition.visitContinues = true; onChatInfo() }, { searching = true },
            observeFile, onDownloadFile, forumTopicTitle,
            hasUnread = (if (forumTopicId > 0L) topicUnreadCount else chat.unreadCount) > 0,
            onMarkUnread = {
                scope.launch {
                    conversation.markChatUnread(chat.id)
                        .onSuccess { Toast.makeText(context, context.getString(R.string.chat_mark_unread_done), Toast.LENGTH_SHORT).show(); onBack() }
                        .onFailure { error = it.userMessage(context, R.string.err_chat_mark_unread_failed) }
                }
            },
            onJumpUnread = ::jumpToFirstUnread,
            onScheduled = { scheduledMessagesOpen = true },
            onChatSettings = { chatSettingsOpen = true },
            onExport = { exportOpen = true }
        )
        // The target picker lives above the message list so the action bar can close before selection.
        val translationTargetMessage = translationPickerId?.let { id -> messages.firstOrNull { it.id == id } }
        if (translationTargetMessage != null && TranslationPolicy.canTranslate(chat.isSecret, translationTargetMessage.text.isNotBlank())) {
            TranslationTargetDialog(
                currentLanguageCode = translationDefaultLanguage(),
                onSelect = { chooseTranslationLanguage(translationTargetMessage, it) },
                onDismiss = { translationPickerId = null }
            )
        }
        reactionOwner?.takeIf { !deleting }?.let { owner ->
            MessageReactionPicker(reactionOptions, owner.reactions.filter { it.isChosen }.mapTo(hashSetOf()) { it.option }, conversation::observeCustomEmoji, observeFile, onDownloadFile) { option ->
                setReaction(owner.id, option, addToRecent = true)
                closeActions()
            }
        }
        // Sync the pinned preview with what the reader is currently seeing, as in official Telegram.
        val visibleOldestPinnedBoundary by remember(listState, messageListItems) {
            derivedStateOf {
                listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                    messageListItems.getOrNull(item.index)?.messages?.minOfOrNull { it.id }
                }.minOrNull()
            }
        }
        PinnedMessagesBar(chat.id, conversation, connectionState == TelegramConnectionState.Connected,
            ::jumpToPinned, visibleOldestMessageId = visibleOldestPinnedBoundary, forumTopicId = forumTopicId)
        if (connectionState == TelegramConnectionState.WaitingForNetwork) Row(
            Modifier.fillMaxWidth().background(palette.warningSurface).errorAnnouncement().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.WifiOff, null, tint = palette.warningText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.chat_network_waiting), color = palette.warningText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
        ChatWallpaperLayer(chatWallpaper)
        val metrics = LocalBubbleMetrics.current
        LazyColumn(state = listState, reverseLayout = true, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = metrics.listPadding), verticalArrangement = Arrangement.spacedBy(metrics.itemSpacing)) {
            items(messageListItems, key = { it.key }) { listItem ->
                if (listItem.unreadSeparator) {
                    UnreadMessagesSeparator()
                } else if (listItem.pendingVideoNote != null) {
                    PendingVideoNoteBubble(listItem.pendingVideoNote)
                } else if (listItem.mediaAlbumId != 0L) {
                    val latest = listItem.messages.maxByOrNull { it.id }
                    val animateArrival = remember(listItem.key) { animationTracker.claim(listItem.messages.map { it.id }) }
                    MessageAnimationFrame(animate = animateArrival, outgoing = latest?.isOutgoing == true, config = messageAnimations) {
                        MessageAlbumBubble(listItem.messages, observeFile, onDownloadFile,
                            isRead = latest?.let { it.isOutgoing && it.sendState == MessageSendState.Sent && it.id <= history.lastReadOutboxMessageId } == true,
                            highlighted = listItem.messages.any { it.id == highlighted || it.id == actionsId },
                            onOpen = { selectedMediaId = it }, onActions = { actionsId = it }, onReplyClick = ::jump, onReply = if (swipeReplyAllowed) ::swipeReply else null,
                            onReaction = ::react, observeCustomEmoji = conversation::observeCustomEmoji, onRetry = { retryFailedMessages(listItem.messages.filter { it.sendState == MessageSendState.Failed }.map { it.id }) })
                    }
                } else {
                    val message = listItem.messages.single()
                    val animateArrival = remember(listItem.key) { animationTracker.claim(listOf(message.id)) }
                    val launch = launchTargets[message.id]
                    val launchModifier = if (launch != null) Modifier.graphicsLayer { alpha = 0f } else Modifier
                    val bubblePosition: ((Rect) -> Unit)? = launch?.let { target ->
                        { rect ->
                            val fieldTop = composerFieldBounds?.top ?: Float.MAX_VALUE
                            val screenTop = launchOverlayBounds?.top ?: 0f
                            // Ignore prefetched or offscreen rows: the overlay must land on a bubble the user can see.
                            if (rect.bottom <= fieldTop && rect.top >= screenTop && target.targetBounds != rect) {
                                target.targetBounds = rect
                            }
                        }
                    }
                    MessageAnimationFrame(animate = animateArrival && launch == null, outgoing = message.isOutgoing, config = messageAnimations, modifier = launchModifier) {
                        val translationLanguage = translationLanguages[message.id]
                        val translationState = translationLanguage?.let { translationStates[message.id to it] }
                        val media = message.media
                        if (message.isCallEvent && message.serviceText != null) CallMessageChip(message.serviceText, message.time)
                        else if (message.serviceText != null) ServiceMessageChip(message.serviceText, message.serviceUserId, onServiceUser)
                        else MessageBubble(message, observeFile, onDownloadFile, isRead = message.isOutgoing && message.sendState == MessageSendState.Sent && message.id <= history.lastReadOutboxMessageId,
                            highlighted = message.id == highlighted || message.id == actionsId, onOpen = { selectedMediaId = message.id }, onActions = { actionsId = message.id }, onReplyClick = ::jump, onReply = if (swipeReplyAllowed) ::swipeReply else null,
                            onPlayed = { conversation.openMessageContent(chat.id, message.id) },
                            onRetry = { retryFailedMessages(listOf(message.id)) }, onReaction = ::react, observeCustomEmoji = conversation::observeCustomEmoji, inlineContent = { InlineBotButtons(message, conversation) },
                            onBubblePositioned = bubblePosition,
                            translation = translationState,
                            onTranslationRetry = { retryTranslation(message) },
                            onTranslationCancel = { cancelTranslation(message.id) },
                            onTranslationDismiss = { translationLanguages = translationLanguages - message.id },
                            transcriptionRequestPending = message.id in transcribingIds,
                            transcriptionRequestError = transcriptionErrors[message.id],
                            transcriptionRated = message.id in ratedTranscriptions,
                            onTranscribe = if (message.sendState == MessageSendState.Sent && TranslationPolicy.canTranscribe(chat.isSecret, media)) {
                                { transcribe(message) }
                            } else null,
                            onRateTranscription = if (chat.isSecret) {
                                null
                            } else {
                                { isGood -> rateTranscription(message, isGood) }
                            })
                    }
                }
            }
            history.error?.let { historyError -> item(key = "history_error") {
                Column(Modifier.fillMaxWidth().errorAnnouncement(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(historyError.resolve(), color = palette.warningText, modifier = Modifier.padding(8.dp))
                    TextButton(onClick = { if (forumTopicId > 0L) conversation.loadOlderForumTopicMessages(chat.id, forumTopicId) else conversation.loadOlderMessages(chat.id) }) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            } }
            if (history.isLoading) item(key = "history_loading") {
                Text(stringResource(R.string.chat_history_loading), color = palette.textSecondary,
                    modifier = Modifier.fillMaxWidth().padding(8.dp), textAlign = TextAlign.Center)
            }
            else if (!history.hasOlderMessages && messages.isEmpty() && history.error == null) item(key = "history_empty") {
                Text(stringResource(R.string.chat_history_empty), color = palette.textSecondary, modifier = Modifier.padding(8.dp))
            }
        }
        if (!searching) FloatingMessageDate(listState, messageListItems, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
        val away by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
        if (away || history.hasNewerMessages) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).clip(RoundedCornerShape(50)).background(palette.surface)
            .border(1.dp, palette.divider, RoundedCornerShape(50)).heightIn(min = 48.dp).clickable(role = Role.Button) {
                if (history.hasNewerMessages) { if (forumTopicId > 0L) conversation.loadForumTopicHistory(chat.id, forumTopicId) else conversation.loadLatestMessages(chat.id); scope.launch { listState.scrollToItem(0) } }
                else scope.launch { listState.animateScrollToItem(0) }
            }
            .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ArrowDownward, null, tint = palette.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.chat_jump_to_latest), color = palette.accentInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        if (searching) ChatSearchResults(
            query = searchQuery,
            filter = searchFilter,
            results = searchResults,
            totalCount = searchTotalCount.takeIf { it > 0 },
            busy = searchBusy,
            error = searchError,
            hasMore = searchNextId != 0L,
            onOpen = ::openSearchResult,
            onMore = ::loadMoreResults,
            onResetFilters = { searchFilter = ChatSearchFilter() }
        )
        if (videoNote.active) VideoNoteRecordingOverlay(videoNote)
        }
        error?.let { failure -> Row(Modifier.fillMaxWidth().background(palette.warningSurface).errorAnnouncement().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(failure, color = palette.warningText, fontSize = 12.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { error = null }) { Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = palette.warningText) }
        } }
        forwardCopyConfirmId?.let { id ->
            AlertDialog(
                onDismissRequest = { forwardCopyConfirmId = null },
                title = { Text(stringResource(R.string.message_forward_copy_title)) },
                text = { Text(stringResource(R.string.message_forward_copy_warning)) },
                confirmButton = {
                    TextButton(onClick = { forwardCopyConfirmId = null; forwardAsCopy = true; forwardId = id }) {
                        Text(stringResource(R.string.message_forward_copy_confirm), color = palette.accent)
                    }
                },
                dismissButton = { TextButton(onClick = { forwardCopyConfirmId = null }) { Text(stringResource(R.string.common_cancel)) } }
            )
        }
        val replyTarget = messages.firstOrNull { it.id == replyId }
        if (deleting && actionMessage != null) {
            val caps = capabilities
            if (caps != null) DeleteConfirmPanel(caps, deleteEveryone, deleteBusy, deleteError, onChoose = { deleteEveryone = it },
                onCancel = { deleting = false; deleteError = null }, onConfirm = { scope.launch {
                    deleteBusy = true; deleteError = null
                    try {
                        val result = conversation.deleteMessage(actionMessage.chatId, actionMessage.id, if (deleteEveryone) DeleteMessageScope.ForEveryone else DeleteMessageScope.ForSelf)
                        if (result.isSuccess) closeActions() else deleteError = result.exceptionOrNull()?.userMessage(context, R.string.err_chat_delete_failed)
                    } finally { deleteBusy = false }
                } })
        } else if (editMessage != null) {
            EditBanner(editMessage.text.ifBlank { editMessage.summary() }, editMessage.media != null, editText.codePointCount(0, editText.length), editLimit, editError, onClose = ::closeEdit)
        } else if (!searching) {
            AttachmentPanel(attachments, draft, limits.caption)
            if (replyId != null) Row(Modifier.fillMaxWidth().background(palette.surface).padding(start = 18.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).border(1.dp, palette.accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(stringResource(R.string.chat_reply_label) + " · " + (replyTarget?.senderName?.ifBlank { if (replyTarget.isOutgoing) stringResource(R.string.chat_you) else chat.title } ?: stringResource(R.string.chat_message)), color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(replyTarget?.summary() ?: stringResource(R.string.chat_message_unavailable), color = palette.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                }
                IconButton(enabled = !sending, onClick = ::clearReply) { Icon(Icons.Rounded.Close, stringResource(R.string.chat_cancel_reply_description), tint = palette.textSecondary) }
            }
        }
        val editing = editMessage != null
        val fieldText = if (editing) editText else draft
        val fieldEntities = if (editing) editEntities else draftEntities
        // While the send request is in flight, Composer Launch owns the visual copy. The draft stays in state and is
        // restored if the send fails; hiding it here prevents two simultaneous copies of the same text.
        val displayedFieldText = if (hidingComposerForLaunch && !editing) "" else fieldText
        val displayedFieldEntities = if (hidingComposerForLaunch && !editing) emptyList() else fieldEntities
        val fieldLimit = when { editing -> editLimit; attachments.active -> limits.caption; else -> limits.text }
        val overLimit = fieldText.codePointCount(0, fieldText.length) > fieldLimit
        val busy = sending || editBusy || attachments.sending
        val canSend = !busy && when {
            editing -> fieldText != editMessage?.text && (editMessage?.media != null || fieldText.isNotBlank())
            attachments.active -> attachments.pending.isNotEmpty() && !attachments.importing
            else -> fieldText.isNotBlank()
        }
        val sendLabel = if (editing) stringResource(R.string.common_save) else stringResource(R.string.common_send)
        val sendState = if (busy) stringResource(R.string.chat_sending_description) else null
        val recordingActive = videoNote.active || audioNote.active
        val recordingLocked = if (recordingMode == ComposerRecordingMode.Voice) audioNote.locked else videoNote.locked
        val recordingCanceling = if (recordingMode == ComposerRecordingMode.Voice) audioNote.canceling else videoNote.canceling
        val showRecord = recordingActive || (!editing && !attachments.active && fieldText.isBlank())
        if (!deleting && !searching && typingNames.isNotEmpty()) TypingRow(typingNames)
        // Мини-плеер стоит над полем ввода и остаётся на месте при поиске и при выборе сообщений. Без поля ввода он внизу экрана, поэтому отступает от системной навигации.
        NowPlayingBar(bottomInset = deleting || searching)
        // Chats the user cannot write to get no composer, only a plain notice, as in Telegram.
        if (!deleting && !searching && !canPostHere) ReadOnlyChatBar()
        if (!deleting && !searching && canPostHere) Column(Modifier.fillMaxWidth().background(palette.surface).navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            if (!recordingActive) {
                if (!editing) AttachmentButton(attachments, enabled = !sending)
                else Spacer(Modifier.width(8.dp))
            }
            if (audioNote.active) AudioNoteRecordingDock(audioNote, Modifier.weight(1f), onCancel = audioNote::discardLocked)
            else if (videoNote.active) VideoNoteRecordingDock(videoNote, Modifier.weight(1f), onCancel = videoNote::discardLocked)
            else FormattingTextField(displayedFieldText, displayedFieldEntities,
                onChange = { if (!hidingComposerForLaunch) { if (editing) editText = it else typedDraft(it) } },
                onEntitiesChange = { if (!hidingComposerForLaunch) { if (editing) editEntities = it else updateDraft { state -> state.copy(entities = it) } } },
                placeholder = if (hidingComposerForLaunch) "" else if (attachments.active) stringResource(R.string.chat_caption_placeholder) else botKeyboard?.placeholder?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chat_message),
                locked = busy || hidingComposerForLaunch, isError = false,
                modifier = Modifier.weight(1f).onGloballyPositioned { if (composerFieldBounds != it.boundsInWindow()) composerFieldBounds = it.boundsInWindow() },
                focusRequest = replyFocusRequest)
            Spacer(Modifier.width(8.dp))
            // Keep the same pointer-input node mounted while recording. Removing the button here
            // cancels its gesture coroutine immediately after onStart changes active to true.
            if (recordingActive && recordingLocked) Box(Modifier.size(48.dp).clip(CircleShape)
                .background(palette.accent).clickable(enabled = !busy, role = androidx.compose.ui.semantics.Role.Button) {
                    if (recordingMode == ComposerRecordingMode.Voice) audioNote.finishLocked() else videoNote.sendLocked()
                }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.recorder_send), tint = Color.White, modifier = Modifier.size(21.dp))
            }
            else if (showRecord) RecordingControlButton(
                mode = recordingMode, active = recordingActive, canceling = recordingCanceling, enabled = !sending && !busy,
                onTap = { if (!recordingActive) recordingSettings.setRecordingMode(if (recordingMode == ComposerRecordingMode.Voice) ComposerRecordingMode.Video else ComposerRecordingMode.Voice) },
                onStart = { if (!recordingActive) startRecording() },
                // Accessibility activation starts hands-free and locks synchronously.
                // If permission is missing or the camera refuses to start, lock() is a no-op.
                // The user can grant access and activate the control again.
                onAccessibleStart = {
                    if (!recordingActive) {
                        startRecording()
                        if (recordingMode == ComposerRecordingMode.Voice) audioNote.lock() else videoNote.lock()
                    }
                },
                onCanceling = { if (recordingMode == ComposerRecordingMode.Voice) audioNote.onDrag(it) else videoNote.onDrag(it) },
                onLock = { if (recordingMode == ComposerRecordingMode.Voice) audioNote.lock() else videoNote.lock() },
                onRelease = { if (recordingMode == ComposerRecordingMode.Voice) audioNote.release(it) else videoNote.release(it) }
            )
            else Box(Modifier.size(accessibleTouchTarget(40.dp))
                // A tap must not move focus off the text field, or the keyboard closes.
                .focusProperties { canFocus = false }
                .combinedClickable(
                    enabled = canSend,
                    onClickLabel = sendLabel,
                    role = Role.Button,
                    onLongClickLabel = if (!editing) stringResource(R.string.chat_send_options) else null,
                    onLongClick = if (editing) null else { { sendOptionsOpen = true } }
                ) {
                    if (editing) {
                        scope.launch {
                            val target = editMessage ?: return@launch
                            editBusy = true; editError = null
                            try {
                                val result = conversation.editMessage(target.chatId, target.id, editText, editEntities)
                                if (result.isSuccess) closeEdit() else editError = result.exceptionOrNull()?.userMessage(context, R.string.err_chat_edit_failed)
                            } finally { editBusy = false }
                        }
                    } else {
                        sendCurrentDraft(MessageSendOptions())
                    }
                }
                .semantics {
                    contentDescription = sendLabel
                    sendState?.let { stateDescription = it }
                },
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(palette.accent.copy(alpha = if (canSend || busy) 1f else 0.4f)), contentAlignment = Alignment.Center) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = palette.onAccent, strokeWidth = 2.dp)
                    else Icon(if (editing) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.Send, contentDescription = null,
                        tint = palette.onAccent.copy(alpha = if (canSend) 1f else 0.8f), modifier = Modifier.size(22.dp))
                }
            }
        }
        if (!recordingActive && overLimit) Text(
            stringResource(R.string.chat_truncated_notice, fieldLimit),
            color = palette.warningText, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
        )
        if (!recordingActive && !editing && !attachments.active) BotReplyKeyboard(chat.id, botKeyboard, conversation)
        }
    }
    ComposerLaunchOverlay(
        launches = composerLaunches,
        chatId = chat.id,
        overlayBounds = launchOverlayBounds,
        onFinished = {
            composerLaunches.remove(it)
            if (composerDraftLaunchKey == it.key) composerDraftLaunchKey = null
        },
        onCancel = {
            composerLaunches.remove(it)
            if (composerDraftLaunchKey == it.key) composerDraftLaunchKey = null
        }
    )
    }
    // Each unacknowledged video draft remains in the app center. Only the oldest is shown;
    // later previews are retained, not overwritten by a second showPreview() call.
    videoPreview?.let { video ->
        RecordingPreviewDialog(
            mediaList = video.media,
            selectedIndex = recordingPreviewIndex,
            sentPaths = video.media.take(video.nextIndex).mapTo(mutableSetOf()) { it.path },
            sending = video.sending,
            progress = (video.nextIndex + 1).coerceAtMost(video.media.size),
            onSelect = recordingRecovery::select,
            onDelete = { VideoNoteExportCenter.discard(video.chatId, video.sessionName, accountId) },
            onSend = {
                val accepted = VideoNoteExportCenter.send(video.chatId, video.sessionName, replyId, accountId) { media, targetReply ->
                    conversation.sendMedia(video.chatId, media, "", targetReply, forumTopicId = video.topicId).map { Unit }
                }
                if (accepted) clearReply()
            }
        )
    }
    if (videoPreview == null) recordingPreview?.let { mediaList -> RecordingPreviewDialog(
        mediaList = mediaList,
        selectedIndex = recordingPreviewIndex,
        sentPaths = sentRecordingPaths,
        sending = recordingQueueSending,
        progress = recordingQueueProgress,
        onSelect = recordingRecovery::select,
        onDelete = { mediaList.firstOrNull()?.let { java.io.File(it.path).parentFile?.deleteRecursively() }; sentRecordingPaths = emptySet(); recordingRecovery.dismissPreview() },
        onSend = { sendRecordedMedia(mediaList) }
    ) }
    val viewedRights = selectedMediaId?.let { MediaViewerPolicy.rightsFor(it, viewerRights) }
    // Фото и видео листаются вместе; остальные вложения открываются по одному.
    // Действия над сообщением есть только у сообщений из загруженной истории: редактирование ищет их в ленте.
    val viewedInChat = messages.firstOrNull { it.id == selectedMediaId }
    val viewed = viewedInChat ?: mediaWindow.messages.firstOrNull { it.id == selectedMediaId }
    val windowPages = mediaWindow.messages.mapNotNull { m -> m.media?.let { MediaViewerItem(m.id, it) } }
    val historyPages = remember(messages) { messages.mapNotNull { m -> m.media?.takeIf { it.kind in viewerSwipeKinds }?.let { MediaViewerItem(m.id, it) } } }
    // Пока окно не пришло или не загрузилось, листаем уже загруженную историю чата; без неё показываем вложение одно.
    val viewerPages = listOf(windowPages, historyPages).firstOrNull { pages -> pages.any { it.messageId == selectedMediaId } }
        ?: listOfNotNull(viewed?.let { m -> m.media?.let { MediaViewerItem(m.id, it) } })
    if (viewed != null && viewed.media != null) MediaViewer(
        pages = viewerPages, selectedId = viewed.id, onSelect = { selectedMediaId = it },
        observeFile = observeFile, downloadFile = onDownloadFile, onViewed = { id -> conversation.openMessageContent(chat.id, id) }, onClose = { selectedMediaId = null },
        title = if (viewed.isOutgoing) stringResource(R.string.chat_you) else viewed.senderName.ifBlank { chat.title }, subtitle = listOfNotNull(viewed.time, viewerPosition?.let { stringResource(R.string.chat_media_position, it.number, it.total) }).joinToString(" · "),
        canExport = viewedRights?.canSave == true, confirmExport = { id -> conversation.messageCapabilities(chat.id, id).getOrNull()?.canSave == true },
        actions = viewedInChat?.let { MediaViewerActions(
            canForward = viewedRights?.canForward == true,
            canEdit = viewedRights?.canEdit == true,
            canDelete = viewedRights?.let { it.canDeleteForSelf || it.canDeleteForAll } == true,
            onForward = { forwardAsCopy = false; forwardId = viewed.id; selectedMediaId = null },
            onEdit = { editId = viewed.id; editText = viewed.text; selectedMediaId = null },
            onDelete = { deleteEveryone = viewedRights?.canDeleteForSelf == false; actionsId = viewed.id; deleting = true; selectedMediaId = null }
        ) }, onShowInChat = { id -> selectedMediaId = null; jumpToMessage(id, R.string.err_chat_history_load_failed) })
    forwardId?.let { id ->
        ForwardChatDialog(chats, forwardBusy, forwardError, asCopy = forwardAsCopy,
            onPick = { target -> scope.launch {
                forwardBusy = true; forwardError = null
                try {
                    val result = conversation.forwardMessage(chat.id, id, target.id, asCopy = forwardAsCopy)
                    if (result.isSuccess) {
                        val copied = forwardAsCopy
                        forwardId = null
                        forwardAsCopy = false
                        Toast.makeText(context, context.getString(if (copied) R.string.message_forwarded_without_author else R.string.chat_message_forwarded), Toast.LENGTH_SHORT).show()
                    } else forwardError = result.exceptionOrNull().userMessage(context, R.string.chat_forward_failed)
                } finally { forwardBusy = false }
            } },
            onClose = { forwardId = null; forwardError = null; forwardAsCopy = false })
    }
    contextMessageId?.let { id -> MessageContextDialog(contextChatId, id, conversation, observeFile, onDownloadFile, onClose = { contextMessageId = null }) }
    if (pinConfirm && actionMessage != null) AlertDialog(
        onDismissRequest = { pinConfirm = false },
        title = { Text(stringResource(R.string.chat_pin_channel_title)) },
        text = { Text(stringResource(R.string.chat_pin_channel_message)) },
        confirmButton = {
            TextButton(onClick = { actionMessage?.let { setPinned(it, true) } }) {
                Text(stringResource(R.string.message_action_pin))
            }
        },
        dismissButton = { TextButton(onClick = { pinConfirm = false }) { Text(stringResource(R.string.common_cancel)) } }
    )
    if (sendOptionsOpen) SendOptionsDialog(
        canSchedule = !chat.isSecret,
        onDismiss = { sendOptionsOpen = false },
        onSendNow = { sendOptionsOpen = false; sendCurrentDraft(MessageSendOptions()) },
        onSendSilently = { sendOptionsOpen = false; sendCurrentDraft(MessageSendOptions(disableNotification = true)) },
        onSchedule = { at -> sendOptionsOpen = false; sendCurrentDraft(MessageSendOptions(scheduledAtSeconds = at)) }
    )
    if (scheduledMessagesOpen) ScheduledMessagesDialog(
        chatId = chat.id,
        conversation = conversation,
        onClose = { scheduledMessagesOpen = false }
    )
    if (chatSettingsOpen) ChatSettingsSheet(
        chatId = chat.id,
        conversation = conversation,
        overrides = chatOverrides,
        globalWallpaper = globalWallpaper,
        onClose = { chatSettingsOpen = false }
    )
    if (exportOpen) ChatExportDialog(
        chat = chat,
        accountId = accountId,
        accountLabel = accountLabel,
        conversation = conversation,
        onDismiss = { exportOpen = false }
    )
}

@Composable
private fun ReadOnlyChatBar() {
    val palette = beyGramPalette()
    Box(Modifier.fillMaxWidth().background(palette.surface).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.chat_read_only), color = palette.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ChatTopBar(
    chat: ChatPreview, info: ProfileInfo?, lastSeenStyle: LastSeenStyle, onBack: () -> Unit, onInfo: () -> Unit, onSearch: () -> Unit,
    observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit, forumTopicTitle: String? = null,
    hasUnread: Boolean = false, onMarkUnread: () -> Unit = {}, onJumpUnread: () -> Unit = {},
    onScheduled: () -> Unit = {}, onChatSettings: () -> Unit = {}, onExport: () -> Unit = {}
) {
    val palette = beyGramPalette()
    var menuOpen by remember { mutableStateOf(false) }
    // Время в строке статуса пересчитывается отдельно от экрана: тик перерисовывает только шапку.
    val now by rememberPresenceClock(info?.status is ProfilePresence.LastSeen)
    val chatInfoLabel = stringResource(R.string.a11y_chat_info)
    Column(Modifier.fillMaxWidth().background(palette.surface)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), tint = palette.textPrimary) }
            // Avatar and title share one focus stop: TalkBack should not visit the same "chat info" target twice.
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = chatInfoLabel, onClick = onInfo)
                    .semantics(mergeDescendants = true) { role = Role.Button },
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChatAvatar(chat, 40.dp, observeFile, downloadFile, Modifier.clearAndSetSemantics {})
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(forumTopicTitle ?: chat.title, color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(chatPresenceLine(chat, info, lastSeenStyle, now).resolve(), color = palette.textSecondary, fontSize = 13.sp)
                }
            }
            if (!chat.isSecret) IconButton(onClick = onScheduled) { Icon(Icons.Rounded.Schedule, stringResource(R.string.chat_scheduled_title), tint = palette.textSecondary) }
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, stringResource(R.string.chat_search_description), tint = palette.textSecondary) }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.feature_more_actions), tint = palette.textSecondary) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_jump_to_unread)) },
                        enabled = hasUnread,
                        onClick = { menuOpen = false; onJumpUnread() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_mark_unread)) },
                        onClick = { menuOpen = false; onMarkUnread() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_export_title)) },
                        onClick = { menuOpen = false; onExport() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_settings_title)) },
                        onClick = { menuOpen = false; onChatSettings() }
                    )
                }
            }
        }
        HorizontalDivider(color = palette.divider)
    }
}

@Composable
internal fun MessageBubble(message: ChatMessage, observeFile: (Int) -> StateFlow<MediaFileState>, onDownloadFile: (Int) -> Unit,
    isRead: Boolean = false, highlighted: Boolean = false, onOpen: () -> Unit, onActions: (() -> Unit)? = null, onReplyClick: (MessageReply) -> Unit = {}, onPlayed: () -> Unit = {}, onRetry: () -> Unit = {}, onReply: ((Long) -> Unit)? = null, onReaction: (Long, ReactionOption) -> Unit, observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>, inlineContent: @Composable () -> Unit = {},
    onBubblePositioned: ((Rect) -> Unit)? = null,
    translation: TranslationUiState? = null,
    onTranslationRetry: (() -> Unit)? = null,
    onTranslationCancel: (() -> Unit)? = null,
    onTranslationDismiss: (() -> Unit)? = null,
    transcriptionRequestPending: Boolean = false,
    transcriptionRequestError: String? = null,
    transcriptionRated: Boolean = false,
    onTranscribe: (() -> Unit)? = null,
    onRateTranscription: ((Boolean) -> Unit)? = null) {
    val palette = beyGramPalette()
    val metrics = LocalBubbleMetrics.current
    val shape = bubbleShape(message.isOutgoing, metrics)
    val hasText = message.text.isNotBlank()
    val circle = message.media?.kind == MediaKind.VideoNote
    // Стикер лежит без пузыря: ни цвета, ни ширины фото.
    val sticker = message.media?.kind == MediaKind.Sticker
    // Время и статус кружка находятся под кругом, вне обрезки видео.
    val inCircle = circle && message.sendState != MessageSendState.Failed
    // Пока сообщение отправляется или не ушло, его id временный и потом меняется: место для общего плеера не ставим.
    val playbackOrigin = if (message.sendState == MessageSendState.Sent) PlaybackOrigin(message.chatId, message.id, LocalPlaybackAccountId.current) else null
    val statusIcon = when {
        !message.isOutgoing -> null
        message.sendState == MessageSendState.Sending -> Icons.Rounded.Schedule
        message.sendState == MessageSendState.Sent -> if (isRead) Icons.Rounded.DoneAll else Icons.Rounded.Done
        else -> null
    }
    // Фото, видео и гифки без подписи: время ставится поверх нижнего правого угла, как в Telegram.
    val overlayMeta = !circle && !hasText && message.reply == null && message.sendState != MessageSendState.Failed &&
        message.media?.kind?.let { it == MediaKind.Photo || it == MediaKind.Video || it == MediaKind.Animation } == true
    val openMediaLabel = stringResource(R.string.a11y_open_attachment)
    val messageActionsLabel = stringResource(R.string.a11y_message_actions)
    Column(Modifier.fillMaxWidth().then(if (circle) Modifier else Modifier.padding(horizontal = metrics.sideInset)), horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start) {
        SwipeToReply(enabled = onReply != null && message.sendState == MessageSendState.Sent, outgoing = message.isOutgoing,
            onReply = { onReply?.invoke(message.id) }) { swipe ->
            // Голосовое и кружок управляются своими элементами в пузыре и не открывают просмотрщик.
            BubbleLayout(
                swipe.widthIn(max = if (circle) androidx.compose.ui.unit.Dp.Infinity else 292.dp)
                    .then(if (message.media != null && !circle && !sticker)
                         Modifier.width(if (message.media.kind == MediaKind.Audio) 280.dp else 244.dp) else Modifier).clip(shape)
                    .background(if (circle || sticker) Color.Transparent else if (message.isOutgoing) palette.outgoingBubble else palette.incomingBubble)
                    .then(if (highlighted && !circle) Modifier.border(2.dp, palette.accent, shape) else Modifier)
                    .then(if (onBubblePositioned != null) Modifier.onGloballyPositioned { onBubblePositioned(it.boundsInWindow()) } else Modifier)
                    .combinedClickable(
                        onClickLabel = if (message.media != null && !message.media.kind.playsInline()) openMediaLabel else null,
                        onLongClickLabel = messageActionsLabel,
                        onClick = { if (message.media != null && !message.media.kind.playsInline()) onOpen() },
                        onLongClick = onActions
                    ),
                overlayMeta = overlayMeta,
                body = {
                    Column {
                        if (!message.isOutgoing && message.senderName.isNotBlank()) Text(message.senderName, color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        // Media sits flush against the bubble's top and side edges; text keeps its own padding.
                        message.media?.let { media ->
                            if (media.kind == MediaKind.VideoNote) VideoNoteBubble(media, observeFile, onDownloadFile, onPlayed, onActions,
                                time = if (inCircle) message.time else "", statusIcon = if (inCircle) statusIcon else null,
                                statusDescription = when {
                                    !message.isOutgoing -> null
                                    message.sendState == MessageSendState.Sending -> stringResource(R.string.chat_sending_description)
                                    isRead -> stringResource(R.string.chat_read_description)
                                    else -> stringResource(R.string.chat_sent_description)
                                }, unseen = !message.isOutgoing && !media.isViewed, highlighted = highlighted, origin = playbackOrigin,
                                transcriptionRequestPending = transcriptionRequestPending,
                                transcriptionRequestError = transcriptionRequestError,
                                transcriptionRated = transcriptionRated,
                                onTranscribe = onTranscribe,
                                onRateTranscription = onRateTranscription)
                            else MediaPreview(media, observeFile, onDownloadFile, onOpen, onActions, onPlayed, origin = playbackOrigin,
                                transcriptionRequestPending = transcriptionRequestPending,
                                transcriptionRequestError = transcriptionRequestError,
                                transcriptionRated = transcriptionRated,
                                onTranscribe = onTranscribe,
                                onRateTranscription = onRateTranscription)
                        }
                        // Без текста и ответа под фото нет строки: время уже стоит поверх него.
                        if (hasText || message.reply != null || !overlayMeta) Column(Modifier.padding(start = metrics.textInset, end = metrics.textInset, top = if (circle) metrics.circleTopInset else metrics.textTopInset)) {
                            message.reply?.let { reply ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(metrics.replyRadius))
                                    .border(1.dp, palette.accent, RoundedCornerShape(metrics.replyRadius))
                                    .clickable { onReplyClick(reply) }.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(reply.senderName.ifBlank { stringResource(R.string.chat_reply_to_message) }, color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(reply.text.resolve(), color = palette.textSecondary, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (hasText) FormattedMessageText(message)
                            message.linkPreview?.let { preview -> MessageLinkPreviewCard(preview, observeFile, onDownloadFile) }
                            translation?.let { state ->
                                TranslationBlock(
                                    state = state,
                                    onRetry = { onTranslationRetry?.invoke() },
                                    onCancel = { onTranslationCancel?.invoke() },
                                    onDismiss = { onTranslationDismiss?.invoke() },
                                    messageId = message.id
                                )
                            }
                        }
                    }
                },
                meta = {
                    if (!inCircle) BubbleMeta(message, isRead, retryAvailable = message.isOutgoing && message.sendState == MessageSendState.Failed,
                        overlay = overlayMeta, onRetry = onRetry)
                },
                footer = inlineContent
            )
        }
        MessageReactionChips(message.reactions, observeCustomEmoji, observeFile, onDownloadFile, onToggle = { onReaction(message.id, it) }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
    }
}


/** A Telegram call is an event with a direction and result, not an empty message bubble. */
@Composable
private fun CallMessageChip(text: String, time: String) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(16.dp))
                .background(palette.surface.copy(alpha = 0.92f))
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Call, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Column {
                Text(text, color = palette.textPrimary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2)
                Text(time, color = palette.textSecondary, fontSize = 11.sp)
            }
        }
    }
}

/** Telegram service actions have no sender bubble, read checks or swipe-to-reply affordances. */
@Composable
internal fun ServiceMessageChip(text: String, userId: Long = 0L, onOpenUser: (Long) -> Unit = {}) {
    val palette = beyGramPalette()
    Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            color = palette.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.clip(RoundedCornerShape(14.dp))
                .background(palette.surface.copy(alpha = 0.87f))
                .then(if (userId > 0L) Modifier.clickable { onOpenUser(userId) } else Modifier)
                .padding(horizontal = 13.dp, vertical = 7.dp)
        )
    }
}
