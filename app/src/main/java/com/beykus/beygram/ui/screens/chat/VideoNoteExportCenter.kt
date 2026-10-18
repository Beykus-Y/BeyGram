package com.beykus.beygram.ui.screens.chat

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.beykus.beygram.data.media.VideoMessageSettings
import com.beykus.beygram.data.media.VideoNoteProcessor
import com.beykus.beygram.diagnostics.recordAppEvent
import com.beykus.beygram.diagnostics.report.EventKind
import com.beykus.beygram.diagnostics.report.Severity
import com.beykus.beygram.domain.model.OutgoingMedia
import kotlinx.coroutines.CancellationException
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

internal data class VideoNoteExportResult(
    val chatId: Long,
    val media: List<OutgoingMedia>,
    val previewFirst: Boolean,
    val sessionName: String,
    /** Number of segments already accepted by TDLib. Retained when a later send fails. */
    val nextIndex: Int = 0,
    val sending: Boolean = false,
    /** Preview and retry items stay in the center until explicit Send/Delete. */
    val requiresPreview: Boolean = previewFirst || media.size > 1
)

/** Pure transitions shared by the app-level sender and regression tests. */
internal fun VideoNoteExportResult.markSegmentSent(): VideoNoteExportResult =
    copy(nextIndex = (nextIndex + 1).coerceAtMost(media.size))

internal fun VideoNoteExportResult.markSendFailed(): VideoNoteExportResult =
    copy(sending = false, requiresPreview = true)

/** Автоматический кружок до приёма TDLib: [processing] — идёт обработка, иначе кружок ждёт отправки. */
internal data class PendingVideoNote(val chatId: Long, val sessionName: String, val processing: Boolean, val previewPath: String? = null)

/**
 * Кружки чата для ленты: сначала обрабатываемые, затем ждущие отправки. Кружок с предпросмотром здесь не показывается:
 * он остаётся в диалоге, пока пользователь не отправит или не удалит его. Одна сессия показывается один раз.
 */
internal fun pendingVideoNotesFor(chatId: Long, processing: List<PendingVideoNote>, results: List<VideoNoteExportResult>): List<PendingVideoNote> =
    (processing.filter { it.chatId == chatId } + results.filter { it.chatId == chatId && !it.requiresPreview }
        .map { PendingVideoNote(it.chatId, it.sessionName, processing = false, previewPath = it.media.firstOrNull()?.thumbnailPath) })
        .distinctBy { it.sessionName }

private val exportMutex = Mutex()

/** Process-wide count of queued/running exports, used to block further recording. */
internal object VideoNoteExportGate {
    var activeExports by mutableStateOf(0)
        private set
    val busy: Boolean get() = activeExports > 0

    // begin() вызывается из потока, который запускает экспорт, end() — из потока, где задача завершилась. Без замка обновления
    // теряются, счётчик застревает выше нуля и запись кружков блокируется до перезапуска процесса.
    @Synchronized fun begin() { activeExports++ }
    @Synchronized fun end() { activeExports = (activeExports - 1).coerceAtLeast(0) }
}

/** Mutex guarantees that only one Media3 Transformer body runs at a time. */
internal fun CoroutineScope.launchExclusiveExport(block: suspend () -> Unit): Job {
    VideoNoteExportGate.begin()
    val job = launch { exportMutex.withLock { block() } }
    job.invokeOnCompletion { VideoNoteExportGate.end() }
    return job
}

/**
 * App-scoped ownership of both exporting and delivery. Neither pending video notes nor their
 * network send jobs are owned by ChatScreen / viewModelScope. A result is removed only on
 * successful TDLib acceptance of every segment or explicit user deletion.
 *
 * The queue survives chat navigation/configuration, not Android process death. Disk-backed
 * recovery of process kills and failed transcodes remains a separate task.
 */
