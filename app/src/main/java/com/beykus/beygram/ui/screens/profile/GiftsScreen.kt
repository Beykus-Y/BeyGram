package com.beykus.beygram.ui.screens.profile

import androidx.activity.compose.BackHandler
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassScreenBackground
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.Switch
import androidx.compose.material3.Text
import com.beykus.beygram.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.GIFT_TEXT_MAX_LENGTH_DEFAULT
import com.beykus.beygram.domain.model.GiftBlockReason
import com.beykus.beygram.domain.model.GiftCatalogFilter
import com.beykus.beygram.domain.model.GiftCatalogItem
import com.beykus.beygram.domain.model.GiftAuctionQuote
import com.beykus.beygram.domain.model.GiftAuctionWin
import com.beykus.beygram.domain.model.GiftAuctionOutcome
import com.beykus.beygram.domain.model.GiftCollectionItem
import com.beykus.beygram.domain.model.GiftListingQuote
import com.beykus.beygram.domain.model.GiftListingOutcome
import com.beykus.beygram.domain.model.GiftSendEvent
import com.beykus.beygram.domain.model.GiftSendOutcome
import com.beykus.beygram.domain.model.GiftSendStep
import com.beykus.beygram.domain.model.GiftTextValidation
import com.beykus.beygram.domain.model.GiftsInfo
import com.beykus.beygram.domain.model.GiftsScreenTab
import com.beykus.beygram.domain.model.MonetizationUnavailableException
import com.beykus.beygram.domain.model.GiftRarityTier
import com.beykus.beygram.domain.model.ReceivedGiftFilter
import com.beykus.beygram.domain.model.ReceivedGiftItem
import com.beykus.beygram.domain.model.ReceivedGiftSort
import com.beykus.beygram.domain.model.ReceivedGiftsPage
import com.beykus.beygram.domain.model.ResaleGiftItem
import com.beykus.beygram.domain.model.ResaleGiftsPage
import com.beykus.beygram.domain.model.resaleCanBuy
import androidx.compose.runtime.key
import com.beykus.beygram.domain.model.StarBalance
import com.beykus.beygram.domain.model.filterGiftCatalog
import com.beykus.beygram.domain.model.formatStarAmount
import com.beykus.beygram.domain.model.giftBlockReason
import com.beykus.beygram.domain.model.mergeReceivedGifts
import com.beykus.beygram.domain.model.next
import com.beykus.beygram.domain.model.nextReceivedGiftsOffset
import com.beykus.beygram.domain.model.togglePinnedGiftIds
import com.beykus.beygram.domain.model.validateGiftText
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.BeyGramPalette
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.resolve
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.DateFormat
import java.util.Date
import com.beykus.beygram.ui.components.GlassScreenScaffold

