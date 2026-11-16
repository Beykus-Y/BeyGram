package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.GiftCatalogItem
import com.beykus.beygram.domain.model.ResaleGiftItem
import com.beykus.beygram.domain.model.ResaleGiftsPage
import com.beykus.beygram.domain.model.StarBalance
import com.beykus.beygram.domain.model.formatStarAmount
import com.beykus.beygram.domain.model.mergeResaleGifts
import com.beykus.beygram.domain.model.nextReceivedGiftsOffset
import com.beykus.beygram.domain.model.resaleCanBuy
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.beyGramPalette

@Composable
internal fun GiftResaleDialog(
    catalog: GiftCatalogItem,
    profileTitle: String,
    balance: StarBalance?,
    purchaseEnabled: Boolean,
    load: suspend (String) -> Result<ResaleGiftsPage>,
    onDismiss: () -> Unit,
    onBuy: (ResaleGiftItem) -> Unit
) {
    val palette = beyGramPalette()
    var rows by remember { mutableStateOf(emptyList<ResaleGiftItem>()) }
    var offset by remember { mutableStateOf("") }
    var nextOffset by remember { mutableStateOf("") }
    var request by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ResaleGiftItem?>(null) }
    LaunchedEffect(request) {
        val requested = offset
        loading = true
        failed = false
        try {
            load(requested).onSuccess { page ->
                rows = if (requested.isEmpty()) page.items else mergeResaleGifts(rows, page.items)
                nextOffset = nextReceivedGiftsOffset(requested, page.nextOffset)
            }.onFailure { failed = true }
        } finally {
            loading = false
        }
    }

    val chosen = selected
    if (chosen != null) {
        val price = chosen.starCount
        BeyGramDialog(
            onDismissRequest = { selected = null },
            title = stringResource(R.string.gifts_resale_title),
            message = stringResource(
                R.string.gifts_resale_confirm,
                chosen.title.ifBlank { chosen.name } + " #" + chosen.number,
                profileTitle,
                formatStarAmount(price ?: 0L),
                balance?.let { formatStarAmount(it.starCount, it.nanostarCount) }.orEmpty()
            ),
            confirmText = stringResource(R.string.gifts_resale_buy, formatStarAmount(price ?: 0L)),
            confirmEnabled = purchaseEnabled && resaleCanBuy(chosen, balance),
            onConfirm = {
                if (selected === chosen && purchaseEnabled && resaleCanBuy(chosen, balance)) {
                    selected = null
                    onBuy(chosen)
                }
            },
            dismissText = stringResource(R.string.common_cancel),
            onDismiss = { selected = null }
        )
    } else {
        BeyGramDialog(
            onDismissRequest = onDismiss,
            title = catalog.resaleTitle.ifBlank { stringResource(R.string.gifts_resale_market) },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { listing ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(listing.emoji.ifBlank { "🎁" }, fontSize = 28.sp)
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(listing.title.ifBlank { listing.name } + " #" + listing.number, color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                                listing.starCount?.let {
                                    Text(stringResource(R.string.gifts_cost, formatStarAmount(it)), color = palette.textSecondary, fontSize = 13.sp)
                                } ?: Text(stringResource(R.string.gifts_resale_ton_unavailable), color = palette.textSecondary, fontSize = 13.sp)
                            }
                            if (purchaseEnabled && resaleCanBuy(listing, balance)) {
                                TextButton(onClick = { selected = listing }, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(stringResource(R.string.gifts_resale_buy, formatStarAmount(listing.starCount!!)))
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                    if (loading) CircularProgressIndicator(color = palette.accent)
                    if (failed) {
                        Text(stringResource(R.string.gifts_load_error), color = palette.danger)
                        TextButton(onClick = { loading = true; request++ }) { Text(stringResource(R.string.common_retry)) }
                    } else if (!loading && rows.isEmpty() && nextOffset.isEmpty()) {
                        Text(stringResource(R.string.gifts_resale_empty), color = palette.textSecondary)
                    }
                    if (!loading && !failed && nextOffset.isNotEmpty()) {
                        TextButton(onClick = { loading = true; offset = nextOffset; request++ }) {
                            Text(stringResource(R.string.gifts_received_more))
                        }
                    }
                }
            },
            confirmText = stringResource(R.string.common_close),
            onConfirm = onDismiss
        )
    }
}
