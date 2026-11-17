package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.settings.ForumNavigationMode
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.components.BeyGramSheet
import com.beykus.beygram.ui.components.SheetActionRow
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.rememberMotionInstant
import com.beykus.beygram.ui.motion.scrollToItemRespectingMotion
import kotlinx.coroutines.flow.StateFlow

/** The narrow rail: an icon with a tiny title under it. */
private val ForumRailWidth = 72.dp

/** The rail on a wide pane: the icon, the title and the count of each topic on one row. */
private val ForumRailExpandedWidth = 240.dp

/** The expanded rail needs a pane this wide, so the chat keeps enough room beside it. */
private val ForumRailExpandedMinPaneWidth = 720.dp

/** Every switch target is at least this tall, even when its visible chip is smaller. */
private val ForumPanelTouchTarget = 48.dp

private val ForumTabTitleMaxWidth = 140.dp

/**
 * The topic a panel view opens first: the topic that was open last in this chat when it still exists, otherwise the first
 * topic of the ordered list. 0 when the forum has no topic to open.
 */
internal fun initialPanelTopicId(ordered: List<ForumTopic>, lastTopicId: Long): Long = when {
    lastTopicId != 0L && ordered.any { it.id == lastTopicId } -> lastTopicId
    else -> ordered.firstOrNull()?.id ?: 0L
}

/**
 * The Tabs view: the forum's topics as a horizontal strip under the chat header. The first item opens the full list. The
 * open topic is kept in view, and a tap switches topics without leaving the chat.
 */
@Composable
fun ForumTopicTabStrip(
    topics: List<ForumTopic>,
    selectedTopicId: Long,
    parentMuted: Boolean,
    onSelect: (ForumTopic) -> Unit,
    onOpenList: () -> Unit,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val palette = beyGramPalette()
    // Inside the glass header the pane behind the strip is the background; a plain header paints its own surface.
    val onGlass = LocalChatChromeOnGlass.current
    val selectedIndex = remember(topics, selectedTopicId) { topics.indexOfFirst { it.id == selectedTopicId } }
    ForumPanelRevealSelected(listState = listState, selectedIndex = selectedIndex)
    Column(modifier.fillMaxWidth().then(if (onGlass) Modifier else Modifier.background(palette.surface))) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "forum-panel-all") { ForumPanelAllTopicsTab(onOpenList = onOpenList) }
            items(topics, key = { "forum-panel-topic:${it.id}" }) { topic ->
                ForumPanelTab(
                    topic = topic,
                    selected = topic.id == selectedTopicId,
                    parentMuted = parentMuted,
                    onSelect = onSelect,
                    observeFile = observeFile,
                    downloadFile = downloadFile,
                    observeCustomEmoji = observeCustomEmoji
                )
            }
        }
        if (!onGlass) HorizontalDivider(color = palette.divider)
    }
}

/**
 * The Rail view: the forum's topics as a vertical panel at the start edge of the chat. The first item opens the full list.
 * [expanded] shows the title and the count of each topic beside its icon.
 */
@Composable
fun ForumTopicRail(
    topics: List<ForumTopic>,
    selectedTopicId: Long,
    parentMuted: Boolean,
    onSelect: (ForumTopic) -> Unit,
    onOpenList: () -> Unit,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    listState: LazyListState = rememberLazyListState()
) {
    val palette = beyGramPalette()
    val selectedIndex = remember(topics, selectedTopicId) { topics.indexOfFirst { it.id == selectedTopicId } }
    ForumPanelRevealSelected(listState = listState, selectedIndex = selectedIndex)
    Row(
        modifier
            .fillMaxHeight()
            .width(if (expanded) ForumRailExpandedWidth else ForumRailWidth)
            .background(palette.surface)
            // The rail sits at the screen's start edge, so it keeps clear of the status bar, the navigation bar and a cutout.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start + WindowInsetsSides.Bottom))
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "forum-panel-all") { ForumPanelRailAllTopics(expanded = expanded, onOpenList = onOpenList) }
            items(topics, key = { "forum-panel-topic:${it.id}" }) { topic ->
                ForumPanelRailItem(
                    topic = topic,
                    selected = topic.id == selectedTopicId,
                    parentMuted = parentMuted,
                    expanded = expanded,
                    onSelect = onSelect,
                    observeFile = observeFile,
                    downloadFile = downloadFile,
                    observeCustomEmoji = observeCustomEmoji
                )
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(palette.divider))
    }
}

/**
 * The Rail beside the open topic. The panel widens only when the pane it shares with the chat is wide enough to keep the
 * chat readable; a narrow pane keeps the narrow rail. [content] is the chat itself.
 */
