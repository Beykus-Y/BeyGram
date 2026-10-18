package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/** Reaction chips under a bubble. The current user's own reaction is filled with the accent colour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MessageReactionChips(reactions: List<MessageReaction>, modifier: Modifier = Modifier) {
    if (reactions.isEmpty()) return
    FlowRow(modifier.widthIn(max = 292.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        reactions.forEach { ReactionChip(it) }
    }
}

@Composable
private fun ReactionChip(reaction: MessageReaction) {
    val palette = beyGramPalette()
    val shape = RoundedCornerShape(50)
    val description = stringResource(R.string.chat_reaction_description, reaction.emoji, reaction.count)
    Row(
        Modifier.semantics { contentDescription = description; selected = reaction.isChosen }
            .clip(shape)
            .background(if (reaction.isChosen) palette.accentSoft else palette.surface2)
            .then(if (reaction.isChosen) Modifier.border(1.dp, palette.accent, shape) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(reaction.emoji, fontSize = 15.sp)
        Spacer(Modifier.width(4.dp))
        Text(reaction.count.toString(), color = if (reaction.isChosen) palette.accentInk else palette.textSecondary,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
