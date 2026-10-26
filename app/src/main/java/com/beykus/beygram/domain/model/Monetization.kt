package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import java.math.BigDecimal
import java.net.URI
import java.util.Currency
import java.util.Locale

/**
 * Telegram Premium state read from TDLib. This model is display-only: the app never
 * stores card data. Checkout, when Telegram offers it, is delegated to an external page.
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
    val isUpgrade: Boolean,
    /** TDLib supplied an internal link that can be resolved to Telegram's external checkout page. */
    val hasExternalPaymentLink: Boolean
) {
    /** Prices and flags must still match TDLib server-side data before a checkout is opened. */
    fun isSameOfferAs(other: PremiumPlan): Boolean =
        monthCount == other.monthCount &&
            amountMinor == other.amountMinor &&
            currency.equals(other.currency, ignoreCase = true) &&
            isCurrent == other.isCurrent &&
            isUpgrade == other.isUpgrade
}

/** Telegram changes plans, prices and payment links server-side; older cached details are not usable. */
const val PREMIUM_INFO_MAX_AGE_MS: Long = 5 * 60_000L

/**
 * One successful Premium read, bound to the account that produced it. Status, plans and prices
 * are account-specific, so a snapshot must never survive an account switch or outlive its TTL.
 */
data class PremiumSnapshot(
    val accountId: String,
    val info: PremiumInfo,
    val loadedAtMillis: Long
) {
    fun isFreshAt(nowMillis: Long): Boolean =
        accountId.isNotBlank() &&
            loadedAtMillis > 0L &&
            nowMillis >= loadedAtMillis &&
            nowMillis - loadedAtMillis <= PREMIUM_INFO_MAX_AGE_MS

    fun isUsableFor(accountId: String, nowMillis: Long): Boolean =
        this.accountId == accountId && isFreshAt(nowMillis)
}

/**
 * Explicit Premium screen state. A distinct [Loading] value prevents a slow read from looking
 * like "Premium has no plans"; [Failed] keeps the reason visible instead of an empty background.
 */
sealed interface PremiumLoadState {
    data object Loading : PremiumLoadState

    data class Ready(
        val snapshot: PremiumSnapshot,
        val refreshing: Boolean = false,
        /** The last refresh failed; cached details may stay visible, but checkout must stay blocked. */
        val refreshError: UiText? = null
    ) : PremiumLoadState

    data object Unsupported : PremiumLoadState

    data class Failed(val reason: UiText) : PremiumLoadState
}

fun PremiumLoadState.startingRefresh(): PremiumLoadState = when (this) {
    is PremiumLoadState.Ready -> copy(refreshing = true, refreshError = null)
    else -> PremiumLoadState.Loading
}

fun premiumLoadSucceeded(accountId: String, info: PremiumInfo, nowMillis: Long): PremiumLoadState.Ready =
    PremiumLoadState.Ready(PremiumSnapshot(accountId, info, nowMillis))

fun PremiumLoadState.failed(
    accountId: String,
    reason: UiText,
    unsupported: Boolean = false
): PremiumLoadState {
    if (unsupported) return PremiumLoadState.Unsupported
    val cached = (this as? PremiumLoadState.Ready)?.snapshot
    if (cached == null || cached.accountId != accountId) return PremiumLoadState.Failed(reason)
    // Keep the same account's snapshot even when it expired: the Premium screen must keep the
    // independently-owned gift-code workflow composed. Stale prices are hidden by the UI and
    // isUsableFor/needsRefresh/canCheckout keep them non-actionable until a refresh succeeds.
    return PremiumLoadState.Ready(cached, refreshing = false, refreshError = reason)
}

/** True when the displayed snapshot expired without a failed refresh and should be re-read. */
fun PremiumLoadState.needsRefresh(accountId: String, nowMillis: Long): Boolean =
    this is PremiumLoadState.Ready &&
        !refreshing &&
        refreshError == null &&
        !snapshot.isUsableFor(accountId, nowMillis)

