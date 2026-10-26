package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.saveable.listSaver
import com.beykus.beygram.domain.model.StickerItem

/** Top-level tabs of the unified sticker/GIF panel. */
internal enum class StickerPanelTab { Stickers, Gifs }

/** Sticker sources inside the Stickers tab. */
internal enum class StickerPanelSection { Recent, Favorites, Pack }

/**
 * Navigation state of the sticker panel. The panel keeps search above the current section, so closing search returns
 * to the pack or list the user came from instead of resetting the whole picker.
 */
internal data class StickerPanelView(
    val tab: StickerPanelTab = StickerPanelTab.Stickers,
    val section: StickerPanelSection = StickerPanelSection.Recent,
    val selectedSetId: Long? = null,
    val searchOpen: Boolean = false,
    val query: String = ""
) {
    val searchActive: Boolean get() = searchOpen || query.isNotBlank()

    /** A back gesture clears search first, then returns from a pack/favorites list; at the root it closes the panel. */
    val canGoBack: Boolean get() = searchActive || section != StickerPanelSection.Recent

    fun switchTab(next: StickerPanelTab): StickerPanelView = copy(
        tab = next,
        section = StickerPanelSection.Recent,
        selectedSetId = null,
        searchOpen = false,
        query = ""
    )

    fun showRecent(): StickerPanelView = copy(section = StickerPanelSection.Recent, selectedSetId = null)

    fun showFavorites(): StickerPanelView = copy(section = StickerPanelSection.Favorites, selectedSetId = null)

    fun openPack(setId: Long): StickerPanelView = copy(
        tab = StickerPanelTab.Stickers,
        section = StickerPanelSection.Pack,
        selectedSetId = setId,
        searchOpen = false,
        query = ""
    )

    fun openSearch(): StickerPanelView = copy(searchOpen = true)

    fun updateQuery(text: String): StickerPanelView = copy(searchOpen = true, query = text)

    fun closeSearch(): StickerPanelView = copy(searchOpen = false, query = "")

    fun back(): StickerPanelView? = when {
        searchActive -> closeSearch()
        section != StickerPanelSection.Recent -> showRecent()
        else -> null
    }
}

internal val StickerPanelViewSaver = listSaver<StickerPanelView, Any>(
    save = {
        listOf(
            it.tab.name,
            it.section.name,
            it.selectedSetId ?: -1L,
            it.searchOpen,
            it.query
        )
    },
    restore = { saved ->
        val tab = enumValue(saved[0] as String, StickerPanelTab.Stickers)
        val section = enumValue(saved[1] as String, StickerPanelSection.Recent)
        val setId = (saved[2] as Long).takeIf { it >= 0L }
        StickerPanelView(
            tab = tab,
            section = when {
                section == StickerPanelSection.Pack && setId == null -> StickerPanelSection.Recent
                tab == StickerPanelTab.Gifs && section == StickerPanelSection.Pack -> StickerPanelSection.Recent
                else -> section
            },
            selectedSetId = if (tab == StickerPanelTab.Gifs) null else setId,
            searchOpen = saved[3] as Boolean,
            query = saved[4] as String
        )
    }
)

private inline fun <reified T : Enum<T>> enumValue(name: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback

/** Loading state shared by recent, favorite, pack and search queries. */
internal sealed interface PickerLoad<out T> {
    data object Loading : PickerLoad<Nothing>
    data class Ready<T>(val value: T) : PickerLoad<T>
    data class Failed(val message: String) : PickerLoad<Nothing>
}

/**
 * Favorite updates are local until TDLib confirms them. A sticker is identified by set and sticker id, not only by its
 * current file id, so the same sticker arriving through search cannot be added twice.
 */
internal fun updateFavoriteStickers(
    current: List<StickerItem>,
    item: StickerItem,
    favorite: Boolean
): List<StickerItem> = if (favorite) {
    (listOf(item) + current).distinctBy { it.key }
} else {
    current.filterNot { it.key == item.key }
}
