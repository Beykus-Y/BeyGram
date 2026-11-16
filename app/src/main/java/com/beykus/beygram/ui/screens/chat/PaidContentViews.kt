package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.design.styledShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.beykus.beygram.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MessageInvoiceInfo
import com.beykus.beygram.domain.model.MessagePaidMediaInfo
import com.beykus.beygram.domain.model.PaidContentKey
import com.beykus.beygram.domain.model.TELEGRAM_STARS_CURRENCY
import com.beykus.beygram.domain.model.formatPriceMinorUnits
import com.beykus.beygram.domain.model.formatStarAmount
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.openExternalPaymentPage
import com.beykus.beygram.ui.text.resolve
import kotlinx.coroutines.flow.StateFlow
import com.beykus.beygram.playback.LocalPlaybackAccountId

/**
 * A paid photo or video bundle. A locked bundle shows TDLib's blurred preview and the Stars price. A purchased bundle
 * shows its first item as a preview and opens all available items in the in-app viewer.
 */
@Composable
internal fun PaidMediaCard(
    paid: MessagePaidMediaInfo,
    message: ChatMessage,
    actions: PaidContentActions?,
    observeFile: ((Int) -> StateFlow<MediaFileState>)?,
    downloadFile: ((Int) -> Unit)?,
    autoDownloadFile: ((AutoDownloadRequest) -> Unit)?,
    modifier: Modifier
) {
    val palette = beyGramPalette()
    val key = message.paidContentKey()
    val owner = remember { Any() }
    val paymentState by PaidContentPaymentStore.state.collectAsState()
    Box(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().clip(styledShape(12.dp)).background(palette.surface2)) {
            if (paid.isPurchased) {
                if (observeFile != null && downloadFile != null) {
                    PurchasedGallery(paid, key, actions, observeFile, downloadFile, autoDownloadFile)
                }
            } else {
                LockedPreview(paid)
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    pluralStringResource(R.plurals.paid_media_count, paid.itemCount, paid.itemCount),
                    color = palette.textSecondary,
                    fontSize = 13.sp
                )
                if (paid.caption.isNotBlank()) {
                    Text(paid.caption, color = palette.messageText, fontSize = 15.sp)
                }
                if (paid.isPurchased) {
                    PaidLabel(stringResource(R.string.paid_media_purchased))
                } else if (actions != null) {
                    FilledTonalButton(
                        onClick = { actions.request(key, owner) },
                        enabled = !paymentState.step.isRunning
                    ) {
                        Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.paid_media_unlock, formatStarAmount(paid.starCount)))
                    }
                }
                PaidContentStatus(key, paymentState)
            }
        }
        if (actions != null) PaidContentDialogs(paymentState.step, key, owner, actions)
    }
}

