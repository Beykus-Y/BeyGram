package com.beykus.beygram.ui.screens.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R
import com.beykus.beygram.data.music.provider.ProviderArtwork
import com.beykus.beygram.data.music.provider.ProviderCovers
import com.beykus.beygram.data.music.provider.ProviderTrack

/** The cached file of a source cover, or null while it downloads or when there is none. */
@Composable
internal fun rememberProviderCover(url: String?): String? {
    val context = LocalContext.current
    val path by produceState<String?>(null, url) {
        value = null
        if (url.isNullOrBlank()) return@produceState
        ProviderArtwork.watch(context)
        value = ProviderCovers.file(context, url)?.absolutePath
    }
    return path
}

/** A track of an optional music source in the shared row: its cover, title, artists and length. */
@Composable
internal fun ProviderTrackRow(
    track: ProviderTrack,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit = {}
) {
    MusicTrackRowLayout(
        title = track.title,
        subtitle = if (track.isAvailable) track.performer else stringResource(R.string.music_unavailable),
        durationSeconds = (track.durationMs / 1_000L).toInt(),
        coverPath = rememberProviderCover(track.coverUrl),
        unavailable = !track.isAvailable,
        current = rememberIsCurrentItem(track.key),
        onClick = onClick,
        trailing = trailing
    )
}
