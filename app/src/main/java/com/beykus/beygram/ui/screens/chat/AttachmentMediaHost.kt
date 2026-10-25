package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Full-screen media surfaces are siblings of the chat, not dialogs. Participating in hit testing
 * keeps the chat underneath out of the pointer path without consuming events that belong to the
 * overlay's own buttons and crop gestures.
 */
internal fun Modifier.overlayPointerBarrier(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}

/**
 * Full-screen media surfaces owned by the composer. They are overlays of the chat screen rather
 * than app routes, so opening the camera or the editor never loses the draft, reply, topic or
 * send options.
 */
@Composable
internal fun AttachmentMediaHost(state: AttachmentState) {
    if (state.cameraOpen) {
        CameraCaptureScreen(
            onClose = state::closeCamera,
            onPhoto = state::capturePhoto,
            onVideo = { file, _ -> state.acceptVideoCapture(file) }
        )
    }
    state.editor?.let { session ->
        PhotoEditorScreen(
            sourcePath = session.sourcePath,
            saving = state.editorSaving,
            error = state.editorError,
            canSendOriginal = session.temporary,
            initialRotation = session.rotation,
            initialCrop = session.crop,
            initialQuality = session.quality,
            initialKeepOriginal = session.keepOriginal,
            onDraftChange = state::updateEditorDraft,
            onCancel = state::cancelPhotoEditor,
            onSendOriginal = state::sendEditorOriginal,
            onSave = state::savePhotoEdit
        )
    }
}