@Composable
fun GiftsScreen(
    accountId: String,
    title: String,
    profileId: Long,
    profileTitle: String,
    isSelf: Boolean,
    load: suspend () -> Result<GiftsInfo>,
    loadMoreReceived: suspend (offset: String) -> Result<ReceivedGiftsPage>,
    send: suspend (giftId: Long, text: String, isPrivate: Boolean) -> GiftSendOutcome,
    loadListing: suspend (String) -> Result<GiftListingQuote> = { Result.failure(UnsupportedOperationException()) },
    updateListing: suspend (GiftListingQuote, Long?) -> GiftListingOutcome = { _, _ -> GiftListingOutcome.Changed },
    onOpenStars: () -> Unit,
    onBack: () -> Unit,
    loadAuction: suspend (String, Long, Boolean) -> Result<GiftAuctionQuote> =
        { _, _, _ -> Result.failure(UnsupportedOperationException()) },
    loadAuctionHistory: suspend (Long) -> Result<List<GiftAuctionWin>> =
        { Result.failure(UnsupportedOperationException()) },
    submitBid: suspend (GiftAuctionQuote, Long, Boolean, Long) -> GiftAuctionOutcome =
        { _, _, _, _ -> GiftAuctionOutcome.Changed },
    loadResale: suspend (Long, String) -> Result<ResaleGiftsPage> = { _, _ -> Result.failure(UnsupportedOperationException()) },
    buyResale: suspend (Long, String, Long) -> GiftSendOutcome = { _, _, _ -> GiftSendOutcome.Blocked(com.beykus.beygram.domain.UiText.Res(R.string.err_gift_unavailable)) },
    toggleGiftSaved: suspend (String, Boolean) -> Result<Unit> = { _, _ -> Result.failure(UnsupportedOperationException()) },
    setPinnedGifts: suspend (List<String>) -> Result<Unit> = { Result.failure(UnsupportedOperationException()) },
    initialTab: GiftsScreenTab = GiftsScreenTab.Received,
    loadReceivedPage: suspend (collectionId: Int, filter: ReceivedGiftFilter, sort: ReceivedGiftSort, offset: String) -> Result<ReceivedGiftsPage> =
        { _, _, _, offset -> loadMoreReceived(offset) }
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val loadingText = stringResource(R.string.a11y_state_loading)
    val context = LocalContext.current
    var listingGift by remember(accountId, profileId) { mutableStateOf<ReceivedGiftItem?>(null) }
    var detailTarget by remember(accountId, profileId) { mutableStateOf<GiftDetailTarget?>(null) }
    var detailBusy by remember { mutableStateOf(false) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var catalogFilter by rememberSaveable { mutableStateOf(GiftCatalogFilter.All) }
    var catalogQuery by rememberSaveable { mutableStateOf("") }
    var activeTab by rememberSaveable(accountId, profileId, isSelf, initialTab) { mutableStateOf(initialTab) }
    var selectedCollectionId by rememberSaveable(accountId, profileId, isSelf) { mutableIntStateOf(0) }
    var receivedFilter by rememberSaveable(accountId, profileId, isSelf) { mutableStateOf(ReceivedGiftFilter.All) }
    var receivedSort by rememberSaveable(accountId, profileId, isSelf) { mutableStateOf(ReceivedGiftSort.Date) }
    var loadingReceivedFilter by remember { mutableStateOf(false) }
    var receivedFilterError by remember { mutableStateOf<String?>(null) }
    val listingState by GiftListingStore.state.collectAsState()
    val listingOperation = listingState[accountId]
    var info by remember { mutableStateOf<GiftsInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var unsupported by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var step by remember { mutableStateOf<GiftSendStep>(GiftSendStep.Idle) }
    var selectedGift by remember { mutableStateOf<GiftCatalogItem?>(null) }
    var auctionGift by remember(accountId, profileId, isSelf) { mutableStateOf<GiftCatalogItem?>(null) }
    val auctionOperations by GiftAuctionStore.states.collectAsState()
    val auctionOperation = auctionOperations[accountId]
    var resaleCatalog by remember(accountId, profileId, isSelf) { mutableStateOf<GiftCatalogItem?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeIsError by remember { mutableStateOf(false) }
    // A send runs in the process-scoped store below, so rotation and navigation cannot
    // cancel a purchase or lose its result. This marker only survives process death.
    var pendingGiftId by rememberSaveable { mutableStateOf(0L) }
    val retainedByAccount by RetainedGiftSendStore.states.collectAsState()
    val retained = retainedByAccount[accountId]
    val retainedRelevant = retained?.let { it.profileId == profileId && it.isSelf == isSelf } == true
    val sending = step is GiftSendStep.Sending || retained?.inFlight == true || listingOperation?.inFlight == true || auctionOperation?.inFlight == true
    val scope = rememberCoroutineScope()
    // The token drops a "load more" response that arrives after a refresh started.
    var generation by remember { mutableIntStateOf(0) }
    var receivedNextOffset by remember { mutableStateOf("") }
    var loadingMore by remember { mutableStateOf(false) }
    var moreError by remember { mutableStateOf<String?>(null) }
    // The message and the name switch belong to the send dialog; they survive rotation with it.
    var giftText by rememberSaveable { mutableStateOf("") }
    var hideSenderName by rememberSaveable { mutableStateOf(false) }

    BackHandler { if (!sending) onBack() }
    LaunchedEffect(listingOperation, isSelf) {
        if (isSelf && listingOperation != null && listingOperation?.outcome != null) reload++
    }
    LaunchedEffect(Unit) {
        if (RetainedGiftSendStore.states.value[accountId] == null && pendingGiftId != 0L) {
            notice = context.getString(R.string.gifts_send_uncertain)
            noticeIsError = true
            step = GiftSendStep.Failed(pendingGiftId, uncertain = true)
        }
    }
    LaunchedEffect(retained) {
        val current = retained ?: return@LaunchedEffect
        if (current.inFlight) {
            notice = context.getString(R.string.gifts_sending)
            noticeIsError = false
            return@LaunchedEffect
        }
        val outcome = current.outcome ?: return@LaunchedEffect
        if (retainedRelevant) {
            when (outcome) {
                GiftSendOutcome.Sent -> {
                    pendingGiftId = 0L
                    notice = context.getString(R.string.gifts_send_success)
                    noticeIsError = false
                    step = GiftSendStep.Sent(current.giftId)
                    reload++
                }
                is GiftSendOutcome.Blocked -> {
                    pendingGiftId = 0L
                    notice = outcome.reason.resolve(context)
                    noticeIsError = true
                    step = GiftSendStep.Failed(current.giftId, uncertain = false)
                }
                is GiftSendOutcome.Failed -> {
                    pendingGiftId = 0L
                    notice = outcome.reason.resolve(context)
                    noticeIsError = true
                    step = GiftSendStep.Failed(current.giftId, uncertain = false)
                }
                GiftSendOutcome.Uncertain -> {
                    pendingGiftId = current.giftId
                    notice = context.getString(R.string.gifts_send_uncertain)
                    noticeIsError = true
                    step = GiftSendStep.Failed(current.giftId, uncertain = true)
                    reload++
                }
            }
        } else if (outcome == GiftSendOutcome.Uncertain) {
            // The purchase was for another profile, but the balance may still have changed.
            pendingGiftId = current.giftId
            notice = context.getString(R.string.gifts_send_uncertain)
            noticeIsError = true
        }
        selectedGift = null
        // An uncertain result stays in the retained store until the explicit acknowledgement. A definite result is
        // acknowledged only on the profile it was sent to: another profile's screen must not erase it unseen.
        if (outcome != GiftSendOutcome.Uncertain && retainedRelevant) RetainedGiftSendStore.acknowledge(accountId)
    }
    LaunchedEffect(reload) {
        generation++
        loading = true
        error = null
        unsupported = false
        loadingMore = false
        moreError = null
        // No "load more" may start from the previous page while this refresh is running.
        receivedNextOffset = ""
        try {
            load()
                .onSuccess { loaded ->
                    info = loaded
                    receivedNextOffset = loaded.receivedNextOffset
                }
                .onFailure { failure ->
                    if (failure is MonetizationUnavailableException) unsupported = true
                    else error = failure.userMessage(context, R.string.gifts_load_error)
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            loading = false
        }
    }

    fun dismissDialog() {
        if (sending) return
        step = step.next(GiftSendEvent.Dismiss)
        selectedGift = null
    }

    fun chooseGift(gift: GiftCatalogItem) {
        if (sending || pendingGiftId != 0L) return
        notice = null
        selectedGift = gift
        giftText = ""
        hideSenderName = false
        step = step.next(GiftSendEvent.Select(gift.id))
    }

    fun confirmGift() {
        if (step !is GiftSendStep.Confirming || pendingGiftId != 0L) return
        val gift = selectedGift ?: return
        val currentInfo = info ?: return
        val nextStep = step.next(GiftSendEvent.Confirm)
        if (nextStep !is GiftSendStep.Sending) return
        val blockReason = giftBlockReason(
            gift = gift,
            isSelf = isSelf,
            isPremium = currentInfo.isPremium,
            balanceStars = currentInfo.balance?.starCount,
            busy = sending
        )
        val textTooLong = validateGiftText(giftText, currentInfo.textMaxLength) == GiftTextValidation.TooLong
        val blockMessage = blockReason?.let { context.getString(giftBlockMessage(it)) }
            ?: if (textTooLong) context.getString(R.string.gifts_text_too_long) else null
        if (blockMessage != null) {
            notice = blockMessage
            noticeIsError = true
            step = step.next(GiftSendEvent.Dismiss)
            selectedGift = null
            return
        }
        // Captured before the purchase starts: the dialog is locked while the request runs.
        val text = giftText.trim()
        val isPrivate = hideSenderName
        val started = RetainedGiftSendStore.start(accountId, profileId, isSelf, gift.id) {
            send(gift.id, text, isPrivate)
        }
        if (!started) {
            notice = context.getString(R.string.gifts_block_busy)
            noticeIsError = true
            step = step.next(GiftSendEvent.Dismiss)
            selectedGift = null
            return
        }
        step = nextStep
        pendingGiftId = gift.id
    }

    fun applyReceivedQuery(
        collectionId: Int = selectedCollectionId,
        filter: ReceivedGiftFilter = receivedFilter,
        sort: ReceivedGiftSort = receivedSort
    ) {
        selectedCollectionId = collectionId
        receivedFilter = filter
        receivedSort = sort
        generation++
        val currentGeneration = generation
        loadingMore = false
        moreError = null
        receivedFilterError = null
        receivedNextOffset = ""
        scope.launch {
            loadingReceivedFilter = true
            try {
                loadReceivedPage(collectionId, filter, sort, "")
                    .onSuccess { page ->
                        if (generation != currentGeneration) return@onSuccess
                        info = info?.copy(received = page.gifts)
                        receivedNextOffset = page.nextOffset
                    }
                    .onFailure { failure ->
                        if (generation == currentGeneration) {
                            receivedFilterError = failure.userMessage(context, R.string.gifts_received_error)
                        }
                    }
            } catch (_: TimeoutCancellationException) {
                if (generation == currentGeneration) {
                    receivedFilterError = context.getString(R.string.err_monetization_timeout)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == currentGeneration) {
                    receivedFilterError = context.getString(R.string.gifts_received_error)
                }
            } finally {
                if (generation == currentGeneration) loadingReceivedFilter = false
            }
        }
    }

    fun requestMoreReceived() {
        // A refresh may still show the previous page; never page from its stale offset.
        if (loading || loadingMore || loadingReceivedFilter || receivedNextOffset.isBlank()) return
        val requestedOffset = receivedNextOffset
        val currentGeneration = generation
        scope.launch {
            loadingMore = true
            moreError = null
            try {
                loadReceivedPage(selectedCollectionId, receivedFilter, receivedSort, requestedOffset)
                    .onSuccess { page ->
                        if (generation != currentGeneration) return@onSuccess
                        info = info?.copy(received = mergeReceivedGifts(info?.received.orEmpty(), page.gifts))
                        receivedNextOffset = nextReceivedGiftsOffset(requestedOffset, page.nextOffset)
                    }
                    .onFailure { failure ->
                        if (generation == currentGeneration) {
                            moreError = failure.userMessage(context, R.string.gifts_received_more_error)
                        }
                    }
            } catch (_: TimeoutCancellationException) {
                if (generation == currentGeneration) {
                    moreError = context.getString(R.string.err_monetization_timeout)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == currentGeneration) {
                    moreError = context.getString(R.string.gifts_received_more_error)
                }
            } finally {
                if (generation == currentGeneration) loadingMore = false
            }
        }
    }

    ProvideGiftAnimationBudget {
        GlassScreenScaffold(
            modifier = Modifier.fillMaxSize().glassScreenBackground(design, palette.screen).windowInsetsPadding(WindowInsets.safeDrawing),
            toolbar = {
                ProfileToolbar(title, onBack = { if (!sending) onBack() }) {
                    IconButton(onClick = { if (!loading && !sending) reload++ }, enabled = !loading && !sending) {
                        Icon(Icons.Rounded.Refresh, stringResource(R.string.common_retry), tint = palette.textPrimary)
                    }
                }
            }
        ) { chrome ->
        when {
            info == null && unsupported -> MonetizationCenteredState(
                title = stringResource(R.string.monetization_unsupported_title),
                body = stringResource(R.string.monetization_unsupported_body)
            )
            info == null && error != null -> MonetizationCenteredState(
                title = stringResource(R.string.gifts_load_error),
                body = error.orEmpty(),
                isError = true,
                onRetry = { reload++ }
            )
            // The first read shows the page's own shape instead of a bare spinner, as the Premium screen does.
            info == null -> Box(Modifier.weight(1f).fillMaxWidth()) {
                MonetizationLoadingPlaceholder(listOf(1, 3))
            }
            else -> {
                val gifts = info!!
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = chrome.top, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // A failed refresh keeps the page on screen and offers the same retry as the full-screen error.
                    if (error != null) item("refresh_error") {
                        MonetizationStateCard(
                            title = stringResource(R.string.gifts_load_error),
                            body = error.orEmpty(),
                            tone = MonetizationTone.Error,
                            onRetry = if (loading || sending) null else ({ reload++ }),
                            announce = true
                        )
                    }
                    notice?.let { message ->
                        item("notice") {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                                Text(
                                    message,
                                    color = if (noticeIsError) palette.danger else palette.successText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.errorAnnouncement()
                                )
                                if (pendingGiftId != 0L) {
                                    TextButton(
                                        onClick = {
                                            pendingGiftId = 0L
                                            RetainedGiftSendStore.acknowledge(accountId)
                                            notice = null
                                            noticeIsError = false
                                            reload++
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text(stringResource(R.string.gifts_uncertain_ack))
                                    }
                                }
                            }
                        }
                    }
                    if (auctionGift == null && auctionOperation != null) item("auction_operation") {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                            val outcome = auctionOperation?.outcome
                            Text(auctionOutcomeText(outcome), color = palette.textPrimary)
                            if (outcome != null) TextButton(onClick = { GiftAuctionStore.acknowledge(accountId); reload++ }) {
                                Text(stringResource(R.string.common_close))
                            }
                        }
                    }
                    item("balance") {
                        GiftsBalanceCard(
                            balance = gifts.balance,
                            profileTitle = profileTitle,
                            isSelf = isSelf,
                            onOpenStars = onOpenStars
                        )
                    }
                    item("tab_switcher") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = activeTab == GiftsScreenTab.Received,
                                onClick = { activeTab = GiftsScreenTab.Received },
                                label = { Text(stringResource(R.string.gifts_tab_received)) },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            )
                            FilterChip(
                                selected = activeTab == GiftsScreenTab.Shop,
                                onClick = { activeTab = GiftsScreenTab.Shop },
                                label = { Text(stringResource(R.string.gifts_tab_shop)) },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            )
                        }
                    }
                    if (activeTab == GiftsScreenTab.Received) {
                        if (isSelf && listingGift == null && listingOperation != null) item("listing_status") {
                            val result = listingOperation?.outcome
                            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                                Text(
                                    when (result) {
                                        GiftListingOutcome.Updated -> stringResource(R.string.gift_listing_updated)
                                        GiftListingOutcome.Changed -> stringResource(R.string.gift_listing_changed)
                                        GiftListingOutcome.Uncertain -> stringResource(R.string.gift_listing_uncertain)
                                        is GiftListingOutcome.Failed -> result.reason.resolve(context)
                                        null -> stringResource(R.string.gifts_sending)
                                    },
                                    color = palette.textPrimary
                                )
                                if (result != null) TextButton(onClick = { GiftListingStore.acknowledge(accountId); reload++ }) {
                                    Text(stringResource(R.string.common_close))
                                }
                            }
                        }
                        if (gifts.collections.isNotEmpty()) {
                            item("collections_chips") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item("collection_all") {
                                        FilterChip(
                                            selected = selectedCollectionId == 0,
                                            onClick = { applyReceivedQuery(collectionId = 0) },
                                            label = { Text(stringResource(R.string.gifts_filter_all)) }
                                        )
                                    }
                                    items(gifts.collections, key = { it.id }) { collection ->
                                        val labelText = buildString {
                                            if (collection.emoji.isNotBlank()) {
                                                append(collection.emoji)
                                                append(' ')
                                            }
                                            append(collection.name.ifBlank { stringResource(R.string.gifts_collections_title) })
                                            if (collection.giftCount > 0) {
                                                append(" (")
                                                append(collection.giftCount)
                                                append(')')
                                            }
                                        }
                                        FilterChip(
                                            selected = selectedCollectionId == collection.id,
                                            onClick = { applyReceivedQuery(collectionId = collection.id) },
                                            label = { Text(labelText) }
                                        )
                                    }
                                }
                            }
                        } else if (gifts.collectionsUnavailable) {
                            item("collections_error") {
                                Text(
                                    stringResource(R.string.gifts_collections_error),
                                    color = palette.danger,
                                    fontSize = 13.sp,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                                        .errorAnnouncement()
                                )
                            }
                        }
                        item("received_filters") {
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val filterOptions = buildList {
                                    add(ReceivedGiftFilter.All to R.string.gifts_filter_all)
                                    add(ReceivedGiftFilter.Collectible to R.string.gifts_received_filter_collectible)
                                    add(ReceivedGiftFilter.Regular to R.string.gifts_received_filter_regular)
                                    if (isSelf) {
                                        add(ReceivedGiftFilter.Saved to R.string.gifts_received_filter_saved)
                                        add(ReceivedGiftFilter.Unsaved to R.string.gifts_received_filter_unsaved)
                                    }
                                }
                                filterOptions.forEach { (option, labelRes) ->
                                    FilterChip(
                                        selected = receivedFilter == option,
                                        onClick = { applyReceivedQuery(filter = option) },
                                        label = { Text(stringResource(labelRes)) }
                                    )
                                }
                                FilterChip(
                                    selected = receivedSort == ReceivedGiftSort.Price,
                                    onClick = {
                                        val nextSort = if (receivedSort == ReceivedGiftSort.Price) ReceivedGiftSort.Date else ReceivedGiftSort.Price
                                        applyReceivedQuery(sort = nextSort)
                                    },
                                    label = { Text(stringResource(R.string.gifts_received_sort_price)) }
                                )
                            }
                        }
                        if (loadingReceivedFilter) item("received_filter_loading") {
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), color = palette.accent)
                        }
                        receivedFilterError?.let { message ->
                            item("received_filter_error") {
                                Text(
                                    message,
                                    color = palette.danger,
                                    fontSize = 14.sp,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
                                        .errorAnnouncement()
                                )
                            }
                        }
                        if (gifts.receivedUnavailable) {
                            item("received_error") {
                                Text(
                                    stringResource(R.string.gifts_received_error),
                                    color = palette.danger,
                                    fontSize = 14.sp,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
                                        .errorAnnouncement()
                                )
                            }
                        } else if (gifts.received.isEmpty() && !loadingReceivedFilter) {
                            item("received_empty") {
                                Text(
                                    stringResource(R.string.gifts_received_empty),
                                    color = palette.textSecondary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                                )
                            }
                        } else if (gifts.received.isNotEmpty()) {
                            item("received_grid") {
                                BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    val columns = (maxWidth / 112.dp).toInt().coerceIn(2, 5)
                                    val rows = gifts.received.chunked(columns)
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        rows.forEach { rowGifts ->
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                rowGifts.forEach { gift ->
                                                    ReceivedGiftGridTile(
                                                        gift = gift,
                                                        isSelf = isSelf,
                                                        sending = sending || pendingGiftId != 0L || listingOperation?.inFlight == true,
                                                        onClick = { detailTarget = GiftDetailTarget.Received(gift) },
                                                        onManageListing = { listingGift = gift },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                repeat(columns - rowGifts.size) {
                                                    Spacer(Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (receivedNextOffset.isNotBlank()) item("received_more") {
                                TextButton(
                                    onClick = ::requestMoreReceived,
                                    enabled = !loadingMore && !loading && !loadingReceivedFilter,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                ) {
                                    Text(stringResource(R.string.gifts_received_more))
                                }
                            }
                            if (loadingMore) item("received_more_loading") {
                                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(22.dp).semantics { contentDescription = loadingText }, color = palette.accent)
                                }
                            }
                            moreError?.let { message ->
                                item("received_more_error") {
                                    Text(
                                        message,
                                        color = palette.danger,
                                        fontSize = 14.sp,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                                            .errorAnnouncement()
                                    )
                                }
                            }
                        }
                    } else {
                        item("catalog_header") {
                            MonetizationSectionHeader(stringResource(R.string.gifts_catalog_title))
                        }
                        if (gifts.catalog.isEmpty()) {
                            item("catalog_empty") {
                                Text(
                                    stringResource(R.string.gifts_catalog_empty),
                                    color = palette.textSecondary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
                                )
                            }
                        } else {
                            item("catalog_filter") {
                                GiftCatalogFilterBar(
                                    filter = catalogFilter,
                                    query = catalogQuery,
                                    onFilter = { catalogFilter = it },
                                    onQuery = { catalogQuery = it }
                                )
                            }
                            val filteredCatalog = filterGiftCatalog(gifts.catalog, catalogFilter, catalogQuery)
                            if (filteredCatalog.isEmpty()) {
                                item("catalog_filtered_empty") {
                                    Text(
                                        stringResource(R.string.gifts_catalog_empty),
                                        color = palette.textSecondary,
                                        fontSize = 14.sp,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
                                    )
                                }
                            } else {
                                items(filteredCatalog, key = { it.id }) { gift ->
                                    val blockReason = giftBlockReason(
                                        gift = gift,
                                        isSelf = false,
                                        isPremium = gifts.isPremium,
                                        balanceStars = gifts.balance?.starCount,
                                        busy = sending
                                    )
                                    GiftCatalogRow(
                                        gift = gift,
                                        blockReason = blockReason,
                                        enabled = !sending && pendingGiftId == 0L,
                                        onSend = { chooseGift(gift) },
                                        onAuction = { if (!sending && pendingGiftId == 0L) auctionGift = gift },
                                        onResale = { if (!sending && pendingGiftId == 0L) resaleCatalog = gift }
                                    )
                                }
                            }
                        }
                    }
                    if (loading) item("loading") {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp).semantics { contentDescription = loadingText }, color = palette.accent)
                        }
                    }
                }
            }
        }
    }

    resaleCatalog?.let { catalog ->
        key(accountId, profileId, isSelf, catalog.id) {
            GiftResaleDialog(
                catalog = catalog,
                profileTitle = profileTitle,
                balance = info?.balance,
                purchaseEnabled = !isSelf && !sending && pendingGiftId == 0L,
                load = { offset -> loadResale(catalog.id, offset) },
                onDismiss = { resaleCatalog = null },
                onBuy = { listing ->
                    val price = listing.starCount
                    if (price != null && !isSelf && !sending && pendingGiftId == 0L &&
                        resaleCanBuy(listing, info?.balance) &&
                        RetainedGiftSendStore.start(accountId, profileId, isSelf, listing.id) {
                            buyResale(profileId, listing.name, price)
                        }) {
                        pendingGiftId = listing.id
                        resaleCatalog = null
                    }
                }
            )
        }
    }
    listingGift?.takeIf { isSelf }?.let { gift ->
        key(accountId, gift.id) {
            GiftListingDialog(
                accountId = accountId,
                gift = gift,
                load = loadListing,
                update = updateListing,
                onDismiss = { listingGift = null }
            )
        }
    }
    auctionGift?.let { catalog ->
        androidx.compose.runtime.key(accountId, profileId, isSelf, catalog.id) {
            GiftAuctionDialog(
                accountId, profileId, profileTitle, isSelf, catalog,
                loadAuction, submitBid, loadAuctionHistory,
                purchaseEnabled = retained == null && pendingGiftId == 0L && listingOperation?.inFlight != true,
                onDismiss = { auctionGift = null; reload++ }
            )
        }
    }
    val dialogGift = selectedGift
    if (dialogGift != null && (step is GiftSendStep.Confirming || step is GiftSendStep.Sending)) {
        val textMaxLength = info?.textMaxLength ?: GIFT_TEXT_MAX_LENGTH_DEFAULT
        val textTooLong = validateGiftText(giftText, textMaxLength) == GiftTextValidation.TooLong
        BeyGramDialog(
            onDismissRequest = { dismissDialog() },
            title = stringResource(R.string.gifts_send_dialog_title),
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GiftVisualImage(dialogGift.visual, 30.dp) {
                            Text(dialogGift.emoji.ifBlank { "🎁" }, fontSize = 30.sp)
                        }
                        Spacer(Modifier.size(10.dp))
                        Text(
                            stringResource(R.string.gifts_cost, formatStarAmount(dialogGift.starCount)),
                            color = palette.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        stringResource(
                            R.string.gifts_send_dialog_body,
                            formatStarAmount(dialogGift.starCount)
                        ),
                        color = palette.textPrimary,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = giftText,
                        onValueChange = { giftText = it },
                        label = { Text(stringResource(R.string.gifts_send_text_label)) },
                        supportingText = {
                            Text(
                                if (textTooLong) stringResource(R.string.gifts_text_too_long)
                                else stringResource(R.string.gifts_send_text_counter, giftText.length, textMaxLength)
                            )
                        },
                        isError = textTooLong,
                        enabled = !sending,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    GiftPrivacyRow(
                        checked = hideSenderName,
                        enabled = !sending,
                        onCheckedChange = { hideSenderName = it }
                    )
                    info?.balance?.let { balance ->
                        Text(
                            stringResource(
                                R.string.gifts_send_dialog_balance,
                                formatStarAmount((balance.starCount - dialogGift.starCount).coerceAtLeast(0L))
                            ),
                            color = palette.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                    if (sending) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
                        Text(
                            stringResource(R.string.gifts_sending),
                            color = palette.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                }
            },
            confirmText = if (sending) stringResource(R.string.gifts_sending) else stringResource(R.string.gifts_send_confirm, formatStarAmount(dialogGift.starCount)),
            confirmEnabled = !sending && !textTooLong,
            onConfirm = ::confirmGift,
            dismissText = stringResource(R.string.common_cancel),
            dismissEnabled = !sending,
            onDismiss = ::dismissDialog
        )
    }
    detailTarget?.let { target ->
        val receivedTarget = (target as? GiftDetailTarget.Received)?.gift
        val canManageOwn = isSelf && receivedTarget != null && receivedTarget.id.isNotBlank() && !receivedTarget.wasRefunded
        GiftDetailSheet(
            target = target,
            actionBusy = detailBusy,
            actionError = detailError,
            onToggleSaved = if (canManageOwn) ({
                val g = receivedTarget!!
                if (!detailBusy) {
                    detailBusy = true
                    detailError = null
                    scope.launch {
                        toggleGiftSaved(g.id, !g.isSaved).fold(
                            onSuccess = {
                                val nextSaved = !g.isSaved
                                info = info?.let { current ->
                                    current.copy(
                                        received = current.received.map { item ->
                                            if (item.id == g.id) item.copy(isSaved = nextSaved) else item
                                        }
                                    )
                                }
                                detailTarget = null
                            },
                            onFailure = { detailError = it.userMessage(context, R.string.feature_action_failed) }
                        )
                        detailBusy = false
                    }
                }
            }) else null,
            onTogglePinned = if (canManageOwn) ({
                val g = receivedTarget!!
                if (!detailBusy) {
                    detailBusy = true
                    detailError = null
                    val nextPinned = togglePinnedGiftIds(info?.received.orEmpty(), g.id)
                    scope.launch {
                        setPinnedGifts(nextPinned).fold(
                            onSuccess = {
                                val pinnedSet = nextPinned.toSet()
                                info = info?.let { current ->
                                    current.copy(
                                        received = current.received.map { item ->
                                            item.copy(isPinned = item.id in pinnedSet)
                                        }
                                    )
                                }
                                detailTarget = null
                            },
                            onFailure = { detailError = it.userMessage(context, R.string.feature_action_failed) }
                        )
                        detailBusy = false
                    }
                }
            }) else null,
            onDismiss = {
                if (!detailBusy) {
                    detailError = null
                    detailTarget = null
                }
            }
        )
    }
    }
}

