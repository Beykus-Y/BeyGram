package com.beykus.beygram.ui.screens.chat

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PackUsageStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        PackUsageStore.clearMemoryCache()
        context.getSharedPreferences("emoji_panel", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun theModeIsOnByDefaultAndKeepsTheIncomingOrderUntilUsageExists() {
        val store = PackUsageStore(context, "1", PackKind.Sticker)
        assertTrue(store.isDynamic())
        val packs = listOf(10L, 20L, 30L)
        // No usage yet: the server/manual order is also the stable base of the usage order.
        assertEquals(packs, store.ordered(packs) { it })
        store.setDynamic(false)
        assertFalse(store.isDynamic())
        assertSame(packs, store.ordered(packs) { it })
    }

    @Test
    fun usageSortsByCountThenRecentUseThenTheOriginalOrder() {
        var now = 0L
        val store = PackUsageStore(context, "1", PackKind.Sticker) { now }
        store.setDynamic(true)
        store.record(10L)
        now = 1L
        store.record(10L)
        now = 2L
        store.record(20L)
        now = 3L
        store.record(20L)
        // Equal counts: the pack used later goes first; a pack without usage keeps the server order behind them.
        assertEquals(listOf(20L, 10L, 30L), store.ordered(listOf(10L, 20L, 30L)) { it })
    }

    @Test
    fun theModeAndUsageAreIsolatedPerAccountAndKind() {
        val sticker = PackUsageStore(context, "1", PackKind.Sticker)
        sticker.setDynamic(false)
        sticker.record(10L)
        val other = PackUsageStore(context, "2", PackKind.Sticker)
        assertTrue(other.isDynamic())
        assertEquals(emptyMap<Long, Long>(), other.counts())
        val emoji = PackUsageStore(context, "1", PackKind.CustomEmoji)
        // One switch covers both kinds; only the usage lists are separate.
        assertFalse(emoji.isDynamic())
        assertEquals(emptyMap<Long, Long>(), emoji.counts())
    }

    @Test
    fun disablingRestoresTheServerOrderWithoutLosingUsage() {
        val store = PackUsageStore(context, "1", PackKind.Sticker)
        store.setDynamic(true)
        store.record(30L)
        store.setDynamic(false)
        assertEquals(listOf(10L, 20L, 30L), store.ordered(listOf(10L, 20L, 30L)) { it })
        store.setDynamic(true)
        assertEquals(listOf(30L, 10L, 20L), store.ordered(listOf(10L, 20L, 30L)) { it })
    }

    @Test
    fun anUnknownPackDoesNotChangeTheOrder() {
        val store = PackUsageStore(context, "1", PackKind.Sticker)
        store.setDynamic(true)
        store.record(99L)
        assertEquals(listOf(10L, 20L), store.ordered(listOf(10L, 20L)) { it })
    }

    @Test
    fun negativeSetIdsArePreservedAndClearAccountIsScoped() {
        val negId = -9876543210L
        val store1 = PackUsageStore(context, "1", PackKind.Sticker)
        val store2 = PackUsageStore(context, "2", PackKind.Sticker)
        store1.record(negId)
        store2.record(42L)

        assertEquals(1L, store1.counts()[negId])

        PackUsageStore.clearAccount(context, "1")
        assertTrue(store1.counts().isEmpty())
        assertEquals(1L, store2.counts()[42L])
    }
}