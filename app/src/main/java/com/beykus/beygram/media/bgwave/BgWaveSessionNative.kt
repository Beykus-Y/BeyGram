package com.beykus.beygram.media.bgwave

import androidx.annotation.Keep
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * JNI handle to one BG Wave P2P session (BG-Media WaveAudioPipeline + WebRTC + Opus).
 *
 * Every call is safe when the native library lacks the P2P build: [create] returns null and the
 * session is reported as unavailable. Nothing here encodes or decodes signaling bytes; BG-Media does that.
 */
class BgWaveSession private constructor(@Volatile private var handle: Long) : BgWaveSessionPort {

    /**
     * Controller, listener audio, and ExoPlayer render threads can touch the session concurrently. Every native
     * call takes the read side and [close] takes the write side, so the native session is never destroyed under
     * a live call.
     */
    private val nativeGuard = ReentrantReadWriteLock()

    val isOpen: Boolean get() = handle != 0L

    /** Native handle, for attaching this session as the BG Mix host PCM tap. 0 when closed. */
    val nativeHandle: Long get() = nativeGuard.read { if (handle != 0L) handle else 0L }

    override fun sessionHandle(): Long = nativeGuard.read { if (handle != 0L) handle else 0L }

    override fun startOffer(nowSeconds: Long, ttlSeconds: Long): Boolean = nativeGuard.read {
        handle != 0L && BgWaveSessionNative.nativeStartOffer(handle, nowSeconds, ttlSeconds)
    }

    /** Routes one remote BG Wave URI (OFFER, ANSWER, CANCEL or END) to the native pipeline. */
    override fun handleRemote(uri: String, nowSeconds: Long): Boolean = nativeGuard.read {
        handle != 0L && BgWaveSessionNative.nativeHandleRemote(handle, uri, nowSeconds)
    }

    /** Next outbound BG Wave URI to send through TDLib, or null when none is waiting. */
    override fun pollOutbound(): String? = nativeGuard.read {
        if (handle != 0L) BgWaveSessionNative.nativePollOutbound(handle).ifEmpty { null } else null
    }

    override fun status(): BgWaveSessionStatus = nativeGuard.read {
        if (handle == 0L) return@read BgWaveSessionStatus(state = "Failed", route = "None", lastError = "closed")
        val parts = BgWaveSessionNative.nativeStatus(handle).split('|', limit = 3)
        BgWaveSessionStatus(
            state = parts.getOrElse(0) { "Failed" },
            route = parts.getOrElse(1) { "None" },
            lastError = parts.getOrElse(2) { "" },
        )
    }

    override fun stop(emitEnd: Boolean, nowSeconds: Long) {
        nativeGuard.read {
            if (handle != 0L) BgWaveSessionNative.nativeStop(handle, emitEnd, nowSeconds)
        }
    }

    fun reset() {
        nativeGuard.read {
            if (handle != 0L) BgWaveSessionNative.nativeReset(handle)
        }
    }

    override fun reconnect(nowSeconds: Long): Boolean = nativeGuard.read {
        handle != 0L && BgWaveSessionNative.nativeReconnect(handle, nowSeconds)
    }

    /** Host: pushes interleaved stereo float PCM captured from the mix. */
    fun pushHostPcm(pcm: FloatArray, frames: Int, sampleRate: Int) {
        nativeGuard.read {
            if (handle != 0L) BgWaveSessionNative.nativePushHostPcm(handle, pcm, frames, sampleRate)
        }
    }

    /** Listener: fills [out] with decoded interleaved stereo and returns the frame count written. */
    override fun pullListenerPcm(out: FloatArray, frames: Int, sampleRate: Int): Int =
        nativeGuard.read {
            if (handle != 0L) BgWaveSessionNative.nativePullListenerPcm(handle, out, frames, sampleRate) else 0
        }

    /** Host: opens a PCM tap on this session for the ExoPlayer render thread. Returns the tap id, or 0. */
    override fun openHostTap(): Long = nativeGuard.read {
        if (handle != 0L) BgWaveSessionNative.nativeOpenHostTap(handle) else 0L
    }

    override fun stats(): String = nativeGuard.read {
        if (handle != 0L) BgWaveSessionNative.nativeStats(handle) else ""
    }

    override fun close() {
        nativeGuard.write {
            if (handle == 0L) return
            val target = handle
            handle = 0L
            BgWaveSessionNative.nativeDestroy(target)
        }
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

@Keep
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
    @JvmStatic external fun nativeOpenHostTap(handle: Long): Long
    @JvmStatic external fun nativePushHostTap(tapId: Long, pcm: FloatArray, frames: Int, sampleRate: Int): Boolean
    @JvmStatic external fun nativeCloseHostTap(tapId: Long)
    @JvmStatic external fun nativePullListenerPcm(handle: Long, out: FloatArray, frames: Int, sampleRate: Int): Int
}
