package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.data.unavailable.UnavailableTelegramRepository
import com.beykus.beygram.domain.model.AnimationItem
import com.beykus.beygram.domain.model.AnimationPage
import com.beykus.beygram.domain.model.CustomEmojiSetBrief
import com.beykus.beygram.domain.model.StickerItem
import com.beykus.beygram.domain.model.StickerSetContent
import com.beykus.beygram.domain.model.StickerSetSummary
import com.beykus.beygram.domain.repository.ConversationRepository
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MediaPickerSessionCacheTest {

    private lateinit var repoA: ConversationRepository
    private lateinit var repoB: ConversationRepository

    @Before
    fun setUp() {
        MediaPickerSessionCache.clearAll()
        repoA = object : ConversationRepository by UnavailableTelegramRepository() {}
        repoB = object : ConversationRepository by UnavailableTelegramRepository() {}
    }

    @After
    fun tearDown() {
        MediaPickerSessionCache.clearAll()
    }

    @Test
    fun accountIsolationAndClearAccountOnlyWipesTargetAccount() {
        val stickerA = listOf(StickerItem(setId = 1L, stickerId = 10L, fileId = 10))
        val stickerB = listOf(StickerItem(setId = 2L, stickerId = 20L, fileId = 20))
        val customA = listOf(CustomEmojiSetBrief(id = 100L, title = "Emoji A", covers = listOf(1L)))
        val customB = listOf(CustomEmojiSetBrief(id = 200L, title = "Emoji B", covers = listOf(2L)))

        MediaPickerSessionCache.putRecentStickers("acc-1", repoA, stickerA, version = 3L)
        MediaPickerSessionCache.putRecentStickers("acc-2", repoA, stickerB, version = 4L)
        MediaPickerSessionCache.putCustomEmojiInstalled("acc-1", repoA, customA, version = 5L)
        MediaPickerSessionCache.putCustomEmojiInstalled("acc-2", repoA, customB, version = 6L)
        MediaPickerSessionCache.putIsPremium("acc-1", repoA, true)
        MediaPickerSessionCache.putIsPremium("acc-2", repoA, false)

        assertEquals(stickerA, MediaPickerSessionCache.getRecentStickers("acc-1", repoA)?.value)
        assertEquals(3L, MediaPickerSessionCache.getRecentStickers("acc-1", repoA)?.version)
        assertEquals(stickerB, MediaPickerSessionCache.getRecentStickers("acc-2", repoA)?.value)
        assertEquals(true, MediaPickerSessionCache.getIsPremium("acc-1", repoA))
        assertEquals(false, MediaPickerSessionCache.getIsPremium("acc-2", repoA))

        MediaPickerSessionCache.clearAccount("acc-1")

        assertNull(MediaPickerSessionCache.getRecentStickers("acc-1", repoA))
        assertNull(MediaPickerSessionCache.getCustomEmojiInstalled("acc-1", repoA))
        assertNull(MediaPickerSessionCache.getIsPremium("acc-1", repoA))
        assertEquals(stickerB, MediaPickerSessionCache.getRecentStickers("acc-2", repoA)?.value)
        assertEquals(customB, MediaPickerSessionCache.getCustomEmojiInstalled("acc-2", repoA)?.value)
        assertEquals(false, MediaPickerSessionCache.getIsPremium("acc-2", repoA))
    }

    @Test
    fun repositoryIdentityMismatchReturnsNullWithoutLeakingAcrossInstances() {
        val sets = listOf(StickerSetSummary(id = 11L, title = "Pack 11"))
        val page = AnimationPage(animations = listOf(AnimationItem(fileId = 77)))

        MediaPickerSessionCache.putInstalledStickerSets("acc-1", repoA, sets, version = 2L)
        MediaPickerSessionCache.putGifRecent("acc-1", repoA, page)
        MediaPickerSessionCache.putGifSaved("acc-1", repoA, page.animations, version = 9L)
        MediaPickerSessionCache.putStickerPack("acc-1", repoA, StickerSetContent(id = 11L, title = "Pack 11"))
        MediaPickerSessionCache.putCustomEmojiSetIds("acc-1", repoA, setId = 55L, ids = listOf(1L, 2L))

        assertNotNull(MediaPickerSessionCache.getInstalledStickerSets("acc-1", repoA))
        assertNull(MediaPickerSessionCache.getInstalledStickerSets("acc-1", repoB))
        assertNull(MediaPickerSessionCache.getGifRecent("acc-1", repoB))
        assertNull(MediaPickerSessionCache.getGifSaved("acc-1", repoB))
        assertNull(MediaPickerSessionCache.getStickerPack("acc-1", repoB, 11L))
        assertTrue(MediaPickerSessionCache.snapshotCustomEmojiSetIds("acc-1", repoB).isEmpty())
    }

    @Test
    fun stickerPacksAndCustomEmojiSetsAreBoundedLruCaches() {
        for (id in 1L..30L) {
            MediaPickerSessionCache.putStickerPack(
                "acc-1",
                repoA,
                StickerSetContent(id = id, title = "Pack $id")
            )
        }
        val stickerSnapshot = MediaPickerSessionCache.snapshotStickerPacks("acc-1", repoA)
        assertEquals(24, stickerSnapshot.size)
        assertFalse(stickerSnapshot.containsKey(1L))
        assertFalse(stickerSnapshot.containsKey(6L))
        assertTrue(stickerSnapshot.containsKey(7L))
        assertTrue(stickerSnapshot.containsKey(30L))

        for (id in 1L..70L) {
            MediaPickerSessionCache.putCustomEmojiSetIds("acc-1", repoA, id, listOf(id))
        }
        val emojiSnapshot = MediaPickerSessionCache.snapshotCustomEmojiSetIds("acc-1", repoA)
        assertEquals(64, emojiSnapshot.size)
        assertFalse(emojiSnapshot.containsKey(1L))
        assertFalse(emojiSnapshot.containsKey(6L))
        assertTrue(emojiSnapshot.containsKey(7L))
        assertTrue(emojiSnapshot.containsKey(70L))
    }

    @Test
    fun customEmojiSetLoaderSkipsInitialLoadedAndReportsNewLoads() = runTest {
        val loadedCalls = mutableListOf<Long>()
        val persisted = mutableMapOf<Long, List<Long>>()
        val loader = CustomEmojiSetLoader(
            scope = this,
            initialLoaded = mapOf(100L to listOf(1L, 2L)),
            onLoaded = { setId, ids -> persisted[setId] = ids },
            load = { setId ->
                loadedCalls += setId
                Result.success(listOf(setId * 10L))
            }
        )

        loader.request(100L)
        loader.request(200L)
        advanceUntilIdle()

        assertEquals(listOf(200L), loadedCalls)
        assertEquals(mapOf(200L to listOf(2000L)), persisted)
        assertEquals(CustomEmojiSetState.Loaded(listOf(1L, 2L)), loader.states.value[100L])
        assertEquals(CustomEmojiSetState.Loaded(listOf(2000L)), loader.states.value[200L])
    }
}
