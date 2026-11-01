package com.beykus.beygram.data.music.bgmusic

/**
 * Constants and shared rules of the `.bgmusic` v1 format. The rules are documented in docs/formats/bgmusic-v1.md.
 * The writer and the reader use the same functions here, so a written file always reads back unchanged.
 */
object BgMusicFormat {
    const val MIME_TYPE = "application/vnd.beygram.music+zip"
    const val FILE_EXTENSION = "bgmusic"

    /** Value of the manifest `format` field. It identifies the format; it is not a cryptographic signature. */
    const val SIGNATURE = "beygram.music"

    /** Format version written by this build. */
    const val FORMAT_VERSION = 1

    /** Highest `min_reader_version` this reader accepts. Files that need a newer reader are reported as Unsupported. */
    const val READER_VERSION = 1

    const val MANIFEST_ENTRY = "manifest.json"
    const val COVER_WEBP_ENTRY = "cover.webp"
    const val COVER_JPEG_ENTRY = "cover.jpg"
    const val COVER_PNG_ENTRY = "cover.png"

    /** Value of the source hint `type` field for public Telegram posts. */
    const val TYPE_TELEGRAM_PUBLIC = "telegram_public"

    // Size limits. Sizes are bytes, measured after decompression for entries and before it for the file.
    const val MAX_ARCHIVE_BYTES: Long = 4L * 1024 * 1024
    const val MAX_MANIFEST_BYTES: Long = 2L * 1024 * 1024
    const val MAX_COVER_BYTES: Long = 1L * 1024 * 1024
    const val MAX_TOTAL_UNCOMPRESSED_BYTES: Long = 4L * 1024 * 1024
    const val MAX_ENTRIES = 8
    const val MAX_ENTRY_NAME_LENGTH = 100
    const val MAX_TRACKS = 5000
    const val MAX_SOURCES_PER_TRACK = 8
    const val MAX_NESTING_DEPTH = 16

    // String caps, in Unicode code points.
    const val MAX_PLAYLIST_TITLE = 128
    const val MAX_DESCRIPTION = 1000
    const val MAX_TRACK_TITLE = 256
    const val MAX_ARTIST = 256
    const val MAX_ALBUM = 256
    const val MAX_AUTHOR_NAME = 128

    const val MAX_DURATION_SEC: Long = 604_800L
    /** 9999-12-31T23:59:59.999Z, the last instant that is still a sensible timestamp. */
    const val MAX_UPDATED_AT_MS: Long = 253_402_300_799_999L
    const val MAX_TELEGRAM_MESSAGE_ID: Long = 2_147_483_647L

