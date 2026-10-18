package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ChatActivity
import com.beykus.beygram.domain.model.InteractionEffect
import com.beykus.beygram.domain.model.InteractionPolicy
import kotlinx.coroutines.*
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/** Telegram drops a chat action after about five seconds, so the one being shown is sent again before then. */
private const val ACTIVITY_REFRESH_NS = 4_000_000_000L
/** Typing stops on its own this long after the last keystroke. */
private const val TYPING_LIFETIME_NS = 5_000_000_000L

/** The only gateway for conversation receipts, presence and chat activity; no receipts are queued while suppressed. */
internal class TdConversationEffects(private val client: () -> Client, private val policy: InteractionPolicy) {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var foreground = false
    private var ready = false
    private var lastOnline: Boolean? = null
    private val opened = mutableSetOf<Long>()
    private val viewed = mutableMapOf<Long, MutableSet<Long>>()
    /** The activity shown in each chat. As in Telegram, a newer activity replaces the one being shown. */
    private val activities = mutableMapOf<Long, ActivityLoop>()
    /** Outgoing media still uploading, by chat and message id. Its activity ends when TDLib reports the message sent or failed. */
    private val uploads = mutableMapOf<Long, MutableMap<Long, ChatActivity>>()

    fun authorized() = synchronized(lock) { ready = true; lastOnline = null; updatePresence() }
    fun refreshPolicy() = synchronized(lock) { viewed.clear(); updatePresence() }
    fun setForeground(active: Boolean) = synchronized(lock) {
        foreground = active
        if (!active) viewed.clear()
        updatePresence()
    }
    fun opened(chatId: Long) = synchronized(lock) { opened.add(chatId); Unit }
    fun closed(chatId: Long) = synchronized(lock) { opened.remove(chatId); viewed.remove(chatId); Unit }
    private fun updatePresence() {
        val online = foreground && policy.allows(InteractionEffect.OnlinePresence, null)
        if (ready && online != lastOnline) {
            lastOnline = online
            client().send(TdApi.SetOption("online", TdApi.OptionValueBoolean(online)), Client.ResultHandler { result ->
                if (result is TdApi.Error) synchronized(lock) { if (lastOnline == online) lastOnline = null }
            })
        }
    }
    private fun canRead(chatId: Long) = ready && foreground && chatId in opened && policy.allows(InteractionEffect.ReadHistory, chatId)
    fun visible(chatId: Long, ids: List<Long>) {
        val batch = synchronized(lock) {
            if (!canRead(chatId)) return
            val sent = viewed.getOrPut(chatId) { mutableSetOf() }
            ids.filter { it > 0 && it !in sent }.distinct().take(100).onEach { sent.add(it) }.toLongArray()
        }
        if (batch.isEmpty()) return
        scope.launch {
            repeat(3) { attempt ->
                if (!synchronized(lock) { canRead(chatId) }) return@launch
                val result = conversationResult { TdRequests(client)(TdApi.ViewMessages(chatId, batch, TdApi.MessageSourceChatHistory(), false)) }
                if (result.isSuccess) return@launch
                if (attempt < 2) delay(1500L * (attempt + 1))
            }
            synchronized(lock) { viewed[chatId]?.removeAll(batch.toSet()) }
        }
    }
    fun contentOpened(chatId: Long, messageId: Long) = synchronized(lock) {
        if (ready && foreground && chatId in opened && policy.allows(InteractionEffect.OpenContent, chatId)) {
            client().send(TdApi.OpenMessageContent(chatId, messageId), Client.ResultHandler { })
        }
    }

    /** Shows [activity] to the chat's other members. Calling it again for the same activity keeps it alive. */
    fun startActivity(chatId: Long, activity: ChatActivity) = synchronized(lock) { startLocked(chatId, activity) }

    fun endActivity(chatId: Long, activity: ChatActivity) {
        synchronized(lock) {
            val loop = activities[chatId]?.takeIf { it.activity == activity } ?: return@synchronized
            activities.remove(chatId)
            loop.job?.cancel()
            sendActivity(chatId, TdApi.ChatActionCancel())
            resumeUploadsLocked(chatId)
        }
    }

    /** Called for every new message. Only outgoing media that is still uploading shows an activity. */
    fun uploadStarted(message: TdApi.Message) {
        synchronized(lock) {
            val activity = uploadActivityOf(message.content) ?: return@synchronized
            if (!message.isOutgoing || message.sendingState !is TdApi.MessageSendingStatePending) return@synchronized
            uploads.getOrPut(message.chatId) { mutableMapOf() }[message.id] = activity
            startLocked(message.chatId, activity)
        }
    }

