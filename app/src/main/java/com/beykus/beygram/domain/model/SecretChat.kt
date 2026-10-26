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
    val layer: Int,
    /** TDLib's SecretChat.keyHash, kept as bytes with value equality so StateFlow updates stay comparable. */
    val keyHash: List<Byte> = emptyList()
) {
    val isReady: Boolean get() = state == SecretChatState.Ready
    val hasSelfDestruct: Boolean get() = ttlSeconds > 0
    val keyFingerprint: String get() = SecretChatKey.fingerprint(keyHash)
}

/**
 * The three views of TDLib's key hash that official Telegram clients show on the "Encryption key" screen. Both sides
 * see the same picture, hex and emoji only when nobody sits in the middle, so every mapping here follows Telegram for
 * Android byte for byte (IdenticonActivity, IdenticonDrawable) and TDLib's SecretChat.keyHash documentation.
 */
object SecretChatKey {
    private const val HEX = "0123456789ABCDEF"
    /** TDLib's keyHash is 16 bytes of SHA-1 followed by 20 bytes of SHA-256 for layer 46+ chats. */
    private const val FULL_HASH_SIZE = 36
    private const val HEX_BYTES = 32
    private const val EMOJI_OFFSET = 16
    const val EMOJI_COUNT = 5

    /** Pixel colors of the key picture, indexed by [identicon] values. */
    val identiconColors: List<Long> = listOf(0xFFFFFFFF, 0xFFD5E6F3, 0xFF2D5775, 0xFF2F99C9)

    /** The first 32 bytes as hex, as official clients print them; shorter legacy hashes are shown whole. */
    fun fingerprint(keyHash: List<Byte>): String = keyHash.take(HEX_BYTES).chunked(4).joinToString(" ") { group ->
        buildString(group.size * 2) {
            group.forEach { byte ->
                val value = byte.toInt() and 0xFF
                append(HEX[value ushr 4])
                append(HEX[value and 0x0F])
            }
        }
    }

    /**
     * Five emoji from the SHA-256 part of the hash: each 4-byte big-endian group with the sign bit dropped picks an
     * entry of the official table. Older 16-byte hashes have no SHA-256 part and therefore no emoji.
     */
    fun emoji(keyHash: List<Byte>): List<String> {
        if (keyHash.size < FULL_HASH_SIZE) return emptyList()
        val table = SecretChatKeyEmoji.table
        return List(EMOJI_COUNT) { index ->
            val at = EMOJI_OFFSET + index * 4
            val num = (keyHash[at].toInt() and 0x7F shl 24) or
                (keyHash[at + 1].toInt() and 0xFF shl 16) or
                (keyHash[at + 2].toInt() and 0xFF shl 8) or
                (keyHash[at + 3].toInt() and 0xFF)
            table[num % table.size]
        }
    }

    /** Side of the square key picture: 12 for a full hash, 8 for a legacy 16-byte one, 0 when there is nothing to draw. */
    fun identiconSide(keyHash: List<Byte>): Int = when {
        keyHash.size >= FULL_HASH_SIZE -> 12
        keyHash.size >= 16 -> 8
        else -> 0
    }

    /** Color indexes of the key picture, row by row from the top left; every pixel takes the next two low bits. */
    fun identicon(keyHash: List<Byte>): List<Int> {
        val side = identiconSide(keyHash)
        return List(side * side) { pixel ->
            val bit = pixel * 2
            keyHash[bit / 8].toInt() shr (bit % 8) and 0x3
        }
    }
}

/** A closed secret chat stays readable, but TDLib cannot deliver new messages in it. */
object SecretChatPosting {
    fun canPost(canPostInChat: Boolean, isSecretChat: Boolean, state: SecretChatState?): Boolean =
        canPostInChat && !(isSecretChat && state == SecretChatState.Closed)
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
