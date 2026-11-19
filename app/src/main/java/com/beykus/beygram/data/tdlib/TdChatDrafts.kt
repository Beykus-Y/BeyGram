package com.beykus.beygram.data.tdlib

import android.content.Context
import android.content.SharedPreferences
import com.beykus.beygram.domain.model.DraftStore
import com.beykus.beygram.domain.model.DraftSyncEngine
import com.beykus.beygram.domain.model.MessageDraft
import com.beykus.beygram.domain.model.MessageDraftCodec
import com.beykus.beygram.domain.model.RemoteDraft
import com.beykus.beygram.domain.model.StoredMessageDraft
import com.beykus.beygram.domain.model.validTextEntities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

private const val WRITE_DELAY_MS = 400L
private const val DRAFTS_PREFS = "chat_message_drafts"
private const val CHAT_PREFIX = "chat_"
private const val TOPIC_PREFIX = "topic_"

/**
 * Local-first composer drafts plus TDLib synchronization, for chats and forum topics.
 * UI edits update a StateFlow and Android's private preferences immediately; the remote request is debounced.
 * The reconciliation rules live in [DraftSyncEngine]. A stale TDLib update never replaces text changed in an open editor,
 * and a synced backup yields to TDLib, so a draft cleared on another device stays cleared.
 */
internal fun tdDraftPreferencesName(accountId: String): String {
    require(TdAccountStorageLayout.isValidAccountId(accountId)) {
        "Invalid account identifier for local drafts"
    }
    return if (accountId == TdAccountStorageLayout.LEGACY_ACCOUNT_ID) DRAFTS_PREFS
    else "${DRAFTS_PREFS}_$accountId"
}

/** Stores one family of drafts under [prefix] in the shared preferences; [suffix] renders a key after the prefix. */
private class PrefsDraftStore<K>(
    private val prefs: SharedPreferences,
    private val prefix: String,
    private val suffix: (K) -> String,
    private val parseSuffix: (String) -> K?
) : DraftStore<K> {
    override fun read(key: K): StoredMessageDraft? =
        prefs.getString(prefix + suffix(key), null)?.let(MessageDraftCodec::decode)

    override fun write(key: K, value: StoredMessageDraft) {
        prefs.edit().putString(prefix + suffix(key), MessageDraftCodec.encode(value)).apply()
    }

    override fun unsyncedKeys(): List<K> = prefs.all.mapNotNull { (name, raw) ->
        if (!name.startsWith(prefix) || raw !is String) return@mapNotNull null
        if (MessageDraftCodec.decode(raw)?.synced != false) return@mapNotNull null
        parseSuffix(name.removePrefix(prefix))
    }

    override fun clearAll() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }
}

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

    private val chats = DraftSyncEngine<Long>(
        store = PrefsDraftStore<Long>(prefs, CHAT_PREFIX, { it.toString() }, { it.toLongOrNull() }),
        scope = scope,
        now = { System.currentTimeMillis() },
        push = { chatId, draft -> pushDraft(chatId, null, draft) },
        fetch = { chatId -> fetchChat(chatId) },
        writeDelayMillis = WRITE_DELAY_MS
    )

    private val topics = DraftSyncEngine<Pair<Long, Long>>(
        store = PrefsDraftStore<Pair<Long, Long>>(
            prefs, TOPIC_PREFIX,
            { "${it.first}_${it.second}" },
            { rest ->
                // Chat ids can be negative, so the topic id is the part after the last underscore.
                val split = rest.lastIndexOf('_')
                val chatId = if (split < 0) null else rest.substring(0, split).toLongOrNull()
                val topicId = if (split < 0) null else rest.substring(split + 1).toLongOrNull()
                if (chatId == null || topicId == null) null else chatId to topicId
            }
        ),
        scope = scope,
        now = { System.currentTimeMillis() },
        push = { key, draft -> pushDraft(key.first, key.second, draft) },
        fetch = { key -> fetchTopic(key.first, key.second) },
        writeDelayMillis = WRITE_DELAY_MS
    )

    fun observe(chatId: Long): StateFlow<MessageDraft> = chats.observe(chatId)

    /** Synchronous state and disk-queue update, safe to call from a Compose callback. */
    fun update(chatId: Long, transform: (MessageDraft) -> MessageDraft) = chats.update(chatId, transform)

    fun observeTopic(chatId: Long, topicId: Long): StateFlow<MessageDraft> = topics.observe(chatId to topicId)

    fun updateTopic(chatId: Long, topicId: Long, transform: (MessageDraft) -> MessageDraft) =
        topics.update(chatId to topicId, transform)

    /** Server refreshes do not discard newer local edits. A null remote draft is a valid cloud clear. */
    fun receive(chatId: Long, raw: TdApi.DraftMessage?) = chats.receive(chatId, remoteOf(raw))

    fun receiveTopic(chatId: Long, topicId: Long, raw: TdApi.DraftMessage?) {
        if (topicId <= 0L) return
        topics.receive(chatId to topicId, remoteOf(raw))
    }

    fun opened(chatId: Long) = chats.opened(chatId)

    fun closed(chatId: Long) = chats.closed(chatId)

    fun topicOpened(chatId: Long, topicId: Long) {
        if (topicId > 0L) topics.opened(chatId to topicId)
    }

    fun topicClosed(chatId: Long, topicId: Long) {
        if (topicId > 0L) topics.closed(chatId to topicId)
    }

    fun connectionReady() {
        chats.connectionReady()
        topics.connectionReady()
    }

    /** Close cancels pending writes without clearing a still-authorized draft. */
    fun dispose() {
        scope.cancel()
    }

    /** Only a real logout removes the local cache; a process restart never does. */
    fun signedOut() {
        chats.signedOut()
        topics.signedOut()
        prefs.edit().clear().apply()
    }

    private suspend fun pushDraft(chatId: Long, topicId: Long?, draft: MessageDraft): Boolean {
        val failure = sendDraft(chatId, topicId, draft) ?: return true
        // A deleted reply target is rejected with 400: the text is still sent, without the reply.
        if (failure.code != 400 || draft.replyToMessageId == null) return false
        return sendDraft(chatId, topicId, draft.copy(replyToMessageId = null)) == null
    }

    /** Null on success, otherwise the TDLib error. Timeouts and cancellation propagate to the engine. */
    private suspend fun sendDraft(chatId: Long, topicId: Long?, draft: MessageDraft): TdRequestError? = try {
        val forumTopic = topicId?.let { TdApi.MessageTopicForum(it.toInt()) }
        request(TdApi.SetChatDraftMessage().apply {
            this.chatId = chatId
            this.topicId = forumTopic
            draftMessage = toTd(draft)
        })
        null
    } catch (error: TdRequestError) {
        error
    }

    private suspend fun fetchChat(chatId: Long): RemoteDraft? = try {
        remoteOf(request(TdApi.GetChat(chatId)).draftMessage)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        null
    }

    private suspend fun fetchTopic(chatId: Long, topicId: Long): RemoteDraft? = try {
        remoteOf(request(TdApi.GetForumTopic(chatId, topicId.toInt())).draftMessage)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        null
    }

    private fun remoteOf(raw: TdApi.DraftMessage?): RemoteDraft =
        RemoteDraft(fromTd(raw), (raw?.date ?: 0).toLong() * 1000L)

    /** Null for a draft type this app cannot show; such a draft is left untouched on both sides. */
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
}
