package com.beykus.beygram.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.UnreadMarkCounts
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.usesGlassPanels
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.fadeEnter
import com.beykus.beygram.ui.motion.fadeExit
import com.beykus.beygram.ui.motion.rememberMotionSpec
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private enum class UnreadMark { Reaction, Mention }

/**
 * Combined bottom-end jump controls for the chat screen: unread reactions (♡), unread mentions (@),
 * and jump-to-latest button. Kept in a non-inline composable so ChatScreen stays well under the JVM method size limit.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatJumpControls(
    chatId: Long,
    forumTopicId: Long,
    unreadMentions: Int,
    unreadReactions: Int,
    showJumpLatest: Boolean,
    conversation: ConversationRepository,
    onJumpToMessage: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onReadAllFailed: () -> Unit = {},
    onJumpEmpty: () -> Unit,
    onJumpFailed: (Throwable?) -> Unit,
    jumpLatestButton: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val motion = rememberMotionSpec(MotionToken.BannerChange)
    val enter = if (motion.isInstant) EnterTransition.None else motion.fadeEnter() + slideInVertically(motion.animationSpec()) { it / 2 }
    val exit = if (motion.isInstant) ExitTransition.None else motion.fadeExit() + slideOutVertically(motion.animationSpec()) { it / 2 }
    // The account's repository and the exact chat/topic own both the lookup and its cancellation.
    var pendingMark by remember(conversation, chatId, forumTopicId) { mutableStateOf<UnreadMark?>(null) }
    var readAllMentions by remember(conversation, chatId, forumTopicId) { mutableStateOf<Boolean?>(null) }
    // A later live count invalidates the snapshot, even while the request for that snapshot is suspended.
    val refreshedCounts = remember(conversation, chatId, forumTopicId, unreadMentions, unreadReactions) {
        mutableStateOf<UnreadMarkCounts?>(null)
    }
    val latestCountSlot by rememberUpdatedState(refreshedCounts)
    val shownMentions = refreshedCounts.value?.mentions ?: unreadMentions
    val shownReactions = refreshedCounts.value?.reactions ?: unreadReactions
    val currentJump by rememberUpdatedState(onJumpToMessage)
    val currentEmpty by rememberUpdatedState(onJumpEmpty)
    val currentFailure by rememberUpdatedState(onJumpFailed)
    LaunchedEffect(conversation, chatId, forumTopicId, pendingMark) {
        val mark = pendingMark ?: return@LaunchedEffect
        val countSlot = latestCountSlot
        try {
            val result = withTimeoutOrNull(8_000) {
                when (mark) {
                    UnreadMark.Mention -> conversation.nextUnreadMention(chatId, forumTopicId)
                    UnreadMark.Reaction -> conversation.nextUnreadReaction(chatId, forumTopicId)
                }
            }
            currentCoroutineContext().ensureActive()
            if (result == null) {
                currentFailure(null)
            } else {
                result.fold(onSuccess = { id ->
                    if (id == null) {
                        val snapshot = withTimeoutOrNull(3_000) {
                            conversation.unreadMarkCounts(chatId, forumTopicId).getOrNull()
                        }
                        currentCoroutineContext().ensureActive()
                        if (snapshot != null && countSlot === latestCountSlot) countSlot.value = snapshot
                        currentEmpty()
                    } else {
                        conversation.viewMessageAgain(chatId, id)
                        currentJump(id)
                    }
                }, onFailure = { currentFailure(it) })
            }
        } finally {
            pendingMark = null
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(visible = shownReactions > 0, enter = enter, exit = exit) {
            ChatFloatingBadgeButton(
                icon = Icons.Rounded.Favorite,
                count = shownReactions,
                label = stringResource(R.string.chat_jump_to_reaction),
                enabled = pendingMark == null,
                onClick = { if (pendingMark == null) pendingMark = UnreadMark.Reaction },
                onLongClick = { readAllMentions = false }
            )
        }
        AnimatedVisibility(visible = shownMentions > 0, enter = enter, exit = exit) {
            ChatFloatingBadgeButton(
                icon = Icons.Rounded.AlternateEmail,
                count = shownMentions,
                label = stringResource(R.string.chat_jump_to_mention),
                enabled = pendingMark == null,
                onClick = { if (pendingMark == null) pendingMark = UnreadMark.Mention },
                onLongClick = { readAllMentions = true }
            )
        }
        AnimatedVisibility(visible = showJumpLatest, enter = enter, exit = exit) {
            jumpLatestButton()
        }
    }
    readAllMentions?.let { mentions ->
        BeyGramDialog(
            onDismissRequest = { readAllMentions = null },
            title = stringResource(if (mentions) R.string.chat_read_all_mentions_title else R.string.chat_read_all_reactions_title),
            message = stringResource(if (mentions) R.string.chat_read_all_mentions_message else R.string.chat_read_all_reactions_message),
            confirmText = stringResource(R.string.chat_read_all_confirm),
            onConfirm = {
                readAllMentions = null
                scope.launch { conversation.readAllMarks(chatId, forumTopicId, mentions).onFailure { onReadAllFailed() } }
            },
            dismissText = stringResource(R.string.common_cancel),
            onDismiss = { readAllMentions = null }
        )
    }
}

/**
 * Floating jump badges for unread mentions (@) and unread reactions (♡) stacked cleanly above the
 * chat's JumpToLatestButton. Respects reduced motion and Liquid Glass styling.
 */
@Composable
internal fun ChatUnreadBadgesColumn(
    unreadMentions: Int,
    unreadReactions: Int,
    onJumpMention: () -> Unit,
    onJumpReaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = rememberMotionSpec(MotionToken.BannerChange)
    val enter = if (motion.isInstant) EnterTransition.None else motion.fadeEnter() + slideInVertically(motion.animationSpec()) { it / 2 }
    val exit = if (motion.isInstant) ExitTransition.None else motion.fadeExit() + slideOutVertically(motion.animationSpec()) { it / 2 }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(visible = unreadReactions > 0, enter = enter, exit = exit) {
            ChatFloatingBadgeButton(
                icon = Icons.Rounded.Favorite,
                count = unreadReactions,
                label = stringResource(R.string.chat_jump_to_reaction),
                onClick = onJumpReaction
            )
        }
        AnimatedVisibility(visible = unreadMentions > 0, enter = enter, exit = exit) {
            ChatFloatingBadgeButton(
                icon = Icons.Rounded.AlternateEmail,
                count = unreadMentions,
                label = stringResource(R.string.chat_jump_to_mention),
                onClick = onJumpMention
            )
        }
    }
}

@Composable
private fun ChatFloatingBadgeButton(
    icon: ImageVector,
    count: Int,
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    enabled: Boolean = true
) {
    val palette = beyGramPalette()
    Box(
        Modifier.size(44.dp).semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.fillMaxSize().let { m ->
                if (LocalDesignSystem.current.usesGlassPanels) m.clip(CircleShape).background(palette.surface.copy(alpha = 0.72f))
                    .border(0.5.dp, palette.textSecondary.copy(alpha = 0.35f), CircleShape)
                else m.shadow(elevation = 4.dp, shape = CircleShape).clip(CircleShape).background(palette.surface)
            }.combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick, onLongClick = onLongClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
        }
        if (count > 0) Box(
            Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)
                .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                .clip(CircleShape).background(palette.accent).padding(horizontal = 4.dp)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (count > 99) "99+" else count.toString(),
                color = palette.onAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
