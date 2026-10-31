package com.beykus.beygram.ui.screens.chat

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.media.AudioCompressor
import com.beykus.beygram.data.media.AudioPlanReason
import com.beykus.beygram.data.media.CaptureRetryState
import com.beykus.beygram.data.media.MediaLimits
import com.beykus.beygram.data.media.NormalizedCrop
import com.beykus.beygram.data.media.OutgoingMediaImporter
import com.beykus.beygram.data.media.PhotoEditor
import com.beykus.beygram.data.media.PhotoExportQuality
import com.beykus.beygram.data.media.PhotoRotation
import com.beykus.beygram.data.media.VideoCompressor
import com.beykus.beygram.data.media.VideoLimitIssue
import com.beykus.beygram.data.media.VideoPlanReason
import com.beykus.beygram.data.media.audioCompressionPlan
import com.beykus.beygram.data.media.videoCompressionPlan
import com.beykus.beygram.data.media.videoLimitIssue
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageExtraDraft
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.model.ViewOncePolicy
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** A photo opened in the editor: either a temporary camera take or an already staged attachment. */
internal data class PhotoEditorSession(
    val sourcePath: String,
    val temporary: Boolean,
    val replaceItem: OutgoingMedia? = null,
    val rotation: PhotoRotation = PhotoRotation.None,
    val crop: NormalizedCrop = NormalizedCrop(),
    val quality: PhotoExportQuality = PhotoExportQuality.High,
    val keepOriginal: Boolean = false
) : java.io.Serializable

