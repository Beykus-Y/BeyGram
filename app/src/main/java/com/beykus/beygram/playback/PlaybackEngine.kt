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
import android.util.Log
import android.view.TextureView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Timeline
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.FlagSet
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
import com.beykus.beygram.data.music.provider.ProviderTracks
import com.beykus.beygram.media.bgmix.BgMixAudible
import com.beykus.beygram.media.bgmix.BgMixNative
import com.beykus.beygram.media.bgmix.BgMixSettingsStore
import com.beykus.beygram.media.bgwave.BgWaveHostTap
import com.beykus.beygram.media.bgwave.BgWaveHub
import com.beykus.beygram.media.bgwave.BgWaveRenderersFactory
import com.beykus.beygram.media.bgmix.BgMixStatus
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
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
@OptIn(UnstableApi::class)
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

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        if (!bgMixOwnsPlayback()) target.onMediaMetadataChanged(mediaMetadata)
    }

    override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
        if (!bgMixOwnsPlayback()) target.onAvailableCommandsChanged(availableCommands)
    }

    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
        if (!bgMixOwnsPlayback()) target.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)
    }

    override fun onIsLoadingChanged(isLoading: Boolean) {
        if (!bgMixOwnsPlayback()) target.onIsLoadingChanged(isLoading)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (!bgMixOwnsPlayback()) {
            target.onEvents(player, events)
        } else if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
            // The placeholder still supplies the Media3 timeline. Do not forward its paused/idle state.
            target.onEvents(player, Player.Events(FlagSet.Builder().add(Player.EVENT_TIMELINE_CHANGED).build()))
        }
    }
}

/**
 * Music that may stream over a BG Wave host tap. Provider (Yandex) tracks are relayed as live sound only: nothing is
 * saved and no file is sent. Restricted catalog tracks are allowed only when they come from a provider. Voice, video
 * notes, secret, view-once and system-private items are never relayed.
 */
internal fun isWaveCaptureEligible(item: PlaybackItem?): Boolean {
    if (item == null) return false
    val isProviderItem = ProviderTracks.isProviderKey(item.key) || ProviderTracks.byKey(item.key) != null
    return item.isMusic &&
        !item.secret &&
        !item.isSecretChat &&
        !item.viewOnce &&
        !item.systemPrivate &&
        (isProviderItem || !item.isRestrictedCatalog)
}

/**
 * Whether the rest of a liked list may join the playing queue. Nothing joins a queue that was started, replaced or
 * stopped after [generation] was read, or a queue whose playing track is not legacy-account music. While BG Mix plays its
 * own session this returns false for the Exo queue; the tracks go to the BG Mix queue through [extendBgMixQueue] instead.
 */
internal fun yandexRestMayJoinQueue(
    generation: Long,
    queueGeneration: Long,
    bgMixActive: Boolean,
    currentIsLegacyMusic: Boolean,
): Boolean = generation == queueGeneration && currentIsLegacyMusic && !bgMixActive

/**
 * A neighbouring queue occurrence in the actual play order. The index identifies the occurrence even when the same
 * track key appears more than once, so the artwork preview and the commit validate the exact same row.
 */
