package com.beykus.beygram.notifications

/**
 * Whether a notification reply may reach Telegram. A send shows the account online for a while when online status is hidden,
 * so only the attempt the user made directly from the notification may send. A reply that the runtime sends again later on its
 * own (start, reconnect, retry timer, secondary attach) is held instead.
 */
internal fun stealthAllowsReplyAttempt(hideOnline: Boolean, firstDirectAttempt: Boolean): Boolean =
    !hideOnline || firstDirectAttempt
