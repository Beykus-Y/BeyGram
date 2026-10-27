package com.beykus.beygram.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ExternalPaymentMethod
import com.beykus.beygram.domain.model.MonetizationUnavailableException
import com.beykus.beygram.domain.model.StarBalance
import com.beykus.beygram.domain.model.StarTopUpOption
import com.beykus.beygram.domain.model.StarTransactionItem
import com.beykus.beygram.domain.model.StarTransactionKind
import com.beykus.beygram.domain.model.StarTransactionsPage
import com.beykus.beygram.domain.model.StarsInfo
import com.beykus.beygram.domain.model.StarsTopUpEvent
import com.beykus.beygram.domain.model.StarsTopUpOutcome
import com.beykus.beygram.domain.model.StarsTopUpStep
import com.beykus.beygram.domain.model.formatPriceMinorUnits
import com.beykus.beygram.domain.model.formatStarAmount
import com.beykus.beygram.domain.model.next
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.resolve
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun StarsScreen(
    load: suspend () -> Result<StarsInfo>,
    loadMore: suspend (String) -> Result<StarTransactionsPage>,
    topUp: suspend (StarTopUpOption) -> StarsTopUpOutcome,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var info by remember { mutableStateOf<StarsInfo?>(null) }
    var transactions by remember { mutableStateOf<List<StarTransactionItem>>(emptyList()) }
    var nextOffset by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var unsupported by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var moreError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    // The token drops a "load more" response that arrives after a refresh started.
    var generation by remember { mutableIntStateOf(0) }
    var checkout by remember { mutableStateOf<StarsTopUpStep>(StarsTopUpStep.Idle) }
    var checkoutGeneration by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<PaymentNotice?>(null) }
    var balanceBeforeTopUp by remember { mutableStateOf<Long?>(null) }
    var checkAfterReturn by remember { mutableStateOf(false) }
    // The browser can outlive this composition; only flags survive rotation. The URL never does.
    var awaitingExternal by rememberSaveable { mutableStateOf(false) }
    var leftApp by rememberSaveable { mutableStateOf(false) }
    val checkoutBusy = awaitingExternal ||
        checkout is StarsTopUpStep.Confirming ||
        checkout is StarsTopUpStep.Preparing ||
        checkout is StarsTopUpStep.Ready ||
        checkout is StarsTopUpStep.Opening ||
        checkout is StarsTopUpStep.AwaitingReturn
    val listState = rememberLazyListState()

    LaunchedEffect(notice) {
        if (notice != null) listState.animateScrollToItem(0)
    }

    fun refreshAfterPayment() {
        checkoutGeneration++
        awaitingExternal = false
        leftApp = false
        checkout = checkout.next(StarsTopUpEvent.Returned)
        checkAfterReturn = true
        reload++
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> if (awaitingExternal) leftApp = true
                Lifecycle.Event.ON_RESUME -> if (awaitingExternal && leftApp) refreshAfterPayment()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    BackHandler(onBack = onBack)
    LaunchedEffect(reload) {
        val afterPayment = checkAfterReturn
        checkAfterReturn = false
        val currentGeneration = generation + 1
        generation = currentGeneration
        loading = true
        error = null
        unsupported = false
        loadingMore = false
        moreError = null
        // No pagination may start from the previous page while this refresh is running.
        nextOffset = ""
        try {
            load()
                .onSuccess { loaded ->
                    if (generation != currentGeneration) return@onSuccess
                    info = loaded
                    transactions = loaded.transactions
                    nextOffset = loaded.nextOffset
                    if (afterPayment) {
                        val before = balanceBeforeTopUp
                        val delta = before?.let { loaded.balance.starCount - it }
                        notice = if (delta != null && delta > 0L) {
                            PaymentNotice(
                                context.getString(R.string.stars_topup_success, formatStarAmount(delta)),
                                PaymentTone.Success
                            )
                        } else {
                            PaymentNotice(context.getString(R.string.stars_topup_refreshed), PaymentTone.Neutral)
                        }
                    }
                    balanceBeforeTopUp = loaded.balance.starCount
                }
                .onFailure { failure ->
                    if (generation != currentGeneration) return@onFailure
                    if (failure is MonetizationUnavailableException) unsupported = true
                    else error = failure.userMessage(context, R.string.stars_load_error)
                    if (afterPayment) {
                        notice = PaymentNotice(context.getString(R.string.stars_topup_refreshed), PaymentTone.Neutral)
                    }
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            if (generation == currentGeneration) loading = false
        }
    }

    fun requestMore() {
        // A refresh may still show the previous page; never page from its stale offset.
        if (loading || loadingMore || nextOffset.isBlank()) return
        val requestedOffset = nextOffset
        val currentGeneration = generation
        scope.launch {
            loadingMore = true
            moreError = null
            try {
                loadMore(requestedOffset)
                    .onSuccess { page ->
                        if (generation != currentGeneration) return@onSuccess
                        transactions = (transactions + page.transactions).distinctBy { it.identityKey }
                        nextOffset = if (page.nextOffset == requestedOffset) "" else page.nextOffset
                        info = info?.copy(balance = page.balance)
                    }
                    .onFailure { failure ->
                        if (generation == currentGeneration) {
                            moreError = failure.userMessage(context, R.string.stars_history_error)
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
                    moreError = context.getString(R.string.stars_history_error)
                }
            } finally {
                if (generation == currentGeneration) loadingMore = false
            }
        }
    }

    fun selectTopUp(option: StarTopUpOption) {
        if (loading || checkoutBusy) return
        notice = null
        checkout = checkout.next(StarsTopUpEvent.Select(option))
    }

    fun dismissTopUp() {
        checkoutGeneration++
        checkout = checkout.next(StarsTopUpEvent.Dismiss)
    }

    fun confirmTopUp() {
        val confirming = checkout as? StarsTopUpStep.Confirming ?: return
        if (awaitingExternal) return
        checkout = checkout.next(StarsTopUpEvent.Confirm)
        val generation = ++checkoutGeneration
        scope.launch {
            val outcome = try {
                topUp(confirming.option)
            } catch (_: TimeoutCancellationException) {
                StarsTopUpOutcome.Failed(UiText.Res(R.string.err_monetization_timeout))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                StarsTopUpOutcome.Failed(UiText.Res(R.string.err_monetization_failed))
            }
            // The dialog may have been dismissed while this read-only request was in flight.
            if (!isActive || generation != checkoutGeneration) return@launch
            when (outcome) {
                is StarsTopUpOutcome.Ready -> {
                    if (outcome.methods.isEmpty()) {
                        checkout = checkout.next(
                            StarsTopUpEvent.Failed(UiText.Res(R.string.stars_topup_unavailable_body))
                        )
                        notice = PaymentNotice(
                            context.getString(R.string.stars_topup_unavailable_body),
                            PaymentTone.Error
                        )
                    } else {
                        checkout = checkout.next(StarsTopUpEvent.Ready(outcome.methods))
                    }
                }
                StarsTopUpOutcome.PackageChanged -> {
                    checkout = checkout.next(
                        StarsTopUpEvent.Failed(UiText.Res(R.string.stars_topup_changed))
                    )
                    notice = PaymentNotice(context.getString(R.string.stars_topup_changed), PaymentTone.Error)
                    reload++
                }
                StarsTopUpOutcome.Unavailable -> {
                    checkout = checkout.next(
                        StarsTopUpEvent.Failed(UiText.Res(R.string.stars_topup_unavailable_body))
                    )
                    notice = PaymentNotice(
                        context.getString(R.string.stars_topup_unavailable_body),
                        PaymentTone.Error
                    )
                    // Options may have changed server-side; refresh the package list.
                    reload++
                }
                is StarsTopUpOutcome.SpendsStars -> {
                    val amount = formatStarAmount(outcome.starCount)
                    checkout = checkout.next(
                        StarsTopUpEvent.Failed(UiText.Res(R.string.stars_topup_spends_stars, listOf(amount)))
                    )
                    notice = PaymentNotice(
                        context.getString(R.string.stars_topup_spends_stars, amount),
                        PaymentTone.Error
                    )
                }
                StarsTopUpOutcome.SubscriptionUnsupported -> {
                    checkout = checkout.next(
                        StarsTopUpEvent.Failed(UiText.Res(R.string.stars_topup_subscription))
                    )
                    notice = PaymentNotice(
                        context.getString(R.string.stars_topup_subscription),
                        PaymentTone.Error
                    )
                }
                StarsTopUpOutcome.Busy -> {
                    checkout = checkout.next(StarsTopUpEvent.Failed(UiText.Res(R.string.stars_topup_busy)))
                    notice = PaymentNotice(context.getString(R.string.stars_topup_busy), PaymentTone.Error)
                }
                is StarsTopUpOutcome.Failed -> {
                    checkout = checkout.next(StarsTopUpEvent.Failed(outcome.reason))
                    notice = PaymentNotice(outcome.reason.resolve(context), PaymentTone.Error)
                }
            }
        }
    }

    fun openTopUpMethod(method: ExternalPaymentMethod) {
        val ready = checkout as? StarsTopUpStep.Ready ?: return
        if (method !in ready.methods) return
        val opening = checkout.next(StarsTopUpEvent.Open(method))
        if (opening !is StarsTopUpStep.Opening) return
        checkout = opening
        balanceBeforeTopUp = info?.balance?.starCount
        if (openExternalPaymentPage(context, method.url.value)) {
            checkout = checkout.next(StarsTopUpEvent.Opened)
            awaitingExternal = true
            leftApp = false
            notice = PaymentNotice(context.getString(R.string.stars_topup_opened_title), PaymentTone.Neutral)
        } else {
            checkout = checkout.next(
                StarsTopUpEvent.Failed(UiText.Res(R.string.stars_topup_open_failed))
            )
            notice = PaymentNotice(context.getString(R.string.stars_topup_open_failed), PaymentTone.Error)
        }
    }

    val confirmOption = when (val step = checkout) {
        is StarsTopUpStep.Confirming -> step.option
        is StarsTopUpStep.Preparing -> step.option
        else -> null
    }
    val readyStep = checkout as? StarsTopUpStep.Ready

    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.stars_title), onBack) {
            IconButton(onClick = { if (!loading && !checkoutBusy) reload++ }, enabled = !loading && !checkoutBusy) {
                Icon(Icons.Rounded.Refresh, stringResource(R.string.common_retry), tint = palette.textPrimary)
            }
        }
        when {
            info == null && unsupported -> MonetizationCenteredState(
                title = stringResource(R.string.monetization_unsupported_title),
                body = stringResource(R.string.monetization_unsupported_body)
            )
            info == null && error != null -> MonetizationCenteredState(
                title = stringResource(R.string.stars_load_error),
                body = error.orEmpty(),
                isError = true,
                onRetry = { reload++ }
            )
            info == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.accent)
            }
            else -> {
                val stars = info!!
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (error != null) item("refresh_error") {
                        Text(
                            error.orEmpty(),
                            color = palette.danger,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
                                .errorAnnouncement()
                        )
                    }
                    notice?.let { current ->
                        item("payment_notice") { PaymentNoticeCard(current) }
                    }
                    item("balance") { StarsBalanceCard(stars.balance, loading) }
                    item("topup_header") { MonetizationSectionHeader(stringResource(R.string.stars_topup_title)) }
                    if (awaitingExternal || checkout is StarsTopUpStep.AwaitingReturn) {
                        item("topup_awaiting") {
                            MonetizationStateCard(
                                title = stringResource(R.string.stars_topup_opened_title),
                                body = stringResource(R.string.stars_topup_opened_body),
                                onRetry = if (loading) null else ({ refreshAfterPayment() }),
                                retryLabel = R.string.stars_topup_check
                            )
                        }
                    }
                    if (stars.topUpOptions.isNotEmpty()) {
                        item("topup_info") {
                            MonetizationStateCard(
                                title = stringResource(R.string.stars_topup_browser_title),
                                body = stringResource(R.string.stars_topup_browser_body)
                            )
                        }
                        item("topup_options") {
                            MonetizationCard {
                                stars.topUpOptions.forEachIndexed { index, option ->
                                    if (index > 0) {
                                        HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 18.dp))
                                    }
                                    StarTopUpRow(
                                        option = option,
                                        enabled = !checkoutBusy && !loading,
                                        onSelect = { selectTopUp(option) }
                                    )
                                }
                            }
                        }
                    } else {
                        item("topup_empty") {
                            Text(
                                stringResource(
                                    if (stars.topUpOptionsUnavailable) R.string.stars_topup_error
                                    else R.string.stars_topup_empty
                                ),
                                color = palette.textSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
                            )
                        }
                    }
                    item("history_header") {
                        MonetizationSectionHeader(stringResource(R.string.stars_history_title))
                    }
                    if (transactions.isEmpty() && !loading) {
                        item("history_empty") {
                            Text(
                                stringResource(R.string.stars_history_empty),
                                color = palette.textSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                    }
                    items(transactions, key = { it.identityKey }) { transaction ->
                        StarTransactionRow(transaction)
                    }
                    if (transactions.isNotEmpty() && nextOffset.isNotBlank()) {
                        item("more") {
                            TextButton(
                                onClick = ::requestMore,
                                enabled = !loadingMore && !loading,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) {
                                Text(stringResource(R.string.stars_more))
                            }
                        }
                    }
                    if (loadingMore) item("more_loading") {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = palette.accent)
                        }
                    }
                    moreError?.let { message ->
                        item("more_error") {
                            Text(
                                message,
                                color = palette.danger,
                                fontSize = 14.sp,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                                    .errorAnnouncement()
                            )
                        }
                    }
                    if (loading) item("loading") {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = palette.accent)
                        }
                    }
                }
            }
        }
    }
    if (confirmOption != null) {
        StarsTopUpConfirmDialog(
            option = confirmOption,
            balanceStars = info?.balance?.starCount,
            preparing = checkout is StarsTopUpStep.Preparing,
            onConfirm = ::confirmTopUp,
            onDismiss = ::dismissTopUp
        )
    }
    if (readyStep != null) {
        StarsTopUpMethodsDialog(
            option = readyStep.option,
            methods = readyStep.methods,
            onOpen = ::openTopUpMethod,
            onDismiss = ::dismissTopUp
        )
    }
}

