package com.beykus.beygram.data.export

import com.beykus.beygram.domain.model.ChatExportFormat
import com.beykus.beygram.domain.model.ChatExportPhase
import com.beykus.beygram.domain.model.ChatExportProgress
import com.beykus.beygram.domain.model.ChatExportRequest
import com.beykus.beygram.domain.model.ChatExportSummary
import com.beykus.beygram.domain.model.ChatExportTarget
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Large pages keep the number of TDLib requests low; TDLib may return fewer messages than requested. */
internal const val CHAT_EXPORT_PAGE_SIZE = 100

/** Limits keep media downloads bounded independently of the history size. */
internal const val CHAT_EXPORT_MAX_MEDIA_FILES = 10_000
internal const val CHAT_EXPORT_MAX_SINGLE_DOWNLOAD_BYTES = 25L * 1024 * 1024
internal const val CHAT_EXPORT_MAX_TOTAL_DOWNLOAD_BYTES = 100L * 1024 * 1024

/**
 * Streams one format directly into [target]. Nothing is buffered in the app cache: when media is requested
 * the target is a ZIP containing the message document and a `media/` folder. A failed or cancelled export
 * closes the stream and calls [ChatExportTarget.discard], so an incomplete file is removed or truncated.
 */
internal class ChatExportStreamer(
    private val target: ChatExportTarget,
    private val source: ChatExportPageSource,
    private val mediaProvider: ChatExportMediaProvider
) {
    suspend fun export(
        chat: ChatExportChat,
        request: ChatExportRequest,
        onProgress: (ChatExportProgress) -> Unit,
        totalMessages: Int? = null
    ): ChatExportSummary {
        var success = false
        var rawOutput: OutputStream? = null
        try {
            require(request.hasValidDateRange) { "Invalid export date range" }
            val output = target.openOutput()
            rawOutput = output
            val counting = CountingOutputStream(output)
            onProgress(ChatExportProgress(phase = ChatExportPhase.Preparing, totalMessages = totalMessages))

            val linkedMedia = HashMap<Long, CopiedMedia>()
            val mediaStats: MediaStats
            val documentStats: DocumentStats
            if (request.includeMedia) {
                val zipResult = writeZip(counting, chat, request, linkedMedia, onProgress, totalMessages)
                mediaStats = zipResult.media
                documentStats = zipResult.document
            } else {
                mediaStats = MediaStats(0, 0)
                documentStats = writePlain(counting, chat, request, linkedMedia, mediaStats, onProgress, totalMessages)
            }

            onProgress(
                ChatExportProgress(
                    phase = ChatExportPhase.Finishing,
                    messagesWritten = documentStats.messages,
                    mediaCopied = mediaStats.copied,
                    bytesWritten = counting.bytesWritten,
                    totalMessages = totalMessages
                )
            )
            success = true
            return ChatExportSummary(
                messages = documentStats.messages,
                mediaFiles = mediaStats.copied,
                bytesWritten = counting.bytesWritten,
                skippedSaveRestricted = documentStats.skippedSaveRestricted,
                skippedMedia = mediaStats.skipped
            )
        } finally {
            if (!success) {
                runCatching { rawOutput?.close() }
                withContext(NonCancellable) { runCatching { target.discard() } }
            }
        }
    }

    private suspend fun writePlain(
        counting: CountingOutputStream,
        chat: ChatExportChat,
        request: ChatExportRequest,
        linkedMedia: Map<Long, CopiedMedia>,
        mediaStats: MediaStats,
        onProgress: (ChatExportProgress) -> Unit,
        totalMessages: Int?
    ): DocumentStats {
        val writer = BufferedWriter(OutputStreamWriter(counting, Charsets.UTF_8))
        var closed = false
        try {
            val stats = writeDocument(
                writer, chat, request, linkedMedia, mediaStats, counting, onProgress, totalMessages
            )
            writer.flush()
            writer.close()
            closed = true
            return stats
        } finally {
            if (!closed) runCatching { writer.close() }
        }
    }

    private suspend fun writeZip(
        counting: CountingOutputStream,
        chat: ChatExportChat,
        request: ChatExportRequest,
        linkedMedia: MutableMap<Long, CopiedMedia>,
        onProgress: (ChatExportProgress) -> Unit,
        totalMessages: Int?
    ): ZipResult {
        val zip = ZipOutputStream(BufferedOutputStream(counting, 64 * 1024), Charsets.UTF_8)
        var closed = false
        try {
            onProgress(
                ChatExportProgress(phase = ChatExportPhase.Media, bytesWritten = counting.bytesWritten, totalMessages = totalMessages)
            )
            val mediaStats = copyMedia(zip, request, linkedMedia, counting, onProgress, totalMessages)
            onProgress(
                ChatExportProgress(
                    phase = ChatExportPhase.Messages,
                    mediaCopied = mediaStats.copied,
                    bytesWritten = counting.bytesWritten,
                    totalMessages = totalMessages
                )
            )
            val entryName = "messages.${request.format.extension}"
            zip.putNextEntry(ZipEntry(entryName))
            val writer = BufferedWriter(OutputStreamWriter(zip, Charsets.UTF_8))
            val documentStats = writeDocument(
                writer, chat, request, linkedMedia, mediaStats, counting, onProgress, totalMessages
            )
            writer.flush()
            zip.closeEntry()
            zip.finish()
            zip.close()
            closed = true
            return ZipResult(documentStats, mediaStats)
        } finally {
            if (!closed) runCatching { zip.close() }
        }
    }

    private suspend fun copyMedia(
        zip: ZipOutputStream,
        request: ChatExportRequest,
        linkedMedia: MutableMap<Long, CopiedMedia>,
        counting: CountingOutputStream,
        onProgress: (ChatExportProgress) -> Unit,
        totalMessages: Int?
    ): MediaStats {
        var copied = 0
        var skipped = 0
        walkExportable(
            request = request,
            onPage = {
                onProgress(
                    ChatExportProgress(
                        phase = ChatExportPhase.Media,
                        messagesWritten = 0,
                        mediaCopied = copied,
                        bytesWritten = counting.bytesWritten,
                        totalMessages = totalMessages
                    )
                )
            }
        ) { message ->
            val media = message.media ?: return@walkExportable
            if (copied >= CHAT_EXPORT_MAX_MEDIA_FILES) {
                skipped++
                return@walkExportable
            }
            val stream = try {
                mediaProvider.open(media)
            } catch (timeout: TimeoutCancellationException) {
                // A single slow TDLib file request skips this attachment; export cancellation still propagates.
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (stream == null) {
                skipped++
                return@walkExportable
            }
            val entry = mediaEntryName(message.id, media)
            zip.putNextEntry(ZipEntry(entry))
            stream.use { copyToZip(it, zip) }
            zip.closeEntry()
            linkedMedia[message.id] = CopiedMedia(media.fileId, entry)
            copied++
            onProgress(
                ChatExportProgress(
                    phase = ChatExportPhase.Media,
                    mediaCopied = copied,
                    bytesWritten = counting.bytesWritten,
                    totalMessages = totalMessages
                )
            )
        }
        return MediaStats(copied, skipped)
    }

    private suspend fun writeDocument(
        writer: Writer,
        chat: ChatExportChat,
        request: ChatExportRequest,
        linkedMedia: Map<Long, CopiedMedia>,
        mediaStats: MediaStats,
        counting: CountingOutputStream,
        onProgress: (ChatExportProgress) -> Unit,
        totalMessages: Int?
    ): DocumentStats = when (request.format) {
        ChatExportFormat.Json -> writeJson(
            writer, chat, request, linkedMedia, mediaStats, counting, onProgress, totalMessages
        )
        ChatExportFormat.Html -> writeHtml(
            writer, chat, request, linkedMedia, mediaStats, counting, onProgress, totalMessages
        )
    }

    private suspend fun writeJson(
        writer: Writer,
        chat: ChatExportChat,
        request: ChatExportRequest,
        linkedMedia: Map<Long, CopiedMedia>,
        mediaStats: MediaStats,
        counting: CountingOutputStream,
        onProgress: (ChatExportProgress) -> Unit,
        totalMessages: Int?
    ): DocumentStats {
        writer.write("{\n")
        writer.write("\"format\":")
        appendJsonString(writer, "beygram-chat-export")
        writer.write(",\n\"version\":1,\n\"exportedAt\":")
        appendJsonString(writer, Instant.now().toString())
        writer.write(",\n\"sourceAccount\":")
        appendJsonString(writer, request.accountLabel)
        writer.write(",\n\"chat\":{\"id\":")
        writer.write(chat.id.toString())
        writer.write(",\"title\":")
        appendJsonString(writer, chat.title)
        writer.write(",\"kind\":")
        appendJsonString(writer, chat.kind)
        writer.write("},\n\"range\":{\"from\":")
        writer.write(request.fromDateSeconds?.toString() ?: "null")
        writer.write(",\"to\":")
        writer.write(request.toDateSeconds?.toString() ?: "null")
        writer.write("},\n\"mediaRequested\":")
        writer.write(request.includeMedia.toString())
        writer.write(",\n\"messages\":[")
        var first = true
        val stats = walkExportable(
            request = request,
            onPage = { written ->
                onProgress(
                    ChatExportProgress(
                        phase = ChatExportPhase.Messages,
                        messagesWritten = written,
                        mediaCopied = mediaStats.copied,
                        bytesWritten = counting.bytesWritten,
                        totalMessages = totalMessages
                    )
                )
            }
        ) { message ->
            if (!first) writer.write(",")
            writer.write("\n")
            writeJsonMessage(
                writer,
                message,
                linkedMedia[message.id]?.takeIf { it.fileId == message.media?.fileId }?.entry
            )
            first = false
        }
        writer.write("\n],\n\"summary\":{\"messages\":")
        writer.write(stats.messages.toString())
        writer.write(",\"mediaFiles\":")
        writer.write(mediaStats.copied.toString())
        writer.write(",\"skippedSaveRestricted\":")
        writer.write(stats.skippedSaveRestricted.toString())
        writer.write(",\"skippedMedia\":")
        writer.write(mediaStats.skipped.toString())
        writer.write("}\n}\n")
        return stats
    }

    private fun writeJsonMessage(writer: Writer, message: ChatExportMessage, mediaEntry: String?) {
        writer.write("{\"id\":")
        writer.write(message.id.toString())
        writer.write(",\"date\":")
        writer.write(message.dateSeconds.toString())
        writer.write(",\"dateIso\":")
        appendJsonString(writer, exportIsoTimestamp(message.dateSeconds))
        writer.write(",\"outgoing\":")
        writer.write(message.isOutgoing.toString())
        writer.write(",\"sender\":{\"id\":")
        writer.write(message.senderId.toString())
        writer.write(",\"name\":")
        appendJsonString(writer, message.senderName)
        writer.write("},\"text\":")
        appendJsonString(writer, message.text)
        writer.write(",\"edited\":")
        writer.write(message.isEdited.toString())
        writer.write(",\"service\":")
        writer.write((message.serviceText != null).toString())
        writer.write(",\"replyToMessageId\":")
        writer.write(message.replyToMessageId.takeIf { it > 0L }?.toString() ?: "null")
        writer.write(",\"media\":")
        val media = message.media
        if (media == null) {
            writer.write("null")
        } else {
            writer.write("{\"kind\":")
            appendJsonString(writer, media.kind.name)
            writer.write(",\"fileName\":")
            appendJsonString(writer, media.fileName)
            writer.write(",\"mimeType\":")
            appendJsonString(writer, media.mimeType)
            writer.write(",\"bytes\":")
            writer.write(media.size.toString())
            writer.write(",\"durationSeconds\":")
            writer.write(media.durationSeconds.toString())
            writer.write(",\"width\":")
            writer.write(media.width.toString())
            writer.write(",\"height\":")
            writer.write(media.height.toString())
            writer.write(",\"exportedPath\":")
            if (mediaEntry != null) appendJsonString(writer, mediaEntry) else writer.write("null")
            writer.write("}")
        }
        writer.write("}")
    }

    private suspend fun writeHtml(
        writer: Writer,
        chat: ChatExportChat,
        request: ChatExportRequest,
        linkedMedia: Map<Long, CopiedMedia>,
        mediaStats: MediaStats,
        counting: CountingOutputStream,
        onProgress: (ChatExportProgress) -> Unit,
        totalMessages: Int?
    ): DocumentStats {
        writer.write("<!doctype html>\n<html lang=\"und\">\n<head>\n<meta charset=\"utf-8\">\n")
        writer.write("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n<meta name=\"generator\" content=\"BeyGram\">\n<title>")
        appendHtmlEscaped(writer, chat.title)
        writer.write("</title>\n<style>")
        writer.write(HTML_STYLE)
        writer.write("</style>\n</head>\n<body>\n<main>\n<header class=\"chat-header\">\n<h1>")
        appendHtmlEscaped(writer, chat.title)
        writer.write("</h1>\n<p class=\"meta\">Source account: ")
        appendHtmlEscaped(writer, request.accountLabel)
        writer.write("</p>\n<p class=\"meta\">Exported ")
        appendHtmlEscaped(writer, Instant.now().toString())
        writer.write("</p>\n<p class=\"meta\">Date range: ")
        appendHtmlEscaped(writer, htmlDateRange(request))
        writer.write("</p>\n<p class=\"warning\">This file contains private chat data and is not protected by BeyGram. Store it securely.</p>\n</header>\n<section class=\"messages\">\n")
        val stats = walkExportable(
            request = request,
            onPage = { written ->
                onProgress(
                    ChatExportProgress(
                        phase = ChatExportPhase.Messages,
                        messagesWritten = written,
                        mediaCopied = mediaStats.copied,
                        bytesWritten = counting.bytesWritten,
                        totalMessages = totalMessages
                    )
                )
            }
        ) { message ->
            writeHtmlMessage(
                writer,
                message,
                linkedMedia[message.id]?.takeIf { it.fileId == message.media?.fileId }?.entry
            )
        }
        writer.write("</section>\n<footer class=\"chat-footer\">\n<p>")
        appendHtmlEscaped(
            writer,
            "Exported ${stats.messages} messages and ${mediaStats.copied} media files. " +
                "Skipped ${stats.skippedSaveRestricted} save-restricted messages and ${mediaStats.skipped} unavailable media files."
        )
        writer.write("</p>\n</footer>\n</main>\n</body>\n</html>\n")
        return stats
    }

    private fun writeHtmlMessage(writer: Writer, message: ChatExportMessage, mediaEntry: String?) {
        writer.write("<article class=\"message ")
        writer.write(if (message.isOutgoing) "outgoing" else "incoming")
        if (message.serviceText != null) writer.write(" service")
        writer.write("\">\n<header><span class=\"sender\">")
        appendHtmlEscaped(writer, message.senderName.ifBlank { "Unknown sender" })
        writer.write("</span> <time datetime=\"")
        appendHtmlEscaped(writer, exportIsoTimestamp(message.dateSeconds))
        writer.write("\">")
        appendHtmlEscaped(writer, formatExportTimestamp(message.dateSeconds))
        writer.write("</time>")
        if (message.isEdited) writer.write(" <span class=\"edited\">edited</span>")
        writer.write("</header>\n")
        val text = message.serviceText ?: message.text
        if (text.isNotBlank()) {
            writer.write("<div class=\"text\">")
            appendHtmlEscaped(writer, text)
            writer.write("</div>\n")
        }
        message.media?.let { media ->
            writer.write("<figure class=\"attachment\">")
            if (mediaEntry != null) {
                val entry = mediaEntry
                if (media.mimeType.startsWith("image/")) {
                    writer.write("<img loading=\"lazy\" src=\"")
                    appendHtmlEscaped(writer, exportMediaHref(entry))
                    writer.write("\" alt=\"")
                    appendHtmlEscaped(writer, media.fileName.ifBlank { media.kind.name })
                    writer.write("\">")
                } else {
                    writer.write("<a href=\"")
                    appendHtmlEscaped(writer, exportMediaHref(entry))
                    writer.write("\">")
                    appendHtmlEscaped(writer, media.fileName.ifBlank { media.kind.name })
                    writer.write("</a>")
                }
            }
            writer.write("<figcaption>")
            appendHtmlEscaped(
                writer,
                listOf(
                    media.kind.name,
                    media.fileName.ifBlank { "attachment" },
                    if (media.size > 0) "${media.size} bytes" else ""
                ).filter { it.isNotBlank() }.joinToString(" · ")
            )
            if (mediaEntry == null) {
                writer.write(" <span class=\"missing\">not included</span>")
            }
            writer.write("</figcaption></figure>\n")
        }
        writer.write("</article>\n")
    }

    /**
     * Walks every page once, newest to oldest, in a bounded window. Messages above [ChatExportRequest.toDateSeconds]
     * are skipped; the walk stops as soon as it passes below the inclusive from-bound. Save-restricted messages are
     * counted and never passed to [onMessage].
     */
    private suspend fun walkExportable(
        request: ChatExportRequest,
        onPage: suspend (written: Int) -> Unit,
        onMessage: suspend (ChatExportMessage) -> Unit
    ): DocumentStats {
        var cursor = 0L
        var lastId = Long.MAX_VALUE
        var written = 0
        var skipped = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val page = source.page(cursor, CHAT_EXPORT_PAGE_SIZE)
            var reachedFromBoundary = false
            for (message in page.messages.sortedByDescending { it.id }) {
                if (message.id >= lastId) continue
                lastId = message.id
                val from = request.fromDateSeconds
                if (from != null && message.dateSeconds < from) {
                    reachedFromBoundary = true
                    break
                }
                if (!request.includesDate(message.dateSeconds)) continue
                if (!message.canBeSaved) {
                    skipped++
                    continue
                }
                onMessage(message)
                written++
            }
            onPage(written)
            if (reachedFromBoundary || !page.hasMore) break
            val next = page.nextBeforeMessageId
            if (next == 0L || next == cursor || (cursor != 0L && next >= cursor)) break
            cursor = next
        }
        return DocumentStats(written, skipped)
    }

    private suspend fun copyToZip(input: InputStream, zip: ZipOutputStream) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            zip.write(buffer, 0, read)
        }
    }

    private fun htmlDateRange(request: ChatExportRequest): String = when {
        request.fromDateSeconds == null && request.toDateSeconds == null -> "all messages"
        request.fromDateSeconds == null -> "until ${formatExportTimestamp(request.toDateSeconds ?: 0L)}"
        request.toDateSeconds == null -> "from ${formatExportTimestamp(request.fromDateSeconds ?: 0L)}"
        else -> "${formatExportTimestamp(request.fromDateSeconds ?: 0L)} – ${formatExportTimestamp(request.toDateSeconds ?: 0L)}"
    }

    private data class DocumentStats(val messages: Int, val skippedSaveRestricted: Int)
    private data class MediaStats(val copied: Int, val skipped: Int)
    private data class CopiedMedia(val fileId: Int, val entry: String)
    private data class ZipResult(val document: DocumentStats, val media: MediaStats)

    private companion object {
        val HTML_STYLE = """
            :root { color-scheme: light dark; }
            body { margin: 0; font-family: system-ui, -apple-system, "Segoe UI", sans-serif; background: #f4f5f7; color: #17181a; }
            main { max-width: 860px; margin: 0 auto; padding: 24px 16px 48px; }
            .chat-header, .chat-footer { background: #fff; border-radius: 16px; padding: 18px 20px; margin-bottom: 16px; }
            h1 { font-size: 22px; margin: 0 0 8px; }
            .meta { color: #5f6368; font-size: 13px; margin: 3px 0; }
            .warning { color: #7a4b00; background: #fff4d6; border-radius: 10px; padding: 10px 12px; font-size: 13px; margin: 12px 0 0; }
            .messages { display: flex; flex-direction: column; gap: 10px; }
            .message { background: #fff; border-radius: 14px; padding: 12px 14px; max-width: 78%; }
            .message.outgoing { align-self: flex-end; background: #dcf2e6; }
            .message.service { align-self: center; max-width: 90%; background: #e7e9ed; color: #4b4f55; text-align: center; }
            .message header { display: flex; gap: 8px; align-items: baseline; margin-bottom: 5px; font-size: 12px; color: #5f6368; }
            .sender { font-weight: 600; color: #1f6f4d; }
            .text { white-space: pre-wrap; overflow-wrap: anywhere; font-size: 15px; line-height: 1.42; }
            .attachment { margin: 10px 0 0; }
            .attachment img { display: block; max-width: 100%; max-height: 420px; border-radius: 10px; }
            .attachment figcaption { font-size: 12px; color: #5f6368; margin-top: 4px; }
            .missing { color: #8a6d3b; }
            .chat-footer { color: #5f6368; font-size: 12px; }
            @media (prefers-color-scheme: dark) {
                body { background: #101214; color: #e8eaed; }
                .chat-header, .chat-footer, .message { background: #1c1f23; }
                .message.outgoing { background: #153c2c; }
                .message.service { background: #262a2f; color: #c2c7ce; }
                .sender { color: #7fd4ab; }
                .warning { background: #3a2c10; color: #ffe08a; }
                .meta, .message header, .attachment figcaption, .chat-footer { color: #aeb4bc; }
            }
        """.trimIndent()
    }
}

/** Counts bytes actually handed to the user-selected stream, including ZIP compression. */
internal class CountingOutputStream(output: OutputStream) : FilterOutputStream(output) {
    var bytesWritten: Long = 0L
        private set

    override fun write(byte: Int) {
        out.write(byte)
        bytesWritten++
    }

    override fun write(bytes: ByteArray) = write(bytes, 0, bytes.size)

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        out.write(bytes, offset, length)
        bytesWritten += length
    }

    override fun flush() = out.flush()

    override fun close() = out.close()
}
