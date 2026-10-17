package com.beykus.beygram.ui.screens.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.SystemClock
import android.os.StatFs
import android.media.MediaMetadataRetriever
import android.util.Log
import com.beykus.beygram.R
import com.beykus.beygram.diagnostics.recordAppEvent
import com.beykus.beygram.diagnostics.report.EventKind
import com.beykus.beygram.diagnostics.report.Severity
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/** OGG/Opus mono recording; short takes auto-send and locked takes go to the preview callback. */
internal class AudioNoteRecorder(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onFinished: (OutgoingMedia, previewFirst: Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    var active by androidx.compose.runtime.mutableStateOf(false)
        private set
    var locked by androidx.compose.runtime.mutableStateOf(false)
        private set
    var canceling by androidx.compose.runtime.mutableStateOf(false)
        private set
    var elapsedMs by androidx.compose.runtime.mutableLongStateOf(0L)
        private set

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private var timer: Job? = null
    private val amplitudes = mutableListOf<Int>()

    fun press() {
        if (active || file != null) return
        if (StatFs(context.filesDir.absolutePath).availableBytes < 32L * 1024L * 1024L) {
            onError(context.getString(R.string.recorder_storage_low))
            return
        }
        val directory = File(context.filesDir, "uploads").resolve(UUID.randomUUID().toString()).apply { mkdirs() }
        val target = directory.resolve("voice-note.ogg")
        Log.i(TAG, "voice_start_requested")
        try {
            val created = MediaRecorder()
            recorder = created
            created.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.OGG)
                setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                setAudioChannels(1)
                setAudioSamplingRate(48_000)
                setAudioEncodingBitRate(96_000)
                setOutputFile(target.absolutePath)
                prepare()
                start()
            }
            file = target
            amplitudes.clear()
            startedAt = SystemClock.elapsedRealtime()
            elapsedMs = 0L
            locked = false
            canceling = false
            active = true
            Log.i(TAG, "voice_recording_started")
            timer = scope.launch {
                while (active) {
                    elapsedMs = SystemClock.elapsedRealtime() - startedAt
                    runCatching { recorder?.maxAmplitude ?: 0 }.getOrNull()?.let(amplitudes::add)
                    delay(100)
                }
            }
        } catch (failure: Exception) {
            Log.e(TAG, "voice_recording_start_failed")
            recordAppEvent(EventKind.UI, "voice_note_start_failed", "Голосовое: запись не запустилась", Severity.ERROR, mapOf("cause" to failure.javaClass.simpleName))
            runCatching { recorder?.release() }
            recorder = null
            target.delete()
            directory.delete()
            onError(context.getString(R.string.voice_recorder_start_failed))
        }
    }

    fun onDrag(cancelNow: Boolean) { canceling = cancelNow }

    fun lock() {
        if (active) { locked = true; canceling = false; Log.i(TAG, "voice_recording_locked") }
    }

    fun release(cancel: Boolean) {
        if (!active || locked) return
        Log.i(TAG, "voice_recording_released cancel=$cancel elapsedMs=$elapsedMs")
        if (cancel) discard() else finish(previewFirst = false)
    }

    fun finishLocked() {
        if (active && locked) finish(previewFirst = false)
    }

    fun stopForRecovery() { if (active) finish(previewFirst = true) }

    fun discardLocked() {
        if (active && locked) discard()
    }

    fun dispose() {
        if (active) discard()
    }

    private fun finish(previewFirst: Boolean) {
        val target = file ?: return
        active = false
        locked = false
        canceling = false
        timer?.cancel(); timer = null
        val durationMs = SystemClock.elapsedRealtime() - startedAt
        Log.i(TAG, "voice_recording_stopping durationMs=$durationMs preview=$previewFirst")
        val current = recorder
        recorder = null
        if (durationMs < 1_000L) {
            runCatching { current?.release() }
            target.parentFile?.deleteRecursively()
            file = null
            Log.i(TAG, "voice_recording_too_short durationMs=$durationMs")
            onError(context.getString(R.string.voice_recorder_too_short))
            return
        }
        try {
            current?.stop()
        } catch (failure: Exception) {
            Log.w(TAG, "voice_recording_stop_failed durationMs=$durationMs")
            recordAppEvent(EventKind.UI, "voice_note_stop_failed", "Голосовое: сохранить запись не удалось", Severity.ERROR, mapOf("cause" to failure.javaClass.simpleName))
            current?.release()
            target.parentFile?.deleteRecursively()
            file = null
            onError(context.getString(R.string.voice_recorder_save_failed))
            return
        }
        current?.release()
        file = null
        if (durationMs < 1_000L || !target.isFile || target.length() == 0L) {
            target.parentFile?.deleteRecursively()
            onError(context.getString(R.string.voice_recorder_too_short))
            return
        }
        val actualDuration = runCatching {
            MediaMetadataRetriever().run {
                try { setDataSource(target.absolutePath); extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() }
                finally { release() }
            }
        }.getOrNull()?.let { ((it + 999L) / 1000L).toInt() } ?: ((durationMs + 999L) / 1000L).toInt()
        onFinished(OutgoingMedia(
            path = target.absolutePath,
            fileName = context.getString(R.string.voice_recorder_file_name),
            mimeType = "audio/ogg",
            kind = MediaKind.Voice,
            durationSeconds = actualDuration,
            waveform = packWaveform(amplitudes)
        ), previewFirst)
        Log.i(TAG, "voice_recording_saved durationSeconds=$actualDuration bytes=${target.length()}")
        recordAppEvent(EventKind.UI, "voice_note_saved", "Голосовое: запись сохранена, $actualDuration с", attrs = mapOf("duration_s" to actualDuration.toString()))
    }

    private fun discard() {
        Log.i(TAG, "voice_recording_discarded")
        active = false
        locked = false
        canceling = false
        timer?.cancel(); timer = null
        val current = recorder
        recorder = null
        if (SystemClock.elapsedRealtime() - startedAt >= 1_000L) runCatching { current?.stop() }
        current?.release()
        file?.parentFile?.deleteRecursively()
        file = null
    }

    private fun packWaveform(source: List<Int>): ByteArray {
        if (source.isEmpty()) return byteArrayOf()
        val count = 100
        val maximum = source.maxOrNull()?.coerceAtLeast(1) ?: 1
        val values = List(count) { index ->
            val start = index * source.size / count
            val end = ((index + 1) * source.size / count).coerceAtLeast(start + 1).coerceAtMost(source.size)
            val peak = source.subList(start, end).maxOrNull() ?: 0
            ((peak.toDouble() / maximum * 31.0).roundToInt()).coerceIn(0, 31)
        }
        val output = ByteArray((count * 5 + 7) / 8)
        values.forEachIndexed { index, value ->
            val bit = index * 5
            val bits = value shl (bit % 8)
            output[bit / 8] = (output[bit / 8].toInt() or bits).toByte()
            if (bit % 8 > 3) output[bit / 8 + 1] = (output[bit / 8 + 1].toInt() or (value shr (8 - bit % 8))).toByte()
        }
        return output
    }

    private companion object { const val TAG = "BeyGramRecorder" }
}

@Composable
internal fun rememberAudioNoteRecorder(onFinished: (OutgoingMedia, Boolean) -> Unit, onError: (String) -> Unit): AudioNoteRecorder {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentOnError by rememberUpdatedState(onError)
    val recorder = remember(context) { AudioNoteRecorder(context, scope, { media, preview -> currentOnFinished(media, preview) }, { currentOnError(it) }) }
    DisposableEffect(recorder) { onDispose { recorder.dispose() } }
    return recorder
}
