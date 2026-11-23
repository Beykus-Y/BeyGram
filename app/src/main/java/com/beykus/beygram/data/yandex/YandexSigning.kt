package com.beykus.beygram.data.yandex

import com.beykus.beygram.data.music.provider.LyricLine
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Pure helpers for the legacy download link and lyric files. No network, no Android types. */
internal object YandexSigning {

    /** Parsed fields of a download-info XML document. */
    data class DownloadInfo(val host: String, val path: String, val ts: String, val s: String)

    /** Direct MP3 link built from download-info fields: MD5(salt + path without the leading slash + s). */
    fun directLink(info: DownloadInfo): String {
        val sign = md5Hex(YandexConfig.DOWNLOAD_SIGN_SALT + info.path.removePrefix("/") + info.s)
        return "https://${info.host}/get-mp3/$sign/${info.ts}${info.path}"
    }

    /**
     * Reads the four fields from the XML. Documents with a DOCTYPE or ENTITY declaration are rejected outright,
     * so no external or expanded entities are ever resolved.
     */
    fun parseDownloadInfo(xml: String): DownloadInfo? {
        if (xml.contains("<!DOCTYPE", ignoreCase = true) || xml.contains("<!ENTITY", ignoreCase = true)) return null
        val host = field(xml, "host") ?: return null
        val path = field(xml, "path") ?: return null
        val ts = field(xml, "ts") ?: return null
        val s = field(xml, "s") ?: return null
        return DownloadInfo(host, path, ts, s)
    }

    private fun field(xml: String, name: String): String? {
        val match = Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(xml) ?: return null
        return match.groupValues[1].removePrefix("<![CDATA[").removeSuffix("]]>").trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Request signature of the lyrics endpoint: Base64(HMAC-SHA256(key, trackId + timeStamp)), UTF-8 bytes, standard
     * Base64 with padding. The result still has to be URL-encoded in the query. Pure: the key is a parameter.
     */
    fun lyricsSign(trackId: String, timeStamp: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal((trackId + timeStamp).toByteArray(Charsets.UTF_8)))
    }

    /**
     * Parses an LRC document. Lines may carry several time tags. Metadata tags such as [ar:...] are skipped.
     * Result is sorted by time and contains only lines with text. A byte order mark at the start is ignored.
     */
    fun parseLrc(text: String): List<LyricLine> {
        val tag = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
        val lines = mutableListOf<LyricLine>()
        for (raw in text.removePrefix(BOM).lineSequence()) {
            val matches = tag.findAll(raw).toList()
            if (matches.isEmpty()) continue
            val content = raw.substring(matches.last().range.last + 1).trim()
            if (content.isEmpty()) continue
            for (m in matches) {
                val minutes = m.groupValues[1].toLong()
                val seconds = m.groupValues[2].toLong()
                val fraction = m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                lines += LyricLine(timeMs = minutes * 60_000L + seconds * 1_000L + fraction, text = content)
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /**
     * Plain lyrics text: the whole body without a byte order mark, every line trimmed, runs of blank lines collapsed.
     * Null when nothing is left.
     */
    fun plainLyricsText(text: String): String? =
        text.replace(BOM, "").lineSequence().map { it.trim() }.joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
            .takeIf { it.isNotEmpty() }

    private const val BOM = "\uFEFF"

    fun md5Hex(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
