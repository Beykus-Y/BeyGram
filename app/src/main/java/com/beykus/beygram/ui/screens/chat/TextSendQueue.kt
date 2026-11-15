package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageReply
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/** Isolates queued and failed plain-text send attempts per account, chat, and forum topic. */
internal data class TextSendScopeKey(
    val accountId: String,
    val chatId: Long,
    val forumTopicId: Long
)

internal enum class TextSendAttemptPhase {
    Sending,
    Failed
}

/** Immutable snapshot of a single plain-text send attempt, independent of the live composer draft. */
internal data class TextSendAttempt(
    val attemptId: Long,
    val scope: TextSendScopeKey,
    val text: String,
    val entities: List<MessageTextEntity>,
    val replyToMessageId: Long?,
    val replyPreview: MessageReply?,
    val options: MessageSendOptions,
    val baselineSequence: Long,
    val timeLabel: String,
    val dismissForceReplyMessageId: Long? = null,
    val phase: TextSendAttemptPhase = TextSendAttemptPhase.Sending,
    val errorMessage: String? = null
) {
    fun toChatMessage(): ChatMessage = ChatMessage(
        id = -attemptId,
        chatId = scope.chatId,
        text = text,
        time = timeLabel,
        isOutgoing = true,
        sendState = if (phase == TextSendAttemptPhase.Sending) MessageSendState.Sending else MessageSendState.Failed,
        reply = replyPreview,
        entities = entities,
        forumTopicId = scope.forumTopicId,
        sendError = errorMessage
    )
}

internal class TextSendStorageException(cause: Exception) : IllegalStateException("Pending text queue storage unavailable", cause)

internal data class TextSendJobRequest(
    val scopeKey: TextSendScopeKey,
    val attemptId: Long,
    val execute: suspend (TextSendAttempt) -> Result<Unit>,
    val onSuccess: (TextSendAttempt) -> Unit,
    val onFailure: (TextSendAttempt, Throwable) -> Unit,
    val formatError: (Throwable) -> String
)

/**
 * Sequential FIFO queue for plain-text send attempts.
 *
 * Each [TextSendScopeKey] runs its own serial worker so rapid consecutive sends preserve their exact order,
 * replies, entities, and send options without blocking the composer input field.
 */
