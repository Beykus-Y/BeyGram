package com.beykus.beygram.domain.lyrics

import com.beykus.beygram.domain.music.ExternalQueryMetadata
import java.text.Normalizer
import kotlin.math.abs

/**
 * Matches and ranks external lyrics candidates against sanitized track metadata.
 * Rejects mismatched durations and conflicting versions (live, remix, cover, etc.).
 */
object LyricsMatcher {

    private val VERSION_TAGS = listOf(
        "live",
        "remix",
        "cover",
        "instrumental",
        "karaoke",
        "acoustic",
        "sped up",
        "slowed",
    )

    private val COMBINING_MARKS = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val BRACKETED_SEGMENT = Regex("[\\(\\[\\{]([^\\)\\]\\}]+)[\\)\\]\\}]")
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}\\s]")
    private val MULTI_SPACE = Regex("\\s+")

    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFKD)
        val noMarks = COMBINING_MARKS.replace(decomposed, "")
        val noBrackets = BRACKETED_SEGMENT.replace(noMarks, " ")
        val clean = NON_ALNUM.replace(noBrackets, " ")
        return MULTI_SPACE.replace(clean, " ").trim()
    }

    fun detectVersionTags(text: String): Set<String> {
        val lowered = text.lowercase()
        return VERSION_TAGS.filterTo(LinkedHashSet()) { tag ->
            Regex("(?<![\\p{L}\\p{N}])${Regex.escape(tag)}(?![\\p{L}\\p{N}])").containsMatchIn(lowered)
        }
    }

    fun hasConflictingVersion(queryTitle: String, candidateTitle: String): Boolean {
        val queryTags = detectVersionTags(queryTitle)
        val candidateTags = detectVersionTags(candidateTitle)
        return queryTags != candidateTags
    }

    fun isDurationCompatible(queryDurationMs: Long, candidateDurationMs: Long): Boolean {
        if (queryDurationMs <= 0L || candidateDurationMs <= 0L) return true
        val toleranceMs = maxOf(3_000L, (queryDurationMs * 0.03).toLong())
        return abs(queryDurationMs - candidateDurationMs) <= toleranceMs
    }

    /**
     * Scores a candidate from 0..1, or returns null when the candidate is incompatible
     * (title mismatch, conflicting version tag, or duration outside tolerance).
     */
    fun scoreCandidate(
        query: ExternalQueryMetadata,
        candidateTitle: String,
        candidateArtist: String,
        candidateDurationMs: Long,
        syncMode: LyricsSyncMode,
    ): Float? {
        val qTitle = normalize(query.title)
        val cTitle = normalize(candidateTitle)
        if (qTitle.isEmpty() || cTitle.isEmpty()) return null
        if (qTitle != cTitle && !cTitle.contains(qTitle) && !qTitle.contains(cTitle)) return null
        if (hasConflictingVersion(query.title, candidateTitle)) return null

        val queryDurationMs = (query.durationSeconds ?: 0).coerceAtLeast(0) * 1_000L
        if (!isDurationCompatible(queryDurationMs, candidateDurationMs)) return null

        val qArtist = normalize(query.artist)
        val cArtist = normalize(candidateArtist)
        if (qArtist.isNotEmpty() && cArtist.isNotEmpty()) {
            val artistMatches = qArtist == cArtist || cArtist.contains(qArtist) || qArtist.contains(cArtist)
            if (!artistMatches) return null
        }

        var score = when (syncMode) {
            LyricsSyncMode.WordSynced -> 0.55f
            LyricsSyncMode.LineSynced -> 0.45f
            LyricsSyncMode.Plain -> 0.25f
        }
        score += if (qTitle == cTitle) 0.25f else 0.12f
        score += when {
            qArtist.isEmpty() || cArtist.isEmpty() -> 0.05f
            qArtist == cArtist -> 0.15f
            else -> 0.08f
        }
        if (queryDurationMs > 0L && candidateDurationMs > 0L) {
            val diffMs = abs(queryDurationMs - candidateDurationMs)
            score += if (diffMs <= 1_000L) 0.05f else 0.02f
        }
        return score.coerceIn(0f, 1f)
    }
}
