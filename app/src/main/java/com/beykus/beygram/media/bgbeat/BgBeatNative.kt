package com.beykus.beygram.media.bgbeat

import android.util.Log
import androidx.annotation.Keep
import com.beykus.beygram.media.bgmedia.BgMediaSdk

@Keep
fun interface BgBeatProgressListener {
    fun onProgress(fraction: Float, stage: String)
}

/**
 * Direct JNI bridge to the C++20 BG Beat analysis engine, built upon BgMediaSdk.
 */
@Keep
object BgBeatNative {
    private const val TAG = "BgBeatNative"

    val isLoaded: Boolean
        get() = try {
            BgMediaSdk.ensureLoaded()
            true
        } catch (_: Throwable) {
            false
        }

    init {
        try {
            BgMediaSdk.ensureLoaded()
            Log.i(TAG, "libbgbeat_jni ready: ${nativeGetVersion()}")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load BG Beat native library", e)
        }
    }

    @JvmStatic
    external fun nativeGetVersion(): String

    @JvmStatic
    external fun nativeCreateCancelToken(): Long

    @JvmStatic
    external fun nativeCancel(tokenHandle: Long)

    @JvmStatic
    external fun nativeDestroyCancelToken(tokenHandle: Long)

    @JvmStatic
    external fun nativeAnalyzeFile(
        filePath: String,
        cacheDir: String,
        minBpm: Double,
        maxBpm: Double,
        cancelTokenHandle: Long,
        progressListener: BgBeatProgressListener?
    ): String?
}
