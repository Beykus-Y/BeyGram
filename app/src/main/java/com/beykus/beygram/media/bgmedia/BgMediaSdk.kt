package com.beykus.beygram.media.bgmedia

import android.os.Build
import android.util.Log

/**
 * Common entry point, native lifecycle, diagnostics and error hierarchy
 * for the BG Media Android SDK.
 */
object BgMediaSdk {
    private const val TAG = "BgMediaSdk"
    private const val LIB_NAME = "bgbeat_jni"

    @Volatile
    private var loaded = false

    @Volatile
    private var loadError: Throwable? = null

    val isLoaded: Boolean get() = loaded

    val lastError: Throwable? get() = loadError

    val primaryAbi: String
        get() = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

    /**
     * Guarantees that the underlying JNI shared library is loaded.
     * Throws [BgMediaException.InitializationException] on failure.
     */
    @Synchronized
    fun ensureLoaded() {
        if (loaded) return
        loadError?.let { throw BgMediaException.InitializationException("Failed to load $LIB_NAME", it) }

        try {
            System.loadLibrary(LIB_NAME)
            loaded = true
            Log.i(TAG, "Successfully loaded native library $LIB_NAME on $primaryAbi")
        } catch (t: Throwable) {
            loadError = t
            Log.e(TAG, "Could not load native library $LIB_NAME", t)
            throw BgMediaException.InitializationException("Could not load native library $LIB_NAME", t)
        }
    }
}

/**
 * Typed exceptions produced by BG Media components.
 */
sealed class BgMediaException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InitializationException(message: String, cause: Throwable? = null) : BgMediaException(message, cause)
    class AudioDecodeException(message: String, cause: Throwable? = null) : BgMediaException(message, cause)
    class AnalysisException(message: String, cause: Throwable? = null) : BgMediaException(message, cause)
    class CancelledException(message: String = "Operation cancelled") : BgMediaException(message)
    class UnsupportedMediaException(message: String) : BgMediaException(message)
}
