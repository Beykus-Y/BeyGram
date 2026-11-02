package com.beykus.beygram.ui.screens.bgmusic

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import com.beykus.beygram.ui.components.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import com.beykus.beygram.ui.components.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.ui.components.sheetSurfaceColor
import com.beykus.beygram.MainActivity
import com.beykus.beygram.R
import com.beykus.beygram.data.music.MUSIC_COVER_DIRECTORY
import com.beykus.beygram.data.music.MusicPlaylist
import com.beykus.beygram.data.music.bgmusic.BgMusicExportOptions
import com.beykus.beygram.data.music.bgmusic.BgMusicExportResult
import com.beykus.beygram.data.music.bgmusic.BgMusicExporter
import com.beykus.beygram.data.music.bgmusic.BgMusicFormat
import com.beykus.beygram.data.music.bgmusic.BgMusicLibraryMapping
import com.beykus.beygram.data.music.bgmusic.BgMusicPublicLinks
import com.beykus.beygram.domain.model.ProfileState
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.playback.LocalPlaybackAccountId
import com.beykus.beygram.share.ShareSession
import com.beykus.beygram.share.ShareSessions
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.formatMediaSize
import com.beykus.beygram.ui.screens.playback.LocalPlaybackRepositorySource
import com.beykus.beygram.ui.screens.share.ShareTargetScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

private val NoProfile: StateFlow<ProfileState> = MutableStateFlow(ProfileState())

/**
 * Export of [playlist] as a `.bgmusic` file, for a chat or for another app. Both privacy switches are off by default. The file
 * is rebuilt whenever a switch changes, so the size shown always matches the file that is sent or shared.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BgMusicExportSheet(playlist: MusicPlaylist, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val source = LocalPlaybackRepositorySource.current
    val repository = source?.repository
    // The chat source owns the account that public-link lookups and playback origins belong to.
    val accountId = source?.accountId ?: LocalPlaybackAccountId.current
    val coverRoot = remember(context) { File(context.filesDir, MUSIC_COVER_DIRECTORY) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val profile by (repository?.myProfile ?: NoProfile).collectAsStateWithLifecycle()
    val authorName = profile.info?.title.orEmpty().trim()
    val favoritesTitle = stringResource(R.string.bgmusic_favorites_name)
    val untitled = stringResource(R.string.bgmusic_untitled_playlist)
    val exportFailedText = stringResource(R.string.bgmusic_export_failed)
    val shareFailedText = stringResource(R.string.media_share_failed)
    val shareTitle = stringResource(R.string.bgmusic_export_share)
    val sentText = stringResource(R.string.bgmusic_export_sent)

    var includeLinks by rememberSaveable { mutableStateOf(false) }
    var includeAuthor by rememberSaveable { mutableStateOf(false) }
    val options = BgMusicExportOptions(
        includePublicLinks = includeLinks,
        includeAuthor = includeAuthor && authorName.isNotEmpty(),
        authorName = authorName,
    )

    var exported by remember { mutableStateOf<BgMusicExportResult?>(null) }
    // Non-zero while a chat send is open. Saved, so a rotation keeps the same share session (and its staged copies).
    var chatRequestId by rememberSaveable { mutableStateOf(0L) }

    LaunchedEffect(playlist, options, repository, accountId) {
        exported = null
        val publicChats = if (options.includePublicLinks && repository != null) {
            val chatIds = playlist.tracks.mapNotNull { track ->
                track.origin?.takeIf { it.accountId == accountId }?.chatId
            }
            BgMusicPublicLinks.usernamesFor(repository, chatIds)
        } else {
            emptyMap<Long, String>()
        }
        val nowMs = System.currentTimeMillis()
        exported = withContext(Dispatchers.IO) {
            val manifest = BgMusicLibraryMapping.toManifest(
                playlist = playlist,
                accountId = accountId,
                options = options,
                publicChats = publicChats,
                nowMs = nowMs,
                favoritesTitle = favoritesTitle,
                untitled = untitled,
            )
            BgMusicExporter.export(context, manifest, playlist.coverPath, coverRoot)
        }
    }

    val ready = exported as? BgMusicExportResult.Ready
    val chatUri = remember(ready?.file) {
        ready?.file?.let { file ->
            runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", file) }.getOrNull()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetSurfaceColor()) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text(
                stringResource(R.string.bgmusic_export_title),
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (playlist.isFavorites) favoritesTitle else playlist.title,
                color = palette.textSecondary,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            ExportOption(
                title = stringResource(R.string.bgmusic_export_option_links),
                note = stringResource(R.string.bgmusic_export_option_links_note),
                checked = includeLinks,
                enabled = repository != null,
                onChange = { includeLinks = it },
            )
            ExportOption(
                title = stringResource(R.string.bgmusic_export_option_author),
                note = stringResource(R.string.bgmusic_export_option_author_note),
                checked = includeAuthor && authorName.isNotEmpty(),
                enabled = authorName.isNotEmpty(),
                onChange = { includeAuthor = it },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.bgmusic_export_privacy_note),
                color = palette.textSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))
            when (val result = exported) {
                null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = palette.accent)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.bgmusic_export_preparing), color = palette.textSecondary, fontSize = 13.sp)
                }
                is BgMusicExportResult.Ready -> Text(
                    stringResource(R.string.bgmusic_export_size, formatMediaSize(result.file.length())),
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                )
                BgMusicExportResult.Failed -> Text(exportFailedText, color = palette.danger, fontSize = 13.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (chatUri != null) {
                            chatRequestId = System.currentTimeMillis()
                        } else {
                            Toast.makeText(context, exportFailedText, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = ready != null && repository != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.bgmusic_export_send_chat))
                }
                OutlinedButton(
                    onClick = {
                        val file = ready?.file
                        if (file != null && !shareExport(context, file, shareTitle)) {
                            Toast.makeText(context, shareFailedText, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = ready != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(shareTitle)
                }
            }
        }
    }

    if (chatRequestId != 0L && chatUri != null && repository != null) {
        val requestId = chatRequestId
        val session = remember(requestId, accountId) {
            ShareSessions.of(context, requestId, listOf(chatUri), "", accountId)
        }
        ChatSendDialog(session = session, repository = repository) { sent ->
            chatRequestId = 0L
            ShareSessions.release(context, requestId)
            if (sent) {
                Toast.makeText(context, sentText, Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }
    }
}

/** One privacy switch with its explanation. The whole row toggles the switch. */
@Composable
private fun ExportOption(
    title: String,
    note: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val palette = beyGramPalette()
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox) { onChange(it) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) palette.textPrimary else palette.textSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(note, color = palette.textSecondary, fontSize = 12.sp)
        }
    }
}