    fun uploadFinished(chatId: Long, messageId: Long) {
        synchronized(lock) {
            val pending = uploads[chatId] ?: return@synchronized
            val activity = pending.remove(messageId) ?: return@synchronized
            if (pending.isEmpty()) {
                uploads.remove(chatId)
                endActivity(chatId, activity)
            } else {
                // Typing or a recording that started meanwhile keeps the chat's activity until it ends. The upload shows after that.
                val current = activities[chatId]?.activity
                if (current == null || current.isUpload()) startLocked(chatId, pending.values.last())
            }
        }
    }

    /** The session ended: its messages will never report back, so every activity stops and the uploads are forgotten. */
    fun signedOut() = synchronized(lock) {
        ready = false
        activities.values.forEach { it.job?.cancel() }
        activities.clear()
        uploads.clear()
    }

    /** Caller holds [lock]. */
    private fun startLocked(chatId: Long, activity: ChatActivity) {
        if (!ready || !policy.allows(InteractionEffect.Typing, chatId)) return
        val current = activities[chatId]
        if (current != null && current.activity == activity) {
            current.touchedAt = System.nanoTime()
            return
        }
        current?.job?.cancel()
        val loop = ActivityLoop(activity, System.nanoTime())
        activities[chatId] = loop
        loop.job = scope.launch { repeatActivity(chatId, loop) }
    }

    /** Caller holds [lock]. Once typing or recording has ended, the newest upload still running shows again. */
    private fun resumeUploadsLocked(chatId: Long) {
        val activity = uploads[chatId]?.values?.lastOrNull() ?: return
        startLocked(chatId, activity)
    }

    /** Sends the activity until it ends, expires or is replaced. Each send happens under [lock], so a cancel can't overtake a newer activity. */
    private suspend fun repeatActivity(chatId: Long, loop: ActivityLoop) {
        while (true) {
            val waitMs = synchronized(lock) {
                // A loop replaced while its delay ran out must not send anything: the newer activity owns the chat now.
                if (activities[chatId] !== loop) return
                val now = System.nanoTime()
                if ((loop.activity == ChatActivity.Typing && typingExpired(loop.touchedAt, now)) || !policy.allows(InteractionEffect.Typing, chatId)) {
                    activities.remove(chatId)
                    sendActivity(chatId, TdApi.ChatActionCancel())
                    resumeUploadsLocked(chatId)
                    return
                }
                sendActivity(chatId, loop.activity.toTdAction())
                // Typing wakes up exactly when it expires; the other activities are sent again on the refresh interval.
                val waitNs = if (loop.activity == ChatActivity.Typing) minOf(ACTIVITY_REFRESH_NS, TYPING_LIFETIME_NS - (now - loop.touchedAt)) else ACTIVITY_REFRESH_NS
                waitNs / 1_000_000L
            }
            delay(waitMs)
        }
    }

    private fun sendActivity(chatId: Long, action: TdApi.ChatAction) {
        client().send(TdApi.SendChatAction(chatId, null, "", action), Client.ResultHandler { })
    }

    private class ActivityLoop(val activity: ChatActivity, var touchedAt: Long) {
        var job: Job? = null
    }
}

/** The activity shown while outgoing [content] uploads. Text and other content upload nothing. */
internal fun uploadActivityOf(content: TdApi.MessageContent): ChatActivity? = when (content) {
    is TdApi.MessagePhoto -> ChatActivity.UploadingPhoto
    is TdApi.MessageVideo, is TdApi.MessageAnimation -> ChatActivity.UploadingVideo
    is TdApi.MessageVideoNote -> ChatActivity.UploadingVideoNote
    is TdApi.MessageVoiceNote -> ChatActivity.UploadingVoice
    is TdApi.MessageAudio, is TdApi.MessageDocument -> ChatActivity.UploadingDocument
    else -> null
}

/** Typing is shown only while the last keystroke is recent enough. */
internal fun typingExpired(touchedAtNs: Long, nowNs: Long): Boolean = nowNs - touchedAtNs >= TYPING_LIFETIME_NS

/** Typing and recordings come from the user; the upload activities come from messages being sent. */
private fun ChatActivity.isUpload(): Boolean = when (this) {
    ChatActivity.Typing, ChatActivity.RecordingVoice, ChatActivity.RecordingVideoNote -> false
    else -> true
}

private fun ChatActivity.toTdAction(): TdApi.ChatAction = when (this) {
    ChatActivity.Typing -> TdApi.ChatActionTyping()
    ChatActivity.RecordingVoice -> TdApi.ChatActionRecordingVoiceNote()
    ChatActivity.RecordingVideoNote -> TdApi.ChatActionRecordingVideoNote()
    ChatActivity.UploadingPhoto -> TdApi.ChatActionUploadingPhoto()
    ChatActivity.UploadingVideo -> TdApi.ChatActionUploadingVideo()
    ChatActivity.UploadingVideoNote -> TdApi.ChatActionUploadingVideoNote()
    ChatActivity.UploadingVoice -> TdApi.ChatActionUploadingVoiceNote()
    ChatActivity.UploadingDocument -> TdApi.ChatActionUploadingDocument()
}
