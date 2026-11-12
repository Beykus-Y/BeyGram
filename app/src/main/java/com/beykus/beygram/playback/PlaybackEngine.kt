package com.beykus.beygram.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
import android.os.SystemClock
import android.view.TextureView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.beykus.beygram.diagnostics.recordAppEvent
import com.beykus.beygram.diagnostics.report.EventKind
import com.beykus.beygram.diagnostics.report.Severity
import com.beykus.beygram.data.media.AutoDownloadDecision
import com.beykus.beygram.data.media.AutoDownloadNetwork
import com.beykus.beygram.data.media.AutoDownloadNetworkStatus
import com.beykus.beygram.data.media.AutoDownloadRuntime
import com.beykus.beygram.data.media.Mp4StreamingProbe
import com.beykus.beygram.data.media.estimatedStreamBytes
import com.beykus.beygram.data.settings.AutoDownloadSettingsStore
import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.media.bgmix.BgMixAudible
import com.beykus.beygram.media.bgmix.BgMixNative
import com.beykus.beygram.media.bgmix.BgMixSettingsStore
import com.beykus.beygram.media.bgmix.BgMixStatus
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Containers that can be parsed from the beginning; MP4/M4A additionally needs a fast-start index. */
private val DIRECT_AUDIO_MIME = setOf(
    "audio/mpeg", "audio/mp3", "audio/ogg", "audio/opus", "audio/aac",
    "audio/flac", "audio/x-flac", "audio/webm", "audio/3gpp", "audio/amr"
)
private val MP4_AUDIO_MIME = setOf("audio/mp4", "audio/m4a", "audio/x-m4a")
/** Ogg/Opus seek tables live at the end; an advertised total length makes Media3 read that page first. */
private const val LATE_MEASURE_MS = 30_000L

private val UNKNOWN_LENGTH_AUDIO_MIME = setOf("audio/ogg", "audio/opus")

/**
 * Whether an audio file can be played before TDLib finishes it. Formats with leading metadata
 * (MP3/OGG/Opus/AAC/FLAC/WebM) stream directly; MP4 audio requires a complete `moov` before `mdat`
 * and a supported audio codec, otherwise the viewer keeps the full-download fallback.
 */
internal fun canPlayAudioProgressively(mimeType: String, path: String?, availableBytes: Long): Boolean {
    val normalized = mimeType.substringBefore(';').trim().lowercase()
    return when (normalized) {
        in DIRECT_AUDIO_MIME -> true
        in MP4_AUDIO_MIME -> path != null && availableBytes > 0L &&
            runCatching { Mp4StreamingProbe.supportsAudioStreaming(File(path), availableBytes) }.getOrDefault(false)
        else -> false
    }
}

internal fun isDirectProgressiveAudio(mimeType: String): Boolean =
    mimeType.substringBefore(';').trim().lowercase() in DIRECT_AUDIO_MIME

internal fun isMp4AudioMime(mimeType: String): Boolean =
    mimeType.substringBefore(';').trim().lowercase() in MP4_AUDIO_MIME

/** The path to start from: completed file, or a readable partial prefix for a supported audio container. */
internal fun partialAudioPlaybackPath(file: MediaFileState, mimeType: String): String? =
    file.localPath ?: file.downloadPath?.takeIf {
        file.size > 0L && file.downloadedPrefixBytes > 0L &&
            canPlayAudioProgressively(mimeType, it, file.downloadedPrefixBytes)
    }

/** Ogg/Opus cannot seek before completion; defer the requested position until a seekable source is available. */
internal fun shouldDeferGrowingSeek(growing: GrowingPlaybackSource?, positionMs: Long): Boolean =
    growing != null && positionMs > 0L && !growing.isComplete() && !growing.advertiseLength

/** Builds live source callbacks for an in-progress audio/voice file. */
internal fun growingAudioSource(
    state: MediaFileState,
    fileId: Int,
    mimeType: String,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onNeedMore: () -> Unit,
    assumeSupported: Boolean = false
): GrowingPlaybackSource? {
    val path = state.downloadPath ?: return null
    if (state.localPath != null || state.size <= 0L || state.downloadedPrefixBytes <= 0L) return null
    if (!assumeSupported && !canPlayAudioProgressively(mimeType, path, state.downloadedPrefixBytes)) return null
    val normalized = mimeType.substringBefore(';').trim().lowercase()
    return GrowingPlaybackSource(
        totalSize = state.size,
        readablePrefix = { observeFile(fileId).value.downloadedPrefixBytes },
        isComplete = { observeFile(fileId).value.localPath != null },
        completedPath = { observeFile(fileId).value.localPath },
        isDownloading = { observeFile(fileId).value.isDownloading },
        onNeedMore = onNeedMore,
        advertiseLength = normalized !in UNKNOWN_LENGTH_AUDIO_MIME
    )
}

/** A seek requested on an unseekable growing Ogg/Opus source; applied after completion. */
private data class DeferredGrowingSeek(val key: String, val positionMs: Long, val resume: Boolean)

/**
 * Passes ExoPlayer events to a session listener, except while BG Mix owns playback. The BG Mix values
 * reach the session through the callbacks the engine calls directly.
 */
private class BgMixAwareListener(
    private val target: Player.Listener,
    private val bgMixOwnsPlayback: () -> Boolean
) : Player.Listener by target {
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!bgMixOwnsPlayback()) target.onIsPlayingChanged(isPlaying)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!bgMixOwnsPlayback()) target.onPlayWhenReadyChanged(playWhenReady, reason)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (!bgMixOwnsPlayback()) target.onPlaybackStateChanged(playbackState)
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (!bgMixOwnsPlayback()) target.onPositionDiscontinuity(oldPosition, newPosition, reason)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (!bgMixOwnsPlayback()) target.onMediaItemTransition(mediaItem, reason)
    }

    override fun onPlayerError(error: PlaybackException) {
        if (!bgMixOwnsPlayback()) target.onPlayerError(error)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (!bgMixOwnsPlayback()) target.onEvents(player, events)
    }
}

/**
 * Общий плеер приложения. Музыка, голосовые и кружки играют через один ExoPlayer, поэтому звук не обрывается
 * при переходе между экранами. Плеер живёт в процессе, а [PlaybackService] держит его на переднем плане,
 * пока он играет: уведомление, экран блокировки и гарнитура работают через систему.
 *
 * The ExoPlayer playlist and [PlaybackState.queue] are kept in lockstep: the queue is the single public source
 * of truth and Media3/MediaSession sees the same items, modes and commands.
 */
object PlaybackEngine {
    private const val PROGRESS_INTERVAL_MS = 250L
    private const val PROGRESS_FAST_INTERVAL_MS = 50L
    private const val BG_DUCK_VOLUME = 0.25f
    /** How many queue items after the session's last track BG Mix downloads ahead. */
    private const val BG_MIX_DOWNLOAD_AHEAD = 2
    private const val BG_MIX_FEED_INTERVAL_MS = 1_000L
    private const val BG_HANDOVER_POLL_MS = 500L
    private const val BG_HANDOVER_IDLE_POLL_MS = 1_000L
    private const val BG_HANDOVER_RETRY_MS = 2_000L
    private const val BG_HANDOVER_ATTEMPTS = 3
    /** A track this close to its end stays on ExoPlayer; BG Mix takes the next one. */
    private const val BG_HANDOVER_MIN_REMAINING_MS = 15_000L
    private const val BG_HANDOVER_PREPARE_TIMEOUT_MS = 30_000L
    private const val BG_HANDOVER_START_TIMEOUT_MS = 500L
    /** Lead of the native position over ExoPlayer's: only the starting guess, the alignment measures the real one. */
    private const val BG_HANDOVER_LEAD_MS = 120L
    /** The silent BG Mix output is lined up with ExoPlayer by measurement, with at most this many seeks. */
    private const val BG_ALIGN_ATTEMPTS = 3
    /** Within this many milliseconds the output counts as lined up with ExoPlayer. */
    private const val BG_ALIGN_TOLERANCE_MS = 8.0
    /** How long to wait for the output to present audio: the first time, and again after each seek. */
    private const val BG_ALIGN_WAIT_MS = 1_500L
    private const val BG_ALIGN_POLL_MS = 10L
    /** Both outputs play together this long, so the handover never leaves a gap. */
    private const val BG_HANDOVER_OVERLAP_MS = 60L
    /** ExoPlayer falls and BG Mix rises over this long, in equal steps, once BG Mix has started silently. */
    private const val BG_HANDOVER_FADE_MS = 200L
    private const val BG_HANDOVER_FADE_STEPS = 10
    private const val SLEEP_FADE_STEP_MS = 100L
    private const val UNDO_CLOSE_MS = 5_000L
    private const val BG_WAKE_LOCK_TIMEOUT_MS = 6 * 60 * 60_000L
    private const val INTERRUPTION_RESUME_LIMIT_MS = 2 * 60_000L
    private const val INTERRUPTION_STALE_MS = 15 * 60_000L
    private const val INTERRUPTION_FADE_IN_MS = 400L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** The queue view shows a real TDLib page request rather than claiming the playlist ended prematurely. */
    private val _queuePageLoading = MutableStateFlow(false)
    val queuePageLoading: StateFlow<Boolean> = _queuePageLoading.asStateFlow()

    private val _sleepTimer = MutableStateFlow<SleepTimerState?>(null)
    val sleepTimer: StateFlow<SleepTimerState?> = _sleepTimer.asStateFlow()

    private val _bgMixTransition = MutableStateFlow<BgMixTransitionUi?>(null)
    /** The BG Mix transition that is mixing right now, or null; kept apart from [state] so its ticks touch only the status line. */
    val bgMixTransition: StateFlow<BgMixTransitionUi?> = _bgMixTransition.asStateFlow()
    private var sleepTimerJob: Job? = null

    /** A sleep timer survives track changes inside one account, but never a switch to another account. */
    fun setSleepTimer(minutes: Int?) {
        cancelSleepTimer()
        if (minutes == null || minutes !in 1..SLEEP_TIMER_MAX_MINUTES) return
        val current = _state.value.item?.takeIf { it.isMusic && !it.systemPrivate } ?: return
        val deadline = SystemClock.elapsedRealtime() + minutes * 60_000L
        startSleepTimer(SleepTimerState(current.accountId, deadline))
    }

    /** The timer ends with the playing track instead of after a fixed time. */
    fun setSleepTimerUntilTrackEnd() {
        cancelSleepTimer()
        val current = _state.value.item?.takeIf { it.isMusic && !it.systemPrivate } ?: return
        startSleepTimer(SleepTimerState(current.accountId, deadlineElapsedMs = 0L, untilTrackEnd = true, trackKey = current.key))
    }

    /** Adds [minutes] to a running timer; without one, starts a timer of that length. */
    fun extendSleepTimer(minutes: Int) {
        val running = _sleepTimer.value
        if (running == null || running.untilTrackEnd) {
            setSleepTimer(minutes)
            return
        }
        _sleepTimer.value = running.copy(deadlineElapsedMs = running.deadlineElapsedMs + minutes * 60_000L)
    }

    private fun startSleepTimer(session: SleepTimerState) {
        _sleepTimer.value = session
        sleepTimerJob = scope.launch {
            // The deadline is compared with elapsedRealtime on every second, so a pause of the handler clock while
            // the processor sleeps only delays the check and never moves the deadline.
            while (true) {
                val timer = _sleepTimer.value ?: return@launch
                val state = _state.value
                if (state.item?.accountId != timer.accountId) {
                    cancelSleepTimer()
                    return@launch
                }
                val remaining = if (timer.untilTrackEnd) {
                    if (state.item.key != timer.trackKey) {
                        cancelSleepTimer()
                        return@launch
                    }
                    val target = exo
                    if (bgMixActive()) {
                        // BG Mix plays outside ExoPlayer, which sits paused on a placeholder: the published state is the truth.
                        if (state.durationMs <= 0L) Long.MAX_VALUE else state.durationMs - state.positionMs
                    } else {
                        val duration = target?.duration ?: C.TIME_UNSET
                        if (target == null || duration == C.TIME_UNSET) Long.MAX_VALUE else duration - target.currentPosition
                    }
                } else {
                    timer.deadlineElapsedMs - SystemClock.elapsedRealtime()
                }
                if (remaining <= SLEEP_FADE_MS) {
                    fadeOutAndPause(remaining.coerceAtLeast(0L))
                    cancelSleepTimer()
                    return@launch
                }
                delay(minOf(1_000L, remaining - SLEEP_FADE_MS))
            }
        }
    }

