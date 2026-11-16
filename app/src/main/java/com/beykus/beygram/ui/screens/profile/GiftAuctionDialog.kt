package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.resolve
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.DateFormat
import java.util.Date

internal data class GiftAuctionOperation(val inFlight: Boolean, val outcome: GiftAuctionOutcome? = null)
internal object GiftAuctionStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow<Map<String, GiftAuctionOperation>>(emptyMap())
    val states = mutable.asStateFlow()
    fun start(account: String, quote: GiftAuctionQuote, total: Long, submit: suspend () -> GiftAuctionOutcome): Boolean {
        if (mutable.value[account] != null || auctionBidDebit(quote, total) == null) return false
        mutable.value = mutable.value + (account to GiftAuctionOperation(true))
        scope.launch {
            val result = try { submit() } catch (_: Exception) { GiftAuctionOutcome.Uncertain }
            mutable.value = mutable.value + (account to GiftAuctionOperation(false, result))
        }
        return true
    }
    fun acknowledge(account: String) {
        if (mutable.value[account]?.inFlight == false) mutable.value = mutable.value - account
    }
}

@Composable
internal fun auctionOutcomeText(outcome: GiftAuctionOutcome?): String = when (outcome) {
    GiftAuctionOutcome.Submitted -> stringResource(R.string.gift_auction_submitted)
    GiftAuctionOutcome.Changed -> stringResource(R.string.gift_auction_changed)
    GiftAuctionOutcome.Uncertain -> stringResource(R.string.gift_auction_uncertain)
    is GiftAuctionOutcome.Failed -> outcome.reason.resolve(LocalContext.current)
    null -> stringResource(R.string.gifts_sending)
}

