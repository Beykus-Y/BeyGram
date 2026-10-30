package com.beykus.beygram.ui.screens.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatStories
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.flow.StateFlow

/**
 * Horizontal story bar above the chat list. It renders only metadata; full story content is loaded by
 * the viewer. The bar is intentionally hidden in search results and server folders by the caller.
 */
@Composable
internal fun StoryBarSection(
    stories: List<ChatStories>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onOpenStories: (ChatStories) -> Unit,
    onAddStory: () -> Unit,
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(shouldLoadMore, stories.size) {
        // Reaching the end asks for the next TDLib page; the repository stops after the 404 end marker.
        if (shouldLoadMore) onLoadMore()
    }
    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "story_add") { AddStoryCell(onClick = onAddStory) }
        items(stories, key = { "story_${it.chatId}" }) { entry ->
            StoryCell(
                entry = entry,
                observeFile = observeFile,
                downloadFile = downloadFile,
                onClick = { onOpenStories(entry) }
            )
        }
    }
}

@Composable
private fun AddStoryCell(onClick: () -> Unit) {
    val palette = beyGramPalette()
    val description = stringResource(R.string.stories_a11y_add)
    Column(
        modifier = Modifier.width(88.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(66.dp).clip(CircleShape).background(palette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.stories_add),
            color = palette.textPrimary,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(min = 30.dp)
        )
    }
}

@Composable
private fun StoryCell(
    entry: ChatStories,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val label = if (entry.isSelf) stringResource(R.string.stories_you) else entry.title
    val description = context.getString(
        if (entry.hasUnread) R.string.stories_a11y_author_unread else R.string.stories_a11y_author,
        label
    )
    Column(
        modifier = Modifier.width(88.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(66.dp).clip(CircleShape)
                .border(2.dp, if (entry.hasUnread) palette.accent else palette.divider, CircleShape)
                .padding(3.dp)
        ) {
            TelegramAvatar(
                initials = entry.initials,
                color = androidx.compose.ui.graphics.Color(entry.avatarColorArgb),
                size = 60.dp,
                photo = entry.photo,
                observeFile = observeFile,
                downloadFile = downloadFile
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = palette.textPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(min = 30.dp)
        )
    }
}
