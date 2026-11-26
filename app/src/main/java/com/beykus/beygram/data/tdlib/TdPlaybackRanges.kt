package com.beykus.beygram.data.tdlib

import com.beykus.beygram.playback.PlaybackRanges
import kotlinx.coroutines.CancellationException
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/**
 * The TDLib side of range-aware playback.
 *
 * Availability is asked per offset (`GetFileDownloadedPrefixSize`), never derived from "downloaded so far": TDLib
 * stores files as a sparse bitmask of parts and only reports a contiguous run from the offset that is actually asked
 * for. A download window is requested with [TdApi.DownloadFile] and its `offset`/`limit`; TDLib keeps one user window
 * per file, so a new request replaces the previous one.
 */
internal class TdPlaybackRanges(
    private val requests: TdRequests,
    /** Fire-and-forget send; the answer to `downloadFile` is not needed because updates arrive through `UpdateFile`. */
    private val send: (TdApi.Function<*>) -> Unit,
) : PlaybackRanges {
    override suspend fun readableFrom(fileId: Int, offset: Long): Long {
        if (fileId <= 0 || offset < 0L) return 0L
        val answer = try {
            requests(TdApi.GetFileDownloadedPrefixSize(fileId, offset))
        } catch (cancelled: CancellationException) {
            // Cancellation is not a failed query: the caller (a closed page, a new video, a switched account) must see
            // it, otherwise the coordinator would happily request a window for a screen that no longer exists.
            throw cancelled
        } catch (_: Exception) {
            null
        }
        return (answer as? TdApi.FileDownloadedPrefixSize)?.size?.coerceAtLeast(0L) ?: 0L
    }

    override fun requestWindow(fileId: Int, offset: Long, limit: Long, urgent: Boolean) {
        if (fileId <= 0 || offset < 0L) return
        send(
            TdApi.DownloadFile(
                fileId,
                if (urgent) PRIORITY_WATCHING else PRIORITY_NORMAL,
                offset,
                limit.coerceAtLeast(0L),
                false,
            )
        )
    }

    override fun cancelWindow(fileId: Int) {
        if (fileId <= 0) return
        // onlyIfPending: a completed file keeps its bytes and must not be interrupted.
        send(TdApi.CancelDownloadFile(fileId, true))
    }

    companion object {
        /** TDLib's "download as soon as possible" priority, used while the user is watching. */
        const val PRIORITY_WATCHING = 32

        /** The priority every ordinary download in the app already uses. */
        const val PRIORITY_NORMAL = 16
    }
}

/** Builds the production implementation for a repository that owns one TDLib client. */
internal fun tdPlaybackRanges(requests: TdRequests, client: () -> Client): TdPlaybackRanges =
    TdPlaybackRanges(requests) { function -> client().send(function, Client.ResultHandler { }) }