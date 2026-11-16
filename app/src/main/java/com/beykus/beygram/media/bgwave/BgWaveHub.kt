package com.beykus.beygram.media.bgwave

import android.media.AudioManager
import android.net.ConnectivityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    @Volatile private var audioManager: AudioManager? = null
    @Volatile private var appContext: android.content.Context? = null
    @Volatile private var listenerAudio: BgWaveListenerAudio? = null
    private var bgMixTapAttached = false
    private var hostTapMissingLogged = false
    @Volatile private var focusLostSid: String? = null

    /**
     * Host: attaches (handle, true) or detaches (0, false) the wave session as the tap of the BG Mix player that is
     * playing. Set by the playback layer; null until then, so the host is silent until it is wired.
     */
    @Volatile var hostTap: ((sessionHandle: Long, attach: Boolean) -> Boolean)? = null

    /** Called once from the application, so the listener can take audio focus and play. */
    fun attachAudioManager(manager: AudioManager, context: android.content.Context? = null) {
        audioManager = manager
        appContext = context?.applicationContext
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var networkWatcher: BgWaveNetworkWatcher? = null

    /** Called once from the application: a network switch during a failed wave then triggers one automatic retry. */
    @Synchronized
    fun attachConnectivityManager(manager: ConnectivityManager) {
        if (networkWatcher != null) return
        networkWatcher = BgWaveNetworkWatcher(manager, scope, controller.state, { retry() }).also { it.start() }
    }
    @Volatile private var ticker: Job? = null

    fun nowSeconds(): Long = System.currentTimeMillis() / 1000L

    fun registerSender(accountId: String, sender: BgWaveSender) {
        senders[accountId] = sender
    }

    /** A signed-out or removed account: its sender goes, and a wave that belonged to it ends. */
    fun forgetAccount(accountId: String) {
        senders.remove(accountId)
        controller.resetAccount(accountId, nowSeconds())
        syncAudio()
    }

    private suspend fun send(accountId: String, chatId: Long, link: String): Result<Unit> {
        val result = try {
            senders[accountId]?.invoke(chatId, link) ?: Result.failure(IllegalStateException("no sender"))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        if (result.isFailure) {
            BgWaveLog.w("send.failed", result.exceptionOrNull(), "reason" to "telegram-send")
        } else {
            BgWaveLog.i("send.ok")
        }
        return result
    }

    /** Host: return true only after this attempt has been handed to Telegram and is still current. */
    suspend fun share(accountId: String, chatId: Long, peerId: Long): Boolean =
        withContext(Dispatchers.IO + NonCancellable) {
            val now = nowSeconds()
            val link = controller.startHosting(accountId, chatId, peerId, now) ?: return@withContext false
            val sid = (BgWaveNative.decodeUri(link, now) as? BgWaveDecodeResult.Ok)?.sidHex
                ?: return@withContext false
            val current = controller.state.value
            if (current == null || current.sidHex != sid || current.accountId != accountId ||
                current.chatId != chatId || current.phase != BgWavePhase.Offering) return@withContext false
            val sent = send(accountId, chatId, link).isSuccess
            controller.offerSent(sid, sent, nowSeconds())
            val latest = controller.state.value
            if (sent && latest != null && latest.sidHex == sid && latest.accountId == accountId &&
                latest.phase != BgWavePhase.Ended && latest.phase != BgWavePhase.Failed) {
                ensureTicker()
                true
            } else {
                syncAudio()
                if (sent) controller.cancelLink(sid, nowSeconds())?.let { send(accountId, chatId, it) }
                false
            }
        }

    /** Listener: joins the wave of [sidHex] and sends the answer back. */
    suspend fun join(sidHex: String): Unit = withContext(Dispatchers.IO + NonCancellable) {
        val invite = controller.inviteStates.value[sidHex] ?: return@withContext
        val link = controller.join(sidHex, nowSeconds()) ?: return@withContext
        val state = controller.state.value ?: return@withContext
        if (state.sidHex != sidHex || state.accountId != invite.accountId ||
            state.chatId != invite.chatId || state.phase != BgWavePhase.Connecting) return@withContext
        BgWaveLog.i("listener.join", "sid" to BgWaveLog.sid(sidHex))
        val sent = send(invite.accountId, invite.chatId, link).isSuccess
        if (sent) ensureTicker() else {
            controller.answerSendFailed(sidHex, nowSeconds())
            syncAudio()
        }
    }

    suspend fun decline(sidHex: String) {
        val invite = controller.inviteStates.value[sidHex] ?: return
        val link = controller.decline(sidHex, nowSeconds()) ?: return
        send(invite.accountId, invite.chatId, link)
    }

    /** Either side: stops the running wave and tells the peer. */
    suspend fun stop() {
        val state = controller.state.value ?: return
        val link = controller.stopIfCurrent(state, nowSeconds())
        syncAudio()
        if (link != null) send(state.accountId, state.chatId, link)
    }

    fun retry(): Boolean = controller.retry(nowSeconds()).also { if (it) ensureTicker() }

    /**
     * Keeps local audio in step with the wave phase. Host: the BG Mix tap is attached while Live and detached otherwise,
     * re-attached each tick so a new mix player picks it up. Listener: playback starts when Live and stops when not.
     */
    @Synchronized
    private fun syncAudio() {
        val state = controller.state.value
        if (focusLostSid != state?.sidHex) focusLostSid = null
        if (state == null || state.phase != BgWavePhase.Live || state.role != BgWaveRole.Host) {
            BgWaveHostTap.close()
            hostTap?.invoke(0L, false)
            bgMixTapAttached = false
        }
        if (state == null || state.phase != BgWavePhase.Live || state.role != BgWaveRole.Listener) {
            listenerAudio?.stop()
            listenerAudio = null
        }
        if (state == null) return
        val live = state.phase == BgWavePhase.Live
        if (state.role == BgWaveRole.Host) {
            if (live && !BgWaveHostTap.isOpen) BgWaveHostTap.open(controller.openHostTap())
            if (!live) BgWaveHostTap.close()
            val tap = hostTap
            if (tap == null) {
                if (live) controller.setHostSourceUnsupported(state.sidHex, true)
                if (live && !hostTapMissingLogged) {
                    BgWaveLog.w("host.bgmix.tap", null, "reason" to "no-provider")
                    hostTapMissingLogged = true
                }
                return
            }
            val handle = controller.activeSession()?.sessionHandle() ?: 0L
            val attached = if (live && handle != 0L) tap(handle, true) else tap(0L, false)
            if (live && attached != bgMixTapAttached) {
                BgWaveLog.i("host.bgmix.tap", "attached" to attached)
            }
            bgMixTapAttached = attached && live
            // The tap is refused for a source that must not be broadcast (a private or non-file track), and BG Mix owns the
            // audio then, so the host is told instead of the listener silently getting nothing.
            if (live) {
                val unsupported = !BgWaveHostTap.captureEligible || (!attached && BgWaveHostTap.exoSuppressedForBgMix)
                controller.setHostSourceUnsupported(state.sidHex, unsupported)
            }
            return
        }
        if (live && listenerAudio == null && focusLostSid != state.sidHex) {
            val source = controller.activeSession()
            val manager = audioManager
            if (source != null && manager != null) {
                val output = BgWaveListenerAudio(source, manager, appContext) {
                    focusLostSid = state.sidHex
                    scope.launch {
                        val link = controller.stopIfCurrent(state, nowSeconds())
                        syncAudio()
                        if (link != null) send(state.accountId, state.chatId, link)
                    }
                }
                if (output.start()) listenerAudio = output
                else focusLostSid = state.sidHex
            }
        } else if (!live) {
            listenerAudio?.stop()
            listenerAudio = null
        }
    }

    /** Live only: one stats line per tick under the BgWave tag. Never throws, so the ticker keeps running. */
    private fun logLiveStats() {
        try {
            val state = controller.state.value ?: return
            if (state.phase != BgWavePhase.Live) return
            val raw = controller.activeSession()?.stats() ?: return
            val stats = BgWaveStats.parse(raw) ?: return
            BgWaveLog.i(
                "session.stats",
                "role" to state.role,
                "sent" to stats.sent,
                "received" to stats.received,
                "lost" to stats.lost,
                "plc" to stats.plc,
                "jitterMs" to stats.jitterMs,
                "bitrate" to stats.bitrateBps,
                "depthMs" to stats.depthMs,
            )
        } catch (_: Exception) {
            // Stats are diagnostics only; a failed read must not stop the wave's ticker.
        }
    }

    @Synchronized
    private fun ensureTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            try {
            while (true) {
                delay(TICK_MILLIS)
                val state = controller.state.value
                if (state == null) {
                    syncAudio()
                    break
                }
                val links = controller.poll(nowSeconds())
                for (link in links) send(state.accountId, state.chatId, link)
                syncAudio()
                logLiveStats()
                if (controller.state.value?.phase.let { it == null || it == BgWavePhase.Ended || it == BgWavePhase.Failed }) break
            }
            } finally {
                synchronized(this@BgWaveHub) {
                    ticker = null
                    val phase = controller.state.value?.phase
                    if (phase != null && phase != BgWavePhase.Ended && phase != BgWavePhase.Failed) ensureTicker()
                }
            }
        }
    }
}
