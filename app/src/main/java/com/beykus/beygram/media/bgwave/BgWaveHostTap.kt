package com.beykus.beygram.media.bgwave

/**
 * The host's PCM tap for ExoPlayer tracks. The render thread pushes interleaved stereo float frames here, and they go to
 * the BG Wave session through its native tap. Only one tap is open at a time, and [open] and [close] run on the hub's
 * thread, while [push] runs on the render thread.
 */
internal object BgWaveHostTap {
    @Volatile var tapId: Long = 0L
        private set

    private var pushedFrames = 0L
    private var missedPushes = 0L
    private var lastLogNanos = 0L

    val isOpen: Boolean get() = tapId != 0L

    fun open(id: Long) {
        if (id == 0L || tapId == id) return
        tapId = id
        BgWaveLog.i("host.tap.open")
    }

    fun close() {
        val id = tapId
        if (id == 0L) return
        tapId = 0L
        BgWaveSessionNative.nativeCloseHostTap(id)
        BgWaveLog.i("host.tap.close", "frames" to pushedFrames, "missed" to missedPushes)
        pushedFrames = 0L
        missedPushes = 0L
    }

    /** Render thread. Pushes [frames] interleaved stereo frames and logs the rate once per second. */
    fun push(pcm: FloatArray, frames: Int, sampleRate: Int) {
        val id = tapId
        if (id == 0L || frames <= 0) return
        if (BgWaveSessionNative.nativePushHostTap(id, pcm, frames, sampleRate)) {
            pushedFrames += frames
        } else {
            missedPushes++
        }
        val now = System.nanoTime()
        if (now - lastLogNanos >= ONE_SECOND_NANOS) {
            BgWaveLog.i("host.tap.frames", "frames" to pushedFrames, "missed" to missedPushes, "rate" to sampleRate)
            lastLogNanos = now
        }
    }

    private const val ONE_SECOND_NANOS = 1_000_000_000L
}
