package com.beykus.beygram.ui.screens.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageIdReplacement
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.TelegramBoundedText
import com.beykus.beygram.domain.model.boundTelegramText
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.resolve
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * One outgoing Composer Launch. Geometry is stored in window coordinates; the overlay converts it to its own local
 * coordinates. The request does not block the TDLib send and is removed on failure or when no visible row appears.
 */
internal class ComposerLaunch(
    val key: Long,
    val text: String,
    val entities: List<MessageTextEntity>,
    val replyToMessageId: Long?,
    val sourceBounds: Rect,
    val baselineSequence: Long,
    val speed: Float
) {
    var messageId by mutableStateOf<Long?>(null)
    var message by mutableStateOf<ChatMessage?>(null)
    var targetBounds by mutableStateOf<Rect?>(null)
    var awaitingSend by mutableStateOf(true)
}

/** The exact bounded text and entities that are handed to TDLib and matched by a Composer Launch. */
internal fun composerLaunchPayload(text: String, entities: List<MessageTextEntity>, limit: Int): TelegramBoundedText =
    boundTelegramText(text, entities, limit)

/** A newly typed draft can reuse the previous launch's text; only the attempt that is still sending may hide it. */
internal fun shouldHideComposerDraft(
    editing: Boolean,
    draft: String,
    activeLaunchKey: Long?,
    launch: ComposerLaunch?
): Boolean = !editing && draft.isNotBlank() && launch != null && launch.key == activeLaunchKey && launch.awaitingSend

/** Follows a temporary message id through a bounded chain of send confirmations. */
internal fun resolveMessageIdThroughReplacements(id: Long, replacements: List<MessageIdReplacement>): Long {
    var current = id
    val seen = mutableSetOf<Long>()
    while (seen.add(current)) {
        val next = replacements.lastOrNull { it.oldId == current }?.newId ?: return current
        current = next
    }
    return current
}

/**
 * The first outgoing message inserted after the launch that carries the same text and reply target. Confirmations are
 * followed through the replacement log, so a send that is confirmed before Compose observes it still matches.
 */
internal fun findComposerLaunchMessage(
    history: ChatHistory,
    text: String,
    replyToMessageId: Long?,
    baselineSequence: Long,
    assignedIds: Set<Long>
): ChatMessage? {
    val arrivals = history.liveArrivals.asSequence()
        .filter { it.sequence > baselineSequence }
        .sortedBy { it.sequence }
        .map { it.messageId }
        .toList()
    if (arrivals.isEmpty()) return null
    val candidateIds = LinkedHashSet<Long>()
    arrivals.forEach { id ->
        val resolved = resolveMessageIdThroughReplacements(id, history.recentReplacements)
        candidateIds += resolved
    }
    val order = candidateIds.withIndex().associate { it.value to it.index }
    return history.messages.asSequence()
        .filter { it.id in candidateIds && it.id !in assignedIds }
        .filter { it.isOutgoing && it.text == text && it.reply?.messageId == replyToMessageId }
        .minByOrNull { order[it.id] ?: Int.MAX_VALUE }
}

/** Measured start/end geometry for the spatial transition. Kept pure so source/destination math can be tested. */
internal data class ComposerLaunchGeometry(
    val sourceCenter: androidx.compose.ui.geometry.Offset,
    val targetCenter: androidx.compose.ui.geometry.Offset,
    val startScale: Float,
    val finalScaleX: Float,
    val finalScaleY: Float,
    val baseLeft: Float,
    val baseTop: Float
)

internal fun composerLaunchGeometry(source: Rect, target: Rect, bubbleSize: IntSize): ComposerLaunchGeometry? {
    if (bubbleSize.width <= 0 || bubbleSize.height <= 0) return null
    val bubbleWidth = bubbleSize.width.toFloat()
    val bubbleHeight = bubbleSize.height.toFloat()
    val targetCenter = target.center
    return ComposerLaunchGeometry(
        sourceCenter = source.center,
        targetCenter = targetCenter,
        startScale = (source.height * 1.15f / bubbleHeight).coerceIn(0.35f, 0.7f),
        finalScaleX = (target.width / bubbleWidth).coerceIn(0.6f, 1.5f),
        finalScaleY = (target.height / bubbleHeight).coerceIn(0.6f, 1.5f),
        baseLeft = targetCenter.x - bubbleWidth / 2f,
        baseTop = targetCenter.y - bubbleHeight / 2f
    )
}

