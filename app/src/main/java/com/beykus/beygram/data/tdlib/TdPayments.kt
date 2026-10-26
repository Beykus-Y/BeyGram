package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.ExternalPaymentMethod
import com.beykus.beygram.domain.model.GiftCatalogItem
import com.beykus.beygram.domain.model.GiftCollectionItem
import com.beykus.beygram.domain.model.GiftSendOutcome
import com.beykus.beygram.domain.model.GiftsInfo
import com.beykus.beygram.domain.model.PremiumCheckoutOutcome
import com.beykus.beygram.domain.model.PremiumFeatureKind
import com.beykus.beygram.domain.model.PremiumGiftCodeApplyOutcome
import com.beykus.beygram.domain.model.PremiumGiftCodeError
import com.beykus.beygram.domain.model.PremiumGiftCodeInfo
import com.beykus.beygram.domain.model.PremiumInfo
import com.beykus.beygram.domain.model.PremiumPlan
import com.beykus.beygram.domain.model.StarsTopUpOutcome
import com.beykus.beygram.domain.model.classifyPremiumGiftCodeError
import com.beykus.beygram.domain.model.normalizePremiumGiftCode
import com.beykus.beygram.domain.model.sanitizePaymentMethodTitle
import com.beykus.beygram.domain.model.validateExternalPaymentUrl
import com.beykus.beygram.domain.model.ReceivedGiftItem
import com.beykus.beygram.domain.model.StarBalance
import com.beykus.beygram.domain.model.StarTopUpOption
import com.beykus.beygram.domain.model.StarTransactionItem
import com.beykus.beygram.domain.model.StarTransactionKind
import com.beykus.beygram.domain.model.StarTransactionsPage
import com.beykus.beygram.domain.model.StarsInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Premium/Stars/Gifts access, one explicit gift purchase, Premium gift code activation and
 * external checkout preparation. TDLib owns balances and prices; this class never invents
 * payment data, logs codes or URLs, collects card details, calls SendPaymentForm, or retries
 * a mutation on its own.
 */
internal class TdPayments(private val requests: TdRequests) {
    @Volatile private var cachedUserId = 0L
    private val userNames = ConcurrentHashMap<Long, String>()
    private val sendInFlight = AtomicBoolean(false)
    // Preparing a checkout is read-only, but one browser hand-off at a time prevents double taps.
    private val checkoutPreparationInFlight = AtomicBoolean(false)

    suspend fun premiumInfo(): Result<PremiumInfo> = premiumInfoResult {
        coroutineScope {
            // Independent reads run in parallel so one slow response cannot consume the whole budget.
            val state = async { requests(TdApi.GetPremiumState()) }
            val features = async { requests(TdApi.GetPremiumFeatures(null)) }
            val me = async { requests(TdApi.GetMe()) }
            val premiumState = state.await()
            val premiumFeatures = features.await()
            val currentUser = me.await()
            cachedUserId = currentUser.id
            PremiumInfo(
                isPremium = currentUser.isPremium,
                statusText = premiumState.state?.text.orEmpty(),
                features = premiumFeatures.features.orEmpty().map { it.toPremiumFeatureKind() }.distinct(),
                plans = premiumState.paymentOptions.orEmpty().mapNotNull { option ->
                    option.paymentOption?.let { payment ->
                        PremiumPlan(
                            monthCount = payment.monthCount.coerceAtLeast(1),
                            amountMinor = payment.amount,
                            currency = payment.currency.orEmpty(),
                            discountPercent = payment.discountPercentage,
                            isCurrent = option.isCurrent,
                            isUpgrade = option.isUpgrade,
                            hasExternalPaymentLink = payment.paymentLink != null
                        )
                    }
                }.sortedWith(compareBy({ it.monthCount }, { it.amountMinor }, { it.currency }))
            )
        }
    }

    /**
     * Bounds every Premium screen read as a whole: status, features and plans arrive together or
     * the screen gets a retryable timeout instead of an indefinitely spinning empty page.
     */
    internal suspend fun <T> premiumInfoResult(
        timeoutMillis: Long = PREMIUM_INFO_TIMEOUT_MS,
        block: suspend () -> T
    ): Result<T> = monetizationResult {
        withTimeout(timeoutMillis) { block() }
    }

