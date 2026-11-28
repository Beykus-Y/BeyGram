package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MessageReply
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.resolve

/** A name made of emoji or other symbols is still the user's actual display name. */
internal fun isSymbolOnlyDisplayName(name: String): Boolean =
    name.isNotBlank() && !name.codePoints().anyMatch { Character.isLetterOrDigit(it) }

@Composable
internal fun ReplyQuote(
    reply: MessageReply,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
) {
    val palette = beyGramPalette()
    val metrics = LocalBubbleMetrics.current
    val dark = com.beykus.beygram.ui.theme.LocalBeyGramDarkTheme.current
    val stripeColor = reply.senderAccentColor?.authorColor(dark, palette.surface, palette.accent) ?: palette.accent
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(metrics.replyRadius))
            .background(stripeColor.copy(alpha = 0.10f))
            .drawBehind {
                val stripe = 3.dp.toPx().coerceAtMost(size.width)
                drawRect(
                    color = stripeColor,
                    topLeft = Offset(if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - stripe, 0f),
                    size = Size(stripe, size.height),
                )
            }
            .combinedClickable(
                onClick = onClick, onLongClick = onLongClick,
                onClickLabel = onClickLabel, onLongClickLabel = onLongClickLabel,
            )
            .padding(start = 11.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isSymbolOnlyDisplayName(reply.senderName)) {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                reply.senderName.ifBlank { stringResource(R.string.chat_reply_to_message) },
                color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(reply.text.resolve(), color = palette.textSecondary, fontSize = 13.sp, lineHeight = 17.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
