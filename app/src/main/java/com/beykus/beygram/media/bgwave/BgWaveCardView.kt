package com.beykus.beygram.media.bgwave

enum class BgWaveCardStatus {
    Pending, Sent, Offering, AwaitingAnswer, Connecting, Live, Reconnecting, Failed, Ended, Declined, Cancelled, Expired, Unavailable
}

enum class BgWaveCardAction { Join, Decline, Stop, Retry }

/** What one wave card shows and offers. Text comes from string resources keyed by [status] and [errorKey]. */
data class BgWaveCardView(
    val status: BgWaveCardStatus,
    val errorKey: String?,
    val actions: List<BgWaveCardAction>,
    /** Which side this device is on, once a wave of this card is running here; null for a card that is only a message. */
    val role: BgWaveRole? = null,
    /** Host side only: listeners connected right now (0 or 1, a wave has one listener); null when not a running host card. */
    val listeners: Int? = null,
    /** True while the live wave runs over a direct connection. */
    val directRoute: Boolean = false,
    /** Host side: the current playback source cannot be sent into the wave. */
    val sourceUnsupported: Boolean = false,
)

/** True when the native layer reports a direct peer-to-peer route. */
fun isDirectRoute(route: String): Boolean = route.equals("Direct", ignoreCase = true)

/**
 * Decides the card of one wave message from the controller's state. A card only offers what can work right now: an
 * expired or settled invitation has no buttons, and «Присоединиться» is absent while another wave is running.
 */
fun bgWaveCardView(
    sidHex: String,
    expiresAt: Long,
    isOutgoing: Boolean,
    invite: BgWaveInvite?,
    session: BgWaveSessionState?,
    nowSeconds: Long,
): BgWaveCardView {
    if (session != null && session.sidHex == sidHex) return sessionCard(session)
    if (isOutgoing) {
        val status = if (nowSeconds >= expiresAt) BgWaveCardStatus.Expired else BgWaveCardStatus.Sent
        return BgWaveCardView(status, null, emptyList())
    }
    if (invite == null) {
        val status = if (nowSeconds >= expiresAt) BgWaveCardStatus.Expired else BgWaveCardStatus.Unavailable
        return BgWaveCardView(status, null, emptyList())
    }
    return when (invite.status) {
        BgWaveInviteStatus.Pending ->
            if (nowSeconds >= invite.expiresAt) BgWaveCardView(BgWaveCardStatus.Expired, null, emptyList())
            else {
                val busy = session != null && session.phase != BgWavePhase.Ended && session.phase != BgWavePhase.Failed
                val actions = if (busy) listOf(BgWaveCardAction.Decline) else listOf(BgWaveCardAction.Join, BgWaveCardAction.Decline)
                BgWaveCardView(BgWaveCardStatus.Pending, null, actions)
            }
        BgWaveInviteStatus.Joined, BgWaveInviteStatus.Ended -> BgWaveCardView(BgWaveCardStatus.Ended, null, emptyList())
        BgWaveInviteStatus.Declined -> BgWaveCardView(BgWaveCardStatus.Declined, null, emptyList())
        BgWaveInviteStatus.Cancelled -> BgWaveCardView(BgWaveCardStatus.Cancelled, null, emptyList())
        BgWaveInviteStatus.Expired -> BgWaveCardView(BgWaveCardStatus.Expired, null, emptyList())
    }
}

private fun sessionCard(session: BgWaveSessionState): BgWaveCardView {
    val base = sessionCardBase(session)
    val connected = session.phase == BgWavePhase.Live || session.phase == BgWavePhase.Reconnecting
    val running = session.phase != BgWavePhase.Failed && session.phase != BgWavePhase.Ended
    return base.copy(
        role = session.role,
        listeners = if (session.role == BgWaveRole.Host && running) (if (session.phase == BgWavePhase.Live) 1 else 0) else null,
        directRoute = session.phase == BgWavePhase.Live && isDirectRoute(session.route),
        sourceUnsupported = session.role == BgWaveRole.Host && connected && session.sourceUnsupported,
    )
}

private fun sessionCardBase(session: BgWaveSessionState): BgWaveCardView = when (session.phase) {
    BgWavePhase.Offering -> BgWaveCardView(BgWaveCardStatus.Offering, null, listOf(BgWaveCardAction.Stop))
    BgWavePhase.AwaitingAnswer -> BgWaveCardView(BgWaveCardStatus.AwaitingAnswer, null, listOf(BgWaveCardAction.Stop))
    BgWavePhase.Connecting -> BgWaveCardView(BgWaveCardStatus.Connecting, null, listOf(BgWaveCardAction.Stop))
    BgWavePhase.Live -> BgWaveCardView(BgWaveCardStatus.Live, null, listOf(BgWaveCardAction.Stop))
    BgWavePhase.Reconnecting -> BgWaveCardView(BgWaveCardStatus.Reconnecting, null, listOf(BgWaveCardAction.Stop))
    BgWavePhase.Failed -> BgWaveCardView(
        BgWaveCardStatus.Failed,
        session.errorKey,
        if (session.errorKey == BgWaveErrors.NO_CONNECTION) listOf(BgWaveCardAction.Retry, BgWaveCardAction.Stop) else listOf(BgWaveCardAction.Stop),
    )
    BgWavePhase.Ended -> BgWaveCardView(BgWaveCardStatus.Ended, null, emptyList())
}
