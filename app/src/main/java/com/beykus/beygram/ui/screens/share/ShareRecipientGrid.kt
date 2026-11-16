package com.beykus.beygram.ui.screens.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.components.ChatAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.styledShape
import kotlinx.coroutines.flow.StateFlow

/** One adaptive recipient grid for internal forwarding and Android's incoming share. */
@Composable
internal fun ShareRecipientGrid(
    chats: List<ChatPreview>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onChoose: (ChatPreview) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedIds: Set<Long>? = null
) {
    val palette = beyGramPalette()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(80.dp),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(chats, key = { it.id }) { chat ->
            val selected = selectedIds?.contains(chat.id) == true
            val action = if (selectedIds == null) {
                Modifier.clickable(enabled = enabled, role = Role.Button) { onChoose(chat) }
            } else {
                Modifier.selectable(selected = selected, enabled = enabled, role = Role.Checkbox) { onChoose(chat) }
            }
            Column(
                Modifier.fillMaxWidth().clip(styledShape(14.dp)).then(action).padding(vertical = 8.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.size(62.dp), contentAlignment = Alignment.Center) {
                    ChatAvatar(chat, 54.dp, observeFile, downloadFile, Modifier.clearAndSetSemantics {})
                    if (selected) {
                        Box(Modifier.matchParentSize().border(2.dp, palette.accent, CircleShape))
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(palette.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Check, null, tint = palette.onAccent, modifier = Modifier.size(15.dp))
                        }
                    }
                }
                Text(
                    chat.title,
                    color = if (selected) palette.accent else palette.textPrimary,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
