package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.Composable

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
            onCancel = state::cancelPhotoEditor,
            onSendOriginal = state::sendEditorOriginal,
            onSave = state::savePhotoEdit
        )
    }
}
