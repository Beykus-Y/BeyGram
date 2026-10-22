package com.beykus.beygram.ui.screens.chat

import java.net.URI
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.theme.LocalMessageTextSize

internal fun entitySpan(kind: TextStyleKind, accent: Color, secondary: Color): SpanStyle = when (kind) {
    TextStyleKind.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
    TextStyleKind.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
    TextStyleKind.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
    TextStyleKind.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    TextStyleKind.Code, TextStyleKind.Pre -> SpanStyle(fontFamily = FontFamily.Monospace, fontFeatureSettings = "liga=0")
    TextStyleKind.Quote -> SpanStyle()
    TextStyleKind.Url, TextStyleKind.Mention, TextStyleKind.MentionName, TextStyleKind.Email, TextStyleKind.Phone, TextStyleKind.Command, TextStyleKind.Hashtag -> SpanStyle(color = accent)
    else -> SpanStyle()
}

/** Only explicit navigation schemes; bot links cannot launch arbitrary Android intents. */
internal fun safeMessageUrl(value: String): String? {
    val url = value.trim().let { if (!it.contains(":")) "https://$it" else it }
    return url.takeIf { runCatching { URI(it).scheme?.lowercase() }.getOrNull() in setOf("http", "https", "tg", "mailto", "tel") }
}

@Composable
internal fun FormattedMessageText(message: ChatMessage, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    val parts = remember(message.text, message.entities) { splitMessageText(message.text, message.entities) }
    if (parts.size == 1 && parts.single().block == null) {
        RichTextPart(message.id, parts.single(), modifier)
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        parts.forEachIndexed { index, part ->
            key(message.id, index, part.text) {
                when (part.block) {
                    TextStyleKind.Quote -> Box(Modifier.clip(RoundedCornerShape(6.dp)).background(palette.accent.copy(alpha = 0.12f))) {
                        Row(Modifier.padding(start = 11.dp, end = 25.dp, top = 6.dp, bottom = 6.dp)) { RichTextPart(message.id, part) }
                        Box(Modifier.matchParentSize().drawWithContent {
                            drawRect(palette.accent, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
                        })
                        Text("❞", color = palette.accent, fontSize = 18.sp,
                            modifier = Modifier.align(Alignment.TopEnd).padding(end = 5.dp, top = 1.dp))
                    }
                    TextStyleKind.Pre -> Box(Modifier.clip(RoundedCornerShape(6.dp)).background(palette.textSecondary.copy(alpha = 0.09f)).padding(8.dp)) {
                        RichTextPart(message.id, part)
                    }
                    else -> RichTextPart(message.id, part)
                }
            }
        }
    }
}

@Composable
private fun RichTextPart(messageId: Long, part: MessageTextPart, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    val messageSize = LocalMessageTextSize.current
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    var revealed by remember(messageId, part) { mutableStateOf(false) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val entities = validTextEntities(part.text, part.entities)
    val hidden = if (revealed) emptyList() else entities.filter { it.kind == TextStyleKind.Spoiler }
    val masked = part.text.toCharArray().also { chars ->
        hidden.forEach { entity -> for (index in entity.offset until entity.offset + entity.length) if (chars[index] != '\n') chars[index] = '•' }
    }.concatToString()
    val annotated = buildAnnotatedString {
        // Retain the original glyph metrics; the overlay hides them without changing wrapping.
        append(part.text)
        entities.forEach { entity ->
            val start = entity.offset
            val end = start + entity.length
            addStyle(entitySpan(entity.kind, palette.accentInk, palette.textSecondary), start, end)
            val text = part.text.substring(start, end)
            val target = when (entity.kind) {
                TextStyleKind.Url -> entity.argument.ifEmpty { text }
                TextStyleKind.Mention -> "https://t.me/${text.removePrefix("@")}" 
                TextStyleKind.MentionName -> "tg://user?id=${entity.argument}"
                TextStyleKind.Email -> "mailto:$text"
                TextStyleKind.Phone -> "tel:$text"
                else -> null
            }
            if (target != null && hidden.none { it.offset < end && it.offset + it.length > start }) {
                addLink(LinkAnnotation.Clickable("url:$start", styles = TextLinkStyles(style = SpanStyle(color = palette.accentInk))) {
                    val safe = safeMessageUrl(target)
                    if (safe == null || runCatching { uri.openUri(safe) }.isFailure) Toast.makeText(context, R.string.chat_link_failed, Toast.LENGTH_SHORT).show()
                }, start, end)
            }
        }
        val underline = entities.filter { it.kind == TextStyleKind.Underline }
        entities.filter { it.kind == TextStyleKind.Strike }.forEach { strike -> underline.forEach {
            val left = maxOf(strike.offset, it.offset)
            val right = minOf(strike.offset + strike.length, it.offset + it.length)
            if (left < right) addStyle(SpanStyle(textDecoration = TextDecoration.Underline + TextDecoration.LineThrough), left, right)
        } }
        hidden.forEach {
            addStyle(SpanStyle(color = Color.Transparent, background = Color.Transparent, textDecoration = TextDecoration.None), it.offset, it.offset + it.length)
            addLink(LinkAnnotation.Clickable("spoiler:${it.offset}", styles = TextLinkStyles(
                style = SpanStyle(color = Color.Transparent, background = Color.Transparent, textDecoration = TextDecoration.None),
                pressedStyle = SpanStyle(color = Color.Transparent, background = Color.Transparent)
            )) { revealed = true }, it.offset, it.offset + it.length)
        }
    }
    val accessibility = if (hidden.isEmpty()) Modifier else Modifier.clearAndSetSemantics {
        text = AnnotatedString(masked)
        onClick(context.getString(R.string.chat_reveal_spoiler)) { revealed = true; true }
    }
    Text(annotated, modifier = modifier.then(accessibility).drawWithContent {
        drawContent()
        val result = layout ?: return@drawWithContent
        hidden.forEach { entity ->
            val path = result.getPathForRange(entity.offset, entity.offset + entity.length)
            val bounds = path.getBounds()
            clipPath(path) {
                val step = 2.6.dp.toPx()
                val radius = 0.65.dp.toPx()
                val columns = (bounds.width / step).toInt() + 1
                val rows = (bounds.height / step).toInt() + 1
                for (row in 0..rows) for (column in 0..columns) {
                    val seed = (row * 73856093) xor (column * 19349663) xor messageId.toInt()
                    val dx = ((seed ushr 2) and 15) / 15f * step
                    val dy = ((seed ushr 6) and 15) / 15f * step
                    drawCircle(palette.textPrimary.copy(alpha = 0.25f + (seed and 7) / 14f), radius,
                        Offset(bounds.left + column * step + dx, bounds.top + row * step + dy))
                }
            }
        }
    }, color = palette.textPrimary, fontSize = messageSize,
        fontFamily = if (part.block == TextStyleKind.Pre) FontFamily.Monospace else FontFamily.Default,
        lineHeight = (messageSize.value * 1.375f).sp, onTextLayout = { layout = it })
}
