package com.beykus.beygram.media.bgbeat

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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
    private class ProgressHub {
        private val listeners = CopyOnWriteArrayList<(Float, String) -> Unit>()

        @Volatile
        private var last: Pair<Float, String>? = null

        fun add(listener: (Float, String) -> Unit): () -> Unit {
            listeners.add(listener)
            last?.let { (fraction, stage) ->
                runCatching { listener(fraction, stage) }
            }
            return { listeners.remove(listener) }
        }

        fun broadcast(fraction: Float, stage: String) {
            last = fraction to stage
            listeners.forEach { listener ->
                runCatching { listener(fraction, stage) }
            }
        }
    }

    private data class HubSlot(
        val hub: ProgressHub,
        val refs: AtomicInteger
    )

    private val hubs = ConcurrentHashMap<String, HubSlot>()

    private fun acquireHub(key: String): HubSlot =
        hubs.compute(key) { _, existing ->
            if (existing == null) {
                HubSlot(ProgressHub(), AtomicInteger(1))
            } else {
                existing.refs.incrementAndGet()
                existing
            }
        }!!

    private fun releaseHub(key: String, slot: HubSlot) {
        if (slot.refs.decrementAndGet() == 0) {
            hubs.remove(key, slot)
        }
    }

    private fun hubKey(
        identity: BgBeatAnalysisIdentity,
        cacheDir: String,
        forceRefresh: Boolean
    ): String = buildString {
        append(identity.filePath)
        append('|').append(identity.fileLength)
        append('|').append(identity.fileLastModified)
        append('|').append(identity.minBpm)
        append('|').append(identity.maxBpm)
        append('|').append(cacheDir)
        if (forceRefresh) {
            // A forced run is not deduplicated with a normal one; give it its own
            // progress hub as well.
            append("|force|").append(System.nanoTime())
        }
    }

    /**
     * Acquires the shared analysis for [identity]. Runs the native acquire on
     * Dispatchers.IO; the call is synchronous and blocks until the first caller's
     * analysis is ready.
     *
     * All consumers that join the same native key register on one Kotlin progress
     * hub. The native owner's callback fans out to every listener, so a waiter
     * (for example BG Beat when the mascot started first) still receives progress.
     */
    suspend fun acquire(
        context: Context,
        identity: BgBeatAnalysisIdentity,
        cancelTokenHandle: Long,
        progressListener: BgBeatProgressListener?,
        forceRefresh: Boolean = false,
        out: AtomicReference<BgAnalysisLease?>
    ): BgAnalysisLease? = withContext(Dispatchers.IO + NonCancellable) {
        // The holder is filled inside the non-cancellable block. If the calling
        // coroutine is cancelled while the native call runs, the value of this
        // function may be discarded, but the caller can still release the lease.
        out.set(null)
        val cacheDir = BgBeatSettingsStore.getAccountCacheDir(context, identity.accountId).absolutePath
        val key = hubKey(identity, cacheDir, forceRefresh)
        val slot = acquireHub(key)
        val removeListener = slot.hub.add { fraction, stage ->
            progressListener?.onProgress(fraction, stage)
        }
        try {
            val handle = try {
                BgBeatNative.nativeAcquireAnalysis(
                    filePath = identity.filePath,
                    cacheDir = cacheDir,
                    minBpm = identity.minBpm,
                    maxBpm = identity.maxBpm,
                    cancelTokenHandle = cancelTokenHandle,
                    progressListener = { fraction, stage ->
                        slot.hub.broadcast(fraction, stage)
                    },
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
                BgAnalysisLease(handle, json).also { out.set(it) }
            }
        } finally {
            removeListener()
            releaseHub(key, slot)
        }
    }
}