/** Checkout requires fresh, confirmed server data from the same account. */
fun PremiumLoadState.canCheckout(accountId: String, nowMillis: Long): Boolean =
    this is PremiumLoadState.Ready &&
        !refreshing &&
        refreshError == null &&
        snapshot.isUsableFor(accountId, nowMillis)

/**
 * Status, feature and price details are visible only while the same account's snapshot is fresh.
 * The gift-code workflow may stay composed independently when this returns false.
 */
fun PremiumLoadState.showsPremiumDetails(accountId: String, nowMillis: Long): Boolean =
    this is PremiumLoadState.Ready && snapshot.isUsableFor(accountId, nowMillis)

/** Server-side information about a Premium gift code; the code itself is never part of this model. */
data class PremiumGiftCodeInfo(
    val creatorName: String?,
    val creationDateSeconds: Int,
    val isFromGiveaway: Boolean,
    val monthCount: Int,
    val dayCount: Int,
    val isUsed: Boolean,
    val useDateSeconds: Int,
    /** The original recipient, not an exclusive restriction: Telegram allows re-gifting. */
    val wasOriginallyForAnotherUser: Boolean
) {
    val canActivate: Boolean get() = !isUsed
}

data class PremiumGiftDuration(val months: Int, val days: Int) {
    val isKnown: Boolean get() = months > 0 || days > 0
}

fun premiumGiftDuration(monthCount: Int, dayCount: Int): PremiumGiftDuration =
    PremiumGiftDuration(monthCount.coerceAtLeast(0), dayCount.coerceAtLeast(0))

enum class PremiumGiftCodeValidation { Valid, Empty, TooLong, InvalidCharacters }

enum class PremiumGiftCodeError { Invalid, Used, Expired, OtherUser, Other }

/** Result of one explicit Premium gift code activation. */
sealed interface PremiumGiftCodeApplyOutcome {
    data object Activated : PremiumGiftCodeApplyOutcome
    /** A definite server rejection; the code was not applied. */
    data class Blocked(val reason: UiText) : PremiumGiftCodeApplyOutcome
    /** A definite failure; the code was not applied. */
    data class Failed(val reason: UiText) : PremiumGiftCodeApplyOutcome
    /** The request may have reached Telegram; check the Premium status before retrying. */
    data object Uncertain : PremiumGiftCodeApplyOutcome
}

/**
 * Validates user input without guessing server-side format rules. Telegram gift codes
 * are case-sensitive, so normalization never changes the code itself.
 */
fun validatePremiumGiftCode(raw: String): PremiumGiftCodeValidation {
    val value = raw.trim()
    return when {
        value.isEmpty() -> PremiumGiftCodeValidation.Empty
        value.length > PREMIUM_GIFT_CODE_MAX_LENGTH -> PremiumGiftCodeValidation.TooLong
        value.any {
            it.isWhitespace() || it.code < 0x20 || it.code == 0x7F || it.category == CharCategory.FORMAT
        } -> PremiumGiftCodeValidation.InvalidCharacters
        else -> PremiumGiftCodeValidation.Valid
    }
}

/** Accepts a bare code or a t.me/giftcode link; returns null when the input is unusable. */
fun normalizePremiumGiftCode(raw: String): String? {
    var value = raw.trim()
    val lower = value.lowercase(Locale.ROOT)
    val prefix = PREMIUM_GIFT_CODE_PREFIXES.firstOrNull { lower.startsWith(it) }
    if (prefix != null) {
        value = value.substring(prefix.length)
            .substringBefore('?')
            .substringBefore('#')
            .trim()
            .trim('/')
    }
    return value.takeIf { validatePremiumGiftCode(it) == PremiumGiftCodeValidation.Valid }
}

