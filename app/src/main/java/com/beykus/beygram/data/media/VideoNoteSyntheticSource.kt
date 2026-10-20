package com.beykus.beygram.data.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Produces a silent, circular 480x480 H.264/MP4 diagnostic clip from deterministic YUV
 * frames. Uses no CameraX, camera permission or microphone, and never embeds user data.
 */
internal object VideoNoteSyntheticSource {
    const val SIDE = 480
    const val FPS = 30
    const val FRAMES = 90

    suspend fun generate(file: File) = withContext(Dispatchers.Default) {
        file.parentFile?.mkdirs()
        file.delete()
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, SIDE, SIDE).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 1_400_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var videoTrack = -1
        val bufferInfo = MediaCodec.BufferInfo()
        var endOfOutput = false
        fun drain(endOfStream: Boolean) {
            var emptyPolls = 0
            while (true) {
                val index = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when (index) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!endOfStream || ++emptyPolls > 500) {
                            if (endOfStream && !endOfOutput) error("Synthetic encoder did not finalize")
                            return
                        }
                    }
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted) { "Video format changed twice" }
                        videoTrack = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    else -> if (index >= 0) {
                        val encoded = codec.getOutputBuffer(index) ?: error("Missing encoded buffer")
                        if (bufferInfo.size > 0) {
                            check(muxerStarted && videoTrack >= 0) { "Muxer not started" }
                            encoded.position(bufferInfo.offset)
                            encoded.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrack, encoded, bufferInfo)
                        }
                        val eos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(index, false)
                        if (eos) {
                            endOfOutput = true
                            return
                        }
                    }
                }
            }
        }
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            for (frame in 0 until FRAMES) {
                currentCoroutineContext().ensureActive()
                var input = codec.dequeueInputBuffer(10_000)
                while (input < 0) {
                    drain(false)
                    currentCoroutineContext().ensureActive()
                    input = codec.dequeueInputBuffer(10_000)
                }
                val image = codec.getInputImage(input)
                    ?: error("Encoder does not support flexible YUV input; no test sent")
                try {
                    drawFrame(image.planes, frame)
                } finally {
                    image.close()
                }
                codec.queueInputBuffer(input, 0, SIDE * SIDE * 3 / 2, frame * 1_000_000L / FPS, 0)
                drain(false)
            }
            var eosInput = codec.dequeueInputBuffer(10_000)
            while (eosInput < 0) {
                drain(false)
                currentCoroutineContext().ensureActive()
                eosInput = codec.dequeueInputBuffer(10_000)
            }
            codec.queueInputBuffer(eosInput, 0, 0, FRAMES * 1_000_000L / FPS, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(true)
            check(endOfOutput && muxerStarted && file.length() > 0) { "Synthetic MP4 export incomplete" }
        } catch (failure: Exception) {
            file.delete()
            throw failure
        } finally {
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
            runCatching { codec.stop() }
            codec.release()
        }
    }

    private fun drawFrame(planes: Array<android.media.Image.Plane>, frame: Int) {
        val y = planes[0]
        val markerX = 48 + frame * 4 % (SIDE - 96)
        val markerY = SIDE / 2 + (frame % 21 - 10) * 4
        val center = SIDE / 2
        val radiusSq = center * center
        for (row in 0 until SIDE) {
            for (col in 0 until SIDE) {
                val dx = col - center
                val dy = row - center
                val inside = dx * dx + dy * dy <= radiusSq
                val grid = col % 48 < 2 || row % 48 < 2
                val marker = kotlin.math.abs(col - markerX) < 12 && kotlin.math.abs(row - markerY) < 12
                val lum = when {
                    !inside -> 16
                    marker -> 235
                    grid -> 202
                    else -> 64 + ((col + row + frame * 3) % 60)
                }
                y.buffer.put(row * y.rowStride + col * y.pixelStride, lum.toByte())
            }
        }
        // Neutral U/V, no image content or microphone samples.
        for (planeIndex in 1..2) {
            val plane = planes[planeIndex]
            for (row in 0 until SIDE / 2) {
                for (col in 0 until SIDE / 2) {
                    plane.buffer.put(row * plane.rowStride + col * plane.pixelStride, 128.toByte())
                }
            }
        }
    }
}
