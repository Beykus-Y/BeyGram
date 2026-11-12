package com.beykus.beygram.media.bgmotion

import android.content.Context
import android.util.Log
import com.beykus.beygram.media.bgbeat.BgAnalysisLease
import com.beykus.beygram.media.bgbeat.BgAnalysisRepository
import com.beykus.beygram.media.bgbeat.BgBeatAnalysis
import com.beykus.beygram.media.bgbeat.BgBeatAnalysisIdentity
import com.beykus.beygram.media.bgbeat.BgBeatIneligibleReason
import com.beykus.beygram.media.bgbeat.BgBeatSettingsStore
import com.beykus.beygram.media.bgbeat.BgBeatStatus
import com.beykus.beygram.media.bgbeat.BgBeatNative
import com.beykus.beygram.media.bgbeat.BgMediaEligibility
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class MascotSessionInfo(
    val cacheHit: Boolean = false,
    val bpm: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val analyzed: Boolean = false,
    val beatCount: Int = 0,
    val sectionCount: Int = 0,
    val analysisHandles: Int = 0,
    val sessions: Int = 0
)

sealed interface BgMascotUiState {
    data object Disabled : BgMascotUiState
    data object Unavailable : BgMascotUiState
    data object Idle : BgMascotUiState
    data class Ineligible(val reason: BgBeatIneligibleReason) : BgMascotUiState
    data class Preparing(val itemKey: String) : BgMascotUiState
    data class Ready(
        val itemKey: String,
        val handle: Long,
        val info: MascotSessionInfo
    ) : BgMascotUiState
    data class Error(val message: String) : BgMascotUiState
}

/**
 * Owns at most one native mascot session for the current track.
 *
 * All state transitions happen on Main. Analysis and session creation run on
 * Dispatchers.Default. The native session is thread-safe at the handle level, but
 * the UI samples it from Main only; the future GL presenter will sample from its
 * GL thread with the separate GL release sequence.
 */
class BgMascotManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "BgMascotManager"

        @Volatile
        private var instance: BgMascotManager? = null

        fun getInstance(context: Context): BgMascotManager =
            instance ?: synchronized(this) {
                instance ?: BgMascotManager(context.applicationContext).also { instance = it }
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settings = BgMascotSettingsStore.getInstance(context)
    private val visible = MutableStateFlow(false)

    private val _uiState = MutableStateFlow<BgMascotUiState>(BgMascotUiState.Disabled)
    val uiState: StateFlow<BgMascotUiState> = _uiState.asStateFlow()

    private val clock = MascotClock()
    private val nativeAvailable: Boolean by lazy { BgMascotNative.isAvailable }

    private var sessionHandle: Long = 0L
    private var sessionIdentity: BgBeatAnalysisIdentity? = null
    private var sessionLease: BgAnalysisLease? = null
    private var prepareJob: Job? = null
    private var prepareToken: Long = 0L
    private var preparingIdentity: BgBeatAnalysisIdentity? = null
    private var failedIdentity: BgBeatAnalysisIdentity? = null
    private var pendingLeaseRef: AtomicReference<BgAnalysisLease?>? = null

    init {
        settings.load(context)
        scope.launch {
            combine(
                PlaybackEngine.state,
                settings.enabled,
                visible
            ) { state, enabled, isVisible ->
                Triple(state, enabled, isVisible)
            }.collect { (state, enabled, isVisible) ->
                onPlaybackState(state, enabled, isVisible)
            }
        }
        scope.launch {
            combine(settings.intensity, settings.audioOffsetMs) { intensity, offset ->
                intensity to offset
            }.collect { (intensity, offset) ->
                if (sessionHandle != 0L) {
                    BgMascotNative.nativeSetIntensity(sessionHandle, intensity)
                    BgMascotNative.nativeSetPlaybackOffset(sessionHandle, offset / 1000.0)
                }
            }
        }
    }

    /** Called by the player stage while it is actually composed and visible. */
    fun setVisible(value: Boolean) {
        visible.value = value
    }

    /** Account switch: never keep an analysis or session from the previous account. */
    fun onAccountChanged(newAccountId: String) {
        if (newAccountId.isBlank()) return
        cancelPrepare()
        destroySession()
        clock.reset()
        failedIdentity = null
        _uiState.value = if (settings.enabled.value) BgMascotUiState.Idle else BgMascotUiState.Disabled
    }

    fun mediaTimeSeconds(nowNanos: Long): Double = clock.timeSeconds(nowNanos)

    fun sampleInto(out: FloatArray, mediaTime: Double): Int {
        val handle = sessionHandle
        if (handle == 0L) return 0
        return try {
            val written = BgMascotNative.nativeSample2D(handle, mediaTime, out)
            if (written != MascotFrameLayout.FLOAT_COUNT) {
                Log.e(TAG, "Frame contract mismatch: native wrote $written, Kotlin expects ${MascotFrameLayout.FLOAT_COUNT}")
                0
            } else {
                written
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Mascot sampling failed", t)
            0
        }
    }

    fun statusJson(mediaTime: Double): String? {
        val handle = sessionHandle
        if (handle == 0L) return null
        return try {
            BgMascotNative.nativeStatusJson(handle, mediaTime)
        } catch (_: Throwable) {
            null
        }
    }

    private fun onPlaybackState(state: PlaybackState, enabled: Boolean, isVisible: Boolean) {
        clock.onPlaybackState(
            itemKey = state.item?.key,
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            playing = state.playing,
            speed = state.speed,
            nowNanos = System.nanoTime()
        )

        if (!enabled) {
            destroySession()
            failedIdentity = null
            if (_uiState.value !is BgMascotUiState.Disabled) {
                _uiState.value = BgMascotUiState.Disabled
            }
            return
        }
        if (!nativeAvailable) {
            destroySession()
            if (_uiState.value !is BgMascotUiState.Unavailable) {
                _uiState.value = BgMascotUiState.Unavailable
            }
            return
        }
        if (!isVisible) {
            // Keep the prepared session for a quick reopen, but do not keep an
            // analysis of a track the player has already left.
            if (sessionHandle != 0L && state.item?.key != sessionIdentity?.itemKey) {
                destroySession()
                _uiState.value = BgMascotUiState.Idle
            }
            return
        }

        val item = state.item
        if (item == null) {
            destroySession()
            if (_uiState.value !is BgMascotUiState.Idle) {
                _uiState.value = BgMascotUiState.Idle
            }
            return
        }

        val ineligible = BgMediaEligibility.reasonFor(item)
        if (ineligible != null) {
            destroySession()
            val current = _uiState.value
            if (current !is BgMascotUiState.Ineligible || current.reason != ineligible) {
                _uiState.value = BgMascotUiState.Ineligible(ineligible)
            }
            return
        }

        val identity = identityFor(item) ?: run {
            destroySession()
            if (_uiState.value !is BgMascotUiState.Idle) {
                _uiState.value = BgMascotUiState.Idle
            }
            return
        }

        if (sessionHandle != 0L && sessionIdentity == identity) {
            val ready = _uiState.value
            if (ready is BgMascotUiState.Ready && ready.itemKey == item.key) return
            _uiState.value = BgMascotUiState.Ready(
                itemKey = item.key,
                handle = sessionHandle,
                info = sessionInfo(sessionHandle)
            )
            return
        }

        if (failedIdentity != null && failedIdentity?.itemKey != item.key) {
            failedIdentity = null
        }
        if (preparingIdentity == identity) return
        if (failedIdentity == identity) {
            val current = _uiState.value
            if (current !is BgMascotUiState.Error) {
                _uiState.value = BgMascotUiState.Error("Анализ трека не удался")
            }
            return
        }
        startPrepare(item, identity)
    }

    private fun identityFor(item: PlaybackItem): BgBeatAnalysisIdentity? {
        val file = File(item.path)
        if (!file.exists() || !file.isFile || file.length() <= 0L) return null
        return BgBeatAnalysisIdentity(
            accountId = item.accountId.ifBlank { PlaybackEngine.currentAccountId ?: "legacy" },
            itemKey = item.key,
            filePath = file.absolutePath,
            fileLength = file.length(),
            fileLastModified = file.lastModified()
        )
    }

    private fun startPrepare(item: PlaybackItem, identity: BgBeatAnalysisIdentity) {
        cancelPrepare()
        // Keep the UI honest while the new track is being analysed: a stale dance
        // to the previous track is worse than a short idle state.
        destroySession()
        preparingIdentity = identity
        failedIdentity = null
        val requestSeq = identity
        _uiState.value = BgMascotUiState.Preparing(item.key)

        val token = BgBeatNative.nativeCreateCancelToken()
        prepareToken = token
        prepareJob = scope.launch {
            var lease: BgAnalysisLease? = null
            var keepLease = false
            val leaseRef = AtomicReference<BgAnalysisLease?>(null)
            val handleRef = AtomicLong(0L)
            pendingLeaseRef = leaseRef
            try {
                lease = BgAnalysisRepository.acquire(
                    context = context,
                    identity = identity,
                    cancelTokenHandle = token,
                    progressListener = null,
                    forceRefresh = false,
                    out = leaseRef
                )
                if (lease == null) {
                    if (preparingIdentity == requestSeq) {
                        failedIdentity = requestSeq
                        _uiState.value = BgMascotUiState.Error("Нет данных анализа для маскота")
                    }
                    return@launch
                }
                if (preparingIdentity != requestSeq) {
                    return@launch
                }

                // A shared snapshot may legitimately be cancelled or failed
                // because BG Beat was cancelled during a track switch. Do not
                // latch CANCELLED as a permanent mascot failure.
                val analysis = try {
                    BgBeatAnalysis.fromJson(lease.json)
                } catch (t: Throwable) {
                    failedIdentity = requestSeq
                    _uiState.value = BgMascotUiState.Error("Некорректный ответ анализа: ${t.localizedMessage}")
                    return@launch
                }
                if (analysis.status == BgBeatStatus.CANCELLED) {
                    // Do not latch a cancellation as a permanent failure. If the
                    // player is running, the next 250 ms tick retries; while paused
                    // the cover simply stays visible instead of a stuck Preparing.
                    if (preparingIdentity == requestSeq) {
                        _uiState.value = BgMascotUiState.Idle
                    }
                    return@launch
                }
                if (analysis.status != BgBeatStatus.OK) {
                    failedIdentity = requestSeq
                    _uiState.value = BgMascotUiState.Error(
                        analysis.message.ifBlank { "Анализ трека не удался" }
                    )
                    return@launch
                }

                val handle = withContext(Dispatchers.Default + NonCancellable) {
                    BgMascotNative.nativeCreateSession(
                        analysisHandle = lease.handle,
                        mode = BgMascotNative.MODE_2D,
                        intensity = settings.intensity.value
                    ).also { handleRef.set(it) }
                }
                if (preparingIdentity != requestSeq || !settings.enabled.value) {
                    if (handle != 0L) BgMascotNative.nativeDestroySession(handle)
                    return@launch
                }
                if (handle == 0L) {
                    failedIdentity = requestSeq
                    _uiState.value = BgMascotUiState.Error("Не удалось создать сессию BG Motion")
                    return@launch
                }

                destroySession()
                sessionHandle = handle
                sessionIdentity = identity
                // Keep the analysis lease alive with the session. BG Beat can
                // acquire the same entry without decoding the track again.
                sessionLease = lease
                keepLease = true
                if (pendingLeaseRef === leaseRef) pendingLeaseRef = null
                BgMascotNative.nativeSetPlaybackOffset(handle, settings.audioOffsetMs.value / 1000.0)
                BgMascotNative.nativeSetIntensity(handle, settings.intensity.value)
                withContext(Dispatchers.IO) {
                    BgBeatSettingsStore.enforceCacheQuota(context)
                }
                _uiState.value = BgMascotUiState.Ready(
                    itemKey = item.key,
                    handle = handle,
                    info = sessionInfo(handle)
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (preparingIdentity == requestSeq) {
                    Log.e(TAG, "Mascot preparation failed", t)
                    failedIdentity = requestSeq
                    _uiState.value = BgMascotUiState.Error("Ошибка подготовки маскота: ${t.localizedMessage}")
                }
            } finally {
                if (!keepLease) {
                    lease?.release()
                    leaseRef.get()?.let { pending ->
                        if (sessionLease !== pending) pending.release()
                    }
                }
                if (pendingLeaseRef === leaseRef) pendingLeaseRef = null
                // Covers cancellation after nativeCreateSession returned but
                // before the handle could be published to the manager.
                val pendingHandle = handleRef.get()
                if (pendingHandle != 0L && pendingHandle != sessionHandle) {
                    BgMascotNative.nativeDestroySession(pendingHandle)
                }
                if (preparingIdentity == requestSeq) preparingIdentity = null
                BgBeatNative.nativeDestroyCancelToken(token)
                if (prepareToken == token) prepareToken = 0L
            }
        }
    }

    private fun cancelPrepare() {
        if (prepareToken != 0L) {
            BgBeatNative.nativeCancel(prepareToken)
        }
        prepareJob?.cancel()
        prepareJob = null
        pendingLeaseRef?.getAndSet(null)?.release()
        pendingLeaseRef = null
        preparingIdentity = null
    }

    private fun destroySession() {
        val handle = sessionHandle
        sessionHandle = 0L
        sessionIdentity = null
        sessionLease?.release()
        sessionLease = null
        if (handle != 0L) {
            BgMascotNative.nativeDestroySession(handle)
        }
    }

    private fun sessionInfo(handle: Long): MascotSessionInfo {
        val json = try {
            BgMascotNative.nativeStatusJson(handle, 0.0)
        } catch (_: Throwable) {
            null
        } ?: return MascotSessionInfo()
        return try {
            val root = JSONObject(json)
            MascotSessionInfo(
                cacheHit = root.optBoolean("cacheHit", false),
                bpm = root.optDouble("bpm", 0.0),
                durationSeconds = root.optDouble("duration", 0.0),
                analyzed = root.optBoolean("analyzed", false),
                beatCount = root.optInt("beatCount", 0),
                sectionCount = root.optInt("sectionCount", 0),
                analysisHandles = root.optInt("analysisHandles", 0),
                sessions = root.optInt("sessions", 0)
            )
        } catch (_: Throwable) {
            MascotSessionInfo()
        }
    }
}
