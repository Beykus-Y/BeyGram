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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.PAID_REACTION_PRESETS
import com.beykus.beygram.domain.model.PaidReactionAmountBlock
import com.beykus.beygram.domain.model.PaidReactionUncertainException
import com.beykus.beygram.domain.model.StarBalance
import com.beykus.beygram.domain.model.formatStarAmount
import com.beykus.beygram.domain.model.paidReactionAmountBlock
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.resolve
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The message a Stars reaction is confirmed for. */
internal data class PaidReactionTarget(val chatId: Long, val messageId: Long)

/**
 * What the confirmation shows; a null [target] means no confirmation is open. [balance] stays null until it has been read, and a
 * null balance blocks the send, because the user must see the balance the Stars come from.
 */
internal data class PaidReactionState(
    val target: PaidReactionTarget? = null,
    val balance: StarBalance? = null,
    val balanceLoading: Boolean = false,
    val balanceFailure: UiText? = null,
    val maximum: Long? = null,
    val starCount: Long = PAID_REACTION_PRESETS.first(),
    val sending: Boolean = false,
    val failure: UiText? = null,
    /** TDLib did not confirm the commit, so the Stars may be spent: Send stays locked until the user closes the confirmation. */
    val uncertain: Boolean = false
)

/**
 * Owns the Stars reaction confirmation of one screen. A tap on a Stars reaction only [open]s the confirmation: the balance is read,
 * the smallest preset is preselected, and [send] runs once for the chosen amount. Nothing is retried, and the dialog stays open while
 * a send is in flight. [onSent] runs after TDLib confirms a send.
 */
@Stable
internal class PaidReactionSender(
    private val conversation: ConversationRepository,
    private val scope: CoroutineScope,
    private val onSent: (PaidReactionTarget) -> Unit
) {
    var state by mutableStateOf(PaidReactionState())
        private set
    private var balanceJob: Job? = null

    /** Opens the confirmation for one message and reads the balance the Stars would be spent from. */
    fun open(chatId: Long, messageId: Long) {
        val current = state
        if (current.sending || current.uncertain) return
        val target = PaidReactionTarget(chatId, messageId)
        val maximum = conversation.messageLimits.value.paidReactionStarMax?.toLong()
        balanceJob?.cancel()
        state = PaidReactionState(target = target, maximum = maximum, balanceLoading = true)
        balanceJob = scope.launch {
            val result = conversation.starBalance()
            // The dialog may have been closed or moved to another message while the balance was read.
            if (state.target != target) return@launch
            state = result.fold(
                onSuccess = { balance -> state.copy(balance = balance, balanceLoading = false) },
                onFailure = { failure ->
                    state.copy(
                        balanceLoading = false,
                        balanceFailure = (failure as? UserMessageException)?.text ?: UiText.Res(R.string.stars_load_error)
                    )
                }
            )
        }
    }

    /** Chooses one preset. An amount that the balance or the maximum does not allow cannot be chosen. */
    fun select(starCount: Long) {
        val current = state
        if (current.sending || current.uncertain || current.target == null) return
        if (paidReactionAmountBlock(starCount, current.balance?.starCount, current.maximum) != null) return
        state = current.copy(starCount = starCount, failure = null)
    }

    /** Sends the chosen amount once. The result either closes the confirmation or explains why nothing was spent. */
    fun send() {
        val current = state
        val target = current.target ?: return
        if (current.sending || current.uncertain) return
        if (paidReactionAmountBlock(current.starCount, current.balance?.starCount, current.maximum) != null) return
        val starCount = current.starCount
        state = current.copy(sending = true, failure = null)
        scope.launch {
            val result = conversation.addPaidReaction(target.chatId, target.messageId, starCount)
            state = result.fold(
                onSuccess = { PaidReactionState() },
                onFailure = { failure ->
                    val reason = (failure as? UserMessageException)?.text ?: UiText.Res(R.string.err_paid_reaction_failed)
                    state.copy(sending = false, failure = reason, uncertain = failure is PaidReactionUncertainException)
                }
            )
            if (result.isSuccess) onSent(target)
        }
    }

    /** Closes the confirmation, except while a send is in flight: its result still has to reach the user. */
    fun dismiss() {
        if (state.sending) return
        balanceJob?.cancel()
        state = PaidReactionState()
    }
}

/**
 * The Stars reaction confirmation of one screen. Returns the sender that the screen's reaction taps open, and draws the dialog
 * while one is open, so a screen needs only this one call. [onSent] runs after TDLib confirms a Stars reaction.
 */
@Composable
internal fun rememberPaidReactions(
    conversation: ConversationRepository,
    onSent: (PaidReactionTarget) -> Unit = {}
): PaidReactionSender {
    val scope = rememberCoroutineScope()
    val latestOnSent by rememberUpdatedState(onSent)
    val sender = remember(conversation, scope) { PaidReactionSender(conversation, scope) { target -> latestOnSent(target) } }
    PaidReactionDialog(sender)
    return sender
}

@Composable
private fun PaidReactionDialog(sender: PaidReactionSender) {
    val state = sender.state
    if (state.target == null) return
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
