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

    /** Attaches or detaches the BG Wave host PCM tap. Returns false when BG Media is unavailable. */
    @JvmStatic
    external fun nativeSetWaveTap(handle: Long, sessionHandle: Long): Boolean

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

    /** Immediate switch to a native queue item without crossfade. Returns one of the MANUAL_* codes. */
    @JvmStatic
    external fun nativeManualSelect(handle: Long, queueItemId: Long): Int

    /** Immediate switch to the next prepared-or-pending item after the logical target. Returns one of the MANUAL_* codes. */
    @JvmStatic
    external fun nativeManualNext(handle: Long): Int

    const val ACTION_PLAY = 0
    const val ACTION_PAUSE = 1
    const val ACTION_NEXT = 2
    const val ACTION_STOP = 3

    const val MANUAL_ACCEPTED = 0
    const val MANUAL_NOT_FOUND = 1
    const val MANUAL_NOT_READY = 2
}
