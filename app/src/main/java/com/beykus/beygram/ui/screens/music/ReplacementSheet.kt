package com.beykus.beygram.ui.screens.music

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.ui.components.sheetSurfaceColor
import com.beykus.beygram.R
import com.beykus.beygram.data.music.MUSIC_REPLACEMENT_LOOKUP_BATCH
import com.beykus.beygram.data.music.MusicLibraries
import com.beykus.beygram.data.music.MusicOutcome
import com.beykus.beygram.data.music.MusicTrackRef
import com.beykus.beygram.data.music.PlaylistTrack
import com.beykus.beygram.data.music.replacementCandidatesFor
import com.beykus.beygram.data.music.resolvePlaylistTracks
import com.beykus.beygram.data.music.toTrackRef
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.launch

/** The playable candidates found so far, and whether every candidate has been checked. */
private data class ReplacementLookup(val playable: List<PlaylistTrack>, val done: Boolean)

/**
 * Offers the tracks of the library that can take the place of [missing], an entry of [playlistId] that cannot be played now.
 * Only candidates that Telegram can still play are listed, and never one that the playlist already lists under another entry.
 * The candidates are checked a batch at a time, so the playable ones show as they are found. Picking one replaces the entry in
 * place; nothing is replaced on its own. When no candidate is left, the sheet says so and offers the search instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReplacementSheet(
    accountId: String,
    telegram: TelegramRepository,
    playlistId: String,
    missing: PlaylistTrack,
    onSearch: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { MusicLibraries.get(context) }
    val library = rememberMusicLibrary(accountId)
    val candidates = remember(library, missing.itemId) {
        val lib = library
        val playlist = lib?.playlist(playlistId)
        if (lib != null && playlist != null) {
            replacementCandidatesFor(lib, playlist, missing.itemId, missing.toTrackRef())
        } else {
            emptyList<MusicTrackRef>()
        }
    }
    // Each batch is checked against Telegram before it is shown, so a track that cannot be played never appears. A batch runs at
    // most three lookups at once (see resolveMusicOrigins).
    val lookup by produceState(ReplacementLookup(emptyList(), done = false), candidates) {
        value = ReplacementLookup(emptyList(), done = false)
        val found = mutableListOf<PlaylistTrack>()
        for (batch in candidates.map { it.toPlaylistEntry() }.chunked(MUSIC_REPLACEMENT_LOOKUP_BATCH)) {
            val resolved = resolvePlaylistTracks(telegram, accountId, batch)
            batch.forEachIndexed { index, entry ->
                if (resolved.getOrNull(index) != null) {
                    found += entry
                }
            }
            value = ReplacementLookup(found.toList(), done = false)
        }
        value = ReplacementLookup(found.toList(), done = true)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = sheetSurfaceColor()) {
        // Scrolls, so a long list of candidates stays reachable.
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)
        ) {
            Text(
                stringResource(R.string.music_find_replacement),
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            Text(
                stringResource(R.string.music_replacement_hint),
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            val found = lookup.playable
            if (library == null || (found.isEmpty() && !lookup.done)) {
                MusicLoadingRow()
            } else if (found.isEmpty()) {
                MusicEmptyText(stringResource(R.string.music_replacement_none))
                TextButton(
                    onClick = {
                        onDismiss()
                        onSearch()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text(stringResource(R.string.music_replacement_search), color = palette.accent)
                }
            } else {
                found.forEach { candidate ->
                    MusicTrackRow(
                        track = candidate,
                        unavailable = false,
                        onClick = {
                            scope.launch {
                                when (val outcome = repository.replaceTrack(playlistId, missing.itemId, candidate)) {
                                    is MusicOutcome.Failed -> context.reportMusicOutcome(outcome)
                                    is MusicOutcome.Done -> {
                                        context.showMusicToast(context.getString(R.string.music_replacement_done))
                                        onDismiss()
                                    }
                                }
                            }
                        }
                    )
                }
            }
            // Some candidates are still being checked, so more may follow the ones shown.
            if (library != null && found.isNotEmpty() && !lookup.done) MusicLoadingRow()
        }
    }
}
