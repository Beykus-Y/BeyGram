package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.model.VideoNoteLossRemoteResult
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.TdApi

/**
 * One opt-in probe at a time. SendMessage returning a local message is not delivery:
 * we await TDLib's UpdateMessageSendSucceeded, then retrieve the file by its remote ID.
 */
internal class TdVideoNoteLossTransport(private val request: TdRequests) {
    private val gate = Mutex()
    private var watcher: Watcher? = null
    private val ownedTestMessages = mutableSetOf<Long>()
    private var ownedChatId: Long? = null

    private class Watcher(val chatId: Long) {
        val outcomes = linkedMapOf<Long, Result<TdApi.Message>>()
        val awaiters = mutableMapOf<Long, CompletableDeferred<TdApi.Message>>()
    }

    @Synchronized
    fun onSendSucceeded(update: TdApi.UpdateMessageSendSucceeded) {
        val w = watcher ?: return
        if (update.message.chatId != w.chatId) return
        val result = Result.success(update.message)
        w.outcomes[update.oldMessageId] = result
        w.awaiters[update.oldMessageId]?.complete(update.message)
        if (w.outcomes.size > 16) w.outcomes.remove(w.outcomes.keys.first())
    }

    @Synchronized
    fun onSendFailed(update: TdApi.UpdateMessageSendFailed) {
        val w = watcher ?: return
        if (update.message.chatId != w.chatId) return
        val error = (update.message.sendingState as? TdApi.MessageSendingStateFailed)?.error
        val failure = TdRequestError(error?.code ?: 0, error?.message ?: "Telegram rejected the test message")
        w.outcomes[update.oldMessageId] = Result.failure(failure)
        w.awaiters[update.oldMessageId]?.completeExceptionally(failure)
        if (w.outcomes.size > 16) w.outcomes.remove(w.outcomes.keys.first())
    }

    private suspend fun awaitSent(w: Watcher, temporaryMessageId: Long): TdApi.Message {
        val deferred = synchronized(this) {
            w.outcomes[temporaryMessageId]?.let { return it.getOrThrow() }
            w.awaiters.getOrPut(temporaryMessageId) { CompletableDeferred() }
        }
        return try {
            withTimeout(90_000L) { deferred.await() }
        } finally {
            synchronized(this) { w.awaiters.remove(temporaryMessageId) }
        }
    }

    suspend fun sendAndFetchOwnTest(media: OutgoingMedia, destination: File): VideoNoteLossRemoteResult = gate.withLock {
        require(media.kind == MediaKind.VideoNote && media.mimeType == "video/mp4") { "Diagnostic must send an MP4 video note" }
        require(media.width == media.height && media.width in 1..640 && media.durationSeconds in 1..60)
        require(File(media.path).isFile && File(media.path).length() > 0)
        // GetMe must succeed before anything is uploaded. A private chat with oneself is Saved Messages.
        val myUserId = request(TdApi.GetMe()).id
        val savedChatId = request(TdApi.CreatePrivateChat(myUserId, false)).id
        check(savedChatId == myUserId) { "Saved Messages chat ID differs from self user ID" }
        val w = Watcher(savedChatId)
        synchronized(this) { watcher = w }
        try {
            val pending = request(TdApi.SendMessage().apply {
                chatId = savedChatId
                inputMessageContent = media.toInputContent("")
            })
            val sent = if (pending.sendingState == null) pending else awaitSent(w, pending.id)
            val videoNote = (sent.content as? TdApi.MessageVideoNote)?.videoNote
                ?: error("Sent message is not a Telegram video note")
            val file = videoNote.video ?: error("Confirmed video note contains no video file")
            // Store only IDs that this diagnostic sent, never expose arbitrary message deletion to UI.
            synchronized(this) {
                ownedChatId = savedChatId
                ownedTestMessages += sent.id
                if (ownedTestMessages.size > 12) ownedTestMessages.remove(ownedTestMessages.first())
            }

            val remoteId = file.remote?.id.orEmpty()
            if (remoteId.isBlank()) return@withLock VideoNoteLossRemoteResult(
                sent.id, null, videoNote.length, file.size.toLong(),
                "Remote ID unavailable after confirmed send; independent verification impossible", false
            )
            // Evict TDLib's cached file before registering the remote identifier.
            // Without observed eviction, an apparent byte-for-byte match is NOT proof of server delivery.
            val evicted = runCatching { request(TdApi.DeleteFile(file.id)); true }.getOrDefault(false)
            val registered = runCatching { request(TdApi.GetRemoteFile(remoteId, null)) }.getOrElse {
                return@withLock VideoNoteLossRemoteResult(sent.id, null, videoNote.length, file.size.toLong(),
                    "GetRemoteFile failed: ${it.javaClass.simpleName}; not verified", false)
            }
            val cachedBeforeDownload = registered.local?.isDownloadingCompleted == true
            val downloaded = runCatching {
                request(TdApi.DownloadFile(registered.id, 32, 0, 0, true), 90_000L)
            }.getOrElse {
                return@withLock VideoNoteLossRemoteResult(sent.id, null, videoNote.length, file.size.toLong(),
                    "Remote download failed: ${it.javaClass.simpleName}; not verified", false)
            }
            val remotePath = downloaded.local?.path.orEmpty()
            val independent = evicted && !cachedBeforeDownload && downloaded.local?.isDownloadingCompleted == true &&
                remotePath.isNotBlank() && remotePath != media.path && remotePath != file.local?.path.orEmpty() &&
                File(remotePath).isFile && File(remotePath).length() > 0
            val existingCopy = File(remotePath).takeIf { it.isFile && it.length() > 0 }
            if (existingCopy != null) {
                destination.parentFile?.mkdirs()
                existingCopy.copyTo(destination, overwrite = true)
            }
            VideoNoteLossRemoteResult(
                messageId = sent.id,
                remoteCopyPath = destination.takeIf { it.isFile && it.length() > 0 }?.absolutePath,
                declaredSide = videoNote.length,
                remoteFileBytes = file.size.toLong(),
                sourceEvidence = if (independent) "TDLib cache evicted → GetRemoteFile → synchronous DownloadFile (new local bytes)"
                    else "Independent remote read NOT proven (cache/eviction/source ambiguity)",
                independentDownloadVerified = independent
            )
        } finally {
            synchronized(this) { watcher = null }
        }
    }

    /** Only the exact message created by the probe can be removed, after separate UI confirmation. */
    suspend fun deleteOwnTest(messageId: Long): Result<Unit> = requestResult {
        val chatId = synchronized(this) {
            check(messageId in ownedTestMessages) { "Not an owned diagnostic message" }
            ownedChatId ?: error("Diagnostic ownership lost")
        }
        check(request(TdApi.GetMe()).id == chatId) { "Account changed" }
        request(TdApi.DeleteMessages(chatId, longArrayOf(messageId), false))
        synchronized(this) { ownedTestMessages.remove(messageId) }
        Unit
    }
}
