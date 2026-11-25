package com.beykus.beygram.playback

/**
 * Range-aware access to a file that TDLib keeps as a sparse local file.
 *
 * TDLib has one user download window per file: a new [requestWindow] replaces the previous one instead of opening a
 * second independent HTTP range. Checks therefore go through [readableFrom], which answers about a concrete offset and
 * never about "how much is downloaded in total".
 */
interface PlaybackRanges {
    /**
     * Contiguous bytes that are known to be readable starting exactly at [offset] (`GetFileDownloadedPrefixSize`).
     * Zero means that region is not present yet; bytes outside the reported range must never be read.
     */
    suspend fun readableFrom(fileId: Int, offset: Long): Long

    /**
     * Asks TDLib to continue the file from [offset] and stop after [limit] bytes (`0` means no limit).
     * [urgent] selects the "download as soon as possible" priority used while the user is watching.
     */
    fun requestWindow(fileId: Int, offset: Long, limit: Long, urgent: Boolean)

    /** Stops the current window of [fileId] only if a download is still pending. */
    fun cancelWindow(fileId: Int)
}

/** The window TDLib was last asked for, kept so duplicate requests are not sent again. */
data class RangeWindow(val offset: Long, val limit: Long, val urgent: Boolean) {
    /** True when reading [length] bytes at [position] stays inside this window. */
    fun covers(position: Long, length: Long): Boolean {
        if (position < offset) return false
        if (limit == 0L) return true
        val end = offset + limit
        if (end < offset) return false // overflow guard
        return position + length <= end
    }
}