internal class TextSendQueue(
    private val workerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val store: TextSendAttemptStore? = null,
    recoveryError: String? = null,
    private val storageDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val storageProblem = MutableStateFlow(false)
    val storageFailed = storageProblem.asStateFlow()
    private val attemptsByScope = MutableStateFlow<Map<TextSendScopeKey, List<TextSendAttempt>>>(emptyMap())
    private val nextAttemptId = AtomicLong(1L)
    private val jobs = HashMap<TextSendScopeKey, java.util.ArrayDeque<TextSendJobRequest>>()
    // The storage monitor is entered on the IO dispatcher. Workers must never wait for it
    // on the UI thread just to poll their in-memory jobs.
    private val lock = Any()
    private val jobsLock = Any()
    private val composerLock = Any()
    private val composerWrites = MutableStateFlow<Set<TextSendScopeKey>>(emptySet())
    @Volatile private var closed = false
    private var readFailure: Exception? = null

    private val initialized = workerScope.async(start = CoroutineStart.UNDISPATCHED) {
        withContext(storageDispatcher) {
            synchronized(lock) {
                if (!closed) {
                    try {
                        val restored = store?.load().orEmpty().map {
                            if (it.phase == TextSendAttemptPhase.Sending) it.copy(
                                phase = TextSendAttemptPhase.Failed, errorMessage = recoveryError
                            ) else it
                        }
                        nextAttemptId.set((restored.maxOfOrNull { it.attemptId } ?: 0L) + 1L)
                        attemptsByScope.value = restored.groupBy { it.scope }
                    } catch (failure: Exception) {
                        // An unreadable snapshot must never be replaced by an empty one.
                        readFailure = failure
                        storageProblem.value = true
                    }
                }
            }
        }
    }

    fun composerWriting(scopeKey: TextSendScopeKey): Flow<Boolean> =
        composerWrites.map { scopeKey in it }.distinctUntilChanged()

    /** Only the durable-write phase owns the composer, not the network round trip. */
    fun launchComposerWrite(scopeKey: TextSendScopeKey, action: suspend () -> Unit) {
        synchronized(composerLock) {
            if (closed || scopeKey in composerWrites.value) return
            composerWrites.value = composerWrites.value + scopeKey
        }
        workerScope.launch {
            try { action() } finally {
                synchronized(composerLock) { composerWrites.value = composerWrites.value - scopeKey }
            }
        }
    }

    /** A queued UI action may finish after navigation, but never survive session logout. */
    fun launchMutation(action: suspend () -> Unit) {
        workerScope.launch { action() }
    }

    internal val activeWorkerCount: Int get() = synchronized(jobsLock) { jobs.size }

    fun observe(scopeKey: TextSendScopeKey): Flow<List<TextSendAttempt>> =
        attemptsByScope.map { it[scopeKey].orEmpty() }.distinctUntilChanged()

    fun current(scopeKey: TextSendScopeKey): List<TextSendAttempt> =
        attemptsByScope.value[scopeKey].orEmpty()

    suspend fun enqueue(
        scopeKey: TextSendScopeKey,
        text: String,
        entities: List<MessageTextEntity>,
        replyToMessageId: Long?,
        replyPreview: MessageReply?,
        options: MessageSendOptions,
        baselineSequence: Long,
        timeLabel: String,
        dismissForceReplyMessageId: Long? = null,
        execute: suspend (TextSendAttempt) -> Result<Unit>,
        onSuccess: (TextSendAttempt) -> Unit = {},
        onFailure: (TextSendAttempt, Throwable) -> Unit = { _, _ -> },
        formatError: (Throwable) -> String
    ): TextSendAttempt {
        ensureInitialized()
        return withContext(storageDispatcher) { synchronized(lock) {
        val attempt = TextSendAttempt(
            attemptId = nextAttemptId.getAndIncrement(),
            scope = scopeKey,
            text = text,
            entities = entities.toList(),
            replyToMessageId = replyToMessageId,
            replyPreview = replyPreview,
            options = options,
            baselineSequence = baselineSequence,
            timeLabel = timeLabel,
            dismissForceReplyMessageId = dismissForceReplyMessageId
        )
        updateAttempts { current ->
            val list = current[scopeKey].orEmpty() + attempt
            current + (scopeKey to list)
        }
        submit(
            TextSendJobRequest(scopeKey, attempt.attemptId, execute, onSuccess, onFailure, formatError)
        )
        attempt
        } }
    }

    suspend fun retry(
        scopeKey: TextSendScopeKey,
        attemptId: Long,
        sendNowConsent: Boolean = false,
        baselineSequence: Long? = null,
        execute: suspend (TextSendAttempt) -> Result<Unit>,
        onSuccess: (TextSendAttempt) -> Unit = {},
        onFailure: (TextSendAttempt, Throwable) -> Unit = { _, _ -> },
        formatError: (Throwable) -> String
    ): Boolean {
        ensureInitialized()
        return withContext(storageDispatcher) { synchronized(lock) {
        val found = updateAttempts { current ->
            val list = current[scopeKey].orEmpty()
            if (list.none { it.attemptId == attemptId && it.phase == TextSendAttemptPhase.Failed }) {
                current
            } else {
                current + (scopeKey to list.map { attempt ->
                    if (attempt.attemptId == attemptId && attempt.phase == TextSendAttemptPhase.Failed) {
                        attempt.copy(
                            phase = TextSendAttemptPhase.Sending,
                            errorMessage = null,
                            options = if (sendNowConsent) attempt.options.copy(sendNowConsent = true) else attempt.options,
                            baselineSequence = baselineSequence ?: attempt.baselineSequence
                        )
                    } else attempt
                })
            }
        }
        if (found) {
            submit(TextSendJobRequest(scopeKey, attemptId, execute, onSuccess, onFailure, formatError))
        }
        found
        } }
    }

    suspend fun discard(scopeKey: TextSendScopeKey, attemptId: Long) {
        ensureInitialized()
        withContext(storageDispatcher) {
            updateAttempts { current -> withoutAttempt(current, scopeKey, attemptId) }
        }
    }

    private suspend fun ensureInitialized() {
        if (closed) throw TextSendStorageException(IllegalStateException("Text send session closed"))
        initialized.await()
    }

    private fun withoutAttempt(
        current: Map<TextSendScopeKey, List<TextSendAttempt>>,
        scopeKey: TextSendScopeKey,
        attemptId: Long
    ): Map<TextSendScopeKey, List<TextSendAttempt>> {
        val list = current[scopeKey].orEmpty().filterNot { it.attemptId == attemptId }
        return if (list.isEmpty()) current - scopeKey else current + (scopeKey to list)
    }

    private fun submit(job: TextSendJobRequest): Unit = synchronized(jobsLock) {
        if (closed) return@synchronized
        val existing = jobs[job.scopeKey]
        if (existing != null) {
            existing.addLast(job)
        } else {
            jobs[job.scopeKey] = java.util.ArrayDeque<TextSendJobRequest>().apply { addLast(job) }
            workerScope.launch {
                while (true) {
                    val next = synchronized(jobsLock) {
                        val pending = jobs[job.scopeKey]
                        val item = pending?.pollFirst()
                        if (item == null) jobs.remove(job.scopeKey)
                        item
                    } ?: break
                    processJob(next)
                }
            }
        }
    }

    /**
     * Persist before publishing user actions. Once TDLib has answered, its result cannot be rolled back;
     * keep the live result even if disk fails, while the durable Sending row remains recoverable with a
     * possible-delivery warning on restart. Later successful writes reconcile the durable snapshot.
     */
    private fun updateAttempts(
        keepLiveOutcomeOnFailure: Boolean = false,
        transform: (Map<TextSendScopeKey, List<TextSendAttempt>>) -> Map<TextSendScopeKey, List<TextSendAttempt>>
    ): Boolean = synchronized(lock) {
        if (closed) {
            if (keepLiveOutcomeOnFailure) return@synchronized false
            throw TextSendStorageException(IllegalStateException("Text send session closed"))
        }
        val current = attemptsByScope.value
        val next = transform(current)
        if (next == current) return@synchronized false
        try {
            readFailure?.let { throw it }
            store?.save(next.values.flatten())
            storageProblem.value = false
        } catch (failure: Exception) {
            storageProblem.value = true
            if (!keepLiveOutcomeOnFailure) throw TextSendStorageException(failure)
        }
        attemptsByScope.value = next
        true
    }

    private suspend fun processJob(job: TextSendJobRequest) {
        val attempt = attemptsByScope.value[job.scopeKey]
            ?.firstOrNull { it.attemptId == job.attemptId }
            ?: return
        val result = try {
            job.execute(attempt)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
        (result.exceptionOrNull() as? CancellationException)?.let { throw it }
        if (result.isSuccess) {
            val committed = withContext(storageDispatcher) {
                updateAttempts(keepLiveOutcomeOnFailure = true) { current ->
                    withoutAttempt(current, job.scopeKey, job.attemptId)
                }
            }
            if (committed) job.onSuccess(attempt)
        } else {
            val error = result.exceptionOrNull() ?: IllegalStateException("Send failed")
            val message = job.formatError(error)
            val committed = withContext(storageDispatcher) {
                updateAttempts(keepLiveOutcomeOnFailure = true) { current ->
                    val list = current[job.scopeKey].orEmpty()
                    val updated = list.map { item ->
                        if (item.attemptId == job.attemptId) {
                            item.copy(phase = TextSendAttemptPhase.Failed, errorMessage = message)
                        } else item
                    }
                    if (updated.isEmpty()) current - job.scopeKey else current + (job.scopeKey to updated)
                }
            }
            if (committed) job.onFailure(attempt, error)
        }
    }

    /** Invalidate this instance before deleting the session's durable data. */
    fun close() {
        synchronized(lock) {
            closed = true
            attemptsByScope.value = emptyMap()
        }
        synchronized(jobsLock) { jobs.clear() }
        synchronized(composerLock) { composerWrites.value = emptySet() }
        workerScope.cancel()
    }
}

/**
 * Filters out in-flight [TextSendAttemptPhase.Sending] attempts that already have a matching outgoing message in
 * [history], preventing duplicate bubbles during the handoff between local queue and TDLib's history store.
 * Returned newest-first to match `LazyColumn(reverseLayout = true)`.
 */
internal fun visiblePendingTextAttempts(
    attempts: List<TextSendAttempt>,
    history: ChatHistory
): List<TextSendAttempt> {
    if (attempts.isEmpty()) return emptyList()
    val claimedIds = HashSet<Long>()
    val visible = ArrayList<TextSendAttempt>(attempts.size)
    for (attempt in attempts) {
        if (attempt.phase == TextSendAttemptPhase.Failed) {
            visible += attempt
            continue
        }
        val match = findComposerLaunchMessage(
            history = history,
            text = attempt.text,
            replyToMessageId = attempt.replyToMessageId,
            baselineSequence = attempt.baselineSequence,
            assignedIds = claimedIds
        )
        if (match != null) {
            claimedIds += resolveMessageIdThroughReplacements(match.id, history.recentReplacements)
        } else {
            visible += attempt
        }
    }
    return visible.asReversed()
}

internal suspend fun executeTextSendAttempt(
    conversation: ConversationRepository,
    attempt: TextSendAttempt
): Result<Unit> {
    val result = conversation.sendMessage(
        chatId = attempt.scope.chatId,
        text = attempt.text,
        replyToMessageId = attempt.replyToMessageId,
        entities = attempt.entities,
        forumTopicId = attempt.scope.forumTopicId,
        clearDraft = false,
        options = attempt.options
    )
    if (result.isSuccess && attempt.dismissForceReplyMessageId != null) {
        conversation.dismissBotKeyboard(attempt.scope.chatId, attempt.dismissForceReplyMessageId)
    }
    return result
}


internal data class FailedSendTarget(
    val message: ChatMessage,
    val localAttemptId: Long? = null
)

@Composable
internal fun FailedSendDetailsDialog(
    target: FailedSendTarget,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit
) {
    val palette = beyGramPalette()
    val clipboard = LocalClipboardManager.current
    val reason = target.message.sendError?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.err_chat_send_failed)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_send_failure_title)) },
        text = {
            Column {
                Text(reason, color = palette.warningText, fontSize = 14.sp)
                if (target.message.text.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        target.message.text,
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onRetry()
            }) {
                Text(stringResource(R.string.chat_retry_send), color = palette.accent)
            }
        },
        dismissButton = {
            if (target.message.text.isNotBlank()) {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(target.message.text))
                    onDismiss()
                }) {
                    Text(stringResource(R.string.chat_send_failure_copy), color = palette.textPrimary)
                }
            }
            TextButton(onClick = {
                onDismiss()
                onDiscard()
            }) {
                Text(stringResource(R.string.chat_send_failure_discard), color = palette.warningText)
            }
        }
    )
}
