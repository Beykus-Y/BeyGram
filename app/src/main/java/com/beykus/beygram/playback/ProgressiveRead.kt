package com.beykus.beygram.playback

/**
 * How many bytes a progressive reader may read at [position] without leaving the region TDLib confirmed.
 *
 * Completion makes the local file authoritative. Before completion only the contiguous run reported for this exact
 * position is safe: TDLib keeps partial files sparse, so `file.length()` and the total downloaded size prove nothing
 * about a specific offset.
 */
internal fun readableBytesAt(
    position: Long,
    fileLength: Long,
    complete: Boolean,
    confirmedFromPosition: Long,
): Long {
    if (position < 0L) return 0L
    val untilEndOfFile = (fileLength - position).coerceAtLeast(0L)
    if (complete) return untilEndOfFile
    return minOf(confirmedFromPosition.coerceAtLeast(0L), untilEndOfFile)
}