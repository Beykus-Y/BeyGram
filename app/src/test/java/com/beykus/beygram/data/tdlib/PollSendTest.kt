package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.OutgoingPoll
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PollSendTest {
    @Test
    fun `regular poll maps options anonymity and multiple answers`() {
        val content = inputMessagePoll(
            OutgoingPoll(
                question = "  Choose a day  ",
                options = listOf(" Monday ", "Tuesday"),
                isAnonymous = false,
                allowsMultipleAnswers = true
            )
        )

        assertEquals("Choose a day", content.question.text)
        assertEquals(2, content.options.size)
        assertEquals("Monday", content.options[0].text.text)
        assertEquals("Tuesday", content.options[1].text.text)
        assertNull(content.options[0].media)
        assertFalse(content.isAnonymous)
        assertTrue(content.allowsMultipleAnswers)
        assertTrue(content.allowsRevoting)
        assertFalse(content.membersOnly)
        assertFalse(content.shuffleOptions)
        assertEquals(0, content.openPeriod)
        assertEquals(0, content.closeDate)
        assertFalse(content.isClosed)
        val type = content.type as TdApi.InputPollTypeRegular
        assertFalse(type.allowAddingOptions)
    }

    @Test
    fun `quiz forces anonymity and carries sorted correct answers and explanation`() {
        val content = inputMessagePoll(
            OutgoingPoll(
                question = "Pick two",
                options = listOf("A", "B", "C"),
                isAnonymous = false,
                allowsMultipleAnswers = true,
                isQuiz = true,
                correctOptionIndices = setOf(2, 0),
                explanation = " Because "
            )
        )

        assertTrue(content.isAnonymous)
        assertFalse(content.allowsRevoting)
        val type = content.type as TdApi.InputPollTypeQuiz
        assertArrayEquals(intArrayOf(0, 2), type.correctOptionIds)
        assertEquals("Because", type.explanation?.text)
        assertNull(type.explanationMedia)
    }

    @Test
    fun `private chat refusal explains the Telegram restriction`() {
        assertEquals(
            UiText.Res(R.string.poll_send_private_chat),
            pollErrorText(TdRequestError(400, "Polls can't be sent to the private chat"))
        )
    }

    @Test
    fun `channel direct messages refusal is not a generic failure`() {
        assertEquals(
            UiText.Res(R.string.poll_send_chat_unsupported),
            pollErrorText(TdRequestError(400, "Polls can't be sent to channel direct messages chats"))
        )
    }

    @Test
    fun `non anonymous channel poll explains the anonymity requirement`() {
        assertEquals(
            UiText.Res(R.string.poll_send_channel_anonymous),
            pollErrorText(TdRequestError(400, "Non-anonymous polls can't be sent to channel chats"))
        )
    }

    @Test
    fun `missing poll permission gets a rights explanation`() {
        assertEquals(
            UiText.Res(R.string.poll_send_rights_missing),
            pollErrorText(TdRequestError(400, "Not enough rights to send polls to the chat"))
        )
    }

    @Test
    fun `invalid poll parameters get a parameter explanation`() {
        assertEquals(
            UiText.Res(R.string.poll_send_invalid),
            pollErrorText(TdRequestError(400, "Poll question length must not exceed 255"))
        )
        assertEquals(
            UiText.Res(R.string.poll_send_invalid),
            pollErrorText(TdRequestError(400, "Wrong quiz correct_option_id specified"))
        )
    }

    @Test
    fun `flood wait stays flood wait`() {
        assertEquals(
            UiText.Res(R.string.err_tdlib_flood_wait),
            pollErrorText(TdRequestError(429, "FLOOD_WAIT_30"))
        )
    }

    @Test
    fun `unknown refusal keeps a poll specific fallback`() {
        assertEquals(
            UiText.Res(R.string.poll_send_failed),
            pollErrorText(TdRequestError(500, "Unexpected server response"))
        )
    }

    @Test
    fun `diagnostic message keeps fixed TDLib phrases and redacts quoted user text`() {
        assertEquals(
            "Polls can't be sent to the private chat",
            redactedTdlibMessage("Polls can't be sent to the private chat")
        )
        val redacted = redactedTdlibMessage("Rejected \"My secret question\" with details")
        assertEquals("Rejected <text> with details", redacted)
        assertNull(redactedTdlibMessage("Вопрос опроса"))
    }
}