/** Survives rotation with the cache path of a camera take whose import failed. */
private val CaptureRetrySaver = Saver<CaptureRetryState, String>(
    save = { it.failedPaths.joinToString("\n") },
    restore = { CaptureRetryState(it.split('\n').filter(String::isNotBlank)) }
)

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
    private val viewOnceState: MutableState<Boolean>,
    private val editorState: MutableState<PhotoEditorSession?>,
    private val captureRetry: MutableState<CaptureRetryState>,
    private val qualityState: MutableState<HashMap<String, MediaPreparation>>,
    private val sendMedia: suspend (List<OutgoingMedia>, String, MessageSendOptions) -> Result<Unit>,
    private val sendSticker: suspend (OutgoingMedia, MessageSendOptions) -> Result<Unit>,
    private val sendExtra: suspend (MessageExtraDraft, MessageSendOptions) -> Result<Unit>
) {
    var menuOpen by mutableStateOf(false)
    var cameraOpen by mutableStateOf(false)
        private set
    var editor: PhotoEditorSession?
        get() = editorState.value
        private set(value) { editorState.value = value }
    var editorSaving by mutableStateOf(false)
        private set
    var editorError by mutableStateOf<String?>(null)
        private set
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
    /** Per-message view-once flag, saved with the pending attachments across activity recreation. */
    var viewOnce: Boolean
        get() = viewOnceState.value
        set(value) { viewOnceState.value = value }
    var notice by mutableStateOf<String?>(null)
        private set
    /** Asks before an ordinary send when stealth mode could not schedule one; the chat screen shows its dialog. */
    val stealthFallback = StealthScheduleFallback()
    val canRetryCapture: Boolean get() = captureRetry.value.canRetry
    val pending: List<OutgoingMedia> get() = pendingState.value
    val error: String? get() = errorState.value
    private val compressor = VideoCompressor(context)
    private val audioCompressor = AudioCompressor(context)
    private var importJob: Job? = null
    private var editorJob: Job? = null
    /** One Media3 job per staged file; changing a file's mode cancels only that file's job. */
    private val qualityJobs = mutableMapOf<String, Job>()
    private val qualityTokens = mutableMapOf<String, Int>()
    private var qualityToken = 0
    // Media3 Transformer requests hardware codecs; serializing transforms avoids exhausting a
    // device's codec instances when several videos are selected at once.
    private val qualityGate = Mutex()
    private val failedCapturePaths: List<String> get() = captureRetry.value.failedPaths
    private val nextFailedCapturePath: String? get() = captureRetry.value.nextPath
    var pickPhotos: () -> Unit = {}
    var pickDocuments: () -> Unit = {}
    var pickAudio: () -> Unit = {}
    var pickPoll: () -> Unit = {}
    var pickLocation: () -> Unit = {}
    var pickContact: () -> Unit = {}
    var pickDice: () -> Unit = {}
    var pickMusic: () -> Unit = {}
    /** False where Telegram rejects polls: secret chats and ordinary private chats with people. */
    var allowsPolls by mutableStateOf(true)
    /** False in secret chats: a track found in the search is a server file that a secret chat cannot reuse. */
    var allowsMusic by mutableStateOf(true)
    /** A poll send failed and [extra] still holds the composed poll, so Retry/Cancel are shown. */
    var extraSendFailed by mutableStateOf(false)
        private set
    private var lastExtraCaption = ""
    private var lastExtraOptions = MessageSendOptions()

    init {
        // A configuration change must not resurrect a running Media3 job or a prepared file that
        // has already disappeared. Interrupted files fall back to the untouched staged original.
        val pendingPaths = pendingState.value.mapTo(HashSet()) { it.path }
        val sanitized = HashMap<String, MediaPreparation>()
        qualityState.value.forEach { (path, preparation) ->
            if (path !in pendingPaths) return@forEach
            val prepared = preparation.prepared?.takeIf { candidate ->
                candidate.path.isNotBlank() && File(candidate.path).isFile
            }
            sanitized[path] = preparation.copy(
                prepared = prepared,
                processing = false,
                progress = null
            )
        }
        if (sanitized != qualityState.value) qualityState.value = sanitized
        if (sanitized.values.any { it.delivery == MediaDeliveryMode.File }) {
            viewOnceState.value = false
        }
        // An activity recreation cancels the Media3 job. Resume the explicitly selected mode from
        // the still-staged original instead of silently sending a different quality.
        val interrupted = pendingState.value.filter { item ->
            val preparation = sanitized[item.path] ?: return@filter false
            val delivery = item.kind != MediaKind.Video || preparation.delivery == MediaDeliveryMode.Media
            preparation.mode != MediaQualityMode.Original && delivery &&
                preparation.prepared == null && preparation.hint == null && preparation.error == null
        }
        if (interrupted.isNotEmpty()) {
            scope.launch {
                interrupted.forEach { item -> item.qualityKind()?.let { startQualityJob(item, it) } }
            }
        }
    }

    /** Per-file quality state. Every entry is keyed by the staged original's path. */
    val preparations: Map<String, MediaPreparation> get() = qualityState.value

    /** The exact media list the repository will receive, used for view-once and other policies. */
    val outgoingPending: List<OutgoingMedia>
        get() = pending.map { outgoingMediaFor(it, preparationFor(it)) }

    val preparing: Boolean get() = preparations.values.any { it.processing }

    /** True while the panel replaces the plain composer (files, a location fix, or a prepared extra). */
    val active: Boolean get() = pending.isNotEmpty() || importing || extra != null || locating

    /** The normal Send button is enabled when the prepared attachment can be delivered. */
    val canSend: Boolean get() = !sending && !importing && !locating && !preparing && (pending.isNotEmpty() || extra != null)

    fun preparationFor(item: OutgoingMedia): MediaPreparation? = preparationFor(preparations, item)

    fun toggleMenu() {
        if (sending || importing || locating || extra != null) return
        menuOpen = !menuOpen
    }

    fun closeMenu() {
        if (sending) return
        menuOpen = false
        errorState.value = null
    }

    fun dismissTransientPanel() {
        if (menuOpen) closeMenu()
    }

    /** Runs one menu choice; every old type keeps its previous callback. */
    fun performAction(action: AttachmentAction) {
        if (sending || importing || locating) return
        if (action.replacesTray && pending.isNotEmpty()) {
            // A poll/contact/dice replaces the whole tray, so never do that behind the user's back.
            errorState.value = context.getString(R.string.composer_menu_chat_unavailable)
            menuOpen = false
            return
        }
        menuOpen = false
        when (action) {
            AttachmentAction.Camera -> openCamera()
            AttachmentAction.PhotoVideo -> pickPhotos()
            AttachmentAction.File -> pickDocuments()
            AttachmentAction.Audio -> pickAudio()
            AttachmentAction.Location -> pickLocation()
            AttachmentAction.Contact -> pickContact()
            AttachmentAction.Poll -> pickPoll()
            AttachmentAction.Dice -> pickDice()
            AttachmentAction.Music -> pickMusic()
        }
    }

    /** A GIF becomes a normal pending attachment, so the caption limit, reply, topic and send options all apply. */
    fun addAnimation(media: OutgoingMedia) {
        if (sending || importing || locating) return
        if (pending.size >= MAX_PENDING_ATTACHMENTS) {
            errorState.value = context.getString(R.string.composer_too_many_attachments)
            return
        }
        errorState.value = null
        // Repeated server GIFs are separate pending attachments, even with one file ID.
        pendingState.value = ArrayList(pending + media.copy(stagingKey = UUID.randomUUID().toString()))
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
                if (result.isFailure) stickerFailed(media, MessageSendOptions(), result.exceptionOrNull())
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
                if (result.isFailure) stickerFailed(media, options, result.exceptionOrNull())
            } finally { sending = false }
        }
    }

    private fun stickerFailed(media: OutgoingMedia, options: MessageSendOptions, failure: Throwable?) {
        if (stealthFallback.offer(failure) { sendStickerWithOptions(media, options.copy(sendNowConsent = true)) }) return
        errorState.value = failure.userMessage(context, R.string.sticker_picker_send_failed)
    }

    fun prepare(uris: List<android.net.Uri>, asDocument: Boolean) {
        if (uris.isEmpty() || locating) return
        if (pending.size + uris.size > MediaLimits.MAX_ATTACHMENTS) {
            errorState.value = context.getString(R.string.composer_too_many_attachments)
            return
        }
        extra = null
        importJob = scope.launch {
            importing = true
            errorState.value = null
            try {
                for (uri in uris) {
                    val prepared = importer.prepare(uri, asDocument)
                    try { coroutineContext.ensureActive() } catch (cancelled: CancellationException) {
                        importer.discard(prepared)
                        throw cancelled
                    }
                    pendingState.value = ArrayList(pending + prepared)
                    when {
                        !asDocument && prepared.kind == MediaKind.Video -> startQualityPreparation(prepared)
                        !asDocument && prepared.kind == MediaKind.Document && prepared.mimeType.startsWith("audio/") ->
                            startQualityPreparation(prepared)
                        !asDocument && prepared.kind == MediaKind.Document && prepared.mimeType.startsWith("video/") ->
                            notice = context.getString(R.string.video_container_as_file)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { errorState.value = failure.userMessage(context, R.string.composer_prepare_failed) }
            finally { importing = false }
        }
    }

    /** Full-screen camera entry; it hands one cache file back and closes without leaving the chat. */
    fun openCamera() {
        if (sending || importing || locating) return
        if (pending.size >= MediaLimits.MAX_ATTACHMENTS) {
            errorState.value = context.getString(R.string.composer_too_many_attachments)
            return
        }
        menuOpen = false
        errorState.value = null
        cameraOpen = true
    }

    fun closeCamera() {
        cameraOpen = false
    }

    /** A camera photo opens the editor first; "send original" is one tap away inside it. */
    fun capturePhoto(file: File) {
        cameraOpen = false
        editorError = null
        editor = PhotoEditorSession(file.absolutePath, temporary = true)
    }

    fun acceptVideoCapture(file: File) {
        cameraOpen = false
        importCaptureFile(file)
    }

    fun editPhoto(item: OutgoingMedia) {
        if (item.kind != com.beykus.beygram.domain.model.MediaKind.Photo || sending || importing) return
        menuOpen = false
        editorError = null
        editor = PhotoEditorSession(item.path, temporary = false, replaceItem = item)
    }

    fun cancelPhotoEditor() {
        if (editorSaving) return
        val session = editor ?: return
        editor = null
        editorError = null
        if (session.temporary) File(session.sourcePath).delete()
    }

    /** Keeps the current crop/rotation/quality in the saved editor session across recreation. */
    fun updateEditorDraft(rotation: PhotoRotation, crop: NormalizedCrop, quality: PhotoExportQuality, keepOriginal: Boolean) {
        val session = editor ?: return
        if (session.rotation == rotation && session.crop == crop && session.quality == quality && session.keepOriginal == keepOriginal) return
        editor = session.copy(rotation = rotation, crop = crop, quality = quality, keepOriginal = keepOriginal)
    }

    /** Sends the untouched camera take without creating an edited copy. */
    fun sendEditorOriginal() {
        val session = editor ?: return
        if (editorSaving) return
        if (pending.size >= MediaLimits.MAX_ATTACHMENTS) {
            editorError = context.getString(R.string.composer_too_many_attachments)
            return
        }
        editor = null
        editorError = null
        if (session.temporary) importCaptureFile(File(session.sourcePath))
    }

    fun savePhotoEdit(rotation: PhotoRotation, crop: NormalizedCrop, quality: PhotoExportQuality, keepOriginal: Boolean) {
        val session = editor ?: return
        if (editorSaving) return
        val additionsExpected = when {
            session.replaceItem != null && keepOriginal -> 1
            session.replaceItem != null -> 0
            keepOriginal -> 2
            else -> 1
        }
        if (pending.size + additionsExpected > MediaLimits.MAX_ATTACHMENTS) {
            editorError = context.getString(R.string.composer_too_many_attachments)
            return
        }
        editorSaving = true
        editorError = null
        editorJob = scope.launch {
            var stagedOriginal: OutgoingMedia? = null
            try {
                if (keepOriginal && session.temporary) {
                    // Stage the source before deleting anything. If this fails, keep the editor and
                    // the camera file so the user can retry or disable the option.
                    stagedOriginal = try {
                        importer.prepareLocal(
                            File(session.sourcePath),
                            asDocument = true,
                            displayName = "original.jpg"
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        editorError = failure.userMessage(context, R.string.photo_editor_original_failed)
                        return@launch
                    }
                }
                val exported = PhotoEditor(context).export(File(session.sourcePath), rotation, crop, quality)
                try {
                    val edited = try {
                        importer.importEditedPhoto(exported.file, exported.mimeType, exported.width, exported.height)
                    } catch (failure: Exception) {
                        stagedOriginal?.let { importer.discard(it) }
                        stagedOriginal = null
                        throw failure
                    }
                    try { coroutineContext.ensureActive() } catch (cancelled: CancellationException) {
                        importer.discard(edited)
                        throw cancelled
                    }
                    val originalCopy = stagedOriginal
                    val replaced = session.replaceItem
                    if (replaced != null) {
                        val withoutReplaced = pending.filterNot { it.path == replaced.path }
                        // "Keep original" promises a separate file, so a retained photo becomes a document.
                        val retained = if (keepOriginal) replaced.copy(kind = MediaKind.Document) else null
                        pendingState.value = ArrayList(withoutReplaced + edited + listOfNotNull(retained))
                        if (!keepOriginal) runCatching { importer.discard(replaced) }
                    } else {
                        pendingState.value = ArrayList(pending + edited + listOfNotNull(originalCopy))
                    }
                    editor = null
                    if (session.temporary) File(session.sourcePath).delete()
                } finally {
                    exported.file.delete()
                }
            } catch (cancelled: CancellationException) {
                stagedOriginal?.let { importer.discard(it) }
                throw cancelled
            }
            catch (failure: Exception) {
                stagedOriginal?.let { importer.discard(it) }
                editorError = failure.userMessage(context, R.string.photo_editor_failed)
            }
            finally { editorSaving = false }
        }
    }

    private fun importCaptureFile(file: File) {
        // Register before copying: activity recreation cancels the staging coroutine, and the path
        // must already be in saved state so the user can retry the only copy afterwards.
        setFailedCapture(file.absolutePath)
        if (sending || importing) return
        importJob = scope.launch {
            importing = true
            errorState.value = null
            try {
                val prepared = importer.prepareLocal(file)
                try { coroutineContext.ensureActive() } catch (cancelled: CancellationException) {
                    importer.discard(prepared)
                    throw cancelled
                }
                pendingState.value = ArrayList(pending + prepared)
                file.delete()
                clearFailedCapture(file.absolutePath)
                when {
                    prepared.kind == MediaKind.Video -> startQualityPreparation(prepared)
                    prepared.kind == MediaKind.Document && prepared.mimeType.startsWith("video/") ->
                        notice = context.getString(R.string.video_container_as_file)
                }
            } catch (cancelled: CancellationException) {
                // Rotation/scope cancellation keeps the registered retry path. An explicit discard
                // has already cleared it, so only then is deleting the file appropriate.
                if (file.absolutePath !in failedCapturePaths) file.delete()
                throw cancelled
            }
            catch (failure: Exception) {
                // The camera/editor is already closed; retain the take so the user can retry it.
                setFailedCapture(file.absolutePath)
                errorState.value = failure.userMessage(context, R.string.composer_prepare_failed)
            }
            finally { importing = false }
        }
    }

    private fun setFailedCapture(path: String) {
        captureRetry.value = captureRetry.value.failed(path)
    }

    private fun clearFailedCapture(path: String) {
        captureRetry.value = captureRetry.value.cleared(path)
    }

    fun retryCapture() {
        val path = nextFailedCapturePath ?: return
        if (importing || sending) return
        val file = File(path)
        if (!file.isFile || file.length() <= 0L) {
            clearFailedCapture(path)
            errorState.value = context.getString(R.string.capture_retry_missing)
            return
        }
        importCaptureFile(file)
    }

    /** Puts a poll/location/contact/dice in the panel; the normal Send button then applies reply and send options. */
    fun prepareExtra(draft: MessageExtraDraft) {
        if (sending || importing || locating) return
        importJob?.cancel()
        clearQualityState()
        if (pending.isNotEmpty()) {
            val discarded = pending.toList()
            scope.launch { discarded.filter { it.path.isNotBlank() }.forEach { importer.discard(it) } }
        }
        pendingState.value = arrayListOf()
        extra = draft
        extraSendFailed = false
        errorState.value = null
        menuOpen = false
    }

    /**
     * Reserves the attachment slot for one location fix. While it is pending, other attachment choices and Send
     * are refused, so a late fix can never overwrite a poll/contact/dice the user picked afterwards.
     */
    fun beginLocationRequest(): Int? {
        if (sending || importing || locating) return null
        if (pending.isNotEmpty()) {
            errorState.value = context.getString(R.string.composer_menu_chat_unavailable)
            return null
        }
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
        clearQualityState()
        if (pending.isNotEmpty()) {
            val discarded = pending.toList()
            scope.launch { discarded.filter { it.path.isNotBlank() }.forEach { importer.discard(it) } }
        }
        pendingState.value = arrayListOf()
        extra = draft
        extraSendFailed = false
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
        extraSendFailed = false
        errorState.value = null
    }

    /** Repeats the failed poll send with the same caption/options; only an explicit tap can retry. */
    fun retryExtra() {
        if (!extraSendFailed || extra == null || sending || importing || locating) return
        send(lastExtraCaption, lastExtraOptions) {}
    }

    /** Explicitly drops a poll that failed to send, instead of leaving a dead attachment in the composer. */
    fun discardFailedExtra() {
        if (sending || importing) return
        extraSendFailed = false
        errorState.value = null
        extra = null
    }

    fun reportError(message: String?) {
        if (message != null) errorState.value = message
    }

    fun dismissNotice() {
        notice = null
    }

    /** Update the actual send order, not merely the thumbnail positions. */
    fun movePending(item: OutgoingMedia, offset: Int) {
        if (sending || importing || locating) return
        pendingState.value = ArrayList(reorderPendingAttachment(pending, item, offset))
    }

    fun remove(item: OutgoingMedia) {
        if (sending || importing) return
        cancelQualityJob(item.path)
        if (qualityState.value.containsKey(item.path)) {
            qualityState.value = HashMap(qualityState.value).apply { remove(item.path) }
        }
        pendingState.value = ArrayList(pending.filterNot { it.samePendingAttachment(item) })
        if (item.path.isNotBlank()) scope.launch { importer.discard(item) }
    }

    /**
     * Per-file quality selection. Changing one file never changes another file or a future import;
     * the untouched original stays staged until the attachment is sent or removed.
     */
    fun selectQuality(item: OutgoingMedia, mode: MediaQualityMode) {
        if (sending || importing) return
        val preparation = preparationFor(item) ?: return
        val kind = item.qualityKind() ?: return
        if (preparation.mode == mode) {
            if (preparation.error != null) {
                updateQuality(item.path) { it.copy(error = null, failedMode = null) }
            }
            return
        }
        cancelQualityJob(item.path)
        updateQuality(item.path) {
            it.copy(
                mode = mode,
                prepared = null,
                processing = false,
                progress = null,
                hint = null,
                failedMode = null,
                error = null
            )
        }
        startQualityJob(item, kind)
    }

    /** Video-only: keep Telegram's video-message semantics or send the exact original as a file. */
    fun selectVideoDelivery(item: OutgoingMedia, delivery: MediaDeliveryMode) {
        if (sending || importing || item.kind != MediaKind.Video) return
        val preparation = preparationFor(item) ?: return
        if (preparation.delivery == delivery) return
        cancelQualityJob(item.path)
        updateQuality(item.path) {
            it.copy(
                delivery = delivery,
                prepared = null,
                processing = false,
                progress = null,
                hint = null,
                failedMode = null,
                error = null
            )
        }
        if (delivery == MediaDeliveryMode.File) viewOnce = false
        startQualityJob(item, MediaQualityKind.Video)
    }

    /** Stops this file's transform and falls back to its staged original. */
    fun cancelQualityPreparation(item: OutgoingMedia) {
        val preparation = preparationFor(item) ?: return
        if (sending) return
        cancelQualityJob(item.path)
        updateQuality(item.path) {
            it.copy(
                mode = MediaQualityMode.Original,
                prepared = null,
                processing = false,
                progress = null,
                hint = null,
                failedMode = null,
                error = null
            )
        }
        notice = context.getString(R.string.composer_quality_cancelled)
    }

    fun retryQualityPreparation(item: OutgoingMedia) {
        val preparation = preparationFor(item) ?: return
        val kind = item.qualityKind() ?: return
        if (sending || importing) return
        val retryMode = preparation.failedMode ?: preparation.mode.takeIf { it != MediaQualityMode.Original }
            ?: MediaQualityMode.Balanced
        cancelQualityJob(item.path)
        updateQuality(item.path) {
            it.copy(
                mode = retryMode,
                prepared = null,
                processing = false,
                progress = null,
                hint = null,
                failedMode = null,
                error = null
            )
        }
        startQualityJob(item, kind)
    }

    /** Registers the per-file default and starts its preparation without touching other files. */
    private fun startQualityPreparation(item: OutgoingMedia) {
        val kind = item.qualityKind() ?: return
        if (preparationFor(item) == null) {
            qualityState.value = HashMap(qualityState.value).apply {
                put(item.path, MediaPreparation(mode = MediaQualityMode.Balanced))
            }
        }
        startQualityJob(item, kind)
    }

    private fun startQualityJob(item: OutgoingMedia, kind: MediaQualityKind) {
        val preparation = preparationFor(item) ?: return
        if (preparation.mode == MediaQualityMode.Original ||
            (kind == MediaQualityKind.Video && preparation.delivery == MediaDeliveryMode.File)
        ) {
            updateQuality(item.path) { it.copy(processing = false, progress = null) }
            return
        }
        cancelQualityJob(item.path)
        val token = nextQualityToken(item.path)
        val requested = preparation.mode
        updateQuality(item.path) {
            it.copy(processing = true, progress = null, prepared = null, hint = null, failedMode = null, error = null)
        }
        qualityJobs[item.path] = scope.launch {
            try {
                qualityGate.withLock {
                    if (!qualityCurrent(item.path, token)) return@withLock
                    when (kind) {
                        MediaQualityKind.Video -> compressVideoQuality(item, requested, token)
                        MediaQualityKind.Audio -> compressAudioQuality(item, requested, token)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (qualityCurrent(item.path, token)) {
                    updateQuality(item.path) {
                        it.copy(
                            mode = MediaQualityMode.Original,
                            prepared = null,
                            processing = false,
                            progress = null,
                            hint = null,
                            failedMode = requested,
                            error = failure.userMessage(context, R.string.composer_quality_error)
                        )
                    }
                    notice = context.getString(R.string.composer_quality_failed_original)
                }
            } finally {
                if (qualityCurrent(item.path, token)) {
                    updateQuality(item.path) { it.copy(processing = false, progress = null) }
                    if (qualityTokens[item.path] == token) qualityJobs.remove(item.path)
                }
            }
        }
    }

    private suspend fun compressVideoQuality(item: OutgoingMedia, mode: MediaQualityMode, token: Int) {
        val source = File(item.path)
        val info = runCatching { compressor.inspect(source, item.mimeType) }.getOrNull()
        if (!qualityCurrent(item.path, token)) return
        if (info == null) {
            updateQuality(item.path) { it.copy(hint = MediaPreparationHint.UnknownMetadata) }
            return
        }
        val limit = videoLimitIssue(info)
        if (limit != null) {
            updateQuality(item.path) {
                it.copy(
                    mode = MediaQualityMode.Original,
                    delivery = MediaDeliveryMode.File,
                    prepared = null,
                    hint = if (limit == VideoLimitIssue.TooLong) MediaPreparationHint.TooLongAsFile else MediaPreparationHint.TooLargeAsFile
                )
            }
            viewOnce = false
            notice = context.getString(
                if (limit == VideoLimitIssue.TooLong) R.string.video_too_long_as_file else R.string.video_too_large_as_file
            )
            return
        }
        val plan = videoCompressionPlan(info, mode.toVideoOption())
        if (!plan.transcode) {
            val hint = when (plan.reason) {
                VideoPlanReason.AlreadyEfficient -> MediaPreparationHint.AlreadyEfficient
                VideoPlanReason.UnknownMetadata -> MediaPreparationHint.UnknownMetadata
                VideoPlanReason.NotWorthIt -> MediaPreparationHint.NotWorthIt
                VideoPlanReason.OriginalRequested, VideoPlanReason.Transcode -> null
            }
            if (hint != null) updateQuality(item.path) { it.copy(hint = hint) }
            return
        }
        val output = File(source.parentFile, "quality-${UUID.randomUUID()}.mp4")
        try {
            compressor.compress(source, output, plan, info) { progress ->
                if (qualityCurrent(item.path, token)) {
                    updateQuality(item.path) { it.copy(progress = progress) }
                }
            }
            if (!qualityCurrent(item.path, token)) return
            val compressed = importer.adoptCompressedVideo(item, output)
            if (!qualityCurrent(item.path, token)) return
            updateQuality(item.path) {
                it.copy(prepared = compressed, hint = null, failedMode = null, error = null)
            }
            notice = context.getString(
                R.string.video_compressed,
                android.text.format.Formatter.formatShortFileSize(context, item.size),
                android.text.format.Formatter.formatShortFileSize(context, compressed.size)
            )
        } finally {
            if (output.exists()) output.delete()
        }
    }

    private suspend fun compressAudioQuality(item: OutgoingMedia, mode: MediaQualityMode, token: Int) {
        val source = File(item.path)
        val info = runCatching { audioCompressor.inspect(source, item.mimeType) }.getOrNull()
        if (!qualityCurrent(item.path, token)) return
        if (info == null) {
            updateQuality(item.path) { it.copy(hint = MediaPreparationHint.UnknownMetadata) }
            return
        }
        val plan = audioCompressionPlan(info, mode.toAudioOption())
        if (!plan.transcode) {
            val hint = when (plan.reason) {
                AudioPlanReason.MetadataPresent -> MediaPreparationHint.MetadataPreserved
                AudioPlanReason.AlreadyEfficient -> MediaPreparationHint.AlreadyEfficient
                AudioPlanReason.UnknownMetadata -> MediaPreparationHint.UnknownMetadata
                AudioPlanReason.NotWorthIt -> MediaPreparationHint.NotWorthIt
                AudioPlanReason.OriginalRequested, AudioPlanReason.Transcode -> null
            }
            if (hint != null) updateQuality(item.path) { it.copy(hint = hint) }
            return
        }
        val output = File(source.parentFile, "quality-${UUID.randomUUID()}.m4a")
        try {
            audioCompressor.compress(source, output, plan) { progress ->
                if (qualityCurrent(item.path, token)) {
                    updateQuality(item.path) { it.copy(progress = progress) }
                }
            }
            if (!qualityCurrent(item.path, token)) return
            val compressed = importer.adoptCompressedAudio(item, output)
            if (!qualityCurrent(item.path, token)) return
            updateQuality(item.path) {
                it.copy(prepared = compressed, hint = null, failedMode = null, error = null)
            }
            notice = context.getString(
                R.string.audio_compressed,
                android.text.format.Formatter.formatShortFileSize(context, item.size),
                android.text.format.Formatter.formatShortFileSize(context, compressed.size)
            )
        } finally {
            if (output.exists()) output.delete()
        }
    }

    private fun nextQualityToken(path: String): Int {
        qualityToken++
        qualityTokens[path] = qualityToken
        return qualityToken
    }

    private fun cancelQualityJob(path: String) {
        qualityToken++
        qualityTokens[path] = qualityToken
        qualityJobs.remove(path)?.cancel()
    }

    private fun qualityCurrent(path: String, token: Int): Boolean = qualityTokens[path] == token

    private fun updateQuality(path: String, transform: (MediaPreparation) -> MediaPreparation) {
        val current = qualityState.value[path] ?: return
        val next = HashMap(qualityState.value)
        next[path] = transform(current)
        qualityState.value = next
    }

    private fun clearQualityState() {
        qualityJobs.values.forEach { it.cancel() }
        qualityJobs.clear()
        qualityTokens.clear()
        qualityState.value = HashMap()
    }

    fun discardAll() {
        if (sending) return
        cancelLocationRequest()
        importJob?.cancel()
        clearQualityState()
        editorJob?.cancel()
        failedCapturePaths.forEach { File(it).delete() }
        captureRetry.value = CaptureRetryState.Empty
        editor?.let { session -> if (session.temporary) File(session.sourcePath).delete() }
        editor = null
        editorSaving = false
        editorError = null
        val discarded = pending.toList()
        pendingState.value = arrayListOf()
        extra = null
        extraSendFailed = false
        importing = false
        notice = null
        errorState.value = null
        menuOpen = false
        cameraOpen = false
        scope.launch { discarded.filter { it.path.isNotBlank() }.forEach { importer.discard(it) } }
    }

    fun send(caption: String, options: MessageSendOptions, onCaptionChange: (String) -> Unit) {
        if (sending || importing || locating || preparing) return
        val preparedExtra = extra
        if (preparedExtra != null) {
            lastExtraCaption = caption
            lastExtraOptions = options
            // Claim the send slot before launching, so a double tap on Retry cannot start a second request.
            sending = true
            scope.launch {
                extraSendFailed = false
                errorState.value = null
                try {
                    val result = sendExtra(preparedExtra, options)
                    if (result.isFailure && stealthFallback.offer(result.exceptionOrNull()) { send(caption, options.copy(sendNowConsent = true), onCaptionChange) }) {
                        // Nothing was sent; the prepared item stays for the user's choice.
                    } else if (result.isFailure) {
                        errorState.value = result.exceptionOrNull().userMessage(context, R.string.composer_send_failed)
                        // Keep the prepared poll in the composer; a retry is always an explicit user action.
                        extraSendFailed = preparedExtra is MessageExtraDraft.Poll
                    } else if (extra === preparedExtra) {
                        extra = null
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    errorState.value = failure.userMessage(context, R.string.composer_send_failed)
                    extraSendFailed = preparedExtra is MessageExtraDraft.Poll
                }
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
                val originals = pending.toList()
                val prepared = originals.map { item -> item to outgoingMediaFor(item, preparationFor(item)) }
                val batches = if (prepared.size in 2..10 && prepared.all { (_, media) -> media.kind == MediaKind.Photo || media.kind == MediaKind.Video }) {
                    listOf(prepared)
                } else {
                    prepared.map { listOf(it) }
                }
                for (batch in batches) {
                    val result = sendMedia(batch.map { it.second }, nextCaption, options)
                    if (result.isFailure) {
                        val retryCaption = nextCaption
                        val asked = stealthFallback.offer(result.exceptionOrNull()) { send(retryCaption, options.copy(sendNowConsent = true), onCaptionChange) }
                        if (!asked) errorState.value = result.exceptionOrNull().userMessage(context, R.string.composer_send_failed)
                        break
                    }
                    val sent = batch.map { it.first }
                    pendingState.value = ArrayList(pending.filterNot { item -> sent.any { it.samePendingAttachment(item) } })
                    if (sent.any { qualityState.value.containsKey(it.path) }) {
                        val remainingQuality = HashMap(qualityState.value)
                        sent.forEach { remainingQuality.remove(it.path) }
                        qualityState.value = remainingQuality
                    }
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

/**
 * A repeated GIF attachment has its own staging key. Comparing only remoteFileId would
 * otherwise remove all identical GIFs after deleting or sending a single occurrence.
 */
internal fun OutgoingMedia.samePendingAttachment(other: OutgoingMedia): Boolean = when {
    stagingKey.isNotBlank() || other.stagingKey.isNotBlank() ->
        stagingKey.isNotBlank() && stagingKey == other.stagingKey
    path.isNotBlank() || other.path.isNotBlank() ->
        path.isNotBlank() && path == other.path
    else -> remoteFileId != 0 && remoteFileId == other.remoteFileId
}

@Composable
internal fun rememberAttachmentState(
    chatId: Long,
    sendMedia: suspend (List<OutgoingMedia>, String, MessageSendOptions) -> Result<Unit>,
    sendSticker: suspend (OutgoingMedia, MessageSendOptions) -> Result<Unit>,
    sendExtra: suspend (MessageExtraDraft, MessageSendOptions) -> Result<Unit>
): AttachmentState {
    val context = LocalContext.current
    val accountId = com.beykus.beygram.playback.LocalPlaybackAccountId.current
    val importer = remember(context, accountId) { OutgoingMediaImporter(context, accountId) }
    val scope = rememberCoroutineScope()
    val pending = rememberSaveable(accountId, chatId) { mutableStateOf(arrayListOf<OutgoingMedia>()) }
    val error = rememberSaveable(accountId, chatId) { mutableStateOf<String?>(null) }
    val viewOnce = rememberSaveable(accountId, chatId) { mutableStateOf(false) }
    // The editor owns the only reference to an un-staged camera take; keep it across rotation.
    val editor = rememberSaveable(accountId, chatId) { mutableStateOf<PhotoEditorSession?>(null) }
    // A failed camera import keeps its cache path across activity recreation too.
    val captureRetry = rememberSaveable(accountId, chatId, stateSaver = CaptureRetrySaver) {
        mutableStateOf(CaptureRetryState.Empty)
    }
    // Quality is per staged file, so it is saved next to the pending attachments and keyed by path.
    val quality = rememberSaveable(accountId, chatId) {
        mutableStateOf(HashMap<String, MediaPreparation>())
    }
    val currentSend by rememberUpdatedState(sendMedia)
    val currentSticker by rememberUpdatedState(sendSticker)
    val currentSendExtra by rememberUpdatedState(sendExtra)
    val state = remember(accountId, chatId) {
        AttachmentState(context, importer, scope, pending, error, viewOnce, editor, captureRetry, quality,
            sendMedia = { media, caption, options -> currentSend(media, caption, options) },
            sendSticker = { media, options -> currentSticker(media, options) },
            sendExtra = { draft, options -> currentSendExtra(draft, options) })
    }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MediaLimits.MAX_ATTACHMENTS)) { state.prepare(it, false) }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { state.prepare(it, true) }
    val audio = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { state.prepare(it, false) }
    state.pickPhotos = { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
    state.pickDocuments = { documents.launch(arrayOf("*/*")) }
    state.pickAudio = { audio.launch(arrayOf("audio/*")) }
    return state
}

@Composable
internal fun AttachmentButton(state: AttachmentState, enabled: Boolean = true) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val available = enabled && !state.sending && !state.importing && !state.locating
    // The sticker/GIF entry is drawn next to this button by the chat screen, not here.
    IconButton(
        onClick = {
            // The action grid replaces the keyboard, not the other way round.
            focus.clearFocus()
            keyboard?.hide()
            state.toggleMenu()
        },
        enabled = available,
        modifier = Modifier.size(48.dp).semantics { contentDescription = context.getString(R.string.composer_attach_description) }
    ) {
        Icon(
            Icons.Rounded.AttachFile,
            contentDescription = null,
            tint = if (state.menuOpen || state.active) palette.accent else palette.textSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}

/** Inline panel above the input: action grid, then the prepared attachments. */
@Composable
internal fun AttachmentPanel(
    state: AttachmentState,
    caption: String,
    captionLimit: Int,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onDownloadFile: (Int) -> Unit,
    allowViewOnce: Boolean = false
) {
    val palette = beyGramPalette()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val configuration = LocalConfiguration.current
    val visible = state.menuOpen || state.active || state.error != null || state.notice != null || state.canRetryCapture
    val viewOnceAvailable = ViewOncePolicy.canSend(state.outgoingPending, allowViewOnce, scheduled = false)
    val maxListHeight = (configuration.screenHeightDp * 0.48f)
        .dp.coerceIn(150.dp, 420.dp)
    LaunchedEffect(viewOnceAvailable, state.pending) {
        if (!viewOnceAvailable) state.viewOnce = false
    }
    var discardConfirm by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = state.menuOpen) {
        state.dismissTransientPanel()
    }
    if (!visible) return
    Column(Modifier.fillMaxWidth().background(palette.surface)) {
        when {
            state.menuOpen -> AttachmentMenu(
                allowsPolls = state.allowsPolls,
                allowsMusic = state.allowsMusic,
                enabled = !state.sending && !state.importing && !state.locating,
                chatObjectsEnabled = state.pending.isEmpty(),
                onAction = state::performAction,
                onClose = state::closeMenu
            )
            state.active -> PreparedAttachmentsPanel(
                state = state,
                caption = caption,
                captionLimit = captionLimit,
                observeFile = observeFile,
                onDownloadFile = onDownloadFile,
                viewOnceAvailable = viewOnceAvailable,
                maxListHeight = maxListHeight,
                onDiscardRequested = { discardConfirm = true }
            )
        }
        state.error?.let { message ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                Text(message, color = palette.danger, fontSize = 13.sp, modifier = Modifier.errorAnnouncement())
                if (state.extraSendFailed) {
                    Text(stringResource(R.string.poll_send_retained), color = palette.textSecondary, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = state::discardFailedExtra, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                            Text(stringResource(R.string.common_cancel), color = palette.textSecondary)
                        }
                        TextButton(onClick = state::retryExtra, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                            Text(stringResource(R.string.common_retry), color = palette.accent)
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(start = 14.dp)) {
            if (state.canRetryCapture && !state.importing) {
                Row(Modifier.fillMaxWidth().padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.capture_retry_hint), color = palette.textSecondary, fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { state.retryCapture() }, enabled = !state.importing && !state.sending, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Refresh, stringResource(R.string.capture_retry), tint = palette.accent)
                    }
                }
            }
            state.notice?.let { message ->
                Row(Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        message, color = palette.textSecondary, fontSize = 12.sp,
                        modifier = Modifier.weight(1f).errorAnnouncement()
                    )
                    IconButton(onClick = { state.dismissNotice() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
    if (discardConfirm) {
        AlertDialog(
            onDismissRequest = { discardConfirm = false },
            title = { Text(stringResource(R.string.composer_attachments_clear_title)) },
            text = { Text(stringResource(R.string.composer_attachments_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    discardConfirm = false
                    state.discardAll()
                    focus.clearFocus()
                    keyboard?.hide()
                }) { Text(stringResource(R.string.composer_attachments_clear_confirm), color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { discardConfirm = false }) { Text(stringResource(R.string.composer_attachments_clear_keep)) }
            }
        )
    }
}

@Composable
private fun PreparedAttachmentsPanel(
    state: AttachmentState,
    caption: String,
    captionLimit: Int,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onDownloadFile: (Int) -> Unit,
    viewOnceAvailable: Boolean,
    maxListHeight: androidx.compose.ui.unit.Dp,
    onDiscardRequested: () -> Unit
) {
    val palette = beyGramPalette()
    val enabled = !state.sending && !state.importing
    val itemCount = state.pending.size + if (state.extra != null) 1 else 0
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.composer_attachments_title, itemCount, MAX_PENDING_ATTACHMENTS),
            color = palette.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDiscardRequested, enabled = !state.sending, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Close, stringResource(R.string.composer_attachments_clear_action), tint = palette.textSecondary)
        }
    }
    if (isCompactAttachmentSet(state.pending, state.extra)) {
        // Mixed batches now share the fixed-height rail. Polls, contacts and other
        // MessageExtraDraft actions keep their dedicated semantic cards.
        CompactVisualAttachmentTray(state, observeFile, onDownloadFile, enabled, maxListHeight)
    } else Column(
        Modifier.fillMaxWidth().heightIn(max = maxListHeight).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)
    ) {
        if (state.locating) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                    .background(palette.surface2).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), color = palette.accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.chat_location_locating),
                    color = palette.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        state.extra?.let { extra ->
            ExtraAttachmentCard(extra, enabled, onRemove = { state.removeExtra() })
        }
        state.pending.forEach { item ->
            PendingAttachmentCard(state, item, observeFile, onDownloadFile, enabled)
        }
        if (state.importing) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), color = palette.accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.composer_preparing), color = palette.textSecondary, fontSize = 13.sp)
            }
        }
    }
    if (viewOnceAvailable) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(value = state.viewOnce, enabled = !state.sending, role = Role.Checkbox) { state.viewOnce = it }
                .padding(horizontal = 14.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = state.viewOnce, onCheckedChange = null, enabled = !state.sending)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.composer_view_once), color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.composer_view_once_hint), color = palette.textSecondary, fontSize = 12.sp)
            }
        }
    }
    if (state.extra == null && !state.locating) {
        val count = caption.codePointCount(0, caption.length)
        Text(
            "$count / $captionLimit",
            color = if (count > captionLimit) palette.danger else palette.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 16.dp, bottom = 2.dp)
        )
    }
}

