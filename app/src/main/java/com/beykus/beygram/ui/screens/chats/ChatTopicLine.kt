package com.beykus.beygram.ui.screens.chats

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.ForumTopicBrief
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.ForumTopicIconTile
import com.beykus.beygram.ui.screens.chat.forumTopicNameOrFallback
import kotlinx.coroutines.flow.StateFlow

/**
 * The topic of a forum row's last message. The flow is collected only for a row that has a topic and an [observe] function;
 * any other row gets an empty state and asks nothing.
 */
@Composable
internal fun rememberRowTopicBrief(
    chatId: Long,
    topicId: Long,
    observe: ((Long, Long) -> StateFlow<ForumTopicBrief?>)?
): State<ForumTopicBrief?> {
    if (topicId == 0L || observe == null) return remember { mutableStateOf<ForumTopicBrief?>(null) }
    val flow = remember(chatId, topicId) { observe(chatId, topicId) }
    return flow.collectAsStateWithLifecycle()
}

/**
 * The line above a forum row's preview: the topic's icon and name, on one line and ellipsized. While TDLib has not answered,
 * the line keeps its height, so the list does not jump when the name arrives. [onClick] opens that topic. [onLongClick] is
 * the row's own long press, so pressing the line does not swallow it.
 */
@Composable
internal fun ChatTopicLine(
    topic: ForumTopicBrief?,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null
) {
    val palette = beyGramPalette()
    val name = if (topic != null) forumTopicNameOrFallback(topic.title, topic.isGeneral, topic.isNameImplicit) else ""
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
                else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (topic != null) {
            ForumTopicIconTile(
                icon = topic.icon,
                isGeneral = topic.isGeneral,
                size = 16.dp,
                observeFile = observeFile,
                downloadFile = downloadFile,
                observeCustomEmoji = observeCustomEmoji
            )
        } else {
            Spacer(Modifier.size(16.dp))
        }
        Spacer(Modifier.width(5.dp))
        Text(
            name,
            color = palette.textSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
