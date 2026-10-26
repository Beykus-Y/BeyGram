package com.beykus.beygram.playback

import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueSourceTest {
    private fun source(
        onManual: (Int) -> Unit = {},
        onAuto: ((AutoDownloadRequest) -> Unit)? = null
    ) = PlaybackQueueSource(
        accountId = "account",
        observeFile = { fileId -> MutableStateFlow(MediaFileState(fileId)) },
        download = onManual,
        autoDownload = onAuto
    )

    private val request = AutoDownloadRequest(fileId = 7, kind = MediaKind.Audio, sizeBytes = 1_000L)

    @Test
    fun prefetchUsesTheAutomaticPathAndNeverTheManualOne() {
        var manual = 0
        var automatic = 0
        val queueSource = source(onManual = { manual++ }, onAuto = { automatic++ })

        assertTrue(queueSource.prefetch(request))
        assertEquals(0, manual)
        assertEquals(1, automatic)

        queueSource.download(7)
        assertEquals(1, manual)
        assertEquals(1, automatic)
    }

    @Test
    fun prefetchWithoutAnAutomaticCallbackIsANoOp() {
        var manual = 0
        val queueSource = source(onManual = { manual++ })

        assertFalse(queueSource.prefetch(request))
        assertEquals(0, manual)
    }
}
