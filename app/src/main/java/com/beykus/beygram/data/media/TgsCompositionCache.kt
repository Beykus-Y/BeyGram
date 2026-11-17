package com.beykus.beygram.data.media

import android.util.LruCache
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Parsed TGS stickers shared by every chat row. Scrolling a sticker out of view and back no longer re-reads, re-unzips and
 * re-parses the file; concurrent requests for the same file share one load, and at most [PARALLEL_LOADS] files are
 * prepared at once so a screen full of new stickers does not flood the IO pool.
 *
 * Bounded by count: a composition is a parsed vector model, not a bitmap, and Lottie's own cache holds 20 of them.
 * Failures are not cached, so a file that finishes downloading later is read again.
 */
internal object TgsCompositionCache {
    private const val MAX_ENTRIES = 32
    private const val PARALLEL_LOADS = 2

    private val cache = LruCache<String, LottieComposition>(MAX_ENTRIES)
    // Path -> key of the last successful load, so peek() can answer from composition without touching the disk.
    private val lastKeyByPath = LruCache<String, String>(MAX_ENTRIES * 2)
    private val inFlight = HashMap<String, Deferred<Result<LottieComposition>>>()
    private val permits = Semaphore(PARALLEL_LOADS)
    // Loads outlive the caller that started them: another row may be waiting for the same file.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The composition last loaded for [path], if it is still cached. No disk access; safe to call from composition. */
    fun peek(path: String): LottieComposition? {
        val key = lastKeyByPath.get(path) ?: return null
        return cache.get(key)
    }

    /** Loads [path] (or returns the cached composition), sharing an in-flight load of the same file. */
    suspend fun load(path: String): Result<LottieComposition> {
        val file = File(path)
        // File size and mtime are part of the key: a file rewritten at the same path is parsed again.
        val key = withContext(Dispatchers.IO) { "$path:${file.length()}:${file.lastModified()}" }
        cache.get(key)?.let { cached ->
            lastKeyByPath.put(path, key)
            return Result.success(cached)
        }
        val deferred = synchronized(inFlight) {
            inFlight[key] ?: scope.async { prepare(path, key, file) }.also { created ->
                inFlight[key] = created
                created.invokeOnCompletion { synchronized(inFlight) { if (inFlight[key] === created) inFlight.remove(key) } }
            }
        }
        return deferred.await()
    }

    private suspend fun prepare(path: String, key: String, file: File): Result<LottieComposition> = permits.withPermit {
        cache.get(key)?.let { return@withPermit Result.success(it) }
        try {
            val json = readTgsJson(file)
            // No Lottie cache key: this object is the cache, the composition is not kept twice.
            val result = LottieCompositionFactory.fromJsonStringSync(json, null)
            val composition = result.value
            if (composition != null) {
                cache.put(key, composition)
                lastKeyByPath.put(path, key)
                Result.success(composition)
            } else {
                Result.failure(result.exception ?: IllegalStateException("TGS parse failed"))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }
}
