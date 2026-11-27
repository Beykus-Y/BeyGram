package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.AnimationItem
import com.beykus.beygram.domain.model.StickerItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerPanelLogicTest {
    @Test fun gifMosaicUsesDimensionsBeforeFilesLoad() {
        assertEquals(16f / 9, gifPickerAspectRatio(AnimationItem(1, width = 160, height = 90)), 0.001f)
        assertEquals(1f, gifPickerAspectRatio(AnimationItem(2, thumbnailWidth = 80, thumbnailHeight = 80)), 0.001f)
        assertEquals(0.68f, gifPickerAspectRatio(AnimationItem(3, width = 1, height = 100)), 0.001f)
        assertEquals(2.1f, gifPickerAspectRatio(AnimationItem(4, width = 100, height = 1)), 0.001f)
    }

    private fun sticker(setId: Long, id: Long, fileId: Int = id.toInt()) =
        StickerItem(setId = setId, stickerId = id, fileId = fileId)

    @Test
    fun searchStartsAboveTheCurrentPackAndClosingItReturnsToThatPack() {
        val pack = StickerPanelView().openPack(42L)
        val searching = pack.openSearch().updateQuery("cat")

        assertTrue(searching.searchActive)
        assertTrue(searching.canGoBack)
        assertEquals(42L, searching.selectedSetId)

        val closed = searching.closeSearch()
        assertFalse(closed.searchActive)
        assertEquals(StickerPanelSection.Pack, closed.section)
        assertEquals(42L, closed.selectedSetId)
    }

    @Test
    fun backClearsSearchBeforeLeavingThePack() {
        val searching = StickerPanelView().openPack(7L).openSearch().updateQuery("wave")
        val afterSearch = searching.back()

        assertEquals(StickerPanelSection.Pack, afterSearch?.section)
        assertEquals(7L, afterSearch?.selectedSetId)
        assertEquals("", afterSearch?.query)

        val afterPack = afterSearch?.back()
        assertEquals(StickerPanelSection.Recent, afterPack?.section)
        assertNull(afterPack?.selectedSetId)
    }

    @Test
    fun backAtTheRootAsksTheCallerToCloseThePanel() {
        assertNull(StickerPanelView().back())
        assertFalse(StickerPanelView().canGoBack)
    }

    @Test
    fun backFromFavoritesReturnsToRecentWithoutClosing() {
        val favorites = StickerPanelView().showFavorites()

        assertTrue(favorites.canGoBack)
        assertEquals(StickerPanelSection.Recent, favorites.back()?.section)
    }

    @Test
    fun backFromAnOpenButEmptySearchOnlyClosesSearch() {
        val view = StickerPanelView().openPack(3L).openSearch()

        assertTrue(view.searchActive)
        val closed = view.back()

        assertEquals(StickerPanelSection.Pack, closed?.section)
        assertFalse(closed?.searchActive ?: true)
    }

    @Test
    fun aRestoredQueryWithoutTheSearchFlagStillCountsAsSearch() {
        val view = StickerPanelView(query = "cat")

        assertTrue(view.searchActive)
        assertTrue(view.canGoBack)
        assertEquals("", view.back()?.query)
    }

    @Test
    fun switchingTabsClearsPackSearchAndSection() {
        val view = StickerPanelView()
            .openPack(9L)
            .openSearch()
            .updateQuery("dog")
            .switchTab(StickerPanelTab.Gifs)

        assertEquals(StickerPanelTab.Gifs, view.tab)
        assertEquals(StickerPanelSection.Recent, view.section)
        assertNull(view.selectedSetId)
        assertFalse(view.searchActive)
        assertEquals("", view.query)
    }

    @Test
    fun openingAPackLeavesSearchAndSelectsThePack() {
        val view = StickerPanelView()
            .openSearch()
            .updateQuery("party")
            .openPack(11L)

        assertEquals(StickerPanelSection.Pack, view.section)
        assertEquals(11L, view.selectedSetId)
        assertFalse(view.searchActive)
        assertEquals("", view.query)
    }

    @Test
    fun favoritesAreInsertedFirstAndDeduplicatedByStickerIdentity() {
        val first = sticker(1, 10, fileId = 100)
        val refreshed = sticker(1, 10, fileId = 101)
        val other = sticker(1, 11)

        val favorites = updateFavoriteStickers(listOf(other), first, favorite = true)
        val updated = updateFavoriteStickers(favorites, refreshed, favorite = true)

        assertEquals(listOf(first.key, other.key), updated.map { it.key })
        assertEquals(2, updated.size)
    }

    @Test
    fun removingAFavoriteDropsOnlyTheMatchingSticker() {
        val first = sticker(1, 10)
        val second = sticker(2, 10)
        val remaining = updateFavoriteStickers(listOf(first, second), first, favorite = false)

        assertEquals(listOf(second.key), remaining.map { it.key })
    }

    @Test
    fun theSameStickerIdInAnotherSetRemainsAFavorite() {
        val first = sticker(1, 10)
        val second = sticker(2, 10)
        val remaining = updateFavoriteStickers(listOf(first, second), first, favorite = false)

        assertTrue(remaining.any { it.key == second.key })
        assertEquals(1, remaining.size)
    }

    @Test
    fun refreshedFileIdDoesNotCreateADuplicateSearchResult() {
        val old = sticker(4, 15, fileId = 40)
        val refreshed = sticker(4, 15, fileId = 401)

        assertEquals(old.key, refreshed.key)
    }

    @Test
    fun aStickerWithoutAServerIdFallsBackToItsFileId() {
        val first = StickerItem(setId = 1, stickerId = 0, fileId = 10)
        val second = StickerItem(setId = 1, stickerId = 0, fileId = 11)

        assertTrue(first.key != second.key)
    }

    @Test
    fun loadStatesKeepLoadingReadyAndFailureDistinct() {
        val loading: PickerLoad<List<StickerItem>> = PickerLoad.Loading
        val ready: PickerLoad<List<StickerItem>> = PickerLoad.Ready(listOf(sticker(1, 2)))
        val failed: PickerLoad<List<StickerItem>> = PickerLoad.Failed("offline")

        assertEquals(PickerLoad.Loading, loading)
        assertEquals(1, (ready as PickerLoad.Ready<List<StickerItem>>).value.size)
        assertEquals("offline", (failed as PickerLoad.Failed).message)
    }

    @Test
    fun emojiIsTheFirstTabButStickersStayTheDefault() {
        assertEquals(
            listOf(StickerPanelTab.Emoji, StickerPanelTab.Stickers, StickerPanelTab.Gifs),
            StickerPanelTab.entries.toList()
        )
        assertEquals(StickerPanelTab.Stickers, StickerPanelView().tab)
    }

    @Test
    fun switchingToEmojiLeavesPacksAndSearchBehind() {
        val view = StickerPanelView()
            .openPack(9L)
            .openSearch()
            .updateQuery("cat")
            .switchTab(StickerPanelTab.Emoji)

        assertEquals(StickerPanelTab.Emoji, view.tab)
        assertEquals(StickerPanelSection.Recent, view.section)
        assertNull(view.selectedSetId)
        assertFalse(view.searchActive)
        assertEquals("", view.query)
    }

    @Test
    fun emojiSearchKeepsItsTabAndBackRestoresItsRoot() {
        val emoji = StickerPanelView().switchTab(StickerPanelTab.Emoji)

        val search = emoji.openSearch().updateQuery("cat")
        assertEquals(StickerPanelTab.Emoji, search.tab)
        assertEquals("cat", search.query)
        assertTrue(search.searchActive)
        assertEquals(emoji, search.back())
    }

    @Test
    fun backOnTheEmojiRootAsksTheCallerToCloseThePanel() {
        val emoji = StickerPanelView().switchTab(StickerPanelTab.Emoji)

        assertFalse(emoji.canGoBack)
        assertNull(emoji.back())
    }

    @Test
    fun restoringEmojiSearchDropsTheStaleStickerPack() {
        val restored = restoreStickerPanelView(
            tab = StickerPanelTab.Emoji,
            section = StickerPanelSection.Favorites,
            setId = 5L,
            searchOpen = true,
            query = "cat"
        )

        assertEquals(StickerPanelView(tab = StickerPanelTab.Emoji, searchOpen = true, query = "cat"), restored)
    }

    @Test
    fun restoringAPackNeedsItsIdAndGifsNeverKeepOne() {
        val withoutId = restoreStickerPanelView(StickerPanelTab.Stickers, StickerPanelSection.Pack, null, false, "")
        assertEquals(StickerPanelSection.Recent, withoutId.section)

        val withId = restoreStickerPanelView(StickerPanelTab.Stickers, StickerPanelSection.Pack, 4L, false, "")
        assertEquals(StickerPanelSection.Pack, withId.section)
        assertEquals(4L, withId.selectedSetId)

        val gifs = restoreStickerPanelView(StickerPanelTab.Gifs, StickerPanelSection.Pack, 4L, false, "")
        assertEquals(StickerPanelSection.Recent, gifs.section)
        assertNull(gifs.selectedSetId)
    }
}
