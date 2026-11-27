package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.data.tdlib.stableColor
import com.beykus.beygram.domain.model.AvatarPhoto
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.ProfileMember
import com.beykus.beygram.domain.model.initialsOf
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.TelegramAvatar
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

private const val AvatarGroupGapSeconds = 5 * 60
private const val MaxCachedGroupAvatars = 128

internal fun ChatPreview.supportsGroupAvatars(): Boolean =
    isGroup && !isChannel && !isSecret && !isSavedMessages

private fun ChatMessage.canShowGroupAvatar(): Boolean =
    !isOutgoing && !isChannelPost && serviceText == null && senderName.isNotBlank()

internal fun groupAvatarRows(
    items: List<ChatMessageListItem>,
    separators: Set<String>
): Map<String, ChatMessage> = buildMap {
    var previous: ChatMessage? = null
    for (item in items.asReversed()) {
        val first = item.messages.minByOrNull { it.id }
        if (first == null || !first.canShowGroupAvatar()) {
            previous = null
            continue
        }
        val prior = previous
        val startsGroup = prior == null || item.key in separators ||
            prior.senderId != first.senderId || prior.senderName != first.senderName ||
            first.dateSeconds - prior.dateSeconds > AvatarGroupGapSeconds
        if (startsGroup) put(item.key, first)
        previous = item.messages.maxByOrNull { it.id }
    }
}

@Composable
internal fun rememberGroupAvatarPhotos(
    conversation: ConversationRepository,
    chatId: Long,
    forumTopicId: Long,
    listState: LazyListState,
    avatars: Map<String, ChatMessage>,
    knownMembers: Map<Long, ProfileMember>
): Map<Long, AvatarPhoto?> {
    val photos = remember(conversation, chatId, forumTopicId) { mutableStateMapOf<Long, AvatarPhoto?>() }
    LaunchedEffect(conversation, chatId, forumTopicId, listState, avatars, knownMembers) {
        if (avatars.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { row ->
                val key = row.key.toString()
                avatars[key]?.senderId?.takeIf { it > 0L && knownMembers[it]?.photo == null }
            }.distinct()
        }.collectLatest { visible ->
            val missing = visible.filterNot(photos::containsKey)
            if (missing.isNotEmpty()) {
                val found = conversation.loadPinnedSenderAvatars(missing)
                val evict = (photos.size + missing.size - MaxCachedGroupAvatars).coerceAtLeast(0)
                photos.keys.filterNot { it in visible }.take(evict).forEach(photos::remove)
                missing.take(MaxCachedGroupAvatars).forEach { id -> photos[id] = found[id] }
            }
        }
    }
    return photos
}

@Composable
internal fun ChatGroupAvatarRow(
    enabled: Boolean,
    message: ChatMessage?,
    avatar: ChatMessage?,
    members: Map<Long, ProfileMember>,
    photos: Map<Long, AvatarPhoto?>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onOpenProfile: ((Long) -> Unit)?,
    peerColors: ConversationRepository? = null,
    content: @Composable () -> Unit
) {
    val userId = message?.senderId?.takeIf { it > 0 }
    val accentFlow = remember(peerColors, userId) {
        if (userId != null) peerColors?.observeUserAccent(userId) else null
    }
    val accent = if (accentFlow != null) {
        key(peerColors, userId) {
            val current by accentFlow.collectAsStateWithLifecycle(initialValue = null)
            current
        }
    } else null
    val palette = beyGramPalette()
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bubble = if (message?.isOutgoing == true) palette.outgoingBubble else palette.incomingBubble
    val author = accent?.authorColor(dark, bubble, palette.messageText) ?: Color.Unspecified
    Row(Modifier.fillMaxWidth()) {
        if (enabled && message?.canShowGroupAvatar() == true) {
            if (avatar == null) {
                Spacer(Modifier.width(44.dp))
            } else {
                val member = members[avatar.senderId]
                val colorKey = if (avatar.senderId > 0) avatar.senderId else avatar.senderName.hashCode().toLong()
                val label = stringResource(R.string.chat_group_sender_avatar, avatar.senderName)
                val action = if (avatar.senderId > 0 && onOpenProfile != null) {
                    Modifier.clickable(role = Role.Button) { onOpenProfile(avatar.senderId) }
                } else Modifier
                Box(Modifier.width(44.dp).padding(start = 8.dp, top = 4.dp)) {
                    TelegramAvatar(
                        initials = initialsOf(avatar.senderName),
                        color = accent?.takeIf { avatar.senderId == userId }?.avatarColor(dark)
                            ?: Color(member?.avatarColorArgb ?: colorKey.stableColor()),
                        size = 36.dp,
                        photo = member?.photo ?: photos[avatar.senderId],
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        modifier = action.semantics { contentDescription = label }
                    )
                }
            }
        }
        Box(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalChatAuthorColor provides author, content = content)
        }
    }
}
