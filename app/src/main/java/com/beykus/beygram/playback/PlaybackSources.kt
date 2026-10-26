package com.beykus.beygram.playback

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.MediaFileState
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.StateFlow

/**
 * Lazily resolves a music queue from one Telegram account. [observeFile] is the repository's live file state;
 * [download] is the explicit manual request used when playback actually opens a track. [autoDownload] is the
 * policy-aware repository path used only for prefetch, so Data Saver/network changes can cancel that traffic.
 *
 * A queue must never mix accounts: numeric TDLib file ids are scoped to the account that produced them.
 */
class PlaybackQueueSource(
    val accountId: String,
    val observeFile: (Int) -> StateFlow<MediaFileState>,
    val download: (Int) -> Unit,
    val autoDownload: ((AutoDownloadRequest) -> Unit)? = null
) {
    /**
     * Sends a prefetch through automatic-download tracking. Returns false when the caller did not supply the
     * automatic path; a manual [download] must never be used for speculative traffic.
     */
    fun prefetch(request: AutoDownloadRequest): Boolean {
        val callback = autoDownload ?: return false
        callback(request)
        return true
    }
}

internal data class ResolvedPlaybackSource(val path: String, val growing: GrowingPlaybackSource?)

/**
 * Live pointer to one PlaybackItem. It can be registered before the file is downloaded; [resolve] then returns null
 * and [request] asks TDLib for the file. The data source waits in BUFFERING instead of silently skipping the track.
 */
internal class PlaybackSourceHandle(
    @Volatile var item: PlaybackItem,
    private val queueSource: PlaybackQueueSource?
) {
    fun resolve(): ResolvedPlaybackSource? {
        val current = item
        if (current.path.isNotBlank()) return ResolvedPlaybackSource(current.path, current.growing)
        val fileId = current.fileId ?: return null
        val source = queueSource?.takeIf { it.accountId == current.accountId } ?: return null
        val state = source.observeFile(fileId).value
        // `partialAudioPlaybackPath` returns the completed file or a readable progressive prefix.
        val path = partialAudioPlaybackPath(state, current.mimeType) ?: return null
        val growing = if (state.localPath == null) {
            growingAudioSource(state, fileId, current.mimeType, source.observeFile, onNeedMore = { request() })
        } else {
            null
        }
        return ResolvedPlaybackSource(path, growing)
    }

    fun request() {
        val current = item
        val fileId = current.fileId ?: return
        val source = queueSource?.takeIf { it.accountId == current.accountId } ?: return
        source.download(fileId)
    }

    /** False for a lazy item that has neither a path nor an account-scoped observer to obtain one. */
    fun canResolve(): Boolean =
        item.path.isNotBlank() || (item.fileId != null && queueSource?.accountId == item.accountId)
}

/**
 * Process-wide bridge between Media3's DataSource (playback thread) and the current queue (main thread).
 * Entries are keyed by [PlaybackItem.systemMediaId], so secret items never expose account/message/file ids.
 */
internal object PlaybackSources {
    private const val SCHEME = "beygram-source"
    private const val AUTHORITY = "track"

    private val handles = ConcurrentHashMap<String, PlaybackSourceHandle>()

    fun register(mediaId: String, handle: PlaybackSourceHandle) {
        handles[mediaId] = handle
    }

    fun update(mediaId: String, item: PlaybackItem) {
        handles[mediaId]?.item = item
    }

    fun handle(mediaId: String): PlaybackSourceHandle? = handles[mediaId]

    fun clear() {
        handles.clear()
    }

    fun uri(mediaId: String): Uri = Uri.Builder().scheme(SCHEME).authority(AUTHORITY).appendPath(mediaId).build()

    fun mediaId(uri: Uri): String? =
        if (uri.scheme == SCHEME && uri.authority == AUTHORITY) uri.lastPathSegment else null
}

/** Builds the Media3 data source used by the single ExoPlayer. Non-BeyGram URIs are delegated unchanged. */
@OptIn(UnstableApi::class)
internal class PlaybackDataSourceFactory(context: Context) : DataSource.Factory {
    private val fallback = DefaultDataSource.Factory(context.applicationContext)

    override fun createDataSource(): DataSource = PlaybackDataSource(fallback.createDataSource())
}

/**
 * Reads a registered queue file, waiting for its path/TDLib download when necessary. The wait is visible to Media3
 * as ordinary loading, so the notification and UI show buffering instead of jumping to an unrelated track.
 */
@OptIn(UnstableApi::class)
internal class PlaybackDataSource(private val fallback: DataSource) : DataSource {
    private var file: RandomAccessFile? = null
    private var filePath: String? = null
    private var growing: GrowingPlaybackSource? = null
    private var position = 0L
    private var uri: Uri? = null
    private var fallbackOpen = false

