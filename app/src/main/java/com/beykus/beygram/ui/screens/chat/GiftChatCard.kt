package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.GiftChatContent
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.screens.profile.GiftArtworkFrame
import com.beykus.beygram.ui.screens.profile.GiftFileAccess
import com.beykus.beygram.ui.screens.profile.ProvideGiftFileAccess
import kotlinx.coroutines.flow.StateFlow

/**
 * A gift message in the timeline (Gifts 2.0, #1142).
 *
 * The card draws the artwork with the shared [GiftArtworkFrame] from the Gifts Visual Core (#1230), so the chat has no
 * renderer of its own: backdrop gradient, symbol pattern, sticker/model and emoji fallback are the same components the
 * gift screens use, including their [com.beykus.beygram.ui.screens.profile.GiftAnimationBudget] behaviour. [caption] is
 * the message text (already localized by the service mapper, including the sender's note); [GiftChatContent] only adds
 * what a plain text chip cannot show: the artwork, the collectible number and the gift's title.
 *
 * File access is passed in rather than read from chat-wide state, because the timeline already owns the media layer for
 * its bubbles and the card must use exactly that one.
 */
@Composable
internal fun GiftChatCard(
    gift: GiftChatContent,
    caption: String,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // One access object for the lifetime of the card: the composition local would otherwise change identity on every
    // recomposition and re-subscribe every artwork file.
    val access = remember(observeFile, downloadFile) { GiftFileAccess(observeFile, downloadFile) }
    ProvideGiftFileAccess(access) {
        val number = if (gift.number > 0) stringResource(R.string.gifts_collectible_number, gift.number) else null
        val generic = stringResource(R.string.gifts_gift_generic)
        val description = listOfNotNull(
            gift.title.takeIf { it.isNotBlank() },
            number,
            caption.takeIf { it.isNotBlank() }
        ).joinToString(", ").ifBlank { generic }
        Column(
            // One node for the screen reader: the artwork already announces itself, so the card is described by what it
            // actually says instead of by its drawing parts.
            modifier = modifier
                .widthIn(max = CARD_MAX_WIDTH)
                .clearAndSetSemantics { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            GiftArtworkFrame(
                visual = gift.visual,
                emoji = gift.emoji,
                collectible = gift.collectible,
                frameSize = ARTWORK_SIZE,
                stickerSize = ARTWORK_SIZE,
                symbolSize = 20.dp,
                cornerRadius = CARD_CORNER
            )
            if (gift.title.isNotBlank()) {
                Text(
                    text = gift.title,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center
                )
            }
            if (number != null) {
                Text(text = number, style = MaterialTheme.typography.labelSmall)
            }
            if (caption.isNotBlank()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private val CARD_MAX_WIDTH = 240.dp
private val ARTWORK_SIZE = 84.dp
private val CARD_CORNER = 16.dp