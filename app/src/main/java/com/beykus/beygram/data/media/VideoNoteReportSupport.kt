package com.beykus.beygram.data.media

/**
 * Per-configuration outcome vocabulary shared by every diagnostic stage. "Not tested" and
 * "unknown" are never shown as "no": a missing measurement stays a missing measurement.
 */
internal enum class VideoNoteTestStatus(val key: String) {
    NOT_ADVERTISED("not_advertised"),
    NOT_APPLICABLE("not_applicable"),
    NOT_QUERIED("not_queried"),
    SKIPPED_BY_POLICY("skipped_by_policy"),
    SKIPPED_BY_USER("skipped_by_user"),
    UNSUPPORTED_SESSION("unsupported_session"),
    BIND_FAILED("bind_failed"),
    SESSION_FAILED("session_failed"),
    RECORDER_FAILED("recorder_failed"),
    TIMED_OUT("timed_out"),
    NO_FRAMES("no_frames"),
    INSUFFICIENT_FRAMES("insufficient_frames"),
    UNSTABLE("unstable"),
    MEASURED_PASS("measured_pass"),
    MEASURED_FAIL("measured_fail"),
    CANCELLED("cancelled");

    /** True only for a status that carries a measurement; everything else means "no conclusion". */
    val isMeasured: Boolean get() = this == MEASURED_PASS || this == MEASURED_FAIL || this == UNSTABLE
}

/**
 * Strips identifiers from report text before it is copied or shared: file paths, e-mails,
 * phone numbers, @handles and bot tokens. Reports must not carry account data (issue #234, §1).
 */
internal object VideoNoteReportRedactor {
    private val rules = listOf(
        Regex("""\b\d{6,}:[A-Za-z0-9_-]{30,}""") to "<token>",
        Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""") to "<email>",
        Regex("""(?<![\w@])@[A-Za-z][A-Za-z0-9_]{4,}""") to "<handle>",
        Regex("""\+\d[\d\s\-()]{7,}\d""") to "<phone>",
        Regex("""(?:/data|/storage|/sdcard|/mnt)/[^\s,;)"']+""") to "<path>"
    )

    fun redact(text: String): String = rules.fold(text) { acc, (regex, replacement) -> regex.replace(acc, replacement) }
}
