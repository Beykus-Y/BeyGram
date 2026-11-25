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
    private class LockEntry(
        val mutex: Mutex = Mutex(),
        var waiters: Int = 0,
    )

    private val downloads = Semaphore(maxConcurrentDownloads.coerceAtLeast(1))
    private val locks = HashMap<String, LockEntry>()
    private val publishLock = Any()
    private val lruAccessMs = HashMap<String, Long>()

    @Volatile
    private var generation = 0

    fun currentGeneration(): Int = generation

    suspend fun getIfFresh(cacheKey: String): File? = withContext(Dispatchers.IO) {
        val target = entryFile(cacheKey)
        synchronized(publishLock) {
            if (!target.isFile || target.length() <= 0L) return@withContext null
            val createdAt = readCreatedAtMs(target)
            val currentNow = now()
            if (currentNow - createdAt >= positiveTtlMs) {
                target.delete()
                metaFile(target).delete()
                lruAccessMs.remove(target.name)
                return@withContext null
            }
            lruAccessMs[target.name] = currentNow
            target.setLastModified(currentNow)
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

        val entry = synchronized(locks) {
            locks.getOrPut(cacheKey) { LockEntry() }.also { it.waiters += 1 }
        }
        try {
            entry.mutex.withLock {
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
                                val renamed = partial.renameTo(target)
                                if (renamed) {
                                    val createdAt = now()
                                    writeCreatedAtMs(target, createdAt)
                                    lruAccessMs[target.name] = createdAt
                                    target.setLastModified(createdAt)
                                    trimLocked()
                                }
                                renamed
                            } else {
                                false
                            }
                        }
                        if (!published) {
                            partial.delete()
                            return@withPermit null
                        }
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
            synchronized(locks) {
                entry.waiters -= 1
                if (entry.waiters <= 0 && locks[cacheKey] === entry) {
                    locks.remove(cacheKey)
                }
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(publishLock) {
            generation += 1
            lruAccessMs.clear()
            rootDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun trimLocked() {
        val files = rootDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp4") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxTotalBytes) return
        for (file in files.sortedBy { lruAccessMs[it.name] ?: it.lastModified() }) {
            if (total <= maxTotalBytes) break
            val size = file.length()
            if (file.delete()) {
                metaFile(file).delete()
                lruAccessMs.remove(file.name)
                total -= size
            }
        }
    }

    private fun metaFile(target: File): File = File(rootDir, target.name + ".meta")

    private fun readCreatedAtMs(target: File): Long {
        val meta = metaFile(target)
        if (meta.isFile) {
            val parsed = runCatching { meta.readText().trim().toLong() }.getOrNull()
            if (parsed != null && parsed > 0L) return parsed
        }
        return target.lastModified()
    }

    private fun writeCreatedAtMs(target: File, createdAtMs: Long) {
        runCatching { metaFile(target).writeText(createdAtMs.toString()) }
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
        internal const val MAX_REDIRECTS = 5
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000

        val DEFAULT_DOWNLOADER = CanvasDownloader { url, target, maxBytes ->
            downloadWithHttpsRedirects(
                initialUrl = url,
                target = target,
                maxBytes = maxBytes,
                openConnection = { targetUrl -> MihomoProxyClient.openConnection(targetUrl, proxy = null) },
            )
        }

        internal suspend fun downloadWithHttpsRedirects(
            initialUrl: String,
            target: File,
            maxBytes: Long,
            openConnection: (URL) -> HttpURLConnection,
        ): Long {
            var currentUrl = URL(initialUrl)
            if (!currentUrl.protocol.equals("https", ignoreCase = true)) {
                throw IOException("Non-HTTPS canvas URL rejected")
            }

            var redirects = 0
            while (true) {
                coroutineContext.ensureActive()
                val connection = openConnection(currentUrl)
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = false
                val cancelHook = coroutineContext.job.invokeOnCompletion { cause ->
                    if (cause != null) runCatching { connection.disconnect() }
                }
                try {
                    val code = connection.responseCode
                    if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                        code == HttpURLConnection.HTTP_MOVED_TEMP ||
                        code == HttpURLConnection.HTTP_SEE_OTHER ||
                        code == 307 ||
                        code == 308
                    ) {
                        if (redirects >= MAX_REDIRECTS) {
                            throw IOException("Too many redirects")
                        }
                        val location = connection.getHeaderField("Location")
                            ?.takeIf { it.isNotBlank() }
                            ?: throw IOException("Missing redirect location")
                        val nextUrl = URL(currentUrl, location)
                        if (!nextUrl.protocol.equals("https", ignoreCase = true)) {
                            throw IOException("Non-HTTPS redirect rejected")
                        }
                        currentUrl = nextUrl
                        redirects += 1
                        continue
                    }
                    if (code !in 200..299) {
                        throw IOException("HTTP $code")
                    }
                    val contentLength = connection.contentLengthLong
                    if (contentLength > maxBytes) {
                        throw IOException("Canvas exceeds max bytes")
                    }
                    return connection.inputStream.use { input ->
                        copyBounded(input, target, maxBytes)
                    }
                } finally {
                    cancelHook.dispose()
                    connection.disconnect()
                }
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
