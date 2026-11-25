package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.GiftChatContent
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.screens.profile.GiftCollectibleBackdrop
import com.beykus.beygram.ui.screens.profile.GiftFileAccess
import com.beykus.beygram.ui.screens.profile.GiftPatternLayer
import com.beykus.beygram.ui.screens.profile.GiftVisualImage
import com.beykus.beygram.ui.screens.profile.ProvideGiftFileAccess
import com.beykus.beygram.ui.screens.profile.backdropEndpoints
import kotlinx.coroutines.flow.StateFlow

/**
 * A gift message in the timeline (Gifts 2.0, #1142).
 *
 * The card draws the very same artwork as the profile gift screens: [GiftVisualImage] for the gift's own sticker and
 * [GiftCollectibleBackdrop] with [GiftPatternLayer] for a collectible gift, so there is no second renderer and no second
 * interpretation of TDLib data. [caption] is the message text (already localized by the service mapper, including the
 * sender's note); [GiftChatContent] only adds what a plain text chip cannot show: the artwork, the collectible number
 * and the gift's title.
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
        val hasBackdrop = backdropEndpoints(gift.collectible) != null
        Column(
            // One node for the screen reader: the artwork already announces itself, so the card is described by what it
            // actually says instead of by its drawing parts.
            modifier = modifier
                .widthIn(max = CARD_MAX_WIDTH)
                .clearAndSetSemantics { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            GiftCollectibleBackdrop(
                details = gift.collectible,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(CARD_CORNER)).padding(vertical = 12.dp)
            ) {
                GiftPatternLayer(gift.collectible?.symbol, hasBackdrop)
                GiftVisualImage(
                    visual = gift.visual,
                    size = ARTWORK_SIZE,
                    fallback = {
                        // Nothing is on the device yet (or TDLib reported no sticker): the emoji TDLib sent, otherwise a
                        // plain gift glyph, so the message never renders as an empty box.
                        Text(text = gift.emoji.ifBlank { DEFAULT_ARTWORK }, fontSize = 44.sp)
                    }
                )
            }
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
private const val DEFAULT_ARTWORK = "🎁"