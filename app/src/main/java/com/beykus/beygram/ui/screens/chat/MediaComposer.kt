package com.beykus.beygram.ui.screens.chat

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.media.OutgoingMediaImporter
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Attachment flow state. The UI is inline (a panel above the input field), so the chat screen
 * owns this holder and renders [AttachmentButton] and [AttachmentPanel] in different places.
 */
internal enum class AttachmentPicker { None, Stickers, Gifs }

@Stable
internal class AttachmentState(
    private val context: Context,
    private val importer: OutgoingMediaImporter,
    private val scope: CoroutineScope,
    private val pendingState: MutableState<ArrayList<OutgoingMedia>>,
    private val errorState: MutableState<String?>,
    private val sendMedia: suspend (List<OutgoingMedia>, String, MessageSendOptions) -> Result<Unit>,
    private val sendSticker: suspend (OutgoingMedia, MessageSendOptions) -> Result<Unit>
) {
    var menuOpen by mutableStateOf(false)
    var importing by mutableStateOf(false)
        private set
    var sending by mutableStateOf(false)
        private set
    var picker by mutableStateOf(AttachmentPicker.None)
        private set
    val pending: List<OutgoingMedia> get() = pendingState.value
    val error: String? get() = errorState.value
    private var importJob: Job? = null
    var pickPhotos: () -> Unit = {}
    var pickDocuments: () -> Unit = {}

    /** True while the panel replaces the plain composer (files are being imported or are ready to send). */
    val active: Boolean get() = pending.isNotEmpty() || importing

    fun toggleMenu() {
        if (sending || importing) return
        if (picker != AttachmentPicker.None) {
            picker = AttachmentPicker.None
            menuOpen = false
        } else menuOpen = !menuOpen
    }

    fun openPicker(target: AttachmentPicker) {
        if (sending || importing || active) return
        errorState.value = null
        picker = target
        menuOpen = false
    }

    fun closePicker() {
        if (sending) return
        picker = AttachmentPicker.None
        errorState.value = null
    }

    /** A GIF becomes a normal pending attachment, so the caption limit, reply, topic and send options all apply. */
    fun addAnimation(media: OutgoingMedia) {
        if (sending || importing) return
        if (pending.size >= MAX_PENDING_ATTACHMENTS) {
            errorState.value = context.getString(R.string.composer_too_many_attachments)
            return
        }
        errorState.value = null
        pendingState.value = ArrayList(pending + media)
        picker = AttachmentPicker.None
        menuOpen = false
    }

    /** Stickers send immediately, like Telegram: no caption and no pending tray, but the same reply and topic. */
    fun sendStickerNow(media: OutgoingMedia) {
        if (sending || importing) return
        scope.launch {
            sending = true
            errorState.value = null
            try {
                val result = sendSticker(media, MessageSendOptions())
                if (result.isFailure) errorState.value = result.exceptionOrNull().userMessage(context, R.string.sticker_picker_send_failed)
            } finally { sending = false }
        }
    }

    /** Sends a sticker with explicit options chosen from the send-options dialog (silent/scheduled). */
    fun sendStickerWithOptions(media: OutgoingMedia, options: MessageSendOptions) {
        if (sending || importing) return
        scope.launch {
            sending = true
            errorState.value = null
            try {
                val result = sendSticker(media, options)
                if (result.isFailure) errorState.value = result.exceptionOrNull().userMessage(context, R.string.sticker_picker_send_failed)
            } finally { sending = false }
        }
    }

    fun prepare(uris: List<android.net.Uri>, asDocument: Boolean) {
        if (uris.isEmpty()) return
        if (uris.size > MAX_PENDING_ATTACHMENTS) { errorState.value = context.getString(R.string.composer_too_many_attachments); return }
        importJob = scope.launch {
            importing = true
            errorState.value = null
            try {
                for (uri in uris) {
                    val prepared = importer.prepare(uri, asDocument)
                    pendingState.value = ArrayList(pending + prepared)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { errorState.value = failure.userMessage(context, R.string.composer_prepare_failed) }
            finally { importing = false }
        }
    }

    fun remove(item: OutgoingMedia) {
        if (sending || importing) return
        pendingState.value = ArrayList(pending.filterNot { it.samePendingAttachment(item) })
        if (item.path.isNotBlank()) scope.launch { importer.discard(item) }
    }

    fun discardAll() {
        if (sending) return
        importJob?.cancel()
        val discarded = pending.toList()
        pendingState.value = arrayListOf()
        importing = false
        errorState.value = null
        menuOpen = false
        picker = AttachmentPicker.None
        scope.launch { discarded.filter { it.path.isNotBlank() }.forEach { importer.discard(it) } }
    }

    fun send(caption: String, options: MessageSendOptions, onCaptionChange: (String) -> Unit) {
        if (sending || importing || pending.isEmpty()) return
        scope.launch {
            sending = true
            errorState.value = null
            var nextCaption = caption
            try {
                val items = pending.toList()
                val batches = if (items.size in 2..10 && items.all { it.kind == com.beykus.beygram.domain.model.MediaKind.Photo || it.kind == com.beykus.beygram.domain.model.MediaKind.Video }) listOf(items) else items.map { listOf(it) }
                for (batch in batches) {
                    val result = sendMedia(batch, nextCaption, options)
                    if (result.isFailure) { errorState.value = result.exceptionOrNull().userMessage(context, R.string.composer_send_failed); break }
                    pendingState.value = ArrayList(pending.filterNot { item -> batch.any { it.samePendingAttachment(item) } })
                    nextCaption = ""
                    onCaptionChange("")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { errorState.value = failure.userMessage(context, R.string.composer_send_failed) }
            finally { sending = false }
        }
    }
}

internal const val MAX_PENDING_ATTACHMENTS = 10

/** Pending rows are keyed by their local path or, for a GIF picked from Telegram, by its server file id. */
internal fun OutgoingMedia.samePendingAttachment(other: OutgoingMedia): Boolean =
    if (path.isNotBlank() || other.path.isNotBlank()) path == other.path
    else remoteFileId == other.remoteFileId && remoteFileId != 0

@Composable
internal fun rememberAttachmentState(
    chatId: Long,
    sendMedia: suspend (List<OutgoingMedia>, String, MessageSendOptions) -> Result<Unit>,
    sendSticker: suspend (OutgoingMedia, MessageSendOptions) -> Result<Unit>
): AttachmentState {
    val context = LocalContext.current
    val accountId = com.beykus.beygram.playback.LocalPlaybackAccountId.current
    val importer = remember(context, accountId) { OutgoingMediaImporter(context, accountId) }
    val scope = rememberCoroutineScope()
    val pending = rememberSaveable(accountId, chatId) { mutableStateOf(arrayListOf<OutgoingMedia>()) }
    val error = rememberSaveable(accountId, chatId) { mutableStateOf<String?>(null) }
    val currentSend by rememberUpdatedState(sendMedia)
    val currentSticker by rememberUpdatedState(sendSticker)
    val state = remember(accountId, chatId) {
        AttachmentState(context, importer, scope, pending, error,
            sendMedia = { media, caption, options -> currentSend(media, caption, options) },
            sendSticker = { media, options -> currentSticker(media, options) })
    }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { state.prepare(it, false) }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { state.prepare(it, true) }
    state.pickPhotos = { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
    state.pickDocuments = { documents.launch(arrayOf("*/*")) }
    return state
}

@Composable
internal fun AttachmentButton(state: AttachmentState, enabled: Boolean = true) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    IconButton(onClick = state::toggleMenu, enabled = enabled && !state.sending && !state.importing,
        modifier = Modifier.size(48.dp).semantics { contentDescription = context.getString(R.string.composer_attach_description) }) {
        Icon(Icons.Rounded.AttachFile, contentDescription = null, tint = if (state.menuOpen || state.active || state.picker != AttachmentPicker.None) palette.accent else palette.textSecondary, modifier = Modifier.size(24.dp))
    }
}

/** Inline strip above the input: type picker, sticker/GIF picker, then the list of prepared attachments. */
@Composable
internal fun AttachmentPanel(
    state: AttachmentState,
    caption: String,
    captionLimit: Int,
    conversation: ConversationRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onDownloadFile: (Int) -> Unit,
    onStickerOptions: (OutgoingMedia) -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val visible = state.menuOpen || state.active || state.picker != AttachmentPicker.None || state.error != null
    if (!visible) return
    Column(Modifier.fillMaxWidth().background(palette.surface)) {
        when {
            state.picker == AttachmentPicker.Stickers -> StickerPickerPanel(
                conversation = conversation,
                observeFile = observeFile,
                downloadFile = onDownloadFile,
                onSend = { state.sendStickerNow(it.toOutgoingMedia()) },
                onOptions = { onStickerOptions(it.toOutgoingMedia()) },
                onClose = state::closePicker
            )
            state.picker == AttachmentPicker.Gifs -> GifPickerPanel(
                conversation = conversation,
                observeFile = observeFile,
                downloadFile = onDownloadFile,
                onPick = { state.addAnimation(it.toOutgoingMedia()) },
                onClose = state::closePicker
            )
            !state.active -> Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.menuOpen) {
                    // The picker choices scroll independently on small screens or with large text.
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TypeChip(Icons.Rounded.Image, stringResource(R.string.composer_type_photo_video)) { state.menuOpen = false; state.pickPhotos() }
                        TypeChip(Icons.Rounded.Description, stringResource(R.string.composer_type_file)) { state.menuOpen = false; state.pickDocuments() }
                        TypeChip(Icons.Rounded.Face, stringResource(R.string.composer_type_stickers)) { state.openPicker(AttachmentPicker.Stickers) }
                        TypeChip(Icons.Rounded.Animation, stringResource(R.string.composer_type_gif)) { state.openPicker(AttachmentPicker.Gifs) }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconButton(onClick = { state.discardAll() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = palette.textSecondary)
                }
            }
            else -> Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        state.pending.forEach { item ->
                            Row(
                                Modifier.clip(RoundedCornerShape(14.dp)).background(palette.surface2)
                                    .padding(start = 6.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PendingAttachmentPreview(item, observeFile, onDownloadFile)
                                Column(Modifier.padding(horizontal = 8.dp).width(112.dp)) {
                                    Text(item.fileName, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    val details = listOfNotNull(item.kind.label(context), formatMediaSize(item.size).takeIf { item.size > 0 })
                                    Text(details.joinToString(" · "), color = palette.textSecondary, fontSize = 12.sp, maxLines = 1)
                                }
                                AttachmentRemoveButton(
                                    fileName = item.fileName,
                                    enabled = !state.sending && !state.importing,
                                    onClick = { state.remove(item) }
                                )
                            }
                        }
                        if (state.importing) CircularProgressIndicator(Modifier.size(24.dp), color = palette.accent, strokeWidth = 2.dp)
                    }
                    IconButton(onClick = { state.discardAll() }, enabled = !state.sending, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.composer_cancel_attachments), tint = palette.textSecondary)
                    }
                }
                val count = caption.codePointCount(0, caption.length)
                Text("$count / $captionLimit", color = if (count > captionLimit) palette.danger else palette.textSecondary, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
            }
        }
        state.error?.let { Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp).errorAnnouncement()) }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * A local thumbnail when the attachment has one; a TDLib thumbnail for a GIF picked from the server. The full GIF is
 * deliberately not downloaded: it is sent by file id and only the small preview is needed here.
 */
@Composable
private fun PendingAttachmentPreview(item: OutgoingMedia, observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit) {
    val size = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
    when {
        item.thumbnailPath != null -> LocalMediaImage(item.thumbnailPath, size)
        item.thumbnailRemoteFileId > 0 -> RemoteAttachmentThumbnail(item.thumbnailRemoteFileId, observeFile, downloadFile, size)
        else -> Icon(Icons.Rounded.Description, null, tint = beyGramPalette().accentInk,
            modifier = Modifier.size(36.dp).background(beyGramPalette().accentSoft, RoundedCornerShape(8.dp)).padding(8.dp))
    }
}

@Composable
private fun RemoteAttachmentThumbnail(fileId: Int, observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit, modifier: Modifier) {
    val file by remember(fileId) { observeFile(fileId) }.collectAsStateWithLifecycle()
    LaunchedEffect(fileId) { downloadFile(fileId) }
    val path = file.localPath
    if (path != null) LocalMediaImage(path, modifier, fit = true, maxDimension = 320)
    else if (file.isDownloading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
    else Icon(Icons.Rounded.Animation, null, tint = beyGramPalette().accentInk, modifier = modifier.background(beyGramPalette().accentSoft).padding(8.dp))
}

/**
 * A real 48dp button instead of a clickable 28dp glyph.
 * Icon remains visually compact while the accessibility action names its file.
 */
@Composable
internal fun AttachmentRemoveButton(fileName: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    val label = stringResource(R.string.composer_remove_attachment_named, fileName)
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp).semantics { contentDescription = label }
    ) {
        Icon(
            Icons.Rounded.Close,
            contentDescription = null,
            tint = palette.textSecondary.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TypeChip(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(Modifier.defaultMinSize(minHeight = 48.dp).clip(RoundedCornerShape(14.dp)).background(palette.accentSoft).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = palette.accentInk, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = palette.accentInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
