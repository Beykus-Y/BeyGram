package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MessageEffect
import com.beykus.beygram.domain.model.MessageEffectKind
import com.beykus.beygram.domain.model.MessageEffectSticker
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/** TDLib's answer for one effect id: found, known to be missing, or temporarily unanswered. */
internal sealed interface MessageEffectAnswer {
    class Found(val effect: MessageEffect) : MessageEffectAnswer
    object Missing : MessageEffectAnswer
    object Unanswered : MessageEffectAnswer
}

/**
 * Message effect metadata by id. Only the overlay for a fresh message asks for an id, so opening a chat or scrolling
 * through old messages never triggers a `getMessageEffect` request. A successful answer stays cached for the process.
 */
internal class TdMessageEffects(
    client: () -> Client,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val lookup: suspend (Long) -> MessageEffectAnswer = { effectId ->
        lookupMessageEffect(TdRequests(client), effectId)
    }
) {
    private val flows = ConcurrentHashMap<Long, MutableStateFlow<MessageEffect?>>()

    /** The effect behind [effectId]; null until TDLib answers and for ids TDLib does not know. */
    fun observe(effectId: Long): StateFlow<MessageEffect?> {
        flows[effectId]?.let { return it }
        val flow = MutableStateFlow<MessageEffect?>(null)
        flows.putIfAbsent(effectId, flow)?.let { return it }
        scope.launch {
            var answer = lookup(effectId)
            var attempt = 0
            while (answer !is MessageEffectAnswer.Found && answer == MessageEffectAnswer.Unanswered &&
                attempt < RETRY_PAUSES_MILLIS.size
            ) {
                delay(RETRY_PAUSES_MILLIS[attempt])
                attempt++
                answer = lookup(effectId)
            }
            (answer as? MessageEffectAnswer.Found)?.let { flow.value = it.effect }
        }
        return flow
    }

    private companion object {
        val RETRY_PAUSES_MILLIS = longArrayOf(1_500L, 4_000L)
    }
}

private suspend fun lookupMessageEffect(requests: TdRequests, effectId: Long): MessageEffectAnswer {
    val effect = runCatching { requests(TdApi.GetMessageEffect(effectId)) }.getOrNull()
        ?: return MessageEffectAnswer.Unanswered
    return MessageEffectAnswer.Found(effect.toMessageEffect(effectId))
}

/**
 * Maps this TDLib version's two effect types. The effect id is passed separately because older generated TDLib
 * bindings do not expose `MessageEffect.id`.
 */
internal fun TdApi.MessageEffect.toMessageEffect(effectId: Long): MessageEffect {
    val effectType = type
    val premium = effectType as? TdApi.MessageEffectTypePremiumSticker
    val regularPremiumSticker = premium?.sticker?.toEffectSticker()
    // A premium sticker's ordinary artwork is not its full-screen effect: TDLib keeps the dedicated
    // effect animation in fullType.premiumAnimation. Fall back to the sticker itself only when it is absent.
    val premiumAnimation = premium?.sticker?.toPremiumAnimationSticker() ?: regularPremiumSticker
    val animation = when (effectType) {
        is TdApi.MessageEffectTypeEmojiReaction -> effectType.effectAnimation.toEffectSticker()
        is TdApi.MessageEffectTypePremiumSticker -> premiumAnimation
        else -> null
    }
    val select = (effectType as? TdApi.MessageEffectTypeEmojiReaction)?.selectAnimation?.toEffectSticker()
    return MessageEffect(
        effectId = effectId,
        emoji = emoji.orEmpty(),
        kind = when (effectType) {
            is TdApi.MessageEffectTypeEmojiReaction -> MessageEffectKind.EmojiReaction
            is TdApi.MessageEffectTypePremiumSticker -> MessageEffectKind.PremiumSticker
            else -> MessageEffectKind.Unknown
        },
        isPremium = isPremium,
        animation = animation,
        selectAnimation = select,
        staticIcon = staticIcon?.toEffectSticker(),
        fallbackSticker = regularPremiumSticker?.takeIf { it.fileId != animation?.fileId }
    )
}

private fun TdApi.Sticker.toPremiumAnimationSticker(): MessageEffectSticker? =
    ((fullType as? TdApi.StickerFullTypeRegular)?.premiumAnimation)?.let(::effectSticker)

private fun TdApi.Sticker.toEffectSticker(): MessageEffectSticker? = sticker?.let(::effectSticker)

private fun TdApi.Sticker.effectSticker(file: TdApi.File) = MessageEffectSticker(
    fileId = file.id,
    emoji = emoji.orEmpty(),
    mimeType = stickerMimeType(format),
    width = width,
    height = height
)
