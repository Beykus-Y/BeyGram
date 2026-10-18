package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MessageReaction
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.emojiPresentation

/** Reaction chips under a bubble. The user's own reaction is filled with the accent colour; a tap passes it to [onToggle]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MessageReactionChips(reactions: List<MessageReaction>, onToggle: (MessageReaction) -> Unit, modifier: Modifier = Modifier) {
    if (reactions.isEmpty()) return
    FlowRow(modifier.widthIn(max = 292.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        reactions.forEach { ReactionChip(it, onToggle) }
    }
}

@Composable
private fun ReactionChip(reaction: MessageReaction, onToggle: (MessageReaction) -> Unit) {
    val palette = beyGramPalette()
    val shape = RoundedCornerShape(50)
    val description = stringResource(R.string.chat_reaction_description, reaction.emoji, reaction.count)
    Row(
        Modifier.semantics { contentDescription = description; selected = reaction.isChosen }
            .clip(shape)
            .background(if (reaction.isChosen) palette.accentSoft else palette.surface2)
            .then(if (reaction.isChosen) Modifier.border(1.dp, palette.accent, shape) else Modifier)
            .clickable { onToggle(reaction) }
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emojiPresentation(reaction.emoji), fontSize = 15.sp)
        Spacer(Modifier.width(4.dp))
        Text(reaction.count.toString(), color = if (reaction.isChosen) palette.accentInk else palette.textSecondary,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Reactions offered for the selected message, shown under the message actions. The user's own reaction is outlined:
 * a tap on it removes it, and a tap on any other emoji adds it. The emoji fill two rows and scroll sideways.
 */
@Composable
internal fun MessageReactionPicker(options: List<String>, chosen: Set<String>, onPick: (String) -> Unit) {
    if (options.isEmpty()) return
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().background(palette.surface)) {
        // Two 40dp rows, 2dp apart, inside 4dp of padding.
        LazyHorizontalGrid(
            rows = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().height(90.dp).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(options, key = { it }) { emoji ->
                val isChosen = emoji in chosen
                val description = stringResource(if (isChosen) R.string.chat_reaction_remove_description else R.string.chat_reaction_pick_description, emoji)
                Box(Modifier.size(40.dp).clip(CircleShape)
                    .background(if (isChosen) palette.accentSoft else Color.Transparent)
                    .semantics { contentDescription = description; selected = isChosen }
                    .clickable { onPick(emoji) },
                    contentAlignment = Alignment.Center) {
                    Text(emojiPresentation(emoji), fontSize = 24.sp)
                }
            }
        }
        HorizontalDivider(color = palette.divider)
    }
}
