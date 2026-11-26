package com.beykus.beygram.ui.screens.chat

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.theme.LocalMessageLineSpacing
import com.beykus.beygram.ui.theme.LocalMessageTextSize
import kotlin.math.ceil
import kotlin.math.floor

/** Media, memory labels, translations and failed sends keep a distinct metadata row. */
internal fun useLastLineTextMetadata(message: ChatMessage, hasTranslation: Boolean): Boolean =
    message.text.isNotBlank() &&
        message.media == null &&
        message.special == null &&
        message.linkPreview == null &&
        message.expiredMediaKind == null &&
        message.inlineKeyboard.isEmpty() &&
        message.memory == null &&
        message.sendState != MessageSendState.Failed &&
        !hasTranslation

internal fun lastLineMetadataFits(
    lastLineWidth: Int,
    metaWidth: Int,
    gap: Int,
    maxWidth: Int,
): Boolean = lastLineWidth >= 0 && metaWidth > 0 && gap >= 0 &&
    lastLineWidth.toLong() + gap + metaWidth <= maxWidth.toLong()

/**
 * Place actual message metadata beside the measured final text line when it fits.
 * The message remains a single accessible Text node; no invisible spaces or placeholder characters are appended.
 * Mixed paragraph/layout directions and oversized metadata use the next row.
 */
@Composable
internal fun PlainMessageWithMeta(
    text: String,
    modifier: Modifier = Modifier,
    meta: @Composable () -> Unit,
) {
    val size = LocalMessageTextSize.current
    val spacing = LocalMessageLineSpacing.current
    val color = beyGramPalette().messageText
    MeasuredMessageTextWithMeta(
        modifier = modifier,
        text = { onTextLayout ->
            Text(
                text, color = color, fontSize = size, fontFamily = FontFamily.Default,
                lineHeight = (size.value * spacing).sp, onTextLayout = onTextLayout
            )
        },
        meta = meta
    )
}

private class MessageTextMeasurement {
    var result: TextLayoutResult? = null
}

/** Let the actual Text node measure once, including spans, fallback fonts and inline placeholders. */
@Composable
internal fun MeasuredMessageTextWithMeta(
    modifier: Modifier,
    text: @Composable ((TextLayoutResult) -> Unit) -> Unit,
    meta: @Composable () -> Unit
) {
    val measurement = remember { MessageTextMeasurement() }
    Layout(
        modifier = modifier,
        content = {
            text { measurement.result = it }
            androidx.compose.foundation.layout.Box { meta() }
        }
    ) { children, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val body = children[0].measure(loose)
        val metadata = children[1].measure(loose)
        val result = measurement.result?.takeIf {
            it.lineCount > 0 && it.layoutInput.text.isNotEmpty() && it.size == IntSize(body.width, body.height)
        }
        val bodyBaseline = body[LastBaseline]
        val metaBaseline = metadata[LastBaseline]
        val alignedTop = if (bodyBaseline != AlignmentLine.Unspecified && metaBaseline != AlignmentLine.Unspecified) {
            bodyBaseline - metaBaseline
        } else -1
        val gap = 4.dp.roundToPx()
        val lastLineWidth = result?.let {
            val line = it.lineCount - 1
            val start = it.getLineStart(line).coerceAtMost(it.layoutInput.text.length - 1)
            val direction = if (layoutDirection == LayoutDirection.Ltr) ResolvedTextDirection.Ltr else ResolvedTextDirection.Rtl
            if (it.getParagraphDirection(start) != direction || alignedTop < it.getLineTop(line).toInt()) null
            else if (layoutDirection == LayoutDirection.Ltr) ceil(it.getLineRight(line)).toInt()
            else body.width - floor(it.getLineLeft(line)).toInt()
        }
        val inline = lastLineWidth != null && lastLineMetadataFits(lastLineWidth, metadata.width, gap, loose.maxWidth)
        val desiredWidth = if (inline) maxOf(body.width, requireNotNull(lastLineWidth) + gap + metadata.width)
            else maxOf(body.width, metadata.width)
        val width = desiredWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val metaTop = if (inline) alignedTop else body.height
        val height = maxOf(body.height, metaTop + metadata.height).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            body.placeRelative(0, 0)
            metadata.placeRelative(width - metadata.width, metaTop)
        }
    }
}
