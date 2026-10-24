package com.beykus.beygram.domain.model

import java.io.OutputStream

/** The document format offered by the chat export. With media the document is placed inside a ZIP. */
enum class ChatExportFormat(val extension: String) {
    Json("json"),
    Html("html")
}

/**
 * User-selected chat export options. The date bounds use the same half-open semantics as search filters:
 * [fromDateSeconds] is inclusive and [toDateSeconds] is exclusive. Both are Unix seconds in the device time zone.
 */
data class ChatExportRequest(
    val format: ChatExportFormat = ChatExportFormat.Json,
    val fromDateSeconds: Long? = null,
    val toDateSeconds: Long? = null,
    val includeMedia: Boolean = false,
    /** Human-readable source account, written into the export and shown before the file picker opens. */
    val accountLabel: String = ""
) {
    val hasDateRange: Boolean
        get() = fromDateSeconds != null || toDateSeconds != null

    /** Equal bounds describe an empty half-open range. */
    val hasValidDateRange: Boolean
        get() = fromDateSeconds == null || toDateSeconds == null || fromDateSeconds < toDateSeconds

    fun includesDate(dateSeconds: Long): Boolean {
        if (!hasDateRange) return true
        if (dateSeconds <= 0L) return false
        if (fromDateSeconds != null && dateSeconds < fromDateSeconds) return false
        if (toDateSeconds != null && dateSeconds >= toDateSeconds) return false
        return true
    }
}

enum class ChatExportPhase { Preparing, Media, Messages, Finishing }

/** Progress is emitted from the export coroutine, not the main thread. */
data class ChatExportProgress(
    val phase: ChatExportPhase = ChatExportPhase.Preparing,
    val messagesWritten: Int = 0,
    val mediaCopied: Int = 0,
    val bytesWritten: Long = 0L,
    /** Server-side message count when no date range is active; null keeps the indicator indeterminate. */
    val totalMessages: Int? = null
)

data class ChatExportSummary(
    val messages: Int,
    val mediaFiles: Int,
    val bytesWritten: Long,
    /** Messages TDLib marked as not saveable; they are omitted completely. */
    val skippedSaveRestricted: Int,
    /** Media entries listed in the document but not copied because of availability or size limits. */
    val skippedMedia: Int
)

/**
 * Destination for the export. Android SAF is the production implementation; tests use an in-memory one.
 * [discard] must remove a newly created document or truncate it after cancellation/failure, because the
 * application never keeps a plaintext temporary copy. Implementations should be idempotent: cleanup can
 * be requested by both the export streamer and the ViewModel cancellation path.
 */
interface ChatExportTarget {
    suspend fun openOutput(): OutputStream
    suspend fun discard()
}
