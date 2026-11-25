package com.beykus.beygram.data.tdlib

import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TdPlaybackRangesTest {
    private class FakeRequests : TdRequests({ throw IllegalStateException("no client in tests") }) {
        var answer: TdApi.Object = TdApi.FileDownloadedPrefixSize(0L)
        val asked = mutableListOf<TdApi.Function<*>>()

        override fun handOff(function: TdApi.Function<*>, handler: Client.ResultHandler) {
            asked += function
            handler.onResult(answer)
        }
    }

    private fun ranges(requests: FakeRequests, sent: MutableList<TdApi.Function<*>>) =
        TdPlaybackRanges(requests) { function -> sent += function }

    @Test
    fun `availability is asked for the exact offset`() = runTest {
        val requests = FakeRequests().apply { answer = TdApi.FileDownloadedPrefixSize(4096L) }
        val sent = mutableListOf<TdApi.Function<*>>()

        val size = ranges(requests, sent).readableFrom(fileId = 12, offset = 1_048_576L)

        assertEquals(4096L, size)
        val asked = requests.asked.single() as TdApi.GetFileDownloadedPrefixSize
        assertEquals(12, asked.fileId)
        assertEquals(1_048_576L, asked.offset)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a negative offset is not asked at all`() = runTest {
        val requests = FakeRequests()
        val sent = mutableListOf<TdApi.Function<*>>()

        assertEquals(0L, ranges(requests, sent).readableFrom(12, -1L))
        assertTrue(requests.asked.isEmpty())
    }

    @Test
    fun `an unknown file id yields zero instead of an exception`() = runTest {
        val requests = FakeRequests()
        val sent = mutableListOf<TdApi.Function<*>>()

        assertEquals(0L, ranges(requests, sent).readableFrom(0, 0L))
        assertTrue(requests.asked.isEmpty())
    }

    @Test
    fun `watching uses the highest priority and no limit`() {
        val requests = FakeRequests()
        val sent = mutableListOf<TdApi.Function<*>>()

        ranges(requests, sent).requestWindow(fileId = 5, offset = 100L, limit = 0L, urgent = true)

        val download = sent.single() as TdApi.DownloadFile
        assertEquals(5, download.fileId)
        assertEquals(TdPlaybackRanges.PRIORITY_WATCHING, download.priority)
        assertEquals(100L, download.offset)
        assertEquals(0L, download.limit)
        assertEquals(false, download.synchronous)
    }

    @Test
    fun `a bounded tail window keeps its limit and the normal priority when not urgent`() {
        val requests = FakeRequests()
        val sent = mutableListOf<TdApi.Function<*>>()

        ranges(requests, sent).requestWindow(fileId = 5, offset = 900L, limit = 400L, urgent = false)

        val download = sent.single() as TdApi.DownloadFile
        assertEquals(TdPlaybackRanges.PRIORITY_NORMAL, download.priority)
        assertEquals(900L, download.offset)
        assertEquals(400L, download.limit)
    }

    @Test
    fun `cancelling only stops a pending download`() {
        val requests = FakeRequests()
        val sent = mutableListOf<TdApi.Function<*>>()

        ranges(requests, sent).cancelWindow(5)

        val cancel = sent.single() as TdApi.CancelDownloadFile
        assertEquals(5, cancel.fileId)
        assertTrue(cancel.onlyIfPending)
    }
}