@Composable
private fun ExtraAttachmentCard(extra: MessageExtraDraft, enabled: Boolean, onRemove: () -> Unit) {
    val palette = beyGramPalette()
    val label = extraLabel(extra)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
            .background(palette.surface2).padding(start = 6.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            when (extra) {
                is MessageExtraDraft.Poll -> Icons.Rounded.Poll
                is MessageExtraDraft.Location -> Icons.Rounded.LocationOn
                is MessageExtraDraft.Contact -> Icons.Rounded.Person
                is MessageExtraDraft.Dice -> Icons.Rounded.Casino
                is MessageExtraDraft.MusicFile, is MessageExtraDraft.MusicBotResult -> Icons.Rounded.MusicNote
            },
            contentDescription = null,
            tint = palette.accentInk,
            modifier = Modifier.size(40.dp).background(palette.accentSoft, RoundedCornerShape(9.dp)).padding(9.dp)
        )
        Text(
            label,
            color = palette.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
        )
        AttachmentRemoveButton(fileName = label, enabled = enabled, onClick = onRemove)
    }
}

/** A subset predicate: visual-only albums have no files or chat-object extras. */
internal fun isCompactVisualAlbum(items: List<OutgoingMedia>, extra: MessageExtraDraft?): Boolean =
    extra == null && items.isNotEmpty() && items.all {
        it.kind == MediaKind.Photo || it.kind == MediaKind.Video
    }