    suspend fun checkPremiumGiftCode(code: String): Result<PremiumGiftCodeInfo> {
        val normalized = normalizePremiumGiftCode(code)
            ?: return Result.failure(UserMessageException(UiText.Res(R.string.err_premium_gift_code_invalid)))
        return monetizationResult(errorText = { premiumGiftCodeFailureText(it) }) {
            val info = requests(TdApi.CheckPremiumGiftCode(normalized))
            val meId = try {
                myUserId()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                0L
            }
            PremiumGiftCodeInfo(
                creatorName = senderDisplayName(info.creatorId),
                creationDateSeconds = info.creationDate,
                isFromGiveaway = info.isFromGiveaway,
                monthCount = info.monthCount,
                dayCount = info.dayCount,
                isUsed = info.useDate > 0,
                useDateSeconds = info.useDate,
                wasOriginallyForAnotherUser = info.userId != 0L && meId != 0L && info.userId != meId
            )
        }
    }

    /** Applies one code only after the UI confirmed its server-side details. */
    suspend fun applyPremiumGiftCode(code: String): PremiumGiftCodeApplyOutcome {
        val normalized = normalizePremiumGiftCode(code)
            ?: return PremiumGiftCodeApplyOutcome.Blocked(UiText.Res(R.string.err_premium_gift_code_invalid))
        return try {
            requests(TdApi.ApplyPremiumGiftCode(normalized), timeoutMillis = PREMIUM_GIFT_CODE_APPLY_TIMEOUT_MS)
            PremiumGiftCodeApplyOutcome.Activated
        } catch (_: TimeoutCancellationException) {
            PremiumGiftCodeApplyOutcome.Uncertain
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: TdRequestError) {
            when (classifyPremiumGiftCodeError(error.message)) {
                PremiumGiftCodeError.Invalid,
                PremiumGiftCodeError.Used,
                PremiumGiftCodeError.Expired,
                PremiumGiftCodeError.OtherUser ->
                    PremiumGiftCodeApplyOutcome.Blocked(premiumGiftCodeFailureText(error))
                PremiumGiftCodeError.Other -> PremiumGiftCodeApplyOutcome.Failed(failureText(error))
            }
        } catch (error: UserMessageException) {
            PremiumGiftCodeApplyOutcome.Blocked(error.text)
        } catch (_: Exception) {
            // The request may already have reached Telegram; never retry it blindly.
            PremiumGiftCodeApplyOutcome.Uncertain
        }
    }

    suspend fun starsInfo(): Result<StarsInfo> = monetizationResult {
        val page = starTransactionsPage(offset = "", limit = FIRST_PAGE_SIZE)
        var optionsUnavailable = false
        val options = try {
            requests(TdApi.GetStarPaymentOptions()).options.orEmpty().map { option ->
                StarTopUpOption(
                    starCount = option.starCount,
                    amountMinor = option.amount,
                    currency = option.currency.orEmpty(),
                    isAdditional = option.isAdditional
                )
            }
        } catch (_: TimeoutCancellationException) {
            optionsUnavailable = true
            emptyList()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            optionsUnavailable = true
            emptyList()
        }
        StarsInfo(
            balance = page.balance,
            transactions = page.transactions,
            nextOffset = page.nextOffset,
            topUpOptions = options,
            topUpOptionsUnavailable = optionsUnavailable
        )
    }

    /**
     * Re-reads Premium prices and resolves Telegram's own out-of-store payment link. This
     * never charges anything and never calls SendPaymentForm: the browser owns the checkout.
     */
    suspend fun premiumPaymentUrl(plan: PremiumPlan): PremiumCheckoutOutcome {
        if (!checkoutPreparationInFlight.compareAndSet(false, true)) return PremiumCheckoutOutcome.Busy
        return try {
            val state = requests(TdApi.GetPremiumState())
            val option = state.paymentOptions.orEmpty()
                .firstOrNull { candidate ->
                    candidate.paymentOption?.matches(plan) == true &&
                        candidate.isCurrent == plan.isCurrent &&
                        candidate.isUpgrade == plan.isUpgrade
                }
                ?: return PremiumCheckoutOutcome.PlanChanged
            val payment = option.paymentOption ?: return PremiumCheckoutOutcome.Unavailable
            if (payment.amount <= 0L || payment.currency.orEmpty().isBlank()) {
                return PremiumCheckoutOutcome.Unavailable
            }
            val paymentLink = payment.paymentLink ?: return PremiumCheckoutOutcome.Unavailable
            val httpUrl = try {
                requests(TdApi.GetInternalLink(paymentLink, true))
            } catch (_: TimeoutCancellationException) {
                return PremiumCheckoutOutcome.Failed(UiText.Res(R.string.err_monetization_timeout))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Auth/flood errors must stay visible; an unsupported link type is a safe fallback.
                return if (isBlockingPaymentError(error)) {
                    PremiumCheckoutOutcome.Failed(failureText(error))
                } else {
                    PremiumCheckoutOutcome.Unavailable
                }
            }
            val url = validateExternalPaymentUrl(httpUrl.url) ?: return PremiumCheckoutOutcome.Unavailable
            // Informational only, exactly once after the user confirmed the plan. A failure here
            // must not hide the external checkout, and it never triggers a second call.
            clickPremiumSubscriptionBestEffort()
            PremiumCheckoutOutcome.Ready(url)
        } catch (timeout: TimeoutCancellationException) {
            PremiumCheckoutOutcome.Failed(UiText.Res(R.string.err_monetization_timeout))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (isPaymentUnavailableError(error)) PremiumCheckoutOutcome.Unavailable
            else PremiumCheckoutOutcome.Failed(failureText(error))
        } finally {
            checkoutPreparationInFlight.set(false)
        }
    }