@Composable
internal fun GiftAuctionDialog(
    accountId: String, profileId: Long, profileTitle: String, isSelf: Boolean, catalog: GiftCatalogItem,
    load: suspend (String, Long, Boolean) -> Result<GiftAuctionQuote>,
    submit: suspend (GiftAuctionQuote, Long, Boolean, Long) -> GiftAuctionOutcome,
    loadHistory: suspend (Long) -> Result<List<GiftAuctionWin>>,
    purchaseEnabled: Boolean, onDismiss: () -> Unit
) {
    val palette = beyGramPalette()
    var quote by remember { mutableStateOf<GiftAuctionQuote?>(null) }
    var amount by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    val states by GiftAuctionStore.states.collectAsState()
    val operation = states[accountId]
    val current = quote
    val total = amount.toLongOrNull()
    val debit = if (current != null && total != null) auctionBidDebit(current, total) else null
    fun refresh() { if (!loading) { loading = true; quote = null; revision++ } }
    LaunchedEffect(revision) {
        loading = true
        error = false
        quote = null
        try {
            val id = catalog.auctionId
            if (id.isNullOrBlank()) { error = true; return@LaunchedEffect }
            load(id, profileId, isSelf).onSuccess {
                if (it.giftId != catalog.id || it.auctionId != id) error = true
                else { quote = it; amount = it.requiredMinimum.takeIf { value -> value > 0L }?.toString().orEmpty() }
            }.onFailure { error = true }
        } finally { loading = false }
    }

    if (operation != null) {
        val busy = operation.inFlight
        BeyGramDialog(
            onDismissRequest = { if (!busy) onDismiss() },
            title = stringResource(R.string.gift_auction_title),
            message = auctionOutcomeText(operation.outcome),
            confirmText = stringResource(R.string.common_close),
            confirmEnabled = !busy,
            onConfirm = { GiftAuctionStore.acknowledge(accountId); onDismiss() },
            dismissOnBackPress = !busy, dismissOnClickOutside = !busy
        )
    } else if (historyOpen && current != null) {
        GiftAuctionHistoryDialog(current.giftId, loadHistory, onDismiss = { historyOpen = false })
    } else if (confirm && current != null && total != null && debit != null) {
        BeyGramDialog(
            onDismissRequest = { confirm = false },
            title = stringResource(R.string.gift_auction_title),
            message = stringResource(R.string.gift_auction_confirm, formatStarAmount(total), profileTitle) +
                "\n\n" + stringResource(R.string.gift_auction_debit, formatStarAmount(debit), formatStarAmount(total)) +
                if (current.ownBid == null) "\n\n" + stringResource(R.string.gifts_hide_name_hint) else "",
            confirmText = stringResource(if (current.ownBid == null) R.string.gift_auction_place else R.string.gift_auction_raise),
            confirmEnabled = purchaseEnabled,
            onConfirm = {
                if (confirm && purchaseEnabled && GiftAuctionStore.start(accountId, current, total) {
                        submit(current, profileId, isSelf, total)
                    }) confirm = false
            },
            dismissText = stringResource(R.string.common_cancel),
            onDismiss = { confirm = false }
        )
    } else {
        BeyGramDialog(
            onDismissRequest = onDismiss,
            title = stringResource(R.string.gift_auction_title),
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(catalog.emoji.ifBlank { "🎁" })
                    if (loading) CircularProgressIndicator(color = palette.accent)
                    if (error) Text(stringResource(R.string.gifts_load_error), color = palette.danger)
                    current?.let { q ->
                        Text(stringResource(R.string.gift_auction_won, q.acquired))
                        if (q.acquired > 0) TextButton(onClick = { historyOpen = true }) {
                            Text(stringResource(R.string.gift_auction_history))
                        }
                        if (q.finished) {
                            Text(stringResource(R.string.gift_auction_finished))
                            q.averagePrice?.let { Text(stringResource(R.string.gift_auction_average, formatStarAmount(it))) }
                        } else {
                            if (q.serverTime < q.start) Text(stringResource(R.string.gift_auction_starts, dateLabel(q.start)))
                            else {
                                Text(stringResource(R.string.gift_auction_round, q.round, q.roundCount))
                                Text(stringResource(R.string.gift_auction_ends, dateLabel(q.roundEnd)))
                            }
                            Text(stringResource(R.string.gift_auction_left, q.remaining))
                            Text(stringResource(R.string.gift_auction_minimum, formatStarAmount(q.requiredMinimum)))
                            q.ownBid?.let { bid ->
                                Text(stringResource(R.string.gift_auction_own_bid, formatStarAmount(bid.stars)))
                                if (bid.returned) Text(stringResource(R.string.gift_auction_returned), color = palette.warningText)
                                else if (bid.recipientId != q.recipientId) Text(stringResource(R.string.gift_auction_other_recipient), color = palette.warningText)
                            }
                            q.balance?.let { Text(stringResource(R.string.gifts_balance, formatStarAmount(it.starCount, it.nanostarCount))) }
                            q.ranks.take(5).forEach { Text(stringResource(R.string.gift_auction_rank, it.position, formatStarAmount(it.stars))) }
                            OutlinedTextField(
                                value = amount,
                                onValueChange = { if (it.length <= 16 && it.all { ch -> ch in '0'..'9' }) amount = it },
                                label = { Text(stringResource(R.string.gift_auction_amount)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                            if (debit == null) Text(stringResource(R.string.gift_auction_unavailable), color = palette.textSecondary)
                        }
                    }
                    TextButton(onClick = ::refresh, enabled = !loading) { Text(stringResource(R.string.common_retry)) }
                }
            },
            confirmText = stringResource(if (current?.ownBid == null) R.string.gift_auction_place else R.string.gift_auction_raise),
            confirmEnabled = purchaseEnabled && !loading && debit != null,
            onConfirm = { if (debit != null && purchaseEnabled) confirm = true },
            dismissText = stringResource(R.string.common_close),
            onDismiss = onDismiss
        )
    }
}

private fun dateLabel(seconds: Int): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    .format(Date(seconds.toLong() * 1000L))

/** Separate read: history failures must not prevent browsing the auction or confirming a bid. */
@Composable
private fun GiftAuctionHistoryDialog(
    giftId: Long, load: suspend (Long) -> Result<List<GiftAuctionWin>>, onDismiss: () -> Unit
) {
    val palette = beyGramPalette()
    var rows by remember(giftId) { mutableStateOf<List<GiftAuctionWin>?>(null) }
    var failed by remember(giftId) { mutableStateOf(false) }
    var revision by remember(giftId) { mutableIntStateOf(0) }
    LaunchedEffect(giftId, revision) {
        failed = false
        rows = null
        load(giftId).onSuccess { rows = it }.onFailure { failed = true }
    }
    BeyGramDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.gift_auction_history),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (failed) {
                    Text(stringResource(R.string.gift_auction_history_error), color = palette.danger)
                    TextButton(onClick = { failed = false; revision++ }) { Text(stringResource(R.string.common_retry)) }
                } else if (rows == null) {
                    CircularProgressIndicator(color = palette.accent)
                } else if (rows.orEmpty().isEmpty()) {
                    Text(stringResource(R.string.gift_auction_history_empty))
                } else {
                    rows.orEmpty().forEach { win ->
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(win.number?.let { stringResource(R.string.gift_auction_history_number, it) }
                                ?: stringResource(R.string.gift_auction_history_number_pending), color = palette.textPrimary)
                            Text(stringResource(R.string.gifts_cost, formatStarAmount(win.stars)), color = palette.accentInk)
                            Text(stringResource(R.string.gift_auction_history_round, win.round, win.position))
                            Text(dateLabel(win.date))
                            Text(stringResource(R.string.gift_auction_history_recipient,
                                win.receiver ?: stringResource(R.string.gift_auction_history_unknown_recipient)))
                            if (win.text.isNotBlank()) Text(win.text)
                            if (win.isPrivate) Text(stringResource(R.string.gifts_private), color = palette.textSecondary)
                        }
                    }
                }
            }
        },
        confirmText = stringResource(R.string.common_close),
        onConfirm = onDismiss
    )
}
