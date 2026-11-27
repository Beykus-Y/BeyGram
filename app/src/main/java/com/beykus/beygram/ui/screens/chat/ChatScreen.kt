package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.design.ComposerVariant
import com.beykus.beygram.ui.design.signatureChrome
import com.beykus.beygram.ui.design.HairlineEdge
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import java.text.DateFormat
import java.util.Date
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.*
import com.beykus.beygram.ui.components.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.draw.shadow
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.SurfaceTreatment
import com.beykus.beygram.ui.design.TopBarVariant
import com.beykus.beygram.ui.design.GlassEdge
import com.beykus.beygram.ui.design.glassBackdropSource
import com.beykus.beygram.ui.design.rememberGlassBackdrop
import com.beykus.beygram.ui.design.recordsGlassBackdrop
import com.beykus.beygram.ui.design.usesGlassPanels
import com.beykus.beygram.ui.design.squareSendButton
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
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
import com.beykus.beygram.domain.search.nextChatSearchCursor
import com.beykus.beygram.data.media.VideoMessageSettingsStore
import com.beykus.beygram.data.media.ComposerRecordingMode
import com.beykus.beygram.data.presence.LastSeenStyle
import com.beykus.beygram.data.settings.AppLocale
import com.beykus.beygram.data.settings.ChatOverridesStore
import com.beykus.beygram.data.settings.ForumNavigationMode
import com.beykus.beygram.data.settings.TranslationSettingsStore
import com.beykus.beygram.data.settings.translationTargetLanguage
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackQueueSource
import com.beykus.beygram.data.music.PlaylistTrack
import com.beykus.beygram.ui.screens.music.AddToPlaylistSheet
import com.beykus.beygram.security.AppLockManager
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.LocalWindowLayout
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.motionEntrance
import com.beykus.beygram.ui.motion.fadeEnter
import com.beykus.beygram.ui.motion.fadeExit
import com.beykus.beygram.ui.motion.rememberMotionSpec
import com.beykus.beygram.ui.motion.rememberMotionInstant
import com.beykus.beygram.ui.motion.scrollToItemRespectingMotion
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.playback.NowPlayingBar
import com.beykus.beygram.ui.screens.playback.PlayerExpansion
import com.beykus.beygram.ui.screens.playback.receiptsAllowedOf
import com.beykus.beygram.ui.text.resolve
import com.beykus.beygram.ui.text.userMessage
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
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
    chat: ChatPreview, info: ProfileInfo?, lastSeenStyle: LastSeenStyle, history: ChatHistory, conversation: ConversationRepository,
    // Read only by the forward dialog, so chat list updates don't recompose the chat while it is closed.
    chats: () -> List<ChatPreview>,
    observeFile: (Int) -> StateFlow<MediaFileState>, onDownloadFile: (Int) -> Unit,
    /** Range-aware playback support from the repository; null keeps the previous whole-prefix behaviour. */
    playbackCoordinator: com.beykus.beygram.playback.UserRangeCoordinator? = null,
    onBack: () -> Unit, onChatInfo: () -> Unit, onStatistics: (() -> Unit)? = null, connectionState: TelegramConnectionState,
    recordingSettings: VideoMessageSettingsStore, translationSettings: TranslationSettingsStore,
    chatOverrides: ChatOverridesStore, focusMessageId: Long = 0L, onFocusHandled: () -> Unit = {},
    forumTopicId: Long = 0L, forumTopicTitle: String? = null, topicCanPost: Boolean = true, topicUnreadCount: Int = 0,
    topicUnreadMentionCount: Int = 0, topicUnreadReactionCount: Int = 0,
    /** The topic row the chat was opened from; its server icon and closed state identify the conversation. */
    forumTopic: ForumTopic? = null,
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
    onAutoDownloadFile: ((AutoDownloadRequest) -> Unit)? = null,
    /** Rows of [history] grouped off the main thread; a null or mismatched value makes the screen group them itself. */
    preparedTimeline: PreparedChatTimeline? = null,
    /** The forum's Tabs strip, drawn by the host under the header; null for ordinary chats and for the topic list. */
    topicStrip: (@Composable () -> Unit)? = null,
    /** The forum's view of its topics, offered in the header menu; null outside a topic, which hides that menu item. */
    forumNavigationMode: ForumNavigationMode? = null,
    onForumNavigationMode: ((ForumNavigationMode) -> Unit)? = null,
    /** The all-messages feed of this forum: [forumTopicId] is 0 and the history lists the messages of every topic. */
    forumAllMessages: Boolean = false,
    /** The forum's topics by id, for the topic labels of the feed; empty outside a forum. */
    forumTopicsById: Map<Long, ForumTopic> = emptyMap(),
    /** Opens a topic from its label in the feed; null leaves the labels unclickable. */
    onOpenForumTopic: ((ForumTopic) -> Unit)? = null,
    /** The shared-element transition to the profile (#999); null keeps the plain navigation. */
    profileTransition: com.beykus.beygram.ui.motion.ChatProfileHeaderSource? = null
) {
    // One persistent state source for text, rich formatting and the selected reply target.
    val composerDraftState = remember(conversation, chat.id, forumTopicId) {
        if (forumTopicId > 0L) conversation.observeForumTopicDraft(chat.id, forumTopicId) else conversation.observeMessageDraft(chat.id)
    }.collectAsStateWithLifecycle()
    val composerDraft by composerDraftState
    // Read where used, not here: a keystroke then invalidates only the composer scope, not the whole screen.
    val draft by remember(composerDraftState) { derivedStateOf { composerDraftState.value.text } }
    val draftEntities by remember(composerDraftState) { derivedStateOf { composerDraftState.value.entities } }
    val replyId by remember(composerDraftState) { derivedStateOf { composerDraftState.value.replyToMessageId } }
    DisposableEffect(conversation, chat.id, forumTopicId) {
        if (forumTopicId > 0L) conversation.forumTopicDraftOpened(chat.id, forumTopicId)
        onDispose { if (forumTopicId > 0L) conversation.forumTopicDraftClosed(chat.id, forumTopicId) }
    }
    fun updateDraft(transform: (com.beykus.beygram.domain.model.MessageDraft) -> com.beykus.beygram.domain.model.MessageDraft) =
        if (forumTopicId > 0L) conversation.updateForumTopicDraft(chat.id, forumTopicId, transform)
        else conversation.updateMessageDraft(chat.id, transform)
    val quoteState = remember(accountId, chat.id, forumTopicId) { ChatQuoteState() }
    fun setReplyTarget(messageId: Long?) {
        quoteState.keepOnlyFor(messageId)
        updateDraft { it.copy(replyToMessageId = messageId) }
    }
    fun clearReply() { setReplyTarget(null) }
    // The all-messages feed has no topic of its own. A reply goes into the topic of the message it answers, and any other message
    // goes to the General topic; the composer waits when that topic is hidden or cannot take messages.
    val feedGeneralTopic = remember(forumTopicsById) { forumTopicsById.values.firstOrNull { it.isGeneral } }
    val feedSendTarget: ForumFeedSendTarget? = if (forumAllMessages) {
        val feedGeneralTopicId = feedGeneralTopic?.id ?: 0L
        val replyTopicId = history.messages.firstOrNull { it.id == replyId }?.let { forumFeedTopicId(it, feedGeneralTopicId) } ?: 0L
        forumFeedSendTarget(replyId, replyTopicId, feedGeneralTopic)
    } else {
        null
    }
    // The topic a new message is sent to. Ordinary chats and the topic views keep their own topic id.
    val sendForumTopicId: Long = when (feedSendTarget) {
        is ForumFeedSendTarget.Reply -> feedSendTarget.replyTopicId
        is ForumFeedSendTarget.General -> feedSendTarget.topicId
        else -> forumTopicId
    }
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
    val messageActions = rememberChatMessageActions(accountId, chat.id, forumTopicId)
    val actionsId by messageActions::targetId
    var contextSceneMessageId by remember(accountId, chat.id, forumTopicId) { mutableStateOf<Long?>(null) }
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
    var deleting by messageActions::deleting
    var deleteEveryone by messageActions::deleteEveryone
    var deleteError by messageActions::deleteError
    var pinConfirm by messageActions::pinConfirm
    val capabilities by messageActions::capabilities
    val capabilitiesError by messageActions::capabilitiesError
    var capabilitiesAttempt by messageActions::capabilitiesAttempt
    var selectedMediaId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    // Layout of the newest media bubbles; the viewer turns it into a screen rectangle when it opens. A plain map: it must
    // not recompose on scroll.
    val mediaCoordinates = remember { BoundedLruMap<Long, LayoutCoordinates>(MEDIA_ORIGIN_LIMIT) }
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
    var frozenForwardSelection by rememberSaveable(accountId, chat.id) { mutableStateOf(longArrayOf()) }
    var selectionDeleteOpen by rememberSaveable(chat.id) { mutableStateOf(false) }
    var selectionDeleteRights by remember { mutableStateOf<BatchDeleteRights?>(null) }
    var selectionDeleteEveryone by remember { mutableStateOf(false) }
    var selectionDeleteBusy by remember { mutableStateOf(false) }
    var selectionDeleteError by remember { mutableStateOf<String?>(null) }
    var contextChatId by rememberSaveable(chat.id) { mutableStateOf(0L) }
    var contextMessageId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var highlighted by remember { mutableStateOf<Long?>(null) }
    // A message outside the loaded history is scrolled to once the window around it reaches the list.
    var pendingJumpId by remember(chat.id, forumTopicId) { mutableStateOf<Long?>(null) }
    // The message the error shows when the loaded window does not contain pendingJumpId.
    var pendingJumpFailure by remember(chat.id, forumTopicId) { mutableIntStateOf(R.string.chat_message_unavailable) }
    var jumpGeneration by remember(chat.id, forumTopicId) { mutableIntStateOf(0) }
    var jumpScrollJob by remember(chat.id, forumTopicId) { mutableStateOf<Job?>(null) }
    var jumpLoadJob by remember(chat.id, forumTopicId) { mutableStateOf<Job?>(null) }
    fun cancelActiveJump() {
        jumpGeneration++
        pendingJumpId = null
        jumpScrollJob?.cancel()
        jumpScrollJob = null
        jumpLoadJob?.cancel()
        jumpLoadJob = null
        conversation.cancelMessageJump(chat.id, forumTopicId)
    }
    var searching by rememberSaveable(chat.id, forumTopicId) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(chat.id, forumTopicId) { mutableStateOf("") }
    var searchFilter by rememberSaveable(chat.id, forumTopicId, stateSaver = ChatSearchFilterSaver) { mutableStateOf(ChatSearchFilter()) }
    var searchResults by remember(chat.id, forumTopicId) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var searchNextId by remember(chat.id, forumTopicId) { mutableLongStateOf(0L) }
    var searchTotalCount by remember(chat.id, forumTopicId) { mutableIntStateOf(0) }
    var searchGeneration by remember(chat.id, forumTopicId) { mutableIntStateOf(0) }
    var searchBusy by remember(chat.id, forumTopicId) { mutableStateOf(false) }
    var searchError by remember(chat.id, forumTopicId) { mutableStateOf<String?>(null) }
    var searchRefreshing by remember(chat.id, forumTopicId) { mutableStateOf(false) }
    var searchRetry by remember(chat.id, forumTopicId) { mutableIntStateOf(0) }
    var searchMatchSession by remember(chat.id, forumTopicId) { mutableStateOf(ChatSearchMatchSession()) }
    var searchMatchGeneration by remember(chat.id, forumTopicId) { mutableIntStateOf(0) }
    fun closeSearch(clearMatchSession: Boolean = true) {
        searching = false; searchQuery = ""; searchFilter = ChatSearchFilter()
        searchResults = emptyList(); searchNextId = 0L; searchTotalCount = 0; searchGeneration++
        searchBusy = false; searchError = null; searchRefreshing = false
        if (clearMatchSession) {
            searchMatchGeneration++
            searchMatchSession = ChatSearchMatchSession()
            cancelActiveJump()
        }
    }
    var sending by remember(accountId, chat.id, forumTopicId) { mutableStateOf(false) }
    val sendApplication = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val textSendGeneration by remember(accountId) { PendingTextSendSessions.generation(accountId) }.collectAsStateWithLifecycle()
    val textSendQueue = remember(sendApplication, accountId, textSendGeneration) {
        PendingTextSendSessions.queue(sendApplication, accountId)
    }
    // The feed has a fixed queue scope that does not depend on the General topic, so a queued or failed text stays visible and can be
    // retried or discarded while General appears or disappears. Each queued text names its own destination topic.
    val textScopeTopicId = if (forumAllMessages) FORUM_FEED_TEXT_SCOPE_TOPIC_ID else sendForumTopicId
    val textSendScopeKey = remember(accountId, chat.id, textScopeTopicId) { TextSendScopeKey(accountId, chat.id, textScopeTopicId) }
    val textQueueWriting by remember(textSendQueue, textSendScopeKey) {
        textSendQueue.composerWriting(textSendScopeKey)
    }.collectAsStateWithLifecycle(initialValue = false)
    val queuedTextAttempts by remember(textSendQueue, textSendScopeKey) {
        textSendQueue.observe(textSendScopeKey)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    var failedSendTarget by remember(accountId, chat.id, forumTopicId) { mutableStateOf<FailedSendTarget?>(null) }
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
    // In the feed a reply clears right after it is sent; the composer stays until that send has finished.
    val feedSendInFlight = feedSendTarget == ForumFeedSendTarget.Unavailable && (sending || textQueueWriting)
    val canPostHere = SecretChatPosting.canPost(
        chat.canPost && topicCanPost && (feedSendTarget != ForumFeedSendTarget.Unavailable || feedSendInFlight),
        chat.isSecret,
        secretChat?.state
    )
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
    val queueStorageFailed by textSendQueue.storageFailed.collectAsStateWithLifecycle()
    val queueStorageCorrupt by textSendQueue.storageCorrupt.collectAsStateWithLifecycle()
    val queueStorageFailureText = stringResource(R.string.chat_send_queue_storage_failed)
    LaunchedEffect(textSendQueue, chat.id, queueStorageFailed, queueStorageCorrupt) {
        if (queueStorageFailed && !queueStorageCorrupt) error = queueStorageFailureText
        else if (!queueStorageFailed && error == queueStorageFailureText) error = null
    }
    var copyLinks by remember(chat.id) { mutableStateOf<List<String>>(emptyList()) }
    // The audio message whose More menu asked to save it to a playlist. Clearing it closes the sheet.
    var playlistTrack by remember(accountId) { mutableStateOf<PlaylistTrack?>(null) }
    var botCommandMenu by remember(chat.id) { mutableStateOf<BotCommandMenu?>(null) }
    var botCommandMenuOpen by rememberSaveable(chat.id) { mutableStateOf(false) }
    var inlineSending by remember(chat.id) { mutableStateOf(false) }
    var webAppSession by remember(chat.id) { mutableStateOf<BotWebAppLaunch?>(null) }
    var pendingWebApp by remember(chat.id) { mutableStateOf<PendingBotWebApp?>(null) }
    var webAppOpening by remember(chat.id) { mutableStateOf(false) }
    val reactionOptions by messageActions::reactionOptions
    var reactionPickerOpen by messageActions::reactionPickerOpen
    val paidReactions = rememberPaidReactions(conversation)
    // Reactions, poll votes and bot buttons ask first while hidden presence warns about risky actions; asked once per chat screen (see StealthRiskGuard).
    val riskGuard = rememberStealthRiskGuard(chat.id) { conversation.stealthWarnsBeforeSending() }
    val limits by conversation.messageLimits.collectAsStateWithLifecycle()
    val typingFlow = remember(conversation, chat.id) { conversation.observeTyping(chat.id) }
    val typingNames by typingFlow.collectAsStateWithLifecycle()
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    // Each forum topic keeps its own list position.
    val scrollPosition: ChatScrollPositionViewModel = viewModel(key = "chat-scroll-$accountId-${chat.id}-$forumTopicId")
    // A send started here follows the list to the bottom when its message arrives (see the newest-arrival effect).
    fun noteLocalSend() = scrollPosition.markLocalSend(System.currentTimeMillis())
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
    // "Play next" and "Add to end of queue" for an audio message, without stopping the current track.
    fun queueActionItem(message: ChatMessage, protected: Boolean): PlaybackItem? {
        if (protected) return null
        val fileId = message.media?.takeIf { it.kind == MediaKind.Audio }?.fileId ?: return null
        return chatMusicItem(message, accountId, observeFile(fileId).value, context.getString(R.string.media_kind_audio))
    }
    fun enqueueMusic(item: PlaybackItem, atEnd: Boolean) {
        PlaybackEngine.enqueue(
            context, item, PlaybackQueueSource(accountId, observeFile, onDownloadFile, onAutoDownloadFile), atEnd
        )
        Toast.makeText(
            context,
            context.getString(if (atEnd) R.string.queue_added_end else R.string.queue_added_next),
            Toast.LENGTH_SHORT
        ).show()
    }
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
    val pendingRecordingSends = remember(chat.id) { ArrayDeque<Pair<VoiceRecordingDestination, List<OutgoingMedia>>>() }
    val videoExports by VideoNoteExportCenter.results.collectAsStateWithLifecycle()
    // The feed claims the notes of every topic of its chat; a topic view claims only the notes recorded in its own topic.
    val videoClaimTopicId: Long? = if (forumAllMessages) null else sendForumTopicId
    val videoPreview = videoExports.firstOrNull { it.claimedBy(chat.id, accountId, videoClaimTopicId) && it.requiresPreview }
    val videoPending by VideoNoteExportCenter.pendingNotes.collectAsStateWithLifecycle()
    // Автоматический кружок виден в ленте сразу: сначала обработка, потом отправка, пока TDLib не примет сообщение.
    val pendingVideoNotes = remember(videoPending, videoExports, chat.id, videoClaimTopicId, accountId) { pendingVideoNotesFor(chat.id, videoPending, videoExports, videoClaimTopicId, accountId) }
    fun startRecordingSendWorker() {
        if (recordingQueueSending) return
        if (pendingRecordingSends.isEmpty()) return
        recordingQueueSending = true
        // Worker живёт в scope восстановления записи: переживает recomposition и не бросает очередь на полпути.
        recordingRecovery.processingScope.launch {
            try {
                while (pendingRecordingSends.isNotEmpty()) {
                    val (destination, mediaList) = pendingRecordingSends.removeFirst()
                    mediaList.forEachIndexed { index, media ->
                        if (media.path in sentRecordingPaths) return@forEachIndexed
                        recordingQueueProgress = index + 1
                        val result = conversation.sendMedia(chat.id, media, "", destination.replyId, forumTopicId = destination.topicId)
                        if (result.isSuccess) {
                            sentRecordingPaths = sentRecordingPaths + media.path
                            // Clear only the reply this take was sent with; a reply set after the take was queued stays.
                            if (replyId == destination.replyId) clearReply()
                        } else {
                            recordingRecovery.showPreview(mediaList, destination, index)
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
    fun sendRecordedMedia(mediaList: List<OutgoingMedia>, destination: VoiceRecordingDestination) {
        noteLocalSend()
        pendingRecordingSends.addLast(destination to mediaList)
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
        riskGuard.run {
            scope.launch {
                conversation.toggleReaction(chat.id, messageId, reaction, addToRecent).onFailure { error = it.userMessage(context, R.string.chat_reaction_failed) }
            }
        }
    }
    // Chip taps leave the user's recent reactions alone; only a reaction picked from the extended menu is added there.
    fun react(messageId: Long, reaction: ReactionOption) = setReaction(messageId, reaction, addToRecent = false)
    fun capturedRecording(media: OutgoingMedia, previewFirst: Boolean) {
        val destination = VoiceRecordingDestination(sendForumTopicId, replyId)
        if (previewFirst) recordingRecovery.showPreview(listOf(media), destination)
        else sendRecordedMedia(listOf(media), destination)
    }
    val mediaLaunchController = remember(accountId, chat.id, forumTopicId) { ComposerMediaLaunchController() }
    val mediaLaunchMotion = rememberMotionSpec(MotionToken.MediaExpand)
    LaunchedEffect(mediaLaunchController.active) {
        val launches = mediaLaunchController.active
        if (launches.isEmpty()) return@LaunchedEffect
        val oldest = launches.minOf { it.capture.capturedAt }
        delay((2_500L - (android.os.SystemClock.elapsedRealtime() - oldest)).coerceAtLeast(0L))
        launches.forEach(mediaLaunchController::finish)
    }
    val attachments = rememberAttachmentState(
        chatId = chat.id,
        mediaLaunchController = mediaLaunchController,
        sendMedia = { snapshot, media, caption, entities ->
            noteLocalSend()
            val sendOptions = snapshot.options
            val stealthScheduledBefore = stealthWillSchedule(conversation, sendOptions)
            // Only the real, visible outgoing photo can receive the spatial effect. Never match by caption/file name.
            val capture = if (design.navigationFloating && messageAnimations.enabled && !mediaLaunchMotion.isInstant &&
                !chat.isSecret && !chat.hasProtectedContent && !sendOptions.viewOnce &&
                sendOptions.scheduledAtSeconds == null && !stealthScheduledBefore) {
                mediaLaunchController.capture(media)
            } else null
            val result = conversation.sendComposerMedia(snapshot.chatId, media, caption, snapshot.replyToMessageId, entities, snapshot.forumTopicId, sendOptions)
            if (result.isSuccess) {
                val ids = result.getOrNull().orEmpty()
                if (capture != null && ids.isNotEmpty() && ids.none { mediaCoordinates[it] != null }) {
                    mediaLaunchController.accept(capture, ids)
                }
                if (stealthScheduledBefore) showStealthScheduledNotice(context)
            }
            result.map { Unit }
        },
        sendSticker = { sticker, options ->
            noteLocalSend()
            val stealthScheduledBefore = stealthWillSchedule(conversation, options)
            val result = conversation.sendSticker(chat.id, sticker, replyId, forumTopicId = sendForumTopicId, options = options)
            if (result.isSuccess) { clearReply(); if (stealthScheduledBefore) showStealthScheduledNotice(context) }
            result
        },
        sendExtra = { draft, options ->
            noteLocalSend()
            val stealthScheduledBefore = stealthWillSchedule(conversation, options)
            val result = when (draft) {
                is MessageExtraDraft.Poll -> conversation.sendPoll(chat.id, draft.poll, replyId, forumTopicId = sendForumTopicId, options = options)
                is MessageExtraDraft.Location -> conversation.sendLocation(chat.id, draft.point, false, replyId, forumTopicId = sendForumTopicId, options = options)
                is MessageExtraDraft.Contact -> conversation.sendContactCard(chat.id, draft.userId, replyId, forumTopicId = sendForumTopicId, options = options)
                is MessageExtraDraft.Dice -> conversation.sendDice(chat.id, draft.emoji, replyId, forumTopicId = sendForumTopicId, options = options)
                is MessageExtraDraft.MusicFile, is MessageExtraDraft.MusicBotResult -> conversation.sendMusicDraft(chat.id, draft, replyId, sendForumTopicId, options)
            }
            if (result.isSuccess) { clearReply(); if (stealthScheduledBefore) showStealthScheduledNotice(context) }
            result
        }
    )
    fun retryLocalTextAttempt(attemptId: Long, sendNowConsent: Boolean = false) {
        textSendQueue.launchMutation {
            try {
                val baseline = if (forumTopicId > 0L) {
                    conversation.observeForumTopicHistory(chat.id, forumTopicId).value.liveArrivalSequence
                } else {
                    conversation.observeChatHistory(chat.id).value.liveArrivalSequence
                }
                textSendQueue.retry(
                    scopeKey = textSendScopeKey,
                    attemptId = attemptId,
                    sendNowConsent = sendNowConsent,
                    baselineSequence = baseline,
                    execute = { attempt -> executeTextSendAttempt(conversation, attempt) },
                    onFailure = { attempt, failure ->
                        attachments.stealthFallback.offer(failure) {
                            retryLocalTextAttempt(attempt.attemptId, sendNowConsent = true)
                        }
                    },
                    formatError = { it.userMessage(context, R.string.err_chat_send_failed) }
                )
            } catch (_: TextSendStorageException) {
                error = queueStorageFailureText
            }
        }
    }
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
    val videoNote = rememberVideoNoteRecorder(recordingSettings, recordingRecovery.processingScope, chat.id, sendForumTopicId, accountId = accountId, replyToMessageId = replyId, onError = { error = it })
    // Video delivery, including TDLib send retries, belongs to the app-wide center.
    // Only automatic notes are claimed here; preview and failed notes remain visible.
    // A note goes to the topic it was recorded in, so another topic never claims it; the feed claims the notes of every topic.
    LaunchedEffect(accountId, chat.id, sendForumTopicId, videoClaimTopicId, forumAllMessages, conversation) {
        launch {
            VideoNoteExportCenter.results.collect { list ->
                list.filter { it.claimedBy(chat.id, accountId, videoClaimTopicId) && !it.requiresPreview && !it.sending }.forEach { item ->
                    noteLocalSend()
                    val started = VideoNoteExportCenter.send(item.chatId, item.sessionName, accountId) { media, targetReply ->
                        conversation.sendMedia(item.chatId, media, "", targetReply, forumTopicId = item.topicId).map { Unit }
                    }
                    // Clear the composer's reply only when this note used it; a reply set after the note was recorded stays.
                    if (started == VideoNoteSendStart.Started && item.replyToMessageId != null && item.replyToMessageId == replyId) clearReply()
                }
            }
        }
        launch {
            VideoNoteExportCenter.failures.collect {
                val failed = if (forumAllMessages) VideoNoteExportCenter.consumeFailureForChat(chat.id, accountId)
                else VideoNoteExportCenter.consumeFailure(chat.id, sendForumTopicId, accountId)
                // The reason matters: "не удалось сохранить" alone cannot distinguish a broken encoder from a full disk.
                if (failed != null) error = failed.message.resolve(context)
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
    // Recording takes the microphone, so music pauses for it and continues after the message is sent or cancelled.
    val recordingInterruptionOwner = remember { Any() }
    val recordingActive = videoNote.active || audioNote.active
    LaunchedEffect(recordingActive) {
        if (recordingActive) {
            PlaybackEngine.beginRecordingFocus(context)
        } else {
            PlaybackEngine.endRecordingFocus(context)
            PlaybackEngine.endInterruption(recordingInterruptionOwner)
        }
    }
    DisposableEffect(recordingInterruptionOwner) {
        onDispose {
            PlaybackEngine.endRecordingFocus(context)
            PlaybackEngine.endInterruption(recordingInterruptionOwner)
        }
    }
    fun startRecording() {
        if (videoNote.active || audioNote.active) return
        // Микрофон записывает и звук из общего плеера, поэтому музыку ставим на паузу, когда запись начинается.
        if (recordingMode == ComposerRecordingMode.Voice) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                PlaybackEngine.beginInterruption(recordingInterruptionOwner)
                audioNote.press()
            } else {
                AppLockManager.get(context).expectExternalActivity()
                audioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        } else if (!VIDEO_NOTE_RECORDING_ENABLED) {
            error = context.getString(R.string.recorder_video_note_unavailable)
        } else if (videoNoteAllowed(context)) {
            PlaybackEngine.beginInterruption(recordingInterruptionOwner)
            videoNote.press()
        } else {
            AppLockManager.get(context).expectExternalActivity()
            videoPermissions.launch(videoNotePermissions)
        }
    }
    val messages = history.messages
    val selectedMessages = remember(messages, selectionIds) { if (selectionIds.isEmpty()) emptyList() else messages.filter { it.id in selectionIds } }
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
    // Sender picker is built from the loaded history, only while the search bar is open; no extra TDLib member request is needed in the search bar.
    val searchSenders = remember(messages, searching) { if (searching) chatSearchSenders(messages) else emptyList() }
    // Album grouping reads only the messages, so a pending video note does not regroup the whole loaded history.
    val waveChat = isWaveChat(chat)
    val timeline = rememberChatTimelineContent(
        accountId = accountId,
        chat = chat,
        forumTopicId = forumTopicId,
        history = history,
        preparedTimeline = preparedTimeline,
        waveFiltered = waveChat,
        pendingVideoNotes = pendingVideoNotes,
        queuedTextAttempts = queuedTextAttempts,
        readBoundaryId = scrollPosition.readBoundaryId,
        forumAllMessages = forumAllMessages,
        generalTopicId = feedGeneralTopic?.id ?: 0L
    )
    val messageListItems = timeline.items
    val rawMessageItems = timeline.itemsWithoutUnreadSeparator
    val daySeparators = timeline.daySeparators
    val feedTopicHeaders = timeline.feedTopicHeaders
    val senderHeaderByKey = timeline.senderHeaderByKey
    val localTextAttemptIds = timeline.localTextAttemptIds
    // The position is saved by message as well as by index: history changes move indexes, not the message on screen.
    val currentListItems by rememberUpdatedState(messageListItems)
    val bottomThresholdPx = with(LocalDensity.current) { 48.dp.roundToPx() }
    DisposableEffect(listState) {
        onDispose {
            chatScrollAnchorOf(currentListItems, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, bottomThresholdPx, System.currentTimeMillis())
                ?.let { scrollPosition.anchor = it }
            scrollPosition.anchorRestored = false
        }
    }
    // Remembers which row sat at the bottom, so the next arrival can tell a reader at the bottom from one above it.
    LaunchedEffect(listState) {
        snapshotFlow {
            val atBottom = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= bottomThresholdPx
            if (atBottom) listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String else null
        }.collect { key -> if (key != null) scrollPosition.previousFirstKey = key }
    }
    val messageItemsByKey = remember(messageListItems) { messageListItems.associateBy { it.key } }
    val currentMessageItemsByKey by rememberUpdatedState(messageItemsByKey)
    val currentMessages by rememberUpdatedState(messages)
    // Live insertions are claimed before the lazy list composes its rows, so only genuinely new messages animate once.
    val animationTracker = remember(chat.id, forumTopicId) { MessageArrivalTracker() }
    // Decorative effects are capped per chat, so a burst of arrivals cannot stack several Lottie overlays.
    val effectLimiter = remember(chat.id, forumTopicId) { MessageEffectPlaybackLimiter() }
    val composerLaunch = remember(accountId, chat.id, forumTopicId) { ComposerLaunchState() }
    remember(history, composerLaunch) {
        animationTracker.observe(history)
        composerLaunch.claimCompletedArrivals(history, animationTracker)
    }
    val composerCursor = remember(chat.id) { ComposerCursor() }
    val hidingComposerForLaunch by remember(composerLaunch, composerDraftState) {
        derivedStateOf { composerLaunch.hidesDraft(editing = editId != null, draft = draft) }
    }
    // Assign targets before the lazy rows compose. The overlay follows the same history snapshot.
    val launchTargets = composerLaunch.targets(history, localTextAttemptIds.keys)
    ComposerLaunchHistoryEffect(composerLaunch, history, queuedTextAttempts)
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
    // Bubbles store their layout on every placement; rectangles are computed only when a menu, viewer or launch reads them.
    val bubbleCoordinates = remember(chat.id) { HashMap<Long, LayoutCoordinates>() }
    // Screen rectangle of a media bubble or album. A detached layout cannot be measured, so it reads as missing.
    fun mediaOrigin(id: Long): Rect? = mediaCoordinates[id]?.mediaScreenBounds()
    // Window rectangle of a single bubble. Album members have no bubble entry: they use the album's screen rectangle,
    // as their menu anchor always has.
    fun bubbleRect(id: Long): Rect? = bubbleCoordinates[id]?.takeIf { it.isAttached }?.boundsInWindow() ?: mediaOrigin(id)
    var actionAnchorBounds by remember(chat.id) { mutableStateOf<Rect?>(null) }
    val actionMessage = actionsId?.let { id -> messages.firstOrNull { it.id == id } }
    // An album's reactions sit on one of its messages, so the picker and the chips act on that message.
    val reactionOwner = actionMessage?.let { target -> messageListItems.firstOrNull { item -> item.messages.any { it.id == target.id } }?.messages?.let(::reactionMessage) }
    val editMessage = editId?.let { id -> messages.firstOrNull { it.id == id } }
    // A swipe reply needs an idle composer on screen: no search, deletion, edit, recording or open action menu, and no
    // send in flight, because a finished send clears the reply target it used.
    val swipeReplyAllowed = (canPostHere || feedSendTarget == ForumFeedSendTarget.Unavailable && chat.canPost) && !searching && !deleting && !selectionMode && editMessage == null && actionMessage == null && !videoNote.active && !audioNote.active &&
        !sending && !attachments.sending && !recordingQueueSending && pendingVideoNotes.isEmpty()
    LaunchedEffect(editId) { editEntities = editMessage?.entities.orEmpty() }
    LaunchedEffect(botKeyboard?.messageId) { if (botKeyboard?.forceReply == true) setReplyTarget(botKeyboard?.messageId) }
    val editLimit = if (editMessage?.media == null) limits.text else limits.caption
    fun closeActions() {
        messageActions.close()
        actionAnchorBounds = null
    }
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
        frozenForwardSelection = longArrayOf()
        selectionDeleteOpen = false
        selectionDeleteRights = null
        selectionDeleteBusy = false
        selectionDeleteError = null
    }
    // A long press opens the anchored contextual popover directly.
    // Multi-select remains a deliberate action within the menu.
    fun openActionsOnLongPress(messageId: Long) {
        if (selectionMode || actionsId == messageId) return
        // Remember the same anchor used by SelectionActionBar's single-message More action.
        // Entering Select from the action menu must not lose the original message context.
        selectionAnchorId = messageId
        actionAnchorBounds = bubbleRect(messageId)?.takeIf { !it.isEmpty }
        messageActions.open(messageId)
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
    ChatMessageActionEffects(messageActions, conversation, chat.id, actionMessage?.id, reactionOwner?.id) {
        it.userMessage(context, R.string.err_chat_actions_load_failed)
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
    // Фото и видео листаются по всему чату: окно грузится вокруг открытого вложения, а не только по загруженной истории.
    val mediaWindow = remember(conversation, chat.id) {
        SharedMediaWindow(
            load = { from, newer -> conversation.sharedMessages(chat.id, SharedMediaKind.Media, from, newer) },
            failure = { it.userMessage(context, R.string.err_chat_history_load_failed) }
        )
    }
    LaunchedEffect(selectedMediaId) {
        val id = selectedMediaId ?: return@LaunchedEffect
        val selectedMessage = currentMessages.firstOrNull { it.id == id }
        val kind = selectedMessage?.media?.kind
        // Вложение, которое уже в окне, не перезагружаем: так просмотрщик возвращается к нему на том же месте.
        if (kind != null && kind in viewerSwipeKinds && mediaWindow.messages.none { it.id == id }) {
            mediaWindow.restart(id, anchorMessage = selectedMessage.takeUnless { it.isViewOnce })
            mediaWindow.loadOlder()
        }
        // Новее догружаем от края: открытое вложение обычно стоит у правого края окна, а прерванная загрузка не оставляет дыр.
        mediaWindow.loadNear(id) { !it.isViewOnce }
    }
    var previousHistoryIds by remember(conversation, chat.id) { mutableStateOf<Set<Long>?>(null) }
    LaunchedEffect(messages, selectedMediaId) {
        val currentIds = messages.mapTo(HashSet(messages.size)) { it.id }
        val prevIds = previousHistoryIds
        if (prevIds != null) {
            val removed = prevIds - currentIds
            if (removed.isNotEmpty()) mediaWindow.removeDeleted(removed)
            if (selectedMediaId != null && mediaWindow.loaded) {
                val windowMax = mediaWindow.messages.maxOfOrNull { it.id } ?: 0L
                val hasNewerMedia = messages.any { m ->
                    m.id !in prevIds && m.id > windowMax && !m.isViewOnce && m.media?.kind in viewerSwipeKinds
                }
                if (hasNewerMedia) mediaWindow.refreshNewer()
            }
        }
        previousHistoryIds = currentIds
    }
    LaunchedEffect(selectedMediaId) {
        // Счётчик при свайпе держит прежнее значение до ответа, чтобы не мигал. Права так не держим: см. MediaViewerPolicy.rightsFor.
        if (selectedMediaId == null) { viewerRights = null; viewerPosition = null }
        val id = selectedMediaId ?: return@LaunchedEffect
        viewerRights = conversation.messageCapabilities(chat.id, id).getOrNull()?.let { MessageRights(id, it) }
        val kind = (currentMessages.firstOrNull { it.id == id } ?: mediaWindow.messages.firstOrNull { it.id == id })?.media?.kind
        viewerPosition = kind?.let { conversation.mediaPosition(chat.id, id, it).getOrNull() }
    }
    BackHandler(enabled = !threadOpen && (stickerPanelOpen || searching || searchMatchSession.isActive || actionMessage != null || editMessage != null || attachments.menuOpen || attachments.active || selectionMode)) {
        when {
            selectionDeleteOpen -> { selectionDeleteOpen = false; selectionDeleteError = null }
            selectionForwardOpen -> { if (!forwardBusy) { selectionForwardOpen = false; frozenForwardSelection = longArrayOf(); forwardError = null; forwardAsCopy = false } }
            selectionMode -> exitSelection()
            searching || searchMatchSession.isActive -> closeSearch()
            deleting -> { deleting = false; deleteError = null }
            actionMessage != null -> closeActions()
            editMessage != null -> closeEdit()
            stickerPanelOpen -> stickerPanelOpen = false
            else -> attachments.discardAll()
        }
    }
    BackHandler(enabled = openStickerSetId != null) { openStickerSetId = null }
    BackHandler(enabled = !threadOpen && !selectionMode && actionMessage == null && editMessage == null && !attachments.menuOpen && !attachments.active && !searching && !searchMatchSession.isActive && openStickerSetId == null && !stickerPanelOpen, onBack = onBack)
    BackHandler(enabled = !threadOpen && (videoNote.active || audioNote.active)) {
        videoNote.stopForRecovery()
        audioNote.stopForRecovery()
    }

    val chatGlass = design.usesGlassPanels
    val floatingComposer = design.navigationFloating
    val chatBackdrop = if (chatGlass && design.recordsGlassBackdrop) rememberGlassBackdrop() else null
    var topChromePx by remember { mutableIntStateOf(0) }
    var bottomChromePx by remember { mutableIntStateOf(0) }
    // With glass chrome the list also runs under the header and composer; those strips do not count as seen.
    val coveredByChrome by rememberUpdatedState((if (chatBackdrop != null) topChromePx else 0) to (if (chatBackdrop != null || floatingComposer) bottomChromePx else 0))
    // Loading/caching messages never triggers receipts; only the focused, resumed viewport does, and not while the full player covers it (the player takes no focus).
    LaunchedEffect(chat.id, lifecycle, conversation, threadOpen) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow {
                if (!window.isWindowFocused || searching || threadOpen || attachments.cameraOpen || attachments.editor != null || !scrollPosition.readBoundaryChecked || !receiptsAllowedOf(PlayerExpansion.phase)) emptyList() else {
                    val layout = listState.layoutInfo
                    val (coveredTop, coveredBottom) = coveredByChrome
                    val ids = ArrayList<Long>()
                    for (item in layout.visibleItemsInfo) {
                        // Reverse list: the main axis starts at the composer, so the bottom strip is the start side.
                        val visible = minOf(item.offset + item.size, layout.viewportEndOffset - coveredTop) -
                            maxOf(item.offset, layout.viewportStartOffset + coveredBottom)
                        if (visible < minOf(item.size / 2, minimumVisible).coerceAtLeast(1)) continue
                        val messages = currentMessageItemsByKey[item.key as? String]?.messages ?: continue
                        for (message in messages) if (message.sendState == MessageSendState.Sent) ids += message.id
                    }
                    ids.sort()
                    // distinct() on a sorted list: drop neighbours that repeat.
                    if (ids.size > 1) {
                        var w = 1
                        for (r in 1 until ids.size) if (ids[r] != ids[w - 1]) ids[w++] = ids[r]
                        while (ids.size > w) ids.removeAt(ids.size - 1)
                    }
                    ids
                }
            }.debounce(350).distinctUntilChanged().collect { ids ->
                // The debounce can still hold a list computed before the player opened, so the player is checked again at send time.
                if (ids.isNotEmpty() && receiptsAllowedOf(PlayerExpansion.phase)) conversation.messagesVisible(chat.id, ids)
            }
        }
    }
    ChatOlderHistoryEffect(conversation, chat.id, forumTopicId, listState, scrollPosition, history, messageListItems.size)
    val listScrollInstant = rememberMotionInstant(MotionToken.ListReorder)
    ChatTimelineOpeningEffects(
        listState = listState,
        scrollPosition = scrollPosition,
        items = messageListItems,
        history = history,
        newest = newest,
        unreadCount = if (forumTopicId > 0L) topicUnreadCount else chat.unreadCount,
        focusMessageId = focusMessageId,
        hasPendingJump = { pendingJumpId != null },
        pendingRecording = pendingVideoNotes.firstOrNull()?.sessionName,
        bottomThresholdPx = bottomThresholdPx,
        listScrollInstant = listScrollInstant
    )
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
    // Targets land above the bottom edge, where the jump badges and the composer do not cover them.
    fun targetScrollOffset(): Int = -(listState.layoutInfo.viewportSize.height * UNREAD_SEPARATOR_VIEWPORT_FRACTION).roundToInt()
    // Read marks stay held while a focused message from outside the chat is being opened; its jump decides the position.
    fun settleFocusedOpening() { if (scrollPosition.focusClaimed) scrollPosition.readBoundaryChecked = true }
    LaunchedEffect(pendingJumpId, messageListItems) {
        val target = pendingJumpId ?: return@LaunchedEffect
        val index = messageListItems.indexOfFirst { item -> item.messages.any { it.id == target } }
        if (index < 0) {
            // The window is in place but has no target (for example, a deleted message): release the opening instead of holding read marks.
            if (!history.isLoading) {
                // The window was replaced around the target, so say why nothing is shown instead of leaving an unexplained position.
                error = context.getString(pendingJumpFailure)
                settleFocusedOpening(); pendingJumpId = null
            }
            return@LaunchedEffect
        }
        // A scroll cancelled by the user's touch still settles the opening, or read marks would stay held for the visit.
        try {
            listState.scrollToItemRespectingMotion(index, scrollOffset = targetScrollOffset(), motionInstant = listScrollInstant)
            highlighted = target
        } finally {
            settleFocusedOpening()
        }
        // Cleared last: changing the key earlier would cancel the scroll that is still running.
        pendingJumpId = null
    }
    LaunchedEffect(highlighted) { if (highlighted != null) { delay(2200); highlighted = null } }
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
                        setReplyTarget(messageId)
                        replyFocusRequest++
                    } else {
                        error = context.getString(R.string.err_message_cannot_reply)
                    }
                }
                .onFailure { error = it.userMessage(context, R.string.err_chat_actions_load_failed) }
        }
    }
    // Results refresh after a short pause in typing; a new query or filter replaces the pending request.
    LaunchedEffect(searching, searchQuery, searchFilter, searchRetry) {
        val generation = ++searchGeneration
        searchNextId = 0L; searchError = null
        val needle = searchQuery.trim()
        val requestedFilter = searchFilter
        val active = needle.isNotEmpty() || requestedFilter.isActive
        searchBusy = searching && active
        if (!searching || !active) {
            searchResults = emptyList(); searchTotalCount = 0; searchRefreshing = false
            return@LaunchedEffect
        }
        val restore = searchMatchSession
        if (restore.canRestoreResults(searchQuery, requestedFilter)) {
            // Возврат из найденного сообщения: прежние результаты без повторного запроса, позицию восстановит список.
            searchResults = restore.results; searchNextId = restore.nextFromMessageId
            searchTotalCount = restore.totalCount; searchBusy = false; searchRefreshing = false
            return@LaunchedEffect
        }
        // Прежние результаты остаются на месте, но помечены устаревшими до ответа: без вспышки пустого экрана.
        searchRefreshing = searchResults.isNotEmpty()
        delay(300)
        (if (forumTopicId > 0L) conversation.searchForumTopicMessages(chat.id, forumTopicId, needle, 0L, requestedFilter)
        else conversation.searchMessages(chat.id, needle, 0L, requestedFilter))
            .onSuccess { page ->
                if (generation == searchGeneration && isCurrentChatSearch(needle, requestedFilter, searchQuery, searchFilter)) {
                    searchResults = page.messages
                    searchNextId = page.nextFromMessageId
                    // TDLib's total predates the local date filter, so the UI falls back to the loaded count then.
                    searchTotalCount = if (requestedFilter.hasDateRange) 0 else page.totalCount
                    searchRefreshing = false
                }
            }
            .onFailure { failure ->
                if (generation == searchGeneration && isCurrentChatSearch(needle, requestedFilter, searchQuery, searchFilter)) {
                    searchResults = emptyList(); searchTotalCount = 0
                    searchRefreshing = false
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
                        // Повторённый курсор означает ту же страницу: останавливаем автодогрузку, а не крутим её по кругу.
                        searchNextId = nextChatSearchCursor(cursor, page.nextFromMessageId)
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
    fun jumpToMessage(messageId: Long, failure: Int) {
        cancelActiveJump()
        val generation = jumpGeneration
        val index = messageListItems.indexOfFirst { item -> item.messages.any { it.id == messageId } }
        if (index >= 0) {
            jumpScrollJob = scope.launch {
                try {
                    listState.scrollToItemRespectingMotion(index, scrollOffset = targetScrollOffset(), motionInstant = listScrollInstant)
                    if (generation == jumpGeneration) highlighted = messageId
                } finally {
                    settleFocusedOpening()
                    if (generation == jumpGeneration) jumpScrollJob = null
                }
            }
            return
        }
        jumpLoadJob = scope.launch {
            try {
                (if (forumTopicId > 0L) conversation.loadForumTopicMessagesAround(chat.id, forumTopicId, messageId) else conversation.loadMessagesAround(chat.id, messageId))
                    .onSuccess { applied ->
                        if (generation != jumpGeneration) return@onSuccess
                        if (applied) { pendingJumpFailure = failure; pendingJumpId = messageId } else settleFocusedOpening()
                    }
                    .onFailure {
                        if (generation != jumpGeneration) return@onFailure
                        error = it.userMessage(context, failure)
                        settleFocusedOpening()
                    }
            } finally {
                if (generation == jumpGeneration) jumpLoadJob = null
            }
        }
    }
    fun jump(reply: MessageReply) {
        if (reply.isUnavailable || reply.messageId <= 0) { error = context.getString(R.string.chat_reply_original_unavailable); return }
        cancelActiveJump()
        val index = messageListItems.indexOfFirst { item -> item.messages.any { it.id == reply.messageId && it.chatId == reply.chatId } }
        if (index >= 0) scope.launch { listState.scrollToItemRespectingMotion(index, scrollOffset = targetScrollOffset(), motionInstant = listScrollInstant); highlighted = reply.messageId }
        // Same chat: load the window around the quoted message and jump there. Other chats keep the context dialog.
        else if (reply.chatId == chat.id) jumpToMessage(reply.messageId, R.string.chat_reply_original_unavailable)
        else { contextChatId = reply.chatId; contextMessageId = reply.messageId }
    }
    fun openSearchResult(message: ChatMessage, scrollIndex: Int, scrollOffset: Int) {
        val snapshot = searchResults
        val index = snapshot.indexOfFirst { it.id == message.id }.coerceAtLeast(0)
        val total = searchTotalCount
        val nextId = searchNextId
        val querySnapshot = searchQuery
        val filterSnapshot = searchFilter
        closeSearch(clearMatchSession = false)
        searchMatchGeneration++
        searchMatchSession = ChatSearchMatchSession(
            query = querySnapshot,
            filter = filterSnapshot,
            results = snapshot,
            index = index,
            totalCount = total,
            nextFromMessageId = nextId,
            scrollIndex = scrollIndex,
            scrollOffset = scrollOffset
        )
        jumpToMessage(message.id, R.string.err_chat_history_load_failed)
    }
    fun reopenSearchFromMatches() {
        val session = searchMatchSession
        cancelActiveJump()
        searchMatchGeneration++
        searchQuery = session.query
        searchFilter = session.filter
        // Сессия остаётся до закрытия поиска: по ней восстанавливается выдача и позиция списка.
        searching = true
    }
    fun stepNewerSearchMatch() {
        val next = searchMatchSession.stepNewer()
        if (next == searchMatchSession) return
        searchMatchSession = next
        next.currentMessageId?.let { jumpToMessage(it, R.string.err_chat_history_load_failed) }
    }
    fun stepOlderSearchMatch() {
        val session = searchMatchSession
        if (!session.canStepOlder) return
        if (session.index < session.results.lastIndex) {
            val next = session.stepOlderLoaded()
            searchMatchSession = next
            next.currentMessageId?.let { jumpToMessage(it, R.string.err_chat_history_load_failed) }
            return
        }
        val needle = session.query.trim()
        val cursor = session.nextFromMessageId
        if (cursor == 0L) return
        val generation = ++searchMatchGeneration
        searchMatchSession = session.copy(busy = true)
        scope.launch {
            (if (forumTopicId > 0L) conversation.searchForumTopicMessages(chat.id, forumTopicId, needle, cursor, session.filter)
            else conversation.searchMessages(chat.id, needle, cursor, session.filter))
                .onSuccess { page ->
                    if (generation != searchMatchGeneration || !searchMatchSession.isActive) return@onSuccess
                    val updated = searchMatchSession.appendPage(page.messages, page.nextFromMessageId)
                    searchMatchSession = updated
                    if (updated.index != session.index) {
                        updated.currentMessageId?.let { jumpToMessage(it, R.string.err_chat_history_load_failed) }
                    }
                }
                .onFailure { failure ->
                    if (generation != searchMatchGeneration || !searchMatchSession.isActive) return@onFailure
                    searchMatchSession = searchMatchSession.copy(busy = false)
                    error = failure.userMessage(context, R.string.chat_search_failed)
                }
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
                scope.launch { listState.scrollToItemRespectingMotion(firstUnread, motionInstant = listScrollInstant); highlighted = target }
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
            scope.launch { listState.scrollToItemRespectingMotion(firstUnread, motionInstant = listScrollInstant); highlighted = target }
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
    val delayed = rememberDelayedSend(chat.id, sendForumTopicId, conversation)
    // A plain text send that came before the account's delay was read. It is sent again once that is known, if the composer still
    // holds it; see below.
    var sendWaitingForDelay by remember(chat.id, forumTopicId) { mutableStateOf<HeldDelayedSend?>(null) }
    var paidTextConfirmation by remember(conversation, chat.id, forumTopicId) { mutableStateOf<PaidTextConfirmation?>(null) }
    val paidTextContext by rememberUpdatedState(Triple(conversation, chat.id, sendForumTopicId))
    val paidTextDestination by rememberUpdatedState(com.beykus.beygram.domain.model.PaidTextDestination(accountId, chat.id, sendForumTopicId))
    /**
     * Sends the current text draft or prepared attachments with [options]. The send-options menu is one-shot:
     * the choice applies to this send only and is not kept for the next one.
     */
    fun sendCurrentDraft(options: MessageSendOptions, priceChecked: Boolean = false) {
        if (secretChatClosed || editId != null || inlineSending || sending || paidTextConfirmation != null || textSendQueue.isComposerWriting(textSendScopeKey)) return
        // Check ordinary private and group text (including explicit forum topics).
        // Other content continues to carry zero Stars authorization.
        if (!priceChecked && com.beykus.beygram.domain.model.supportsPaidTextQuote(chat) &&
            pendingSticker == null && !attachments.active && draft.isNotBlank()) {
            val submitted = composerDraft
            val target = Triple(conversation, chat.id, sendForumTopicId)
            val destination = paidTextDestination
            sending = true
            scope.launch {
                var ordinary = false
                try {
                    // If the read fails, TDLib can still accept a regular message with a zero Stars authorization.
                    // Never translate a failed lookup into permission to spend.
                    val price = conversation.paidTextPrice(chat.id).getOrNull()
                    if (paidTextContext != target || paidTextDestination != destination || composerDraft != submitted || attachments.active || pendingSticker != null) return@launch
                    if (price == null || price <= 0L) {
                        ordinary = true
                    } else {
                        if (options.scheduledAtSeconds != null || conversation.stealthHoldsSends()) {
                            error = context.getString(R.string.stealth_schedule_unavailable_paid)
                            return@launch
                        }
                        val balance = conversation.starBalance().getOrElse {
                            error = it.userMessage(context, R.string.stars_load_error)
                            return@launch
                        }
                        if (paidTextContext != target || paidTextDestination != destination || composerDraft != submitted || attachments.active || pendingSticker != null) return@launch
                        paidTextConfirmation = PaidTextConfirmation(submitted, price, balance, options.disableNotification, destination, options.disableLinkPreview)
                    }
                } finally {
                    sending = false
                }
                if (ordinary && paidTextContext == target && paidTextDestination == destination && composerDraft == submitted && !attachments.active && pendingSticker == null) {
                    sendCurrentDraft(options, priceChecked = true)
                }
            }
            return
        }
        // Everything below sends: text, attachments, stickers and the paid path that came back here after its check.
        noteLocalSend()
        pendingSticker?.let { sticker ->
            pendingSticker = null
            attachments.sendStickerWithOptions(sticker, options)
            return
        }
        // View-once is selected in the attachment panel; scheduling or groups silently drop the flag instead of
        // producing a TDLib error, and the composer hides those paths for view-once media.
        val quotedOptions = quoteState.consumeInto(options, replyId)
        val effectiveOptions = if (attachments.active) {
            quotedOptions.copy(viewOnce = attachments.viewOnce && ViewOncePolicy.canSend(attachments.pending, isPrivateChat = allowViewOnce, isSecretChat = chat.isSecret, scheduled = quotedOptions.scheduledAtSeconds != null))
        } else quotedOptions
        if (attachments.active) {
            attachments.send(
                draft, draftEntities, replyId, sendForumTopicId, effectiveOptions,
                onCaptionConsumed = { sentCaption -> updateDraft { it.withAttachmentCaptionConsumed(sentCaption) } },
                onReplyConsumed = { sentReplyId -> updateDraft { it.withAttachmentReplyConsumed(sentReplyId) } }
            )
            return
        }
        val outgoingAnimation = messageAnimations.direction(outgoing = true)
        val outgoing = composerLaunchPayload(draft, draftEntities, limits.text)
        // Stealth mode requires Telegram's scheduled queue (#541). Local delay is separate and is skipped even for
        // an invalid legacy stealth lead, which the repository refuses instead of sending normally.
        val stealthScheduled = stealthWillSchedule(conversation, effectiveOptions)
        val stealthHolds = effectiveOptions.scheduledAtSeconds == null && conversation.stealthHoldsSends()
        // A delayed text is stored first and only then cleared from the composer. A bot's forced reply is always sent directly.
        val delayable = !stealthScheduled && !stealthHolds && !effectiveOptions.sendNowConsent && botKeyboard?.forceReply != true
        when (if (delayable) delayed.route(outgoing.text, effectiveOptions) else DelayedSendRoute.SendNow) {
            // The delay is not read yet: the text waits for it, instead of going out as if no delay were set.
            DelayedSendRoute.Hold -> { sendWaitingForDelay = HeldDelayedSend(effectiveOptions, composerDraft); return }
            DelayedSendRoute.Queue -> {
                // Only the text that was stored leaves the composer: text typed while the write ran stays.
                // Snapshot now: draft is a live derived state, and the callback runs after the write.
                val storedText = draft
                delayed.enqueue(outgoing.text, outgoing.entities, replyId, sendForumTopicId, effectiveOptions, onQueued = { updateDraft { current -> draftAfterDelayedQueue(current, storedText = storedText) } })
                return
            }
            DelayedSendRoute.SendNow -> Unit
        }
        if (effectiveOptions.scheduledAtSeconds == null && !stealthScheduled) {
            if (outgoing.text.isBlank()) return
            val replySnapshot = replyId
            val replyPreviewSnapshot = replySnapshot?.let { id ->
                val target = messages.firstOrNull { it.id == id }
                MessageReply(
                    chatId = chat.id,
                    messageId = id,
                    text = target?.text?.takeIf { it.isNotBlank() }?.let { com.beykus.beygram.domain.UiText.Raw(it) }
                        ?: com.beykus.beygram.domain.UiText.Res(R.string.chat_message),
                    senderName = target?.senderName?.ifBlank {
                        if (target.isOutgoing) context.getString(R.string.chat_you) else chat.title
                    } ?: ""
                )
            }
            val forceReplyId = botKeyboard?.takeIf { it.forceReply }?.messageId
            val launch = if (messageAnimations.enabled && outgoingAnimation.preset == MessageAnimationPreset.ComposerLaunch) {
                composerLaunch.start(outgoing, replySnapshot, history.liveArrivalSequence, outgoingAnimation.tuning.speed)
            } else null
            val timeLabel = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
            val baselineSeq = history.liveArrivalSequence
            error = null
            val submittedDraft = composerDraft
            textSendQueue.launchComposerWrite(textSendScopeKey) {
                try {
                    val attempt = textSendQueue.enqueue(
                        scopeKey = textSendScopeKey,
                        text = outgoing.text,
                        entities = outgoing.entities,
                        replyToMessageId = replySnapshot,
                        replyPreview = replyPreviewSnapshot,
                        options = effectiveOptions,
                        baselineSequence = baselineSeq,
                        timeLabel = timeLabel,
                        dismissForceReplyMessageId = forceReplyId,
                        // Fixed when the text is queued: a feed text goes to the reply's topic or General, whatever the composer does next.
                        destinationTopicId = sendForumTopicId,
                        execute = { attempt -> executeTextSendAttempt(conversation, attempt) },
                        onSuccess = {
                            launch?.awaitingSend = false
                        },
                        onFailure = { attempt, failure ->
                            launch?.awaitingSend = false
                            launch?.let(composerLaunch::cancel)
                            attachments.stealthFallback.offer(failure) {
                                retryLocalTextAttempt(attempt.attemptId, sendNowConsent = true)
                            }
                        },
                        formatError = { it.userMessage(context, R.string.err_chat_send_failed) }
                    )
                    launch?.localAttemptId = attempt.attemptId
                    launch?.message = attempt.toChatMessage()
                    launch?.awaitingSend = false
                } catch (_: TextSendStorageException) {
                    launch?.awaitingSend = false
                    launch?.let(composerLaunch::cancel)
                    error = queueStorageFailureText
                    return@launchComposerWrite
                }
                updateDraft { current -> if (current == submittedDraft) MessageDraft() else current }
                launch?.let(composerLaunch::releaseDraft)
            }
            return
        }
        // A scheduled message is not in the chat yet, so it must not run the Composer Launch animation.
        val submitted = composerDraft
        sending = true; error = null
        scope.launch {
            try {
                val result = conversation.sendMessage(
                    chat.id, outgoing.text, replyId, outgoing.entities, forumTopicId = sendForumTopicId,
                    clearDraft = false, options = effectiveOptions
                )
                if (result.isSuccess) {
                    updateDraft { current -> if (current == submitted) MessageDraft() else current }
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
                sending = false
            }
        }
    }

    // A send held while the account's delay was read goes through the same path once that is known, if the composer still holds
    // the text that was sent. An edit made meanwhile cancels it: nothing else goes out, and the composer keeps what is there.
    LaunchedEffect(delayed.ready) {
        val held = sendWaitingForDelay
        if (delayed.ready && held != null) {
            sendWaitingForDelay = null
            if (!heldSendStillMatches(held.draft, composerDraft, attachments.active, pendingSticker != null)) {
                Toast.makeText(context, R.string.delayed_send_edited_while_waiting, Toast.LENGTH_LONG).show()
            } else if (draft.isNotBlank()) {
                sendCurrentDraft(held.options)
            }
        }
    }

    // Reported by PinnedMessagesBar: while the preview sits under the header, the group ends with this row's hairline.
    var pinnedPreviewShown by remember(chat.id, forumTopicId) { mutableStateOf(false) }
    // The pinned data outlives the bar: the keyboard, the search and the selection mode hide the row for a while, and
    // a hidden bar must not lose its loaded pages or re-request them when it comes back.
    val pinnedVersion by remember(conversation, chat.id) { conversation.observePinnedMessagesVersion(chat.id) }.collectAsStateWithLifecycle()
    val pinnedState = remember(conversation, chat.id, forumTopicId) {
        PinnedMessagesState(chat.id, forumTopicId) { from -> conversation.pinnedMessages(chat.id, from, forumTopicId) }
    }
    val pinsConnected = connectionState == TelegramConnectionState.Connected
    LaunchedEffect(pinnedState, pinnedVersion, pinsConnected, pinnedState.retryTick) { pinnedState.refresh(pinnedVersion, pinsConnected) }
    var pinnedListOpen by rememberSaveable(chat.id, forumTopicId) { mutableStateOf(false) }
    var pinnedPreviewSource by remember(chat.id, forumTopicId) { mutableStateOf<PinnedPreviewSource?>(null) }
    var livePinnedPreview by remember(chat.id, forumTopicId) { mutableStateOf<PinnedPreviewSource?>(null) }
    var livePinnedHeader by remember(chat.id, forumTopicId) { mutableStateOf<Rect?>(null) }
    var pinnedHeaderSource by remember(chat.id, forumTopicId) { mutableStateOf<Rect?>(null) }
    // Hiding the pinned bar is local to this chat and this account; the pins themselves stay on the server.
    val pinnedBarHidden = chatOverrideState[chat.id]?.pinnedBarHidden == true
    val windowLayout = LocalWindowLayout.current
    // In a short window the keyboard leaves the messages almost nothing: the pinned strip yields its line while it is up.
    val imeInsets = WindowInsets.ime
    val imeDensity = LocalDensity.current
    // Only the open/closed flip recomposes the screen, not every frame of the keyboard animation.
    val keyboardOpen by remember(imeInsets, imeDensity) { derivedStateOf { imeInsets.getBottom(imeDensity) > 0 } }
    val compactChrome = windowLayout.heightCompact
    Box(Modifier.fillMaxSize().onGloballyPositioned { composerLaunch.overlayBounds = it.boundsInWindow() }) {
    // The system bar bands take the colour of the bar next to them (header on top, composer at the bottom), so the edges
    // of the screen are not two-toned. In Liquid Glass that is the pane tint over the screen colour. The status bar band
    // is drawn only for non-glass styles; in glass the header pane covers the status bar.
    val systemBarDensity = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(systemBarDensity).toFloat()
    val navBarPx = WindowInsets.navigationBars.getBottom(systemBarDensity).toFloat()
    val glassBandFill = palette.surface.copy(alpha = 0.72f).compositeOver(palette.screen)
    // The band takes the colour of the header next to it: Signature's header stands on the canvas, the rest on the surface.
    val topBandFill = when {
        chatGlass -> glassBandFill
        design.components.topBar == TopBarVariant.Signature -> palette.screen
        else -> palette.surface
    }
    val bottomBandFill = when {
        chatGlass -> glassBandFill
        design.components.composer == ComposerVariant.Signature -> palette.screen
        else -> palette.surface
    }
    Column(Modifier.fillMaxSize().drawBehind {
        drawRect(palette.screen)
        if (!chatGlass) drawRect(topBandFill, size = Size(size.width, statusBarPx))
        drawRect(bottomBandFill, topLeft = Offset(0f, size.height - navBarPx), size = Size(size.width, navBarPx))
    }.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
        // The boundary must reflect what the reader really sees: rows fully hidden behind the overlapping chrome do
        // not pick the pin, and fast scrolling stays stable because partially visible rows still count.
        val visibleOldestPinnedBoundary by remember(listState, messageListItems) {
            derivedStateOf {
                var oldest: Long? = null
                val layout = listState.layoutInfo
                val coveredTop = if (chatBackdrop != null) topChromePx else 0
                val uncoveredTop = layout.viewportEndOffset - coveredTop
                for (item in layout.visibleItemsInfo) {
                    // Reverse list: the main axis starts at the composer, so the header covers the highest offsets.
                    if (coveredTop > 0 && item.offset >= uncoveredTop) continue
                    val messages = messageListItems.getOrNull(item.index)?.messages ?: continue
                    for (message in messages) if (oldest == null || message.id < oldest) oldest = message.id
                }
                oldest
            }
        }
        // The pinned bar follows the resolved panels placement: attached it belongs to the header's pane, floating it
        // is its own surface under it. The search, the selection mode, a hidden bar and a keyboard in a short window
        // skip the row, never the data.
        val showPinnedBar = !(compactChrome && keyboardOpen) && !searching && !selectionMode && !pinnedBarHidden
        val pinnedBarSlot: @Composable ColumnScope.() -> Unit = {
            if (showPinnedBar) PinnedMessagesBar(
                state = pinnedState,
                conversation = conversation,
                observeFile = observeFile,
                visibleOldestMessageId = visibleOldestPinnedBoundary,
                onOpen = ::jumpToPinned,
                onOpenList = { pinnedPreviewSource = null; pinnedHeaderSource = null; pinnedListOpen = true },
                onOpenListFromPreview = { source -> pinnedPreviewSource = source; pinnedHeaderSource = livePinnedHeader; pinnedListOpen = true },
                hiddenPreviewId = pinnedPreviewSource?.id?.takeIf { pinnedListOpen },
                onPreviewMeasured = { livePinnedPreview = it },
                onHideBar = { chatOverrides.setPinnedBarHidden(chat.id, true) },
                onUnpin = { message -> setPinned(message, false) },
                onRowShownChange = { pinnedPreviewShown = it }
            )
        }
        ChatChromeHost(chatGlass, GlassEdge.BottomHairline, chatBackdrop, { topChromePx = it },
            paneContent = {
        if (selectionMode) SelectionActionBar(
            count = selectionIds.size,
            busy = selectionBusy || forwardBusy,
            error = selectionError,
            canCopy = selectionCanCopy,
            canSave = selectionCanSave,
            onClose = ::exitSelection,
            onMore = singleActionTarget?.let { id -> { exitSelection(); actionAnchorBounds = bubbleRect(id); messageActions.open(id) } },
            onForward = {
                val ids = forwardIdsForSelection(messages, selectionIds)
                if (ids.isNotEmpty()) {
                    frozenForwardSelection = ids.toLongArray()
                    forwardAsCopy = false; selectionForwardOpen = true; selectionError = null
                }
            },
            onDelete = ::requestSelectionDelete,
            onCopy = ::copySelection,
            onSave = ::saveSelection
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
            observeCustomEmoji = conversation::observeCustomEmoji,
            forumTopic = forumTopic,
            profileTransition = profileTransition,
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
            secretChat = secretChat,
            pinnedBelow = pinnedPreviewShown && !design.navigationFloating,
            showBack = !windowLayout.twoPane || forumTopicId > 0L || forumAllMessages,
            compact = compactChrome,
            pinnedCount = pinnedState.count,
            onPinnedHeaderMeasured = { livePinnedHeader = it },
            onOpenPinned = { pinnedPreviewSource = null; pinnedHeaderSource = null; pinnedListOpen = true },
            forumNavigationMode = forumNavigationMode,
            onForumNavigationMode = onForumNavigationMode,
            forumAllMessages = forumAllMessages
        )
        // The forum's Tabs strip sits under the header. Search and selection own that band while they are open.
        if (!selectionMode && !searching && topicStrip != null) topicStrip()
        // Keep playback accessible below the chat header, not between messages and the composer.
        // The input/IME and reply controls should remain adjacent to the message list.
        // In the split layout the player already lives under the list on the left, so the chat has no strip of its own.
        if (!windowLayout.twoPane) NowPlayingBar(compact = true)
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
        ChatQuoteHost(quoteState) { id ->
            setReplyTarget(id)
            replyFocusRequest++
        }
        if (actionMessage != null && !deleting && !reactionPickerOpen && !pinConfirm) {
            val chosenReactions = reactionOwner?.reactions
                ?.filter { it.isChosen && !it.isPaid }
                ?.mapTo(hashSetOf()) { it.option }
                .orEmpty()
            val sceneEligible = !chat.isSecret && !chat.hasProtectedContent && !waveChat &&
                actionMessage.supportsContextScene()
            MessageContextPopover(
                anchorBounds = actionAnchorBounds,
                paneBounds = composerLaunch.overlayBounds,
                foreground = if (sceneEligible) {
                    { selection ->
                        MessageBubble(
                            message = actionMessage,
                            observeFile = observeFile,
                            onDownloadFile = onDownloadFile,
                            isRead = actionMessage.isOutgoing && actionMessage.id <= history.lastReadOutboxMessageId,
                            onOpen = {},
                            onReaction = { _, _ -> },
                            observeCustomEmoji = conversation::observeCustomEmoji,
                            showSenderHeader = senderHeaderByKey["message:${actionMessage.id}"] == true,
                            paneInset = false,
                            contextTextSelection = selection
                        )
                    }
                } else null,
                foregroundText = actionMessage.text.takeIf { sceneEligible },
                onQuoteFragment = { fragment ->
                    quoteState.select(actionMessage.id, fragment.text, fragment.position)
                    setReplyTarget(actionMessage.id)
                    replyFocusRequest++
                    closeActions()
                },
                onCopyFragment = { fragment ->
                    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.chat_message), fragment))
                    closeActions()
                },
                onForegroundVisible = { visible ->
                    if (visible) contextSceneMessageId = actionMessage.id
                    else if (contextSceneMessageId == actionMessage.id) contextSceneMessageId = null
                },
                isOutgoing = actionMessage.isOutgoing,
                capabilities = capabilities?.let { caps ->
                    // TDLib already forbids this; keep a local guard so a stale capability response cannot expose view-once.
                    if (actionMessage.isViewOnce) caps.copy(canForward = false, canCopy = false, canGetLink = false, canEdit = false) else caps
                },
                error = capabilitiesError,
                hasText = actionMessage.text.isNotBlank(),
                isMedia = actionMessage.media != null,
                canPin = canTogglePin(capabilities?.canPin == true),
                isPinned = actionMessage.isPinned,
                reactionOptions = reactionOptions,
                chosenReactions = chosenReactions,
                observeCustomEmoji = conversation::observeCustomEmoji,
                observeFile = observeFile,
                onDownloadFile = onDownloadFile,
                onQuickReaction = if (reactionOwner != null) {
                    { option ->
                        setReaction(reactionOwner.id, option, addToRecent = true)
                        closeActions()
                    }
                } else null,
                onClose = ::closeActions,
                onRetry = { capabilitiesAttempt++ },
                onReply = { quoteState.clear(); setReplyTarget(actionMessage.id); replyFocusRequest++; closeActions() },
                onQuote = if (actionMessage.text.isNotBlank()) { { quoteState.pickerTarget = actionMessage; closeActions() } } else null,
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
                onReactions = if (reactionOwner != null && reactionOptions.isNotEmpty()) { { reactionPickerOpen = true } } else null,
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
                } else null,
                onAddToPlaylist = if (chat.hasProtectedContent) null else playlistTrackOf(actionMessage, accountId, { id -> observeFile(id).value }, context.getString(R.string.media_kind_audio))?.let { track -> { playlistTrack = track; closeActions() } },
                onPlayNext = queueActionItem(actionMessage, chat.hasProtectedContent)?.let { item -> { enqueueMusic(item, atEnd = false); closeActions() } },
                onEnqueue = queueActionItem(actionMessage, chat.hasProtectedContent)?.let { item -> { enqueueMusic(item, atEnd = true); closeActions() } }
            )
        }
        reactionOwner?.takeIf { !deleting && reactionPickerOpen }?.let { owner ->
            MessageReactionPicker(reactionOptions, owner.reactions.filter { it.isChosen && !it.isPaid }.mapTo(hashSetOf()) { it.option }, conversation::observeCustomEmoji, observeFile, onDownloadFile) { option ->
                setReaction(owner.id, option, addToRecent = true)
                closeActions()
            }
        }
        // Sync the pinned preview with what the reader is currently seeing, as in official Telegram.
        if (!design.navigationFloating) pinnedBarSlot()
        ChatConnectionBanner(accountId, connectionState)
        // Non-inline wrappers keep these large blocks out of ChatScreen's own bytecode (JVM 64 KB method limit).
            },
            floatingContent = { if (design.navigationFloating) pinnedBarSlot() }
        )
        ChatScreenBox(Modifier.fillMaxWidth().weight(1f)) {
        ChatScreenBox(Modifier.underChatChrome({ if (chatBackdrop != null) topChromePx else 0 }, { if (chatBackdrop != null || floatingComposer) bottomChromePx else 0 }).let { m -> if (chatBackdrop != null) m.glassBackdropSource(chatBackdrop) else m }) {
        ChatWallpaperLayer(chatWallpaper)
        val metrics = LocalBubbleMetrics.current
        val chromeDensity = LocalDensity.current
        val chromeTopPad = if (chatBackdrop != null) with(chromeDensity) { topChromePx.toDp() } else 0.dp
        val chromeBottomPad = if (chatBackdrop != null || floatingComposer) with(chromeDensity) { bottomChromePx.toDp() } else 0.dp
        LazyColumn(state = listState, reverseLayout = true, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = metrics.listPadding + chromeTopPad, bottom = metrics.listPadding + chromeBottomPad), verticalArrangement = Arrangement.spacedBy(metrics.itemSpacing)) {
            items(messageListItems, key = { it.key }, contentType = { chatListContentType(it) }) { listItem ->
                ChatScreenColumn(Modifier.fillMaxWidth()) {
                daySeparators[listItem.key]?.let { ChatHistoryDateSeparator(it) }
                feedTopicHeaders[listItem.key]?.let { topicId ->
                    ForumFeedTopicHeader(
                        topic = forumTopicsById[topicId],
                        observeFile = observeFile,
                        downloadFile = onDownloadFile,
                        observeCustomEmoji = conversation::observeCustomEmoji,
                        onOpen = onOpenForumTopic,
                        modifier = Modifier.padding(horizontal = metrics.sideInset)
                    )
                }
                if (listItem.unreadSeparator) {
                    UnreadMessagesSeparator()
                } else if (listItem.pendingVideoNote != null) {
                    PendingVideoNoteBubble(listItem.pendingVideoNote)
                } else if (listItem.mediaAlbumId != 0L) {
                    val latest = listItem.messages.maxByOrNull { it.id }
                    val animateArrival = remember(listItem.key) { animationTracker.claim(listItem.messages.map { it.id }) }
                    val effectMessage = listItem.messages.firstOrNull { it.effectId != 0L }
                    val albumLaunches = mediaLaunchController.active.mapNotNull { flight ->
                        val id = resolveMessageIdThroughReplacements(flight.messageId, history.recentReplacements)
                        listItem.messages.firstOrNull { it.id == id && it.isOutgoing &&
                            it.sendState != MessageSendState.Failed && it.media?.let { media ->
                                !media.hasSpoiler && !media.isSecret && !media.isSecretChat
                            } == true }?.let { id to flight }
                    }.toMap()
                    MessageAnimationFrame(
                        animate = animateArrival && albumLaunches.isEmpty(),
                        outgoing = latest?.isOutgoing == true,
                        config = messageAnimations,
                        sideInset = metrics.sideInset
                    ) {
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
                                DisposableEffect(listItem.key) {
                                    onDispose {
                                        listItem.messages.forEach { bubbleCoordinates.remove(it.id); mediaCoordinates.remove(it.id) }
                                    }
                                }
                                val commentsPost = listItem.messages.firstOrNull { it.replyCount > 0 }
                                    ?: listItem.messages.firstOrNull()
                                val albumComments = commentsPost?.takeIf { showsChannelComments(chat.isChannel, it.isChannelPost, linkedDiscussionChatId) }
                                MessageAlbumBubble(listItem.messages, observeFile, onDownloadFile,
                                    isRead = latest?.let { it.isOutgoing && it.sendState == MessageSendState.Sent && it.id <= history.lastReadOutboxMessageId } == true,
                                    highlighted = listItem.messages.any { it.id == highlighted || it.id == actionsId },
                                    onOpen = { selectedMediaId = it }, onActions = ::openActionsOnLongPress, onReplyClick = ::jump,
                                    onPlayed = { conversation.openMessageContent(chat.id, it) },
                                    onMediaBounds = { coordinates ->
                                        listItem.messages.forEach { mediaCoordinates[it.id] = coordinates }
                                        if (actionsId != null && listItem.messages.any { it.id == actionsId }) {
                                            coordinates.mediaScreenBounds()?.let { actionAnchorBounds = it }
                                        }
                                    }, onReply = if (swipeReplyAllowed) ::swipeReply else null,
                                    onReaction = ::react, loadQuickReactions = conversation::availableReactions, observeCustomEmoji = conversation::observeCustomEmoji, onRetry = { retryFailedMessages(listItem.messages.filter { it.sendState == MessageSendState.Failed }.map { it.id }) },
                                    selectionMode = selectionMode,
                                    selected = listItem.messages.all { it.id in selectionIds },
                                    onToggleSelection = { listItem.messages.firstOrNull()?.let(::toggleSelection) },
                                    commentsBar = albumComments?.let { post ->
                                        { ChannelCommentsBar(listItem.messages.maxOfOrNull { it.replyCount } ?: 0) { onOpenComments(post) } }
                                    },
                                    onAutoDownloadFile = onAutoDownloadFile,
                                    showSenderHeader = senderHeaderByKey[listItem.key] == true,
                                    onOpenMessageEntity = onOpenMessageEntity,
                                    composerMediaLaunches = albumLaunches,
                                    onComposerMediaLaunchFinished = mediaLaunchController::finish,
                                    onLoadVersions = { chatId, messageId -> conversation.messageVersions(chatId, messageId) })
                            }
                        }
                    }
                } else {
                    val message = listItem.messages.single()
                    val animateArrival = remember(listItem.key) { animationTracker.claim(listOf(message.id)) }
                    // A confirmed send replaces the temporary id; the dice throw keeps its original identity.
                    val diceAnimationId = rememberDiceAnimationId(message.id, history.recentReplacements)
                    val launch = launchTargets[message.id]
                    val mediaLaunch = mediaLaunchController.active.firstOrNull {
                        message.media?.kind in setOf(MediaKind.Photo, MediaKind.Video) && message.isOutgoing &&
                            message.media?.let { !it.hasSpoiler && !it.isSecret && !it.isSecretChat } == true &&
                            message.sendState != MessageSendState.Failed &&
                            resolveMessageIdThroughReplacements(it.messageId, history.recentReplacements) == message.id
                    }
                    val launchModifier = if (launch != null) Modifier.graphicsLayer {
                        alpha = if (launch.ownsRow(message.id)) 0f else 1f
                    } else Modifier
                    // Only an open action menu and an in-flight launch follow the bubble while the list moves. Other rows only
                    // store their layout; the rectangle is computed when something reads it.
                    val bubblePosition: (LayoutCoordinates) -> Unit = { coordinates ->
                        bubbleCoordinates[message.id] = coordinates
                        val inActionMenu = actionsId == message.id
                        if (inActionMenu || launch != null) {
                            val rect = coordinates.boundsInWindow()
                            if (inActionMenu) actionAnchorBounds = rect
                            launch?.positionRow(message.id, rect)
                        }
                    }
                    val isCenteredRow = message.serviceText != null
                    val isCircleMedia = !isCenteredRow && message.media?.kind == MediaKind.VideoNote
                    val bubbleSideInset = if (isCenteredRow || isCircleMedia) 0.dp else metrics.sideInset
                    MessageAnimationFrame(
                        animate = animateArrival && launch == null && mediaLaunch == null,
                        outgoing = message.isOutgoing,
                        config = messageAnimations,
                        modifier = launchModifier,
                        centered = isCenteredRow,
                        sideInset = bubbleSideInset
                    ) {
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
                            DisposableEffect(launch, message.id) {
                                onDispose { launch?.releaseRow(message.id) }
                            }
                            DisposableEffect(message.id) {
                                onDispose { bubbleCoordinates.remove(message.id); mediaCoordinates.remove(message.id) }
                            }
                            val translationLanguage = translationLanguages[message.id]
                            val translationState = translationLanguage?.let { translationStates[message.id to it] }
                            val media = message.media
                            val transcriptionFailure = transcriptionErrors[message.id]
                                ?.takeIf { it.serverFailureAtStart == media?.transcriptionError }
                                ?.failure
                            if (message.isCallEvent && message.serviceText != null) CallMessageChip(message.serviceText, message.time)
                            // A gift keeps the same text (search, previews and the screen reader use it) and gains the
                            // artwork, number and title that a plain chip cannot show.
                            else if (message.gift != null && message.serviceText != null) GiftChatCard(
                                gift = message.gift,
                                caption = message.serviceText,
                                observeFile = observeFile,
                                downloadFile = onDownloadFile
                            )
                            else if (message.serviceText != null) ServiceMessageChip(message.serviceText, message.serviceUserId, onServiceUser)
                            else {
                                val localAttempt = localTextAttemptIds[message.id]
                                MessageBubble(message, observeFile, onDownloadFile, isRead = message.isOutgoing && message.sendState == MessageSendState.Sent && message.id <= history.lastReadOutboxMessageId,
                                    highlighted = message.id == highlighted || message.id == actionsId,
                                    onOpen = { selectedMediaId = message.id },
                                    onActions = {
                                        if (localAttempt != null) {
                                            if (localAttempt.phase == TextSendAttemptPhase.Failed) failedSendTarget = FailedSendTarget(message, localAttempt.attemptId)
                                        } else {
                                            openActionsOnLongPress(message.id)
                                        }
                                    },
                                    onReplyClick = ::jump,
                                    onReply = if (swipeReplyAllowed) ::swipeReply else null,
                                    onPlayed = { conversation.openMessageContent(chat.id, message.id) },
                                    musicLinkCards = !chat.isSecret,
                                    waveAccountId = accountId.takeIf { waveChat },
                                    onRetry = {
                                        if (localAttempt != null) {
                                            retryLocalTextAttempt(localAttempt.attemptId)
                                        } else {
                                            retryFailedMessages(listOf(message.id))
                                        }
                                    },
                                    connectionState = connectionState,
                                    onShowFailureDetails = if (message.isOutgoing && message.sendState == MessageSendState.Failed) {
                                        { failedSendTarget = FailedSendTarget(message, localAttempt?.attemptId) }
                                    } else null,
                                    onReaction = ::react, loadQuickReactions = conversation::availableReactions, quickReactionAcrossRow = true, observeCustomEmoji = conversation::observeCustomEmoji, inlineContent = { InlineBotButtons(message, conversation, riskGuard, ::requestBotWebApp, onOpenEntity = onOpenMessageEntity) },
                                onBubblePositioned = bubblePosition,
                                onMediaBounds = { coordinates -> mediaCoordinates[message.id] = coordinates },
                                composerMediaLaunch = mediaLaunch,
                                onComposerMediaLaunchFinished = mediaLaunchController::finish,
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
                                    riskGuard.run {
                                        scope.launch {
                                            conversation.votePoll(chat.id, messageId, choices)
                                                .onFailure { error = it.userMessage(context, R.string.chat_poll_vote_failed) }
                                        }
                                    }
                                },
                                onPollStop = { messageId ->
                                    riskGuard.run {
                                        scope.launch {
                                            conversation.stopPoll(chat.id, messageId)
                                                .onFailure { error = it.userMessage(context, R.string.chat_poll_stop_failed) }
                                        }
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
                                onLoadVersions = { chatId, messageId -> conversation.messageVersions(chatId, messageId) },
                                onAddPollOption = { messageId, text ->
                                    if (riskGuard.confirm()) conversation.addPollOption(chat.id, messageId, text)
                                    else Result.failure(CancellationException("stealth risk declined"))
                                },
                                onAutoDownloadFile = onAutoDownloadFile,
                                onMusicPlay = { selected, startMs ->
                                    // Never build a cross-topic, protected or secret conversation playlist.
                                    if (chat.isSecret || chat.hasProtectedContent || selected.systemPrivate) {
                                        PlaybackEngine.toggle(context, selected.copy(secret = true), startPositionMs = startMs)
                                    } else if (PlaybackEngine.state.value.item?.key == selected.key) {
                                        // A second tap preserves any pages already appended to the active queue.
                                        PlaybackEngine.toggle(context, selected, startPositionMs = startMs)
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
                                            ),
                                            startPositionMs = startMs
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
                                onOpenMessageEntity = onOpenMessageEntity,
                                hideForContextScene = contextSceneMessageId == message.id)
                            }
                        }
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
        }
        if (!searching) FloatingMessageDate(listState, messageListItems, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
        val away by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
        ChatJumpControls(
            chatId = chat.id,
            forumTopicId = forumTopicId,
            unreadMentions = if (forumTopicId > 0L) topicUnreadMentionCount else chat.unreadMentionCount,
            unreadReactions = if (forumTopicId > 0L) topicUnreadReactionCount else chat.unreadReactionCount,
            showJumpLatest = away || history.hasNewerMessages,
            conversation = conversation,
            onJumpToMessage = { id -> jumpToMessage(id, R.string.chat_message_unavailable) },
            onReadAllFailed = { error = context.getString(R.string.chat_read_all_failed) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp)
        ) {
            JumpToLatestButton(unread = if (forumTopicId > 0L) topicUnreadCount else chat.unreadCount, onClick = {
                val hasNewerMessages = history.hasNewerMessages
                if (hasNewerMessages) {
                    if (forumTopicId > 0L) conversation.loadForumTopicHistory(chat.id, forumTopicId)
                    else conversation.loadLatestMessages(chat.id)
                }
                scope.launch {
                    if (shouldAnimateJumpToLatest(listScrollInstant.value, hasNewerMessages)) {
                        listState.scrollToItemRespectingMotion(0, motionInstant = listScrollInstant)
                    } else {
                        listState.scrollToItem(0)
                    }
                }
            })
        }
        if (searching) ChatSearchResults(
            query = searchQuery,
            filter = searchFilter,
            results = searchResults,
            totalCount = searchTotalCount.takeIf { it > 0 },
            busy = searchBusy,
            error = searchError,
            hasMore = searchNextId != 0L,
            stale = searchRefreshing,
            restore = searchMatchSession,
            onOpen = ::openSearchResult,
            onMore = ::loadMoreResults,
            onRetry = { searchRetry++ },
            onResetFilters = { searchFilter = ChatSearchFilter() }
        )
        if (videoNote.active) VideoNoteRecordingOverlay(videoNote)
        }
        ChatChromeHost(chatGlass, GlassEdge.TopHairline, chatBackdrop, { bottomChromePx = it }, floatingComposer = floatingComposer, paneContent = {
        if (queueStorageCorrupt) Row(
            Modifier.fillMaxWidth().background(palette.warningSurface).errorAnnouncement().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.chat_send_queue_corrupt_notice), color = palette.warningText, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                textSendQueue.launchMutation {
                    try {
                        textSendQueue.quarantineAndReset()
                        if (error == queueStorageFailureText) error = null
                    } catch (_: TextSendStorageException) {
                        error = queueStorageFailureText
                    }
                }
            }) {
                Text(stringResource(R.string.chat_send_queue_reset_action), color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        error?.let { failure -> Row(Modifier.fillMaxWidth().background(palette.warningSurface).errorAnnouncement().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(failure, color = palette.warningText, fontSize = 12.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { error = null }) { Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = palette.warningText) }
        } }
        forwardCopyConfirmId?.let { id ->
            BeyGramDialog(
                onDismissRequest = { forwardCopyConfirmId = null },
                title = stringResource(R.string.message_forward_copy_title),
                message = stringResource(R.string.message_forward_copy_warning),
                confirmText = stringResource(R.string.message_forward_copy_confirm),
                onConfirm = { forwardCopyConfirmId = null; forwardAsCopy = true; forwardId = id },
                dismissText = stringResource(R.string.common_cancel),
                onDismiss = { forwardCopyConfirmId = null }
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
        if (!searching && !selectionMode && !deleting && searchMatchSession.isActive) {
            ChatSearchMatchBar(
                session = searchMatchSession,
                onReopenSearch = ::reopenSearchFromMatches,
                onStepOlder = ::stepOlderSearchMatch,
                onStepNewer = ::stepNewerSearchMatch,
                onClose = { closeSearch() }
            )
        }
        // Keep presence above the entire composer, including any staged attachment tray.
        // Never place it between attachment previews and the caption/keyboard.
        key(chat.id) {
            AnimatedTypingRow(if (!deleting && !searching && !selectionMode) typingNames else emptyList())
        }
        val replyTarget = replyId?.let { id -> messages.firstOrNull { it.id == id } }
        if (deleting && actionMessage != null) {
            ChatMessageDeletePrompt(actionMessage, messageActions, conversation, scope, onDeleted = ::closeActions)
        } else if (editMessage != null) {
            EditBanner(editMessage.text.ifBlank { editMessage.summary() }, editMessage.media != null, editText.codePointCount(0, editText.length), editLimit, editError, onClose = ::closeEdit)
        }
        // The reply strip lives inside the field container, above the text, as one composer unit.
        val replyHeader: (@Composable () -> Unit)? = if (replyId == null) null else ({
            val senderLabel = stringResource(R.string.chat_reply_label) + " · " + (replyTarget?.senderName?.ifBlank { if (replyTarget.isOutgoing) stringResource(R.string.chat_you) else chat.title } ?: stringResource(R.string.chat_message))
            val previewText = quoteState.textFor(replyId)?.let { "«$it»" } ?: replyTarget?.summary() ?: stringResource(R.string.chat_message_unavailable)
            ChatReplyHeaderStrip(senderLabel, previewText, chatGlass, sending, ::clearReply)
        })
        val editing = editMessage != null
        val fieldText = if (editing) editText else draft
        val fieldEntities = if (editing) editEntities else draftEntities
        // While the send request is in flight, Composer Launch owns the visual copy. The draft stays in state and is
        // restored if the send fails; hiding it here prevents two simultaneous copies of the same text.
        val displayedFieldText = if (hidingComposerForLaunch && !editing) "" else fieldText
        val displayedFieldEntities = if (hidingComposerForLaunch && !editing) emptyList() else fieldEntities
        val fieldLimit = when { editing -> editLimit; attachments.active && attachments.extra == null -> limits.caption; else -> limits.text }
        val overLimit = fieldText.codePointCount(0, fieldText.length) > fieldLimit
        val busy = sending || inlineSending || editBusy || attachments.sending || textQueueWriting
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
        // Chats the user cannot write to get no composer, only a plain notice, as in Telegram.
        if (!deleting && !searching && !selectionMode && !canPostHere) ReadOnlyChatBar(
            when {
                secretChatClosed -> stringResource(R.string.secret_chat_closed_composer)
                // The feed needs a reply or a writable General topic; a chat without posting rights keeps the plain notice.
                feedSendTarget == ForumFeedSendTarget.Unavailable && chat.canPost -> stringResource(R.string.forum_all_messages_hint)
                else -> stringResource(R.string.chat_read_only)
            }
        )
        if (!deleting && !searching && !selectionMode && canPostHere) ChatScreenColumn(Modifier.fillMaxWidth().then(
            // Signature keeps the composer on the canvas behind one hairline; the field itself is the only filled plane.
            if (chatGlass || floatingComposer) Modifier
            else if (design.components.composer == ComposerVariant.Signature) Modifier.signatureChrome(palette.screen, palette.divider, HairlineEdge.Top)
            else Modifier.background(palette.surface)
        ).navigationBarsPadding().imePadding()) {
        // A bot with no outgoing message in its whole history gets an explicit /start action, independent of inline mode or Mini Apps.
        if (info?.kind == ProfileKind.Bot && !editing && !recordingActive && !attachments.active &&
            !history.isLoading && !history.hasOlderMessages && history.messages.none { it.isOutgoing }
        ) BotStartBar(
            conversation = conversation,
            chatId = chat.id,
            forumTopicId = sendForumTopicId,
            onError = { error = it.userMessage(context, R.string.chat_bot_start_failed) }
        )
        if (!recordingActive && !editing && !attachments.active) BotInlineResultsPanel(
            chatId = chat.id,
            text = draft,
            active = !sending && !hidingComposerForLaunch,
            replyToMessageId = replyId,
            forumTopicId = sendForumTopicId,
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
        val embeddedAttachments = !editing && attachments.active && !attachments.menuOpen && !stickerPanelOpen
        AttachmentComposerContainer(embeddedAttachments) {
        if (!editing) {
            ChatComposerPanel(
                stickerPanelOpen = stickerPanelOpen,
                embedded = embeddedAttachments,
                attachments = attachments,
                conversation = conversation,
                observeFile = observeFile,
                onDownloadFile = onDownloadFile,
                allowViewOnce = allowViewOnce,
                chatId = chat.id,
                replyId = replyId,
                forumTopicId = sendForumTopicId,
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
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = if (embeddedAttachments) 2.dp else 10.dp, vertical = if (compactChrome || embeddedAttachments) 4.dp else 8.dp), verticalAlignment = Alignment.Bottom) {
            if (!recordingActive) {
                if (!editing) {
                    botCommandMenu?.takeIf { it.commands.isNotEmpty() }?.let {
                        BotCommandButton(enabled = !sending && !inlineSending && !attachments.active) { botCommandMenuOpen = true }
                    }
                    AttachmentButton(attachments, enabled = !sending && !inlineSending, embedded = embeddedAttachments)
                    Spacer(Modifier.width(8.dp))
                } else Spacer(Modifier.width(8.dp))
            }
            if (audioNote.active) AudioNoteRecordingDock(audioNote, Modifier.weight(1f), onCancel = audioNote::discardLocked)
            else if (videoNote.active) VideoNoteRecordingDock(videoNote, Modifier.weight(1f), onCancel = videoNote::discardLocked)
            else Column(Modifier.weight(1f)) {
                FormattingTextField(displayedFieldText, displayedFieldEntities,
                onChange = { if (!hidingComposerForLaunch) { if (stickerPanelOpen) stickerPanelOpen = false; if (editing) editText = it else typedDraft(it) } },
                onEntitiesChange = { if (!hidingComposerForLaunch) { if (editing) editEntities = it else updateDraft { state -> state.copy(entities = it) } } },
                placeholder = if (hidingComposerForLaunch) "" else if (attachments.active && attachments.extra == null) stringResource(R.string.chat_caption_placeholder) else botKeyboard?.placeholder?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chat_message),
                locked = busy || hidingComposerForLaunch, isError = false,
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused && stickerPanelOpen) stickerPanelOpen = false }.onGloballyPositioned { composerLaunch.fieldBounds = it.boundsInWindow() },
                focusRequest = replyFocusRequest, cursor = composerCursor,
                // One entry to Emoji / Stickers / GIF, inside the input and before Record/Send.
                header = if (editing) null else replyHeader,
                embedded = embeddedAttachments,
                trailingAction = if (!editing && !attachments.active) {
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
            }
            Spacer(Modifier.width(8.dp))
            // Keep the same pointer-input node mounted while recording. Removing the button here
            // cancels its gesture coroutine immediately after onStart changes active to true.
            if (recordingActive && recordingLocked) Box(Modifier.size(48.dp).clip(CircleShape)
                .background(palette.accent).clickable(enabled = !busy, role = androidx.compose.ui.semantics.Role.Button) {
                    if (recordingMode == ComposerRecordingMode.Voice) audioNote.finishLocked() else videoNote.sendLocked()
                }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.recorder_send), tint = palette.onAccent, modifier = Modifier.size(ComposerIconSize))
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
            else Box(Modifier.size(ComposerButtonSize)
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
                SendControlFace(busy = busy, canSend = canSend, editing = editing)
            }
        }
        // The counter is outside the control row: showing it must not shift the buttons below the field.
        if (!recordingActive && !editing && attachments.active && attachments.extra == null) {
            val count = attachmentCaptionLength(draft)
            if (showCaptionCounter(count, limits.caption)) {
                Text(
                    "$count / ${limits.caption}",
                    color = if (count > limits.caption) palette.danger else palette.textSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.End).padding(end = 18.dp, bottom = 2.dp)
                )
            }
        }
        if (!recordingActive && overLimit) Text(
            stringResource(R.string.chat_truncated_notice, fieldLimit),
            color = palette.warningText, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
        )
        }
        if (!recordingActive && !editing && !attachments.active) BotReplyKeyboard(chat.id, botKeyboard, conversation, sendGuard, ::requestBotWebApp)
        }
        })
    }
    ChatComposerLaunchOverlay(composerLaunch, chat.id)
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
            failure = video.failure,
            progress = (video.nextIndex + 1).coerceAtMost(video.media.size),
            onSelect = recordingRecovery::select,
            onDelete = { VideoNoteExportCenter.discard(video.chatId, video.sessionName, accountId) },
            onSend = {
                noteLocalSend()
                val started = VideoNoteExportCenter.send(video.chatId, video.sessionName, accountId) { media, targetReply ->
                    conversation.sendMedia(video.chatId, media, "", targetReply, forumTopicId = video.topicId).map { Unit }
                }
                if (started == VideoNoteSendStart.Started && video.nextIndex == 0 && video.replyToMessageId != null && video.replyToMessageId == replyId) clearReply()
            }
        )
    }
    if (videoPreview == null) recordingPreview?.let { preview -> RecordingPreviewDialog(
        mediaList = preview.media,
        selectedIndex = recordingPreviewIndex,
        sentPaths = sentRecordingPaths,
        sending = recordingQueueSending,
        progress = recordingQueueProgress,
        onSelect = recordingRecovery::select,
        onDelete = { preview.media.firstOrNull()?.let { java.io.File(it.path).parentFile?.deleteRecursively() }; sentRecordingPaths = emptySet(); recordingRecovery.dismissPreview() },
        onSend = { sendRecordedMedia(preview.media, preview.destination) }
    ) }
    val viewedRights = selectedMediaId?.let { MediaViewerPolicy.rightsFor(it, viewerRights) }
    // A view-once message is deleted by TDLib right after OpenMessageContent. Keep the already opened item for this
    // viewing session, otherwise the live history update would close the viewer before the user can finish looking.
    var retainedViewOnce by remember(chat.id) { mutableStateOf<ChatMessage?>(null) }
    var openedViewOnceIds by remember(chat.id) { mutableStateOf(emptySet<Long>()) }
    // Фото и видео листаются вместе; остальные вложения открываются по одному.
    // Действия над сообщением есть только у сообщений из загруженной истории: редактирование ищет их в ленте.
    val viewedInChat = selectedMediaId?.let { id -> messages.firstOrNull { it.id == id && it.media != null } }
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
    val windowPages = mediaWindow.messages.mapNotNull { m -> m.media?.let { MediaViewerItem(m.id, it, m.isViewOnce, chat.id, m.text) } }
    // Only an open viewer pages through the whole history; while it is closed, the scan of every update is skipped.
    val viewerOpen = selectedMediaId != null
    val historyPages = remember(messages, viewerOpen) {
        if (!viewerOpen) emptyList<MediaViewerItem>() else messages.mapNotNull { m -> m.media?.takeIf { it.kind in viewerSwipeKinds }?.let { MediaViewerItem(m.id, it, m.isViewOnce, chat.id, m.text) } }
    }
    val historyRange = remember(messages, viewerOpen) {
        if (!viewerOpen || messages.isEmpty()) null
        else messages.minOf { it.id }..messages.maxOf { it.id }
    }
    val liveHistoryIds = remember(messages, viewerOpen) {
        if (!viewerOpen) null else messages.mapTo(HashSet(messages.size)) { it.id }
    }
    val fallbackItem = viewed?.let { m -> m.media?.let { MediaViewerItem(m.id, it, m.isViewOnce, chat.id, m.text) } }
    // Объединяем пересекающиеся страницы окна и живой истории чата с дедупликацией, чтобы не терять крайнее фото.
    val viewerPages = resolveMediaViewerPages(
        windowPages = windowPages,
        historyPages = historyPages,
        selectedId = selectedMediaId,
        fallbackItem = fallbackItem,
        historyMessageIdRange = historyRange,
        liveHistoryIds = liveHistoryIds,
    )
    val viewOnceGate = rememberViewOnceStealthGate(chat.id)
    val viewOnceAsked = viewed?.takeIf { viewOnceGate.needsConsent(chat.id, it.id, it.isViewOnce, conversation.interactionAllowed(InteractionEffect.OpenContent, chat.id)) }
    if (viewOnceAsked != null) viewOnceGate.ConsentDialog(chat.id, viewOnceAsked.id) { selectedMediaId = null }
    if (viewed != null && viewed.media != null && viewOnceAsked == null) MediaViewer(
        pages = viewOnceGate.swipeable(viewerPages, chat.id, viewed.id, conversation.interactionAllowed(InteractionEffect.OpenContent, chat.id)), selectedId = viewed.id, onSelect = { selectedMediaId = it }, originBounds = mediaOrigin(viewed.id),
        observeFile = observeFile, downloadFile = onDownloadFile,
        playbackCoordinator = playbackCoordinator,
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
            onDelete = {
                messageActions.open(viewed.id)
                deleteEveryone = viewedRights?.canDeleteForSelf == false
                deleting = true
                selectedMediaId = null
            }
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
        selectionForwardOpen -> frozenForwardSelection.toList()
        else -> emptyList()
    }
    if (forwardIds.isNotEmpty()) {
        // Scoped to this open picker, not to the lifetime of a chat screen.
        val delivery = rememberSaveable(accountId, chat.id, forwardId, selectionForwardOpen, saver = ForwardDeliveryLedger.saver) { ForwardDeliveryLedger() }
        val pendingIds = forwardIds
        var removeCaption by rememberSaveable(accountId, chat.id, forwardId, selectionForwardOpen) { mutableStateOf(false) }
        var retryBlocked by rememberSaveable(accountId, chat.id, forwardId, selectionForwardOpen) { mutableStateOf(false) }
        val canRemoveCaption = messages.any { message ->
            message.id in pendingIds && message.text.isNotBlank() && message.media?.kind in setOf(
                MediaKind.Photo, MediaKind.Video, MediaKind.Animation, MediaKind.Audio, MediaKind.Document
            )
        }
        val preview = messages.firstOrNull { it.id == pendingIds.singleOrNull() }
            ?.takeIf { !it.isViewOnce && !chat.isSecret && !chat.hasProtectedContent && it.entities.isEmpty() }
            ?.text?.takeIf { it.isNotBlank() }?.take(240)
            ?: stringResource(R.string.share_forward_message_count, pendingIds.size)
        ForwardChatDialog(
            chats(), forwardBusy, forwardError, observeFile, onDownloadFile, asCopy = forwardAsCopy,
            commentLimit = limits.text,
            previewText = preview,
            previewMedia = messages.firstOrNull { it.id == pendingIds.singleOrNull() }
                ?.takeUnless { it.isViewOnce || chat.isSecret || chat.hasProtectedContent }?.media,
            previewAlbum = if (pendingIds.size > 1 && !chat.isSecret && !chat.hasProtectedContent) messages
                .filter { it.id in pendingIds && !it.isViewOnce }.mapNotNull { it.media }
                .filter { !it.hasSpoiler && !it.isSecret && !it.isSecretChat && it.kind in setOf(MediaKind.Photo, MediaKind.Video, MediaKind.Animation) }.take(10)
                else emptyList(),
            onAsCopyChange = { showCopy -> forwardAsCopy = showCopy; if (!showCopy) removeCaption = false },
            removeCaption = removeCaption,
            onRemoveCaptionChange = if (canRemoveCaption) ({ remove: Boolean -> removeCaption = remove; if (remove) forwardAsCopy = true }) else null,
            retryBlocked = retryBlocked,
            recipientRepository = conversation as? com.beykus.beygram.domain.repository.TelegramRepository,
            loadTopics = { conversation.forumTopics(it) },
            // Track confirmed delivery per destination and message; a comment failure must not re-forward content.
            onSend = { targets, comment ->
                forwardBusy = true; forwardError = null
                val failed = mutableListOf<ForwardTarget>()
                var lastError: String? = null
                try {
                    for (target in targets) {
                        val topic = target.topicId.takeIf { it > 0L }
                        if (delivery.isComplete(target)) continue
                        val remaining = delivery.pendingIds(target, pendingIds)
                        if (remaining.isNotEmpty()) {
                            val forwarded = conversation.forwardMessages(
                                chat.id, remaining, target.chatId, asCopy = forwardAsCopy, messageThreadId = topic,
                                options = MessageSendOptions(removeForwardCaption = removeCaption)
                            )
                            forwarded.getOrNull()?.let { delivery.recordForwarded(target, it.forwardedIds) }
                            if (delivery.pendingIds(target, pendingIds).isNotEmpty()) {
                                failed += target
                                if (com.beykus.beygram.share.isShareOutcomeUnknown(forwarded.exceptionOrNull())) retryBlocked = true
                                lastError = forwarded.exceptionOrNull().userMessage(context, R.string.chat_forward_failed)
                                continue
                            }
                        }
                        if (comment.isNotEmpty()) {
                            // The draft belongs to the destination composer, which this forward must not clear.
                            val result = conversation.sendMessage(target.chatId, comment, forumTopicId = target.topicId, clearDraft = false)
                            if (result.isFailure) {
                                failed += target
                                if (com.beykus.beygram.share.isShareOutcomeUnknown(result.exceptionOrNull())) retryBlocked = true
                                lastError = result.exceptionOrNull().userMessage(context, R.string.chat_forward_failed)
                                continue
                            }
                        }
                        delivery.markComplete(target)
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
                            pendingIds.size > 1 -> context.getString(R.string.message_selection_forwarded, pendingIds.size)
                            copied -> context.getString(R.string.message_forwarded_without_author)
                            else -> context.getString(R.string.chat_message_forwarded)
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    // Only failed targets stay selected; confirmed message IDs are excluded from retry.
                    forwardError = context.getString(R.string.forward_failed_chats, failed.size) + lastError?.let { "\n$it" }.orEmpty()
                }
                failed
            },
            onClose = { forwardId = null; selectionForwardOpen = false; frozenForwardSelection = longArrayOf(); forwardError = null; forwardAsCopy = false }
        )
    }
    ChatScreenOverlays {
    contextMessageId?.let { id -> MessageContextDialog(contextChatId, id, conversation, riskGuard, observeFile, onDownloadFile, onClose = { contextMessageId = null }, autoDownloadFile = onAutoDownloadFile, onOpenMessageEntity = onOpenMessageEntity, allowEntityCopy = !chat.hasProtectedContent) }
    playlistTrack?.let { track -> AddToPlaylistSheet(accountId = accountId, track = track, onDismiss = { playlistTrack = null }) }
    if (copyLinks.isNotEmpty()) MessageLinkCopyDialog(
        links = copyLinks,
        onCopy = { value ->
            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.message_action_copy_entity), value))
            Toast.makeText(context, context.getString(R.string.message_entity_copied), Toast.LENGTH_SHORT).show()
            copyLinks = emptyList()
        },
        onDismiss = { copyLinks = emptyList() }
    )
    if (pinConfirm && actionMessage != null) BeyGramDialog(
        onDismissRequest = { pinConfirm = false },
        title = stringResource(R.string.chat_pin_channel_title),
        message = stringResource(R.string.chat_pin_channel_message),
        confirmText = stringResource(R.string.message_action_pin),
        onConfirm = { actionMessage?.let { setPinned(it, true) } },
        dismissText = stringResource(R.string.common_cancel),
        onDismiss = { pinConfirm = false }
    )
    paidTextConfirmation?.let { confirmation ->
        BeyGramDialog(
            onDismissRequest = { paidTextConfirmation = null },
            title = stringResource(R.string.paid_text_title),
            message = stringResource(
                R.string.paid_text_confirm,
                formatStarAmount(confirmation.price),
                formatStarAmount(confirmation.balance.starCount, confirmation.balance.nanostarCount)
            ),
            confirmText = stringResource(R.string.common_send),
            confirmEnabled = paidTextCanConfirm(confirmation.price, confirmation.balance),
            onConfirm = {
                // Ignore a second callback from a dialog that already consumed its confirmation.
                if (paidTextConfirmation === confirmation) {
                    paidTextConfirmation = null
                    if (paidTextDestination != confirmation.destination || !canPostHere ||
                        composerDraft != confirmation.draft || attachments.active || pendingSticker != null || editId != null) {
                        error = context.getString(R.string.paid_text_draft_changed)
                    } else if (!conversation.stealthHoldsSends() && !textSendQueue.isComposerWriting(textSendScopeKey)) {
                        val outgoing = composerLaunchPayload(confirmation.draft.text, confirmation.draft.entities, limits.text)
                        val forceReply = botKeyboard?.takeIf { it.forceReply }?.messageId
                        // Process-scoped composer mutex, but no queued/persisted retryable payment authorization.
                        textSendQueue.launchComposerWrite(textSendScopeKey) {
                            val result = conversation.sendPaidTextMessage(
                                confirmation.destination.chatId, outgoing.text, confirmation.draft.replyToMessageId, outgoing.entities,
                                confirmation.price, confirmation.disableNotification,
                                confirmation.destination.forumTopicId, confirmation.disableLinkPreview
                            )
                            if (result.isSuccess) {
                                updateDraft { if (it == confirmation.draft) MessageDraft() else it }
                                forceReply?.let { conversation.dismissBotKeyboard(chat.id, it) }
                            } else {
                                val reason = result.exceptionOrNull()?.userMessage(context, R.string.err_chat_send_failed).orEmpty()
                                Toast.makeText(
                                    context.applicationContext,
                                    reason + "\n" + context.getString(R.string.paid_text_uncertain),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } else {
                        error = context.getString(R.string.stealth_schedule_unavailable_paid)
                    }
                }
            },
            dismissText = stringResource(R.string.common_cancel),
            onDismiss = { paidTextConfirmation = null }
        )
    }
    sendGuard.Dialog()
    riskGuard.Dialog()
    attachments.stealthFallback.Dialog()
    failedSendTarget?.let { target ->
        FailedSendDetailsDialog(
            target = target,
            onDismiss = { failedSendTarget = null },
            onRetry = {
                val attemptId = target.localAttemptId
                if (attemptId != null) {
                    retryLocalTextAttempt(attemptId)
                } else {
                    retryFailedMessages(listOf(target.message.id))
                }
            },
            onDiscard = {
                val attemptId = target.localAttemptId
                if (attemptId != null) {
                    textSendQueue.launchMutation {
                        try {
                            textSendQueue.discard(textSendScopeKey, attemptId)
                        } catch (_: TextSendStorageException) {
                            error = queueStorageFailureText
                        }
                    }
                } else {
                    scope.launch {
                        conversation.deleteMessages(chat.id, listOf(target.message.id), DeleteMessageScope.ForSelf)
                    }
                }
            }
        )
    }
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
        onClose = { scheduledMessagesOpen = false },
        onCompose = if (canPostHere) ({
            scheduledMessagesOpen = false
            replyFocusRequest++
        }) else null
    )
    if (pinnedListOpen) PinnedMessagesScreen(
        state = pinnedState,
        conversation = conversation,
        chat = chat,
        chatTitle = forumTopicTitle?.takeIf { it.isNotBlank() }?.let { "${chat.title} · $it" } ?: chat.title,
        members = info?.memberPreview.orEmpty(),
        senderChats = chats(),
        observeFile = observeFile,
        downloadFile = onDownloadFile,
        barHidden = pinnedBarHidden,
        previewSource = pinnedPreviewSource,
        livePreviewSource = { livePinnedPreview },
        headerSource = pinnedHeaderSource,
        liveHeaderSource = { livePinnedHeader },
        onRestoreBar = { chatOverrides.setPinnedBarHidden(chat.id, false) },
        onOpenMessage = { message -> pinnedListOpen = false; pinnedPreviewSource = null; pinnedHeaderSource = null; jumpToPinned(message) },
        onClose = { pinnedListOpen = false; pinnedPreviewSource = null; pinnedHeaderSource = null }
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
    }
    pendingWebApp?.let { request ->
        BeyGramDialog(
            onDismissRequest = { pendingWebApp = null },
            title = stringResource(R.string.chat_webapp_consent_title),
            message = stringResource(R.string.chat_webapp_consent),
            confirmText = stringResource(R.string.chat_webapp_open_action),
            onConfirm = { pendingWebApp = null; launchBotWebApp(request) },
            dismissText = stringResource(R.string.common_cancel),
            onDismiss = { pendingWebApp = null }
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
 * The return-to-latest control: a 48dp round floating button with an arrow and, when there are unread messages, the
 * chat's unread count on its edge. It sits over the message list, so the composer and the keyboard never cover it.
 */
@Composable
private fun JumpToLatestButton(unread: Int, onClick: () -> Unit) {
    val palette = beyGramPalette()
    val label = stringResource(R.string.chat_jump_to_latest)
    Box(
        Modifier.size(48.dp).semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.fillMaxSize().let { m ->
                // Liquid Glass: a translucent disc with a hairline rim instead of a solid disc with a shadow.
                if (LocalDesignSystem.current.usesGlassPanels) m.clip(CircleShape).background(palette.surface.copy(alpha = 0.72f))
                    .border(0.5.dp, palette.textSecondary.copy(alpha = 0.35f), CircleShape)
                else m.shadow(elevation = 4.dp, shape = CircleShape).clip(CircleShape).background(palette.surface)
            }.clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.ArrowDownward, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
        }
        // The badge sits outside the clipped circle so the corner offset does not crop it.
        if (unread > 0) Box(
            Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)
                .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                .clip(CircleShape).background(palette.accent).padding(horizontal = 4.dp)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center
        ) {
            Text(if (unread > 99) "99+" else unread.toString(), color = palette.onAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/**
 * The round send control: a checkmark while editing, a spinner while sending. Material 3 fills it with the tonal accent
 * container; the other styles keep the solid accent, and Material 2 keeps its rounded square.
 */
@Composable
private fun SendControlFace(busy: Boolean, canSend: Boolean, editing: Boolean) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val fill = if (design.composerTonal) palette.accentSoft else palette.accent
    val ink = if (design.composerTonal) palette.accentInk else palette.onAccent
    Box(Modifier.size(ComposerButtonSize).clip(if (design.squareSendButton) RoundedCornerShape(design.shapes.button) else CircleShape).background(fill.copy(alpha = if (canSend || busy) 1f else 0.4f)), contentAlignment = Alignment.Center) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = ink, strokeWidth = 2.dp)
        else Icon(if (editing) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.Send, contentDescription = null,
            tint = ink.copy(alpha = if (canSend) 1f else 0.8f), modifier = Modifier.size(ComposerIconSize))
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

@Composable
private fun ChatScreenOverlays(content: @Composable () -> Unit) {
    content()
}

/** Keeps the attachment strip and the unified sticker panel out of ChatScreen's own bytecode-heavy composer lambda. */
@Composable
private fun ChatComposerPanel(
    stickerPanelOpen: Boolean,
    embedded: Boolean,
    attachments: AttachmentState,
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
            observeFile = observeFile,
            onDownloadFile = onDownloadFile,
            allowViewOnce = allowViewOnce,
            embedded = embedded
        )
    }
}

@Composable
private fun ReadOnlyChatBar(message: String) {
    val palette = beyGramPalette()
    Box(Modifier.fillMaxWidth().chatChromeBackground(palette.surface).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
        Text(message, color = palette.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
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