/**
 * Full-screen overlay for Composer Launch. It draws a bubble that starts at the composer's measured bounds and lands
 * exactly on the measured bubble bounds of the real message. The real row stays hidden until this overlay finishes.
 */
@Composable
internal fun ComposerLaunchOverlay(
    launches: List<ComposerLaunch>,
    chatId: Long,
    overlayBounds: Rect?,
    onFinished: (ComposerLaunch) -> Unit,
    onCancel: (ComposerLaunch) -> Unit
) {
    if (overlayBounds == null || launches.isEmpty()) return
    Box(Modifier.fillMaxSize().clipToBounds()) {
        launches.forEach { launch ->
            key(launch.key) {
                ComposerLaunchLayer(launch, chatId, overlayBounds, onFinished, onCancel)
            }
        }
    }
}

@Composable
internal fun ComposerLaunchLayer(
    launch: ComposerLaunch,
    chatId: Long,
    overlayBounds: Rect,
    onFinished: (ComposerLaunch) -> Unit,
    onCancel: (ComposerLaunch) -> Unit
) {
    var bubbleSize by remember { mutableStateOf(IntSize.Zero) }
    val progress = remember { Animatable(0f) }
    val message = launch.message ?: ChatMessage(
        id = -launch.key,
        chatId = chatId,
        text = launch.text,
        time = "",
        isOutgoing = true,
        entities = launch.entities,
        sendState = MessageSendState.Sending
    )
    val source = launch.sourceBounds.translate(-overlayBounds.left, -overlayBounds.top)
    val target = launch.targetBounds?.translate(-overlayBounds.left, -overlayBounds.top)
    LaunchedEffect(launch.key) {
        var total = 0
        var sinceSend = 0
        while ((launch.targetBounds == null || bubbleSize.width == 0) && total < 6000) {
            delay(30)
            total += 30
            if (!launch.awaitingSend) sinceSend += 30
            if (!launch.awaitingSend && sinceSend >= 900) break
        }
        if (launch.targetBounds == null || bubbleSize.width == 0) {
            onCancel(launch)
            return@LaunchedEffect
        }
        val duration = (320f / launch.speed).roundToInt().coerceIn(180, 520)
        progress.animateTo(1f, tween(duration, easing = CubicBezierEasing(0.16f, 0.84f, 0.24f, 1f)))
        onFinished(launch)
    }
    val bubble = @Composable {
        ComposerLaunchBubble(
            message = message,
            modifier = Modifier.onSizeChanged { bubbleSize = it }
        )
    }
    if (target == null) {
        val center = source.center
        Box(
            Modifier.offset {
                IntOffset(
                    (center.x - bubbleSize.width / 2f).roundToInt(),
                    (center.y - bubbleSize.height / 2f).roundToInt()
                )
            }.graphicsLayer {
                val visible = bubbleSize.width > 0 && bubbleSize.height > 0
                alpha = if (visible) 0.92f else 0f
                val scale = (source.height * 1.15f / bubbleSize.height.coerceAtLeast(1)).coerceIn(0.35f, 0.7f)
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin.Center
            }
        ) { bubble() }
        return
    }
    val geometry = composerLaunchGeometry(source, target, bubbleSize)
    if (geometry == null) {
        // The bubble must be composed once to learn its final size before the flight can be placed.
        Box(Modifier.alpha(0f)) { bubble() }
        return
    }
    Box(
        Modifier.offset { IntOffset(geometry.baseLeft.roundToInt(), geometry.baseTop.roundToInt()) }
            .graphicsLayer {
                val p = progress.value
                alpha = 0.92f + 0.08f * p.coerceIn(0f, 1f)
                scaleX = geometry.startScale + (geometry.finalScaleX - geometry.startScale) * p
                scaleY = geometry.startScale + (geometry.finalScaleY - geometry.startScale) * p
                translationX = (geometry.sourceCenter.x - geometry.targetCenter.x) * (1f - p)
                translationY = (geometry.sourceCenter.y - geometry.targetCenter.y) * (1f - p)
                transformOrigin = TransformOrigin.Center
            }
    ) { bubble() }
}

