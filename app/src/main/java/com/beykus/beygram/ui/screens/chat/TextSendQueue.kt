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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val workerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    private val nextAttemptId = AtomicLong(1L)
    private val attemptsByScope = MutableStateFlow<Map<TextSendScopeKey, List<TextSendAttempt>>>(emptyMap())
    private val channels = HashMap<TextSendScopeKey, Channel<TextSendJobRequest>>()
    private val lock = Any()

    fun observe(scopeKey: TextSendScopeKey): Flow<List<TextSendAttempt>> =
        attemptsByScope.map { it[scopeKey].orEmpty() }.distinctUntilChanged()

    fun current(scopeKey: TextSendScopeKey): List<TextSendAttempt> =
        attemptsByScope.value[scopeKey].orEmpty()

    fun enqueue(
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
        val attempt = TextSendAttempt(
            attemptId = nextAttemptId.getAndIncrement(),
            scope = scopeKey,
            text = text,
            entities = entities,
            replyToMessageId = replyToMessageId,
            replyPreview = replyPreview,
            options = options,
            baselineSequence = baselineSequence,
            timeLabel = timeLabel,
            dismissForceReplyMessageId = dismissForceReplyMessageId
        )
        attemptsByScope.update { current ->
            val list = current[scopeKey].orEmpty() + attempt
            current + (scopeKey to list)
        }
        channelFor(scopeKey).trySend(
            TextSendJobRequest(scopeKey, attempt.attemptId, execute, onSuccess, onFailure, formatError)
        )
        return attempt
    }

    fun retry(
        scopeKey: TextSendScopeKey,
        attemptId: Long,
        sendNowConsent: Boolean = false,
        baselineSequence: Long? = null,
        execute: suspend (TextSendAttempt) -> Result<Unit>,
        onSuccess: (TextSendAttempt) -> Unit = {},
        onFailure: (TextSendAttempt, Throwable) -> Unit = { _, _ -> },
        formatError: (Throwable) -> String
    ): Boolean {
        var found = false
        attemptsByScope.update { current ->
            val list = current[scopeKey].orEmpty()
            val updated = list.map { attempt ->
                if (attempt.attemptId == attemptId) {
                    found = true
                    attempt.copy(
                        phase = TextSendAttemptPhase.Sending,
                        errorMessage = null,
                        options = if (sendNowConsent) attempt.options.copy(sendNowConsent = true) else attempt.options,
                        baselineSequence = baselineSequence ?: attempt.baselineSequence
                    )
                } else attempt
            }
            if (found) current + (scopeKey to updated) else current
        }
        if (found) {
            channelFor(scopeKey).trySend(
                TextSendJobRequest(scopeKey, attemptId, execute, onSuccess, onFailure, formatError)
            )
        }
        return found
    }

    fun discard(scopeKey: TextSendScopeKey, attemptId: Long) {
        attemptsByScope.update { current ->
            val list = current[scopeKey].orEmpty().filterNot { it.attemptId == attemptId }
            if (list.isEmpty()) current - scopeKey else current + (scopeKey to list)
        }
    }

    private fun channelFor(scopeKey: TextSendScopeKey): Channel<TextSendJobRequest> = synchronized(lock) {
        channels.getOrPut(scopeKey) {
            Channel<TextSendJobRequest>(Channel.UNLIMITED).also { channel ->
                workerScope.launch {
                    for (job in channel) {
                        processJob(job)
                    }
                }
            }
        }
    }

    private suspend fun processJob(job: TextSendJobRequest) {
        val attempt = attemptsByScope.value[job.scopeKey]
            ?.firstOrNull { it.attemptId == job.attemptId }
            ?: return
        val result = runCatching { job.execute(attempt) }
            .getOrElse { Result.failure(it) }
        if (result.isSuccess) {
            discard(job.scopeKey, job.attemptId)
            job.onSuccess(attempt)
        } else {
            val error = result.exceptionOrNull() ?: IllegalStateException("Send failed")
            val message = job.formatError(error)
            attemptsByScope.update { current ->
                val list = current[job.scopeKey].orEmpty()
                val updated = list.map { item ->
                    if (item.attemptId == job.attemptId) {
                        item.copy(phase = TextSendAttemptPhase.Failed, errorMessage = message)
                    } else item
                }
                if (updated.isEmpty()) current - job.scopeKey else current + (job.scopeKey to updated)
            }
            job.onFailure(attempt, error)
        }
    }

    fun close() {
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

internal class ChatTextSendViewModel : androidx.lifecycle.ViewModel() {
    val queue = TextSendQueue()

    override fun onCleared() {
        queue.close()
        super.onCleared()
    }
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
