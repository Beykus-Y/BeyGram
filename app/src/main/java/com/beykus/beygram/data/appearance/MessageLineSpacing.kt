package com.beykus.beygram.data.appearance

/**
 * Line height of message text, as a multiple of the text size. [Normal] is the 1.375 that BeyGram has always used.
 * Choices are stored by name, so a missing or unknown stored value means [Normal].
 */
enum class MessageLineSpacing(val multiplier: Float) {
    Compact(1.2f),
    Normal(1.375f),
    Relaxed(1.6f)
}

/** Reads a stored [MessageLineSpacing] name. Missing or unknown values fall back to [MessageLineSpacing.Normal]. */
internal fun messageLineSpacingFromStored(stored: String?): MessageLineSpacing =
    MessageLineSpacing.entries.firstOrNull { it.name == stored } ?: MessageLineSpacing.Normal
