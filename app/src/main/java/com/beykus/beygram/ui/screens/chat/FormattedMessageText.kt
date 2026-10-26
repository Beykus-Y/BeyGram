package com.beykus.beygram.ui.screens.chat

import java.net.URI
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.deeplink.MessageEntityLink
import com.beykus.beygram.deeplink.messageEntityLink
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.ui.accessibility.systemReducedMotion
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.theme.LocalMessageLineSpacing
import com.beykus.beygram.ui.theme.LocalMessageTextSize
import kotlinx.coroutines.flow.StateFlow

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

internal typealias ObserveCustomEmoji = (Long) -> StateFlow<CustomEmoji?>
internal typealias ObserveMediaFile = (Int) -> StateFlow<MediaFileState>
internal typealias DownloadMediaFile = (Int) -> Unit

@Composable
internal fun FormattedMessageText(
    message: ChatMessage,
    observeCustomEmoji: ObserveCustomEmoji? = null,
    observeFile: ObserveMediaFile? = null,
    downloadFile: DownloadMediaFile? = null,
    onOpenEntity: ((MessageEntityLink) -> Unit)? = null,
    entityClicksEnabled: Boolean = true,
    modifier: Modifier = Modifier
) = FormattedMessageText(message.text, message.entities, message.id, observeCustomEmoji, observeFile, downloadFile, onOpenEntity, entityClicksEnabled, modifier)