    @Volatile
    private var closed = false

    override fun addTransferListener(transferListener: TransferListener) {
        fallback.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        close()
        closed = false
        val mediaId = PlaybackSources.mediaId(dataSpec.uri)
        if (mediaId == null) {
            val length = fallback.open(dataSpec)
            fallbackOpen = true
            uri = dataSpec.uri
            return length
        }
        val handle = PlaybackSources.handle(mediaId)
            ?: throw IOException("No playback source registered for this item")
        if (!handle.canResolve()) throw IOException("No account-scoped playback source for this item")
        val resolved = awaitSource(handle)
        val opened = RandomAccessFile(resolved.path, "r")
        opened.seek(dataSpec.position.coerceAtLeast(0L))
        file = opened
        filePath = resolved.path
        growing = resolved.growing
        position = dataSpec.position.coerceAtLeast(0L)
        uri = dataSpec.uri
        val total = when {
            resolved.growing == null -> opened.length()
            resolved.growing.isComplete() -> opened.length()
            resolved.growing.advertiseLength -> resolved.growing.totalSize
            else -> C.LENGTH_UNSET.toLong()
        }
        if (total == C.LENGTH_UNSET.toLong()) return C.LENGTH_UNSET.toLong()
        val remaining = (total - position).coerceAtLeast(0L)
        return if (dataSpec.length == C.LENGTH_UNSET.toLong()) remaining else minOf(remaining, dataSpec.length)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (fallbackOpen) return fallback.read(buffer, offset, length)
        var opened = file ?: return C.RESULT_END_OF_INPUT
        val source = growing
        val startedAt = SystemClock.elapsedRealtime()
        var requestedAt = 0L
        while (!closed) {
            // TDLib may move the completed file. Re-open the final path at the same logical position instead of
            // treating the old partial file's EOF as the end of the track.
            if (source != null && source.isComplete()) {
                val completed = source.completedPath()
                if (completed != null && completed != filePath && File(completed).isFile) {
                    runCatching { opened.close() }
                    opened = RandomAccessFile(completed, "r").apply { seek(position) }
                    file = opened
                    filePath = completed
                }
            }
            val readable = when {
                source == null -> opened.length()
                source.isComplete() -> opened.length()
                else -> minOf(source.readablePrefix(), opened.length())
            }
            val available = readable - position
            if (available > 0L) {
                val toRead = minOf(available, length.toLong()).toInt()
                val read = opened.read(buffer, offset, toRead)
                if (read > 0) {
                    position += read
                    return read
                }
            }
            // A completed file with no remaining bytes is a normal end of input.
            if (source == null || source.isComplete()) return C.RESULT_END_OF_INPUT
            val now = SystemClock.elapsedRealtime()
            if (!source.isDownloading() && now - startedAt >= NO_TRANSFER_TIMEOUT_MS) {
                throw IOException("Audio download was cancelled")
            }
            if (now - requestedAt >= REQUEST_INTERVAL_MS) {
                requestedAt = now
                source.onNeedMore()
            }
            if (now - startedAt >= STALL_TIMEOUT_MS) {
                throw IOException("Timed out waiting for the next audio chunk")
            }
            sleep()
        }
        return C.RESULT_END_OF_INPUT
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        closed = true
        fallbackOpen = false
        growing = null
        runCatching { file?.close() }
        file = null
        filePath = null
        fallback.close()
    }

    private fun awaitSource(handle: PlaybackSourceHandle): ResolvedPlaybackSource {
        var resolved = handle.resolve()
        if (resolved != null) return resolved
        val startedAt = SystemClock.elapsedRealtime()
        var requestedAt = 0L
        while (!closed) {
            val now = SystemClock.elapsedRealtime()
            if (now - requestedAt >= REQUEST_INTERVAL_MS) {
                requestedAt = now
                handle.request()
            }
            sleep()
            resolved = handle.resolve()
            if (resolved != null) return resolved
            if (now - startedAt >= OPEN_TIMEOUT_MS) {
                throw IOException("Audio is not available")
            }
        }
        throw IOException("Playback source was closed")
    }

    private fun sleep() {
        try {
            Thread.sleep(POLL_INTERVAL_MS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while waiting for audio", interrupted)
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 50L
        const val REQUEST_INTERVAL_MS = 1_000L
        const val NO_TRANSFER_TIMEOUT_MS = 10_000L
        const val STALL_TIMEOUT_MS = 45_000L
        const val OPEN_TIMEOUT_MS = 60_000L
    }
}
