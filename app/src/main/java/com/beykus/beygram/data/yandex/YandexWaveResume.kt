package com.beykus.beygram.data.yandex

import android.content.Context
import com.beykus.beygram.R
import com.beykus.beygram.data.music.provider.MusicProviderState
import com.beykus.beygram.data.music.provider.ProviderTrack
import com.beykus.beygram.data.music.provider.ProviderTracks
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackRadio
import com.beykus.beygram.playback.PlaybackState
import com.beykus.beygram.playback.musicSessionFresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Keeps «Моя волна» across restarts. While a wave plays, its seeds, current track, place and the tracks it already gave
 * are saved. After a restart the wave comes back paused on the same track and place, and «Продолжить» on the wave card
 * starts it again from there. Only one music session is kept, the last one played: another track or queue replaces it,
 * closing the player removes it, and so does signing out of the source. A saved wave older than a week is dropped.
 *
 * A wave belongs to the account it was started under. Signing out, or signing in as another account, ends the playing
 * wave (player queue and feedback), removes its save and forbids any further write; a save of another account is never
 * restored. The rules live in [YandexWaveAccountGuard].
 */
internal object YandexWaveResume {
    private const val PREFS = "yandex_wave_resume_v1"
    private const val KEY = "wave"
    private const val SAVE_INTERVAL_MS = 12_000L
    private const val POSITION_STEP_MS = 1_000L

    /** A saved wave: the account it belongs to, what it was built from, the current track and place, and the tracks it already gave. */
    data class Saved(
        val uid: String,
        val seeds: List<String>,
        val subtitle: String,
        val track: ProviderTrack,
        val positionMs: Long,
        val given: List<String>,
        val savedAtMs: Long,
    )

