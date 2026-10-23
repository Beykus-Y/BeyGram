package com.beykus.beygram.data.tdlib

import android.content.Context
import com.beykus.beygram.domain.model.MessageDraft
import com.beykus.beygram.domain.model.MessageDraftCodec
import com.beykus.beygram.domain.model.MessageDraftSync
import com.beykus.beygram.domain.model.StoredMessageDraft
import com.beykus.beygram.domain.model.validTextEntities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val WRITE_DELAY_MS = 400L
private const val DRAFTS_PREFS = "chat_message_drafts"

/**
 * Local-first composer drafts plus TDLib synchronization.
 * UI edits update a StateFlow and Android's private preferences immediately; the remote request is debounced.
 * A stale TDLib update is never allowed to replace text changed in an open editor.
 * Once TDLib holds a value, its backup is marked synced, so a draft cleared on another device is never sent back.
 */
internal fun tdDraftPreferencesName(accountId: String): String =
    if (accountId == TdAccountStorageLayout.LEGACY_ACCOUNT_ID) DRAFTS_PREFS
    else "${DRAFTS_PREFS}_${TdAccountStorageLayout.requireAccountId(accountId)}"

internal class TdChatDrafts(
    context: Context,
    accountId: String = TdAccountStorageLayout.LEGACY_ACCOUNT_ID,
    client: () -> Client
) {
    private val prefs = context.applicationContext.getSharedPreferences(
        tdDraftPreferencesName(accountId), Context.MODE_PRIVATE
    )
    private val request = TdRequests(client)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val guard = Any()
    private val states = ConcurrentHashMap<Long, MutableStateFlow<MessageDraft>>()
    // Each topic has its own local state and cloud-write queue; parent chat drafts stay untouched.
    private val topicStates = ConcurrentHashMap<Pair<Long, Long>, MutableStateFlow<MessageDraft>>()
    private val topicRevisions = ConcurrentHashMap<Pair<Long, Long>, AtomicLong>()
    private val topicLocks = ConcurrentHashMap<Pair<Long, Long>, Mutex>()
    private val topicDirty = ConcurrentHashMap.newKeySet<Pair<Long, Long>>()
    private fun topicKey(chatId: Long, topicId: Long) = "topic_${chatId}_${topicId}"
    private fun topicState(chatId: Long, topicId: Long): MutableStateFlow<MessageDraft> =
        topicStates.computeIfAbsent(chatId to topicId) {
            val saved = prefs.getString(topicKey(chatId, topicId), null)?.let(MessageDraftCodec::decode)
            MutableStateFlow(saved?.value ?: MessageDraft())
        }
    fun observeTopic(chatId: Long, topicId: Long): StateFlow<MessageDraft> = topicState(chatId, topicId).asStateFlow()
    fun updateTopic(chatId: Long, topicId: Long, transform: (MessageDraft) -> MessageDraft) = synchronized(guard) {
        val key = chatId to topicId
        val flow = topicState(chatId, topicId)
        val updated = transform(flow.value)
        val next = updated.copy(entities = validTextEntities(updated.text, updated.entities))
        if (next != flow.value) {
            flow.value = next
            prefs.edit().putString(topicKey(chatId, topicId),
                MessageDraftCodec.encode(StoredMessageDraft(next, System.currentTimeMillis()))).apply()
            topicDirty.add(key)
            topicRevisions.computeIfAbsent(key) { AtomicLong() }.incrementAndGet()
            scheduleTopic(chatId, topicId, WRITE_DELAY_MS)
        }
    }

    /** Server refreshes do not discard newer local edits. A null remote draft is a valid cloud clear. */
    fun receiveTopic(chatId: Long, topicId: Long, raw: TdApi.DraftMessage?) {
        if (topicId <= 0L) return
        val remote = fromTd(raw) ?: return
        val remoteTime = (raw?.date ?: 0).toLong() * 1000L
        val key = chatId to topicId
        synchronized(guard) {
            val flow = topicState(chatId, topicId)
            val local = prefs.getString(topicKey(chatId, topicId), null)?.let(MessageDraftCodec::decode)
            if (flow.value == remote) {
                topicDirty.remove(key)
                if (local != null && !local.synced) prefs.edit().putString(topicKey(chatId, topicId),
                    MessageDraftCodec.encode(local.copy(synced = true))).apply()
                return
            }
            if (key in topicDirty) return
            if (MessageDraftSync.keepsUnsentLocal(local, remote, remoteTime)) {
                topicDirty.add(key)
                scheduleTopic(chatId, topicId, WRITE_DELAY_MS)
                return
            }
            flow.value = remote
            prefs.edit().putString(topicKey(chatId, topicId),
                MessageDraftCodec.encode(StoredMessageDraft(remote, remoteTime, synced = true))).apply()
        }
    }

    private fun scheduleTopic(chatId: Long, topicId: Long, waitMillis: Long) {
        val key = chatId to topicId
        val generation = topicRevisions.computeIfAbsent(key) { AtomicLong() }.get()
        scope.launch {
            if (waitMillis > 0L) delay(waitMillis)
            topicLocks.computeIfAbsent(key) { Mutex() }.withLock {
                if (topicRevisions[key]?.get() != generation || key !in topicDirty) return@withLock
                val snapshot = topicState(chatId, topicId).value
                val ok = runCatching {
                    request(TdApi.SetChatDraftMessage().apply {
                        this.chatId = chatId
                        this.topicId = TdApi.MessageTopicForum(topicId.toInt())
                        draftMessage = toTd(snapshot)
                    })
                }.isSuccess
                if (ok) synchronized(guard) {
                    if (topicRevisions[key]?.get() == generation && topicState(chatId, topicId).value == snapshot) {
                        topicDirty.remove(key)
                        prefs.getString(topicKey(chatId, topicId), null)?.let(MessageDraftCodec::decode)?.let { saved ->
                            prefs.edit().putString(topicKey(chatId, topicId),
                                MessageDraftCodec.encode(saved.copy(synced = true))).apply()
                        }
                    }
                }
            }
        }
    }
    private val revisions = ConcurrentHashMap<Long, AtomicLong>()
    private val sendLocks = ConcurrentHashMap<Long, Mutex>()
    private val dirty = ConcurrentHashMap.newKeySet<Long>()
    private val editedWhileOpen = ConcurrentHashMap.newKeySet<Long>()

    private fun key(chatId: Long) = "chat_$chatId"
    private fun stored(chatId: Long): StoredMessageDraft? =
        prefs.getString(key(chatId), null)?.let(MessageDraftCodec::decode)

    private fun save(chatId: Long, draft: StoredMessageDraft) {
        prefs.edit().putString(key(chatId), MessageDraftCodec.encode(draft)).apply()
    }

    private fun state(chatId: Long): MutableStateFlow<MessageDraft> =
        states.computeIfAbsent(chatId) { MutableStateFlow(stored(chatId)?.value ?: MessageDraft()) }

    fun observe(chatId: Long): StateFlow<MessageDraft> = state(chatId).asStateFlow()

    /** Synchronous state and disk-queue update, safe to call from a Compose callback. */
    fun update(chatId: Long, transform: (MessageDraft) -> MessageDraft) {
        synchronized(guard) {
            val flow = state(chatId)
            val changed = transform(flow.value)
            val next = changed.copy(entities = validTextEntities(changed.text, changed.entities))
            if (next == flow.value) return
            flow.value = next
            save(chatId, StoredMessageDraft(next, System.currentTimeMillis()))
            dirty.add(chatId)
            editedWhileOpen.add(chatId)
            revisions.computeIfAbsent(chatId) { AtomicLong() }.incrementAndGet()
            schedule(chatId, WRITE_DELAY_MS)
        }
    }

    fun closed(chatId: Long) {
        editedWhileOpen.remove(chatId)
        if (chatId in dirty) {
            schedule(chatId, 0L)
        } else {
            // Cloud drafts that changed while the editor was open were ignored, so the current one is read once.
            scope.launch {
                val chat = runCatching { request(TdApi.GetChat(chatId)) }.getOrNull() ?: return@launch
                receive(chatId, chat.draftMessage)
            }
        }
    }

    fun connectionReady() {
        dirty.forEach { schedule(it, 0L) }
        topicDirty.forEach { (chatId, topicId) -> scheduleTopic(chatId, topicId, 0L) }
    }

    /**
     * Existing local edits win over older cloud echoes; newer untouched drafts arrive from other devices.
     * A synced backup always yields to TDLib, so a draft cleared on another device stays cleared.
     */
    fun receive(chatId: Long, raw: TdApi.DraftMessage?) {
        val remote = fromTd(raw) ?: return
        val remoteTime = (raw?.date ?: 0).toLong() * 1000L
        synchronized(guard) {
            val flow = state(chatId)
            val local = stored(chatId)
            if (flow.value == remote) {
                dirty.remove(chatId)
                if (local != null && !local.synced) save(chatId, local.copy(synced = true))
                return
            }
            if (chatId in dirty || chatId in editedWhileOpen) return
            if (MessageDraftSync.keepsUnsentLocal(local, remote, remoteTime)) {
                dirty.add(chatId)
                schedule(chatId, WRITE_DELAY_MS)
                return
            }
            flow.value = remote
            save(chatId, StoredMessageDraft(remote, remoteTime, synced = true))
        }
    }

    private fun fromTd(raw: TdApi.DraftMessage?): MessageDraft? {
        if (raw == null) return MessageDraft()
        val input = raw.content as? TdApi.DraftMessageContentText ?: return null
        val formatted = input.text ?: return null
        val reply = (raw.replyTo as? TdApi.InputMessageReplyToMessage)?.messageId?.takeIf { it > 0L }
        return MessageDraft(formatted.text.orEmpty(), formatted.text?.let { validTextEntities(it, formatted.domainEntities()) }.orEmpty(), reply)
    }

    private fun toTd(draft: MessageDraft): TdApi.DraftMessage? {
        if (draft.isEmpty) return null
        return TdApi.DraftMessage().apply {
            date = (System.currentTimeMillis() / 1000L).toInt()
            content = TdApi.DraftMessageContentText().apply {
                text = formattedText(draft.text, draft.entities)
            }
            draft.replyToMessageId?.takeIf { it > 0L }?.let { replyId ->
                replyTo = TdApi.InputMessageReplyToMessage().apply {
                    messageId = replyId
                    pollOptionId = ""
                }
            }
        }
    }

    private fun schedule(chatId: Long, waitMillis: Long) {
        val generation = revisions.computeIfAbsent(chatId) { AtomicLong() }.get()
        scope.launch {
            if (waitMillis > 0L) delay(waitMillis)
            sendLocks.computeIfAbsent(chatId) { Mutex() }.withLock {
                if (generation != revisions[chatId]?.get() || chatId !in dirty) return@withLock
                val snapshot = state(chatId).value
                // Failures leave the private backup intact for the next edit or network reconnect.
                val succeeded = runCatching {
                    request(TdApi.SetChatDraftMessage().apply {
                        this.chatId = chatId
                        topicId = null
                        draftMessage = toTd(snapshot)
                    })
                }.isSuccess
                if (succeeded) confirmSent(chatId, snapshot, generation)
            }
        }
    }

    /** TDLib now holds [snapshot]; a newer edit keeps the backup unsynced until its own request succeeds. */
    private fun confirmSent(chatId: Long, snapshot: MessageDraft, generation: Long) {
        synchronized(guard) {
            if (generation != revisions[chatId]?.get() || state(chatId).value != snapshot) return
            dirty.remove(chatId)
            stored(chatId)?.let { save(chatId, it.copy(synced = true)) }
        }
    }

    /** Only a real logout removes the local single-account cache; a process restart never does. */
    fun signedOut() {
        synchronized(guard) {
            revisions.values.forEach { it.incrementAndGet() }
            topicRevisions.values.forEach { it.incrementAndGet() }
            topicDirty.clear()
            topicStates.values.forEach { it.value = MessageDraft() }
            dirty.clear()
            editedWhileOpen.clear()
            states.values.forEach { it.value = MessageDraft() }
            prefs.edit().clear().apply()
        }
    }
}
