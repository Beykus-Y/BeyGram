package com.beykus.beygram.media.bgmix

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Owns one native BG Mix session and the AudioTrack that plays it. Music only: nothing here is used for
 * voice messages, video notes or other media.
 *
 * Threads:
 * - control calls and the 50 ms status tick take [lock] and then the native control mutex;
 * - a dedicated render thread pulls frames with [BgMixNative.nativeRender] and writes them to AudioTrack,
 *   without taking [lock], because MixEngine::render is built for that.
 *
 * [release] stops the render thread before the native host is destroyed.
 */
class BgMixPlayer(private val cacheDir: File) {
    private val lock = ReentrantLock()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // All fields below are guarded by [lock].
    private var handle = 0L
    private var track: AudioTrack? = null
    private var renderThread: Thread? = null
    private var tickJob: Job? = null

    @Volatile
    private var running = false

    private val _status = MutableStateFlow<BgMixStatus?>(null)
    val status: StateFlow<BgMixStatus?> = _status.asStateFlow()

    val isRunning: Boolean get() = running

    /** Creates the native session and starts output. Returns false when BG Mix is unavailable. */
    fun start(): Boolean = lock.withLock {
        if (handle != 0L) return true
        if (!BgMixNative.isAvailable) {
            Log.w(TAG, "BG Mix native library is not available")
            return false
        }
        cacheDir.mkdirs()
        val h = BgMixNative.nativeCreate(cacheDir.absolutePath.toByteArray(Charsets.UTF_8))
        if (h == 0L) return false

        val at = try {
            openTrack()
        } catch (e: RuntimeException) {
            Log.e(TAG, "AudioTrack could not be created", e)
            BgMixNative.nativeDestroy(h)
            return false
        }

        handle = h
        track = at
        running = true
        renderThread = Thread({ renderLoop(h, at) }, "bgmix-render").apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
            start()
        }
        tickJob = scope.launch {
            while (isActive) {
                tick()
                delay(TICK_MS)
            }
        }
        true
    }

    /** Replaces the queue. Returns one native id per file, in order; 0 marks a file the decoder rejected. */
    fun setQueue(files: List<File>): List<Long> = withHandle(emptyList<Long>()) { h ->
        BgMixNative.nativeClearQueue(h)
        files.map { BgMixNative.nativeAddTrack(h, it.absolutePath.toByteArray(Charsets.UTF_8)) }
    }

    /** Returns the queue item id, or 0 when the file was rejected. */
    fun addTrack(file: File): Long = withHandle(0L) { h ->
        BgMixNative.nativeAddTrack(h, file.absolutePath.toByteArray(Charsets.UTF_8))
    }

    fun removeTrack(id: Long): Boolean = withHandle(false) { h -> BgMixNative.nativeRemoveTrack(h, id) }

    fun play() = transport(BgMixNative.ACTION_PLAY)

    fun pause() = transport(BgMixNative.ACTION_PAUSE)

    /** Starts the planned smooth transition to the next queue item. */
    fun next() = transport(BgMixNative.ACTION_NEXT)

    fun stopAll() = transport(BgMixNative.ACTION_STOP)

    /** Stops output and frees the native session. Safe to call more than once. */
    fun release() {
        val target = lock.withLock {
            val at = track ?: return
            val released = ReleaseTarget(at, renderThread, handle)
            // Clearing handle under the lock means no control call can start on it after this point.
            running = false
            tickJob?.cancel()
            tickJob = null
            track = null
            renderThread = null
            handle = 0L
            released
        }
        runCatching { target.track.pause(); target.track.flush(); target.track.stop() }
        target.renderThread?.join(RENDER_JOIN_TIMEOUT_MS)
        if (target.renderThread?.isAlive == true) {
            // Never free the native session under a render call that is still running: leak it instead.
            Log.w(TAG, "render thread did not stop; native session kept alive")
        } else {
            // Destroying the session waits for a running analysis to finish, so it must not block the caller.
            val handle = target.handle
            Thread({ BgMixNative.nativeDestroy(handle) }, "bgmix-destroy").start()
        }
        runCatching { target.track.release() }
        _status.value = null
    }

    private class ReleaseTarget(val track: AudioTrack, val renderThread: Thread?, val handle: Long)

    /** Seeks the master deck; false while a transition is running or nothing is loaded. */
    fun seek(seconds: Double): Boolean = withHandle(false) { h -> BgMixNative.nativeSeek(h, seconds) }

    private fun transport(action: Int) = withHandle(Unit) { h ->
        BgMixNative.nativeTransport(h, action)
    }

    private fun tick() {
        lock.withLock {
            val h = handle
            if (h == 0L || !running) return
            BgMixNative.nativeUpdate(h)
            _status.value = BgMixStatus.parse(BgMixNative.nativeStatus(h))
        }
    }

    private inline fun <T> withHandle(default: T, block: (Long) -> T): T = lock.withLock {
        val h = handle
        if (h == 0L) default else block(h)
    }

    private fun renderLoop(h: Long, at: AudioTrack) {
        val buffer = FloatArray(RENDER_FRAMES * CHANNELS)
        while (running) {
            val frames = BgMixNative.nativeRender(h, buffer, RENDER_FRAMES)
            if (frames <= 0) {
                Thread.sleep(2)
                continue
            }
            val written = at.write(buffer, 0, frames * CHANNELS, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                Log.w(TAG, "AudioTrack write failed: $written")
                break
            }
        }
    }

    /** Builds and starts the track. Any failure releases it before rethrowing. */
    private fun openTrack(): AudioTrack {
        val at = createTrack()
        try {
            check(at.state == AudioTrack.STATE_INITIALIZED) { "AudioTrack not initialised" }
            at.play()
        } catch (e: RuntimeException) {
            runCatching { at.release() }
            throw e
        }
        return at
    }

    private fun createTrack(): AudioTrack {
        val minBytes = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_MASK, ENCODING)
        // About 200 ms of float stereo: enough headroom for a render thread that is briefly late.
        val bufferBytes = maxOf(minBytes, SAMPLE_RATE / 5 * CHANNELS * BYTES_PER_SAMPLE)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_MASK)
                    .build()
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    companion object {
        private const val TAG = "BgMixPlayer"
        /** Must match kMixSampleRate in bgmix_jni.cpp. */
        const val SAMPLE_RATE = 48_000
        private const val CHANNELS = 2
        private const val BYTES_PER_SAMPLE = 4
        private const val CHANNEL_MASK = AudioFormat.CHANNEL_OUT_STEREO
        private const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT
        private const val RENDER_FRAMES = 1024
        private const val TICK_MS = 50L
        private const val RENDER_JOIN_TIMEOUT_MS = 1_000L
    }
}