/** Error codes come from Telegram; the client only classifies them for the UI. */
fun classifyPremiumGiftCodeError(message: String?): PremiumGiftCodeError {
    val value = message.orEmpty().uppercase(Locale.ROOT)
    val mentionsCode = value.contains("GIFT_CODE") || value.contains("GIFTCODE")
    return when {
        mentionsCode && value.contains("USED") -> PremiumGiftCodeError.Used
        mentionsCode && value.contains("EXPIRED") -> PremiumGiftCodeError.Expired
        mentionsCode && (value.contains("INVALID") || value.contains("NOT_FOUND")) -> PremiumGiftCodeError.Invalid
        mentionsCode && (value.contains("OTHER_USER") || value.contains("NOT_ALLOWED")) -> PremiumGiftCodeError.OtherUser
        else -> PremiumGiftCodeError.Other
    }
}

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
    InvoicePurchase,
    InvoiceSale,
    Withdrawal,
    Other
}

data class StarTransactionItem(
    val id: String,
    val amount: StarBalance,
    val isRefund: Boolean,
    val dateSeconds: Int,
    val kind: StarTransactionKind
) {
    /** Telegram may reuse a transaction id for its refund; both rows must stay visible. */
    val identityKey: String get() = (if (isRefund) "refund:" else "transaction:") + id
}

data class StarsInfo(
    val balance: StarBalance,
    val transactions: List<StarTransactionItem>,
    val nextOffset: String,
    /** Current packages from TDLib; checkout is confirmed again before any external page opens. */
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
) {
    /** Re-check the exact package before a payment page is opened. */
    fun isSameOfferAs(other: StarTopUpOption): Boolean =
        starCount == other.starCount &&
            amountMinor == other.amountMinor &&
            currency.equals(other.currency, ignoreCase = true)
}

/**
 * An HTTPS payment page resolved by TDLib. Only [validateExternalPaymentUrl] should create it:
 * the app opens this URL in the system browser and never loads it into an embedded WebView.
 */
@JvmInline
value class ExternalPaymentUrl internal constructor(val value: String)

/** One external payment method returned by Telegram's payment form. Titles are display-only. */
data class ExternalPaymentMethod(
    val title: String,
    val url: ExternalPaymentUrl
)

/**
 * Fail-closed URL policy for a payment page. Payment links can contain invoice or session
 * secrets, so they are never logged or persisted; only a validated value may reach an Intent.
 */
fun validateExternalPaymentUrl(raw: String?): ExternalPaymentUrl? {
    val value = raw ?: return null
    if (value.isEmpty() || value.length > PAYMENT_URL_MAX_LENGTH || value != value.trim()) return null
    if (value.any { it.isWhitespace() || it.isISOControl() }) return null
    // Encoded CR/LF is never needed by a checkout URL and is a classic redirect-injection trick.
    if (value.contains("%0d", ignoreCase = true) || value.contains("%0a", ignoreCase = true)) return null
    val uri = try {
        URI(value)
    } catch (_: Exception) {
        return null
    }
    if (!"https".equals(uri.scheme, ignoreCase = true) || uri.isOpaque) return null
    if (uri.rawUserInfo != null) return null
    val host = uri.host ?: return null
    if (!isSafePaymentHost(host)) return null
    // Keep the default port only: an arbitrary port bypasses the expected web endpoint.
    if (uri.port != -1 && uri.port != 443) return null
    return ExternalPaymentUrl(value)
}

private fun isSafePaymentHost(host: String): Boolean {
    if (host.length > PAYMENT_HOST_MAX_LENGTH || '.' !in host) return false
    return host.split('.').all { label ->
        label.isNotEmpty() && label.length <= PAYMENT_HOST_LABEL_MAX_LENGTH && PAYMENT_HOST_LABEL.matches(label)
    }
}

