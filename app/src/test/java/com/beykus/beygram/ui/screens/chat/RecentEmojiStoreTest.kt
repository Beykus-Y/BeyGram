package com.beykus.beygram.ui.screens.chat

import android.content.Context
import com.beykus.beygram.domain.model.RecentEmojiHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentEmojiStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("emoji_panel", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun recoveredEmojiFollowLocalOnesWithoutDuplicates() {
        val store = RecentEmojiStore(context, "1")
        store.record("🎉")
        store.record("❤️")
        val merged = store.mergeRecovered(RecentEmojiHistory(unicode = listOf("❤️", "🔥", "🎉")))
        // Local order is kept; only unseen recovered entries are appended.
        assertEquals(listOf("❤️", "🎉", "🔥"), merged)
        assertEquals(merged, store.load())
    }

    @Test
    fun customRecentsMergeAndRecordNewestFirst() {
        val store = RecentEmojiStore(context, "1")
        store.recordCustom(10L)
        store.recordCustom(20L)
        assertEquals(listOf(20L, 10L), store.loadCustom())
        assertEquals(listOf(20L, 10L, 30L), store.mergeRecoveredCustom(listOf(10L, 30L)))
    }

    @Test
    fun anExplicitClearSuppressesRecovery() {
        val store = RecentEmojiStore(context, "1")
        store.record("🎉")
        store.recordCustom(10L)
        val (unicode, custom) = store.clearAll()
        assertTrue(unicode.isEmpty())
        assertTrue(custom.isEmpty())
        assertTrue(store.load().isEmpty())
        assertTrue(store.loadCustom().isEmpty())
        assertTrue(store.restoreSuppressed())
        // Old history must not come back after the user cleared it on purpose.
        assertFalse(store.mayAttemptRestore(System.currentTimeMillis()))
    }

    @Test
    fun aFailedAttemptIsRetriedOnlyAfterThePause() {
        val store = RecentEmojiStore(context, "1")
        val start = 1_000L
        store.markRestoreAttempt(start)
        assertFalse(store.mayAttemptRestore(start + 1_000L))
        assertTrue(store.mayAttemptRestore(start + 16 * 60_000L))
        store.markRestoreDone()
        assertFalse(store.mayAttemptRestore(start + 60 * 60_000L))
    }

    @Test
    fun recentsAreIsolatedByAccount() {
        RecentEmojiStore(context, "1").record("🎉")
        assertTrue(RecentEmojiStore(context, "2").load().isEmpty())
        assertEquals(listOf("🎉"), RecentEmojiStore(context, "1").load())
    }

    @Test
    fun legacyRecentsMigrateOnceOnlyToLegacyAccount() {
        context.getSharedPreferences("emoji_panel", Context.MODE_PRIVATE)
            .edit()
            .putString("recent", "🔥,🎉")
            .commit()

        val secondary = RecentEmojiStore(context, "2")
        assertTrue(secondary.load().isEmpty())

        val legacy = RecentEmojiStore(context, "legacy")
        assertEquals(listOf("🔥", "🎉"), legacy.load())
        assertFalse(context.getSharedPreferences("emoji_panel", Context.MODE_PRIVATE).contains("recent"))
    }

    @Test
    fun suppressedClearBlocksLateMergeAndMarkDone() {
        val store = RecentEmojiStore(context, "1")
        store.clearAll()
        assertTrue(store.mergeRecovered(RecentEmojiHistory(unicode = listOf("🔥"))).isEmpty())
        assertTrue(store.mergeRecoveredCustom(listOf(42L)).isEmpty())
        store.markRestoreDone()
        assertFalse(store.restoreDone())
        assertTrue(store.load().isEmpty())
        assertTrue(store.loadCustom().isEmpty())
    }

    @Test
    fun clearAccountResetsRecentsAndRestoreFlagsForSlotReuse() {
        val store = RecentEmojiStore(context, "1")
        store.record("🎉")
        store.recordCustom(10L)
        store.markRestoreDone()

        RecentEmojiStore.clearAccount(context, "1")

        val reused = RecentEmojiStore(context, "1")
        assertTrue(reused.load().isEmpty())
        assertTrue(reused.loadCustom().isEmpty())
        assertFalse(reused.restoreDone())
        assertFalse(reused.restoreSuppressed())
        assertTrue(reused.mayAttemptRestore(1_000L))
    }
}