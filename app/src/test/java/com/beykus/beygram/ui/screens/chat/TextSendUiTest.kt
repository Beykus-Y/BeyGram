package com.beykus.beygram.ui.screens.chat

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.robolectric.RuntimeEnvironment
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.MessageAnimationConfig
import com.beykus.beygram.data.appearance.MessageAnimationDirection
import com.beykus.beygram.data.appearance.MessageAnimationPreset
import com.beykus.beygram.data.presence.LastSeenStyle
import com.beykus.beygram.data.settings.ChatOverridesStore
import com.beykus.beygram.data.settings.TranslationSettingsStore
import com.beykus.beygram.data.media.VideoMessageSettingsStore
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.AnimationItem
import com.beykus.beygram.domain.model.AnimationPage
import com.beykus.beygram.domain.model.BotCallbackAnswer
import com.beykus.beygram.domain.model.BotCommandMenu
import com.beykus.beygram.domain.model.BotKeyboard
import com.beykus.beygram.domain.model.BotWebAppLaunch
import com.beykus.beygram.domain.model.BotWebAppSource
import com.beykus.beygram.domain.model.BotWebAppTheme
import com.beykus.beygram.domain.model.ChatActivity
import com.beykus.beygram.domain.model.ChatExportProgress
import com.beykus.beygram.domain.model.ChatExportRequest
import com.beykus.beygram.domain.model.ChatExportSummary
import com.beykus.beygram.domain.model.ChatExportTarget
import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatNotificationPreferences
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ChatSearchFilter
import com.beykus.beygram.domain.model.ContactPreview
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.DeleteMessageScope
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.domain.model.ForwardedMessages
import com.beykus.beygram.domain.model.GeoPoint
import com.beykus.beygram.domain.model.GlobalMessagePage
import com.beykus.beygram.domain.model.GlobalSearchFilter
import com.beykus.beygram.domain.model.GlobalSearchPage
import com.beykus.beygram.domain.model.InlineBotResults
import com.beykus.beygram.domain.model.LiveMessageArrival
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MediaPosition
import com.beykus.beygram.domain.model.MessageCapabilities
import com.beykus.beygram.domain.model.MessageContext
import com.beykus.beygram.domain.model.MessageDraft
import com.beykus.beygram.domain.model.MessageEffect
import com.beykus.beygram.domain.model.MessageLimits
import com.beykus.beygram.domain.model.MessageReaction
import com.beykus.beygram.domain.model.MessageReportReason
import com.beykus.beygram.domain.model.MessageSearchPage
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.MessageThread
import com.beykus.beygram.domain.model.MessageThreadPage
import com.beykus.beygram.domain.model.MessageTranslation
import com.beykus.beygram.domain.model.MusicBotTrack
import com.beykus.beygram.domain.model.NotificationSoundOption
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.model.OutgoingPoll
import com.beykus.beygram.domain.model.PinnedMessagePage
import com.beykus.beygram.domain.model.PollVotersPage
import com.beykus.beygram.domain.model.PublicChatOpenResult
import com.beykus.beygram.domain.model.ReactionOption
import com.beykus.beygram.domain.model.SavedMusicBotTrack
import com.beykus.beygram.domain.model.SecretChatDetails
import com.beykus.beygram.domain.model.SharedMediaKind
import com.beykus.beygram.domain.model.SpeechRecognitionTrial
import com.beykus.beygram.domain.model.StickerItem
import com.beykus.beygram.domain.model.StickerSearchPage
import com.beykus.beygram.domain.model.StickerSetContent
import com.beykus.beygram.domain.model.StickerSetSummary
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi", application = Application::class)
class TextSendUiTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var app: Context
    private lateinit var testScope: CoroutineScope

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        PendingTextSendSessions.resetForTests()
        app.filesDir.listFiles()?.filter { it.name.startsWith("pending-text") }?.forEach { it.deleteRecursively() }
        app.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("pending-text") }?.forEach { it.deleteRecursively() }
    }

    @After
    fun tearDown() {
        PendingTextSendSessions.resetForTests()
        testScope.cancel()
        app.filesDir.listFiles()?.filter { it.name.startsWith("pending-text") }?.forEach { it.deleteRecursively() }
        app.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("pending-text") }?.forEach { it.deleteRecursively() }
    }

    @Test
    fun chatScreen_rapidSendTapsQueueAndDeliverAllMessagesWithoutDropping() {
        val repo = FakeConversationRepository()
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        val gates = ArrayDeque(listOf(firstGate, secondGate))
        val sentTexts = CopyOnWriteArrayList<String>()
        repo.onSendMessage = { _, text, _, _, _ ->
            sentTexts += text
            gates.removeFirst().await()
            Result.success(Unit)
        }

        renderChatScreen(repo = repo)

        val sendDesc = app.getString(R.string.common_send)
        compose.onNode(hasSetTextAction()).performTextInput("first")
        compose.onNodeWithContentDescription(sendDesc).performClick()
        compose.waitUntil(3_000) {
            sentTexts.size == 1 && compose.onAllNodes(hasSetTextAction() and hasText("first")).fetchSemanticsNodes().isEmpty()
        }

        compose.onNode(hasSetTextAction()).performTextInput("second")
        compose.onNodeWithContentDescription(sendDesc).performClick()

        compose.onNodeWithText("first").assertIsDisplayed()
        compose.onNodeWithText("second").assertIsDisplayed()

        firstGate.complete(Unit)
        compose.waitUntil(3_000) { sentTexts.size == 2 }
        assertEquals(listOf("first", "second"), sentTexts.toList())
        secondGate.complete(Unit)
    }

    @Test
    fun chatScreen_typingDuringSlowQueueIoPreservesNewText() {
        val accountId = "acc-slow-io"
        val saveStarted = CountDownLatch(1)
        val allowSaveFinish = CountDownLatch(1)
        val slowStore = object : TextSendAttemptStore {
            var rows = emptyList<TextSendAttempt>()
            override fun load(): List<TextSendAttempt> = rows
            override fun save(attempts: List<TextSendAttempt>) {
                rows = attempts.toList()
                if (attempts.isNotEmpty()) {
                    saveStarted.countDown()
                    allowSaveFinish.await(5, TimeUnit.SECONDS)
                }
            }
        }
        val queue = TextSendQueue(testScope, store = slowStore, storageDispatcher = Dispatchers.IO)
        PendingTextSendSessions.installQueueForTests(accountId, queue)

        val repo = FakeConversationRepository()
        val sendGate = CompletableDeferred<Unit>()
        repo.onSendMessage = { _, _, _, _, _ ->
            sendGate.await()
            Result.success(Unit)
        }

        renderChatScreen(repo = repo, accountId = accountId)

        val sendDesc = app.getString(R.string.common_send)
        compose.onNode(hasSetTextAction()).performTextInput("queued")
        compose.onNodeWithContentDescription(sendDesc).performClick()
        assertTrue(saveStarted.await(5, TimeUnit.SECONDS))

        // Composer is already cleared while disk save is in flight; typing new text must survive IO completion.
        compose.onNode(hasSetTextAction()).performTextInput("typing-while-saving")
        allowSaveFinish.countDown()
        compose.waitForIdle()

        compose.onNodeWithText("typing-while-saving").assertIsDisplayed()
        sendGate.complete(Unit)
    }

    @Test
    fun chatScreen_forumTopicRetryBaselineReadsForumTopicHistory() {
        val accountId = "acc-forum"
        val chatId = 100L
        val topicId = 55L
        val scopeKey = TextSendScopeKey(accountId, chatId, topicId)
        val queue = TextSendQueue(testScope, storageDispatcher = Dispatchers.IO)
        runBlocking {
            queue.enqueue(
                scopeKey = scopeKey,
                text = "topic failed",
                entities = emptyList(),
                replyToMessageId = null,
                replyPreview = null,
                options = MessageSendOptions(),
                baselineSequence = 1L,
                timeLabel = "10:00",
                execute = { Result.failure(IllegalStateException("offline")) },
                formatError = { "offline" }
            )
        }
        PendingTextSendSessions.installQueueForTests(accountId, queue)

        val repo = FakeConversationRepository()
        // Main chat history stays at sequence 0; forum topic history is at sequence 9.
        repo.topicHistory(chatId, topicId).value = ChatHistory(liveArrivalSequence = 9L)
        val sendGate = CompletableDeferred<Unit>()
        var capturedTopicId = -1L
        repo.onSendMessage = { _, _, _, forumTopicId, _ ->
            capturedTopicId = forumTopicId
            sendGate.await()
            Result.success(Unit)
        }

        renderChatScreen(repo = repo, accountId = accountId, chatId = chatId, forumTopicId = topicId)

        val retryText = " · " + app.getString(R.string.chat_retry_send)
        compose.waitUntil(3_000) {
            compose.onAllNodesWithText(retryText).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(retryText).performClick()
        compose.waitUntil(3_000) { capturedTopicId == topicId }

        assertEquals(9L, queue.current(scopeKey).first().baselineSequence)
        repo.topicHistory(chatId, topicId).value = ChatHistory(
            messages = listOf(
                ChatMessage(
                    id = 9001L,
                    chatId = chatId,
                    text = "topic failed",
                    time = "10:01",
                    isOutgoing = true,
                    forumTopicId = topicId
                )
            ),
            liveArrivals = listOf(LiveMessageArrival(sequence = 10L, messageId = 9001L)),
            liveArrivalSequence = 10L
        )
        sendGate.complete(Unit)
        compose.waitUntil(3_000) {
            queue.current(scopeKey).isEmpty() && compose.onAllNodesWithText("topic failed").fetchSemanticsNodes().size == 1
        }
        assertEquals(1, compose.onAllNodesWithText("topic failed").fetchSemanticsNodes().size)
    }

    @Test
    fun chatScreen_composerLaunchWithQueuedTextSendDoesNotDuplicateBubble() {
        val repo = FakeConversationRepository()
        val chatId = 200L
        val sendGate = CompletableDeferred<Unit>()
        repo.onSendMessage = { _, _, _, _, _ ->
            sendGate.await()
            Result.success(Unit)
        }

        renderChatScreen(
            repo = repo,
            chatId = chatId,
            messageAnimations = MessageAnimationConfig(
                enabled = true,
                linkedDirections = true,
                shared = MessageAnimationDirection(MessageAnimationPreset.ComposerLaunch)
            )
        )

        val sendDesc = app.getString(R.string.common_send)
        compose.onNode(hasSetTextAction()).performTextInput("launch-once")
        compose.onNodeWithContentDescription(sendDesc).performClick()

        // After overlay finishes, only the single local row is rendered.
        compose.mainClock.advanceTimeBy(600)
        compose.waitUntil(3_000) {
            compose.onAllNodesWithText("launch-once").fetchSemanticsNodes().size == 1
        }

        // Emitting TDLib confirmation while local send is still finishing must not produce a second bubble.
        repo.chatHistory(chatId).value = ChatHistory(
            messages = listOf(
                ChatMessage(
                    id = 777L,
                    chatId = chatId,
                    text = "launch-once",
                    time = "10:02",
                    isOutgoing = true
                )
            ),
            liveArrivals = listOf(LiveMessageArrival(sequence = 1L, messageId = 777L)),
            liveArrivalSequence = 1L
        )
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodesWithText("launch-once").fetchSemanticsNodes().size)

        sendGate.complete(Unit)
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodesWithText("launch-once").fetchSemanticsNodes().size)
    }

    @Test
    fun messageThreadScreen_deduplicatesConfirmedCommentAndPreservesTypingAndShowsTdlibFailureDetails() {
        val repo = FakeConversationRepository()
        val channelChatId = 300L
        val discussionChatId = 400L
        val postId = 10L
        val sendGate = CompletableDeferred<Unit>()
        var deletedMessageIds = emptyList<Long>()
        val failedComment = ChatMessage(
            id = 500L,
            chatId = discussionChatId,
            text = "tdlib failed comment",
            time = "10:05",
            isOutgoing = true,
            sendState = MessageSendState.Failed,
            sendError = "MESSAGE_TOO_LONG",
            messageThreadId = postId
        )
        repo.threadResult = MessageThread(
            channelChatId = channelChatId,
            channelMessageId = postId,
            discussionChatId = discussionChatId,
            threadId = postId,
            discussionTitle = "Discussion",
            canReply = true,
            messages = listOf(failedComment)
        )
        repo.onSendThreadReply = { _, _, text, _, _, _ ->
            repo.chatHistory(discussionChatId).value = ChatHistory(
                messages = listOf(
                    failedComment,
                    ChatMessage(
                        id = 501L,
                        chatId = discussionChatId,
                        text = text,
                        time = "10:06",
                        isOutgoing = true,
                        messageThreadId = postId
                    )
                ),
                liveArrivals = listOf(LiveMessageArrival(sequence = 1L, messageId = 501L)),
                liveArrivalSequence = 1L
            )
            sendGate.await()
            Result.success(
                ChatMessage(
                    id = 501L,
                    chatId = discussionChatId,
                    text = text,
                    time = "10:06",
                    isOutgoing = true,
                    messageThreadId = postId
                )
            )
        }
        repo.onDeleteMessages = { _, ids, _ ->
            deletedMessageIds = ids
            Result.success(Unit)
        }

        var draftText by mutableStateOf("")
        var draftEntities by mutableStateOf<List<MessageTextEntity>>(emptyList())
        var draftReplyId by mutableStateOf(0L)
        compose.setContent {
            BeyGramTheme {
                MessageThreadScreen(
                    accountId = "acc-thread",
                    channelChatId = channelChatId,
                    channelTitle = "Channel",
                    postId = postId,
                    initialPost = ChatMessage(
                        id = postId,
                        chatId = channelChatId,
                        text = "Root post",
                        time = "09:00",
                        isOutgoing = false,
                        isChannelPost = true
                    ),
                    conversation = repo,
                    observeFile = { MutableStateFlow(MediaFileState(it)) },
                    downloadFile = {},
                    initialDraftText = draftText,
                    initialDraftEntities = draftEntities,
                    initialReplyToId = draftReplyId,
                    onDraftChange = { t, e, r ->
                        draftText = t
                        draftEntities = e
                        draftReplyId = r
                    },
                    onDraftCommitted = { _, _, _ ->
                        draftText = ""
                        draftEntities = emptyList()
                        draftReplyId = 0L
                    },
                    onBack = {},
                    onOpenProfile = {}
                )
            }
        }

        compose.waitUntil(3_000) {
            compose.onAllNodesWithText("tdlib failed comment").fetchSemanticsNodes().isNotEmpty()
        }

        // Open TDLib failure details dialog on failed comment, verify error reason, and delete it.
        val detailsText = " · " + app.getString(R.string.chat_send_failure_details)
        compose.onNodeWithText(detailsText).performClick()
        compose.onNodeWithText("MESSAGE_TOO_LONG").assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.chat_send_failure_discard)).performClick()
        compose.waitUntil(3_000) { deletedMessageIds == listOf(500L) }

        // Type character-by-character while onDraftChange round-trips back to initialDraftText.
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("hello")
        field.performTextInput(" world")
        assertEquals("hello world", draftText)

        compose.onNodeWithContentDescription(app.getString(R.string.common_send)).performClick()
        compose.waitUntil(3_000) {
            compose.onAllNodesWithText("hello world").fetchSemanticsNodes().size == 1
        }
        sendGate.complete(Unit)
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodesWithText("hello world").fetchSemanticsNodes().size)
    }

    @Test
    fun bubbleMeta_wrapsRetryAndDetailsCleanlyWhenNarrowAndLargeFont() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.6f)) {
                BeyGramTheme {
                    Box(Modifier.width(120.dp)) {
                        BubbleMeta(
                            message = ChatMessage(
                                id = -1L,
                                chatId = 1L,
                                text = "failed",
                                time = "12:34",
                                isOutgoing = true,
                                sendState = MessageSendState.Failed
                            ),
                            isRead = false,
                            retryAvailable = true,
                            overlay = false,
                            onRetry = {},
                            inline = false,
                            onShowFailureDetails = {}
                        )
                    }
                }
            }
        }
        val retryNode = compose.onNodeWithText(" · " + app.getString(R.string.chat_retry_send))
        val detailsNode = compose.onNodeWithText(" · " + app.getString(R.string.chat_send_failure_details))
        retryNode.assertIsDisplayed()
        detailsNode.assertIsDisplayed()
        val retryBounds = retryNode.fetchSemanticsNode().boundsInRoot
        val detailsBounds = detailsNode.fetchSemanticsNode().boundsInRoot
        assertTrue(retryBounds.width > 0f && detailsBounds.width > 0f)
        val nonOverlapping = detailsBounds.top >= retryBounds.bottom - 1f || detailsBounds.left >= retryBounds.right - 1f
        assertTrue("Expected wrapped metadata without horizontal overlap: retry=$retryBounds details=$detailsBounds", nonOverlapping)
    }

    @Test
    fun pendingTextSendSessions_asyncQuarantineAndSignedOutDoNotLeakSnapshot() {
        val accountId = "acc-async-purge"
        val legacyFile = File(app.filesDir, PendingTextSendFiles.name(accountId))
        val activeFile = File(app.noBackupFilesDir, PendingTextSendFiles.name(accountId))
        FileTextSendAttemptStore(legacyFile).save(
            listOf(
                TextSendAttempt(
                    attemptId = 1L,
                    scope = TextSendScopeKey(accountId, 1L, 0L),
                    text = "stale-legacy",
                    entities = emptyList(),
                    replyToMessageId = null,
                    replyPreview = null,
                    options = MessageSendOptions(),
                    baselineSequence = 0L,
                    timeLabel = "09:00"
                )
            )
        )
        FileTextSendAttemptStore(activeFile).save(
            listOf(
                TextSendAttempt(
                    attemptId = 2L,
                    scope = TextSendScopeKey(accountId, 1L, 0L),
                    text = "stale-active",
                    entities = emptyList(),
                    replyToMessageId = null,
                    replyPreview = null,
                    options = MessageSendOptions(),
                    baselineSequence = 0L,
                    timeLabel = "09:01"
                )
            )
        )

        PendingTextSendSessions.scheduleStartupQuarantine(app)
        PendingTextSendSessions.signedOut(app, accountId)
        PendingTextSendSessions.authorized(app, accountId)
        val queue = PendingTextSendSessions.queue(app, accountId)
        runBlocking { queue.awaitInitializedForTests() }

        assertTrue(queue.current(TextSendScopeKey(accountId, 1L, 0L)).isEmpty())
        assertFalse(activeFile.exists())
        assertFalse(File(app.noBackupFilesDir, "${PendingTextSendFiles.QUARANTINE}/${PendingTextSendFiles.name(accountId)}").exists())
    }

    private fun renderChatScreen(
        repo: FakeConversationRepository,
        accountId: String = "acc-test",
        chatId: Long = 100L,
        forumTopicId: Long = 0L,
        messageAnimations: MessageAnimationConfig = MessageAnimationConfig(enabled = false)
    ) {
        val historyFlow = if (forumTopicId > 0L) repo.topicHistory(chatId, forumTopicId) else repo.chatHistory(chatId)
        val chat = ChatPreview(
            id = chatId,
            title = "Test Chat",
            initials = "TC",
            lastMessage = UiText.Raw(""),
            lastMessageTime = "",
            unreadCount = 0,
            avatarColorArgb = 0xFF336699,
            canPost = true
        )
        compose.setContent {
            val history by historyFlow.collectAsState()
            BeyGramTheme {
                ChatScreen(
                    chat = chat,
                    info = null,
                    lastSeenStyle = LastSeenStyle.Relative,
                    history = history,
                    conversation = repo,
                    chats = listOf(chat),
                    observeFile = { MutableStateFlow(MediaFileState(it)) },
                    onDownloadFile = {},
                    onBack = {},
                    onChatInfo = {},
                    connectionState = TelegramConnectionState.Connected,
                    recordingSettings = VideoMessageSettingsStore(app),
                    translationSettings = TranslationSettingsStore(app, accountId),
                    chatOverrides = ChatOverridesStore(app, accountId),
                    forumTopicId = forumTopicId,
                    messageAnimations = messageAnimations,
                    accountId = accountId
                )
            }
        }
    }

    private class FakeConversationRepository : ConversationRepository {
        private val chatHistories = HashMap<Long, MutableStateFlow<ChatHistory>>()
        private val topicHistories = HashMap<Pair<Long, Long>, MutableStateFlow<ChatHistory>>()
        private val drafts = HashMap<Long, MutableStateFlow<MessageDraft>>()
        private val topicDrafts = HashMap<Pair<Long, Long>, MutableStateFlow<MessageDraft>>()

        var onSendMessage: suspend (Long, String, Long?, Long, MessageSendOptions) -> Result<Unit> = { _, _, _, _, _ ->
            Result.success(Unit)
        }
        var threadResult: MessageThread = MessageThread(0L, 0L, 0L, 0L)
        var onSendThreadReply: suspend (Long, Long, String, List<MessageTextEntity>, Long?, Boolean) -> Result<ChatMessage?> = { _, _, text, _, _, _ ->
            Result.success(ChatMessage(id = 1L, chatId = 0L, text = text, time = "12:00", isOutgoing = true))
        }
        var onDeleteMessages: suspend (Long, List<Long>, DeleteMessageScope) -> Result<Unit> = { _, _, _ ->
            Result.success(Unit)
        }

        fun chatHistory(chatId: Long): MutableStateFlow<ChatHistory> =
            chatHistories.getOrPut(chatId) { MutableStateFlow(ChatHistory(hasOlderMessages = false)) }

        fun topicHistory(chatId: Long, topicId: Long): MutableStateFlow<ChatHistory> =
            topicHistories.getOrPut(chatId to topicId) { MutableStateFlow(ChatHistory(hasOlderMessages = false)) }

        override fun observePinnedMessagesVersion(chatId: Long): StateFlow<Long> = MutableStateFlow(0L)
        override suspend fun pinnedMessages(chatId: Long, fromMessageId: Long, forumTopicId: Long): Result<PinnedMessagePage> =
            Result.success(PinnedMessagePage(emptyList(), 0, 0L))
        override suspend fun setMessagePinned(chatId: Long, messageId: Long, pinned: Boolean): Result<Unit> = Result.success(Unit)
        override fun observeBotKeyboard(chatId: Long): StateFlow<BotKeyboard?> = MutableStateFlow(null)
        override suspend fun dismissBotKeyboard(chatId: Long, messageId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun botCallback(chatId: Long, messageId: Long, data: List<Byte>): Result<BotCallbackAnswer> =
            Result.failure(UnsupportedOperationException())
        override suspend fun botCommandMenu(chatId: Long): Result<BotCommandMenu?> = Result.success(null)
        override suspend fun inlineBotQuery(chatId: Long, username: String, query: String, offset: String): Result<InlineBotResults?> =
            Result.success(null)
        override suspend fun musicBotSearch(query: String, offset: String, contextChatId: Long): Result<InlineBotResults?> =
            Result.success(null)
        override suspend fun musicBotTrack(queryId: Long, resultId: String): Result<MusicBotTrack?> = Result.success(null)
        override suspend fun saveMusicBotTrack(queryId: Long, resultId: String): Result<SavedMusicBotTrack?> = Result.success(null)
        override suspend fun sendInlineBotResult(
            chatId: Long,
            queryId: Long,
            resultId: String,
            replyToMessageId: Long?,
            forumTopicId: Long,
            options: MessageSendOptions
        ): Result<Unit> = Result.success(Unit)
        override suspend fun openBotWebApp(
            chatId: Long,
            botUserId: Long,
            url: String,
            source: BotWebAppSource,
            forumTopicId: Long,
            replyToMessageId: Long?,
            theme: BotWebAppTheme,
            buttonText: String
        ): Result<BotWebAppLaunch> = Result.failure(UnsupportedOperationException())
        override suspend fun sendBotWebAppData(botUserId: Long, buttonText: String, data: String): Result<Unit> = Result.success(Unit)
        override fun closeBotWebApp(launchId: Long) {}
        override val messageLimits: StateFlow<MessageLimits> = MutableStateFlow(MessageLimits())
        override fun observeMessageDraft(chatId: Long): StateFlow<MessageDraft> =
            drafts.getOrPut(chatId) { MutableStateFlow(MessageDraft()) }
        override fun observeForumTopicDraft(chatId: Long, forumTopicId: Long): StateFlow<MessageDraft> =
            topicDrafts.getOrPut(chatId to forumTopicId) { MutableStateFlow(MessageDraft()) }
        override fun updateForumTopicDraft(chatId: Long, forumTopicId: Long, transform: (MessageDraft) -> MessageDraft) {
            val flow = topicDrafts.getOrPut(chatId to forumTopicId) { MutableStateFlow(MessageDraft()) }
            flow.value = transform(flow.value)
        }
        override fun updateMessageDraft(chatId: Long, transform: (MessageDraft) -> MessageDraft) {
            val flow = drafts.getOrPut(chatId) { MutableStateFlow(MessageDraft()) }
            flow.value = transform(flow.value)
        }
        override fun observeChatHistory(chatId: Long): StateFlow<ChatHistory> = chatHistory(chatId)
        override fun observeForumTopicHistory(chatId: Long, forumTopicId: Long): StateFlow<ChatHistory> = topicHistory(chatId, forumTopicId)
        override fun loadForumTopicHistory(chatId: Long, forumTopicId: Long) {}
        override fun loadOlderForumTopicMessages(chatId: Long, forumTopicId: Long) {}
        override suspend fun latestForumTopicPreview(chatId: Long, forumTopicId: Long): Result<String?> = Result.success(null)
        override suspend fun loadForumTopicMessagesAround(chatId: Long, forumTopicId: Long, messageId: Long): Result<Boolean> = Result.success(true)
        override suspend fun linkedDiscussionChatId(chatId: Long): Result<Long> = Result.success(0L)
        override suspend fun loadMessageThread(chatId: Long, messageId: Long): Result<MessageThread> = Result.success(threadResult)
        override suspend fun loadMessageThreadComments(discussionChatId: Long, threadId: Long, fromMessageId: Long): Result<MessageThreadPage> =
            Result.success(MessageThreadPage(emptyList(), 0L, false))
        override suspend fun sendThreadReply(
            discussionChatId: Long,
            threadId: Long,
            text: String,
            entities: List<MessageTextEntity>,
            replyToMessageId: Long?,
            sendNowConsent: Boolean
        ): Result<ChatMessage?> = onSendThreadReply(discussionChatId, threadId, text, entities, replyToMessageId, sendNowConsent)
        override fun observeDeletedMessages(chatId: Long): StateFlow<Set<Long>> = MutableStateFlow(emptySet())
        override fun openChat(chatId: Long) {}
        override fun closeChat(chatId: Long) {}
        override fun loadOlderMessages(chatId: Long) {}
        override suspend fun loadMessagesAround(chatId: Long, messageId: Long): Result<Boolean> = Result.success(true)
        override suspend fun newerMessages(chatId: Long, forumTopicId: Long, afterMessageId: Long): Result<List<ChatMessage>> =
            Result.success(emptyList())
        override suspend fun peekMessages(chatId: Long): Result<List<ChatMessage>> = Result.success(emptyList())
        override fun loadLatestMessages(chatId: Long) {}
        override suspend fun sendMessage(
            chatId: Long,
            text: String,
            replyToMessageId: Long?,
            entities: List<MessageTextEntity>,
            forumTopicId: Long,
            clearDraft: Boolean,
            options: MessageSendOptions
        ): Result<Unit> = onSendMessage(chatId, text, replyToMessageId, forumTopicId, options)
        override suspend fun isForumChat(chatId: Long): Result<Boolean> = Result.success(false)
        override suspend fun forumTopics(chatId: Long, onPage: (List<ForumTopic>) -> Unit): Result<List<ForumTopic>?> = Result.success(null)
        override suspend fun canCreateForumTopic(chatId: Long): Result<Boolean> = Result.success(false)
        override suspend fun createForumTopic(chatId: Long, name: String): Result<Long> = Result.failure(UnsupportedOperationException())
        override suspend fun renameForumTopic(chatId: Long, forumTopicId: Long, name: String): Result<Unit> = Result.success(Unit)
        override suspend fun setForumTopicClosed(chatId: Long, forumTopicId: Long, closed: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun setForumTopicPinned(chatId: Long, forumTopicId: Long, pinned: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun deleteForumTopic(chatId: Long, forumTopicId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun sendMedia(chatId: Long, media: OutgoingMedia, caption: String, replyToMessageId: Long?, entities: List<MessageTextEntity>, forumTopicId: Long, options: MessageSendOptions): Result<Unit> = Result.success(Unit)
        override suspend fun sendMediaAlbum(chatId: Long, media: List<OutgoingMedia>, caption: String, replyToMessageId: Long?, entities: List<MessageTextEntity>, forumTopicId: Long, options: MessageSendOptions): Result<Unit> = Result.success(Unit)
        override suspend fun sendPoll(chatId: Long, poll: OutgoingPoll, replyToMessageId: Long?, forumTopicId: Long, options: MessageSendOptions): Result<Unit> = Result.success(Unit)
        override suspend fun sendLocation(chatId: Long, point: GeoPoint, live: Boolean, replyToMessageId: Long?, forumTopicId: Long, options: MessageSendOptions): Result<Unit> = Result.success(Unit)
        override suspend fun sendContactCard(chatId: Long, userId: Long, replyToMessageId: Long?, forumTopicId: Long, options: MessageSendOptions): Result<Unit> = Result.success(Unit)
        override suspend fun sendDice(chatId: Long, emoji: String, replyToMessageId: Long?, forumTopicId: Long, options: MessageSendOptions): Result<Unit> = Result.success(Unit)
        override suspend fun votePoll(chatId: Long, messageId: Long, optionIndices: List<Int>): Result<Unit> = Result.success(Unit)
        override suspend fun stopPoll(chatId: Long, messageId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun pollVoters(chatId: Long, messageId: Long, optionIndex: Int, offset: Int, limit: Int): Result<PollVotersPage> =
            Result.failure(UnsupportedOperationException())
        override suspend fun addPollOption(chatId: Long, messageId: Long, text: String): Result<Unit> = Result.success(Unit)
        override suspend fun stopLiveLocation(chatId: Long, messageId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun contacts(): Result<List<ContactPreview>> = Result.success(emptyList())
        override suspend fun sendSticker(chatId: Long, sticker: OutgoingMedia, replyToMessageId: Long?, forumTopicId: Long, options: MessageSendOptions): Result<Unit> = Result.success(Unit)
        override suspend fun recentStickers(): Result<List<StickerItem>> = Result.success(emptyList())
        override suspend fun favoriteStickers(): Result<List<StickerItem>> = Result.success(emptyList())
        override suspend fun setStickerFavorite(sticker: StickerItem, favorite: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun installedStickerSets(): Result<List<StickerSetSummary>> = Result.success(emptyList())
        override suspend fun stickerSet(setId: Long): Result<StickerSetContent> = Result.failure(UnsupportedOperationException())
        override suspend fun changeStickerSet(setId: Long, installed: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun searchStickers(query: String, offset: Int): Result<StickerSearchPage> = Result.failure(UnsupportedOperationException())
        override suspend fun savedAnimations(): Result<List<AnimationItem>> = Result.success(emptyList())
        override suspend fun recentAnimations(offset: String): Result<AnimationPage> = Result.success(AnimationPage(emptyList(), ""))
        override suspend fun searchAnimations(query: String, offset: String): Result<AnimationPage> = Result.success(AnimationPage(emptyList(), ""))
        override suspend fun isPremiumAccount(): Result<Boolean> = Result.success(false)
        override suspend fun scheduledMessages(chatId: Long): Result<List<ChatMessage>> = Result.success(emptyList())
        override suspend fun deleteScheduledMessage(chatId: Long, messageId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun rescheduleMessage(chatId: Long, messageId: Long, atSeconds: Long): Result<Unit> = Result.success(Unit)
        override suspend fun sendScheduledNow(chatId: Long, messageId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun retryMessages(chatId: Long, messageIds: List<Long>, sendNowConsent: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun messageCapabilities(chatId: Long, messageId: Long): Result<MessageCapabilities> =
            Result.success(MessageCapabilities())
        override suspend fun chatNotificationPreferences(chatId: Long): Result<ChatNotificationPreferences> =
            Result.failure(UnsupportedOperationException())
        override suspend fun setChatNotificationPreferences(chatId: Long, preferences: ChatNotificationPreferences): Result<Unit> =
            Result.success(Unit)
        override val secretChats: StateFlow<Map<Long, SecretChatDetails>> = MutableStateFlow(emptyMap())
        override fun refreshSecretChat(chatId: Long) {}
        override suspend fun setSecretChatTtl(chatId: Long, seconds: Int): Result<Unit> = Result.success(Unit)
        override suspend fun closeSecretChat(chatId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun savedNotificationSounds(): Result<List<NotificationSoundOption>> = Result.success(emptyList())
        override suspend fun translateMessage(chatId: Long, messageId: Long, toLanguageCode: String): Result<MessageTranslation> =
            Result.failure(UnsupportedOperationException())
        override suspend fun recognizeSpeech(chatId: Long, messageId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun rateSpeechRecognition(chatId: Long, messageId: Long, isGood: Boolean): Result<Unit> = Result.success(Unit)
        override val speechRecognitionTrial: StateFlow<SpeechRecognitionTrial?> = MutableStateFlow(null)
        override suspend fun mediaPosition(chatId: Long, messageId: Long, kind: MediaKind): Result<MediaPosition> =
            Result.failure(UnsupportedOperationException())
        override suspend fun editMessage(chatId: Long, messageId: Long, text: String, entities: List<MessageTextEntity>): Result<Unit> =
            Result.success(Unit)
        override suspend fun deleteMessage(chatId: Long, messageId: Long, scope: DeleteMessageScope): Result<Unit> =
            onDeleteMessages(chatId, listOf(messageId), scope)
        override suspend fun deleteMessages(chatId: Long, messageIds: List<Long>, scope: DeleteMessageScope): Result<Unit> =
            onDeleteMessages(chatId, messageIds, scope)
        override suspend fun markChatUnread(chatId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun reportMessages(chatId: Long, messageIds: List<Long>, reason: MessageReportReason): Result<Unit> =
            Result.success(Unit)
        override suspend fun forwardMessage(fromChatId: Long, messageId: Long, toChatId: Long, asCopy: Boolean, messageThreadId: Long?, options: MessageSendOptions): Result<Unit> = Result.success(Unit)
        override suspend fun forwardMessages(fromChatId: Long, messageIds: List<Long>, toChatId: Long, asCopy: Boolean, messageThreadId: Long?, options: MessageSendOptions): Result<ForwardedMessages> =
            Result.failure(UnsupportedOperationException())
        override suspend fun messageLink(chatId: Long, messageId: Long, inMessageThread: Boolean): Result<String> = Result.success("")
        override suspend fun availableReactions(chatId: Long, messageId: Long): Result<List<ReactionOption>> = Result.success(emptyList())
        override suspend fun toggleReaction(chatId: Long, messageId: Long, reaction: ReactionOption, addToRecent: Boolean): Result<List<MessageReaction>> =
            Result.success(emptyList())
        override fun observeCustomEmoji(customEmojiId: Long): StateFlow<CustomEmoji?> = MutableStateFlow(null)
        override fun observeMessageEffect(effectId: Long): StateFlow<MessageEffect?> = MutableStateFlow(null)
        override suspend fun messageContext(chatId: Long, messageId: Long): Result<MessageContext> =
            Result.failure(UnsupportedOperationException())
        override suspend fun searchMessages(chatId: Long, query: String, fromMessageId: Long, filter: ChatSearchFilter): Result<MessageSearchPage> =
            Result.success(MessageSearchPage(emptyList(), 0L))
        override suspend fun searchForumTopicMessages(chatId: Long, forumTopicId: Long, query: String, fromMessageId: Long, filter: ChatSearchFilter): Result<MessageSearchPage> =
            Result.success(MessageSearchPage(emptyList(), 0L))
        override suspend fun searchGlobalMessages(query: String, offset: String, filter: GlobalSearchFilter): Result<GlobalMessagePage> =
            Result.success(GlobalMessagePage(emptyList(), ""))
        override suspend fun searchGlobalChats(query: String, limit: Int): Result<GlobalSearchPage<ChatPreview>> =
            Result.failure(UnsupportedOperationException())
        override suspend fun searchGlobalContacts(query: String, limit: Int): Result<GlobalSearchPage<ContactPreview>> =
            Result.failure(UnsupportedOperationException())
        override suspend fun searchGlobalPublicChats(query: String): Result<GlobalSearchPage<ChatPreview>> =
            Result.failure(UnsupportedOperationException())
        override suspend fun openPublicChat(chatId: Long): Result<PublicChatOpenResult> =
            Result.failure(UnsupportedOperationException())
        override suspend fun sharedMessages(chatId: Long, kind: SharedMediaKind, fromMessageId: Long, newer: Boolean): Result<MessageSearchPage> =
            Result.success(MessageSearchPage(emptyList(), 0L))
        override fun messagesVisible(chatId: Long, messageIds: List<Long>) {}
        override suspend fun exportChat(chatId: Long, target: ChatExportTarget, request: ChatExportRequest, onProgress: (ChatExportProgress) -> Unit): Result<ChatExportSummary> =
            Result.failure(UnsupportedOperationException())
        override fun setAppForeground(foreground: Boolean) {}
        override fun refreshInteractionPolicy() {}
        override fun openMessageContent(chatId: Long, messageId: Long) {}
        override fun observeTyping(chatId: Long): StateFlow<List<String>> = MutableStateFlow(emptyList())
        override fun startChatActivity(chatId: Long, activity: ChatActivity) {}
        override fun endChatActivity(chatId: Long, activity: ChatActivity) {}
    }
}