/** A real staged media list, including documents, stays compact; chat-object extras do not. */
internal fun isCompactAttachmentSet(items: List<OutgoingMedia>, extra: MessageExtraDraft?): Boolean =
    extra == null && items.isNotEmpty()

/** A video delivered as a document must show its filename, unlike ordinary photo/video. */
internal fun isDocumentAttachmentTile(item: OutgoingMedia, prep: MediaPreparation?): Boolean =
    item.kind !in setOf(MediaKind.Photo, MediaKind.Video, MediaKind.Animation) ||
        (item.kind == MediaKind.Video && prep?.delivery == MediaDeliveryMode.File)

/** Duration is omitted when missing, never fabricated. */
internal fun attachmentDurationLabel(seconds: Int): String? =
    seconds.takeIf { it > 0 }?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }

/** The same list used by AttachmentState.send defines the album order. */
internal fun reorderPendingAttachment(
    pending: List<OutgoingMedia>,
    item: OutgoingMedia,
    offset: Int
): List<OutgoingMedia> {
    if (offset !in -1..1 || offset == 0) return pending
    val index = pending.indexOfFirst { it.samePendingAttachment(item) }
    if (index < 0 || index + offset !in pending.indices) return pending
    return ArrayList(pending).apply { add(index + offset, removeAt(index)) }
}

