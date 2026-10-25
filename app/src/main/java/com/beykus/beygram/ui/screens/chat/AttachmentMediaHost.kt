package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.Composable

/**
 * Full-screen media surfaces owned by the composer. They are overlays of the chat screen rather
 * than app routes, so opening the camera never loses the draft, reply, topic or send options.
 */
@Composable
internal fun AttachmentMediaHost(state: AttachmentState) {
    if (state.cameraOpen) {
        CameraCaptureScreen(
            onClose = state::closeCamera,
            onPhoto = state::acceptCapture,
            onVideo = { file, _ -> state.acceptCapture(file) }
        )
    }
}
