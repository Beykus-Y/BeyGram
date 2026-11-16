package com.beykus.beygram.data.music.provider

import android.content.Context
import com.beykus.beygram.data.vpn.MihomoProxyClient
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Disk cache of the cover pictures of an optional music source. A cover is downloaded once by its address into the
 * app cache and then shown from the file, so the existing artwork view and the player read it like any other cover.
 *
 * Requests go out directly, never through the local proxy, like the other requests of the source. The cache is
 * bounded: the oldest files are deleted once it grows past [MAX_BYTES]. It is cleared on sign-out.
 */
object ProviderCovers {
    private const val DIRECTORY = "provider-covers"
    private const val MAX_BYTES = 48L * 1024 * 1024
    private const val MAX_FILE_BYTES = 4L * 1024 * 1024
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

    /** Rows scrolling into view ask together; a few downloads at a time keep the list and the service calm. */
    private val downloads = Semaphore(4)

    /** One lock per address, so two rows with the same cover download it once. */
    private val locks = HashMap<String, Mutex>()

    /** When each failed address last failed. It is not asked again for [RETRY_AFTER_MS], so a scroll does not retry it. */
    private val failed = HashMap<String, Long>()
    private const val RETRY_AFTER_MS = 5L * 60 * 1000

    /**
     * Bumped by [clear]. A download that started before a clear does not publish its file: the cover of a signed-out
     * account never comes back after the cache was emptied. Publishing and clearing hold [publishLock].
     */
    @Volatile
    private var generation = 0
    private val publishLock = Any()

    private fun root(context: Context): File = File(context.applicationContext.cacheDir, DIRECTORY)

    /**
     * The address of the same cover at [sizePx] pixels. Covers of the source carry their size as the last path part,
     * such as `…/200x200`; other addresses are returned unchanged.
     */
    fun sized(url: String, sizePx: Int): String = url.replace(SIZE_SUFFIX, "/${sizePx}x$sizePx")

    /** The cached file of [url], downloading it when needed. Null when it cannot be downloaded. */
    suspend fun file(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext null
        val target = File(root(context), hash(url))
        if (target.isFile && target.length() > 0) {
            target.setLastModified(System.currentTimeMillis())
            return@withContext target
        }
        val failedAt = synchronized(failed) { failed[url] }
        if (failedAt != null && System.currentTimeMillis() - failedAt < RETRY_AFTER_MS) return@withContext null
        val lock = synchronized(locks) { locks.getOrPut(url) { Mutex() } }
        try {
            lock.withLock {
                if (target.isFile && target.length() > 0) return@withLock target
                downloads.withPermit { download(context, url, target) }
            }
        } finally {
            synchronized(locks) { if (!lock.isLocked) locks.remove(url) }
        }
    }

    /** Deletes every cached cover, for example after sign-out. */
    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        synchronized(failed) { failed.clear() }
        synchronized(publishLock) {
            generation++
            root(context).listFiles()?.forEach { it.delete() }
        }
    }

    private fun download(context: Context, url: String, target: File): File? {
        val dir = root(context)
        dir.mkdirs()
        val startedIn = generation
        val partial = File(dir, target.name + ".part")
        var connection: HttpURLConnection? = null
        return try {
            connection = MihomoProxyClient.openConnection(URL(url), proxy = null)
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            var written = 0L
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        written += read
                        if (written > MAX_FILE_BYTES) throw IOException("cover too large")
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (written == 0L) throw IOException("empty cover")
            val published = synchronized(publishLock) { startedIn == generation && partial.renameTo(target) }
            if (!published) {
                partial.delete()
                return null
            }
            trim(dir)
            target
        } catch (e: CancellationException) {
            partial.delete()
            throw e
        } catch (e: Exception) {
            partial.delete()
            synchronized(failed) { failed[url] = System.currentTimeMillis() }
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** Deletes the least recently used covers until the cache fits [MAX_BYTES]. */
    private fun trim(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_BYTES) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun hash(url: String): String =
        MessageDigest.getInstance("SHA-1").digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private val SIZE_SUFFIX = Regex("/\\d+x\\d+$")
}
