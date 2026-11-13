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
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps «Моя волна» across restarts. While a wave plays, its seeds, current track, place and the tracks it already gave
 * are saved. After a restart the wave comes back paused on the same track and place, and «Продолжить» on the wave card
 * starts it again from there. Only one music session is kept, the last one played: another track or queue replaces it,
 * closing the player removes it, and so does signing out of the source. A saved wave older than a week is dropped.
 */
internal object YandexWaveResume {
    private const val PREFS = "yandex_wave_resume_v1"
    private const val KEY = "wave"
    private const val VERSION = 1
    private const val SAVE_INTERVAL_MS = 12_000L
    private const val POSITION_STEP_MS = 1_000L
    private const val MAX_POSITION_MS = 86_400_000L

    /** A saved wave: what it was built from, the current track and place, and the tracks it already gave. */
    data class Saved(
        val seeds: List<String>,
        val subtitle: String,
        val track: ProviderTrack,
        val positionMs: Long,
        val given: List<String>,
        val savedAtMs: Long,
    )

    private class Active(
        val session: YandexWaveSession,
        val radio: PlaybackRadio,
        val seeds: List<String>,
        val subtitle: String,
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
                when (state) {
                    is MusicProviderState.SignedIn -> {
                        signedIn = true
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
    fun attach(context: Context, session: YandexWaveSession, radio: PlaybackRadio, seeds: List<String>, subtitle: String) {
        appContext = context.applicationContext
        active = Active(session, radio, seeds, subtitle)
        ensureWatching()
    }

    /** Continues the saved wave from its place: restores it first when nothing is loaded yet. */
    fun resume() {
        restoreIfIdle()
        val key = savedState.value?.track?.key ?: return
        if (PlaybackEngine.state.value.item?.key != key) return
        PlaybackEngine.resume(key) {}
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
            wasInWave = true
            save(current, state, item)
        } else if (item.isMusic) {
            // Any other track or queue replaces the wave: only the last music played is kept.
            wasInWave = false
            if (savedState.value != null) clearSaved()
        }
    }

    private fun save(current: Active, state: PlaybackState, item: PlaybackItem) {
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
            seeds = current.seeds,
            subtitle = current.subtitle,
            track = track,
            positionMs = state.positionMs.coerceIn(0L, MAX_POSITION_MS),
            given = current.session.givenIds(),
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
        if (PlaybackEngine.state.value.item != null) return
        restoredThisProcess = true
        if (!saved.track.isAvailable) {
            clearSaved()
            return
        }
        ProviderTracks.remember(listOf(saved.track))
        val session = YandexWaveSession(
            seeds = saved.seeds,
            start = { s, queue -> yandex.waveStart(s, queue) },
            next = { id, queue -> yandex.waveNext(id, queue) },
            initialGiven = saved.given,
        )
        session.seed(saved.track)
        val radio = PlaybackRadio(title = app.getString(R.string.yandex_wave_title), subtitle = saved.subtitle)
        active = Active(session, radio, saved.seeds, saved.subtitle)
        // Paused: nothing plays and no request goes out until the person presses Play.
        PlaybackEngine.restoreYandexRadio(app, saved.track, saved.positionMs, { session.more() }, radio)
        YandexWaveFeedback.start(yandex, session, radio)
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
        val json = JSONObject()
            .put("version", VERSION)
            .put("saved_at", value.savedAtMs)
            .put("seeds", JSONArray(value.seeds))
            .put("subtitle", value.subtitle)
            .put("track", trackToJson(value.track))
            .put("position", value.positionMs)
            .put("given", JSONArray(value.given.takeLast(YandexWave.QUEUE_HINT)))
        prefs(context).edit().putString(KEY, json.toString()).apply()
    }

    /** The saved wave, or null. A record that cannot be read or is too old is removed. */
    private fun read(context: Context): Saved? {
        val raw = prefs(context).getString(KEY, null) ?: return null
        val saved = runCatching { parse(raw) }.getOrNull()
        if (saved == null || !musicSessionFresh(saved.savedAtMs, System.currentTimeMillis())) {
            prefs(context).edit().remove(KEY).apply()
            return null
        }
        return saved
    }

    private fun parse(raw: String): Saved {
        val json = JSONObject(raw)
        require(json.getInt("version") == VERSION)
        val seeds = json.getJSONArray("seeds").strings()
        require(seeds.isNotEmpty())
        return Saved(
            seeds = seeds,
            subtitle = json.getString("subtitle"),
            track = trackFromJson(json.getJSONObject("track")),
            positionMs = json.getLong("position").coerceIn(0L, MAX_POSITION_MS),
            given = json.getJSONArray("given").strings().takeLast(YandexWave.QUEUE_HINT),
            savedAtMs = json.getLong("saved_at"),
        )
    }

    private fun trackToJson(track: ProviderTrack): JSONObject = JSONObject()
        .put("id", track.trackId)
        .put("title", track.title)
        .put("artists", JSONArray(track.artists))
        .put("duration", track.durationMs)
        .put("available", track.isAvailable)
        .apply {
            track.albumTitle?.let { put("album", it) }
            track.albumId?.let { put("albumId", it) }
            track.coverUrl?.let { put("cover", it) }
        }

    private fun trackFromJson(json: JSONObject): ProviderTrack = ProviderTrack(
        trackId = json.getString("id"),
        title = json.getString("title"),
        artists = json.getJSONArray("artists").strings(),
        albumTitle = json.optStringOrNull("album"),
        durationMs = json.getLong("duration"),
        coverUrl = json.optStringOrNull("cover"),
        isAvailable = json.getBoolean("available"),
        albumId = json.optStringOrNull("albumId"),
    )

    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

    private fun JSONObject.optStringOrNull(name: String): String? = if (has(name)) getString(name) else null
}