    /**
     * Prepares a Stars top-up through Telegram's out-of-store invoice. Only URLs that TDLib
     * returns in additionalPaymentOptions are exposed; a card form or a Stars-spending form
     * is rejected because neither can be completed safely in this app.
     */
    suspend fun starsTopUpPayment(option: StarTopUpOption): StarsTopUpOutcome {
        if (!checkoutPreparationInFlight.compareAndSet(false, true)) return StarsTopUpOutcome.Busy
        return try {
            val current = requests(TdApi.GetStarPaymentOptions()).options.orEmpty()
                .firstOrNull { candidate -> candidate.matches(option) }
                ?: return StarsTopUpOutcome.PackageChanged
            if (current.currency.orEmpty().isBlank() || current.amount <= 0L || current.starCount <= 0L) {
                return StarsTopUpOutcome.Unavailable
            }
            val form = requests(
                TdApi.GetPaymentForm(
                    TdApi.InputInvoiceTelegram(
                        TdApi.TelegramPaymentPurposeStars(
                            current.currency,
                            current.amount,
                            current.starCount,
                            0L
                        )
                    ),
                    null
                )
            )
            when (val type = form.type) {
                is TdApi.PaymentFormTypeRegular -> {
                    val invoice = type.invoice ?: return StarsTopUpOutcome.Unavailable
                    // A test invoice or a recurring subscription must never be opened as a top-up.
                    if (invoice.isTest) return StarsTopUpOutcome.Unavailable
                    if (invoice.subscriptionPeriod > 0) return StarsTopUpOutcome.SubscriptionUnsupported
                    if (!invoice.currency.orEmpty().equals(current.currency, ignoreCase = true)) {
                        return StarsTopUpOutcome.PackageChanged
                    }
                    val total = invoice.priceParts.orEmpty().sumOf { it.amount }
                    if (total != current.amount) return StarsTopUpOutcome.PackageChanged
                    val methods = type.additionalPaymentOptions.orEmpty()
                        .mapNotNull { paymentOption ->
                            val url = validateExternalPaymentUrl(paymentOption.url) ?: return@mapNotNull null
                            ExternalPaymentMethod(
                                title = sanitizePaymentMethodTitle(paymentOption.title),
                                url = url
                            )
                        }
                        .distinctBy { it.url.value }
                    // No URL means the provider expects card fields in the client. BeyGram does not
                    // collect them and never fabricates an InputCredentials/SendPaymentForm call.
                    if (methods.isEmpty()) StarsTopUpOutcome.Unavailable
                    else StarsTopUpOutcome.Ready(methods)
                }
                is TdApi.PaymentFormTypeStars -> StarsTopUpOutcome.SpendsStars(type.starCount)
                is TdApi.PaymentFormTypeStarSubscription -> StarsTopUpOutcome.SubscriptionUnsupported
                else -> StarsTopUpOutcome.Unavailable
            }
        } catch (timeout: TimeoutCancellationException) {
            StarsTopUpOutcome.Failed(UiText.Res(R.string.err_monetization_timeout))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (isPaymentUnavailableError(error)) StarsTopUpOutcome.Unavailable
            else StarsTopUpOutcome.Failed(failureText(error))
        } finally {
            checkoutPreparationInFlight.set(false)
        }
    }

