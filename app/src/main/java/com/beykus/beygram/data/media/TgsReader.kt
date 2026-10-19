package com.beykus.beygram.data.media

import java.io.File
import java.util.zip.GZIPInputStream

private const val GZIP_ID1 = 0x1f
private const val GZIP_ID2 = 0x8b

/** Lottie JSON анимированного стикера. TGS хранится как JSON, сжатый gzip; файл без gzip-заголовка читается как есть. */
internal fun readTgsJson(file: File): String {
    val bytes = file.readBytes()
    val gzipped = bytes.size >= 2 && (bytes[0].toInt() and 0xFF) == GZIP_ID1 && (bytes[1].toInt() and 0xFF) == GZIP_ID2
    if (!gzipped) return bytes.toString(Charsets.UTF_8)
    return GZIPInputStream(bytes.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
}