@Composable
private fun StarsBalanceCard(balance: StarBalance, loading: Boolean) {
    val palette = beyGramPalette()
    MonetizationCard {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)
                .semantics(mergeDescendants = true) {}
        ) {
            Text(
                stringResource(R.string.stars_balance_heading),
                color = palette.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Star, null, tint = palette.accentInk, modifier = Modifier.size(30.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(
                        R.string.stars_balance_value,
                        formatStarAmount(balance.starCount, balance.nanostarCount)
                    ),
                    color = palette.textPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                if (loading) {
                    Spacer(Modifier.size(10.dp))
                    CircularProgressIndicator(Modifier.size(18.dp), color = palette.accent)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.stars_balance_hint), color = palette.textSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StarTopUpRow(
    option: StarTopUpOption,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth()
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = stringResource(R.string.stars_topup_select, formatStarAmount(option.starCount)),
                onClick = onSelect
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.stars_topup_option_stars, formatStarAmount(option.starCount)),
            color = if (enabled) palette.textPrimary else palette.textSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(
                R.string.stars_topup_option_price,
                formatPriceMinorUnits(option.amountMinor, option.currency)
            ),
            color = palette.textSecondary,
            fontSize = 14.sp
        )
        if (enabled) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                tint = palette.textSecondary,
                modifier = Modifier.padding(start = 6.dp).size(20.dp)
            )
        }
    }
}

