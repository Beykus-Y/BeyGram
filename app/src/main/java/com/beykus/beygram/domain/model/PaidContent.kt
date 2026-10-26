package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText

/** TDLib's currency code for Telegram Stars. Stars have no fractional unit. */
const val TELEGRAM_STARS_CURRENCY = "XTR"

/** One paid message. The same message can be shown in the chat, in a comment thread or in a context dialog. */
data class PaidContentKey(val chatId: Long, val messageId: Long)

/**
 * The payment form of one paid message, reduced to what the app may act on. Only [Stars] is ever
 * submitted; the other kinds are either offered as external pages or refused.
 */
sealed interface PaidContentFormKind {
    /** Telegram Stars at the price TDLib currently asks for. */
    data class Stars(val starCount: Long) : PaidContentFormKind

    /** A real-money or provider form. Only its validated HTTPS pages may be offered, never card fields. */
    data class External(val methods: List<ExternalPaymentMethod>) : PaidContentFormKind

    /** A recurring Star subscription, which this app does not handle. */
    data object Subscription : PaidContentFormKind

    /** A form type this build does not know. */
    data object Unknown : PaidContentFormKind
}

/** What the user may do after a payment form was read. Reading a form never spends anything. */
sealed interface PaidContentPlan {
    data class ConfirmStars(val starCount: Long, val balance: StarBalance) : PaidContentPlan
    data class InsufficientStars(val starCount: Long, val balance: StarBalance) : PaidContentPlan
    data object BalanceUnknown : PaidContentPlan
    data class OpenExternal(val methods: List<ExternalPaymentMethod>) : PaidContentPlan
    data object SubscriptionUnsupported : PaidContentPlan
    data object Unavailable : PaidContentPlan
    data class Failed(val reason: UiText) : PaidContentPlan
}

/**
 * Stars can be confirmed only when the price is known and the balance covers it. A missing balance blocks the
 * confirmation, because the user must see the balance before any Stars leave the account.
 */
fun paidContentPlanFor(form: PaidContentFormKind, balance: StarBalance?): PaidContentPlan = when (form) {
    is PaidContentFormKind.Stars -> when {
        form.starCount <= 0L -> PaidContentPlan.Unavailable
        balance == null -> PaidContentPlan.BalanceUnknown
        balance.starCount < form.starCount -> PaidContentPlan.InsufficientStars(form.starCount, balance)
        else -> PaidContentPlan.ConfirmStars(form.starCount, balance)
    }
    is PaidContentFormKind.External ->
        if (form.methods.isEmpty()) PaidContentPlan.Unavailable else PaidContentPlan.OpenExternal(form.methods)
    PaidContentFormKind.Subscription -> PaidContentPlan.SubscriptionUnsupported
    PaidContentFormKind.Unknown -> PaidContentPlan.Unavailable
}

/** The decision taken again just before SendPaymentForm, from the form that was read at payment time. */
sealed interface PaidContentPayGate {
    data object Send : PaidContentPayGate
    data object PriceChanged : PaidContentPayGate
    data object NotStars : PaidContentPayGate
}

/**
 * Only a Stars form for exactly the amount the user confirmed may be submitted. Every other form, and a changed
 * price, is refused before SendPaymentForm is called.
 */
fun paidContentPayGate(form: PaidContentFormKind, confirmedStarCount: Long): PaidContentPayGate = when (form) {
    is PaidContentFormKind.Stars ->
        if (confirmedStarCount > 0L && form.starCount == confirmedStarCount) PaidContentPayGate.Send
        else PaidContentPayGate.PriceChanged
    is PaidContentFormKind.External, PaidContentFormKind.Subscription, PaidContentFormKind.Unknown ->
        PaidContentPayGate.NotStars
}

/** Result of one payment attempt. Only [InsufficientStars], [FloodWait], [Failed] and the gate refusals spend nothing. */
sealed interface PaidContentPaymentOutcome {
    data object Paid : PaidContentPaymentOutcome
    data object InsufficientStars : PaidContentPaymentOutcome
    data object FloodWait : PaidContentPaymentOutcome
    data object PriceChanged : PaidContentPaymentOutcome
    data object NotPayable : PaidContentPaymentOutcome
    data object Busy : PaidContentPaymentOutcome

    /** A definite refusal reported by Telegram. No Stars were spent. */
    data class Failed(val reason: UiText) : PaidContentPaymentOutcome

    /** The request may have reached Telegram. The user must check the chat before paying again. */
    data object Uncertain : PaidContentPaymentOutcome
}

/** Telegram reports an unaffordable Stars payment as BALANCE_TOO_LOW. */
fun isInsufficientStarsError(message: String?): Boolean =
    message.orEmpty().contains("BALANCE_TOO_LOW", ignoreCase = true)

/** Classifies a refusal from TDLib before any generic text is shown. Null means no specific class applies. */
fun paidContentErrorOutcome(code: Int?, message: String?): PaidContentPaymentOutcome? = when {
    isInsufficientStarsError(message) -> PaidContentPaymentOutcome.InsufficientStars
    code == TOO_MANY_REQUESTS_CODE || message.orEmpty().contains("FLOOD_WAIT", ignoreCase = true) ->
        PaidContentPaymentOutcome.FloodWait
    else -> null
}

private const val TOO_MANY_REQUESTS_CODE = 429
