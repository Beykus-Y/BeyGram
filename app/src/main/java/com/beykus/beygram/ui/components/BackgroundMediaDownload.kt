package com.beykus.beygram.ui.components

import androidx.compose.runtime.compositionLocalOf

/**
 * Downloads avatars and preview thumbnails without applying the media auto-download policy. Such
 * downloads are started by the UI, not by a Download button, so the repository counts them as
 * automatic traffic. A composition local keeps every avatar/preview caller on the same origin without
 * changing their existing callback signatures.
 */
internal val LocalBackgroundMediaDownload = compositionLocalOf<((Int) -> Unit)?> { null }