    private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val TELEGRAM_USERNAME_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]{4,31}")
    private val AUDIO_HASH_PATTERN = Regex("sha256:[0-9a-f]{64}")
    private val ENTRY_NAME_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

    /** Suffixes of archives that must never appear as entries, even as "unknown" entries. */
    private val NESTED_ARCHIVE_SUFFIXES = listOf(
        ".zip", ".bgmusic", ".jar", ".apk", ".7z", ".rar", ".tar", ".gz", ".tgz", ".bz2", ".xz",
    )

    /**
     * Makes single-line text safe to store, such as titles, artists, albums and names. Line breaks (CR, LF, VT, FF,
     * NEL, U+2028, U+2029) and tabs become one space each, so words are not glued together. Other control
     * characters, bidi embedding, override and isolate characters, invisible marks, and unpaired surrogates are removed.
     * The text is
     * trimmed and capped to [maxCodePoints] code points; cutting never splits a surrogate pair. The result can be empty.
     */
    internal fun sanitizeText(raw: String, maxCodePoints: Int): String =
        sanitize(raw, maxCodePoints, keepLineBreaks = false)

    /**
     * Makes free text, the playlist description, safe to store and keeps its paragraphs. Every line break becomes LF,
     * and a run of more than two LF in a row is cut to two. Tabs become spaces. The other rules of [sanitizeText]
     * apply, including the trim and the cap.
     */
    internal fun sanitizeMultilineText(raw: String, maxCodePoints: Int): String =
        sanitize(raw, maxCodePoints, keepLineBreaks = true)

    private const val TAB = 0x09

    private fun sanitize(raw: String, maxCodePoints: Int, keepLineBreaks: Boolean): String {
        // CRLF is one line break. Joining it first stops the CR and the LF from counting as two.
        val text = raw.replace("\r\n", "\n")
        val kept = StringBuilder(text.length)
        var lineBreaksInRow = 0
        var index = 0
        while (index < text.length) {
            val codePoint = Character.codePointAt(text, index)
            index += Character.charCount(codePoint)
            when {
                keepLineBreaks && isLineBreak(codePoint) -> {
                    if (lineBreaksInRow < 2) {
                        kept.append('\n')
                        lineBreaksInRow++
                    }
                }
                isLineBreak(codePoint) || codePoint == TAB -> {
                    kept.append(' ')
                    lineBreaksInRow = 0
                }
                !isDroppedCodePoint(codePoint) -> {
                    kept.appendCodePoint(codePoint)
                    lineBreaksInRow = 0
                }
            }
        }
        // Trim, cap, then trim again: cutting can leave a line break or a space at the end.
        val trimmed = kept.toString().trim()
        val end = endIndexForCodePoints(trimmed, maxCodePoints)
        return trimmed.substring(0, end).trimEnd()
    }

    /** LF, VT, FF, CR, NEL, LINE SEPARATOR and PARAGRAPH SEPARATOR. */
    private fun isLineBreak(codePoint: Int): Boolean =
        codePoint == 0x0A || codePoint == 0x0B || codePoint == 0x0C || codePoint == 0x0D ||
            codePoint == 0x85 || codePoint == 0x2028 || codePoint == 0x2029

    /** Controls, bidi embedding, override and isolate characters, invisible marks, and unpaired surrogates. */
    private fun isDroppedCodePoint(codePoint: Int): Boolean =
        codePoint < 0x20 ||
            codePoint in 0x7F..0x9F ||
            codePoint in 0xD800..0xDFFF ||
            codePoint in 0x202A..0x202E ||
            codePoint in 0x2066..0x2069 ||
            codePoint == 0x061C || // ARABIC LETTER MARK
            codePoint == 0x200B || // ZERO WIDTH SPACE
            codePoint in 0x200E..0x200F || // LEFT-TO-RIGHT and RIGHT-TO-LEFT MARK
            codePoint == 0xFEFF // ZERO WIDTH NO-BREAK SPACE (byte order mark)

    private fun endIndexForCodePoints(text: String, maxCodePoints: Int): Int {
        var index = 0
        var count = 0
        while (index < text.length && count < maxCodePoints) {
            index += Character.charCount(Character.codePointAt(text, index))
            count++
        }
        return index
    }

    /** Returns the canonical lowercase form of a UUID, or null when [raw] is not in 8-4-4-4-12 hex form. */
    internal fun normalizeUuid(raw: String): String? =
        if (UUID_PATTERN.matches(raw)) raw.lowercase() else null

    /** Accepts "name" or "@name" and returns the bare public username, or null when it is not a valid one. */
    internal fun normalizeTelegramUsername(raw: String): String? {
        val name = raw.trim().removePrefix("@")
        return if (TELEGRAM_USERNAME_PATTERN.matches(name)) name else null
    }

    /** Returns [raw] when it is a `sha256:` hash in lowercase hex, otherwise null. */
    internal fun normalizeAudioHash(raw: String): String? =
        if (AUDIO_HASH_PATTERN.matches(raw)) raw else null

    /**
     * True for entry names that stay in the archive root: ASCII letters, digits, `.`, `_` and `-`, starting with
     * a letter or digit, no "..", and at most [MAX_ENTRY_NAME_LENGTH] characters. This rejects separators
     * (`/`, `\`), drive letters (`:`), absolute paths, traversal and directory names.
     */
    internal fun isSafeEntryName(name: String): Boolean =
        name.length in 1..MAX_ENTRY_NAME_LENGTH && !name.contains("..") && ENTRY_NAME_PATTERN.matches(name)

    internal fun hasNestedArchiveSuffix(name: String): Boolean {
        val lower = name.lowercase()
        return NESTED_ARCHIVE_SUFFIXES.any { lower.endsWith(it) }
    }

    internal fun isCoverEntryName(name: String): Boolean =
        name == COVER_WEBP_ENTRY || name == COVER_JPEG_ENTRY || name == COVER_PNG_ENTRY

    /** The cover entry name that matches the image's magic bytes, or null when the bytes are not WebP, JPEG or PNG. */
    internal fun coverEntryNameFor(bytes: ByteArray): String? = when {
        hasBytesAt(bytes, 0, 0x52, 0x49, 0x46, 0x46) && hasBytesAt(bytes, 8, 0x57, 0x45, 0x42, 0x50) -> COVER_WEBP_ENTRY
        hasBytesAt(bytes, 0, 0xFF, 0xD8, 0xFF) -> COVER_JPEG_ENTRY
        hasBytesAt(bytes, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> COVER_PNG_ENTRY
        else -> null
    }

    /** True when the start of [head] looks like an archive or compressed container. Used to reject nested archives. */
    internal fun looksLikeArchive(head: ByteArray): Boolean =
        hasBytesAt(head, 0, 0x50, 0x4B, 0x03, 0x04) ||
            hasBytesAt(head, 0, 0x50, 0x4B, 0x05, 0x06) ||
            hasBytesAt(head, 0, 0x50, 0x4B, 0x07, 0x08) ||
            hasBytesAt(head, 0, 0x1F, 0x8B) ||
            hasBytesAt(head, 0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) ||
            hasBytesAt(head, 0, 0x52, 0x61, 0x72, 0x21) ||
            hasBytesAt(head, 0, 0x42, 0x5A, 0x68) ||
            hasBytesAt(head, 0, 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00)

    /** Checks an exact byte pattern at [offset]. Out-of-range positions simply do not match. */
    internal fun hasBytesAt(bytes: ByteArray, offset: Int, vararg expected: Int): Boolean {
        if (offset < 0 || offset + expected.size > bytes.size) return false
        for (i in expected.indices) {
            if ((bytes[offset + i].toInt() and 0xFF) != expected[i]) return false
        }
        return true
    }
}
