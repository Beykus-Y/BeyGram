package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.launch

/**
 * Emoji tab of the unified panel: standard emoji in a grid under group headers, a strip that jumps to a group and a delete
 * key. Tapping an emoji only reports it; the caller inserts it into the draft and keeps the panel open.
 */
@Composable
internal fun EmojiPanel(
    recents: List<String>,
    onEmoji: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = remember(recents) { EmojiCatalog.withRecent(recents) }
    val headerIndexes = remember(groups) { groupHeaderIndexes(groups) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    // The strip highlights the last group whose header has scrolled to the top of the grid.
    val activeId by remember(groups) {
        derivedStateOf {
            val top = gridState.firstVisibleItemIndex
            groups.lastOrNull { (headerIndexes[it.id] ?: 0) <= top }?.id ?: groups.firstOrNull()?.id
        }
    }
    Column(modifier) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(48.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            state = gridState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            groups.forEach { group ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "header-${group.id}") {
                    EmojiGroupHeader(stringResource(group.titleRes))
                }
                gridItems(group.emoji, key = { "${group.id}-$it" }) { emoji ->
                    EmojiCell(emoji = emoji, onClick = { onEmoji(emoji) })
                }
            }
        }
        EmojiGroupStrip(
            groups = groups,
            activeId = activeId,
            onSelect = { group ->
                scope.launch { gridState.animateScrollToItem(headerIndexes[group.id] ?: 0) }
            },
            onBackspace = onBackspace
        )
    }
}

/** Grid item index of every group header. A group is one header item followed by one item per emoji. */
private fun groupHeaderIndexes(groups: List<EmojiGroup>): Map<String, Int> {
    val indexes = LinkedHashMap<String, Int>()
    var next = 0
    groups.forEach { group ->
        indexes[group.id] = next
        next += 1 + group.emoji.size
    }
    return indexes
}

@Composable
private fun EmojiGroupHeader(title: String) {
    Text(
        title,
        color = beyGramPalette().textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 4.dp).semantics { heading() }
    )
}

/** One emoji on a 48dp touch target; the glyph is 28sp so it reads well in the grid. */
@Composable
private fun EmojiCell(emoji: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = 28.sp, maxLines = 1)
    }
}

@Composable
private fun EmojiGroupStrip(
    groups: List<EmojiGroup>,
    activeId: String?,
    onSelect: (EmojiGroup) -> Unit,
    onBackspace: () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            rowItems(groups, key = { it.id }) { group ->
                EmojiGroupButton(group = group, selected = group.id == activeId, onClick = { onSelect(group) })
            }
        }
        IconButton(onClick = onBackspace, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Rounded.Backspace, stringResource(R.string.emoji_backspace), tint = palette.textSecondary)
        }
    }
}

@Composable
private fun EmojiGroupButton(group: EmojiGroup, selected: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    val label = stringResource(group.titleRes)
    Box(
        Modifier.size(48.dp)
            .clip(CircleShape)
            .background(if (selected) palette.accentSoft else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Text(group.icon, fontSize = 20.sp)
    }
}