/** Used for inline translations, whose text and entities are not fields of the original [ChatMessage]. */
@Composable
internal fun FormattedMessageText(
    text: String,
    entities: List<MessageTextEntity>,
    messageId: Long,
    observeCustomEmoji: ObserveCustomEmoji? = null,
    observeFile: ObserveMediaFile? = null,
    downloadFile: DownloadMediaFile? = null,
    onOpenEntity: ((MessageEntityLink) -> Unit)? = null,
    entityClicksEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val palette = beyGramPalette()
    val parts = remember(text, entities) { splitMessageText(text, entities) }
    if (parts.size == 1 && parts.single().block == null) {
        RichTextPart(messageId, parts.single(), observeCustomEmoji, observeFile, downloadFile, onOpenEntity, entityClicksEnabled, modifier)
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        parts.forEachIndexed { index, part ->
            key(messageId, index, part.text) {
                when (part.block) {
                    TextStyleKind.Quote -> Box(Modifier.clip(RoundedCornerShape(6.dp)).background(palette.accent.copy(alpha = 0.12f))) {
                        Row(Modifier.padding(start = 11.dp, end = 25.dp, top = 6.dp, bottom = 6.dp)) {
                            RichTextPart(messageId, part, observeCustomEmoji, observeFile, downloadFile, onOpenEntity, entityClicksEnabled)
                        }
                        Box(Modifier.matchParentSize().drawWithContent {
                            drawRect(palette.accent, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
                        })
                        Text("❞", color = palette.accent, fontSize = 18.sp,
                            modifier = Modifier.align(Alignment.TopEnd).padding(end = 5.dp, top = 1.dp))
                    }
                    TextStyleKind.Pre -> Box(Modifier.clip(RoundedCornerShape(6.dp)).background(palette.textSecondary.copy(alpha = 0.09f)).padding(8.dp)) {
                        RichTextPart(messageId, part, observeCustomEmoji, observeFile, downloadFile, onOpenEntity, entityClicksEnabled)
                    }
                    else -> RichTextPart(messageId, part, observeCustomEmoji, observeFile, downloadFile, onOpenEntity, entityClicksEnabled)
                }
            }
        }
    }
}

@Composable
private fun RichTextPart(
    messageId: Long,
    part: MessageTextPart,
    observeCustomEmoji: ObserveCustomEmoji?,
    observeFile: ObserveMediaFile?,
    downloadFile: DownloadMediaFile?,
    onOpenEntity: ((MessageEntityLink) -> Unit)?,
    entityClicksEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = beyGramPalette()
    val messageSize = LocalMessageTextSize.current
    val lineSpacing = LocalMessageLineSpacing.current
    val context = LocalContext.current
    var revealed by remember(messageId, part) { mutableStateOf(false) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val entities = validTextEntities(part.text, part.entities)
    val hidden = if (revealed) emptyList() else entities.filter { it.kind == TextStyleKind.Spoiler }
    val masked = part.text.toCharArray().also { chars ->
        hidden.forEach { entity -> for (index in entity.offset until entity.offset + entity.length) if (chars[index] != '\n') chars[index] = '•' }
    }.concatToString()
    val animationsEnabled = LocalChatAnimationPreferences.current.animatedEmoji && !systemReducedMotion()
    val emojiLookupReady = observeCustomEmoji != null && observeFile != null && downloadFile != null
    // Emoji in a hidden spoiler must not download or appear before the spoiler is revealed.
    val inlineEmoji = remember(part.text, entities, hidden, animationsEnabled, emojiLookupReady) {
        if (!animationsEnabled || !emojiLookupReady) emptyList() else {
            inlineCustomEmojiRuns(part.text, entities, enabled = true).filterNot { run ->
                hidden.any { it.offset < run.end && it.offset + it.length > run.offset }
            }
        }
    }
    // A link hidden under an unrevealed spoiler is not tappable; a revealed spoiler exposes its links again.
    val actions = remember(part.text, entities, hidden, entityClicksEnabled) {
        if (!entityClicksEnabled) emptyList() else entities.mapNotNull { entity ->
            val end = entity.offset + entity.length
            val link = messageEntityLink(entity, part.text) ?: return@mapNotNull null
            if (hidden.any { it.offset < end && it.offset + it.length > entity.offset }) return@mapNotNull null
            MessageEntityAction(entity.offset, end, link)
        }
    }
    val spoilers = remember(entities, hidden, entityClicksEnabled) {
        if (entityClicksEnabled) hidden else emptyList()
    }
    val annotated = buildAnnotatedString {
        // Inline content uses the original glyphs as alternate text, so every offset and style below stays unchanged.
        var cursor = 0
        inlineEmoji.forEach { run ->
            append(part.text.substring(cursor, run.offset))
            appendInlineContent(inlineEmojiId(run), run.alternate)
            cursor = run.end
        }
        append(part.text.substring(cursor))
        entities.forEach { entity ->
            val start = entity.offset
            val end = start + entity.length
            addStyle(entitySpan(entity.kind, palette.accentInk, palette.textSecondary), start, end)
        }
        val underline = entities.filter { it.kind == TextStyleKind.Underline }
        entities.filter { it.kind == TextStyleKind.Strike }.forEach { strike -> underline.forEach {
            val left = maxOf(strike.offset, it.offset)
            val right = minOf(strike.offset + strike.length, it.offset + it.length)
            if (left < right) addStyle(SpanStyle(textDecoration = TextDecoration.Underline + TextDecoration.LineThrough), left, right)
        } }
        hidden.forEach {
            addStyle(SpanStyle(color = Color.Transparent, background = Color.Transparent, textDecoration = TextDecoration.None), it.offset, it.offset + it.length)
        }
    }
    val observeEmoji = observeCustomEmoji
    val observe = observeFile
    val download = downloadFile
    val inlineContent: Map<String, InlineTextContent> =
        if (inlineEmoji.isEmpty() || observeEmoji == null || observe == null || download == null) emptyMap() else {
            inlineEmoji.associate { run ->
                val id = inlineEmojiId(run)
                id to InlineTextContent(
                    // Same height as the line's text keeps wrapping and bubble measurement identical to plain text.
                    Placeholder(width = messageSize, height = messageSize, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter)
                ) { alternateText ->
                    InlineCustomEmojiGlyph(run.customEmojiId, alternateText, messageSize, observeEmoji, observe, download)
                }
            }
        }
    val accessibility = when {
        hidden.isNotEmpty() -> Modifier.clearAndSetSemantics {
            text = AnnotatedString(masked)
            onClick(context.getString(R.string.chat_reveal_spoiler)) { revealed = true; true }
        }
        actions.isEmpty() -> Modifier
        else -> Modifier.semantics {
            customActions = actions.map { action ->
                CustomAccessibilityAction(
                    context.getString(R.string.a11y_open_link, action.link.accessibilityLabel())
                ) {
                    openMessageEntity(context, action.link, onOpenEntity)
                    true
                }
            }
        }
    }
    val tapModifier = Modifier.messageEntityTapHandler(
        layout = { layout },
        actions = actions,
        spoilers = spoilers,
        onLink = { link -> openMessageEntity(context, link, onOpenEntity) },
        onRevealSpoiler = { revealed = true }
    )
    Text(annotated, modifier = modifier.then(accessibility).then(tapModifier).drawWithContent {
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
                    drawCircle(palette.messageText.copy(alpha = 0.25f + (seed and 7) / 14f), radius,
                        Offset(bounds.left + column * step + dx, bounds.top + row * step + dy))
                }
            }
        }
    }, color = palette.messageText, fontSize = messageSize,
        fontFamily = if (part.block == TextStyleKind.Pre) FontFamily.Monospace else FontFamily.Default,
        lineHeight = (messageSize.value * lineSpacing).sp, inlineContent = inlineContent,
        onTextLayout = { layout = it })
}

