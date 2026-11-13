package com.beykus.beygram.media.bgwave

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Native session seen by the controller; [BgWaveSession] is the production implementation. */
interface BgWaveSessionPort : AutoCloseable {
    fun startOffer(nowSeconds: Long, ttlSeconds: Long): Boolean
    fun handleRemote(uri: String, nowSeconds: Long): Boolean
    fun pollOutbound(): String?
    fun status(): BgWaveSessionStatus
    fun stop(emitEnd: Boolean, nowSeconds: Long)
    fun reconnect(nowSeconds: Long): Boolean
    /** Native handle for attaching the host PCM tap; 0 when there is no native session. */
    fun sessionHandle(): Long = 0L
    /** Listener: pulls decoded interleaved stereo frames for playback. Returns frames written. */
    fun pullListenerPcm(out: FloatArray, frames: Int, sampleRate: Int): Int = 0
}

enum class BgWaveRole { Host, Listener }

enum class BgWavePhase { Offering, AwaitingAnswer, Connecting, Live, Reconnecting, Failed, Ended }

/** The one wave this device takes part in. Everything shown to the user is derived from it; no text lives here. */
data class BgWaveSessionState(
    val role: BgWaveRole,
    val accountId: String,
    val chatId: Long,
    val peerId: Long,
    val sidHex: String,
    val phase: BgWavePhase,
    /** Resource key of the failure, set only in [BgWavePhase.Failed]. */
    val errorKey: String? = null,
    /** "None", "Direct" or the route reported by the native layer; shown only as a status, never as a promise. */
    val route: String = "None",
)

/** What the card of one received invitation shows. */
enum class BgWaveInviteStatus { Pending, Joined, Declined, Cancelled, Ended, Expired }

data class BgWaveInvite(
    val accountId: String,
    val chatId: Long,
    val senderId: Long,
    val uri: String,
    val expiresAt: Long,
    val status: BgWaveInviteStatus,
)

object BgWaveErrors {
    const val UNAVAILABLE = "bgwave_error_unavailable"
    const val SEND_FAILED = "bgwave_error_send_failed"
    const val NO_CONNECTION = "bgwave_error_no_connection"
    const val BUSY = "bgwave_error_busy"
    const val EXPIRED = "bgwave_error_expired"
    const val REJECTED = "bgwave_error_rejected"
}

/**
 * Drives one BG Wave from the user's point of view (issue #799): the host offers, the listener joins, both see honest
 * states. It owns no codec and no audio: every signaling byte goes through the BG-Media session, and the audio is the
 * session's business. What it does own is the trust decision about each message: it acts only on a message that comes
 * from the expected peer, in the expected chat and account, for the expected session, once.
 *
 * All entry points are synchronized, so TDLib updates and button taps can arrive on any thread.
 */