data class AdjacentPlayback(val queueIndex: Int, val item: PlaybackItem)

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
    /** Maximum lookahead from the playing track, independent of native queue growth. */
    private const val BG_MIX_DOWNLOAD_AHEAD = 2
    /** Completed copies kept as a small offline window: N−1 (ownership keeps it one further back) through N+3. */
    private const val MUSIC_RETENTION_AHEAD = 3
    private const val QUEUE_PAGE_TIMEOUT_MS = 20_000L
    private const val BG_MIX_FEED_INTERVAL_MS = 1_000L
    /** A paused BG Mix queue wakes on resume instead of polling the feed every second. */
    private const val BG_MIX_FEED_PAUSED_WAIT_MS = 30_000L
    private const val BG_HANDOVER_POLL_MS = 500L
    /** A state that cannot become ready on its own is re-checked on change, or after this long at the latest. */
    private const val BG_HANDOVER_IDLE_WAIT_MS = 30_000L
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

    private val _manualSkips = MutableStateFlow(0L)

    /**
     * Counts the manual Next and queue-row picks. It moves before the player state changes, so a listener who leaves a
     * track on purpose can be told apart from a track that ended (the listening feedback reads it).
     */
    val manualSkips: StateFlow<Long> = _manualSkips.asStateFlow()

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
        if (!(_state.value.playing || _state.value.intentPlaying)) {
            pause()
            return
        }
        val steps = (fadeMs / SLEEP_FADE_STEP_MS).toInt().coerceAtLeast(1)
        try {
            for (step in 1..steps) {
                sleepGain = 1f - step.toFloat() / steps
                applyMusicGain()
                delay(SLEEP_FADE_STEP_MS)
            }
            pause()
        } finally {
            sleepGain = 1f
            applyMusicGain()
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
    private var queuePageNetworkJob: Job? = null
    private var queuePageRetries = 0
    /** True when an endless radio reached the end of loaded tracks while the user intended to keep playing. */
    private var endlessWaitingForRecovery = false
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
    /** Deferred queue edit while a native crossfade is running, applied as soon as the transition finishes. */
    private var pendingBgMixRetarget: Pair<List<PlaybackItem>, PlaybackQueueSource?>? = null
    /** Listeners the media session registered on the wrapper; BG Mix events go to them directly. */
    private val bgMixListeners = mutableSetOf<Player.Listener>()
    /** Session listener -> the copy that drops ExoPlayer events while BG Mix plays. */
    private val bgMixFiltered = mutableMapOf<Player.Listener, Player.Listener>()
    private var bgNoisyRegistered = false
    private var bgMixJob: Job? = null
    private var bgMixSeekJob: Job? = null
    /** Queue order from before BG Mix shuffled it; null while shuffle is off. */
    private var bgMixOriginalQueue: List<PlaybackItem>? = null
    /** Brings a queue that ExoPlayer plays into BG Mix once the playing track is a finished file; see [scheduleBgMixHandover]. */
    private var bgMixHandoverJob: Job? = null
    /** Counts the handover cancellations: a crossfade that started under an older count has been superseded. */
    private var bgMixHandoverGeneration = 0
    /** True from the native commit until the handover fade ends: the ordinary crossfade has no ownership then. */
    private var bgMixHandoverFading = false
    /** Adds tracks that finished downloading to the running BG Mix session and asks for the next ones; see [startBgMixFeed]. */
    private var bgMixFeedJob: Job? = null
    /** Latest active BG Wave native session handle from [BgWaveHub], kept so new BG Mix players attach at once. */
    @Volatile
    private var lastWaveSessionHandle: Long = 0L

    init {
        BgWaveHub.hostTap = ::attachWaveTap
    }

    private fun attachWaveTap(sessionHandle: Long, attach: Boolean): Boolean {
        lastWaveSessionHandle = if (attach && sessionHandle != 0L) sessionHandle else 0L
        val driver = bgMix ?: return false
        if (!attach || sessionHandle == 0L || !BgWaveHostTap.captureEligible || !driver.isActive) {
            driver.attachWaveTap(0L)
            return false
        }
        return driver.attachWaveTap(sessionHandle)
    }

    private fun syncBgMixWaveTap(driver: BgMixQueueDriver? = bgMix) {
        if (driver == null || !driver.isActive) return
        val handle = if (BgWaveHostTap.captureEligible) lastWaveSessionHandle else 0L
        driver.attachWaveTap(handle)
    }

    private fun updateWaveCaptureEligibility(item: PlaybackItem?) {
        val eligible = isWaveCaptureEligible(item)
        BgWaveHostTap.setCaptureEligible(eligible)
        if (!eligible) {
            bgMix?.attachWaveTap(0L)
        } else if (bgMixActive() && lastWaveSessionHandle != 0L) {
            bgMix?.attachWaveTap(lastWaveSessionHandle)
        }
    }

    /** Key of the track that was moved past early for trailing silence. Cleared by the next media item transition. */
    private var trailingSkippedKey: String? = null

    /** Overlaps the end of a music track with the start of the next one, when the crossfade setting is on. */
    private val musicEnvelopes = java.util.WeakHashMap<ExoPlayer, Float>()
    private var coexistenceGain = 1f
    private var sleepGain = 1f
    private var systemFocusGain = 1f
    private var coexistenceTarget = 1f
    private var gainRamp: Job? = null
    private var preferencesJob: Job? = null
    private var coexistenceContext: Context? = null
    private var actionFocus: AudioFocusRequest? = null
    private var focusGain = 0
    private var focusGranted = false
    private var musicWantsFocus = false
    private var focusSuspended = false
    private var resumeMusicOnFocus = false
    private var resumeSpeechOnFocus: String? = null
    private var actionFocusGeneration = 0L
    private val recordingOwners = mutableMapOf<Any, CoexistenceAction>()
    private var speechAction: CoexistenceAction? = null
    private val coexistencePauseOwner = Any()
    private val speech = SpeechPlayback(scope, canPlay = { !focusSuspended && (focusGain == 0 || focusGranted) }) { item ->
        speechAction = when (item?.kind) {
            MediaKind.Voice -> CoexistenceAction.PlayVoice
            MediaKind.VideoNote -> CoexistenceAction.PlayVideoNote
            else -> null
        }
        refreshCoexistence()
    }
    val speechState = speech.state

    private fun ensureCoexistence(context: Context) {
        coexistenceContext = context.applicationContext
        AudioCoexistenceSettings.load(context)
        if (preferencesJob != null) return
        preferencesJob = scope.launch {
            AudioCoexistenceSettings.state.collect {
                applySpeechGain()
                refreshCoexistence()
                applyMusicGain()
            }
        }
    }

    private fun refreshCoexistence() {
        val preferences = AudioCoexistenceSettings.state.value
        val policies = (recordingOwners.values + listOfNotNull(speechAction)).map(preferences::policy)
        if (policies.any { it.music == MusicInterference.Pause }) beginInterruption(coexistencePauseOwner)
        else endInterruption(coexistencePauseOwner)
        val external = policies.maxOfOrNull { it.external } ?: ExternalAudioInterference.Continue
        reconcileFocus(external)
        val target = coexistenceAttenuation(policies)
        if (target == coexistenceTarget) return
        coexistenceTarget = target
        gainRamp?.cancel()
        val from = coexistenceGain
        val steps = if (target < from) 10 else 16
        gainRamp = scope.launch {
            for (step in 1..steps) {
                delay(20)
                coexistenceGain = from + (target - from) * step / steps
                applyMusicGain()
            }
        }
    }

    internal fun updateRecording(context: Context, owner: Any, action: CoexistenceAction?) {
        ensureCoexistence(context)
        if (action == null) recordingOwners.remove(owner)
        else {
            recordingOwners[owner] = action
            if (speechState.value.intentPlaying) speechState.value.item?.key?.let(speech::toggle)
        }
        refreshCoexistence()
    }

    private fun musicGain(envelope: Float): Float = musicOutputGain(
        base = AudioCoexistenceSettings.state.value.musicPercent / 100f,
        coexistence = coexistenceGain,
        focus = systemFocusGain,
        sleep = sleepGain,
        envelope = envelope,
    )

    private var bgEnvelope = 1f

    private fun applySpeechGain() {
        speech.setGain(AudioCoexistenceSettings.state.value.speechPercent / 100f * systemFocusGain)
    }

    private fun applyMusicGain() {
        for ((renderer, envelope) in musicEnvelopes) renderer.volume = musicGain(envelope)
        bgMix?.let(::applyBgGain)
    }

    private fun applyBgGain(driver: BgMixQueueDriver) {
        driver.setVolume(musicGain(bgEnvelope))
    }

    private fun setMusicEnvelope(renderer: ExoPlayer, envelope: Float) {
        musicEnvelopes[renderer] = envelope
        renderer.volume = musicGain(envelope)
    }

    private fun startSpeech(
        context: Context,
        item: PlaybackItem,
        positionMs: Long,
        onStarted: (() -> Unit)?,
        preview: Boolean = false,
    ) {
        ensureCoexistence(context)
        if (activeAccountId != null && item.accountId != activeAccountId) return
        if (recordingOwners.isNotEmpty()) return
        resumeSpeechOnFocus = null
        val media = mediaItemFor(item)
        speech.start(context, item, media, positionMs, if (preview) 1f else _state.value.voiceSpeed, onStarted, preview) {
            PlaybackSources.register(item.systemMediaId, PlaybackSourceHandle(item, null))
        }
        applySpeechGain()
    }

    internal fun toggleSpeechPreview(context: Context, key: String, kind: MediaKind, path: String, seconds: Int) {
        val item = PlaybackItem(
            key = key,
            kind = kind,
            path = path,
            title = "",
            durationSeconds = seconds,
            accountId = activeAccountId ?: LEGACY_PLAYBACK_ACCOUNT_ID,
            secret = true,
        )
        if (!toggleSpeech(key)) startSpeech(context, item, 0L, null, preview = true)
    }
    internal fun toggleSpeech(key: String): Boolean {
        if (recordingOwners.isNotEmpty()) return false
        resumeSpeechOnFocus = null
        return speech.toggle(key)
    }
    fun onAppLocked() {
        resumeSpeechOnFocus = null
        speech.pause()
        stopSecret()
    }

    internal fun seekSpeech(key: String, positionMs: Long) = speech.seek(key, positionMs)
    internal fun attachSpeechVideo(key: String, owner: Any, view: TextureView) = speech.attach(key, owner, view)
    internal fun detachSpeechVideo(owner: Any) = speech.detach(owner)
    internal fun stopPrivateSpeech(key: String) {
        if (speechState.value.item?.let { it.key == key && it.systemPrivate } == true) speech.stop()
    }

    private fun PlaybackItem.isSpeech(): Boolean = kind == MediaKind.Voice || kind == MediaKind.VideoNote

    private val crossfader = TrackCrossfader(
        scope = scope,
        enabled = { TrackTransitionSettings.crossfade.value },
        blocked = { crossfadeOwnershipBlock(bgMixActive(), bgMixHandoverFading, deferredSeek != null) },
        newPlayer = { newCrossfadePlayer() },
        trailingMs = { mediaId -> crossfadeTrailingMs(mediaId) },
        nextAfter = { primary, mediaId -> crossfadeNextAfter(primary, mediaId) },
        overlapMs = { TrackTransitionSettings.crossfadeSeconds.value * 1_000L },
        setEnvelope = ::setMusicEnvelope,
        releaseTail = {
            musicEnvelopes.remove(it)
            it.release()
        },
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
        if (bgMixActive()) notifyChanges(snapshot, _state.value)

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
            if (wasPlaying) playPrimary(target)
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
        ensureCoexistence(context)
        val created = ExoPlayer.Builder(context.applicationContext)
            .setRenderersFactory(BgWaveRenderersFactory(context.applicationContext))
            .setMediaSourceFactory(DefaultMediaSourceFactory(PlaybackDataSourceFactory(context.applicationContext)))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ false
            )
            .setHandleAudioBecomingNoisy(true)
            // Keeps the CPU and Wi-Fi awake while a track plays or loads, so a locked screen cannot stall the queue.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also {
                setMusicEnvelope(it, 1f)
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
                if (requestPrimaryFocus()) super.play()
                prefetchNext()
            }

            override fun pause() {
                invalidateInterruptionResume()
                if (bgMixActive()) {
                    pauseBgMix()
                    releasePrimaryFocus()
                    return
                }
                // System/lock-screen/headset pause must cancel a pending deferred resume as well.
                deferredSeek = deferredSeek?.copy(resume = false)
                _state.update { it.copy(intentPlaying = false, playing = false) }
                progress?.cancel()
                progress = null
                super.pause()
                releasePrimaryFocus()
            }

            override fun setPlayWhenReady(playWhenReady: Boolean) {
                if (playWhenReady) play() else pause()
            }

            override fun getAvailableCommands(): Player.Commands {
                val base = super.getAvailableCommands()
                if (!bgMixActive()) return base
                return nativeSessionCommands(base, _state.value)
            }

            override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

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

            override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
                if (bgMixActive()) {
                    // The native renderer exposes only the current placeholder in the Media3 timeline.
                    if (mediaItemIndex == 0) seekToPosition(if (positionMs == C.TIME_UNSET) 0L else positionMs)
                } else super.seekTo(mediaItemIndex, positionMs)
            }

            override fun seekToDefaultPosition() {
                if (bgMixActive()) seekToPosition(0L) else super.seekToDefaultPosition()
            }

            override fun seekToDefaultPosition(mediaItemIndex: Int) {
                if (bgMixActive()) {
                    if (mediaItemIndex == 0) seekToPosition(0L)
                } else super.seekToDefaultPosition(mediaItemIndex)
            }

            override fun seekBack() {
                if (bgMixActive()) seekToPosition((currentPosition - seekBackIncrement).coerceAtLeast(0L)) else super.seekBack()
            }

            override fun seekForward() {
                if (bgMixActive()) seekToPosition((currentPosition + seekForwardIncrement).coerceAtMost(duration.takeIf { it > 0 } ?: Long.MAX_VALUE)) else super.seekForward()
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
                else -> nativeSessionPlaybackState(_state.value)
            }

            override fun getPlaybackSuppressionReason(): Int =
                if (bgMixActive()) Player.PLAYBACK_SUPPRESSION_REASON_NONE else super.getPlaybackSuppressionReason()

            override fun getMediaMetadata(): MediaMetadata =
                if (bgMixActive()) {
                    val snapshot = _state.value
                    val metadata = snapshot.item?.let { placeholderItem(it).mediaMetadata } ?: MediaMetadata.EMPTY
                    if (snapshot.durationMs > 0L && snapshot.item?.systemPrivate == false) {
                        metadata.buildUpon().setDurationMs(snapshot.durationMs).build()
                    } else metadata
                } else super.getMediaMetadata()

            override fun isLoading(): Boolean = if (bgMixActive()) _state.value.isBuffering else super.isLoading()

            override fun getBufferedPosition(): Long =
                if (bgMixActive()) _state.value.let { if (it.isBuffering) it.positionMs else maxOf(it.positionMs, it.durationMs) }
                else super.getBufferedPosition()

            override fun getContentBufferedPosition(): Long =
                if (bgMixActive()) bufferedPosition else super.getContentBufferedPosition()

            override fun getTotalBufferedDuration(): Long =
                if (bgMixActive()) (bufferedPosition - currentPosition).coerceAtLeast(0L) else super.getTotalBufferedDuration()

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
    /** Whether the playing queue is endless (a radio such as «Моя волна»); its loader keeps it going. */
    fun currentQueueIsEndless(): Boolean = queueSource?.loadMore != null

    /**
     * Replaces the tracks after the playing one with [tracks], and makes [loadMore] the loader of this queue. The playing
     * track keeps playing from its position. Used when a radio's settings change; does nothing without a playing item.
     */
    fun replaceYandexUpcoming(
        context: Context,
        tracks: List<com.beykus.beygram.data.music.provider.ProviderTrack>,
        radio: PlaybackRadio,
        loadMore: suspend () -> List<com.beykus.beygram.data.music.provider.ProviderTrack>,
    ) {
        val playable = tracks.filter { it.isAvailable }
        if (playable.isEmpty() || _state.value.item == null) return
        clearUpcoming()
        // clearUpcoming drops the loader; this queue gets the new one, so the radio keeps going under the same radio.
        queueSource = PlaybackQueueSource(
            accountId = LEGACY_PLAYBACK_ACCOUNT_ID,
            observeFile = { fileId -> MutableStateFlow(MediaFileState(id = fileId)) },
            download = {},
            loadMore = { loadMore().filter { it.isAvailable }.map(::providerPlaybackItem) },
            radio = radio,
        )
        playable.forEach { track -> enqueue(context, providerPlaybackItem(track), source = null, atEnd = true) }
    }

    /**
     * Plays source [tracks] from [startIndex]. [loadMore] makes the queue endless (a radio such as «Моя волна»): the
     * engine asks it for the next tracks when few are left, as it does for Telegram history pages.
     */
    fun playYandexTracks(
        context: Context,
        tracks: List<com.beykus.beygram.data.music.provider.ProviderTrack>,
        startIndex: Int,
        loadMore: (suspend () -> List<com.beykus.beygram.data.music.provider.ProviderTrack>)? = null,
        radio: PlaybackRadio? = null,
        /** Set when the first track is the one already playing: the queue takes over at this position without restarting it. */
        keepPositionMs: Long? = null,
    ) {
        val playable = tracks.filter { it.isAvailable }
        if (playable.isEmpty()) return
        val startTrackId = tracks.getOrNull(startIndex)?.trackId
        val index = playable.indexOfFirst { it.trackId == startTrackId }.coerceAtLeast(0)
        val items = playable.drop(index).map(::providerPlaybackItem)
        val source = loadMore?.let { load ->
            PlaybackQueueSource(
                accountId = LEGACY_PLAYBACK_ACCOUNT_ID,
                // Source rows carry no TDLib file, so these are never asked for.
                observeFile = { fileId -> MutableStateFlow(MediaFileState(id = fileId)) },
                download = {},
                loadMore = { load().filter { it.isAvailable }.map(::providerPlaybackItem) },
                radio = radio
            )
        }
        playQueue(context, items, 0, source = source, startPositionMs = keepPositionMs)
    }

    /**
     * Restores a paused «Моя волна» after a restart: [track] is current at [positionMs]. Nothing plays, downloads or asks
     * the source until Play; [loadMore] continues the wave when the queue runs low.
     */
    fun restoreYandexRadio(
        context: Context,
        track: com.beykus.beygram.data.music.provider.ProviderTrack,
        positionMs: Long,
        loadMore: suspend () -> List<com.beykus.beygram.data.music.provider.ProviderTrack>,
        radio: PlaybackRadio,
    ) {
        if (_state.value.item != null || !track.isAvailable) return
        YandexTrackResolver.ensureConfigured(context)
        val source = PlaybackQueueSource(
            accountId = LEGACY_PLAYBACK_ACCOUNT_ID,
            observeFile = { fileId -> MutableStateFlow(MediaFileState(id = fileId)) },
            download = {},
            loadMore = { loadMore().filter { it.isAvailable }.map(::providerPlaybackItem) },
            radio = radio,
        )
        val queue = queueForPlayback(listOf(providerPlaybackItem(track)), 0)
        // Shown paused without preparing the player: a prepared player would request the file at once. Play starts it for real.
        val appContext = context.applicationContext
        restoredRadioStart = { startQueue(appContext, queue, source, startPositionMs = positionMs, startFraction = 0f, autoPlay = true) }
        startQueue(context, queue, source, startPositionMs = positionMs, startFraction = 0f, autoPlay = false)
    }

    /** The first Play of a restored «Моя волна»: starts the stored queue for real (file request, prepare, play). */
    private var restoredRadioStart: (() -> Unit)? = null

    private fun providerPlaybackItem(track: com.beykus.beygram.data.music.provider.ProviderTrack): PlaybackItem =
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
        if (item.isSpeech()) {
            if (speechState.value.item?.key == item.key && startPositionMs != null) {
                speech.seek(item.key, startPositionMs)
            }
            if (!toggleSpeech(item.key)) startSpeech(context, item, startPositionMs ?: 0L, onStarted)
            return
        }
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
            updateWaveCaptureEligibility(merged)
            _state.update { it.copy(item = merged, queue = nextItems, queueIndex = nextIndex).withTimelineNavigation(exo) }
            if (timelineChanged(currentState, nextItems, nextIndex)) {
                // A queue that replaces the playing one is a new queue: the old one's page load and extensions stop here.
                dropQueuePages()
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
        speech.setForeground(active)
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
        if (previous != null && previous != accountId) {
            recordingOwners.clear()
            resumeSpeechOnFocus = null
            interruption = null
            refreshCoexistence()
        }
        if (speechState.value.item?.accountId?.let { it != accountId } == true) {
            interruption = null
            speech.stop()
        }
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
        if (item.isSpeech()) {
            startSpeech(context, item, startPositionMs ?: (startFraction * item.durationSeconds * 1000).toLong(), onStarted)
            return
        }
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
        restoredRadioStart = null
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
        finishClosedPlayer()
        queuePageJob?.cancel()
        queuePageJob = null
        _queuePageLoading.value = false
        prefetchedKeys.clear()

        PlaybackSources.clearExcept(speechState.value.item?.systemMediaId)
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
        updateWaveCaptureEligibility(item)
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
        // The new queue is the published ownership now: only then may the old copies outside it be dropped. Evicting
        // before the state change let a prune read the previous queue and keep or delete the wrong files.
        if (YandexTrackResolver.evictOutside(queue.items.mapTo(HashSet()) { it.key }, includePlayback = true)) {
            appContext?.let { YandexTrackResolver.pruneUnused(it) }
        }
        // A new queue replaces the playback window at once: abandoned fetches must not hold the slots of its rows.
        reprioritizeLazyRows()
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
        playPrimary(target)
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
        finishClosedPlayer()
        queuePageJob?.cancel()
        queuePageJob = null
        _queuePageLoading.value = false
        syncPosition()
        clearDeferredSeek()
        pendingStartFraction = 0f
        startedCallback = startedCallback?.takeIf { it.first == item.key }
        prefetchedKeys.clear()

        PlaybackSources.clearExcept(speechState.value.item?.systemMediaId)
        registerSource(item, queueSource)
        val resolved = resolvedItem(item)
        val requestedStart = (startPositionMs ?: 0L).coerceAtLeast(0L)
        val defer = shouldDeferGrowingSeek(resolved.growing, requestedStart)
        target.setMediaItems(listOf(mediaItemFor(resolved)), 0, if (defer) 0L else requestedStart)
        applyPlayerModes(target, music = resolved.isMusic && !resolved.systemPrivate, speech = resolved.kind != MediaKind.Audio)
        applySpeed(target, resolved)
        updateWaveCaptureEligibility(resolved)
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
        // A single file has no queue: only its own fetch is wanted, abandoned requests free their slots.
        LazyTrackRegistry.prioritize(LazyFetchWindow.of(resolved.key))
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
        playPrimary(target)
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
        // The current provider row has its file now, so the rows after it may start downloading (see prefetchNext).
        if (_state.value.item?.key == resolved.key && LazyTrackRegistry.isLazyKey(resolved.key)) prefetchNext()
    }

    /** A provider row whose download finished while it played from the growing copy: the same row, as a complete file. */
    private fun settledProviderItem(item: PlaybackItem): PlaybackItem? {
        if (item.growing == null) return null
        val path = LazyTrackRegistry.sourceFor(item.key)?.resolvedPath(item.key) ?: return null
        return item.copy(path = path, growing = null)
    }

    /**
     * A growing track whose download has finished becomes a complete file. Until this runs, the track keeps its
     * "still loading" flag, so BG Beat says so and the silence and crossfade rules never measure it.
     */
    private fun settleFinishedDownload() {
        val item = _state.value.item ?: return
        // A provider row settles here too, whatever its length is: its download completed, so the row is a file again.
        // Without this, a row whose length is not advertised would keep the rows after it waiting forever.
        val settled = settledGrowingItem(item) ?: settledProviderItem(item) ?: return
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
        val resolved = handle.resolve() ?: run {
            val stale = handle.item
            if (stale.path.isNotBlank() && stale.growing == null && !java.io.File(stale.path).isFile) {
                val cleared = stale.copy(path = "")
                handle.item = cleared
                return cleared
            }
            return stale
        }
        val updated = handle.item.copy(path = resolved.path, growing = resolved.growing)
        handle.item = updated
        return updated
    }

    @OptIn(UnstableApi::class)
    private fun mediaItemFor(item: PlaybackItem): MediaItem {
        val metadata = item.systemMetadata()
        val mediaId = item.systemMediaId
        // A path is not proof of a copy: a removed cache file or a provider row must still go through the source
        // handle, which re-resolves (and re-fetches) instead of opening a dead URI.
        val needsSource = item.fileId != null || item.growing != null ||
            LazyTrackRegistry.isLazyKey(item.key) || !item.hasReadyLocalCopy ||
            BgVideoNoteFile.eligible(item)
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
            /* handleAudioFocus = */ false
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
            val driver = bgMix ?: return
            bgMixSeekJob?.cancel()
            val session = driver.status
            val before = _state.value
            bgMixSeekJob = scope.launch {
                if (bgMix?.status === session && driver.seek(positionMs.coerceAtLeast(0L) / 1000.0)) {
                    publishConfirmedNativeSeek(session, before, positionMs)
                }
            }
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
        PlaybackSources.clearExcept(speechState.value.item?.systemMediaId)
        registerSource(completed, queueSource)
        target.setMediaItems(listOf(mediaItemFor(completed)), 0, positionMs)
        applyPlayerModes(target, music = completed.isMusic)
        applySpeed(target, completed)
        target.prepare()
        if (resume) {
            playPrimary(target)
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
        PlaybackSources.clearExcept(speechState.value.item?.systemMediaId)
        queue.forEach { registerSource(it, queueSource) }
        val item = resolvedItem(queue[index])
        val resolvedQueue = queue.map { if (it.key == item.key) item else it }
        val requestedStart = positionMs.coerceAtLeast(0L)
        val defer = shouldDeferGrowingSeek(item.growing, requestedStart)
        target.setMediaItems(resolvedQueue.map(::mediaItemFor), index, if (defer) 0L else requestedStart)
        installShuffleOrder(target, resolvedQueue.size, playbackOrder, index)
        applyPlayerModes(target, music = item.isMusic && !item.systemPrivate, speech = item.kind != MediaKind.Audio)
        applySpeed(target, item)
        updateWaveCaptureEligibility(item)
        target.prepare()
        if (defer) {
            target.pause()
            deferredSeek = DeferredGrowingSeek(item.key, requestedStart, resume = play)
            startDeferredSeekWatcher()
        } else if (play) {
            playPrimary(target)
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
        restoredRadioStart?.let { start ->
            restoredRadioStart = null
            start()
            return
        }
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
        restoredRadioStart?.let { start ->
            restoredRadioStart = null
            start()
            return
        }
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
            if (currentQueueIsEndless()) {
                triggerManualEndlessRecovery(startServiceIfNeeded = true)
                return
            }
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
        invalidateInterruptionResume()
        pauseMusicForInterruption()
        releasePrimaryFocus()
    }

    private fun pauseMusicForInterruption() {
        endlessWaitingForRecovery = false
        endlessRecovery = null
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
        var wasPlaying: Boolean,
        val startedAt: Long,
        var itemKey: String?
    )

    private var interruption: Interruption? = null

    private fun invalidateInterruptionResume() {
        interruption?.wasPlaying = false
    }

    /**
     * A video, a story with sound or a recording needs the speakers or the microphone for a while. Music that was
     * playing is paused and remembered; [endInterruption] by the last owner continues it, unless the listener took
     * over in the meantime (another track, play pressed) or the break was longer than [INTERRUPTION_RESUME_LIMIT_MS].
     */
    fun beginInterruption(owner: Any) {
        val now = SystemClock.elapsedRealtime()
        val existing = interruption
        if (existing != null &&
            (coexistencePauseOwner in existing.owners || now - existing.startedAt < INTERRUPTION_STALE_MS)
        ) {
            existing.owners += owner
            return
        }
        val state = _state.value
        val item = state.item
        val music = item != null && item.isMusic && !item.systemPrivate
        val wasPlaying = music && (state.playing || state.intentPlaying) && !state.waitingForNetwork
        interruption = Interruption(mutableSetOf(owner), wasPlaying, now, item?.key)
        if (wasPlaying) pauseMusicForInterruption()
    }

    fun endInterruption(owner: Any) {
        val current = interruption ?: return
        if (!current.owners.remove(owner) || current.owners.isNotEmpty()) return
        interruption = null
        if (!current.wasPlaying) return
        val state = _state.value
        val longBreak = SystemClock.elapsedRealtime() - current.startedAt > INTERRUPTION_RESUME_LIMIT_MS
        if ((longBreak && owner !== coexistencePauseOwner) || state.item?.key != current.itemKey || state.intentPlaying || state.playing) return
        if (bgMixActive()) {
            bgMixPlay()
            return
        }
        val target = exo ?: return
        // The music returns with a short fade-in, so it does not hit the ears at full volume.
        val original = musicEnvelopes[target] ?: 1f
        setMusicEnvelope(target, 0f)
        controls?.play()
        scope.launch {
            try {
                val steps = INTERRUPTION_FADE_IN_MS / SLEEP_FADE_STEP_MS
                for (step in 1..steps) {
                    setMusicEnvelope(target, original * step.toFloat() / steps)
                    delay(SLEEP_FADE_STEP_MS)
                }
            } finally {
                setMusicEnvelope(target, original)
            }
        }
    }

    private fun reconcileFocus(
        mode: ExternalAudioInterference = (recordingOwners.values + listOfNotNull(speechAction))
            .maxOfOrNull { AudioCoexistenceSettings.state.value.policy(it).external }
            ?: ExternalAudioInterference.Continue,
    ): Boolean {
        val audio = coexistenceContext?.getSystemService(AudioManager::class.java) ?: return false
        val gain = when {
            musicWantsFocus -> AudioManager.AUDIOFOCUS_GAIN
            mode == ExternalAudioInterference.Pause -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            mode == ExternalAudioInterference.Duck -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            else -> 0
        }
        if (focusSuspended) {
            if (resumeMusicOnFocus || resumeSpeechOnFocus != null || gain != 0) return false
            focusSuspended = false
        }
        if (gain == focusGain && (gain == 0 || focusGranted)) return true
        val generation = ++actionFocusGeneration
        actionFocus?.let(audio::abandonAudioFocusRequest)
        actionFocus = null
        focusGain = gain
        focusGranted = false
        systemFocusGain = 1f
        applyMusicGain()
        applySpeechGain()
        if (gain == 0) return true
        val request = AudioFocusRequest.Builder(gain)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(
                        if (musicWantsFocus) android.media.AudioAttributes.CONTENT_TYPE_MUSIC
                        else android.media.AudioAttributes.CONTENT_TYPE_SPEECH
                    )
                    .build()
            )
            .setOnAudioFocusChangeListener({ change ->
                if (generation == actionFocusGeneration) onAudioFocusChanged(change)
            }, android.os.Handler(android.os.Looper.getMainLooper()))
            .build()
        focusGranted = audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (focusGranted) actionFocus = request
        return focusGranted
    }

    private fun onAudioFocusChanged(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusGranted = true
                focusSuspended = false
                systemFocusGain = 1f
                applyMusicGain()
                applySpeechGain()
                val restoreMusic = resumeMusicOnFocus
                val restoreSpeech = resumeSpeechOnFocus
                resumeMusicOnFocus = false
                resumeSpeechOnFocus = null
                if (restoreSpeech != null && recordingOwners.isEmpty()) speech.toggle(restoreSpeech)
                if (restoreMusic) {
                    if (bgMixActive()) bgMixPlay() else controls?.play()
                }
                reconcileFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // On Android versions that apply automatic ducking this callback is suppressed.
                systemFocusGain = BG_DUCK_VOLUME
                applyMusicGain()
                applySpeechGain()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS -> {
                val transient = change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                focusGranted = false
                focusSuspended = true
                resumeMusicOnFocus = transient && (musicWantsFocus || interruption?.wasPlaying == true)
                resumeSpeechOnFocus = speechState.value.item?.key?.takeIf {
                    transient && speechState.value.intentPlaying
                }
                invalidateInterruptionResume()
                musicWantsFocus = false
                if (bgMixHandoverFading) {
                    stopBgMix()
                    exo?.let { setMusicEnvelope(it, 1f) }
                }
                pauseMusicForInterruption()
                speech.pause()
                if (!transient) {
                    actionFocusGeneration++
                    actionFocus?.let { request ->
                        coexistenceContext?.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(request)
                    }
                    actionFocus = null
                    focusGain = 0
                    focusSuspended = false
                }
            }
        }
    }

    private fun requestPrimaryFocus(): Boolean {
        val blocked = interruption?.takeIf { coexistencePauseOwner in it.owners }
        if (blocked != null) {
            blocked.wasPlaying = true
            blocked.itemKey = _state.value.item?.key
            _state.update { it.copy(playing = false, intentPlaying = false) }
            return false
        }
        musicWantsFocus = true
        if (reconcileFocus()) return true
        musicWantsFocus = false
        _state.update { it.copy(playing = false, intentPlaying = false) }
        return false
    }

    private fun playPrimary(target: ExoPlayer) {
        if (requestPrimaryFocus()) target.play()
    }

    private fun releasePrimaryFocus() {
        musicWantsFocus = false
        resumeMusicOnFocus = false
        reconcileFocus()
    }
    /** Account the shared player is bound to. Profile music uses it to hide "add to profile" for another account's tracks. */
    fun activeAccount(): String? = activeAccountId

    fun stopAll() {
        recordingOwners.clear()
        resumeSpeechOnFocus = null
        resumeMusicOnFocus = false
        interruption = null
        speech.stop()
        stop()
    }

    /** Explicit user stop: closes the session, clears the queue and removes the notification. */
    fun stop() {
        _state.value.item?.accountId?.let { owner -> persistence?.forget(owner) }
        finishClosedPlayer()
        releaseQueueAndHalt()
    }

    private fun releaseQueueAndHalt() {
        restoredRadioStart = null
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
        YandexTrackResolver.evictOutside(emptySet(), includePlayback = true)
        appContext?.let { YandexTrackResolver.pruneUnused(it) }
    }

    /** Any new playback or an explicit stop ends the chance to undo the last close. */
    private fun dropClosedPlayer() {
        closedPlayer = null
        undoJob?.cancel()
        _undoClose.value = false
    }

    /**
     * New playback or an explicit stop ends the undo for good: the closed music session is forgotten now, so it cannot
     * come back at the next start. Undo itself clears [closedPlayer] first and keeps the session.
     */
    private fun finishClosedPlayer() {
        closedPlayer?.state?.item?.accountId?.let { owner -> persistence?.forget(owner) }
        dropClosedPlayer()
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
        val owner = item.accountId
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
            MediaMetadata.Builder().apply {
                val metadata = item.systemMetadata()
                if (metadata.title.isNotBlank()) setTitle(metadata.title)
                if (metadata.artist.isNotBlank()) setArtist(metadata.artist)
                if (metadata.album.isNotBlank()) setAlbumTitle(metadata.album)
                if (metadata.durationMs > 0L) setDurationMs(metadata.durationMs)
                metadata.artworkPath?.let { artworkUri(it) }?.let { setArtworkUri(it) }
            }.build()
        )
        .build()

    /** Tells the media session which of its player values changed. ExoPlayer events are ignored during BG Mix. */
    @OptIn(UnstableApi::class)
    private fun notifyChanges(before: PlaybackState, after: PlaybackState) {
        val player = controls ?: return
        // Once native ownership is gone, Exo's real callbacks resume. Do not publish a fake
        // native pause whose getters already refer to the other renderer.
        if (bgMixListeners.isEmpty() || !bgMixActive()) return
        val flags = nativeSessionEventFlags(before, after)
        if (flags.isEmpty()) return
        val builder = FlagSet.Builder()
        flags.forEach { builder.add(it) }
        val events = Player.Events(builder.build())
        bgMixListeners.toList().forEach { listener ->
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) listener.onPlaybackStateChanged(player.playbackState)
            if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
                listener.onPlayWhenReadyChanged(player.playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            }
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) listener.onIsPlayingChanged(player.isPlaying)
            if (events.contains(Player.EVENT_IS_LOADING_CHANGED)) listener.onIsLoadingChanged(player.isLoading)
            if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) {
                listener.onPositionDiscontinuity(nativePositionInfo(before), nativePositionInfo(after), Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
            }
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                listener.onMediaItemTransition(after.item?.let { placeholderItem(it) }, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
            }
            if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) listener.onMediaMetadataChanged(player.mediaMetadata)
            if (events.contains(Player.EVENT_AVAILABLE_COMMANDS_CHANGED)) listener.onAvailableCommandsChanged(player.availableCommands)
            if (events.contains(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED)) {
                listener.onPlaybackSuppressionReasonChanged(player.playbackSuppressionReason)
            }
            listener.onEvents(player, events)
        }
    }

    @OptIn(UnstableApi::class)
    private fun nativePositionInfo(snapshot: PlaybackState): Player.PositionInfo = Player.PositionInfo(
        null, 0, snapshot.item?.let { placeholderItem(it) }, null, 0,
        snapshot.positionMs, snapshot.positionMs, C.INDEX_UNSET, C.INDEX_UNSET
    )

    /** Only report a seek after native acceptance and a matching current-session position snapshot. */
    @OptIn(UnstableApi::class)
    private suspend fun publishConfirmedNativeSeek(
        session: StateFlow<BgMixStatus?>?,
        before: PlaybackState,
        requestedMs: Long
    ) {
        if (session == null || bgMix?.status !== session || !bgMixActive()) return
        val target = requestedMs.coerceIn(0L, before.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE)
        val after = withTimeoutOrNull(1_500L) {
            _state.first { current ->
                !current.bgMixActive || !sameNativeSessionOccurrence(before, current) ||
                    kotlin.math.abs(current.positionMs - target) <= 500L
            }
        } ?: return
        if (bgMix?.status !== session || !bgMixActive() || !after.bgMixActive ||
            !sameNativeSessionOccurrence(before, after)
        ) return
        val player = controls ?: return
        val events = Player.Events(FlagSet.Builder().add(Player.EVENT_POSITION_DISCONTINUITY).build())
        bgMixListeners.toList().forEach { listener ->
            listener.onPositionDiscontinuity(nativePositionInfo(before), nativePositionInfo(after), Player.DISCONTINUITY_REASON_SEEK)
            listener.onEvents(player, events)
        }
    }

    /** Pauses BG Mix when another app takes audio focus or the output becomes noisy (headphones unplugged). */
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
            if (bgMixActive()) {
                resumeSpeechOnFocus = null
                speech.pause()
                pause()
            }
        }
    }

    private fun registerBgNoisyReceiver(context: Context) {
        if (bgNoisyRegistered) return
        ContextCompat.registerReceiver(
            context.applicationContext,
            bgNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        bgNoisyRegistered = true
    }
    private fun unregisterBgNoisyReceiver() {
        val context = appContext
        if (context != null && bgNoisyRegistered) {
            runCatching { context.unregisterReceiver(bgNoisyReceiver) }
            bgNoisyRegistered = false
        }
    }

    private fun bgMixDriver(context: Context): BgMixQueueDriver =
        (bgMix ?: BgMixQueueDriver(File(context.filesDir, "bgmix-cache")).also { bgMix = it }).also {
            // Lets a live BG Wave host tap the mix that is playing right now; the hub re-attaches every tick.
            BgWaveHub.hostTap = ::attachWaveTap
        }

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
        BgWaveHostTap.exoSuppressedForBgMix = true
        updateWaveCaptureEligibility(item)
        syncBgMixWaveTap(driver)
        progress?.cancel()
        progress = null
        exo?.let {
            crossfader.cancel(it)
            it.stop()
            // The handover took ExoPlayer's level down; it comes back for its next playback.
            setMusicEnvelope(it, 1f)
            it.clearMediaItems()
            // A paused placeholder keeps the session's current item (title, duration) and notification alive.
            it.playWhenReady = false
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
            // The native deck was confirmed playing before the fade; publishing a pause here would make the system
            // card blink pause/play at every handover. A later status tick corrects this if the deck stopped.
            playing = autoPlay,
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
                    if (bgMix?.status === statusFlow) failBgMix()
                }
                statusFlow.collect { status ->
                    if (status != null && bgMix?.status === statusFlow) applyBgMix(status)
                }
            }
        }
        startBgMixFeed()
        // A radio handed over near the end of its page asks for the next one right away.
        loadNextQueuePage()
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
            var loggedPending: Pair<Int, Int>? = null
            while (true) {
                val driver = bgMix?.takeIf { it.isActive } ?: return@launch
                if (driver.pendingIndex == null) return@launch
                val currentIndex = _state.value.queueIndex
                val downloadEnd = bgMixWindowEndExclusive(driver.queueItems.size, currentIndex, BG_MIX_DOWNLOAD_AHEAD)
                if (currentIndex in driver.queueItems.indices) {
                    val keepStart = (currentIndex - 1).coerceAtLeast(0)
                    // The retention window is wider than the download window: a copy that just played must not be
                    // deleted by this very loop while the regular ownership window still names it.
                    val keepEnd = bgMixWindowEndExclusive(driver.queueItems.size, currentIndex, MUSIC_RETENTION_AHEAD)
                    val keep = driver.queueItems.subList(keepStart, keepEnd).mapTo(HashSet()) { it.key }
                    // A staged handover deck is prepared but not in the queue window yet: its copy must survive.
                    keep += driver.keysInUse()
                    if (YandexTrackResolver.evictOutside(keep)) {
                        appContext?.let { YandexTrackResolver.pruneUnused(it) }
                    }
                    // The native queue needs its next files at once: keep their fetches ahead of stale ones.
                    reprioritizeLazyRows()
                }
                val appended = driver.appendReady(currentIndex, ::bgMixFileFor)
                val pending = driver.pendingIndex
                // Privacy-safe readiness trace: where the next deck waits, without titles, paths or service ids.
                val marker = pending?.let { currentIndex to it }
                if (appended > 0 || (marker != null && marker != loggedPending)) {
                    loggedPending = marker
                    recordAppEvent(
                        kind = EventKind.PERFORMANCE,
                        code = "bgmix_deck_feed",
                        text = "Музыка: очередь BG Mix получила готовые файлы",
                        attrs = mapOf(
                            "queue_index" to currentIndex.toString(),
                            "pending_offset" to ((pending ?: currentIndex) - currentIndex).toString(),
                            "native_tracks" to driver.nativeCount.toString(),
                            "appended" to appended.toString(),
                            "download_ahead" to BG_MIX_DOWNLOAD_AHEAD.toString(),
                        )
                    )
                }
                if (pending == null) return@launch
                // Only the track right after the playing one is part of playback; the rows after it follow the policy.
                val playingNext = currentIndex + 1
                val context = appContext
                val source = queueSource
                if (context != null) {
                    for (index in pending until downloadEnd) {
                        // Speculative traffic goes through the automatic-download policy, like the regular prefetch.
                        prefetchItem(context, source, driver.queueItems[index], playbackNext = index == playingNext)
                    }
                }
                val state = _state.value
                if (state.playing || state.intentPlaying) {
                    delay(BG_MIX_FEED_INTERVAL_MS)
                } else {
                    // A paused queue needs no deck prepared right now. Wait for resume instead of doing file I/O on a
                    // one-second timer for hours.
                    withTimeoutOrNull(BG_MIX_FEED_PAUSED_WAIT_MS) {
                        _state.first { it.playing || it.intentPlaying || it.ended }
                    }
                }
            }
        }
    }

    /**
     * Offers the queue ExoPlayer has just started to BG Mix. The track keeps playing on ExoPlayer, so it starts at once
     * even while its file downloads. Once the playing track is a finished file, BG Mix prepares it silently and takes over
     * at the same place. The player shows the BG Mix mark from the start for a track BG Mix can take.
     */
    private fun scheduleBgMixHandover(rejectedKey: String? = null) {
        cancelBgMixHandover()
        val context = appContext ?: return
        BgMixSettingsStore.load(context)
        if (!BgMixSettingsStore.enabled.value) {
            Log.i("BgMixHandover", "blocked reason=${BgMixHandoverBlockReason.Disabled.name}")
            return
        }
        if (!BgMixNative.isAvailable) {
            Log.i("BgMixHandover", "blocked reason=${BgMixHandoverBlockReason.NativeUnavailable.name}")
            return
        }
        bgMixHandoverJob = scope.launch {
            runBgMixHandover(context, rejectedKey)
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

    private suspend fun runBgMixHandover(context: Context, initialRejectedKey: String? = null) {
        var failures = 0
        var rejectedKey = initialRejectedKey
        var lastItemKey: String? = null
        var exhaustedKey: String? = null
        var lastLoggedReason: BgMixHandoverBlockReason? = null
        while (currentCoroutineContext().isActive) {
            if (bgMixActive()) return
            val state = _state.value
            val item = state.item ?: return
            if (!item.isMusic || item.systemPrivate || state.queueIndex !in state.queue.indices) return
            if (item.key != lastItemKey) {
                failures = 0
                exhaustedKey = null
                lastItemKey = item.key
            }
            val file = bgMixFileFor(item)
            val candidate = BgMixQueueDriver.isCandidate(item) || file != null
            val target = exo
            val remainingMs = target?.duration?.takeIf { it != C.TIME_UNSET }?.let { it - target.currentPosition }
            val reason = bgMixHandoverBlockReason(
                enabled = BgMixSettingsStore.enabled.value,
                nativeAvailable = BgMixNative.isAvailable,
                isMusic = item.isMusic,
                systemPrivate = item.systemPrivate,
                isCandidate = candidate,
                isRetiring = bgMix?.isRetiring == true,
                isRejected = item.key == rejectedKey,
                isAttemptsExhausted = item.key == exhaustedKey,
                playing = state.playing,
                intentPlaying = state.intentPlaying,
                ended = state.ended,
                waitingForNetwork = state.waitingForNetwork,
                hasDeferredSeek = deferredSeek != null,
                speed = effectiveSpeed(item),
                remainingMs = remainingMs,
                minRemainingMs = BG_HANDOVER_MIN_REMAINING_MS,
                fileReady = file != null,
            )
            if (reason != lastLoggedReason) {
                Log.i("BgMixHandover", "state reason=${reason.name} attempts=$failures")
                lastLoggedReason = reason
            }
            setBgMixPreparing(reason.showsPreparingIndicator())
            if (reason != BgMixHandoverBlockReason.Ready || file == null) {
                if (state.intentPlaying && reason.showsPreparingIndicator()) {
                    // The file or a retiring host is about to become ready: a short poll takes over at once.
                    delay(BG_HANDOVER_POLL_MS)
                } else {
                    // Nothing about a paused, private or rejected track changes by itself. Polling the filesystem every
                    // second for hours is what drained the battery; a state change or a long timeout wakes this instead.
                    awaitHandoverWakeup(state)
                }
                continue
            }
            // The ordinary overlap is already moving the primary's gain; staging a second native session on top of it
            // would either cut its tail or never commit. The next track is handed over after this transition.
            if (crossfader.isOverlapping()) {
                delay(BG_HANDOVER_POLL_MS)
                continue
            }
            when (handOverToBgMix(context, state, file) { rejectedKey = file.key }) {
                true -> return
                // The queue or playback state changed while the session was being prepared: re-evaluate without burning
                // attempts. A short pause keeps a transient state from spinning this loop.
                null -> {
                    delay(BG_HANDOVER_POLL_MS)
                    continue
                }
                false -> {
                    if (rejectedKey != file.key) failures++ else failures = 0
                    if (failures >= BG_HANDOVER_ATTEMPTS) {
                        exhaustedKey = file.key
                        setBgMixPreparing(false)
                    }
                    delay(BG_HANDOVER_RETRY_MS)
                }
            }
        }
    }

    /**
     * Prepares a BG Mix session for the queue from the playing track while ExoPlayer keeps playing, then starts it at
     * ExoPlayer's position and lets ExoPlayer go. False when the session could not be prepared, null when the queue,
     * track or occurrence changed meanwhile, or the committed fade was superseded: a new state has to be evaluated.
     */
    private suspend fun handOverToBgMix(context: Context, state: PlaybackState, file: PlaybackItem, onRejected: () -> Unit): Boolean? {
        // A missing player, timeline or a different current item is transient: re-evaluate instead of burning attempts.
        val target = exo ?: return null
        val occurrence = target.handoverOccurrence() ?: return null
        if (occurrence.mediaId != file.key) return null
        val generation = bgMixHandoverGeneration
        fun sameOccurrence() = exo === target && occurrence == target.handoverOccurrence()
        fun stillPreparing() = generation == bgMixHandoverGeneration && sameOccurrence() && stillHandingOver(file)
        val driver = bgMixDriver(context)
        val single = state.repeatMode == PlaybackRepeatMode.One
        val items = state.queue.toMutableList().also { it[state.queueIndex] = file }
        val shuffled = state.shuffleEnabled && !single
        // The native session has no shuffle order. It takes the rest of ExoPlayer's own shuffle order from the current
        // track on, so tracks that already played do not come back; Repeat All keeps the played ones after them.
        val order = if (shuffled) bgMixShuffleOrder(items, state.queueIndex, state.repeatMode) else items
        val index = if (shuffled) 0 else state.queueIndex
        val queueKeys = state.queue.map { it.key }
        fun queueChanged(): Boolean = _state.value.queue.map { it.key } != queueKeys || _state.value.shuffleEnabled != state.shuffleEnabled
        if (!driver.stage(order, index, single, ::bgMixFileFor)) {
            Log.w("BgMixHandover", "handover_failed stage=stage")
            return false
        }
        if (!stillPreparing()) {
            driver.discardStaged()
            return null
        }
        val status = driver.stagedStatus
        val loaded = status?.let {
            withTimeoutOrNull(BG_HANDOVER_PREPARE_TIMEOUT_MS) {
                it.first { snapshot ->
                    snapshot?.queue?.any { row -> row.state == "failed" } == true ||
                        snapshot?.decks?.getOrNull(snapshot.master)?.let { deck -> deck.loaded && deck.durationSeconds > 0.0 } == true
                }
            }
        }
        if (queueChanged()) {
            driver.discardStaged()
            return null
        }
        if (loaded?.queue?.any { it.state == "failed" } == true) {
            Log.w("BgMixHandover", "handover_failed stage=decode_rejected")
            onRejected()
            driver.discardStaged()
            return false
        }
        if (!stillPreparing()) {
            driver.discardStaged()
            return null
        }
        if (loaded == null) {
            Log.w("BgMixHandover", "handover_failed stage=prepare_timeout")
            driver.discardStaged()
            return false
        }
        // A starting guess: the alignment below measures where the native output is really heard.
        val positionMs = target.currentPosition + BG_HANDOVER_LEAD_MS
        if (!driver.seekStaged(positionMs / 1000.0)) {
            Log.w("BgMixHandover", "handover_failed stage=seek_staged")
            driver.discardStaged()
            return false
        }
        // BG Mix starts silent: its first samples must not land on top of ExoPlayer at full level.
        driver.setStagedVolume(0f)
        driver.playStaged()
        // ExoPlayer keeps playing until the native output runs, so the handover has no gap.
        val started = withTimeoutOrNull(BG_HANDOVER_START_TIMEOUT_MS) {
            driver.stagedStatus?.first { snapshot -> snapshot?.decks?.getOrNull(snapshot.master)?.playing == true }
        }
        if (!stillPreparing()) {
            driver.discardStaged()
            return null
        }
        if (started == null) {
            Log.w("BgMixHandover", "handover_failed stage=start_timeout")
            driver.discardStaged()
            return false
        }
        delay(BG_HANDOVER_OVERLAP_MS)
        // Still silent: the output is lined up with ExoPlayer by measurement before anything of it is heard.
        val alignment = alignStagedSilently(target, driver)
        if (!stillPreparing() || queueChanged()) {
            Log.i("BgMixHandover", "handover_superseded stage=before_commit")
            driver.discardStaged()
            return null
        }
        if (!alignment.safeToHandOver) {
            // Handing over a deck that is far away from ExoPlayer would be heard as a repeat or a jump inside the
            // song. ExoPlayer keeps the sound; a later attempt may still align the staged deck.
            Log.w(
                "BgMixHandover",
                "handover_deferred stage=alignment measured=${alignment.measured} offset=${alignment.lastOffsetMs ?: "unknown"}"
            )
            driver.discardStaged()
            return false
        }
        if (!stillPreparing() || !requestPrimaryFocus()) {
            driver.discardStaged()
            return false
        }
        registerBgNoisyReceiver(context)
        // An overlap that already moves the primary's level keeps both players of this occurrence: cancelling it here
        // would cut the outgoing tail audibly, and two owners would write the same gain. The next track is worth
        // handing over instead; the queue is not lost, BG Mix is offered it again.
        if (crossfader.isOverlapping()) {
            Log.i("BgMixHandover", "handover_deferred stage=crossfade")
            unregisterBgNoisyReceiver()
            driver.discardStaged()
            return null
        }
        // From here the ordinary crossfade must not write any gain: the handover owns the primary level.
        bgMixHandoverFading = true
        bgEnvelope = 0f
        if (!driver.commitStaged()) {
            bgMixHandoverFading = false
            bgEnvelope = 1f
            driver.discardStaged()
            unregisterBgNoisyReceiver()
            return false
        }
        // Suppress ExoPlayer's tap before attaching BG Mix's tap so both never push PCM simultaneously.
        BgWaveHostTap.exoSuppressedForBgMix = true
        updateWaveCaptureEligibility(order[index])
        syncBgMixWaveTap(driver)
        val committedSession = driver.status
        // Once committed, native playback suppresses Exo callbacks. Check the live Exo occurrence throughout the fade.
        return withContext(NonCancellable) {
            _audioSessionId.value = driver.audioSessionId
            // The ordinary crossfade may have prepared or already started a transition on the same primary. It loses
            // ownership atomically here: its tail is released and the primary level is restored before the handover
            // reads the level it fades from. Without this the two ramps write the same gain and dip the sound.
            bgMixHandoverFading = true
            crossfader.cancel(target)
            val completed = try {
                crossfadeIntoBgMix(target, driver) {
                    generation == bgMixHandoverGeneration && driver.status === committedSession &&
                        driver.isActive && exo === target &&
                        occurrence.canFinishOn(target.handoverOccurrence(), ended = target.playbackState == Player.STATE_ENDED)
                }
            } finally {
                bgMixHandoverFading = false
            }
            if (!completed) {
                Log.i("BgMixHandover", "handover_superseded stage=fade")
                if (driver.status === committedSession && driver.isActive) {
                    val occurrenceChanged = !sameOccurrence()
                    stopBgMix()
                    if (exo === target) {
                        setMusicEnvelope(target, 1f)
                        if (occurrenceChanged) {
                            listener.onMediaItemTransition(target.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
                        }
                        if (target.playbackState == Player.STATE_ENDED) listener.onPlaybackStateChanged(Player.STATE_ENDED)
                        syncPosition()
                    }
                }
                // The fade was superseded by a stop, another track or EOF: this is not a failed attempt, so the new
                // playback state is evaluated again without spending the track's retry budget.
                return@withContext null
            }
            bgMixHandoverJob = null
            bgMixOriginalQueue = if (shuffled) items else null
            adoptBgMixSession(driver, index, positionMs = positionMs, autoPlay = true)
            true
        }
    }

    /**
     * Waits until the state the handover decision depends on changes. A pending file download still ends here on the
     * long timeout, so a completed download is noticed even if no state change follows.
     */
    private suspend fun awaitHandoverWakeup(snapshot: PlaybackState) {
        withTimeoutOrNull(BG_HANDOVER_IDLE_WAIT_MS) {
            _state.first { next ->
                bgMixActive() || next.item?.key != snapshot.item?.key ||
                    next.queueIndex != snapshot.queueIndex ||
                    next.playing != snapshot.playing ||
                    next.intentPlaying != snapshot.intentPlaying ||
                    next.ended != snapshot.ended ||
                    next.waitingForNetwork != snapshot.waitingForNetwork
            }
        }
    }

    private fun ExoPlayer.handoverOccurrence(): BgMixExoOccurrence? {
        val mediaId = currentMediaItem?.mediaId ?: return null
        val timeline = currentTimeline
        val index = currentMediaItemIndex
        if (index !in 0 until timeline.windowCount) return null
        return BgMixExoOccurrence(mediaId, timeline.getWindow(index, Timeline.Window()).uid)
    }

    /**
     * Hands the sound from ExoPlayer to BG Mix over [BG_HANDOVER_FADE_MS], while both play the same occurrence.
     */
    private suspend fun crossfadeIntoBgMix(target: ExoPlayer, driver: BgMixQueueDriver, stillOwnsFade: () -> Boolean): Boolean {
        val from = musicEnvelopes[target] ?: 1f
        val quarterTurn = (kotlin.math.PI / 2).toFloat()
        val step = BG_HANDOVER_FADE_MS / BG_HANDOVER_FADE_STEPS
        for (n in 1..BG_HANDOVER_FADE_STEPS) {
            if (!stillOwnsFade()) return false
            val t = n.toFloat() / BG_HANDOVER_FADE_STEPS
            setMusicEnvelope(target, from * kotlin.math.cos(t * quarterTurn).toFloat())
            bgEnvelope = kotlin.math.sin(t * quarterTurn).toFloat()
            applyBgGain(driver)
            delay(step)
        }
        if (!stillOwnsFade()) return false
        setMusicEnvelope(target, 0f)
        bgEnvelope = 1f
        applyBgGain(driver)
        return true
    }

    /**
     * Lines the silent BG Mix output up with ExoPlayer before any of it is heard. It measures where BG Mix is heard on the
     * audio clock and seeks it while it is silent. The new audio starts once the audio already queued has played out, and
     * by then ExoPlayer has moved on by that same amount, so the target is ExoPlayer's position plus the queued audio. The
     * check repeats until the difference is within [BG_ALIGN_TOLERANCE_MS]. The result says whether the final
     * measurement is safe enough to commit; the caller keeps ExoPlayer when it is not.
     */
    private suspend fun alignStagedSilently(target: ExoPlayer, driver: BgMixQueueDriver): BgMixAlignmentResult {
        val offsetsMs = mutableListOf<Long>()
        fun finish(aligned: Boolean): BgMixAlignmentResult {
            recordAppEvent(
                kind = EventKind.PERFORMANCE,
                code = "bgmix_handover_alignment",
                text = if (aligned) "Переход в BG Mix: звук выровнен с плеером" else "Переход в BG Mix: выровнять звук не удалось",
                attrs = mapOf(
                    "offsets_ms" to offsetsMs.joinToString(","),
                    "measured" to offsetsMs.isNotEmpty().toString(),
                    "last_offset_ms" to (offsetsMs.lastOrNull() ?: 0L).toString(),
                ),
            )
            return BgMixAlignmentResult(alignedInTolerance = aligned, offsetsMs = offsetsMs)
        }
        // At most BG_ALIGN_ATTEMPTS corrections; each one is checked by the measurement that follows it.
        for (correction in 0..BG_ALIGN_ATTEMPTS) {
            val audible = awaitStagedAudible(driver) ?: return finish(false)
            val exoMs = target.currentPosition.toDouble()
            val offsetMs = bgMixAlignmentOffsetMs(audible, exoMs)
            offsetsMs += offsetMs.toLong()
            if (kotlin.math.abs(offsetMs) <= BG_ALIGN_TOLERANCE_MS) return finish(true)
            // After the last allowed correction this measurement is only the check, so nothing more is corrected.
            if (correction == BG_ALIGN_ATTEMPTS) break
            if (!driver.seekStaged(bgMixAlignmentSeekMs(audible, exoMs) / 1_000.0)) return finish(false)
        }
        return finish(false)
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

    /** Switches ExoPlayer's own audio-focus handling; the audio kind it plays with is kept. */
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
    private fun continueBgMixQueueOnExo(items: List<PlaybackItem>, index: Int, autoPlay: Boolean = true, rejected: Boolean = false) {
        val context = appContext ?: return
        if (!bgMixActive() || index !in items.indices) return
        stopBgMix()
        startQueue(context, PlaybackQueue(items, index), queueSource, startPositionMs = null, startFraction = 0f, autoPlay = autoPlay, offerBgMix = !rejected)
        if (rejected) scheduleBgMixHandover(rejectedKey = items[index].key)
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
        // Start playback immediately on Exo; the retired host must finish before
        // the asynchronous handover can prepare a new one.
        startQueue(context, PlaybackQueue(items, index), queueSource,
            startPositionMs = positionMs, startFraction = 0f, autoPlay = autoPlay)
    }

    /** Copies the native status into the Media state the UI and the notification read. */
    private fun applyBgMix(status: BgMixStatus) {
        val driver = bgMix ?: return
        val before = _state.value
        val master = status.decks.getOrNull(status.master)
        val masterId = master?.trackId?.takeIf { it != 0L }
        // A native track that is not mapped yet is a transient of a queue replacement. Fall back to the occurrence that
        // was current, never to index 0: a blind zero would show another song until the next status tick.
        val nativeIndex = when {
            masterId != null -> driver.indexOfNative(masterId)
                ?: bgMixLogicalIndex(driver.queueItems, before.item?.occurrenceId, before.item?.key, before.queueIndex)
            else -> before.queueIndex
        }
        if (nativeIndex !in driver.queueItems.indices) return
        // A queue edit during a transition is published at once. The native session still reports the old order for a
        // few ticks, so its snapshot must not roll the edited queue back underneath the listener.
        val pendingQueue = pendingBgMixRetarget?.first
        val logicalQueue = pendingQueue ?: driver.queueItems
        val index = if (pendingQueue != null) {
            bgMixLogicalIndex(logicalQueue, before.item?.occurrenceId, before.item?.key, nativeIndex)
        } else {
            nativeIndex
        }
        if (index !in logicalQueue.indices) return
        _bgMixTransition.value = nextBgMixTransition(
            previous = _bgMixTransition.value,
            status = status,
            previousIndex = before.queueIndex,
            indexOfNative = driver::indexOfNative,
            itemAt = driver::itemAt
        )
        val item = logicalQueue[index]
        // Eligibility changes on track adoption/transition, not on every 50 ms status tick.
        if (before.item?.key != item.key || before.item?.let(::isWaveCaptureEligible) != isWaveCaptureEligible(item)) {
            updateWaveCaptureEligibility(item)
        }
        val deckPlaying = master?.playing == true
        val terminalQueue = status.queue.isNotEmpty() && status.queue.all { it.state == "played" || it.state == "failed" }
        // Native keeps an ended master loaded (reported as playing in the queue). If no usable
        // successor is admitted, resume the logical queue on Exo rather than strand playback at EOF.
        val hasNativeSuccessor = driver.hasUnfinishedNativeAfter(nativeIndex, status)
        val masterEnded = master?.ended == true && !hasNativeSuccessor && !status.paused
        val finished = (terminalQueue || masterEnded) && !status.transitionActive && !deckPlaying
        val pending = driver.pendingIndex
        val failed = driver.failedIndex(status, nativeIndex)
        // A rejected current/successor must still play in logical queue order on ExoPlayer.
        val fallbackIndex = if (failed != null && (failed == nativeIndex || finished)) failed else if (finished) pending else null
        if (fallbackIndex != null) {
            val items = driver.queueItems
            // Ignore a delayed fallback after a queue replacement or a new native session.
            val sessionStatus = driver.status
            scope.launch {
                val fresh = sessionStatus?.value ?: return@launch
                if (bgMix?.status === sessionStatus && driver.queueItems === items &&
                    driver.pendingIndex == pending && _state.value.queueIndex == before.queueIndex &&
                    bgMixDeferredMasterMatches(status, fresh)
                ) {
                    continueBgMixQueueOnExo(items, fallbackIndex, autoPlay = before.intentPlaying || before.playing, rejected = fallbackIndex == failed)
                }
            }
            return
        }
        // An endless queue (a radio) played out its known tracks while the next page loads or waits for its retry: keep the
        // play intent and wait. The page is requested on every track change, and its result applies this status again.
        val pageInFlight = queuePageJob?.isActive == true || (
            queuePageRetryJob?.isActive == true &&
                queuePageRetries <= ENDLESS_AWAIT_FAST_RETRIES &&
                AutoDownloadNetworkStatus.network.value != AutoDownloadNetwork.None
            )
        val awaitingPage = bgMixAwaitsNextPage(
            finished = finished,
            pendingIndex = pending,
            endless = queueSource?.loadMore != null,
            repeat = _state.value.repeatMode,
            pageInFlight = pageInFlight
        )
        val reportFinished = finished && !awaitingPage
        if (reportFinished && queueSource?.loadMore != null && (before.intentPlaying || before.playing) &&
            _state.value.repeatMode == PlaybackRepeatMode.Off
        ) {
            endlessWaitingForRecovery = true
        }
        val playing = !status.paused && deckPlaying && !finished
        val hasNext = status.queue.any { (driver.indexOfNative(it.id) ?: -1) > nativeIndex } || pending != null
        _state.update { current ->
            current.copy(
                // Repeat One keeps one track in the native queue, so Next is judged by the logical queue.
                timelineHasNext = hasNext || current.repeatMode == PlaybackRepeatMode.One ||
                    (index >= logicalQueue.lastIndex && bgMixHasNext(index, logicalQueue.size)),
                timelineHasPrevious = true,
                item = item,
                queue = logicalQueue,
                queueIndex = index,
                playing = playing,
                intentPlaying = !status.paused && (!finished || awaitingPage),
                positionMs = ((master?.positionSeconds ?: 0.0) * 1000).toLong(),
                durationMs = ((master?.durationSeconds ?: 0.0) * 1000).toLong(),
                started = current.started || playing,
                ended = reportFinished,
                isBuffering = awaitingPage,
                bgMixActive = true
            )
        }
        // A radio asks for its next page as BG Mix advances, the same way the regular player does on a track change.
        val trackChanged = before.item?.key != item.key
        if (trackChanged) {
            queuePageRetries = 0
            queuePageRetryJob?.cancel()
            queuePageRetryJob = null
            queuePageNetworkJob?.cancel()
            queuePageNetworkJob = null
            endlessWaitingForRecovery = false
            endlessRecovery = null
            loadNextQueuePage()
        }
        if (before.queueIndex != index) {
            // The native queue moved (or the listener edited it): the files of the new window go first.
            reprioritizeLazyRows()
        }
        if (before.item?.key != _state.value.item?.key) {
            exo?.let { target ->
                if (target.mediaItemCount > 0) target.replaceMediaItem(0, placeholderItem(item))
            }
        }
        val repeat = _state.value.repeatMode
        val repeatsQueue = reportFinished && !before.ended && repeat != PlaybackRepeatMode.Off &&
            (masterEnded || status.queue.any { it.state == "played" })
        if (repeatsQueue) {
            // Repeat One plays the same track again, Repeat All starts the queue over; the mode stays BG Mix.
            val items = driver.queueItems
            val next = if (repeat == PlaybackRepeatMode.One) index else 0
            val sessionStatus = driver.status
            notifyChanges(before, _state.value)
            scope.launch {
                val fresh = sessionStatus?.value ?: return@launch
                val current = _state.value
                if (bgMix?.status === sessionStatus && driver.queueItems === items &&
                    current.ended && current.repeatMode == repeat && current.queueIndex == index &&
                    bgMixDeferredMasterMatches(status, fresh)
                ) restartBgMix(items, next, 0L, autoPlay = true)
            }
            return
        }
        if (reportFinished && !before.ended) {
            unregisterBgNoisyReceiver()
            releasePrimaryFocus()
            releaseBgWakeLock()
        }
        notifyChanges(before, _state.value)
        fireStartedCallback()
        if (!status.transitionActive) {
            val deferred = pendingBgMixRetarget
            if (deferred != null) {
                pendingBgMixRetarget = null
                if (!retargetBgMix(deferred.first, deferred.second)) {
                    // Never index 0 as a blind fallback: keep the occurrence that is playing, or the nearest one after
                    // it in the edited queue.
                    val fallbackIdx = bgMixLogicalIndex(deferred.first, item.occurrenceId, item.key, index)
                    scope.launch {
                        continueBgMixQueueOnExo(
                            deferred.first,
                            fallbackIdx,
                            autoPlay = _state.value.intentPlaying || _state.value.playing
                        )
                    }
                }
            }
        }
    }

    private fun toggleBgMix() {
        val state = _state.value
        if (!state.ended && (state.intentPlaying || state.playing)) pause() else bgMixPlay()
    }

    /** Resumes BG Mix, or restarts the queue from its current index when it has ended. */
    private fun bgMixPlay() {
        if (_state.value.ended) {
            if (currentQueueIsEndless()) {
                triggerManualEndlessRecovery(startServiceIfNeeded = false)
                return
            }
            playQueueIndex(_state.value.queueIndex)
            return
        }
        val context = appContext
        if (context == null || !requestPrimaryFocus()) return
        registerBgNoisyReceiver(context)
        val before = _state.value
        bgMix?.let(::applyBgGain)
        bgMix?.resume()
        holdBgWakeLock()
        _state.update { it.copy(intentPlaying = true, ended = false) }
        notifyChanges(before, _state.value)
        startPlaybackService()
    }

    private fun pauseBgMix() {
        endlessWaitingForRecovery = false
        endlessRecovery = null
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
        pendingBgMixRetarget = null
        bgMix?.attachWaveTap(0L)
        BgWaveHostTap.exoSuppressedForBgMix = false
        bgMix?.stop()
        bgEnvelope = 1f
        _bgMixTransition.value = null
        unregisterBgNoisyReceiver()
        releaseBgWakeLock()
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
        invalidateInterruptionResume()
        stopBgMix()
        endlessRecovery = null
        endlessWaitingForRecovery = false
        updateWaveCaptureEligibility(null)
        BgWaveHostTap.exoSuppressedForBgMix = false
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
        PlaybackSources.clearExcept(speechState.value.item?.systemMediaId)
        exo?.let {
            crossfader.cancel(it)
            it.stop()
            it.clearMediaItems()
        }
        releasePrimaryFocus()
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
        if (speechState.value.item?.systemPrivate == true) speech.stop()
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
            // A Next that moves nowhere (the last track without repeat) is not a skip.
            if (!last || state.repeatMode == PlaybackRepeatMode.One || state.repeatMode == PlaybackRepeatMode.All) {
                _manualSkips.update { it + 1 }
            }
            when {
                state.repeatMode == PlaybackRepeatMode.One -> restartBgMix(state.queue, if (last) 0 else state.queueIndex + 1, 0L, state.intentPlaying || state.ended)
                // A successor rejected by the decoder must not be skipped on manual Next either.
                !last && bgMix?.let { driver -> driver.status?.value?.let { driver.failedIndex(it, state.queueIndex + 1) } } == state.queueIndex + 1 ->
                    continueBgMixQueueOnExo(state.queue, state.queueIndex + 1, autoPlay = state.playing || state.intentPlaying || state.ended, rejected = true)
                // The next track is still downloading and not in the native queue yet: the regular player streams it.
                !last && bgMix?.hasNativeAfter(state.queueIndex) == false && bgMix?.pendingIndex != null ->
                    bgMix?.pendingIndex?.let {
                        // Like the regular Next, a paused queue stays paused on the new track.
                        continueBgMixQueueOnExo(state.queue, it, autoPlay = state.playing || state.intentPlaying || state.ended)
                    }
                // Manual Next is an explicit jump, not the native planned transition (session.next()), which adds a
                // crossfade and could lift a pause. A prepared successor switches in the native session at once; any
                // other one moves the queue to the regular player at the next track from 0, and startQueue schedules
                // the automatic handover back to BG Mix.
                !last -> if (bgMix?.manualNext() != true) {
                    continueBgMixQueueOnExo(state.queue, state.queueIndex + 1, autoPlay = state.playing || state.intentPlaying || state.ended)
                }
                state.repeatMode == PlaybackRepeatMode.All -> restartBgMix(state.queue, 0, 0L, state.intentPlaying || state.ended)
                currentQueueIsEndless() -> triggerManualEndlessRecovery(startServiceIfNeeded = false)
            }
            return
        }
        val target = exo ?: return
        val state = _state.value
        val hasNext = target.hasNextMediaItem()
        val fallback = if (state.shuffleEnabled) null else (state.queueIndex + 1).takeIf { it in state.queue.indices }
        val wrapAll = state.repeatMode == PlaybackRepeatMode.All && target.mediaItemCount > 1
        if (!hasNext && !wrapAll && fallback == null) {
            if (currentQueueIsEndless()) triggerManualEndlessRecovery(startServiceIfNeeded = true)
            return
        }
        _manualSkips.update { it + 1 }
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
            playPrimary(target)
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
            _manualSkips.update { it + 1 }
            // A prepared row switches in the native session at once, without a crossfade. An explicit row pick plays,
            // as it always did, so play is requested after the switch. Otherwise the regular player takes over below.
            if (index in _state.value.queue.indices && bgMix?.manualSelect(index) == true) {
                if (!requestPrimaryFocus()) return
                bgMix?.let(::applyBgGain)
                bgMix?.resume()
                _state.update { it.copy(ended = false, failed = false, intentPlaying = true) }
                return
            }
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
        _manualSkips.update { it + 1 }
        transitionToken++
        clearDeferredSeek()
        _state.update { it.copy(ended = false, failed = false, intentPlaying = true) }
        target.seekTo(index, 0L)
        if (target.playbackState == Player.STATE_IDLE || target.playbackState == Player.STATE_ENDED) target.prepare()
        playPrimary(target)
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
                if (state.ended) {
                    restartBgMix(state.queue, state.queueIndex, 0L, state.intentPlaying || state.ended)
                } else {
                    val driver = bgMix ?: return
                    val session = driver.status
                    bgMixSeekJob?.cancel()
                    bgMixSeekJob = scope.launch {
                        if (driver.seek(0.0)) {
                            publishConfirmedNativeSeek(session, state, 0L)
                        } else if (bgMix?.status === session && _state.value.item?.key == state.item?.key) {
                            restartBgMix(state.queue, state.queueIndex, 0L, _state.value.intentPlaying)
                        }
                    }
                }
            } else {
                // A prepared previous track switches in the native session at once; otherwise the queue restarts on the regular player.
                if (bgMix?.manualSelect(state.queueIndex - 1) != true) {
                    restartBgMix(state.queue, state.queueIndex - 1, 0L, state.intentPlaying)
                }
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
                playPrimary(target)
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
            playPrimary(target)
            startProgress()
        }
    }

    /** Files the player still reads: the playing track, its bounded ownership window, and any music waiting behind a voice note. */
    fun musicPathsInUse(): Set<String> {
        val current = _state.value
        val waiting = interruptedMusic?.let {
            val idx = it.queueIndex
            val start = (idx - 1).coerceAtLeast(0)
            val end = (idx + 2).coerceAtMost(it.queue.size)
            listOf(it.item) + if (start < end) it.queue.subList(start, end) else emptyList()
        }.orEmpty()
        val bgMixPaths = bgMix?.pathsInUse().orEmpty()
        return activeMusicOwnershipPaths(current, waiting, bgMixPaths)
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
            // The session follows the new order in place when it can; otherwise it starts again at the same place.
            val keepOriginal = bgMixOriginalQueue
            if (!retargetBgMix(items)) restartBgMix(items, index, state.positionMs, state.intentPlaying)
            if (enabled) bgMixOriginalQueue = keepOriginal
            return
        }
        val target = exo
        if (_state.value.item?.isMusic == true && _state.value.item?.systemPrivate != true) target?.shuffleModeEnabled = enabled
        _state.update { it.copy(shuffleEnabled = enabled).withTimelineNavigation(target) }
        if (enabled && target != null) installShuffleOrder(target, target.mediaItemCount, currentIndex = target.currentMediaItemIndex)
        prefetchedKeys.clear()
        // A new shuffle order moves the next rows; fetches of the old order are no longer wanted.
        reprioritizeLazyRows()
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
        if (bgMixActive()) {
            // BG Mix follows the edit in its own session; the Exo timeline is not touched.
            val queue = base.filterNot { it.key == item.key }.toMutableList()
            val currentAt = queue.indexOfFirst { it.key == current.key }
            if (currentAt >= 0) {
                queue.add(currentAt + 1, item)
                if (retargetBgMix(queue)) return
            }
        }
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
        if (bgMixActive() && retargetBgMix(queue)) return
        if (canEditInPlace(target, base.size) && target != null) {
            registerSource(item, queueSource)
            target.addMediaItem(mediaItemFor(item))
            commitInPlaceQueue(queue)
            return
        }
        rebuildTimeline(queue, base.indexOfFirst { it.key == current.key }.coerceAtLeast(0), state.positionMs, state.playing || state.intentPlaying)
    }

    /**
     * Changes whenever the playing queue starts over, is replaced, cleared or stopped. [appendYandexTracks] takes the value
     * read when its tracks were started, so tracks of an older queue never land in a newer one.
     */
    val queueGeneration: Long get() = queuePageGeneration

    /**
     * Adds [tracks] after the last track of the playing queue without restarting the current track. It acts only while the
     * queue is still the one [generation] was read for; a queue started, replaced or stopped since then is left alone, and
     * nothing starts when no music plays. While BG Mix plays its own session, nothing is added: an edit of the Exo
     * queue would rebuild the timeline and stop BG Mix. The rest of the liked list is then not queued; its tracks stay in
     * ProviderTracks. Returns whether the queue took any track.
     */
    @OptIn(UnstableApi::class)
    fun appendYandexTracks(tracks: List<com.beykus.beygram.data.music.provider.ProviderTrack>, generation: Long): Boolean {
        val state = _state.value
        val current = state.item ?: return false
        val currentIsLegacyMusic = current.isMusic && !current.systemPrivate && current.accountId == LEGACY_PLAYBACK_ACCOUNT_ID
        // BG Mix is checked below: its own queue takes the tracks, the Exo timeline stays untouched.
        if (!yandexRestMayJoinQueue(generation, queuePageGeneration, bgMixActive = false, currentIsLegacyMusic = currentIsLegacyMusic)) return false
        val base = state.queue.ifEmpty { listOf(current) }
        val queued = base.mapTo(HashSet()) { it.key }
        val items = tracks.filter { it.isAvailable }.distinctBy { it.key }.filter { it.key !in queued }.map(::providerPlaybackItem)
        if (items.isEmpty()) return false
        if (bgMixActive()) return extendBgMixQueue(items, queueSource)
        val target = exo ?: return false
        val queue = base + items
        // The queue ran out while the rest was loading: its first new track plays on, as a page of an endless queue does.
        val ended = state.ended && state.intentPlaying
        if (canEditInPlace(target, base.size)) {
            items.forEach { registerSource(it, queueSource) }
            target.addMediaItems(items.map(::mediaItemFor))
            commitInPlaceQueue(queue)
            if (ended) {
                target.seekTo(base.size, 0L)
                if (target.playbackState == Player.STATE_IDLE || target.playbackState == Player.STATE_ENDED) target.prepare()
                playPrimary(target)
                startPlaybackService()
                startProgress()
            }
            return true
        }
        if (ended) {
            rebuildTimeline(queue, base.size, 0L, play = true)
        } else {
            rebuildTimeline(queue, base.indexOfFirst { it.key == current.key }.coerceAtLeast(0), state.positionMs, state.playing || state.intentPlaying)
        }
        return true
    }

    /**
     * Adds [items] after the running BG Mix session's queue; they join its native queue as their files finish and
     * [startBgMixFeed] restarts for them. False when no session runs or BG Mix cannot take them.
     */
    private fun extendBgMixQueue(items: List<PlaybackItem>, source: PlaybackQueueSource?): Boolean {
        val driver = bgMix?.takeIf { it.isActive } ?: return false
        if (!driver.extendQueue(items)) return false
        items.forEach { registerSource(it, source) }
        _state.update { it.copy(queue = driver.queueItems) }
        startBgMixFeed()
        return true
    }

    /**
     * Mirrors an edited logical queue into the running BG Mix session; the playing track keeps playing. False when the session
     * cannot follow the edit: the caller then takes its regular path, which stops the session and plays on ExoPlayer.
     */
    private fun retargetBgMix(queue: List<PlaybackItem>, source: PlaybackQueueSource? = queueSource): Boolean {
        val driver = bgMix?.takeIf { it.isActive } ?: return false
        val current = _state.value.item ?: return false
        if (driver.status?.value?.transitionActive == true) {
            val newIndex = bgMixLogicalIndex(
                queue,
                current.occurrenceId,
                current.key,
                _state.value.queueIndex
            ).takeIf { it in queue.indices } ?: return false
            pendingBgMixRetarget = queue to source
            val known = _state.value.queue.mapTo(HashSet()) { it.key }
            queue.filterNot { it.key in known }.forEach { registerSource(it, source) }
            _state.update {
                it.copy(
                    queue = queue,
                    queueIndex = newIndex,
                    timelineHasNext = bgMixHasNext(newIndex, queue.size) || driver.pendingIndex != null
                )
            }
            return true
        }
        pendingBgMixRetarget = null
        // Only new items get a source registration: re-registering a known one would replace its handle with a copy
        // that may predate a resolved file.
        val known = _state.value.queue.mapTo(HashSet()) { it.key }
        if (!driver.retarget(queue, current.key)) return false
        val items = driver.queueItems
        val index = bgMixLogicalIndex(items, current.occurrenceId, current.key, _state.value.queueIndex)
            .takeIf { it in items.indices } ?: return false
        items.filterNot { it.key in known }.forEach { registerSource(it, source) }
        _state.update {
            it.copy(
                queue = items,
                queueIndex = index,
                timelineHasNext = bgMixHasNext(index, items.size) || driver.pendingIndex != null
            )
        }
        startBgMixFeed()
        return true
    }

    /** Ends the page load of a queue that a new one replaces: its result is dropped when it arrives. */
    private fun dropQueuePages() {
        pendingBgMixRetarget = null
        queuePageGeneration++
        queuePageRetries = 0
        endlessWaitingForRecovery = false
        endlessRecovery = null
        queuePageJob?.cancel()
        queuePageJob = null
        queuePageRetryJob?.cancel()
        queuePageRetryJob = null
        queuePageNetworkJob?.cancel()
        queuePageNetworkJob = null
        _queuePageLoading.value = false
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
        if (index != state.queueIndex && bgMixActive() && retargetBgMix(queue)) {
            PlaybackSources.remove(state.queue[index].systemMediaId)
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
        queuePageRetries = 0
        endlessWaitingForRecovery = false
        queuePageJob?.cancel()
        queuePageJob = null
        queuePageRetryJob?.cancel()
        queuePageRetryJob = null
        queuePageNetworkJob?.cancel()
        queuePageNetworkJob = null
        _queuePageLoading.value = false
        queueSource = queueSource?.let {
            PlaybackQueueSource(it.accountId, it.observeFile, it.download, it.autoDownload, loadMore = null)
        }
        if (bgMixActive() && retargetBgMix(listOf(state.queue[keep]))) {
            state.queue.forEachIndexed { index, item -> if (index != keep) PlaybackSources.remove(item.systemMediaId) }
            return
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
        if (bgMixActive() && retargetBgMix(queue)) return
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
        speech.setSpeed(speed)
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
    private fun startPlaybackService(): Boolean {
        val context = appContext ?: return false
        val snapshot = _state.value
        if (snapshot.item == null || snapshot.item.systemPrivate) return false
        val intent = Intent(context, PlaybackService::class.java)
        try {
            context.startService(intent)
            return true
        } catch (error: Exception) {
            // Android 8+ refuses a plain start from the background. While audio plays the service may still be
            // started as a foreground one, and a failure is written to the diagnostics instead of being swallowed.
            val native = bgMixActive()
            val eligible = canRecoverPlaybackService(snapshot, native, exo?.isPlaying == true)
            val recovery = if (eligible) runCatching { ContextCompat.startForegroundService(context, intent) } else null
            val recovered = recovery?.isSuccess == true
            Log.w("PlaybackService", "start_failed renderer=${if (native) "bgmix" else "exo"} " +
                "error=${error.javaClass.simpleName} recovery=${recovery?.exceptionOrNull()?.javaClass?.simpleName ?: if (recovered) "accepted" else "not_attempted"}")
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
            return recovered
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
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) {
                invalidateInterruptionResume()
                releasePrimaryFocus()
            }
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
                releasePrimaryFocus()
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
            if (mediaId == null) {
                updateWaveCaptureEligibility(null)
            }
            val snapshot = _state.value
            if (mediaId != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                transitionToken++
                val index = exoQueueOccurrenceIndex(
                    snapshot.queue,
                    mediaId,
                    exo?.currentMediaItemIndex,
                    exo?.mediaItemCount
                )
                if (index >= 0) {
                    val item = resolvedItem(snapshot.queue[index])
                    updateWaveCaptureEligibility(item)
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
                    // The occurrence is published now, so the new window can free the slots of abandoned rows.
                    reprioritizeLazyRows()
                }
            }
            updateAudioSessionId()
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                prefetchNext()
                requestSilenceMeasurement()
                queuePageRetries = 0
                loadNextQueuePage()
                if (!bgMixActive() && bgMixHandoverJob?.isActive != true &&
                    _state.value.item?.let { it.isMusic && !it.systemPrivate } == true
                ) {
                    scheduleBgMixHandover()
                }
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
        endlessWaitingForRecovery = false
        endlessRecovery = null
        queuePageRetryJob?.cancel()
        queuePageRetryJob = null
        queuePageNetworkJob?.cancel()
        queuePageNetworkJob = null
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
            playPrimary(target)
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
        if (keepPlaying) playPrimary(target)
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
        if (queueSource?.loadMore != null && _state.value.intentPlaying && _state.value.repeatMode == PlaybackRepeatMode.Off) {
            endlessWaitingForRecovery = true
        }
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

    /** The radio the current queue belongs to, null for an ordinary queue. Read by the player header. */
    val activeRadio: PlaybackRadio? get() = queueSource?.radio

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

    /** A radio that has played out its known tracks. The page that ends it resumes playback only when a foreground service may run. */
    private data class EndlessRecovery(
        /** The page generation the request was made in: a page of an older queue must not resume anything. */
        val generation: Long,
        /** True for an explicit Play/Next: the page must start the new track, not just extend the timeline. */
        val manual: Boolean,
    )

    @Volatile
    private var endlessRecovery: EndlessRecovery? = null

    /**
     * Manual recovery when the listener taps Play or Next at the tail of an endless radio queue
     * (e.g. after temporary network loss or exhausted page retries). The ended flag is cleared so the controls show a
     * pending resume; [endlessRecovery] carries that intent to the page result, which would otherwise see ended=false
     * and never start playback — the regression reported against build 815.
     */
    private fun triggerManualEndlessRecovery(startServiceIfNeeded: Boolean) {
        queuePageRetries = 0
        queuePageRetryJob?.cancel()
        queuePageRetryJob = null
        queuePageNetworkJob?.cancel()
        queuePageNetworkJob = null
        endlessWaitingForRecovery = true
        endlessRecovery = EndlessRecovery(generation = queuePageGeneration, manual = true)
        _state.update { it.copy(failed = false, ended = false, intentPlaying = true, isBuffering = true) }
        if (startServiceIfNeeded && _state.value.item?.systemPrivate != true) startPlaybackService()
        loadNextQueuePage()
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
        // Source tracks (such as «Моя волна») belong to no Telegram account, so the account switch check skips them.
        val providerItem = snapshot.item.key.startsWith(com.beykus.beygram.data.music.provider.YANDEX_TRACK_KEY_PREFIX)
        _queuePageLoading.value = true
        queuePageJob = scope.launch {
            try {
                // A request that never answers must not hold a waiting radio forever: it counts as a failed page.
                val page = withTimeoutOrNull(QUEUE_PAGE_TIMEOUT_MS) { loader() } ?: throw java.io.IOException("Queue page timed out")
                if (generation != queuePageGeneration || source !== queueSource ||
                    (!providerItem && activeAccountId != null && currentAccount != activeAccountId)) return@launch
                val now = _state.value
                if (now.queue.isEmpty() || now.item?.accountId != currentAccount) return@launch
                val fresh = appendMusicPage(now.queue, page, currentAccount)
                if (fresh.isEmpty()) {
                    // An empty or fully repeated radio batch is not the end of an endless source: try again with the bounded backoff.
                    if (providerItem) scheduleQueuePageRetry(generation)
                    return@launch
                }
                if (bgMixActive()) {
                    // BG Mix plays its own queue: the page extends it instead of the Exo timeline.
                    if (extendBgMixQueue(fresh, source)) {
                        queuePageRetries = 0
                        queuePageNetworkJob?.cancel()
                        queuePageNetworkJob = null
                        val recovery = endlessRecovery?.takeIf { it.generation == generation }
                        val shouldResume = queuePageResumesPlayback(
                            ended = now.ended,
                            intentPlaying = now.intentPlaying,
                            endlessWaiting = endlessWaitingForRecovery,
                            recoveryPending = recovery != null,
                        )
                        endlessRecovery = null
                        endlessWaitingForRecovery = false
                        if (shouldResume) _state.update { it.copy(intentPlaying = true) }
                    } else {
                        scheduleQueuePageRetry(generation)
                    }
                    return@launch
                }
                val target = exo ?: return@launch
                fresh.forEach { registerSource(it, source) }
                target.addMediaItems(fresh.map(::mediaItemFor))
                // An explicit Play/Next at the tail asked for this page even though the ended flag was cleared: the
                // request itself is the resume signal, independent of the state that was published meanwhile.
                val recovery = endlessRecovery?.takeIf { it.generation == generation }
                val ended = queuePageResumesPlayback(
                    ended = now.ended,
                    intentPlaying = now.intentPlaying,
                    endlessWaiting = endlessWaitingForRecovery,
                    recoveryPending = recovery != null,
                )
                endlessRecovery = null
                endlessWaitingForRecovery = false
                queuePageNetworkJob?.cancel()
                queuePageNetworkJob = null
                _state.update { it.copy(queue = it.queue + fresh).withTimelineNavigation(target) }
                if (ended) {
                    target.seekTo(now.queue.size, 0L)
                    if (target.playbackState == Player.STATE_IDLE || target.playbackState == Player.STATE_ENDED) target.prepare()
                    // Android 12+ refuses a background foreground-service start. Audio must never begin without the
                    // notification and its controls: the new page stays queued and the listener resumes it in the app.
                    if (startPlaybackService()) {
                        playPrimary(target)
                        startProgress()
                    } else {
                        _state.update { it.copy(ended = true, isBuffering = false, intentPlaying = true) }
                    }
                }
                queuePageRetries = 0
                prefetchNext()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the existing playable timeline and try again with bounded backoff while the track plays.
                scheduleQueuePageRetry(generation)
            } finally {
                if (generation == queuePageGeneration) {
                    queuePageJob = null
                    _queuePageLoading.value = false
                    stopServiceIfQueueStillEnded()
                    // BG Mix may be waiting at the end of the radio for this page: apply its status again, so it either
                    // continues with the new tracks or reports the end once no retry is left.
                    if (bgMixActive()) bgMix?.status?.value?.let(::applyBgMix)
                }
            }
        }
    }

    private fun scheduleQueuePageRetry(generation: Long) {
        val endless = queueSource?.loadMore != null
        if (endless && AutoDownloadNetworkStatus.network.value == AutoDownloadNetwork.None) {
            queuePageRetryJob?.cancel()
            queuePageRetryJob = scope.launch {
                appContext?.let { AutoDownloadNetworkStatus.ensureStarted(it) }
                AutoDownloadNetworkStatus.network.first { it != AutoDownloadNetwork.None }
                if (generation == queuePageGeneration) {
                    queuePageRetries = 0
                    loadNextQueuePage()
                }
            }
            if (generation == queuePageGeneration && bgMixActive()) bgMix?.status?.value?.let(::applyBgMix)
            return
        }
        val pause = if (endless) endlessQueuePageRetryDelayMs(queuePageRetries) else queuePageRetryDelayMs(queuePageRetries)
        if (pause == null) {
            // No retry left: a manual Play/Next that never got its page must not leave the control spinning forever.
            if (endlessRecovery?.generation == generation) {
                endlessRecovery = null
                endlessWaitingForRecovery = false
                _state.update { it.copy(isBuffering = false, ended = it.item != null) }
            }
            // A BG Mix queue that waited for this page reports its end now.
            if (generation == queuePageGeneration && bgMixActive()) bgMix?.status?.value?.let(::applyBgMix)
            if (endless && generation == queuePageGeneration) {
                queuePageNetworkJob?.cancel()
                queuePageNetworkJob = scope.launch {
                    appContext?.let { AutoDownloadNetworkStatus.ensureStarted(it) }
                    AutoDownloadNetworkStatus.network.drop(1).first { it != AutoDownloadNetwork.None }
                    if (generation == queuePageGeneration) {
                        queuePageRetries = 0
                        loadNextQueuePage()
                    }
                }
            }
            return
        }
        queuePageRetries++
        queuePageRetryJob?.cancel()
        queuePageRetryJob = scope.launch {
            delay(pause)
            if (generation == queuePageGeneration) loadNextQueuePage()
        }
        if (endless && queuePageRetries > ENDLESS_AWAIT_FAST_RETRIES && generation == queuePageGeneration && bgMixActive()) {
            bgMix?.status?.value?.let(::applyBgMix)
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
     * The track that plays after the current one in real play order (BG Mix queue order, or ExoPlayer's order with shuffle
     * and repeat), with its local path when its file is resolved; null when there is none or it is private. Main thread only.
     */
    fun nextInPlayOrder(): PlaybackItem? {
        val state = _state.value
        val current = state.item ?: return null
        if (current.systemPrivate || state.queue.isEmpty()) return null
        val next = (
            if (bgMixActive()) {
                bgMix?.itemAt(state.queueIndex + 1)
            } else {
                val target = exo ?: return null
                val nextIndex = target.nextMediaItemIndex
                if (nextIndex == C.INDEX_UNSET) return null
                val nextMediaId = target.getMediaItemAt(nextIndex).mediaId
                state.queue.firstOrNull { it.systemMediaId == nextMediaId }
            }
        ) ?: return null
        if (next.systemPrivate || next.key == current.key) return null
        return withResolvedSource(next)
    }

    /**
     * The adjacent occurrence in the real play order for the artwork swipe: direction < 0 is the next track, > 0 the
     * previous one. It never restarts the current track, and the edge of a queue with Repeat Off has no neighbour.
     * Main thread only; this one function feeds both the preview and the commit.
     */
    fun adjacentInPlayOrder(direction: Int): AdjacentPlayback? {
        val state = _state.value
        if (direction == 0 || state.item == null) return null
        val index = adjacentPlaybackQueueIndex(
            queueSize = state.queue.size,
            currentIndex = state.queueIndex,
            order = playbackOrderIndices(state),
            repeatMode = state.repeatMode,
            step = if (direction < 0) 1 else -1,
        ) ?: return null
        return state.queue.getOrNull(index)?.let { AdjacentPlayback(index, it) }
    }

    /**
     * An explicit swipe jump to the adjacent occurrence, independent of the Previous restart threshold. [expectedQueueIndex]
     * and [expectedOccurrenceId] are the occurrence the carousel showed; if the queue or playback order changed since,
     * nothing is played. Returns false when the jump was refused, so the UI can roll its preview back without waiting
     * for a timeout.
     */
    fun skipToAdjacent(direction: Int, expectedQueueIndex: Int, expectedOccurrenceId: String?): Boolean {
        val state = _state.value
        val current = state.item ?: return false
        if (direction == 0) return false
        val index = adjacentPlaybackQueueIndex(
            queueSize = state.queue.size,
            currentIndex = state.queueIndex,
            order = playbackOrderIndices(state),
            repeatMode = state.repeatMode,
            step = if (direction < 0) 1 else -1,
        ) ?: return false
        if (index != expectedQueueIndex) return false
        val target = state.queue.getOrNull(index) ?: return false
        if (expectedOccurrenceId != null && target.occurrenceId != expectedOccurrenceId) return false
        if (target.systemPrivate || current.systemPrivate) return false
        val resume = state.playing || state.intentPlaying || state.ended
        _manualSkips.update { it + 1 }
        if (bgMixActive()) {
            // A prepared row switches in the native session at once; any other one plays on the regular player and is
            // handed back to BG Mix when its file is ready. A paused queue stays paused on the new track.
            if (bgMix?.manualSelect(index) != true) {
                restartBgMix(state.queue, index, 0L, autoPlay = resume)
            }
            return true
        }
        val targetPlayer = exo ?: return false
        if (index >= targetPlayer.mediaItemCount) return false
        transitionToken++
        clearDeferredSeek()
        pendingStartFraction = 0f
        _state.update { it.copy(ended = false, failed = false) }
        targetPlayer.seekTo(index, 0L)
        if (targetPlayer.playbackState == Player.STATE_IDLE || targetPlayer.playbackState == Player.STATE_ENDED) {
            targetPlayer.prepare()
        }
        _state.update { it.copy(intentPlaying = resume).withTimelineNavigation(targetPlayer) }
        if (resume) {
            if (!current.systemPrivate) startPlaybackService()
            playPrimary(targetPlayer)
            startProgress()
        }
        loadNextQueuePage()
        prefetchNext()
        return true
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
            // Keep BgWaveHostTap fed solely by the primary player so the overlapping tail never double-pushes at 2x speed.
            .setRenderersFactory(BgWaveRenderersFactory(context, tapEnabled = false))
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
        return resolved.growing == null && resolved.hasReadyLocalCopy
    }

    /**
     * Requests at most two following tracks from the actual Media3 playback order. The auto-download policy
     * (Data Saver/roaming/size) gates this; the current item, when actually opened, is always requested by its
     * data source instead.
     */
    @OptIn(UnstableApi::class)
    private fun prefetchNext() {
        // BG Mix owns its own bounded feed; the ExoPlayer placeholder has no useful playback order.
        if (bgMixActive()) return
        // A provider queue has no TDLib source, but its rows still prefetch through the same policy.
        val source = queueSource
        val context = appContext ?: return
        val state = _state.value
        val playbackOrder = if (state.shuffleEnabled && exo != null) {
            runCatching { exo!!.shuffleOrder.toIndexOrder() }.getOrDefault(state.queue.indices.toList())
        } else {
            state.queue.indices.toList()
        }
        // While the current provider row is still being fetched, rows ahead of it wait: their downloads shared the
        // bandwidth and the request queue with it and pushed the first sound back by seconds. The resolve of the
        // current row calls this again (publishResolvedSource).
        val currentPending = state.item?.let(::lazyRowPending) == true
        val window = playbackFetchWindow(state, playbackOrder)
        val candidates = prefetchCandidates(state, playbackOrder)
        val keep = playbackRetentionKeys(state, playbackOrder).toMutableSet()
        state.item?.key?.let(keep::add)
        // A BG Mix handover may be preparing a deck outside the regular lookahead: keep its copy too.
        bgMix?.keysInUse()?.let(keep::addAll)
        if (YandexTrackResolver.evictOutside(keep)) {
            YandexTrackResolver.pruneUnused(context)
        }
        // Roles are published even when there is nothing to fetch: a stale unfinished fetch then frees its slot.
        LazyTrackRegistry.prioritize(window)
        val nextKey = window.next.firstOrNull()
        candidates.forEach { item ->
            if (currentPending && LazyTrackRegistry.isLazyKey(item.key)) return@forEach
            prefetchItem(context, source, item, playbackNext = item.key == nextKey)
        }
    }

    /** Queue indices in the order playback visits them: BG Mix's own order, or ExoPlayer's shuffle order. */
    private fun playbackOrderIndices(state: PlaybackState): List<Int> {
        if (state.queue.isEmpty()) return emptyList()
        if (!state.shuffleEnabled || bgMixActive()) return state.queue.indices.toList()
        val order = exo?.let { runCatching { it.shuffleOrder.toIndexOrder() }.getOrNull() }
        return order?.takeIf { it.size == state.queue.size } ?: state.queue.indices.toList()
    }

    /**
     * Tells the lazy resolver which rows playback needs now, with their real roles: the row that is opening, N+1 for
     * the next deck, N+2 as bounded lookahead, and N−1 only to keep its copy. An unfinished fetch for any other row has
     * no reader and frees its slot.
     */
    private fun reprioritizeLazyRows() {
        val state = _state.value
        if (state.item == null) return
        LazyTrackRegistry.prioritize(playbackFetchWindow(state, playbackOrderIndices(state)))
    }

    /**
     * Queue rows whose local copies are kept during playback: N−1 through N+[MUSIC_RETENTION_AHEAD] of the real
     * play order. Playback only downloads N+1 and N+2; the extra row keeps a just-played neighbour from being evicted
     * at the moment the window moves past it. This is the only offline cache for music; it is never the whole queue.
     */
    private fun playbackRetentionKeys(state: PlaybackState, playbackOrder: List<Int>): Set<String> {
        if (state.queue.isEmpty() || state.queueIndex !in state.queue.indices) return emptySet()
        val order = playbackOrder.filter { it in state.queue.indices }.distinct()
        val position = order.indexOf(state.queueIndex)
        val indices = if (position >= 0) {
            ((position - 1)..(position + MUSIC_RETENTION_AHEAD)).mapNotNull { order.getOrNull(it) }
        } else {
            (state.queueIndex..minOf(state.queue.lastIndex, state.queueIndex + MUSIC_RETENTION_AHEAD)).toList()
        }
        return indices.mapTo(HashSet(indices.size)) { state.queue[it].key }
    }

    /** A provider row (bot result or service track) whose file is not complete yet. */
    private fun lazyRowPending(item: PlaybackItem): Boolean {
        // The row names a growing copy while it downloads, so the provider decides, not the path.
        val lazy = LazyTrackRegistry.sourceFor(item.key) ?: return false
        return lazy.resolvedPath(item.key) == null && !lazy.failed(item.key)
    }

    /**
     * Fetches [item] ahead under the automatic-download policy, so Data Saver, roaming and size limits apply.
     * [playbackNext] marks the row that plays after the current one: a streamed row is part of playback, so only a
     * missing network stops it.
     */
    private fun prefetchItem(context: Context, source: PlaybackQueueSource?, item: PlaybackItem, playbackNext: Boolean = false) {
        run {
            val lazySource = LazyTrackRegistry.sourceFor(item.key)
            if (lazySource != null) {
                // A provider row is fetched only by its resolver. Its queue path may be stale after a cache eviction,
                // so only a copy the resolver itself can still read counts as ready.
                if (item.growing != null || lazySource.resolvedPath(item.key) != null) return@run
                if (item.hasReadyLocalCopy) return@run
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
                // The row right after the playing one is part of playback: it must start before ordinary speculation.
                if (allowed) {
                    if (playbackNext) lazySource.prefetchSoon(item.key) else lazySource.prefetch(item.key)
                }
                return@run
            }
            val fileId = item.fileId ?: return@run
            if (source == null || source.accountId != item.accountId) return@run
            // Speculative traffic must use the repository automatic path; the manual callback is reserved for
            // the track playback actually opens, so Data Saver/network cancellation can track prefetch downloads.
            if (source.autoDownload == null) return@run
            // The row's own path may be a stale claim after a cache eviction: only an existing copy skips the download.
            if (item.hasReadyLocalCopy) return@run
            val fileState = runCatching { source.observeFile(fileId).value }.getOrNull() ?: return@run
            if (fileState.isDownloading) return@run
            val observedCopy = fileState.localPath
            if (observedCopy != null && localCopyReady(observedCopy)) return@run
            // The remembered request no longer produced a copy (the file was removed from the device cache): ask again.
            if (item.key in prefetchedKeys) prefetchedKeys.remove(item.key)
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

/**
 * Bounded set of local file paths pinned against cache eviction during playback:
 * the playing item, its immediate predecessor, the lookahead prefetch window,
 * any active BG Mix paths, and an interrupted music track waiting behind a voice/video note.
 */
internal fun activeMusicOwnershipPaths(
    state: PlaybackState,
    interrupted: List<PlaybackItem> = emptyList(),
    bgMixPaths: Set<String> = emptySet(),
    behindCount: Int = 1,
    aheadCount: Int = 3,
): Set<String> {
    val result = HashSet<String>()
    state.item?.path?.takeIf { it.isNotBlank() }?.let { result.add(it) }
    if (state.queue.isNotEmpty()) {
        val anchor = state.queueIndex.coerceIn(0, state.queue.lastIndex)
        val start = (anchor - behindCount).coerceAtLeast(0)
        val endExclusive = (anchor + aheadCount + 1).coerceAtMost(state.queue.size)
        for (i in start until endExclusive) {
            state.queue[i].path.takeIf { it.isNotBlank() }?.let { result.add(it) }
        }
    }
    interrupted.forEach { it.path.takeIf { p -> p.isNotBlank() }?.let(result::add) }
    bgMixPaths.forEach { if (it.isNotBlank()) result.add(it) }
    return result
}
