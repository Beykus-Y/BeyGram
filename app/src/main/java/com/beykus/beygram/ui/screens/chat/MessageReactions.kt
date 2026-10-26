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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MessageReaction
import com.beykus.beygram.domain.model.ReactionOption
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.emojiPresentation
import kotlinx.coroutines.flow.StateFlow

/**
 * Reaction chips under a bubble. The user's own reaction is filled with the accent colour; a tap passes its option to [onToggle].
 * A custom emoji is drawn as its sticker, with a visible emoji placeholder until the sticker can be drawn. A paid reaction is
 * display-only: it shows a star and its Stars count, and it is never clickable, because adding Stars needs a separate confirmation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MessageReactionChips(
    reactions: List<MessageReaction>, observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>, observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit, onToggle: (ReactionOption) -> Unit, modifier: Modifier = Modifier
) {
    if (reactions.isEmpty()) return
    FlowRow(modifier.widthIn(max = 292.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        reactions.forEach { reaction -> key(reaction.option) { ReactionChip(reaction, observeCustomEmoji, observeFile, downloadFile, onToggle) } }
    }
}

@Composable
private fun ReactionChip(
    reaction: MessageReaction, observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>, observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit, onToggle: (ReactionOption) -> Unit
) {
    val palette = beyGramPalette()
    val shape = RoundedCornerShape(50)
    val description = when {
        reaction.isPaid -> stringResource(R.string.chat_reaction_paid_description, reaction.count)
        reaction.customEmojiId == 0L -> stringResource(R.string.chat_reaction_description, reaction.emoji, reaction.count)
        else -> stringResource(R.string.chat_reaction_custom_description, reaction.count)
    }
    // The pill keeps its compact look, while the invisible touch area meets the 48 dp minimum. Paid Stars chips have no action.
    val action = if (reaction.isPaid) Modifier else Modifier.clickable { onToggle(reaction.option) }
    Box(
        Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = description; selected = reaction.isChosen }
            .then(action),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.clip(shape)
                .background(if (reaction.isChosen) palette.accentSoft else palette.surface2)
                .then(if (reaction.isChosen) Modifier.border(1.dp, palette.accent, shape) else Modifier)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReactionGlyph(reaction.option, 18.dp, 15.sp, observeCustomEmoji, observeFile, downloadFile)
            Spacer(Modifier.width(4.dp))
            Text(reaction.count.toString(), color = if (reaction.isChosen) palette.accentInk else palette.textSecondary,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** A reaction as it is drawn: its emoji text, a custom emoji's sticker, or the paid Stars star. */
@Composable
private fun ReactionGlyph(
    option: ReactionOption, size: Dp, fontSize: TextUnit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>, observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit
) {
    if (option.isPaid) {
        Icon(Icons.Rounded.Star, contentDescription = null, tint = PaidReactionTint, modifier = Modifier.size(size))
        return
    }
    if (option.customEmojiId == 0L) {
        Text(emojiPresentation(option.emoji.ifBlank { CUSTOM_EMOJI_FALLBACK }), fontSize = fontSize)
        return
    }
    val custom by remember(option.customEmojiId) { observeCustomEmoji(option.customEmojiId) }.collectAsStateWithLifecycle()
    val emoji = custom
    // A chip must never collapse to an empty box while TDLib looks the sticker up, or after it reports that the id is gone.
    if (emoji == null) {
        Text(emojiPresentation(CUSTOM_EMOJI_FALLBACK), fontSize = fontSize)
        return
    }
    CustomEmojiImage(emoji, size, observeFile, downloadFile) {
        Text(emojiPresentation(emoji.emoji.ifBlank { CUSTOM_EMOJI_FALLBACK }), fontSize = fontSize)
    }
}

private const val CUSTOM_EMOJI_FALLBACK = "🙂"
private val PaidReactionTint = Color(0xFFFFB300)

/**
 * Reactions offered for the selected message, shown under the message actions. The user's own reaction is outlined:
 * a tap on it removes it, and a tap on any other reaction adds it. Paid Stars reactions are never offered here, so a
 * normal tap cannot spend Stars. The reactions fill two rows and scroll sideways.
 */
@Composable
internal fun MessageReactionPicker(
    options: List<ReactionOption>, chosen: Set<ReactionOption>, observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>,
    observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit, onPick: (ReactionOption) -> Unit
) {
    val offered = remember(options) { options.filterNot { it.isPaid } }
    if (offered.isEmpty()) return
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().background(palette.surface)) {
        // Two 48dp touch rows, 2dp apart, inside 4dp of padding.
        LazyHorizontalGrid(
            rows = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().height(106.dp).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(offered, key = { option -> if (option.customEmojiId != 0L) "#${option.customEmojiId}" else option.emoji }) { option ->
                val isChosen = option in chosen
                val description = if (option.customEmojiId != 0L) stringResource(if (isChosen) R.string.chat_reaction_custom_remove_description else R.string.chat_reaction_custom_pick_description)
                    else stringResource(if (isChosen) R.string.chat_reaction_remove_description else R.string.chat_reaction_pick_description, option.emoji)
                Box(Modifier.size(48.dp).clip(CircleShape)
                    .background(if (isChosen) palette.accentSoft else Color.Transparent)
                    .semantics { contentDescription = description; selected = isChosen }
                    .clickable { onPick(option) },
                    contentAlignment = Alignment.Center) {
                    ReactionGlyph(option, 28.dp, 24.sp, observeCustomEmoji, observeFile, downloadFile)
                }
            }
        }
        HorizontalDivider(color = palette.divider)
    }
}
