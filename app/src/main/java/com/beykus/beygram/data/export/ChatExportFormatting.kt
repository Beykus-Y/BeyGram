package com.beykus.beygram.data.export

import com.beykus.beygram.domain.model.MediaKind
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val UNSAFE_FILE_NAME = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F\\u007F]+")
private val DOT_RUN = Regex("\\.{2,}")
private val WHITESPACE = Regex("\\s+")
private val SAFE_EXTENSION = Regex("[a-z0-9]{1,8}")
private val EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())

/** JSON string escaping that also replaces unpaired surrogates with U+FFFD, so every export stays valid JSON. */
internal fun jsonString(value: String): String = buildString(value.length + 2) { appendJsonString(this, value) }

internal fun appendJsonString(out: Appendable, value: String) {
    out.append('"')
    var index = 0
    while (index < value.length) {
        val char = value[index]
        when (char) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\b' -> out.append("\\b")
            '\u000C' -> out.append("\\f")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> when {
                char < ' ' || char == '\u2028' || char == '\u2029' -> {
                    out.append("\\u")
                    out.append(char.code.toString(16).padStart(4, '0'))
                }
                Character.isHighSurrogate(char) && (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) ->
                    out.append("\\ufffd")
                Character.isLowSurrogate(char) && (index == 0 || !Character.isHighSurrogate(value[index - 1])) ->
                    out.append("\\ufffd")
                else -> out.append(char)
            }
        }
        index++
    }
    out.append('"')
}

/** Escapes a value for both HTML text and quoted attributes. Newlines stay intact; the document uses pre-wrap. */
internal fun htmlEscape(value: String): String = buildString(value.length) { appendHtmlEscaped(this, value) }

internal fun appendHtmlEscaped(out: Appendable, value: String) {
    for (char in value) {
        when (char) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '"' -> out.append("&quot;")
            '\'' -> out.append("&#39;")
            else -> out.append(char)
        }
    }
}

/**
 * Makes a user-supplied title safe as one file name component. Path separators are replaced rather than
 * removed, control characters and Windows-reserved characters become underscores, and leading/trailing
 * dots and spaces are trimmed so `.`/`..` can never become a path component.
 */
internal fun safeExportFileName(raw: String, fallback: String = "chat", maxLength: Int = 96): String {
    val cleaned = raw.trim()
        .replace(UNSAFE_FILE_NAME, "_")
        .replace(WHITESPACE, " ")
        .trim(' ', '.')
        .ifBlank { fallback }
    val safe = cleaned.replace(DOT_RUN, "_").ifBlank { fallback }
    val limited = if (safe.length <= maxLength) safe else safe.takeCodeUnits(maxLength).trimEnd(' ', '.')
    return limited.ifBlank { fallback }
}

/** A code-unit prefix that never ends in the high half of a surrogate pair (invalid for ZIP/UTF-8 names). */
private fun String.takeCodeUnits(maxLength: Int): String {
    if (length <= maxLength) return this
    var end = maxLength.coerceAtLeast(0)
    if (end > 0 && Character.isHighSurrogate(this[end - 1])) end--
    return substring(0, end)
}

internal fun safeExportFileNameWithExtension(raw: String, fallback: String, extension: String): String {
    val safeExtension = extension.trimStart('.').ifBlank { "bin" }
    val base = safeExportFileName(raw, fallback, (96 - safeExtension.length - 1).coerceAtLeast(1))
    return "$base.$safeExtension"
}

/** A unique ZIP entry path: the message id prefix keeps files from different messages apart. */
internal fun mediaEntryName(messageId: Long, media: ChatExportMedia): String {
    val extension = mediaExtension(media)
    val rawBase = media.fileName.substringBeforeLast('.', media.fileName)
    val fallback = when (media.kind) {
        MediaKind.Photo -> "photo"
        MediaKind.Video -> "video"
        MediaKind.Animation -> "animation"
        MediaKind.Sticker -> "sticker"
        MediaKind.Audio -> "audio"
        MediaKind.Document -> "document"
        MediaKind.Voice -> "voice"
        MediaKind.VideoNote -> "video_note"
    }
    val base = safeExportFileName(rawBase, fallback, 80)
    return "media/${messageId}_$base.$extension"
}

/** Percent-encodes ZIP paths for HTML href/src while keeping the slash separators. */
internal fun exportMediaHref(entry: String): String =
    entry.split('/').joinToString("/") { segment -> URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20") }

internal fun mediaExtension(media: ChatExportMedia): String {
    val fromName = media.fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (SAFE_EXTENSION.matches(fromName)) return fromName
    return when (media.mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/ogg", "audio/opus", "audio/x-opus+ogg" -> "ogg"
        "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a"
        "audio/wav", "audio/x-wav" -> "wav"
        "application/pdf" -> "pdf"
        "application/zip" -> "zip"
        "application/x-tgsticker" -> "tgs"
        else -> when (media.kind) {
            MediaKind.Photo -> "jpg"
            MediaKind.Video, MediaKind.Animation, MediaKind.VideoNote -> "mp4"
            MediaKind.Sticker -> "webp"
            MediaKind.Audio -> "mp3"
            MediaKind.Voice -> "ogg"
            MediaKind.Document -> "bin"
        }
    }
}

internal fun exportIsoTimestamp(seconds: Long): String =
    if (seconds > 0L) Instant.ofEpochSecond(seconds).toString() else ""

internal fun formatExportTimestamp(seconds: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    if (seconds <= 0L) "" else EXPORT_TIME_FORMATTER.format(Instant.ofEpochSecond(seconds).atZone(zone))
