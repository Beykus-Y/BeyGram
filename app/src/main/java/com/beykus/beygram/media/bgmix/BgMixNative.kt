package com.beykus.beygram.media.bgmix

import androidx.annotation.Keep
import com.beykus.beygram.media.bgmedia.BgMediaSdk

/**
 * JNI surface of BG Mix (see app/src/main/cpp/bgmix_jni.cpp). Strings travel as UTF-8 bytes.
 * Every handle-taking call must stay on the owner's lock; [BgMixPlayer] enforces that.
 */
@Keep
object BgMixNative {
    /** True when the native library loads and was built with the BG Media core. */
    val isAvailable: Boolean
        get() = try {
            BgMediaSdk.ensureLoaded()
            nativeIsAvailable()
        } catch (_: Throwable) {
            false
        }

    @JvmStatic
    external fun nativeIsAvailable(): Boolean

    /** Returns 0 when the host could not be created. */
    @JvmStatic
    external fun nativeCreate(cacheDir: ByteArray): Long

    @JvmStatic
    external fun nativeDestroy(handle: Long)

    /** Returns the queue item id, or 0 when the path was rejected. */
    @JvmStatic
    external fun nativeAddTrack(handle: Long, path: ByteArray): Long

    @JvmStatic
    external fun nativeRemoveTrack(handle: Long, id: Long): Boolean

    @JvmStatic
    external fun nativeClearQueue(handle: Long)

    /** action: 0 play, 1 pause, 2 next (planned transition), 3 stop. */
    @JvmStatic
    external fun nativeTransport(handle: Long, action: Int)

    /** Seeks the master deck; false while a transition is running. */
    @JvmStatic
    external fun nativeSeek(handle: Long, seconds: Double): Boolean

    @JvmStatic
    external fun nativeUpdate(handle: Long)

    /** Fills [out] with interleaved stereo float frames and returns the frame count written. */
    @JvmStatic
    external fun nativeRender(handle: Long, out: FloatArray, frames: Int): Int

    /** UTF-8 JSON, see [BgMixStatus]. */
    @JvmStatic
    external fun nativeStatus(handle: Long): ByteArray?

    const val ACTION_PLAY = 0
    const val ACTION_PAUSE = 1
    const val ACTION_NEXT = 2
    const val ACTION_STOP = 3
}