    private suspend fun clickPremiumSubscriptionBestEffort() {
        try {
            requests(TdApi.ClickPremiumSubscriptionButton(), timeoutMillis = PREMIUM_CLICK_TIMEOUT_MS)
        } catch (_: TimeoutCancellationException) {
            // The link is already resolved; a missed analytics click must not block checkout.
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Best effort only.
        }
    }

    suspend fun starTransactions(offset: String): Result<StarTransactionsPage> = monetizationResult {
        starTransactionsPage(offset = offset, limit = PAGE_SIZE)
    }

    suspend fun giftsInfo(profileId: Long, isSelf: Boolean): Result<GiftsInfo> = monetizationResult {
        val me = requests(TdApi.GetMe())
        cachedUserId = me.id
        val owner = if (isSelf) TdApi.MessageSenderUser(me.id) else giftOwner(profileId)
        val catalog = requests(TdApi.GetAvailableGifts()).gifts.orEmpty()
            .mapNotNull { it.toCatalogItem() }
            .sortedBy { it.starCount }
        val balance = try {
            currentBalanceOrNull()
        } catch (_: TimeoutCancellationException) {
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        var received: List<ReceivedGiftItem> = emptyList()
        var receivedUnavailable = false
        var collections: List<GiftCollectionItem> = emptyList()
        var collectionsUnavailable = false
        if (isSelf) {
            try {
                val page = requests(
                    TdApi.GetReceivedGifts(
                        "", owner, 0,
                        false, false, false, false, false, false, false, false, false,
                        "", RECEIVED_LIMIT
                    )
                )
                received = receivedItems(page.gifts.orEmpty().toList())
            } catch (_: TimeoutCancellationException) {
                receivedUnavailable = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                receivedUnavailable = true
            }
            try {
                collections = requests(TdApi.GetGiftCollections(owner)).collections.orEmpty().map { collection ->
                    GiftCollectionItem(
                        id = collection.id,
                        name = collection.name,
                        giftCount = collection.giftCount,
                        emoji = collection.icon?.emoji.orEmpty()
                    )
                }
            } catch (_: TimeoutCancellationException) {
                collectionsUnavailable = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                collectionsUnavailable = true
            }
        }
        GiftsInfo(
            balance = balance,
            catalog = catalog,
            received = received,
            collections = collections,
            isPremium = me.isPremium,
            receivedUnavailable = receivedUnavailable,
            collectionsUnavailable = collectionsUnavailable
        )
    }

    /**
     * Sends exactly one gift. Only one request can be in flight; a timeout is
     * reported as [GiftSendOutcome.Uncertain] so the screen never retries blindly.
     */
    suspend fun sendGift(profileId: Long, isSelf: Boolean, giftId: Long): GiftSendOutcome {
        if (!sendInFlight.compareAndSet(false, true)) {
            return GiftSendOutcome.Blocked(UiText.Res(R.string.err_gift_send_busy))
        }
        try {
            val owner = try {
                if (isSelf) TdApi.MessageSenderUser(myUserId()) else giftOwner(profileId)
            } catch (_: TimeoutCancellationException) {
                return GiftSendOutcome.Failed(UiText.Res(R.string.err_monetization_timeout))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                return GiftSendOutcome.Blocked(failureText(error))
            }
            val gift = try {
                availableGift(giftId)
            } catch (_: TimeoutCancellationException) {
                return GiftSendOutcome.Failed(UiText.Res(R.string.err_monetization_timeout))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                return GiftSendOutcome.Failed(failureText(error))
            } ?: return GiftSendOutcome.Blocked(UiText.Res(R.string.err_gift_unavailable))
            val canSend = try {
                requests(TdApi.CanSendGift(giftId))
            } catch (_: TimeoutCancellationException) {
                return GiftSendOutcome.Failed(UiText.Res(R.string.err_monetization_timeout))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                return GiftSendOutcome.Failed(failureText(error))
            }
            when (canSend) {
                is TdApi.CanSendGiftResultFail -> return GiftSendOutcome.Blocked(
                    canSend.reason?.text?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) }
                        ?: UiText.Res(R.string.err_gift_unavailable)
                )
                is TdApi.CanSendGiftResultOk -> Unit
                else -> return GiftSendOutcome.Blocked(UiText.Res(R.string.err_gift_unavailable))
            }
            val balance = try {
                currentBalanceOrNull()
            } catch (_: TimeoutCancellationException) {
                return GiftSendOutcome.Failed(UiText.Res(R.string.err_monetization_timeout))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                return GiftSendOutcome.Failed(failureText(error))
            }
            if (balance != null && balance.starCount < gift.starCount) {
                return GiftSendOutcome.Blocked(UiText.Res(R.string.err_gift_insufficient_stars))
            }
            return try {
                requests(
                    TdApi.SendGift(giftId, owner, TdApi.FormattedText("", emptyArray()), false, false),
                    timeoutMillis = GIFT_SEND_TIMEOUT_MS
                )
                GiftSendOutcome.Sent
            } catch (_: TimeoutCancellationException) {
                GiftSendOutcome.Uncertain
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: TdRequestError) {
                when {
                    error.message?.contains("STARGIFT_USAGE_LIMITED", ignoreCase = true) == true ->
                        GiftSendOutcome.Blocked(UiText.Res(R.string.err_gift_sold_out))
                    else -> GiftSendOutcome.Failed(failureText(error))
                }
            } catch (error: Exception) {
                // The request may already have reached TDLib; treating it as certain failure
                // would invite a second charged purchase.
                GiftSendOutcome.Uncertain
            }
        } finally {
            sendInFlight.set(false)
        }
    }

    private suspend fun starTransactionsPage(offset: String, limit: Int): StarTransactionsPage {
        val page = requests(
            TdApi.GetStarTransactions(TdApi.MessageSenderUser(myUserId()), "", null, offset, limit)
        )
        return StarTransactionsPage(
            balance = page.starAmount.toBalance(),
            transactions = page.transactions.orEmpty().map { transaction ->
                StarTransactionItem(
                    id = transaction.id,
                    amount = transaction.starAmount.toBalance(),
                    isRefund = transaction.isRefund,
                    dateSeconds = transaction.date,
                    kind = transaction.type.toKind()
                )
            },
            nextOffset = page.nextOffset.orEmpty()
        )
    }

    private suspend fun myUserId(): Long {
        cachedUserId.takeIf { it > 0L }?.let { return it }
        val me = requests(TdApi.GetMe())
        cachedUserId = me.id
        return me.id
    }

    private suspend fun currentBalanceOrNull(): StarBalance? =
        requests(TdApi.GetStarTransactions(TdApi.MessageSenderUser(myUserId()), "", null, "", 1))
            .starAmount?.toBalance()

    private suspend fun availableGift(giftId: Long): TdApi.Gift? =
        requests(TdApi.GetAvailableGifts()).gifts.orEmpty()
            .firstOrNull { it.gift?.id == giftId }
            ?.gift

    /** Resolves a private chat profile to the user who receives the gift; groups are not gift targets. */
    private suspend fun giftOwner(profileId: Long): TdApi.MessageSender {
        val chat = try {
            requests(TdApi.GetChat(profileId))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // A private chat id equals the user id; the chat may simply not be cached locally.
            if (profileId > 0) return TdApi.MessageSenderUser(profileId)
            throw error
        }
        return when (val type = chat.type) {
            is TdApi.ChatTypePrivate -> TdApi.MessageSenderUser(type.userId)
            is TdApi.ChatTypeSecret -> TdApi.MessageSenderUser(type.userId)
            else -> throw UserMessageException(UiText.Res(R.string.err_gift_recipient_unsupported))
        }
    }

    private suspend fun receivedItems(gifts: List<TdApi.ReceivedGift>): List<ReceivedGiftItem> = coroutineScope {
        val keys = gifts.mapNotNull { gift -> gift.senderId?.let { sender -> sender.senderKey() } }.distinct()
        val names = keys.map { key ->
            async {
                key to when {
                    key.startsWith("u") -> userName(key.removePrefix("u").toLongOrNull() ?: 0L)
                    key.startsWith("c") -> chatName(key.removePrefix("c").toLongOrNull() ?: 0L)
                    else -> null
                }
            }
        }.awaitAll().toMap()
        gifts.map { gift -> gift.toReceivedGift(gift.senderId?.let { sender -> sender.senderKey() }?.let { key -> names[key] }) }
    }

    private suspend fun userName(userId: Long): String? {
        if (userId <= 0L) return null
        userNames[userId]?.let { return it }
        val user = try {
            requests(TdApi.GetUser(userId))
        } catch (_: TimeoutCancellationException) {
            return null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }
        val name = listOf(user.firstName, user.lastName)
            .filter { !it.isNullOrBlank() }
            .joinToString(" ")
        if (name.isNotBlank()) userNames[userId] = name
        return name.takeIf { it.isNotBlank() }
    }

    private suspend fun chatName(chatId: Long): String? = try {
        requests(TdApi.GetChat(chatId)).title?.takeIf { it.isNotBlank() }
    } catch (_: TimeoutCancellationException) {
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private suspend fun <T> monetizationResult(
        errorText: (Exception) -> UiText = { failureText(it) },
        block: suspend () -> T
    ): Result<T> = try {
        Result.success(block())
    // withTimeout raises TimeoutCancellationException, which is also a CancellationException.
    // It must become a visible read error; only real caller cancellation may propagate.
    } catch (timeout: TimeoutCancellationException) {
        Result.failure(UserMessageException(errorText(timeout), timeout))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: UserMessageException) {
        Result.failure(error)
    } catch (error: Exception) {
        Result.failure(UserMessageException(errorText(error), error))
    }

    private suspend fun senderDisplayName(sender: TdApi.MessageSender?): String? = when (sender) {
        is TdApi.MessageSenderUser -> userName(sender.userId)
        is TdApi.MessageSenderChat -> chatName(sender.chatId)
        else -> null
    }

    private fun premiumGiftCodeFailureText(error: Exception): UiText =
        when (classifyPremiumGiftCodeError(error.message)) {
            PremiumGiftCodeError.Invalid -> UiText.Res(R.string.err_premium_gift_code_invalid)
            PremiumGiftCodeError.Used -> UiText.Res(R.string.err_premium_gift_code_used)
            PremiumGiftCodeError.Expired -> UiText.Res(R.string.err_premium_gift_code_expired)
            PremiumGiftCodeError.OtherUser -> UiText.Res(R.string.err_premium_gift_code_other_user)
            PremiumGiftCodeError.Other -> failureText(error)
        }

    private fun failureText(error: Exception): UiText = when {
        error is UserMessageException -> error.text
        error is TimeoutCancellationException -> UiText.Res(R.string.err_monetization_timeout)
        error is TdRequestError && error.code == 401 -> UiText.Res(R.string.err_monetization_unauthorized)
        error is TdRequestError && error.code == 429 -> UiText.Res(R.string.err_tdlib_flood_wait)
        error.message?.contains("FLOOD_WAIT", ignoreCase = true) == true -> UiText.Res(R.string.err_tdlib_flood_wait)
        error.message?.contains("AUTH", ignoreCase = true) == true -> UiText.Res(R.string.err_monetization_unauthorized)
        else -> UiText.Res(R.string.err_monetization_failed)
    }
}

