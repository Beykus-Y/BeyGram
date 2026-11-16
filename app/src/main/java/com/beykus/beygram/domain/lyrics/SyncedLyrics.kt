package com.beykus.beygram.domain.lyrics

/** One line of a synchronized lyrics text: the moment it starts and its words. */
data class LyricLine(val startMs: Long, val text: String)

/**
 * Synchronized lyrics sorted by start time. Source-agnostic: any provider that
 * returns LRC (or a list of timed lines) can feed it.
 */
class SyncedLyrics(lines: List<LyricLine>) {
    val lines: List<LyricLine> = lines.sortedBy { it.startMs }

    val isEmpty: Boolean get() = lines.isEmpty()

    /**
     * Index of the line that should be highlighted at [positionMs]: the last line
     * whose start is not after the position. Returns -1 before the first line.
     */
    fun activeIndex(positionMs: Long): Int {
        if (lines.isEmpty() || positionMs < lines[0].startMs) return -1
        var low = 0
        var high = lines.lastIndex
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (lines[mid].startMs <= positionMs) low = mid else high = mid - 1
        }
        return low
    }
}

/**
 * Parser for LRC text, the format providers use for synchronized lyrics.
 *
 * Supported: `[mm:ss]`, `[mm:ss.x]`, `[mm:ss.xx]`, `[mm:ss.xxx]`, several timestamps
 * on one line, and the global `[offset:±ms]` tag. Other tags (`[ti:]`, `[ar:]`, …)
 * are ignored. Malformed lines are skipped, never thrown.
 */
object LrcParser {
    /** Larger input is rejected: real lyrics are a few kilobytes. */
    const val MAX_INPUT_CHARS = 256 * 1024

    private val timestamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""^\[offset:\s*([+-]?\d{1,7})]$""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): SyncedLyrics {
        if (raw.isBlank() || raw.length > MAX_INPUT_CHARS) return SyncedLyrics(emptyList())
        var offsetMs = 0L
        val pending = mutableListOf<Pair<Long, String>>()
        for (rawLine in raw.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            offsetTag.find(line)?.let { match ->
                offsetMs = match.groupValues[1].toLongOrNull() ?: 0L
                return@let
            }
            val matches = timestamp.findAll(line).toList()
            if (matches.isEmpty()) continue
            val text = line.substring(matches.last().range.last + 1).trim()
            for (match in matches) {
                val startMs = toMillis(match) ?: continue
                pending += startMs to text
            }
        }
        val lines = pending.map { (startMs, text) ->
            // LRC convention: a positive offset makes lyrics appear earlier.
            LyricLine(startMs = (startMs - offsetMs).coerceAtLeast(0L), text = text)
        }
        return SyncedLyrics(lines)
    }

    private fun toMillis(match: MatchResult): Long? {
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        if (seconds >= 60) return null
        val fraction = match.groupValues[3]
        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.toLong()
        }
        return minutes * 60_000L + seconds * 1_000L + fractionMs
    }
}
