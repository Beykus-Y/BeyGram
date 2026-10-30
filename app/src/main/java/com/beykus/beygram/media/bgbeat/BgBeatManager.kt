package com.beykus.beygram.media.bgbeat

import android.content.Context
import android.util.Log
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * Coordinates BG Beat music analysis with PlaybackEngine and account isolation.
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
    private var activeItemKey: String? = null
    private var activeAccountId: String? = null

    init {
        // Observe playback item and account changes
        scope.launch {
            PlaybackEngine.state.collect { state ->
                val item = state.item
                val itemKey = item?.key
                val itemAccount = item?.accountId ?: PlaybackEngine.currentAccountId ?: "legacy"

                if (itemKey != activeItemKey || itemAccount != activeAccountId) {
                    activeItemKey = itemKey
                    activeAccountId = itemAccount
                    cancelCurrentAnalysis()
                    evaluateAndAnalyze(item, itemAccount, force = false)
                }
            }
        }

        // Observe settings toggle
        scope.launch {
            settingsStore.enabled.collect { isEnabled ->
                val currentItem = PlaybackEngine.state.value.item
                val currentAccount = currentItem?.accountId ?: PlaybackEngine.currentAccountId ?: "legacy"
                if (!isEnabled) {
                    cancelCurrentAnalysis()
                    sequenceId.incrementAndGet()
                    _uiState.value = BgBeatUiState.Disabled
                } else {
                    evaluateAndAnalyze(currentItem, currentAccount, force = false)
                }
            }
        }
    }

    fun onAccountChanged(newAccountId: String) {
        if (newAccountId.isBlank()) return
        if (activeAccountId != newAccountId) {
            activeAccountId = newAccountId
            cancelCurrentAnalysis()
            sequenceId.incrementAndGet()
            val currentItem = PlaybackEngine.state.value.item
            evaluateAndAnalyze(currentItem, newAccountId, force = false)
        }
    }

    fun cancelCurrentAnalysis() {
        if (currentCancelToken != 0L) {
            BgBeatNative.nativeCancel(currentCancelToken)
        }
        currentJob?.cancel()
        currentJob = null
    }

    fun reanalyze() {
        val currentItem = PlaybackEngine.state.value.item ?: return
        val currentAccount = currentItem.accountId.ifBlank { PlaybackEngine.currentAccountId ?: "legacy" }
        cancelCurrentAnalysis()
        evaluateAndAnalyze(currentItem, currentAccount, force = true)
    }

    private fun evaluateAndAnalyze(item: PlaybackItem?, accountId: String, force: Boolean) {
        if (!settingsStore.enabled.value) {
            _uiState.value = BgBeatUiState.Disabled
            return
        }

        if (item == null) {
            _uiState.value = BgBeatUiState.Idle(null, accountId)
            return
        }

        // Rule 1: Only regular music
        if (item.kind != MediaKind.Audio) {
            _uiState.value = BgBeatUiState.Ineligible("BG Beat: только для музыки", item.key, accountId)
            return
        }

        // Rule 2: Exclude secret media and system-private items
        if (item.secret || item.systemPrivate) {
            _uiState.value = BgBeatUiState.Ineligible("BG Beat: приватное медиа исключено", item.key, accountId)
            return
        }

        // Rule 3: Must be a fully downloaded local file (exclude streaming/growing)
        if (item.growing != null) {
            _uiState.value = BgBeatUiState.Ineligible("BG Beat: файл ещё загружается", item.key, accountId)
            return
        }

        val file = File(item.path)
        if (!file.exists() || !file.isFile || file.length() <= 0L || file.name.endsWith(".temp")) {
            _uiState.value = BgBeatUiState.Ineligible("BG Beat: файл ещё не загружен", item.key, accountId)
            return
        }

        if (!BgBeatNative.isLoaded) {
            _uiState.value = BgBeatUiState.Error("Нативная библиотека BG Beat не загружена", item.key, accountId)
            return
        }

        startAnalysis(item, file, accountId, force)
    }

    private fun startAnalysis(item: PlaybackItem, file: File, accountId: String, force: Boolean) {
        val reqSeq = sequenceId.incrementAndGet()
        val itemKey = item.key

        currentJob = scope.launch {
            _uiState.value = BgBeatUiState.Analyzing(0f, "Запуск анализа", itemKey, accountId)

            val cancelToken = BgBeatNative.nativeCreateCancelToken()
            currentCancelToken = cancelToken

            val cacheDir = BgBeatSettingsStore.getAccountCacheDir(context, accountId).absolutePath

            try {
                val result = withContext(Dispatchers.Default) {
                    if (sequenceId.get() != reqSeq || activeItemKey != itemKey || activeAccountId != accountId) {
                        return@withContext null
                    }

                    val effectiveCacheDir = if (force) "" else cacheDir
                    val jsonStr = BgBeatNative.nativeAnalyzeFile(
                        filePath = file.absolutePath,
                        cacheDir = effectiveCacheDir,
                        minBpm = 55.0,
                        maxBpm = 200.0,
                        cancelTokenHandle = cancelToken,
                        progressListener = { fraction, stage ->
                            if (sequenceId.get() == reqSeq && activeItemKey == itemKey && activeAccountId == accountId) {
                                scope.launch(Dispatchers.Main) {
                                    if (sequenceId.get() == reqSeq && activeItemKey == itemKey && activeAccountId == accountId) {
                                        _uiState.value = BgBeatUiState.Analyzing(fraction, stage, itemKey, accountId)
                                    }
                                }
                            }
                        }
                    )

                    if (sequenceId.get() != reqSeq || activeItemKey != itemKey || activeAccountId != accountId) {
                        return@withContext null
                    }

                    if (jsonStr != null) {
                        BgBeatAnalysis.fromJson(jsonStr)
                    } else {
                        BgBeatAnalysis(status = BgBeatStatus.ERROR, message = "Пустой ответ от C++ движка")
                    }
                }

                if (sequenceId.get() != reqSeq || activeItemKey != itemKey || activeAccountId != accountId || result == null) {
                    return@launch
                }

                when (result.status) {
                    BgBeatStatus.OK -> {
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
                if (sequenceId.get() == reqSeq && activeItemKey == itemKey && activeAccountId == accountId) {
                    Log.e(TAG, "Analysis exception for item $itemKey", e)
                    _uiState.value = BgBeatUiState.Error("Исключение: ${e.localizedMessage}", itemKey, accountId)
                }
            } finally {
                BgBeatNative.nativeDestroyCancelToken(cancelToken)
                if (currentCancelToken == cancelToken) {
                    currentCancelToken = 0L
                }
            }
        }
    }
}