@Composable
fun ForumTopicRailPane(
    topics: List<ForumTopic>,
    selectedTopicId: Long,
    parentMuted: Boolean,
    onSelect: (ForumTopic) -> Unit,
    onOpenList: () -> Unit,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val expandedRail = this.maxWidth >= ForumRailExpandedMinPaneWidth
        Row(Modifier.fillMaxSize()) {
            ForumTopicRail(
                topics = topics,
                selectedTopicId = selectedTopicId,
                parentMuted = parentMuted,
                onSelect = onSelect,
                onOpenList = onOpenList,
                observeFile = observeFile,
                downloadFile = downloadFile,
                observeCustomEmoji = observeCustomEmoji,
                expanded = expandedRail,
                listState = listState
            )
            Box(Modifier.weight(1f).fillMaxHeight()) {
                content()
            }
        }
    }
}

/** The forum's view choice: offered from the topic list and from a topic's menu. The current view carries a check. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumNavigationModeSheet(
    current: ForumNavigationMode,
    onSelect: (ForumNavigationMode) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = beyGramPalette()
    val checkMark: @Composable () -> Unit = {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
    }
    BeyGramSheet(onDismissRequest = onDismiss, title = stringResource(R.string.forum_navigation_title)) {
        ForumNavigationMode.entries.forEach { mode ->
            val isCurrent = mode == current
            SheetActionRow(
                icon = forumPanelModeIcon(mode),
                title = stringResource(forumPanelModeLabel(mode)),
                onClick = { onSelect(mode) },
                modifier = Modifier.semantics { selected = isCurrent },
                trailing = if (isCurrent) checkMark else null
            )
        }
    }
}

/**
 * Keeps the open topic inside a panel. Item 0 is the "all topics" button, so topic [selectedIndex] is item index + 1. A
 * topic that is already fully visible does not move the panel. The scroll follows the motion setting.
 */
@Composable
private fun ForumPanelRevealSelected(listState: LazyListState, selectedIndex: Int) {
    val motionInstant = rememberMotionInstant(MotionToken.TabSelect)
    LaunchedEffect(selectedIndex) {
        if (selectedIndex < 0) return@LaunchedEffect
        val item = selectedIndex + 1
        val layout = listState.layoutInfo
        val shown = layout.visibleItemsInfo.firstOrNull { it.index == item }
        val fullyVisible = shown != null &&
            shown.offset >= layout.viewportStartOffset &&
            shown.offset + shown.size <= layout.viewportEndOffset
        if (!fullyVisible) {
            // One neighbour stays in front of the topic, so the "all topics" button stays in reach when it can.
            listState.scrollToItemRespectingMotion((item - 1).coerceAtLeast(0), motionInstant = motionInstant)
        }
    }
}

/** The strip's first item: a neutral chip that opens the full topic list, with its search. */
@Composable
private fun ForumPanelAllTopicsTab(onOpenList: () -> Unit) {
    val palette = beyGramPalette()
    val shape = styledShape(14.dp)
    Box(
        Modifier
            .heightIn(min = ForumPanelTouchTarget)
            .clickable(role = Role.Button, onClick = onOpenList),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .heightIn(min = 36.dp)
                .clip(shape)
                .background(palette.surface2)
                .padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.List, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.forum_panel_all_topics),
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** One topic of the strip: the topic tile, a short name and its unread mark. The whole cell switches topics. */
@Composable
private fun ForumPanelTab(
    topic: ForumTopic,
    selected: Boolean,
    parentMuted: Boolean,
    onSelect: (ForumTopic) -> Unit,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>
) {
    val palette = beyGramPalette()
    val title = forumTopicDisplayTitle(topic)
    val muted = topic.isMutedIn(parentMuted)
    val description = forumPanelDescription(topic = topic, title = title, muted = muted)
    val shape = styledShape(14.dp)
    Box(
        Modifier
            .heightIn(min = ForumPanelTouchTarget)
            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(topic) })
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .heightIn(min = 36.dp)
                .clip(shape)
                .background(if (selected) palette.accentSoft else Color.Transparent)
                .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ForumTopicIcon(
                topic = topic,
                size = 24.dp,
                observeFile = observeFile,
                downloadFile = downloadFile,
                observeCustomEmoji = observeCustomEmoji
            )
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                color = if (selected) palette.accentInk else palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = ForumTabTitleMaxWidth)
            )
            if (topic.hasUnread) {
                Spacer(Modifier.width(6.dp))
                ForumPanelUnreadMark(topic = topic, muted = muted)
            }
        }
    }
}

