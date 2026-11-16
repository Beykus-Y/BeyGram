package com.beykus.beygram.media.bgwave

/**
 * One reading of the native session stats, as [BgWaveSessionNative.nativeStats] formats it:
 * "sent|received|lost|plc|jitterMs|bitrateBps|depthMs". Counters are whole numbers and jitter may be fractional; no addresses are carried.
 */
internal data class BgWaveStats(
    val sent: Long,
    val received: Long,
    val lost: Long,
    val plc: Long,
    val jitterMs: Double,
    val bitrateBps: Long,
    val depthMs: Long,
) {
    companion object {
        private const val FIELD_COUNT = 7

        /** Returns null for empty, short, long or non-numeric input. Never throws. */
        fun parse(raw: String): BgWaveStats? {
            if (raw.isEmpty()) return null
            val parts = raw.split('|')
            if (parts.size != FIELD_COUNT) return null
            // The native side may print a field as a floating-point number (std::to_string of a float gives "12.500000").
            val values = DoubleArray(FIELD_COUNT)
            for (i in 0 until FIELD_COUNT) {
                val value = parts[i].toDoubleOrNull() ?: return null
                if (!value.isFinite()) return null
                values[i] = value
            }
            return BgWaveStats(
                sent = values[0].toLong(),
                received = values[1].toLong(),
                lost = values[2].toLong(),
                plc = values[3].toLong(),
                jitterMs = values[4],
                bitrateBps = values[5].toLong(),
                depthMs = values[6].toLong(),
            )
        }
    }
}
