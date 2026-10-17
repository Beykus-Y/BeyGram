package com.beykus.beygram.diagnostics.report

import java.io.File

/**
 * Хранит чистый черновик описания проблемы: только текст, без nonce, токенов
 * и служебных состояний. Каталог исключён из backup/data-extraction правил.
 * После смерти процесса отчёт сам не отправляется — пользователь видит
 * восстановленный черновик и начинает заново.
 */
class ReportDraftStore(private val baseDir: File) {

    private val directory: File get() = File(baseDir, DIRECTORY_NAME)
    private val file: File get() = File(directory, FILE_NAME)

    fun load(): String? = try {
        val text = file.readText(Charsets.UTF_8)
        text.ifEmpty { null }
    } catch (_: Exception) {
        null
    }

    fun save(description: String, includeDiagnostics: Boolean) = try {
        directory.mkdirs()
        file.writeText(description, Charsets.UTF_8)
        File(directory, FLAGS_NAME).writeText(if (includeDiagnostics) "1" else "0", Charsets.UTF_8)
    } catch (_: Exception) {
        // Черновик не критичен: сбой записи не должен ломать отправку.
    }

    fun loadIncludeDiagnostics(): Boolean = try {
        File(directory, FLAGS_NAME).readText(Charsets.UTF_8).trim() != "0"
    } catch (_: Exception) {
        true
    }

    fun clear() = try {
        file.delete()
        File(directory, FLAGS_NAME).delete()
    } catch (_: Exception) {
    }

    companion object {
        const val DIRECTORY_NAME = "diagnostics-report"
        private const val FILE_NAME = "draft.txt"
        private const val FLAGS_NAME = "flags.txt"
    }
}
