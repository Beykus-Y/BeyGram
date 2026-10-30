package com.beykus.beygram.media.bgbeat

import android.util.Log
import androidx.annotation.Keep

@Keep
fun interface BgBeatProgressListener {
    fun onProgress(fraction: Float, stage: String)
}

/**
 * Direct JNI bridge to the C++20 BG Beat analysis engine.
 */
@Keep
object BgBeatNative {
    private const val TAG = "BgBeatNative"

    @Volatile
    var isLoaded: Boolean = false
        private set

    init {
        try {
            System.loadLibrary("bgbeat_jni")
            isLoaded = true
            Log.i(TAG, "libbgbeat_jni loaded successfully: ${nativeGetVersion()}")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load libbgbeat_jni", e)
            isLoaded = false
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