/** The rail's first item: the full topic list, as an icon with a tiny label, or as a full row when the rail is expanded. */
@Composable
private fun ForumPanelRailAllTopics(expanded: Boolean, onOpenList: () -> Unit) {
    val palette = beyGramPalette()
    val label = stringResource(R.string.forum_panel_all_topics)
    val shape = styledShape(14.dp)
    if (expanded) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ForumPanelTouchTarget)
                .clip(shape)
                .background(palette.surface2)
                .clickable(role = Role.Button, onClick = onOpenList)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.List, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(shape)
                .background(palette.surface2)
                .clickable(role = Role.Button, onClick = onOpenList)
                .padding(vertical = 6.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.List, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(22.dp))
            Text(
                label,
                color = palette.textPrimary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** One topic of the rail. Expanded, it also shows the title and the unread mark on the row. */
@Composable
private fun ForumPanelRailItem(
    topic: ForumTopic,
    selected: Boolean,
    parentMuted: Boolean,
    expanded: Boolean,
    onSelect: (ForumTopic) -> Unit,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>
) {
    val palette = beyGramPalette()
    val title = forumTopicDisplayTitle(topic)
    val muted = topic.isMutedIn(parentMuted)
    val description = forumPanelDescription(topic = topic, title = title, muted = muted)
    val shape = styledShape(14.dp)
    val cell = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(if (selected) palette.accentSoft else Color.Transparent)
        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(topic) })
        .semantics { contentDescription = description }
    if (expanded) {
        Row(
            cell.heightIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ForumTopicIcon(
                topic = topic,
                size = 36.dp,
                observeFile = observeFile,
                downloadFile = downloadFile,
                observeCustomEmoji = observeCustomEmoji
            )
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                color = if (selected) palette.accentInk else palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (topic.hasUnread) {
                Spacer(Modifier.width(8.dp))
                ForumPanelUnreadMark(topic = topic, muted = muted)
            }
        }
    } else {
        Column(
            cell.heightIn(min = 56.dp).padding(vertical = 4.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(40.dp)) {
                ForumTopicIcon(
                    topic = topic,
                    size = 40.dp,
                    observeFile = observeFile,
                    downloadFile = downloadFile,
                    observeCustomEmoji = observeCustomEmoji
                )
                if (topic.hasUnread) {
                    ForumPanelUnreadMark(topic = topic, muted = muted, modifier = Modifier.align(Alignment.TopEnd))
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                title,
                color = if (selected) palette.accentInk else palette.textPrimary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * The unread mark, drawn like the list rows: a count in the accent (grey when the topic is muted), or a dot when only
 * mentions, reactions or poll votes are waiting. The row's spoken label already names the counts, so the mark is silent.
 */
@Composable
private fun ForumPanelUnreadMark(topic: ForumTopic, muted: Boolean, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    val radius = LocalDesignSystem.current.mainScreen.badgeRadius
    val color = if (muted) palette.textSecondary else palette.accent
    if (topic.unreadCount > 0) {
        Box(
            modifier
                .clearAndSetSemantics {}
                .defaultMinSize(minWidth = 18.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(radius))
                .background(color)
                .padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (topic.unreadCount > 99) "99+" else topic.unreadCount.toString(),
                color = if (muted) palette.surface else palette.onAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    } else {
        Box(modifier.clearAndSetSemantics {}.size(8.dp).clip(CircleShape).background(color))
    }
}

/** The spoken label of a panel item: the title, the unread and mention counts, then "muted" for a silenced topic. */
@Composable
private fun forumPanelDescription(topic: ForumTopic, title: String, muted: Boolean): String {
    val unread = if (topic.unreadCount > 0) stringResource(R.string.forum_topic_unread_count, topic.unreadCount) else null
    val mentions = if (topic.unreadMentionCount > 0) {
        stringResource(R.string.forum_topic_mentions, topic.unreadMentionCount)
    } else {
        null
    }
    val mutedLabel = if (muted) stringResource(R.string.forum_topic_muted) else null
    return listOfNotNull(title, unread, mentions, mutedLabel).joinToString(", ")
}

private fun forumPanelModeIcon(mode: ForumNavigationMode): ImageVector = when (mode) {
    ForumNavigationMode.Classic -> Icons.Rounded.List
    ForumNavigationMode.Tabs -> Icons.Rounded.MoreHoriz
    ForumNavigationMode.Rail -> Icons.Rounded.MoreVert
}

private fun forumPanelModeLabel(mode: ForumNavigationMode): Int = when (mode) {
    ForumNavigationMode.Classic -> R.string.forum_navigation_classic
    ForumNavigationMode.Tabs -> R.string.forum_navigation_tabs
    ForumNavigationMode.Rail -> R.string.forum_navigation_rail
}
