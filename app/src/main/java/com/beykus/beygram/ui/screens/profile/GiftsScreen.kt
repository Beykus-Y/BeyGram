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
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.GiftBlockReason
import com.beykus.beygram.domain.model.GiftCatalogItem
import com.beykus.beygram.domain.model.GiftCollectionItem
import com.beykus.beygram.domain.model.GiftSendEvent
import com.beykus.beygram.domain.model.GiftSendOutcome
import com.beykus.beygram.domain.model.GiftSendStep
import com.beykus.beygram.domain.model.GiftsInfo
import com.beykus.beygram.domain.model.MonetizationUnavailableException
import com.beykus.beygram.domain.model.ReceivedGiftItem
import com.beykus.beygram.domain.model.StarBalance
import com.beykus.beygram.domain.model.formatStarAmount
import com.beykus.beygram.domain.model.giftBlockReason
import com.beykus.beygram.domain.model.next
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.resolve
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun GiftsScreen(
    title: String,
    profileTitle: String,
    isSelf: Boolean,
    load: suspend () -> Result<GiftsInfo>,
    send: suspend (giftId: Long) -> GiftSendOutcome,
    onOpenStars: () -> Unit,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<GiftsInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var unsupported by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var step by remember { mutableStateOf<GiftSendStep>(GiftSendStep.Idle) }
    var selectedGift by remember { mutableStateOf<GiftCatalogItem?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeIsError by remember { mutableStateOf(false) }
    val sending = step is GiftSendStep.Sending

    BackHandler { if (!sending) onBack() }
    LaunchedEffect(reload) {
        loading = true
        error = null
        unsupported = false
        try {
            load()
                .onSuccess { info = it }
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
        if (sending) return
        notice = null
        selectedGift = gift
        step = step.next(GiftSendEvent.Select(gift.id))
    }

    fun confirmGift() {
        if (step !is GiftSendStep.Confirming) return
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
        if (blockReason != null) {
            notice = context.getString(giftBlockMessage(blockReason))
            noticeIsError = true
            step = step.next(GiftSendEvent.Dismiss)
            selectedGift = null
            return
        }
        step = nextStep
        scope.launch {
            val outcome = try {
                send(gift.id)
            } catch (_: TimeoutCancellationException) {
                GiftSendOutcome.Uncertain
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Never present an unexpected transport failure as a safe retry.
                GiftSendOutcome.Uncertain
            }
            when (outcome) {
                GiftSendOutcome.Sent -> {
                    notice = context.getString(R.string.gifts_send_success)
                    noticeIsError = false
                    reload++
                }
                is GiftSendOutcome.Blocked -> {
                    notice = outcome.reason.resolve(context)
                    noticeIsError = true
                }
                is GiftSendOutcome.Failed -> {
                    notice = outcome.reason.resolve(context)
                    noticeIsError = true
                }
                GiftSendOutcome.Uncertain -> {
                    notice = context.getString(R.string.gifts_send_uncertain)
                    noticeIsError = true
                    reload++
                }
            }
            step = step.next(
                when (outcome) {
                    GiftSendOutcome.Sent -> GiftSendEvent.Succeeded
                    is GiftSendOutcome.Blocked, is GiftSendOutcome.Failed -> GiftSendEvent.Failed
                    GiftSendOutcome.Uncertain -> GiftSendEvent.Uncertain
                }
            )
            selectedGift = null
        }
    }

    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(title, onBack) {
            IconButton(onClick = { if (!loading && !sending) reload++ }, enabled = !loading && !sending) {
                Icon(Icons.Rounded.Refresh, stringResource(R.string.common_retry), tint = palette.textPrimary)
            }
        }
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
            info == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.accent)
            }
            else -> {
                val gifts = info!!
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
                    notice?.let { message ->
                        item("notice") {
                            Text(
                                message,
                                color = if (noticeIsError) palette.danger else palette.successText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
                                    .errorAnnouncement()
                            )
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
                    if (isSelf) {
                        item("received_header") {
                            MonetizationSectionHeader(stringResource(R.string.gifts_received_title))
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
                        } else if (gifts.received.isEmpty()) {
                            item("received_empty") {
                                Text(
                                    stringResource(R.string.gifts_received_empty),
                                    color = palette.textSecondary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
                                )
                            }
                        } else {
                            items(gifts.received, key = { it.id }) { gift -> ReceivedGiftRow(gift) }
                        }
                        item("collections_header") {
                            MonetizationSectionHeader(stringResource(R.string.gifts_collections_title))
                        }
                        if (gifts.collectionsUnavailable) {
                            item("collections_error") {
                                Text(
                                    stringResource(R.string.gifts_collections_error),
                                    color = palette.danger,
                                    fontSize = 14.sp,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
                                        .errorAnnouncement()
                                )
                            }
                        } else if (gifts.collections.isEmpty()) {
                            item("collections_empty") {
                                Text(
                                    stringResource(R.string.gifts_collections_empty),
                                    color = palette.textSecondary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
                                )
                            }
                        } else {
                            items(gifts.collections, key = { it.id }) { collection -> GiftCollectionRow(collection) }
                        }
                        item("catalog_header") {
                            MonetizationSectionHeader(stringResource(R.string.gifts_catalog_title))
                        }
                        item("catalog_own_hint") {
                            Text(
                                stringResource(R.string.gifts_catalog_own_hint),
                                color = palette.textSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
                            )
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
                            items(gifts.catalog, key = { it.id }) { gift ->
                                GiftCatalogRow(
                                    gift = gift,
                                    blockReason = null,
                                    enabled = false,
                                    showSend = false,
                                    onSend = {}
                                )
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
                            items(gifts.catalog, key = { it.id }) { gift ->
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
                                    enabled = !sending,
                                    onSend = { chooseGift(gift) }
                                )
                            }
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

    val dialogGift = selectedGift
    if (dialogGift != null && (step is GiftSendStep.Confirming || step is GiftSendStep.Sending)) {
        AlertDialog(
            onDismissRequest = { dismissDialog() },
            title = { Text(stringResource(R.string.gifts_send_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(dialogGift.emoji.ifBlank { "🎁" }, fontSize = 30.sp)
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
            confirmButton = {
                TextButton(
                    onClick = ::confirmGift,
                    enabled = !sending,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        if (sending) stringResource(R.string.gifts_sending)
                        else stringResource(R.string.gifts_send_confirm, formatStarAmount(dialogGift.starCount))
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = ::dismissDialog, enabled = !sending, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
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

@Composable
private fun GiftCatalogRow(
    gift: GiftCatalogItem,
    blockReason: GiftBlockReason?,
    enabled: Boolean,
    showSend: Boolean = true,
    onSend: () -> Unit
) {
    val palette = beyGramPalette()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(gift.emoji.ifBlank { "🎁" }, fontSize = 32.sp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    stringResource(R.string.gifts_cost, formatStarAmount(gift.starCount)),
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
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
                blockReason?.let { reason ->
                    Text(
                        stringResource(giftBlockMessage(reason)),
                        color = palette.danger,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp).errorAnnouncement()
                    )
                }
            }
            if (blockReason == null && showSend) {
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
    }
}

@Composable
private fun ReceivedGiftRow(gift: ReceivedGiftItem) {
    val palette = beyGramPalette()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(gift.emoji.ifBlank { "🎁" }, fontSize = 28.sp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    gift.name.ifBlank { stringResource(R.string.gifts_received_title) },
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
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
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
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
