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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.media.AudioCompressionPreferences
import com.beykus.beygram.data.media.AudioCompressor
import com.beykus.beygram.data.media.AudioPlanReason
import com.beykus.beygram.data.media.AudioQualityOption
import com.beykus.beygram.data.media.CaptureRetryState
import com.beykus.beygram.data.media.MediaLimits
import com.beykus.beygram.data.media.NormalizedCrop
import com.beykus.beygram.data.media.OutgoingMediaImporter
import com.beykus.beygram.data.media.PhotoEditor
import com.beykus.beygram.data.media.PhotoExportQuality
import com.beykus.beygram.data.media.PhotoRotation
import com.beykus.beygram.data.media.VideoCompressionPreferences
import com.beykus.beygram.data.media.VideoCompressor
import com.beykus.beygram.data.media.VideoLimitIssue
import com.beykus.beygram.data.media.VideoQualityOption
import com.beykus.beygram.data.media.audioCompressionPlan
import com.beykus.beygram.data.media.videoCompressionPlan
import com.beykus.beygram.data.media.videoLimitIssue
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
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

/** Visible progress for one attachment that is being re-encoded before it can be sent. */
internal enum class MediaProcessingKind { Video, Audio }

internal data class MediaProcessing(
    val path: String,
    val fileName: String,
    val progress: Float?,
    val kind: MediaProcessingKind
)

