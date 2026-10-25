package com.beykus.beygram.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MonetizationPayments
import com.beykus.beygram.domain.model.MonetizationUnavailableException
import com.beykus.beygram.domain.model.StarBalance
import com.beykus.beygram.domain.model.StarTopUpOption
import com.beykus.beygram.domain.model.StarTransactionItem
import com.beykus.beygram.domain.model.StarTransactionKind
import com.beykus.beygram.domain.model.StarTransactionsPage
import com.beykus.beygram.domain.model.StarsInfo
import com.beykus.beygram.domain.model.formatPriceMinorUnits
import com.beykus.beygram.domain.model.formatStarAmount
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun StarsScreen(
    load: suspend () -> Result<StarsInfo>,
    loadMore: suspend (String) -> Result<StarTransactionsPage>,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    BackHandler(onBack = onBack)
    LaunchedEffect(reload) {
        val currentGeneration = generation + 1
        generation = currentGeneration
        loading = true
        error = null
        unsupported = false
        loadingMore = false
        moreError = null
        try {
            load()
                .onSuccess { loaded ->
                    if (generation != currentGeneration) return@onSuccess
                    info = loaded
                    transactions = loaded.transactions
                    nextOffset = loaded.nextOffset
                }
                .onFailure { failure ->
                    if (generation != currentGeneration) return@onFailure
                    if (failure is MonetizationUnavailableException) unsupported = true
                    else error = failure.userMessage(context, R.string.stars_load_error)
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            if (generation == currentGeneration) loading = false
        }
    }

    fun requestMore() {
        if (loadingMore || nextOffset.isBlank()) return
        val requestedOffset = nextOffset
        val currentGeneration = generation
        scope.launch {
            loadingMore = true
            moreError = null
            loadMore(requestedOffset)
                .onSuccess { page ->
                    if (generation != currentGeneration) return@onSuccess
                    transactions = (transactions + page.transactions).distinctBy { it.id }
                    nextOffset = if (page.nextOffset == requestedOffset) "" else page.nextOffset
                    info = info?.copy(balance = page.balance)
                }
                .onFailure { failure ->
                    if (generation == currentGeneration) {
                        moreError = failure.userMessage(context, R.string.stars_history_error)
                    }
                }
            if (generation == currentGeneration) loadingMore = false
        }
    }

    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.stars_title), onBack) {
            IconButton(onClick = { if (!loading) reload++ }, enabled = !loading) {
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
                    item("balance") { StarsBalanceCard(stars.balance, loading) }
                    item("topup_header") { MonetizationSectionHeader(stringResource(R.string.stars_topup_title)) }
                    if (!MonetizationPayments.starTopUpSupported) {
                        item("topup_fallback") {
                            MonetizationStateCard(
                                title = stringResource(R.string.stars_topup_unavailable_title),
                                body = stringResource(R.string.stars_topup_unavailable_body),
                                tone = MonetizationTone.Warning
                            )
                        }
                    }
                    if (stars.topUpOptions.isNotEmpty()) {
                        item("topup_options") {
                            MonetizationCard {
                                stars.topUpOptions.forEachIndexed { index, option ->
                                    if (index > 0) {
                                        HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 18.dp))
                                    }
                                    StarTopUpRow(option)
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
                    items(transactions, key = { it.id }) { transaction ->
                        StarTransactionRow(transaction)
                    }
                    if (transactions.isNotEmpty() && nextOffset.isNotBlank()) {
                        item("more") {
                            TextButton(
                                onClick = ::requestMore,
                                enabled = !loadingMore,
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
private fun StarTopUpRow(option: StarTopUpOption) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 18.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.stars_topup_option_stars, formatStarAmount(option.starCount)),
            color = palette.textPrimary,
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
    }
}

@Composable
private fun StarTransactionRow(transaction: StarTransactionItem) {
    val palette = beyGramPalette()
    val positive = transaction.amount.starCount > 0 ||
        (transaction.amount.starCount == 0L && transaction.amount.nanostarCount > 0)
    val amountText = formatStarAmount(transaction.amount.starCount, transaction.amount.nanostarCount)
        .let { if (positive) "+$it" else it }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
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
    StarTransactionKind.Withdrawal -> R.string.stars_tx_withdrawal
    StarTransactionKind.Other -> R.string.stars_tx_other
}
