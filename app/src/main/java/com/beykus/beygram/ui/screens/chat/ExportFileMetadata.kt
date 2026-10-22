package com.beykus.beygram.ui.screens.chat

import java.io.File

/** Metadata written to MediaStore. Original Telegram filenames win over generic MIME guesses. */
internal data class ExportFileMetadata(val displayName: String, val mimeType: String)


/**
 * A conservative container signature, sampled from at most 16 bytes. This is evidence for
 * export metadata only; it neither validates the rest of the file nor changes its bytes.
 */
internal data class ExportSignatureHint(val extension: String, val mimeType: String)

internal fun detectExportSignature(header: ByteArray): ExportSignatureHint? {
    fun starts(vararg bytes: Int): Boolean = header.size >= bytes.size &&
        bytes.indices.all { index -> (header[index].toInt() and 0xff) == bytes[index] }
    fun ascii(prefix: String, offset: Int = 0): Boolean =
        header.size >= offset + prefix.length &&
            prefix.indices.all { header[offset + it].toInt() == prefix[it].code }
    return when {
        starts(0x25, 0x50, 0x44, 0x46, 0x2d) -> ExportSignatureHint("pdf", "application/pdf")
        starts(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) ->
            ExportSignatureHint("png", "image/png")
        starts(0xff, 0xd8, 0xff) -> ExportSignatureHint("jpg", "image/jpeg")
        ascii("GIF87a") || ascii("GIF89a") -> ExportSignatureHint("gif", "image/gif")
        ascii("RIFF") && ascii("WEBP", 8) -> ExportSignatureHint("webp", "image/webp")
        // ZIP is deliberately not identified: APK, DOCX and EPUB share its prefix, so the bytes alone cannot name the type.
        ascii("OggS") -> ExportSignatureHint("ogg", "application/ogg")
        ascii("fLaC") -> ExportSignatureHint("flac", "audio/flac")
        else -> null
    }
}

/** Only a small fixed prefix is read; no full-file allocation or speculative content scan. */
internal fun readExportSignature(file: File): ExportSignatureHint? = runCatching {
    if (!file.isFile) return@runCatching null
    val bytes = ByteArray(16)
    val count = file.inputStream().use { it.read(bytes) }
    if (count <= 0) null else detectExportSignature(bytes.copyOf(count))
}.getOrNull()

/**
 * Determine a safe exported name and MIME without using TDLib's private cache path.
 * In particular application/octet-stream must never append .bin to a known filename.
 */
internal fun resolveExportFileMetadata(
    originalName: String,
    declaredMimeType: String,
    fallbackName: String,
    extensionForMime: (String) -> String?,
    mimeForExtension: (String) -> String?,
    signatureHint: ExportSignatureHint? = null
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
    val actualMime = if (declaredMime == "application/octet-stream") {
        when {
            originalExtension != null -> mimeForExtension(originalExtension)?.takeIf { it.isNotBlank() } ?: declaredMime
            signatureHint != null -> signatureHint.mimeType
            else -> declaredMime
        }
    } else declaredMime

    // Only infer a suffix for extensionless files with a known, non-generic MIME.
    // Never replace or append a suffix to a filename that already has one.
    val inferredExtension = if (!hasExtension && actualMime != "application/octet-stream") {
        (if (declaredMime == "application/octet-stream") signatureHint?.extension else null)
            ?: extensionForMime(actualMime)?.trimStart('.')?.takeIf {
                it.isNotBlank() && !it.equals("bin", ignoreCase = true) &&
                    it.all { char -> char.isLetterOrDigit() }
            }
    } else null

    return ExportFileMetadata(
        displayName = if (inferredExtension == null) name else name + "." + inferredExtension,
        mimeType = actualMime
    )
}