private val CaptureRetrySaver = Saver<CaptureRetryState, String>(
    save = { it.failedPath.orEmpty() },
    restore = { CaptureRetryState(it.takeIf(String::isNotBlank)) }
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
    private val editorState: MutableState<PhotoEditorSession?>,
    private val captureRetry: MutableState<CaptureRetryState>,
    private val sendMedia: suspend (List<OutgoingMedia>, String, MessageSendOptions) -> Result<Unit>
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
    var processing by mutableStateOf<MediaProcessing?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    val canRetryCapture: Boolean get() = captureRetry.value.canRetry
    var videoQuality by mutableStateOf(VideoCompressionPreferences(context).load())
        private set
    var audioQuality by mutableStateOf(AudioCompressionPreferences(context).load())
        private set
    val pending: List<OutgoingMedia> get() = pendingState.value
    val error: String? get() = errorState.value
    private val videoPreferences = VideoCompressionPreferences(context)
    private val audioPreferences = AudioCompressionPreferences(context)
    private val compressor = VideoCompressor(context)
    private val audioCompressor = AudioCompressor(context)
    private var compressionCancelled = false
    private var compressionJob: Job? = null
    private var importJob: Job? = null
    private var editorJob: Job? = null
    private val failedCapturePath: String? get() = captureRetry.value.failedPath
    var pickPhotos: () -> Unit = {}
    var pickDocuments: () -> Unit = {}
    var pickAudio: () -> Unit = {}

    /** True while the panel replaces the plain composer (files are being imported or are ready to send). */
    val active: Boolean get() = pending.isNotEmpty() || importing

    fun prepare(uris: List<android.net.Uri>, asDocument: Boolean) {
        if (uris.isEmpty()) return
        if (pending.size + uris.size > MediaLimits.MAX_ATTACHMENTS) {
            errorState.value = context.getString(R.string.composer_too_many_attachments)
            return
        }
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
                        !asDocument && prepared.kind == MediaKind.Video -> maybeCompressVideo(prepared)
                        !asDocument && prepared.kind == MediaKind.Document && prepared.mimeType.startsWith("audio/") ->
                            maybeCompressAudio(prepared)
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
        if (sending || importing) return
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
        val previous = failedCapturePath
        if (previous != null && previous != file.absolutePath) File(previous).delete()
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
                setFailedCapture(null)
                when {
                    prepared.kind == MediaKind.Video -> maybeCompressVideo(prepared)
                    prepared.kind == MediaKind.Document && prepared.mimeType.startsWith("video/") ->
                        notice = context.getString(R.string.video_container_as_file)
                }
            } catch (cancelled: CancellationException) {
                // Rotation/scope cancellation keeps the registered retry path. An explicit discard
                // has already cleared it, so only then is deleting the file appropriate.
                if (failedCapturePath != file.absolutePath) file.delete()
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

    private fun setFailedCapture(path: String?) {
        captureRetry.value = if (path == null) CaptureRetryState.Empty else captureRetry.value.failed(path)
    }

    fun retryCapture() {
        val path = failedCapturePath ?: return
        if (importing || sending) return
        val file = File(path)
        if (!file.isFile || file.length() <= 0L) {
            setFailedCapture(null)
            errorState.value = context.getString(R.string.capture_retry_missing)
            return
        }
        setFailedCapture(null)
        importCaptureFile(file)
    }

    fun remove(item: OutgoingMedia) {
        if (sending || importing) return
        pendingState.value = ArrayList(pending.filterNot { it.path == item.path })
        scope.launch { importer.discard(item) }
    }

    /** Cycles the preset used for the next imported video; already staged files are never re-encoded silently. */
    fun cycleVideoQuality() {
        val next = when (videoQuality) {
            VideoQualityOption.Compact -> VideoQualityOption.Balanced
            VideoQualityOption.Balanced -> VideoQualityOption.Original
            VideoQualityOption.Original -> VideoQualityOption.Compact
        }
        videoQuality = next
        videoPreferences.save(next)
        notice = context.getString(R.string.video_quality_notice, context.getString(next.labelRes()))
    }

    fun cycleAudioQuality() {
        val next = when (audioQuality) {
            AudioQualityOption.Compact -> AudioQualityOption.Balanced
            AudioQualityOption.Balanced -> AudioQualityOption.Original
            AudioQualityOption.Original -> AudioQualityOption.Compact
        }
        audioQuality = next
        audioPreferences.save(next)
        notice = context.getString(R.string.audio_quality_notice, context.getString(next.labelRes()))
    }

    fun dismissNotice() {
        notice = null
    }

    /** Stops the running compression but keeps the already staged original video. */
    fun cancelCompression() {
        if (processing == null) return
        compressionCancelled = true
        compressionJob?.cancel()
    }

    /**
     * Applies the size/duration limits and, when needed, one Media3 Transformer pass. The original
     * stays pending until the compressed file is adopted, so a failure or cancel loses nothing.
     * Compression runs in its own job: cancelling it never aborts import of the remaining picks.
     */
    private suspend fun maybeCompressVideo(original: OutgoingMedia) {
        val option = videoQuality
        if (option == VideoQualityOption.Original) return
        val source = File(original.path)
        val info = runCatching { compressor.inspect(source, original.mimeType) }.getOrNull() ?: return
        val limit = videoLimitIssue(info)
        if (limit != null) {
            // Keep the bytes, send them as a file and say why instead of failing the attachment.
            replacePending(original, original.copy(kind = MediaKind.Document))
            notice = context.getString(
                if (limit == VideoLimitIssue.TooLong) R.string.video_too_long_as_file else R.string.video_too_large_as_file
            )
            return
        }
        val plan = videoCompressionPlan(info, option)
        if (!plan.transcode) return
        compressionCancelled = false
        processing = MediaProcessing(original.path, original.fileName, progress = 0f, kind = MediaProcessingKind.Video)
        val output = File(source.parentFile, "compressed-${UUID.randomUUID()}.mp4")
        // Never let a failed child cancel the composer scope: the failure is a normal result and the
        // original attachment must stay usable.
        val task = scope.async<Result<OutgoingMedia>> {
            try {
                compressor.compress(source, output, plan, info) { progress ->
                    processing = processing?.copy(progress = progress)
                }
                Result.success(importer.adoptCompressedVideo(original, output))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Result.failure(failure)
            }
        }
        compressionJob = task
        try {
            val compressed = task.await().getOrElse {
                output.delete()
                notice = context.getString(R.string.video_compression_failed_original)
                return
            }
            // The composer may have been cleared while the transform finished; do not resurrect it.
            coroutineContext.ensureActive()
            replacePending(original, compressed)
            File(original.path).takeIf { it.absolutePath != compressed.path }?.delete()
            notice = context.getString(
                R.string.video_compressed,
                android.text.format.Formatter.formatShortFileSize(context, original.size),
                android.text.format.Formatter.formatShortFileSize(context, compressed.size)
            )
        } catch (cancelled: CancellationException) {
            output.delete()
            // A leave-the-screen cancellation must keep propagating; an explicit cancel keeps the original.
            if (!compressionCancelled) {
                task.cancel()
                throw cancelled
            }
            notice = context.getString(R.string.video_compression_cancelled)
        } finally {
            if (compressionJob === task) compressionJob = null
            processing = null
        }
    }

    private fun replacePending(original: OutgoingMedia, replacement: OutgoingMedia) {
        pendingState.value = ArrayList(pending.map { if (it.path == original.path) replacement else it })
    }

    /**
     * Same safe contract as video: only plain audio documents are candidates, tagged files and
     * efficient files are kept, and the original stays pending until the AAC export is adopted.
     * Voice/video notes never reach this method.
     */
    private suspend fun maybeCompressAudio(original: OutgoingMedia) {
        if (original.kind != MediaKind.Document || !original.mimeType.startsWith("audio/")) return
        val option = audioQuality
        if (option == AudioQualityOption.Original) return
        val source = File(original.path)
        val info = runCatching { audioCompressor.inspect(source, original.mimeType) }.getOrNull() ?: return
        val plan = audioCompressionPlan(info, option)
        if (plan.reason == AudioPlanReason.MetadataPresent) {
            notice = context.getString(R.string.audio_metadata_kept)
            return
        }
        if (!plan.transcode) return
        compressionCancelled = false
        processing = MediaProcessing(original.path, original.fileName, progress = 0f, kind = MediaProcessingKind.Audio)
        val output = File(source.parentFile, "compressed-${UUID.randomUUID()}.m4a")
        val task = scope.async<Result<OutgoingMedia>> {
            try {
                audioCompressor.compress(source, output, plan) { progress ->
                    processing = processing?.copy(progress = progress)
                }
                Result.success(importer.adoptCompressedAudio(original, output))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Result.failure(failure)
            }
        }
        compressionJob = task
        try {
            val compressed = task.await().getOrElse {
                output.delete()
                notice = context.getString(R.string.audio_compression_failed_original)
                return
            }
            coroutineContext.ensureActive()
            replacePending(original, compressed)
            File(original.path).takeIf { it.absolutePath != compressed.path }?.delete()
            notice = context.getString(
                R.string.audio_compressed,
                android.text.format.Formatter.formatShortFileSize(context, original.size),
                android.text.format.Formatter.formatShortFileSize(context, compressed.size)
            )
        } catch (cancelled: CancellationException) {
            output.delete()
            if (!compressionCancelled) {
                task.cancel()
                throw cancelled
            }
            notice = context.getString(R.string.audio_compression_cancelled)
        } finally {
            if (compressionJob === task) compressionJob = null
            processing = null
        }
    }

    fun discardAll() {
        if (sending) return
        importJob?.cancel()
        compressionJob?.cancel()
        editorJob?.cancel()
        failedCapturePath?.let { File(it).delete() }
        setFailedCapture(null)
        editor?.let { session -> if (session.temporary) File(session.sourcePath).delete() }
        editor = null
        editorSaving = false
        editorError = null
        val discarded = pending.toList()
        pendingState.value = arrayListOf()
        importing = false
        processing = null
        notice = null
        compressionCancelled = false
        errorState.value = null
        menuOpen = false
        cameraOpen = false
        scope.launch { discarded.forEach { importer.discard(it) } }
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
    sendMedia: suspend (List<OutgoingMedia>, String, MessageSendOptions) -> Result<Unit>
): AttachmentState {
    val context = LocalContext.current
    val accountId = com.beykus.beygram.playback.LocalPlaybackAccountId.current
    val importer = remember(context, accountId) { OutgoingMediaImporter(context, accountId) }
    val scope = rememberCoroutineScope()
    val pending = rememberSaveable(accountId, chatId) { mutableStateOf(arrayListOf<OutgoingMedia>()) }
    val error = rememberSaveable(accountId, chatId) { mutableStateOf<String?>(null) }
    // The editor owns the only reference to an un-staged camera take; keep it across rotation.
    val editor = rememberSaveable(accountId, chatId) { mutableStateOf<PhotoEditorSession?>(null) }
    // A failed camera import keeps its cache path across activity recreation too.
    val captureRetry = rememberSaveable(accountId, chatId, stateSaver = CaptureRetrySaver) {
        mutableStateOf(CaptureRetryState.Empty)
    }
    val currentSend by androidx.compose.runtime.rememberUpdatedState(sendMedia)
    val state = remember(accountId, chatId) { AttachmentState(context, importer, scope, pending, error, editor, captureRetry) { media, caption, options -> currentSend(media, caption, options) } }
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
    IconButton(onClick = { state.menuOpen = !state.menuOpen }, enabled = enabled && !state.sending && !state.importing,
        modifier = Modifier.size(48.dp).semantics { contentDescription = context.getString(R.string.composer_attach_description) }) {
        Icon(Icons.Rounded.AttachFile, contentDescription = null, tint = if (state.menuOpen || state.active) palette.accent else palette.textSecondary, modifier = Modifier.size(24.dp))
    }
}

/** Inline strip above the input: type picker, then the list of prepared attachments. */
@Composable
internal fun AttachmentPanel(state: AttachmentState, caption: String, captionLimit: Int) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val visible = state.menuOpen || state.active || state.error != null || state.notice != null
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
                        TypeChip(Icons.Rounded.PhotoCamera, stringResource(R.string.composer_type_camera)) {
                            state.menuOpen = false
                            focus.clearFocus()
                            keyboard?.hide()
                            state.openCamera()
                        }
                        TypeChip(Icons.Rounded.Image, stringResource(R.string.composer_type_photo_video)) { state.menuOpen = false; state.pickPhotos() }
                        TypeChip(Icons.Rounded.GraphicEq, stringResource(R.string.composer_type_audio)) { state.menuOpen = false; state.pickAudio() }
                        TypeChip(Icons.Rounded.Description, stringResource(R.string.composer_type_file)) { state.menuOpen = false; state.pickDocuments() }
                        TypeChip(
                            Icons.Rounded.Compress,
                            stringResource(R.string.video_quality_chip, stringResource(state.videoQuality.labelRes()))
                        ) {
                            focus.clearFocus()
                            keyboard?.hide()
                            state.cycleVideoQuality()
                        }
                        TypeChip(
                            Icons.Rounded.GraphicEq,
                            stringResource(R.string.audio_quality_chip, stringResource(state.audioQuality.labelRes()))
                        ) {
                            focus.clearFocus()
                            keyboard?.hide()
                            state.cycleAudioQuality()
                        }
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
                            if (item.kind == com.beykus.beygram.domain.model.MediaKind.Photo) {
                                IconButton(
                                    onClick = { state.editPhoto(item) },
                                    enabled = !state.sending && !state.importing,
                                    modifier = Modifier.size(48.dp).semantics {
                                        contentDescription = context.getString(R.string.composer_edit_photo_named, item.fileName)
                                    }
                                ) {
                                    Icon(
                                        Icons.Rounded.Edit, contentDescription = null,
                                        tint = palette.textSecondary.copy(alpha = if (state.sending || state.importing) 0.38f else 1f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
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
        state.processing?.let { progress ->
            val fraction = progress.progress
            val audio = progress.kind == MediaProcessingKind.Audio
            Row(Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (fraction != null) stringResource(
                            if (audio) R.string.audio_compressing else R.string.video_compressing,
                            (fraction * 100).toInt().coerceIn(0, 100)
                        ) else stringResource(if (audio) R.string.audio_compressing_indeterminate else R.string.video_compressing_indeterminate),
                        color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.errorAnnouncement()
                    )
                    Spacer(Modifier.height(4.dp))
                    if (fraction != null) {
                        LinearProgressIndicator(
                            progress = { fraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = palette.accent,
                            trackColor = palette.surface2
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = palette.accent,
                            trackColor = palette.surface2
                        )
                    }
                    Text(progress.fileName, color = palette.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { state.cancelCompression() }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        stringResource(if (audio) R.string.audio_compression_cancel else R.string.video_compression_cancel),
                        tint = palette.textSecondary
                    )
                }
            }
        }
        state.error?.let { Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp)) }
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

@Composable
private fun TypeChip(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(Modifier.defaultMinSize(minHeight = 48.dp).clip(RoundedCornerShape(14.dp)).background(palette.accentSoft).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = palette.accentInk, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = palette.accentInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun VideoQualityOption.labelRes(): Int = when (this) {
    VideoQualityOption.Compact -> R.string.video_quality_compact
    VideoQualityOption.Balanced -> R.string.video_quality_balanced
    VideoQualityOption.Original -> R.string.video_quality_original
}

private fun AudioQualityOption.labelRes(): Int = when (this) {
    AudioQualityOption.Compact -> R.string.audio_quality_compact
    AudioQualityOption.Balanced -> R.string.audio_quality_balanced
    AudioQualityOption.Original -> R.string.audio_quality_original
}