/** TDLib titles are plain text, but controls and excessive length must not reach the UI. */
fun sanitizePaymentMethodTitle(raw: String?): String {
    val cleaned = raw.orEmpty()
        .filterNot { it.isISOControl() || it.category == CharCategory.FORMAT }
        .replace(WHITESPACE_RUN, " ")
        .trim()
    if (cleaned.codePointCount(0, cleaned.length) <= PAYMENT_METHOD_TITLE_MAX_LENGTH) return cleaned
    return cleaned.substring(0, cleaned.offsetByCodePoints(0, PAYMENT_METHOD_TITLE_MAX_LENGTH))
}

/** Result of resolving an external Premium checkout page. No request is retried by the UI. */
sealed interface PremiumCheckoutOutcome {
    data class Ready(val url: ExternalPaymentUrl) : PremiumCheckoutOutcome
    /** Telegram no longer offers the exact plan/price the user confirmed. */
    data object PlanChanged : PremiumCheckoutOutcome
    /** TDLib offered no link that this app can safely open outside a WebView. */
    data object Unavailable : PremiumCheckoutOutcome
    data object Busy : PremiumCheckoutOutcome
    data class Failed(val reason: UiText) : PremiumCheckoutOutcome
}

/** Result of preparing a Stars top-up. A Stars-spending form is explicitly not a top-up. */
sealed interface StarsTopUpOutcome {
    data class Ready(val methods: List<ExternalPaymentMethod>) : StarsTopUpOutcome
    /** Telegram no longer offers the exact package/price the user confirmed. */
    data object PackageChanged : StarsTopUpOutcome
    /** The payment form needs card fields or saved credentials, which this app deliberately lacks. */
    data object Unavailable : StarsTopUpOutcome
    /** The form would spend existing Stars instead of adding new ones; it must never be submitted. */
    data class SpendsStars(val starCount: Long) : StarsTopUpOutcome
    /** Recurring Star subscriptions are outside this flow. */
    data object SubscriptionUnsupported : StarsTopUpOutcome
    data object Busy : StarsTopUpOutcome
    data class Failed(val reason: UiText) : StarsTopUpOutcome
}

enum class PremiumPlanBlock { Busy, CurrentPlan, NoExternalCheckout }

/**
 * Client-side gate for a plan row. [step] also blocks a second checkout while one is being
 * prepared or while the browser is in front of the app.
 */
fun premiumPlanBlock(plan: PremiumPlan, step: PremiumCheckoutStep): PremiumPlanBlock? = when {
    step !is PremiumCheckoutStep.Idle && step !is PremiumCheckoutStep.Failed -> PremiumPlanBlock.Busy
    plan.isCurrent && !plan.isUpgrade -> PremiumPlanBlock.CurrentPlan
    !plan.hasExternalPaymentLink -> PremiumPlanBlock.NoExternalCheckout
    else -> null
}

/** A checkout must be confirmed, prepared and opened exactly once; the URL is dropped on open. */
sealed interface PremiumCheckoutStep {
    data object Idle : PremiumCheckoutStep
    data class Confirming(val plan: PremiumPlan) : PremiumCheckoutStep
    data class Preparing(val plan: PremiumPlan) : PremiumCheckoutStep
    data class LinkReady(val plan: PremiumPlan, val url: ExternalPaymentUrl) : PremiumCheckoutStep
    data class AwaitingReturn(val plan: PremiumPlan) : PremiumCheckoutStep
    data class Failed(val plan: PremiumPlan, val reason: UiText) : PremiumCheckoutStep
}

sealed interface PremiumCheckoutEvent {
    data class Select(val plan: PremiumPlan) : PremiumCheckoutEvent
    data object Confirm : PremiumCheckoutEvent
    data class Ready(val url: ExternalPaymentUrl) : PremiumCheckoutEvent
    data object Opened : PremiumCheckoutEvent
    data object Returned : PremiumCheckoutEvent
    data class Failed(val reason: UiText) : PremiumCheckoutEvent
    data object Dismiss : PremiumCheckoutEvent
}

