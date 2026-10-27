package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.PAID_REACTION_PRESETS
import com.beykus.beygram.domain.model.PaidReactionUncertainException
import com.beykus.beygram.domain.model.StarBalance
import com.beykus.beygram.domain.model.paidReactionAmountBlock
import com.beykus.beygram.domain.repository.ConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The message a Stars reaction is confirmed for. */
internal data class PaidReactionTarget(val chatId: Long, val messageId: Long)

/**
 * What the confirmation shows; a null [target] means no confirmation is open. [balance] stays null until it has been read, and a
 * null balance blocks the send, because the user must see the balance the Stars come from. [owner] is the screen that shows the
 * confirmation. It is null only while a send in flight or an uncertain result waits for a screen after its own screen went away.
 */
internal data class PaidReactionState(
    val owner: Any? = null,
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
 * A screen that leaves composition gives up its confirmation. An in-flight send or an uncertain result stays, without an owner,
 * until another screen takes it over. A state that [owner] does not own is returned unchanged.
 */
internal fun PaidReactionState.released(owner: Any): PaidReactionState = when {
    this.owner !== owner -> this
    sending || uncertain -> copy(owner = null)
    else -> PaidReactionState()
}

/** A send or an uncertain result that no screen owns is taken over by [owner]. Any other state is returned unchanged. */
internal fun PaidReactionState.claimedBy(owner: Any): PaidReactionState =
    if (this.owner == null && (sending || uncertain)) copy(owner = owner) else this

/** The balance that was read, or the reason why it could not be read. */
internal fun PaidReactionState.balanceRead(result: Result<StarBalance>): PaidReactionState = result.fold(
    onSuccess = { balance -> copy(balance = balance, balanceLoading = false) },
    onFailure = { failure ->
        copy(balanceLoading = false, balanceFailure = (failure as? UserMessageException)?.text ?: UiText.Res(R.string.stars_load_error))
    }
)

/** A confirmed send closes the confirmation. A refused one keeps it open with the reason, and an unknown outcome is uncertain. */
internal fun PaidReactionState.sendFinished(result: Result<Unit>): PaidReactionState = result.fold(
    onSuccess = { PaidReactionState() },
    onFailure = { failure ->
        val reason = (failure as? UserMessageException)?.text ?: UiText.Res(R.string.err_paid_reaction_failed)
        copy(sending = false, failure = reason, uncertain = failure is PaidReactionUncertainException)
    }
)

/**
 * Process-scoped Stars reaction confirmation, the same way as the paid content flow. The balance read and the send run in this
 * store's scope and not in a composition, so a rotation or a screen that leaves composition cannot cancel a charge that TDLib may
 * still make. The state stays here until its owner closes it, and a send or an uncertain result that outlived its screen is shown
 * by the next screen that composes a confirmation.
 */
internal object PaidReactionStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PaidReactionState())
    val state: StateFlow<PaidReactionState> = _state.asStateFlow()
    private var balanceJob: Job? = null

    /** Tells the screen that owns the state that its send went through. The screen that opened or claimed the state sets it. */
    private var onSent: (PaidReactionTarget) -> Unit = {}

    /** The conversation the open confirmation was made in. Only that conversation may take over its send or uncertain result. */
    private var openedIn: ConversationRepository? = null

    /** Opens the confirmation for one message for [owner], and reads the balance the Stars would be spent from. */
    fun open(conversation: ConversationRepository, owner: Any, chatId: Long, messageId: Long, onSent: (PaidReactionTarget) -> Unit) {
        val current = _state.value
        if (current.sending || current.uncertain) return
        val target = PaidReactionTarget(chatId, messageId)
        val maximum = conversation.messageLimits.value.paidReactionStarMax?.toLong()
        balanceJob?.cancel()
        this.onSent = onSent
        openedIn = conversation
        _state.value = PaidReactionState(owner = owner, target = target, maximum = maximum, balanceLoading = true)
        balanceJob = scope.launch {
            val result = conversation.starBalance()
            // The confirmation may have been closed or moved to another message while the balance was read.
            if (_state.value.target != target) return@launch
            _state.value = _state.value.balanceRead(result)
        }
    }

    /** Chooses one preset. An amount that the balance or the maximum does not allow cannot be chosen. */
    fun select(owner: Any, starCount: Long) {
        val current = _state.value
        if (current.owner !== owner || current.target == null || current.sending || current.uncertain) return
        if (paidReactionAmountBlock(starCount, current.balance?.starCount, current.maximum) != null) return
        _state.value = current.copy(starCount = starCount, failure = null)
    }

    /** Sends the chosen amount once. The result either closes the confirmation or explains why nothing was spent. */
    fun send(conversation: ConversationRepository, owner: Any) {
        val current = _state.value
        val target = current.target ?: return
        if (current.owner !== owner || current.sending || current.uncertain) return
        if (paidReactionAmountBlock(current.starCount, current.balance?.starCount, current.maximum) != null) return
        val starCount = current.starCount
        _state.value = current.copy(sending = true, failure = null)
        scope.launch {
            val result = conversation.addPaidReaction(target.chatId, target.messageId, starCount)
            _state.value = _state.value.sendFinished(result)
            if (result.isSuccess) onSent(target)
        }
    }

    /** Closes the confirmation, except while a send is in flight: its result still has to reach the user. */
    fun dismiss(owner: Any) {
        val current = _state.value
        if (current.owner !== owner || current.sending) return
        balanceJob?.cancel()
        _state.value = PaidReactionState()
    }

    /** Called when the owner's screen leaves composition. An in-flight send or an uncertain result stays for the next screen. */
    fun release(owner: Any) {
        val current = _state.value
        if (current.owner !== owner) return
        if (!current.sending && !current.uncertain) balanceJob?.cancel()
        _state.value = current.released(owner)
    }

    /**
     * Lets [owner] take over a send or an uncertain result that no screen owns, and reports that send to [onSent]. A screen
     * of another account or conversation cannot take it over: a retry would spend that account's Stars on the original message.
     */
    fun claim(owner: Any, conversation: ConversationRepository, onSent: (PaidReactionTarget) -> Unit) {
        if (conversation !== openedIn) return
        val claimed = _state.value.claimedBy(owner)
        if (claimed.owner !== owner) return
        this.onSent = onSent
        _state.value = claimed
    }
}
