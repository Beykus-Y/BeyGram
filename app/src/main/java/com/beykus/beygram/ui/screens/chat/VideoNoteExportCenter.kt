package com.beykus.beygram.ui.screens.chat

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.beykus.beygram.data.media.VideoMessageSettings
import com.beykus.beygram.data.media.VideoNoteProcessor
import com.beykus.beygram.domain.model.OutgoingMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

private const val TAG = "BeyGramRecorder"

/** Готовый экспорт, ждущий доставку в свой чат. */
internal data class VideoNoteExportResult(val chatId: Long, val media: List<OutgoingMedia>, val previewFirst: Boolean)

/** Сериализует тела экспортов: не более одного Transformer в процессе, даже если рекордеров несколько. */
private val exportMutex = Mutex()

/**
 * Процесс-глобальный шлюз экспорта кружков: один Transformer за раз. Живёт вне экрана чата,
 * поэтому смена чата или поворот экрана не позволяет запустить второй экспорт параллельно.
 */
internal object VideoNoteExportGate {
    var activeExports by mutableStateOf(0)
        private set
    val busy: Boolean get() = activeExports > 0

    fun begin() { activeExports++ }

    fun end() { activeExports = (activeExports - 1).coerceAtLeast(0) }
}

/**
 * Запускает экспорт под процесс-глобальным шлюзом и через [exportMutex]. Освобождение шлюза привязано
 * к самому Job через [Job.invokeOnCompletion]: если scope уже отменён и тело корутины не выполнится,
 * шлюз всё равно освободится — иначе после ухода из чата с поздним Finalize записи остались бы заблокированы.
 */
internal fun CoroutineScope.launchExclusiveExport(block: suspend () -> Unit): Job {
    VideoNoteExportGate.begin()
    val job = launch { exportMutex.withLock { block() } }
    job.invokeOnCompletion { VideoNoteExportGate.end() }
    return job
}

/**
 * Владелец экспорта кружков уровня приложения: переживает выход из чата и уничтожение его ViewModel.
 * Результат кладётся в очередь [results] и доставляется, когда чат снова открыт, — уход посреди
 * обработки больше не теряет запись. RAW и готовые сегменты при сбое остаются на диске.
 */
internal object VideoNoteExportCenter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _results = MutableStateFlow<List<VideoNoteExportResult>>(emptyList())
    val results: StateFlow<List<VideoNoteExportResult>> = _results.asStateFlow()

    /** Ошибки удерживаются до открытия чата: SharedFlow без replay терял их при закрытом чате. */
    private val _failures = MutableStateFlow<List<Long>>(emptyList())
    val failures: StateFlow<List<Long>> = _failures.asStateFlow()

    fun enqueue(context: Context, chatId: Long, source: File, settings: VideoMessageSettings, sessionName: String, previewFirst: Boolean) {
        val appContext = context.applicationContext
        scope.launchExclusiveExport {
            try {
                val outputDirectory = File(appContext.filesDir, "uploads").resolve(sessionName).apply { mkdirs() }
                Log.i(TAG, "video_processing_started session=$sessionName chatId=$chatId")
                val media = VideoNoteProcessor(appContext).process(source, settings, outputDirectory)
                Log.i(TAG, "video_processing_completed session=$sessionName segments=${media.size} bytes=${media.sumOf { it.size }}")
                _results.update { it + VideoNoteExportResult(chatId, media, previewFirst) }
            } catch (failure: Exception) {
                Log.e(TAG, "video_processing_failed session=$sessionName type=${failure.javaClass.simpleName}", failure)
                _failures.update { it + chatId }
            }
        }
    }

    /** Убирает результат после подтверждённой доставки: показа превью или постановки в очередь отправки. */
    fun consume(chatId: Long, result: VideoNoteExportResult) {
        _results.update { current -> current.filterNot { it.chatId == chatId && it == result } }
    }

    /** Забирает накопленную ошибку экспорта чата. */
    fun consumeFailure(chatId: Long): Boolean {
        val had = chatId in _failures.value
        if (had) _failures.update { current -> current - chatId }
        return had
    }
}
