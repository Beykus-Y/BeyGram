package com.beykus.beygram.data.music.provider

import android.content.Context
import java.io.File
import com.beykus.beygram.playback.PlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Covers of source tracks in the player, the mini-player, the notification and the lock screen. Source rows carry no
 * file and no cover, so the cover is downloaded when a track becomes current and handed to the player through
 * [PlaybackEngine.updateArtwork], which changes only the metadata and leaves the playhead alone.
 */
object ProviderArtwork {
    /** Size of the player cover, in pixels. Lists use the smaller one the source sends. */
    const val PLAYER_COVER_PX = 400

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching = false

    /** Plays source [tracks] from [startIndex], as [PlaybackEngine.playYandexTracks] does, with covers in the player. */
    fun playTracks(context: Context, tracks: List<ProviderTrack>, startIndex: Int) = playTracks(context, tracks, startIndex, null)

    /**
     * Same, with an endless queue: [loadMore] returns the next tracks (empty when there are none yet). Loaded tracks
     * are remembered too, so the player menu and covers work for them.
     */
    fun playTracks(
        context: Context,
        tracks: List<ProviderTrack>,
        startIndex: Int,
        loadMore: (suspend () -> List<ProviderTrack>)?,
    ) {
        remember(tracks)
        watch(context)
        val remembering = loadMore?.let { load -> suspend { load().also { remember(it) } } }
        PlaybackEngine.playYandexTracks(context, tracks, startIndex, remembering)
    }

    private fun remember(tracks: List<ProviderTrack>) = ProviderTracks.remember(tracks)

    /**
     * Follows the current item once per process and fetches the cover of each source track that becomes current, and
     * clears the cover cache on sign-out. Lists that show covers call it too, so the cache is cleared even before
     * anything was played.
     */
    fun watch(context: Context) {
        synchronized(this) {
            if (watching) return
            watching = true
        }
        val app = context.applicationContext
        scope.launch {
            PlaybackEngine.state
                .map { state -> state.item?.let { item -> item.key to item.artworkPath } }
                .distinctUntilChangedBy { it?.first }
                .collect { current ->
                    val (key, path) = current ?: return@collect
                    val url = ProviderTracks.byKey(key)?.coverUrl ?: return@collect
                    launch {
                        // A cover the cache has since deleted is fetched again, not left as a dead path.
                        if (path != null && withContext(Dispatchers.IO) { File(path).isFile }) return@launch
                        val file = ProviderCovers.file(app, ProviderCovers.sized(url, PLAYER_COVER_PX))
                            ?: ProviderCovers.file(app, url)
                            ?: return@launch
                        withContext(Dispatchers.Main) { PlaybackEngine.updateArtwork(key, file.absolutePath) }
                    }
                }
        }
        // Signing out of the source removes its covers from this device.
        MusicProviders.yandex(app)?.let { provider ->
            scope.launch {
                provider.state.collect { state ->
                    if (state is MusicProviderState.SignedOut) {
                        ProviderTracks.clearLikes()
                        ProviderCovers.clear(app)
                    }
                }
            }
        }
    }
}
