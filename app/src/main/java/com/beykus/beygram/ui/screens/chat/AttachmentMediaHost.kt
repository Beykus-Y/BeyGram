package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Full-screen media surfaces are siblings of the chat, not dialogs. A pointer barrier keeps taps
 * on noninteractive areas from reaching the chat header or list underneath.
 */
internal fun Modifier.consumeOverlayPointerInput(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            // Final pass: children have already handled buttons and crop drags.
            val event = awaitPointerEvent(PointerEventPass.Final)
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
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
