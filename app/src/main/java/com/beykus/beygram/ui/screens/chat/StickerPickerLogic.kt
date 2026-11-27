package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.unit.dp
import com.beykus.beygram.domain.model.AnimationItem
import com.beykus.beygram.domain.model.AnimationPage
import com.beykus.beygram.domain.model.StickerItem
import com.beykus.beygram.domain.model.StickerSearchPage

/**
 * One geometry for every sticker picker surface (the bottom dock and the pack sheet). Around 65 dp gives five columns
 * on a typical 360 dp phone while the 60 dp cell keeps a comfortable touch target; narrower panes fall back to four
 * rather than squeezing the artwork. Cells stay square and the artwork is aspect-fitted inside them, as in Telegram.
 */
internal object StickerPickerMetrics {
    val columnMin = 65.dp
    val cell = 60.dp
    val gap = 4.dp
    val contentPadding = 8.dp
}

/**
 * Pure paging helpers for the pickers. TDLib pages can repeat an item that was already shown (for example when the
 * same sticker matches two search pages), so appended pages are de-duplicated instead of rendering duplicate keys.
 */
internal fun mergeStickerPages(existing: List<StickerItem>, page: StickerSearchPage): List<StickerItem> =
    (existing + page.stickers).distinctBy { it.key }

internal fun mergeAnimationPages(existing: List<AnimationItem>, page: AnimationPage): List<AnimationItem> =
    (existing + page.animations).distinctBy { it.key }
