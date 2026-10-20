package com.beykus.beygram.data.media

import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNoteLossComparisonTest {
    private fun sample(width: Int = 480, fps: Double = 30.0, frames: Int = 90): VideoNoteFrameStats =
        VideoNoteFrameStats(width, width, frames, fps, fps.toInt(), 34.0, 0, 100_000, "video/avc")

    @Test fun byteEqualityFromCacheIsNotRemoteProof() {
        val result = compareVideoNoteLoss(sample(), sample(), sameSha256 = true, independent = false)
        assertTrue(result.contains("НЕ ПОДТВЕРЖДЕНО"))
        assertTrue(result.contains("нельзя доказать", ignoreCase = true))
    }

    @Test fun containerOnlyChangesDoNotClaimVisualDegradation() {
        val result = compareVideoNoteLoss(sample(), sample(), sameSha256 = false, independent = true)
        assertTrue(result.contains("байты/контейнер"))
        assertTrue(result.contains("НЕ доказана"))
    }

    @Test fun resolutionAndFpsChangesAreReportedAsBetweenStages() {
        val result = compareVideoNoteLoss(sample(), sample(width = 384, fps = 20.0, frames = 60), false, true)
        assertTrue(result.contains("480x480 → 384x384"))
        assertTrue(result.contains("Изменилось число кадров: ДА"))
        assertTrue(result.contains("инициатор перекодирования не доказаны"))
    }

    @Test fun missingDownloadedFileKeepsOutcomeUnknown() {
        val result = compareVideoNoteLoss(sample(), remote = null, sameSha256 = null, independent = false)
        assertTrue(result.contains("не удалось"))
        assertTrue(result.contains("неизвестно"))
    }

    @Test fun verifiedIdenticalRemoteIsOnlyByteEquality() {
        val result = compareVideoNoteLoss(sample(), sample(), true, true)
        assertTrue(result.contains("совпадают"))
    }
}
