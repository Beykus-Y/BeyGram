package com.beykus.beygram.domain.model

/** Name/reply accent and its base color for single-color surfaces such as avatar placeholders. */
data class PeerAccentColor(
    val builtInId: Int,
    val lightRgb: List<Int> = emptyList(),
    val darkRgb: List<Int> = emptyList()
)

data class GroupAuthorAppearance(
    val accent: PeerAccentColor?,
    val emojiStatus: GroupAuthorEmojiStatus?
)

data class GroupAuthorEmojiStatus(val customEmojiId: Long, val expiresAtSeconds: Long) {
    fun isActive(nowSeconds: Long): Boolean = expiresAtSeconds == 0L || nowSeconds < expiresAtSeconds
}
