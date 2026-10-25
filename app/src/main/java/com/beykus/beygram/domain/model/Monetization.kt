package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

/**
 * Telegram Premium state read from TDLib. This model is display-only: the app never
 * starts a payment from it and never stores card data.
 */
data class PremiumInfo(
    val isPremium: Boolean,
    /** Telegram's own textual subscription state, which may contain the expiry date. */
    val statusText: String,
    val features: List<PremiumFeatureKind>,
    val plans: List<PremiumPlan>
)

/** Premium capabilities from TDLib. Unknown server features degrade to [Other]. */
enum class PremiumFeatureKind {
    AccentColor,
    AdvancedChatManagement,
    AnimatedProfilePhoto,
    AppIcons,
    BackgroundForBoth,
    Business,
    ChatBoost,
    Checklists,
    CustomEmoji,
    DisabledAds,
    EmojiStatus,
    ForumTopicIcon,
    ImprovedDownloadSpeed,
    IncreasedLimits,
    IncreasedUploadFileSize,
    LastSeenTimes,
    MessageEffects,
    MessagePrivacy,
    PaidMessages,
    ProfileBadge,
    ProtectPrivateChatContent,
    RealTimeChatTranslation,
    SavedMessagesTags,
    TextComposition,
    UniqueReactions,
    UniqueStickers,
    UpgradedStories,
    VoiceRecognition,
    Other
}

/** One Premium plan offered by Telegram; prices stay in the original currency. */
data class PremiumPlan(
    val monthCount: Int,
    val amountMinor: Long,
    val currency: String,
    val discountPercent: Int,
    val isCurrent: Boolean,
    val isUpgrade: Boolean
)

/** Telegram Stars amount; nanostars are billionths of a Star. */
data class StarBalance(val starCount: Long, val nanostarCount: Int = 0)

enum class StarTransactionKind {
    TopUp,
    GiftPurchase,
    GiftSale,
    GiftUpgrade,
    GiftTransfer,
    GiftAuction,
    PremiumPurchase,
    PaidMessageSend,
    PaidMessageReceive,
    PaidReactionSend,
    PaidReactionReceive,
    PaidMediaPurchase,
    PaidMediaSale,
    SubscriptionPurchase,
    SubscriptionSale,
    Withdrawal,
    Other
}

data class StarTransactionItem(
    val id: String,
    val amount: StarBalance,
    val isRefund: Boolean,
    val dateSeconds: Int,
    val kind: StarTransactionKind
)

data class StarsInfo(
    val balance: StarBalance,
    val transactions: List<StarTransactionItem>,
    val nextOffset: String,
    /** Informational options from TDLib. They are never purchased in this build. */
    val topUpOptions: List<StarTopUpOption>,
    val topUpOptionsUnavailable: Boolean
)

data class StarTransactionsPage(
    val balance: StarBalance,
    val transactions: List<StarTransactionItem>,
    val nextOffset: String
)

/** A Telegram Stars package; [amountMinor] is in the smallest unit of [currency]. */
data class StarTopUpOption(
    val starCount: Long,
    val amountMinor: Long,
    val currency: String,
    val isAdditional: Boolean
)

/**
 * A gift that TDLib currently sells directly. Resale and auction-only gifts are
 * represented but cannot be bought from this screen.
 */
data class GiftCatalogItem(
    val id: Long,
    val emoji: String,
    val starCount: Long,
    val isPremiumOnly: Boolean,
    /** Smallest remaining purchase limit reported by TDLib; null means unlimited. */
    val remainingCount: Int?,
    val isSoldOut: Boolean,
    val isAuctionOnly: Boolean
)

data class ReceivedGiftItem(
    val id: String,
    val senderName: String?,
    val dateSeconds: Int,
    val emoji: String,
    val starCount: Long?,
    val name: String,
    val text: String,
    val isPrivate: Boolean,
    val isSaved: Boolean,
    val isUpgraded: Boolean,
    val wasRefunded: Boolean
)

data class GiftCollectionItem(
    val id: Int,
    val name: String,
    val giftCount: Int,
    val emoji: String
)

data class GiftsInfo(
    /** Null when the balance request failed but the catalog is still usable. */
    val balance: StarBalance?,
    val catalog: List<GiftCatalogItem>,
    val received: List<ReceivedGiftItem>,
    val collections: List<GiftCollectionItem>,
    val isPremium: Boolean,
    val receivedUnavailable: Boolean,
    val collectionsUnavailable: Boolean
)

