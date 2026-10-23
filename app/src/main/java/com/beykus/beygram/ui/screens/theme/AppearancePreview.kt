package com.beykus.beygram.ui.screens.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.MessageAnimationPreset
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageReply
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.ChatWallpaperLayer
import com.beykus.beygram.ui.screens.chat.ComposerLaunch
import com.beykus.beygram.ui.screens.chat.ComposerLaunchBubble
import com.beykus.beygram.ui.screens.chat.ComposerLaunchLayer
import com.beykus.beygram.ui.screens.chat.LocalBubbleMetrics
import com.beykus.beygram.ui.screens.chat.LocalChatWallpaper
import com.beykus.beygram.ui.screens.chat.MessageAnimationFrame
import com.beykus.beygram.ui.screens.chat.bubbleMetricsFor
import com.beykus.beygram.ui.screens.chat.bubbleShape
import com.beykus.beygram.ui.screens.chat.BubbleLayout
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.beykus.beygram.ui.theme.LocalMessageLineSpacing
import com.beykus.beygram.ui.theme.LocalMessageTextSize
import com.beykus.beygram.ui.theme.resolveTokens

/**
 * Draws a subtree with the draft's real theme, bubble metrics and wallpaper. Preview surfaces use the same values
 * as a chat, so the small card and the full demonstration cannot drift from each other.
 */
@Composable
internal fun AppearanceThemeScope(
    config: AppearanceConfig,
    systemDark: Boolean,
    content: @Composable () -> Unit
) {
    val dark = config.mode.isDark(systemDark)
    val metrics = remember(config.bubbleStyle, config.density, config.bubbleCornerRadiusDp) {
        bubbleMetricsFor(config.bubbleStyle, config.density, config.bubbleCornerRadiusDp)
    }
    BeyGramTheme(
        darkTheme = dark,
        tokens = resolveTokens(config, systemDark),
        messageTextSizeSp = config.messageTextSizeSp,
        messageLineSpacing = config.messageLineSpacing
    ) {
        CompositionLocalProvider(
            LocalBubbleMetrics provides metrics,
            LocalChatWallpaper provides config.wallpaper
        ) {
            content()
        }
    }
}

/**
 * The same compact chat fragment used by the overview and every section preview. Tapping the outer card is handled
 * by the caller; [onSend] is only for the full preview's replay affordance.
 */
@Composable
internal fun AppearanceChatPreview(
    config: AppearanceConfig,
    compact: Boolean,
    animate: Boolean,
    replayKey: Int,
    modifier: Modifier = Modifier,
    systemDark: Boolean = isSystemInDarkTheme(),
    showComposer: Boolean = true,
    onSend: (() -> Unit)? = null
) {
    AppearanceThemeScope(config, systemDark) {
        key(replayKey) {
            AppearancePreviewBody(
                config = config,
                compact = compact,
                animate = animate,
                modifier = modifier,
                showComposer = showComposer,
                onSend = onSend
            )
        }
    }
}