private fun TdApi.PremiumPaymentOption.matches(plan: PremiumPlan): Boolean =
    monthCount.coerceAtLeast(1) == plan.monthCount &&
        amount == plan.amountMinor &&
        currency.orEmpty().equals(plan.currency, ignoreCase = true)

private fun TdApi.StarPaymentOption.matches(option: StarTopUpOption): Boolean =
    starCount == option.starCount &&
        amount == option.amountMinor &&
        currency.orEmpty().equals(option.currency, ignoreCase = true)

/** A provider/form refusal means this build has no safe external checkout, not that the network failed. */
private fun isPaymentUnavailableError(error: Exception): Boolean {
    val message = error.message.orEmpty()
    return message.contains("PAYMENT_PROVIDER", ignoreCase = true) ||
        message.contains("PAYMENT_FORM", ignoreCase = true) ||
        message.contains("INVOICE", ignoreCase = true) ||
        message.contains("CURRENCY", ignoreCase = true) ||
        message.contains("AMOUNT", ignoreCase = true) ||
        message.contains("PREMIUM_CURRENTLY_UNAVAILABLE", ignoreCase = true) ||
        message.contains("PREMIUM_PURCHASE", ignoreCase = true)
}

private fun isBlockingPaymentError(error: Exception): Boolean {
    val message = error.message.orEmpty()
    return (error is TdRequestError && (error.code == 401 || error.code == 429)) ||
        message.contains("FLOOD_WAIT", ignoreCase = true) ||
        message.contains("AUTH", ignoreCase = true)
}

