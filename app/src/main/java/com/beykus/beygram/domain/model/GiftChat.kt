package com.beykus.beygram.domain.model

/**
 * Structured gift data of a chat message (Gifts 2.0, #1142).
 *
 * Only facts that TDLib actually reported are stored: file identifiers of the real sticker/model/symbol, names, the
 * collectible number and the amounts. Anything missing stays zero or empty and is never invented in the UI. The plain
 * text of the message is produced separately and remains the source for search, accessibility and fallback.
 */
enum class GiftChatKind {
    /** An ordinary gift that still exists in the chat. */
    Regular,
    /** A collectible (upgraded) gift with a model, symbol and number. */
    Upgraded,
    /** A gift that was returned; the original sticker may still be known. */
    Refunded,
    /** Telegram Premium gifted in the chat. */
    Premium,
    /** Telegram Stars gifted in the chat. */
    Stars,
    /** TON transferred with a gift sticker. */
    Ton,
    /** An offer to buy a collectible gift (no payment is performed by the client). */
    PurchaseOffer,
    /** An offer that expired or was rejected. */
    PurchaseOfferRejected,
}

data class GiftChatContent(
    val kind: GiftChatKind,
    /** `sticker.sticker.id` of the gift itself, or of the currency gift sticker. */
    val stickerFileId: Int = 0,
    /** `model.sticker.sticker.id` of a collectible gift. */
    val modelFileId: Int = 0,
    /** `symbol.sticker.sticker.id` of a collectible gift. */
    val symbolFileId: Int = 0,
    val title: String = "",
    val name: String = "",
    val modelName: String = "",
    val symbolName: String = "",
    val backdropName: String = "",
    /** Collectible number; 0 when the gift is not numbered (or the number is unknown). */
    val number: Int = 0,
    val starCount: Long = 0,
    val tonAmount: Long = 0,
    val premiumMonths: Int = 0,
    /** Comment attached by the sender ("note"), if any. */
    val note: String = "",
    val isPremium: Boolean = false,
    val isRefunded: Boolean = false,
    val wasConverted: Boolean = false,
    val wasUpgraded: Boolean = false,
    /**
     * The real sticker of the gift, in the same shape the gift screens draw ([GiftVisual]). Null when TDLib reported no
     * drawable sticker, in which case the card shows the emoji or a generic glyph instead.
     */
    val visual: GiftVisual? = null,
    /**
     * Collectible attributes of an upgraded gift: symbol, backdrop colours, rarity and number. Shared with the profile
     * gift screens, which is why the chat card needs no second renderer for them. Null for a regular gift.
     */
    val collectible: GiftCollectibleDetails? = null,
)