@Composable
private fun GiftsBalanceCard(
    balance: StarBalance?,
    profileTitle: String,
    isSelf: Boolean,
    onOpenStars: () -> Unit
) {
    val palette = beyGramPalette()
    MonetizationCard {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            if (!isSelf && profileTitle.isNotBlank()) {
                Text(
                    stringResource(R.string.gifts_target, profileTitle),
                    color = palette.textSecondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Star, null, tint = palette.accentInk, modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    if (balance == null) stringResource(R.string.gifts_balance_unknown)
                    else stringResource(
                        R.string.gifts_balance,
                        formatStarAmount(balance.starCount, balance.nanostarCount)
                    ),
                    color = palette.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
            TextButton(onClick = onOpenStars, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.gifts_open_stars))
            }
        }
    }
}

/** The whole row toggles the switch, so the label and the control announce as one setting. */
@Composable
private fun GiftPrivacyRow(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(R.string.gifts_hide_name), color = palette.textPrimary, fontSize = 14.sp)
            Text(stringResource(R.string.gifts_hide_name_hint), color = palette.textSecondary, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GiftCatalogFilterBar(
    filter: GiftCatalogFilter,
    query: String,
    onFilter: (GiftCatalogFilter) -> Unit,
    onQuery: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            label = { Text(stringResource(R.string.gifts_search_hint)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            GiftCatalogFilter.entries.forEach { option ->
                val labelRes = when (option) {
                    GiftCatalogFilter.All -> R.string.gifts_filter_all
                    GiftCatalogFilter.Available -> R.string.gifts_filter_available
                    GiftCatalogFilter.Resale -> R.string.gifts_filter_resale
                    GiftCatalogFilter.Auction -> R.string.gifts_filter_auction
                    GiftCatalogFilter.Limited -> R.string.gifts_filter_limited
                }
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilter(option) },
                    label = { Text(stringResource(labelRes)) }
                )
            }
        }
    }
}