private fun TdApi.StarAmount?.toBalance(): StarBalance =
    StarBalance(starCount = this?.starCount ?: 0L, nanostarCount = this?.nanostarCount ?: 0)

private fun TdApi.AvailableGift.toCatalogItem(): GiftCatalogItem? {
    val value = gift ?: return null
    val remaining = listOfNotNull(
        value.userLimits?.remainingCount,
        value.overallLimits?.remainingCount
    ).minOrNull()
    return GiftCatalogItem(
        id = value.id,
        emoji = value.sticker?.emoji.orEmpty(),
        starCount = value.starCount,
        isPremiumOnly = value.isPremium,
        remainingCount = remaining,
        isSoldOut = remaining != null && remaining <= 0,
        isAuctionOnly = value.auctionInfo != null
    )
}

private fun TdApi.ReceivedGift.toReceivedGift(senderName: String?): ReceivedGiftItem {
    val sent = gift
    val regular = (sent as? TdApi.SentGiftRegular)?.gift
    val upgraded = (sent as? TdApi.SentGiftUpgraded)?.gift
    val emoji = regular?.sticker?.emoji.orEmpty().ifBlank { upgraded?.symbol?.sticker?.emoji.orEmpty() }
    val name = upgraded?.title.orEmpty().ifBlank { upgraded?.model?.name.orEmpty() }
    return ReceivedGiftItem(
        id = receivedGiftId,
        senderName = senderName,
        dateSeconds = date,
        emoji = emoji,
        starCount = regular?.starCount,
        name = name,
        text = text?.text.orEmpty(),
        isPrivate = isPrivate,
        isSaved = isSaved,
        isUpgraded = upgraded != null,
        wasRefunded = wasRefunded
    )
}

