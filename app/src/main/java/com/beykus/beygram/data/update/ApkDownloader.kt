package com.beykus.beygram.data.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.util.UUID

/** An interrupted download: its bytes wait in the partial file of [release], and [validator] identifies the server copy. */
data class PartialDownload(val release: StableRelease, val validator: String)

/** Callbacks of one download. They run on the download's coroutine. */
interface DownloadListener {
    /** The server accepted the request; [startBytes] of the file are already on disk. [validator] may be null. */
    suspend fun onResponse(startBytes: Long, validator: String?)
    suspend fun onProgress(progress: DownloadProgress)
    /** All bytes arrived. The hash and the package checks run now; the file is not usable until they pass. */
    suspend fun onVerifying()
}

private const val BUFFER_SIZE = 64 * 1024
private const val FREE_SPACE_MARGIN = 20_000_000L

/** Failures after which the partial bytes are not trusted any more. Network errors keep the partial for a resume. */
internal val DISCARD_PARTIAL_REASONS = setOf(
    StableUpdateReason.ChecksumMismatch,
    StableUpdateReason.PackageMismatch,
    StableUpdateReason.SignerMismatch,
    StableUpdateReason.InvalidMetadata,
)

/** The partial file of a release. The name binds the partial to its version code and checksum. */
internal fun partialFileName(prefix: String, release: StableRelease): String =
    "$prefix-resume-${release.versionCode}-${release.sha256.take(16)}.part"

internal fun StableRelease.binds(other: StableRelease): Boolean =
    versionCode == other.versionCode && sha256 == other.sha256 && size == other.size && apkName == other.apkName

/**
 * Shared download of both channels. Resumes only a partial that binds to [release] and has a strong validator;
 * the whole file is hashed, then [verifyPackage] runs. Returns a new immutable APK file of [updateDir].
 * An APK already handed to the installer is never a target here, so no existing file is overwritten.
 */
internal suspend fun downloadApkFile(
    updateDir: File,
    prefix: String,
    url: String,
    release: StableRelease,
    resume: PartialDownload?,
    listener: DownloadListener,
    verifyPackage: (File) -> Unit,
): File {
    if (!updateDir.exists() && !updateDir.mkdirs()) {
        throw StableUpdateException(StableUpdateReason.Storage, "Нет доступа к папке обновлений.")
    }
    val partial = File(updateDir, partialFileName(prefix, release))
    val fresh = partial.isFile &&
        System.currentTimeMillis() - partial.lastModified() <= ApkRetentionPolicy.PARTIAL_MAX_AGE_MS
    val plan = RangeResumePolicy.plan(
        partialBytes = if (fresh) partial.length() else 0L,
        validator = resume?.takeIf { it.release.binds(release) }?.validator,
        expectedSize = release.size,
    )
    // A partial that cannot be resumed is never trusted: it is removed before anything is requested.
    if (plan == null) partial.delete()
    val remaining = release.size - (plan?.offset ?: 0L)
    if (updateDir.usableSpace < remaining + FREE_SPACE_MARGIN) {
        throw StableUpdateException(StableUpdateReason.NoFreeSpace, "Недостаточно свободного места для APK.")
    }

    var connection: HttpURLConnection = UpdateHttp.connect(url, plan?.headers().orEmpty())
    // Cancelling the coroutine closes the socket at once, which unblocks a read that waits on the network.
    val watcher = CoroutineScope(currentCoroutineContext()).launch {
        suspendCancellableCoroutine<Unit> { continuation ->
            continuation.invokeOnCancellation { connection.disconnect() }
        }
    }
    var target: File? = null
    try {
        val first = RangeResumePolicy.decide(
            plan = plan,
            status = connection.responseCode,
            contentRange = connection.getHeaderField("Content-Range"),
            responseValidator = validatorOf(connection),
            expectedSize = release.size,
        )
        val decision = if (first != ResumeDecision.RestartWithoutRange) {
            first
        } else {
            connection.disconnect()
            partial.delete()
            connection = UpdateHttp.connect(url)
            RangeResumePolicy.decide(
                plan = null,
                status = connection.responseCode,
                contentRange = connection.getHeaderField("Content-Range"),
                responseValidator = validatorOf(connection),
                expectedSize = release.size,
            )
        }
        val start = when (decision) {
            is ResumeDecision.Append -> decision.offset
            ResumeDecision.FromStart -> {
                partial.delete()
                0L
            }
            ResumeDecision.RestartWithoutRange, ResumeDecision.Reject ->
                throw StableUpdateException(StableUpdateReason.ServerError, "Сервер ответил не так, как ожидалось.")
        }
        if (start > 0L && partial.length() != start) {
            throw StableUpdateException(StableUpdateReason.InvalidMetadata, "Частичный файл изменился.")
        }
        val reported = connection.contentLengthLong
        if (reported > 0L && reported != release.size - start) {
            throw StableUpdateException(StableUpdateReason.InvalidMetadata, "Размер файла на сервере не совпадает с манифестом.")
        }
        listener.onResponse(start, validatorOf(connection))

        val digest = MessageDigest.getInstance("SHA-256")
        if (start > 0L) feedPrefix(partial, start, digest)
        val meter = DownloadMeter(release.size, start, System.nanoTime())
        val throttle = ProgressThrottle()
        var downloaded = start
        connection.inputStream.use { input ->
            FileOutputStream(partial, start > 0L).buffered().use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    downloaded += count
                    if (downloaded > release.size) {
                        throw StableUpdateException(StableUpdateReason.ChecksumMismatch, "Размер APK превышен.")
                    }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                    val now = System.nanoTime()
                    val complete = downloaded == release.size
                    if (throttle.shouldEmit(now, complete)) listener.onProgress(meter.sample(downloaded, now))
                }
            }
        }
        if (downloaded != release.size) {
            // The connection ended early: the partial stays so that the next attempt can resume it.
            throw StableUpdateException(StableUpdateReason.Network, "Загрузка прервана до конца.")
        }
        listener.onVerifying()
        if (digest.digest().toHex() != release.sha256) {
            throw StableUpdateException(StableUpdateReason.ChecksumMismatch, "Контрольная сумма APK не совпадает.")
        }
        val apk = File(updateDir, "$prefix-${UUID.randomUUID()}.apk")
        if (!partial.renameTo(apk)) throw StableUpdateException(StableUpdateReason.Storage, "Не удалось сохранить APK.")
        target = apk
        verifyPackage(apk)
        return apk
    } catch (error: Throwable) {
        target?.delete()
        if (error is StableUpdateException && error.reason in DISCARD_PARTIAL_REASONS) partial.delete()
        currentCoroutineContext().ensureActive()
        throw error
    } finally {
        watcher.cancel()
        connection.disconnect()
    }
}

private fun validatorOf(connection: HttpURLConnection): String? =
    RangeResumePolicy.strongValidator(connection.getHeaderField("ETag"), connection.getHeaderField("Last-Modified"))

/** Feeds the first [bytes] of an existing partial into the digest, so the final hash covers the whole file. */
private suspend fun feedPrefix(file: File, bytes: Long, digest: MessageDigest) {
    file.inputStream().use { input ->
        val buffer = ByteArray(BUFFER_SIZE)
        var remaining = bytes
        while (remaining > 0L) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw IOException("Частичный файл короче ожидаемого.")
            digest.update(buffer, 0, read)
            remaining -= read
        }
    }
}

/** SHA-256 of a whole file, hex, computed off the main thread by the caller. */
internal suspend fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toHex()
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
