package com.beykus.beygram.media.bgwave

/**
 * JNI handle to one BG Wave P2P session (BG-Media WaveAudioPipeline + WebRTC + Opus).
 *
 * Every call is safe when the native library lacks the P2P build: [create] returns null and the
 * session is reported as unavailable. Nothing here encodes or decodes signaling bytes; BG-Media does that.
 */
class BgWaveSession private constructor(private var handle: Long) : BgWaveSessionPort {

    val isOpen: Boolean get() = handle != 0L

    override fun startOffer(nowSeconds: Long, ttlSeconds: Long): Boolean =
        isOpen && BgWaveSessionNative.nativeStartOffer(handle, nowSeconds, ttlSeconds)

    /** Routes one remote BG Wave URI (OFFER, ANSWER, CANCEL or END) to the native pipeline. */
    override fun handleRemote(uri: String, nowSeconds: Long): Boolean =
        isOpen && BgWaveSessionNative.nativeHandleRemote(handle, uri, nowSeconds)

    /** Next outbound BG Wave URI to send through TDLib, or null when none is waiting. */
    override fun pollOutbound(): String? {
        if (!isOpen) return null
        return BgWaveSessionNative.nativePollOutbound(handle).ifEmpty { null }
    }

    override fun status(): BgWaveSessionStatus {
        if (!isOpen) return BgWaveSessionStatus(state = "Failed", route = "None", lastError = "closed")
        val parts = BgWaveSessionNative.nativeStatus(handle).split('|', limit = 3)
        return BgWaveSessionStatus(
            state = parts.getOrElse(0) { "Failed" },
            route = parts.getOrElse(1) { "None" },
            lastError = parts.getOrElse(2) { "" },
        )
    }

    override fun stop(emitEnd: Boolean, nowSeconds: Long) {
        if (isOpen) BgWaveSessionNative.nativeStop(handle, emitEnd, nowSeconds)
    }

    fun reset() {
        if (isOpen) BgWaveSessionNative.nativeReset(handle)
    }

    override fun reconnect(nowSeconds: Long): Boolean = isOpen && BgWaveSessionNative.nativeReconnect(handle, nowSeconds)

    /** Host: pushes interleaved stereo float PCM captured from the mix. */
    fun pushHostPcm(pcm: FloatArray, frames: Int, sampleRate: Int) {
        if (isOpen) BgWaveSessionNative.nativePushHostPcm(handle, pcm, frames, sampleRate)
    }

    /** Listener: fills [out] with decoded interleaved stereo and returns the frame count written. */
    fun pullListenerPcm(out: FloatArray, frames: Int, sampleRate: Int): Int =
        if (isOpen) BgWaveSessionNative.nativePullListenerPcm(handle, out, frames, sampleRate) else 0

    fun stats(): String = if (isOpen) BgWaveSessionNative.nativeStats(handle) else ""

    override fun close() {
        if (!isOpen) return
        BgWaveSessionNative.nativeDestroy(handle)
        handle = 0L
    }

    companion object {
        /** Returns null when the native library or the P2P build is not available. */
        fun create(isHost: Boolean, bitrateBps: Int): BgWaveSession? {
            if (!BgWaveSessionNative.loaded) return null
            val handle = BgWaveSessionNative.nativeCreate(if (isHost) 0 else 1, bitrateBps)
            return if (handle == 0L) null else BgWaveSession(handle)
        }
    }
}

data class BgWaveSessionStatus(val state: String, val route: String, val lastError: String)

internal object BgWaveSessionNative {
    val loaded: Boolean = try {
        System.loadLibrary("bgbeat_jni")
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    }

    @JvmStatic external fun nativeCreate(role: Int, bitrateBps: Int): Long
    @JvmStatic external fun nativeDestroy(handle: Long)
    @JvmStatic external fun nativeStartOffer(handle: Long, nowSeconds: Long, ttlSeconds: Long): Boolean
    @JvmStatic external fun nativeHandleRemote(handle: Long, uri: String, nowSeconds: Long): Boolean
    @JvmStatic external fun nativePollOutbound(handle: Long): String
    @JvmStatic external fun nativeStatus(handle: Long): String
    @JvmStatic external fun nativeStop(handle: Long, emitEnd: Boolean, nowSeconds: Long)
    @JvmStatic external fun nativeReset(handle: Long)
    @JvmStatic external fun nativeReconnect(handle: Long, nowSeconds: Long): Boolean
    @JvmStatic external fun nativeStats(handle: Long): String
    @JvmStatic external fun nativePushHostPcm(handle: Long, pcm: FloatArray, frames: Int, sampleRate: Int)
    @JvmStatic external fun nativePullListenerPcm(handle: Long, out: FloatArray, frames: Int, sampleRate: Int): Int
}
