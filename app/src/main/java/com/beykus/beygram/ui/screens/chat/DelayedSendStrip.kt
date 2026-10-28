package com.beykus.beygram.ui.screens.chat

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.delayed.DelayedSendEntry
import com.beykus.beygram.data.delayed.DelayedSendQueue
import com.beykus.beygram.data.delayed.DelayedSendQueueFullException
import com.beykus.beygram.data.delayed.DelayedSendStatus
import com.beykus.beygram.data.delayed.delayedSendEligible
import com.beykus.beygram.data.settings.DelayedSendSettingsStore
import com.beykus.beygram.data.tdlib.TdLibTelegramRepository
import com.beykus.beygram.domain.model.MessageDraft
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Time between two frames of a countdown. */
private const val COUNTDOWN_TICK_MILLIS = 500L

private val NoDelayedSendEntries: StateFlow<List<DelayedSendEntry>> = MutableStateFlow(emptyList<DelayedSendEntry>()).asStateFlow()

private val NoDelayedSendSeconds: StateFlow<Int> = MutableStateFlow(0).asStateFlow()

/**
 * The delayed-send state of one chat screen. [tryEnqueue] takes a plain text send from the composer when a delay is set,
 * and [Strip] lists this chat's waiting messages above the composer. Without a TDLib account, or with the delay off, every
 * send goes out at once. The messages wait on the device, and sending them does not hide that the account was online.
 */
internal class DelayedSendController(
    private val queue: DelayedSendQueue?,
    private val delayFlow: StateFlow<Int>,
    private val chatId: Long,
    private val forumTopicId: Long,
    private val scope: CoroutineScope,
    private val context: Context
) {
    // Read and written on the main thread only: by the composer, and by the callback that ends the store.
    private var storing = false

    /**
     * Returns false when the send must go out now. Returns true when the delayed path took it: [onQueued] runs once the message
     * is stored, and nothing is sent now. When the store fails, the draft stays and an error is shown.
     */
    fun tryEnqueue(
        text: String,
        entities: List<MessageTextEntity>,
        replyTo: Long?,
        topicId: Long,
        options: MessageSendOptions,
        onQueued: () -> Unit
    ): Boolean {
        val store = queue ?: return false
        val delaySeconds = delayFlow.value
        val eligible = !options.viewOnce && delayedSendEligible(
            delaySeconds = delaySeconds,
            hasAttachments = false,
            isEdit = false,
            scheduled = options.scheduledAtSeconds != null,
            hasSticker = false,
            text = text
        )
        if (!eligible) return false
        // A second tap while the first message is being stored must not store the same text again.
        if (storing) return true
        storing = true
        scope.launch {
            // Not cancellable: once the message is stored it will be sent, so leaving the chat in between must still
            // clear the draft. Otherwise the old text comes back and is easily sent twice.
            withContext(NonCancellable) {
                val result = store.enqueue(chatId, topicId, replyTo ?: 0L, text, entities, options.disableNotification, delaySeconds)
                storing = false
                result.onSuccess { onQueued() }.onFailure { error ->
                    val message = if (error is DelayedSendQueueFullException) R.string.delayed_send_queue_full else R.string.delayed_send_queue_failed
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        }
        return true
    }

    /** Sends a waiting message now. */
    fun sendNow(id: String) {
        scope.launch { withContext(Dispatchers.IO) { queue?.sendNow(id) } }
    }

    /** Deletes a waiting message without putting its text back. */
    fun discard(id: String) {
        scope.launch { withContext(Dispatchers.IO) { queue?.cancel(id) } }
    }

    /** Takes a waiting message back into the composer. The caller has checked that the composer is empty. */
    private fun restore(id: String, onRestoreDraft: (MessageDraft) -> Unit) {
        scope.launch {
            val entry = withContext(Dispatchers.IO) { queue?.cancel(id) } ?: return@launch
            onRestoreDraft(MessageDraft(entry.text, entry.entities, entry.replyToMessageId.takeIf { it > 0L }))
        }
    }

    /**
     * This chat's waiting messages, one row each, above the composer. Shows nothing when there are none. Cancelling puts the
     * text back into the composer only when it is empty; otherwise the message stays and a short notice says why.
     */
    @Composable
    fun Strip(composerEmpty: Boolean, onRestoreDraft: (MessageDraft) -> Unit) {
        val all by (queue?.entries ?: NoDelayedSendEntries).collectAsStateWithLifecycle()
        val rows = all.filter { it.chatId == chatId && it.topicId == forumTopicId }
        if (rows.isEmpty()) return
        val palette = beyGramPalette()
        Column(Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 12.dp, vertical = 4.dp)) {
            rows.forEach { entry ->
                DelayedSendRow(
                    entry = entry,
                    onCancel = {
                        if (composerEmpty) {
                            restore(entry.id, onRestoreDraft)
                        } else {
                            Toast.makeText(context, R.string.delayed_send_clear_composer, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDiscard = { discard(entry.id) },
                    onSendNow = { sendNow(entry.id) }
                )
            }
        }
    }
}

/** One waiting message: a shortened text, its status, and the actions that apply to that status. */
@Composable
private fun DelayedSendRow(entry: DelayedSendEntry, onCancel: () -> Unit, onDiscard: () -> Unit, onSendNow: () -> Unit) {
    val palette = beyGramPalette()
    var now by remember(entry.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(entry.id, entry.status) {
        while (entry.status == DelayedSendStatus.Queued && entry.notBeforeMillis > now) {
            delay(COUNTDOWN_TICK_MILLIS)
            now = System.currentTimeMillis()
        }
    }
    val secondsLeft = ((entry.notBeforeMillis - now + 999L) / 1000L).coerceAtLeast(0L).toInt()
    val status = when (entry.status) {
        DelayedSendStatus.Queued ->
            if (secondsLeft > 0) stringResource(R.string.delayed_send_countdown, secondsLeft) else stringResource(R.string.delayed_send_waiting)
        DelayedSendStatus.Dispatching -> stringResource(R.string.delayed_send_sending)
        DelayedSendStatus.Failed -> stringResource(R.string.delayed_send_failed)
        DelayedSendStatus.Uncertain -> stringResource(R.string.delayed_send_uncertain)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(entry.text.replace('\n', ' '), color = palette.textPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status, color = palette.textSecondary, fontSize = 12.sp)
        }
        when (entry.status) {
            DelayedSendStatus.Queued, DelayedSendStatus.Failed -> {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.delayed_send_cancel), color = palette.accent) }
                TextButton(onClick = onSendNow) { Text(stringResource(R.string.delayed_send_now), color = palette.accent) }
            }
            DelayedSendStatus.Uncertain -> {
                // The message may already have reached the chat, so the user chooses between resending and deleting.
                TextButton(onClick = onSendNow) { Text(stringResource(R.string.delayed_send_retry), color = palette.accent) }
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.delayed_send_delete), color = palette.danger) }
            }
            DelayedSendStatus.Dispatching -> Unit
        }
    }
}