fun PremiumCheckoutStep.next(event: PremiumCheckoutEvent): PremiumCheckoutStep = when (this) {
    is PremiumCheckoutStep.Confirming -> when (event) {
        PremiumCheckoutEvent.Confirm -> PremiumCheckoutStep.Preparing(plan)
        PremiumCheckoutEvent.Dismiss, PremiumCheckoutEvent.Returned -> PremiumCheckoutStep.Idle
        else -> this
    }
    is PremiumCheckoutStep.Preparing -> when (event) {
        is PremiumCheckoutEvent.Ready -> PremiumCheckoutStep.LinkReady(plan, event.url)
        is PremiumCheckoutEvent.Failed -> PremiumCheckoutStep.Failed(plan, event.reason)
        PremiumCheckoutEvent.Dismiss -> PremiumCheckoutStep.Idle
        else -> this
    }
    is PremiumCheckoutStep.LinkReady -> when (event) {
        PremiumCheckoutEvent.Opened -> PremiumCheckoutStep.AwaitingReturn(plan)
        is PremiumCheckoutEvent.Failed -> PremiumCheckoutStep.Failed(plan, event.reason)
        else -> this
    }
    is PremiumCheckoutStep.AwaitingReturn -> when (event) {
        PremiumCheckoutEvent.Returned -> PremiumCheckoutStep.Idle
        // A new plan or a second confirmation cannot start while the browser may still be open.
        else -> this
    }
    is PremiumCheckoutStep.Failed -> when (event) {
        is PremiumCheckoutEvent.Select -> PremiumCheckoutStep.Confirming(event.plan)
        PremiumCheckoutEvent.Dismiss, PremiumCheckoutEvent.Returned -> PremiumCheckoutStep.Idle
        else -> this
    }
    is PremiumCheckoutStep.Idle -> when (event) {
        is PremiumCheckoutEvent.Select -> PremiumCheckoutStep.Confirming(event.plan)
        else -> this
    }
}

/** The Stars top-up state machine mirrors Premium: one browser hand-off at a time. */
sealed interface StarsTopUpStep {
    data object Idle : StarsTopUpStep
    data class Confirming(val option: StarTopUpOption) : StarsTopUpStep
    data class Preparing(val option: StarTopUpOption) : StarsTopUpStep
    data class Ready(val option: StarTopUpOption, val methods: List<ExternalPaymentMethod>) : StarsTopUpStep
    data class Opening(val option: StarTopUpOption, val method: ExternalPaymentMethod) : StarsTopUpStep
    data class AwaitingReturn(val option: StarTopUpOption) : StarsTopUpStep
    data class Failed(val option: StarTopUpOption, val reason: UiText) : StarsTopUpStep
}

sealed interface StarsTopUpEvent {
    data class Select(val option: StarTopUpOption) : StarsTopUpEvent
    data object Confirm : StarsTopUpEvent
    data class Ready(val methods: List<ExternalPaymentMethod>) : StarsTopUpEvent
    data class Open(val method: ExternalPaymentMethod) : StarsTopUpEvent
    data object Opened : StarsTopUpEvent
    data object Returned : StarsTopUpEvent
    data class Failed(val reason: UiText) : StarsTopUpEvent
    data object Dismiss : StarsTopUpEvent
}