@Composable
private fun AppearancePreviewBody(
    config: AppearanceConfig,
    compact: Boolean,
    animate: Boolean,
    modifier: Modifier,
    showComposer: Boolean,
    onSend: (() -> Unit)?
) {
    val palette = beyGramPalette()
    val metrics = LocalBubbleMetrics.current
    val contact = stringResource(R.string.theme_preview_contact)
    val time = stringResource(R.string.theme_preview_time)
    val replyTime = stringResource(R.string.theme_preview_time_reply)
    val incomingText = stringResource(R.string.theme_preview_incoming)
    val outgoingText = stringResource(R.string.theme_preview_outgoing)
    val replyText = stringResource(R.string.theme_preview_reply)
    val mediaCaption = stringResource(R.string.theme_preview_media_caption)
    val serviceText = stringResource(R.string.theme_preview_service)
    val input = stringResource(R.string.wallpaper_preview_input)
    val outgoing = config.messageAnimations.direction(outgoing = true)
    val composerLaunch = showComposer && animate && config.messageAnimations.enabled &&
        outgoing.preset == MessageAnimationPreset.ComposerLaunch
    val outgoingMessage = remember(outgoingText, replyTime, replyText, contact, compact) {
        ChatMessage(
            id = PREVIEW_OUTGOING_ID,
            chatId = 0L,
            text = outgoingText,
            time = replyTime,
            isOutgoing = true,
            sendState = MessageSendState.Sent,
            reply = if (compact) null else MessageReply(
                chatId = 0L,
                messageId = PREVIEW_REPLY_ID,
                text = UiText.Raw(replyText),
                senderName = contact
            )
        )
    }
    var overlayBounds by remember { mutableStateOf<Rect?>(null) }
    var sourceBounds by remember { mutableStateOf<Rect?>(null) }
    var targetBounds by remember { mutableStateOf<Rect?>(null) }
    var launch by remember { mutableStateOf<ComposerLaunch?>(null) }
    var finished by remember { mutableStateOf(false) }
    val fieldCleared = composerLaunch && (launch != null || finished)
    val showOutgoing = !composerLaunch || finished
    LaunchedEffect(composerLaunch, sourceBounds, targetBounds) {
        if (!composerLaunch || finished || launch != null) return@LaunchedEffect
        val source = sourceBounds ?: return@LaunchedEffect
        val target = targetBounds ?: return@LaunchedEffect
        launch = ComposerLaunch(
            key = PREVIEW_LAUNCH_KEY,
            text = outgoingMessage.text,
            entities = emptyList(),
            replyToMessageId = PREVIEW_REPLY_ID,
            sourceBounds = source,
            baselineSequence = 0L,
            speed = outgoing.tuning.speed
        ).also {
            it.message = outgoingMessage
            it.targetBounds = target
            it.awaitingSend = false
        }
    }

    Box(
        modifier.fillMaxWidth()
            .then(if (compact) Modifier else Modifier.fillMaxSize())
            .clip(if (compact) RoundedCornerShape(20.dp) else RoundedCornerShape(0.dp))
            .background(palette.screen)
            .clipToBounds()
            .onGloballyPositioned { overlayBounds = it.boundsInWindow() }
    ) {
        Column(Modifier.fillMaxWidth().then(if (compact) Modifier else Modifier.fillMaxSize())) {
            if (!compact) PreviewChatHeader(contact)
            val messagesModifier = if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().weight(1f)
            Box(messagesModifier) {
                ChatWallpaperLayer(config.wallpaper, modifier = Modifier.matchParentSize())
                if (compact) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = metrics.sideInset, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(metrics.itemSpacing)
                    ) {
                        PreviewMessagesContent(
                            compact = true,
                            config = config,
                            animate = animate,
                            composerLaunch = composerLaunch,
                            showOutgoing = showOutgoing,
                            outgoingMessage = outgoingMessage,
                            incomingText = incomingText,
                            time = time,
                            replyTime = replyTime,
                            mediaCaption = mediaCaption,
                            serviceText = serviceText,
                            onTargetPositioned = { targetBounds = it }
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = metrics.sideInset,
                            end = metrics.sideInset,
                            top = metrics.listPadding,
                            bottom = metrics.listPadding
                        ),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(metrics.itemSpacing)) {
                                PreviewMessagesContent(
                                    compact = false,
                                    config = config,
                                    animate = animate,
                                    composerLaunch = composerLaunch,
                                    showOutgoing = showOutgoing,
                                    outgoingMessage = outgoingMessage,
                                    incomingText = incomingText,
                                    time = time,
                                    replyTime = replyTime,
                                    mediaCaption = mediaCaption,
                                    serviceText = serviceText,
                                    onTargetPositioned = { targetBounds = it }
                                )
                            }
                        }
                    }
                }
            }
            if (showComposer) {
                PreviewComposer(
                    placeholder = if (fieldCleared) "" else input,
                    fieldPositioned = { sourceBounds = it },
                    onSend = onSend
                )
            }
        }
        if (composerLaunch && overlayBounds != null) {
            launch?.let { request ->
                ComposerLaunchLayer(
                    launch = request,
                    chatId = 0L,
                    overlayBounds = overlayBounds!!,
                    onFinished = { finished = true; launch = null },
                    onCancel = { finished = true; launch = null }
                )
            }
        }
    }
}

