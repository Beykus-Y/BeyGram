package com.beykus.beygram.ui.screens.chat

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.theme.LocalMessageLineSpacing
import com.beykus.beygram.ui.theme.LocalMessageTextSize
import kotlin.math.ceil

/** Rich entities, media, memory labels and non-final send states keep the existing renderer and metadata row. */
internal fun useLastLineTextMetadata(message: ChatMessage, hasTranslation: Boolean): Boolean =
    message.text.isNotBlank() &&
        message.entities.isEmpty() &&
        message.media == null &&
        message.special == null &&
        message.linkPreview == null &&
        message.expiredMediaKind == null &&
        message.inlineKeyboard.isEmpty() &&
        message.memory == null &&
        message.sendState == MessageSendState.Sent &&
        !hasTranslation

internal fun lastLineMetadataFits(
    lastLineRight: Int,
    metaWidth: Int,
    gap: Int,
    maxWidth: Int,
): Boolean = lastLineRight >= 0 && metaWidth > 0 && gap >= 0 &&
    lastLineRight.toLong() + gap + metaWidth <= maxWidth.toLong()

/**
 * Place actual message metadata beside the measured final text line when it fits.
 * The message remains a single accessible Text node; no invisible spaces or placeholder characters are appended.
 * RTL paragraphs and metadata taller than the final line keep a separate row rather than risk overlap.
 */
@Composable
internal fun PlainMessageWithMeta(
    text: String,
    modifier: Modifier = Modifier,
    meta: @Composable () -> Unit,
) {
    val size = LocalMessageTextSize.current
    val spacing = LocalMessageLineSpacing.current
    val style = LocalTextStyle.current.merge(TextStyle(
        fontSize = size,
        fontFamily = FontFamily.Default,
        lineHeight = (size.value * spacing).sp,
        color = beyGramPalette().messageText,
    ))
    val measurer = rememberTextMeasurer()
    Layout(modifier = modifier, content = { Text(text, style = style); androidx.compose.foundation.layout.Box { meta() } }) { children, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val textPlaceable = children[0].measure(loose)
        val metaPlaceable = children[1].measure(loose)
        val result = measurer.measure(AnnotatedString(text), style = style, constraints = loose)
        val line = result.lineCount - 1
        val gap = 4.dp.roundToPx()
        val lastRight = ceil(result.getLineRight(line)).toInt().coerceAtLeast(0)
        val metaBaseline = metaPlaceable[LastBaseline]
        val textBaseline = textPlaceable[LastBaseline]
        val top = if (metaBaseline != AlignmentLine.Unspecified && textBaseline != AlignmentLine.Unspecified)
            textBaseline - metaBaseline else -1
        val paragraphLtr = result.getParagraphDirection(result.getLineStart(line).coerceAtMost(text.lastIndex)) ==
            ResolvedTextDirection.Ltr
        val inline = layoutDirection == LayoutDirection.Ltr && paragraphLtr &&
            result.size == androidx.compose.ui.unit.IntSize(textPlaceable.width, textPlaceable.height) &&
            top >= result.getLineTop(line).toInt() &&
            lastLineMetadataFits(lastRight, metaPlaceable.width, gap, loose.maxWidth)
        val desiredWidth = if (inline) maxOf(textPlaceable.width, lastRight + gap + metaPlaceable.width)
            else maxOf(textPlaceable.width, metaPlaceable.width)
        val width = desiredWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val metaTop = if (inline) top else textPlaceable.height
        val height = maxOf(textPlaceable.height, metaTop + metaPlaceable.height)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            textPlaceable.placeRelative(0, 0)
            metaPlaceable.placeRelative(width - metaPlaceable.width, metaTop)
        }
    }
}
