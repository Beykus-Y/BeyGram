package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.ui.components.LocalWindowLayout
import com.beykus.beygram.ui.components.StyledListDivider
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.screens.chats.ChatListDensity
import com.beykus.beygram.ui.screens.chats.LocalChatListDensity
import com.beykus.beygram.ui.text.resolve
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One dense forum row, built like a chat-list row: no card, a real topic tile, a title and one preview line, then the
 * chat-list time and unread marks. Long press is the only management affordance; the row never carries a menu button.
 */
@Composable
internal fun ForumTopicRow(
    topic: ForumTopic,
    parentMuted: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)?,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>,
    modifier: Modifier = Modifier
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val main = design.mainScreen
    val listDensity = LocalChatListDensity.current
    val shortWindow = LocalWindowLayout.current.heightCompact
    val defaultPadding = LocalChatListDensity.current.rowVerticalPadding
    val rowPadding = if (shortWindow && defaultPadding == ChatListDensity().rowVerticalPadding) {
        6.dp
    } else {
        listDensity.rowVerticalPadding
    }
    val tileSize = if (shortWindow) minOf(main.avatarSize, 44.dp) else main.avatarSize
    val tileGap = 14.dp
    val dividerIndent = 20.dp + tileSize + tileGap
    val fontScale = LocalDensity.current.fontScale
    // A long topic name needs a second line once the system text is large, even though the chat list keeps one line.
    val titleMaxLines = if (fontScale >= 1.3f) 2 else 1
    val muted = topic.isMutedIn(parentMuted)
    val unread = topic.hasUnread
    val title = forumTopicDisplayTitle(topic)
    val preview = forumTopicPreview(topic)
    val previewColor = when {
        topic.lastMessage?.sendState == MessageSendState.Failed -> palette.warningText
        unread && !muted && main.unreadPreviewEmphasis -> palette.textPrimary
        else -> palette.textSecondary
    }
    val time = forumTopicTimeLabel(topic.lastMessage?.dateSeconds ?: 0L)
    val manageLabel = if (onLongPress != null) stringResource(R.string.forum_manage) else null
    val description = forumTopicStateDescription(topic, muted)
    val tileShape = RoundedCornerShape(main.rowRadius)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (main.rowInset > 0.dp) Modifier.padding(horizontal = main.rowInset).clip(tileShape) else Modifier)
                .combinedClickable(
                    enabled = enabled,
                    onClickLabel = null,
                    onLongClickLabel = manageLabel,
                    onLongClick = onLongPress,
                    role = Role.Button,
                    onClick = onOpen
                )
                .semantics(mergeDescendants = true) {
                    if (description.isNotEmpty()) stateDescription = description
                }
                .padding(horizontal = (20.dp - main.rowInset).coerceAtLeast(0.dp), vertical = rowPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ForumTopicIcon(
                topic = topic,
                size = tileSize,
                observeFile = observeFile,
                downloadFile = downloadFile,
                observeCustomEmoji = observeCustomEmoji
            )
            Spacer(Modifier.width(tileGap))
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = if (titleMaxLines > 1) Alignment.Top else Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        color = palette.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = main.rowTitleWeight,
                        maxLines = titleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (topic.isPinned) {
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.padding(start = 5.dp).size(13.dp)
                        )
                    }
                    if (topic.isClosed) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.padding(start = 5.dp).size(13.dp)
                        )
                    }
                    if (time.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            time,
                            color = if (unread) palette.accent else palette.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (muted) {
                        Icon(
                            Icons.Rounded.NotificationsOff,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.padding(end = 5.dp).size(14.dp)
                        )
                    }
                    if (topic.hasDraft) {
                        Text(
                            stringResource(R.string.forum_topic_draft),
                            color = palette.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.clearAndSetSemantics {}
                        )
                        if (topic.draftPreview.isNotBlank()) {
                            Text(
                                " · ${topic.draftPreview}",
                                color = palette.accent,
                                fontSize = 14.sp,
                                maxLines = listDensity.previewLines,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    } else {
                        Text(
                            preview,
                            color = previewColor,
                            fontSize = 14.sp,
                            maxLines = listDensity.previewLines,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // An unread count already owns the trailing edge. Showing read ticks beside it would ask the user to
                    // read two competing delivery states in one glance, so the ticks return only when the topic is read.
                    if (unread) {
                        Row(
                            Modifier.padding(start = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ForumTopicUnreadBadge(topic, muted, main.badgeRadius)
                        }
                    } else if (!topic.hasDraft && topic.lastMessage?.isOutgoing == true) {
                        Row(
                            Modifier.padding(start = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ForumTopicSendStatus(topic)
                        }
                    }
                }
                if (topic.unreadMentionCount > 0 || topic.unreadReactionCount > 0 || topic.unreadPollVoteCount > 0) {
                    Spacer(Modifier.height(3.dp))
                    Row(
                        Modifier.clearAndSetSemantics {},
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (topic.unreadMentionCount > 0) {
                            ForumTopicAttentionMark(
                                Icons.Rounded.AlternateEmail,
                                topic.unreadMentionCount,
                                palette.accent
                            )
                        }
                        if (topic.unreadReactionCount > 0) {
                            ForumTopicAttentionMark(
                                Icons.Rounded.EmojiEmotions,
                                topic.unreadReactionCount,
                                palette.textSecondary
                            )
                        }
                        if (topic.unreadPollVoteCount > 0) {
                            ForumTopicAttentionMark(
                                Icons.Rounded.Poll,
                                topic.unreadPollVoteCount,
                                palette.textSecondary
                            )
                        }
                    }
                }
            }
        }
        StyledListDivider(startIndent = dividerIndent)
    }
}

/** The icon tile: the server colour when present, a custom emoji when it has one, and a real topic glyph otherwise. */
@Composable
internal fun ForumTopicIcon(
    topic: ForumTopic,
    size: Dp,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>,
    modifier: Modifier = Modifier
) {
    val palette = beyGramPalette()
    val serverColor = topic.icon?.colorArgb?.let(::forumTopicIconColor)
    val background = serverColor ?: palette.accentSoft
    val ink = serverColor?.let(::forumTopicIconInk) ?: palette.accentInk
    val emojiId = topic.icon?.customEmojiId ?: 0L
    // The caller passes a bound repository method; keying by the id avoids re-subscribing on every recomposition.
    val emojiFlow = remember(emojiId) {
        if (emojiId != 0L) observeCustomEmoji(emojiId) else MutableStateFlow<CustomEmoji?>(null)
    }
    val emoji by emojiFlow.collectAsStateWithLifecycle()
    val fallback: @Composable () -> Unit = {
        Icon(
            imageVector = if (topic.isGeneral) Icons.Rounded.Forum else Icons.Rounded.Tag,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(size * 0.5f)
        )
    }
    Box(
        modifier.size(size).clip(styledShape(15.dp)).background(background),
        contentAlignment = Alignment.Center
    ) {
        val current = emoji
        if (current != null) {
            CustomEmojiImage(current, size * 0.64f, observeFile, downloadFile, fallback)
        } else {
            fallback()
        }
    }
}

@Composable
private fun ForumTopicUnreadBadge(topic: ForumTopic, muted: Boolean, radius: Dp) {
    val palette = beyGramPalette()
    val badgeColor = if (muted) palette.textSecondary else palette.accent
    // The pill grows with the font scale, so large digits at 2x are not clipped. Manual unread without a count is a dot.
    val badgeScale = LocalDensity.current.fontScale.coerceAtLeast(1f)

    val badgeSize = 22.dp * badgeScale
    if (topic.unreadCount > 0) {
        Box(
            Modifier
                .clearAndSetSemantics {}
                .defaultMinSize(minWidth = badgeSize)
                .height(badgeSize)
                .clip(RoundedCornerShape(radius * badgeScale))
                .background(badgeColor)
                .padding(horizontal = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (topic.unreadCount > 99) "99+" else topic.unreadCount.toString(),
                color = if (muted) palette.surface else palette.onAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        Box(Modifier.clearAndSetSemantics {}.size(10.dp).clip(RoundedCornerShape(50)).background(badgeColor))
    }
}

@Composable
private fun ForumTopicSendStatus(topic: ForumTopic) {
    val palette = beyGramPalette()
    val message = topic.lastMessage ?: return
    if (!message.isOutgoing) return
    val modifier = Modifier.size(16.dp)
    when (message.sendState) {
        MessageSendState.Sending -> Icon(Icons.Rounded.Schedule, null, tint = palette.textSecondary, modifier = modifier)
        MessageSendState.Failed -> Icon(Icons.Rounded.ErrorOutline, null, tint = palette.warningText, modifier = modifier)
        MessageSendState.Sent -> {
            val read = message.messageId > 0L && message.messageId <= topic.lastReadOutboxMessageId
            if (read) {
                Icon(Icons.Rounded.DoneAll, null, tint = palette.accent, modifier = modifier)
            } else {
                Icon(Icons.Rounded.Done, null, tint = palette.textSecondary, modifier = modifier)
            }
        }
    }
}

@Composable
private fun ForumTopicAttentionMark(icon: ImageVector, count: Int, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(2.dp))
        Text(
            if (count > 99) "99+" else count.toString(),
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun forumTopicPreview(topic: ForumTopic): String {
    val message = topic.lastMessage
    val body = message?.preview?.resolve()?.takeIf { it.isNotBlank() }
        ?: topic.lastMessagePreview.takeIf { it.isNotBlank() }
    if (body == null) return stringResource(R.string.forum_topic_recent_activity)
    val author = when {
        // A service event already names the actor ("Alice joined"); prefixing it again reads as a duplicate.
        message?.isService == true -> null
        message?.isOutgoing == true -> stringResource(R.string.chat_you)
        message != null && message.senderName.isNotBlank() -> message.senderName
        else -> null
    }
    return if (author == null) body else stringResource(R.string.forum_topic_preview_sender, author, body)
}

/** Pinned, closed, unread, mention, reaction, muted and draft states, each named once for TalkBack. */
@Composable
private fun forumTopicStateDescription(topic: ForumTopic, muted: Boolean): String {
    val pinned = stringResource(R.string.forum_topic_pinned)
    val closed = stringResource(R.string.forum_topic_closed)
    val mutedLabel = stringResource(R.string.forum_topic_muted)
    val draftLabel = stringResource(R.string.forum_topic_draft)
    val unreadLabel = if (topic.unreadCount > 0) stringResource(R.string.forum_topic_unread_count, topic.unreadCount) else null
    val mentionLabel = if (topic.unreadMentionCount > 0) stringResource(R.string.forum_topic_mentions, topic.unreadMentionCount) else null
    val reactionLabel = if (topic.unreadReactionCount > 0) stringResource(R.string.forum_topic_reactions, topic.unreadReactionCount) else null
    val pollLabel = if (topic.unreadPollVoteCount > 0) stringResource(R.string.forum_topic_poll_votes, topic.unreadPollVoteCount) else null
    // The row hides delivery ticks while a badge or a draft marker owns the trailing edge; TalkBack must match.
    val sendLabel = if (topic.hasUnread || topic.hasDraft) null else forumTopicSendStatusLabel(topic)
    return listOfNotNull(
        pinned.takeIf { topic.isPinned },
        closed.takeIf { topic.isClosed },
        unreadLabel,
        mentionLabel,
        reactionLabel,
        pollLabel,
        mutedLabel.takeIf { muted },
        draftLabel.takeIf { topic.hasDraft },
        sendLabel
    ).joinToString(", ")
}

@Composable
private fun forumTopicSendStatusLabel(topic: ForumTopic): String? {
    val message = topic.lastMessage ?: return null
    if (!message.isOutgoing) return null
    return when (message.sendState) {
        MessageSendState.Sending -> stringResource(R.string.chat_sending_description)
        MessageSendState.Failed -> stringResource(R.string.chat_status_not_sent)
        MessageSendState.Sent -> {
            val read = message.messageId > 0L && message.messageId <= topic.lastReadOutboxMessageId
            stringResource(if (read) R.string.chat_read_description else R.string.chat_sent_description)
        }
    }
}

/** Section labels follow the style: Material 2 keeps capitals, the other styles use sentence case and the token tracking. */
@Composable
internal fun ForumTopicSectionLabel(title: String, count: Int? = null) {
    val palette = beyGramPalette()
    val main = LocalDesignSystem.current.mainScreen
    val locale = LocalConfiguration.current.locales[0]
    val label = remember(title, main.sectionLabelUppercase, locale) {
        val sentence = title.lowercase(locale).replaceFirstChar { it.titlecase(locale) }
        if (main.sectionLabelUppercase) sentence.uppercase(locale) else sentence
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = palette.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = main.sectionLabelTracking,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        if (count != null) {
            Text(count.toString(), color = palette.textSecondary, fontSize = 12.sp)
        }
    }
}

/** The skeleton keeps the new row geometry: the same tile, two text lines and a short time bar, never a card. */
@Composable
internal fun ForumTopicSkeletonRow(modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    val main = LocalDesignSystem.current.mainScreen
    val listDensity = LocalChatListDensity.current
    val shortWindow = LocalWindowLayout.current.heightCompact
    val tileSize = if (shortWindow) minOf(main.avatarSize, 44.dp) else main.avatarSize
    val tileGap = 14.dp
    val defaultPadding = ChatListDensity().rowVerticalPadding
    val rowPadding = if (shortWindow && listDensity.rowVerticalPadding == defaultPadding) 6.dp else listDensity.rowVerticalPadding
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (main.rowInset > 0.dp) Modifier.padding(horizontal = main.rowInset) else Modifier)
                .clearAndSetSemantics {}
                .padding(horizontal = (20.dp - main.rowInset).coerceAtLeast(0.dp), vertical = rowPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(tileSize).clip(styledShape(15.dp)).background(palette.surface2)
            )
            Spacer(Modifier.width(tileGap))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.fillMaxWidth(0.55f).height(12.dp).clip(styledShape(6.dp)).background(palette.surface2))
                Box(Modifier.fillMaxWidth(0.85f).height(10.dp).clip(styledShape(5.dp)).background(palette.surface2))
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(38.dp).height(9.dp).clip(styledShape(5.dp)).background(palette.surface2))
        }
        StyledListDivider(startIndent = 20.dp + tileSize + tileGap)
    }
}

/** Skeleton rows only; the caller gives it the remaining screen height so the header stays usable. */
@Composable
internal fun ForumTopicSkeletonList(rows: Int = 6, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize()) {
        items(rows) { ForumTopicSkeletonRow() }
    }
}
