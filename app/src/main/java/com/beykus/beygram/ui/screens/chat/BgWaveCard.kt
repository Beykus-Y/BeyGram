package com.beykus.beygram.ui.screens.chat

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.media.bgwave.BgWaveCardAction
import com.beykus.beygram.media.bgwave.BgWaveCardKeys
import com.beykus.beygram.media.bgwave.BgWaveCardState
import com.beykus.beygram.media.bgwave.BgWaveCardStatus
import com.beykus.beygram.media.bgwave.BgWaveErrors
import com.beykus.beygram.media.bgwave.BgWaveHub
import com.beykus.beygram.media.bgwave.BgWaveLink
import com.beykus.beygram.media.bgwave.BgWaveNative
import com.beykus.beygram.media.bgwave.BgWaveRole
import com.beykus.beygram.media.bgwave.bgWaveCardStateFor
import com.beykus.beygram.media.bgwave.bgWaveCardView
import com.beykus.beygram.ui.components.Button
import com.beykus.beygram.ui.components.OutlinedButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.styledShape
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/** The wave card state of a message, or null for an ordinary message. Cheap for messages without a wave link. */
internal fun bgWaveStateOf(message: ChatMessage): Pair<String, BgWaveCardState>? {
    if (message.isForwarded || (!message.isOutgoing && message.senderId != message.chatId)) return null
    val link = BgWaveLink.find(message.text, message.entities) ?: return null
    // Judged at the time the message was sent, so a card of a wave that is still running does not turn into «expired»
    // five minutes later; the card itself checks the invitation's expiry against the clock.
    val judgedAt = message.dateSeconds.takeIf { it > 0L } ?: BgWaveHub.nowSeconds()
    val state = bgWaveCardStateFor(BgWaveNative.decodeUri(link, judgedAt)) ?: return null
    return link to state
}

/** True for a hidden ANSWER, CANCEL or END: its effect shows on the original card, so the line itself is not drawn. */
internal fun isBgWaveSignalMessage(message: ChatMessage): Boolean =
    BgWaveLink.find(message.text, message.entities) != null && bgWaveStateOf(message)?.second is BgWaveCardState.Signal

@StringRes
private fun safeTextRes(key: String): Int = when (key) {
    BgWaveCardKeys.UNSUPPORTED_VERSION -> R.string.bgwave_safe_unsupported_version
    BgWaveCardKeys.EXPIRED -> R.string.bgwave_safe_expired
    BgWaveCardKeys.NOT_YET_VALID -> R.string.bgwave_safe_not_yet_valid
    BgWaveCardKeys.UNAVAILABLE -> R.string.bgwave_safe_unavailable
    else -> R.string.bgwave_safe_invalid
}

@StringRes
private fun errorRes(key: String?): Int? = when (key) {
    BgWaveErrors.UNAVAILABLE -> R.string.bgwave_error_unavailable
    BgWaveErrors.SEND_FAILED -> R.string.bgwave_error_send_failed
    BgWaveErrors.NO_CONNECTION -> R.string.bgwave_error_no_connection
    BgWaveErrors.BUSY -> R.string.bgwave_error_busy
    BgWaveErrors.EXPIRED -> R.string.bgwave_error_expired
    BgWaveErrors.REJECTED -> R.string.bgwave_error_rejected
    BgWaveErrors.OFFER_TOO_LARGE -> R.string.bgwave_error_offer_too_large
    else -> null
}

@StringRes
private fun statusRes(status: BgWaveCardStatus): Int = when (status) {
    BgWaveCardStatus.Pending -> R.string.bgwave_card_invite_subtitle
    BgWaveCardStatus.Sent -> R.string.bgwave_status_sent
    BgWaveCardStatus.Offering -> R.string.bgwave_status_offering
    BgWaveCardStatus.AwaitingAnswer -> R.string.bgwave_status_awaiting
    BgWaveCardStatus.Connecting -> R.string.bgwave_status_connecting
    BgWaveCardStatus.Live -> R.string.bgwave_status_live
    BgWaveCardStatus.Reconnecting -> R.string.bgwave_status_reconnecting
    BgWaveCardStatus.Failed -> R.string.bgwave_status_failed
    BgWaveCardStatus.Ended -> R.string.bgwave_status_ended
    BgWaveCardStatus.Declined -> R.string.bgwave_status_declined
    BgWaveCardStatus.Cancelled -> R.string.bgwave_status_cancelled
    BgWaveCardStatus.Expired -> R.string.bgwave_safe_expired
    BgWaveCardStatus.Unavailable -> R.string.bgwave_safe_unavailable
}

/**
 * The card a BG Wave message becomes in the chat. The raw service line is not drawn; unknown, expired or damaged data is
 * shown as plain safe text. Nothing here opens the link: «Присоединиться» goes through the wave controller only.
 */