private fun inlineEmojiId(run: InlineCustomEmojiRun): String = "custom-emoji:${run.customEmojiId}:${run.offset}"

internal data class MessageEntityAction(val start: Int, val end: Int, val link: MessageEntityLink)

/** The entity under a tap. A visible link wins over a spoiler range; hidden links never reach the action list. */
internal sealed interface MessageEntityTap {
    data class Link(val action: MessageEntityAction) : MessageEntityTap
    data object Spoiler : MessageEntityTap
}

internal fun messageEntityTapAt(
    offset: Int,
    actions: List<MessageEntityAction>,
    spoilers: List<IntRange>
): MessageEntityTap? {
    actions.firstOrNull { offset in it.start until it.end }?.let { return MessageEntityTap.Link(it) }
    return spoilers.firstOrNull { offset in it }?.let { MessageEntityTap.Spoiler }
}

private fun MessageEntityLink.accessibilityLabel(): String = when (this) {
    is MessageEntityLink.PublicChat -> "@$username"
    is MessageEntityLink.User -> "tg://user?id=$userId"
    is MessageEntityLink.TelegramMessage -> url
    is MessageEntityLink.TelegramInvite -> url
    is MessageEntityLink.ExternalUrl -> url
    is MessageEntityLink.Email -> address
    is MessageEntityLink.Phone -> number
    is MessageEntityLink.TelegramPhone -> phone
}

/**
 * Entity taps are detected without LinkAnnotation on purpose: its tap detector consumes every down event and would
 * block the bubble's long press. Here the down is left for the parent; only a completed short tap on an entity is
 * consumed, while a long press or a drag stays with the message menu, selection mode and the scrolling list.
 */
