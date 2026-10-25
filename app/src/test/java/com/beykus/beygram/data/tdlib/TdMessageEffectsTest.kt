package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MessageEffect
import com.beykus.beygram.domain.model.MessageEffectKind
import com.beykus.beygram.domain.model.MessageEffectSticker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TdMessageEffectsTest {
    private fun sticker(fileId: Int, emoji: String, format: TdApi.StickerFormat): TdApi.Sticker = TdApi.Sticker().apply {
        sticker = TdApi.File().apply { id = fileId }
        this.emoji = emoji
        width = 512
        height = 512
        this.format = format
    }

    @Test
    fun emojiReactionMapsBothAnimationsAndTheMimeType() {
        val effect = TdApi.MessageEffect().apply {
            emoji = "🎉"
            isPremium = true
            type = TdApi.MessageEffectTypeEmojiReaction(
                sticker(11, "🎉", TdApi.StickerFormatTgs()),
                sticker(12, "🎉", TdApi.StickerFormatWebp())
            )
        }

        val mapped = effect.toMessageEffect(99L)

        assertEquals(99L, mapped.effectId)
        assertEquals("🎉", mapped.emoji)
        assertEquals(MessageEffectKind.EmojiReaction, mapped.kind)
        assertEquals(true, mapped.isPremium)
        assertEquals(MessageEffectSticker(11, "🎉", "application/x-tgsticker", 512, 512), mapped.selectAnimation)
        assertEquals(MessageEffectSticker(12, "🎉", "image/webp", 512, 512), mapped.animation)
        assertEquals(mapped.animation, mapped.primarySticker)
    }

    @Test
    fun premiumStickerUsesTheDedicatedEffectAnimationAndKeepsItsArtworkAsFallback() {
        val premiumSticker = sticker(21, "⭐", TdApi.StickerFormatTgs()).apply {
            fullType = TdApi.StickerFullTypeRegular(TdApi.File().apply { id = 31 })
        }
        val effect = TdApi.MessageEffect().apply {
            emoji = "⭐"
            type = TdApi.MessageEffectTypePremiumSticker(premiumSticker)
            staticIcon = sticker(22, "⭐", TdApi.StickerFormatWebp())
        }

        val mapped = effect.toMessageEffect(7L)

        assertEquals(MessageEffectKind.PremiumSticker, mapped.kind)
        assertEquals(31, mapped.animation?.fileId)
        assertEquals("application/x-tgsticker", mapped.animation?.mimeType)
        assertEquals(21, mapped.fallbackSticker?.fileId)
        assertEquals(22, mapped.staticIcon?.fileId)
    }

    @Test
    fun premiumStickerWithoutDedicatedAnimationFallsBackToTheStickerItself() {
        val effect = TdApi.MessageEffect().apply {
            emoji = "⭐"
            type = TdApi.MessageEffectTypePremiumSticker(sticker(21, "⭐", TdApi.StickerFormatTgs()))
        }

        val mapped = effect.toMessageEffect(7L)

        assertEquals(21, mapped.animation?.fileId)
        assertEquals(null, mapped.fallbackSticker)
    }

    @Test
    fun unansweredLookupIsRetriedBeforeItIsCached() = runTest {
        val found = MessageEffect(effectId = 42L, emoji = "🔥")
        var asks = 0
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val effects = TdMessageEffects(
                client = { error("the lookup is replaced in this test") },
                scope = scope,
                lookup = {
                    asks++
                    if (asks == 1) MessageEffectAnswer.Unanswered else MessageEffectAnswer.Found(found)
                }
            )
            val observed = effects.observe(42L)

            advanceTimeBy(2_000L)

            assertEquals(found, observed.value)
            assertEquals(2, asks)
            assertSame(observed, effects.observe(42L))
            assertEquals(2, asks)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun laterObservationRetriesAnExhaustedTransientFailure() = runTest {
        var asks = 0
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val effects = TdMessageEffects(
                client = { error("the lookup is replaced in this test") },
                scope = scope,
                lookup = {
                    asks++
                    MessageEffectAnswer.Unanswered
                }
            )
            val observed = effects.observe(42L)

            // First attempt plus two bounded retries (1.5 s + 4 s).
            advanceTimeBy(6_000L)
            assertEquals(3, asks)
            assertNull(observed.value)

            // A later observer restarts the lookup on the same flow instead of keeping a permanent null cache.
            assertSame(observed, effects.observe(42L))
            advanceTimeBy(6_000L)
            assertEquals(6, asks)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun missingEffectIsNotRetriedForever() = runTest {
        var asks = 0
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val effects = TdMessageEffects(
                client = { error("the lookup is replaced in this test") },
                scope = scope,
                lookup = {
                    asks++
                    MessageEffectAnswer.Missing
                }
            )
            val observed = effects.observe(8L)

            advanceTimeBy(60_000L)

            assertNull(observed.value)
            assertEquals(1, asks)
        } finally {
            scope.cancel()
        }
    }
}
