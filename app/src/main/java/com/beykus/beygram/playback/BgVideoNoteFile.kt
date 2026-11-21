package com.beykus.beygram.playback

import androidx.annotation.Keep
import com.beykus.beygram.BuildConfig

/** BG Video owns reads of a completed file. Media3 still owns the decoder and surface. */
internal class BgVideoNoteFile private constructor(private var handle: Long, val length: Long) {
    @Synchronized
    fun read(position: Long, output: ByteArray, offset: Int, length: Int): Int =
        if (handle == 0L) -1 else BgVideoNoteNative.read(handle, position, output, offset, length)

    @Synchronized
    fun close() {
        if (handle == 0L) return
        BgVideoNoteNative.close(handle)
        handle = 0L
    }

    companion object {
        fun eligible(item: PlaybackItem, enabled: Boolean = BuildConfig.BG_VIDEO_NOTE_PILOT): Boolean =
            enabled && item.kind == MediaKind.VideoNote && !item.systemPrivate

        fun open(path: String): BgVideoNoteFile? {
            if (!BgVideoNoteNative.available) return null
            val handle = BgVideoNoteNative.open(path.toByteArray(Charsets.UTF_8))
            if (handle == 0L) return null
            val length = BgVideoNoteNative.size(handle)
            if (length <= 0L) {
                BgVideoNoteNative.close(handle)
                return null
            }
            return BgVideoNoteFile(handle, length)
        }
    }
}

@Keep
internal object BgVideoNoteNative {
    val available: Boolean by lazy {
        try {
            System.loadLibrary("bgvideo_note_jni")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    external fun open(path: ByteArray): Long
    external fun size(handle: Long): Long
    external fun read(handle: Long, position: Long, output: ByteArray, offset: Int, length: Int): Int
    external fun close(handle: Long)
}
