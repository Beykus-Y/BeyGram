package com.beykus.beygram.domain.model

import java.util.Locale

/** Coordinates from a Telegram location message. [accuracyMeters] is zero when the sender did not report it. */
data class GeoPoint(val latitude: Double, val longitude: Double, val accuracyMeters: Double = 0.0) {
    val isValid: Boolean get() = latitude in -90.0..90.0 && longitude in -180.0..180.0

    /** A stable, machine-readable form used for maps and for the coordinates line under the card. */
    fun formatted(): String = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
}

data class MessagePollOption(
    val id: String,
    val text: String,
    val voterCount: Int = 0,
    /** Server-provided 0..100. [MessagePoll.percentageOf] covers older servers that only send vote counts. */
    val percentage: Int = 0,
    val isChosen: Boolean = false,
    val isBeingChosen: Boolean = false,
    /** 0-based position in TDLib's original options array; vote requests and quiz answers use it. */
    val optionIndex: Int = 0
)

/** Why TDLib says this account cannot vote right now. [None] means voting is allowed by the server. */
enum class PollVoteRestriction { None, Closed, CountryRestricted, MembershipRequired, Other, Scheduled, YetUnsent }

data class MessagePoll(
    val id: Long = 0L,
    val question: String,
    val description: String = "",
    val options: List<MessagePollOption> = emptyList(),
    val totalVoterCount: Int = 0,
    val isAnonymous: Boolean = true,
    val allowsMultipleAnswers: Boolean = false,
    val allowsRevoting: Boolean = false,
    val isClosed: Boolean = false,
    val canSeeResults: Boolean = true,
    val canGetVoters: Boolean = false,
    val isQuiz: Boolean = false,
    /** 0-based option indices of the correct quiz answers; empty for regular polls. */
    val correctOptionIndices: Set<Int> = emptySet(),
    val explanation: String = "",
    val voteRestriction: PollVoteRestriction = PollVoteRestriction.None
) {
    val chosenOptionIndices: List<Int>
        get() = options.filter { it.isChosen }.map { it.optionIndex }

    val hasVoted: Boolean get() = options.any { it.isChosen }

    /** A quiz can be answered once; a regular poll can be voted again only when the server allows revoting. */
    val canVote: Boolean
        get() = !isClosed && voteRestriction == PollVoteRestriction.None &&
            !(isQuiz && hasVoted) && !(hasVoted && !allowsRevoting)

    /**
     * TDLib is the authority on result visibility. Some polls hide results until they close, so voting must not
     * reveal counts that the server zeroes while they are hidden.
     */
    val revealsResults: Boolean get() = canSeeResults

    fun isCorrect(index: Int): Boolean = isQuiz && index in correctOptionIndices

    /**
     * TDLib normally sends the percentage itself. Older servers send only vote counts, so fall back to
     * [option.voterCount] over the number of counted votes without dividing by zero.
     */
    fun percentageOf(index: Int): Int {
        val option = options.getOrNull(index) ?: return 0
        if (option.percentage in 0..100 && option.percentage > 0) return option.percentage
        val counted = options.sumOf { it.voterCount.coerceAtLeast(0) }.takeIf { it > 0 } ?: totalVoterCount.coerceAtLeast(0)
        if (counted <= 0) return 0
        return (option.voterCount.coerceAtLeast(0) * 100.0 / counted).toInt().coerceIn(0, 100)
    }
}

data class MessageLocationInfo(val point: GeoPoint)

data class MessageLiveLocationInfo(
    val point: GeoPoint,
    val livePeriodSeconds: Int = 0,
    /** Wall-clock end of the sharing window, so the card can count down without another server update. */
    val expiresAtSeconds: Long = 0L
)

data class MessageVenueInfo(
    val point: GeoPoint,
    val title: String,
    val address: String = "",
    val provider: String = "",
    val type: String = ""
)