/** Result of one explicit gift purchase. Never retried automatically by the UI. */
sealed interface GiftSendOutcome {
    data object Sent : GiftSendOutcome
    /** The server or the client refused before spending Stars. */
    data class Blocked(val reason: UiText) : GiftSendOutcome
    /** A definite failure; Stars were not spent. */
    data class Failed(val reason: UiText) : GiftSendOutcome
    /** The request may have reached Telegram; the user must check history before retrying. */
    data object Uncertain : GiftSendOutcome
}

enum class GiftBlockReason {
    OwnProfile,
    Busy,
    AuctionOnly,
    SoldOut,
    PremiumRequired,
    BalanceUnknown,
    InsufficientStars
}

/**
 * Client-side pre-check for the send button. A null balance blocks the send because
 * the user must see the price and remaining balance before confirming.
 */
fun giftBlockReason(
    gift: GiftCatalogItem,
    isSelf: Boolean,
    isPremium: Boolean,
    balanceStars: Long?,
    busy: Boolean
): GiftBlockReason? = when {
    isSelf -> GiftBlockReason.OwnProfile
    busy -> GiftBlockReason.Busy
    gift.isAuctionOnly -> GiftBlockReason.AuctionOnly
    gift.isSoldOut -> GiftBlockReason.SoldOut
    gift.isPremiumOnly && !isPremium -> GiftBlockReason.PremiumRequired
    balanceStars == null -> GiftBlockReason.BalanceUnknown
    balanceStars < gift.starCount -> GiftBlockReason.InsufficientStars
    else -> null
}

/**
 * Purchases require a payment provider and store billing integration that BeyGram
 * does not ship. Keeping the switches in one place makes accidental fake payments
 * harder: the UI can only show plans and packages while both values are false.
 */
object MonetizationPayments {
    const val premiumPurchaseSupported = false
    const val starTopUpSupported = false
}

/** The current build cannot use this section at all, as opposed to a transient request error. */
class MonetizationUnavailableException(text: UiText, cause: Throwable? = null) : UserMessageException(text, cause)

/** A gift send step is explicit so a double tap cannot start a second purchase. */
sealed interface GiftSendStep {
    data object Idle : GiftSendStep
    data class Confirming(val giftId: Long) : GiftSendStep
    data class Sending(val giftId: Long) : GiftSendStep
    data class Sent(val giftId: Long) : GiftSendStep
    data class Failed(val giftId: Long, val uncertain: Boolean) : GiftSendStep
}

sealed interface GiftSendEvent {
    data class Select(val giftId: Long) : GiftSendEvent
    data object Confirm : GiftSendEvent
    data object Dismiss : GiftSendEvent
    data object Succeeded : GiftSendEvent
    data object Failed : GiftSendEvent
    data object Uncertain : GiftSendEvent
}

fun GiftSendStep.next(event: GiftSendEvent): GiftSendStep = when (this) {
    is GiftSendStep.Sending -> when (event) {
        GiftSendEvent.Succeeded -> GiftSendStep.Sent(giftId)
        GiftSendEvent.Failed -> GiftSendStep.Failed(giftId, uncertain = false)
        GiftSendEvent.Uncertain -> GiftSendStep.Failed(giftId, uncertain = true)
        // Everything else is ignored while a request is in flight: no second purchase.
        else -> this
    }
    else -> when (event) {
        is GiftSendEvent.Select -> GiftSendStep.Confirming(event.giftId)
        GiftSendEvent.Confirm -> if (this is GiftSendStep.Confirming) GiftSendStep.Sending(giftId) else this
        GiftSendEvent.Dismiss -> GiftSendStep.Idle
        else -> this
    }
}

/** Formats a price in minor units without relying on device currency display names. */
fun formatPriceMinorUnits(amountMinor: Long, currencyCode: String): String {
    val code = currencyCode.trim().uppercase(Locale.ROOT)
    if (code.isEmpty()) return amountMinor.toString()
    val digits = runCatching { Currency.getInstance(code).defaultFractionDigits }
        .getOrNull()
        ?.takeIf { it in 0..3 }
        ?: 2
    val amount = BigDecimal.valueOf(amountMinor).movePointLeft(digits).setScale(digits)
    return "${amount.toPlainString()} $code"
}

/** Exact Stars amount without locale-dependent grouping, so history rows always parse the same way. */
fun formatStarAmount(starCount: Long, nanostarCount: Int = 0): String =
    BigDecimal.valueOf(starCount)
        .add(BigDecimal.valueOf(nanostarCount.toLong(), 9))
        .stripTrailingZeros()
        .toPlainString()
