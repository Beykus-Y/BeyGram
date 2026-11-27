package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.ui.components.BeyGramPopover
import com.beykus.beygram.ui.components.BeyGramPopoverItem
import com.beykus.beygram.ui.design.panelFill
import com.beykus.beygram.ui.design.PanelFloor
import com.beykus.beygram.ui.design.signatureChrome
import com.beykus.beygram.ui.design.HairlineEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.TopBarVariant
import com.beykus.beygram.ui.design.usesGlassPanels
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.data.presence.LastSeenStyle
import com.beykus.beygram.data.settings.ForumNavigationMode
import com.beykus.beygram.ui.components.ChatAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.resolve
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A caller without custom-emoji delivery still gets a stable flow instead of a fresh one on every recomposition. */
private val NoCustomEmoji: StateFlow<CustomEmoji?> = MutableStateFlow(null)
private val NoCustomEmojiLookup: (Long) -> StateFlow<CustomEmoji?> = { NoCustomEmoji }

@Composable
internal fun ChatTopBar(
    chat: ChatPreview, info: ProfileInfo?, lastSeenStyle: LastSeenStyle, onBack: () -> Unit, onInfo: () -> Unit, onSearch: () -> Unit,
    observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit, forumTopicTitle: String? = null,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?> = NoCustomEmojiLookup,
    forumTopic: ForumTopic? = null,
    hasUnread: Boolean = false, onSelectMessages: () -> Unit = {}, onMarkUnread: () -> Unit = {}, onJumpUnread: () -> Unit = {},
    onScheduled: () -> Unit = {}, onChatSettings: () -> Unit = {}, onStatistics: (() -> Unit)? = null,
    onExport: () -> Unit = {}, secretChat: SecretChatDetails? = null,
    /** The pinned preview is drawn directly under this header, so it closes the group with the one hairline. */
    pinnedBelow: Boolean = false,
    /** Pins in the chat: above zero the overflow menu can open the pinned list even while the bar is hidden. */
    pinnedCount: Int = 0,
    onPinnedHeaderMeasured: (Rect?) -> Unit = {},
    onOpenPinned: () -> Unit = {},
    /** False in the split layout: the list is already on screen, so an arrow that "goes back" would promise nothing new. */
    showBack: Boolean = true,
    /** A short window: one 48 dp line with the status beside the title instead of under it. */
    compact: Boolean = false,
    /** The forum's view of its topics for the menu item; ordinary chats pass null and get no such item. */
    forumNavigationMode: ForumNavigationMode? = null,
    onForumNavigationMode: ((ForumNavigationMode) -> Unit)? = null,
    /** The all-messages feed: the subtitle names the feed instead of the chat's presence. */
    forumAllMessages: Boolean = false,
    /** The shared-element transition to the profile (#999); null keeps the header reporting off. */
    profileTransition: com.beykus.beygram.ui.motion.ChatProfileHeaderSource? = null
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    var menuOpen by remember { mutableStateOf(false) }
    var modeSheetOpen by remember { mutableStateOf(false) }
    // Время в строке статуса пересчитывается отдельно от экрана: тик перерисовывает только шапку.
    val now by rememberPresenceClock(info?.status is ProfilePresence.LastSeen)
    val chatInfoLabel = stringResource(R.string.a11y_chat_info)
    val headerView = androidx.compose.ui.platform.LocalView.current
    val reportHeader by androidx.compose.runtime.rememberUpdatedState(onPinnedHeaderMeasured)
    DisposableEffect(chat.id, forumTopicTitle) { onDispose { reportHeader(null) } }
    val onGlass = LocalChatChromeOnGlass.current
    // The window identity the header bounds are captured with; a resize invalidates them.
    val transitionFingerprint = profileTransition?.let {
        val container = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
        val densityScale = LocalDensity.current.density
        remember(container, densityScale) {
            com.beykus.beygram.ui.motion.ChatProfileWindowFingerprint(container.width, container.height, densityScale)
        }
    }
    Column(Modifier.fillMaxWidth().then(
        if (onGlass) Modifier else when (design.components.topBar) {
            TopBarVariant.Elevated -> Modifier.shadow(design.surfaces.barElevation).background(palette.surface)
            TopBarVariant.Glass -> Modifier.background(design.surfaces.panelFill(palette.surface, PanelFloor.Bar))
            // Signature: the header shares the canvas with the messages, one hairline marks where it ends. The pinned
            // preview below closes the group, so its own hairline is the single edge of the two.
            TopBarVariant.Signature -> if (pinnedBelow) Modifier.background(palette.screen)
                else Modifier.signatureChrome(palette.screen, palette.divider, HairlineEdge.Bottom)
            TopBarVariant.Flat -> Modifier.background(palette.surface)
        }
    )) {
        Row(
            Modifier.fillMaxWidth().onGloballyPositioned { reportHeader(it.pinnedBoundsOnScreen(headerView)) }
                .padding(start = 8.dp, end = 8.dp, top = if (compact) 0.dp else 6.dp, bottom = if (compact) 0.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), tint = palette.textPrimary) }
            // Avatar and title share one focus stop: TalkBack should not visit the same "chat info" target twice.
            // While a flight carries the header elements they are invisible, so they must not be tappable either.
            Row(
                Modifier
                    .weight(1f).heightIn(min = 48.dp)
                    .graphicsLayer { alpha = profileTransition?.headerVisibility() ?: 1f }
                    .clickable(
                        role = Role.Button,
                        onClickLabel = chatInfoLabel,
                        enabled = profileTransition == null || !profileTransition.flightActive(),
                        onClick = onInfo
                    )
                    .semantics(mergeDescendants = true) { role = Role.Button },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // A topic is identified by its own server icon; the parent group moves into the subtitle instead of
                // pretending that the group's presence is the topic's status.
                val statusText = when {
                    forumTopic != null -> stringResource(R.string.forum_topic_in_group, chat.title)
                    forumAllMessages -> stringResource(R.string.forum_all_messages)
                    chat.isSecret -> secretChatStatusLine(secretChat)
                    else -> chatPresenceLine(chat, info, lastSeenStyle, now).resolve()
                }
                val avatarSize = if (compact) 32.dp else 40.dp
                val avatarReport = if (profileTransition != null && transitionFingerprint != null) {
                    Modifier.onGloballyPositioned { coordinates ->
                        profileTransition.reportAvatar(coordinates.boundsInWindow(), transitionFingerprint)
                    }
                } else Modifier
                if (forumTopic != null) {
                    ForumTopicIcon(
                        topic = forumTopic,
                        size = avatarSize,
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        observeCustomEmoji = observeCustomEmoji,
                        modifier = Modifier.clearAndSetSemantics {}.then(avatarReport)
                    )
                } else {
                    ChatAvatar(chat, avatarSize, observeFile, downloadFile, Modifier.clearAndSetSemantics {}.then(avatarReport))
                }
                Spacer(Modifier.width(if (compact) 10.dp else 12.dp))
                // At a large font the compact line gives up the status before it cuts the title.
                // The parent group is the topic's only context; it keeps its own line even in the compact header.
                val statusInline = compact && forumTopic == null && LocalDensity.current.fontScale < 1.3f
                val topicTitle = forumTopic?.let { forumTopicDisplayTitle(it) }
                val titleText = topicTitle ?: forumTopicTitle?.takeIf { it.isNotBlank() } ?: chat.title
                val titleRow: @Composable () -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(titleText, color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier
                            .weight(1f, fill = false)
                            .then(if (profileTransition != null && transitionFingerprint != null) {
                                Modifier.onGloballyPositioned { coordinates ->
                                    profileTransition.reportTitle(coordinates.boundsInWindow(), transitionFingerprint)
                                }
                            } else Modifier))
                        if (forumTopic?.isClosed == true) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = stringResource(R.string.forum_topic_closed),
                                tint = palette.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        if (chat.isSecret && forumTopicTitle == null) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = stringResource(R.string.a11y_secret_chat_lock),
                                tint = palette.accent,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        if (statusInline) {
                            Text(
                                " · $statusText",
                                color = palette.textSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .then(if (profileTransition != null && transitionFingerprint != null) {
                                        Modifier.onGloballyPositioned { coordinates ->
                                            profileTransition.reportStatus(coordinates.boundsInWindow(), transitionFingerprint)
                                        }
                                    } else Modifier)
                            )
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    titleRow()
                    if (!compact || forumTopic != null) {
                        Text(
                            statusText,
                            color = palette.textSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .then(if (profileTransition != null && transitionFingerprint != null) {
                                    Modifier.onGloballyPositioned { coordinates ->
                                        profileTransition.reportStatus(coordinates.boundsInWindow(), transitionFingerprint)
                                    }
                                } else Modifier)
                        )
                    }
                }
            }
            // Scheduled messages stay accessible from overflow, without permanently consuming
            // an entire title-width action slot in every ordinary chat.
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, stringResource(R.string.chat_search_description), tint = palette.textSecondary) }
            var overflowBounds by remember { mutableStateOf<androidx.compose.ui.unit.IntRect?>(null) }
            Box(
                Modifier.onGloballyPositioned { coords ->
                    val r = coords.boundsInWindow()
                    overflowBounds = androidx.compose.ui.unit.IntRect(
                        r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()
                    )
                }
            ) {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.feature_more_actions), tint = palette.textSecondary) }
                BeyGramPopover(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    sameWindowAnchorBounds = overflowBounds.takeIf { LocalDesignSystem.current.usesGlassPanels }
                ) {
                    BeyGramPopoverItem(
                        text = stringResource(R.string.chat_jump_to_unread),
                        leadingIcon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = null, tint = if (hasUnread) palette.textSecondary else palette.textSecondary.copy(alpha = 0.4f)) },
                        enabled = hasUnread,
                        onClick = { menuOpen = false; onJumpUnread() }
                    )
                    if (!chat.isSecret) BeyGramPopoverItem(
                        text = stringResource(R.string.chat_scheduled_title),
                        leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null, tint = palette.textSecondary) },
                        onClick = { menuOpen = false; onScheduled() }
                    )
                    BeyGramPopoverItem(
                        text = stringResource(R.string.chat_mark_unread),
                        leadingIcon = { Icon(Icons.Rounded.MarkChatUnread, contentDescription = null, tint = palette.textSecondary) },
                        onClick = { menuOpen = false; onMarkUnread() }
                    )
                    // The pinned list stays reachable even when the bar is hidden locally.
                    if (pinnedCount > 0) BeyGramPopoverItem(
                        text = stringResource(R.string.chat_pinned_title),
                        leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null, tint = palette.textSecondary) },
                        onClick = { menuOpen = false; onOpenPinned() }
                    )
                    BeyGramPopoverItem(
                        text = stringResource(R.string.chat_export_title),
                        leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null, tint = palette.textSecondary) },
                        onClick = { menuOpen = false; onExport() }
                    )
                    BeyGramPopoverItem(
                        text = stringResource(R.string.message_action_select),
                        leadingIcon = { Icon(Icons.Rounded.CheckCircleOutline, contentDescription = null, tint = palette.textSecondary) },
                        onClick = { menuOpen = false; onSelectMessages() }
                    )
                    BeyGramPopoverItem(
                        text = stringResource(R.string.chat_settings_title),
                        leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null, tint = palette.textSecondary) },
                        onClick = { menuOpen = false; onChatSettings() }
                    )
                    if (forumNavigationMode != null && onForumNavigationMode != null) BeyGramPopoverItem(
                        text = stringResource(R.string.forum_navigation_title),
                        leadingIcon = { Icon(Icons.Rounded.Forum, contentDescription = null, tint = palette.textSecondary) },
                        onClick = { menuOpen = false; modeSheetOpen = true }
                    )
                    if (onStatistics != null) BeyGramPopoverItem(
                        text = stringResource(R.string.channel_statistics_title),
                        leadingIcon = { Icon(Icons.Rounded.BarChart, contentDescription = null, tint = palette.textSecondary) },
                        onClick = { menuOpen = false; onStatistics() }
                    )
                }
                val currentMode = forumNavigationMode
                val setMode = onForumNavigationMode
                if (modeSheetOpen && currentMode != null && setMode != null) {
                    ForumNavigationModeSheet(
                        current = currentMode,
                        onSelect = { mode ->
                            modeSheetOpen = false
                            setMode(mode)
                        },
                        onDismiss = { modeSheetOpen = false }
                    )
                }
            }
        }
        // Liquid Glass with the pinned preview below: the preview's own hairline is the single edge of the group.
        if (!onGlass && !pinnedBelow) HorizontalDivider(color = palette.divider)
    }
}
