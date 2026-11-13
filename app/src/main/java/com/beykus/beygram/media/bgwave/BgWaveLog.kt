package com.beykus.beygram.media.bgwave

import android.util.Log

/**
 * One log tag for the whole BG Wave path. Read it on a phone with `adb logcat -s BgWave`.
 *
 * Never logs URIs, Telegram ids, message text, IP addresses, ICE or DTLS values. Session ids are cut to
 * 8 hex chars, which is enough to match the two phones' logs without exposing the full id. Logging never throws.
 */
internal object BgWaveLog {
    const val TAG = "BgWave"

    fun i(event: String, vararg fields: Pair<String, Any?>) = write(Log.INFO, null, event, fields)

    fun w(event: String, error: Throwable? = null, vararg fields: Pair<String, Any?>) =
        write(Log.WARN, error, event, fields)

    /** First 8 hex chars of a session id, or "-" when there is none. */
    fun sid(sidHex: String?): String = sidHex?.take(SID_LOG_CHARS).orEmpty().ifEmpty { "-" }

    private fun write(level: Int, error: Throwable?, event: String, fields: Array<out Pair<String, Any?>>) {
        try {
            val line = buildString {
                append(event)
                for ((key, value) in fields) append(' ').append(key).append('=').append(value ?: "-")
                if (error != null) append(" err=").append(error.javaClass.simpleName)
            }
            Log.println(level, TAG, line)
        } catch (_: Throwable) {
            // Logging must never break the wave, including in JVM unit tests where android.util.Log is stubbed.
        }
    }

    private const val SID_LOG_CHARS = 8
}