private fun TdApi.MessageSender.senderKey(): String? = when (this) {
    is TdApi.MessageSenderUser -> "u$userId"
    is TdApi.MessageSenderChat -> "c$chatId"
    else -> null
}

private fun TdApi.PremiumFeature.toPremiumFeatureKind(): PremiumFeatureKind = when (this) {
    is TdApi.PremiumFeatureAccentColor -> PremiumFeatureKind.AccentColor
    is TdApi.PremiumFeatureAdvancedChatManagement -> PremiumFeatureKind.AdvancedChatManagement
    is TdApi.PremiumFeatureAnimatedProfilePhoto -> PremiumFeatureKind.AnimatedProfilePhoto
    is TdApi.PremiumFeatureAppIcons -> PremiumFeatureKind.AppIcons
    is TdApi.PremiumFeatureBackgroundForBoth -> PremiumFeatureKind.BackgroundForBoth
    is TdApi.PremiumFeatureBusiness -> PremiumFeatureKind.Business
    is TdApi.PremiumFeatureChatBoost -> PremiumFeatureKind.ChatBoost
    is TdApi.PremiumFeatureChecklists -> PremiumFeatureKind.Checklists
    is TdApi.PremiumFeatureCustomEmoji -> PremiumFeatureKind.CustomEmoji
    is TdApi.PremiumFeatureDisabledAds -> PremiumFeatureKind.DisabledAds
    is TdApi.PremiumFeatureEmojiStatus -> PremiumFeatureKind.EmojiStatus
    is TdApi.PremiumFeatureForumTopicIcon -> PremiumFeatureKind.ForumTopicIcon
    is TdApi.PremiumFeatureImprovedDownloadSpeed -> PremiumFeatureKind.ImprovedDownloadSpeed
    is TdApi.PremiumFeatureIncreasedLimits -> PremiumFeatureKind.IncreasedLimits
    is TdApi.PremiumFeatureIncreasedUploadFileSize -> PremiumFeatureKind.IncreasedUploadFileSize
    is TdApi.PremiumFeatureLastSeenTimes -> PremiumFeatureKind.LastSeenTimes
    is TdApi.PremiumFeatureMessageEffects -> PremiumFeatureKind.MessageEffects
    is TdApi.PremiumFeatureMessagePrivacy -> PremiumFeatureKind.MessagePrivacy
    is TdApi.PremiumFeaturePaidMessages -> PremiumFeatureKind.PaidMessages
    is TdApi.PremiumFeatureProfileBadge -> PremiumFeatureKind.ProfileBadge
    is TdApi.PremiumFeatureProtectPrivateChatContent -> PremiumFeatureKind.ProtectPrivateChatContent
    is TdApi.PremiumFeatureRealTimeChatTranslation -> PremiumFeatureKind.RealTimeChatTranslation
    is TdApi.PremiumFeatureSavedMessagesTags -> PremiumFeatureKind.SavedMessagesTags
    is TdApi.PremiumFeatureTextComposition -> PremiumFeatureKind.TextComposition
    is TdApi.PremiumFeatureUniqueReactions -> PremiumFeatureKind.UniqueReactions
    is TdApi.PremiumFeatureUniqueStickers -> PremiumFeatureKind.UniqueStickers
    is TdApi.PremiumFeatureUpgradedStories -> PremiumFeatureKind.UpgradedStories
    is TdApi.PremiumFeatureVoiceRecognition -> PremiumFeatureKind.VoiceRecognition
    else -> PremiumFeatureKind.Other
}

