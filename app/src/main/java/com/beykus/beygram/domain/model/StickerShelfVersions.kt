package com.beykus.beygram.domain.model

/** Invalidates only the server-backed picker collection whose TDLib update was received. */
data class StickerShelfVersions(
    val stickerPacks: Long = 0,
    val emojiPacks: Long = 0,
    val recentStickers: Long = 0,
    val favoriteStickers: Long = 0,
    val savedAnimations: Long = 0
)
