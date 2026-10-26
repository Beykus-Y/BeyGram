package com.beykus.beygram.ui.screens.playback

import android.content.Context
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.toIndexOrder

/**
 * Original queue indices in the order Media3 will actually play them. Shuffle is owned by ExoPlayer, so reading its
 * ShuffleOrder is the only non-duplicating source of truth; UI never re-creates a random sequence of its own.
 *
 * Returns null when the player wrapper does not expose an ExoPlayer or the order is not a full permutation; the queue
 * then falls back to the source order instead of hiding tracks.
 */
@OptIn(UnstableApi::class)
internal fun media3PlaybackOrder(context: Context): List<Int>? {
    val wrapper = PlaybackEngine.player(context) as? ForwardingPlayer ?: return null
    val exo = wrapper.wrappedPlayer as? ExoPlayer ?: return null
    return runCatching {
        val order = exo.shuffleOrder.toIndexOrder()
        order.takeIf {
            it.size == exo.mediaItemCount &&
                it.distinct().size == it.size &&
                it.all { index -> index in 0 until exo.mediaItemCount }
        }
    }.getOrNull()
}
