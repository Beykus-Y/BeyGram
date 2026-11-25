package com.beykus.beygram.data.canvas

import com.beykus.beygram.data.vpn.MihomoProxyClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

fun interface CanvasDownloader {
    /**
     * Streams [url] into [target] up to [maxBytes], returning written bytes or throwing [IOException].
     * Never logs or persists [url].
     */
    suspend fun download(url: String, target: File, maxBytes: Long): Long
}

/**
 * Bounded disk cache for animated cover MP4 files (`cacheDir/provider-canvas-v1`).
 * Enforces per-file size cap, total LRU byte budget, TTL, per-URL deduplication, and generation-guarded [clear].
 */
class CanvasDiskCache(
    private val rootDir: File,
    private val maxTotalBytes: Long = MAX_TOTAL_BYTES,
    private val maxFileBytes: Long = MAX_FILE_BYTES,
    private val positiveTtlMs: Long = POSITIVE_TTL_MS,
    private val maxConcurrentDownloads: Int = 2,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val downloader: CanvasDownloader = DEFAULT_DOWNLOADER,
) {
    private val downloads = Semaphore(maxConcurrentDownloads.coerceAtLeast(1))
    private val locks = HashMap<String, Mutex>()
    private val publishLock = Any()

    @Volatile
    private var generation = 0

    fun currentGeneration(): Int = generation

    suspend fun getIfFresh(cacheKey: String): File? = withContext(Dispatchers.IO) {
        val target = entryFile(cacheKey)
        synchronized(publishLock) {
            if (!target.isFile || target.length() <= 0L) return@withContext null
            val age = now() - target.lastModified()
            if (age >= positiveTtlMs) {
                target.delete()
                return@withContext null
            }
            target.setLastModified(now())
            target
        }
    }

    suspend fun getOrDownload(
        cacheKey: String,
        remoteUrl: String,
        expectedGeneration: Int = generation,
    ): File? = withContext(Dispatchers.IO) {
        if (!remoteUrl.startsWith("https://", ignoreCase = true)) return@withContext null
        getIfFresh(cacheKey)?.let { return@withContext it }

        val lock = synchronized(locks) { locks.getOrPut(cacheKey) { Mutex() } }
        try {
            lock.withLock {
                getIfFresh(cacheKey)?.let { return@withLock it }
                downloads.withPermit {
                    coroutineContext.ensureActive()
                    if (generation != expectedGeneration) return@withPermit null
                    rootDir.mkdirs()
                    val target = entryFile(cacheKey)
                    val partial = File(rootDir, target.name + ".part")
                    try {
                        val written = downloader.download(remoteUrl, partial, maxFileBytes)
                        if (written <= 0L || written > maxFileBytes) {
                            partial.delete()
                            return@withPermit null
                        }
                        val published = synchronized(publishLock) {
                            if (generation == expectedGeneration) {
                                if (target.exists()) target.delete()
                                partial.renameTo(target)
                            } else {
                                false
                            }
                        }
                        if (!published) {
                            partial.delete()
                            return@withPermit null
                        }
                        target.setLastModified(now())
                        trimLocked()
                        target
                    } catch (cancelled: CancellationException) {
                        partial.delete()
                        throw cancelled
                    } catch (_: Exception) {
                        partial.delete()
                        null
                    }
                }
            }
        } finally {
            synchronized(locks) { if (!lock.isLocked) locks.remove(cacheKey) }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(publishLock) {
            generation += 1
            rootDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun trimLocked() {
        val files = rootDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxTotalBytes) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxTotalBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun entryFile(cacheKey: String): File {
        val hash = MessageDigest.getInstance("SHA-1")
            .digest(cacheKey.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(rootDir, "$hash.mp4")
    }

    companion object {
        const val MAX_TOTAL_BYTES = 48L * 1024 * 1024
        const val MAX_FILE_BYTES = 12L * 1024 * 1024
        const val POSITIVE_TTL_MS = 24L * 60 * 60 * 1000L
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000

        val DEFAULT_DOWNLOADER = CanvasDownloader { url, target, maxBytes ->
            val connection = MihomoProxyClient.openConnection(URL(url), proxy = null)
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            val cancelHook = coroutineContext.job.invokeOnCompletion { cause ->
                if (cause != null) runCatching { connection.disconnect() }
            }
            try {
                if (connection.responseCode !in 200..299) {
                    throw IOException("HTTP ${connection.responseCode}")
                }
                val contentLength = connection.contentLengthLong
                if (contentLength > maxBytes) {
                    throw IOException("Canvas exceeds max bytes")
                }
                connection.inputStream.use { input ->
                    copyBounded(input, target, maxBytes)
                }
            } finally {
                cancelHook.dispose()
                connection.disconnect()
            }
        }

        internal fun copyBounded(input: InputStream, target: File, maxBytes: Long): Long {
            var written = 0L
            target.outputStream().use { output ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    written += read
                    if (written > maxBytes) throw IOException("Canvas exceeds max bytes")
                    output.write(buffer, 0, read)
                }
            }
            return written
        }
    }
}
