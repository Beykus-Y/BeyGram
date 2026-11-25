package com.beykus.beygram.ui.screens.chat

import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.beykus.beygram.R
import java.io.File

/**
 * Plays the freshly recorded video note inside the preview dialog (#1228).
 *
 * `VideoView` draws through a `SurfaceView`, which is a separate window layer: inside a dialog it stays black on several
 * devices (the reported Samsung M21 among them) even when the file is fine, and it reports nothing when it fails. A
 * Media3 player drawing into a `TextureView` lives in the dialog's own view hierarchy, so the frame is composited with
 * the rest of the dialog instead of falling behind it.
 *
 * A file that really cannot be decoded is not hidden: the player error replaces the (then pointless) black rectangle
 * with a poster frame and an explicit line, so the user sees that the recording is unreadable instead of a silent black
 * box.
 */
@Composable
internal fun VideoNotePreview(
    path: String,
    posterPath: String?,
    modifier: Modifier = Modifier
) {
    var playbackError by remember(path) { mutableStateOf<String?>(null) }
    var ready by remember(path) { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(path, context) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) ready = true
                }

                override fun onPlayerError(error: PlaybackException) {
                    playbackError = error.errorCodeName
                }
            })
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (playbackError == null) {
            // `key(path)` rebuilds the view for another segment, and `update` re-attaches the surface when the player
            // itself is recreated: an AndroidView keeps its view across recompositions, so without both a second
            // segment would play with sound and a black picture (Codex review of #1231).
            key(path) {
                AndroidView(
                    factory = { context ->
                        TextureView(context).also { view -> player.setVideoTextureView(view) }
                    },
                    update = { view -> player.setVideoTextureView(view) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        // Until the first decoded frame (and forever, if the file is unreadable) the poster stands in for the video.
        if (playbackError != null || !ready) {
            val poster = posterPath?.takeIf { File(it).isFile }
            if (poster != null) {
                LocalMediaImage(poster, Modifier.fillMaxSize(), fit = true, animate = false)
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black))
            }
        }
        if (playbackError != null) {
            Text(
                text = stringResource(R.string.recorder_preview_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}