package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MessageSpecialContent
import com.beykus.beygram.domain.model.PollVoteRestriction
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialContentMappingTest {
    private val mapper = TdMediaMapper(MediaFileStore())

    private fun pollOption(id: String, text: String, votes: Int, percentage: Int, chosen: Boolean) =
        TdApi.PollOption().apply {
            this.id = id
            this.text = TdApi.FormattedText(text, emptyArray())
            voterCount = votes
            votePercentage = percentage
            isChosen = chosen
            isBeingChosen = false
            recentVoterIds = emptyArray()
            author = null
            additionDate = 0
        }

    @Test
    fun `poll maps quiz options votes and restriction`() {
        val quiz = TdApi.PollTypeQuiz().apply {
            correctOptionIds = intArrayOf(1)
            explanation = TdApi.FormattedText("Because", emptyArray())
            explanationMedia = null
        }
        val poll = TdApi.Poll().apply {
            id = 42L
            question = TdApi.FormattedText("Pick one", emptyArray())
            options = arrayOf(
                pollOption("a", "First", 1, 25, false),
                pollOption("b", "Second", 3, 75, true)
            )
            totalVoterCount = 4
            recentVoterIds = emptyArray()
            canGetVoters = true
            canSeeResults = true
            isAnonymous = false
            allowsMultipleAnswers = false
            allowsRevoting = false
            membersOnly = true
            countryCodes = emptyArray()
            optionOrder = intArrayOf(0, 1)
            type = quiz
            openPeriod = 0
            closeDate = 0
            isClosed = false
            voteRestrictionReason = TdApi.PollVoteRestrictionReasonMembershipRequired()
        }
        val content = TdApi.MessagePoll().apply {
            this.poll = poll
            description = TdApi.FormattedText("Details", emptyArray())
            media = null
            canAddOption = false
        }

        val special = mapper.special(content) as MessageSpecialContent.Poll
        assertEquals("Pick one", special.poll.question)
        assertEquals("Details", special.poll.description)
        assertEquals(2, special.poll.options.size)
        assertEquals("Second", special.poll.options[1].text)
        assertEquals(75, special.poll.percentageOf(1))
        assertTrue(special.poll.hasVoted)
        assertTrue(special.poll.isQuiz)
        assertTrue(special.poll.isCorrect(1))
        assertEquals(PollVoteRestriction.MembershipRequired, special.poll.voteRestriction)
        assertEquals(42L, special.poll.id)
    }

    @Test
    fun `live location keeps its expiry and coordinates`() {
        val content = TdApi.MessageLiveLocation().apply {
            location = TdApi.LiveLocation().apply {
                location = TdApi.Location(55.75, 37.61, 12.0)
                livePeriod = 900
                heading = 0
                proximityAlertRadius = 0
            }
            expiresIn = 120
        }

        val special = mapper.special(content) as MessageSpecialContent.LiveLocation
        assertEquals(55.75, special.location.point.latitude, 0.0001)
        assertEquals(37.61, special.location.point.longitude, 0.0001)
        assertEquals(12.0, special.location.point.accuracyMeters, 0.0001)
        assertEquals(900, special.location.livePeriodSeconds)
        assertTrue(special.location.expiresAtSeconds > System.currentTimeMillis() / 1000L)
    }

    @Test
    fun `contact and dice map without text fallbacks`() {
        val contact = TdApi.MessageContact().apply {
            this.contact = TdApi.Contact("+15551234567", "Alice", "Smith", "vcard", 7L)
        }
        val contactSpecial = mapper.special(contact) as MessageSpecialContent.Contact
        assertEquals(7L, contactSpecial.contact.userId)
        assertEquals("Alice Smith", contactSpecial.contact.displayName)
        assertEquals("+15551234567", contactSpecial.contact.phone)

        val dice = TdApi.MessageDice().apply {
            emoji = "🎲"
            value = 4
            initialState = null
            finalState = null
            successAnimationFrameNumber = 0
        }
        val diceSpecial = mapper.special(dice) as MessageSpecialContent.Dice
        assertEquals("🎲", diceSpecial.dice.emoji)
        assertEquals(4, diceSpecial.dice.value)
    }

    @Test
    fun `all other content gets the explicit unsupported fallback`() {
        val unsupported = mapper.special(TdApi.MessageUnsupported()) as MessageSpecialContent.Unsupported
        assertEquals("MessageUnsupported", unsupported.contentName)
        assertTrue(unsupported is MessageSpecialContent.Unsupported)
    }

    @Test
    fun `plain text and known media keep their own renderers`() {
        assertNull(mapper.special(TdApi.MessageText().apply { text = TdApi.FormattedText("hi", emptyArray()); linkPreview = null; linkPreviewOptions = null }))
        assertNull(mapper.special(TdApi.MessageSticker().apply { sticker = TdApi.Sticker() }))
    }
}