/**
 * Chooses a chat for the archive through the same picker that handles incoming shares. The file travels as a document.
 * [onClose] gets true when the archive was sent. The share session is released by the caller, so unsent copies are removed.
 */
@Composable
private fun ChatSendDialog(
    session: ShareSession,
    repository: TelegramRepository,
    onClose: (sent: Boolean) -> Unit,
) {
    val chats by repository.chats.collectAsStateWithLifecycle()
    val archivedChats by repository.archivedChats.collectAsStateWithLifecycle()
    val captionLimit = repository.messageLimits.collectAsStateWithLifecycle().value.caption
    Dialog(
        onDismissRequest = { if (!session.sending) onClose(false) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = !session.sending,
            dismissOnClickOutside = !session.sending,
        ),
    ) {
        ShareTargetScreen(
            requestId = session.requestId,
            text = "",
            uris = session.uris,
            session = session,
            chats = chats + archivedChats,
            captionLimit = captionLimit,
            loadTopics = { chatId -> repository.forumTopics(chatId) },
            sendText = { chatId, topicId, text ->
                repository.sendMessage(chatId, text, forumTopicId = topicId, clearDraft = false)
            },
            sendMedia = { chatId, topicId, media, caption ->
                if (media.size == 1) {
                    repository.sendMedia(chatId, media.single(), caption, forumTopicId = topicId)
                } else {
                    repository.sendMediaAlbum(chatId, media, caption, forumTopicId = topicId)
                }
            },
            onSent = { _, _ -> onClose(true) },
            onCancel = { onClose(false) },
        )
    }
}

/**
 * Hands the archive to the system share sheet. BeyGram is left out of the targets, so the file is never sent back into the app.
 * Returns false when no app could take it.
 */
private fun shareExport(context: Context, file: File, title: String): Boolean = runCatching {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(BgMusicFormat.MIME_TYPE)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    val chooser = Intent.createChooser(send, title)
        .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf<Parcelable>(ComponentName(context, MainActivity::class.java)))
    context.startActivity(chooser)
}.isSuccess
