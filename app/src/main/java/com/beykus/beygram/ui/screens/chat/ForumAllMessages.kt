package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.flow.StateFlow

/*
 * The all-messages feed of a forum: the chat's own history, which TDLib returns for every topic at once, with the topic of
 * each message named above it. Nothing here keeps a copy of the messages.
 */

/** The topic a message of the feed belongs to. A message without a topic id is in the General topic. */
internal fun forumFeedTopicId(message: ChatMessage, generalTopicId: Long): Long =
    if (message.forumTopicId > 0L) message.forumTopicId else generalTopicId

/** Where a message sent from the feed goes. */
internal sealed interface ForumFeedSendTarget {
    /**
     * A reply. The repository sends it into the topic of the message it answers, so [replyTopicId] only names that topic for
     * the composer's own bookkeeping; it is 0 when the replied message is not loaded.
     */
    data class Reply(val replyTopicId: Long) : ForumFeedSendTarget

    /** A message without a reply goes to the General topic. */
    data class General(val topicId: Long) : ForumFeedSendTarget

    /** The General topic is missing (hidden), or it cannot take messages (closed, read-only): the composer waits for a choice. */
    data object Unavailable : ForumFeedSendTarget
}

/**
 * Chooses where the composer of the feed sends. A reply always has a target, because TDLib takes the reply's own topic. Without a
 * reply, the General topic from the topics list must exist and accept messages; [generalTopic] is null when it is hidden.
 */
internal fun forumFeedSendTarget(replyToMessageId: Long?, replyTopicId: Long, generalTopic: ForumTopic?): ForumFeedSendTarget = when {
    replyToMessageId != null && replyToMessageId != 0L -> ForumFeedSendTarget.Reply(replyTopicId)
    generalTopic != null && generalTopic.canWrite -> ForumFeedSendTarget.General(generalTopic.id)
    else -> ForumFeedSendTarget.Unavailable
}

/** Oldest first, then by id, the same order the timeline uses for a chat. */
private val feedChronology = compareBy<ChatMessage> { it.dateSeconds }.thenBy { it.id }

/**
 * The topic label that opens above the first message of each run of one topic. [items] are the timeline rows, newest first;
 * the result maps a row key to the topic its header names. A row without messages, such as the unread separator, ends a run.
 */
internal fun forumFeedTopicHeaders(items: List<ChatMessageListItem>, topicOf: (ChatMessage) -> Long): Map<String, Long> {
    val headers = HashMap<String, Long>()
    var previousTopic: Long? = null
    for (item in items.asReversed()) {
        if (item.messages.isEmpty()) {
            previousTopic = null
            continue
        }
        // An album's members normally share one topic; the first and the last decide whether the row starts a run.
        val topic = topicOf(item.messages.minWith(feedChronology))
        if (topic != previousTopic) headers[item.key] = topic
        previousTopic = topicOf(item.messages.maxWith(feedChronology))
    }
    return headers
}

/**
 * The topic label above a message of the feed. A known topic shows its icon and name and, when [onOpen] is set, opens that
 * topic on a tap. A topic missing from the topics list shows a generic label and cannot be opened.
 */
@Composable
internal fun ForumFeedTopicHeader(
    topic: ForumTopic?,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>,
    onOpen: ((ForumTopic) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val palette = beyGramPalette()
    val title = if (topic != null) forumTopicDisplayTitle(topic) else stringResource(R.string.forum_all_messages_unknown_topic)
    val open = onOpen
    val openLabel = stringResource(R.string.forum_all_messages_open_topic)
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (topic != null && open != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = openLabel) { open(topic) }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (topic != null) {
            ForumTopicIcon(
                topic = topic,
                size = 18.dp,
                observeFile = observeFile,
                downloadFile = downloadFile,
                observeCustomEmoji = observeCustomEmoji
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            title,
            color = palette.accentInk,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
