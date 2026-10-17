package com.beykus.beygram.diagnostics.report

/**
 * Модель данных отчёта — зеркало строгой серверной схемы
 * `BeyGram-Diagnostics-Server/app/schemas.py` (extra="forbid").
 * Никаких дополнительных полей: сервер отклонит тело с неизвестным ключом.
 *
 * Правила приватности соблюдаются на уровне типов: здесь нет места для
 * telegram_user_id, nonce, токенов или адресов — они не входят в контракт.
 */

/** Разрешённые сервером категории событий (`EventKind`). */
enum class EventKind(val wire: String) {
    CONNECTION("connection"),
    NETWORK("network"),
    VPN("vpn"),
    TDLIB("tdlib"),
    UPDATE("update"),
    ERROR("error"),
    PERFORMANCE("performance"),
    UI("ui"),
    OTHER("other");

    companion object {
        fun fromWire(value: String): EventKind = entries.firstOrNull { it.wire == value } ?: OTHER
    }
}

/** Разрешённые уровни важности (`Severity`). */
enum class Severity(val wire: String) {
    DEBUG("debug"),
    INFO("info"),
    WARNING("warning"),
    ERROR("error"),
    CRITICAL("critical");

    companion object {
        fun fromWire(value: String): Severity = entries.firstOrNull { it.wire == value } ?: INFO
    }
}

/**
 * Одно плоское событие диагностики. Ограничения сервера:
 * `message` ≤ 500 символов, `code` ≤ 64, до 24 атрибутов с ключами ≤ 64 и значениями ≤ 200.
 */
data class DiagnosticEvent(
    val seq: Long? = null,
    val atSeconds: Double? = null,
    val kind: EventKind = EventKind.OTHER,
    val severity: Severity = Severity.INFO,
    val code: String? = null,
    val message: String = "",
    val attrs: Map<String, String> = emptyMap(),
)

/**
 * Разрешённые несекретные метаданные клиента. Это единственный набор полей,
 * который сервер принимает в `client_meta`; приложениеId и тип сборки
 * отдельными неописанными ключами не передаются (они входят в app_version).
 */
data class ReportClientMeta(
    val appVersion: String? = null,
    val appVersionCode: Int? = null,
    val platform: String? = null,
    val osVersion: String? = null,
    val deviceModel: String? = null,
    val locale: String? = null,
    val vpnActive: Boolean? = null,
    val proxyEnabled: Boolean? = null,
    val tdlibVersion: String? = null,
    val eventCountTotal: Int? = null,
    val bufferOverflowed: Boolean? = null,
)

/**
 * Неизменяемое тело POST /api/v1/bug-reports.
 * Экземпляр фиксируется до первой попытки отправки и не меняется при retry:
 * сервер проверяет отпечаток содержимого для report_id.
 */
data class BugReportPayload(
    val reportId: String,
    val description: String,
    val summary: String? = null,
    val clientMeta: ReportClientMeta? = null,
    val events: List<DiagnosticEvent> = emptyList(),
    val createdAtSeconds: Double? = null,
) {
    init {
        require(reportId.isNotEmpty()) { "report_id is required" }
        require(description.isNotEmpty()) { "description is required" }
    }

    companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_DESCRIPTION_CHARS = 2000
        const val MAX_SUMMARY_CHARS = 8000
        const val MAX_EVENTS = 2000
    }
}

/** Успешный ответ 202 на POST — это «принято в очередь», а не «доставлено». */
data class AcceptedReport(
    val reportId: String,
    val status: String,
    val acceptedAtSeconds: Double,
    val statusToken: String,
    val idempotentReplay: Boolean,
    val detail: String,
)

/** Ответ GET /api/v1/bug-reports/{report_id}. `delivered` — только отсюда. */
data class ReportStatusSnapshot(
    val reportId: String,
    val status: String,
    val acceptedAtSeconds: Double,
    val updatedAtSeconds: Double,
    val deliveredAtSeconds: Double?,
    val attempts: Int,
    val detail: String,
)
