package com.beykus.beygram.data.canvas

import android.content.Context
import com.beykus.beygram.data.music.provider.MusicProviderState
import com.beykus.beygram.data.music.provider.MusicProviders
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
 * Protects against stale track results, respects privacy/data-saver flags, and clears on sign-out.
 */
class CanvasRepository(
    private val diskCache: CanvasDiskCache,
    private val trackLookup: (String) -> ProviderTrack? = { key -> ProviderTracks.byKey(key) },
    private val isDataSaverActive: () -> Boolean = { false },
    private val now: () -> Long = { System.currentTimeMillis() },
    private val negativeTtlMs: Long = NEGATIVE_TTL_MS,
) {
    private val negLock = Any()
    private val negativeCache = HashMap<String, Long>()

    fun observeCanvas(
        identity: TrackIdentity,
        settings: CanvasSourceSettings,
    ): Flow<CanvasLoadState> = flow {
        if (settings.mode == BgCanvasMode.Off) {
            emit(CanvasLoadState.Idle)
            return@flow
        }
        if (identity.privacy.systemPrivate) {
            emit(CanvasLoadState.Unavailable(CanvasUnavailableReason.RestrictedPrivacy))
            return@flow
        }
        if (identity.audioSource != TrackAudioSource.YandexMusic) {
            emit(CanvasLoadState.Unavailable(CanvasUnavailableReason.NotAvailable))
            return@flow
        }
        val track = trackLookup(identity.playbackKey)
        val videoUri = track?.backgroundVideoUri?.takeIf { it.startsWith("https://", ignoreCase = true) }
        if (videoUri == null) {
            emit(CanvasLoadState.Unavailable(CanvasUnavailableReason.NotAvailable))
            return@flow
        }

        val key = cacheKey(identity, track.backgroundVideoId)
        val startedGen = diskCache.currentGeneration()
        val cached = diskCache.getIfFresh(key)
        if (cached != null) {
            emit(
                CanvasLoadState.Ready(
                    CanvasAsset(
                        playbackKey = identity.playbackKey,
                        localFilePath = cached.absolutePath,
                        videoId = track.backgroundVideoId,
                        sizeBytes = cached.length(),
                    )
                )
            )
            return@flow
        }

        if (settings.respectDataSaver && isDataSaverActive()) {
            emit(CanvasLoadState.Unavailable(CanvasUnavailableReason.DataSaver))
            return@flow
        }
        if (isNegativeCached(key, now())) {
            emit(CanvasLoadState.Unavailable(CanvasUnavailableReason.Failed))
            return@flow
        }

        emit(CanvasLoadState.Loading)
        val downloaded = diskCache.getOrDownload(key, videoUri, expectedGeneration = startedGen)
        if (downloaded != null) {
            emit(
                CanvasLoadState.Ready(
                    CanvasAsset(
                        playbackKey = identity.playbackKey,
                        localFilePath = downloaded.absolutePath,
                        videoId = track.backgroundVideoId,
                        sizeBytes = downloaded.length(),
                    )
                )
            )
        } else {
            recordNegative(key, now())
            emit(CanvasLoadState.Unavailable(CanvasUnavailableReason.Failed))
        }
    }

    suspend fun prefetch(identity: TrackIdentity, settings: CanvasSourceSettings) {
        if (settings.mode == BgCanvasMode.Off) return
        if (identity.privacy.systemPrivate || identity.audioSource != TrackAudioSource.YandexMusic) return
        if (settings.respectDataSaver && isDataSaverActive()) return
        val track = trackLookup(identity.playbackKey) ?: return
        val videoUri = track.backgroundVideoUri?.takeIf { it.startsWith("https://", ignoreCase = true) } ?: return
        val key = cacheKey(identity, track.backgroundVideoId)
        if (isNegativeCached(key, now())) return
        val startedGen = diskCache.currentGeneration()
        diskCache.getOrDownload(key, videoUri, expectedGeneration = startedGen)
    }

    suspend fun clearCacheOffMain() {
        synchronized(negLock) { negativeCache.clear() }
        diskCache.clear()
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
        negativeCache[key] = atMs
    }

    private fun cacheKey(identity: TrackIdentity, videoId: String?): String {
        val part = videoId?.takeIf { it.isNotBlank() } ?: identity.providerTrackId ?: identity.playbackKey
        return "${identity.accountId}|canvas|$part"
    }

    companion object {
        private const val NEGATIVE_TTL_MS = 15 * 60_000L
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
