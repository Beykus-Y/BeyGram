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
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
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
import com.beykus.beygram.deeplink.MessageEntityLink
import com.beykus.beygram.deeplink.copyableMessageLinks
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
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackQueueSource
import com.beykus.beygram.playback.PlaybackOrigin
import com.beykus.beygram.playback.LocalPlaybackAccountId
import com.beykus.beygram.security.AppLockManager
import com.beykus.beygram.ui.accessibility.accessibleTouchTarget
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.ChatAvatar
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.motionEntrance
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.playback.NowPlayingBar
import com.beykus.beygram.ui.components.beyGramFilledTextFieldColors
import com.beykus.beygram.ui.components.beyGramTextFieldColors
import com.beykus.beygram.ui.text.resolve
import com.beykus.beygram.ui.text.userMessage
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** More than one transition can be in flight, but an accidental tap storm must not create an unbounded overlay. */
private const val MAX_ACTIVE_COMPOSER_LAUNCHES = 4

private data class PendingBotWebApp(val url: String, val botUserId: Long, val messageId: Long, val source: BotWebAppSource, val buttonText: String)

/** The synchronous refusal plus the server result it replaced, so a later async result can invalidate it. */
private data class LocalTranscriptionFailure(
    val failure: SpeechRecognitionFailure,
    val serverFailureAtStart: SpeechRecognitionFailure?
)

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
    onBack: () -> Unit, onChatInfo: () -> Unit, onStatistics: (() -> Unit)? = null, connectionState: TelegramConnectionState,
    recordingSettings: VideoMessageSettingsStore, translationSettings: TranslationSettingsStore,
    chatOverrides: ChatOverridesStore, focusMessageId: Long = 0L, onFocusHandled: () -> Unit = {},
    forumTopicId: Long = 0L, forumTopicTitle: String? = null, topicCanPost: Boolean = true, topicUnreadCount: Int = 0,
    onServiceUser: (Long) -> Unit = {},
    /** Current account's Premium status; free-recognition quota hints are hidden for Premium accounts. */
    isPremiumAccount: Boolean = false,
    /** Opens the existing Telegram Premium screen; no purchase is started from a chat bubble. */
    onPremium: (() -> Unit)? = null,
    /** A formatter link that belongs to Telegram: the host resolves it in-app instead of opening a browser. */
    onOpenMessageEntity: ((MessageEntityLink) -> Unit)? = null,
    /** Opens a private chat for a contact card's "Message" action; defaults to the profile route. */
    onOpenUserChat: (Long) -> Unit = onServiceUser,
    /** Opens the channel post's comment thread; only channel posts with a linked discussion group call it. */
    onOpenComments: (ChatMessage) -> Unit = {},
    /** A thread overlay above this chat owns Back until it closes, so the channel must not navigate away. */
    threadOpen: Boolean = false,
    messageAnimations: MessageAnimationConfig = MessageAnimationConfig(),
    accountId: String = com.beykus.beygram.data.tdlib.TdAccountStorageLayout.LEGACY_ACCOUNT_ID,
    accountLabel: String = "",
    /** Repository-backed automatic download; absent means the local policy gates the plain download callback. */
    onAutoDownloadFile: ((AutoDownloadRequest) -> Unit)? = null
) {
    // One persistent state source for text, rich formatting and the selected reply target.
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
    val speechRecognitionTrial by remember(conversation) { conversation.speechRecognitionTrial }.collectAsStateWithLifecycle()
    val speechRecognitionUi = remember(speechRecognitionTrial, isPremiumAccount, onPremium) {
        SpeechRecognitionUi(speechRecognitionTrial, isPremiumAccount, onPremium)
    }
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
    var reportMessageId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var translationLanguages by remember(chat.id) { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var translationStates by remember(chat.id) { mutableStateOf<Map<Pair<Long, String>, TranslationUiState>>(emptyMap()) }
    // A cached translation is valid only for the exact source text/entities that produced it.
    var translationSources by remember(chat.id) { mutableStateOf<Map<Long, Pair<String, List<MessageTextEntity>>>>(emptyMap()) }
    val translationJobs = remember(chat.id) { mutableMapOf<Long, Job>() }
    // Local request state covers the gap before TDLib sends UpdateMessageContent with the recognition result.
    var transcribingIds by remember(chat.id) { mutableStateOf<Set<Long>>(emptySet()) }
    // A local refusal is valid only while TDLib still has the same server result that preceded the attempt.
    var transcriptionErrors by remember(chat.id) { mutableStateOf<Map<Long, LocalTranscriptionFailure>>(emptyMap()) }
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
    // Window bounds of media bubbles, read once when the viewer opens. A plain map: it must not recompose on scroll.
    val mediaOrigins = remember { HashMap<Long, Rect>() }
    // Sticker messages open their set instead of the generic media viewer. Zero means "not in a set" and gets a
    // friendly placeholder from StickerSetSheet rather than a failed TDLib request.
    var openStickerSetId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var viewerRights by remember { mutableStateOf<MessageRights?>(null) }
    var viewerPosition by remember { mutableStateOf<MediaPosition?>(null) }
    var forwardId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var forwardBusy by remember { mutableStateOf(false) }
    var forwardError by remember { mutableStateOf<String?>(null) }
    // A copy hides the original author; this survives recreation together with the pending forwardId.
    var forwardAsCopy by rememberSaveable(chat.id) { mutableStateOf(false) }
    var forwardCopyConfirmId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    // Multi-select is a separate mode from the single-message action bar. Albums are selected as one group.
    var selectionMode by rememberSaveable(chat.id) { mutableStateOf(false) }
    val selectionSaver = remember {
        listSaver<Set<Long>, Long>(save = { it.toList() }, restore = { it.toSet() })
    }
    var selectionIds by rememberSaveable(chat.id, stateSaver = selectionSaver) { mutableStateOf(emptySet<Long>()) }
    // The message the user long-pressed to start selection; its one-message actions stay reachable while the selection is still just it.
    var selectionAnchorId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var selectionBusy by remember { mutableStateOf(false) }
    var selectionError by remember { mutableStateOf<String?>(null) }
    var selectionForwardOpen by rememberSaveable(chat.id) { mutableStateOf(false) }
    var selectionDeleteOpen by rememberSaveable(chat.id) { mutableStateOf(false) }
    var selectionDeleteRights by remember { mutableStateOf<BatchDeleteRights?>(null) }
    var selectionDeleteEveryone by remember { mutableStateOf(false) }
    var selectionDeleteBusy by remember { mutableStateOf(false) }
    var selectionDeleteError by remember { mutableStateOf<String?>(null) }
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
    // A sticker long-pressed in the picker waits for the explicit send-options choice (silent/scheduled).
    var pendingSticker by remember(chat.id) { mutableStateOf<OutgoingMedia?>(null) }
    // The unified sticker/GIF panel replaces the attachment strip above the composer, not the attach menu.
    var stickerPanelOpen by rememberSaveable(chat.id) { mutableStateOf(false) }
    var scheduledMessagesOpen by remember(chat.id) { mutableStateOf(false) }
    var chatSettingsOpen by remember(chat.id) { mutableStateOf(false) }
    val chatOverrideState by chatOverrides.state.collectAsStateWithLifecycle()
    val globalWallpaper = LocalChatWallpaper.current
    val chatWallpaper = chatOverrideState[chat.id]?.wallpaper ?: globalWallpaper
    val secretChats by remember(conversation) { conversation.secretChats }.collectAsStateWithLifecycle()
    val secretChat = secretChats[chat.id]
    val secretChatClosed = chat.isSecret && secretChat?.state == SecretChatState.Closed
    // A closed secret chat cannot accept new messages even though TDLib may still report posting permission.
    val canPostHere = SecretChatPosting.canPost(chat.canPost && topicCanPost, chat.isSecret, secretChat?.state)
    LaunchedEffect(chat.id, chat.isSecret, conversation) {
        if (chat.isSecret) conversation.refreshSecretChat(chat.id)
    }
    // A secret chat must stay out of Recents screenshots and screen recordings while it is open.
    SecureWindowWhileVisible(chat.isSecret)
    // A channel's linked discussion group is stable for the visit; no per-post request is needed to show the chip.
    var linkedDiscussionChatId by remember(chat.id) { mutableLongStateOf(0L) }
    LaunchedEffect(chat.id, chat.isChannel, conversation) {
        if (!chat.isChannel) {
            linkedDiscussionChatId = 0L
            return@LaunchedEffect
        }
        // A transient failure must not look like "no discussion group": retry with a bounded backoff while open.
        var attempt = 0
        while (true) {
            conversation.linkedDiscussionChatId(chat.id)
                .onSuccess { linkedDiscussionChatId = it; return@LaunchedEffect }
            delay((5_000L * (1 shl attempt.coerceAtMost(4))).coerceAtMost(60_000L))
            attempt++
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var copyLinks by remember(chat.id) { mutableStateOf<List<String>>(emptyList()) }
    var botCommandMenu by remember(chat.id) { mutableStateOf<BotCommandMenu?>(null) }
    var botCommandMenuOpen by rememberSaveable(chat.id) { mutableStateOf(false) }
    var inlineSending by remember(chat.id) { mutableStateOf(false) }
    var webAppSession by remember(chat.id) { mutableStateOf<BotWebAppLaunch?>(null) }
    var pendingWebApp by remember(chat.id) { mutableStateOf<PendingBotWebApp?>(null) }
    var webAppOpening by remember(chat.id) { mutableStateOf(false) }
    var reactionOptions by remember(conversation, chat.id) { mutableStateOf<List<ReactionOption>>(emptyList()) }
    val paidReactions = rememberPaidReactions(conversation)
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
    // Bots expose commands through UserFullInfo; non-bot chats resolve to null without an extra full-info request.
    LaunchedEffect(chat.id, conversation) {
        botCommandMenu = conversation.botCommandMenu(chat.id).getOrNull()
    }
    fun insertBotCommand(command: String) {
        if (inlineSending) return
        val normalized = command.trim().removePrefix("/").trim()
        if (normalized.isEmpty()) return
        updateDraft { it.copy(text = "/$normalized ", entities = emptyList()) }
        replyFocusRequest++
        botCommandMenuOpen = false
    }
    fun requestBotWebApp(url: String, botUserId: Long, messageId: Long, source: BotWebAppSource, buttonText: String) {
        if (webAppSession != null || webAppOpening || pendingWebApp != null) return
        pendingWebApp = PendingBotWebApp(url, botUserId, messageId, source, buttonText)
    }
    fun launchBotWebApp(request: PendingBotWebApp) {
        val theme = palette.toBotWebAppTheme()
        scope.launch {
            webAppOpening = true
            try {
                conversation.openBotWebApp(
                    chat.id,
                    request.botUserId,
                    request.url,
                    request.source,
                    forumTopicId,
                    request.messageId.takeIf { it > 0L },
                    theme,
                    request.buttonText
                )
                    .onSuccess { webAppSession = it }
                    .onFailure { error = it.userMessage(context, R.string.chat_webapp_open_failed) }
            } finally {
                webAppOpening = false
            }
        }
    }
    // The callback owns a short-lived TDLib launch; close it even when recreation or navigation disposes the chat.
    DisposableEffect(webAppSession?.launchId) {
        val launchId = webAppSession?.launchId ?: 0L
        onDispose { conversation.closeBotWebApp(launchId) }
    }
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
        if (reaction.isPaid) {
            paidReactions.open(chat.id, messageId)
            return
        }
        scope.launch {
            conversation.toggleReaction(chat.id, messageId, reaction, addToRecent).onFailure { error = it.userMessage(context, R.string.chat_reaction_failed) }
        }
    }
    // Chip taps leave the user's recent reactions alone; only a reaction picked from the extended menu is added there.
    fun react(messageId: Long, reaction: ReactionOption) = setReaction(messageId, reaction, addToRecent = false)
    fun capturedRecording(media: OutgoingMedia, previewFirst: Boolean) {
        if (previewFirst) recordingRecovery.showPreview(listOf(media))
        else sendRecordedMedia(listOf(media))
    }
    val attachments = rememberAttachmentState(
        chatId = chat.id,
        sendMedia = { media, caption, options ->
            val stealthScheduledBefore = stealthWillSchedule(conversation, options)
            val result = if (media.size > 1) conversation.sendMediaAlbum(chat.id, media, caption, replyId, draftEntities, forumTopicId = forumTopicId, options = options)
            else conversation.sendMedia(chat.id, media.single(), caption, replyId, draftEntities, forumTopicId = forumTopicId, options = options)
            if (result.isSuccess) { clearReply(); if (stealthScheduledBefore) showStealthScheduledNotice(context) }
            result
        },
        sendSticker = { sticker, options ->
            val stealthScheduledBefore = stealthWillSchedule(conversation, options)
            val result = conversation.sendSticker(chat.id, sticker, replyId, forumTopicId = forumTopicId, options = options)
            if (result.isSuccess) { clearReply(); if (stealthScheduledBefore) showStealthScheduledNotice(context) }
            result
        },
        sendExtra = { draft, options ->
            val stealthScheduledBefore = stealthWillSchedule(conversation, options)
            val result = when (draft) {
                is MessageExtraDraft.Poll -> conversation.sendPoll(chat.id, draft.poll, replyId, forumTopicId = forumTopicId, options = options)
                is MessageExtraDraft.Location -> conversation.sendLocation(chat.id, draft.point, false, replyId, forumTopicId = forumTopicId, options = options)
                is MessageExtraDraft.Contact -> conversation.sendContactCard(chat.id, draft.userId, replyId, forumTopicId = forumTopicId, options = options)
                is MessageExtraDraft.Dice -> conversation.sendDice(chat.id, draft.emoji, replyId, forumTopicId = forumTopicId, options = options)
                is MessageExtraDraft.MusicFile, is MessageExtraDraft.MusicBotResult -> conversation.sendMusicDraft(chat.id, draft, replyId, forumTopicId, options)
            }
            if (result.isSuccess) { clearReply(); if (stealthScheduledBefore) showStealthScheduledNotice(context) }
            result
        }
    )
    fun retryFailedMessages(messageIds: List<Long>, sendNowConsent: Boolean = false) {
        scope.launch {
            val result = conversation.retryMessages(chat.id, messageIds, sendNowConsent)
            val failure = result.exceptionOrNull() ?: return@launch
            // A resend that stealth mode cannot keep scheduled goes out only after the user agrees.
            if (attachments.stealthFallback.offer(failure) { retryFailedMessages(messageIds, sendNowConsent = true) }) return@launch
            error = failure.message ?: context.getString(R.string.chat_retry_send_failed)
        }
    }
    // View-once is a regular-private-chat photo/video feature. TDLib rejects per-message self-destruct in secret chats,
    // groups, channels and Saved Messages, so those keep only the separate chat-wide TTL controls.
    val allowViewOnce = !chat.isSecret && !chat.isGroup && !chat.isChannel && !chat.isSavedMessages
    // TDLib accepts polls only in groups/channels with the poll right, in Saved Messages and in private bot chats.
    // Ordinary private chats with people must not offer the composer, or the send would only fail server-side.
    val pollsAllowed = when {
        chat.isSecret -> false
        chat.isSavedMessages -> true
        chat.isGroup || chat.isChannel -> chat.canSendPolls
        else -> chat.canSendPolls || info?.kind == ProfileKind.Bot
    }
    ComposerExtraPickers(chat.id, pollsAllowed, conversation, attachments, remember(accountId, chat.isSecret) { if (chat.isSecret) null else ComposerMusicSource(accountId, observeFile, onDownloadFile) })
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
    // Any other composer mode takes over the strip; an open sticker panel must not cover it or leak a stale draft state.
    LaunchedEffect(
        stickerPanelOpen,
        attachments.menuOpen,
        attachments.active,
        canPostHere,
        threadOpen,
        actionsId,
        deleting,
        selectedMediaId,
        forwardId,
        contextChatId,
        scheduledMessagesOpen,
        chatSettingsOpen,
        exportOpen,
        translationPickerId,
        sendOptionsOpen,
        sending,
        inlineSending,
        editId,
        searching,
        selectionMode,
        videoNote.active,
        audioNote.active
    ) {
        if (!canPostHere || threadOpen || actionsId != null || deleting || selectedMediaId != null ||
            forwardId != null || contextChatId != 0L || scheduledMessagesOpen || chatSettingsOpen ||
            exportOpen || translationPickerId != null ||
            sendOptionsOpen || sending || inlineSending ||
            attachments.menuOpen || attachments.active ||
            editId != null || searching || selectionMode || videoNote.active || audioNote.active
        ) {
            stickerPanelOpen = false
        }
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
            } else {
                AppLockManager.get(context).expectExternalActivity()
                audioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        } else if (!VIDEO_NOTE_RECORDING_ENABLED) {
            error = context.getString(R.string.recorder_video_note_unavailable)
        } else if (videoNoteAllowed(context)) {
            PlaybackEngine.pause()
            videoNote.press()
        } else {
            AppLockManager.get(context).expectExternalActivity()
            videoPermissions.launch(videoNotePermissions)
        }
    }
    val messages = history.messages
    val selectedMessages = remember(messages, selectionIds) { messages.filter { it.id in selectionIds } }
    val selectionCanCopy = selectedMessages.any { it.text.isNotBlank() }
    val selectionCanSave = selectedMessages.any { it.media != null }
    // A deleted or reloaded-away message must not keep a phantom selection count. Wait for the first loaded page,
    // otherwise a restored selection would be cleared before history arrives.
    LaunchedEffect(selectionMode, messages) {
        if (selectionMode && messages.isNotEmpty()) {
            val loaded = messages.mapTo(hashSetOf()) { it.id }
            val kept = selectionIds.filterTo(linkedSetOf()) { it in loaded }
            if (kept != selectionIds) selectionIds = kept
        }
    }
    // Sender picker is built from the loaded history; no extra TDLib member request is needed in the search bar.
    val searchSenders = remember(messages) { chatSearchSenders(messages) }
    // Кружки в обработке стоят в списке первыми (внизу ленты), чтобы индексы прокрутки и дат учитывали их тоже.
    val rawMessageItems = remember(messages, pendingVideoNotes) {
        pendingVideoNotes.map { ChatMessageListItem("pending-video:${it.sessionName}", emptyList(), pendingVideoNote = it) } + groupTelegramMediaAlbums(messages)
    }
    val messageListItems = remember(rawMessageItems, scrollPosition.readBoundaryId) {
        scrollPosition.readBoundaryId?.let { withUnreadSeparator(rawMessageItems, it) } ?: rawMessageItems
    }
    // In a private chat the peer is already named in the header; groups need the author once per block, not per bubble.
    val senderHeaderByKey = remember(messageListItems, chat.isGroup, chat.isChannel, chat.isSavedMessages) {
        senderHeaderVisibility(chat, messageListItems)
    }
    val messageItemsByKey = remember(messageListItems) { messageListItems.associateBy { it.key } }
    val currentMessageItemsByKey by rememberUpdatedState(messageItemsByKey)
    val currentMessages by rememberUpdatedState(messages)
    // Live insertions are claimed before the lazy list composes its rows, so only genuinely new messages animate once.
    val animationTracker = remember(chat.id, forumTopicId) { MessageArrivalTracker() }
    remember(history) { animationTracker.observe(history) }
    // Decorative effects are capped per chat, so a burst of arrivals cannot stack several Lottie overlays.
    val effectLimiter = remember(chat.id, forumTopicId) { MessageEffectPlaybackLimiter() }
    // Composer Launch is a spatial overlay; the real row stays hidden until the overlay lands on it.
    val composerLaunches = remember(chat.id, forumTopicId) { mutableStateListOf<ComposerLaunch>() }
    var composerFieldBounds by remember { mutableStateOf<Rect?>(null) }
    val composerCursor = remember(chat.id) { ComposerCursor() }
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
    val swipeReplyAllowed = canPostHere && !searching && !deleting && !selectionMode && editMessage == null && actionMessage == null && !videoNote.active && !audioNote.active &&
        !sending && !attachments.sending && !recordingQueueSending && pendingVideoNotes.isEmpty()
    LaunchedEffect(editId) { editEntities = editMessage?.entities.orEmpty() }
    LaunchedEffect(botKeyboard?.messageId) { if (botKeyboard?.forceReply == true) updateDraft { it.copy(replyToMessageId = botKeyboard?.messageId) } }
    val editLimit = if (editMessage?.media == null) limits.text else limits.caption
    fun closeActions() { actionsId = null; deleting = false; deleteError = null; capabilities = null; capabilitiesError = null; pinConfirm = false }
    fun enterSelection(message: ChatMessage? = null) {
        closeActions()
        selectionMode = true
        selectionError = null
        if (message != null) {
            val group = selectionGroupIds(messages, message.id)
            if (selectionIds.size + group.count { it !in selectionIds } > MAX_MESSAGE_BATCH_SIZE) {
                selectionError = context.getString(R.string.message_selection_limit, MAX_MESSAGE_BATCH_SIZE)
                return
            }
            selectionIds = selectionIds + group
        }
    }
    fun exitSelection() {
        selectionMode = false
        selectionIds = emptySet()
        selectionAnchorId = null
        selectionError = null
        selectionForwardOpen = false
        selectionDeleteOpen = false
        selectionDeleteRights = null
        selectionDeleteBusy = false
        selectionDeleteError = null
    }
    // A long press on a bubble starts multi-select with that message already picked. The one-message action sheet stays
    // reachable from the selection bar while the selection is exactly the pressed message or its album.
    fun selectOnLongPress(messageId: Long) {
        selectionAnchorId = messageId
        messages.firstOrNull { it.id == messageId }?.let { enterSelection(it) }
    }
    val singleActionTarget = selectionAnchorId?.takeIf { selectionIds == selectionGroupIds(messages, it) }
    fun toggleSelection(message: ChatMessage) {
        val group = selectionGroupIds(messages, message.id)
        val removing = group.all { it in selectionIds }
        if (!removing && selectionIds.size + group.count { it !in selectionIds } > MAX_MESSAGE_BATCH_SIZE) {
            selectionError = context.getString(R.string.message_selection_limit, MAX_MESSAGE_BATCH_SIZE)
            return
        }
        selectionIds = if (removing) selectionIds - group else selectionIds + group
        selectionError = null
    }
    fun copySelection() {
        if (selectionBusy) return
        val targets = selectedMessages.filter { it.text.isNotBlank() }
        if (targets.isEmpty()) {
            selectionError = context.getString(R.string.message_selection_copy_empty)
            return
        }
        selectionBusy = true
        selectionError = null
        scope.launch {
            try {
                val results = coroutineScope {
                    targets.map { message -> async { conversation.messageCapabilities(chat.id, message.id) } }.awaitAll()
                }
                val failure = results.firstNotNullOfOrNull { it.exceptionOrNull() }
                if (failure != null) {
                    selectionError = failure.userMessage(context, R.string.err_chat_actions_load_failed)
                    return@launch
                }
                if (results.any { it.getOrNull()?.canCopy != true }) {
                    selectionError = context.getString(R.string.err_messages_cannot_copy)
                    return@launch
                }
                val text = selectedText(messages, targets.mapTo(hashSetOf()) { it.id })
                clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.chat_message), text))
                Toast.makeText(context, context.getString(R.string.message_selection_copied, targets.size), Toast.LENGTH_SHORT).show()
                exitSelection()
            } finally {
                selectionBusy = false
            }
        }
    }
    fun saveSelection() {
        if (selectionBusy) return
        val targets = selectedMessages.filter { it.media != null }
        if (targets.isEmpty()) {
            selectionError = context.getString(R.string.message_selection_save_none)
            return
        }
        selectionBusy = true
        selectionError = null
        scope.launch {
            var saved = 0
            var skipped = 0
            var failed = 0
            try {
                for (message in targets) {
                    val media = message.media ?: continue
                    val canSave = conversation.messageCapabilities(chat.id, message.id).getOrNull()?.canSave == true
                    if (!canSave || media.isSecret) {
                        skipped++
                        continue
                    }
                    val state = observeFile(media.fileId)
                    val path = state.value.localPath ?: run {
                        onDownloadFile(media.fileId)
                        withTimeoutOrNull(60_000) {
                            state.first { it.localPath != null || it.error != null }.localPath
                        }
                    }
                    if (path == null) failed++
                    else if (withContext(Dispatchers.IO) { saveSelectedMediaToDevice(context, path, media) }.isSuccess) saved++
                    else failed++
                }
            } finally {
                selectionBusy = false
            }
            val message = when {
                saved > 0 && skipped + failed == 0 -> context.getString(R.string.message_selection_saved, saved)
                saved > 0 -> context.getString(R.string.message_selection_saved_partial, saved, skipped + failed)
                skipped > 0 -> context.getString(R.string.message_selection_save_denied)
                else -> context.getString(R.string.media_save_failed)
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            if (saved == 0) selectionError = message
        }
    }
    fun requestSelectionDelete() {
        if (selectionBusy || selectionIds.isEmpty()) return
        val targets = selectedMessages
        selectionBusy = true
        selectionError = null
        scope.launch {
            try {
                val results = coroutineScope {
                    targets.map { message -> async { conversation.messageCapabilities(chat.id, message.id) } }.awaitAll()
                }
                val failure = results.firstNotNullOfOrNull { it.exceptionOrNull() }
                if (failure != null) {
                    selectionError = failure.userMessage(context, R.string.err_chat_actions_load_failed)
                    return@launch
                }
                val rights = batchDeleteRights(results.mapNotNull { it.getOrNull() })
                if (!rights.canDelete) {
                    selectionError = context.getString(R.string.err_messages_cannot_delete)
                    return@launch
                }
                selectionDeleteRights = rights
                selectionDeleteEveryone = !rights.canDeleteForSelf
                selectionDeleteError = null
                selectionDeleteOpen = true
            } finally {
                selectionBusy = false
            }
        }
    }
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
        val serverFailureAtStart = media.transcriptionError
        transcriptionErrors = transcriptionErrors - message.id
        transcribingIds = transcribingIds + message.id
        scope.launch {
            val result = conversation.recognizeSpeech(message.chatId, message.id)
            transcribingIds = transcribingIds - message.id
            result.onFailure { error ->
                val failure = (error as? SpeechRecognitionException)?.failure
                    ?: SpeechRecognitionFailure(SpeechRecognitionFailureReason.Other)
                transcriptionErrors = transcriptionErrors + (message.id to LocalTranscriptionFailure(failure, serverFailureAtStart))
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
    /**
     * Emoji and backspace from the sticker panel change the text the way a typed change does: formatting ranges are
     * adjusted first, then the text changes. A send that holds the draft ignores them, as it ignores typing.
     */
    fun editComposerText(edit: (String, TextRange) -> ComposerTextEdit) {
        if (hidingComposerForLaunch) return
        val before = if (editMessage != null) editText else draft
        val result = edit(before, composerCursor.selectionFor(before))
        if (result.text == before) return
        // The field adopts this selection when the new text reaches it, so the cursor stays after the inserted emoji.
        composerCursor.update(result.text, result.selection)
        if (editMessage != null) {
            editEntities = adjustTextEntities(before, result.text, editEntities)
            editText = result.text
        } else {
            updateDraft { it.copy(entities = adjustTextEntities(before, result.text, it.entities)) }
            typedDraft(result.text)
        }
    }
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
    LaunchedEffect(conversation, chat.id, reactionOwner?.id) {
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
    BackHandler(enabled = !threadOpen && (stickerPanelOpen || searching || actionMessage != null || editMessage != null || attachments.menuOpen || attachments.active || selectionMode)) {
        when {
            selectionDeleteOpen -> { selectionDeleteOpen = false; selectionDeleteError = null }
            selectionForwardOpen -> { selectionForwardOpen = false; forwardError = null; forwardAsCopy = false }
            selectionMode -> exitSelection()
            searching -> closeSearch()
            deleting -> { deleting = false; deleteError = null }
            actionMessage != null -> closeActions()
            editMessage != null -> closeEdit()
            stickerPanelOpen -> stickerPanelOpen = false
            else -> attachments.discardAll()
        }
    }
    BackHandler(enabled = openStickerSetId != null) { openStickerSetId = null }
    BackHandler(enabled = !threadOpen && !selectionMode && actionMessage == null && editMessage == null && !attachments.menuOpen && !attachments.active && !searching && openStickerSetId == null && !stickerPanelOpen, onBack = onBack)
    BackHandler(enabled = !threadOpen && (videoNote.active || audioNote.active)) {
        videoNote.stopForRecovery()
        audioNote.stopForRecovery()
    }

    // Loading/caching messages never triggers receipts; only the focused, resumed viewport does.
    LaunchedEffect(chat.id, lifecycle, conversation, threadOpen) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow {
                if (!window.isWindowFocused || searching || threadOpen || attachments.cameraOpen || attachments.editor != null || !scrollPosition.readBoundaryChecked) emptyList() else {
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

    // Composer sends confirm first while hidden presence warns about sending; asked once per chat screen (see StealthSendGuard).
    val sendGuard = rememberStealthSendGuard(chat.id, conversation)
    val delayed = rememberDelayedSend(chat.id, forumTopicId, conversation)
    /**
     * Sends the current text draft or prepared attachments with [options]. The send-options menu is one-shot:
     * the choice applies to this send only and is not kept for the next one.
     */
    fun sendCurrentDraft(options: MessageSendOptions) {
        if (secretChatClosed || editId != null || inlineSending) return
        pendingSticker?.let { sticker ->
            pendingSticker = null
            attachments.sendStickerWithOptions(sticker, options)
            return
        }
        // View-once is selected in the attachment panel; scheduling or groups silently drop the flag instead of
        // producing a TDLib error, and the composer hides those paths for view-once media.
        val effectiveOptions = if (attachments.active) {
            options.copy(viewOnce = attachments.viewOnce && ViewOncePolicy.canSend(attachments.pending, isPrivateChat = allowViewOnce, isSecretChat = chat.isSecret, scheduled = options.scheduledAtSeconds != null))
        } else options
        if (attachments.active) {
            attachments.send(draft, effectiveOptions) { value -> updateDraft { it.copy(text = value, entities = if (value.isEmpty()) emptyList() else it.entities) } }
            return
        }
        val outgoingAnimation = messageAnimations.direction(outgoing = true)
        val outgoing = composerLaunchPayload(draft, draftEntities, limits.text)
        // Stealth mode hands the message to Telegram's scheduled queue (#541), or waits out a short lead itself; the local
        // delay is a separate convenience and is skipped then, so stealth sending is the only thing that holds the message.
        val stealthScheduled = stealthWillSchedule(conversation, effectiveOptions)
        val stealthHolds = effectiveOptions.scheduledAtSeconds == null && conversation.stealthHoldsSends()
        // A delayed text is stored first and only then cleared from the composer. A bot's forced reply is always sent directly.
        if (!stealthScheduled && !stealthHolds && !effectiveOptions.sendNowConsent && botKeyboard?.forceReply != true &&
            delayed.tryEnqueue(outgoing.text, outgoing.entities, replyId, forumTopicId, effectiveOptions, onQueued = { updateDraft { MessageDraft() } })) return
        // A scheduled message is not in the chat yet, so it must not run the Composer Launch animation.
        val launch = if (effectiveOptions.scheduledAtSeconds == null && !stealthScheduled && messageAnimations.enabled && outgoingAnimation.preset == MessageAnimationPreset.ComposerLaunch && outgoing.text.isNotBlank()) {
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
        // A stealth lead too short for the server is waited out by the repository and outlives this screen, so the draft is
        // taken now: a later success must not clear text typed meanwhile. A failure puts it back if the composer is still empty.
        val stealthWaitsLocally = stealthHolds && !stealthScheduled && !effectiveOptions.sendNowConsent
        val submitted = composerDraft
        if (stealthWaitsLocally) updateDraft { MessageDraft() }
        sending = true; error = null
        scope.launch {
            var delivered = false
            try {
                val result = conversation.sendMessage(
                    chat.id, outgoing.text, replyId, outgoing.entities, forumTopicId = forumTopicId,
                    clearDraft = !stealthWaitsLocally, options = effectiveOptions
                )
                if (stealthWaitsLocally && result.isFailure) updateDraft { current -> if (current.text.isEmpty()) submitted else current }
                if (result.isSuccess) {
                    delivered = true
                    if (!stealthWaitsLocally) updateDraft { MessageDraft() }
                    launch?.awaitingSend = false
                    if (composerDraftLaunchKey == launch?.key) composerDraftLaunchKey = null
                    botKeyboard?.takeIf { it.forceReply }?.let { conversation.dismissBotKeyboard(chat.id, it.messageId) }
                    if (effectiveOptions.scheduledAtSeconds != null) {
                        Toast.makeText(context, context.getString(R.string.chat_schedule_success), Toast.LENGTH_SHORT).show()
                    } else if (stealthScheduled) {
                        showStealthScheduledNotice(context)
                    }
                } else if (!attachments.stealthFallback.offer(result.exceptionOrNull()) { sendCurrentDraft(effectiveOptions.copy(sendNowConsent = true)) }) {
                    error = result.exceptionOrNull()?.userMessage(context, R.string.err_chat_send_failed)
                }
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
        if (selectionMode) SelectionActionBar(
            count = selectionIds.size,
            busy = selectionBusy || forwardBusy,
            error = selectionError,
            canCopy = selectionCanCopy,
            canSave = selectionCanSave,
            onClose = ::exitSelection,
            onMore = singleActionTarget?.let { id -> { exitSelection(); actionsId = id } },
            onForward = { forwardAsCopy = false; selectionForwardOpen = true; selectionError = null },
            onDelete = ::requestSelectionDelete,
            onCopy = ::copySelection,
            onSave = ::saveSelection
        ) else if (actionMessage != null) MessageActionsBar(
            capabilities = capabilities?.let { caps ->
                // TDLib already forbids this; keep a local guard so a stale capability response cannot expose view-once.
                if (actionMessage.isViewOnce) caps.copy(canForward = false, canCopy = false, canGetLink = false, canEdit = false) else caps
            },
            error = capabilitiesError, hasText = actionMessage.text.isNotBlank(), isMedia = actionMessage.media != null,
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
            onCopyEntity = if (actionMessage.isViewOnce || chat.hasProtectedContent) null else copyableMessageLinks(actionMessage.text, actionMessage.entities)
                .takeIf { it.isNotEmpty() }
                ?.let { values -> { copyLinks = values; closeActions() } },
            onForward = { forwardAsCopy = false; forwardId = actionMessage.id; closeActions() },
            onForwardCopy = { forwardCopyConfirmId = actionMessage.id; closeActions() },
            onSelect = { enterSelection(actionMessage) },
            onTranslate = if (actionMessage.mediaAlbumId == 0L && actionMessage.sendState == MessageSendState.Sent &&
                TranslationPolicy.canTranslate(chat.isSecret, actionMessage.text.isNotBlank())) {
                { translationPickerId = actionMessage.id; closeActions() }
            } else null,
            onReport = if (!actionMessage.isOutgoing && !chat.isSecret) {
                { reportMessageId = actionMessage.id; closeActions() }
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
            onSelectMessages = { enterSelection() },
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
            onStatistics = onStatistics,
            onExport = { exportOpen = true },
            secretChat = secretChat
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
        reportMessageId?.let { id ->
            MessageReportDialog(
                onSelect = { reason ->
                    reportMessageId = null
                    scope.launch {
                        conversation.reportMessages(chat.id, listOf(id), reason)
                            .onSuccess { Toast.makeText(context, context.getString(R.string.message_report_done), Toast.LENGTH_SHORT).show() }
                            .onFailure { error = it.userMessage(context, R.string.err_message_report_unavailable) }
                    }
                },
                onDismiss = { reportMessageId = null }
            )
        }
        reactionOwner?.takeIf { !deleting }?.let { owner ->
            MessageReactionPicker(reactionOptions, owner.reactions.filter { it.isChosen && !it.isPaid }.mapTo(hashSetOf()) { it.option }, conversation::observeCustomEmoji, observeFile, onDownloadFile) { option ->
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
        // Non-inline wrappers keep these large blocks out of ChatScreen's own bytecode (JVM 64 KB method limit).
        ChatScreenBox(Modifier.fillMaxWidth().weight(1f)) {
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
                    val effectMessage = listItem.messages.firstOrNull { it.effectId != 0L }
                    MessageAnimationFrame(animate = animateArrival, outgoing = latest?.isOutgoing == true, config = messageAnimations) {
                        MessageEffectFrame(
                            effectId = effectMessage?.effectId ?: 0L,
                            messageId = effectMessage?.id ?: latest?.id ?: 0L,
                            animate = animateArrival,
                            observeEffect = conversation::observeMessageEffect,
                            observeFile = observeFile,
                            downloadFile = onDownloadFile,
                            limiter = effectLimiter
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                val commentsPost = listItem.messages.firstOrNull { it.replyCount > 0 }
                                    ?: listItem.messages.firstOrNull()
                                val albumComments = commentsPost?.takeIf { showsChannelComments(chat.isChannel, it.isChannelPost, linkedDiscussionChatId) }
                                MessageAlbumBubble(listItem.messages, observeFile, onDownloadFile,
                                    isRead = latest?.let { it.isOutgoing && it.sendState == MessageSendState.Sent && it.id <= history.lastReadOutboxMessageId } == true,
                                    highlighted = listItem.messages.any { it.id == highlighted || it.id == actionsId },
                                    onOpen = { selectedMediaId = it }, onActions = ::selectOnLongPress, onReplyClick = ::jump,
                                    onMediaBounds = { rect -> listItem.messages.forEach { mediaOrigins[it.id] = rect } }, onReply = if (swipeReplyAllowed) ::swipeReply else null,
                                    onReaction = ::react, observeCustomEmoji = conversation::observeCustomEmoji, onRetry = { retryFailedMessages(listItem.messages.filter { it.sendState == MessageSendState.Failed }.map { it.id }) },
                                    selectionMode = selectionMode,
                                    selected = listItem.messages.all { it.id in selectionIds },
                                    onToggleSelection = { listItem.messages.firstOrNull()?.let(::toggleSelection) },
                                    commentsBar = albumComments?.let { post ->
                                        { ChannelCommentsBar(listItem.messages.maxOfOrNull { it.replyCount } ?: 0) { onOpenComments(post) } }
                                    },
                                    onAutoDownloadFile = onAutoDownloadFile,
                                    showSenderHeader = senderHeaderByKey[listItem.key] == true,
                                    onOpenMessageEntity = onOpenMessageEntity)
                            }
                        }
                    }
                } else {
                    val message = listItem.messages.single()
                    val animateArrival = remember(listItem.key) { animationTracker.claim(listOf(message.id)) }
                    // A confirmed send replaces the temporary id; the dice throw keeps its original identity.
                    val diceAnimationId = rememberDiceAnimationId(message.id, history.recentReplacements)
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
                        MessageEffectFrame(
                            effectId = message.effectId,
                            messageId = message.id,
                            animate = animateArrival && launch == null,
                            observeEffect = conversation::observeMessageEffect,
                            observeFile = observeFile,
                            downloadFile = onDownloadFile,
                            limiter = effectLimiter
                        ) {
                        Column(Modifier.fillMaxWidth()) {
                            val translationLanguage = translationLanguages[message.id]
                            val translationState = translationLanguage?.let { translationStates[message.id to it] }
                            val media = message.media
                            val transcriptionFailure = transcriptionErrors[message.id]
                                ?.takeIf { it.serverFailureAtStart == media?.transcriptionError }
                                ?.failure
                            if (message.isCallEvent && message.serviceText != null) CallMessageChip(message.serviceText, message.time)
                            else if (message.serviceText != null) ServiceMessageChip(message.serviceText, message.serviceUserId, onServiceUser)
                            else MessageBubble(message, observeFile, onDownloadFile, isRead = message.isOutgoing && message.sendState == MessageSendState.Sent && message.id <= history.lastReadOutboxMessageId,
                                highlighted = message.id == highlighted || message.id == actionsId, onOpen = { selectedMediaId = message.id }, onActions = { selectOnLongPress(message.id) }, longPressSelects = true, onReplyClick = ::jump, onReply = if (swipeReplyAllowed) ::swipeReply else null,
                                onPlayed = { conversation.openMessageContent(chat.id, message.id) },
                                onRetry = { retryFailedMessages(listOf(message.id)) }, onReaction = ::react, observeCustomEmoji = conversation::observeCustomEmoji, inlineContent = { InlineBotButtons(message, conversation, ::requestBotWebApp) },
                                onBubblePositioned = bubblePosition,
                                onMediaBounds = { rect -> mediaOrigins[message.id] = rect },
                                translation = translationState,
                                onTranslationRetry = { retryTranslation(message) },
                                onTranslationCancel = { cancelTranslation(message.id) },
                                onTranslationDismiss = { translationLanguages = translationLanguages - message.id },
                                transcriptionRequestPending = message.id in transcribingIds,
                                transcriptionRequestError = transcriptionFailure,
                                transcriptionRated = message.id in ratedTranscriptions,
                                speechRecognitionUi = speechRecognitionUi,
                                onTranscribe = if (message.sendState == MessageSendState.Sent && TranslationPolicy.canTranscribe(chat.isSecret, media)) {
                                    { transcribe(message) }
                                } else null,
                                onRateTranscription = if (chat.isSecret) {
                                    null
                                } else {
                                    { isGood -> rateTranscription(message, isGood) }
                                },
                                onPollVote = { messageId, choices ->
                                    scope.launch {
                                        conversation.votePoll(chat.id, messageId, choices)
                                            .onFailure { error = it.userMessage(context, R.string.chat_poll_vote_failed) }
                                    }
                                },
                                onPollStop = { messageId ->
                                    scope.launch {
                                        conversation.stopPoll(chat.id, messageId)
                                            .onFailure { error = it.userMessage(context, R.string.chat_poll_stop_failed) }
                                    }
                                },
                                onStopLiveLocation = { target ->
                                    if (target.special is MessageSpecialContent.LiveLocation) scope.launch {
                                        conversation.stopLiveLocation(target.chatId, target.id)
                                            .onFailure { error = it.userMessage(context, R.string.chat_location_stop_failed) }
                                    }
                                },
                                onWriteContact = onOpenUserChat,
                                onLoadPollVoters = { messageId, optionIndex, offset ->
                                    conversation.pollVoters(chat.id, messageId, optionIndex, offset)
                                },
                                onAddPollOption = { messageId, text ->
                                    conversation.addPollOption(chat.id, messageId, text)
                                },
                                onAutoDownloadFile = onAutoDownloadFile,
                                onMusicPlay = { selected ->
                                    // Never build a cross-topic, protected or secret conversation playlist.
                                    if (chat.isSecret || chat.hasProtectedContent || selected.systemPrivate) {
                                        PlaybackEngine.toggle(context, selected.copy(secret = true))
                                    } else if (PlaybackEngine.state.value.item?.key == selected.key) {
                                        // A second tap preserves any pages already appended to the active queue.
                                        PlaybackEngine.toggle(context, selected)
                                    } else {
                                        val fallback = context.getString(R.string.media_kind_audio)
                                        val initial = orderedChatMusic(
                                            messages.filter { forumTopicId == 0L || it.forumTopicId == forumTopicId },
                                            accountId,
                                            { id -> observeFile(id).value }, fallback
                                        ).map { if (it.key == selected.key) selected else it }
                                        val queue = if (initial.any { it.key == selected.key }) initial else
                                            (initial + selected).sortedByDescending { it.origin?.messageId ?: 0L }
                                        val index = queue.indexOfFirst { it.key == selected.key }
                                        var cursor = queue.lastOrNull()?.origin?.messageId ?: 0L
                                        suspend fun nextMusicPage(): List<PlaybackItem> {
                                            // Empty filtered pages must advance the TDLib cursor, not stall pagination.
                                            repeat(4) {
                                                if (cursor == 0L) return emptyList()
                                                val page = conversation.sharedMessages(chat.id, SharedMediaKind.Music, cursor)
                                                    .getOrElse { return emptyList() }
                                                val previous = cursor
                                                cursor = page.nextFromMessageId.takeIf { it != previous } ?: 0L
                                                val next = orderedChatMusic(
                                                    page.messages.filter { forumTopicId == 0L || it.forumTopicId == forumTopicId },
                                                    accountId, { id -> observeFile(id).value }, fallback
                                                )
                                                if (next.isNotEmpty()) return next
                                            }
                                            return emptyList()
                                        }
                                        PlaybackEngine.playQueue(
                                            context, queue, index,
                                            PlaybackQueueSource(
                                                accountId, observeFile, onDownloadFile, onAutoDownloadFile,
                                                loadMore = { nextMusicPage() }
                                            )
                                        )
                                    }
                                },
                                animateSpecial = animateArrival && launch == null,
                                diceAnimationId = diceAnimationId,
                                selectionMode = selectionMode,
                                selected = message.id in selectionIds,
                                onToggleSelection = { toggleSelection(message) },
                                onStickerOpen = { stickerMedia -> openStickerSetId = stickerMedia.stickerSetId },
                                commentsBar = if (showsChannelComments(chat.isChannel, message.isChannelPost, linkedDiscussionChatId)) {
                                    { ChannelCommentsBar(message.replyCount) { onOpenComments(message) } }
                                } else null,
                                showSenderHeader = senderHeaderByKey[listItem.key] == true,
                                paidContent = rememberPaidContentActions(conversation),
                                onOpenMessageEntity = onOpenMessageEntity)
                        }
                        }
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
        val deleteRights = selectionDeleteRights
        if (selectionDeleteOpen && deleteRights != null) SelectionDeleteDialog(
            count = selectionIds.size,
            rights = deleteRights,
            everyone = selectionDeleteEveryone,
            busy = selectionDeleteBusy,
            error = selectionDeleteError,
            onChoose = { selectionDeleteEveryone = it },
            onDismiss = { if (!selectionDeleteBusy) { selectionDeleteOpen = false; selectionDeleteError = null } },
            onConfirm = {
                scope.launch {
                    selectionDeleteBusy = true
                    selectionDeleteError = null
                    try {
                        val ids = selectedMessages.map { it.id }.sorted()
                        val deleteScope = if (selectionDeleteEveryone) DeleteMessageScope.ForEveryone else DeleteMessageScope.ForSelf
                        val result = conversation.deleteMessages(chat.id, ids, deleteScope)
                        if (result.isSuccess) {
                            selectionDeleteOpen = false
                            Toast.makeText(context, context.getString(R.string.message_selection_deleted, ids.size), Toast.LENGTH_SHORT).show()
                            exitSelection()
                        } else {
                            selectionDeleteError = result.exceptionOrNull().userMessage(context, R.string.err_chat_delete_failed)
                        }
                    } finally {
                        selectionDeleteBusy = false
                    }
                }
            }
        )
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
        } else if (!searching && !selectionMode) {
            ChatComposerPanel(
                stickerPanelOpen = stickerPanelOpen,
                attachments = attachments,
                draft = draft,
                captionLimit = limits.caption,
                conversation = conversation,
                observeFile = observeFile,
                onDownloadFile = onDownloadFile,
                allowViewOnce = allowViewOnce,
                chatId = chat.id,
                replyId = replyId,
                forumTopicId = forumTopicId,
                scope = scope,
                onReplyConsumed = { sentReplyId ->
                    if (sentReplyId != null) {
                        updateDraft { current ->
                            if (current.replyToMessageId == sentReplyId) current.copy(replyToMessageId = null) else current
                        }
                    }
                },
                onStickerOptions = { sticker ->
                    pendingSticker = sticker
                    sendOptionsOpen = true
                },
                onSendError = { message -> error = message },
                onInsertEmoji = { emoji -> editComposerText { text, selection -> insertAtSelection(text, selection, emoji) } },
                onDeleteBackward = { editComposerText(::deleteBeforeSelection) },
                onCloseStickerPanel = { stickerPanelOpen = false }
            )
            if (replyId != null) Row(Modifier.fillMaxWidth().motionEntrance(MotionToken.BannerChange, 8.dp).background(palette.surface).padding(start = 18.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
        val fieldLimit = when { editing -> editLimit; attachments.active && attachments.extra == null -> limits.caption; else -> limits.text }
        val overLimit = fieldText.codePointCount(0, fieldText.length) > fieldLimit
        val busy = sending || inlineSending || editBusy || attachments.sending
        val canSend = !busy && when {
            editing -> fieldText != editMessage?.text && (editMessage?.media != null || fieldText.isNotBlank())
            attachments.active -> attachments.canSend
            else -> fieldText.isNotBlank()
        }
        val sendLabel = if (editing) stringResource(R.string.common_save) else stringResource(R.string.common_send)
        val sendState = if (busy) stringResource(R.string.chat_sending_description) else null
        val recordingActive = videoNote.active || audioNote.active
        val recordingLocked = if (recordingMode == ComposerRecordingMode.Voice) audioNote.locked else videoNote.locked
        val recordingCanceling = if (recordingMode == ComposerRecordingMode.Voice) audioNote.canceling else videoNote.canceling
        val showRecord = recordingActive || (!editing && !attachments.active && fieldText.isBlank())
        if (!deleting && !searching && !selectionMode && typingNames.isNotEmpty()) TypingRow(typingNames)
        // Мини-плеер стоит над полем ввода и остаётся на месте при поиске и при выборе сообщений. Без поля ввода он внизу экрана, поэтому отступает от системной навигации.
        NowPlayingBar(bottomInset = deleting || searching || selectionMode)
        // Chats the user cannot write to get no composer, only a plain notice, as in Telegram.
        if (!deleting && !searching && !selectionMode && !canPostHere) ReadOnlyChatBar(
            if (secretChatClosed) stringResource(R.string.secret_chat_closed_composer) else stringResource(R.string.chat_read_only)
        )
        if (!deleting && !searching && !selectionMode && canPostHere) ChatScreenColumn(Modifier.fillMaxWidth().background(palette.surface).navigationBarsPadding().imePadding()) {
        // A bot with no outgoing message in its whole history gets an explicit /start action, independent of inline mode or Mini Apps.
        if (info?.kind == ProfileKind.Bot && !editing && !recordingActive && !attachments.active &&
            !history.isLoading && !history.hasOlderMessages && history.messages.none { it.isOutgoing }
        ) BotStartBar(
            conversation = conversation,
            chatId = chat.id,
            forumTopicId = forumTopicId,
            onError = { error = it.userMessage(context, R.string.chat_bot_start_failed) }
        )
        if (!recordingActive && !editing && !attachments.active) BotInlineResultsPanel(
            chatId = chat.id,
            text = draft,
            active = !sending && !hidingComposerForLaunch,
            replyToMessageId = replyId,
            forumTopicId = forumTopicId,
            conversation = conversation,
            observeFile = observeFile,
            onDownloadFile = onDownloadFile,
            onOpenWebApp = ::requestBotWebApp,
            onSendingChange = { busy ->
                inlineSending = busy
                // The attachment picker would replace the panel whose coroutine is waiting for TDLib.
                if (busy) attachments.menuOpen = false
            },
            onSent = { sentText ->
                updateDraft { if (it.text == sentText) it.copy(text = "", entities = emptyList()) else it }
                if (replyId != null) clearReply()
            }
        )
        delayed.Strip(composerEmpty = draft.isEmpty() && replyId == null, onRestoreDraft = { restored -> updateDraft { restored } })
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            if (!recordingActive) {
                if (!editing) {
                    botCommandMenu?.takeIf { it.commands.isNotEmpty() }?.let {
                        BotCommandButton(enabled = !sending && !inlineSending && !attachments.active) { botCommandMenuOpen = true }
                    }
                    AttachmentButton(attachments, enabled = !sending && !inlineSending)
                } else Spacer(Modifier.width(8.dp))
            }
            if (audioNote.active) AudioNoteRecordingDock(audioNote, Modifier.weight(1f), onCancel = audioNote::discardLocked)
            else if (videoNote.active) VideoNoteRecordingDock(videoNote, Modifier.weight(1f), onCancel = videoNote::discardLocked)
            else FormattingTextField(displayedFieldText, displayedFieldEntities,
                onChange = { if (!hidingComposerForLaunch) { if (stickerPanelOpen) stickerPanelOpen = false; if (editing) editText = it else typedDraft(it) } },
                onEntitiesChange = { if (!hidingComposerForLaunch) { if (editing) editEntities = it else updateDraft { state -> state.copy(entities = it) } } },
                placeholder = if (hidingComposerForLaunch) "" else if (attachments.active && attachments.extra == null) stringResource(R.string.chat_caption_placeholder) else botKeyboard?.placeholder?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chat_message),
                locked = busy || hidingComposerForLaunch, isError = false,
                modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused && stickerPanelOpen) stickerPanelOpen = false }.onGloballyPositioned { if (composerFieldBounds != it.boundsInWindow()) composerFieldBounds = it.boundsInWindow() },
                focusRequest = replyFocusRequest, cursor = composerCursor,
                // One entry to Emoji / Stickers / GIF, inside the input and before Record/Send.
                trailingAction = if (!editing) {
                    {
                        StickerPanelEntryButton(
                            open = stickerPanelOpen,
                            enabled = !busy && !attachments.active,
                            onClick = {
                                if (stickerPanelOpen) {
                                    stickerPanelOpen = false
                                } else if (!busy && !attachments.active) {
                                    attachments.menuOpen = false
                                    stickerPanelOpen = true
                                }
                            }
                        )
                    }
                } else null)
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
                    onLongClickLabel = if (!editing && !attachments.viewOnce) stringResource(R.string.chat_send_options) else null,
                    onLongClick = if (editing || attachments.viewOnce) null else { { sendOptionsOpen = true } }
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
                        sendGuard.run { sendCurrentDraft(MessageSendOptions()) }
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
        if (!recordingActive && !editing && !attachments.active) BotReplyKeyboard(chat.id, botKeyboard, conversation, ::requestBotWebApp)
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
    AttachmentMediaHost(attachments)
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
    // A view-once message is deleted by TDLib right after OpenMessageContent. Keep the already opened item for this
    // viewing session, otherwise the live history update would close the viewer before the user can finish looking.
    var retainedViewOnce by remember(chat.id) { mutableStateOf<ChatMessage?>(null) }
    var openedViewOnceIds by remember(chat.id) { mutableStateOf(emptySet<Long>()) }
    // Фото и видео листаются вместе; остальные вложения открываются по одному.
    // Действия над сообщением есть только у сообщений из загруженной истории: редактирование ищет их в ленте.
    val viewedInChat = messages.firstOrNull { it.id == selectedMediaId && it.media != null }
    val liveViewed = viewedInChat ?: mediaWindow.messages.firstOrNull { it.id == selectedMediaId }
    val retainedViewed = retainedViewOnce?.takeIf { it.id == selectedMediaId }
    val viewed = liveViewed?.takeIf { it.media != null } ?: retainedViewed
    LaunchedEffect(selectedMediaId, liveViewed?.media?.fileId, liveViewed?.isViewOnce) {
        if (selectedMediaId == null) {
            retainedViewOnce = null
            return@LaunchedEffect
        }
        if (liveViewed?.media != null && liveViewed.isViewOnce) retainedViewOnce = liveViewed
    }
    val windowPages = mediaWindow.messages.mapNotNull { m -> m.media?.let { MediaViewerItem(m.id, it, m.isViewOnce, chat.id) } }
    val historyPages = remember(messages) { messages.mapNotNull { m -> m.media?.takeIf { it.kind in viewerSwipeKinds }?.let { MediaViewerItem(m.id, it, m.isViewOnce, chat.id) } } }
    // Пока окно не пришло или не загрузилось, листаем уже загруженную историю чата; без неё показываем вложение одно.
    val viewerPages = listOf(windowPages, historyPages).firstOrNull { pages -> pages.any { it.messageId == selectedMediaId } }
        ?: listOfNotNull(viewed?.let { m -> m.media?.let { MediaViewerItem(m.id, it, m.isViewOnce, chat.id) } })
    val viewOnceGate = rememberViewOnceStealthGate(chat.id)
    val viewOnceAsked = viewed?.takeIf { viewOnceGate.needsConsent(chat.id, it.id, it.isViewOnce, conversation.interactionAllowed(InteractionEffect.OpenContent, chat.id)) }
    if (viewOnceAsked != null) viewOnceGate.ConsentDialog(chat.id, viewOnceAsked.id) { selectedMediaId = null }
    if (viewed != null && viewed.media != null && viewOnceAsked == null) MediaViewer(
        pages = viewOnceGate.swipeable(viewerPages, chat.id, viewed.id, conversation.interactionAllowed(InteractionEffect.OpenContent, chat.id)), selectedId = viewed.id, onSelect = { selectedMediaId = it }, originBounds = mediaOrigins[viewed.id],
        observeFile = observeFile, downloadFile = onDownloadFile,
        onViewed = { id ->
            val once = (messages.firstOrNull { it.id == id } ?: retainedViewOnce)?.isViewOnce == true
            if (!once || id !in openedViewOnceIds) {
                if (once) openedViewOnceIds = openedViewOnceIds + id
                viewOnceGate.openContent(conversation, chat.id, id)
            }
        }, onClose = { selectedMediaId = null },
        title = if (viewed.isOutgoing) stringResource(R.string.chat_you) else viewed.senderName.ifBlank { chat.title }, subtitle = listOfNotNull(viewed.time, viewerPosition?.let { stringResource(R.string.chat_media_position, it.number, it.total) }).joinToString(" · "),
        canExport = viewedRights?.let { ViewOncePolicy.canExport(it.canSave, viewed.isViewOnce) } == true,
        confirmExport = { id -> conversation.messageCapabilities(chat.id, id).getOrNull()?.canSave == true },
        actions = viewedInChat?.let { MediaViewerActions(
            canForward = viewedRights?.let { ViewOncePolicy.canForward(it.canForward, viewed.isViewOnce) } == true,
            canEdit = viewedRights?.canEdit == true && !viewed.isViewOnce,
            canDelete = viewedRights?.let { it.canDeleteForSelf || it.canDeleteForAll } == true,
            onForward = { forwardAsCopy = false; forwardId = viewed.id; selectedMediaId = null },
            onEdit = { editId = viewed.id; editText = viewed.text; selectedMediaId = null },
            onDelete = { deleteEveryone = viewedRights?.canDeleteForSelf == false; actionsId = viewed.id; deleting = true; selectedMediaId = null }
        ) }, onShowInChat = { id -> selectedMediaId = null; jumpToMessage(id, R.string.err_chat_history_load_failed) },
            autoDownloadFile = onAutoDownloadFile)
    StickerSetOverlay(
        setId = openStickerSetId,
        conversation = conversation,
        observeFile = observeFile,
        downloadFile = onDownloadFile,
        attachments = attachments,
        canSend = canPostHere,
        onDismiss = { openStickerSetId = null }
    )
    val forwardIds = when {
        forwardId != null -> listOf(forwardId!!)
        selectionForwardOpen -> forwardIdsForSelection(messages, selectionIds)
        else -> emptyList()
    }
    if (forwardIds.isNotEmpty()) ForwardChatDialog(
        chats, forwardBusy, forwardError, observeFile, onDownloadFile, asCopy = forwardAsCopy,
        commentLimit = limits.text,
        loadTopics = { conversation.forumTopics(it) },
        // Each target is one forward request. A target that refuses the messages, or refuses the comment after them,
        // stays selected so a retry only touches it; the others are already sent and drop out of the selection.
        onSend = { targets, comment ->
            forwardBusy = true; forwardError = null
            val failed = mutableListOf<ForwardTarget>()
            var lastError: String? = null
            try {
                for (target in targets) {
                    val topic = target.topicId.takeIf { it > 0L }
                    val forwarded = if (forwardIds.size == 1) {
                        conversation.forwardMessage(
                            chat.id, forwardIds.single(), target.chatId, asCopy = forwardAsCopy, messageThreadId = topic
                        ).map { ForwardedMessages(forwardedIds = forwardIds, failedIds = emptyList()) }
                    } else {
                        conversation.forwardMessages(chat.id, forwardIds, target.chatId, asCopy = forwardAsCopy, messageThreadId = topic)
                    }
                    if (forwarded.getOrNull()?.isComplete != true) {
                        failed += target
                        lastError = forwarded.exceptionOrNull().userMessage(context, R.string.chat_forward_failed)
                        continue
                    }
                    if (comment.isNotEmpty()) {
                        // The draft belongs to the destination composer, which this forward must not clear.
                        conversation.sendMessage(target.chatId, comment, forumTopicId = target.topicId, clearDraft = false).onFailure {
                            failed += target
                            lastError = it.userMessage(context, R.string.chat_forward_failed)
                        }
                    }
                }
            } finally {
                forwardBusy = false
            }
            if (failed.isEmpty()) {
                val copied = forwardAsCopy
                forwardError = null
                if (selectionMode) exitSelection()
                Toast.makeText(
                    context,
                    when {
                        targets.size > 1 -> context.getString(R.string.forward_sent_to_chats, targets.size)
                        forwardIds.size > 1 -> context.getString(R.string.message_selection_forwarded, forwardIds.size)
                        copied -> context.getString(R.string.message_forwarded_without_author)
                        else -> context.getString(R.string.chat_message_forwarded)
                    },
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                // Only the failed targets stay selected, so a retry cannot duplicate a chat that already has the messages.
                forwardError = context.getString(R.string.forward_failed_chats, failed.size) + lastError?.let { "\n$it" }.orEmpty()
            }
            failed
        },
        onClose = { forwardId = null; selectionForwardOpen = false; forwardError = null; forwardAsCopy = false }
    )
    contextMessageId?.let { id -> MessageContextDialog(contextChatId, id, conversation, observeFile, onDownloadFile, onClose = { contextMessageId = null }, autoDownloadFile = onAutoDownloadFile, onOpenMessageEntity = onOpenMessageEntity, allowEntityCopy = !chat.hasProtectedContent) }
    if (copyLinks.isNotEmpty()) MessageLinkCopyDialog(
        links = copyLinks,
        onCopy = { value ->
            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.message_action_copy_entity), value))
            Toast.makeText(context, context.getString(R.string.message_entity_copied), Toast.LENGTH_SHORT).show()
            copyLinks = emptyList()
        },
        onDismiss = { copyLinks = emptyList() }
    )
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
    sendGuard.Dialog()
    attachments.stealthFallback.Dialog()
    if (sendOptionsOpen) SendOptionsDialog(
        canSchedule = !chat.isSecret,
        onDismiss = { sendOptionsOpen = false; pendingSticker = null },
        onSendNow = { sendOptionsOpen = false; sendGuard.run({ pendingSticker = null }) { sendCurrentDraft(MessageSendOptions()) } },
        onSendSilently = { sendOptionsOpen = false; sendGuard.run({ pendingSticker = null }) { sendCurrentDraft(MessageSendOptions(disableNotification = true)) } },
        onSchedule = { at -> sendOptionsOpen = false; sendGuard.run({ pendingSticker = null }) { sendCurrentDraft(MessageSendOptions(scheduledAtSeconds = at)) } }
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
        isSecretChat = chat.isSecret,
        onSecretChatClosed = onBack,
        onClose = { chatSettingsOpen = false }
    )
    if (exportOpen) ChatExportDialog(
        chat = chat,
        accountId = accountId,
        accountLabel = accountLabel,
        conversation = conversation,
        onDismiss = { exportOpen = false }
    )
    botCommandMenu?.let { menu ->
        if (botCommandMenuOpen) BotCommandSheet(chat.title, menu, onDismiss = { botCommandMenuOpen = false }, onInsert = ::insertBotCommand)
    }
    pendingWebApp?.let { request ->
        AlertDialog(
            onDismissRequest = { pendingWebApp = null },
            title = { Text(stringResource(R.string.chat_webapp_consent_title)) },
            text = { Text(stringResource(R.string.chat_webapp_consent)) },
            confirmButton = {
                TextButton(onClick = { pendingWebApp = null; launchBotWebApp(request) }) {
                    Text(stringResource(R.string.chat_webapp_open_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingWebApp = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
    webAppSession?.let { session ->
        // DisposableEffect above closes the TDLib launch when this state is cleared or the screen is disposed.
        BotWebAppScreen(
            session = session,
            title = chat.title,
            theme = palette.toBotWebAppTheme(),
            onSendData = { data -> conversation.sendBotWebAppData(session.botUserId, session.buttonText, data) },
            onClose = { webAppSession = null }
        )
    }
}

/**
 * Box/Column are inline, so their content is compiled into the caller. ChatScreen is close to the
 * JVM method size limit; passing the content through these wrappers compiles it as a separate lambda.
 */
@Composable
private fun ChatScreenBox(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier, content = content)
}

@Composable
private fun ChatScreenColumn(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, content = content)
}

/** Keeps the attachment strip and the unified sticker panel out of ChatScreen's own bytecode-heavy composer lambda. */
@Composable
private fun ChatComposerPanel(
    stickerPanelOpen: Boolean,
    attachments: AttachmentState,
    draft: String,
    captionLimit: Int,
    conversation: ConversationRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onDownloadFile: (Int) -> Unit,
    allowViewOnce: Boolean,
    chatId: Long,
    replyId: Long?,
    forumTopicId: Long,
    scope: CoroutineScope,
    onReplyConsumed: (Long?) -> Unit,
    onStickerOptions: (OutgoingMedia) -> Unit,
    onSendError: (String) -> Unit,
    onInsertEmoji: (String) -> Unit,
    onDeleteBackward: () -> Unit,
    onCloseStickerPanel: () -> Unit
) {
    if (stickerPanelOpen) {
        StickerPanelHost(
            conversation = conversation,
            chatId = chatId,
            replyId = replyId,
            forumTopicId = forumTopicId,
            observeFile = observeFile,
            downloadFile = onDownloadFile,
            scope = scope,
            composerReserve = if (replyId != null) 144.dp else 80.dp,
            onReplyConsumed = onReplyConsumed,
            onAnimationAttach = { animation ->
                attachments.addAnimation(animation.toOutgoingMedia())
                onCloseStickerPanel()
            },
            onStickerOptions = { sticker ->
                onStickerOptions(sticker.toOutgoingMedia())
                onCloseStickerPanel()
            },
            onSendError = onSendError,
            onInsertEmoji = onInsertEmoji,
            onDeleteBackward = onDeleteBackward,
            onClose = onCloseStickerPanel
        )
    } else {
        AttachmentPanel(
            state = attachments,
            caption = draft,
            captionLimit = captionLimit,
            observeFile = observeFile,
            onDownloadFile = onDownloadFile,
            allowViewOnce = allowViewOnce
        )
    }
}

@Composable
private fun ReadOnlyChatBar(message: String) {
    val palette = beyGramPalette()
    Box(Modifier.fillMaxWidth().background(palette.surface).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
        Text(message, color = palette.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ChatTopBar(
    chat: ChatPreview, info: ProfileInfo?, lastSeenStyle: LastSeenStyle, onBack: () -> Unit, onInfo: () -> Unit, onSearch: () -> Unit,
    observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit, forumTopicTitle: String? = null,
    hasUnread: Boolean = false, onSelectMessages: () -> Unit = {}, onMarkUnread: () -> Unit = {}, onJumpUnread: () -> Unit = {},
    onScheduled: () -> Unit = {}, onChatSettings: () -> Unit = {}, onStatistics: (() -> Unit)? = null,
    onExport: () -> Unit = {}, secretChat: SecretChatDetails? = null
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(forumTopicTitle ?: chat.title, color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (chat.isSecret && forumTopicTitle == null) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = stringResource(R.string.a11y_secret_chat_lock),
                                tint = palette.accent,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Text(
                        if (chat.isSecret) secretChatStatusLine(secretChat)
                        else chatPresenceLine(chat, info, lastSeenStyle, now).resolve(),
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                        text = { Text(stringResource(R.string.message_action_select)) },
                        onClick = { menuOpen = false; onSelectMessages() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_settings_title)) },
                        onClick = { menuOpen = false; onChatSettings() }
                    )
                    if (onStatistics != null) DropdownMenuItem(
                        text = { Text(stringResource(R.string.channel_statistics_title)) },
                        onClick = { menuOpen = false; onStatistics() }
                    )
                }
            }
        }
        HorizontalDivider(color = palette.divider)
    }
}

/**
 * The comments affordance is the bottom strip of a channel post's bubble, spanning the bubble's full width as in
 * Telegram; reactions stay below the bubble, so the strip never pushes the next post further away.
 */
@Composable
internal fun ChannelCommentsBar(count: Int, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = palette.textSecondary.copy(alpha = 0.18f))
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
                .padding(start = 12.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (count > 0) LocalContext.current.resources.getQuantityString(R.plurals.chat_comments_count, count, count)
                else stringResource(R.string.chat_comments_leave),
                color = palette.accentInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
        }
    }
}

/** One chat bubble: regular text/media, special cards, and the placeholder for expired self-destructing media. */
@Composable
internal fun MessageBubble(message: ChatMessage, observeFile: (Int) -> StateFlow<MediaFileState>, onDownloadFile: (Int) -> Unit,
    isRead: Boolean = false, highlighted: Boolean = false, onOpen: () -> Unit, onActions: (() -> Unit)? = null,
    /** Chat bubbles start multi-select on long press; other callers keep their own long-press action. */
    longPressSelects: Boolean = false, onReplyClick: (MessageReply) -> Unit = {}, onPlayed: () -> Unit = {}, onRetry: () -> Unit = {}, onReply: ((Long) -> Unit)? = null, onReaction: (Long, ReactionOption) -> Unit, observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>, inlineContent: @Composable () -> Unit = {},
    onSenderClick: (() -> Unit)? = null,
    /** Callers pass the chat/grouping decision; the sender name itself stays available for replies, search and profiles. */
    showSenderHeader: Boolean = true,
    onBubblePositioned: ((Rect) -> Unit)? = null,
    /** Reports the window bounds of a bubble that carries media, so the viewer can open from it. */
    onMediaBounds: ((Rect) -> Unit)? = null,
    translation: TranslationUiState? = null,
    onTranslationRetry: (() -> Unit)? = null,
    onTranslationCancel: (() -> Unit)? = null,
    onTranslationDismiss: (() -> Unit)? = null,
    transcriptionRequestPending: Boolean = false,
    transcriptionRequestError: SpeechRecognitionFailure? = null,
    transcriptionRated: Boolean = false,
    onTranscribe: (() -> Unit)? = null,
    onRateTranscription: ((Boolean) -> Unit)? = null,
    speechRecognitionUi: SpeechRecognitionUi = SpeechRecognitionUi(),
    onAutoDownloadFile: ((AutoDownloadRequest) -> Unit)? = null,
    onMusicPlay: ((PlaybackItem) -> Unit)? = null,
    commentsBar: (@Composable () -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onPollVote: ((Long, List<Int>) -> Unit)? = null,
    onPollStop: ((Long) -> Unit)? = null,
    onStopLiveLocation: ((ChatMessage) -> Unit)? = null,
    onWriteContact: ((Long) -> Unit)? = null,
    onLoadPollVoters: (suspend (messageId: Long, optionIndex: Int, offset: Int) -> Result<PollVotersPage>)? = null,
    onAddPollOption: (suspend (messageId: Long, text: String) -> Result<Unit>)? = null,
    /** Opens the set of a sticker message; when absent a sticker tap does not fall through to the media viewer. */
    onStickerOpen: ((MessageMedia) -> Unit)? = null,
    animateSpecial: Boolean = false,
    diceAnimationId: Long = 0L,
    onOpenMessageEntity: ((MessageEntityLink) -> Unit)? = null,
    paidContent: PaidContentActions? = null
) {
    val palette = beyGramPalette()
    val metrics = LocalBubbleMetrics.current
    val shape = bubbleShape(message.isOutgoing, metrics)
    val hasText = message.text.isNotBlank()
    val special = message.special
    // Expired media owns its card; unknown content still gets the explicit fallback, never an empty bubble.
    val renderedSpecial = resolvedSpecialContent(message)
    val showText = hasText && special !is MessageSpecialContent.Contact
    val circle = message.media?.kind == MediaKind.VideoNote
    // Стикер лежит без пузыря: ни цвета, ни ширины фото.
    val stickerMedia = message.media?.takeIf { it.kind == MediaKind.Sticker }
    val sticker = stickerMedia != null
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
    val openStickerSetLabel = if (sticker && onStickerOpen != null) stringResource(R.string.sticker_picker_open_set) else null
    val longPressLabel = stringResource(if (longPressSelects) R.string.message_action_select else R.string.a11y_message_actions)
    val selectionLabel = if (selectionMode) stringResource(if (selected) R.string.message_selection_deselect else R.string.message_selection_select) else null
    val selectionState = if (selectionMode) stringResource(if (selected) R.string.message_selection_selected else R.string.message_selection_not_selected) else null
    Column(Modifier.fillMaxWidth().then(if (circle) Modifier else Modifier.padding(horizontal = metrics.sideInset)), horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start) {
        SwipeToReply(enabled = onReply != null && message.sendState == MessageSendState.Sent, outgoing = message.isOutgoing,
            onReply = { onReply?.invoke(message.id) }) { swipe ->
            // Голосовое и кружок управляются своими элементами в пузыре и не открывают просмотрщик.
            Box(swipe) {
                BubbleLayout(
                    Modifier.widthIn(max = if (circle) androidx.compose.ui.unit.Dp.Infinity else 292.dp)
                        .then(if (message.media != null && !circle && !sticker)
                             Modifier.width(if (message.media.kind == MediaKind.Audio) 280.dp else 244.dp) else Modifier).clip(shape)
                        .background(if (circle || sticker) Color.Transparent else if (message.isOutgoing) palette.outgoingBubble else palette.incomingBubble)
                        .then(if ((selected || highlighted) && !circle) Modifier.border(2.dp, palette.accent, shape) else Modifier)
                        .then(if (onBubblePositioned != null) Modifier.onGloballyPositioned { onBubblePositioned(it.boundsInWindow()) } else Modifier)
                        .then(if (onMediaBounds != null && message.media != null) Modifier.onGloballyPositioned { onMediaBounds(it.boundsInWindow()) } else Modifier)
                        .semantics {
                            if (selectionMode) {
                                this.selected = selected
                                if (selectionState != null) stateDescription = selectionState
                            }
                        }
                        .combinedClickable(
                            onClickLabel = when {
                                selectionMode -> selectionLabel
                                sticker -> openStickerSetLabel
                                message.media != null && !message.media.kind.playsInline() -> openMediaLabel
                                else -> null
                            },
                            onLongClickLabel = if (selectionMode) selectionLabel else longPressLabel,
                            onClick = {
                                when {
                                    selectionMode -> onToggleSelection()
                                    sticker -> stickerMedia?.let { onStickerOpen?.invoke(it) }
                                    message.media != null && !message.media.kind.playsInline() -> onOpen()
                                }
                            },
                            onLongClick = if (selectionMode) onToggleSelection else onActions
                        ),
                    overlayMeta = overlayMeta,
                    // Only plain text may share its final line with time/status; complex content keeps the footer row.
                    inlineMeta = showText && message.media == null && renderedSpecial == null &&
                        message.reply == null && message.linkPreview == null && translation == null &&
                        message.expiredMediaKind == null && message.sendState != MessageSendState.Failed,
                    body = {
                    Column {
                        if (showSenderHeader && !message.isOutgoing && message.senderName.isNotBlank()) Text(message.senderName, color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                .then(if (onSenderClick != null) Modifier.clickable { onSenderClick?.invoke() } else Modifier))
                        // Media sits flush against the bubble's top and side edges; text keeps its own padding.
                        message.media?.let { media ->
                            if (media.kind == MediaKind.VideoNote) VideoNoteBubble(media, observeFile, onDownloadFile,
                                onPlayed = onPlayed,
                                onLongPress = if (selectionMode) onToggleSelection else onActions,
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
                                onRateTranscription = onRateTranscription,
                                speechRecognitionUi = speechRecognitionUi,
                                autoDownloadFile = onAutoDownloadFile)
                            else MediaPreview(media, observeFile, onDownloadFile,
                                onOpen = if (selectionMode) onToggleSelection else onOpen,
                                onLongPress = if (selectionMode) onToggleSelection else onActions,
                                onPlayed = onPlayed, origin = playbackOrigin,
                                transcriptionRequestPending = transcriptionRequestPending,
                                transcriptionRequestError = transcriptionRequestError,
                                transcriptionRated = transcriptionRated,
                                onTranscribe = onTranscribe,
                                onRateTranscription = onRateTranscription,
                                speechRecognitionUi = speechRecognitionUi,
                                viewOnce = message.isViewOnce,
                                autoDownloadFile = onAutoDownloadFile,
                                onMusicPlay = onMusicPlay,
                                onStickerOpen = onStickerOpen?.let { callback ->
                                    stickerMedia?.let { stickerMedia ->
                                        { if (selectionMode) onToggleSelection() else callback(stickerMedia) }
                                    }
                                })
                        }
                        message.expiredMediaKind?.let { kind -> ExpiredMediaCard(kind) }
                        // Без текста и ответа под фото нет строки: время уже стоит поверх него.
                        if (showText || renderedSpecial != null || message.reply != null || !overlayMeta) Column(Modifier.padding(start = metrics.textInset, end = metrics.textInset, top = if (circle) metrics.circleTopInset else metrics.textTopInset)) {
                            message.reply?.let { reply ->
                                Column(Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(metrics.replyRadius))
                                    .border(1.dp, palette.accent, RoundedCornerShape(metrics.replyRadius))
                                    .clickable { if (selectionMode) onToggleSelection() else onReplyClick(reply) }.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(reply.senderName.ifBlank { stringResource(R.string.chat_reply_to_message) }, color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(reply.text.resolve(), color = palette.textSecondary, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            renderedSpecial?.let { content ->
                                SpecialMessageContentView(
                                    special = content,
                                    message = message,
                                    onPollVote = onPollVote,
                                    onPollStop = onPollStop,
                                    onStopLiveLocation = onStopLiveLocation,
                                    onWriteContact = onWriteContact,
                                    onLoadPollVoters = onLoadPollVoters,
                                    onAddPollOption = onAddPollOption,
                                    observeFile = observeFile,
                                    downloadFile = onDownloadFile,
                                    autoDownloadFile = onAutoDownloadFile,
                                    animateDice = animateSpecial,
                                    diceAnimationId = diceAnimationId,
                                    paidContent = paidContent
                                )
                            }
                            if (showText) FormattedMessageText(message, observeCustomEmoji, observeFile, onDownloadFile, onOpenEntity = onOpenMessageEntity, entityClicksEnabled = !selectionMode)
                            message.linkPreview?.let { preview -> MessageLinkPreviewCard(preview, observeFile, onDownloadFile, autoDownloadFile = onAutoDownloadFile) }
                            translation?.let { state ->
                                TranslationBlock(
                                    state = state,
                                    onRetry = { onTranslationRetry?.invoke() },
                                    onCancel = { onTranslationCancel?.invoke() },
                                    onDismiss = { onTranslationDismiss?.invoke() },
                                    messageId = message.id,
                                    observeCustomEmoji = observeCustomEmoji,
                                    observeFile = observeFile,
                                    downloadFile = onDownloadFile
                                )
                            }
                        }
                    }
                },
                meta = {
                    if (!inCircle) BubbleMeta(message, isRead, retryAvailable = message.isOutgoing && message.sendState == MessageSendState.Failed,
                        overlay = overlayMeta, onRetry = onRetry)
                },
                footer = inlineContent,
                bottomBar = { if (!circle && !sticker) commentsBar?.invoke() }
            )
                // Media previews own tap gestures (play, open, video note). In selection mode a transparent overlay
                // makes the whole bubble selectable and keeps playback/download controls out of the way.
                if (selectionMode && message.media != null) Box(
                    Modifier.matchParentSize().combinedClickable(
                        onClickLabel = selectionLabel,
                        onLongClickLabel = selectionLabel,
                        onClick = onToggleSelection,
                        onLongClick = onToggleSelection
                    )
                )
                if (selectionMode) SelectionCheckBadge(selected, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        // Кружок и стикер лежат без пузыря: полосе комментариев нужна своя подложка того же цвета.
        if (circle || sticker) commentsBar?.let { bar ->
            Box(Modifier.padding(top = 2.dp).width(244.dp).clip(RoundedCornerShape(metrics.replyRadius))
                .background(if (message.isOutgoing) palette.outgoingBubble else palette.incomingBubble)) { bar() }
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
