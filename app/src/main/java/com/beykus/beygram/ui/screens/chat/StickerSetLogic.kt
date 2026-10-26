package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.StickerSetContent

/** What a loaded sticker set can show. TDLib may filter out custom-emoji and mask stickers, leaving an empty list. */
internal enum class StickerSetContentState { Unavailable, Empty, Ready }

/**
 * A sticker message without a positive set id is a single sticker, not a broken request. Empty content means the set
 * has no regular stickers this screen can show (for example a custom-emoji or mask pack), so both cases get a friendly
 * placeholder instead of an endlessly loading grid.
 */
internal fun stickerSetContentState(setId: Long, content: StickerSetContent): StickerSetContentState = when {
    setId <= 0L -> StickerSetContentState.Unavailable
    content.stickers.isEmpty() -> StickerSetContentState.Empty
    else -> StickerSetContentState.Ready
}