    /**
     * The wave that plays, for the sign-in [generation] (runtime) and the account [uid] (the saved place).
     * [sessionOf] gives the live session, which a settings change replaces.
     */
    private class Active(
        val generation: Long,
        val uid: String,
        val sessionOf: () -> YandexWaveSession,
        val radio: PlaybackRadio,
        var seeds: List<String>,
        var subtitle: String,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val savedState = MutableStateFlow<Saved?>(null)

    /** The saved wave, or null when there is none. */
    val saved: StateFlow<Saved?> = savedState.asStateFlow()

    /**
     * The saved wave while the wave card should offer «Продолжить»: nothing is loaded, or the saved track is loaded and
     * paused. Null while another track plays or the wave plays.
     */
    val offered: StateFlow<Saved?> = combine(
        savedState,
        PlaybackEngine.state.map { it.item?.key to it.playing }.distinctUntilChanged(),
    ) { saved, (key, playing) ->
        saved?.takeIf { !playing && (key == null || key == it.track.key) }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private var appContext: Context? = null
    private var provider: YandexMusicProvider? = null
    private var active: Active? = null
    private var watching = false
    private var wasInWave = false
    private var restoredThisProcess = false
    private var lastKey: String? = null
    private var lastPlaying = false
    private var lastPositionSecond = -1L
    private var lastWriteAt = 0L

    /** Starts following the Yandex source: its saved wave is read now, comes back paused on sign-in, and sign-out removes it. */
    fun watchProvider(context: Context, provider: YandexMusicProvider) {
        val app = context.applicationContext
        appContext = app
        this.provider = provider
        savedState.value = read(app)
        ensureWatching()
        scope.launch {
            var signedIn = false
            provider.state.collect { state ->
                // First: a wave of another account (or of nobody) ends before anything else can write or restore.
                if (activeWaveIsStale()) endActiveWave()
                when (state) {
                    is MusicProviderState.SignedIn -> {
                        signedIn = true
                        val foreign = savedState.value?.takeIf { it.uid != state.account.uid }
                        if (foreign != null) clearSaved()
                        restoreIfIdle()
                    }
                    is MusicProviderState.SignedOut -> {
                        if (signedIn) clearSaved()
                        signedIn = false
                    }
                }
            }
        }
    }

    /** Called when a wave starts: from now on its place is saved while it plays. */
    fun attach(context: Context, generation: Long, sessionOf: () -> YandexWaveSession, radio: PlaybackRadio, seeds: List<String>, subtitle: String) {
        // The wave belongs to the sign-in it started under; if that is gone already, or nobody is signed in, nothing is attached.
        val source = provider ?: return
        // One snapshot, so the uid and the generation always belong to the same sign-in.
        val identity = source.accountIdentity() ?: return
        if (YandexWaveAccountGuard.shouldEndWave(generation, identity.generation)) return
        val uid = identity.uid
        appContext = context.applicationContext
        active = Active(generation, uid, sessionOf, radio, seeds, subtitle)
        ensureWatching()
    }

    /** The playing wave got new settings: its saved seeds and caption follow, so a restart plays the new choice. */
    fun settingsChanged(seeds: List<String>, subtitle: String) {
        val current = active ?: return
        current.seeds = seeds
        current.subtitle = subtitle
    }

    /** Continues the saved wave from its place: restores it first when nothing is loaded yet. */
    fun resume() {
        restoreIfIdle()
        val key = savedState.value?.track?.key ?: return
        if (PlaybackEngine.state.value.item?.key != key) return
        PlaybackEngine.resume(key) {}
    }

    /** True when the playing wave belongs to a sign-in that is gone (signed out, or signed in again, even as the same account). */
    private fun activeWaveIsStale(): Boolean {
        val current = active ?: return false
        return YandexWaveAccountGuard.shouldEndWave(current.generation, provider?.accountGeneration())
    }

    /**
     * Ends the wave of the sign-in that is gone: no more saves first, then the saved place, the feedback and the
     * player's radio queue (so it asks for no more tracks). Feedback stops before the player does, so the queue
     * closing is not reported to the service as a skip.
     */
    private fun endActiveWave() {
        val current = active ?: return
        active = null
        wasInWave = false
        clearSaved()
        YandexWaveFeedback.stop(current.radio)
        if (PlaybackEngine.activeRadio === current.radio) PlaybackEngine.stop()
    }

    private fun ensureWatching() {
        if (watching) return
        watching = true
        scope.launch { PlaybackEngine.state.collect { onState(it) } }
    }

    private fun onState(state: PlaybackState) {
        val item = state.item
        if (item == null) {
            // Closing the player ends the wave for good, so its saved place goes with it.
            if (wasInWave) clearSaved()
            wasInWave = false
            return
        }
        val current = active
        if (current != null && PlaybackEngine.activeRadio === current.radio) {
            if (activeWaveIsStale()) {
                endActiveWave()
                return
            }
            wasInWave = true
            save(current, state, item)
        } else if (item.isMusic) {
            // Any other track or queue replaces the wave: only the last music played is kept.
            wasInWave = false
            if (savedState.value != null) clearSaved()
        }
    }

    private fun save(current: Active, state: PlaybackState, item: PlaybackItem) {
        if (!YandexWaveAccountGuard.canSave(current.generation, provider?.accountGeneration())) {
            endActiveWave()
            return
        }
        val track = ProviderTracks.byKey(item.key) ?: return
        val now = System.currentTimeMillis()
        val second = state.positionMs / POSITION_STEP_MS
        val changed = item.key != lastKey || state.playing != lastPlaying ||
            (!state.playing && second != lastPositionSecond)
        val periodic = state.playing && now - lastWriteAt >= SAVE_INTERVAL_MS
        if (!changed && !periodic) return
        lastKey = item.key
        lastPlaying = state.playing
        lastPositionSecond = second
        lastWriteAt = now
        val context = appContext ?: return
        val value = Saved(
            uid = current.uid,
            seeds = current.seeds,
            subtitle = current.subtitle,
            track = track,
            positionMs = state.positionMs.coerceIn(0L, YandexWaveSavedCodec.MAX_POSITION_MS),
            given = current.sessionOf().givenIds(),
            savedAtMs = now,
        )
        write(context, value)
        savedState.value = value
    }

    private fun restoreIfIdle() {
        if (restoredThisProcess) return
        val app = appContext ?: return
        val yandex = provider ?: return
        val saved = savedState.value ?: return
        // Signed out, the saved wave waits for the sign-in.
        val identity = yandex.accountIdentity() ?: return
        if (identity.uid != saved.uid) {
            // Another account's wave is never offered: it goes away.
            clearSaved()
            return
        }
        if (PlaybackEngine.state.value.item != null) return
        // The restored wave is bound to the sign-in that is current now; its saved uid was checked above.
        val generation = identity.generation
        restoredThisProcess = true
        if (!saved.track.isAvailable) {
            clearSaved()
            return
        }
        ProviderTracks.remember(listOf(saved.track))
        val session = YandexWaveSession(
            seeds = saved.seeds,
            start = { s, queue -> yandex.waveStart(generation, s, queue) },
            next = { id, queue -> yandex.waveNext(generation, id, queue) },
            initialGiven = saved.given,
        )
        session.seed(saved.track)
        val radio = PlaybackRadio(title = app.getString(R.string.yandex_wave_title), subtitle = saved.subtitle)
        active = Active(generation, saved.uid, { session }, radio, saved.seeds, saved.subtitle)
        // Paused: nothing plays and no request goes out until the person presses Play.
        PlaybackEngine.restoreYandexRadio(app, saved.track, saved.positionMs, { session.more() }, radio)
        YandexWaveFeedback.start(yandex, generation, session, radio)
    }

    private fun clearSaved() {
        savedState.value = null
        lastKey = null
        lastPlaying = false
        lastPositionSecond = -1L
        lastWriteAt = 0L
        appContext?.let { prefs(it).edit().remove(KEY).apply() }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun write(context: Context, value: Saved) {
        prefs(context).edit().putString(KEY, YandexWaveSavedCodec.serialize(value)).apply()
    }

    /** The saved wave, or null. A record that cannot be read or is too old is removed. */
    private fun read(context: Context): Saved? {
        val raw = prefs(context).getString(KEY, null) ?: return null
        val saved = runCatching { YandexWaveSavedCodec.parse(raw) }.getOrNull()
        if (saved == null || !musicSessionFresh(saved.savedAtMs, System.currentTimeMillis())) {
            prefs(context).edit().remove(KEY).apply()
            return null
        }
        return saved
    }
}
