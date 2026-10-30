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

sealed interface BgBeatUiState {
    val itemKey: String? get() = null
    val accountId: String? get() = null

    data object Disabled : BgBeatUiState
    data class Idle(override val itemKey: String? = null, override val accountId: String? = null) : BgBeatUiState
    data class Ineligible(val reason: String, override val itemKey: String? = null, override val accountId: String? = null) : BgBeatUiState
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
            _uiState.value = BgBeatUiState.Ineligible("BG Beat: только для музыки", item.key, accountId)
            return
        }

        // Filter 2: Exclude secret media and system-private items
        if (item.secret || item.systemPrivate) {
            cancelCurrentAnalysis()
            activeIdentity = null
            completedIdentity = null
            _uiState.value = BgBeatUiState.Ineligible("BG Beat: приватное медиа исключено", item.key, accountId)
            return
        }

        // Filter 3: Must not be actively streaming/growing
        if (item.growing != null) {
            cancelCurrentAnalysis()
            activeIdentity = null
            completedIdentity = null
            _uiState.value = BgBeatUiState.Ineligible("BG Beat: файл ещё загружается", item.key, accountId)
            return
        }

        // Filter 4: Must be a completed, valid file on local storage
        val file = File(item.path)
        if (!file.exists() || !file.isFile || file.length() <= 0L || file.name.endsWith(".temp")) {
            cancelCurrentAnalysis()
            activeIdentity = null
            completedIdentity = null
            _uiState.value = BgBeatUiState.Ineligible("BG Beat: файл ещё не загружен", item.key, accountId)
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

        // Deduplicate: If an analysis for this exact file identity is already in flight, do nothing
        if (activeIdentity == identity) {
            return
        }

        // Deduplicate: If this exact file identity is already completed and shown, do nothing
        if (completedIdentity == identity && _uiState.value is BgBeatUiState.Ready) {
            return
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
                        _uiState.value = BgBeatUiState.Ineligible(
                            result.message.ifBlank { "Формат аудио не поддерживается" },
                            itemKey,
                            accountId
                        )
                    }
                    BgBeatStatus.ERROR -> {
                        _uiState.value = BgBeatUiState.Error(
                            result.message.ifBlank { "Ошибка анализа музыки" },
                            itemKey,
                            accountId
                        )
                    }
                }
            } catch (ce: CancellationException) {
                // Cooperative cancellation must NOT be captured as an analysis failure
                throw ce
            } catch (e: Exception) {
                if (sequenceId.get() == reqSeq && activeIdentity == identity) {
                    Log.e(TAG, "Analysis exception for item $itemKey", e)
                    _uiState.value = BgBeatUiState.Error("Исключение: ${e.localizedMessage}", itemKey, accountId)
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
