package com.beykus.beygram.data.canvas

import android.content.Context
import com.beykus.beygram.data.music.provider.MusicProviderState
import com.beykus.beygram.data.music.provider.MusicProviders
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTrack
import com.beykus.beygram.data.music.provider.ProviderTracks
import com.beykus.beygram.domain.music.TrackAudioSource
import com.beykus.beygram.domain.music.TrackIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Coordinates animated cover metadata resolution and bounded caching for [TrackIdentity].
 * Protects against stale track results, respects privacy/data-saver flags, restores metadata
 * after app restart, and clears on sign-out.
 */
class CanvasRepository(
    private val diskCache: CanvasDiskCache,
    private val trackLookup: (String) -> ProviderTrack? = { key -> ProviderTracks.byKey(key) },
    private val trackFetcher: suspend (String) -> ProviderTrack? = { null },
    private val isDataSaverActive: () -> Boolean = { false },
    private val now: () -> Long = { System.currentTimeMillis() },
    private val negativeTtlMs: Long = NEGATIVE_TTL_MS,
) {
    private val negLock = Any()
    private val negativeCache = LinkedHashMap<String, Long>(16, 0.75f, true)

    private val diagLock = Any()
    private var diagLoads = 0
    private var diagCacheHits = 0
    private var diagDownloadsOk = 0
    private var diagDownloadsFail = 0
    private var diagMetadataRestored = 0
    private var diagFirstFrames = 0
    private var diagPlaybackErrors = 0
    private var diagLastUnavailable: CanvasUnavailableReason? = null
    private var diagLastErrorCode: String? = null
    private var diagLastSizeBytes: Long? = null

    fun observeCanvas(
        identity: TrackIdentity,
        settings: CanvasSourceSettings,
    ): Flow<CanvasLoadState> = flow {
        if (settings.mode == BgCanvasMode.Off) {
            emit(CanvasLoadState.Idle)
            return@flow
        }
        if (identity.privacy.systemPrivate) {
            recordUnavailable(CanvasUnavailableReason.RestrictedPrivacy)
            emit(CanvasLoadState.Unavailable(CanvasUnavailableReason.RestrictedPrivacy))
            return@flow
        }
        if (identity.audioSource != TrackAudioSource.YandexMusic) {
            recordUnavailable(CanvasUnavailableReason.NotAvailable)
            emit(CanvasLoadState.Unavailable(CanvasUnavailableReason.NotAvailable))
            return@flow
        }

        synchronized(diagLock) { diagLoads += 1 }

        var emittedLoading = false
        var track = trackLookup(identity.playbackKey)
        if (track == null) {
            val trackId = identity.providerTrackId
            if (trackId.isNullOrBlank()) {
                recordUnavailable(CanvasUnavailableReason.NotAvailable)
                emit(CanvasLoadState.Unavailable(CanvasUnavailableReason.NotAvailable))
                return@flow
            }
            if (settings.respectDataSaver && isDataSaverActive()) {
                recordUnavailable(CanvasUnavailableReason.DataSaver)
                emit(CanvasLoadState.Unavailable(CanvasUnavailableReason.DataSaver))
                return@flow
            }
            emittedLoading = true
            emit(CanvasLoadState.Loading)
            track = runCatching { trackFetcher(trackId) }.getOrNull()
            if (track != null) {
                synchronized(diagLock) { diagMetadataRestored += 1 }
            }
        }

        val videoUri = track?.backgroundVideoUri?.takeIf { it.startsWith("https://", ignoreCase = true) }
        if (videoUri == null) {
            recordUnavailable(CanvasUnavailableReason.NotAvailable)
            emit(CanvasLoadState.Unavailable(CanvasUnavailableReason.NotAvailable))
            return@flow
        }

        val key = cacheKey(identity, track.backgroundVideoId)
        val startedGen = diskCache.currentGeneration()
        val cached = diskCache.getIfFresh(key)
        if (cached != null) {
            val size = cached.length()
            synchronized(diagLock) {
                diagCacheHits += 1
                diagLastSizeBytes = size
            }
            emit(
                CanvasLoadState.Ready(
                    CanvasAsset(
                        playbackKey = identity.playbackKey,
                        localFilePath = cached.absolutePath,
                        videoId = track.backgroundVideoId,
                        sizeBytes = size,
                    )
                )
            )
            return@flow
        }

        if (settings.respectDataSaver && isDataSaverActive()) {
            recordUnavailable(CanvasUnavailableReason.DataSaver)
            emit(CanvasLoadState.Unavailable(CanvasUnavailableReason.DataSaver))
            return@flow
        }
        if (isNegativeCached(key, now())) {
            recordUnavailable(CanvasUnavailableReason.Failed)
            emit(CanvasLoadState.Unavailable(CanvasUnavailableReason.Failed))
            return@flow
        }

        if (!emittedLoading) {
            emit(CanvasLoadState.Loading)
        }
        val downloaded = diskCache.getOrDownload(key, videoUri, expectedGeneration = startedGen)
        if (downloaded != null) {
            val size = downloaded.length()
            synchronized(diagLock) {
                diagDownloadsOk += 1
                diagLastSizeBytes = size
            }
            emit(
                CanvasLoadState.Ready(
                    CanvasAsset(
                        playbackKey = identity.playbackKey,
                        localFilePath = downloaded.absolutePath,
                        videoId = track.backgroundVideoId,
                        sizeBytes = size,
                    )
                )
            )
        } else {
            recordNegative(key, now())
            synchronized(diagLock) { diagDownloadsFail += 1 }
            recordUnavailable(CanvasUnavailableReason.Failed)
            emit(CanvasLoadState.Unavailable(CanvasUnavailableReason.Failed))
        }
    }

    suspend fun prefetch(identity: TrackIdentity, settings: CanvasSourceSettings) {
        if (settings.mode == BgCanvasMode.Off) return
        if (identity.privacy.systemPrivate || identity.audioSource != TrackAudioSource.YandexMusic) return
        if (settings.respectDataSaver && isDataSaverActive()) return
        val track = trackLookup(identity.playbackKey) ?: run {
            val trackId = identity.providerTrackId ?: return
            runCatching { trackFetcher(trackId) }.getOrNull()?.also {
                synchronized(diagLock) { diagMetadataRestored += 1 }
            }
        } ?: return
        val videoUri = track.backgroundVideoUri?.takeIf { it.startsWith("https://", ignoreCase = true) } ?: return
        val key = cacheKey(identity, track.backgroundVideoId)
        if (isNegativeCached(key, now())) return
        val startedGen = diskCache.currentGeneration()
        diskCache.getOrDownload(key, videoUri, expectedGeneration = startedGen)
    }

    fun recordFirstFrame() {
        synchronized(diagLock) {
            diagFirstFrames += 1
        }
    }

    fun recordPlaybackError(errorCode: String) {
        val safeCode = errorCode
            .replace(Regex("[^A-Za-z0-9_.-]"), "_")
            .take(48)
            .ifBlank { "unknown" }
        synchronized(diagLock) {
            diagPlaybackErrors += 1
            diagLastErrorCode = safeCode
        }
    }

    /**
     * Returns a redacted, URL-free diagnostic summary for Yandex Music settings.
     */
    fun diagnosticsSummary(): String? = synchronized(diagLock) {
        if (diagLoads == 0 && diagFirstFrames == 0 && diagPlaybackErrors == 0) return@synchronized null
        buildString {
            append("canvas.runtime: ")
            append("loads=").append(diagLoads)
            append("; hits=").append(diagCacheHits)
            append("; dlOk=").append(diagDownloadsOk)
            append("; dlFail=").append(diagDownloadsFail)
            append("; restored=").append(diagMetadataRestored)
            append("; firstFrame=").append(diagFirstFrames)
            append("; playErr=").append(diagPlaybackErrors)
            diagLastSizeBytes?.let { append("; lastBytes=").append(it) }
            append("; lastReason=").append(diagLastUnavailable?.name ?: "none")
            append("; lastErr=").append(diagLastErrorCode ?: "none")
        }
    }

    suspend fun clearCacheOffMain() {
        synchronized(negLock) { negativeCache.clear() }
        diskCache.clear()
    }

    internal fun negativeCacheSize(): Int = synchronized(negLock) { negativeCache.size }

    private fun recordUnavailable(reason: CanvasUnavailableReason) {
        synchronized(diagLock) {
            diagLastUnavailable = reason
        }
    }

    private fun isNegativeCached(key: String, atMs: Long): Boolean = synchronized(negLock) {
        val failedAt = negativeCache[key] ?: return false
        if (atMs - failedAt >= negativeTtlMs) {
            negativeCache.remove(key)
            return false
        }
        true
    }

    private fun recordNegative(key: String, atMs: Long) = synchronized(negLock) {
        if (negativeCache.size >= MAX_NEGATIVE_ENTRIES && !negativeCache.containsKey(key)) {
            val iter = negativeCache.entries.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                if (atMs - entry.value >= negativeTtlMs) {
                    iter.remove()
                }
            }
            val evictIter = negativeCache.entries.iterator()
            while (negativeCache.size >= MAX_NEGATIVE_ENTRIES && evictIter.hasNext()) {
                evictIter.next()
                evictIter.remove()
            }
        }
        negativeCache[key] = atMs
    }

    private fun cacheKey(identity: TrackIdentity, videoId: String?): String {
        val part = videoId?.takeIf { it.isNotBlank() } ?: identity.providerTrackId ?: identity.playbackKey
        return "${identity.accountId}|canvas|$part"
    }

    companion object {
        private const val NEGATIVE_TTL_MS = 15 * 60_000L
        internal const val MAX_NEGATIVE_ENTRIES = 128
    }
}

object AppCanvasRepository {
    @Volatile
    private var instance: CanvasRepository? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun get(context: Context): CanvasRepository {
        instance?.let { return it }
        return synchronized(this) {
            instance?.let { return it }
            val app = context.applicationContext
            val created = CanvasRepository(
                diskCache = CanvasDiskCache(File(app.cacheDir, "provider-canvas-v1")),
                trackFetcher = { trackId ->
                    when (val res = MusicProviders.yandex(app)?.tracks(listOf(trackId))) {
                        is ProviderResult.Success -> {
                            val found = res.value.firstOrNull { it.trackId == trackId } ?: res.value.firstOrNull()
                            if (found != null) ProviderTracks.remember(listOf(found))
                            found
                        }
                        else -> null
                    }
                },
                isDataSaverActive = { isAndroidDataSaverOn(app) },
            )
            MusicProviders.yandex(app)?.let { provider ->
                scope.launch {
                    provider.state.collect { state ->
                        if (state is MusicProviderState.SignedOut) {
                            created.clearCacheOffMain()
                        }
                    }
                }
            }
            instance = created
            created
        }
    }

    private fun isAndroidDataSaverOn(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        cm?.restrictBackgroundStatus == android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
    }.getOrDefault(false)
}
