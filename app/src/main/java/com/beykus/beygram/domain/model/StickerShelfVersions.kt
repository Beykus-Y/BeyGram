package com.beykus.beygram.domain.model

/** Invalidates only the server-backed picker collection whose TDLib update was received. */
data class StickerShelfVersions(
    val stickerPacks: Long = 0,
    val emojiPacks: Long = 0,
    val recentStickers: Long = 0,
    val favoriteStickers: Long = 0,
    val savedAnimations: Long = 0
)

data class EmojiPickerSearchResults(
    val unicode: List<String> = emptyList(),
    val custom: List<Long> = emptyList(),
    val packs: List<CustomEmojiSetBrief> = emptyList()
)
