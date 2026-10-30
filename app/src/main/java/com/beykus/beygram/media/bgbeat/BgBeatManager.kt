package com.beykus.beygram.media.bgbeat

import android.content.Context
import android.util.Log
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
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

sealed interface BgBeatUiState {
    data object Disabled : BgBeatUiState
    data object Idle : BgBeatUiState
    data class Ineligible(val reason: String) : BgBeatUiState
    data class Analyzing(val fraction: Float, val stage: String) : BgBeatUiState
    data class Ready(val analysis: BgBeatAnalysis) : BgBeatUiState
    data class Error(val message: String) : BgBeatUiState
}

/**
 * Coordinates BG Beat music analysis with PlaybackEngine.
 * - Strictly background execution (Dispatchers.Default).
 * - Cooperative cancellation when track changes.
 * - Only analyzes fully downloaded non-secret Audio files.
 * - No auto-downloads, no UI blocking, no modifications to Media3.
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
                    BgBeatSettingsStore(context.applicationContext)
                ).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow<BgBeatUiState>(BgBeatUiState.Idle)
    val uiState: StateFlow<BgBeatUiState> = _uiState.asStateFlow()

    private var currentJob: Job? = null
    private var currentCancelToken: Long = 0L
    private var activeItemKey: String? = null

    init {
        // Observe playback and settings
        scope.launch {
            PlaybackEngine.state.collect { state ->
                val item = state.item
                if (item?.key != activeItemKey) {
                    activeItemKey = item?.key
                    cancelCurrentAnalysis()
                    evaluateAndAnalyze(item, force = false)
                }
            }
        }

        scope.launch {
            settingsStore.enabled.collect { isEnabled ->
                val currentItem = PlaybackEngine.state.value.item
                if (!isEnabled) {
                    cancelCurrentAnalysis()
                    _uiState.value = BgBeatUiState.Disabled
                } else {
                    evaluateAndAnalyze(currentItem, force = false)
                }
            }
        }
    }

    private fun cancelCurrentAnalysis() {
        if (currentCancelToken != 0L) {
            BgBeatNative.nativeCancel(currentCancelToken)
        }
        currentJob?.cancel()
        currentJob = null
    }

    fun reanalyze() {
        val currentItem = PlaybackEngine.state.value.item ?: return
        cancelCurrentAnalysis()
        evaluateAndAnalyze(currentItem, force = true)
    }

    private fun evaluateAndAnalyze(item: PlaybackItem?, force: Boolean) {
        if (!settingsStore.enabled.value) {
            _uiState.value = BgBeatUiState.Disabled
            return
        }

        if (item == null) {
            _uiState.value = BgBeatUiState.Idle
            return
        }

        // Filter 1: Only regular music
        if (item.kind != MediaKind.Audio) {
            _uiState.value = BgBeatUiState.Ineligible("BG Beat: только для музыки")
            return
        }

        // Filter 2: Exclude secret media
        if (item.secret) {
            _uiState.value = BgBeatUiState.Ineligible("BG Beat: приватное медиа исключено")
            return
        }

        // Filter 3: Must be fully downloaded local file
        val file = File(item.path)
        if (!file.exists() || !file.isFile || file.length() <= 0L || file.name.endsWith(".temp")) {
            _uiState.value = BgBeatUiState.Ineligible("BG Beat: файл ещё не загружен")
            return
        }

        if (!BgBeatNative.isLoaded) {
            _uiState.value = BgBeatUiState.Error("Нативная библиотека BG Beat не загружена")
            return
        }

        startAnalysis(file, force)
    }

    private fun startAnalysis(file: File, force: Boolean) {
        currentJob = scope.launch {
            _uiState.value = BgBeatUiState.Analyzing(0f, "Запуск анализа")

            val cancelToken = BgBeatNative.nativeCreateCancelToken()
            currentCancelToken = cancelToken

            val cacheDir = BgBeatSettingsStore.getAccountCacheDir(context, "legacy").absolutePath

            try {
                val result = withContext(Dispatchers.Default) {
                    // If forced reanalysis, we can either pass an empty cache or let the analyzer overwrite
                    val effectiveCacheDir = if (force) "" else cacheDir
                    val jsonStr = BgBeatNative.nativeAnalyzeFile(
                        filePath = file.absolutePath,
                        cacheDir = effectiveCacheDir,
                        minBpm = 55.0,
                        maxBpm = 200.0,
                        cancelTokenHandle = cancelToken,
                        progressListener = { fraction, stage ->
                            scope.launch(Dispatchers.Main) {
                                _uiState.value = BgBeatUiState.Analyzing(fraction, stage)
                            }
                        }
                    )
                    if (jsonStr != null) {
                        BgBeatAnalysis.fromJson(jsonStr)
                    } else {
                        BgBeatAnalysis(status = BgBeatStatus.ERROR, message = "Пустой ответ от C++ движка")
                    }
                }

                when (result.status) {
                    BgBeatStatus.OK -> {
                        _uiState.value = BgBeatUiState.Ready(result)
                        withContext(Dispatchers.IO) {
                            BgBeatSettingsStore.enforceCacheQuota(context)
                        }
                    }
                    BgBeatStatus.CANCELLED -> {
                        _uiState.value = BgBeatUiState.Idle
                    }
                    BgBeatStatus.UNSUPPORTED -> {
                        _uiState.value = BgBeatUiState.Ineligible(result.message.ifBlank { "Формат аудио не поддерживается" })
                    }
                    BgBeatStatus.ERROR -> {
                        _uiState.value = BgBeatUiState.Error(result.message.ifBlank { "Ошибка анализа музыки" })
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Analysis exception", e)
                _uiState.value = BgBeatUiState.Error("Исключение: ${e.localizedMessage}")
            } finally {
                BgBeatNative.nativeDestroyCancelToken(cancelToken)
                if (currentCancelToken == cancelToken) {
                    currentCancelToken = 0L
                }
            }
        }
    }
}
