package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.AnimationItem
import com.beykus.beygram.domain.model.AnimationPage
import com.beykus.beygram.domain.model.StickerItem
import com.beykus.beygram.domain.model.StickerSearchPage

/**
 * Pure paging helpers for the pickers. TDLib pages can repeat an item that was already shown (for example when the
 * same sticker matches two search pages), so appended pages are de-duplicated instead of rendering duplicate keys.
 */
internal fun mergeStickerPages(existing: List<StickerItem>, page: StickerSearchPage): List<StickerItem> =
    (existing + page.stickers).distinctBy { it.key }

internal fun mergeAnimationPages(existing: List<AnimationItem>, page: AnimationPage): List<AnimationItem> =
    (existing + page.animations).distinctBy { it.key }
