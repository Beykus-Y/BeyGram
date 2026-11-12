package com.beykus.beygram.media.bgbeat

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A ref-counted snapshot of one track analysis. The native registry deduplicates
 * concurrent work for the same file identity, so BG Beat and BG Motion share one
 * PCM decode, one FFT pass and one cached AnalysisResult.
 *
 * Always [release] a lease when the consumer no longer needs it. A mascot session
 * keeps its own shared reference after creation, so releasing the lease there is
 * safe.
 */
class BgAnalysisLease internal constructor(
    val handle: Long,
    val json: String
) {
    private val released = AtomicBoolean(false)

    fun release() {
        if (released.compareAndSet(false, true)) {
            BgBeatNative.nativeReleaseAnalysis(handle)
        }
    }
}

object BgAnalysisRepository {
    /**
     * Acquires the shared analysis for [identity]. Runs the native acquire on
     * Dispatchers.Default; the call is synchronous and blocked until the first
     * caller's analysis is ready.
     */
    suspend fun acquire(
        context: Context,
        identity: BgBeatAnalysisIdentity,
        cancelTokenHandle: Long,
        progressListener: BgBeatProgressListener?,
        forceRefresh: Boolean = false
    ): BgAnalysisLease? = withContext(Dispatchers.Default + NonCancellable) {
        val cacheDir = BgBeatSettingsStore.getAccountCacheDir(context, identity.accountId).absolutePath
        val handle = try {
            BgBeatNative.nativeAcquireAnalysis(
                filePath = identity.filePath,
                cacheDir = cacheDir,
                minBpm = identity.minBpm,
                maxBpm = identity.maxBpm,
                cancelTokenHandle = cancelTokenHandle,
                progressListener = progressListener,
                forceRefresh = forceRefresh
            )
        } catch (_: Throwable) {
            0L
        }
        if (handle == 0L) return@withContext null

        val json = try {
            BgBeatNative.nativeAnalysisJson(handle)
        } catch (_: Throwable) {
            null
        }
        if (json == null) {
            BgBeatNative.nativeReleaseAnalysis(handle)
            null
        } else {
            BgAnalysisLease(handle, json)
        }
    }
}