internal object VideoNoteExportCenter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _results = MutableStateFlow<List<VideoNoteExportResult>>(emptyList())
    val results: StateFlow<List<VideoNoteExportResult>> = _results.asStateFlow()

    private val _failures = MutableStateFlow<List<Long>>(emptyList())
    val failures: StateFlow<List<Long>> = _failures.asStateFlow()

    private val _pendingNotes = MutableStateFlow<List<PendingVideoNote>>(emptyList())
    /** Автоматические кружки, которые TDLib ещё не принял: лента показывает их сразу, с индикатором. */
    val pendingNotes: StateFlow<List<PendingVideoNote>> = _pendingNotes.asStateFlow()

    /** Used both by the real exporter and deterministic queue tests. */
    internal fun publish(result: VideoNoteExportResult) {
        _results.update { pending ->
            if (pending.any { it.sessionName == result.sessionName }) pending else pending + result
        }
    }

    fun enqueue(context: Context, chatId: Long, source: File, settings: VideoMessageSettings, sessionName: String, previewFirst: Boolean) {
        val appContext = context.applicationContext
        // Автоматический кружок виден в ленте сразу после отпускания, а не когда обработка закончится.
        if (!previewFirst) _pendingNotes.update { it + PendingVideoNote(chatId, sessionName, processing = true) }
        scope.launchExclusiveExport {
            try {
                val outputDirectory = File(appContext.filesDir, "uploads").resolve(sessionName).apply { mkdirs() }
                Log.i(TAG, "video_processing_started session=" + sessionName + " chatId=" + chatId)
                val media = VideoNoteProcessor(appContext).process(source, settings, outputDirectory)
                Log.i(TAG, "video_processing_completed session=" + sessionName + " segments=" + media.size)
                recordAppEvent(EventKind.OTHER, "video_note_processed", "Кружок: обработка завершена", attrs = mapOf("segments" to media.size.toString()))
                publish(VideoNoteExportResult(chatId, media, previewFirst, sessionName))
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                Log.e(TAG, "video_processing_failed session=" + sessionName, failure)
                recordAppEvent(EventKind.OTHER, "video_note_processing_failed", "Кружок: обработка не удалась", Severity.ERROR, mapOf("cause" to failure.javaClass.simpleName))
                _failures.update { it + chatId }
            } finally {
                _pendingNotes.update { current -> current.filterNot { it.sessionName == sessionName } }
            }
        }
    }

    private fun updateResult(sessionName: String, transform: (VideoNoteExportResult) -> VideoNoteExportResult?) {
        _results.update { current ->
            current.mapNotNull { result ->
                if (result.sessionName == sessionName) transform(result) else result
            }
        }
    }

    /**
     * Claims a video-note exactly once, synchronously, before starting an app-scoped send job.
     * Each successful segment advances nextIndex; failure retains this and all later notes.
     * A caller leaving its chat cannot cancel the send job or drop its pending result.
     */
    internal fun send(
        chatId: Long,
        sessionName: String,
        replyId: Long?,
        sender: suspend (OutgoingMedia, Long?) -> Result<Unit>
    ): Boolean {
        val initial: VideoNoteExportResult = synchronized(this) {
            val current = _results.value.firstOrNull { it.chatId == chatId && it.sessionName == sessionName }
                ?: return false
            if (current.sending) return false
            updateResult(sessionName) { it.copy(sending = true) }
            current
        }
        scope.launch {
            var reply = replyId
            try {
                for (index in initial.nextIndex until initial.media.size) {
                    val sent = sender(initial.media[index], reply)
                    if (sent.isFailure) {
                        updateResult(sessionName) { it.markSendFailed() }
                        return@launch
                    }
                    updateResult(sessionName) { it.markSegmentSent() }
                    reply = null
                }
                updateResult(sessionName) { null } // ACK only after all segments were accepted.
            } catch (cancelled: CancellationException) {
                updateResult(sessionName) { it.markSendFailed() }
                throw cancelled
            } catch (failure: Exception) {
                Log.e(TAG, "video_note_send_failed session=" + sessionName, failure)
                recordAppEvent(EventKind.OTHER, "video_note_send_failed", "Кружок: отправка прервалась", Severity.ERROR, mapOf("cause" to failure.javaClass.simpleName))
                updateResult(sessionName) { it.markSendFailed() }
            }
        }
        return true
    }

    /** Explicit dismissal of an unsent draft; never delete a job while it is sending. */
    fun discard(chatId: Long, sessionName: String) {
        val discarded = synchronized(this) {
            val current = _results.value.firstOrNull { it.chatId == chatId && it.sessionName == sessionName }
                ?: return
            if (current.sending) return
            updateResult(sessionName) { null }
            current
        }
        scope.launch(Dispatchers.IO) {
            // Previously accepted parts may still be in TDLib's upload queue; delete only unsent.
            discarded.media.drop(discarded.nextIndex).forEach { media ->
                runCatching { File(media.path).delete() }
                media.thumbnailPath?.let { path -> runCatching { File(path).delete() } }
            }
        }
    }

    fun consumeFailure(chatId: Long): Boolean {
        val had = chatId in _failures.value
        if (had) _failures.update { current -> current - chatId }
        return had
    }
}