@Composable
private fun StarsTopUpConfirmDialog(
    option: StarTopUpOption,
    balanceStars: Long?,
    preparing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = beyGramPalette()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.stars_topup_confirm_title),
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        R.string.stars_topup_confirm_body,
                        formatStarAmount(option.starCount),
                        formatPriceMinorUnits(option.amountMinor, option.currency)
                    ),
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )
                if (balanceStars != null) {
                    Text(
                        stringResource(R.string.stars_topup_confirm_balance, formatStarAmount(balanceStars)),
                        color = palette.textSecondary,
                        fontSize = 13.sp
                    )
                }
                Text(
                    stringResource(R.string.stars_topup_external_hint),
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                if (preparing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
                    Text(
                        stringResource(R.string.stars_topup_preparing),
                        color = palette.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !preparing,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(
                    if (preparing) stringResource(R.string.stars_topup_preparing)
                    else stringResource(R.string.stars_topup_continue)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun StarsTopUpMethodsDialog(
    option: StarTopUpOption,
    methods: List<ExternalPaymentMethod>,
    onOpen: (ExternalPaymentMethod) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = beyGramPalette()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.stars_topup_methods_title),
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        R.string.stars_topup_methods_summary,
                        formatStarAmount(option.starCount),
                        formatPriceMinorUnits(option.amountMinor, option.currency)
                    ),
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.stars_topup_methods_hint),
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                methods.forEach { method ->
                    FilledTonalButton(
                        onClick = { onOpen(method) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = palette.accentSoft,
                            contentColor = palette.accentInk
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(
                            method.title.ifBlank { stringResource(R.string.stars_topup_method_fallback) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun StarTransactionRow(transaction: StarTransactionItem) {
    val palette = beyGramPalette()
    val positive = transaction.amount.starCount > 0 ||
        (transaction.amount.starCount == 0L && transaction.amount.nanostarCount > 0)
    val amountText = formatStarAmount(transaction.amount.starCount, transaction.amount.nanostarCount)
        .let { if (positive) "+$it" else it }
    Column(
        Modifier.fillMaxWidth().profileCardSurface(palette.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(starTransactionLabel(transaction.kind)),
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(transaction.dateSeconds.coerceAtLeast(0).toLong() * 1000L)),
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )
            }
            Text(
                amountText,
                color = when {
                    positive -> palette.successText
                    transaction.amount.starCount < 0 || transaction.amount.nanostarCount < 0 -> palette.danger
                    else -> palette.textSecondary
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            )
        }
        if (transaction.isRefund) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.stars_tx_refund), color = palette.warningText, fontSize = 12.sp)
        }
    }
}

@StringRes
internal fun starTransactionLabel(kind: StarTransactionKind): Int = when (kind) {
    StarTransactionKind.TopUp -> R.string.stars_tx_topup
    StarTransactionKind.GiftPurchase -> R.string.stars_tx_gift_purchase
    StarTransactionKind.GiftSale -> R.string.stars_tx_gift_sale
    StarTransactionKind.GiftUpgrade -> R.string.stars_tx_gift_upgrade
    StarTransactionKind.GiftTransfer -> R.string.stars_tx_gift_transfer
    StarTransactionKind.GiftAuction -> R.string.stars_tx_gift_auction
    StarTransactionKind.PremiumPurchase -> R.string.stars_tx_premium
    StarTransactionKind.PaidMessageSend -> R.string.stars_tx_paid_message_send
    StarTransactionKind.PaidMessageReceive -> R.string.stars_tx_paid_message_receive
    StarTransactionKind.PaidReactionSend -> R.string.stars_tx_paid_reaction_send
    StarTransactionKind.PaidReactionReceive -> R.string.stars_tx_paid_reaction_receive
    StarTransactionKind.PaidMediaPurchase -> R.string.stars_tx_paid_media_purchase
    StarTransactionKind.PaidMediaSale -> R.string.stars_tx_paid_media_sale
    StarTransactionKind.SubscriptionPurchase -> R.string.stars_tx_subscription_purchase
    StarTransactionKind.SubscriptionSale -> R.string.stars_tx_subscription_sale
    StarTransactionKind.InvoicePurchase -> R.string.stars_tx_invoice_purchase
    StarTransactionKind.InvoiceSale -> R.string.stars_tx_invoice_sale
    StarTransactionKind.Withdrawal -> R.string.stars_tx_withdrawal
    StarTransactionKind.Other -> R.string.stars_tx_other
}
