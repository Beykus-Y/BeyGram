package com.beykus.beygram.domain.lyrics

import com.beykus.beygram.domain.music.ExternalQueryMetadata
import java.text.Normalizer
import kotlin.math.abs

/**
 * Matches and ranks external lyrics candidates against sanitized track metadata.
 * Rejects mismatched durations, conflicting versions (live, remix, cover, etc.), and weak matches.
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
        val toleranceMs = maxOf(2_000L, (queryDurationMs * 0.02).toLong())
        return abs(queryDurationMs - candidateDurationMs) <= toleranceMs
    }

    private fun isTokenCompatible(normalizedQuery: String, normalizedCandidate: String): Boolean {
        if (normalizedQuery == normalizedCandidate) return true
        if (normalizedQuery.length < 4 || normalizedCandidate.length < 4) return false
        val qTokens = normalizedQuery.split(' ').filter { it.isNotEmpty() }.toSet()
        val cTokens = normalizedCandidate.split(' ').filter { it.isNotEmpty() }.toSet()
        if (qTokens.isEmpty() || cTokens.isEmpty()) return false
        val overlap = qTokens.intersect(cTokens).size.toFloat() / maxOf(qTokens.size, cTokens.size).toFloat()
        return overlap >= 0.8f
    }

    /**
     * Scores a candidate from 0..1, or returns null when the candidate is incompatible
     * (title mismatch, artist mismatch, conflicting version tag, or duration outside tolerance).
     */
    fun scoreCandidate(
        query: ExternalQueryMetadata,
        candidateTitle: String,
        candidateArtist: String,
        candidateDurationMs: Long,
        syncMode: LyricsSyncMode,
        candidateAlbum: String? = null,
    ): Float? {
        val qTitle = normalize(query.title)
        val cTitle = normalize(candidateTitle)
        if (qTitle.isEmpty() || cTitle.isEmpty()) return null
        if (hasConflictingVersion(query.title, candidateTitle)) return null

        val qArtist = normalize(query.artist)
        val cArtist = normalize(candidateArtist)
        if (qArtist.isNotEmpty()) {
            if (cArtist.isEmpty() || !isTokenCompatible(qArtist, cArtist)) return null
            if (!isTokenCompatible(qTitle, cTitle)) return null
        } else {
            // Without an artist name, require an exact normalized title match to avoid false positives.
            if (qTitle != cTitle) return null
        }

        val queryDurationMs = (query.durationSeconds ?: 0).coerceAtLeast(0) * 1_000L
        if (!isDurationCompatible(queryDurationMs, candidateDurationMs)) return null

        var score = when (syncMode) {
            LyricsSyncMode.WordSynced -> 0.50f
            LyricsSyncMode.LineSynced -> 0.40f
            LyricsSyncMode.Plain -> 0.20f
        }
        score += if (qTitle == cTitle) 0.25f else 0.14f
        score += when {
            qArtist.isEmpty() -> 0.05f
            qArtist == cArtist -> 0.15f
            else -> 0.09f
        }
        val qAlbum = query.album?.let(::normalize).orEmpty()
        val cAlbum = candidateAlbum?.let(::normalize).orEmpty()
        if (qAlbum.isNotEmpty() && cAlbum.isNotEmpty()) {
            score += if (isTokenCompatible(qAlbum, cAlbum)) 0.05f else -0.05f
        }
        if (queryDurationMs > 0L && candidateDurationMs > 0L) {
            val diffMs = abs(queryDurationMs - candidateDurationMs)
            score += if (diffMs <= 1_000L) 0.05f else 0.02f
        }
        return score.coerceIn(0f, 1f)
    }
}
