package com.beykus.beygram.ui.screens.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.beykus.beygram.data.canvas.AppCanvasRepository
import com.beykus.beygram.data.canvas.BgCanvasMode
import com.beykus.beygram.data.canvas.CanvasLoadState
import com.beykus.beygram.data.canvas.CanvasSettingsStore
import com.beykus.beygram.domain.music.TrackIdentity
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.ui.accessibility.rememberSystemReducedMotion
import com.beykus.beygram.ui.motion.LocalMotionSettings
import com.beykus.beygram.ui.motion.MotionCategory
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.rememberMotionSpec
import java.io.File

internal fun isBgCanvasAllowed(
    active: Boolean,
    resumed: Boolean,
    mode: BgCanvasMode,
    reducedMotion: Boolean,
    motionEnabled: Boolean,
    mediaMotionDisabled: Boolean,
    powerSave: Boolean,
): Boolean =
    active &&
        resumed &&
        mode != BgCanvasMode.Off &&
        !reducedMotion &&
        motionEnabled &&
        !mediaMotionDisabled &&
        !powerSave

private fun readPowerSaveMode(context: Context): Boolean = runCatching {
    (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
}.getOrDefault(false)

/**
 * Observes BG Canvas availability for [item] when [active], resumed, and allowed by settings,
 * reduced motion, and battery saver. Never spawns decoders for queued tracks; only prefetches the
 * single immediate next track's file into disk cache once the current canvas is ready.
 */
@Composable
internal fun rememberBgCanvasState(
    item: PlaybackItem,
    active: Boolean,
): Pair<BgCanvasMode, CanvasLoadState> {
    val context = LocalContext.current
    val settingsFlow = remember(context) { CanvasSettingsStore.flow(context) }
    val settings by settingsFlow.collectAsStateWithLifecycle()
    val reducedMotion = rememberSystemReducedMotion()
    val motion = LocalMotionSettings.current

    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var powerSave by remember(context) { mutableStateOf(readPowerSaveMode(context)) }
    LaunchedEffect(context, resumed) {
        powerSave = readPowerSaveMode(context)
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                powerSave = readPowerSaveMode(context)
            }
        }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        runCatching {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    val allowed = isBgCanvasAllowed(
        active = active,
        resumed = resumed,
        mode = settings.mode,
        reducedMotion = reducedMotion,
        motionEnabled = motion.enabled,
        mediaMotionDisabled = MotionCategory.Media in motion.disabledCategories,
        powerSave = powerSave,
    )

    var state by remember(item.key) { mutableStateOf<CanvasLoadState>(CanvasLoadState.Idle) }

    if (!allowed) {
        return settings.mode to CanvasLoadState.Idle
    }

    val identity = remember(item.key, item.accountId, item.secret, item.isSecretChat, item.viewOnce) {
        TrackIdentity.fromPlaybackItem(item)
    }

    LaunchedEffect(identity, settings) {
        state = CanvasLoadState.Idle
        val repo = AppCanvasRepository.get(context)
        repo.observeCanvas(identity, settings).collect { nextState ->
            state = nextState
            if (nextState is CanvasLoadState.Ready) {
                val pb = PlaybackEngine.state.value
                val nextItem = pb.queue.getOrNull(pb.queueIndex + 1)
                if (nextItem != null && !nextItem.systemPrivate) {
                    repo.prefetch(TrackIdentity.fromPlaybackItem(nextItem), settings)
                }
            }
        }
    }

    return settings.mode to state
}

/**
 * Muted, non-audio-focus, looping Media3 video overlay that keeps the static poster visible until
 * the first frame renders, and falls back to the static cover on any playback error.
 */
@Composable
internal fun BgCanvasOverlay(
    state: CanvasLoadState,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val asset = (state as? CanvasLoadState.Ready)?.asset ?: return
    val context = LocalContext.current
    var failedKey by remember(asset.playbackKey, asset.localFilePath) { mutableStateOf(false) }
    if (failedKey) return

    var firstFrameRendered by remember(asset.playbackKey, asset.localFilePath) { mutableStateOf(false) }
    val mediaMotion = rememberMotionSpec(MotionToken.MediaExpand)
    val alpha by animateFloatAsState(
        targetValue = if (firstFrameRendered) 1f else 0f,
        animationSpec = mediaMotion.animationSpec(),
        label = "bgCanvasFade",
    )

    Box(modifier.graphicsLayer { this.alpha = alpha }) {
        CanvasVideoSurface(
            filePath = asset.localFilePath,
            playing = playing,
            modifier = Modifier.fillMaxSize(),
            onFirstFrame = {
                if (!firstFrameRendered) {
                    firstFrameRendered = true
                    AppCanvasRepository.get(context).recordFirstFrame()
                }
            },
            onError = { errorCode ->
                if (!failedKey) {
                    failedKey = true
                    AppCanvasRepository.get(context).recordPlaybackError(errorCode)
                }
            },
        )
    }
}

@Composable
internal fun CanvasVideoSurface(
    filePath: String,
    playing: Boolean,
    modifier: Modifier = Modifier,
    onFirstFrame: () -> Unit = {},
    onError: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val currentOnFirstFrame by rememberUpdatedState(onFirstFrame)
    val currentOnError by rememberUpdatedState(onError)
    val player = remember(context) {
        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
        }
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                // Never acquire audio focus or interrupt music playback.
                setAudioAttributes(AudioAttributes.DEFAULT, false)
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                currentOnFirstFrame()
            }

            override fun onPlayerError(error: PlaybackException) {
                currentOnError(error.errorCodeName)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, filePath) {
        val file = File(filePath)
        if (!file.isFile || file.length() <= 0L) {
            currentOnError("missing_file")
            return@LaunchedEffect
        }
        player.setMediaItem(MediaItem.fromUri(file.toURI().toString()))
        player.prepare()
    }

    LaunchedEffect(player, playing) {
        player.playWhenReady = playing
    }

    var attachedView by remember { mutableStateOf<TextureView?>(null) }
    DisposableEffect(player, attachedView) {
        val tv = attachedView
        if (tv != null) {
            player.setVideoTextureView(tv)
        }
        onDispose {
            if (tv != null) {
                player.clearVideoTextureView(tv)
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).also { attachedView = it }
        },
        modifier = modifier,
    )
}