class BgWaveController(
    private val createSession: (isHost: Boolean) -> BgWaveSessionPort?,
    private val decode: (uri: String, nowSeconds: Long) -> BgWaveDecodeResult,
    private val encodeControl: (type: Int, sidHex: String, nowSeconds: Long, nonce: Long) -> String,
    private val maxInvites: Int = 50,
) {
    private val lock = Any()
    private var session: BgWaveSessionPort? = null
    private val invites = LinkedHashMap<String, BgWaveInvite>()
    /** Highest nonce accepted per session and direction; a repeat or an older one is a replay. */
    private val lastNonce = HashMap<String, Long>()
    /** Sessions that ended or were cancelled; they are never opened again. */
    private val closed = HashSet<String>()

    private val _state = MutableStateFlow<BgWaveSessionState?>(null)
    val state: StateFlow<BgWaveSessionState?> = _state.asStateFlow()

    private val _invites = MutableStateFlow<Map<String, BgWaveInvite>>(emptyMap())
    val inviteStates: StateFlow<Map<String, BgWaveInvite>> = _invites.asStateFlow()

    /**
     * Starts a wave as host and returns the https link to send to [chatId], or null with the reason in [state]. The
     * caller sends it and then reports the outcome with [offerSent].
     */
    fun startHosting(accountId: String, chatId: Long, peerId: Long, nowSeconds: Long): String? = synchronized(lock) {
        if (session != null) return fail(BgWaveErrors.BUSY, keepSession = true)
        val created = createSession(true) ?: return fail(BgWaveErrors.UNAVAILABLE)
        if (!created.startOffer(nowSeconds, OFFER_TTL_SECONDS)) {
            created.close()
            return fail(BgWaveErrors.UNAVAILABLE)
        }
        val uri = created.pollOutbound()
        val decoded = uri?.let { decode(it, nowSeconds) } as? BgWaveDecodeResult.Ok
        val link = uri?.let { BgWaveLink.toHttps(it) }
        if (decoded == null || decoded.type != TYPE_OFFER || link == null) {
            created.close()
            return fail(BgWaveErrors.REJECTED)
        }
        session = created
        BgWaveLog.i("host.offer.created", "sid" to BgWaveLog.sid(decoded.sidHex))
        _state.value = BgWaveSessionState(BgWaveRole.Host, accountId, chatId, peerId, decoded.sidHex, BgWavePhase.Offering)
        link
    }

    /** The live native session, for the audio pump. Read when the wave changes phase, never per frame. */
    fun activeSession(): BgWaveSessionPort? = synchronized(lock) { session }

    /** The invitation was handed to Telegram (or not). A failed send tears the wave down. */
    fun offerSent(sidHex: String, success: Boolean, nowSeconds: Long): Unit = synchronized(lock) {
        val current = _state.value ?: return
        if (current.sidHex != sidHex || current.role != BgWaveRole.Host || current.phase != BgWavePhase.Offering) return
        BgWaveLog.i("host.offer.sent", "sid" to BgWaveLog.sid(sidHex), "ok" to success)
        if (success) {
            _state.value = current.copy(phase = BgWavePhase.AwaitingAnswer)
        } else {
            closeSession(emitEnd = false, nowSeconds = nowSeconds)
            _state.value = current.copy(phase = BgWavePhase.Failed, errorKey = BgWaveErrors.SEND_FAILED)
        }
    }

    /**
     * Feeds one Telegram message that carries a wave link. [outgoing] messages are the user's own and never drive the
     * wave; [privateChat] must be true only for an ordinary one-to-one chat, never a group, channel or secret chat.
     */
    fun onMessage(
        accountId: String,
        chatId: Long,
        senderId: Long,
        outgoing: Boolean,
        privateChat: Boolean,
        uri: String,
        nowSeconds: Long,
    ): Unit = synchronized(lock) {
        if (!privateChat || outgoing) {
            BgWaveLog.i("msg.ignored", "reason" to (if (outgoing) "outgoing" else "not-private"))
            return
        }
        // In a private chat TDLib's chat id is the peer's user id, so a message must come from the chat's own peer.
        if (senderId != chatId) return
        val decoded = decode(uri, nowSeconds)
        val msg = decoded as? BgWaveDecodeResult.Ok
        if (msg == null) {
            BgWaveLog.w("msg.rejected", null, "reason" to (decoded as? BgWaveDecodeResult.Rejected)?.reason)
            return
        }
        BgWaveLog.i("msg.received", "type" to msg.type, "sid" to BgWaveLog.sid(msg.sidHex))
        if (msg.sidHex in closed) return
        // A signal for a session this device never heard of is dropped, so strangers cannot fill the nonce table.
        if (msg.type != TYPE_OFFER && !invites.containsKey(msg.sidHex) && _state.value?.sidHex != msg.sidHex) {
            BgWaveLog.i("msg.unknown-session", "sid" to BgWaveLog.sid(msg.sidHex))
            return
        }
        if (!acceptNonce(msg.sidHex, msg.type, msg.nonce)) {
            BgWaveLog.w("msg.replay", null, "type" to msg.type, "sid" to BgWaveLog.sid(msg.sidHex))
            return
        }
        when (msg.type) {
            TYPE_OFFER -> rememberInvite(accountId, chatId, senderId, uri, msg)
            TYPE_ANSWER -> onAnswer(accountId, chatId, senderId, uri, msg, nowSeconds)
            TYPE_CANCEL, TYPE_END -> onTeardown(accountId, chatId, senderId, msg)
            else -> Unit
        }
    }

    /** Listener taps «Присоединиться». Returns the https ANSWER link to send, or null with the reason in [state]. */
    fun join(sidHex: String, nowSeconds: Long): String? = synchronized(lock) {
        val invite = invites[sidHex]
        if (invite == null || invite.status != BgWaveInviteStatus.Pending) return null
        if (session != null) return fail(BgWaveErrors.BUSY, keepSession = true)
        if (nowSeconds >= invite.expiresAt) {
            setInvite(sidHex, invite.copy(status = BgWaveInviteStatus.Expired))
            return fail(BgWaveErrors.EXPIRED)
        }
        val created = createSession(false) ?: return fail(BgWaveErrors.UNAVAILABLE)
        if (!created.handleRemote(invite.uri, nowSeconds)) {
            created.close()
            return fail(BgWaveErrors.REJECTED)
        }
        val answer = created.pollOutbound()
        val decoded = answer?.let { decode(it, nowSeconds) } as? BgWaveDecodeResult.Ok
        val link = answer?.let { BgWaveLink.toHttps(it) }
        if (decoded == null || decoded.type != TYPE_ANSWER || decoded.sidHex != sidHex || link == null) {
            created.close()
            return fail(BgWaveErrors.REJECTED)
        }
        session = created
        setInvite(sidHex, invite.copy(status = BgWaveInviteStatus.Joined))
        _state.value = BgWaveSessionState(
            BgWaveRole.Listener, invite.accountId, invite.chatId, invite.senderId, sidHex, BgWavePhase.Connecting,
        )
        link
    }

    /** The answer could not be handed to Telegram; the wave cannot start. */
    fun answerSendFailed(sidHex: String, nowSeconds: Long): Unit = synchronized(lock) {
        val current = _state.value ?: return
        if (current.sidHex != sidHex || current.role != BgWaveRole.Listener) return
        closeSession(emitEnd = false, nowSeconds = nowSeconds)
        _state.value = current.copy(phase = BgWavePhase.Failed, errorKey = BgWaveErrors.SEND_FAILED)
    }

    /**
     * Listener taps «Отклонить». Returns the https CANCEL link to tell the host, or null when there is nothing to send
     * (the invitation is unknown or already settled).
     */
    fun decline(sidHex: String, nowSeconds: Long): String? = synchronized(lock) {
        val invite = invites[sidHex] ?: return null
        if (invite.status != BgWaveInviteStatus.Pending) return null
        setInvite(sidHex, invite.copy(status = BgWaveInviteStatus.Declined))
        BgWaveLink.toHttps(encodeControl(TYPE_CANCEL, sidHex, nowSeconds, FIRST_CONTROL_NONCE))
            ?.takeIf { decode(it, nowSeconds) is BgWaveDecodeResult.Ok }
    }

    /**
     * Either side taps «Остановить». Returns the https END (or CANCEL, before an answer) link for the peer, or null if
     * nothing is running. The wave is closed either way.
     */
    fun stop(nowSeconds: Long): String? = synchronized(lock) {
        val current = _state.value ?: return null
        if (current.phase == BgWavePhase.Ended || current.phase == BgWavePhase.Failed) {
            _state.value = null
            return null
        }
        val type = if (current.phase == BgWavePhase.Offering || current.phase == BgWavePhase.AwaitingAnswer) TYPE_CANCEL else TYPE_END
        closeSession(emitEnd = false, nowSeconds = nowSeconds)
        _state.value = current.copy(phase = BgWavePhase.Ended)
        BgWaveLink.toHttps(encodeControl(type, current.sidHex, nowSeconds, STOP_NONCE))
            ?.takeIf { decode(it, nowSeconds) is BgWaveDecodeResult.Ok }
    }

    /** «Повторить» after a failure: asks the session to reconnect. */
    fun retry(nowSeconds: Long): Boolean = synchronized(lock) {
        val current = _state.value ?: return false
        val active = session ?: return false
        if (current.phase != BgWavePhase.Failed && current.phase != BgWavePhase.Reconnecting) return false
        val started = active.reconnect(nowSeconds)
        _state.value = if (started) current.copy(phase = BgWavePhase.Reconnecting, errorKey = null)
        else current.copy(phase = BgWavePhase.Failed, errorKey = BgWaveErrors.NO_CONNECTION)
        started
    }

    /** Clears a finished or failed wave from the screen. */
    fun dismiss(): Unit = synchronized(lock) {
        val current = _state.value ?: return
        if (current.phase == BgWavePhase.Ended || current.phase == BgWavePhase.Failed) {
            closeSession(emitEnd = false, nowSeconds = 0L)
            _state.value = null
        }
    }

    /** Polls the native session; call about once a second while a wave is running. Returns outbound links to send. */
    fun poll(nowSeconds: Long): List<String> = synchronized(lock) {
        val current = _state.value ?: return emptyList()
        val active = session ?: return emptyList()
        val outbound = ArrayList<String>()
        while (outbound.size < MAX_POLLED) {
            val next = active.pollOutbound() ?: break
            val link = BgWaveLink.toHttps(next) ?: continue
            if (decode(link, nowSeconds) is BgWaveDecodeResult.Ok) outbound += link
        }
        if (current.phase != BgWavePhase.Offering && current.phase != BgWavePhase.AwaitingAnswer) {
            val status = active.status()
            val phase = phaseOf(status.state, current.phase)
            if (phase != current.phase || status.route != current.route) {
                BgWaveLog.i(
                    "phase",
                    "from" to current.phase,
                    "to" to phase,
                    "native" to status.state,
                    "route" to status.route,
                    "sid" to BgWaveLog.sid(current.sidHex),
                )
            }
            if (phase == BgWavePhase.Failed && current.phase != BgWavePhase.Failed && status.lastError.isNotEmpty()) {
                BgWaveLog.w("native.error", null, "msg" to status.lastError)
            }
            _state.value = current.copy(
                phase = phase,
                route = status.route,
                errorKey = if (phase == BgWavePhase.Failed) BgWaveErrors.NO_CONNECTION else null,
            )
            if (phase == BgWavePhase.Ended) closeSession(emitEnd = false, nowSeconds = nowSeconds)
        }
        outbound
    }

    /** Tears everything down, for example when the account changes. */
    fun reset(nowSeconds: Long): Unit = synchronized(lock) {
        closeSession(emitEnd = false, nowSeconds = nowSeconds)
        _state.value = null
        invites.clear()
        lastNonce.clear()
        closed.clear()
        _invites.value = emptyMap()
    }

    // The three private handlers below run with [lock] held.

    private fun onAnswer(accountId: String, chatId: Long, senderId: Long, uri: String, msg: BgWaveDecodeResult.Ok, nowSeconds: Long) {
        val current = _state.value ?: return
        val active = session ?: return
        if (current.role != BgWaveRole.Host || current.phase != BgWavePhase.AwaitingAnswer) return
        if (current.sidHex != msg.sidHex || current.accountId != accountId || current.chatId != chatId || current.peerId != senderId) return
        if (!active.handleRemote(uri, nowSeconds)) {
            closeSession(emitEnd = false, nowSeconds = nowSeconds)
            _state.value = current.copy(phase = BgWavePhase.Failed, errorKey = BgWaveErrors.REJECTED)
            return
        }
        _state.value = current.copy(phase = BgWavePhase.Connecting)
    }

    private fun onTeardown(accountId: String, chatId: Long, senderId: Long, msg: BgWaveDecodeResult.Ok) {
        val status = if (msg.type == TYPE_CANCEL) BgWaveInviteStatus.Cancelled else BgWaveInviteStatus.Ended
        invites[msg.sidHex]?.let { invite ->
            if (invite.accountId == accountId && invite.chatId == chatId && invite.senderId == senderId) {
                closed += msg.sidHex
                if (invite.status == BgWaveInviteStatus.Pending || invite.status == BgWaveInviteStatus.Joined) {
                    setInvite(msg.sidHex, invite.copy(status = status))
                }
            }
        }
        val current = _state.value ?: return
        if (current.sidHex != msg.sidHex || current.accountId != accountId || current.chatId != chatId || current.peerId != senderId) return
        closed += msg.sidHex
        closeSession(emitEnd = false, nowSeconds = 0L)
        _state.value = current.copy(phase = BgWavePhase.Ended)
    }

    private fun rememberInvite(accountId: String, chatId: Long, senderId: Long, uri: String, msg: BgWaveDecodeResult.Ok) {
        if (invites.containsKey(msg.sidHex)) return
        while (invites.size >= maxInvites) {
            val eldest = invites.keys.first()
            invites.remove(eldest)
            lastNonce.keys.removeAll { it.startsWith("$eldest:") }
        }
        setInvite(msg.sidHex, BgWaveInvite(accountId, chatId, senderId, uri, msg.expiresAt, BgWaveInviteStatus.Pending))
    }

    private fun setInvite(sidHex: String, invite: BgWaveInvite) {
        invites[sidHex] = invite
        _invites.update { invites.toMap() }
    }

    private fun acceptNonce(sidHex: String, type: Int, nonce: Long): Boolean {
        val key = "$sidHex:$type"
        val last = lastNonce[key]
        if (last != null && nonce <= last) return false
        lastNonce[key] = nonce
        return true
    }

    private fun closeSession(emitEnd: Boolean, nowSeconds: Long) {
        val active = session ?: return
        session = null
        BgWaveLog.i("session.close", "emitEnd" to emitEnd)
        try {
            active.stop(emitEnd, nowSeconds)
        } finally {
            active.close()
        }
    }

    private fun fail(errorKey: String, keepSession: Boolean = false): String? {
        val previous = _state.value
        BgWaveLog.w("fail", null, "key" to errorKey, "keepSession" to keepSession)
        if (!keepSession || previous == null) {
            _state.value = BgWaveSessionState(
                role = previous?.role ?: BgWaveRole.Host,
                accountId = previous?.accountId.orEmpty(),
                chatId = previous?.chatId ?: 0L,
                peerId = previous?.peerId ?: 0L,
                sidHex = previous?.sidHex.orEmpty(),
                phase = BgWavePhase.Failed,
                errorKey = errorKey,
            )
        }
        return null
    }

    companion object {
        const val TYPE_OFFER = 1
        const val TYPE_ANSWER = 2
        const val TYPE_CANCEL = 3
        const val TYPE_END = 4
        const val OFFER_TTL_SECONDS = 300L
        private const val FIRST_CONTROL_NONCE = 1L
        private const val STOP_NONCE = 2L
        private const val MAX_POLLED = 8

        /** Maps the native pipeline state name onto the phase the UI shows. Unknown names change nothing. */
        internal fun phaseOf(nativeState: String, fallback: BgWavePhase): BgWavePhase = when {
            nativeState.contains("Live", ignoreCase = true) -> BgWavePhase.Live
            nativeState.contains("Reconnect", ignoreCase = true) -> BgWavePhase.Reconnecting
            nativeState.contains("Fail", ignoreCase = true) -> BgWavePhase.Failed
            nativeState.contains("End", ignoreCase = true) -> BgWavePhase.Ended
            nativeState.contains("Connect", ignoreCase = true) -> BgWavePhase.Connecting
            else -> fallback
        }
    }
}
