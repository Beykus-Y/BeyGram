package com.beykus.beygram.ui.screens.chat

import java.net.URI
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.ui.components.beyGramPalette

internal fun entitySpan(kind: TextStyleKind, accent: Color, secondary: Color): SpanStyle = when (kind) {
    TextStyleKind.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
    TextStyleKind.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
    TextStyleKind.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
    TextStyleKind.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    TextStyleKind.Code, TextStyleKind.Pre -> SpanStyle(fontFamily = FontFamily.Monospace, background = secondary.copy(alpha = 0.12f))
    TextStyleKind.Quote -> SpanStyle(color = secondary, background = accent.copy(alpha = 0.08f))
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
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    var revealed by remember(message.id, message.text, message.entities) { mutableStateOf(false) }
    val entities = validTextEntities(message.text, message.entities)
    val hidden = if (revealed) emptyList() else entities.filter { it.kind == TextStyleKind.Spoiler }
    val annotated = buildAnnotatedString {
        val visibleText = message.text.toCharArray()
        hidden.forEach { entity ->
            for (index in entity.offset until entity.offset + entity.length) if (visibleText[index] != '\n') visibleText[index] = '\u2022'
        }
        append(String(visibleText))
        entities.forEach { entity ->
            val start = entity.offset
            val end = start + entity.length
            addStyle(entitySpan(entity.kind, palette.accentInk, palette.textSecondary), start, end)
            if (entity.kind == TextStyleKind.Quote) addStyle(ParagraphStyle(textIndent = TextIndent(12.sp, 12.sp)), start, end)
            val text = message.text.substring(start, end)
            val target = when (entity.kind) {
                TextStyleKind.Url -> entity.argument.ifEmpty { text }
                TextStyleKind.Mention -> "https://t.me/${text.removePrefix("@")}" 
                TextStyleKind.MentionName -> "tg://user?id=${entity.argument}"
                TextStyleKind.Email -> "mailto:$text"
                TextStyleKind.Phone -> "tel:$text"
                else -> null
            }
            if (target != null && hidden.none { it.offset < end && it.offset + it.length > start }) {
                addLink(LinkAnnotation.Clickable("url:$start") {
                    val safe = safeMessageUrl(target)
                    if (safe == null || runCatching { uri.openUri(safe) }.isFailure) Toast.makeText(context, R.string.chat_link_failed, Toast.LENGTH_SHORT).show()
                }, start, end)
            }
        }
        hidden.forEach {
            addStyle(SpanStyle(color = Color.Transparent, background = palette.textSecondary), it.offset, it.offset + it.length)
            addLink(LinkAnnotation.Clickable("spoiler:${it.offset}") { revealed = true }, it.offset, it.offset + it.length)
        }
    }
    Text(annotated, modifier = modifier, color = palette.textPrimary, fontSize = 16.sp, lineHeight = 22.sp)
}
