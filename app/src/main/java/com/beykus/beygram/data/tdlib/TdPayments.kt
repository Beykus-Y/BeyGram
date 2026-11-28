package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.ExternalPaymentMethod
import com.beykus.beygram.domain.model.GIFT_TEXT_MAX_LENGTH_DEFAULT
import com.beykus.beygram.domain.model.GiftCatalogItem
import com.beykus.beygram.domain.model.GiftCollectibleDetails
import com.beykus.beygram.domain.model.GiftRarityTier
import com.beykus.beygram.domain.model.GiftVisual
import com.beykus.beygram.domain.model.StickerFileFormat
import com.beykus.beygram.domain.model.GiftAuctionQuote
import com.beykus.beygram.domain.model.GiftAuctionOwnBid
import com.beykus.beygram.domain.model.GiftAuctionRank
import com.beykus.beygram.domain.model.GiftAuctionWin
import com.beykus.beygram.domain.model.sortAuctionWins
import com.beykus.beygram.domain.model.GiftAuctionOutcome
import com.beykus.beygram.domain.model.auctionBidDebit
import com.beykus.beygram.domain.model.auctionQuoteMatches
import com.beykus.beygram.domain.model.ResaleGiftItem
import com.beykus.beygram.domain.model.ResaleGiftsPage
import com.beykus.beygram.domain.model.resalePriceMatches
import com.beykus.beygram.domain.model.GiftCollectionItem
import com.beykus.beygram.domain.model.GiftListingQuote
import com.beykus.beygram.domain.model.GiftListingOutcome
import com.beykus.beygram.domain.model.giftListingAllowed
import com.beykus.beygram.domain.model.GiftSendOutcome
import com.beykus.beygram.domain.model.GiftTextValidation
import com.beykus.beygram.domain.model.GiftsInfo
import com.beykus.beygram.domain.model.ReceivedGiftFilter
import com.beykus.beygram.domain.model.ReceivedGiftSort
import com.beykus.beygram.domain.model.PremiumCheckoutOutcome
import com.beykus.beygram.domain.model.PremiumFeatureKind
import com.beykus.beygram.domain.model.PremiumGiftCodeApplyOutcome
import com.beykus.beygram.domain.model.PremiumGiftCodeError
import com.beykus.beygram.domain.model.PremiumGiftCodeInfo
import com.beykus.beygram.domain.model.PremiumInfo
import com.beykus.beygram.domain.model.PaidContentFormKind
import com.beykus.beygram.domain.model.PaidContentPayGate
import com.beykus.beygram.domain.model.PaidContentPaymentOutcome
import com.beykus.beygram.domain.model.PaidContentPlan
import com.beykus.beygram.domain.model.PremiumPlan
import com.beykus.beygram.domain.model.ReceivedGiftsPage
import com.beykus.beygram.domain.model.paidContentErrorOutcome
import com.beykus.beygram.domain.model.paidContentPayGate
import com.beykus.beygram.domain.model.paidContentPlanFor
import com.beykus.beygram.domain.model.StarsTopUpOutcome
import com.beykus.beygram.domain.model.classifyPremiumGiftCodeError
import com.beykus.beygram.domain.model.giftTextLimit
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
import com.beykus.beygram.domain.model.validateGiftText
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
 * Premium/Stars/Gifts access, one explicit gift purchase, Premium gift code activation,
 * external checkout preparation and one confirmed Stars payment for a paid message. TDLib owns
 * balances and prices; this class never invents payment data, logs codes or URLs, collects card
 * details, or retries a mutation on its own. SendPaymentForm is called only for a Telegram Stars
 * form whose price was read again and matches the price the user confirmed.
 */
