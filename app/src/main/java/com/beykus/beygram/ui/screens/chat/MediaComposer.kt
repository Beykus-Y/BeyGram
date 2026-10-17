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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.media.OutgoingMediaImporter
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Attachment flow state. The UI is inline (a panel above the input field), so the chat screen
 * owns this holder and renders [AttachmentButton] and [AttachmentPanel] in different places.
 */
@Stable
internal class AttachmentState(
    private val context: Context,
    private val importer: OutgoingMediaImporter,
    private val scope: CoroutineScope,
    private val pendingState: MutableState<ArrayList<OutgoingMedia>>,
    private val errorState: MutableState<String?>,
    private val sendMedia: suspend (OutgoingMedia, String) -> Result<Unit>
) {
    var menuOpen by mutableStateOf(false)
    var importing by mutableStateOf(false)
        private set
    var sending by mutableStateOf(false)
        private set
    val pending: List<OutgoingMedia> get() = pendingState.value
    val error: String? get() = errorState.value
    private var importJob: Job? = null
    var pickPhotos: () -> Unit = {}
    var pickDocuments: () -> Unit = {}

    /** True while the panel replaces the plain composer (files are being imported or are ready to send). */
    val active: Boolean get() = pending.isNotEmpty() || importing

    fun prepare(uris: List<android.net.Uri>, asDocument: Boolean) {
        if (uris.isEmpty()) return
        if (uris.size > 10) { errorState.value = context.getString(R.string.composer_too_many_attachments); return }
        importJob = scope.launch {
            importing = true
            errorState.value = null
            try {
                for (uri in uris) {
                    val prepared = importer.prepare(uri, asDocument)
                    pendingState.value = ArrayList(pending + prepared)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { errorState.value = failure.message ?: context.getString(R.string.composer_prepare_failed) }
            finally { importing = false }
        }
    }

    fun remove(item: OutgoingMedia) {
        if (sending) return
        pendingState.value = ArrayList(pending.filterNot { it.path == item.path })
        scope.launch { importer.discard(item) }
    }

    fun discardAll() {
        if (sending) return
        importJob?.cancel()
        val discarded = pending.toList()
        pendingState.value = arrayListOf()
        importing = false
        errorState.value = null
        menuOpen = false
        scope.launch { discarded.forEach { importer.discard(it) } }
    }

    fun send(caption: String, onCaptionChange: (String) -> Unit) {
        if (sending || importing || pending.isEmpty()) return
        scope.launch {
            sending = true
            errorState.value = null
            var nextCaption = caption
            try {
                for (item in pending.toList()) {
                    val result = sendMedia(item, nextCaption)
                    if (result.isFailure) { errorState.value = result.exceptionOrNull()?.message ?: context.getString(R.string.composer_send_failed); break }
                    pendingState.value = ArrayList(pending.filterNot { it.path == item.path })
                    nextCaption = ""
                    onCaptionChange("")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { errorState.value = failure.message ?: context.getString(R.string.composer_send_failed) }
            finally { sending = false }
        }
    }
}

@Composable
internal fun rememberAttachmentState(
    chatId: Long,
    sendMedia: suspend (OutgoingMedia, String) -> Result<Unit>
): AttachmentState {
    val context = LocalContext.current
    val importer = remember { OutgoingMediaImporter(context) }
    val scope = rememberCoroutineScope()
    val pending = rememberSaveable(chatId) { mutableStateOf(arrayListOf<OutgoingMedia>()) }
    val error = rememberSaveable(chatId) { mutableStateOf<String?>(null) }
    val currentSend by androidx.compose.runtime.rememberUpdatedState(sendMedia)
    val state = remember(chatId) { AttachmentState(context, importer, scope, pending, error) { media, caption -> currentSend(media, caption) } }
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
    IconButton(onClick = { state.menuOpen = !state.menuOpen }, enabled = enabled && !state.sending,
        modifier = Modifier.size(44.dp).semantics { contentDescription = context.getString(R.string.composer_attach_description) }) {
        Icon(Icons.Rounded.AttachFile, contentDescription = null, tint = if (state.menuOpen || state.active) palette.accent else palette.textSecondary, modifier = Modifier.size(24.dp))
    }
}

/** Inline strip above the input: type picker, then the list of prepared attachments. */
@Composable
internal fun AttachmentPanel(state: AttachmentState, caption: String, captionLimit: Int) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val visible = state.menuOpen || state.active || state.error != null
    if (!visible) return
    Column(Modifier.fillMaxWidth().background(palette.surface).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 2.dp)) {
        if (!state.active) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.menuOpen) {
                    TypeChip(Icons.Rounded.Image, stringResource(R.string.composer_type_photo_video)) { state.menuOpen = false; state.pickPhotos() }
                    TypeChip(Icons.Rounded.Description, stringResource(R.string.composer_type_file)) { state.menuOpen = false; state.pickDocuments() }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { state.discardAll() }) { Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = palette.textSecondary) }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    state.pending.forEach { item ->
                        Row(Modifier.clip(RoundedCornerShape(14.dp)).background(palette.surface2).padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (item.thumbnailPath != null) LocalMediaImage(item.thumbnailPath, Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
                            else Icon(Icons.Rounded.Description, null, tint = palette.accentInk, modifier = Modifier.size(36.dp).background(palette.accentSoft, RoundedCornerShape(8.dp)).padding(8.dp))
                            Column(Modifier.padding(horizontal = 8.dp).width(112.dp)) {
                                Text(item.fileName, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("${item.kind.label(context)} · ${formatMediaSize(item.size)}", color = palette.textSecondary, fontSize = 12.sp, maxLines = 1)
                            }
                            Icon(Icons.Rounded.Close, stringResource(R.string.composer_remove_attachment), tint = palette.textSecondary,
                                modifier = Modifier.size(28.dp).clip(CircleShape).clickable(enabled = !state.sending && !state.importing) { state.remove(item) }.padding(5.dp))
                        }
                    }
                    if (state.importing) CircularProgressIndicator(Modifier.size(24.dp), color = palette.accent, strokeWidth = 2.dp)
                }
                IconButton(onClick = { state.discardAll() }, enabled = !state.sending) { Icon(Icons.Rounded.Close, stringResource(R.string.composer_cancel_attachments), tint = palette.textSecondary) }
            }
            val count = caption.codePointCount(0, caption.length)
            Text("$count / $captionLimit", color = if (count > captionLimit) palette.danger else palette.textSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
        }
        state.error?.let { Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp)) }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun TypeChip(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(Modifier.height(40.dp).clip(RoundedCornerShape(14.dp)).background(palette.accentSoft).clickable(onClick = onClick).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = palette.accentInk, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = palette.accentInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