@Composable
internal fun ComposerLaunchBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    val metrics = LocalBubbleMetrics.current
    val shape = bubbleShape(outgoing = true, metrics)
    BubbleLayout(
        modifier = modifier.widthIn(max = 292.dp).clip(shape).background(palette.outgoingBubble),
        overlayMeta = false,
        body = {
            Column {
                if (message.reply != null || message.text.isNotBlank()) {
                    Column(Modifier.padding(start = metrics.textInset, end = metrics.textInset, top = metrics.textTopInset)) {
                        message.reply?.let { reply ->
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(metrics.replyRadius))
                                    .border(1.dp, palette.accent, RoundedCornerShape(metrics.replyRadius))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    reply.senderName.ifBlank { stringResource(R.string.chat_reply_to_message) },
                                    color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                                )
                                Text(
                                    reply.text.resolve(), color = palette.textSecondary, fontSize = 14.sp,
                                    lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (message.text.isNotBlank()) FormattedMessageText(message)
                    }
                }
            }
        },
        meta = {
            BubbleMeta(message, isRead = false, retryAvailable = false, overlay = false, onRetry = {})
        }
    )
}

/**
 * The settings preview for Composer Launch. It uses the same [ComposerLaunchLayer] and measured source/target
 * rectangles as the real chat; only the chat contents and the send button are simulated.
 */
@Composable
internal fun ComposerLaunchPreview(
    text: String,
    time: String,
    speed: Float,
    enabled: Boolean,
    replayKey: Any,
    modifier: Modifier = Modifier
) {
    val palette = beyGramPalette()
    val message = remember(text, time) {
        ChatMessage(
            id = -1L,
            chatId = 0L,
            text = text,
            time = time,
            isOutgoing = true,
            sendState = MessageSendState.Sending
        )
    }
    var overlayBounds by remember(replayKey) { mutableStateOf<Rect?>(null) }
    var sourceBounds by remember(replayKey) { mutableStateOf<Rect?>(null) }
    var targetBounds by remember(replayKey) { mutableStateOf<Rect?>(null) }
    var request by remember(replayKey) { mutableStateOf<ComposerLaunch?>(null) }
    var finished by remember(replayKey) { mutableStateOf(false) }
    var fieldCleared by remember(replayKey) { mutableStateOf(false) }
    LaunchedEffect(sourceBounds, targetBounds, replayKey, enabled) {
        if (enabled && !finished && request == null && sourceBounds != null && targetBounds != null) {
            request = ComposerLaunch(
                key = 1L,
                text = text,
                entities = emptyList(),
                replyToMessageId = null,
                sourceBounds = sourceBounds!!,
                baselineSequence = 0L,
                speed = speed
            ).also {
                it.message = message
                it.targetBounds = targetBounds
                it.awaitingSend = false
            }
            fieldCleared = true
        }
    }
    Box(
        modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(14.dp))
            .background(palette.screen).clipToBounds()
            .onGloballyPositioned { overlayBounds = it.boundsInWindow() }
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            DemoIncomingBubble(modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(10.dp))
            Box(Modifier.align(Alignment.End).alpha(if (finished || !enabled) 1f else 0f)
                .onGloballyPositioned { targetBounds = it.boundsInWindow() }) {
                ComposerLaunchBubble(message)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).defaultMinSize(minHeight = 38.dp)
                        .clip(RoundedCornerShape(19.dp)).background(palette.surface2)
                        .padding(horizontal = 13.dp, vertical = 9.dp)
                        .onGloballyPositioned { sourceBounds = it.boundsInWindow() }
                ) {
                    Text(
                        if (fieldCleared) "" else text,
                        color = palette.textPrimary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(palette.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = null,
                        tint = palette.onAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        val bounds = overlayBounds
        request?.let { launch ->
            if (bounds != null) {
                ComposerLaunchLayer(
                    launch = launch,
                    chatId = 0L,
                    overlayBounds = bounds,
                    onFinished = { finished = true; request = null },
                    onCancel = { finished = true; request = null }
                )
            }
        }
    }
}

@Composable
private fun DemoIncomingBubble(modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    val metrics = LocalBubbleMetrics.current
    BubbleLayout(
        modifier = modifier.widthIn(max = 180.dp).clip(bubbleShape(outgoing = false, metrics))
            .background(palette.incomingBubble),
        overlayMeta = false,
        body = {
            Column(Modifier.padding(horizontal = metrics.textInset, vertical = metrics.textTopInset)) {
                Text(
                    stringResource(R.string.theme_preview_incoming),
                    color = palette.messageText,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        meta = { Text(stringResource(R.string.theme_preview_time), color = palette.messageTime, fontSize = 10.sp) }
    )
}
