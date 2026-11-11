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
    private val coverUrls = HashMap<String, String>()
    private var watching = false

    /** Plays source [tracks] from [startIndex], as [PlaybackEngine.playYandexTracks] does, with covers in the player. */
    fun playTracks(context: Context, tracks: List<ProviderTrack>, startIndex: Int) {
        remember(tracks)
        watch(context)
        PlaybackEngine.playYandexTracks(context, tracks, startIndex)
    }

    private fun remember(tracks: List<ProviderTrack>) {
        synchronized(coverUrls) {
            // Only the latest queues matter; an old queue's addresses go once the map grows large.
            if (coverUrls.size > MAX_REMEMBERED) coverUrls.clear()
            tracks.forEach { track -> track.coverUrl?.let { coverUrls[track.key] = it } }
        }
    }

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
                    val url = synchronized(coverUrls) { coverUrls[key] } ?: return@collect
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
                    if (state is MusicProviderState.SignedOut) ProviderCovers.clear(app)
                }
            }
        }
    }

    private const val MAX_REMEMBERED = 5_000
}