private fun Modifier.messageEntityTapHandler(
    layout: () -> TextLayoutResult?,
    actions: List<MessageEntityAction>,
    spoilers: List<MessageTextEntity>,
    onLink: (MessageEntityLink) -> Unit,
    onRevealSpoiler: () -> Unit
): Modifier {
    if (actions.isEmpty() && spoilers.isEmpty()) return this
    return pointerInput(actions, spoilers) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val currentLayout = layout() ?: return@awaitEachGesture
            val offset = currentLayout.getOffsetForPosition(down.position)
            if (down.position.x < 0f || down.position.y < 0f ||
                down.position.x > currentLayout.size.width.toFloat() || down.position.y > currentLayout.size.height.toFloat()
            ) return@awaitEachGesture
            val tap = messageEntityTapAt(
                offset,
                actions,
                spoilers.map { it.offset until it.offset + it.length }
            ) ?: return@awaitEachGesture

            var up: androidx.compose.ui.input.pointer.PointerInputChange? = null
            var moved = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUp()) { up = change; break }
                val delta = change.position - change.previousPosition
                if (delta.x * delta.x + delta.y * delta.y > viewConfiguration.touchSlop * viewConfiguration.touchSlop) { moved = true; break }
                if (change.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) break
            }
            if (!moved && up != null && !up.isConsumed &&
                up.uptimeMillis - down.uptimeMillis <= viewConfiguration.longPressTimeoutMillis
            ) {
                up.consume()
                when (tap) {
                    is MessageEntityTap.Link -> onLink(tap.action.link)
                    MessageEntityTap.Spoiler -> onRevealSpoiler()
                }
            }
        }
    }
}

/**
 * A Telegram entity is opened by the chat's TDLib navigation when available; everything else goes to exactly one
 * external handler. The chat callback performs the lookup, so a tap never silently becomes a browser page or nothing.
 */
private fun openMessageEntity(context: Context, link: MessageEntityLink, handler: ((MessageEntityLink) -> Unit)?) {
    when (link) {
        is MessageEntityLink.ExternalUrl -> openWithSystemHandler(context, link.url)
        is MessageEntityLink.Email -> openWithSystemHandler(context, "mailto:${link.address}")
        is MessageEntityLink.Phone -> openWithSystemHandler(context, "tel:${link.number.filter { it.isDigit() || it == '+' }}")
        is MessageEntityLink.PublicChat -> {
            if (handler != null) handler(link)
            else openWithSystemHandler(context, "https://t.me/${link.username}")
        }
        is MessageEntityLink.User -> {
            if (handler != null) handler(link)
            else openWithSystemHandler(context, "tg://user?id=${link.userId}")
        }
        is MessageEntityLink.TelegramMessage -> {
            if (handler != null) handler(link)
            else openWithSystemHandler(context, link.url)
        }
        is MessageEntityLink.TelegramInvite -> {
            if (handler != null) handler(link)
            else openWithSystemHandler(context, link.url)
        }
        is MessageEntityLink.TelegramPhone -> {
            if (handler != null) handler(link)
            else openWithSystemHandler(context, "https://t.me/+${link.phone.removePrefix("+")}")
        }
    }
}

private fun openWithSystemHandler(context: Context, target: String) {
    val safe = safeMessageUrl(target)
    if (safe == null) {
        Toast.makeText(context, R.string.chat_link_failed, Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safe))
    // A non-Activity context (a dialog's window, an application context) needs its own task for ACTION_VIEW.
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.chat_link_failed, Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, R.string.chat_link_failed, Toast.LENGTH_SHORT).show()
    }
}

/**
 * One animated custom emoji in the text flow. The placeholder keeps the original emoji as its alternate text, so a
 * missing TGS/WebP file, WebM sticker or disabled animation still shows the ordinary emoji instead of a gap.
 */
@Composable
private fun InlineCustomEmojiGlyph(
    customEmojiId: Long,
    alternateText: String,
    size: TextUnit,
    observeCustomEmoji: ObserveCustomEmoji,
    observeFile: ObserveMediaFile,
    downloadFile: DownloadMediaFile
) {
    val custom by remember(customEmojiId) { observeCustomEmoji(customEmojiId) }.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val pixelSize = with(density) { size.toDp() }
    val fallback = alternateText.ifBlank { "🙂" }
    Box(Modifier.fillMaxSize().clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        val emoji = custom
        if (emoji != null) {
            CustomEmojiImage(emoji, pixelSize, observeFile, downloadFile) {
                Text(fallback, fontSize = size, lineHeight = size, textAlign = TextAlign.Center, maxLines = 1)
            }
        } else {
            Text(fallback, fontSize = size, lineHeight = size, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}
