package com.beykus.beygram.security

/**
 * Decides what a notification may reveal. Secret chats hide their title and sender entirely;
 * chats with protected content keep their title but never show message text or the sender.
 * The user's existing "previews" switch and Telegram's per-message permission are honoured.
 */
internal object NotificationPreviewPolicy {
    fun hideTitle(isSecret: Boolean, metadataReady: Boolean): Boolean = isSecret || !metadataReady

    fun hideText(
        previews: Boolean,
        previewAllowed: Boolean,
        isSecret: Boolean,
        metadataReady: Boolean,
        hasProtectedContent: Boolean,
    ): Boolean = !previews || !previewAllowed || hideTitle(isSecret, metadataReady) || hasProtectedContent

    fun hideSender(
        isSecret: Boolean,
        metadataReady: Boolean,
        hasProtectedContent: Boolean,
    ): Boolean = hideTitle(isSecret, metadataReady) || hasProtectedContent
}