    /** Lowers the volume smoothly, then pauses. The queue and the saved place stay, so the music can continue later. */
    private suspend fun fadeOutAndPause(fadeMs: Long) {
        val target = exo
        if (target == null || bgMixActive() || !(_state.value.playing || _state.value.intentPlaying)) {
            pause()
            return
        }
        val original = target.volume
        val steps = (fadeMs / SLEEP_FADE_STEP_MS).toInt().coerceAtLeast(1)
        try {
            for (step in 1..steps) {
                target.volume = original * (1f - step.toFloat() / steps)
                delay(SLEEP_FADE_STEP_MS)
            }
            pause()
        } finally {
            // A cancelled fade or a finished one both give the volume back, so the next play is not silent.
            target.volume = original
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimer.value = null
    }

    /** The current ExoPlayer audio session; the equalizer attaches to exactly this session. */
    private val _audioSessionId = MutableStateFlow<Int?>(null)
    val audioSessionId: StateFlow<Int?> = _audioSessionId.asStateFlow()

    /** Snapshot for callers that need to attach an audio effect immediately. */
    fun currentAudioSessionId(): Int? = _audioSessionId.value

    private val _showInChat = MutableSharedFlow<PlaybackOrigin>(extraBufferCapacity = 1)
    /** Запросы открыть чат с сообщением текущего файла. Их разбирает корневой экран приложения. */
    val showInChat: SharedFlow<PlaybackOrigin> = _showInChat.asSharedFlow()

    private var exo: ExoPlayer? = null
    private var controls: Player? = null
    private var appContext: Context? = null
    private var progress: Job? = null
    private var pendingStartFraction = 0f
    private var deferredSeek: DeferredGrowingSeek? = null
    private var deferredSeekJob: Job? = null
    private var videoOwner: Any? = null
    private var videoView: TextureView? = null
    /** Музыка, которая ждёт, пока голосовое или кружок доиграет. Очищается при остановке и при новом запуске музыки. */
    private var interruptedMusic: InterruptedMusic? = null
    /** Lazily resolves not-yet-downloaded queue items for the current account. */
    private var queueSource: PlaybackQueueSource? = null
    private var queuePageJob: Job? = null
    private var persistence: MusicSessionPersistence? = null
    private var queuePageRetryJob: Job? = null
    private var queuePageRetries = 0
    /** Tracks skipped in a row without one starting; see [MAX_CONSECUTIVE_SKIPS]. */
    private var consecutiveSkips = 0
    /** Start of the current wait for the network, 0 when the queue is not waiting. */
    private var networkWaitSince = 0L
    private var networkWaitJob: Job? = null

    private val _notices = MutableSharedFlow<PlaybackNotice>(extraBufferCapacity = 4)
    /** Short events for the root screen to show, such as a track that was skipped because it cannot be opened. */
    val notices: SharedFlow<PlaybackNotice> = _notices.asSharedFlow()
    /** Invalidates a page returning after another chat, account or queue takes over the player. */
    private var queuePageGeneration = 0L
    /** Отметка «прослушано», которую плеер вызовет, когда файл начнёт играть. Работает, даже если пузырь уже ушёл с экрана. */
    private var startedCallback: Pair<String, () -> Unit>? = null
    /** Приложение на переднем плане. Отметку репозиторий принимает только отсюда, поэтому файл, начавший играть в фоне, ждёт возврата. */
    private var foreground = false
    /** Changing this token invalidates an asynchronously delivered ENDED from the previous source. */
    private var transitionToken = 0L
    private var activeAccountId: String? = null
    val currentAccountId: String? get() = activeAccountId
    private val prefetchedKeys = mutableSetOf<String>()
    private var prefetchPolicyJob: Job? = null
    private var prefetchPolicyAccount: String? = null
    /** BG Mix music path: used only for eligible local music queues while the experimental flag is on. */
    private var bgMix: BgMixQueueDriver? = null
    /** Listeners the media session registered on the wrapper; BG Mix events go to them directly. */
    private val bgMixListeners = mutableSetOf<Player.Listener>()
    /** Session listener -> the copy that drops ExoPlayer events while BG Mix plays. */
    private val bgMixFiltered = mutableMapOf<Player.Listener, Player.Listener>()
    private var bgFocusRequest: AudioFocusRequest? = null
    private var bgNoisyRegistered = false
    private var bgMixJob: Job? = null
    private var bgMixSeekJob: Job? = null
    /** Queue order from before BG Mix shuffled it; null while shuffle is off. */
    private var bgMixOriginalQueue: List<PlaybackItem>? = null
    /** Brings a queue that ExoPlayer plays into BG Mix once the playing track is a finished file; see [scheduleBgMixHandover]. */
    private var bgMixHandoverJob: Job? = null
    /** Counts the handover cancellations: a crossfade that started under an older count has been superseded. */
    private var bgMixHandoverGeneration = 0
    /** Adds tracks that finished downloading to the running BG Mix session and asks for the next ones; see [startBgMixFeed]. */
    private var bgMixFeedJob: Job? = null

    /** Key of the track that was moved past early for trailing silence. Cleared by the next media item transition. */
    private var trailingSkippedKey: String? = null

    /** Overlaps the end of a music track with the start of the next one, when the crossfade setting is on. */
    private val crossfader = TrackCrossfader(
        scope = scope,
        enabled = { TrackTransitionSettings.crossfade.value },
        busy = { bgMixActive() || deferredSeek != null },
        newPlayer = { newCrossfadePlayer() },
        trailingMs = { mediaId -> crossfadeTrailingMs(mediaId) },
        nextAfter = { primary, mediaId -> crossfadeNextAfter(primary, mediaId) },
        overlapMs = { TrackTransitionSettings.crossfadeSeconds.value * 1_000L },
    )

    /**
     * A cover thumbnail may finish downloading after playback has already started. The key carries account/origin,
     * so another account's file with the same numeric id is never updated.
     */
    fun updateArtwork(key: String, path: String) {
        // The BG Mix queue is copied from its native session on every status tick: the cover must be in that copy too.
        bgMix?.updateArtwork(key, path)
        val snapshot = _state.value
        val item = snapshot.item
        val queue = snapshot.queue
        val updatedQueue = queue.map { if (it.key == key) it.copy(artworkPath = path) else it }
        val updatedItem = item?.takeIf { it.key == key }?.copy(artworkPath = path)
        if (updatedItem == item && updatedQueue == queue) return
        _state.update { it.copy(item = updatedItem ?: it.item, queue = updatedQueue) }

        val current = updatedItem ?: updatedQueue.firstOrNull { it.key == key } ?: return
        if (current.systemPrivate) return
        PlaybackSources.update(current.systemMediaId, current)
        val target = exo ?: return
        val index = (0 until target.mediaItemCount).firstOrNull {
            target.getMediaItemAt(it).mediaId == current.systemMediaId
        } ?: return
        // During BG Mix ExoPlayer holds the placeholder; its metadata is what the notification and the lock screen show.
        val replacement = if (bgMixActive()) placeholderItem(current) else mediaItemFor(current)
        // Only the metadata changed. Media3 updates an item with the same address in place, so the playhead is not
        // touched: an explicit seek here would flush the decoder buffers and cause a glitch in a fresh track.
        val existingUri = target.getMediaItemAt(index).localConfiguration?.uri
        val sameSource = existingUri == replacement.localConfiguration?.uri
        val position = target.currentPosition
        val wasPlaying = target.isPlaying
        target.replaceMediaItem(index, replacement)
        if (!sameSource && index == target.currentMediaItemIndex) {
            // A genuinely different source for the playing item is reopened at the same position.
            target.seekTo(position)
            if (wasPlaying) target.play()
        }
    }

    /** The process-wide player used by the MediaSession (notification, lockscreen, Bluetooth). */
    fun player(context: Context): Player = controls ?: createPlayer(context).also { controls = it }

    private fun rawPlayer(context: Context): ExoPlayer {
        player(context)
        return exo!!
    }

    /** Единственный ExoPlayer процесса. Создаётся на главном потоке при первом обращении. */
    @OptIn(UnstableApi::class)
    private fun createPlayer(context: Context): Player {
        val created = ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(PlaybackDataSourceFactory(context.applicationContext)))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            // Keeps the CPU and Wi-Fi awake while a track plays or loads, so a locked screen cannot stall the queue.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also {
                it.addListener(listener)
                it.repeatMode = _state.value.repeatMode.toPlayerRepeatMode()
                it.shuffleModeEnabled = _state.value.shuffleEnabled
                it.setPlaybackSpeed(_state.value.musicSpeed)
            }
        exo = created
        appContext = context.applicationContext
        persistence = MusicSessionPersistence(
            context.applicationContext,
            _state,
            activeAccount = { activeAccountId },
        ).also { it.start(scope) }
        TrackTransitionSettings.load(context)
        _audioSessionId.value = null
        return object : ForwardingPlayer(created) {
            override fun play() {
                if (bgMixActive()) {
                    bgMixPlay()
                    return
                }
                if (deferredSeek != null) {
                    deferredSeek = deferredSeek?.copy(resume = true)
                    _state.update { it.copy(intentPlaying = true) }
                    return
                }
                if (playbackState == Player.STATE_IDLE) prepare()
                if (playbackState == Player.STATE_ENDED) {
                    // Секретный файл после конца не повторяется ни кнопкой уведомления, ни мини-плеером.
                    if (_state.value.item?.systemPrivate == true) return
                    seekToDefaultPosition()
                }
                // Повтор доигравшего файла снимает отметку конца сразу, чтобы возврат музыки не сработал до первого события плеера.
                _state.update { it.copy(ended = false, failed = false, intentPlaying = true) }
                if (_state.value.item?.systemPrivate != true) startPlaybackService()
                super.play()
                prefetchNext()
            }

            override fun pause() {
                if (bgMixActive()) {
                    pauseBgMix()
                    return
                }
                // System/lock-screen/headset pause must cancel a pending deferred resume as well.
                deferredSeek = deferredSeek?.copy(resume = false)
                _state.update { it.copy(intentPlaying = false, playing = false) }
                progress?.cancel()
                progress = null
                super.pause()
            }

            override fun getAvailableCommands(): Player.Commands {
                val base = super.getAvailableCommands()
                if (!bgMixActive()) return base
                return Player.Commands.Builder().addAll(base)
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                if (bgMixActive() && (command == Player.COMMAND_PLAY_PAUSE || command == Player.COMMAND_SEEK_TO_NEXT)) {
                    return true
                }
                return super.isCommandAvailable(command)
            }

            // The headset and the notification use the app's own Next and Previous, so Repeat One, shuffle and BG Mix
            // behave the same as with the buttons on the screen.
            override fun seekToNext() {
                this@PlaybackEngine.next()
            }

            override fun seekToNextMediaItem() {
                this@PlaybackEngine.next()
            }

            override fun seekToPrevious() {
                this@PlaybackEngine.previous()
            }

            override fun seekToPreviousMediaItem() {
                this@PlaybackEngine.previous()
            }

            override fun seekTo(positionMs: Long) {
                if (bgMixActive()) seekToPosition(positionMs) else super.seekTo(positionMs)
            }

            override fun addListener(listener: Player.Listener) {
                bgMixListeners.add(listener)
                bgMixFiltered.remove(listener)?.let { super.removeListener(it) }
                val filtered = BgMixAwareListener(listener) { bgMixActive() }
                bgMixFiltered[listener] = filtered
                super.addListener(filtered)
            }

            override fun removeListener(listener: Player.Listener) {
                bgMixListeners.remove(listener)
                bgMixFiltered.remove(listener)?.let { super.removeListener(it) }
            }

            override fun isPlaying(): Boolean = if (bgMixActive()) _state.value.playing else super.isPlaying()

            override fun getPlayWhenReady(): Boolean =
                if (bgMixActive()) _state.value.intentPlaying else super.getPlayWhenReady()

            override fun getPlaybackState(): Int = when {
                !bgMixActive() -> super.getPlaybackState()
                _state.value.ended -> Player.STATE_ENDED
                else -> Player.STATE_READY
            }

            override fun getCurrentPosition(): Long =
                if (bgMixActive()) _state.value.positionMs else super.getCurrentPosition()

            override fun getContentPosition(): Long =
                if (bgMixActive()) _state.value.positionMs else super.getContentPosition()

            override fun getDuration(): Long =
                if (bgMixActive()) _state.value.durationMs.takeIf { it > 0L } ?: C.TIME_UNSET else super.getDuration()

            override fun stop() {
                // MediaSession "stop" closes the session completely instead of leaving a phantom notification.
                PlaybackEngine.stop()
            }
        }
    }

    /**
     * Тот же файл ставит на паузу или продолжает, другой файл начинает играть сначала. [onStarted] вызывается один раз, когда
     * файл начнёт играть, даже если пузырь, который его запустил, уже ушёл с экрана. Если это случилось в фоне, вызов ждёт [setForeground].
     */
    fun toggle(context: Context, item: PlaybackItem, onStarted: (() -> Unit)? = null, startPositionMs: Long? = null) =
        startItem(context, item, emptyList(), -1, source = null, onStarted = onStarted, startPositionMs = startPositionMs)

    /** Запускает [index]-й файл очереди [items]. Когда он доиграет, следующий файл очереди начинает играть сам. Тап по играющему файлу ставит его на паузу. */
    fun playQueue(context: Context, items: List<PlaybackItem>, index: Int, onStarted: (() -> Unit)? = null) {
        val item = items.getOrNull(index) ?: return
        startItem(context, item, items, index, source = null, onStarted = onStarted)
    }

    /**
     * Plays Yandex Music tracks as one queue. Rows carry no file: each is downloaded when it becomes current or next.
     * Tracks the provider reports as unavailable are left out; [startIndex] counts the tracks as given. The queue starts at
     * the chosen track: the rows above it were never played, so the queue must not show them as played.
     */
    fun playYandexTracks(
        context: Context,
        tracks: List<com.beykus.beygram.data.music.provider.ProviderTrack>,
        startIndex: Int,
    ) {
        val playable = tracks.filter { it.isAvailable }
        if (playable.isEmpty()) return
        val startTrackId = tracks.getOrNull(startIndex)?.trackId
        val index = playable.indexOfFirst { it.trackId == startTrackId }.coerceAtLeast(0)
        val items = playable.drop(index).map { track ->
            PlaybackItem(
                key = track.key,
                kind = MediaKind.Audio,
                path = "",
                title = track.title,
                durationSeconds = (track.durationMs / 1_000L).toInt(),
                performer = track.performer,
                trackTitle = track.title,
                album = track.albumTitle.orEmpty(),
            )
        }
        playQueue(context, items, 0, source = null)
    }

    /**
     * Queue variant with a lazy TDLib source. Tracks that are not downloaded yet stay in the queue and are resolved
     * on demand; only the current item and 1–2 following items may request a download.
     */
    fun playQueue(
        context: Context,
        items: List<PlaybackItem>,
        index: Int,
        source: PlaybackQueueSource?,
        onStarted: (() -> Unit)? = null,
        startPositionMs: Long? = null
    ) {
        val item = items.getOrNull(index) ?: return
        startItem(context, item, items, index, source = source, onStarted = onStarted, startPositionMs = startPositionMs)
    }

    /**
     * Restores a revalidated TDLib queue after process death. No playback, MediaSession service or prefetch
     * begins until the user explicitly presses Play. A live player always wins over disk state.
     */
    fun restoreQueue(
        context: Context,
        items: List<PlaybackItem>,
        index: Int,
        source: PlaybackQueueSource,
        positionMs: Long,
        shuffle: Boolean,
        repeat: PlaybackRepeatMode
    ) {
        if (_state.value.item != null || items.isEmpty() || index !in items.indices) return
        if (activeAccountId != null && source.accountId != activeAccountId) return
        if (items.any { !it.isMusic || it.systemPrivate || it.accountId != source.accountId || it.origin == null }) return
        val queue = queueForPlayback(items, index)
        if (queue.items.isEmpty()) return
        _state.update { it.copy(shuffleEnabled = shuffle, repeatMode = repeat) }
        startQueue(context, queue, source, startPositionMs = positionMs, startFraction = 0f, autoPlay = false)
    }

    /** Общая часть [toggle] и [playQueue]: тот же файл ставит на паузу или продолжает, другой запускает с очередью [items]. */
    private fun startItem(
        context: Context,
        item: PlaybackItem,
        items: List<PlaybackItem>,
        index: Int,
        source: PlaybackQueueSource?,
        onStarted: (() -> Unit)?,
        startPositionMs: Long? = null
    ) {
        YandexTrackResolver.ensureConfigured(context)
        if (onStarted != null) startedCallback = item.key to onStarted
        val currentState = _state.value
        if (bgMixActive() && currentState.item?.key == item.key) {
            // A shared timecode wins over the mix position: the user asked for that exact moment of the track.
            if (startPositionMs != null) seekToPosition(startPositionMs)
            bgMixPlay()
            return
        }
        if (exo != null && currentState.item?.key == item.key) {
            // A file that plays without a queue (for example from the chat feed) adopts the queue of the music tab,
            // so after pause/resume it continues there. The same file from another account/message has another key
            // and deliberately starts a new context instead.
            val given = if (items.isNotEmpty() && index in items.indices) queueForPlayback(items, index) else null
            val nextQueue = queueAfterReselect(
                PlaybackQueue(currentState.queue, currentState.queueIndex),
                item,
                given,
                currentOrigin = currentState.item?.origin
            )
            if (source != null) queueSource = source
            val merged = mergePlaybackItem(item, currentState.item)
            PlaybackSources.update(merged.systemMediaId, merged)
            val nextItems = nextQueue.items.map { if (it.key == merged.key) merged else it }
            val nextIndex = nextQueue.index.takeIf { it in nextItems.indices } ?: nextItems.indexOfFirst { it.key == merged.key }
            val shouldPlay = currentState.playing || currentState.intentPlaying
            _state.update { it.copy(item = merged, queue = nextItems, queueIndex = nextIndex).withTimelineNavigation(exo) }
            if (timelineChanged(currentState, nextItems, nextIndex)) {
                if (!replaceQueueAroundCurrent(nextItems, nextIndex, currentState.queue.size)) {
                    rebuildTimeline(nextItems, nextIndex, currentState.positionMs, shouldPlay)
                }
            }
            if (startPositionMs != null) {
                // A timecode never pauses: it moves the track to that moment and plays from there.
                seekToPosition(startPositionMs)
                if (!(currentState.playing || currentState.intentPlaying)) togglePlayPause()
                return
            }
            // Second tap on the same file keeps the documented pause/resume semantics.
            togglePlayPause()
            return
        }

        if (item.kind == MediaKind.Audio && !item.systemPrivate) {
            val given = if (items.isNotEmpty() && index in items.indices) queueForPlayback(items, index) else null
            // A voice/video note may still be playing while the user taps the interrupted track. Restore its saved
            // position/queue/modes before interruptedAfterStart clears the waiting state.
            val restore = interruptedMusicRestore(interruptedMusic, item.key, given, source)
            if (restore != null) {
                _state.update { it.copy(shuffleEnabled = restore.shuffleEnabled, repeatMode = restore.repeatMode) }
            }
            val resumeMs = startPositionMs ?: restore?.positionMs
            val effectiveSource = restore?.source ?: source
            val queue = restore?.queue ?: given ?: PlaybackQueue()
            queueSource = effectiveSource
            interruptedMusic = interruptedAfterStart(currentState, interruptedMusic, item, effectiveSource)
            if (queue.items.isNotEmpty()) {
                startQueue(context, queue, effectiveSource, startPositionMs = resumeMs, startFraction = 0f)
            } else {
                startSingle(context, item, startPositionMs = resumeMs, startFraction = 0f)
            }
            return
        }

        // Голосовое или кружок поверх музыки очередь не сбрасывают: музыка продолжится и дальше пойдёт по очереди.
        interruptedMusic = interruptedAfterStart(currentState, interruptedMusic, item, queueSource)
        queueSource = null
        startSingle(context, item, startPositionMs = startPositionMs, startFraction = 0f)
    }

    private fun mergePlaybackItem(item: PlaybackItem, previous: PlaybackItem?): PlaybackItem = item.copy(
        path = item.path.ifBlank { previous?.path.orEmpty() },
        growing = item.growing ?: previous?.growing.takeIf { item.path.isBlank() },
        artworkPath = item.artworkPath ?: previous?.artworkPath,
        performer = item.performer.ifBlank { previous?.performer.orEmpty() },
        trackTitle = item.trackTitle.ifBlank { previous?.trackTitle.orEmpty() },
        album = item.album.ifBlank { previous?.album.orEmpty() },
        mimeType = item.mimeType.ifBlank { previous?.mimeType.orEmpty() },
        sizeBytes = item.sizeBytes.takeIf { it > 0L } ?: previous?.sizeBytes ?: 0L
    )

    /**
     * Swaps the queue around the playing track without reopening it: Media3 keeps the current item (and its position
     * and buffer) and only the tracks before and after it are replaced. Returns false when the playlist cannot be
     * edited that way, and the caller rebuilds the timeline instead.
     */
    @OptIn(UnstableApi::class)
    private fun replaceQueueAroundCurrent(nextItems: List<PlaybackItem>, nextIndex: Int, oldCount: Int): Boolean {
        val target = exo ?: return false
        val oldIndex = target.currentMediaItemIndex
        val currentItem = nextItems.getOrNull(nextIndex) ?: return false
        if (bgMixActive() || target.mediaItemCount != oldCount || oldIndex !in 0 until target.mediaItemCount) return false
        if (target.getMediaItemAt(oldIndex).mediaId != currentItem.systemMediaId) return false
        val before = nextItems.subList(0, nextIndex)
        val after = nextItems.subList(nextIndex + 1, nextItems.size)
        before.forEach { registerSource(it, queueSource) }
        after.forEach { registerSource(it, queueSource) }
        if (oldIndex + 1 < target.mediaItemCount) target.removeMediaItems(oldIndex + 1, target.mediaItemCount)
        if (oldIndex > 0) target.removeMediaItems(0, oldIndex)
        if (before.isNotEmpty()) target.addMediaItems(0, before.map(::mediaItemFor))
        if (after.isNotEmpty()) target.addMediaItems(after.map(::mediaItemFor))
        installShuffleOrder(target, nextItems.size, currentIndex = nextIndex)
        prefetchedKeys.clear()
        prefetchNext()
        return true
    }

    private fun timelineChanged(current: PlaybackState, queue: List<PlaybackItem>, index: Int): Boolean =
        current.queue.map { it.key } != queue.map { it.key } || current.queueIndex != index

    /** Приложение ушло в фон или вернулось на передний план. Отметка, которую файл начал в фоне, уходит на возврате. */
    fun setForeground(active: Boolean) {
        foreground = active
        if (active) fireStartedCallback()
    }

    /**
     * The app's selected Telegram account changed. Music from the previous account must never keep playing under
     * another account's UI or leak its queue/artwork into the new session.
     */
    fun setActiveAccount(accountId: String) {
        if (accountId.isBlank()) return
        val previous = activeAccountId
        activeAccountId = accountId
        if (previous == null || previous == accountId) return
        val item = _state.value.item ?: return
        if (item.accountId != accountId) stop()
    }

    /**
     * Запускает файл с начала или с доли [startFraction]. Скорость сохраняется между файлами. Если запустили музыку, которую
     * прервало голосовое или кружок, она продолжается с того же места и из того же queue index.
     */
    fun play(
        context: Context,
        item: PlaybackItem,
        startFraction: Float = 0f,
        onStarted: (() -> Unit)? = null,
        startPositionMs: Long? = null
    ) {
        if (onStarted != null) startedCallback = item.key to onStarted
        val current = _state.value
        val waiting = interruptedMusic
        val restore = waiting?.takeIf { it.item.key == item.key }
        val resumeMs = startPositionMs ?: resumePositionMs(waiting, item.key)
        val interruptedSource = if (item.isMusic) restore?.source ?: queueSource else queueSource
        interruptedMusic = interruptedAfterStart(current, waiting, item, interruptedSource)
        if (item.isMusic && restore != null && restore.queueIndex in restore.queue.indices) {
            queueSource = restore.source
            _state.update { it.copy(shuffleEnabled = restore.shuffleEnabled, repeatMode = restore.repeatMode) }
            startQueue(
                context,
                PlaybackQueue(restore.queue, restore.queueIndex),
                restore.source,
                startPositionMs = resumeMs,
                startFraction = startFraction
            )
        } else {
            queueSource = if (item.isMusic) restore?.source else null
            startSingle(context, item, startPositionMs = resumeMs, startFraction = startFraction)
        }
    }

    @OptIn(UnstableApi::class)
    private fun startQueue(
        context: Context,
        queue: PlaybackQueue,
        source: PlaybackQueueSource?,
        startPositionMs: Long?,
        startFraction: Float,
        autoPlay: Boolean = true,
        offerBgMix: Boolean = true
    ) {
        val selected = queue.items.getOrNull(queue.index) ?: return
        // The queue always starts on ExoPlayer, which plays a local file at once and streams one that is still downloading.
        // With BG Mix on, the handover below moves the queue into BG Mix once the track is ready for it (see
        // scheduleBgMixHandover): starting BG Mix directly kept the track silent while its whole file was decoded.
        stopBgMix()
        val target = rawPlayer(context)
        transitionToken++
        syncPosition()
        clearDeferredSeek()
        pendingStartFraction = 0f
        startedCallback = startedCallback?.takeIf { it.first == selected.key }
        queueSource = source
        queuePageGeneration++
        resetErrorPolicy()
        dropClosedPlayer()
        queuePageJob?.cancel()
        queuePageJob = null
        _queuePageLoading.value = false
        prefetchedKeys.clear()

        PlaybackSources.clear()
        queue.items.forEach { registerSource(it, source) }
        val item = resolvedItem(selected)
        val resolvedQueue = queue.items.map { if (it.key == item.key) item else it }
        val requestedStart = (startPositionMs ?: 0L).coerceAtLeast(0L)
        val defer = shouldDeferGrowingSeek(item.growing, requestedStart)
        val mediaItems = resolvedQueue.map(::mediaItemFor)
        target.setMediaItems(mediaItems, queue.index, if (defer) 0L else requestedStart)
        installShuffleOrder(target, resolvedQueue.size, currentIndex = queue.index)
        applyPlayerModes(target, music = true)
        applySpeed(target, item)
        if (autoPlay) target.prepare()
        updateAudioSessionId()

        val speed = effectiveSpeed(item)
        val playing = false
        _state.value = stateFor(
            item = item,
            queue = resolvedQueue,
            index = queue.index,
            positionMs = requestedStart,
            playing = playing,
            intentPlaying = autoPlay,
            speed = speed
        )
        updateAudioSessionId()
        if (offerBgMix) scheduleBgMixHandover()
        if (!autoPlay) return // Restore remains silent, idle and does not trigger any TDLib download.
        refreshPrefetchPolicyObserver()
        if (defer) {
            target.pause()
            deferredSeek = DeferredGrowingSeek(item.key, requestedStart, resume = true)
            _state.update { it.copy(playing = false, intentPlaying = true, isBuffering = true).withTimelineNavigation(target) }
            startDeferredSeekWatcher()
            return
        }
        pendingStartFraction = if (startPositionMs == null) startFraction.coerceIn(0f, 1f) else 0f
        target.play()
        if (!item.systemPrivate) startPlaybackService()
        startProgress()
        prefetchNext()
        requestSilenceMeasurement()
        loadNextQueuePage()
    }

    @OptIn(UnstableApi::class)
    private fun startSingle(context: Context, item: PlaybackItem, startPositionMs: Long?, startFraction: Float) {
        stopBgMix()
        val target = rawPlayer(context)
        transitionToken++
        queuePageGeneration++
        resetErrorPolicy()
        dropClosedPlayer()
        queuePageJob?.cancel()
        queuePageJob = null
        _queuePageLoading.value = false
        syncPosition()
        clearDeferredSeek()
        pendingStartFraction = 0f
        startedCallback = startedCallback?.takeIf { it.first == item.key }
        prefetchedKeys.clear()

        PlaybackSources.clear()
        registerSource(item, queueSource)
        val resolved = resolvedItem(item)
        val requestedStart = (startPositionMs ?: 0L).coerceAtLeast(0L)
        val defer = shouldDeferGrowingSeek(resolved.growing, requestedStart)
        target.setMediaItems(listOf(mediaItemFor(resolved)), 0, if (defer) 0L else requestedStart)
        applyPlayerModes(target, music = resolved.isMusic && !resolved.systemPrivate, speech = resolved.kind != MediaKind.Audio)
        applySpeed(target, resolved)
        target.prepare()
        updateAudioSessionId()

        _state.value = stateFor(
            item = resolved,
            queue = emptyList(),
            index = -1,
            positionMs = requestedStart,
            playing = false,
            intentPlaying = true,
            speed = effectiveSpeed(resolved)
        )
        updateAudioSessionId()
        refreshPrefetchPolicyObserver()
        if (resolved.systemPrivate) stopPlaybackService()
        if (defer) {
            target.pause()
            deferredSeek = DeferredGrowingSeek(resolved.key, requestedStart, resume = true)
            _state.update { it.copy(playing = false, intentPlaying = true, isBuffering = true).withTimelineNavigation(target) }
            startDeferredSeekWatcher()
            return
        }
        pendingStartFraction = if (startPositionMs == null) startFraction.coerceIn(0f, 1f) else 0f
        target.play()
        if (resolved.systemPrivate) stopPlaybackService() else startPlaybackService()
        startProgress()
    }

    private fun stateFor(
        item: PlaybackItem,
        queue: List<PlaybackItem>,
        index: Int,
        positionMs: Long,
        playing: Boolean,
        intentPlaying: Boolean,
        speed: Float
    ): PlaybackState {
        val current = _state.value
        val target = exo
        return PlaybackState(
            item = item,
            playing = playing,
            intentPlaying = intentPlaying,
            positionMs = positionMs,
            durationMs = durationNow(),
            speed = speed,
            failed = false,
            started = false,
            ended = false,
            queue = queue,
            queueIndex = index,
            shuffleEnabled = current.shuffleEnabled,
            repeatMode = current.repeatMode,
            isBuffering = target?.playbackState == Player.STATE_BUFFERING,
            bufferedFraction = target?.bufferedPercentage?.coerceIn(0, 100)?.toFloat()?.div(100f) ?: 0f,
            musicSpeed = current.musicSpeed,
            voiceSpeed = current.voiceSpeed
        ).withTimelineNavigation(target)
    }

    /** The Media3 timeline is authoritative under shuffle/repeat; UI availability must match next/previous handlers. */
    private fun PlaybackState.withTimelineNavigation(target: Player? = exo): PlaybackState {
        // BG Mix publishes its own navigation from its queue; the ExoPlayer timeline is empty then.
        if (bgMixActive()) return this
        return copy(
            timelineHasNext = target?.hasNextMediaItem(),
            timelineHasPrevious = target?.hasPreviousMediaItem()
        )
    }

    /**
     * A new playlist must get a fresh randomized order, not the sequential order Media3 derives from a previous
     * one-off Play Next order. An explicit order is only used by Play Next itself.
     */
    @OptIn(UnstableApi::class)
    private fun installShuffleOrder(target: ExoPlayer, itemCount: Int, explicit: List<Int>? = null, currentIndex: Int = C.INDEX_UNSET) {
        if (itemCount <= 0) return
        val order = when {
            explicit != null && explicit.size == itemCount -> PlaybackShuffleOrder(explicit)
            // The playing track goes first, so the whole rest of the queue is still ahead of it.
            _state.value.shuffleEnabled && itemCount > 1 -> PlaybackShuffleOrder.random(itemCount, currentIndex = currentIndex)
            else -> return
        }
        target.setShuffleOrder(order)
    }

    private fun registerSource(item: PlaybackItem, source: PlaybackQueueSource?) {
        PlaybackSources.register(
            item.systemMediaId,
            PlaybackSourceHandle(item, source) { resolved ->
                // Resolved on the Media3 loader thread; publish the state snapshot on the main dispatcher.
                scope.launch { publishResolvedSource(resolved) }
            }
        )
    }

    private fun publishResolvedSource(resolved: PlaybackItem) {
        _state.update { it.withResolvedSource(resolved) }
        PlaybackSources.update(resolved.systemMediaId, resolved)
    }

    /**
     * A growing track whose download has finished becomes a complete file. Until this runs, the track keeps its
     * "still loading" flag, so BG Beat says so and the silence and crossfade rules never measure it.
     */
    private fun settleFinishedDownload() {
        val item = _state.value.item ?: return
        val settled = settledGrowingItem(item) ?: return
        publishResolvedSource(settled)
        // A track that finished downloading while it plays has no measurement yet; ask for it right away.
        requestSilenceMeasurement()
    }

    /** Key of the track whose neighbours were measured again shortly before its end. */
    private var lateMeasureKey: String? = null

    /** Thirty seconds before the end, a next track that downloaded in the meantime is measured before the transition. */
    private fun measureBeforeTransition(target: ExoPlayer) {
        if (!TrackTransitionSettings.skipSilence.value && !TrackTransitionSettings.crossfade.value) return
        val duration = target.duration
        if (duration == C.TIME_UNSET || duration <= 0L || duration - target.currentPosition > LATE_MEASURE_MS) return
        val key = _state.value.item?.key ?: return
        if (lateMeasureKey == key) return
        lateMeasureKey = key
        requestSilenceMeasurement()
    }

    private fun resolvedItem(item: PlaybackItem): PlaybackItem {
        val handle = PlaybackSources.handle(item.systemMediaId) ?: return item
        val resolved = handle.resolve() ?: return handle.item
        val updated = handle.item.copy(path = resolved.path, growing = resolved.growing)
        handle.item = updated
        return updated
    }

    @OptIn(UnstableApi::class)
    private fun mediaItemFor(item: PlaybackItem): MediaItem {
        val metadata = item.systemMetadata()
        val mediaId = item.systemMediaId
        val needsSource = item.fileId != null || item.path.isBlank() || item.growing != null
        val uri = if (needsSource) PlaybackSources.uri(mediaId) else Uri.fromFile(File(item.path))
        val mime = item.mimeType.ifBlank {
            when (item.kind) {
                MediaKind.Audio -> "audio/mpeg"
                MediaKind.Voice -> "audio/ogg"
                MediaKind.VideoNote -> "video/mp4"
                else -> ""
            }
        }
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .apply { if (mime.isNotBlank()) setMimeType(mime) }
            .setMediaMetadata(
                MediaMetadata.Builder().apply {
                    if (metadata.title.isNotBlank()) setTitle(metadata.title)
                    if (metadata.artist.isNotBlank()) setArtist(metadata.artist)
                    if (metadata.album.isNotBlank()) setAlbumTitle(metadata.album)
                    val art = metadata.artworkPath?.let { artworkUri(it) }
                    if (art != null) setArtworkUri(art)
                    if (metadata.durationMs > 0L) setDurationMs(metadata.durationMs)
                }.build()
            )
            .build()
    }

    private fun artworkUri(path: String): Uri? {
        val context = appContext ?: return null
        val file = File(path)
        if (!file.isFile) return null
        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }.getOrElse { Uri.fromFile(file) }
    }

    private fun PlaybackRepeatMode.toPlayerRepeatMode(): Int = when (this) {
        PlaybackRepeatMode.Off -> Player.REPEAT_MODE_OFF
        PlaybackRepeatMode.One -> Player.REPEAT_MODE_ONE
        PlaybackRepeatMode.All -> Player.REPEAT_MODE_ALL
    }

    private fun applyPlayerModes(target: ExoPlayer, music: Boolean, speech: Boolean = !music) {
        val state = _state.value
        target.repeatMode = if (music) state.repeatMode.toPlayerRepeatMode() else Player.REPEAT_MODE_OFF
        target.shuffleModeEnabled = music && state.shuffleEnabled
        applyAudioKind(target, speech = speech)
    }

    /** True while the player is set up for speech (voice messages, video notes), false for music, null before the first call. */
    private var speechAttributes: Boolean? = null

    /**
     * Voice messages and video notes are speech: a short sound of another app (a navigator prompt, a notification) pauses
     * them instead of ducking, so no words are lost. Music keeps the music type. The attributes change only when the kind
     * of file changes, because changing them reconfigures the audio output.
     */
    private fun applyAudioKind(target: ExoPlayer, speech: Boolean) {
        if (speechAttributes == speech) return
        speechAttributes = speech
        target.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(if (speech) C.AUDIO_CONTENT_TYPE_SPEECH else C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true
        )
    }

    private fun effectiveSpeed(item: PlaybackItem): Float =
        playbackSpeedFor(item.kind, _state.value.musicSpeed, _state.value.voiceSpeed)

    private fun applySpeed(target: ExoPlayer, item: PlaybackItem) {
        target.setPlaybackSpeed(effectiveSpeed(item))
    }

    /** Перемотка по абсолютной позиции; для растущего Ogg запрос откладывается до полной загрузки. */
    fun seekToPosition(positionMs: Long) {
        if (bgMixActive()) {
            // A seek during a BG Mix transition is refused by the native session; the next status tick keeps the UI honest.
            bgMix?.seek(positionMs.coerceAtLeast(0L) / 1000.0)
            return
        }
        val target = exo ?: return
        val position = positionMs.coerceAtLeast(0L)
        val item = _state.value.item ?: return
        val growing = item.growing
        if (shouldDeferGrowingSeek(growing, position)) {
            val resume = _state.value.intentPlaying
            target.pause()
            deferredSeek = DeferredGrowingSeek(item.key, position, resume = resume)
            _state.update {
                it.copy(
                    positionMs = position,
                    playing = false,
                    intentPlaying = resume,
                    isBuffering = resume
                ).withTimelineNavigation(target)
            }
            startDeferredSeekWatcher()
            return
        }
        if (growing != null && !growing.advertiseLength && growing.isComplete() && growing.completedPath() != null) {
            rebuildCurrentAt(item, position, _state.value.intentPlaying)
            return
        }
        clearDeferredSeek()
        target.seekTo(position)
        _state.update { it.copy(positionMs = position) }
    }

    /** Перемотка по доле длины. [fallbackDurationMs] используется, если контейнер ещё не сообщил длину. */
    fun seekTo(fraction: Float, fallbackDurationMs: Long = 0L) {
        if (bgMixActive()) {
            val duration = _state.value.durationMs.takeIf { it > 0L } ?: fallbackDurationMs
            if (duration > 0L) seekToPosition((fraction.coerceIn(0f, 1f) * duration.toFloat()).toLong())
            return
        }
        val target = exo ?: return
        val duration = target.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: fallbackDurationMs
        if (duration <= 0L) return
        val position = (fraction.coerceIn(0f, 1f) * duration.toFloat()).toLong()
        seekToPosition(position)
    }

    /** Rebuilds the current source on the completed file, preserving queue/index and the requested position. */
    private fun rebuildCurrentAt(item: PlaybackItem, positionMs: Long, resume: Boolean) {
        val path = item.growing?.completedPath() ?: return
        val completed = item.copy(path = path, growing = null)
        val state = _state.value
        if (state.queue.isNotEmpty()) {
            val queue = state.queue.map { if (it.key == completed.key) completed else it }
            val index = queue.indexOfFirst { it.key == completed.key }
            if (index >= 0) {
                rebuildTimeline(queue, index, positionMs, resume)
                return
            }
        }
        val target = rawPlayer(appContext ?: return)
        PlaybackSources.clear()
        registerSource(completed, queueSource)
        target.setMediaItems(listOf(mediaItemFor(completed)), 0, positionMs)
        applyPlayerModes(target, music = completed.isMusic)
        applySpeed(target, completed)
        target.prepare()
        if (resume) {
            target.play()
            if (completed.systemPrivate) stopPlaybackService() else startPlaybackService()
            startProgress()
        } else {
            target.pause()
            progress?.cancel()
            progress = null
        }
        _state.update {
            it.copy(
                item = completed,
                positionMs = positionMs,
                playing = false,
                intentPlaying = resume,
                ended = false,
                failed = false,
                durationMs = durationNow()
            ).withTimelineNavigation(target)
        }
        updateAudioSessionId()
        refreshPrefetchPolicyObserver()
    }

    @OptIn(UnstableApi::class)
    private fun rebuildTimeline(
        queue: List<PlaybackItem>,
        index: Int,
        positionMs: Long,
        play: Boolean,
        playbackOrder: List<Int>? = null
    ) {
        if (queue.isEmpty() || index !in queue.indices) return
        if (bgMixActive()) stopBgMix()
        val target = exo ?: return
        transitionToken++
        clearDeferredSeek()
        pendingStartFraction = 0f
        prefetchedKeys.clear()
        PlaybackSources.clear()
        queue.forEach { registerSource(it, queueSource) }
        val item = resolvedItem(queue[index])
        val resolvedQueue = queue.map { if (it.key == item.key) item else it }
        val requestedStart = positionMs.coerceAtLeast(0L)
        val defer = shouldDeferGrowingSeek(item.growing, requestedStart)
        target.setMediaItems(resolvedQueue.map(::mediaItemFor), index, if (defer) 0L else requestedStart)
        installShuffleOrder(target, resolvedQueue.size, playbackOrder, index)
        applyPlayerModes(target, music = item.isMusic && !item.systemPrivate, speech = item.kind != MediaKind.Audio)
        applySpeed(target, item)
        target.prepare()
        if (defer) {
            target.pause()
            deferredSeek = DeferredGrowingSeek(item.key, requestedStart, resume = play)
            startDeferredSeekWatcher()
        } else if (play) {
            target.play()
            if (item.systemPrivate) stopPlaybackService() else startPlaybackService()
            startProgress()
        } else {
            target.pause()
            progress?.cancel()
            progress = null
        }
        _state.update {
            it.copy(
                item = item,
                queue = resolvedQueue,
                queueIndex = index,
                positionMs = requestedStart,
                playing = false,
                intentPlaying = play,
                speed = effectiveSpeed(item),
                started = exo?.isPlaying == true,
                ended = false,
                failed = false,
                waitingForNetwork = false,
                bgMixActive = false,
                isBuffering = if (defer) play else target.playbackState == Player.STATE_BUFFERING,
                bufferedFraction = target.bufferedPercentage.coerceIn(0, 100).toFloat() / 100f,
                durationMs = durationNow()
            ).withTimelineNavigation(target)
        }
        updateAudioSessionId()
        refreshPrefetchPolicyObserver()
        if (play && !defer) prefetchNext()
    }

    private fun startDeferredSeekWatcher() {
        deferredSeekJob?.cancel()
        deferredSeekJob = scope.launch {
            while (true) {
                val pending = deferredSeek ?: break
                val item = _state.value.item
                if (item?.key != pending.key) {
                    deferredSeek = null
                    break
                }
                val growing = item.growing
                if (growing?.isComplete() == true && growing.completedPath() != null) {
                    deferredSeek = null
                    rebuildCurrentAt(item, pending.positionMs, pending.resume)
                    break
                }
                delay(200)
            }
        }
    }

    private fun clearDeferredSeek() {
        deferredSeek = null
        deferredSeekJob?.cancel()
        deferredSeekJob = null
    }

    /** Отметка для файла, который продолжают после паузы: вызывается, когда он начнёт играть, даже если пузырь уже ушёл с экрана. */
    fun resume(key: String, onStarted: () -> Unit) {
        if (_state.value.item?.key != key) return
        startedCallback = key to onStarted
        if (deferredSeek?.key == key) {
            deferredSeek = deferredSeek?.copy(resume = true)
            _state.update { it.copy(intentPlaying = true) }
            return
        }
        val target = controls ?: return
        // This is an explicit resume request, not a toggle. A voice note that is still buffering already has
        // intentPlaying=true; calling togglePlayPause() here would pause it instead of letting it continue.
        _state.update { it.copy(failed = false, intentPlaying = true) }
        if (_state.value.item?.systemPrivate != true) startPlaybackService()
        target.play()
        prefetchNext()
    }

    /** Пауза или продолжение. Конец файла и ошибку обрабатывает обёртка плеера, поэтому системные кнопки ведут себя так же. */
    fun togglePlayPause() {
        if (bgMixActive()) {
            toggleBgMix()
            return
        }
        val target = controls ?: return
        // A deferred Ogg seek is waiting for the completed file; starting at zero here would undo it.
        if (deferredSeek != null) {
            deferredSeek = deferredSeek?.copy(resume = true)
            _state.update { it.copy(intentPlaying = true) }
            return
        }
        // At ENDED ExoPlayer keeps playWhenReady=true; the user's Play must restart, not "pause" an already silent item.
        if (_state.value.ended) {
            _state.update { it.copy(failed = false, intentPlaying = true) }
            if (_state.value.item?.systemPrivate != true) startPlaybackService()
            target.play()
            prefetchNext()
            return
        }
        // Решает то же состояние, что видит интерфейс: файл, который уже буферизуется, пауза отменяет, а не запускает снова.
        if (_state.value.intentPlaying || _state.value.playing) {
            pause()
            return
        }
        _state.update { it.copy(failed = false, ended = false, intentPlaying = true) }
        if (_state.value.item?.systemPrivate != true) startPlaybackService()
        target.play()
        prefetchNext()
        loadNextQueuePage()
    }

    /** Пауза без сброса файла: так голосовое или кружок могут остановить музыку, не теряя её места. Состояние меняем сразу: если файл ещё буферизуется, плеер не пришлёт событие о паузе. */
    fun pause() {
        if (bgMixActive()) {
            pauseBgMix()
            return
        }
        exo?.pause()
        deferredSeek = deferredSeek?.copy(resume = false)
        progress?.cancel()
        progress = null
        _state.update { it.copy(playing = false, intentPlaying = false) }
    }

    private class Interruption(
        val owners: MutableSet<Any>,
        val wasPlaying: Boolean,
        val startedAt: Long,
        val itemKey: String?
    )

    private var interruption: Interruption? = null

    /**
     * A video, a story with sound or a recording needs the speakers or the microphone for a while. Music that was
     * playing is paused and remembered; [endInterruption] by the last owner continues it, unless the listener took
     * over in the meantime (another track, play pressed) or the break was longer than [INTERRUPTION_RESUME_LIMIT_MS].
     */
    fun beginInterruption(owner: Any) {
        val now = SystemClock.elapsedRealtime()
        val existing = interruption
        if (existing != null && now - existing.startedAt < INTERRUPTION_STALE_MS) {
            existing.owners += owner
            return
        }
        val state = _state.value
        val item = state.item
        val music = item != null && item.isMusic && !item.systemPrivate
        val wasPlaying = music && (state.playing || state.intentPlaying) && !state.waitingForNetwork
        interruption = Interruption(mutableSetOf(owner), wasPlaying, now, item?.key)
        if (wasPlaying) pause()
    }

    fun endInterruption(owner: Any) {
        val current = interruption ?: return
        if (!current.owners.remove(owner) || current.owners.isNotEmpty()) return
        interruption = null
        if (!current.wasPlaying) return
        val state = _state.value
        val longBreak = SystemClock.elapsedRealtime() - current.startedAt > INTERRUPTION_RESUME_LIMIT_MS
        if (longBreak || state.item?.key != current.itemKey || state.intentPlaying || state.playing) return
        val target = exo ?: return
        // The music returns with a short fade-in, so it does not hit the ears at full volume.
        val original = target.volume
        target.volume = 0f
        controls?.play()
        scope.launch {
            try {
                val steps = INTERRUPTION_FADE_IN_MS / SLEEP_FADE_STEP_MS
                for (step in 1..steps) {
                    target.volume = original * step.toFloat() / steps
                    delay(SLEEP_FADE_STEP_MS)
                }
            } finally {
                target.volume = original
            }
        }
    }

    private var recordingFocus: AudioFocusRequest? = null

    /**
     * While a voice message or a video note is recorded, other apps are asked to stop their sound for a moment, so music
     * from elsewhere does not end up in the recording. [endRecordingFocus] gives the focus back.
     */
    fun beginRecordingFocus(context: Context) {
        if (recordingFocus != null) return
        val audio = context.applicationContext.getSystemService(AudioManager::class.java) ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
        if (audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) recordingFocus = request
    }

    fun endRecordingFocus(context: Context) {
        val request = recordingFocus ?: return
        recordingFocus = null
        context.applicationContext.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(request)
    }

    /** Account the shared player is bound to. Profile music uses it to hide "add to profile" for another account's tracks. */
    fun activeAccount(): String? = activeAccountId

    /** Explicit user stop: closes the session, clears the queue and removes the notification. */
    fun stop() {
        _state.value.item?.accountId?.let { owner -> persistence?.forget(owner) }
        closedPlayer = null
        undoJob?.cancel()
        _undoClose.value = false
        releaseQueueAndHalt()
    }

    private fun releaseQueueAndHalt() {
        cancelSleepTimer()
        _queuePageLoading.value = false
        interruptedMusic = null
        queueSource = null
        queuePageGeneration++
        resetErrorPolicy()
        dropClosedPlayer()
        queuePageJob?.cancel()
        queuePageJob = null
        prefetchedKeys.clear()
        haltPlayer()
    }

    /** Any new playback or an explicit stop ends the chance to undo the last close. */
    private fun dropClosedPlayer() {
        closedPlayer = null
        undoJob?.cancel()
        _undoClose.value = false
    }

    private class ClosedPlayer(val state: PlaybackState, val source: PlaybackQueueSource?)

    private var closedPlayer: ClosedPlayer? = null
    private var undoJob: Job? = null
    private val _undoClose = MutableStateFlow(false)
    /** True for a few seconds after the mini-player was closed, while "Вернуть" can still bring the queue back. */
    val undoClose: StateFlow<Boolean> = _undoClose.asStateFlow()

    /**
     * Hides the player, pauses the music and removes the notification, but keeps the queue and the place: the saved
     * session stays on disk, and for [UNDO_CLOSE_MS] the exact queue can be brought back with [undoClose]. Anything
     * that is not music is simply stopped.
     */
    fun close() {
        val snapshot = _state.value
        val item = snapshot.item
        if (item == null || !item.isMusic || item.systemPrivate) {
            stop()
            return
        }
        syncPosition()
        val frozen = _state.value
        persistence?.flush()
        val closedSource = queueSource
        // Shutting down drops any earlier snapshot, so the new one is stored only after it.
        releaseQueueAndHalt()
        closedPlayer = ClosedPlayer(frozen, closedSource)
        _undoClose.value = true
        undoJob?.cancel()
        // Closing for good drops the saved session too, so the closed track does not come back at the next start.
        val owner = activeAccountId ?: item.accountId
        undoJob = scope.launch {
            delay(UNDO_CLOSE_MS)
            _undoClose.value = false
            closedPlayer = null
            persistence?.forget(owner)
        }
    }

    /** Brings back the queue that [close] hid, paused at the same place. */
    fun undoClose(context: Context) {
        val closed = closedPlayer ?: return
        closedPlayer = null
        undoJob?.cancel()
        _undoClose.value = false
        val frozen = closed.state
        val queue = frozen.queue.ifEmpty { listOfNotNull(frozen.item) }
        val index = frozen.queueIndex.takeIf { it in queue.indices } ?: return
        _state.update { it.copy(shuffleEnabled = frozen.shuffleEnabled, repeatMode = frozen.repeatMode) }
        queueSource = closed.source
        startQueue(
            context,
            PlaybackQueue(queue, index),
            closed.source,
            startPositionMs = frozen.positionMs,
            startFraction = 0f,
            autoPlay = false
        )
    }

    /** Alias used by UI that wants an unambiguous "clear everything" action. */
    fun clear() = stop()

    private fun bgMixActive(): Boolean = bgMix?.isActive == true

    /** Placeholder for the ExoPlayer timeline while BG Mix plays: the current file, never played by ExoPlayer. */
    private fun placeholderItem(item: PlaybackItem): MediaItem = MediaItem.Builder()
        .setMediaId(item.key)
        .setUri(Uri.fromFile(File(item.path)))
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(item.title).setArtist(item.performer).apply {
                item.systemMetadata().artworkPath?.let { artworkUri(it) }?.let { setArtworkUri(it) }
            }.build()
        )
        .build()

    /** Tells the media session which of its player values changed. ExoPlayer events are ignored during BG Mix. */
    @OptIn(UnstableApi::class)
    private fun notifyChanges(before: PlaybackState, after: PlaybackState) {
        if (bgMixListeners.isEmpty()) return
        val itemChanged = before.item?.key != after.item?.key
        val playbackState = if (after.ended) Player.STATE_ENDED else Player.STATE_READY
        val wasEnded = before.ended
        bgMixListeners.toList().forEach { listener ->
            if (before.playing != after.playing) listener.onIsPlayingChanged(after.playing)
            if (before.intentPlaying != after.intentPlaying) {
                listener.onPlayWhenReadyChanged(after.intentPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            }
            if (wasEnded != after.ended) listener.onPlaybackStateChanged(playbackState)
            if (itemChanged) {
                listener.onMediaItemTransition(
                    after.item?.let { placeholderItem(it) },
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                )
            }
        }
    }

    /** Pauses BG Mix when another app takes audio focus or the output becomes noisy (headphones unplugged). */
    /** True while BG Mix is paused only because another app took focus for a moment, so it may resume on its own. */
    private var bgResumeOnFocusGain = false

    /** True while a short sound asked BG Mix to duck; the level is restored with the focus. */
    private var bgDucked = false

    private val bgFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (!bgMixActive()) return@OnAudioFocusChangeListener
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                bgResumeOnFocusGain = false
                pauseBgMix()
            }
            // A navigator prompt asks to duck: the output is lowered and restored when the focus returns.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                bgMix?.setVolume(BG_DUCK_VOLUME)
                bgDucked = true
            }
            // A call or another short sound pauses the music and it returns when the focus does.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                val playing = _state.value.intentPlaying && !_state.value.ended
                if (playing) {
                    pauseBgMix()
                    bgResumeOnFocusGain = true
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (bgDucked) {
                    bgDucked = false
                    bgMix?.setVolume(1f)
                }
                if (bgResumeOnFocusGain) {
                    bgResumeOnFocusGain = false
                    bgMixPlay()
                }
            }
        }
    }

    private var bgWakeLock: PowerManager.WakeLock? = null

    /** BG Mix has its own output thread, so it holds the CPU awake while it plays; the regular player does it by itself. */
    private fun holdBgWakeLock() {
        val context = appContext ?: return
        val lock = bgWakeLock ?: context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "beygram:bgmix")
            ?.apply { setReferenceCounted(false) }
            ?.also { bgWakeLock = it }
            ?: return
        // A timeout is a safety net only; pausing or stopping releases the lock at once.
        runCatching { if (!lock.isHeld) lock.acquire(BG_WAKE_LOCK_TIMEOUT_MS) }
    }

    private fun releaseBgWakeLock() {
        val lock = bgWakeLock ?: return
        runCatching { if (lock.isHeld) lock.release() }
    }

    private val bgNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (bgMixActive()) pauseBgMix()
        }
    }

    /** Requests audio focus and the becoming-noisy receiver. Returns true when focus was granted. */
    private fun requestBgFocus(context: Context): Boolean {
        var granted = false
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio != null) {
            // One request serves the whole session; asking again with the same object refreshes the focus
            // instead of leaving the previous request behind.
            val request = bgFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(bgFocusListener)
                .build()
            granted = audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (granted) bgFocusRequest = request
        }
        if (!bgNoisyRegistered) {
            ContextCompat.registerReceiver(
                context.applicationContext,
                bgNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            bgNoisyRegistered = true
        }
        return granted
    }

    private fun releaseBgFocus() {
        val context = appContext
        val request = bgFocusRequest
        bgFocusRequest = null
        if (context != null && request != null) {
            context.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(request)
        }
        if (context != null && bgNoisyRegistered) {
            runCatching { context.unregisterReceiver(bgNoisyReceiver) }
            bgNoisyRegistered = false
        }
    }

    /**
     * Starts [requested] through BG Mix when the experimental flag is on and the tapped item is a finished local file.
     * The finished files after it start with it; the rest join the session as they finish downloading. Returns false
     * without touching the ExoPlayer state, so the caller falls back to the regular path.
     */
    private fun startBgMix(context: Context, requested: PlaybackQueue, reshuffle: Boolean = false, autoPlay: Boolean = true): Boolean {
        BgMixSettingsStore.load(context)
        if (!BgMixSettingsStore.enabled.value) return false
        if (requested.index !in requested.items.indices) return false
        val single = _state.value.repeatMode == PlaybackRepeatMode.One
        // A position restoration waiting on the old session must not seek the new one.
        bgMixSeekJob?.cancel()
        bgMixSeekJob = null
        // Creates the shared player and appContext on first use, so the notification and queue paths work.
        player(context)
        val driver = bgMixDriver(context)
        // Focus comes before the driver starts: the native session plays as soon as it starts, so it must not start
        // without focus. A refused focus keeps the queue on ExoPlayer, which asks for focus in the same way.
        if (!requestBgFocus(context)) return false
        // Shuffle keeps the current track first and mixes the rest; the order before it is kept for switching shuffle off.
        val playQueue = if (reshuffle && _state.value.shuffleEnabled) {
            bgMixOriginalQueue = requested.items
            val current = requested.items[requested.index]
            PlaybackQueue(listOf(current) + requested.items.filterIndexed { i, _ -> i != requested.index }.shuffled(), 0)
        } else {
            if (reshuffle) bgMixOriginalQueue = null
            requested
        }
        if (!driver.start(playQueue.items, playQueue.index, single, ::bgMixFileFor)) {
            releaseBgFocus()
            return false
        }
        adoptBgMixSession(driver, playQueue.index, positionMs = 0L, autoPlay = autoPlay)
        return true
    }

    private fun bgMixDriver(context: Context): BgMixQueueDriver =
        bgMix ?: BgMixQueueDriver(File(context.filesDir, "bgmix-cache")).also { bgMix = it }

    /**
     * The item as a complete local file BG Mix can decode, or null while it is not one. The queue snapshot may still
     * have no path for a track that finished downloading since, so the live source is asked first.
     */
    private fun bgMixFileFor(item: PlaybackItem): PlaybackItem? {
        if (BgMixQueueDriver.isEligible(item)) return item
        // A track streamed while it downloaded keeps its growing source until the engine settles it.
        settledGrowingItem(item)?.takeIf { BgMixQueueDriver.isEligible(it) }?.let { return it }
        val resolved = PlaybackSources.handle(item.systemMediaId)?.resolve() ?: return null
        val growing = resolved.growing
        val path = when {
            growing == null -> resolved.path
            growing.isComplete() -> growing.completedPath() ?: return null
            else -> return null
        }
        return item.copy(path = path, growing = null).takeIf { BgMixQueueDriver.isEligible(it) }
    }

    /**
     * Makes the driver's active session the player of the queue: ExoPlayer sits paused on a placeholder, the state and
     * the media session follow the native status from now on.
     */
    private fun adoptBgMixSession(driver: BgMixQueueDriver, index: Int, positionMs: Long, autoPlay: Boolean) {
        val queue = driver.queueItems
        val item = queue[index]
        val before = _state.value
        progress?.cancel()
        progress = null
        exo?.let {
            crossfader.cancel(it)
            it.stop()
            // The handover took ExoPlayer's level down; it comes back for its next playback.
            it.volume = 1f
            it.clearMediaItems()
            // A paused placeholder keeps the session's current item (title, duration) and notification alive.
            it.playWhenReady = false
            // Focus handling returns only after the pause: while ExoPlayer still asks to play, it takes the focus from BG Mix.
            setExoFocusHandling(it, handle = true)
            it.setMediaItem(placeholderItem(item))
            it.prepare()
        }
        transitionToken++
        _bgMixTransition.value = null
        clearDeferredSeek()
        pendingStartFraction = 0f
        startedCallback = startedCallback?.takeIf { it.first == item.key }
        // The equalizer attaches to the BG Mix output track the same way it does to ExoPlayer.
        _audioSessionId.value = driver.audioSessionId
        holdBgWakeLock()
        _state.value = stateFor(
            item = item,
            queue = queue,
            index = index,
            positionMs = positionMs,
            playing = false,
            intentPlaying = true,
            speed = 1f
        )
        _state.update {
            it.copy(
                timelineHasNext = bgMixHasNext(index, queue.size) || driver.pendingIndex != null,
                timelineHasPrevious = true,
                bgMixActive = true,
                // A track handed over from ExoPlayer has already started; its mark is not taken back.
                started = before.started && before.item?.key == item.key
            )
        }
        notifyChanges(before, _state.value)
        bgMixJob?.cancel()
        val statusFlow = driver.status
        val failureFlow = driver.failure
        if (statusFlow != null && failureFlow != null) {
            bgMixJob = scope.launch {
                // Output that fails during the session hands the queue back to ExoPlayer. Stopping the session cancels this.
                launch {
                    failureFlow.first { it != null }
                    failBgMix()
                }
                statusFlow.collect { status -> if (status != null) applyBgMix(status) }
            }
        }
        startBgMixFeed()
        startPlaybackService()
        if (!autoPlay) pauseBgMix()
    }

    /**
     * While the session runs, adds each queue item whose file has finished to the native queue, in order, and asks for
     * the files of the next [BG_MIX_DOWNLOAD_AHEAD] items, because BG Mix plays only complete files.
     */
    private fun startBgMixFeed() {
        bgMixFeedJob?.cancel()
        bgMixFeedJob = scope.launch {
            while (true) {
                val driver = bgMix?.takeIf { it.isActive } ?: return@launch
                if (driver.pendingIndex == null) return@launch
                driver.appendReady(::bgMixFileFor)
                val first = driver.pendingIndex ?: return@launch
                // Only the track right after the playing one is part of playback; the rows after it follow the policy.
                val playingNext = _state.value.queueIndex + 1
                val context = appContext
                val source = queueSource
                if (context != null) {
                    for (index in first until minOf(first + BG_MIX_DOWNLOAD_AHEAD, driver.queueItems.size)) {
                        // Speculative traffic goes through the automatic-download policy, like the regular prefetch.
                        prefetchItem(context, source, driver.queueItems[index], playbackNext = index == playingNext)
                    }
                }
                delay(BG_MIX_FEED_INTERVAL_MS)
            }
        }
    }

    /**
     * Offers the queue ExoPlayer has just started to BG Mix. The track keeps playing on ExoPlayer, so it starts at once
     * even while its file downloads. Once the playing track is a finished file, BG Mix prepares it silently and takes over
     * at the same place. The player shows the BG Mix mark from the start for a track BG Mix can take.
     */
    private fun scheduleBgMixHandover() {
        cancelBgMixHandover()
        val context = appContext ?: return
        BgMixSettingsStore.load(context)
        if (!BgMixSettingsStore.enabled.value || !bgMixAdmitsSource(queueSource)) return
        if (!BgMixNative.isAvailable) return
        bgMixHandoverJob = scope.launch {
            runBgMixHandover(context)
            if (!bgMixActive()) setBgMixPreparing(false)
        }
    }

    private fun cancelBgMixHandover() {
        bgMixHandoverGeneration++
        bgMixHandoverJob?.cancel()
        bgMixHandoverJob = null
        bgMix?.discardStaged()
        setBgMixPreparing(false)
    }

    private fun setBgMixPreparing(preparing: Boolean) {
        if (_state.value.bgMixPreparing != preparing) _state.update { it.copy(bgMixPreparing = preparing) }
    }

    private suspend fun runBgMixHandover(context: Context) {
        var failures = 0
        while (failures < BG_HANDOVER_ATTEMPTS) {
            if (bgMixActive()) return
            val state = _state.value
            val item = state.item ?: return
            if (!item.isMusic || item.systemPrivate || state.queueIndex !in state.queue.indices) return
            val candidate = BgMixQueueDriver.isCandidate(item) || bgMixFileFor(item) != null
            setBgMixPreparing(candidate)
            val file = if (candidate && bgMixHandoverMoment(state, item)) bgMixFileFor(item) else null
            if (file == null) {
                delay(if (state.intentPlaying) BG_HANDOVER_POLL_MS else BG_HANDOVER_IDLE_POLL_MS)
                continue
            }
            when (handOverToBgMix(context, state, file)) {
                true -> return
                // The queue changed while the session was being prepared: prepare again for the new one.
                null -> continue
                false -> {
                    failures++
                    delay(BG_HANDOVER_RETRY_MS)
                }
            }
        }
    }

    /** ExoPlayer plays this music track audibly at normal speed, with enough of it left to be worth moving. */
    private fun bgMixHandoverMoment(state: PlaybackState, item: PlaybackItem): Boolean {
        val target = exo ?: return false
        if (!state.playing || !state.intentPlaying || state.ended || state.waitingForNetwork) return false
        if (deferredSeek != null || effectiveSpeed(item) != 1f) return false
        val duration = target.duration
        return duration == C.TIME_UNSET || duration - target.currentPosition >= BG_HANDOVER_MIN_REMAINING_MS
    }

    /**
     * Prepares a BG Mix session for the queue from the playing track while ExoPlayer keeps playing, then starts it at
     * ExoPlayer's position and lets ExoPlayer go. False when the session could not be prepared or the track changed, null
     * when the queue was edited meanwhile and the session has to be prepared again.
     */
    private suspend fun handOverToBgMix(context: Context, state: PlaybackState, file: PlaybackItem): Boolean? {
        val generation = bgMixHandoverGeneration
        val driver = bgMixDriver(context)
        val single = state.repeatMode == PlaybackRepeatMode.One
        val items = state.queue.map { if (it.key == file.key) file else it }
        val shuffled = state.shuffleEnabled && !single
        // The native session has no shuffle order. It takes the rest of ExoPlayer's own shuffle order from the current
        // track on, so tracks that already played do not come back; Repeat All keeps the played ones after them.
        val order = if (shuffled) bgMixShuffleOrder(items, state.queueIndex, state.repeatMode) else items
        val index = if (shuffled) 0 else state.queueIndex
        val queueKeys = state.queue.map { it.key }
        fun queueChanged(): Boolean = _state.value.queue.map { it.key } != queueKeys || _state.value.shuffleEnabled != state.shuffleEnabled
        if (!driver.stage(order, index, single, ::bgMixFileFor)) return false
        val status = driver.stagedStatus
        val loaded = status?.let {
            withTimeoutOrNull(BG_HANDOVER_PREPARE_TIMEOUT_MS) {
                it.first { snapshot -> snapshot?.decks?.getOrNull(snapshot.master)?.let { deck -> deck.loaded && deck.durationSeconds > 0.0 } == true }
            }
        }
        if (queueChanged()) {
            driver.discardStaged()
            return null
        }
        if (loaded == null || !stillHandingOver(file)) {
            driver.discardStaged()
            return false
        }
        val target = exo ?: run { driver.discardStaged(); return false }
        // A starting guess: the alignment below measures where the native output is really heard.
        val positionMs = target.currentPosition + BG_HANDOVER_LEAD_MS
        if (!driver.seekStaged(positionMs / 1000.0)) {
            driver.discardStaged()
            return false
        }
        // BG Mix starts silent: its first samples must not land on top of ExoPlayer at full level.
        driver.setStagedVolume(0f)
        driver.playStaged()
        // ExoPlayer keeps playing until the native output runs, so the handover has no gap.
        withTimeoutOrNull(BG_HANDOVER_START_TIMEOUT_MS) {
            driver.stagedStatus?.first { snapshot -> snapshot?.decks?.getOrNull(snapshot.master)?.playing == true }
        }
        delay(BG_HANDOVER_OVERLAP_MS)
        // Still silent: the output is lined up with ExoPlayer by measurement before anything of it is heard.
        alignStagedSilently(target, driver)
        if (queueChanged()) {
            driver.discardStaged()
            return null
        }
        // ExoPlayer must not pause for the focus change that BG Mix causes: the crossfade takes its level down instead.
        setExoFocusHandling(target, handle = false)
        if (!stillHandingOver(file) || !requestBgFocus(context)) {
            setExoFocusHandling(target, handle = true)
            driver.discardStaged()
            return false
        }
        if (!driver.commitStaged()) {
            releaseBgFocus()
            setExoFocusHandling(target, handle = true)
            return false
        }
        // From here the handover is committed. The crossfade runs to its end unless a stop or a new track supersedes it.
        withContext(NonCancellable) {
            // The equalizer moves to the BG Mix output while that output is still silent, so the re-attach is not heard.
            _audioSessionId.value = driver.audioSessionId
            if (!crossfadeIntoBgMix(target, driver, generation)) {
                // A stop or a new track came meanwhile: BG Mix is not adopted, and ExoPlayer plays on at full level.
                if (driver.isActive) stopBgMix()
                target.volume = 1f
                setExoFocusHandling(target, handle = true)
                updateAudioSessionId()
                return@withContext
            }
            bgMixHandoverJob = null
            bgMixOriginalQueue = if (shuffled) items else null
            adoptBgMixSession(driver, index, positionMs = positionMs, autoPlay = true)
        }
        return true
    }

    /**
     * Hands the sound from ExoPlayer to the BG Mix output over [BG_HANDOVER_FADE_MS]. ExoPlayer falls and BG Mix rises along
     * a quarter-sine pair, so the level holds while both play the same track. Returns false as soon as the handover is
     * superseded; the levels are not written after that.
     */
    private suspend fun crossfadeIntoBgMix(target: ExoPlayer, driver: BgMixQueueDriver, generation: Int): Boolean {
        val from = target.volume
        val quarterTurn = (kotlin.math.PI / 2).toFloat()
        val step = BG_HANDOVER_FADE_MS / BG_HANDOVER_FADE_STEPS
        for (n in 1..BG_HANDOVER_FADE_STEPS) {
            if (!stillHandedOver(generation, driver)) return false
            val t = n.toFloat() / BG_HANDOVER_FADE_STEPS
            target.volume = from * kotlin.math.cos(t * quarterTurn).toFloat()
            driver.setVolume(kotlin.math.sin(t * quarterTurn).toFloat())
            delay(step)
        }
        if (!stillHandedOver(generation, driver)) return false
        target.volume = 0f
        driver.setVolume(1f)
        return true
    }

    /**
     * Lines the silent BG Mix output up with ExoPlayer before any of it is heard. It measures where BG Mix is heard on the
     * audio clock and seeks it while it is silent. The new audio starts once the audio already queued has played out, and
     * by then ExoPlayer has moved on by that same amount, so the target is ExoPlayer's position plus the queued audio. The
     * check repeats until the difference is within [BG_ALIGN_TOLERANCE_MS]. False when no measurement came, and the
     * starting guess stays.
     */
    private suspend fun alignStagedSilently(target: ExoPlayer, driver: BgMixQueueDriver): Boolean {
        val offsetsMs = mutableListOf<Long>()
        fun finish(aligned: Boolean): Boolean {
            recordAppEvent(
                kind = EventKind.PERFORMANCE,
                code = "bgmix_handover_alignment",
                text = if (aligned) "Переход в BG Mix: звук выровнен с плеером" else "Переход в BG Mix: выровнять звук не удалось",
                attrs = mapOf("offsets_ms" to offsetsMs.joinToString(",")),
            )
            return aligned
        }
        repeat(BG_ALIGN_ATTEMPTS) {
            val audible = awaitStagedAudible(driver) ?: return finish(false)
            val exoMs = target.currentPosition.toDouble()
            val offsetMs = bgMixAlignmentOffsetMs(audible, exoMs)
            offsetsMs += offsetMs.toLong()
            if (kotlin.math.abs(offsetMs) <= BG_ALIGN_TOLERANCE_MS) return finish(true)
            if (!driver.seekStaged(bgMixAlignmentSeekMs(audible, exoMs) / 1_000.0)) return finish(false)
        }
        return finish(true)
    }

    /** The staged output's audible position, waited for briefly: the track presents its first frames a moment after a seek. */
    private suspend fun awaitStagedAudible(driver: BgMixQueueDriver): BgMixAudible? =
        withTimeoutOrNull(BG_ALIGN_WAIT_MS) {
            var audible = driver.stagedAudible()
            while (audible == null) {
                delay(BG_ALIGN_POLL_MS)
                audible = driver.stagedAudible()
            }
            audible
        }

    /** Whether the handover that started as [generation] still owns [driver]: no stop and no new track has taken it. */
    private fun stillHandedOver(generation: Int, driver: BgMixQueueDriver): Boolean =
        generation == bgMixHandoverGeneration && driver.isActive

    /** Switches ExoPlayer's own audio-focus handling; the audio kind it plays with is kept. */
    private fun setExoFocusHandling(target: ExoPlayer, handle: Boolean) {
        target.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(if (speechAttributes == true) C.AUDIO_CONTENT_TYPE_SPEECH else C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            handle
        )
    }

    /** The queue in ExoPlayer's shuffle order from [currentIndex] on; with Repeat All, the tracks before it follow. */
    private fun bgMixShuffleOrder(items: List<PlaybackItem>, currentIndex: Int, repeat: PlaybackRepeatMode): List<PlaybackItem> {
        val order = exo?.let { target -> runCatching { target.shuffleOrder.toIndexOrder() }.getOrNull() }
            ?.takeIf { it.size == items.size && currentIndex in it }
            ?: return listOf(items[currentIndex]) + items.filterIndexed { i, _ -> i != currentIndex }.shuffled()
        val at = order.indexOf(currentIndex)
        val upcoming = order.drop(at)
        val played = if (repeat == PlaybackRepeatMode.All) order.take(at) else emptyList()
        return (upcoming + played).map { items[it] }
    }

    /** The handover still applies: the same track plays on ExoPlayer and nothing took the queue meanwhile. */
    private fun stillHandingOver(file: PlaybackItem): Boolean {
        val now = _state.value
        return !bgMixActive() && exo != null && now.item?.key == file.key && now.playing && now.intentPlaying && deferredSeek == null
    }

    /**
     * The native queue ran out before the next file finished downloading. The regular player streams that track at once,
     * and BG Mix takes the queue back once its file is complete.
     */
    private fun continueBgMixQueueOnExo(items: List<PlaybackItem>, index: Int, autoPlay: Boolean = true) {
        val context = appContext ?: return
        if (!bgMixActive() || index !in items.indices) return
        stopBgMix()
        startQueue(context, PlaybackQueue(items, index), queueSource, startPositionMs = null, startFraction = 0f, autoPlay = autoPlay)
    }

    private fun bgMixHasNext(index: Int, size: Int): Boolean =
        index < size - 1 || _state.value.repeatMode != PlaybackRepeatMode.Off

    /**
     * Starts the BG Mix queue again from [index] without leaving the mode: used for Previous, shuffle and repeat changes,
     * which the native session cannot do on its own. The position is restored once the new session plays. If the
     * session cannot start again, the queue continues on the regular player from the same place.
     */
    private fun restartBgMix(items: List<PlaybackItem>, index: Int, positionMs: Long, autoPlay: Boolean) {
        val context = appContext ?: return
        val ok = startBgMix(context, PlaybackQueue(items, index), reshuffle = false, autoPlay = autoPlay)
        if (!ok) {
            stopBgMix()
            startQueue(context, PlaybackQueue(items, index), queueSource, startPositionMs = positionMs, startFraction = 0f, autoPlay = autoPlay)
            return
        }
        bgMixSeekJob?.cancel()
        if (positionMs < 1_000L) return
        val statusFlow = bgMix?.status ?: return
        bgMixSeekJob = scope.launch {
            withTimeoutOrNull(3_000L) {
                statusFlow.first { status -> status?.decks?.getOrNull(status.master)?.playing == true }
            }
            bgMix?.seek(positionMs / 1000.0)
        }
    }

    /** Copies the native status into the Media state the UI and the notification read. */
    private fun applyBgMix(status: BgMixStatus) {
        val driver = bgMix ?: return
        val before = _state.value
        _bgMixTransition.value = nextBgMixTransition(
            previous = _bgMixTransition.value,
            status = status,
            previousIndex = before.queueIndex,
            indexOfNative = driver::indexOfNative,
            itemAt = driver::itemAt
        )
        val playingNativeId = status.queue.firstOrNull { it.state == "playing" }?.id
        val index = playingNativeId?.let { driver.indexOfNative(it) } ?: _state.value.queueIndex
        val item = driver.itemAt(index) ?: return
        val master = status.decks.getOrNull(status.master)
        val deckPlaying = master?.playing == true
        val finished = status.queue.isNotEmpty() &&
            status.queue.all { it.state == "played" || it.state == "failed" } &&
            !status.transitionActive && !deckPlaying
        val pending = driver.pendingIndex
        if (finished && pending != null) {
            val items = driver.queueItems
            scope.launch { continueBgMixQueueOnExo(items, pending) }
            return
        }
        val playing = !status.paused && deckPlaying && !finished
        val hasNext = status.queue.any { (driver.indexOfNative(it.id) ?: -1) > index } || pending != null
        _state.update { current ->
            current.copy(
                // Repeat One keeps one track in the native queue, so Next is judged by the logical queue.
                timelineHasNext = hasNext || current.repeatMode == PlaybackRepeatMode.One ||
                    (index >= driver.queueItems.lastIndex && bgMixHasNext(index, driver.queueItems.size)),
                timelineHasPrevious = true,
                item = item,
                queue = driver.queueItems,
                queueIndex = index,
                playing = playing,
                intentPlaying = !status.paused && !finished,
                positionMs = ((master?.positionSeconds ?: 0.0) * 1000).toLong(),
                durationMs = ((master?.durationSeconds ?: 0.0) * 1000).toLong(),
                started = current.started || playing,
                ended = finished,
                isBuffering = false,
                bgMixActive = true
            )
        }
        if (before.item?.key != _state.value.item?.key) {
            exo?.let { target ->
                if (target.mediaItemCount > 0) target.replaceMediaItem(0, placeholderItem(item))
            }
        }
        val repeat = _state.value.repeatMode
        val repeatsQueue = finished && !before.ended && repeat != PlaybackRepeatMode.Off &&
            status.queue.any { it.state == "played" }
        if (repeatsQueue) {
            // Repeat One plays the same track again, Repeat All starts the queue over; the mode stays BG Mix.
            val items = driver.queueItems
            val next = if (repeat == PlaybackRepeatMode.One) index else 0
            notifyChanges(before, _state.value)
            scope.launch { restartBgMix(items, next, 0L, autoPlay = true) }
            return
        }
        if (finished && !before.ended) {
            releaseBgFocus()
            releaseBgWakeLock()
        }
        notifyChanges(before, _state.value)
        fireStartedCallback()
    }

    private fun toggleBgMix() {
        val state = _state.value
        if (!state.ended && (state.intentPlaying || state.playing)) pauseBgMix() else bgMixPlay()
    }

    /** Resumes BG Mix, or restarts the queue from its current index when it has ended. */
    private fun bgMixPlay() {
        if (_state.value.ended) {
            playQueueIndex(_state.value.queueIndex)
            return
        }
        val context = appContext
        if (context != null && !requestBgFocus(context)) return
        val before = _state.value
        bgMix?.resume()
        holdBgWakeLock()
        _state.update { it.copy(intentPlaying = true, ended = false) }
        notifyChanges(before, _state.value)
    }

    private fun pauseBgMix() {
        // Any pause but the focus listener's transient one is final: a later focus gain must not restart playback.
        bgResumeOnFocusGain = false
        val before = _state.value
        bgMix?.pause()
        releaseBgWakeLock()
        _state.update { it.copy(playing = false, intentPlaying = false) }
        notifyChanges(before, _state.value)
    }

    private fun stopBgMix() {
        // Only a session that was running changes what the media session shows; ExoPlayer stops are not affected.
        val wasActive = bgMixActive()
        val before = _state.value
        cancelBgMixHandover()
        bgMixFeedJob?.cancel()
        bgMixFeedJob = null
        bgMixJob?.cancel()
        bgMixJob = null
        bgMixSeekJob?.cancel()
        bgMixSeekJob = null
        bgMixOriginalQueue = null
        bgMix?.stop()
        _bgMixTransition.value = null
        releaseBgFocus()
        releaseBgWakeLock()
        bgResumeOnFocusGain = false
        bgDucked = false
        if (wasActive && (before.playing || before.intentPlaying)) {
            notifyChanges(before, before.copy(playing = false, intentPlaying = false))
        }
    }

    /** BG Mix lost its output mid-session: the session is released, and the queue continues on ExoPlayer from the last position. */
    private fun failBgMix() = switchBgMixToExo()

    /**
     * Hands the BG Mix queue to the regular player at the same place. Used when its output fails and when the listener
     * presses something BG Mix cannot do (shuffle, repeat, speed, previous), so a button never looks active and does nothing.
     */
    private fun switchBgMixToExo() {
        val context = appContext ?: return
        if (!bgMixActive()) return
        val snapshot = _state.value
        val queue = PlaybackQueue(snapshot.queue, snapshot.queueIndex)
        stopBgMix()
        // The output failed, so the same queue is not offered to BG Mix again.
        startQueue(
            context,
            queue,
            queueSource,
            startPositionMs = snapshot.positionMs,
            startFraction = 0f,
            autoPlay = snapshot.intentPlaying,
            offerBgMix = false
        )
    }

    private fun haltPlayer() {
        stopBgMix()
        progress?.cancel()
        progress = null
        clearDeferredSeek()
        pendingStartFraction = 0f
        startedCallback = null
        transitionToken++
        prefetchedKeys.clear()
        prefetchPolicyJob?.cancel()
        prefetchPolicyJob = null
        prefetchPolicyAccount = null
        PlaybackSources.clear()
        exo?.let {
            crossfader.cancel(it)
            it.stop()
            it.clearMediaItems()
        }
        _audioSessionId.value = null
        val previous = _state.value
        _state.value = PlaybackState(
            speed = previous.musicSpeed,
            musicSpeed = previous.musicSpeed,
            voiceSpeed = previous.voiceSpeed,
            repeatMode = previous.repeatMode,
            shuffleEnabled = previous.shuffleEnabled
        )
        stopPlaybackService()
    }

    /** Останавливает секретный/view-once файл или медиа из секретного чата, когда пользователь ушёл из чата или свернул приложение. Остальные файлы играют дальше. */
    fun stopSecret() {
        if (_state.value.item?.systemPrivate != true) return
        val music = interruptedMusic
        val context = appContext
        if (music == null || context == null) {
            stop()
            return
        }
        interruptedMusic = null
        haltPlayer()
        restoreInterruptedMusic(music)
    }

    fun next() {
        if (bgMixActive()) {
            val state = _state.value
            val last = state.queueIndex >= state.queue.lastIndex
            when {
                state.repeatMode == PlaybackRepeatMode.One -> restartBgMix(state.queue, if (last) 0 else state.queueIndex + 1, 0L, state.intentPlaying || state.ended)
                // The next track is still downloading and not in the native queue yet: the regular player streams it.
                !last && bgMix?.hasNativeAfter(state.queueIndex) == false && bgMix?.pendingIndex != null ->
                    bgMix?.pendingIndex?.let {
                        // Like the regular Next, a paused queue stays paused on the new track.
                        continueBgMixQueueOnExo(state.queue, it, autoPlay = state.playing || state.intentPlaying || state.ended)
                    }
                !last -> bgMix?.next()
                state.repeatMode == PlaybackRepeatMode.All -> restartBgMix(state.queue, 0, 0L, state.intentPlaying || state.ended)
            }
            return
        }
        val target = exo ?: return
        val state = _state.value
        val hasNext = target.hasNextMediaItem()
        val fallback = if (state.shuffleEnabled) null else (state.queueIndex + 1).takeIf { it in state.queue.indices }
        val wrapAll = state.repeatMode == PlaybackRepeatMode.All && target.mediaItemCount > 1
        if (!hasNext && !wrapAll && fallback == null) return
        val wasPlaying = state.playing || state.intentPlaying || state.ended
        transitionToken++
        clearDeferredSeek()
        _state.update { it.copy(ended = false, failed = false) }
        when {
            hasNext -> {
                val before = target.currentMediaItemIndex
                target.seekToNextMediaItem()
                // Repeat One may make ExoPlayer's "next" the same item. Manual Next is documented to advance.
                if (target.currentMediaItemIndex == before && state.repeatMode == PlaybackRepeatMode.One && fallback != null) {
                    target.seekTo(fallback, 0L)
                }
            }
            wrapAll -> target.seekTo(0, 0L)
            fallback != null -> target.seekTo(fallback, 0L)
        }
        _state.update { it.copy(intentPlaying = wasPlaying).withTimelineNavigation(target) }
        if (wasPlaying) {
            if (target.playbackState == Player.STATE_IDLE) target.prepare()
            if (state.item?.systemPrivate != true) startPlaybackService()
            target.play()
            startProgress()
        }
        loadNextQueuePage()
    }

    /**
     * A queue row is a direct Play action: never toggle the current track or modify the queue order.
     * Media3 emits the same item transition as hardware Next/Previous, keeping the system session in sync.
     */
    fun playQueueItem(key: String) {
        val index = _state.value.queue.indexOfFirst { it.key == key }
        if (index >= 0) playQueueIndex(index)
    }

    fun playQueueIndex(index: Int) {
        if (bgMixActive()) {
            val context = appContext
            val queue = PlaybackQueue(_state.value.queue, index)
            // The regular player starts the track at once; BG Mix takes it back as soon as it has prepared it.
            stopBgMix()
            if (context != null) {
                startQueue(context, queue, queueSource, startPositionMs = null, startFraction = 0f)
                return
            }
        }
        val snapshot = _state.value
        if (!canSelectQueueIndex(snapshot, index)) return
        val target = exo ?: return
        if (index >= target.mediaItemCount) return
        transitionToken++
        clearDeferredSeek()
        _state.update { it.copy(ended = false, failed = false, intentPlaying = true) }
        target.seekTo(index, 0L)
        if (target.playbackState == Player.STATE_IDLE || target.playbackState == Player.STATE_ENDED) target.prepare()
        target.play()
        startPlaybackService()
        startProgress()
        prefetchNext()
        loadNextQueuePage()
    }

    fun previous() {
        if (bgMixActive()) {
            val state = _state.value
            // Like the regular player: a few seconds in, Previous restarts the track; otherwise it steps back.
            if (state.ended || state.positionMs >= PREVIOUS_RESTART_THRESHOLD_MS || state.queueIndex <= 0) {
                // A native seek is refused during a transition; the session then starts the track again instead of dropping the press.
                if (state.ended || bgMix?.seek(0.0) != true) restartBgMix(state.queue, state.queueIndex, 0L, state.intentPlaying || state.ended)
            } else {
                restartBgMix(state.queue, state.queueIndex - 1, 0L, state.intentPlaying)
            }
            return
        }
        val target = exo ?: return
        val state = _state.value
        // Previous restarts the current track when it is already a few seconds in; otherwise it goes to the previous item.
        if (!state.ended && state.positionMs >= PREVIOUS_RESTART_THRESHOLD_MS) {
            val resume = state.restartResumeIntent()
            // A deferred growing-file seek must not resurrect the old position when the download completes.
            clearDeferredSeek()
            pendingStartFraction = 0f
            target.seekTo(0L)
            if (resume) {
                if (target.playbackState == Player.STATE_IDLE) target.prepare()
                if (state.item?.systemPrivate != true) startPlaybackService()
                target.play()
                startProgress()
            }
            _state.update {
                it.copy(
                    positionMs = 0L,
                    ended = false,
                    failed = false,
                    playing = target.isPlaying,
                    intentPlaying = resume,
                    isBuffering = target.playbackState == Player.STATE_BUFFERING
                ).withTimelineNavigation(target)
            }
            return
        }
        val wasPlaying = state.playing || state.intentPlaying || state.ended
        transitionToken++
        clearDeferredSeek()
        val hasPrevious = target.hasPreviousMediaItem()
        // No previous item in the actual (shuffled) order and nothing to restart: Previous is disabled.
        if (!hasPrevious && !state.ended && state.positionMs < PREVIOUS_RESTART_THRESHOLD_MS &&
            !(state.repeatMode == PlaybackRepeatMode.All && target.mediaItemCount > 1)
        ) {
            return
        }
        _state.update { it.copy(ended = false, failed = false) }
        if (!hasPrevious) {
            if (state.repeatMode == PlaybackRepeatMode.All && target.mediaItemCount > 1) {
                target.seekTo(target.mediaItemCount - 1, 0L)
            } else {
                target.seekTo(0L)
            }
        } else {
            val before = target.currentMediaItemIndex
            target.seekToPreviousMediaItem()
            // Repeat One can suppress manual Previous as well.
            if (target.currentMediaItemIndex == before && state.repeatMode == PlaybackRepeatMode.One && !state.shuffleEnabled) {
                val fallback = (state.queueIndex - 1).takeIf { it in state.queue.indices }
                if (fallback != null) target.seekTo(fallback, 0L)
            }
        }
        _state.update { it.copy(intentPlaying = wasPlaying).withTimelineNavigation(target) }
        if (wasPlaying) {
            if (target.playbackState == Player.STATE_IDLE) target.prepare()
            if (state.item?.systemPrivate != true) startPlaybackService()
            target.play()
            startProgress()
        }
    }

    /** Files the player still reads: the playing track, its queue, and a music track waiting behind a voice note. */
    fun musicPathsInUse(): Set<String> {
        val current = _state.value
        val waiting = interruptedMusic?.let { listOf(it.item) + it.queue }.orEmpty()
        return (listOfNotNull(current.item) + current.queue + waiting).map { it.path }.filter { it.isNotBlank() }.toSet()
    }

    fun setShuffle(enabled: Boolean) {
        if (_state.value.shuffleEnabled == enabled) return
        if (bgMixActive()) {
            val state = _state.value
            val current = state.item ?: return
            _state.update { it.copy(shuffleEnabled = enabled) }
            val items: List<PlaybackItem>
            if (enabled) {
                bgMixOriginalQueue = state.queue
                items = listOf(current) + state.queue.filterIndexed { i, _ -> i != state.queueIndex }.shuffled()
            } else {
                val original = bgMixOriginalQueue
                bgMixOriginalQueue = null
                items = original?.takeIf { queue -> queue.any { it.key == current.key } } ?: state.queue
            }
            val index = items.indexOfFirst { it.key == current.key }.coerceAtLeast(0)
            // The native session has no shuffle, so it starts again at the same place with the new order.
            val keepOriginal = bgMixOriginalQueue
            restartBgMix(items, index, state.positionMs, state.intentPlaying)
            if (enabled) bgMixOriginalQueue = keepOriginal
            return
        }
        val target = exo
        if (_state.value.item?.isMusic == true && _state.value.item?.systemPrivate != true) target?.shuffleModeEnabled = enabled
        _state.update { it.copy(shuffleEnabled = enabled).withTimelineNavigation(target) }
        if (enabled && target != null) installShuffleOrder(target, target.mediaItemCount, currentIndex = target.currentMediaItemIndex)
        prefetchedKeys.clear()
        prefetchNext()
    }

    fun cycleRepeat() {
        setRepeatMode(_state.value.repeatMode.next())
    }

    fun setRepeatMode(mode: PlaybackRepeatMode) {
        if (bgMixActive()) {
            val state = _state.value
            _state.update { it.copy(repeatMode = mode) }
            // Repeat One keeps only the current track in the native queue, so entering or leaving it starts the session again.
            if ((mode == PlaybackRepeatMode.One) != (state.repeatMode == PlaybackRepeatMode.One)) {
                val original = bgMixOriginalQueue
                restartBgMix(state.queue, state.queueIndex, state.positionMs, state.intentPlaying)
                bgMixOriginalQueue = original
            } else {
                _state.update { it.copy(timelineHasNext = bgMixHasNext(it.queueIndex, it.queue.size)) }
            }
            return
        }
        val target = exo
        if (_state.value.item?.isMusic == true && _state.value.item?.systemPrivate != true) target?.repeatMode = mode.toPlayerRepeatMode()
        _state.update { it.copy(repeatMode = mode).withTimelineNavigation(target) }
        prefetchedKeys.clear()
        prefetchNext()
        if (mode != PlaybackRepeatMode.One) loadNextQueuePage()
    }

    /** True when the Media3 playlist mirrors [PlaybackState.queue], so it can be edited in place. */
    private fun canEditInPlace(target: ExoPlayer?, queueSize: Int): Boolean =
        target != null && !bgMixActive() && queueSize > 0 && target.mediaItemCount == queueSize

    /**
     * Publishes a queue that Media3 already holds after an in-place edit. The current item keeps playing, so no
     * source is reopened; only the cached prefetch decision is refreshed for the new order.
     */
    private fun commitInPlaceQueue(queue: List<PlaybackItem>) {
        val target = exo ?: return
        prefetchedKeys.clear()
        _state.update {
            it.copy(
                queue = queue,
                queueIndex = target.currentMediaItemIndex.coerceIn(queue.indices)
            ).withTimelineNavigation(target)
        }
        prefetchNext()
        requestSilenceMeasurement()
    }

    /** Inserts [item] after the current item and keeps the current position. */
    @OptIn(UnstableApi::class)
    fun playNext(item: PlaybackItem, source: PlaybackQueueSource? = null) {
        if (item.systemPrivate || !item.isMusic) return
        val state = _state.value
        val current = state.item ?: return
        if (current.systemPrivate || !current.isMusic || item.key == current.key) return
        if (source != null) queueSource = source
        val shouldPlay = state.playing || state.intentPlaying
        val target = exo
        val base = state.queue.ifEmpty { listOf(current) }
        val currentIndex = (target?.currentMediaItemIndex?.takeIf { it in base.indices }
            ?: state.queueIndex.takeIf { it in base.indices }
            ?: base.indexOfFirst { it.key == current.key })
        if (currentIndex < 0) return
        val existing = base.indexOfFirst { it.key == item.key }
        val oldOrder = if (state.shuffleEnabled && target != null) {
            runCatching { target.shuffleOrder.toIndexOrder() }.getOrDefault(base.indices.toList())
        } else {
            base.indices.toList()
        }
        val inserted = queueAfterPlayNext(base, currentIndex, item, existing, oldOrder)
        if (canEditInPlace(target, base.size) && target != null) {
            // The playing track is never reopened: the new item is added (or an existing one moved) next to it.
            val insertAt = currentIndex + 1
            if (existing in base.indices && existing != currentIndex) {
                target.moveMediaItem(existing, if (existing < currentIndex) currentIndex else insertAt)
            } else {
                registerSource(item, queueSource)
                target.addMediaItem(insertAt, mediaItemFor(item))
            }
            if (state.shuffleEnabled && inserted.playbackOrder.size == target.mediaItemCount) {
                target.setShuffleOrder(PlaybackShuffleOrder(inserted.playbackOrder))
            }
            commitInPlaceQueue(inserted.queue)
            return
        }
        if (state.shuffleEnabled && target != null) {
            rebuildTimeline(inserted.queue, inserted.queueIndex, state.positionMs, shouldPlay, inserted.playbackOrder)
            return
        }
        val queue = base.filterNot { it.key == item.key }.toMutableList()
        val rebuiltIndex = queue.indexOfFirst { it.key == current.key }.takeIf { it >= 0 } ?: 0
        queue.add((rebuiltIndex + 1).coerceIn(0, queue.size), item)
        rebuildTimeline(queue, rebuiltIndex, state.positionMs, shouldPlay)
    }

    /**
     * Adds [item] to the queue without interrupting the current track: [atEnd] puts it last, otherwise it plays next.
     * When no music is playing, the item simply starts as a one-track queue.
     */
    @OptIn(UnstableApi::class)
    fun enqueue(context: Context, item: PlaybackItem, source: PlaybackQueueSource?, atEnd: Boolean) {
        if (item.systemPrivate || !item.isMusic) return
        val state = _state.value
        val current = state.item
        if (current == null || !current.isMusic || current.systemPrivate || current.accountId != item.accountId) {
            playQueue(context, listOf(item), 0, source)
            return
        }
        if (!atEnd) {
            playNext(item, source)
            return
        }
        if (item.key == current.key) return
        if (source != null && queueSource == null) queueSource = source
        val target = exo
        val base = state.queue.ifEmpty { listOf(current) }
        val existing = base.indexOfFirst { it.key == item.key }
        if (existing >= 0) {
            if (existing != base.lastIndex) moveInQueue(existing, base.lastIndex)
            return
        }
        val queue = base + item
        if (canEditInPlace(target, base.size) && target != null) {
            registerSource(item, queueSource)
            target.addMediaItem(mediaItemFor(item))
            commitInPlaceQueue(queue)
            return
        }
        rebuildTimeline(queue, base.indexOfFirst { it.key == current.key }.coerceAtLeast(0), state.positionMs, state.playing || state.intentPlaying)
    }

    /** Removes an item from the queue; removing the playing item advances to the next (or previous) one. */
    fun removeFromQueue(index: Int) {
        val state = _state.value
        if (index !in state.queue.indices) return
        val queue = state.queue.toMutableList().apply { removeAt(index) }
        if (queue.isEmpty()) {
            stop()
            return
        }
        val target = exo
        if (index != state.queueIndex && canEditInPlace(target, state.queue.size) && target != null) {
            // A neighbour leaves the list; the playing track and its prefetched successor stay untouched.
            val removed = state.queue[index]
            target.removeMediaItem(index)
            PlaybackSources.remove(removed.systemMediaId)
            commitInPlaceQueue(queue)
            return
        }
        val shouldPlay = state.playing || state.intentPlaying
        val currentIndex = when {
            index < state.queueIndex -> state.queueIndex - 1
            index > state.queueIndex -> state.queueIndex
            else -> index.coerceAtMost(queue.lastIndex)
        }
        val position = if (index == state.queueIndex) 0L else state.positionMs
        rebuildTimeline(queue, currentIndex.coerceIn(queue.indices), position, shouldPlay)
    }

    /** Drops every track except the current one. The playing track and its position are not touched. */
    fun clearUpcoming() {
        val state = _state.value
        val keep = state.queueIndex
        if (state.queue.size <= 1 || keep !in state.queue.indices) return
        // A page that is still loading, or one that the source would load later, must not refill the cleared queue.
        queuePageGeneration++
        queuePageJob?.cancel()
        queuePageJob = null
        queuePageRetryJob?.cancel()
        queuePageRetryJob = null
        _queuePageLoading.value = false
        queueSource = queueSource?.let {
            PlaybackQueueSource(it.accountId, it.observeFile, it.download, it.autoDownload, loadMore = null)
        }
        // From the end: removing a later row never moves an earlier one.
        for (index in state.queue.indices.reversed()) {
            if (index != keep) removeFromQueue(index)
        }
    }

    /** Moves an item inside the queue while keeping the current item and position. */
    fun moveInQueue(from: Int, to: Int) {
        val state = _state.value
        if (from !in state.queue.indices || to !in state.queue.indices || from == to) return
        val queue = state.queue.toMutableList()
        val moved = queue.removeAt(from)
        queue.add(to, moved)
        val target = exo
        if (canEditInPlace(target, state.queue.size) && target != null) {
            target.moveMediaItem(from, to)
            commitInPlaceQueue(queue)
            return
        }
        val currentKey = state.item?.key ?: return
        val index = queue.indexOfFirst { it.key == currentKey }.takeIf { it >= 0 } ?: state.queueIndex
        rebuildTimeline(queue, index.coerceIn(queue.indices), state.positionMs, state.playing || state.intentPlaying)
    }

    /**
     * Legacy kind-based selector retained for external callers. Inline voice/video-note controls must call
     * [setVoiceSpeed] explicitly; music controls call [setMusicSpeed].
     */
    @Deprecated(
        message = "Use setMusicSpeed or setVoiceSpeed explicitly",
        replaceWith = ReplaceWith("setMusicSpeed(speed) or setVoiceSpeed(speed)"),
        level = DeprecationLevel.WARNING
    )
    fun setSpeed(speed: Float) {
        if (speed !in PLAYBACK_SPEED_OPTIONS) return
        val kind = _state.value.item?.kind
        if (kind == null || kind == MediaKind.Audio) setMusicSpeed(speed) else setVoiceSpeed(speed)
    }

    fun setMusicSpeed(speed: Float) {
        if (speed !in PLAYBACK_SPEED_OPTIONS) return
        val current = _state.value
        val applies = current.item?.kind == MediaKind.Audio || current.item == null
        _state.update { it.copy(musicSpeed = speed, speed = if (applies) speed else it.speed) }
        if (applies) exo?.setPlaybackSpeed(speed)
    }

    fun setVoiceSpeed(speed: Float) {
        if (speed !in PLAYBACK_SPEED_OPTIONS) return
        val current = _state.value
        val item = current.item
        val applies = item != null && item.kind != MediaKind.Audio
        _state.update { it.copy(voiceSpeed = speed, speed = if (applies) speed else it.speed) }
        if (applies) exo?.setPlaybackSpeed(speed)
    }

    /** Просит открыть чат с сообщением, из которого запущен файл. Экран приложения переходит к этому сообщению. */
    fun requestShowInChat(origin: PlaybackOrigin) {
        _showInChat.tryEmit(origin)
    }

    /** Выводит видео текущего файла на [view]. Пузырь кружка вызывает это, пока кружок показан: без экрана звук продолжает играть. */
    fun attachVideo(owner: Any, view: TextureView) {
        val target = exo ?: return
        if (videoOwner === owner && videoView === view) return
        videoView?.let { target.clearVideoTextureView(it) }
        videoOwner = owner
        videoView = view
        target.setVideoTextureView(view)
    }

    /** Снимает видео, если его держит [owner]. Чужой пузырь ничего не снимает, поэтому порядок ухода с экрана не важен. */
    fun detachVideo(owner: Any) {
        if (videoOwner !== owner) return
        videoView?.let { exo?.clearVideoTextureView(it) }
        videoOwner = null
        videoView = null
    }

    /** Сервис нужен для уведомления и экрана блокировки. Фоновый запуск может не пройти, тогда звук всё равно играет. */
    private fun startPlaybackService() {
        val context = appContext ?: return
        val intent = Intent(context, PlaybackService::class.java)
        try {
            context.startService(intent)
        } catch (error: Exception) {
            // Android 8+ refuses a plain start from the background. While audio plays the service may still be
            // started as a foreground one, and a failure is written to the diagnostics instead of being swallowed.
            val recovered = exo?.isPlaying == true &&
                runCatching { ContextCompat.startForegroundService(context, intent) }.isSuccess
            recordAppEvent(
                kind = EventKind.ERROR,
                code = "playback_service_start_failed",
                text = "Музыка: сервис воспроизведения не запустился",
                severity = Severity.WARNING,
                attrs = mapOf(
                    "error" to error.javaClass.simpleName,
                    "foreground" to foreground.toString(),
                    "recovered" to recovered.toString()
                )
            )
        }
    }

    /** Explicit stop must remove the foreground notification instead of leaving a phantom session. */
    private fun stopPlaybackService() {
        val context = appContext ?: return
        runCatching { context.stopService(Intent(context, PlaybackService::class.java)) }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (bgMixActive()) return
            _state.update {
                it.copy(
                    playing = isPlaying,
                    intentPlaying = it.intentPlaying || isPlaying,
                    durationMs = durationNow(),
                    started = it.started || isPlaying
                )
            }
            if (isPlaying) {
                if (consecutiveSkips != 0 || networkWaitSince != 0L || _state.value.waitingForNetwork) resetErrorPolicy()
                startProgress()
                fireStartedCallback()
            } else {
                syncPosition()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (bgMixActive()) return
            val pendingDeferredResume = deferredSeek?.resume == true &&
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
            _state.update {
                it.copy(
                    intentPlaying = intentAfterPlayWhenReadyChange(
                        it.intentPlaying,
                        playWhenReady,
                        reason,
                        pendingDeferredResume
                    )
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (bgMixActive()) return
            updateAudioSessionId()
            if (playbackState == Player.STATE_READY) applyPendingStartFraction()
            val endedNow = playbackState == Player.STATE_ENDED
            _state.update {
                it.copy(
                    durationMs = durationNow(),
                    ended = endedNow,
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    bufferedFraction = exo?.bufferedPercentage?.coerceIn(0, 100)?.toFloat()?.div(100f) ?: it.bufferedFraction
                ).withTimelineNavigation(exo)
            }
            if (playbackState == Player.STATE_BUFFERING) startProgress()
            if (playbackState == Player.STATE_READY && exo?.isPlaying == true) {
                _state.update { it.copy(started = true) }
                fireStartedCallback()
            }
            // Следующий файл ставим после того, как плеер закончит обрабатывать конец файла, а не внутри его обработчика.
            if (endedNow) {
                val key = _state.value.item?.key
                val token = transitionToken
                scope.launch { handleEnded(key, token) }
            }
        }

        // Перемотку с уведомления или экрана блокировки делает сам плеер, минуя PlaybackEngine: позицию обновляем и на паузе.
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (bgMixActive()) return
            syncPosition()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (bgMixActive()) return
            trailingSkippedKey = null
            val mediaId = mediaItem?.mediaId
            val snapshot = _state.value
            if (mediaId != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                transitionToken++
                val index = snapshot.queue.indexOfFirst { it.key == mediaId }
                if (index >= 0) {
                    val item = resolvedItem(snapshot.queue[index])
                    val speed = effectiveSpeed(item)
                    exo?.setPlaybackSpeed(speed)
                    val queue = snapshot.queue.map { if (it.key == item.key) item else it }
                    _state.update {
                        it.copy(
                            item = item,
                            queue = queue,
                            queueIndex = index,
                            positionMs = 0L,
                            durationMs = durationNow(),
                            speed = speed,
                            ended = false,
                            failed = false,
                            started = exo?.isPlaying == true,
                            isBuffering = exo?.playbackState == Player.STATE_BUFFERING,
                            intentPlaying = exo?.playWhenReady ?: it.intentPlaying
                        ).withTimelineNavigation(exo)
                    }
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) exo?.let { skipLeadingSilence(it, item) }
                    startedCallback = startedCallback?.takeIf { it.first == item.key }
                    if (exo?.isPlaying == true) fireStartedCallback()
                }
            }
            updateAudioSessionId()
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                prefetchNext()
                requestSilenceMeasurement()
                queuePageRetries = 0
                loadNextQueuePage()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (bgMixActive()) return
            progress?.cancel()
            clearDeferredSeek()
            val target = exo
            val snapshot = _state.value
            val musicQueue = snapshot.item?.let { it.isMusic && !it.systemPrivate } == true
            if (musicQueue && target != null) {
                val kind = classifyPlaybackError(error)
                val now = SystemClock.elapsedRealtime()
                if (kind == PlaybackErrorKind.NetworkWait && networkWaitSince == 0L) networkWaitSince = now
                val waited = if (networkWaitSince != 0L) now - networkWaitSince else 0L
                when (playbackErrorAction(kind, target.hasNextMediaItem(), consecutiveSkips, waited)) {
                    PlaybackErrorAction.WaitForNetwork -> {
                        beginNetworkWait(waited)
                        return
                    }
                    PlaybackErrorAction.SkipToNext -> {
                        skipBrokenTrack(target)
                        return
                    }
                    PlaybackErrorAction.Fail -> Unit
                }
            }
            networkWaitJob?.cancel()
            networkWaitSince = 0L
            _state.update {
                it.copy(playing = false, intentPlaying = false, failed = true, waitingForNetwork = false, isBuffering = false)
                    .withTimelineNavigation(exo)
            }
            // Голосовое или кружок, которые не открылись, не должны оставить прерванную музыку навсегда на паузе.
            val music = interruptedMusic ?: return
            val context = appContext ?: return
            if (_state.value.item?.key == music.item.key) return
            interruptedMusic = null
            scope.launch { restoreInterruptedMusic(music) }
        }
    }

    private fun resetErrorPolicy() {
        consecutiveSkips = 0
        networkWaitSince = 0L
        networkWaitJob?.cancel()
        networkWaitJob = null
        queuePageRetries = 0
        queuePageRetryJob?.cancel()
        queuePageRetryJob = null
        if (_state.value.waitingForNetwork) _state.update { it.copy(waitingForNetwork = false) }
    }

    /**
     * The queue lost the network: keep the intent to play, show a calm status and continue from the same position
     * when the network returns (or after a pause when it claims to be up). The wait is bounded.
     */
    private fun beginNetworkWait(waitedMs: Long) {
        val key = _state.value.item?.key ?: return
        _state.update {
            it.copy(playing = false, failed = false, waitingForNetwork = true, isBuffering = false)
                .withTimelineNavigation(exo)
        }
        networkWaitJob?.cancel()
        networkWaitJob = scope.launch {
            appContext?.let { AutoDownloadNetworkStatus.ensureStarted(it) }
            val remaining = (NETWORK_WAIT_LIMIT_MS - waitedMs).coerceAtLeast(1L)
            val recovered = withTimeoutOrNull(remaining) {
                if (AutoDownloadNetworkStatus.network.value == AutoDownloadNetwork.None) {
                    AutoDownloadNetworkStatus.network.first { it != AutoDownloadNetwork.None }
                } else {
                    delay(NETWORK_RETRY_DELAY_MS)
                }
                true
            }
            val target = exo ?: return@launch
            val now = _state.value
            if (!now.waitingForNetwork || now.item?.key != key) return@launch
            if (recovered != true) {
                networkWaitSince = 0L
                _state.update { it.copy(failed = true, intentPlaying = false, waitingForNetwork = false) }
                return@launch
            }
            if (!target.playWhenReady) {
                // The listener paused the queue on purpose while it waited, so the wait is over without playing.
                networkWaitSince = 0L
                _state.update { it.copy(waitingForNetwork = false) }
                return@launch
            }
            target.prepare()
            target.play()
        }
    }

    /** A track that cannot be opened is skipped with a short notice; the whole queue is not stopped by one bad file. */
    private fun skipBrokenTrack(target: ExoPlayer) {
        consecutiveSkips++
        _notices.tryEmit(PlaybackNotice.TrackSkipped)
        _state.update { it.copy(failed = false, waitingForNetwork = false) }
        // The user's intent decides whether the next track starts: a track that failed while paused stays paused.
        val keepPlaying = target.playWhenReady
        target.seekToNextMediaItem()
        target.prepare()
        if (keepPlaying) target.play()
    }

    private fun durationNow(): Long = exo?.duration?.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L

    /** Доля начала становится позицией, когда плеер знает настоящую длину файла. Пока длина неизвестна, доля ждёт. */
    private fun applyPendingStartFraction() {
        val target = exo ?: return
        val duration = target.duration
        if (pendingStartFraction <= 0f || duration == C.TIME_UNSET || duration <= 0L) return
        target.seekTo((pendingStartFraction * duration).toLong())
        pendingStartFraction = 0f
    }

    /** Файл доиграл: сначала возвращаем музыку, которую прервало голосовое или кружок, иначе закрываем фоновую сессию. */
    private fun handleEnded(endedKey: String?, token: Long) {
        if (!shouldHandleEnded(endedKey, _state.value.item?.key, token, transitionToken)) return
        if (!_state.value.ended) return
        if (interruptedMusic != null) {
            resumeInterruptedMusic()
            return
        }
        // Секретный файл доиграл: его нельзя перемотать и запустить снова, поэтому он уходит из плеера.
        if (_state.value.item?.systemPrivate == true) {
            stop()
            return
        }
        // The last loaded track ended while the next queue page is still loading (or about to be retried). The page
        // restarts playback, and Android would refuse to start the service again from the background, so it stays up.
        if (queuePageJob?.isActive == true || queuePageRetryJob?.isActive == true) return
        // ExoPlayer already advanced through Repeat/Next when it could; ENDED means the session is finished.
        stopPlaybackService()
        _audioSessionId.value = null
    }

    /** A page request that brought no new tracks leaves the queue ended, so the service it kept alive can stop. */
    private fun stopServiceIfQueueStillEnded() {
        if (queuePageRetryJob?.isActive == true || interruptedMusic != null) return
        if (exo?.playbackState != Player.STATE_ENDED) return
        stopPlaybackService()
        _audioSessionId.value = null
    }

    private fun resumeInterruptedMusic() {
        if (!_state.value.ended) return
        val music = interruptedMusic ?: return
        interruptedMusic = null
        restoreInterruptedMusic(music)
    }

    private fun restoreInterruptedMusic(music: InterruptedMusic) {
        val context = appContext ?: return
        _state.update { it.copy(shuffleEnabled = music.shuffleEnabled, repeatMode = music.repeatMode) }
        queueSource = music.source
        if (music.queue.isNotEmpty() && music.queueIndex in music.queue.indices) {
            startQueue(
                context,
                PlaybackQueue(music.queue, music.queueIndex),
                music.source,
                startPositionMs = music.positionMs,
                startFraction = 0f
            )
        } else {
            startSingle(context, music.item, startPositionMs = music.positionMs, startFraction = 0f)
        }
    }

    /** Отдаёт отметку текущему файлу один раз, когда он уже играет и приложение на переднем плане. Если файл сменился, отметка уже сброшена в [play]. */
    private fun fireStartedCallback() {
        val pending = startedCallback ?: return
        if (!foreground || !_state.value.started || pending.first != _state.value.item?.key) return
        startedCallback = null
        pending.second()
    }

    private fun syncPosition() {
        val target = exo ?: return
        updateAudioSessionId()
        _state.update {
            it.copy(
                positionMs = target.currentPosition.coerceAtLeast(0L),
                durationMs = durationNow(),
                bufferedFraction = target.bufferedPercentage.coerceIn(0, 100).toFloat() / 100f,
                isBuffering = target.playbackState == Player.STATE_BUFFERING,
                playing = target.isPlaying
            ).withTimelineNavigation(target)
        }
    }

    /** The source of the playing queue: its account and file observers. The queue panel reads Telegram download state through it. */
    internal fun activeQueueSource(): PlaybackQueueSource? = queueSource

    private fun updateAudioSessionId() {
        val current = _state.value.item
        val id = exo?.audioSessionId
        val isMusic = current != null && current.isMusic && !current.systemPrivate
        val musicSession = if (id != null && id > 0 && isMusic) id else null
        if (_audioSessionId.value != musicSession) _audioSessionId.value = musicSession
    }

    /**
     * The progress tick is quick only where timing matters: in the last seconds of a track with the silence skip on, so the
     * skip lands within a few tens of milliseconds of the measured silence instead of a quarter of a second after it.
     */
    private fun progressDelayMs(target: ExoPlayer): Long {
        if (!TrackTransitionSettings.skipSilence.value) return PROGRESS_INTERVAL_MS
        val duration = target.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return PROGRESS_INTERVAL_MS
        return if (duration - target.currentPosition <= SILENCE_SCAN_MS + 500L) PROGRESS_FAST_INTERVAL_MS else PROGRESS_INTERVAL_MS
    }

    /** Обновляет позицию, пока файл играет. Пауза и конец файла останавливают цикл. */
    private fun startProgress() {
        progress?.cancel()
        progress = scope.launch {
            while (true) {
                syncPosition()
                settleFinishedDownload()
                val target = exo ?: break
                crossfader.watch(target)
                measureBeforeTransition(target)
                skipTrailingSilence(target)
                if (!target.isPlaying && target.playbackState != Player.STATE_BUFFERING) break
                delay(progressDelayMs(target))
            }
        }
    }

    /**
     * Extends the existing Media3 timeline without rebuilding the current source or resetting the seek position.
     * The loader belongs to the active Telegram account, and a stale result cannot resurrect a stopped queue.
     */
    private fun loadNextQueuePage() {
        val source = queueSource ?: return
        val loader = source.loadMore ?: return
        val snapshot = _state.value
        if (snapshot.item?.isMusic != true || snapshot.item.systemPrivate || snapshot.queue.isEmpty()) return
        // Repeat One never approaches a new track: avoid fetching the channel history on every replay.
        if (snapshot.repeatMode == PlaybackRepeatMode.One) return
        if (snapshot.queue.size - snapshot.queueIndex > 5 || queuePageJob?.isActive == true) return
        val generation = queuePageGeneration
        val currentAccount = snapshot.item.accountId
        _queuePageLoading.value = true
        queuePageJob = scope.launch {
            try {
                val page = loader()
                if (generation != queuePageGeneration || source !== queueSource ||
                    (activeAccountId != null && currentAccount != activeAccountId)) return@launch
                val now = _state.value
                if (now.queue.isEmpty() || now.item?.accountId != currentAccount) return@launch
                val fresh = appendMusicPage(now.queue, page, currentAccount)
                if (fresh.isEmpty()) return@launch
                val target = exo ?: return@launch
                fresh.forEach { registerSource(it, source) }
                target.addMediaItems(fresh.map(::mediaItemFor))
                val ended = now.ended && now.intentPlaying
                _state.update { it.copy(queue = it.queue + fresh).withTimelineNavigation(target) }
                if (ended) {
                    target.seekTo(now.queue.size, 0L)
                    if (target.playbackState == Player.STATE_IDLE || target.playbackState == Player.STATE_ENDED) target.prepare()
                    target.play()
                    startPlaybackService()
                    startProgress()
                }
                queuePageRetries = 0
                prefetchNext()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the existing playable timeline and try again after 2, 5 and 15 seconds while the track plays.
                scheduleQueuePageRetry(generation)
            } finally {
                if (generation == queuePageGeneration) {
                    queuePageJob = null
                    _queuePageLoading.value = false
                    stopServiceIfQueueStillEnded()
                }
            }
        }
    }

    private fun scheduleQueuePageRetry(generation: Long) {
        val pause = queuePageRetryDelayMs(queuePageRetries) ?: return
        queuePageRetries++
        queuePageRetryJob?.cancel()
        queuePageRetryJob = scope.launch {
            delay(pause)
            if (generation == queuePageGeneration) loadNextQueuePage()
        }
    }

    /**
     * Asks for silence measurements of the playing track and of the track ExoPlayer plays next, in playback order.
     * The work runs on a background thread, so playback never waits for it.
     */
    private fun requestSilenceMeasurement() {
        if (!TrackTransitionSettings.skipSilence.value && !TrackTransitionSettings.crossfade.value) return
        val context = appContext ?: return
        val state = _state.value
        val target = exo ?: return
        if (state.queue.isEmpty()) return
        val nextIndex = target.nextMediaItemIndex
        val nextMediaId = if (nextIndex != C.INDEX_UNSET) target.getMediaItemAt(nextIndex).mediaId else null
        val next = nextMediaId?.let { id -> state.queue.firstOrNull { it.systemMediaId == id } }
        val candidates = listOfNotNull(state.item, next).map { withResolvedSource(it) }
        SilenceAnalyzer.request(context, candidates)
    }

    /** The local path of a queue item, for measuring only. Nothing is published and TDLib is not asked for a download. */
    private fun withResolvedSource(item: PlaybackItem): PlaybackItem {
        settledGrowingItem(item)?.let { return it }
        val resolved = PlaybackSources.handle(item.systemMediaId)?.resolve() ?: return item
        return if (resolved.growing == null) item.copy(path = resolved.path) else item
    }

    /**
     * Moves on early when the rest of the current track is silence, and starts the next track after its own leading
     * silence. Runs on the progress tick, and only in the last [SILENCE_SCAN_MS] of a measured track.
     */
    private fun skipTrailingSilence(target: ExoPlayer) {
        if (bgMixActive() || deferredSeek != null || !TrackTransitionSettings.skipSilence.value) return
        if (!target.isPlaying || target.repeatMode == Player.REPEAT_MODE_ONE || !target.hasNextMediaItem()) return
        val duration = target.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return
        val position = target.currentPosition
        if (duration - position > SILENCE_SCAN_MS) return
        val state = _state.value
        val current = state.item ?: return
        // The engine's item lags the player by one callback; act only when the player is on the same track.
        if (target.currentMediaItem?.mediaId != current.systemMediaId) return
        // A crossfade that is watching this track owns its end; the silence skip must not move the track early.
        if (crossfader.isWatching(current.systemMediaId)) return
        if (state.queue.size < 2 || current.key == trailingSkippedKey) return
        val edges = SilenceAnalyzer.cachedEdges(withResolvedSource(current)) ?: return
        if (!trailingSkipDue(position, duration, edges.trailingMs)) return
        val nextIndex = target.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return
        val nextId = target.getMediaItemAt(nextIndex).mediaId
        val next = state.queue.firstOrNull { it.systemMediaId == nextId } ?: return
        val startMs = SilenceAnalyzer.cachedEdges(withResolvedSource(next))
            ?.let { leadingSkipStartMs(it.leadingMs) } ?: 0L
        // One early move per track: a tick that runs before ExoPlayer reports the new index must not move again.
        trailingSkippedKey = current.key
        target.seekTo(nextIndex, startMs)
    }

    /** A natural move to the next track starts after that track's leading silence. Repeat One keeps the same start. */
    private fun skipLeadingSilence(target: ExoPlayer, item: PlaybackItem) {
        if (bgMixActive() || !TrackTransitionSettings.skipSilence.value) return
        if (target.repeatMode == Player.REPEAT_MODE_ONE) return
        val startMs = SilenceAnalyzer.cachedEdges(withResolvedSource(item))?.let { leadingSkipStartMs(it.leadingMs) } ?: 0L
        if (startMs > 0L) target.seekTo(startMs)
    }

    /** The second player of a crossfade. It plays the outgoing tail and never takes audio focus, because the primary holds it. */
    @OptIn(UnstableApi::class)
    private fun newCrossfadePlayer(): ExoPlayer? {
        val context = appContext ?: return null
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(PlaybackDataSourceFactory(context)))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ false
            )
            .setHandleAudioBecomingNoisy(false)
            .build()
    }

    /** Measured trailing silence of a queue item, once it is known. */
    private fun crossfadeTrailingMs(mediaId: String): Long? {
        val item = _state.value.queue.firstOrNull { it.systemMediaId == mediaId } ?: return null
        return SilenceAnalyzer.cachedEdges(withResolvedSource(item))?.trailingMs
    }

    /**
     * The track ExoPlayer plays after [mediaId], when that transition can be a crossfade: music from the queue, both as
     * complete local files. Null keeps the transition a plain cut.
     */
    private fun crossfadeNextAfter(primary: ExoPlayer, mediaId: String): CrossfadeNext? {
        val state = _state.value
        if (state.queue.size < 2 || primary.repeatMode == Player.REPEAT_MODE_ONE || !primary.hasNextMediaItem()) return null
        val current = state.queue.firstOrNull { it.systemMediaId == mediaId } ?: return null
        if (!isCrossfadeMusic(current)) return null
        val index = primary.nextMediaItemIndex
        if (index == C.INDEX_UNSET) return null
        val nextId = primary.getMediaItemAt(index).mediaId
        val next = state.queue.firstOrNull { it.systemMediaId == nextId } ?: return null
        if (!isCrossfadeMusic(next)) return null
        if (!crossfadeAllowedBetween(current, next, crossfadeTrailingMs(mediaId))) return null
        val startMs = if (TrackTransitionSettings.skipSilence.value) {
            SilenceAnalyzer.cachedEdges(withResolvedSource(next))?.let { leadingSkipStartMs(it.leadingMs) } ?: 0L
        } else {
            0L
        }
        return CrossfadeNext(index, nextId, startMs)
    }

    /** Music from a complete local file: never a voice message, a video note, secret media or a file that is still downloading. */
    private fun isCrossfadeMusic(item: PlaybackItem): Boolean {
        if (!item.isMusic || item.systemPrivate) return false
        val resolved = withResolvedSource(item)
        return resolved.growing == null && resolved.path.isNotBlank()
    }

    /**
     * Requests at most two following tracks from the actual Media3 playback order. The auto-download policy
     * (Data Saver/roaming/size) gates this; the current item, when actually opened, is always requested by its
     * data source instead.
     */
    @OptIn(UnstableApi::class)
    private fun prefetchNext() {
        // A provider queue has no TDLib source, but its rows still prefetch through the same policy.
        val source = queueSource
        val context = appContext ?: return
        val state = _state.value
        val playbackOrder = if (state.shuffleEnabled && exo != null) {
            runCatching { exo!!.shuffleOrder.toIndexOrder() }.getOrDefault(state.queue.indices.toList())
        } else {
            state.queue.indices.toList()
        }
        prefetchCandidates(state, playbackOrder).forEachIndexed { index, item ->
            prefetchItem(context, source, item, playbackNext = index == 0)
        }
    }

    /**
     * Fetches [item] ahead under the automatic-download policy, so Data Saver, roaming and size limits apply.
     * [playbackNext] marks the row that plays after the current one: a streamed row is part of playback, so only a
     * missing network stops it.
     */
    private fun prefetchItem(context: Context, source: PlaybackQueueSource?, item: PlaybackItem, playbackNext: Boolean = false) {
        run {
            val lazySource = LazyTrackRegistry.sourceFor(item.key)
            if (lazySource != null && item.path.isBlank()) {
                // A row without a file (bot result or provider track) is fetched ahead like a file. Nothing is remembered per
                // key: the source keeps one fetch per key and retries a failed one after its cooldown.
                val allowed = runCatching {
                    if (playbackNext) {
                        AutoDownloadRuntime.streamingPrefetchAllowed(context)
                    } else {
                        val request = AutoDownloadRequest(
                            fileId = 0,
                            kind = item.kind,
                            // A provider row has no size before its file exists: estimate it from the duration.
                            sizeBytes = item.sizeBytes.takeIf { it > 0L } ?: estimatedStreamBytes(item.durationSeconds),
                            isSecret = false,
                            isSecretChat = false,
                            hasSpoiler = false
                        )
                        AutoDownloadRuntime.decision(context, request, prefetchAccountFor(item)) == AutoDownloadDecision.Allowed
                    }
                }.getOrDefault(false)
                if (allowed) lazySource.request(item.key)
                return@run
            }
            val fileId = item.fileId ?: return@run
            if (source == null || source.accountId != item.accountId) return@run
            // Speculative traffic must use the repository automatic path; the manual callback is reserved for
            // the track playback actually opens, so Data Saver/network cancellation can track prefetch downloads.
            if (source.autoDownload == null) return@run
            if (item.key in prefetchedKeys) return@run
            val fileState = runCatching { source.observeFile(fileId).value }.getOrNull() ?: return@run
            if (fileState.localPath != null || fileState.isDownloading) return@run
            val request = AutoDownloadRequest(
                fileId = fileId,
                kind = item.kind,
                sizeBytes = item.sizeBytes,
                isSecret = item.secret || item.viewOnce,
                isSecretChat = item.isSecretChat,
                hasSpoiler = item.hasSpoiler
            )
            val allowed = runCatching {
                AutoDownloadRuntime.decision(context, request, item.accountId) == AutoDownloadDecision.Allowed
            }.getOrDefault(false)
            if (allowed) {
                val accepted = runCatching { source.prefetch(request) }.getOrDefault(false)
                if (shouldRememberPrefetch(allowed, accepted)) prefetchedKeys.add(item.key)
            }
        }
    }

    /**
     * A denied prefetch is intentionally not remembered: a network/settings change must retry it. Keep one observer
     * per current account so Data Saver toggles and route changes clear the settled keys and evaluate again.
     */
    private fun refreshPrefetchPolicyObserver() {
        val accountId = prefetchPolicyAccountId()
        if (accountId == null || _state.value.queue.isEmpty()) {
            prefetchPolicyJob?.cancel()
            prefetchPolicyJob = null
            prefetchPolicyAccount = null
            return
        }
        val context = appContext ?: return
        if (prefetchPolicyJob?.isActive == true && prefetchPolicyAccount == accountId) return
        prefetchPolicyJob?.cancel()
        prefetchPolicyAccount = accountId
        AutoDownloadNetworkStatus.ensureStarted(context.applicationContext)
        val settings = AutoDownloadSettingsStore.getInstance(context.applicationContext, accountId).settings
        prefetchPolicyJob = scope.launch {
            combine(AutoDownloadNetworkStatus.network, settings) { _, _ -> Unit }.collect {
                prefetchedKeys.clear()
                prefetchNext()
            }
        }
    }

    /** The account whose auto-download settings decide the queue: the Telegram source's, or the signed-in one for provider rows. */
    private fun prefetchPolicyAccountId(): String? {
        queueSource?.let { return it.accountId }
        if (_state.value.queue.none { LazyTrackRegistry.isLazyKey(it.key) }) return null
        return activeAccountId ?: LEGACY_PLAYBACK_ACCOUNT_ID
    }

    /** A provider row belongs to no account, so its download decision follows the signed-in account, not the legacy one. */
    private fun prefetchAccountFor(item: PlaybackItem): String =
        if (item.accountId == LEGACY_PLAYBACK_ACCOUNT_ID) activeAccountId ?: item.accountId else item.accountId
}
