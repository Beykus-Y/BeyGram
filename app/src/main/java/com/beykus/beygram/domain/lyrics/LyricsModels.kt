package com.beykus.beygram.domain.lyrics

import com.beykus.beygram.data.music.provider.ProviderLyrics
import com.beykus.beygram.domain.music.TrackIdentity

enum class LyricsSyncMode {
    Plain,
    LineSynced,
    WordSynced,
}

enum class LyricsSourceId {
    YandexMusic,
    EmbeddedTags,
    LrcLib,
}

/**
 * Timing for a single word or syllable inside [RichLyricLine.text].
 * Indices are validated UTF-16 character offsets so surrogate pairs are never split.
 * Word timings must come from real source data and must never be synthesized uniformly.
 */
data class LyricWord(
    val startMs: Long,
    val endMs: Long,
    val startCharIndex: Int,
    val endCharIndexExclusive: Int,
    val isBackgroundVocal: Boolean = false,
) {
    companion object {
        fun validated(
            lineText: String,
            startMs: Long,
            endMs: Long,
            startCharIndex: Int,
            endCharIndexExclusive: Int,
            isBackgroundVocal: Boolean = false,
        ): LyricWord? {
            if (startMs < 0L || endMs < startMs) return null
            if (startCharIndex < 0 || endCharIndexExclusive <= startCharIndex) return null
            if (endCharIndexExclusive > lineText.length) return null
            if (Character.isLowSurrogate(lineText[startCharIndex])) return null
            if (endCharIndexExclusive < lineText.length && Character.isLowSurrogate(lineText[endCharIndexExclusive])) {
                return null
            }
            return LyricWord(
                startMs = startMs,
                endMs = endMs,
                startCharIndex = startCharIndex,
                endCharIndexExclusive = endCharIndexExclusive,
                isBackgroundVocal = isBackgroundVocal,
            )
        }
    }
}

data class RichLyricLine(
    val startMs: Long?,
    val endMs: Long? = null,
    val text: String,
    val isInstrumental: Boolean = false,
    val isBackgroundVocal: Boolean = false,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
    val romanization: String? = null,
)

data class LyricsAttribution(
    val sourceId: LyricsSourceId,
    val sourceDisplayName: String,
    val copyrightHolder: String? = null,
    val isExternalLookup: Boolean = false,
)

data class LyricsDocument(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val syncMode: LyricsSyncMode,
    val lines: List<RichLyricLine>,
    val plainText: String?,
    val attribution: LyricsAttribution,
    val isInstrumentalTrack: Boolean = false,
) {
    val hasWordTimings: Boolean
        get() = syncMode == LyricsSyncMode.WordSynced && lines.any { it.words.isNotEmpty() }

    val hasTranslations: Boolean
        get() = lines.any { !it.translation.isNullOrBlank() }

    val hasRomanization: Boolean
        get() = lines.any { !it.romanization.isNullOrBlank() }

    /**
     * Index of the active line at [positionMs], or -1 for plain lyrics or before the first line.
     */
    fun activeLineIndex(positionMs: Long): Int {
        if (syncMode == LyricsSyncMode.Plain || lines.isEmpty()) return -1
        val firstStart = lines.firstOrNull()?.startMs ?: return -1
        if (positionMs < firstStart) return -1
        var low = 0
        var high = lines.lastIndex
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            val midStart = lines[mid].startMs
            if (midStart != null && midStart <= positionMs) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        return low
    }

    /**
     * Projects timed lines to the legacy [SyncedLyrics] model for full backward compatibility.
     */
    fun toSyncedLyrics(): SyncedLyrics = SyncedLyrics(
        lines.mapNotNull { line ->
            line.startMs?.let { start -> LyricLine(startMs = start, text = line.text) }
        }
    )

    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

enum class LyricsUnavailableReasonV2 {
    NoText,
    NotSignedIn,
    ExternalLookupDisabled,
    RestrictedPrivacy,
    Failed,
}

sealed interface LyricsLoadState {
    object Loading : LyricsLoadState
    data class Ready(val document: LyricsDocument, val isUpgrading: Boolean = false) : LyricsLoadState
    data class Unavailable(val reason: LyricsUnavailableReasonV2) : LyricsLoadState
}

sealed interface LyricsFetchResult {
    data class Found(val document: LyricsDocument, val matchScore: Float = 1f) : LyricsFetchResult
    object NotFound : LyricsFetchResult
    object AuthRequired : LyricsFetchResult
    data class Unavailable(val retryable: Boolean) : LyricsFetchResult
}

interface LyricsSource {
    val id: LyricsSourceId
    val isExternalNetwork: Boolean
    fun supports(identity: TrackIdentity): Boolean
    suspend fun fetch(identity: TrackIdentity): LyricsFetchResult
}

/**
 * Lossless adapter from the existing Yandex [ProviderLyrics] model to [LyricsDocument].
 * Returns null if the provider lyrics contain neither timed lines nor non-blank plain text.
 */
fun ProviderLyrics.toLyricsDocument(sourceDisplayName: String = "Yandex Music"): LyricsDocument? {
    val attribution = LyricsAttribution(
        sourceId = LyricsSourceId.YandexMusic,
        sourceDisplayName = sourceDisplayName,
        copyrightHolder = copyrightHolder,
        isExternalLookup = false,
    )
    if (isSynced) {
        val richLines = lines
            .sortedBy { it.timeMs }
            .map { line ->
                RichLyricLine(
                    startMs = line.timeMs.coerceAtLeast(0L),
                    text = line.text,
                    isInstrumental = line.text.isBlank(),
                )
            }
        return LyricsDocument(
            syncMode = LyricsSyncMode.LineSynced,
            lines = richLines,
            plainText = plainText,
            attribution = attribution,
        )
    }
    val plain = plainText?.takeIf { it.isNotBlank() } ?: return null
    val plainLines = plain
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { RichLyricLine(startMs = null, text = it) }
        .toList()
    return LyricsDocument(
        syncMode = LyricsSyncMode.Plain,
        lines = plainLines,
        plainText = plain,
        attribution = attribution,
    )
}
