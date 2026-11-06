package com.beykus.beygram.media.bgbeat

import android.content.Context
import android.util.Log
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Why BG Beat cannot analyse the current item. The screen turns each reason into its own string resource. */
enum class BgBeatIneligibleReason { NotMusic, PrivateMedia, StillDownloading, NotDownloaded, UnsupportedFormat }

sealed interface BgBeatUiState {
    val itemKey: String? get() = null
    val accountId: String? get() = null

    data object Disabled : BgBeatUiState
    data class Idle(override val itemKey: String? = null, override val accountId: String? = null) : BgBeatUiState
    data class Ineligible(val reason: BgBeatIneligibleReason, override val itemKey: String? = null, override val accountId: String? = null) : BgBeatUiState
    data class Analyzing(val fraction: Float, val stage: String, override val itemKey: String, override val accountId: String) : BgBeatUiState
    data class Ready(val analysis: BgBeatAnalysis, override val itemKey: String, override val accountId: String) : BgBeatUiState
    data class Error(val message: String, override val itemKey: String? = null, override val accountId: String? = null) : BgBeatUiState
}

/**
 * Unique identity of an audio analysis task to prevent duplicate parallel executions
 * and properly re-trigger analysis once a pending download completes.
 */
data class BgBeatAnalysisIdentity(
    val accountId: String,
    val itemKey: String,
    val filePath: String,
    val fileLength: Long,
    val fileLastModified: Long,
    val minBpm: Double = 55.0,
    val maxBpm: Double = 200.0
)

/** What [BgBeatManager] does when playback reports a file identity again. */
internal enum class BgBeatRerun { Start, Wait, ShowFailed }

/**
 * Decides what a playback update for [identity] means. [failed] is the identity of the last analysis that ended without
 * a result: its outcome is shown again and it is not run again, until the file changes or the analysis is requested anew.
 */
internal fun bgBeatRerun(
    identity: BgBeatAnalysisIdentity,
    active: BgBeatAnalysisIdentity?,
    completed: BgBeatAnalysisIdentity?,
    readyNow: Boolean,
    failed: BgBeatAnalysisIdentity?
): BgBeatRerun = when {
    active == identity -> BgBeatRerun.Wait
    completed == identity && readyNow -> BgBeatRerun.Wait
    failed == identity -> BgBeatRerun.ShowFailed
    else -> BgBeatRerun.Start
}

/**
 * Coordinates BG Beat music analysis with PlaybackEngine and account isolation.
 * - Single source of observation via Flow combine to prevent duplicate parallel triggers.
 * - Deduplicates analysis tasks by BgBeatAnalysisIdentity.
 * - Automatically triggers analysis when an in-progress file download completes, without initiating downloads itself.
 * - Strictly background execution on Dispatchers.Default.
 * - Cooperative cancellation during both PCM decoding and beat tracking.
 * - Per-account cache segregation (no cross-account data leakage).
 * - Monotonic sequence guard against race conditions on track switch, account switch or disable.
 * - Preserves Media3 / ExoPlayer audio playback architecture completely untouched.
 */
