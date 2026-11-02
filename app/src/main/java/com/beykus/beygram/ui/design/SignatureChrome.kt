package com.beykus.beygram.ui.design

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Which edge of a bar carries the BeyGram Signature hairline: the edge that meets the content. */
enum class HairlineEdge { Top, Bottom }

/**
 * BeyGram Signature chrome: the bar is drawn on the canvas colour and separated from the content by one 1dp hairline
 * on [edge], instead of a tinted surface or a shadow (docs/beygram-signature.md). The line is drawn over the content so
 * it never changes the measured size of the bar.
 */
fun Modifier.signatureChrome(canvas: Color, hairline: Color, edge: HairlineEdge): Modifier =
    this.drawWithContent {
        drawRect(canvas)
        drawContent()
        val stroke = 1.dp.toPx()
        val y = if (edge == HairlineEdge.Top) stroke / 2 else size.height - stroke / 2
        drawLine(hairline, Offset(0f, y), Offset(size.width, y), stroke)
    }
