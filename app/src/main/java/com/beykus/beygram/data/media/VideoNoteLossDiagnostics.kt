package com.beykus.beygram.data.media

import android.content.Context
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.repository.TelegramRepository
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

internal data class VideoNoteLossState(
    val phase: String = "Ожидание запуска",
    val running: Boolean = false,
    val report: String = "",
    val sentMessageId: Long? = null,
    val localDirectory: File? = null,
    val independentDownload: Boolean = false
)

/** Pure comparison: changed container bytes are NOT automatically a loss of visual quality. */
internal fun compareVideoNoteLoss(
    local: VideoNoteFrameStats,
    remote: VideoNoteFrameStats?,
    sameSha256: Boolean?,
    independent: Boolean
): String = buildString {
    if (remote == null) {
        appendLine("Повторное получение файла: не удалось. Состояние параметров неизвестно.")
        return@buildString
    }
    val changedSize = local.width != remote.width || local.height != remote.height
    val changedCount = local.frameCount != remote.frameCount
    val changedFps = local.measuredFps != null && remote.measuredFps != null &&
        kotlin.math.abs(local.measuredFps - remote.measuredFps) > 1.0
    appendLine("Изменён размер кадра: ${if (changedSize) "ДА" else "НЕТ"} (${local.width}x${local.height} → ${remote.width}x${remote.height})")
    appendLine("Изменилось число кадров: ${if (changedCount) "ДА" else "НЕТ"} (${local.frameCount} → ${remote.frameCount})")
    appendLine("Изменился измеренный FPS: ${if (changedFps) "ДА" else "НЕТ / не определён"}")
    appendLine("Совпадение SHA-256: ${when (sameSha256) { true -> "ДА"; false -> "НЕТ"; null -> "нет данных" }}")
    appendLine("Независимое скачивание: ${if (independent) "подтверждено" else "НЕ ПОДТВЕРЖДЕНО"}")
    when {
        !independent -> appendLine("Вывод ограничен: TDLib мог вернуть закэшированные байты. Нельзя доказать отсутствие изменений на сервере.")
        changedSize || changedFps || changedCount -> appendLine("Изменение параметров обнаружено между локальным отправляемым MP4 и повторно полученным файлом. Этап и инициатор перекодирования не доказаны.")
        sameSha256 == false -> appendLine("Изменились байты/контейнер, но потери размера/FPS/числа кадров не обнаружены. Визуальная деградация НЕ доказана.")
        sameSha256 == true -> appendLine("Байты локального и независимо полученного файла совпадают.")
        else -> appendLine("Недостаточно данных для вывода.")
    }
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
    private val _state = MutableStateFlow(VideoNoteLossState())
    val state: StateFlow<VideoNoteLossState> = _state.asStateFlow()

    suspend fun run() {
        if (_state.value.running) return
        _state.value = VideoNoteLossState(phase = "Генерация локального теста", running = true)
        val folder = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
        val source = File(folder, "synthetic-input.mp4")
        val original = File(folder, "source-reference.mp4")
        val remoteCopy = File(folder, "downloaded-after-telegram.mp4")
        try {
            check(repository.authState.value == com.beykus.beygram.domain.model.AuthState.Ready) {
                "Telegram account not authenticated"
            }
            removeOldTempFiles()
            VideoNoteSyntheticSource.generate(source)
            source.copyTo(original)
            val sourceStats = analyzeVideoNoteFrameSamples(original)
            _state.value = _state.value.copy(phase = "Экспорт через Media3")
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
            _state.value = _state.value.copy(phase = "TDLib: отправка кружка в Избранное")
            val sent = repository.sendVideoNoteLossTest(output, remoteCopy).getOrThrow()
            // Only after confirmed UpdateMessageSendSucceeded do we show the result as sent.
            _state.value = _state.value.copy(
                phase = "Анализ полученного файла", sentMessageId = sent.messageId,
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
                appendLine("BeyGram — диагностика потерь видеосообщений")
                appendLine("Тип: синтетические кадры, без камеры и микрофона")
                appendLine("Источник A: ${sourceStats.summary()}")
                appendLine("SHA-256 A: $sourceHash")
                appendLine("Media3 C: ${exportStats.summary()}")
                appendLine("SHA-256 C: $exportedHash")
                appendLine("TDLib D: video note, ${output.width}x${output.height}, ${output.durationSeconds}s; SHA-256=$exportedHash")
                appendLine("TDLib E: UpdateMessageSendSucceeded подтверждён; remote length=${sent.declaredSide}; remote bytes=${sent.remoteFileBytes}")
                appendLine("Источник F: ${sent.sourceEvidence}")
                appendLine("Файл F: ${downloadedStats?.summary() ?: "недоступен"}")
                appendLine("SHA-256 F: ${remoteSha ?: "не измерен"}")
                appendLine()
                appendLine("A→C: изменение разрешения=${sourceStats.width != exportStats.width}; кадров=${sourceStats.frameCount != exportStats.frameCount}")
                appendLine("C→D: локальный файл перечитан и хэширован до передачи")
                append(compareVideoNoteLoss(exportStats, downloadedStats, remoteSha?.let { it == exportedHash }, sent.independentDownloadVerified))
                appendLine("Синтетический тест НЕ измеряет FPS физической камеры и не доказывает визуальное качество.")
                appendLine("В отчёт не включены Telegram message ID, chat ID и идентификаторы файлов.")
            }
            _state.value = _state.value.copy(
                phase = "Проверка завершена", running = false, report = report, localDirectory = folder
            )
        } catch (cancelled: CancellationException) {
            _state.value = _state.value.copy(phase = "Проверка отменена", running = false, localDirectory = folder,
                report = "Проверка отменена. Если тест уже отправлен, проверьте Избранное.")
            throw cancelled
        } catch (failure: Exception) {
            _state.value = _state.value.copy(phase = "Ошибка проверки", running = false, localDirectory = folder,
                report = "Ошибка диагностики: ${failure.javaClass.simpleName}. " +
                    "Файл мог быть отправлен, если сбой произошёл после TDLib accepted. Проверьте Избранное. " +
                    "Заключение об отсутствии потерь НЕ получено.")
        }
    }

    suspend fun deleteTestMessage(): Result<Unit> {
        val id = _state.value.sentMessageId ?: return Result.failure(IllegalStateException("Нет подтверждённого тестового сообщения"))
        val result = repository.deleteVideoNoteLossTest(id)
        if (result.isSuccess) _state.value = _state.value.copy(sentMessageId = null, phase = "Тест удалён из Избранного")
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
