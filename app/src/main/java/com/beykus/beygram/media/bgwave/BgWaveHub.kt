package com.beykus.beygram.media.bgwave

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Hands a wave link to Telegram as a hidden service link in the given chat. */
typealias BgWaveSender = suspend (chatId: Long, link: String) -> Result<Unit>

/**
 * The single place where the wave state machine meets Telegram and the clock. UI calls the suspend functions; TDLib
 * pushes received messages through [controller]. One wave runs at a time for the whole app.
 */
object BgWaveHub {
    const val DEFAULT_BITRATE_BPS = 0
    private const val CONTROL_CAPS = 3L
    private const val TICK_MILLIS = 1_000L

    val controller = BgWaveController(
        createSession = { host -> BgWaveSession.create(host, DEFAULT_BITRATE_BPS) },
        decode = { uri, now -> BgWaveNative.decodeUri(uri, now) },
        encodeControl = { type, sid, now, nonce ->
            BgWaveNative.encodeControl(type, sid, now, now + BgWaveController.OFFER_TTL_SECONDS, CONTROL_CAPS, nonce, asHttps = true)
        },
    )

    private val senders = ConcurrentHashMap<String, BgWaveSender>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var ticker: Job? = null

    fun nowSeconds(): Long = System.currentTimeMillis() / 1000L

    fun registerSender(accountId: String, sender: BgWaveSender) {
        senders[accountId] = sender
    }

    /** A signed-out or removed account: its sender goes, and a wave that belonged to it ends. */
    fun forgetAccount(accountId: String) {
        senders.remove(accountId)
        if (controller.state.value?.accountId == accountId) controller.reset(nowSeconds())
    }

    private suspend fun send(accountId: String, chatId: Long, link: String): Result<Unit> {
        val result = senders[accountId]?.invoke(chatId, link) ?: Result.failure(IllegalStateException("no sender"))
        if (result.isFailure) {
            BgWaveLog.w("send.failed", result.exceptionOrNull(), "reason" to "telegram-send")
        } else {
            BgWaveLog.i("send.ok")
        }
        return result
    }

    /** Host: starts a wave and sends the invitation to [chatId]. The result is read from [BgWaveController.state]. */
    suspend fun share(accountId: String, chatId: Long, peerId: Long) {
        val now = nowSeconds()
        val link = controller.startHosting(accountId, chatId, peerId, now) ?: return
        val sid = controller.state.value?.sidHex.orEmpty()
        val sent = send(accountId, chatId, link).isSuccess
        controller.offerSent(sid, sent, nowSeconds())
        if (sent) ensureTicker()
    }

    /** Listener: joins the wave of [sidHex] and sends the answer back. */
    suspend fun join(sidHex: String) {
        val now = nowSeconds()
        val link = controller.join(sidHex, now) ?: return
        val state = controller.state.value ?: return
        BgWaveLog.i("listener.join", "sid" to BgWaveLog.sid(sidHex))
        val sent = send(state.accountId, state.chatId, link).isSuccess
        if (sent) ensureTicker() else controller.answerSendFailed(sidHex, nowSeconds())
    }

    suspend fun decline(sidHex: String) {
        val invite = controller.inviteStates.value[sidHex] ?: return
        val link = controller.decline(sidHex, nowSeconds()) ?: return
        send(invite.accountId, invite.chatId, link)
    }

    /** Either side: stops the running wave and tells the peer. */
    suspend fun stop() {
        val state = controller.state.value ?: return
        val link = controller.stop(nowSeconds()) ?: return
        send(state.accountId, state.chatId, link)
    }

    fun retry(): Boolean = controller.retry(nowSeconds())

    @Synchronized
    private fun ensureTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (true) {
                delay(TICK_MILLIS)
                val state = controller.state.value ?: break
                val links = controller.poll(nowSeconds())
                for (link in links) send(state.accountId, state.chatId, link)
                if (controller.state.value?.phase.let { it == BgWavePhase.Ended || it == BgWavePhase.Failed }) break
            }
        }
    }
}