data class MessageContactInfo(
    val userId: Long = 0L,
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = ""
) {
    val displayName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { phone }
}

data class MessageDiceInfo(val emoji: String, val value: Int = 0)

/** Everything that is not a photo/video/document/audio/sticker/voice note or plain text. */
sealed interface MessageSpecialContent {
    data class Poll(val poll: MessagePoll) : MessageSpecialContent
    data class Location(val location: MessageLocationInfo) : MessageSpecialContent
    data class LiveLocation(val location: MessageLiveLocationInfo) : MessageSpecialContent
    data class Venue(val venue: MessageVenueInfo) : MessageSpecialContent
    data class Contact(val contact: MessageContactInfo) : MessageSpecialContent
    data class Dice(val dice: MessageDiceInfo) : MessageSpecialContent

    /**
     * A safe placeholder for paid, future or otherwise unsupported content. It is never rendered as an empty bubble.
     * [contentName] is TDLib's class name and is only used for diagnostics, never shown to the user.
     */
    data class Unsupported(val contentName: String = "") : MessageSpecialContent
}

/**
 * A poll composed in the chat before it is handed to TDLib. [correctOptionIndices] are positions in [options].
 * Validation lives here so both the composer and the repository can explain the same problem.
 */
data class OutgoingPoll(
    val question: String,
    val options: List<String>,
    val isAnonymous: Boolean = true,
    val allowsMultipleAnswers: Boolean = false,
    val isQuiz: Boolean = false,
    val correctOptionIndices: Set<Int> = emptySet(),
    val explanation: String = ""
) {
    fun normalized(): OutgoingPoll = copy(
        question = question.trim(),
        options = options.map { it.trim() },
        explanation = explanation.trim()
    )

    fun problem(): PollDraftProblem? {
        val value = normalized()
        return when {
            value.question.isEmpty() -> PollDraftProblem.QuestionEmpty
            value.question.codePointCount(0, value.question.length) > MAX_POLL_QUESTION_LENGTH -> PollDraftProblem.QuestionTooLong
            value.options.size < MIN_POLL_OPTIONS -> PollDraftProblem.TooFewOptions
            value.options.size > MAX_POLL_OPTIONS -> PollDraftProblem.TooManyOptions
            value.options.any { it.isEmpty() } -> PollDraftProblem.OptionEmpty
            value.options.any { it.codePointCount(0, it.length) > MAX_POLL_OPTION_LENGTH } -> PollDraftProblem.OptionTooLong
            value.isQuiz && value.correctOptionIndices.isEmpty() -> PollDraftProblem.CorrectAnswerMissing
            value.isQuiz && value.correctOptionIndices.any { it !in value.options.indices } -> PollDraftProblem.CorrectAnswerInvalid
            value.isQuiz && !value.allowsMultipleAnswers && value.correctOptionIndices.size != 1 -> PollDraftProblem.CorrectAnswerCount
            else -> null
        }
    }

    companion object {
        const val MIN_POLL_OPTIONS = 2
        const val MAX_POLL_OPTIONS = 10
        const val MAX_POLL_QUESTION_LENGTH = 255
        const val MAX_POLL_OPTION_LENGTH = 100
    }
}

enum class PollDraftProblem {
    QuestionEmpty,
    QuestionTooLong,
    TooFewOptions,
    TooManyOptions,
    OptionEmpty,
    OptionTooLong,
    CorrectAnswerMissing,
    CorrectAnswerInvalid,
    CorrectAnswerCount
}

/** A prepared poll/location/contact/dice message waiting in the composer for the normal Send button. */
sealed interface MessageExtraDraft {
    data class Poll(val poll: OutgoingPoll) : MessageExtraDraft
    data class Location(val point: GeoPoint) : MessageExtraDraft
    data class Contact(val userId: Long, val title: String) : MessageExtraDraft
    data class Dice(val emoji: String) : MessageExtraDraft
}