class BgBeatManager(
    private val context: Context,
    private val settingsStore: BgBeatSettingsStore
) {
    companion object {
        private const val TAG = "BgBeatManager"

        @Volatile
        private var instance: BgBeatManager? = null

        fun getInstance(context: Context): BgBeatManager {
            return instance ?: synchronized(this) {
                instance ?: BgBeatManager(
                    context.applicationContext,
                    BgBeatSettingsStore.getInstance(context.applicationContext)
                ).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow<BgBeatUiState>(BgBeatUiState.Idle())
    val uiState: StateFlow<BgBeatUiState> = _uiState.asStateFlow()

    private val sequenceId = AtomicLong(0L)
    private var currentJob: Job? = null
    private var currentCancelToken: Long = 0L

    @Volatile
    private var activeIdentity: BgBeatAnalysisIdentity? = null

    @Volatile
    private var completedIdentity: BgBeatAnalysisIdentity? = null

    /** The last analysis that ended without a result (UNSUPPORTED, ERROR or an exception), with the state it showed. */
    @Volatile
    private var failedAnalysis: Pair<BgBeatAnalysisIdentity, BgBeatUiState>? = null

    init {
        // Observe both playback and settings in a single unified flow to prevent duplicate runs
        scope.launch {
            combine(
                PlaybackEngine.state,
                settingsStore.enabled
            ) { state, isEnabled ->
                state to isEnabled
            }.collectLatest { (state, isEnabled) ->
                handleStateOrSettingsChange(state, isEnabled)
            }
        }
    }

    private fun handleStateOrSettingsChange(state: PlaybackState, isEnabled: Boolean) {
        if (!isEnabled) {
            cancelCurrentAnalysis()
            sequenceId.incrementAndGet()
            activeIdentity = null
            completedIdentity = null
            failedAnalysis = null
            _uiState.value = BgBeatUiState.Disabled
            return
        }

        val item = state.item
        if (item == null) {
            cancelCurrentAnalysis()
            activeIdentity = null
            completedIdentity = null
            _uiState.value = BgBeatUiState.Idle(null, PlaybackEngine.currentAccountId)
            return
        }

        val accountId = item.accountId.ifBlank { PlaybackEngine.currentAccountId ?: "legacy" }

        // Filter 1: Only regular music files
        if (item.kind != MediaKind.Audio) {
            cancelCurrentAnalysis()
            activeIdentity = null
            completedIdentity = null
            _uiState.value = BgBeatUiState.Ineligible(BgBeatIneligibleReason.NotMusic, item.key, accountId)
            return
        }

        // Filter 2: Exclude secret media and system-private items
        if (item.secret || item.systemPrivate) {
            cancelCurrentAnalysis()
            activeIdentity = null
            completedIdentity = null
            _uiState.value = BgBeatUiState.Ineligible(BgBeatIneligibleReason.PrivateMedia, item.key, accountId)
            return
        }

        // Filter 3: Must not be actively streaming/growing
        if (item.growing != null) {
            cancelCurrentAnalysis()
            activeIdentity = null
            completedIdentity = null
            _uiState.value = BgBeatUiState.Ineligible(BgBeatIneligibleReason.StillDownloading, item.key, accountId)
            return
        }

        // Filter 4: Must be a completed, valid file on local storage
        val file = File(item.path)
        if (!file.exists() || !file.isFile || file.length() <= 0L || file.name.endsWith(".temp")) {
            cancelCurrentAnalysis()
            activeIdentity = null
            completedIdentity = null
            _uiState.value = BgBeatUiState.Ineligible(BgBeatIneligibleReason.NotDownloaded, item.key, accountId)
            return
        }

        if (!BgBeatNative.isLoaded) {
            cancelCurrentAnalysis()
            activeIdentity = null
            completedIdentity = null
            _uiState.value = BgBeatUiState.Error("Нативная библиотека BG Beat не загружена", item.key, accountId)
            return
        }

        val identity = BgBeatAnalysisIdentity(
            accountId = accountId,
            itemKey = item.key,
            filePath = file.absolutePath,
            fileLength = file.length(),
            fileLastModified = file.lastModified()
        )

        // Deduplicate by file identity. Playback reports every 250 ms, so each case must stop here: an analysis in flight,
        // a finished one that is still shown, and an unsuccessful one, whose outcome is shown again instead of re-run.
        val failed = failedAnalysis
        when (
            bgBeatRerun(
                identity = identity,
                active = activeIdentity,
                completed = completedIdentity,
                readyNow = _uiState.value is BgBeatUiState.Ready,
                failed = failed?.first
            )
        ) {
            BgBeatRerun.Wait -> return
            BgBeatRerun.ShowFailed -> {
                // Another track may still be analysed: its job is cancelled and its callbacks are invalidated first, so
                // they cannot overwrite the state of this track.
                cancelCurrentAnalysis()
                sequenceId.incrementAndGet()
                if (failed != null && _uiState.value != failed.second) _uiState.value = failed.second
                return
            }
            BgBeatRerun.Start -> Unit
        }

        // File is fully ready or download finished: start analysis automatically
        startAnalysis(item, file, accountId, identity, force = false)
    }

    fun onAccountChanged(newAccountId: String) {
        if (newAccountId.isBlank()) return
        cancelCurrentAnalysis()
        sequenceId.incrementAndGet()
        activeIdentity = null
        completedIdentity = null
        val currentItem = PlaybackEngine.state.value.item
        handleStateOrSettingsChange(PlaybackEngine.state.value, settingsStore.enabled.value)
    }

    fun cancelCurrentAnalysis() {
        if (currentCancelToken != 0L) {
            BgBeatNative.nativeCancel(currentCancelToken)
        }
        currentJob?.cancel()
        currentJob = null
        activeIdentity = null
    }

    fun reanalyze() {
        val currentItem = PlaybackEngine.state.value.item ?: return
        val currentAccount = currentItem.accountId.ifBlank { PlaybackEngine.currentAccountId ?: "legacy" }
        val file = File(currentItem.path)
        if (!file.exists() || !file.isFile || file.length() <= 0L) return

        val identity = BgBeatAnalysisIdentity(
            accountId = currentAccount,
            itemKey = currentItem.key,
            filePath = file.absolutePath,
            fileLength = file.length(),
            fileLastModified = file.lastModified()
        )

        cancelCurrentAnalysis()
        completedIdentity = null
        failedAnalysis = null
        startAnalysis(currentItem, file, currentAccount, identity, force = true)
    }

    private fun startAnalysis(
        item: PlaybackItem,
        file: File,
        accountId: String,
        identity: BgBeatAnalysisIdentity,
        force: Boolean
    ) {
        cancelCurrentAnalysis()
        val reqSeq = sequenceId.incrementAndGet()
        val itemKey = item.key
        activeIdentity = identity

        currentJob = scope.launch {
            _uiState.value = BgBeatUiState.Analyzing(0f, "Запуск анализа", itemKey, accountId)

            val cancelToken = BgBeatNative.nativeCreateCancelToken()
            currentCancelToken = cancelToken

            val cacheDir = BgBeatSettingsStore.getAccountCacheDir(context, accountId).absolutePath

            try {
                val result = withContext(Dispatchers.Default) {
                    if (sequenceId.get() != reqSeq || activeIdentity != identity) {
                        return@withContext null
                    }

                    val effectiveCacheDir = if (force) "" else cacheDir
                    val jsonStr = BgBeatNative.nativeAnalyzeFile(
                        filePath = file.absolutePath,
                        cacheDir = effectiveCacheDir,
                        minBpm = identity.minBpm,
                        maxBpm = identity.maxBpm,
                        cancelTokenHandle = cancelToken,
                        progressListener = { fraction, stage ->
                            if (sequenceId.get() == reqSeq && activeIdentity == identity) {
                                scope.launch(Dispatchers.Main) {
                                    if (sequenceId.get() == reqSeq && activeIdentity == identity) {
                                        _uiState.value = BgBeatUiState.Analyzing(fraction, stage, itemKey, accountId)
                                    }
                                }
                            }
                        }
                    )

                    if (sequenceId.get() != reqSeq || activeIdentity != identity) {
                        return@withContext null
                    }

                    if (jsonStr != null) {
                        BgBeatAnalysis.fromJson(jsonStr)
                    } else {
                        BgBeatAnalysis(status = BgBeatStatus.ERROR, message = "Пустой ответ от C++ движка")
                    }
                }

                if (sequenceId.get() != reqSeq || activeIdentity != identity || result == null) {
                    return@launch
                }

                when (result.status) {
                    BgBeatStatus.OK -> {
                        completedIdentity = identity
                        _uiState.value = BgBeatUiState.Ready(result, itemKey, accountId)
                        withContext(Dispatchers.IO) {
                            BgBeatSettingsStore.enforceCacheQuota(context)
                        }
                    }
                    BgBeatStatus.CANCELLED -> {
                        _uiState.value = BgBeatUiState.Idle(itemKey, accountId)
                    }
                    BgBeatStatus.UNSUPPORTED -> {
                        val outcome = BgBeatUiState.Ineligible(BgBeatIneligibleReason.UnsupportedFormat, itemKey, accountId)
                        failedAnalysis = identity to outcome
                        _uiState.value = outcome
                    }
                    BgBeatStatus.ERROR -> {
                        val outcome = BgBeatUiState.Error(
                            result.message.ifBlank { "Ошибка анализа музыки" },
                            itemKey,
                            accountId
                        )
                        failedAnalysis = identity to outcome
                        _uiState.value = outcome
                    }
                }
            } catch (ce: CancellationException) {
                // Cooperative cancellation must NOT be captured as an analysis failure
                throw ce
            } catch (e: Exception) {
                if (sequenceId.get() == reqSeq && activeIdentity == identity) {
                    Log.e(TAG, "Analysis exception for item $itemKey", e)
                    val outcome = BgBeatUiState.Error("Исключение: ${e.localizedMessage}", itemKey, accountId)
                    failedAnalysis = identity to outcome
                    _uiState.value = outcome
                }
            } finally {
                BgBeatNative.nativeDestroyCancelToken(cancelToken)
                if (currentCancelToken == cancelToken) {
                    currentCancelToken = 0L
                }
                if (sequenceId.get() == reqSeq && activeIdentity == identity) {
                    activeIdentity = null
                }
            }
        }
    }
}
