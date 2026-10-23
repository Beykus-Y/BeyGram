package com.beykus.beygram.data.media

import android.content.Context
import androidx.annotation.StringRes
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.repository.TelegramRepository
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

internal data class VideoNoteLossState(
    @StringRes val phaseRes: Int = R.string.loss_phase_idle,
    val running: Boolean = false,
    val report: String = "",
    val sentMessageId: Long? = null,
    val localDirectory: File? = null,
    val independentDownload: Boolean = false
)

/** Pure comparison: changed container bytes are NOT automatically a loss of visual quality.
 * The optional context localizes the user-visible report; null keeps a stable text for JVM tests.
 */
internal fun compareVideoNoteLoss(
    local: VideoNoteFrameStats,
    remote: VideoNoteFrameStats?,
    sameSha256: Boolean?,
    independent: Boolean,
    context: Context? = null
): String = buildString {
    fun line(id: Int, fallback: String, vararg args: Any): String =
        context?.getString(id, *args) ?: fallback
    fun yes(value: Boolean): String =
        line(if (value) R.string.loss_yes else R.string.loss_no, if (value) "ДА" else "НЕТ")
    if (remote == null) {
        appendLine(line(R.string.loss_compare_missing,
            "Повторное получение файла: не удалось. Состояние параметров неизвестно."))
        return@buildString
    }
    val changedSize = local.width != remote.width || local.height != remote.height
    val changedCount = local.frameCount != remote.frameCount
    val changedFps = local.measuredFps != null && remote.measuredFps != null &&
        kotlin.math.abs(local.measuredFps - remote.measuredFps) > 1.0
    appendLine(line(R.string.loss_compare_size,
        "Изменён размер кадра: ${yes(changedSize)} (${local.width}x${local.height} → ${remote.width}x${remote.height})",
        yes(changedSize), "${local.width}x${local.height}", "${remote.width}x${remote.height}"))
    appendLine(line(R.string.loss_compare_frames,
        "Изменилось число кадров: ${yes(changedCount)} (${local.frameCount} → ${remote.frameCount})",
        yes(changedCount), local.frameCount, remote.frameCount))
    val fpsValue = if (changedFps) yes(true) else line(R.string.loss_not_known, "НЕТ / не определён")
    appendLine(line(R.string.loss_compare_fps, "Изменился измеренный FPS: ${fpsValue}", fpsValue))
    val sha = when (sameSha256) {
        true -> yes(true)
        false -> yes(false)
        null -> line(R.string.loss_no_data, "нет данных")
    }
    appendLine(line(R.string.loss_compare_sha, "Совпадение SHA-256: ${sha}", sha))
    val verified = line(if (independent) R.string.loss_verified else R.string.loss_unverified,
        if (independent) "подтверждено" else "НЕ ПОДТВЕРЖДЕНО")
    appendLine(line(R.string.loss_compare_remote, "Независимое скачивание: ${verified}", verified))
    when {
        !independent -> appendLine(line(R.string.loss_compare_cached_warning,
            "Вывод ограничен: Telegram мог вернуть закэшированные байты. Нельзя доказать отсутствие изменений на сервере."))
        changedSize || changedFps || changedCount -> appendLine(line(R.string.loss_compare_transformed,
            "Изменение параметров обнаружено между локальным отправляемым MP4 и повторно полученным файлом. Этап и инициатор перекодирования не доказаны."))
        sameSha256 == false -> appendLine(line(R.string.loss_compare_bytes_changed,
            "Изменились байты/контейнер, но потери размера/FPS/числа кадров не обнаружены. Визуальная деградация НЕ доказана."))
        sameSha256 == true -> appendLine(line(R.string.loss_compare_identical,
            "Байты локального и независимо полученного файла совпадают."))
        else -> appendLine(line(R.string.loss_compare_insufficient, "Недостаточно данных для вывода."))
    }
}

/** Did the requested synthetic frame rate survive each stage? Null measurements stay "unknown". */
internal fun syntheticFpsKept(target: Int, measured: Double?): Boolean? = measured?.let { it >= target * 0.95 }

