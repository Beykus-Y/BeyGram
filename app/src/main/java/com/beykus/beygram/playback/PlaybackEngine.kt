package com.beykus.beygram.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
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
import com.beykus.beygram.data.media.AutoDownloadDecision
import com.beykus.beygram.data.media.AutoDownloadNetwork
import com.beykus.beygram.data.media.AutoDownloadNetworkStatus
import com.beykus.beygram.data.media.AutoDownloadRuntime
import com.beykus.beygram.data.media.Mp4StreamingProbe
import com.beykus.beygram.data.settings.AutoDownloadSettingsStore
import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.media.bgmix.BgMixSettingsStore
import com.beykus.beygram.media.bgmix.BgMixStatus
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** The queue view shows a real TDLib page request rather than claiming the playlist ended prematurely. */
    private val _queuePageLoading = MutableStateFlow(false)
    val queuePageLoading: StateFlow<Boolean> = _queuePageLoading.asStateFlow()

    private val _sleepTimer = MutableStateFlow<SleepTimerState?>(null)
    val sleepTimer: StateFlow<SleepTimerState?> = _sleepTimer.asStateFlow()
    private var sleepTimerJob: Job? = null

    /** A sleep timer survives track changes inside one account, but never a switch to another account. */
    fun setSleepTimer(minutes: Int?) {
        cancelSleepTimer()
        if (minutes == null || minutes !in SLEEP_TIMER_MINUTES) return
        val current = _state.value.item?.takeIf { it.isMusic && !it.systemPrivate } ?: return
        val deadline = SystemClock.elapsedRealtime() + minutes * 60_000L
        val session = SleepTimerState(current.accountId, deadline)
        _sleepTimer.value = session
        sleepTimerJob = scope.launch {
            delay(minutes * 60_000L)
            if (_sleepTimer.value == session) {
                if (_state.value.item?.accountId == session.accountId) stop() else cancelSleepTimer()
            }
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
    )

    /**
     * A cover thumbnail may finish downloading after playback has already started. The key carries account/origin,
     * so another account's file with the same numeric id is never updated.
     */
    fun updateArtwork(key: String, path: String) {
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
        val position = target.currentPosition
        val wasPlaying = target.isPlaying
        target.replaceMediaItem(index, mediaItemFor(current))
        if (index == target.currentMediaItemIndex) {
            // Replacing metadata must not move the playhead; Media3 may re-open the current source.
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
            .build()
            .also {
                it.addListener(listener)
                it.repeatMode = _state.value.repeatMode.toPlayerRepeatMode()
                it.shuffleModeEnabled = _state.value.shuffleEnabled
                it.setPlaybackSpeed(_state.value.musicSpeed)
            }
        exo = created
        appContext = context.applicationContext
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

            override fun seekToNext() {
                if (bgMixActive()) bgMix?.next() else super.seekToNext()
            }

            override fun seekToPrevious() {
                if (!bgMixActive()) super.seekToPrevious()
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
    fun toggle(context: Context, item: PlaybackItem, onStarted: (() -> Unit)? = null) =
        startItem(context, item, emptyList(), -1, source = null, onStarted = onStarted)

    /** Запускает [index]-й файл очереди [items]. Когда он доиграет, следующий файл очереди начинает играть сам. Тап по играющему файлу ставит его на паузу. */
    fun playQueue(context: Context, items: List<PlaybackItem>, index: Int, onStarted: (() -> Unit)? = null) {
        val item = items.getOrNull(index) ?: return
        startItem(context, item, items, index, source = null, onStarted = onStarted)
    }

    /**
     * Queue variant with a lazy TDLib source. Tracks that are not downloaded yet stay in the queue and are resolved
     * on demand; only the current item and 1–2 following items may request a download.
     */
    fun playQueue(context: Context, items: List<PlaybackItem>, index: Int, source: PlaybackQueueSource?, onStarted: (() -> Unit)? = null) {
        val item = items.getOrNull(index) ?: return
        startItem(context, item, items, index, source = source, onStarted = onStarted)
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
        onStarted: (() -> Unit)?
    ) {
        if (onStarted != null) startedCallback = item.key to onStarted
        val currentState = _state.value
        if (bgMixActive() && currentState.item?.key == item.key) {
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
            val resumeMs = restore?.positionMs
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
        startSingle(context, item, startPositionMs = null, startFraction = 0f)
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
        autoPlay: Boolean = true
    ) {
        val selected = queue.items.getOrNull(queue.index) ?: return
        if (autoPlay && startPositionMs == null && startFraction <= 0f && bgMixAdmitsSource(source) && startBgMix(context, queue)) return
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
        applyPlayerModes(target, music = resolved.isMusic && !resolved.systemPrivate)
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

    private fun applyPlayerModes(target: ExoPlayer, music: Boolean) {
        val state = _state.value
        target.repeatMode = if (music) state.repeatMode.toPlayerRepeatMode() else Player.REPEAT_MODE_OFF
        target.shuffleModeEnabled = music && state.shuffleEnabled
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
        applyPlayerModes(target, music = item.isMusic && !item.systemPrivate)
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

    /** Account the shared player is bound to. Profile music uses it to hide "add to profile" for another account's tracks. */
    fun activeAccount(): String? = activeAccountId

    /** Explicit user stop: closes the session, clears the queue and removes the notification. */
    fun stop() {
        cancelSleepTimer()
        _queuePageLoading.value = false
        _state.value.item?.accountId?.let { owner ->
            appContext?.let { context -> MusicSessionStore(context).clear(owner) }
        }
        interruptedMusic = null
        queueSource = null
        queuePageGeneration++
        resetErrorPolicy()
        queuePageJob?.cancel()
        queuePageJob = null
        prefetchedKeys.clear()
        haltPlayer()
    }

    /** Alias used by UI that wants an unambiguous "clear everything" action. */
    fun clear() = stop()

    private fun bgMixActive(): Boolean = bgMix?.isActive == true

    /** Placeholder for the ExoPlayer timeline while BG Mix plays: the current file, never played by ExoPlayer. */
    private fun placeholderItem(item: PlaybackItem): MediaItem = MediaItem.Builder()
        .setMediaId(item.key)
        .setUri(Uri.fromFile(File(item.path)))
        .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).setArtist(item.performer).build())
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
    private val bgFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            if (bgMixActive()) pauseBgMix()
        }
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
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
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
     * Starts [queue] through BG Mix when the experimental flag is on and every item is an eligible local file.
     * Returns false without touching the ExoPlayer state, so the caller falls back to the regular path.
     */
    private fun startBgMix(context: Context, queue: PlaybackQueue): Boolean {
        BgMixSettingsStore.load(context)
        if (!BgMixSettingsStore.enabled.value) return false
        // BG Mix does not repeat; a queue with repeat selected stays on ExoPlayer, which does.
        if (_state.value.repeatMode != PlaybackRepeatMode.Off) return false
        // BG Mix plays the queue in order; shuffle stays on ExoPlayer.
        if (_state.value.shuffleEnabled) return false
        if (queue.index !in queue.items.indices) return false
        // Creates the shared player and appContext on first use, so the notification and queue paths work.
        player(context)
        val driver = bgMix ?: BgMixQueueDriver(File(context.filesDir, "bgmix-cache")).also { bgMix = it }
        // Focus comes before the driver starts: the native session plays as soon as it starts, so it must not start
        // without focus. A refused focus keeps the queue on ExoPlayer, which asks for focus in the same way.
        if (!requestBgFocus(context)) return false
        if (!driver.start(queue.items, queue.index)) {
            releaseBgFocus()
            return false
        }
        val item = queue.items[queue.index]
        val before = _state.value
        progress?.cancel()
        progress = null
        exo?.let {
            it.stop()
            it.clearMediaItems()
            // A paused placeholder keeps the session's current item (title, duration) and notification alive.
            it.playWhenReady = false
            it.setMediaItem(placeholderItem(item))
            it.prepare()
        }
        transitionToken++
        clearDeferredSeek()
        pendingStartFraction = 0f
        startedCallback = startedCallback?.takeIf { it.first == item.key }
        _audioSessionId.value = null
        _state.value = stateFor(
            item = item,
            queue = queue.items,
            index = queue.index,
            positionMs = 0L,
            playing = false,
            intentPlaying = true,
            speed = 1f
        )
        _state.update {
            it.copy(timelineHasNext = queue.index < queue.items.lastIndex, timelineHasPrevious = false)
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
        startPlaybackService()
        return true
    }

    /** Copies the native status into the Media state the UI and the notification read. */
    private fun applyBgMix(status: BgMixStatus) {
        val driver = bgMix ?: return
        val before = _state.value
        val playingNativeId = status.queue.firstOrNull { it.state == "playing" }?.id
        val index = playingNativeId?.let { driver.indexOfNative(it) } ?: _state.value.queueIndex
        val item = driver.itemAt(index) ?: return
        val master = status.decks.getOrNull(status.master)
        val deckPlaying = master?.playing == true
        val finished = status.queue.isNotEmpty() &&
            status.queue.all { it.state == "played" || it.state == "failed" } &&
            !status.transitionActive && !deckPlaying
        val playing = !status.paused && deckPlaying && !finished
        val hasNext = status.queue.any { (driver.indexOfNative(it.id) ?: -1) > index }
        _state.update { current ->
            current.copy(
                timelineHasNext = hasNext,
                timelineHasPrevious = false,
                item = item,
                queue = driver.queueItems,
                queueIndex = index,
                playing = playing,
                intentPlaying = !status.paused && !finished,
                positionMs = ((master?.positionSeconds ?: 0.0) * 1000).toLong(),
                durationMs = ((master?.durationSeconds ?: 0.0) * 1000).toLong(),
                started = current.started || playing,
                ended = finished,
                isBuffering = false
            )
        }
        if (before.item?.key != _state.value.item?.key) {
            exo?.let { target ->
                if (target.mediaItemCount > 0) target.replaceMediaItem(0, placeholderItem(item))
            }
        }
        if (finished && !before.ended) releaseBgFocus()
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
        _state.update { it.copy(intentPlaying = true, ended = false) }
        notifyChanges(before, _state.value)
    }

    private fun pauseBgMix() {
        val before = _state.value
        bgMix?.pause()
        _state.update { it.copy(playing = false, intentPlaying = false) }
        notifyChanges(before, _state.value)
    }

    private fun stopBgMix() {
        // Only a session that was running changes what the media session shows; ExoPlayer stops are not affected.
        val wasActive = bgMixActive()
        val before = _state.value
        bgMixJob?.cancel()
        bgMixJob = null
        bgMix?.stop()
        releaseBgFocus()
        if (wasActive && (before.playing || before.intentPlaying)) {
            notifyChanges(before, before.copy(playing = false, intentPlaying = false))
        }
    }

    /** BG Mix lost its output mid-session: the session is released, and the queue continues on ExoPlayer from the last position. */
    private fun failBgMix() {
        val context = appContext ?: return
        if (!bgMixActive()) return
        val snapshot = _state.value
        val queue = PlaybackQueue(snapshot.queue, snapshot.queueIndex)
        stopBgMix()
        // A position is given, so startQueue does not offer the same queue to BG Mix again.
        startQueue(
            context,
            queue,
            queueSource,
            startPositionMs = snapshot.positionMs,
            startFraction = 0f,
            autoPlay = snapshot.intentPlaying
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
            bgMix?.next()
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
            if (context != null && bgMixAdmitsSource(queueSource) && startBgMix(context, queue)) return
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
        if (bgMixActive()) return
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
        runCatching { context.startService(Intent(context, PlaybackService::class.java)) }
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
        target.seekToNextMediaItem()
        target.prepare()
        target.play()
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
        // ExoPlayer already advanced through Repeat/Next when it could; ENDED means the session is finished.
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

    private fun updateAudioSessionId() {
        val current = _state.value.item
        val id = exo?.audioSessionId
        val isMusic = current != null && current.isMusic && !current.systemPrivate
        val musicSession = if (id != null && id > 0 && isMusic) id else null
        if (_audioSessionId.value != musicSession) _audioSessionId.value = musicSession
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
                skipTrailingSilence(target)
                if (!target.isPlaying && target.playbackState != Player.STATE_BUFFERING) break
                delay(PROGRESS_INTERVAL_MS)
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
        val source = queueSource ?: return
        val context = appContext ?: return
        val state = _state.value
        val playbackOrder = if (state.shuffleEnabled && exo != null) {
            runCatching { exo!!.shuffleOrder.toIndexOrder() }.getOrDefault(state.queue.indices.toList())
        } else {
            state.queue.indices.toList()
        }
        prefetchCandidates(state, playbackOrder).forEach { item ->
            val fileId = item.fileId ?: return@forEach
            if (source.accountId != item.accountId) return@forEach
            // Speculative traffic must use the repository automatic path; the manual callback is reserved for
            // the track playback actually opens, so Data Saver/network cancellation can track prefetch downloads.
            if (source.autoDownload == null) return@forEach
            if (item.key in prefetchedKeys) return@forEach
            val fileState = runCatching { source.observeFile(fileId).value }.getOrNull() ?: return@forEach
            if (fileState.localPath != null || fileState.isDownloading) return@forEach
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
        val source = queueSource
        if (source == null || _state.value.queue.isEmpty()) {
            prefetchPolicyJob?.cancel()
            prefetchPolicyJob = null
            prefetchPolicyAccount = null
            return
        }
        val context = appContext ?: return
        if (prefetchPolicyJob?.isActive == true && prefetchPolicyAccount == source.accountId) return
        prefetchPolicyJob?.cancel()
        prefetchPolicyAccount = source.accountId
        AutoDownloadNetworkStatus.ensureStarted(context.applicationContext)
        val settings = AutoDownloadSettingsStore.getInstance(context.applicationContext, source.accountId).settings
        prefetchPolicyJob = scope.launch {
            combine(AutoDownloadNetworkStatus.network, settings) { _, _ -> Unit }.collect {
                prefetchedKeys.clear()
                prefetchNext()
            }
        }
    }
}
