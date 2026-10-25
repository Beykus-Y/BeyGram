package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSpecialTest {
    private fun option(
        text: String,
        votes: Int = 0,
        percentage: Int = 0,
        chosen: Boolean = false
    ) = MessagePollOption(text, text, voterCount = votes, percentage = percentage, isChosen = chosen)

    @Test
    fun `percentage falls back to vote counts when the server omits it`() {
        val poll = MessagePoll(
            question = "Q",
            options = listOf(option("a", votes = 1), option("b", votes = 3)),
            totalVoterCount = 4
        )

        assertEquals(25, poll.percentageOf(0))
        assertEquals(75, poll.percentageOf(1))
        assertEquals(0, poll.percentageOf(2))
    }

    @Test
    fun `zero votes never divide by zero`() {
        val poll = MessagePoll(question = "Q", options = listOf(option("a"), option("b")), totalVoterCount = 0)

        assertEquals(0, poll.percentageOf(0))
        assertEquals(0, poll.percentageOf(1))
    }

    @Test
    fun `quiz can be answered once and reveals correctness after a vote`() {
        val unanswered = MessagePoll(
            question = "Q",
            options = listOf(option("a"), option("b")),
            isQuiz = true,
            canSeeResults = false,
            correctOptionIndices = setOf(1)
        )
        val answered = unanswered.copy(options = listOf(option("a"), option("b", chosen = true)))

        assertTrue(unanswered.canVote)
        assertFalse(answered.canVote)
        assertFalse(unanswered.revealsResults)
        assertTrue(answered.revealsResults)
        assertTrue(answered.isCorrect(1))
        assertFalse(answered.isCorrect(0))
        assertEquals(listOf(1), answered.chosenOptionIndices)
    }

    @Test
    fun `regular poll revoting depends on the server flag`() {
        val voted = MessagePoll(
            question = "Q",
            options = listOf(option("a", chosen = true), option("b")),
            allowsRevoting = false
        )
        assertFalse(voted.canVote)
        assertTrue(voted.copy(allowsRevoting = true).canVote)
    }

    @Test
    fun `poll draft validation covers question options and quiz answers`() {
        assertNull(OutgoingPoll("Q", listOf("a", "b")).problem())
        assertEquals(PollDraftProblem.QuestionEmpty, OutgoingPoll("  ", listOf("a", "b")).problem())
        assertEquals(PollDraftProblem.TooFewOptions, OutgoingPoll("Q", listOf("a")).problem())
        assertEquals(PollDraftProblem.TooManyOptions, OutgoingPoll("Q", (1..11).map { it.toString() }).problem())
        assertEquals(PollDraftProblem.OptionEmpty, OutgoingPoll("Q", listOf("a", " ")).problem())
        assertEquals(PollDraftProblem.CorrectAnswerMissing, OutgoingPoll("Q", listOf("a", "b"), isQuiz = true).problem())
        assertEquals(
            PollDraftProblem.CorrectAnswerCount,
            OutgoingPoll("Q", listOf("a", "b"), isQuiz = true, correctOptionIndices = setOf(0, 1)).problem()
        )
        assertNull(
            OutgoingPoll(
                "Q", listOf("a", "b"), isQuiz = true, allowsMultipleAnswers = true, correctOptionIndices = setOf(0, 1)
            ).problem()
        )
    }

    @Test
    fun `poll draft trims input and caps question length`() {
        val trimmed = OutgoingPoll("  Q  ", listOf(" a ", " b ")).normalized()
        assertEquals("Q", trimmed.question)
        assertEquals(listOf("a", "b"), trimmed.options)
        assertEquals(
            PollDraftProblem.QuestionTooLong,
            OutgoingPoll("x".repeat(OutgoingPoll.MAX_POLL_QUESTION_LENGTH + 1), listOf("a", "b")).problem()
        )
    }

    @Test
    fun `coordinates are formatted without locale surprises`() {
        assertEquals("55.75580, 37.61730", GeoPoint(55.7558, 37.6173).formatted())
        assertTrue(GeoPoint(0.0, 0.0).isValid)
        assertFalse(GeoPoint(91.0, 0.0).isValid)
    }

    @Test
    fun `unsupported content is a value with a safe placeholder type`() {
        val unsupported: MessageSpecialContent = MessageSpecialContent.Unsupported("MessagePaidMedia")

        assertTrue(unsupported is MessageSpecialContent.Unsupported)
        assertEquals("MessagePaidMedia", (unsupported as MessageSpecialContent.Unsupported).contentName)
    }
}