/** Identity independent of filename, as different photos are commonly both named photo.jpg. */
internal fun stagedMediaKey(item: OutgoingMedia, index: Int): String = when {
    item.stagingKey.isNotBlank() -> "staged:" + item.stagingKey
    item.path.isNotBlank() -> "path:" + item.path
    // Legacy unsuffixed remote entries from saved state still need distinct Compose keys.
    item.remoteFileId != 0 -> "remote:" + item.remoteFileId + ":" + index
    else -> "index:" + index
}

/** Compact gallery of staged photo, video, animation and file tiles; per-file details expand on demand. */
@Composable
private fun CompactVisualAttachmentTray(
    state: AttachmentState,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onDownloadFile: (Int) -> Unit,
    enabled: Boolean,
    maxListHeight: androidx.compose.ui.unit.Dp
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    var selectedItemKey by remember { mutableStateOf<String?>(null) }
    var menuItemKey by remember { mutableStateOf<String?>(null) }
    val activeItem = state.pending.withIndex().firstOrNull { (index, item) ->
        stagedMediaKey(item, index) == selectedItemKey
    }?.value
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(state.pending, key = { index, item -> stagedMediaKey(item, index) }) { index, item ->
                val preparation = state.preparationFor(item)
                val itemKey = stagedMediaKey(item, index)
                val moveLeft = stringResource(R.string.composer_move_attachment_left)
                val moveRight = stringResource(R.string.composer_move_attachment_right)
                val actionsLabel = stringResource(R.string.composer_attachment_actions)
                val documentTile = isDocumentAttachmentTile(item, preparation)
                Box(
                    modifier = Modifier.size(100.dp).clip(RoundedCornerShape(14.dp))
                        .background(palette.surface2)
                ) {
                    if (documentTile) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                if (item.kind == MediaKind.Audio || item.mimeType.startsWith("audio/"))
                                    Icons.Rounded.MusicNote else Icons.Rounded.Description,
                                contentDescription = null,
                                tint = palette.accentInk,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    item.fileName.ifBlank { item.kind.label(context) },
                                    color = palette.textPrimary, fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.size > 0L) {
                                    Text(formatMediaSize(item.size), color = palette.textSecondary,
                                        fontSize = 10.sp, maxLines = 1)
                                }
                            }
                        }
                    } else {
                        // Real bounded thumbnails for media; filenames are hidden for visual media.
                        PendingAttachmentPreview(
                            item, observeFile, onDownloadFile, Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().combinedClickable(
                            enabled = enabled, role = Role.Button,
                            onClick = {
                                if (item.kind == MediaKind.Photo) state.editPhoto(item)
                                else selectedItemKey = if (selectedItemKey == itemKey) null else itemKey
                            },
                            onLongClick = { menuItemKey = itemKey },
                            onLongClickLabel = actionsLabel
                        ).semantics {
                            contentDescription = if (item.kind == MediaKind.Photo)
                                context.getString(R.string.composer_edit_photo_named, item.fileName)
                            else item.kind.label(context) + ": " + item.fileName
                            // TalkBack can reorder without simulating a long press or drag.
                            customActions = buildList {
                                if (enabled && index > 0) add(CustomAccessibilityAction(moveLeft) {
                                    state.movePending(item, -1); true
                                })
                                if (enabled && index < state.pending.lastIndex) add(CustomAccessibilityAction(moveRight) {
                                    state.movePending(item, 1); true
                                })
                            }
                        }
                    )
                    if (item.kind == MediaKind.Video && !documentTile) {
                        Row(
                            modifier = Modifier.align(Alignment.BottomStart)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(7.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(20.dp))
                            attachmentDurationLabel(item.durationSeconds)?.let { duration ->
                                Text(duration, color = Color.White, fontSize = 10.sp)
                            }
                        }
                    } else if (item.kind == MediaKind.Animation && !documentTile) {
                        Text("GIF", color = Color.White, fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                    if (preparation?.processing == true) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp).size(20.dp),
                            color = Color.White, strokeWidth = 2.dp
                        )
                    }
                    DropdownMenu(
                        expanded = enabled && menuItemKey == itemKey,
                        onDismissRequest = { menuItemKey = null }
                    ) {
                        DropdownMenuItem(
                            text = { Text(moveLeft) },
                            enabled = index > 0,
                            onClick = { menuItemKey = null; state.movePending(item, -1) }
                        )
                        DropdownMenuItem(
                            text = { Text(moveRight) },
                            enabled = index < state.pending.lastIndex,
                            onClick = { menuItemKey = null; state.movePending(item, 1) }
                        )
                    }
                    IconButton(
                        onClick = { menuItemKey = null; state.remove(item) },
                        enabled = enabled,
                        modifier = Modifier.align(Alignment.TopEnd).size(48.dp).semantics {
                            contentDescription = context.getString(R.string.composer_remove_attachment_named, item.fileName)
                        }
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                                .background(Color.Black.copy(alpha = 0.70f), CircleShape)
                                .padding(4.dp)
                        )
                    }
                }
            }
            if (state.pending.size < MAX_PENDING_ATTACHMENTS) {
                item(key = "add-more-media") {
                    Box(
                        Modifier.size(100.dp).clip(RoundedCornerShape(14.dp))
                            .background(palette.surface2)
                            .clickable(enabled = enabled, role = Role.Button, onClick = state.pickPhotos)
                            .semantics { contentDescription = context.getString(R.string.composer_attach_description) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = palette.accentInk,
                            modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
        if (activeItem != null) {
            val preparation = state.preparationFor(activeItem)
            if (preparation != null) {
                // Quality options are intentionally collapsed by default. On small screens,
                // bound their expanded height independently of the fixed media rail.
                // The user must be able to reach File/Media, retry and the last preset.
                Column(
                    Modifier.fillMaxWidth()
                        .heightIn(max = (maxListHeight - 110.dp).coerceAtLeast(80.dp))
                        .verticalScroll(rememberScrollState())
                ) {
                    MediaQualityPanel(
                        item = activeItem, preparation = preparation, enabled = enabled,
                        onModeSelected = { state.selectQuality(activeItem, it) },
                        onDeliverySelected = { state.selectVideoDelivery(activeItem, it) },
                        onCancel = { state.cancelQualityPreparation(activeItem) },
                        onRetry = { state.retryQualityPreparation(activeItem) },
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
            } else if (isDocumentAttachmentTile(activeItem, null)) {
                // Full filename can be inspected without permanently expanding every file tile.
                Column(
                    Modifier.fillMaxWidth()
                        .heightIn(max = (maxListHeight - 110.dp).coerceAtLeast(80.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(activeItem.fileName.ifBlank { activeItem.kind.label(context) },
                        color = palette.textPrimary, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold)
                    if (activeItem.size > 0) {
                        Text(formatMediaSize(activeItem.size), color = palette.textSecondary,
                            fontSize = 12.sp)
                    }
                }
            }
        }
        if (state.importing) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), color = palette.accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.composer_preparing),
                    color = palette.textSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PendingAttachmentCard(
    state: AttachmentState,
    item: OutgoingMedia,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onDownloadFile: (Int) -> Unit,
    enabled: Boolean
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val preparation = state.preparationFor(item)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
            .background(palette.surface2).padding(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PendingAttachmentPreview(item, observeFile, onDownloadFile)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    item.fileName,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                val details = listOfNotNull(item.kind.label(context), formatMediaSize(item.size).takeIf { item.size > 0 })
                Text(details.joinToString(" · "), color = palette.textSecondary, fontSize = 12.sp, maxLines = 1)
            }
            if (item.kind == MediaKind.Photo) {
                IconButton(
                    onClick = { state.editPhoto(item) },
                    enabled = enabled,
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = context.getString(R.string.composer_edit_photo_named, item.fileName)
                    }
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = palette.textSecondary.copy(alpha = if (enabled) 1f else 0.38f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            AttachmentRemoveButton(fileName = item.fileName, enabled = enabled, onClick = { state.remove(item) })
        }
        if (preparation != null) {
            MediaQualityPanel(
                item = item,
                preparation = preparation,
                enabled = enabled,
                onModeSelected = { state.selectQuality(item, it) },
                onDeliverySelected = { state.selectVideoDelivery(item, it) },
                onCancel = { state.cancelQualityPreparation(item) },
                onRetry = { state.retryQualityPreparation(item) }
            )
        }
    }
}

/**
 * A local thumbnail when the attachment has one; a TDLib thumbnail for a GIF picked from the server. The full GIF is
 * deliberately not downloaded: it is sent by file id and only the small preview is needed here.
 */
@Composable
private fun PendingAttachmentPreview(
    item: OutgoingMedia,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    modifier: Modifier = Modifier.size(40.dp).clip(RoundedCornerShape(9.dp))
) {
    when {
        item.thumbnailPath != null -> LocalMediaImage(item.thumbnailPath, modifier, maxDimension = 320)
        item.thumbnailRemoteFileId > 0 ->
            RemoteAttachmentThumbnail(item.thumbnailRemoteFileId, observeFile, downloadFile, modifier)
        item.path.isNotBlank() && (item.kind == MediaKind.Photo || item.kind == MediaKind.Video || item.kind == MediaKind.Animation) ->
            LocalMediaImage(item.path, modifier, maxDimension = 320, allowVideoFrame = item.kind != MediaKind.Photo)
        else -> Icon(Icons.Rounded.Description, null, tint = beyGramPalette().accentInk,
            modifier = modifier.background(beyGramPalette().accentSoft).padding(9.dp))
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
private fun extraLabel(extra: MessageExtraDraft): String = when (extra) {
    is MessageExtraDraft.Poll -> stringResource(
        R.string.composer_extra_poll,
        extra.poll.normalized().question.ifBlank { stringResource(R.string.chat_poll_untitled) }
    )
    is MessageExtraDraft.Location -> stringResource(R.string.composer_extra_location)
    is MessageExtraDraft.Contact -> stringResource(R.string.composer_extra_contact, extra.title)
    is MessageExtraDraft.Dice -> stringResource(R.string.composer_extra_dice, extra.emoji)
    is MessageExtraDraft.MusicFile -> stringResource(R.string.composer_extra_music, extra.label)
    is MessageExtraDraft.MusicBotResult -> stringResource(R.string.composer_extra_music, extra.label)
}