@Composable
private fun PreviewMessagesContent(
    compact: Boolean,
    config: AppearanceConfig,
    animate: Boolean,
    composerLaunch: Boolean,
    showOutgoing: Boolean,
    outgoingMessage: ChatMessage,
    incomingText: String,
    time: String,
    replyTime: String,
    mediaCaption: String,
    serviceText: String,
    onTargetPositioned: (Rect) -> Unit
) {
    PreviewDateChip(stringResource(R.string.theme_preview_today))
    MessageAnimationFrame(
        animate = animate && config.messageAnimations.enabled,
        outgoing = false,
        config = config.messageAnimations
    ) {
        PreviewIncomingBubble(
            text = incomingText,
            time = time,
            reply = null,
            mediaCaption = null
        )
    }
    if (composerLaunch) {
        Box(
            Modifier.fillMaxWidth().alpha(if (showOutgoing) 1f else 0f)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ComposerLaunchBubble(
                    outgoingMessage,
                    Modifier.onGloballyPositioned { onTargetPositioned(it.boundsInWindow()) }
                )
            }
        }
    } else {
        MessageAnimationFrame(
            animate = animate && config.messageAnimations.enabled,
            outgoing = true,
            config = config.messageAnimations
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ComposerLaunchBubble(outgoingMessage)
            }
        }
    }
    if (!compact) {
        MessageAnimationFrame(
            animate = animate && config.messageAnimations.enabled,
            outgoing = false,
            config = config.messageAnimations
        ) {
            PreviewIncomingBubble(
                text = mediaCaption,
                time = replyTime,
                reply = null,
                mediaCaption = mediaCaption
            )
        }
        PreviewServiceChip(serviceText)
    }
}

@Composable
private fun PreviewChatHeader(contact: String) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(palette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(contact.take(1), color = palette.accentInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(contact, color = palette.textPrimary, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.presence_online), color = palette.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Icon(Icons.Rounded.MoreVert, null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PreviewDateChip(text: String) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(
            text,
            color = palette.textSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(palette.surface2)
                .padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun PreviewServiceChip(text: String) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(
            text,
            color = palette.textSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(palette.surface2)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PreviewIncomingBubble(
    text: String,
    time: String,
    reply: String?,
    mediaCaption: String?
) {
    val palette = beyGramPalette()
    val metrics = LocalBubbleMetrics.current
    val messageSize = LocalMessageTextSize.current
    val lineSpacing = LocalMessageLineSpacing.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        BubbleLayout(
            modifier = Modifier.widthIn(max = 292.dp).clip(bubbleShape(outgoing = false, metrics))
                .background(palette.incomingBubble),
            overlayMeta = false,
            body = {
                Column(
                    Modifier.padding(
                        start = metrics.textInset,
                        end = metrics.textInset,
                        top = metrics.textTopInset
                    ),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (reply != null) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(metrics.replyRadius))
                                .background(palette.accentSoft)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(reply, color = palette.accentInk, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (mediaCaption != null) {
                        Box(
                            Modifier.fillMaxWidth().height(if (text == mediaCaption) 72.dp else 64.dp)
                                .clip(RoundedCornerShape(metrics.replyRadius))
                                .background(
                                    Brush.linearGradient(
                                        listOf(palette.accentSoft, palette.accent.copy(alpha = 0.28f))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Image, null, tint = palette.accentInk, modifier = Modifier.size(24.dp))
                        }
                    }
                    Text(
                        text,
                        color = palette.messageText,
                        fontSize = messageSize,
                        lineHeight = (messageSize.value * lineSpacing).sp,
                        maxLines = if (mediaCaption != null) 2 else 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            meta = { PreviewBubbleMeta(time, outgoing = false) }
        )
    }
}

@Composable
private fun PreviewBubbleMeta(time: String, outgoing: Boolean) {
    val palette = beyGramPalette()
    Row(
        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(time, color = palette.messageTime, fontSize = 11.sp, lineHeight = 15.sp)
        if (outgoing) {
            Icon(Icons.Rounded.DoneAll, null, tint = palette.accent, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun PreviewComposer(
    placeholder: String,
    fieldPositioned: (Rect) -> Unit,
    onSend: (() -> Unit)?
) {
    val palette = beyGramPalette()
    val replayDescription = stringResource(R.string.theme_animations_replay)
    Row(
        Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.weight(1f).defaultMinSize(minHeight = 40.dp)
                .clip(CircleShape).background(palette.surface2)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .onGloballyPositioned { fieldPositioned(it.boundsInWindow()) }
        ) {
            Text(
                placeholder,
                color = palette.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(palette.accent)
                .then(
                    if (onSend != null) Modifier.clickable(role = Role.Button, onClick = onSend)
                        .semantics { contentDescription = replayDescription }
                    else Modifier
                ),
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

/** The full-screen demonstration opened from the overview or the animation editor. */
@Composable
internal fun AppearanceFullPreviewPage(
    config: AppearanceConfig,
    systemDark: Boolean,
    replayKey: Int,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppearanceChatPreview(
        config = config,
        compact = false,
        animate = true,
        replayKey = replayKey,
        modifier = modifier.fillMaxSize(),
        systemDark = systemDark,
        onSend = onReplay
    )
}

private const val PREVIEW_OUTGOING_ID = -7L
private const val PREVIEW_REPLY_ID = 2L
private const val PREVIEW_LAUNCH_KEY = 9L
