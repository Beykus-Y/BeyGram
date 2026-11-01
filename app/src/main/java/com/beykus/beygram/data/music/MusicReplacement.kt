package com.beykus.beygram.data.music

import kotlin.math.abs

/** Largest difference in length, in seconds, between a track and a replacement that still counts as the same recording. */
const val MUSIC_REPLACEMENT_MAX_DURATION_DELTA_SECONDS = 3

/** Most candidates offered for one replacement search. Each one is checked against Telegram before it is shown. */
const val MUSIC_REPLACEMENT_MAX_CANDIDATES = 40

/**
 * Candidates checked against Telegram at a time. The playable ones are shown after each batch, so the list fills in while the
 * rest are still being checked. Each batch runs at most three lookups at once (see resolveMusicOrigins).
 */
const val MUSIC_REPLACEMENT_LOOKUP_BATCH = 8

private val NON_LETTER_OR_DIGIT = Regex("[^\\p{L}\\p{N}]+")

/**
 * The form in which titles and performers are compared: lowercase, "ё" written as "е", and every run of other characters
 * (punctuation, symbols, spaces) collapsed into one space.
 */
fun normalizeMusicMatchText(raw: String): String =
    raw.lowercase().replace('ё', 'е').replace(NON_LETTER_OR_DIGIT, " ").trim()

/**
 * Whether [candidate] may stand in for [missing]: the same normalized title and performer, and either the same audio hash
 * or a length within [MUSIC_REPLACEMENT_MAX_DURATION_DELTA_SECONDS]. Lengths count only when both are known.
 */
fun isMusicReplacement(missing: MusicTrackRef, candidate: MusicTrackRef): Boolean {
    val title = normalizeMusicMatchText(missing.title)
    if (title.isEmpty() || title != normalizeMusicMatchText(candidate.title)) return false
    if (normalizeMusicMatchText(missing.performer) != normalizeMusicMatchText(candidate.performer)) return false
    val sameFile = !missing.audioHash.isNullOrBlank() && missing.audioHash == candidate.audioHash
    val sameLength = missing.durationSeconds > 0 && candidate.durationSeconds > 0 &&
        abs(missing.durationSeconds - candidate.durationSeconds) <= MUSIC_REPLACEMENT_MAX_DURATION_DELTA_SECONDS
    return sameFile || sameLength
}

/**
 * The tracks of [library] that may replace [missing], best first: playlist entries newest first, then the listen stats, most
 * recently played first. Only tracks with a Telegram origin from this account qualify, each track appears once, and the
 * missing track never appears. Whether a candidate can still be played is not checked here.
 */
fun replacementCandidates(library: MusicLibrary, missing: MusicTrackRef): List<MusicTrackRef> {
    val fromPlaylists = library.playlists
        .flatMap { it.tracks }
        .sortedByDescending { it.addedAt }
        .map { it.toTrackRef() }
    val fromStats = library.stats.values
        .sortedWith(compareByDescending<MusicListenStats> { it.lastPlayedAt ?: 0L }.thenBy { it.trackKey })
        .map { it.track }
    return (fromPlaylists + fromStats)
        .asSequence()
        .filter { it.origin?.accountId == library.accountId && it.trackKey != missing.trackKey }
        .filter { isMusicReplacement(missing, it) }
        .distinctBy { it.trackKey }
        .take(MUSIC_REPLACEMENT_MAX_CANDIDATES)
        .toList()
}

/**
 * The candidates of [replacementCandidates] that [playlist] does not already list under another entry. The library refuses a
 * track that is listed twice, so such a candidate could never be picked. [missingItemId] is the entry being replaced: its own
 * track does not count as listed.
 */
fun replacementCandidatesFor(
    library: MusicLibrary,
    playlist: MusicPlaylist,
    missingItemId: String,
    missing: MusicTrackRef
): List<MusicTrackRef> {
    val listed = playlist.tracks.filter { it.itemId != missingItemId }.map { it.trackKey }.toSet()
    return replacementCandidates(library, missing).filter { it.trackKey !in listed }
}