internal class TdPayments(
    private val requests: TdRequests,
    private val files: MediaFileStore
) {
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

    /** The balance alone, read the way [starsInfo] reads it; the paid reaction dialog needs it before any Stars are spent. */
    suspend fun starBalance(): Result<StarBalance> = monetizationResult {
        currentBalanceOrNull() ?: throw UserMessageException(UiText.Res(R.string.stars_load_error))
    }

    suspend fun starTransactions(offset: String): Result<StarTransactionsPage> = monetizationResult {
        starTransactionsPage(offset = offset, limit = PAGE_SIZE)
    }

    suspend fun giftsInfo(profileId: Long, isSelf: Boolean): Result<GiftsInfo> = monetizationResult {
        val me = requests(TdApi.GetMe())
        cachedUserId = me.id
        val owner = if (isSelf) TdApi.MessageSenderUser(me.id) else giftOwner(profileId)
        val catalog = requests(TdApi.GetAvailableGifts()).gifts.orEmpty()
            .mapNotNull { it.toCatalogItem { sticker -> giftVisual(sticker) } }
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
        var receivedNextOffset = ""
        var receivedUnavailable = false
        var collections: List<GiftCollectionItem> = emptyList()
        var collectionsUnavailable = false
        try {
            val page = receivedGiftsPage(owner, offset = "")
            received = page.gifts
            receivedNextOffset = page.nextOffset
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
        GiftsInfo(
            balance = balance,
            catalog = catalog,
            received = received,
            receivedNextOffset = receivedNextOffset,
            collections = collections,
            isPremium = me.isPremium,
            receivedUnavailable = receivedUnavailable,
            collectionsUnavailable = collectionsUnavailable,
            textMaxLength = readGiftTextLimit()
        )
    }

    /**
     * Maps a TDLib sticker to the gift's visual and registers its file (and thumbnail) so the visible cell can fetch
     * its own preview. A sticker in a format the app cannot draw yields null, and the emoji is shown instead.
     */
    internal fun giftVisual(sticker: TdApi.Sticker?): GiftVisual? {
        val value = sticker ?: return null
        val file = value.sticker ?: return null
        val format = value.format?.toStickerFileFormat() ?: return null
        files.register(file.toMediaFileState())
        val thumbnail = value.thumbnail?.file?.also { files.register(it.toMediaFileState()) }
        return GiftVisual(
            fileId = file.id,
            format = format,
            thumbnailFileId = thumbnail?.id,
            width = value.width,
            height = value.height
        )
    }

    /**
     * The collectible attributes of an upgraded gift that its card shows beside the model. Null for a regular gift.
     * The symbol sticker is registered like the visual; the colours and rarity are plain values.
     */
    internal fun giftCollectible(upgraded: TdApi.UpgradedGift?): GiftCollectibleDetails? {
        val gift = upgraded ?: return null
        val colors = gift.backdrop?.colors
        val modelRarity = gift.model?.rarity
        val symbolRarity = gift.symbol?.rarity
        val backdropRarity = gift.backdrop?.rarity
        return GiftCollectibleDetails(
            number = gift.number,
            symbol = giftVisual(gift.symbol?.sticker),
            backdropCenterColor = colors?.centerColor,
            backdropEdgeColor = colors?.edgeColor,
            backdropSymbolColor = colors?.symbolColor,
            backdropTextColor = colors?.textColor,
            modelRarityPerMille = (modelRarity as? TdApi.UpgradedGiftAttributeRarityPerMille)?.perMille,
            modelRarityTier = modelRarity.toRarityTier(),
            slug = gift.name.orEmpty(),
            title = gift.title.orEmpty(),
            totalUpgradedCount = gift.totalUpgradedCount.coerceAtLeast(0),
            maxUpgradedCount = gift.maxUpgradedCount.coerceAtLeast(0),
            modelName = gift.model?.name.orEmpty(),
            symbolName = gift.symbol?.name.orEmpty(),
            symbolRarityPerMille = (symbolRarity as? TdApi.UpgradedGiftAttributeRarityPerMille)?.perMille,
            symbolRarityTier = symbolRarity.toRarityTier(),
            backdropName = gift.backdrop?.name.orEmpty(),
            backdropRarityPerMille = (backdropRarity as? TdApi.UpgradedGiftAttributeRarityPerMille)?.perMille,
            backdropRarityTier = backdropRarity.toRarityTier(),
            upgradedGiftId = gift.id.takeIf { it > 0L }
        )
    }

    /** Read-only market request. The listed price is specific to the collectible name and currency. */
    suspend fun resaleGifts(giftId: Long, offset: String): Result<ResaleGiftsPage> = monetizationResult {
        val page = requests(TdApi.SearchGiftsForResale().apply {
            this.giftId = giftId
            order = TdApi.GiftForResaleOrderPrice()
            attributes = emptyArray()
            this.offset = offset
            limit = 20
        })
        ResaleGiftsPage(
            items = page.gifts.orEmpty().mapNotNull { listing ->
                val gift = listing.gift ?: return@mapNotNull null
                val name = gift.name.orEmpty()
                val price = gift.resaleParameters ?: return@mapNotNull null
                if (name.isBlank() || gift.id <= 0L) return@mapNotNull null
                ResaleGiftItem(
                    id = gift.id,
                    name = name,
                    title = gift.title.orEmpty(),
                    number = gift.number,
                    emoji = gift.symbol?.sticker?.emoji.orEmpty(),
                    starCount = price.starCount.takeIf { it > 0L && !price.toncoinOnly },
                    isOwned = listing.receivedGiftId.orEmpty().isNotBlank(),
                    visual = giftVisual(gift.model?.sticker),
                    collectible = giftCollectible(gift)
                )
            }.distinctBy { it.name },
            nextOffset = page.nextOffset.orEmpty()
        )
    }

    /** One explicit Stars resale purchase. No TON, no retries, no authorization at an increased price. */
    suspend fun buyResoldGift(profileId: Long, name: String, confirmedStars: Long): GiftSendOutcome {
        fun blocked() = GiftSendOutcome.Blocked(UiText.Res(R.string.gifts_resale_changed))
        if (name.isBlank() || confirmedStars <= 0L) return blocked()
        if (!sendInFlight.compareAndSet(false, true)) {
            return GiftSendOutcome.Blocked(UiText.Res(R.string.err_gift_send_busy))
        }
        var dispatched = false
        return try {
            val recipient = giftOwner(profileId)
            val gift = requests(TdApi.GetUpgradedGift(name))
            val price = gift.resaleParameters
            val owner = gift.ownerId as? TdApi.MessageSenderUser
            val ownId = myUserId()
            if (gift.name != name || owner?.userId == ownId ||
                !resalePriceMatches(confirmedStars, price?.starCount, price?.toncoinOnly != false)) {
                return blocked()
            }
            val balance = currentBalanceOrNull()
            if (balance == null || balance.starCount < confirmedStars) {
                return GiftSendOutcome.Blocked(UiText.Res(R.string.err_gift_insufficient_stars))
            }
            dispatched = true
            when (requests(TdApi.SendResoldGift(name, recipient, TdApi.GiftResalePriceStar(confirmedStars)),
                timeoutMillis = GIFT_SEND_TIMEOUT_MS)) {
                is TdApi.GiftResaleResultOk -> GiftSendOutcome.Sent
                is TdApi.GiftResaleResultPriceIncreased -> blocked()
                else -> GiftSendOutcome.Uncertain
            }
        } catch (timeout: TimeoutCancellationException) {
            if (dispatched) GiftSendOutcome.Uncertain else GiftSendOutcome.Failed(failureText(timeout))
        } catch (cancelled: CancellationException) {
            if (dispatched) GiftSendOutcome.Uncertain else throw cancelled
        } catch (error: Exception) {
            if (dispatched) GiftSendOutcome.Uncertain else GiftSendOutcome.Failed(failureText(error))
        } finally {
            sendInFlight.set(false)
        }
    }

    /** Next page of the signed-in user's received gifts; the screen owns the cursor and the merge. */
    suspend fun receivedGifts(offset: String): Result<ReceivedGiftsPage> = monetizationResult {
        receivedGiftsPage(myUserId(), offset)
    }

    suspend fun receivedGiftsForUser(userId: Long, offset: String): Result<ReceivedGiftsPage> = monetizationResult {
        if (userId <= 0L) return@monetizationResult ReceivedGiftsPage(emptyList(), "")
        receivedGiftsPage(userId, offset)
    }

    suspend fun toggleGiftSaved(receivedGiftId: String, isSaved: Boolean): Result<Unit> = monetizationResult {
        if (receivedGiftId.isBlank()) throw IllegalArgumentException("Missing gift id")
        requests(TdApi.ToggleGiftIsSaved(receivedGiftId, isSaved))
        Unit
    }

    suspend fun setPinnedGifts(receivedGiftIds: List<String>): Result<Unit> = monetizationResult {
        val me = myUserId()
        requests(TdApi.SetPinnedGifts(TdApi.MessageSenderUser(me), receivedGiftIds.toTypedArray()))
        Unit
    }

    /**
     * Sends exactly one gift. Only one request can be in flight; a timeout is
     * reported as [GiftSendOutcome.Uncertain] so the screen never retries blindly.
     * The message is checked against TDLib's limit before anything can be charged.
     */
    suspend fun sendGift(
        profileId: Long,
        isSelf: Boolean,
        giftId: Long,
        text: String,
        isPrivate: Boolean
    ): GiftSendOutcome {
        // Checked before the in-flight flag, so a rejected message never blocks a valid purchase.
        if (validateGiftText(text, readGiftTextLimit()) == GiftTextValidation.TooLong) {
            return GiftSendOutcome.Blocked(UiText.Res(R.string.gifts_text_too_long))
        }
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
                    TdApi.SendGift(giftId, owner, TdApi.FormattedText(text.trim(), emptyArray()), isPrivate, false),
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

    /**
     * Reads what paying for a paid media or invoice message would cost. Nothing is spent here: a Stars price
     * needs a confirmation that shows the balance, and a real-money form is offered only as validated pages.
     */
    suspend fun paidContentPlan(chatId: Long, messageId: Long): PaidContentPlan = try {
        val form = requests(TdApi.GetPaymentForm(TdApi.InputInvoiceMessage(chatId, messageId), null))
        val kind = form.toPaidContentFormKind()
        val balance = if (kind is PaidContentFormKind.Stars) readBalanceOrNull() else null
        paidContentPlanFor(kind, balance)
    } catch (_: TimeoutCancellationException) {
        PaidContentPlan.Failed(UiText.Res(R.string.err_monetization_timeout))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        if (isPaymentUnavailableError(error)) PaidContentPlan.Unavailable else PaidContentPlan.Failed(failureText(error))
    }

    /**
     * Pays one paid message with Telegram Stars, and only for the price the user confirmed. The form is read
     * again first; a changed price, a real-money form or a subscription is refused before SendPaymentForm runs.
     * A timeout or an unknown failure after the request is sent is Uncertain, and it is never retried.
     */
    suspend fun payPaidContentWithStars(chatId: Long, messageId: Long, confirmedStarCount: Long): PaidContentPaymentOutcome {
        if (!sendInFlight.compareAndSet(false, true)) return PaidContentPaymentOutcome.Busy
        try {
            val inputInvoice = TdApi.InputInvoiceMessage(chatId, messageId)
            val form = try {
                requests(TdApi.GetPaymentForm(inputInvoice, null))
            } catch (_: TimeoutCancellationException) {
                return PaidContentPaymentOutcome.Failed(UiText.Res(R.string.err_monetization_timeout))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                return PaidContentPaymentOutcome.Failed(failureText(error))
            }
            when (paidContentPayGate(form.toPaidContentFormKind(), confirmedStarCount)) {
                PaidContentPayGate.Send -> Unit
                PaidContentPayGate.PriceChanged -> return PaidContentPaymentOutcome.PriceChanged
                PaidContentPayGate.NotStars -> return PaidContentPaymentOutcome.NotPayable
            }
            return try {
                val result = requests(
                    TdApi.SendPaymentForm(inputInvoice, form.id, "", "", null, 0L),
                    timeoutMillis = PAID_CONTENT_PAYMENT_TIMEOUT_MS
                )
                if (result.success) PaidContentPaymentOutcome.Paid else PaidContentPaymentOutcome.Failed(UiText.Res(R.string.paid_content_not_completed))
            } catch (_: TimeoutCancellationException) {
                // The request may still complete on Telegram's side, so the result stays open.
                PaidContentPaymentOutcome.Uncertain
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: TdRequestError) {
                paidContentErrorOutcome(error.code, error.message) ?: PaidContentPaymentOutcome.Failed(failureText(error))
            } catch (_: Exception) {
                // A network failure can happen after Telegram accepted the payment; a retry could charge twice.
                PaidContentPaymentOutcome.Uncertain
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

    private suspend fun readBalanceOrNull(): StarBalance? = try {
        currentBalanceOrNull()
    } catch (_: TimeoutCancellationException) {
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    /** Only the Stars form can be paid here; real-money forms keep their validated external pages. */
    private fun TdApi.PaymentForm.toPaidContentFormKind(): PaidContentFormKind = when (val paymentType = type) {
        is TdApi.PaymentFormTypeStars -> PaidContentFormKind.Stars(paymentType.starCount)
        is TdApi.PaymentFormTypeRegular -> PaidContentFormKind.External(
            paymentType.additionalPaymentOptions.orEmpty()
                .mapNotNull { option ->
                    val url = validateExternalPaymentUrl(option.url) ?: return@mapNotNull null
                    ExternalPaymentMethod(title = sanitizePaymentMethodTitle(option.title), url = url)
                }
                .distinctBy { it.url.value }
        )
        is TdApi.PaymentFormTypeStarSubscription -> PaidContentFormKind.Subscription
        else -> PaidContentFormKind.Unknown
    }

    /** The recipient is resolved by the account's repository, not by a display name. */
    suspend fun giftAuctionQuote(auctionId: String, profileId: Long, isSelf: Boolean): Result<GiftAuctionQuote> =
        monetizationResult {
            require(auctionId.isNotBlank())
            val recipient = if (isSelf) myUserId() else (giftOwner(profileId) as? TdApi.MessageSenderUser)?.userId
                ?: throw UserMessageException(UiText.Res(R.string.gift_auction_unavailable))
            val response = requests(TdApi.GetGiftAuctionState(auctionId))
            val gift = response.gift ?: throw UserMessageException(UiText.Res(R.string.gift_auction_unavailable))
            val now = (requests(TdApi.GetOption("unix_time")) as? TdApi.OptionValueInteger)?.value ?: 0L
            val balance = readBalanceOrNull()
            when (val state = response.state) {
                is TdApi.AuctionStateActive -> GiftAuctionQuote(
                    auctionId, gift.id, recipient, now, state.startDate, state.endDate,
                    state.currentRoundNumber, state.totalRoundCount, state.currentRoundEndDate,
                    state.leftItemCount, state.acquiredItemCount, state.minBid,
                    state.userBid?.let { bid ->
                        GiftAuctionOwnBid(bid.starCount, bid.bidDate, bid.nextBidStarCount,
                            (bid.ownerId as? TdApi.MessageSenderUser)?.userId, bid.wasReturned)
                    },
                    state.bidLevels.orEmpty().map { GiftAuctionRank(it.position, it.starCount) },
                    balance, null, false
                )
                is TdApi.AuctionStateFinished -> GiftAuctionQuote(
                    auctionId, gift.id, recipient, now, state.startDate, state.endDate,
                    0, 0, 0, 0, state.acquiredItemCount, 0L, null, emptyList(),
                    balance, state.averagePrice, true
                )
                else -> throw UserMessageException(UiText.Res(R.string.gift_auction_unavailable))
            }
        }

    /** Read-only receipt history; never use it as an authorization or filter it by the open profile. */
    suspend fun giftAuctionHistory(giftId: Long): Result<List<GiftAuctionWin>> = monetizationResult {
        require(giftId > 0L)
        val rows = requests(TdApi.GetGiftAuctionAcquiredGifts(giftId)).gifts.orEmpty().filterNotNull()
        val senders = rows.mapNotNull { it.receiverId }.distinctBy { it.senderKey() }
        val names = coroutineScope {
            senders.map { sender -> async { sender.senderKey() to senderDisplayName(sender) } }
                .awaitAll().toMap()
        }
        sortAuctionWins(rows.map { row ->
            GiftAuctionWin(
                receiver = row.receiverId?.senderKey()?.let { names[it] },
                date = row.date,
                stars = row.starCount,
                round = row.auctionRoundNumber,
                position = row.auctionRoundPosition,
                number = row.uniqueGiftNumber.takeIf { it > 0 },
                text = row.text?.text.orEmpty(),
                isPrivate = row.isPrivate
            )
        })
    }

    /** Confirms a total bid, never automatically follows an increased minimum. */
    suspend fun submitGiftAuctionBid(quote: GiftAuctionQuote, profileId: Long, isSelf: Boolean, total: Long): GiftAuctionOutcome {
        if (auctionBidDebit(quote, total) == null) return GiftAuctionOutcome.Changed
        if (!sendInFlight.compareAndSet(false, true)) {
            return GiftAuctionOutcome.Failed(UiText.Res(R.string.err_gift_send_busy))
        }
        var dispatched = false
        return try {
            val current = giftAuctionQuote(quote.auctionId, profileId, isSelf).getOrThrow()
            if (!auctionQuoteMatches(quote, current) || auctionBidDebit(current, total) == null) {
                return GiftAuctionOutcome.Changed
            }
            dispatched = true
            if (current.ownBid == null) {
                requests(TdApi.PlaceGiftAuctionBid(current.giftId, total, current.recipientId,
                    TdApi.FormattedText("", emptyArray()), true), timeoutMillis = GIFT_SEND_TIMEOUT_MS)
            } else {
                // The recipient and text of the original bid are immutable.
                requests(TdApi.IncreaseGiftAuctionBid(current.giftId, total), timeoutMillis = GIFT_SEND_TIMEOUT_MS)
            }
            GiftAuctionOutcome.Submitted
        } catch (timeout: TimeoutCancellationException) {
            if (dispatched) GiftAuctionOutcome.Uncertain else GiftAuctionOutcome.Failed(failureText(timeout))
        } catch (cancelled: CancellationException) {
            if (dispatched) GiftAuctionOutcome.Uncertain else throw cancelled
        } catch (error: Exception) {
            if (dispatched) GiftAuctionOutcome.Uncertain else GiftAuctionOutcome.Failed(failureText(error))
        } finally {
            sendInFlight.set(false)
        }
    }

    /** Reads a specific owned collectible, current listing and authoritative limits. No mutation. */
    suspend fun giftListingQuote(receivedId: String): Result<GiftListingQuote> = monetizationResult {
        require(receivedId.isNotBlank())
        val received = requests(TdApi.GetReceivedGift(receivedId))
        val gift = (received.gift as? TdApi.SentGiftUpgraded)?.gift
            ?: throw UserMessageException(UiText.Res(R.string.gift_listing_unavailable))
        val owner = gift.ownerId as? TdApi.MessageSenderUser
        if (received.receivedGiftId != receivedId || received.wasRefunded || owner?.userId != myUserId()) {
            throw UserMessageException(UiText.Res(R.string.gift_listing_unavailable))
        }
        suspend fun option(name: String): Long? =
            (requests(TdApi.GetOption(name)) as? TdApi.OptionValueInteger)?.value
        val now = option("unix_time") ?: 0L
        val minimum = option("gift_resale_star_count_min") ?: 0L
        val maximum = option("gift_resale_star_count_max") ?: 0L
        val earnings = option("gift_resale_star_earnings_per_mille") ?: -1L
        val listed = gift.resaleParameters
        GiftListingQuote(
            receivedId = receivedId,
            collectibleName = gift.name.orEmpty(),
            currentStars = listed?.takeUnless { it.toncoinOnly }?.starCount?.takeIf { it > 0L },
            tonOnly = listed?.toncoinOnly == true,
            eligible = now > 0L && received.nextResaleDate > 0 && received.nextResaleDate.toLong() <= now,
            minimum = minimum,
            maximum = maximum,
            earningsPerMille = earnings
        )
    }

    /** One confirmed listing mutation, using the received gift ID (not the catalog ID). */
    suspend fun updateGiftListing(quote: GiftListingQuote, newStars: Long?): GiftListingOutcome {
        if (!giftListingAllowed(quote, newStars)) return GiftListingOutcome.Changed
        if (!sendInFlight.compareAndSet(false, true)) {
            return GiftListingOutcome.Failed(UiText.Res(R.string.err_gift_send_busy))
        }
        var dispatched = false
        return try {
            val current = giftListingQuote(quote.receivedId).getOrThrow()
            if (current != quote || !giftListingAllowed(current, newStars)) return GiftListingOutcome.Changed
            dispatched = true
            requests(
                TdApi.SetGiftResalePrice(quote.receivedId, newStars?.let { TdApi.GiftResalePriceStar(it) }),
                timeoutMillis = GIFT_SEND_TIMEOUT_MS
            )
            GiftListingOutcome.Updated
        } catch (timeout: TimeoutCancellationException) {
            if (dispatched) GiftListingOutcome.Uncertain else GiftListingOutcome.Failed(failureText(timeout))
        } catch (cancelled: CancellationException) {
            if (dispatched) GiftListingOutcome.Uncertain else throw cancelled
        } catch (error: Exception) {
            if (dispatched) GiftListingOutcome.Uncertain else GiftListingOutcome.Failed(failureText(error))
        } finally {
            sendInFlight.set(false)
        }
    }

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
        gifts.map { gift ->
            gift.toReceivedGift(
                senderName = gift.senderId?.let { sender -> sender.senderKey() }?.let { key -> names[key] },
                visualOf = { sticker -> giftVisual(sticker) },
                collectibleOf = { upgraded -> giftCollectible(upgraded) }
            )
        }
    }

    suspend fun receivedGiftsForOwner(
        profileId: Long,
        isSelf: Boolean,
        collectionId: Int = 0,
        filter: ReceivedGiftFilter = ReceivedGiftFilter.All,
        sort: ReceivedGiftSort = ReceivedGiftSort.Date,
        offset: String = ""
    ): Result<ReceivedGiftsPage> = monetizationResult {
        val owner = if (isSelf) TdApi.MessageSenderUser(myUserId()) else giftOwner(profileId)
        receivedGiftsPage(
            owner = owner,
            collectionId = collectionId,
            filter = filter,
            sort = sort,
            offset = offset
        )
    }

    private suspend fun receivedGiftsPage(ownerId: Long, offset: String): ReceivedGiftsPage =
        receivedGiftsPage(TdApi.MessageSenderUser(ownerId), offset = offset)

    private suspend fun receivedGiftsPage(
        owner: TdApi.MessageSender,
        collectionId: Int = 0,
        filter: ReceivedGiftFilter = ReceivedGiftFilter.All,
        sort: ReceivedGiftSort = ReceivedGiftSort.Date,
        offset: String = ""
    ): ReceivedGiftsPage {
        val page = requests(
            TdApi.GetReceivedGifts(
                "",
                owner,
                collectionId,
                filter == ReceivedGiftFilter.Saved,
                filter == ReceivedGiftFilter.Unsaved,
                false,
                false,
                filter == ReceivedGiftFilter.Collectible,
                filter == ReceivedGiftFilter.Regular,
                false,
                false,
                sort == ReceivedGiftSort.Price,
                offset,
                RECEIVED_LIMIT
            )
        )
        return ReceivedGiftsPage(
            gifts = receivedItems(page.gifts.orEmpty().toList()).distinctBy { it.id },
            nextOffset = page.nextOffset.orEmpty()
        )
    }

    /** TDLib owns the gift message limit; when the option cannot be read, the default applies. */
    private suspend fun readGiftTextLimit(): Int = try {
        giftTextLimit((requests(TdApi.GetOption(GIFT_TEXT_LENGTH_OPTION)) as? TdApi.OptionValueInteger)?.value)
    } catch (_: TimeoutCancellationException) {
        GIFT_TEXT_MAX_LENGTH_DEFAULT
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        GIFT_TEXT_MAX_LENGTH_DEFAULT
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

internal fun TdApi.StickerFormat.toStickerFileFormat(): StickerFileFormat? = when (this) {
    is TdApi.StickerFormatTgs -> StickerFileFormat.Tgs
    is TdApi.StickerFormatWebm -> StickerFileFormat.Webm
    is TdApi.StickerFormatWebp -> StickerFileFormat.Webp
    else -> null
}

private fun TdApi.AvailableGift.toCatalogItem(visualOf: (TdApi.Sticker?) -> GiftVisual?): GiftCatalogItem? {
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
        isAuctionOnly = value.auctionInfo != null,
        auctionId = value.auctionInfo?.id?.takeIf { it.isNotBlank() },
        resaleCount = resaleCount.coerceAtLeast(0),
        minResaleStarCount = minResaleStarCount.coerceAtLeast(0L),
        resaleTitle = title.orEmpty(),
        visual = visualOf(value.sticker)
    )
}

internal fun TdApi.UpgradedGiftAttributeRarity?.toRarityTier(): GiftRarityTier? = when (this) {
    is TdApi.UpgradedGiftAttributeRarityUncommon -> GiftRarityTier.Uncommon
    is TdApi.UpgradedGiftAttributeRarityRare -> GiftRarityTier.Rare
    is TdApi.UpgradedGiftAttributeRarityEpic -> GiftRarityTier.Epic
    is TdApi.UpgradedGiftAttributeRarityLegendary -> GiftRarityTier.Legendary
    else -> null
}

private fun TdApi.ReceivedGift.toReceivedGift(
    senderName: String?,
    visualOf: (TdApi.Sticker?) -> GiftVisual?,
    collectibleOf: (TdApi.UpgradedGift?) -> GiftCollectibleDetails?
): ReceivedGiftItem {
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
        wasRefunded = wasRefunded,
        // An upgraded gift is drawn by its model; a regular gift by its own sticker.
        visual = visualOf(regular?.sticker ?: upgraded?.model?.sticker),
        collectible = collectibleOf(upgraded),
        isPinned = isPinned,
        canBeUpgraded = canBeUpgraded,
        canBeTransferred = canBeTransferred
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
private const val GIFT_TEXT_LENGTH_OPTION = "gift_text_length_max"
private const val PREMIUM_INFO_TIMEOUT_MS = 20_000L
private const val GIFT_SEND_TIMEOUT_MS = 60_000L
private const val PREMIUM_GIFT_CODE_APPLY_TIMEOUT_MS = 60_000L
private const val PREMIUM_CLICK_TIMEOUT_MS = 3_000L
private const val PAID_CONTENT_PAYMENT_TIMEOUT_MS = 60_000L
