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

internal data class GiftListingOperation(
    val receivedId: String,
    val inFlight: Boolean,
    val outcome: GiftListingOutcome? = null
)

/** Account-scoped; leaving a dialog does not cancel a dispatched listing operation. */
internal object GiftListingStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow<Map<String, GiftListingOperation>>(emptyMap())
    val state = mutable.asStateFlow()

    fun start(account: String, quote: GiftListingQuote, price: Long?, update: suspend () -> GiftListingOutcome): Boolean {
        if (mutable.value[account] != null || !giftListingAllowed(quote, price)) return false
        mutable.value = mutable.value + (account to GiftListingOperation(quote.receivedId, true))
        scope.launch {
            val outcome = try { update() } catch (_: Exception) { GiftListingOutcome.Uncertain }
            mutable.value = mutable.value + (account to GiftListingOperation(quote.receivedId, false, outcome))
        }
        return true
    }

    fun acknowledge(account: String) {
        if (mutable.value[account]?.inFlight == false) mutable.value = mutable.value - account
    }
}

@Composable
internal fun GiftListingDialog(
    accountId: String,
    gift: ReceivedGiftItem,
    load: suspend (String) -> Result<GiftListingQuote>,
    update: suspend (GiftListingQuote, Long?) -> GiftListingOutcome,
    onDismiss: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    var quote by remember { mutableStateOf<GiftListingQuote?>(null) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    val operations by GiftListingStore.state.collectAsState()
    val operation = operations[accountId]
    val current = quote
    val price = input.toLongOrNull()
    val proposed = if (remove) null else price
    val busy = operation?.inFlight == true
    LaunchedEffect(revision) {
        quote = null
        loading = true
        error = false
        try {
            load(gift.id).onSuccess { loaded ->
                quote = loaded
                input = loaded.currentStars?.toString().orEmpty()
            }.onFailure { error = true }
        } finally { loading = false }
    }
    if (operation != null) {
        val outcome = operation.outcome
        BeyGramDialog(
            onDismissRequest = { if (!busy) onDismiss() },
            title = stringResource(R.string.gift_listing_manage),
            message = when (outcome) {
                GiftListingOutcome.Updated -> stringResource(R.string.gift_listing_updated)
                GiftListingOutcome.Changed -> stringResource(R.string.gift_listing_changed)
                GiftListingOutcome.Uncertain -> stringResource(R.string.gift_listing_uncertain)
                is GiftListingOutcome.Failed -> outcome.reason.resolve(context)
                null -> stringResource(R.string.gifts_sending)
            },
            confirmText = stringResource(R.string.common_close),
            confirmEnabled = !busy,
            onConfirm = { GiftListingStore.acknowledge(accountId); onDismiss() },
            dismissOnBackPress = !busy,
            dismissOnClickOutside = !busy
        )
    } else if (confirm && current != null) {
        BeyGramDialog(
            onDismissRequest = { confirm = false },
            title = stringResource(R.string.gift_listing_title),
            message = if (remove) stringResource(R.string.gift_listing_remove_confirm)
                else stringResource(R.string.gift_listing_confirm, formatStarAmount(proposed ?: 0L)),
            confirmText = if (remove) stringResource(R.string.gift_listing_remove)
                else stringResource(R.string.gift_listing_submit, formatStarAmount(proposed ?: 0L)),
            confirmEnabled = (remove || proposed != null) && giftListingAllowed(current, proposed),
            onConfirm = {
                if (confirm && (remove || proposed != null) && GiftListingStore.start(accountId, current, proposed) {
                        update(current, proposed)
                    }) confirm = false
            },
            dismissText = stringResource(R.string.common_cancel),
            onDismiss = { confirm = false }
        )
    } else {
        val enabled = current != null && price != null && giftListingAllowed(current, price)
        BeyGramDialog(
            onDismissRequest = onDismiss,
            title = gift.name.ifBlank { stringResource(R.string.gift_listing_title) },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (loading) CircularProgressIndicator(color = palette.accent)
                    if (error) {
                        Text(stringResource(R.string.gifts_load_error), color = palette.danger)
                        TextButton(onClick = { revision++ }) { Text(stringResource(R.string.common_retry)) }
                    }
                    current?.let { q ->
                        q.currentStars?.let {
                            Text(stringResource(R.string.gift_listing_current, formatStarAmount(it)))
                        }
                        if (!q.eligible || q.tonOnly || q.minimum <= 0L || q.maximum < q.minimum || q.earningsPerMille !in 0L..1000L) {
                            Text(stringResource(R.string.gift_listing_unavailable), color = palette.textSecondary)
                        } else {
                            OutlinedTextField(
                                value = input,
                                onValueChange = { if (it.length <= 19 && it.all { ch -> ch in '0'..'9' }) input = it },
                                label = { Text(stringResource(R.string.gift_listing_price)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(stringResource(R.string.gift_listing_range, formatStarAmount(q.minimum), formatStarAmount(q.maximum)))
                            price?.let { giftListingProceeds(it, q.earningsPerMille) }?.let {
                                Text(stringResource(R.string.gift_listing_proceeds, formatStarAmount(it)))
                            }
                        }
                        if (giftListingAllowed(q, null)) TextButton(onClick = { remove = true; confirm = true }) {
                            Text(stringResource(R.string.gift_listing_remove))
                        }
                    }
                }
            },
            confirmText = stringResource(R.string.gift_listing_title),
            confirmEnabled = enabled,
            onConfirm = { if (enabled) { remove = false; confirm = true } },
            dismissText = stringResource(R.string.common_cancel),
            onDismiss = onDismiss
        )
    }
}
