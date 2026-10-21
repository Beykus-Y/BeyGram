package com.beykus.beygram.ui.screens.chat

/** Metadata written to MediaStore. Original Telegram filenames win over generic MIME guesses. */
internal data class ExportFileMetadata(val displayName: String, val mimeType: String)

/**
 * Determine a safe exported name and MIME without using TDLib's private cache path.
 * In particular application/octet-stream must never append .bin to a known filename.
 */
internal fun resolveExportFileMetadata(
    originalName: String,
    declaredMimeType: String,
    fallbackName: String,
    extensionForMime: (String) -> String?,
    mimeForExtension: (String) -> String?
): ExportFileMetadata {
    fun cleanName(raw: String): String = raw.substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("[\\u0000-\\u001F\\u007F]"), "_")
        .trim().trimEnd('.', ' ')

    val name = cleanName(originalName).ifBlank { cleanName(fallbackName).ifBlank { "file" } }
    val lastDot = name.lastIndexOf('.')
    val hasExtension = lastDot > 0 && lastDot < name.lastIndex
    val originalExtension = if (hasExtension) name.substring(lastDot + 1).lowercase() else null
    val declaredMime = declaredMimeType.substringBefore(';').trim().lowercase()
        .ifBlank { "application/octet-stream" }
    val actualMime = if (declaredMime == "application/octet-stream" && originalExtension != null) {
        mimeForExtension(originalExtension)?.takeIf { it.isNotBlank() } ?: declaredMime
    } else declaredMime

    // Only infer a suffix for extensionless files with a known, non-generic MIME.
    // Never replace or append a suffix to a filename that already has one.
    val inferredExtension = if (!hasExtension && actualMime != "application/octet-stream") {
        extensionForMime(actualMime)?.trimStart('.')?.takeIf {
            it.isNotBlank() && !it.equals("bin", ignoreCase = true) &&
                it.all { char -> char.isLetterOrDigit() }
        }
    } else null

    return ExportFileMetadata(
        displayName = if (inferredExtension == null) name else name + "." + inferredExtension,
        mimeType = actualMime
    )
}
