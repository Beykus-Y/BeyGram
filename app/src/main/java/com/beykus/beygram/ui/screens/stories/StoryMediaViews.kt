package com.beykus.beygram.ui.screens.stories

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.ui.screens.chat.LocalMediaImage
import java.io.File

/**
 * Stories are personal content even when the poster did not enable protected content. Hiding the
 * window preview and screenshots prevents the current story from appearing in Recents or a capture.
 */
@Composable
internal fun SecureStoryWindow() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.storyActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/** Reads Android's animation scale so progress and transitions can avoid motion when the user disabled it. */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f ||
            Settings.Global.getFloat(context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
    }
}

@Composable
internal fun StoryPhoto(
    file: MediaFileState,
    thumbnail: MediaFileState,
    minithumbnail: ByteArray?,
    modifier: Modifier = Modifier
) {
    val path = file.localPath ?: thumbnail.localPath
    val mini = remember(minithumbnail) {
        minithumbnail?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            path != null -> LocalMediaImage(path, Modifier.fillMaxSize())
            mini != null -> Image(mini, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            file.error != null || thumbnail.error != null -> Text(
                stringResource(R.string.stories_image_failed),
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(24.dp)
            )
            else -> CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

/** A minimal story video player, separate from the message media viewer so playback policy cannot affect chats. */
@Composable
internal fun StoryVideo(
    path: String,
    playing: Boolean,
    onFailure: () -> Unit,
    onReady: () -> Unit = {},
    onPlayingChanged: (Boolean) -> Unit = {},
    onEnded: () -> Unit = {},
    restartToken: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var buffering by remember(path) { mutableStateOf(true) }
    var failed by remember(path) { mutableStateOf(false) }
    val currentOnFailure by rememberUpdatedState(onFailure)
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnPlayingChanged by rememberUpdatedState(onPlayingChanged)
    val currentOnEnded by rememberUpdatedState(onEnded)
    val player = remember(path) {
        ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
    DisposableEffect(player, path) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                currentOnPlayingChanged(isPlaying && player.playbackState != Player.STATE_BUFFERING)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) currentOnReady()
                if (playbackState == Player.STATE_ENDED) currentOnEnded()
                currentOnPlayingChanged(player.isPlaying && playbackState != Player.STATE_BUFFERING)
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
                buffering = false
                currentOnPlayingChanged(false)
                currentOnFailure()
            }
        }
        player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        player.prepare()
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(playing, player) {
        if (playing) {
            PlaybackEngine.pause()
            player.play()
        } else {
            player.pause()
        }
    }
    LaunchedEffect(restartToken, player) {
        if (restartToken > 0) player.seekToDefaultPosition()
    }
    if (!failed) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { viewContext -> PlayerView(viewContext).apply {
                    setUseController(false)
                    setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
                } },
                update = { it.player = player },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize()
            )
            if (buffering) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}


private fun Context.storyActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