/** A payment request. A paid invoice shows its receipt state, and a test invoice is marked as a test. */
@Composable
internal fun InvoiceCard(
    invoice: MessageInvoiceInfo,
    message: ChatMessage,
    actions: PaidContentActions?,
    modifier: Modifier
) {
    val palette = beyGramPalette()
    val key = message.paidContentKey()
    val owner = remember { Any() }
    val paymentState by PaidContentPaymentStore.state.collectAsState()
    val price = formatPriceMinorUnits(invoice.totalAmountMinor, invoice.currency)
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().clip(styledShape(12.dp)).background(palette.surface2).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Star, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    invoice.title.ifBlank { stringResource(R.string.paid_content_invoice) },
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (invoice.isTest) {
                    Text(
                        stringResource(R.string.paid_content_test),
                        color = palette.warningText,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(styledShape(6.dp))
                            .background(palette.warningSurface)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (invoice.description.isNotBlank()) {
                Text(invoice.description, color = palette.textSecondary, fontSize = 14.sp)
            }
            Text(price, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (invoice.isPaid) {
                PaidLabel(stringResource(R.string.paid_content_paid))
            } else if (actions != null) {
                FilledTonalButton(
                    onClick = { actions.request(key, owner) },
                    enabled = !paymentState.step.isRunning
                ) {
                    Text(stringResource(R.string.paid_content_pay, price))
                }
            }
            PaidContentStatus(key, paymentState)
        }
        if (actions != null) PaidContentDialogs(paymentState.step, key, owner, actions)
    }
}

/**
 * The album is local to this message and account. The shared viewer uses occurrence IDs, not message IDs:
 * multiple attachments belong to one message and even duplicate file IDs must remain separate pages.
 * No message actions or export handlers are provided to this read-only gallery.
 */
internal fun purchasedMediaPages(paid: MessagePaidMediaInfo): List<MediaViewerItem> =
    paid.purchasedMedia.mapIndexed { index, item ->
        MediaViewerItem(messageId = index.toLong() + 1L, media = item, caption = paid.caption)
    }

@Composable
private fun PurchasedGallery(
    paid: MessagePaidMediaInfo,
    contentKey: PaidContentKey,
    actions: PaidContentActions?,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    autoDownloadFile: ((AutoDownloadRequest) -> Unit)?
) {
    val accountId = LocalPlaybackAccountId.current
    val pages = purchasedMediaPages(paid)
    val item = pages.firstOrNull() ?: return
    // Reset the whole subtree (including remembered file observers) across account/message/album changes.
    key(accountId, contentKey, actions, pages.map { it.media.fileId }) {
        var selectedId by remember { mutableStateOf<Long?>(null) }
        MediaPreview(
            media = item.media,
            observeFile = observeFile,
            downloadFile = downloadFile,
            onOpen = { selectedId = item.messageId },
            autoDownloadFile = autoDownloadFile
        )
        selectedId?.let { selected ->
            MediaViewer(
                pages = pages,
                selectedId = selected,
                onSelect = { selectedId = it },
                observeFile = observeFile,
                downloadFile = downloadFile,
                // These page IDs are album occurrences, not Telegram message IDs. Never send them to TDLib.
                onViewed = {},
                onClose = { selectedId = null },
                canExport = false,
                confirmExport = { false },
                fullFileOnlyWhenActive = true,
                autoDownloadFile = autoDownloadFile,
                forceSecure = true
            )
        }
    }
}

/** TDLib's blurred preview of a locked bundle, or a neutral box when none was sent. */
@Composable
private fun LockedPreview(paid: MessagePaidMediaInfo) {
    val palette = beyGramPalette()
    val ratio = if (paid.previewWidth > 0 && paid.previewHeight > 0) {
        (paid.previewWidth.toFloat() / paid.previewHeight).coerceIn(0.6f, 1.8f)
    } else {
        4f / 3f
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(ratio).background(palette.surface2),
        contentAlignment = Alignment.Center
    ) {
        paid.blurredMinithumbnail?.let { MinithumbnailImage(it, Modifier.fillMaxSize()) }
        Box(Modifier.fillMaxSize().background(palette.surface2.copy(alpha = 0.35f)))
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun PaidLabel(text: String) {
    val palette = beyGramPalette()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = palette.successText, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = palette.successText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Progress and the last result for this message. The result is shown on the message it belongs to. */
@Composable
private fun PaidContentStatus(key: PaidContentKey, state: PaidContentPaymentState) {
    val palette = beyGramPalette()
    val progress = when (val step = state.step) {
        is PaidContentStep.Preparing -> if (step.key == key) stringResource(R.string.paid_content_checking) else null
        is PaidContentStep.Paying -> if (step.key == key) stringResource(R.string.paid_content_paying) else null
        else -> null
    }
    if (progress != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), color = palette.accent, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(progress, color = palette.textSecondary, fontSize = 13.sp)
        }
    }
    val notice = state.notice?.takeIf { it.key == key }
    if (notice != null) {
        val succeeded = notice.text == UiText.Res(R.string.paid_content_paid_notice)
        Text(
            notice.text.resolve(),
            color = if (succeeded) palette.successText else palette.warningText,
            fontSize = 13.sp
        )
    }
}

/** Only the bubble that started a request shows its dialog. */
@Composable
private fun PaidContentDialogs(step: PaidContentStep, key: PaidContentKey, owner: Any, actions: PaidContentActions) {
    when (step) {
        is PaidContentStep.Confirming -> {
            if (step.key == key && step.owner === owner) StarsConfirmDialog(step, actions)
        }
        is PaidContentStep.ChoosingExternal -> {
            if (step.key == key && step.owner === owner) ExternalMethodsDialog(step, actions)
        }
        else -> Unit
    }
}

/** Stars leave the balance only after this dialog's confirm button, and only for the price it shows. */
@Composable
private fun StarsConfirmDialog(step: PaidContentStep.Confirming, actions: PaidContentActions) {
    val price = formatPriceMinorUnits(step.starCount, TELEGRAM_STARS_CURRENCY)
    val balance = formatStarAmount(step.balance.starCount, step.balance.nanostarCount)
    BeyGramDialog(
        onDismissRequest = { actions.dismiss(step.key) },
        title = stringResource(R.string.paid_content_confirm_title),
        message = stringResource(R.string.paid_content_confirm_body, price, balance),
        confirmText = stringResource(R.string.paid_content_pay, price),
        onConfirm = { actions.confirm(step.key) },
        dismissText = stringResource(R.string.common_cancel),
        onDismiss = { actions.dismiss(step.key) }
    )
}

/** Real-money forms open only the validated pages that TDLib returned. Card fields are never requested here. */
@Composable
private fun ExternalMethodsDialog(step: PaidContentStep.ChoosingExternal, actions: PaidContentActions) {
    val context = LocalContext.current
    BeyGramDialog(
        onDismissRequest = { actions.dismiss(step.key) },
        title = stringResource(R.string.paid_content_external_title),
        content = {
            Column {
                Text(stringResource(R.string.paid_content_external_body))
                step.methods.forEach { method ->
                    TextButton(
                        onClick = {
                            val opened = openExternalPaymentPage(context, method.url.value)
                            actions.closeExternal(
                                step.key,
                                if (opened) null else UiText.Res(R.string.paid_content_open_failed)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(method.title.ifBlank { stringResource(R.string.paid_content_open_page) })
                    }
                }
            }
        },
        confirmText = stringResource(R.string.common_cancel),
        onConfirm = { actions.dismiss(step.key) }
    )
}

/** A read or a payment owns the store until it finishes, so every other paid button waits. */
private val PaidContentStep.isRunning: Boolean
    get() = this is PaidContentStep.Preparing || this is PaidContentStep.Paying