private fun syntheticFpsVerdict(context: Context, target: Int, source: Double?, export: Double?, remote: Double?): String {
    fun mark(value: Double?) = when (syntheticFpsKept(target, value)) {
        true -> context.getString(R.string.loss_yes)
        false -> context.getString(R.string.loss_no)
        null -> context.getString(R.string.loss_no_data)
    }
    return "A=${mark(source)}, C=${mark(export)}, F=${mark(remote)}"
}

/**
 * Explicitly triggered by the user after the confirmation dialog. Synthetic frames never
 * access the camera or microphone. All media stays in noBackup and is not added to crash reports.
 */
internal class VideoNoteLossDiagnostics(
    private val context: Context,
    private val repository: TelegramRepository
) {
    private val root = File(context.noBackupFilesDir, "video-note-loss")
    private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    companion object {
        // Keep each repository's diagnostic sender and sentMessageId alive while switching
        // UI accounts. Identity keys prevent a different TDLib session claiming the result.
        private val instances = java.util.IdentityHashMap<TelegramRepository, VideoNoteLossDiagnostics>()
        @Synchronized fun getInstance(context: Context, repository: TelegramRepository): VideoNoteLossDiagnostics =
            instances.getOrPut(repository) { VideoNoteLossDiagnostics(context.applicationContext, repository) }
    }

    @Synchronized fun start(fps: Int = VideoNoteSyntheticSource.FPS_30): Boolean {
        // Never lose a deletion handle by starting over an already-sent test message.
        if (activeJob?.isActive == true || _state.value.running ||
            _state.value.sentMessageId != null) return false
        activeJob = workScope.launch { run(fps) }
        return true
    }
    private val _state = MutableStateFlow(VideoNoteLossState())
    val state: StateFlow<VideoNoteLossState> = _state.asStateFlow()

    private suspend fun run(fps: Int) {
        if (_state.value.running) return
        _state.value = VideoNoteLossState(phaseRes = R.string.loss_phase_generate, running = true)
        val folder = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
        val source = File(folder, "synthetic-input.mp4")
        val original = File(folder, "source-reference.mp4")
        val remoteCopy = File(folder, "downloaded-after-telegram.mp4")
        try {
            check(repository.authState.value == com.beykus.beygram.domain.model.AuthState.Ready) {
                "Telegram account not authenticated"
            }
            removeOldTempFiles()
            VideoNoteSyntheticSource.generate(source, fps)
            source.copyTo(original)
            val sourceStats = analyzeVideoNoteFrameSamples(original)
            _state.value = _state.value.copy(phaseRes = R.string.loss_phase_export)
            val exportDirectory = File(folder, "export")
            val output: OutgoingMedia = VideoNoteProcessor(context)
                .process(source, VideoMessageSettings(preset = VideoQualityPreset.High), exportDirectory)
                .single()
            val outputFile = File(output.path)
            val exportStats = analyzeVideoNoteFrameSamples(outputFile)
            val exportedHash = sha256(outputFile)
            val sourceHash = sha256(original)
            require(output.kind == com.beykus.beygram.domain.model.MediaKind.VideoNote)
            require(output.width == output.height && output.width in 1..640)
            require(output.durationSeconds in 1..60)
            _state.value = _state.value.copy(phaseRes = R.string.loss_phase_send)
            val sent = repository.sendVideoNoteLossTest(output, remoteCopy).getOrThrow()
            // Only after confirmed UpdateMessageSendSucceeded do we show the result as sent.
            _state.value = _state.value.copy(
                phaseRes = R.string.loss_phase_analyze, sentMessageId = sent.messageId,
                independentDownload = sent.independentDownloadVerified
            )
            // A corrupt or unreadable remote MP4 must be reported as inconclusive, not hide
            // the confirmed send receipt or the optional exact-message deletion control.
            val downloadedStats = sent.remoteCopyPath?.let { path ->
                runCatching { analyzeVideoNoteFrameSamples(File(path)) }.getOrNull()
            }
            val remoteSha = sent.remoteCopyPath?.let { path ->
                runCatching { sha256(File(path)) }.getOrNull()
            }
            val report = buildString {
                appendLine(context.getString(R.string.loss_report_heading))
                appendLine(context.getString(R.string.loss_report_type_fps, fps, VideoNoteSyntheticSource.frameCount(fps),
                    VideoNoteSyntheticSource.SECONDS))
                appendLine(context.getString(R.string.loss_report_source, sourceStats.summary(context)))
                appendLine("SHA-256 A: $sourceHash")
                appendLine(context.getString(R.string.loss_report_export, exportStats.summary(context)))
                appendLine("SHA-256 C: $exportedHash")
                appendLine(context.getString(R.string.loss_report_sent, output.width, output.height, output.durationSeconds, exportedHash))
                appendLine(context.getString(R.string.loss_report_confirmed_shape, sent.declaredSide, sent.declaredSide,
                    sent.declaredDurationSeconds, sent.remoteFileBytes))
                appendLine(context.getString(
                    R.string.loss_report_evidence,
                    context.getString(if (sent.independentDownloadVerified)
                        R.string.loss_evidence_verified else R.string.loss_evidence_unverified)
                ))
                appendLine(context.getString(R.string.loss_report_file, downloadedStats?.summary(context)
                    ?: context.getString(R.string.loss_report_unavailable)))
                appendLine("SHA-256 F: ${remoteSha ?: context.getString(R.string.loss_report_not_measured)}")
                appendLine()
                appendLine(context.getString(R.string.loss_report_processing_changes,
                    sourceStats.width != exportStats.width, sourceStats.frameCount != exportStats.frameCount))
                appendLine(context.getString(R.string.loss_report_fps_kept,
                    syntheticFpsVerdict(context, fps, sourceStats.measuredFps, exportStats.measuredFps, downloadedStats?.measuredFps)))
                appendLine(context.getString(R.string.loss_report_local_hashed))
                append(compareVideoNoteLoss(exportStats, downloadedStats,
                    remoteSha?.let { it == exportedHash }, sent.independentDownloadVerified, context))
                appendLine(context.getString(R.string.loss_report_camera_notice))
                appendLine(context.getString(R.string.loss_report_private_notice))
            }
            _state.value = _state.value.copy(
                phaseRes = R.string.loss_phase_done, running = false, report = report, localDirectory = folder
            )
        } catch (cancelled: CancellationException) {
            _state.value = _state.value.copy(phaseRes = R.string.loss_phase_cancelled, running = false, localDirectory = folder,
                report = context.getString(R.string.loss_report_cancelled))
            throw cancelled
        } catch (failure: Exception) {
            _state.value = _state.value.copy(phaseRes = R.string.loss_phase_failed, running = false, localDirectory = folder,
                report = context.getString(R.string.loss_report_failure, failure.javaClass.simpleName))
        }
    }

    suspend fun deleteTestMessage(): Result<Unit> {
        val id = _state.value.sentMessageId ?: return Result.failure(IllegalStateException("Нет подтверждённого тестового сообщения"))
        val result = repository.deleteVideoNoteLossTest(id)
        if (result.isSuccess) _state.value = _state.value.copy(sentMessageId = null, phaseRes = R.string.loss_phase_deleted)
        return result
    }

    fun clearLocalFiles() {
        if (_state.value.running) return
        _state.value.localDirectory?.takeIf { it.parentFile == root }?.deleteRecursively()
        _state.value = _state.value.copy(localDirectory = null)
    }

    private fun removeOldTempFiles() {
        root.mkdirs()
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        root.listFiles()?.filter { it.isDirectory && it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
    }

    private suspend fun sha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val chunk = ByteArray(8192)
            while (true) {
                val count = stream.read(chunk)
                if (count < 0) break
                digest.update(chunk, 0, count)
            }
        }
        digest.digest().joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 0xff) }
    }
}
