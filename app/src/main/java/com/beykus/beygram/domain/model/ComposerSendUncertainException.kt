package com.beykus.beygram.domain.model

/**
 * A composer media send was handed to Telegram, but the local wait ended before a confirmation arrived. The message may
 * already be on the server, so the caller must not resend the same items automatically.
 */
class ComposerSendUncertainException(cause: Throwable?) : Exception(cause)
