package com.beykus.beygram.media.bgwave

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * Plays the decoded BG Wave stream on a listener device.
 *
 * Pulls 20 ms frames from the native session on a dedicated thread and writes them to an [AudioTrack].
 * It holds audio focus while playing. When focus is lost for good the output stops, and the session
 * is left to the caller. Every resource is released in [stop], which is safe to call more than once.
 */
class BgWaveListenerAudio(
    private val session: BgWaveSessionPort,
    private val audioManager: AudioManager,
    private val onFocusLost: () -> Unit,
) {
    private var track: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        BgWaveLog.i("audio.focus", "change" to change)
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                stop()
                onFocusLost()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> track?.pause()
            AudioManager.AUDIOFOCUS_GAIN -> if (running) track?.play()
        }
    }

    /** Returns false when focus or the output could not be started. */
    @Synchronized
    fun start(): Boolean {
        if (running) return true
        if (!requestFocus()) return false

        val minBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBytes <= 0) {
            BgWaveLog.w("audio.start.failed", null, "reason" to "min-buffer")
            abandonFocus()
            return false
        }
        val out = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBytes, FRAMES_PER_READ * CHANNELS * FLOAT_BYTES * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (out.state != AudioTrack.STATE_INITIALIZED) {
            BgWaveLog.w("audio.start.failed", null, "reason" to "track-state")
            out.release()
            abandonFocus()
            return false
        }
        track = out
        running = true
        out.play()
        BgWaveLog.i("audio.start", "rate" to SAMPLE_RATE)
        thread = Thread({ pumpLoop(out) }, "bgwave-listener-audio").apply {
            isDaemon = true
            start()
        }
        return true
    }

    /** Stops output, joins the pump thread, releases the track and gives back audio focus. */
    @Synchronized
    fun stop() {
        running = false
        val pump = thread
        thread = null
        if (pump != null && pump !== Thread.currentThread()) {
            try {
                pump.join(JOIN_TIMEOUT_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        track?.let {
            try {
                it.stop()
            } catch (e: IllegalStateException) {
                BgWaveLog.w("audio.stop.failed", e)
            }
            it.release()
        }
        track = null
        abandonFocus()
    }

    private fun pumpLoop(out: AudioTrack) {
        val buffer = FloatArray(FRAMES_PER_READ * CHANNELS)
        var pulledFrames = 0L
        var underruns = 0L
        var lastLogNanos = System.nanoTime()
        while (running) {
            val frames = session.pullListenerPcm(buffer, FRAMES_PER_READ, SAMPLE_RATE)
            pulledFrames += maxOf(frames, 0)
            if (frames <= 0) underruns++
            val now = System.nanoTime()
            if (now - lastLogNanos >= 1_000_000_000L) {
                BgWaveLog.i("audio.pull", "frames" to pulledFrames, "underruns" to underruns)
                pulledFrames = 0L
                underruns = 0L
                lastLogNanos = now
            }
            if (frames <= 0) {
                // No decoded audio yet: write silence so the output clock keeps running.
                buffer.fill(0f)
                out.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
                continue
            }
            // Silence the tail if the pull returned fewer frames than requested.
            if (frames < FRAMES_PER_READ) buffer.fill(0f, frames * CHANNELS, buffer.size)
            val written = out.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                BgWaveLog.w("audio.write.failed", null, "code" to written)
                running = false
            }
        }
    }

    private fun requestFocus(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        val granted = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) BgWaveLog.w("audio.focus.denied")
        if (granted) focusRequest = request
        return granted
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val CHANNELS = 2
        const val FLOAT_BYTES = 4
        const val FRAMES_PER_READ = 960
        const val JOIN_TIMEOUT_MS = 500L
    }
}
