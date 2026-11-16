package com.beykus.beygram.media.bgmotion

import android.util.Log
import androidx.annotation.Keep
import com.beykus.beygram.media.bgmedia.BgMediaSdk

/**
 * JNI surface of the temporary 2D mascot test rig. The session owns the shared
 * AnalysisResult and BG Motion 2.0 choreography. BG Motion 3.x will use the same
 * analysis handle and lifecycle, but its own ClipPose/DanceFrame presenter.
 *
 * Native calls with one handle are serialized by the session mutex; they do not
 * require a particular thread for the 2D path. The future 3D GL path will have a
 * separate GL-thread release step.
 */
@Keep
object BgMascotNative {
    private const val TAG = "BgMascotNative"

    const val MODE_2D = 0
    const val MODE_3D = 1

    val isAvailable: Boolean
        get() = try {
            BgMediaSdk.ensureLoaded()
            nativeIsAvailable()
        } catch (_: Throwable) {
            false
        }

    init {
        try {
            BgMediaSdk.ensureLoaded()
            Log.i(TAG, "libbgbeat_jni mascot: ${nativeGetVersion()}")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load mascot native library", e)
        }
    }

    @JvmStatic
    external fun nativeIsAvailable(): Boolean

    @JvmStatic
    external fun nativeGetVersion(): String

    @JvmStatic
    external fun nativeCreateSession(
        analysisHandle: Long,
        mode: Int,
        intensity: Float
    ): Long

    @JvmStatic
    external fun nativeDestroySession(handle: Long)

    /** Fills [out] with MascotFrameLayout values; returns the written count. */
    @JvmStatic
    external fun nativeSample2D(handle: Long, mediaTime: Double, out: FloatArray): Int

    @JvmStatic
    external fun nativeSetIntensity(handle: Long, intensity: Float)

    @JvmStatic
    external fun nativeSetPlaybackOffset(handle: Long, offsetSeconds: Double)

    @JvmStatic
    external fun nativeStatusJson(handle: Long, mediaTime: Double): String?
}
