package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoNoteRootCauseTest {
    private fun diagnose(e: VideoNoteStageEvidence) = diagnoseVideoNoteFps(e)

    @Test fun normal60NotAdvertisedIsReportedBeforeAnyMeasurement() {
        val d = diagnose(VideoNoteStageEvidence(targetFps = 60, normalHighRangeAdvertised = false, rawFps = 30.0))
        assertEquals(VideoNoteRootCause.NOT_ADVERTISED_IN_NORMAL_AE, d.cause)
        assertEquals(VideoNoteConfidence.ESTABLISHED, d.confidence)
    }

    @Test fun unsupportedSessionWins() {
        val d = diagnose(VideoNoteStageEvidence(60, normalHighRangeAdvertised = true, sessionSupported = false))
        assertEquals(VideoNoteRootCause.SESSION_COMBINATION_UNSUPPORTED, d.cause)
    }

    @Test fun sensorAt30WithAcceptedRangeIsSensorLimit() {
        val d = diagnose(VideoNoteStageEvidence(60, normalHighRangeAdvertised = true, sensorFps = 30.1))
        assertEquals(VideoNoteRootCause.SENSOR_OR_AE_OR_HAL_LIMIT, d.cause)
    }

    @Test fun sensor60ButComboAt30IsUseCaseCombination() {
        val d = diagnose(VideoNoteStageEvidence(60, sensorFps = 60.0, useCaseComboFps = 30.0))
        assertEquals(VideoNoteRootCause.USECASE_COMBINATION_OR_CAMERAX_FALLBACK, d.cause)
        assertEquals(VideoNoteConfidence.ESTABLISHED, d.confidence)
    }

    @Test fun combo60ButRawAt30IsRecorderOrEncoder() {
        val d = diagnose(VideoNoteStageEvidence(60, sensorFps = 60.0, useCaseComboFps = 60.0, rawFps = 30.0))
        assertEquals(VideoNoteRootCause.RECORDER_OR_ENCODER_BELOW_TARGET, d.cause)
        assertEquals(VideoNoteConfidence.ESTABLISHED, d.confidence)
    }

    @Test fun rawLowWithNothingElseMeasuredIsOnlyUnlocalized() {
        val d = diagnose(VideoNoteStageEvidence(60, rawFps = 30.0))
        assertEquals(VideoNoteRootCause.RAW_BELOW_TARGET_UNLOCALIZED, d.cause)
        assertEquals(VideoNoteConfidence.LIKELY, d.confidence)
    }

    @Test fun raw60ExportedAt30IsExportDrop() {
        val d = diagnose(VideoNoteStageEvidence(60, rawFps = 60.0, exportedFps = 30.0))
        assertEquals(VideoNoteRootCause.EXPORT_DROP_OR_FPS_CAP, d.cause)
        assertEquals(VideoNoteConfidence.ESTABLISHED, d.confidence)
    }

    @Test fun cachedRemoteFileNeverBlamesTelegram() {
        val d = diagnose(VideoNoteStageEvidence(60, rawFps = 60.0, exportedFps = 60.0, remoteFps = 30.0, remoteIndependent = false))
        assertEquals(VideoNoteRootCause.REMOTE_STATUS_UNKNOWN, d.cause)
        assertEquals(VideoNoteConfidence.NONE, d.confidence)
    }

    @Test fun independentRemoteAt30IsTelegramTransformation() {
        val d = diagnose(VideoNoteStageEvidence(60, rawFps = 60.0, exportedFps = 60.0, remoteFps = 30.0, remoteIndependent = true))
        assertEquals(VideoNoteRootCause.TELEGRAM_REMOTE_TRANSFORMATION, d.cause)
    }

    @Test fun remote60DisplayAt30IsPlayback() {
        val d = diagnose(VideoNoteStageEvidence(60, exportedFps = 60.0, remoteFps = 60.0, remoteIndependent = true, displayFps = 30.0))
        assertEquals(VideoNoteRootCause.PLAYBACK_DECODER_OR_RENDERING, d.cause)
    }

    @Test fun everythingAtTargetFindsNoLimit() {
        val d = diagnose(VideoNoteStageEvidence(60, sensorFps = 60.0, rawFps = 59.9, exportedFps = 60.0, remoteFps = 60.0, remoteIndependent = true))
        assertEquals(VideoNoteRootCause.NO_LIMIT_FOUND, d.cause)
    }

    @Test fun nothingMeasuredIsInsufficientData() {
        assertEquals(VideoNoteRootCause.INSUFFICIENT_DATA, diagnose(VideoNoteStageEvidence(60)).cause)
    }

    @Test fun target30WithAllGoodIsNoLimit() {
        val d = diagnose(VideoNoteStageEvidence(30, normalHighRangeAdvertised = false, rawFps = 30.0, exportedFps = 30.0))
        assertEquals(VideoNoteRootCause.NO_LIMIT_FOUND, d.cause)
    }
}
