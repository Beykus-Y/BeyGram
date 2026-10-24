package com.beykus.beygram.domain.model

/**
 * Pure rules for translation and speech recognition. The UI uses these rules to hide actions; the repository repeats
 * the secret-chat check before calling TDLib so a future caller cannot bypass it.
 */
object TranslationPolicy {
    /**
     * Only text messages and media captions can be translated, and a secret chat is never allowed: TDLib would need to
     * send the message text to Telegram's translation service.
     */
    fun canTranslate(isSecretChat: Boolean, hasText: Boolean): Boolean = !isSecretChat && hasText

    /** Secret chats never send voice content to the server-side recognition service. */
    fun canTranscribe(isSecretChat: Boolean, media: MessageMedia?): Boolean =
        !isSecretChat && media != null &&
            (media.kind == MediaKind.Voice || media.kind == MediaKind.VideoNote) &&
            !media.hasTranscription && !media.isTranscribing
}