fun StarsTopUpStep.next(event: StarsTopUpEvent): StarsTopUpStep = when (this) {
    is StarsTopUpStep.Confirming -> when (event) {
        StarsTopUpEvent.Confirm -> StarsTopUpStep.Preparing(option)
        StarsTopUpEvent.Dismiss, StarsTopUpEvent.Returned -> StarsTopUpStep.Idle
        else -> this
    }
    is StarsTopUpStep.Preparing -> when (event) {
        is StarsTopUpEvent.Ready -> StarsTopUpStep.Ready(option, event.methods)
        is StarsTopUpEvent.Failed -> StarsTopUpStep.Failed(option, event.reason)
        StarsTopUpEvent.Dismiss -> StarsTopUpStep.Idle
        else -> this
    }
    is StarsTopUpStep.Ready -> when (event) {
        // Only a method returned by this form may be opened; an arbitrary URL cannot be injected.
        is StarsTopUpEvent.Open -> if (event.method in methods) {
            StarsTopUpStep.Opening(option, event.method)
        } else {
            this
        }
        StarsTopUpEvent.Dismiss, StarsTopUpEvent.Returned -> StarsTopUpStep.Idle
        is StarsTopUpEvent.Failed -> StarsTopUpStep.Failed(option, event.reason)
        else -> this
    }
    is StarsTopUpStep.Opening -> when (event) {
        StarsTopUpEvent.Opened -> StarsTopUpStep.AwaitingReturn(option)
        is StarsTopUpEvent.Failed -> StarsTopUpStep.Failed(option, event.reason)
        else -> this
    }
    is StarsTopUpStep.AwaitingReturn -> when (event) {
        StarsTopUpEvent.Returned -> StarsTopUpStep.Idle
        else -> this
    }
    is StarsTopUpStep.Failed -> when (event) {
        is StarsTopUpEvent.Select -> StarsTopUpStep.Confirming(event.option)
        StarsTopUpEvent.Dismiss, StarsTopUpEvent.Returned -> StarsTopUpStep.Idle
        else -> this
    }
    is StarsTopUpStep.Idle -> when (event) {
        is StarsTopUpEvent.Select -> StarsTopUpStep.Confirming(event.option)
        else -> this
    }
}

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
    /** Cursor for the next received-gifts page; empty when the list is complete. */
    val receivedNextOffset: String,
    val collections: List<GiftCollectionItem>,
    val isPremium: Boolean,
    val receivedUnavailable: Boolean,
    val collectionsUnavailable: Boolean,
    /** Longest gift message TDLib reports; the send dialog validates against it before any purchase. */
    val textMaxLength: Int
)

data class ReceivedGiftsPage(
    val gifts: List<ReceivedGiftItem>,
    val nextOffset: String
)

/** Used only when TDLib does not report the gift message limit. */
const val GIFT_TEXT_MAX_LENGTH_DEFAULT = 128

/** TDLib reports the limit as an integer option; a missing or implausible value keeps the default. */
fun giftTextLimit(serverValue: Long?): Int =
    serverValue?.takeIf { it in 1L..GIFT_TEXT_MAX_LENGTH_CEILING }?.toInt() ?: GIFT_TEXT_MAX_LENGTH_DEFAULT

enum class GiftTextValidation { Valid, TooLong }

/**
 * Checks the gift message as typed. The length is counted in UTF-16 units, the same measure
 * the send dialog's counter shows, so what the user sees is what gets validated.
 */
fun validateGiftText(raw: String, maxLength: Int): GiftTextValidation =
    if (raw.length > maxLength) GiftTextValidation.TooLong else GiftTextValidation.Valid

/**
 * Appends a received-gifts page. New gifts can shift the pages, so one gift may arrive twice;
 * each received gift id must appear only once, keeping the first copy.
 */
fun mergeReceivedGifts(current: List<ReceivedGiftItem>, next: List<ReceivedGiftItem>): List<ReceivedGiftItem> =
    (current + next).distinctBy { it.id }

/**
 * A cursor that comes back unchanged means the list is complete; requesting it again would
 * return the same page forever.
 */
fun nextReceivedGiftsOffset(requestedOffset: String, returnedOffset: String): String =
    if (returnedOffset.isBlank() || returnedOffset == requestedOffset) "" else returnedOffset

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
 * Checkout is delegated to Telegram's payment page outside the app. The client implements
 * the hand-off, but actual availability still depends on TDLib and the region. The app never
 * collects card data and never calls SendPaymentForm with credentials or saved cards.
 */