private fun TdApi.StarTransactionType.toKind(): StarTransactionKind = when (this) {
    is TdApi.StarTransactionTypeUserDeposit,
    is TdApi.StarTransactionTypeAppStoreDeposit,
    is TdApi.StarTransactionTypeGooglePlayDeposit,
    is TdApi.StarTransactionTypeFragmentDeposit,
    is TdApi.StarTransactionTypePremiumBotDeposit,
    is TdApi.StarTransactionTypeGiveawayDeposit -> StarTransactionKind.TopUp
    is TdApi.StarTransactionTypeGiftPurchase,
    is TdApi.StarTransactionTypeUpgradedGiftPurchase -> StarTransactionKind.GiftPurchase
    is TdApi.StarTransactionTypeGiftSale,
    is TdApi.StarTransactionTypeUpgradedGiftSale -> StarTransactionKind.GiftSale
    is TdApi.StarTransactionTypeGiftUpgrade,
    is TdApi.StarTransactionTypeGiftUpgradePurchase -> StarTransactionKind.GiftUpgrade
    is TdApi.StarTransactionTypeGiftTransfer -> StarTransactionKind.GiftTransfer
    is TdApi.StarTransactionTypeGiftAuctionBid -> StarTransactionKind.GiftAuction
    is TdApi.StarTransactionTypePremiumPurchase -> StarTransactionKind.PremiumPurchase
    is TdApi.StarTransactionTypePaidMessageSend,
    is TdApi.StarTransactionTypePaidGroupCallMessageSend,
    is TdApi.StarTransactionTypeSuggestedPostPaymentSend -> StarTransactionKind.PaidMessageSend
    is TdApi.StarTransactionTypePaidMessageReceive,
    is TdApi.StarTransactionTypePaidGroupCallMessageReceive,
    is TdApi.StarTransactionTypeSuggestedPostPaymentReceive -> StarTransactionKind.PaidMessageReceive
    is TdApi.StarTransactionTypeChannelPaidReactionSend,
    is TdApi.StarTransactionTypePaidGroupCallReactionSend -> StarTransactionKind.PaidReactionSend
    is TdApi.StarTransactionTypeChannelPaidReactionReceive,
    is TdApi.StarTransactionTypePaidGroupCallReactionReceive -> StarTransactionKind.PaidReactionReceive
    is TdApi.StarTransactionTypeBotPaidMediaPurchase,
    is TdApi.StarTransactionTypeChannelPaidMediaPurchase -> StarTransactionKind.PaidMediaPurchase
    is TdApi.StarTransactionTypeBotPaidMediaSale,
    is TdApi.StarTransactionTypeChannelPaidMediaSale -> StarTransactionKind.PaidMediaSale
    is TdApi.StarTransactionTypeBotInvoicePurchase -> StarTransactionKind.InvoicePurchase
    is TdApi.StarTransactionTypeBotSubscriptionPurchase,
    is TdApi.StarTransactionTypeChannelSubscriptionPurchase -> StarTransactionKind.SubscriptionPurchase
    is TdApi.StarTransactionTypeBotInvoiceSale -> StarTransactionKind.InvoiceSale
    is TdApi.StarTransactionTypeBotSubscriptionSale,
    is TdApi.StarTransactionTypeChannelSubscriptionSale -> StarTransactionKind.SubscriptionSale
    is TdApi.StarTransactionTypeFragmentWithdrawal,
    is TdApi.StarTransactionTypeTelegramAdsWithdrawal -> StarTransactionKind.Withdrawal
    else -> StarTransactionKind.Other
}

private const val PAGE_SIZE = 30
private const val FIRST_PAGE_SIZE = 30
private const val RECEIVED_LIMIT = 30
private const val PREMIUM_INFO_TIMEOUT_MS = 20_000L
private const val GIFT_SEND_TIMEOUT_MS = 60_000L
private const val PREMIUM_GIFT_CODE_APPLY_TIMEOUT_MS = 60_000L
private const val PREMIUM_CLICK_TIMEOUT_MS = 3_000L
