package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.ui.components.beyGramPalette

/** Отступ плашки времени от края фото, когда время стоит поверх него. */
private val OverlayInset = 6.dp

/**
 * Пузырь из трёх частей: [body] — содержимое сверху, [meta] — время и статус в правом нижнем углу,
 * [footer] — кнопки под пузырём. Ширина пузыря — по самой широкой части, поэтому время всегда у правого края.
 * При [overlayMeta] время кладётся поверх нижнего правого угла [body], а не отдельной строкой.
 */
@Composable
internal fun BubbleLayout(
    modifier: Modifier,
    overlayMeta: Boolean,
    body: @Composable () -> Unit,
    meta: @Composable () -> Unit,
    footer: @Composable () -> Unit = {}
) {
    // Каждая часть обёрнута в Box, чтобы пустая часть всё равно давала одно измеряемое место.
    Layout(content = { Box { body() }; Box { meta() }; Box { footer() } }, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val bodyPlaceable = measurables[0].measure(loose)
        val metaPlaceable = measurables[1].measure(loose)
        val footerPlaceable = measurables[2].measure(loose)
        val width = constraints.constrainWidth(maxOf(bodyPlaceable.width, metaPlaceable.width, footerPlaceable.width))
        val inset = OverlayInset.roundToPx()
        val metaTop = if (overlayMeta) bodyPlaceable.height - metaPlaceable.height - inset else bodyPlaceable.height
        val footerTop = bodyPlaceable.height + (if (overlayMeta) 0 else metaPlaceable.height)
        val height = footerTop + footerPlaceable.height
        val metaLeft = width - metaPlaceable.width - (if (overlayMeta) inset else 0)
        layout(width, height) {
            bodyPlaceable.placeRelative(0, 0)
            metaPlaceable.placeRelative(metaLeft, metaTop)
            footerPlaceable.placeRelative(0, footerTop)
        }
    }
}

/** Время, статус отправки и кнопка повтора. При [overlay] — белым на тёмной плашке поверх фото. */
@Composable
internal fun BubbleMeta(message: ChatMessage, isRead: Boolean, retryAvailable: Boolean, overlay: Boolean, onRetry: () -> Unit) {
    val palette = beyGramPalette()
    val failed = message.sendState == MessageSendState.Failed
    val status = if (message.isOutgoing && failed) " · " + stringResource(R.string.chat_status_not_sent) else ""
    val textColor = when {
        overlay -> Color.White
        failed -> palette.warningText
        else -> palette.textSecondary
    }
    val modifier = if (overlay) Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
        else Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text((if (message.isEdited) stringResource(R.string.chat_edited) + " · " else "") + message.time + status,
            color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        if (retryAvailable) Text(" · " + stringResource(R.string.chat_retry_send), color = palette.warningText, fontSize = 12.sp,
            modifier = Modifier.clickable(onClick = onRetry))
        if (message.isOutgoing && message.sendState == MessageSendState.Sending) {
            Spacer(Modifier.width(3.dp))
            Icon(Icons.Rounded.Schedule, stringResource(R.string.chat_sending_description), tint = if (overlay) Color.White else palette.textSecondary,
                modifier = Modifier.size(14.dp))
        }
        if (message.isOutgoing && message.sendState == MessageSendState.Sent) {
            Spacer(Modifier.width(3.dp))
            Icon(if (isRead) Icons.Rounded.DoneAll else Icons.Rounded.Done,
                if (isRead) stringResource(R.string.chat_read_description) else stringResource(R.string.chat_sent_description),
                tint = if (overlay) Color.White else palette.accent, modifier = Modifier.size(16.dp))
        }
    }
}
