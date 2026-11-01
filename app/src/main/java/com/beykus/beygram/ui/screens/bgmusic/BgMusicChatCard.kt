package com.beykus.beygram.ui.screens.bgmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.music.bgmusic.BgMusicCacheKey
import com.beykus.beygram.data.music.bgmusic.BgMusicFileParser
import com.beykus.beygram.data.music.bgmusic.BgMusicParsed
import com.beykus.beygram.data.music.bgmusic.BgMusicReader
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.playback.LocalPlaybackAccountId
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.LocalMediaImage
import com.beykus.beygram.ui.screens.chat.formatMediaSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Opens the local playlist with the given id in the app's playlist screen. The app provides it; without a provider the preview
 * offers no open action.
 */
val LocalOpenMusicPlaylist = compositionLocalOf<((playlistId: String) -> Unit)?> { null }

/** Whether a document message is a `.bgmusic` file, judged from its name or MIME type before any byte is read. */
internal fun isBgMusicDocument(media: MessageMedia): Boolean =
    media.kind == MediaKind.Document && BgMusicReader.looksLikeCandidate(media.fileName, media.mimeType)

/** Total length as h:mm:ss or m:ss. Unknown durations (0) are counted as nothing. */
internal fun bgMusicDurationText(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%d:%02d".format(minutes, rest)
}

/**
 * A `.bgmusic` document inside the chat bubble. Once the file is on the device it is read on Dispatchers.IO and the result is
 * kept per account, file id and size. A valid playlist shows as a card: its body opens [BgMusicPreviewScreen], and the
 * body is the message's own action area when selection is on. An unsupported file keeps the ordinary document row with a
 * note. A broken file shows the ordinary document row.
 */
@Composable
internal fun BgMusicChatDocument(
    media: MessageMedia,
    file: MediaFileState,
    downloadFile: (Int) -> Unit,
    selectionActive: Boolean,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)?,
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val accountId = LocalPlaybackAccountId.current
    val openPlaylist = LocalOpenMusicPlaylist.current
    val localPath = file.localPath
    val key = remember(accountId, media.fileId, file.size) { BgMusicCacheKey(accountId, media.fileId, file.size) }
    var parsed by remember(key) { mutableStateOf(BgMusicFileParser.cached(key)) }
    var previewing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(key, localPath) {
        if (parsed == null && localPath != null) {
            parsed = withContext(Dispatchers.IO) { BgMusicFileParser.parse(context, key, File(localPath)) }
        }
    }
    val current = parsed
    when (current) {
        is BgMusicParsed.Playlist -> BgMusicPlaylistCard(
            playlist = current,
            selectionActive = selectionActive,
            onPreview = { previewing = true },
            onOpen = onOpen,
            onLongPress = onLongPress,
        )
        is BgMusicParsed.Unsupported -> Column {
            fallback()
            Text(
                stringResource(R.string.bgmusic_unsupported_version),
                color = palette.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
        is BgMusicParsed.Invalid -> fallback()
        null -> BgMusicPendingCard(
            media = media,
            file = file,
            downloadFile = downloadFile,
            selectionActive = selectionActive,
            onOpen = onOpen,
            onLongPress = onLongPress,
        )
    }
    if (previewing && current is BgMusicParsed.Playlist) {
        BgMusicPreviewScreen(
            parsed = current,
            onDismiss = { previewing = false },
            onOpenLocalPlaylist = openPlaylist?.let { open ->
                { playlistId: String ->
                    // The preview closes before the app leaves the chat, so it is not still open when the chat comes back.
                    previewing = false
                    open(playlistId)
                }
            },
        )
    }
}

/** The card of a readable playlist: cover, title, track count and length, and the button that opens the preview. */
@Composable
private fun BgMusicPlaylistCard(
    playlist: BgMusicParsed.Playlist,
    selectionActive: Boolean,
    onPreview: () -> Unit,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)?,
) {
    val palette = beyGramPalette()
    val manifest = playlist.manifest
    val trackCount = manifest.tracks.size
    val totalSeconds = manifest.tracks.sumOf { it.durationSec }
    val openLabel = stringResource(R.string.bgmusic_open_collection)
    val longPressLabel = stringResource(R.string.a11y_message_actions)
    val tap: () -> Unit = { if (selectionActive) onOpen() else onPreview() }
    Column(
        Modifier
            .width(244.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics { role = Role.Button }
            .combinedClickable(
                onClickLabel = openLabel,
                onLongClickLabel = longPressLabel,
                onClick = tap,
                onLongClick = onLongPress,
            )
            .padding(top = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CollectionCover(playlist.coverPath)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    manifest.title,
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(R.plurals.bgmusic_tracks_count, trackCount, trackCount) +
                        if (totalSeconds > 0L) " · " + bgMusicDurationText(totalSeconds) else "",
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Not clickable of its own: the card takes the tap and the long press, so both reach it from the whole card.
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(palette.accentSoft)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                openLabel,
                color = palette.accentInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Cover of a collection: the app's own copy when there is one, otherwise a note icon. */
@Composable
private fun CollectionCover(coverPath: String?) {
    val palette = beyGramPalette()
    val shape = RoundedCornerShape(8.dp)
    // The cache file goes with its cache entry, so a cover file that is gone shows the icon instead.
    val cover = remember(coverPath) { coverPath?.takeIf { File(it).isFile } }
    if (cover != null) {
        LocalMediaImage(
            path = cover,
            modifier = Modifier.size(44.dp).clip(shape),
            maxDimension = 256,
        )
    } else {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = palette.accentInk,
            modifier = Modifier.size(44.dp).background(palette.accentSoft, shape).padding(10.dp),
        )
    }
}

/**
 * The bubble while the file is not read yet: a download control before the file is on the device, and a spinner while it is
 * being read. The body tap downloads the file, or selects the message in selection mode.
 */
@Composable
private fun BgMusicPendingCard(
    media: MessageMedia,
    file: MediaFileState,
    downloadFile: (Int) -> Unit,
    selectionActive: Boolean,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)?,
) {
    val palette = beyGramPalette()
    val downloaded = file.localPath != null
    val name = media.fileName.ifBlank { stringResource(R.string.media_kind_file) }
    val downloadLabel = stringResource(R.string.bgmusic_download)
    val longPressLabel = stringResource(R.string.a11y_message_actions)
    Column(
        Modifier
            .width(244.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClickLabel = downloadLabel,
                onLongClickLabel = longPressLabel,
                onClick = {
                    when {
                        selectionActive -> onOpen()
                        !downloaded && !file.isDownloading -> downloadFile(media.fileId)
                        else -> Unit
                    }
                },
                onLongClick = onLongPress,
            )
            .padding(top = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = palette.accentInk,
                modifier = Modifier.size(44.dp).background(palette.accentSoft, CircleShape).padding(10.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(formatMediaSize(file.size), color = palette.textSecondary, fontSize = 13.sp)
                if (file.isDownloading) LinearProgressIndicator(
                    progress = { file.downloadProgress },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
            when {
                downloaded -> CircularProgressIndicator(
                    Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = palette.accent,
                )
                !file.isDownloading -> Icon(
                    Icons.Rounded.Download,
                    contentDescription = downloadLabel,
                    tint = palette.accentInk,
                )
            }
        }
    }
}