@Composable
private fun GiftCatalogRow(
    gift: GiftCatalogItem,
    blockReason: GiftBlockReason?,
    enabled: Boolean,
    showSend: Boolean = true,
    onSend: () -> Unit,
    onAuction: () -> Unit,
    onResale: () -> Unit
) {
    val palette = beyGramPalette()
    Column(
        Modifier.fillMaxWidth().profileCardSurface()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // The gift and its text read as one item; the buttons beside them stay separate controls for TalkBack.
            Row(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                GiftArtworkFrame(
                    visual = gift.visual,
                    emoji = gift.emoji,
                    frameSize = 52.dp,
                    stickerSize = 36.dp,
                    cornerRadius = 12.dp
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        if (gift.auctionId.isNullOrBlank()) stringResource(R.string.gifts_cost, formatStarAmount(gift.starCount)) else stringResource(R.string.gift_auction_title),
                        color = palette.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (gift.resaleTitle.isNotBlank()) Text(gift.resaleTitle, color = palette.textPrimary, fontSize = 14.sp)
                    val details = mutableListOf<String>()
                    if (gift.isPremiumOnly) details += stringResource(R.string.gifts_premium_only)
                    if (gift.isAuctionOnly) details += stringResource(R.string.gifts_auction_only)
                    if (gift.isSoldOut) details += stringResource(R.string.gifts_sold_out)
                    gift.remainingCount?.takeIf { it > 0 }?.let {
                        details += stringResource(R.string.gifts_remaining, it)
                    }
                    if (details.isNotEmpty()) {
                        Text(details.joinToString(" · "), color = palette.textSecondary, fontSize = 12.sp)
                    }
                    blockReason?.takeUnless { it == GiftBlockReason.AuctionOnly && !gift.auctionId.isNullOrBlank() }?.let { reason ->
                        Text(
                            stringResource(giftBlockMessage(reason)),
                            color = palette.danger,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp).errorAnnouncement()
                        )
                    }
                }
            }
            if (!gift.auctionId.isNullOrBlank()) {
                TextButton(onClick = onAuction, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.gift_auction_title))
                }
            } else if (blockReason == null && showSend) {
                TextButton(
                    onClick = onSend,
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.gifts_send))
                }
            } else if (blockReason != null) {
                Icon(Icons.Rounded.CardGiftcard, null, tint = palette.disabled, modifier = Modifier.size(24.dp))
            }
        }
        if (gift.resaleCount > 0) {
            if (gift.minResaleStarCount > 0L) Text(
                stringResource(R.string.gifts_resale_summary, gift.resaleCount.toString(), formatStarAmount(gift.minResaleStarCount)),
                color = palette.textSecondary, fontSize = 13.sp
            )
            TextButton(onClick = onResale, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.gifts_resale_title))
            }
        }
    }
}

