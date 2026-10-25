package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageSelfDestruct
import com.beykus.beygram.domain.model.SecretChatDetails
import com.beykus.beygram.domain.model.SecretChatState
import com.beykus.beygram.domain.model.SecretChatTtl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.drinkless.tdlib.TdApi

/**
 * Secret-chat lifecycle bridge. TDLib exposes the secret chat id only through ChatTypeSecret, while the state and partner
 * live in SecretChat. Details stay in memory only: a secret chat is device-specific and must never be restored from an
 * app backup as if it were a cloud chat.
 */
internal class TdSecretChats(private val requests: TdRequests) {
    private val _details = MutableStateFlow<Map<Long, SecretChatDetails>>(emptyMap())
    val details: StateFlow<Map<Long, SecretChatDetails>> = _details.asStateFlow()

    suspend fun create(userId: Long): TdApi.Chat {
        validateUser(userId)
        val chat = requests(TdApi.CreateNewSecretChat(userId))
        // The chat already exists even if the follow-up details request is slow or unavailable.
        try {
            load(chat.id)
        } catch (_: TimeoutCancellationException) {
            // The details request outlived its own timeout; the chat itself was created successfully.
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The chat screen refreshes details again after navigation.
        }
        return chat
    }

    suspend fun load(chatId: Long): SecretChatDetails {
        val chat = requests(TdApi.GetChat(chatId))
        val type = chat.type as? TdApi.ChatTypeSecret
            ?: throw UserMessageException(UiText.Res(R.string.err_secret_chat_not_secret))
        val secret = requests(TdApi.GetSecretChat(type.secretChatId))
        return secret.toDetails(chatId, chat.messageAutoDeleteTime).also(::put)
    }

    suspend fun setTtl(chatId: Long, seconds: Int) {
        if (!SecretChatTtl.isValid(seconds)) {
            throw UserMessageException(UiText.Res(R.string.err_secret_chat_ttl_invalid))
        }
        val chat = requests(TdApi.GetChat(chatId))
        if (chat.type !is TdApi.ChatTypeSecret) {
            throw UserMessageException(UiText.Res(R.string.err_secret_chat_not_secret))
        }
        requests(TdApi.SetChatMessageAutoDeleteTime(chatId, seconds))
        applyTtl(chatId, seconds)
    }

    suspend fun close(chatId: Long) {
        val chat = requests(TdApi.GetChat(chatId))
        val type = chat.type as? TdApi.ChatTypeSecret
            ?: throw UserMessageException(UiText.Res(R.string.err_secret_chat_not_secret))
        requests(TdApi.CloseSecretChat(type.secretChatId))
        applyState(chatId, SecretChatState.Closed)
    }

    /** Applies UpdateSecretChat. The chat id is resolved by the repository from the chat cache. */
    fun apply(chatId: Long, secret: TdApi.SecretChat, ttlSeconds: Int) {
        // TdProfiles does not cache UpdateChatMessageAutoDeleteTime, so a lifecycle/key update can carry an older TTL.
        // Keep the directory's current value whenever it was already loaded.
        put(secret.toDetails(chatId, mergedSecretChatTtl(_details.value[chatId]?.ttlSeconds, ttlSeconds)))
    }

    fun applyTtl(chatId: Long, ttlSeconds: Int) {
        _details.update { current ->
            val existing = current[chatId] ?: return@update current
            current + (chatId to existing.copy(ttlSeconds = ttlSeconds.coerceAtLeast(0)))
        }
    }

    fun applyState(chatId: Long, state: SecretChatState) {
        _details.update { current ->
            val existing = current[chatId] ?: return@update current
            current + (chatId to existing.copy(state = state))
        }
    }

    /** Forget every in-memory detail on logout; no secret-chat metadata survives the session. */
    fun clear() {
        _details.value = emptyMap()
    }

    fun remove(chatId: Long) {
        _details.update { it - chatId }
    }

    private fun put(value: SecretChatDetails) {
        _details.update { it + (value.chatId to value) }
    }

    private suspend fun validateUser(userId: Long) {
        if (userId <= 0L) throw UserMessageException(UiText.Res(R.string.err_secret_chat_self))
        val user = requests(TdApi.GetUser(userId))
        val me = requests(TdApi.GetMe())
        when {
            user.id == me.id -> throw UserMessageException(UiText.Res(R.string.err_secret_chat_self))
            user.type is TdApi.UserTypeBot -> throw UserMessageException(UiText.Res(R.string.err_secret_chat_bot))
            user.type is TdApi.UserTypeDeleted -> throw UserMessageException(UiText.Res(R.string.err_secret_chat_deleted))
        }
    }
}

/** TDLib reports secret chat state through subclasses; keep the mapping in one place. */
internal fun secretChatStateOf(state: TdApi.SecretChatState): SecretChatState = when (state) {
    is TdApi.SecretChatStatePending -> SecretChatState.Pending
    is TdApi.SecretChatStateReady -> SecretChatState.Ready
    else -> SecretChatState.Closed
}

/** Prefers the TTL already published by UpdateChatMessageAutoDeleteTime over a stale Chat snapshot. */
internal fun mergedSecretChatTtl(currentTtl: Int?, fallbackTtl: Int): Int =
    currentTtl?.coerceAtLeast(0) ?: fallbackTtl.coerceAtLeast(0)

internal fun TdApi.SecretChat.toDetails(chatId: Long, ttlSeconds: Int): SecretChatDetails = SecretChatDetails(
    chatId = chatId,
    secretChatId = id,
    userId = userId,
    state = secretChatStateOf(state),
    isOutbound = isOutbound,
    ttlSeconds = ttlSeconds.coerceAtLeast(0),
    layer = layer
)

/** Only ChatTypeSecret has a secret chat id; everything else is a cloud chat. */
internal fun TdApi.ChatType.secretChatIdOrNull(): Int? = (this as? TdApi.ChatTypeSecret)?.secretChatId

internal fun messageSelfDestructOf(type: TdApi.MessageSelfDestructType?): MessageSelfDestruct? = when (type) {
    is TdApi.MessageSelfDestructTypeImmediately -> MessageSelfDestruct.Immediately
    is TdApi.MessageSelfDestructTypeTimer -> MessageSelfDestruct.Timer(type.selfDestructTime)
    else -> null
}

/** TDLib keeps an expired view-once attachment as MessageExpired*; the bubble needs a readable placeholder. */
internal fun expiredMediaKindOf(content: TdApi.MessageContent): MediaKind? = when (content) {
    is TdApi.MessageExpiredPhoto -> MediaKind.Photo
    is TdApi.MessageExpiredVideo -> MediaKind.Video
    is TdApi.MessageExpiredVideoNote -> MediaKind.VideoNote
    is TdApi.MessageExpiredVoiceNote -> MediaKind.Voice
    else -> null
}
