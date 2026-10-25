package com.beykus.beygram.domain.model

/** The kinds of message effect this TDLib version can describe. Unknown types stay silent. */
enum class MessageEffectKind { EmojiReaction, PremiumSticker, Unknown }

/**
 * One sticker file used by a message effect. [fileId] is registered with TDLib, so the UI downloads it only after the
 * effect is actually played. [mimeType] follows the same TGS/WebP/WebM values as custom emoji and stickers.
 */
data class MessageEffectSticker(
    val fileId: Int,
    val emoji: String = "",
    val mimeType: String = "",
    val width: Int = 0,
    val height: Int = 0
)

/**
 * A Telegram message effect as returned by `getMessageEffect`. Only metadata and sticker file ids are kept here; the
 * actual Lottie/image data is downloaded lazily by the effect overlay.
 *
 * [animation] is the main effect animation (or the premium sticker), [selectAnimation] is the small emoji animation
 * Telegram plays while the effect starts, and [staticIcon] is a WebP fallback. Any of them may be absent.
 */
data class MessageEffect(
    val effectId: Long,
    val emoji: String = "",
    val kind: MessageEffectKind = MessageEffectKind.Unknown,
    val isPremium: Boolean = false,
    val animation: MessageEffectSticker? = null,
    val selectAnimation: MessageEffectSticker? = null,
    val staticIcon: MessageEffectSticker? = null,
    /** A premium sticker's ordinary artwork, shown when its dedicated premium effect file is unavailable. */
    val fallbackSticker: MessageEffectSticker? = null
) {
    /** The best file to show on screen: the animated effect first, then its emoji selection, then the static icon. */
    val primarySticker: MessageEffectSticker?
        get() = animation ?: selectAnimation ?: staticIcon ?: fallbackSticker
}
