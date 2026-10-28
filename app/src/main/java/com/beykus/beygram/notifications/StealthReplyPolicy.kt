package com.beykus.beygram.notifications

/**
 * Whether a notification reply may reach Telegram. A send shows the account online for a while when online status is hidden,
 * so only the attempt the user made directly from the notification may send. A reply that the runtime sends again later on its
 * own (start, reconnect, retry timer, secondary attach) is held instead.
 */
internal fun stealthAllowsReplyAttempt(hideOnline: Boolean, firstDirectAttempt: Boolean): Boolean =
    !hideOnline || firstDirectAttempt

/** Long enough for the direct attempt, which may wait for authorization first, to claim a reply it has just stored. */
internal const val STEALTH_DIRECT_REPLY_WINDOW_MILLIS = 45_000L

/**
 * An automatic flush leaves a reply alone while it is this fresh, because the direct attempt for it may not have started
 * yet. Holding it now would take away the one send the user allowed. A later flush holds it if it is still there.
 */
internal fun stealthReplyLeftForDirectAttempt(hideOnline: Boolean, firstDirectAttempt: Boolean, ageMillis: Long): Boolean =
    hideOnline && !firstDirectAttempt && ageMillis in 0 until STEALTH_DIRECT_REPLY_WINDOW_MILLIS
