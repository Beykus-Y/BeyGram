package com.beykus.beygram.ui.screens.chat

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import com.beykus.beygram.ui.components.TextButton
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

/** The account's queue and delay setting, read from disk together. A null queue means there is no TDLib account to send through. */
internal class DelayedSendSetup(val queue: DelayedSendQueue?, val delay: StateFlow<Int>)

private val NoDelayedSendSetup = DelayedSendSetup(queue = null, delay = NoDelayedSendSeconds)

/** What the composer does with a plain text send. */
internal enum class DelayedSendRoute {
    /** The account's queue and delay are still being read: the send waits for them. */
    Hold,

    /** The message is stored and goes out after the delay. */
    Queue,

    /** The message goes out at once. */
    SendNow
}

/**
 * The composer's decision for a plain text send. Until the queue and the delay are read, the send is held: a delay that is
 * not read yet must not be taken for "off". Without a queue, or with the delay off, the send goes out at once.
 */
internal fun delayedSendRoute(setupRead: Boolean, hasQueue: Boolean, delaySeconds: Int, eligible: Boolean): DelayedSendRoute = when {
    !setupRead -> DelayedSendRoute.Hold
    hasQueue && eligible && delaySeconds > 0 -> DelayedSendRoute.Queue
    else -> DelayedSendRoute.SendNow
}

/**
 * A plain text send that waits for the account's delay to be read, with the composer as it was when it was sent. It goes out only
 * while the composer still holds that text.
 */
internal class HeldDelayedSend(val options: MessageSendOptions, val draft: MessageDraft)

/**
 * Whether a held send still matches the composer: the text is the same, and no attachment or sticker was added since. Anything else
 * cancels the send, so nothing goes out that the user did not send.
 */
internal fun heldSendStillMatches(held: MessageDraft, now: MessageDraft, attachmentsActive: Boolean, stickerPending: Boolean): Boolean =
    held == now && !attachmentsActive && !stickerPending

/**
 * The delayed-send state of one chat screen. [route] tells the composer what a plain text send does, [enqueue] stores one that
 * waits, and [Strip] lists this chat's waiting messages above the composer. Without a TDLib account, or with the delay off, every
 * send goes out at once. The messages wait on the device, and sending them does not hide that the account was online.
 */
internal class DelayedSendController(
    /** Null while the queue and the delay are still read from disk. */
    private val setup: DelayedSendSetup?,
    private val chatId: Long,
    private val forumTopicId: Long,
    private val scope: CoroutineScope,
    private val context: Context
) {
    private val queue: DelayedSendQueue? get() = setup?.queue

    // Read and written on the main thread only: by the composer, and by the callback that ends the store.
    private var storing = false

    /** Whether the queue and the delay have been read. Until they have, [route] holds every plain text send. */
    val ready: Boolean get() = setup != null

    /** What a plain text send from the composer does. Stealth scheduling and the other send paths are decided by the caller. */
    fun route(text: String, options: MessageSendOptions): DelayedSendRoute {
        val current = setup
        val delaySeconds = current?.delay?.value ?: 0
        val eligible = !options.viewOnce && delayedSendEligible(
            delaySeconds = delaySeconds,
            hasAttachments = false,
            isEdit = false,
            scheduled = options.scheduledAtSeconds != null,
            hasSticker = false,
            text = text
        )
        return delayedSendRoute(
            setupRead = current != null,
            hasQueue = current?.queue != null,
            delaySeconds = delaySeconds,
            eligible = eligible
        )
    }

    /**
     * Stores a message that [route] sent to the queue. [onQueued] runs once the message is stored, and only then may the caller
     * clear the composer. When the store fails, the draft stays and an error is shown.
     */
    fun enqueue(
        text: String,
        entities: List<MessageTextEntity>,
        replyTo: Long?,
        topicId: Long,
        options: MessageSendOptions,
        onQueued: () -> Unit
    ) {
        val current = setup ?: return
        val store = current.queue ?: return
        val delaySeconds = current.delay.value
        // A second tap while the first message is being stored must not store the same text again.
        if (storing) return
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
            // Not cancellable: once the entry is removed from the queue its text must reach the draft, or it is lost.
            withContext(NonCancellable) {
                val entry = withContext(Dispatchers.IO) { queue?.cancel(id) } ?: return@withContext
                onRestoreDraft(MessageDraft(entry.text, entry.entities, entry.replyToMessageId.takeIf { it > 0L }))
            }
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
        // Bounded and scrollable: a backlog of waiting messages must never push the composer off the screen.
        Column(Modifier.fillMaxWidth().heightIn(max = 168.dp).chatChromeBackground(palette.surface).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)) {
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
 * The chat screen's delayed sending. The account is the conversation's TDLib account; without one, every send goes out at once.
 * The account's queue is attached to [conversation] when this screen opens and stays attached after it closes.
 */
@Composable
internal fun rememberDelayedSend(chatId: Long, forumTopicId: Long, conversation: ConversationRepository): DelayedSendController {
    val context = LocalContext.current
    val accountId = (conversation as? TdLibTelegramRepository)?.accountId
    // The queue and the delay setting are read from disk together, off the main thread. Until that is done the setup is null,
    // and the composer holds its sends: "not read yet" must not look like "delay off". Without a TDLib account there is nothing to read.
    val setup by produceState<DelayedSendSetup?>(if (accountId == null) NoDelayedSendSetup else null, accountId) {
        value = accountId?.let { id ->
            withContext(Dispatchers.IO) {
                DelayedSendSetup(
                    queue = DelayedSendQueue.forAccount(context, id),
                    delay = DelayedSendSettingsStore.forAccount(context, id).delaySeconds
                )
            }
        } ?: NoDelayedSendSetup
    }
    DisposableEffect(setup?.queue, conversation) {
        setup?.queue?.attach(conversation)
        // Kept attached after the chat closes: a message queued just before leaving must still go out on time.
        // The next chat screen of this account attaches its own repository.
        onDispose { }
    }
    val scope = rememberCoroutineScope()
    return remember(setup, chatId, forumTopicId, scope) {
        DelayedSendController(setup, chatId, forumTopicId, scope, context)
    }
}