/**
 * The chat screen's delayed sending. The account is the conversation's TDLib account; without one, or with the delay off,
 * every send goes out at once. The account's queue is attached to [conversation] when this screen opens and stays attached after it closes.
 */
@Composable
internal fun rememberDelayedSend(chatId: Long, forumTopicId: Long, conversation: ConversationRepository): DelayedSendController {
    val context = LocalContext.current
    val accountId = (conversation as? TdLibTelegramRepository)?.accountId
    // The first use of an account reads its stored queue and settings from disk, so it happens off the main thread.
    val queue by produceState<DelayedSendQueue?>(null, accountId) {
        value = accountId?.let { withContext(Dispatchers.IO) { DelayedSendQueue.forAccount(context, it) } }
    }
    val delayFlow by produceState(NoDelayedSendSeconds, accountId) {
        value = accountId?.let { withContext(Dispatchers.IO) { DelayedSendSettingsStore.forAccount(context, it).delaySeconds } } ?: NoDelayedSendSeconds
    }
    DisposableEffect(queue, conversation) {
        queue?.attach(conversation)
        // Kept attached after the chat closes: a message queued just before leaving must still go out on time.
        // The next chat screen of this account attaches its own repository.
        onDispose { }
    }
    val scope = rememberCoroutineScope()
    return remember(queue, delayFlow, chatId, forumTopicId, scope) {
        DelayedSendController(queue, delayFlow, chatId, forumTopicId, scope, context)
    }
}
