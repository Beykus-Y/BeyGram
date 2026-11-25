package com.beykus.beygram.domain.lyrics

/**
 * Parses standard LRC and Enhanced LRC (`<mm:ss.xx>word`) into normalized [RichLyricLine] entries.
 * Never synthesizes artificial word timings: [LyricWord] entries are emitted only when explicit
 * inline word timestamps are present and pass [LyricWord.validated].
 */
object EnhancedLrcParser {

    const val MAX_INPUT_CHARS = LrcParser.MAX_INPUT_CHARS

    private val lineTimestamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val wordTimestamp = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val offsetTag = Regex("""^\[offset:\s*([+-]?\d{1,7})]$""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): Pair<LyricsSyncMode, List<RichLyricLine>> {
        if (raw.isBlank() || raw.length > MAX_INPUT_CHARS) {
            return LyricsSyncMode.Plain to emptyList()
        }
        var offsetMs = 0L
        val pending = mutableListOf<Pair<Long, String>>()
        for (rawLine in raw.removePrefix("\uFEFF").lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            offsetTag.find(line)?.let { match ->
                offsetMs = match.groupValues[1].toLongOrNull() ?: 0L
                return@let
            }
            val matches = lineTimestamp.findAll(line).toList()
            if (matches.isEmpty()) continue
            val content = line.substring(matches.last().range.last + 1).trim()
            for (match in matches) {
                val startMs = toMillis(match) ?: continue
                pending += startMs to content
            }
        }
        if (pending.isEmpty()) return LyricsSyncMode.Plain to emptyList()

        var anyWordTimings = false
        val parsedLines = pending.map { (rawStartMs, rawContent) ->
            val startMs = (rawStartMs - offsetMs).coerceAtLeast(0L)
            val (cleanText, words) = parseWords(rawContent, offsetMs, startMs)
            if (words.isNotEmpty()) anyWordTimings = true
            RichLyricLine(
                startMs = startMs,
                endMs = words.lastOrNull()?.endMs,
                text = cleanText,
                isInstrumental = cleanText.isBlank(),
                words = words,
            )
        }.sortedBy { it.startMs ?: 0L }

        val mode = if (anyWordTimings) LyricsSyncMode.WordSynced else LyricsSyncMode.LineSynced
        return mode to parsedLines
    }

    private fun parseWords(
        content: String,
        offsetMs: Long,
        lineStartMs: Long,
    ): Pair<String, List<LyricWord>> {
        val tags = wordTimestamp.findAll(content).toList()
        if (tags.isEmpty()) return content to emptyList()

        val textBuilder = StringBuilder(content.length)
        val rawSegments = mutableListOf<Triple<Long, Int, Int>>()
        var cursor = 0
        for (i in tags.indices) {
            val tag = tags[i]
            if (tag.range.first > cursor && i == 0) {
                // Prefix text before the first inline word timestamp
                textBuilder.append(content, cursor, tag.range.first)
            }
            val segStartIndex = textBuilder.length
            val nextTagStart = if (i + 1 < tags.size) tags[i + 1].range.first else content.length
            val segmentText = content.substring(tag.range.last + 1, nextTagStart)
            textBuilder.append(segmentText)
            val segEndIndex = textBuilder.length
            val wordStartMs = toMillis(tag)?.let { (it - offsetMs).coerceAtLeast(0L) }
            if (wordStartMs != null && segmentText.isNotBlank() && segEndIndex > segStartIndex) {
                rawSegments += Triple(wordStartMs, segStartIndex, segEndIndex)
            }
            cursor = nextTagStart
        }

        val cleanText = textBuilder.toString().trim()
        if (cleanText.isEmpty() || rawSegments.isEmpty()) return cleanText to emptyList()

        val leadingTrim = textBuilder.length - textBuilder.toString().trimStart().length
        val words = mutableListOf<LyricWord>()
        for (i in rawSegments.indices) {
            val (startMs, rawStartIdx, rawEndIdx) = rawSegments[i]
            var startIdx = (rawStartIdx - leadingTrim).coerceAtLeast(0)
            var endIdx = (rawEndIdx - leadingTrim).coerceAtMost(cleanText.length)
            while (startIdx < endIdx && cleanText[startIdx].isWhitespace()) startIdx++
            while (endIdx > startIdx && cleanText[endIdx - 1].isWhitespace()) endIdx--
            if (startIdx >= endIdx) continue
            val nextStartMs = rawSegments.getOrNull(i + 1)?.first ?: maxOf(startMs, lineStartMs)
            val endMs = maxOf(startMs, nextStartMs)
            LyricWord.validated(
                lineText = cleanText,
                startMs = startMs,
                endMs = endMs,
                startCharIndex = startIdx,
                endCharIndexExclusive = endIdx,
            )?.let(words::add)
        }
        return cleanText to words
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
