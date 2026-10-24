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
    companion object {
        /**
         * TDLib turns a scheduled date at or before now+10 seconds into an immediate send. Require a strictly larger
         * lead so a minute-resolution picker cannot silently deliver the message now.
         */
        const val MIN_SCHEDULE_LEAD_SECONDS = 10L
    }

    /** A scheduled time must be far enough in the future; immediate sends are always valid. */
    fun scheduledTimeIsFuture(nowSeconds: Long = System.currentTimeMillis() / 1000): Boolean =
        scheduledAtSeconds == null || scheduledAtSeconds > nowSeconds + MIN_SCHEDULE_LEAD_SECONDS
}
