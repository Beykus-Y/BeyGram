package com.beykus.beygram.playback

import android.content.Context
import android.view.TextureView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
internal class SpeechPlayback(
    private val scope: CoroutineScope,
    private val canPlay: () -> Boolean,
    private val onActivityChanged: (PlaybackItem?) -> Unit,
) {
    private val current = MutableStateFlow(PlaybackState())
    val state = current.asStateFlow()
    private var player: ExoPlayer? = null
    private var ticker: Job? = null
    private var surface: Pair<Any, TextureView>? = null
    private var foreground = true
    private var startedCallback: (() -> Unit)? = null
    private var pendingSeek: Long? = null

    fun start(
        context: Context,
        item: PlaybackItem,
        media: MediaItem,
        positionMs: Long,
        speed: Float,
        onStarted: (() -> Unit)?,
        preview: Boolean,
        registerSource: () -> Unit,
    ) {
        stop(releasePolicy = false)
        registerSource()
        startedCallback = onStarted
        current.value = PlaybackState(item = item, intentPlaying = true, speed = speed, voiceSpeed = speed)
        val target = ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(PlaybackDataSourceFactory(context.applicationContext)))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                false,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = target
        target.volume = AudioCoexistenceSettings.state.value.speechPercent / 100f
        target.setPlaybackSpeed(speed)
        target.addListener(object : Player.Listener {
            override fun onEvents(source: Player, events: Player.Events) {
                if (player !== target) return
                if (target.playerError != null || target.playbackState == Player.STATE_ENDED) {
                    val failed = target.playerError != null
                    val snapshot = current.value
                    stop()
                    if (!item.systemPrivate || (preview && failed)) current.value = snapshot.copy(
                        playing = false, intentPlaying = false, isBuffering = false, ended = !failed, failed = failed,
                    )
                    return
                }
                publish(target)
                onActivityChanged(item.takeIf { target.playWhenReady })
                dispatchStarted()
            }
        })
        pendingSeek = positionMs.takeIf { shouldDeferGrowingSeek(item.growing, it) }
        target.setMediaItem(media, if (pendingSeek != null) 0 else positionMs)
        onActivityChanged(item)
        target.prepare()
        if (canPlay()) target.play() else onActivityChanged(null)
        ticker = scope.launch {
            while (isActive && player === target) {
                delay(100)
                if (player !== target) break
                val deferred = pendingSeek
                if (deferred != null && !shouldDeferGrowingSeek(item.growing, deferred)) {
                    pendingSeek = null
                    target.seekTo(deferred)
                }
                publish(target)
            }
        }
    }

    fun toggle(key: String): Boolean {
        val target = player ?: return false
        val item = state.value.item?.takeIf { it.key == key } ?: return false
        if (target.playWhenReady) {
            target.pause()
            onActivityChanged(null)
        } else {
            onActivityChanged(item)
            if (canPlay()) target.play() else onActivityChanged(null)
        }
        publish(target)
        return true
    }

    fun seek(key: String, positionMs: Long) {
        val item = state.value.item?.takeIf { it.key == key } ?: return
        val target = player ?: return
        pendingSeek = positionMs.takeIf { shouldDeferGrowingSeek(item.growing, it) }
        if (pendingSeek == null) target.seekTo(positionMs)
        publish(target)
    }

    fun setForeground(active: Boolean) {
        foreground = active
        if (!active && state.value.item?.systemPrivate == true) stop()
        if (active) dispatchStarted()
    }

    fun pause() {
        val target = player ?: return
        target.pause()
        publish(target)
        onActivityChanged(null)
    }

    fun setGain(gain: Float) {
        player?.volume = gain
    }

    fun setSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
        current.value = state.value.copy(speed = speed, voiceSpeed = speed)
    }

    fun attach(key: String, owner: Any, view: TextureView) {
        if (state.value.item?.key != key || surface?.second === view) return
        val target = player ?: return
        surface?.second?.let(target::clearVideoTextureView)
        surface = owner to view
        target.setVideoTextureView(view)
    }

    fun detach(owner: Any) {
        val attached = surface ?: return
        if (attached.first !== owner) return
        player?.clearVideoTextureView(attached.second)
        surface = null
    }

    fun stop(releasePolicy: Boolean = true) {
        ticker?.cancel()
        ticker = null
        val previous = player
        player = null
        previous?.release()
        surface = null
        pendingSeek = null
        startedCallback = null
        state.value.item?.systemMediaId?.let(PlaybackSources::remove)
        current.value = PlaybackState()
        if (releasePolicy) onActivityChanged(null)
    }

    private fun dispatchStarted() {
        if (!foreground || !state.value.started) return
        val callback = startedCallback ?: return
        startedCallback = null
        callback()
    }

    private fun publish(target: ExoPlayer) {
        val snapshot = current.value
        current.value = snapshot.copy(
            playing = target.isPlaying,
            intentPlaying = target.playWhenReady,
            started = snapshot.started || target.isPlaying,
            positionMs = pendingSeek ?: target.currentPosition,
            durationMs = target.duration.takeIf { it > 0 } ?: (snapshot.item?.durationSeconds ?: 0) * 1000L,
            isBuffering = target.playbackState == Player.STATE_BUFFERING,
            bufferedFraction = target.bufferedPercentage / 100f,
        )
    }
}
