package com.beykus.beygram.data.export

import com.beykus.beygram.domain.model.ChatExportFormat
import com.beykus.beygram.domain.model.ChatExportPhase
import com.beykus.beygram.domain.model.ChatExportProgress
import com.beykus.beygram.domain.model.ChatExportRequest
import com.beykus.beygram.domain.model.ChatExportTarget
import com.beykus.beygram.domain.model.MediaKind
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatExportStreamerTest {
    private class RecordingTarget : ChatExportTarget {
        var closed = false
        val bytes = object : ByteArrayOutputStream() {
            override fun close() {
                closed = true
                super.close()
            }
        }
        var discardCalls = 0

        override suspend fun openOutput(): OutputStream = bytes
        override suspend fun discard() {
            discardCalls++
        }
    }

    private object NoMediaProvider : ChatExportMediaProvider {
        override suspend fun open(media: ChatExportMedia): InputStream? = null
    }

    private class FakeSource(
        private val pages: List<ChatExportPage>,
        private val failOnCursor: Long? = null
    ) : ChatExportPageSource {
        override suspend fun page(beforeMessageId: Long, limit: Int): ChatExportPage {
            if (beforeMessageId == failOnCursor) throw CancellationException("cancelled by test")
            if (beforeMessageId == 0L) return pages.firstOrNull() ?: ChatExportPage(emptyList(), 0, false)
            val previousIndex = pages.indexOfFirst { it.nextBeforeMessageId == beforeMessageId }
            return pages.getOrElse(previousIndex + 1) { ChatExportPage(emptyList(), 0, false) }
        }
    }

    private fun message(id: Long, date: Long, text: String = "message $id", canBeSaved: Boolean = true) =
        ChatExportMessage(
            id = id,
            dateSeconds = date,
            isOutgoing = id % 2L == 0L,
            senderId = 10L,
            senderName = "Sender",
            text = text,
            canBeSaved = canBeSaved
        )

    private fun page(messages: List<ChatExportMessage>, next: Long, more: Boolean) =
        ChatExportPage(messages, next, more)

    private fun chat() = ChatExportChat(1L, "Project", "group")

    @Test fun `streams bounded pages into valid json and reports progress`() = runBlocking {
        val target = RecordingTarget()
        val source = FakeSource(
            listOf(
                page(listOf(message(3, 300), message(2, 200)), 2, true),
                page(listOf(message(1, 100)), 0, false)
            )
        )
        val progress = mutableListOf<ChatExportProgress>()
        val summary = ChatExportStreamer(target, source, NoMediaProvider).export(
            chat = chat(),
            request = ChatExportRequest(format = ChatExportFormat.Json, accountLabel = "Me"),
            onProgress = { progress += it },
            totalMessages = 3
        )

        assertEquals(3, summary.messages)
        assertEquals(0, target.discardCalls)
        assertTrue(target.closed)
        assertEquals(3, progress.filter { it.phase == ChatExportPhase.Messages }.maxOf { it.messagesWritten })
        val json = JSONObject(String(target.bytes.toByteArray(), Charsets.UTF_8))
        assertEquals(3, json.getJSONArray("messages").length())
        assertEquals("Me", json.getString("sourceAccount"))
        assertEquals(3, json.getJSONObject("summary").getInt("messages"))
    }

    @Test fun `date range is applied locally and stops at the old boundary`() = runBlocking {
        val target = RecordingTarget()
        val source = FakeSource(
            listOf(
                page(listOf(message(4, 300), message(3, 200)), 3, true),
                page(listOf(message(2, 100), message(1, 50)), 0, false)
            )
        )
        val summary = ChatExportStreamer(target, source, NoMediaProvider).export(
            chat = chat(),
            request = ChatExportRequest(fromDateSeconds = 100L, toDateSeconds = 250L),
            onProgress = {}
        )

        assertEquals(2, summary.messages)
        assertEquals(0, target.discardCalls)
        val json = JSONObject(String(target.bytes.toByteArray(), Charsets.UTF_8))
        val messages = json.getJSONArray("messages")
        assertEquals(2, messages.length())
        assertEquals(200L, messages.getJSONObject(0).getLong("date"))
        assertEquals(100L, messages.getJSONObject(1).getLong("date"))
    }

    @Test fun `save restricted messages are omitted and counted`() = runBlocking {
        val target = RecordingTarget()
        val source = FakeSource(
            listOf(page(listOf(message(2, 200, canBeSaved = false), message(1, 100)), 0, false))
        )
        val summary = ChatExportStreamer(target, source, NoMediaProvider).export(
            chat = chat(),
            request = ChatExportRequest(),
            onProgress = {}
        )

        assertEquals(1, summary.messages)
        assertEquals(1, summary.skippedSaveRestricted)
        val json = JSONObject(String(target.bytes.toByteArray(), Charsets.UTF_8))
        assertEquals(1, json.getJSONArray("messages").length())
        assertEquals(1, json.getJSONObject("summary").getInt("skippedSaveRestricted"))
    }

    @Test fun `html export escapes message text and stays a complete document`() = runBlocking {
        val target = RecordingTarget()
        val source = FakeSource(listOf(page(listOf(message(1, 100, "<b>& \"quoted\"")), 0, false)))
        ChatExportStreamer(target, source, NoMediaProvider).export(
            chat = chat(),
            request = ChatExportRequest(format = ChatExportFormat.Html, accountLabel = "Me"),
            onProgress = {}
        )

        val html = String(target.bytes.toByteArray(), Charsets.UTF_8)
        assertTrue(html.startsWith("<!doctype html>"))
        assertTrue(html.contains("&lt;b&gt;&amp; &quot;quoted&quot;"))
        assertFalse(html.contains("<b>& \"quoted\""))
        assertTrue(html.contains("Source account: Me"))
    }

    @Test fun `media export is a zip with a streamed media entry`() = runBlocking {
        val target = RecordingTarget()
        val media = ChatExportMedia(MediaKind.Photo, 7, "", "image/jpeg", size = 3)
        val message = message(1, 100).copy(media = media)
        val source = FakeSource(listOf(page(listOf(message), 0, false)))
        val provider = object : ChatExportMediaProvider {
            override suspend fun open(media: ChatExportMedia) = ByteArrayInputStream(byteArrayOf(1, 2, 3))
        }
        val progress = mutableListOf<ChatExportProgress>()

        val summary = ChatExportStreamer(target, source, provider).export(
            chat = chat(),
            request = ChatExportRequest(includeMedia = true),
            onProgress = { progress += it }
        )

        assertEquals(1, summary.mediaFiles)
        assertTrue(progress.any { it.phase == ChatExportPhase.Media && it.mediaCopied == 1 })
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(target.bytes.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        assertEquals(setOf("media/1_photo.jpg", "messages.json"), entries.keys)
        assertEquals(listOf<Byte>(1, 2, 3), entries.getValue("media/1_photo.jpg").toList())
        val json = JSONObject(String(entries.getValue("messages.json"), Charsets.UTF_8))
        assertEquals("media/1_photo.jpg", json.getJSONArray("messages").getJSONObject(0).getJSONObject("media").getString("exportedPath"))
    }

    @Test fun `cancellation closes the stream and discards the incomplete target`() = runBlocking {
        val target = RecordingTarget()
        val source = FakeSource(listOf(page(listOf(message(1, 100)), 1, true)), failOnCursor = 1)
        val failure = try {
            ChatExportStreamer(target, source, NoMediaProvider).export(
                chat = chat(),
                request = ChatExportRequest(),
                onProgress = {}
            )
            null
        } catch (cancelled: CancellationException) {
            cancelled
        }

        assertNotNull(failure)
        assertTrue(target.closed)
        assertEquals(1, target.discardCalls)
    }

    @Test fun `media provider that cannot open a file keeps metadata only`() = runBlocking {
        val target = RecordingTarget()
        val media = ChatExportMedia(MediaKind.Document, 8, "report.pdf", "application/pdf", size = 20)
        val source = FakeSource(listOf(page(listOf(message(1, 100).copy(media = media)), 0, false)))
        val summary = ChatExportStreamer(target, source, NoMediaProvider).export(
            chat = chat(),
            request = ChatExportRequest(includeMedia = true),
            onProgress = {}
        )

        assertEquals(0, summary.mediaFiles)
        assertEquals(1, summary.skippedMedia)
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(target.bytes.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        assertEquals(setOf("messages.json"), entries.keys)
        val json = JSONObject(String(entries.getValue("messages.json"), Charsets.UTF_8))
        assertTrue(json.getJSONArray("messages").getJSONObject(0).getJSONObject("media").isNull("exportedPath"))
    }
}
