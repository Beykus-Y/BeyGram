package com.beykus.beygram.domain.model

/** TDLib's SecretChatState, kept independent from protocol types so screens never import TdApi. */
enum class SecretChatState { Pending, Ready, Closed }

/**
 * Device-specific secret chat metadata. [ttlSeconds] is TDLib's Chat.messageAutoDeleteTime and applies to messages
 * sent after the change; already delivered messages keep the timer TDLib gave them.
 */
data class SecretChatDetails(
    val chatId: Long,
    val secretChatId: Int,
    val userId: Long,
    val state: SecretChatState,
    val isOutbound: Boolean,
    val ttlSeconds: Int,
    val layer: Int
) {
    val isReady: Boolean get() = state == SecretChatState.Ready
    val hasSelfDestruct: Boolean get() = ttlSeconds > 0
}

/**
 * Self-destruct timer bounds accepted by TDLib for secret chats. Zero disables the timer. The same timer is applied to
 * photos/videos sent with view-once as MessageSelfDestructTypeImmediately instead, so it is never mixed into chat TTL.
 */
object SecretChatTtl {
    const val OFF = 0
    const val ONE_HOUR = 60 * 60
    const val ONE_DAY = 24 * 60 * 60
    const val SEVEN_DAYS = 7 * 24 * 60 * 60
    const val MAX_SECONDS = 365 * 24 * 60 * 60

    val presets = listOf(OFF, ONE_HOUR, ONE_DAY, SEVEN_DAYS)

    /** TDLib accepts any secret-chat timer up to one year; zero means "off". */
    fun isValid(seconds: Int): Boolean = seconds == OFF || seconds in 1..MAX_SECONDS

    /**
     * Builds a custom timer from picker fields. Every field must be non-negative and within its natural range; the total
     * must be at least one minute because a shorter custom timer is not useful in practice.
     */
    fun customSeconds(days: Int, hours: Int, minutes: Int): Int? {
        if (days < 0 || hours !in 0..23 || minutes !in 0..59 || days > 365) return null
        val total = days * 86_400 + hours * 3_600 + minutes * 60
        return total.takeIf { it in 60..MAX_SECONDS }
    }
}

/** Split seconds into calendar-like units; the UI localizes the unit labels. */
data class TtlParts(val days: Int, val hours: Int, val minutes: Int, val seconds: Int) {
    val isZero: Boolean get() = days == 0 && hours == 0 && minutes == 0 && seconds == 0
}

fun ttlParts(totalSeconds: Int): TtlParts {
    val value = totalSeconds.coerceAtLeast(0)
    return TtlParts(
        days = value / 86_400,
        hours = value % 86_400 / 3_600,
        minutes = value % 3_600 / 60,
        seconds = value % 60
    )
}

/**
 * Human-readable TTL for custom values. Only the two largest non-zero units are shown ("1 d 2 h"), which keeps the chat
 * header compact while still distinguishing nearby custom timers.
 */
fun formatTtlDuration(
    totalSeconds: Int,
    dayUnit: String,
    hourUnit: String,
    minuteUnit: String,
    secondUnit: String
): String {
    val parts = ttlParts(totalSeconds)
    val values = buildList {
        if (parts.days > 0) add("${parts.days} $dayUnit")
        if (parts.hours > 0) add("${parts.hours} $hourUnit")
        if (parts.minutes > 0) add("${parts.minutes} $minuteUnit")
        if (parts.seconds > 0) add("${parts.seconds} $secondUnit")
    }
    return values.take(2).joinToString(" ")
}

/** A secret chat is E2E only with a regular, non-deleted user other than the current account. */
object SecretChatEligibility {
    fun canStart(userId: Long, isSelf: Boolean, isBot: Boolean, isDeleted: Boolean = false): Boolean =
        userId > 0L && !isSelf && !isBot && !isDeleted
}

/** TDLib's self-destruct type for an individual message, separate from the chat's auto-delete TTL. */
sealed interface MessageSelfDestruct {
    /** The content can be opened once and is destroyed as soon as it is closed. */
    data object Immediately : MessageSelfDestruct

    /** The message is destroyed [seconds] after it is sent/opened. */
    data class Timer(val seconds: Int) : MessageSelfDestruct
}

/** View-once is a regular-private-chat photo/video feature; albums, secret chats, groups and scheduled sends are not supported. */
object ViewOncePolicy {
    fun canSend(
        media: List<OutgoingMedia>,
        isPrivateChat: Boolean,
        isSecretChat: Boolean = false,
        scheduled: Boolean
    ): Boolean = isPrivateChat && !isSecretChat && !scheduled && media.size == 1 &&
        media.single().kind in setOf(MediaKind.Photo, MediaKind.Video)

    /** TDLib already refuses saving such media; the UI adds a local guard so a cached right can never export it. */
    fun canExport(canBeSaved: Boolean, isViewOnce: Boolean): Boolean = canBeSaved && !isViewOnce

    fun canForward(canBeForwarded: Boolean, isViewOnce: Boolean): Boolean = canBeForwarded && !isViewOnce
}
