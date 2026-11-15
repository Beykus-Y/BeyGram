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
    /** Host: opens a PCM tap for the render thread; 0 when there is no native session. */
    fun openHostTap(): Long = 0L
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
    private var starting = false
    private var startingAccountId: String? = null
    private var generation = 0L
    private var offerExpiresAt = 0L
    private val invites = LinkedHashMap<String, BgWaveInvite>()
    /** Highest nonce accepted per session and direction; a repeat or an older one is a replay. */
    private val lastNonce = HashMap<String, Long>()
    /** Sessions that ended or were cancelled; bounded FIFO so long-running sessions cannot grow it unboundedly. */
    private val closed = LinkedHashSet<String>()

    private val _state = MutableStateFlow<BgWaveSessionState?>(null)
    val state: StateFlow<BgWaveSessionState?> = _state.asStateFlow()

    private val _invites = MutableStateFlow<Map<String, BgWaveInvite>>(emptyMap())
    val inviteStates: StateFlow<Map<String, BgWaveInvite>> = _invites.asStateFlow()

    /**
     * Starts a wave as host and returns the https link to send to [chatId], or null with the reason in [state]. The
     * caller sends it and then reports the outcome with [offerSent]. Native ICE gathering runs without holding [lock].
     */
    fun startHosting(accountId: String, chatId: Long, peerId: Long, nowSeconds: Long): String? {
        val gen = synchronized(lock) {
            if (starting) return fail(BgWaveErrors.BUSY, keepSession = true)
            if (session != null) {
                val previousPhase = _state.value?.phase
                if (previousPhase == BgWavePhase.Failed || previousPhase == BgWavePhase.Ended) {
                    closeSession(emitEnd = false, nowSeconds = nowSeconds)
                    _state.value = null
                } else {
                    return fail(BgWaveErrors.BUSY, keepSession = true)
                }
            }
            starting = true
            startingAccountId = accountId
            val currentGen = ++generation
            _state.value = BgWaveSessionState(BgWaveRole.Host, accountId, chatId, peerId, "", BgWavePhase.Offering)
            currentGen
        }
        var created: BgWaveSessionPort? = null
        try {
            created = createSession(true)
            if (created == null) {
                return finishStartFailed(gen, BgWaveRole.Host, accountId, chatId, peerId, "", BgWaveErrors.UNAVAILABLE)
            }
            if (!created.startOffer(nowSeconds, OFFER_TTL_SECONDS)) {
                created.close()
                created = null
                return finishStartFailed(gen, BgWaveRole.Host, accountId, chatId, peerId, "", BgWaveErrors.UNAVAILABLE)
            }
            val uri = created.pollOutbound()
            val decoded = uri?.let { decode(it, nowSeconds) } as? BgWaveDecodeResult.Ok
            val link = uri?.let { BgWaveLink.toHttps(it) }
            if (decoded == null || decoded.type != TYPE_OFFER || link == null) {
                created.close()
                created = null
                return finishStartFailed(gen, BgWaveRole.Host, accountId, chatId, peerId, "", BgWaveErrors.REJECTED)
            }
            return commitHostSession(gen, created, accountId, chatId, peerId, decoded, link)
        } catch (t: Throwable) {
            created?.let { runCatching { it.close() } }
            synchronized(lock) {
                if (generation == gen && starting) {
                    starting = false
                    startingAccountId = null
                    if (_state.value?.sidHex.isNullOrEmpty()) _state.value = null
                }
            }
            throw t
        }
    }

    /** Host: opens a PCM tap on the live session, under the controller lock so the session cannot close meanwhile. */
    fun openHostTap(): Long = synchronized(lock) { session?.openHostTap() ?: 0L }

    /** The live native session, for the audio pump. Read when the wave changes phase, never per frame. */
    fun activeSession(): BgWaveSessionPort? = synchronized(lock) { session }

    /** The invitation was handed to Telegram (or not). A failed send tears the wave down. */
    fun offerSent(sidHex: String, success: Boolean, nowSeconds: Long): Unit = synchronized(lock) {
        val current = _state.value ?: return
        if (starting || current.sidHex != sidHex || current.role != BgWaveRole.Host || current.phase != BgWavePhase.Offering) return
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
        // Validate the sender binding before advancing replay protection. A different account
        // or chat must never be able to exhaust the real peer's nonce window.
        val boundInvite = invites[msg.sidHex]
        if (boundInvite != null && (boundInvite.accountId != accountId ||
                boundInvite.chatId != chatId || boundInvite.senderId != senderId)) return
        val boundState = _state.value?.takeIf { it.sidHex == msg.sidHex }
        if (boundState != null && (boundState.accountId != accountId ||
                boundState.chatId != chatId || boundState.peerId != senderId)) return
        if (!acceptNonce(msg.sidHex, msg.type, msg.nonce)) {
            BgWaveLog.w("msg.replay", null, "type" to msg.type, "sid" to BgWaveLog.sid(msg.sidHex))
            return
        }
        when (msg.type) {
            TYPE_OFFER -> onOffer(accountId, chatId, senderId, uri, msg, nowSeconds)
            TYPE_ANSWER -> onAnswer(accountId, chatId, senderId, uri, msg, nowSeconds)
            TYPE_CANCEL, TYPE_END -> onTeardown(accountId, chatId, senderId, msg)
            else -> Unit
        }
    }

    /** Listener taps «Присоединиться». Returns the https ANSWER link to send, or null with the reason in [state]. */
    fun join(sidHex: String, nowSeconds: Long): String? {
        val (invite, gen) = synchronized(lock) {
            val found = invites[sidHex]
            if (found == null || found.status != BgWaveInviteStatus.Pending) return null
            if (starting) return fail(BgWaveErrors.BUSY, keepSession = true)
            if (session != null) {
                val previousPhase = _state.value?.phase
                if (previousPhase == BgWavePhase.Failed || previousPhase == BgWavePhase.Ended) {
                    closeSession(emitEnd = false, nowSeconds = nowSeconds)
                    _state.value = null
                } else {
                    return fail(BgWaveErrors.BUSY, keepSession = true)
                }
            }
            if (nowSeconds >= found.expiresAt) {
                setInvite(sidHex, found.copy(status = BgWaveInviteStatus.Expired))
                return fail(
                    BgWaveErrors.EXPIRED,
                    role = BgWaveRole.Listener,
                    accountId = found.accountId,
                    chatId = found.chatId,
                    peerId = found.senderId,
                    attemptSidHex = sidHex,
                )
            }
            starting = true
            startingAccountId = found.accountId
            val currentGen = ++generation
            _state.value = BgWaveSessionState(
                BgWaveRole.Listener, found.accountId, found.chatId, found.senderId, sidHex, BgWavePhase.Connecting,
            )
            found to currentGen
        }
        var created: BgWaveSessionPort? = null
        try {
            created = createSession(false)
            if (created == null) {
                return finishStartFailed(
                    gen, BgWaveRole.Listener, invite.accountId, invite.chatId, invite.senderId, sidHex, BgWaveErrors.UNAVAILABLE,
                )
            }
            if (!created.handleRemote(invite.uri, nowSeconds)) {
                created.close()
                created = null
                return finishStartFailed(
                    gen, BgWaveRole.Listener, invite.accountId, invite.chatId, invite.senderId, sidHex, BgWaveErrors.REJECTED,
                )
            }
            val answer = created.pollOutbound()
            val decoded = answer?.let { decode(it, nowSeconds) } as? BgWaveDecodeResult.Ok
            val link = answer?.let { BgWaveLink.toHttps(it) }
            if (decoded == null || decoded.type != TYPE_ANSWER || decoded.sidHex != sidHex || link == null) {
                created.close()
                created = null
                return finishStartFailed(
                    gen, BgWaveRole.Listener, invite.accountId, invite.chatId, invite.senderId, sidHex, BgWaveErrors.REJECTED,
                )
            }
            return commitJoinSession(gen, created, sidHex, link)
        } catch (t: Throwable) {
            created?.let { runCatching { it.close() } }
            synchronized(lock) {
                if (generation == gen && starting) {
                    starting = false
                    startingAccountId = null
                    if (_state.value?.sidHex == sidHex) _state.value = null
                }
            }
            throw t
        }
    }

    /** The answer could not be handed to Telegram; the wave cannot start. */
    fun answerSendFailed(sidHex: String, nowSeconds: Long): Unit = synchronized(lock) {
        val current = _state.value ?: return
        if (current.sidHex != sidHex || current.role != BgWaveRole.Listener || current.phase != BgWavePhase.Connecting) return
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
        markClosed(sidHex)
        setInvite(sidHex, invite.copy(status = BgWaveInviteStatus.Declined))
        BgWaveLink.toHttps(encodeControl(TYPE_CANCEL, sidHex, nowSeconds, FIRST_CONTROL_NONCE))
            ?.takeIf { decode(it, nowSeconds) is BgWaveDecodeResult.Ok }
    }

    /**
     * Either side taps «Остановить». Returns the https END (or CANCEL, before an answer) link for the peer, or null if
     * nothing is running. The wave is closed either way.
     */
    fun stop(nowSeconds: Long): String? = synchronized(lock) {
        val current = _state.value ?: run {
            if (starting) {
                generation++
                starting = false
                startingAccountId = null
            }
            closeSession(emitEnd = false, nowSeconds = nowSeconds)
            return null
        }
        if (current.phase == BgWavePhase.Ended || current.phase == BgWavePhase.Failed) {
            markClosed(current.sidHex)
            closeSession(emitEnd = false, nowSeconds = nowSeconds)
            _state.value = null
            return null
        }
        val wasStarting = starting
        if (wasStarting) {
            generation++
            starting = false
            startingAccountId = null
        }
        if (current.sidHex.isEmpty()) {
            closeSession(emitEnd = false, nowSeconds = nowSeconds)
            _state.value = null
            return null
        }
        markClosed(current.sidHex)
        if (current.role == BgWaveRole.Listener) {
            invites[current.sidHex]?.let { invite ->
                if (invite.status == BgWaveInviteStatus.Pending) {
                    setInvite(current.sidHex, invite.copy(status = BgWaveInviteStatus.Ended))
                }
            }
        }
        val type = if (current.phase == BgWavePhase.Offering || current.phase == BgWavePhase.AwaitingAnswer) TYPE_CANCEL else TYPE_END
        closeSession(emitEnd = false, nowSeconds = nowSeconds)
        _state.value = current.copy(phase = BgWavePhase.Ended)
        if (wasStarting && current.role == BgWaveRole.Listener) return null
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

    /** Encodes a CANCEL link for an offer whose send finished after [stop] already ended the local wave. */
    internal fun cancelLink(sidHex: String, nowSeconds: Long): String? =
        if (sidHex.isEmpty()) null
        else BgWaveLink.toHttps(encodeControl(TYPE_CANCEL, sidHex, nowSeconds, ABORT_CANCEL_NONCE))
            ?.takeIf { decode(it, nowSeconds) is BgWaveDecodeResult.Ok }

    /** Polls the native session; call about once a second while a wave is running. Returns outbound links to send. */
    fun poll(nowSeconds: Long): List<String> = synchronized(lock) {
        val current = _state.value ?: return emptyList()
        val active = session ?: return emptyList()
        if (current.phase == BgWavePhase.AwaitingAnswer && offerExpiresAt > 0L && nowSeconds >= offerExpiresAt) {
            BgWaveLog.i("host.offer.expired", "sid" to BgWaveLog.sid(current.sidHex))
            markClosed(current.sidHex)
            closeSession(emitEnd = false, nowSeconds = nowSeconds)
            _state.value = current.copy(phase = BgWavePhase.Ended, errorKey = null)
            return emptyList()
        }
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
            if (phase == BgWavePhase.Ended) {
                markClosed(current.sidHex)
                closeSession(emitEnd = false, nowSeconds = nowSeconds)
            }
        }
        outbound
    }

    /** Tears everything down, for example in tests or on a full app reset. */
    fun reset(nowSeconds: Long): Unit = synchronized(lock) {
        generation++
        starting = false
        startingAccountId = null
        closeSession(emitEnd = false, nowSeconds = nowSeconds)
        _state.value = null
        invites.clear()
        lastNonce.clear()
        closed.clear()
        _invites.value = emptyMap()
    }

    /** Clears only [accountId]'s invitations and closes the active wave only if it belongs to [accountId]. */
    fun resetAccount(accountId: String, nowSeconds: Long): Unit = synchronized(lock) {
        val removed = invites.entries.filter { it.value.accountId == accountId }.map { it.key }
        if (removed.isNotEmpty()) {
            for (sidHex in removed) {
                invites.remove(sidHex)
                lastNonce.keys.removeAll { it.startsWith("$sidHex:") }
            }
            _invites.update { invites.toMap() }
        }
        if (starting && startingAccountId == accountId) {
            generation++
            starting = false
            startingAccountId = null
        }
        val current = _state.value
        if (current != null && current.accountId == accountId) {
            if (current.sidHex.isNotEmpty()) {
                lastNonce.keys.removeAll { it.startsWith("${current.sidHex}:") }
            }
            closeSession(emitEnd = false, nowSeconds = nowSeconds)
            _state.value = null
        }
    }

    private fun finishStartFailed(
        gen: Long,
        role: BgWaveRole,
        accountId: String,
        chatId: Long,
        peerId: Long,
        sidHex: String,
        errorKey: String,
    ): String? = synchronized(lock) {
        if (generation != gen || !starting) return@synchronized null
        starting = false
        startingAccountId = null
        fail(
            errorKey,
            keepSession = false,
            role = role,
            accountId = accountId,
            chatId = chatId,
            peerId = peerId,
            attemptSidHex = sidHex,
        )
    }

    private fun commitHostSession(
        gen: Long,
        created: BgWaveSessionPort,
        accountId: String,
        chatId: Long,
        peerId: Long,
        decoded: BgWaveDecodeResult.Ok,
        link: String,
    ): String? {
        val aborted = synchronized(lock) {
            if (generation != gen || !starting || session != null) {
                true
            } else {
                starting = false
                startingAccountId = null
                session = created
                offerExpiresAt = decoded.expiresAt
                BgWaveLog.i("host.offer.created", "sid" to BgWaveLog.sid(decoded.sidHex))
                _state.value = BgWaveSessionState(BgWaveRole.Host, accountId, chatId, peerId, decoded.sidHex, BgWavePhase.Offering)
                false
            }
        }
        if (aborted) {
            BgWaveLog.i("host.offer.aborted", "sid" to BgWaveLog.sid(decoded.sidHex))
            runCatching { created.stop(false, 0L) }
            runCatching { created.close() }
            return null
        }
        return link
    }

    private fun commitJoinSession(
        gen: Long,
        created: BgWaveSessionPort,
        sidHex: String,
        link: String,
    ): String? {
        val aborted = synchronized(lock) {
            val latestInvite = invites[sidHex]
            if (generation != gen || !starting || session != null ||
                latestInvite == null || latestInvite.status != BgWaveInviteStatus.Pending || sidHex in closed
            ) {
                if (generation == gen && starting) {
                    starting = false
                    startingAccountId = null
                    _state.value = null
                }
                true
            } else {
                starting = false
                startingAccountId = null
                session = created
                setInvite(sidHex, latestInvite.copy(status = BgWaveInviteStatus.Joined))
                _state.value = BgWaveSessionState(
                    BgWaveRole.Listener, latestInvite.accountId, latestInvite.chatId, latestInvite.senderId, sidHex, BgWavePhase.Connecting,
                )
                false
            }
        }
        if (aborted) {
            BgWaveLog.i("listener.join.aborted", "sid" to BgWaveLog.sid(sidHex))
            runCatching { created.stop(false, 0L) }
            runCatching { created.close() }
            return null
        }
        return link
    }

    // The private handlers below run with [lock] held.

    private fun onOffer(accountId: String, chatId: Long, senderId: Long, uri: String, msg: BgWaveDecodeResult.Ok, nowSeconds: Long) {
        val current = _state.value
        val active = session
        if (current != null && current.sidHex == msg.sidHex) {
            if (active != null &&
                current.accountId == accountId &&
                current.chatId == chatId &&
                current.peerId == senderId &&
                current.phase == BgWavePhase.Reconnecting
            ) {
                active.handleRemote(uri, nowSeconds)
            }
            return
        }
        rememberInvite(accountId, chatId, senderId, uri, msg)
    }

    private fun onAnswer(accountId: String, chatId: Long, senderId: Long, uri: String, msg: BgWaveDecodeResult.Ok, nowSeconds: Long) {
        val current = _state.value ?: return
        val active = session ?: return
        if (current.sidHex != msg.sidHex || current.accountId != accountId || current.chatId != chatId || current.peerId != senderId) return
        if (current.phase == BgWavePhase.Reconnecting) {
            active.handleRemote(uri, nowSeconds)
            return
        }
        if (current.role != BgWaveRole.Host || current.phase != BgWavePhase.AwaitingAnswer) return
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
                markClosed(msg.sidHex)
                if (invite.status == BgWaveInviteStatus.Pending || invite.status == BgWaveInviteStatus.Joined) {
                    setInvite(msg.sidHex, invite.copy(status = status))
                }
            }
        }
        val current = _state.value ?: return
        if (current.sidHex != msg.sidHex || current.accountId != accountId || current.chatId != chatId || current.peerId != senderId) return
        if (starting && startingAccountId == accountId) {
            generation++
            starting = false
            startingAccountId = null
        }
        markClosed(msg.sidHex)
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

    private fun markClosed(sidHex: String) {
        if (sidHex.isEmpty() || closed.contains(sidHex)) return
        while (closed.size >= MAX_CLOSED) {
            val eldest = closed.iterator().next()
            closed.remove(eldest)
            if (!invites.containsKey(eldest)) lastNonce.keys.removeAll { it.startsWith("$eldest:") }
        }
        closed.add(sidHex)
    }

    private fun acceptNonce(sidHex: String, type: Int, nonce: Long): Boolean {
        val key = "$sidHex:$type"
        val last = lastNonce[key]
        if (last != null && java.lang.Long.compareUnsigned(nonce, last) <= 0) return false
        lastNonce[key] = nonce
        return true
    }

    private fun closeSession(emitEnd: Boolean, nowSeconds: Long) {
        offerExpiresAt = 0L
        val active = session ?: return
        session = null
        BgWaveLog.i("session.close", "emitEnd" to emitEnd)
        try {
            active.stop(emitEnd, nowSeconds)
        } finally {
            active.close()
        }
    }

    private fun fail(
        errorKey: String,
        keepSession: Boolean = false,
        role: BgWaveRole? = null,
        accountId: String? = null,
        chatId: Long? = null,
        peerId: Long? = null,
        attemptSidHex: String? = null,
    ): String? {
        val previous = _state.value
        BgWaveLog.w("fail", null, "key" to errorKey, "keepSession" to keepSession)
        if (!keepSession || previous == null) {
            _state.value = BgWaveSessionState(
                role = role ?: previous?.role ?: BgWaveRole.Host,
                accountId = accountId ?: previous?.accountId.orEmpty(),
                chatId = chatId ?: previous?.chatId ?: 0L,
                peerId = peerId ?: previous?.peerId ?: 0L,
                sidHex = attemptSidHex ?: previous?.sidHex.orEmpty(),
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
        private const val ABORT_CANCEL_NONCE = 3L
        private const val MAX_POLLED = 8
        private const val MAX_CLOSED = 512

        /** Maps exact native pipeline state names onto the phase the UI shows. Unknown names change nothing. */
        internal fun phaseOf(nativeState: String, fallback: BgWavePhase): BgWavePhase =
            when (nativeState.trim().lowercase()) {
                "live", "livep2p", "liverelay" -> BgWavePhase.Live
                "reconnecting" -> BgWavePhase.Reconnecting
                "failed", "p2pfailed" -> BgWavePhase.Failed
                "ended" -> BgWavePhase.Ended
                "connecting", "connectingp2p", "connectingrelay" -> BgWavePhase.Connecting
                else -> fallback
            }
    }
}
