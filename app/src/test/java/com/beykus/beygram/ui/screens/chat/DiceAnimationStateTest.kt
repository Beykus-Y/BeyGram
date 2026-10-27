package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.DiceStickerAsset
import com.beykus.beygram.domain.model.MessageDiceInfo
import com.beykus.beygram.domain.model.MessageDiceState
import com.beykus.beygram.domain.model.MessageIdReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceAnimationStateTest {
    private val asset = DiceStickerAsset(fileId = 1, mimeType = "application/x-tgsticker")
    private val initial = MessageDiceState.Regular(asset)
    private val final = MessageDiceState.Regular(DiceStickerAsset(fileId = 2, mimeType = "application/x-tgsticker"))
    private val key = DiceAnimationKey(accountId = "account", chatId = 10L, messageId = 20L)

    /** Stands in for the preferences: [stored] is what a restarted process reads back. */
    private class FakeDicePlaybackPersistence(var stored: Map<DiceAnimationKey, Long> = emptyMap()) : DicePlaybackPersistence {
        var loads = 0

        override fun load(): Map<DiceAnimationKey, Long> {
            loads += 1
            return stored
        }

        override fun save(entries: Map<DiceAnimationKey, Long>) {
            stored = entries.toMap()
        }
    }

    @Test
    fun `a live throw is claimed once and continues across a recreated row`() {
        val store = DiceAnimationPlaybackStore()

        assertTrue(store.begin(key, eligible = true))
        assertTrue(store.begin(key, eligible = false))
        assertFalse(store.isDone(key))

        store.finish(key)
        assertTrue(store.isDone(key))
        assertFalse(store.begin(key, eligible = true))
    }

    @Test
    fun `an already consumed throw does not start on chat reopening`() {
        val store = DiceAnimationPlaybackStore()
        assertFalse(store.begin(key, eligible = false))
        assertFalse(store.canBegin(key, eligible = false))
    }

    @Test
    fun `accounts chats and messages do not share consumption`() {
        val store = DiceAnimationPlaybackStore()
        assertTrue(store.begin(key, eligible = true))
        store.finish(key)

        assertTrue(store.begin(key.copy(accountId = "other"), eligible = true))
        assertTrue(store.begin(key.copy(chatId = 11L), eligible = true))
        assertTrue(store.begin(key.copy(messageId = 21L), eligible = true))
    }

    @Test
    fun `a throw that never reaches its result expires instead of leaking`() {
        var now = 0L
        val store = DiceAnimationPlaybackStore(continuationWindowMillis = 1_000L, clock = { now })
        assertTrue(store.begin(key, eligible = true))
        now = 1_001L

        assertFalse(store.begin(key, eligible = false))
        assertTrue(store.isDone(key))
    }

    @Test
    fun `consumed entries stay bounded`() {
        val store = DiceAnimationPlaybackStore(maxEntries = 2)
        repeat(5) { index ->
            store.finish(key.copy(messageId = index.toLong()))
        }
        assertEquals(2, store.size())
    }

    @Test
    fun `a finished throw stays consumed after a process restart`() {
        var now = 1_000_000L
        val disk = FakeDicePlaybackPersistence()
        val beforeRestart = DiceAnimationPlaybackStore(clock = { now }, persistence = disk)
        assertTrue(beforeRestart.begin(key, eligible = true))
        now += 3_000L
        beforeRestart.finish(key)

        // A new process starts with empty memory and reads the same preferences.
        val afterRestart = DiceAnimationPlaybackStore(clock = { now }, persistence = disk)
        assertFalse(afterRestart.begin(key, eligible = true))
        assertFalse(afterRestart.canBegin(key, eligible = true))
        assertTrue(afterRestart.isDone(key))
        // Only the finished throw is consumed; another recent throw still plays.
        assertTrue(afterRestart.begin(key.copy(messageId = 21L), eligible = true))
    }

    @Test
    fun `finished throws older than the retention window are not saved`() {
        var now = 0L
        val disk = FakeDicePlaybackPersistence()
        val store = DiceAnimationPlaybackStore(clock = { now }, persistence = disk)
        store.finish(key)
        now += DICE_PLAYBACK_RETENTION_MILLIS + 1_000L
        store.finish(key.copy(messageId = 21L))

        assertEquals(setOf(key.copy(messageId = 21L)), disk.stored.keys)
    }

    @Test
    fun `persisted throws older than the retention window are ignored on load`() {
        val now = 10_000_000L
        val oldKey = key
        val recentKey = key.copy(messageId = 21L)
        val disk = FakeDicePlaybackPersistence(
            stored = mapOf(
                oldKey to now - DICE_PLAYBACK_RETENTION_MILLIS - 1_000L,
                recentKey to now - 1_000L
            )
        )
        val store = DiceAnimationPlaybackStore(clock = { now }, persistence = disk)

        assertTrue(store.begin(oldKey, eligible = true))
        assertFalse(store.begin(recentKey, eligible = true))
    }

    @Test
    fun `saving keeps only the newest finished throws up to the cap`() {
        var now = 0L
        val disk = FakeDicePlaybackPersistence()
        val store = DiceAnimationPlaybackStore(maxEntries = 2, clock = { now }, persistence = disk)
        repeat(3) { index ->
            now = index * 1_000L
            store.finish(key.copy(messageId = index.toLong()))
        }

        assertEquals(setOf(key.copy(messageId = 1L), key.copy(messageId = 2L)), disk.stored.keys)
    }

    @Test
    fun `attach merges persisted throws once and ignores later attachments`() {
        val now = 1_000L
        val store = DiceAnimationPlaybackStore(clock = { now })
        assertTrue(store.begin(key.copy(messageId = 1L), eligible = true))
        val first = FakeDicePlaybackPersistence(stored = mapOf(key to now))
        val second = FakeDicePlaybackPersistence(stored = mapOf(key.copy(messageId = 2L) to now))

        store.attach(first)
        store.attach(second)

        assertFalse(store.begin(key, eligible = true))
        assertTrue(store.begin(key.copy(messageId = 2L), eligible = true))
        assertEquals(1, first.loads)
        assertEquals(0, second.loads)
    }

    @Test
    fun `a persisted throw keeps an account id that contains the separator`() {
        val account = key.copy(accountId = "acc|ount|7")
        val line = encodeDicePlaybackEntry(account, finishedAtMillis = 42L)

        assertEquals(account to 42L, decodeDicePlaybackEntry(line))
        assertNull(decodeDicePlaybackEntry("20|x|42|account"))
        assertNull(decodeDicePlaybackEntry("20|20|42"))
    }

    @Test
    fun `final value selects the final state and never the initial throw`() {
        val dice = MessageDiceInfo("🎲", value = 4, initialState = initial, finalState = final)
        assertEquals(final, dice.displayState)
        assertTrue(dice.hasFinalValue)
    }

    @Test
    fun `a final value without a final asset falls back to the result`() {
        val dice = MessageDiceInfo("🎲", value = 4, initialState = initial, finalState = null)
        assertNull(dice.displayState)
        assertEquals("🎲", dice.fallbackEmoji)
    }

    @Test
    fun `slot machine and jackpot are recognised`() {
        val slot = MessageDiceState.SlotMachine(asset, asset, asset, asset, asset)
        val dice = MessageDiceInfo("🎰", value = 64, initialState = slot, finalState = slot)
        assertTrue(dice.isSlotMachine)
        assertTrue(dice.isJackpot)
        assertEquals(5, slot.layers.size)

        // Even before TDLib supplies the stickers, the emoji identifies the result and the jackpot fallback.
        val withoutAssets = MessageDiceInfo("🎰", value = 64)
        assertTrue(withoutAssets.isSlotMachine)
        assertTrue(withoutAssets.isJackpot)
    }

    @Test
    fun `a blank emoji has a meaningful fallback`() {
        assertEquals("🎲", MessageDiceInfo("", value = 0).fallbackEmoji)
    }

    @Test
    fun `temporary message id resolves to the original throw`() {
        val replacements = listOf(
            MessageIdReplacement(oldId = -5L, newId = 100L),
            MessageIdReplacement(oldId = 100L, newId = 200L)
        )
        assertEquals(-5L, resolveOriginalMessageId(200L, replacements))
        assertEquals(-5L, resolveOriginalMessageId(100L, replacements))
        assertEquals(300L, resolveOriginalMessageId(300L, replacements))
    }

    @Test
    fun `a recent outgoing throw stays eligible after the id replacement`() {
        assertTrue(isRecentDiceMessage(dateSeconds = 1_000L, nowSeconds = 1_060L))
        assertFalse(isRecentDiceMessage(dateSeconds = 1_000L, nowSeconds = 1_061L))
        assertFalse(isRecentDiceMessage(dateSeconds = 0L, nowSeconds = 1_060L))
        assertFalse(isRecentDiceMessage(dateSeconds = 2_000L, nowSeconds = 1_060L))
    }

    @Test
    fun `a cyclic replacement log still terminates`() {
        val replacements = listOf(
            MessageIdReplacement(oldId = 2L, newId = 1L),
            MessageIdReplacement(oldId = 1L, newId = 2L)
        )
        assertTrue(resolveOriginalMessageId(1L, replacements) in setOf(1L, 2L))
    }
}
