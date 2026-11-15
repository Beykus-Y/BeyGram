package com.beykus.beygram.media.bgwave

import java.util.concurrent.atomic.AtomicLong

/**
 * The host's PCM tap for ExoPlayer tracks. The render thread pushes interleaved stereo float frames here, and they go to
 * the BG Wave session through its native tap. Only one tap is open at a time, and [open] and [close] run on the hub's
 * thread, while [push] runs on the render thread.
 */
internal object BgWaveHostTap {
    @Volatile var tapId: Long = 0L
        private set

    /** True only while the active playback item is eligible public music (never voice, video note, secret, or catalog). */
    @Volatile var captureEligible: Boolean = false
        private set

    /** True while BG Mix owns playback (from commitStaged onward) so ExoPlayer stops pushing before BG Mix attaches. */
    @Volatile var exoSuppressedForBgMix: Boolean = false

    @Volatile internal var closeNativeTap: (Long) -> Unit = BgWaveSessionNative::nativeCloseHostTap
    @Volatile internal var pushNativeTap: (Long, FloatArray, Int, Int) -> Boolean = BgWaveSessionNative::nativePushHostTap

    private val pushedFrames = AtomicLong(0L)
    private val missedPushes = AtomicLong(0L)
    private val lastLogNanos = AtomicLong(0L)

    val isOpen: Boolean get() = tapId != 0L

    val isActiveForExo: Boolean get() = isOpen && captureEligible && !exoSuppressedForBgMix

    fun setCaptureEligible(eligible: Boolean) {
        captureEligible = eligible
    }

    @Synchronized
    fun open(id: Long) {
        if (id == 0L || tapId == id) return
        val prev = tapId
        if (prev != 0L) {
            tapId = 0L
            closeNativeTap(prev)
            BgWaveLog.i("host.tap.close", "frames" to pushedFrames.getAndSet(0L), "missed" to missedPushes.getAndSet(0L))
        }
        pushedFrames.set(0L)
        missedPushes.set(0L)
        lastLogNanos.set(0L)
        tapId = id
        BgWaveLog.i("host.tap.open")
    }

    @Synchronized
    fun close() {
        val id = tapId
        if (id == 0L) return
        tapId = 0L
        closeNativeTap(id)
        BgWaveLog.i("host.tap.close", "frames" to pushedFrames.getAndSet(0L), "missed" to missedPushes.getAndSet(0L))
        lastLogNanos.set(0L)
    }

    /** Render thread. Pushes [frames] interleaved stereo frames and logs the rate once per second. */
    fun push(pcm: FloatArray, frames: Int, sampleRate: Int) {
        val id = tapId
        if (id == 0L || !captureEligible || exoSuppressedForBgMix || frames <= 0) return
        if (pushNativeTap(id, pcm, frames, sampleRate)) {
            pushedFrames.addAndGet(frames.toLong())
        } else {
            missedPushes.incrementAndGet()
        }
        val now = System.nanoTime()
        val prevLog = lastLogNanos.get()
        if (now - prevLog >= ONE_SECOND_NANOS && lastLogNanos.compareAndSet(prevLog, now)) {
            BgWaveLog.i("host.tap.frames", "frames" to pushedFrames.get(), "missed" to missedPushes.get(), "rate" to sampleRate)
        }
    }

    private const val ONE_SECOND_NANOS = 1_000_000_000L
}
