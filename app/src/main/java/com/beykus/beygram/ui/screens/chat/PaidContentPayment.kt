package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ExternalPaymentMethod
import com.beykus.beygram.domain.model.PaidContentKey
import com.beykus.beygram.domain.model.PaidContentPaymentOutcome
import com.beykus.beygram.domain.model.PaidContentPlan
import com.beykus.beygram.domain.model.StarBalance
import com.beykus.beygram.domain.repository.ConversationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One step of the paid-content flow. The owner is the bubble that started it, and only that bubble shows the
 * confirmation. The same message shown in a comment thread or a context dialog therefore never opens a second one.
 */
internal sealed interface PaidContentStep {
    data object Idle : PaidContentStep
    data class Preparing(val key: PaidContentKey, val owner: Any) : PaidContentStep
    data class Confirming(
        val key: PaidContentKey,
        val owner: Any,
        val starCount: Long,
        val balance: StarBalance
    ) : PaidContentStep
    data class ChoosingExternal(
        val key: PaidContentKey,
        val owner: Any,
        val methods: List<ExternalPaymentMethod>
    ) : PaidContentStep
    data class Paying(val key: PaidContentKey, val starCount: Long) : PaidContentStep
}

/** The last result for one message. It stays until the next request for that message. */
internal data class PaidContentNotice(val key: PaidContentKey, val text: UiText)

internal data class PaidContentPaymentState(
    val step: PaidContentStep = PaidContentStep.Idle,
    val notice: PaidContentNotice? = null
)

/**
 * Process-scoped paid-content state. A read or a payment runs in this scope, so a bubble that scrolls away or a
 * context dialog that closes never cancels it halfway. Nothing is paid without a confirmation of the shown price.
 */
internal object PaidContentPaymentStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PaidContentPaymentState())
    val state: StateFlow<PaidContentPaymentState> = _state.asStateFlow()

    /** Reads the price only. A read or a payment that is already running blocks a new request. */
    fun request(conversation: ConversationRepository, key: PaidContentKey, owner: Any) {
        val current = _state.value.step
        if (current is PaidContentStep.Preparing || current is PaidContentStep.Paying) return
        // An earlier dialog that was never confirmed is replaced, never resumed.
        _state.value = PaidContentPaymentState(step = PaidContentStep.Preparing(key, owner))
        scope.launch {
            val plan = try {
                conversation.paidContentPlan(key.chatId, key.messageId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PaidContentPlan.Failed(UiText.Res(R.string.err_monetization_failed))
            }
            val latest = _state.value.step
            // Only the request that is still open may publish its result.
            if (latest is PaidContentStep.Preparing && latest.key == key && latest.owner === owner) {
                _state.value = plan.toState(key, owner)
            }
        }
    }

    /** Pays the price that the dialog showed. Paying is set first, so a second tap cannot start another payment. */
    fun confirm(conversation: ConversationRepository, key: PaidContentKey) {
        val confirming = _state.value.step as? PaidContentStep.Confirming ?: return
        if (confirming.key != key) return
        _state.value = PaidContentPaymentState(step = PaidContentStep.Paying(key, confirming.starCount))
        scope.launch {
            val outcome = try {
                conversation.payPaidContentWithStars(key.chatId, key.messageId, confirming.starCount)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PaidContentPaymentOutcome.Uncertain
            }
            _state.value = PaidContentPaymentState(notice = PaidContentNotice(key, outcome.toText()))
        }
    }

    /** Closes an unconfirmed dialog for [key]. A payment that is already running cannot be closed from here. */
    fun dismiss(key: PaidContentKey) {
        val belongsToKey = when (val step = _state.value.step) {
            is PaidContentStep.Confirming -> step.key == key
            is PaidContentStep.ChoosingExternal -> step.key == key
            else -> false
        }
        if (belongsToKey) _state.value = PaidContentPaymentState()
    }

    /** Closes the page chooser once a page was opened, or keeps the reason why no page could be opened. */
    fun closeExternal(key: PaidContentKey, failure: UiText?) {
        val step = _state.value.step
        if (step !is PaidContentStep.ChoosingExternal || step.key != key) return
        _state.value = PaidContentPaymentState(notice = failure?.let { PaidContentNotice(key, it) })
    }
}

private fun PaidContentPlan.toState(key: PaidContentKey, owner: Any): PaidContentPaymentState = when (this) {
    is PaidContentPlan.ConfirmStars -> PaidContentPaymentState(
        step = PaidContentStep.Confirming(key, owner, this.starCount, this.balance)
    )
    is PaidContentPlan.OpenExternal -> PaidContentPaymentState(
        step = PaidContentStep.ChoosingExternal(key, owner, this.methods)
    )
    is PaidContentPlan.InsufficientStars -> noticeState(key, UiText.Res(R.string.paid_content_insufficient_stars))
    PaidContentPlan.BalanceUnknown -> noticeState(key, UiText.Res(R.string.paid_content_balance_unknown))
    PaidContentPlan.SubscriptionUnsupported -> noticeState(key, UiText.Res(R.string.paid_content_subscription_unsupported))
    PaidContentPlan.Unavailable -> noticeState(key, UiText.Res(R.string.paid_content_not_available))
    is PaidContentPlan.Failed -> noticeState(key, this.reason)
}

private fun noticeState(key: PaidContentKey, text: UiText): PaidContentPaymentState =
    PaidContentPaymentState(notice = PaidContentNotice(key, text))

private fun PaidContentPaymentOutcome.toText(): UiText = when (this) {
    PaidContentPaymentOutcome.Paid -> UiText.Res(R.string.paid_content_paid_notice)
    PaidContentPaymentOutcome.InsufficientStars -> UiText.Res(R.string.paid_content_insufficient_stars)
    PaidContentPaymentOutcome.FloodWait -> UiText.Res(R.string.err_tdlib_flood_wait)
    PaidContentPaymentOutcome.PriceChanged -> UiText.Res(R.string.paid_content_price_changed)
    PaidContentPaymentOutcome.NotPayable -> UiText.Res(R.string.paid_content_not_available)
    PaidContentPaymentOutcome.Busy -> UiText.Res(R.string.paid_content_busy)
    is PaidContentPaymentOutcome.Failed -> this.reason
    PaidContentPaymentOutcome.Uncertain -> UiText.Res(R.string.paid_content_uncertain)
}

/** The entry points that one bubble needs. The conversation is the one that shows the bubble. */
internal class PaidContentActions(private val conversation: ConversationRepository) {
    fun request(key: PaidContentKey, owner: Any) = PaidContentPaymentStore.request(conversation, key, owner)
    fun confirm(key: PaidContentKey) = PaidContentPaymentStore.confirm(conversation, key)
    fun dismiss(key: PaidContentKey) = PaidContentPaymentStore.dismiss(key)
    fun closeExternal(key: PaidContentKey, failure: UiText?) = PaidContentPaymentStore.closeExternal(key, failure)
}

@Composable
internal fun rememberPaidContentActions(conversation: ConversationRepository): PaidContentActions =
    remember(conversation) { PaidContentActions(conversation) }

internal fun ChatMessage.paidContentKey(): PaidContentKey = PaidContentKey(chatId = chatId, messageId = id)