@Composable
internal fun ReceivedGiftGridTile(
    gift: ReceivedGiftItem,
    isSelf: Boolean,
    sending: Boolean,
    onClick: () -> Unit,
    onManageListing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = beyGramPalette()
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface2.copy(alpha = 0.65f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GiftArtworkFrame(
                visual = gift.visual,
                emoji = gift.emoji,
                collectible = gift.collectible,
                frameSize = 68.dp,
                stickerSize = 48.dp,
                symbolSize = 18.dp,
                cornerRadius = 14.dp
            )
            if (gift.isPinned) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = stringResource(R.string.gifts_pinned),
                    tint = palette.accentInk,
                    modifier = Modifier.align(Alignment.TopEnd).size(15.dp)
                )
            }
            if (isSelf && !gift.isSaved) {
                Icon(
                    Icons.Rounded.VisibilityOff,
                    contentDescription = stringResource(R.string.gifts_received_filter_unsaved),
                    tint = palette.textSecondary,
                    modifier = Modifier.align(Alignment.TopStart).size(15.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val title = gift.collectible?.let { "#${it.number}" }
            ?: gift.name.ifBlank { stringResource(R.string.gifts_gift_generic) }
        Text(
            title,
            color = palette.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        gift.starCount?.let { stars ->
            Text(
                stringResource(R.string.gifts_cost, formatStarAmount(stars)),
                color = palette.accentInk,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isSelf && gift.isUpgraded && !gift.wasRefunded && gift.id.isNotBlank()) {
            TextButton(
                onClick = onManageListing,
                enabled = !sending,
                modifier = Modifier.heightIn(min = 36.dp)
            ) {
                Text(stringResource(R.string.gift_listing_manage), fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun ReceivedGiftRow(gift: ReceivedGiftItem, onClick: (() -> Unit)? = null) {
    val palette = beyGramPalette()
    val cardModifier = Modifier.fillMaxWidth().profileCardSurface().let { base ->
        if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base
    }
    Column(
        cardModifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The gift itself is the main visual, so the emoji gets a larger slot than the text around it.
            // An upgraded gift shows its backdrop behind the model and its symbol in the corner; a regular gift has neither.
            GiftArtworkFrame(
                visual = gift.visual,
                emoji = gift.emoji,
                collectible = gift.collectible,
                frameSize = 52.dp,
                stickerSize = 36.dp,
                symbolSize = 18.dp,
                cornerRadius = 12.dp,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                // Regular gifts have no title from Telegram, so they show a generic name rather than the list heading.
                val displayTitle = gift.name.ifBlank {
                    gift.collectible?.title.orEmpty().ifBlank { stringResource(R.string.gifts_gift_generic) }
                }
                Text(
                    displayTitle,
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                gift.collectible?.modelName?.takeIf { it.isNotBlank() && it != displayTitle }?.let { model ->
                    Text(model, color = palette.textSecondary, fontSize = 12.sp)
                }
                Text(
                    stringResource(R.string.gifts_from, gift.senderName ?: stringResource(R.string.gifts_sender_unknown)),
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(gift.dateSeconds.coerceAtLeast(0).toLong() * 1000L)),
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )
                // An upgraded gift names its number and the model's rarity; a regular gift names neither.
                gift.collectible?.let { details ->
                    Text(
                        stringResource(R.string.gifts_collectible_number, details.number),
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                    // A tier is named and coloured per level, with the exact share as a fallback when TDLib sends
                    // only the per-mille value, which is when the tier is null.
                    val rarityLabel = when {
                        details.modelRarityTier != null ->
                            stringResource(giftRarityLabelRes(details.modelRarityTier))
                        details.modelRarityPerMille != null ->
                            stringResource(
                                R.string.gifts_rarity_percent,
                                String.format(java.util.Locale.getDefault(), "%.1f", details.modelRarityPerMille / 10.0)
                            )
                        else -> null
                    }
                    rarityLabel?.let { label ->
                        Text(
                            label,
                            color = giftRarityColor(details.modelRarityTier, palette),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            gift.starCount?.let { stars ->
                Text(
                    stringResource(R.string.gifts_cost, formatStarAmount(stars)),
                    color = palette.accentInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (gift.text.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(gift.text, color = palette.textPrimary, fontSize = 14.sp)
        }
        val badges = mutableListOf<String>()
        if (gift.isPinned) badges += stringResource(R.string.gifts_pinned)
        if (gift.isUpgraded) badges += stringResource(R.string.gifts_upgraded)
        if (gift.isPrivate) badges += stringResource(R.string.gifts_private)
        if (gift.wasRefunded) badges += stringResource(R.string.gifts_refunded)
        if (badges.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                badges.joinToString(" · "),
                color = if (gift.wasRefunded) palette.danger else palette.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun GiftCollectionRow(collection: GiftCollectionItem) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().profileCardSurface()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (collection.emoji.isNotBlank()) Text(collection.emoji, fontSize = 26.sp)
        else Icon(Icons.Rounded.CardGiftcard, null, tint = palette.accentInk, modifier = Modifier.size(24.dp))
        Text(
            collection.name.ifBlank { stringResource(R.string.gifts_collections_title) },
            color = palette.textPrimary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f).padding(start = 12.dp)
        )
        Text(
            pluralStringResource(
                R.plurals.gifts_collection_count,
                collection.giftCount,
                collection.giftCount
            ),
            color = palette.textSecondary,
            fontSize = 12.sp
        )
    }
}

@StringRes
private fun giftBlockMessage(reason: GiftBlockReason): Int = when (reason) {
    GiftBlockReason.OwnProfile -> R.string.gifts_block_own
    GiftBlockReason.Busy -> R.string.gifts_block_busy
    GiftBlockReason.AuctionOnly -> R.string.gifts_block_auction
    GiftBlockReason.SoldOut -> R.string.gifts_block_sold_out
    GiftBlockReason.PremiumRequired -> R.string.gifts_block_premium
    GiftBlockReason.BalanceUnknown -> R.string.gifts_block_balance_unknown
    GiftBlockReason.InsufficientStars -> R.string.gifts_block_balance
}

/**
 * Process-wide retention for a SendGift operation. It deliberately outlives the screen
 * composition, so a rotation or navigation cannot cancel the request or lose its result.
 * A result stays until the screen acknowledges it.
 */
private data class RetainedGiftSend(
    val profileId: Long,
    val isSelf: Boolean,
    val giftId: Long,
    val inFlight: Boolean,
    val outcome: GiftSendOutcome? = null
)

private object RetainedGiftSendStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _states = MutableStateFlow<Map<String, RetainedGiftSend>>(emptyMap())
    val states: StateFlow<Map<String, RetainedGiftSend>> = _states.asStateFlow()

    /** Returns false when the same account already has an operation or an unacknowledged result. */
    fun start(
        accountId: String,
        profileId: Long,
        isSelf: Boolean,
        giftId: Long,
        send: suspend () -> GiftSendOutcome
    ): Boolean {
        if (_states.value[accountId] != null) return false
        update(accountId, RetainedGiftSend(profileId, isSelf, giftId, inFlight = true))
        scope.launch {
            val outcome = try {
                send()
            } catch (_: TimeoutCancellationException) {
                GiftSendOutcome.Uncertain
            } catch (cancelled: CancellationException) {
                // The process is going away; there is nothing left to publish.
                return@launch
            } catch (_: Exception) {
                GiftSendOutcome.Uncertain
            }
            update(accountId, RetainedGiftSend(profileId, isSelf, giftId, inFlight = false, outcome = outcome))
        }
        return true
    }

    fun acknowledge(accountId: String) {
        _states.value = _states.value - accountId
    }

    private fun update(accountId: String, value: RetainedGiftSend) {
        _states.value = _states.value + (accountId to value)
    }
}

/** The label of a model's rarity tier, shown under an upgraded gift's number. */
internal fun giftRarityLabelRes(tier: GiftRarityTier): Int = when (tier) {
    GiftRarityTier.Uncommon -> R.string.gifts_rarity_uncommon
    GiftRarityTier.Rare -> R.string.gifts_rarity_rare
    GiftRarityTier.Epic -> R.string.gifts_rarity_epic
    GiftRarityTier.Legendary -> R.string.gifts_rarity_legendary
}

/** Each rarity tier has its own colour from the theme palette, so the levels read apart without the label. */
internal fun giftRarityColor(tier: GiftRarityTier?, palette: BeyGramPalette): Color = when (tier) {
    GiftRarityTier.Uncommon -> palette.textSecondary
    GiftRarityTier.Rare -> palette.accentInk
    GiftRarityTier.Epic -> palette.warningText
    GiftRarityTier.Legendary -> palette.accent
    null -> palette.textSecondary
}
