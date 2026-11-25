package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.GiftChatContent
import com.beykus.beygram.domain.model.GiftChatKind
import com.beykus.beygram.domain.model.GiftCollectibleDetails
import com.beykus.beygram.domain.model.GiftVisual
import org.drinkless.tdlib.TdApi

/**
 * Structured projection of TDLib gift messages (Gifts 2.0, #1142).
 *
 * The text form stays in [TdServiceMessageMapper]; this function only carries the facts that the UI needs to draw a real
 * gift (file identifiers, names, number, amounts, note). It never invents data: absent fields stay at their defaults.
 *
 * [visual] and [collectible] come from the same mappers the profile gift screens use, so the chat card draws the very
 * same sticker and backdrop and the file is registered once, for both screens. Both are optional: a caller without a
 * media layer (the mapping tests) gets a purely textual projection.
 */
internal fun giftChatContent(
    content: TdApi.MessageContent,
    visual: (TdApi.Sticker?) -> GiftVisual? = { null },
    collectible: (TdApi.UpgradedGift?) -> GiftCollectibleDetails? = { null },
): GiftChatContent? = when (content) {
    is TdApi.MessageGift -> {
        val gift = content.gift
        GiftChatContent(
            kind = if (content.wasRefunded) GiftChatKind.Refunded else GiftChatKind.Regular,
            stickerFileId = gift?.sticker?.sticker?.id ?: 0,
            starCount = gift?.starCount ?: 0L,
            number = content.uniqueGiftNumber.coerceAtLeast(0),
            note = content.text?.text.orEmpty(),
            isPremium = content.isPrepaidUpgrade,
            isRefunded = content.wasRefunded,
            wasConverted = content.wasConverted,
            wasUpgraded = content.wasUpgraded,
            emoji = gift?.sticker?.emoji.orEmpty(),
            visual = visual(gift?.sticker),
        )
    }
    is TdApi.MessageUpgradedGift -> {
        val gift = content.gift
        GiftChatContent(
            kind = GiftChatKind.Upgraded,
            stickerFileId = gift?.model?.sticker?.sticker?.id ?: 0,
            modelFileId = gift?.model?.sticker?.sticker?.id ?: 0,
            symbolFileId = gift?.symbol?.sticker?.sticker?.id ?: 0,
            title = gift?.title.orEmpty(),
            name = gift?.name.orEmpty(),
            modelName = gift?.model?.name.orEmpty(),
            symbolName = gift?.symbol?.name.orEmpty(),
            backdropName = gift?.backdrop?.name.orEmpty(),
            number = gift?.number?.coerceAtLeast(0) ?: 0,
            note = gift?.originalDetails?.text?.text.orEmpty(),
            isPremium = gift?.isPremium == true,
            emoji = gift?.model?.sticker?.emoji.orEmpty(),
            visual = visual(gift?.model?.sticker),
            collectible = collectible(gift),
        )
    }
    is TdApi.MessageRefundedUpgradedGift -> GiftChatContent(
        kind = GiftChatKind.Refunded,
        stickerFileId = content.gift?.sticker?.sticker?.id ?: 0,
        starCount = content.gift?.starCount ?: 0L,
        isRefunded = true,
        emoji = content.gift?.sticker?.emoji.orEmpty(),
        visual = visual(content.gift?.sticker),
    )
    is TdApi.MessageGiftedPremium -> GiftChatContent(
        kind = GiftChatKind.Premium,
        stickerFileId = content.sticker?.sticker?.id ?: 0,
        premiumMonths = content.monthCount.coerceAtLeast(0),
        note = content.text?.text.orEmpty(),
        isPremium = true,
        emoji = content.sticker?.emoji.orEmpty(),
        visual = visual(content.sticker),
    )
    is TdApi.MessageGiftedStars -> GiftChatContent(
        kind = GiftChatKind.Stars,
        stickerFileId = content.sticker?.sticker?.id ?: 0,
        starCount = content.starCount.coerceAtLeast(0L),
        emoji = content.sticker?.emoji.orEmpty(),
        visual = visual(content.sticker),
    )
    is TdApi.MessageGiftedTon -> GiftChatContent(
        kind = GiftChatKind.Ton,
        stickerFileId = content.sticker?.sticker?.id ?: 0,
        tonAmount = content.tonAmount.coerceAtLeast(0L),
        emoji = content.sticker?.emoji.orEmpty(),
        visual = visual(content.sticker),
    )
    is TdApi.MessageUpgradedGiftPurchaseOffer -> GiftChatContent(
        kind = GiftChatKind.PurchaseOffer,
        stickerFileId = content.gift?.model?.sticker?.sticker?.id ?: 0,
        modelFileId = content.gift?.model?.sticker?.sticker?.id ?: 0,
        title = content.gift?.title.orEmpty(),
        name = content.gift?.name.orEmpty(),
        number = content.gift?.number?.coerceAtLeast(0) ?: 0,
        emoji = content.gift?.model?.sticker?.emoji.orEmpty(),
        visual = visual(content.gift?.model?.sticker),
        collectible = collectible(content.gift),
    )
    is TdApi.MessageUpgradedGiftPurchaseOfferRejected -> GiftChatContent(
        kind = GiftChatKind.PurchaseOfferRejected,
        stickerFileId = content.gift?.model?.sticker?.sticker?.id ?: 0,
        modelFileId = content.gift?.model?.sticker?.sticker?.id ?: 0,
        title = content.gift?.title.orEmpty(),
        name = content.gift?.name.orEmpty(),
        number = content.gift?.number?.coerceAtLeast(0) ?: 0,
        emoji = content.gift?.model?.sticker?.emoji.orEmpty(),
        visual = visual(content.gift?.model?.sticker),
        collectible = collectible(content.gift),
    )
    else -> null
}