object MonetizationPayments {
    const val externalCheckoutHandoffImplemented = true
    const val nativePaymentFormImplemented = false
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

/** A code is checked, shown for confirmation and only then applied. */
sealed interface PremiumGiftCodeStep {
    data object Idle : PremiumGiftCodeStep
    data object Checking : PremiumGiftCodeStep
    data class Confirming(val info: PremiumGiftCodeInfo) : PremiumGiftCodeStep
    data class Applying(val info: PremiumGiftCodeInfo) : PremiumGiftCodeStep
    data class Activated(val info: PremiumGiftCodeInfo) : PremiumGiftCodeStep
    data class Failed(val uncertain: Boolean) : PremiumGiftCodeStep
}

sealed interface PremiumGiftCodeEvent {
    data object Check : PremiumGiftCodeEvent
    data class Checked(val info: PremiumGiftCodeInfo) : PremiumGiftCodeEvent
    data object Apply : PremiumGiftCodeEvent
    data object Applied : PremiumGiftCodeEvent
    data object Failed : PremiumGiftCodeEvent
    data object Uncertain : PremiumGiftCodeEvent
    data object Reset : PremiumGiftCodeEvent
}

fun PremiumGiftCodeStep.next(event: PremiumGiftCodeEvent): PremiumGiftCodeStep = when (this) {
    is PremiumGiftCodeStep.Applying -> when (event) {
        PremiumGiftCodeEvent.Applied -> PremiumGiftCodeStep.Activated(info)
        PremiumGiftCodeEvent.Failed -> PremiumGiftCodeStep.Failed(uncertain = false)
        PremiumGiftCodeEvent.Uncertain -> PremiumGiftCodeStep.Failed(uncertain = true)
        // A check, reset or a second apply must not discard an in-flight activation.
        else -> this
    }
    is PremiumGiftCodeStep.Confirming -> when (event) {
        PremiumGiftCodeEvent.Apply -> PremiumGiftCodeStep.Applying(info)
        PremiumGiftCodeEvent.Reset -> PremiumGiftCodeStep.Idle
        else -> this
    }
    is PremiumGiftCodeStep.Checking -> when (event) {
        is PremiumGiftCodeEvent.Checked -> PremiumGiftCodeStep.Confirming(event.info)
        PremiumGiftCodeEvent.Failed -> PremiumGiftCodeStep.Failed(uncertain = false)
        PremiumGiftCodeEvent.Reset -> PremiumGiftCodeStep.Idle
        else -> this
    }
    is PremiumGiftCodeStep.Idle, is PremiumGiftCodeStep.Activated, is PremiumGiftCodeStep.Failed -> when (event) {
        PremiumGiftCodeEvent.Check -> PremiumGiftCodeStep.Checking
        PremiumGiftCodeEvent.Reset -> PremiumGiftCodeStep.Idle
        else -> this
    }
}

/** Formats a price in minor units without relying on device currency display names. */
fun formatPriceMinorUnits(amountMinor: Long, currencyCode: String): String {
    val code = currencyCode.trim().uppercase(Locale.ROOT)
    if (code.isEmpty()) return amountMinor.toString()
    // Telegram Stars (XTR) have no fractional unit, unlike ISO 4217 test currency defaults.
    if (code == "XTR") return "${formatStarAmount(amountMinor)} Stars"
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

private const val PREMIUM_GIFT_CODE_MAX_LENGTH = 128
private const val GIFT_TEXT_MAX_LENGTH_CEILING = 4096L
private const val PAYMENT_URL_MAX_LENGTH = 4096
private const val PAYMENT_HOST_MAX_LENGTH = 253
private const val PAYMENT_HOST_LABEL_MAX_LENGTH = 63
private const val PAYMENT_METHOD_TITLE_MAX_LENGTH = 80
private val PAYMENT_HOST_LABEL = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")
private val WHITESPACE_RUN = Regex("\\s+")
private val PREMIUM_GIFT_CODE_PREFIXES = listOf(
    "https://t.me/giftcode/",
    "http://t.me/giftcode/",
    "https://telegram.me/giftcode/",
    "http://telegram.me/giftcode/",
    "t.me/giftcode/",
    "telegram.me/giftcode/"
)
