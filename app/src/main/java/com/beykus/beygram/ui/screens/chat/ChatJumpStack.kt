package com.beykus.beygram.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.usesGlassPanels
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.fadeEnter
import com.beykus.beygram.ui.motion.fadeExit
import com.beykus.beygram.ui.motion.rememberMotionSpec
import kotlinx.coroutines.launch

/**
 * Combined bottom-end jump controls for the chat screen: unread reactions (♡), unread mentions (@),
 * and jump-to-latest button. Kept in a non-inline composable so ChatScreen stays well under the JVM method size limit.
 */
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
    jumpLatestButton: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
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
                onClick = {
                    scope.launch {
                        conversation.nextUnreadReaction(chatId, forumTopicId).getOrNull()?.let { id ->
                            conversation.viewMessageAgain(chatId, id)
                            onJumpToMessage(id)
                        }
                    }
                }
            )
        }
        AnimatedVisibility(visible = unreadMentions > 0, enter = enter, exit = exit) {
            ChatFloatingBadgeButton(
                icon = Icons.Rounded.AlternateEmail,
                count = unreadMentions,
                label = stringResource(R.string.chat_jump_to_mention),
                onClick = {
                    scope.launch {
                        conversation.nextUnreadMention(chatId, forumTopicId).getOrNull()?.let { id ->
                            conversation.viewMessageAgain(chatId, id)
                            onJumpToMessage(id)
                        }
                    }
                }
            )
        }
        AnimatedVisibility(visible = showJumpLatest, enter = enter, exit = exit) {
            jumpLatestButton()
        }
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
    onClick: () -> Unit
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
            }.clickable(role = Role.Button, onClick = onClick),
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
