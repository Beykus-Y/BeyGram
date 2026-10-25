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
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Poll
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.media.OutgoingMediaImporter
import com.beykus.beygram.domain.model.MessageExtraDraft
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
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
    private val sendMedia: suspend (List<OutgoingMedia>, String, MessageSendOptions) -> Result<Unit>,
    private val sendExtra: suspend (MessageExtraDraft, MessageSendOptions) -> Result<Unit>
) {
    var menuOpen by mutableStateOf(false)
    var importing by mutableStateOf(false)
        private set
    var sending by mutableStateOf(false)
        private set
    var extra by mutableStateOf<MessageExtraDraft?>(null)
        private set
    /** True while a location fix is in flight; the composer blocks other attachment choices and Send. */
    var locating by mutableStateOf(false)
        private set
    private val locationRequests = LocationRequestGate()
    val pending: List<OutgoingMedia> get() = pendingState.value
    val error: String? get() = errorState.value
    private var importJob: Job? = null
    var pickPhotos: () -> Unit = {}
    var pickDocuments: () -> Unit = {}
    var pickPoll: () -> Unit = {}
    var pickLocation: () -> Unit = {}
    var pickContact: () -> Unit = {}
    var pickDice: () -> Unit = {}
    /** Secret chats do not support server-side polls; the chip disappears instead of failing. */
    var allowsPolls: Boolean = true

    /** True while the panel replaces the plain composer (files, a location fix, or a prepared extra). */
    val active: Boolean get() = pending.isNotEmpty() || importing || extra != null || locating

    /** The normal Send button is enabled when the prepared attachment can be delivered. */
    val canSend: Boolean get() = !sending && !importing && !locating && (pending.isNotEmpty() || extra != null)

    fun prepare(uris: List<android.net.Uri>, asDocument: Boolean) {
        if (uris.isEmpty() || locating) return
        if (uris.size > 10) { errorState.value = context.getString(R.string.composer_too_many_attachments); return }
        extra = null
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

    /** Puts a poll/location/contact/dice in the panel; the normal Send button then applies reply and send options. */
    fun prepareExtra(draft: MessageExtraDraft) {
        if (sending || importing || locating) return
        importJob?.cancel()
        if (pending.isNotEmpty()) scope.launch { pending.toList().forEach { importer.discard(it) } }
        pendingState.value = arrayListOf()
        extra = draft
        errorState.value = null
        menuOpen = false
    }

    /**
     * Reserves the attachment slot for one location fix. While it is pending, other attachment choices and Send
     * are refused, so a late fix can never overwrite a poll/contact/dice the user picked afterwards.
     */
    fun beginLocationRequest(): Int? {
        if (sending || importing || locating) return null
        val request = locationRequests.begin() ?: return null
        locating = true
        errorState.value = null
        menuOpen = false
        return request
    }

    /** Applies a completed fix only if it is still the active request; a cancelled/late fix is ignored. */
    fun completeLocationRequest(request: Int, draft: MessageExtraDraft.Location?): Boolean {
        if (!locationRequests.complete(request)) return false
        locating = false
        if (draft == null) return true
        importJob?.cancel()
        if (pending.isNotEmpty()) scope.launch { pending.toList().forEach { importer.discard(it) } }
        pendingState.value = arrayListOf()
        extra = draft
        errorState.value = null
        menuOpen = false
        return true
    }

    fun cancelLocationRequest() {
        locationRequests.cancel()
        locating = false
    }

    fun removeExtra() {
        if (sending || importing) return
        extra = null
    }

    fun reportError(message: String?) {
        if (message != null) errorState.value = message
    }

    fun remove(item: OutgoingMedia) {
        if (sending || importing) return
        pendingState.value = ArrayList(pending.filterNot { it.path == item.path })
        scope.launch { importer.discard(item) }
    }

    fun discardAll() {
        if (sending) return
        cancelLocationRequest()
        importJob?.cancel()
        val discarded = pending.toList()
        pendingState.value = arrayListOf()
        extra = null
        importing = false
        errorState.value = null
        menuOpen = false
        scope.launch { discarded.forEach { importer.discard(it) } }
    }

    fun send(caption: String, options: MessageSendOptions, onCaptionChange: (String) -> Unit) {
        if (sending || importing || locating) return
        val preparedExtra = extra
        if (preparedExtra != null) {
            scope.launch {
                sending = true
                errorState.value = null
                try {
                    val result = sendExtra(preparedExtra, options)
                    if (result.isFailure) {
                        errorState.value = result.exceptionOrNull().userMessage(context, R.string.composer_send_failed)
                    } else if (extra === preparedExtra) {
                        extra = null
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { errorState.value = failure.userMessage(context, R.string.composer_send_failed) }
                finally { sending = false }
            }
            return
        }
        if (pending.isEmpty()) return
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
                    pendingState.value = ArrayList(pending.filterNot { item -> batch.any { it.path == item.path } })
                    nextCaption = ""
                    onCaptionChange("")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { errorState.value = failure.userMessage(context, R.string.composer_send_failed) }
            finally { sending = false }
        }
    }
}

@Composable
internal fun rememberAttachmentState(
    chatId: Long,
    sendMedia: suspend (List<OutgoingMedia>, String, MessageSendOptions) -> Result<Unit>,
    sendExtra: suspend (MessageExtraDraft, MessageSendOptions) -> Result<Unit>
): AttachmentState {
    val context = LocalContext.current
    val accountId = com.beykus.beygram.playback.LocalPlaybackAccountId.current
    val importer = remember(context, accountId) { OutgoingMediaImporter(context, accountId) }
    val scope = rememberCoroutineScope()
    val pending = rememberSaveable(accountId, chatId) { mutableStateOf(arrayListOf<OutgoingMedia>()) }
    val error = rememberSaveable(accountId, chatId) { mutableStateOf<String?>(null) }
    val currentSend by androidx.compose.runtime.rememberUpdatedState(sendMedia)
    val currentSendExtra by androidx.compose.runtime.rememberUpdatedState(sendExtra)
    val state = remember(accountId, chatId) {
        AttachmentState(context, importer, scope, pending, error,
            { media, caption, options -> currentSend(media, caption, options) },
            { draft, options -> currentSendExtra(draft, options) })
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
    IconButton(onClick = { state.menuOpen = !state.menuOpen }, enabled = enabled && !state.sending && !state.importing && !state.locating,
        modifier = Modifier.size(48.dp).semantics { contentDescription = context.getString(R.string.composer_attach_description) }) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.menuOpen) {
                    // The picker choices scroll independently on small screens or with large text.
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TypeChip(Icons.Rounded.Image, stringResource(R.string.composer_type_photo_video)) { state.menuOpen = false; state.pickPhotos() }
                        TypeChip(Icons.Rounded.Description, stringResource(R.string.composer_type_file)) { state.menuOpen = false; state.pickDocuments() }
                        if (state.allowsPolls) TypeChip(Icons.Rounded.Poll, stringResource(R.string.composer_type_poll)) { state.menuOpen = false; state.pickPoll() }
                        TypeChip(Icons.Rounded.LocationOn, stringResource(R.string.composer_type_location)) { state.menuOpen = false; state.pickLocation() }
                        TypeChip(Icons.Rounded.Person, stringResource(R.string.composer_type_contact)) { state.menuOpen = false; state.pickContact() }
                        TypeChip(Icons.Rounded.Casino, stringResource(R.string.composer_type_dice)) { state.menuOpen = false; state.pickDice() }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconButton(onClick = { state.discardAll() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = palette.textSecondary)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.locating) {
                        Row(
                            Modifier.clip(RoundedCornerShape(14.dp)).background(palette.surface2)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = palette.accent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.chat_location_locating), color = palette.textPrimary,
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    state.extra?.let { extra ->
                        val label = extraLabel(extra)
                        Row(
                            Modifier.clip(RoundedCornerShape(14.dp)).background(palette.surface2)
                                .padding(start = 6.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when (extra) {
                                    is MessageExtraDraft.Poll -> Icons.Rounded.Poll
                                    is MessageExtraDraft.Location -> Icons.Rounded.LocationOn
                                    is MessageExtraDraft.Contact -> Icons.Rounded.Person
                                    is MessageExtraDraft.Dice -> Icons.Rounded.Casino
                                },
                                contentDescription = null, tint = palette.accentInk,
                                modifier = Modifier.size(36.dp).background(palette.accentSoft, RoundedCornerShape(8.dp)).padding(8.dp)
                            )
                            Text(label, color = palette.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp).width(180.dp))
                            AttachmentRemoveButton(
                                fileName = label,
                                enabled = !state.sending && !state.importing,
                                onClick = { state.removeExtra() }
                            )
                        }
                    }
                    state.pending.forEach { item ->
                        Row(
                            Modifier.clip(RoundedCornerShape(14.dp)).background(palette.surface2)
                                .padding(start = 6.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.thumbnailPath != null) LocalMediaImage(item.thumbnailPath, Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
                            else Icon(Icons.Rounded.Description, null, tint = palette.accentInk, modifier = Modifier.size(36.dp).background(palette.accentSoft, RoundedCornerShape(8.dp)).padding(8.dp))
                            Column(Modifier.padding(horizontal = 8.dp).width(112.dp)) {
                                Text(item.fileName, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("${item.kind.label(context)} · ${formatMediaSize(item.size)}", color = palette.textSecondary, fontSize = 12.sp, maxLines = 1)
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
            if (state.extra == null && !state.locating) {
                val count = caption.codePointCount(0, caption.length)
                Text("$count / $captionLimit", color = if (count > captionLimit) palette.danger else palette.textSecondary, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
            }
        }
        state.error?.let { Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp)) }
        Spacer(Modifier.height(4.dp))
    }
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

/**
 * Pure reservation gate for the asynchronous location fix. It makes late callbacks harmless and lets the attachment
 * state refuse competing choices while one fix owns the slot.
 */
internal class LocationRequestGate {
    private var generation = 0
    private var active: Int? = null

    val isBusy: Boolean get() = active != null

    /** Returns a request token, or null when another fix already owns the slot. */
    fun begin(): Int? {
        if (active != null) return null
        val request = ++generation
        active = request
        return request
    }

    /** True only for the request that still owns the slot; cancelled or older tokens are ignored. */
    fun complete(request: Int): Boolean {
        if (active != request) return false
        active = null
        return true
    }

    fun cancel() {
        generation++
        active = null
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

@Composable
private fun extraLabel(extra: MessageExtraDraft): String = when (extra) {
    is MessageExtraDraft.Poll -> stringResource(
        R.string.composer_extra_poll,
        extra.poll.normalized().question.ifBlank { stringResource(R.string.chat_poll_untitled) }
    )
    is MessageExtraDraft.Location -> stringResource(R.string.composer_extra_location)
    is MessageExtraDraft.Contact -> stringResource(R.string.composer_extra_contact, extra.title)
    is MessageExtraDraft.Dice -> stringResource(R.string.composer_extra_dice, extra.emoji)
}