@Composable
internal fun BgWaveMessageCard(message: ChatMessage, accountId: String) {
    val resolved = remember(message.id, message.text, message.entities, message.isForwarded, message.senderId) { bgWaveStateOf(message) } ?: return
    when (val state = resolved.second) {
        is BgWaveCardState.SafeText -> SafeWaveText(state.messageKey, resolved.first)
        is BgWaveCardState.Signal -> Unit
        is BgWaveCardState.Invite -> WaveCard(message, state, accountId)
    }
}

@Composable
private fun SafeWaveText(messageKey: String, link: String) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val linkLabel = stringResource(R.string.bgwave_service_label)
    val openLabel = stringResource(R.string.a11y_open_link, linkLabel)
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            stringResource(safeTextRes(messageKey)),
            color = palette.textSecondary,
            fontSize = 14.sp,
        )
        // The service link is shown, never opened by itself: only an explicit tap goes to the usual message-link path.
        Text(
            link,
            color = palette.accent,
            fontSize = 13.sp,
            textDecoration = TextDecoration.Underline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(onClickLabel = openLabel, role = Role.Button) { openWithSystemHandler(context, link) }
                .padding(vertical = 12.dp)
                .semantics { contentDescription = linkLabel },
        )
    }
}

@Composable
private fun WaveCard(message: ChatMessage, invite: BgWaveCardState.Invite, accountId: String) {
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    val session by BgWaveHub.controller.state.collectAsStateWithLifecycle()
    val invites by BgWaveHub.controller.inviteStates.collectAsStateWithLifecycle()
    val now by produceState(BgWaveHub.nowSeconds(), invite.sidHex, invite.expiresAt) {
        val initial = BgWaveHub.nowSeconds()
        value = initial
        if (initial < invite.expiresAt) {
            // Recheck wall time periodically so a clock adjustment cannot leave an expired card joinable.
            while (value < invite.expiresAt) {
                delay(1_000L)
                value = BgWaveHub.nowSeconds()
            }
        }
    }
    val ownedInvite = invites[invite.sidHex]?.takeIf { it.accountId == accountId && it.chatId == message.chatId }
    val view = bgWaveCardView(invite.sidHex, invite.expiresAt, message.isOutgoing, ownedInvite, session, now)
    val actions = if (session?.sidHex == invite.sidHex &&
        (session?.accountId != accountId || session?.chatId != message.chatId)) emptyList() else view.actions
    val statusText = stringResource(statusRes(view.status))
    val errorText = errorRes(view.errorKey)?.let { stringResource(it) }
    val roleText = (view.role ?: if (message.isOutgoing) BgWaveRole.Host else BgWaveRole.Listener).let {
        stringResource(if (it == BgWaveRole.Host) R.string.bgwave_card_role_host else R.string.bgwave_card_role_listener)
    }
    val listenersText = view.listeners?.let { stringResource(R.string.bgwave_card_listeners, it) }
    val routeText = if (view.directRoute) stringResource(R.string.bgwave_card_route_direct) else null
    val sourceText = if (view.sourceUnsupported) stringResource(R.string.bgwave_status_source_unsupported) else null
    val iconDescription = stringResource(R.string.bgwave_card_invite_title)
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp)
            .clip(styledShape(8.dp))
            .background(palette.accent.copy(alpha = 0.08f))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(palette.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = iconDescription, tint = palette.onAccent, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(start = 10.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(stringResource(R.string.bgwave_card_invite_title), color = palette.messageText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(roleText, color = palette.textSecondary, fontSize = 12.sp)
                Text(statusText, color = palette.textSecondary, fontSize = 12.sp)
                if (listenersText != null) Text(listenersText, color = palette.textSecondary, fontSize = 12.sp)
                if (routeText != null) Text(routeText, color = palette.textSecondary, fontSize = 12.sp)
                if (sourceText != null) Text(sourceText, color = palette.danger, fontSize = 12.sp)
                if (errorText != null) Text(errorText, color = palette.danger, fontSize = 12.sp)
            }
        }
        if (actions.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (action in actions) {
                    when (action) {
                        BgWaveCardAction.Join -> Button(onClick = { scope.launch { BgWaveHub.join(invite.sidHex) } }) {
                            Text(stringResource(R.string.bgwave_action_join))
                        }
                        BgWaveCardAction.Decline -> OutlinedButton(onClick = { scope.launch { BgWaveHub.decline(invite.sidHex) } }) {
                            Text(stringResource(R.string.bgwave_action_decline))
                        }
                        BgWaveCardAction.Stop -> OutlinedButton(onClick = { scope.launch { BgWaveHub.stop() } }) {
                            Text(stringResource(R.string.bgwave_action_stop))
                        }
                        BgWaveCardAction.Retry -> Button(onClick = { BgWaveHub.retry() }) {
                            Text(stringResource(R.string.bgwave_action_retry))
                        }
                    }
                }
            }
        }
    }
}
