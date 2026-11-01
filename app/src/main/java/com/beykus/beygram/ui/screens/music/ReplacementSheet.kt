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
import com.beykus.beygram.R
import com.beykus.beygram.data.music.MusicLibraries
import com.beykus.beygram.data.music.MusicOutcome
import com.beykus.beygram.data.music.PlaylistTrack
import com.beykus.beygram.data.music.replacementCandidates
import com.beykus.beygram.data.music.resolvePlaylistTracks
import com.beykus.beygram.data.music.toTrackRef
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.launch

/**
 * Offers the tracks of the library that can take the place of [missing], an entry of [playlistId] that cannot be played now.
 * Only candidates that Telegram can still play are listed. Picking one replaces the entry in place; nothing is replaced on
 * its own. When no candidate is left, the sheet says so and offers the search instead.
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
        library?.let { replacementCandidates(it, missing.toTrackRef()) }.orEmpty()
    }
    // Every candidate is looked up before it is offered, so a track that Telegram cannot play never appears in the list.
    val playable by produceState<List<PlaylistTrack>?>(null, candidates) {
        value = null
        val entries = candidates.map { it.toPlaylistEntry() }
        val resolved = resolvePlaylistTracks(telegram, accountId, entries)
        value = entries.filterIndexed { index, _ -> resolved.getOrNull(index) != null }
    }
    val found = playable

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.surface) {
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
            if (library == null || found == null) {
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
        }
    }
}
