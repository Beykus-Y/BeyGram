package com.beykus.beygram.domain.model

/**
 * Delivery options for a regular outgoing message. TDLib is the only scheduled-message store:
 * [scheduledAtSeconds] is sent with the message and the server keeps it after a restart.
 */
data class MessageSendOptions(
    val disableNotification: Boolean = false,
    /** Unix timestamp in seconds, or null to send immediately. */
    val scheduledAtSeconds: Long? = null
) {
    /** A scheduled time must be in the future; immediate sends are always valid. */
    fun scheduledTimeIsFuture(nowSeconds: Long = System.currentTimeMillis() / 1000): Boolean =
        scheduledAtSeconds == null || scheduledAtSeconds > nowSeconds
}
