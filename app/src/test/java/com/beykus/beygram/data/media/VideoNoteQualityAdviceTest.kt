package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoNoteQualityAdviceTest {
    @Test fun highRequestIsOnlyPlannedNotPromised() {
        assertEquals(
            VideoNoteQualityAdvice.HIGH_FPS_NOT_YET_MEASURED,
            videoNoteQualityAdvice(VideoNoteFpsCause.AS_REQUESTED, requestedFps = 60)
        )
    }

    @Test fun lowRequestIsIntentional() {
        assertEquals(
            VideoNoteQualityAdvice.AS_REQUESTED,
            videoNoteQualityAdvice(VideoNoteFpsCause.AS_REQUESTED, requestedFps = 30)
        )
    }

    @Test fun cameraWithoutNormalSixtyDoesNotBlameSettings() {
        assertEquals(
            VideoNoteQualityAdvice.NORMAL_60_UNAVAILABLE,
            videoNoteQualityAdvice(VideoNoteFpsCause.NO_NORMAL_60, requestedFps = 60)
        )
    }

    @Test fun encoderAndStreamLimitsSuggestALowerResolution() {
        assertEquals(
            VideoNoteQualityAdvice.ENCODER_LIMIT,
            videoNoteQualityAdvice(VideoNoteFpsCause.ENCODER_REJECTS_60, requestedFps = 60)
        )
        assertEquals(
            VideoNoteQualityAdvice.STREAM_LIMIT,
            videoNoteQualityAdvice(VideoNoteFpsCause.STREAM_REJECTS_60, requestedFps = 60)
        )
    }

    @Test fun fallbackAndMissingRangesStayDistinct() {
        assertEquals(
            VideoNoteQualityAdvice.RANGE_FALLBACK,
            videoNoteQualityAdvice(VideoNoteFpsCause.CAMERA_RANGE_FALLBACK, requestedFps = 30)
        )
        assertEquals(
            VideoNoteQualityAdvice.NO_RANGE,
            videoNoteQualityAdvice(VideoNoteFpsCause.NO_COMPATIBLE_RANGE, requestedFps = 60)
        )
    }

    @Test fun measuredStageWinsOverThePlan() {
        assertEquals(
            VideoNoteQualityAdvice.EXPORT_DROP,
            videoNoteQualityAdvice(VideoNoteFpsCause.AS_REQUESTED, requestedFps = 60, stage = VideoNoteFpsLimitStage.EXPORT)
        )
        assertEquals(
            VideoNoteQualityAdvice.CAMERA_BELOW_TARGET,
            videoNoteQualityAdvice(VideoNoteFpsCause.AS_REQUESTED, requestedFps = 60, stage = VideoNoteFpsLimitStage.CAMERA_RUNTIME)
        )
    }

    @Test fun missingCauseIsHonestlyUnknown() {
        assertEquals(
            VideoNoteQualityAdvice.UNKNOWN,
            videoNoteQualityAdvice(cause = null, requestedFps = 60)
        )
    }
}
