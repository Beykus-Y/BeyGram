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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.beykus.beygram.ui.design.GlassPanel
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.usesGlassPanels
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.fadeEnter
import com.beykus.beygram.ui.motion.fadeExit
import com.beykus.beygram.ui.motion.rememberMotionSpec
import com.beykus.beygram.ui.motion.scrollToItemRespectingMotion
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CoroutineScope
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
internal fun ChatFloatingBadgeButton(
    icon: ImageVector,
    count: Int,
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    enabled: Boolean = true
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    Box(
        Modifier.size(44.dp).testTag("chat_floating_badge_button").semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (design.usesGlassPanels) {
            GlassPanel(
                design = design,
                tint = palette.surface,
                rim = palette.textSecondary,
                backdropBase = palette.screen,
                shape = CircleShape,
                modifier = Modifier.fillMaxSize()
                    .combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp).testTag("chat_floating_badge_glyph"))
                }
            }
        } else {
            Box(
                Modifier.fillMaxSize().shadow(elevation = 4.dp, shape = CircleShape).clip(CircleShape).background(palette.surface)
                    .combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick, onLongClick = onLongClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp).testTag("chat_floating_badge_glyph"))
            }
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

@Composable
internal fun BoxScope.ChatTimelineJumpOverlays(
    listState: LazyListState,
    items: List<ChatMessageListItem>,
    searching: Boolean,
    chatId: Long,
    forumTopicId: Long,
    unread: Int,
    unreadMentions: Int,
    unreadReactions: Int,
    hasNewerMessages: Boolean,
    conversation: ConversationRepository,
    scope: CoroutineScope,
    listScrollInstant: State<Boolean>,
    onJumpToMessage: (Long) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    if (!searching) FloatingMessageDate(listState, items, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
    val away by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
    ChatJumpControls(
        chatId = chatId,
        forumTopicId = forumTopicId,
        unreadMentions = unreadMentions,
        unreadReactions = unreadReactions,
        showJumpLatest = away || hasNewerMessages,
        conversation = conversation,
        onJumpToMessage = onJumpToMessage,
        onReadAllFailed = { onError(context.getString(R.string.chat_read_all_failed)) },
        onJumpEmpty = { onError(context.getString(R.string.global_search_empty)) },
        onJumpFailed = { failure ->
            onError(failure?.userMessage(context, R.string.chat_search_failed)
                ?: context.getString(R.string.err_tdlib_request_timeout))
        },
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp)
    ) {
        JumpToLatestButton(unread = unread, onClick = {
            if (hasNewerMessages) {
                if (forumTopicId > 0L) conversation.loadForumTopicHistory(chatId, forumTopicId)
                else conversation.loadLatestMessages(chatId)
            }
            scope.launch {
                if (shouldAnimateJumpToLatest(listScrollInstant.value, hasNewerMessages)) {
                    listState.scrollToItemRespectingMotion(0, motionInstant = listScrollInstant)
                } else {
                    listState.scrollToItem(0)
                }
            }
        })
    }
}

@Composable
internal fun JumpToLatestButton(unread: Int, onClick: () -> Unit) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val label = stringResource(R.string.chat_jump_to_latest)
    Box(
        Modifier.size(48.dp).testTag("jump_to_latest_button").semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (design.usesGlassPanels) {
            GlassPanel(
                design = design,
                tint = palette.surface,
                rim = palette.textSecondary,
                backdropBase = palette.screen,
                shape = CircleShape,
                modifier = Modifier.fillMaxSize().clickable(role = Role.Button, onClick = onClick)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp).testTag("jump_to_latest_glyph"))
                }
            }
        } else {
            Box(
                Modifier.fillMaxSize()
                    .shadow(elevation = 4.dp, shape = CircleShape).clip(CircleShape).background(palette.surface)
                    .clickable(role = Role.Button, onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ArrowDownward, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp).testTag("jump_to_latest_glyph"))
            }
        }
        // The badge sits outside the clipped circle so the corner offset does not crop it.
        if (unread > 0) Box(
            Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)
                .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                .clip(CircleShape).background(palette.accent).padding(horizontal = 4.dp)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center
        ) {
            Text(if (unread > 99) "99+" else unread.toString(), color = palette.onAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
