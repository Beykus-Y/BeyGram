package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.PAID_REACTION_PRESETS
import com.beykus.beygram.domain.model.PaidReactionAmountBlock
import com.beykus.beygram.domain.model.formatStarAmount
import com.beykus.beygram.domain.model.paidReactionAmountBlock
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.resolve

/**
 * The Stars reaction confirmation of one screen. [owner] is how [PaidReactionStore] tells the screens apart: only the owner shows
 * the confirmation, and the screen's reactions open it through this sender. The state itself outlives the composition.
 */
@Stable
internal class PaidReactionSender(
    private val conversation: ConversationRepository,
    val owner: Any,
    private val onSent: (PaidReactionTarget) -> Unit
) {
    /** Opens the confirmation for one message and reads the balance the Stars would be spent from. */
    fun open(chatId: Long, messageId: Long) = PaidReactionStore.open(conversation, owner, chatId, messageId, onSent)

    /** Chooses one preset. */
    fun select(starCount: Long) = PaidReactionStore.select(owner, starCount)

    /** Sends the chosen amount once. */
    fun send() = PaidReactionStore.send(conversation, owner)

    /** Closes the confirmation, except while a send is in flight. */
    fun dismiss() = PaidReactionStore.dismiss(owner)

    /** Takes over a send or an uncertain result that another screen left behind. */
    fun claim() = PaidReactionStore.claim(owner, onSent)

    /** Gives up the confirmation when the screen leaves composition; a send in flight keeps running. */
    fun release() = PaidReactionStore.release(owner)
}

/**
 * The Stars reaction confirmation of one screen. Returns the sender that the screen's reaction taps open, and draws the dialog
 * while this screen owns one, so a screen needs only this one call. [onSent] runs after TDLib confirms a Stars reaction.
 */
@Composable
internal fun rememberPaidReactions(
    conversation: ConversationRepository,
    onSent: (PaidReactionTarget) -> Unit = {}
): PaidReactionSender {
    val owner = remember { Any() }
    val latestOnSent by rememberUpdatedState(onSent)
    val sender = remember(conversation, owner) { PaidReactionSender(conversation, owner) { target -> latestOnSent(target) } }
    PaidReactionDialog(sender)
    return sender
}

@Composable
private fun PaidReactionDialog(sender: PaidReactionSender) {
    val state by PaidReactionStore.state.collectAsState()
    // A send in flight or an uncertain result that a screen left behind is taken over here, so the warning is still shown.
    LaunchedEffect(state.owner, state.target) { sender.claim() }
    DisposableEffect(sender) { onDispose { sender.release() } }
    if (state.target == null || state.owner !== sender.owner) return
    val palette = beyGramPalette()
    val balance = state.balance
    val blocked = paidReactionAmountBlock(state.starCount, balance?.starCount, state.maximum)
    val busy = state.sending
    AlertDialog(
        onDismissRequest = { sender.dismiss() },
        title = { Text(stringResource(R.string.paid_reaction_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (balance != null) {
                    Text(
                        stringResource(R.string.paid_reaction_balance, formatStarAmount(balance.starCount, balance.nanostarCount)),
                        color = palette.textSecondary,
                        fontSize = 13.sp
                    )
                } else if (state.balanceLoading) {
                    Text(stringResource(R.string.paid_reaction_balance_loading), color = palette.textSecondary, fontSize = 13.sp)
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
                }
                state.balanceFailure?.let { reason ->
                    Text(reason.resolve(), color = palette.warningText, fontSize = 14.sp, modifier = Modifier.errorAnnouncement())
                }
                if (balance != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PAID_REACTION_PRESETS.forEach { preset ->
                            val allowed = !busy && !state.uncertain && paidReactionAmountBlock(preset, balance.starCount, state.maximum) == null
                            PaidReactionChip(preset, isSelected = preset == state.starCount, enabled = allowed) { sender.select(preset) }
                        }
                    }
                    Text(
                        stringResource(R.string.paid_reaction_dialog_body, formatStarAmount(state.starCount)),
                        color = palette.textPrimary,
                        fontSize = 15.sp
                    )
                    Text(stringResource(R.string.paid_reaction_public), color = palette.textSecondary, fontSize = 13.sp)
                    if (blocked == PaidReactionAmountBlock.ExceedsBalance) {
                        Text(
                            stringResource(R.string.paid_reaction_not_enough),
                            color = palette.warningText,
                            fontSize = 14.sp,
                            modifier = Modifier.errorAnnouncement()
                        )
                    }
                }
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
                    Text(stringResource(R.string.paid_reaction_sending), color = palette.textSecondary, fontSize = 13.sp)
                }
                state.failure?.let { reason ->
                    Text(reason.resolve(), color = palette.warningText, fontSize = 14.sp, modifier = Modifier.errorAnnouncement())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { sender.send() },
                enabled = balance != null && blocked == null && !busy && !state.uncertain,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.common_send))
            }
        },
        dismissButton = {
            TextButton(onClick = { sender.dismiss() }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (state.uncertain) R.string.common_close else R.string.common_cancel))
            }
        }
    )
}

/** One Stars amount the user can pick. An amount that is not allowed is dimmed and does nothing when tapped. */
@Composable
private fun PaidReactionChip(starCount: Long, isSelected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val palette = beyGramPalette()
    val shape = RoundedCornerShape(50)
    // The pill stays compact, while the touch area meets the 48 dp minimum.
    Box(
        Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .semantics { selected = isSelected }
            .clickable(enabled = enabled, onClick = onSelect),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.clip(shape)
                .background(if (isSelected) palette.accentSoft else palette.surface2)
                .then(if (isSelected) Modifier.border(1.dp, palette.accent, shape) else Modifier)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Star, contentDescription = null, tint = PaidReactionTint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                formatStarAmount(starCount),
                color = if (isSelected) palette.accentInk else palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
