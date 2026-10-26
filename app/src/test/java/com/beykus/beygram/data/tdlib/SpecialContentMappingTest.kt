package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageDiceState
import com.beykus.beygram.domain.model.MessageSpecialContent
import com.beykus.beygram.domain.model.PollVoteRestriction
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            canAddOption = true
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
        assertTrue(special.poll.canAddOption)
    }

    @Test
    fun `shuffled poll order keeps the original option ids for voting`() {
        val poll = TdApi.Poll().apply {
            id = 99L
            question = TdApi.FormattedText("Shuffled", emptyArray())
            options = arrayOf(
                pollOption("a", "First", 0, 0, false),
                pollOption("b", "Second", 0, 0, false)
            )
            totalVoterCount = 0
            recentVoterIds = emptyArray()
            canGetVoters = false
            canSeeResults = true
            isAnonymous = true
            allowsMultipleAnswers = false
            allowsRevoting = true
            membersOnly = false
            countryCodes = emptyArray()
            optionOrder = intArrayOf(1, 0)
            type = TdApi.PollTypeRegular()
            openPeriod = 0
            closeDate = 0
            isClosed = false
            voteRestrictionReason = null
        }
        val special = mapper.special(TdApi.MessagePoll().apply { this.poll = poll }) as MessageSpecialContent.Poll

        assertEquals("Second", special.poll.options[0].text)
        assertEquals(1, special.poll.options[0].optionIndex)
        assertEquals("First", special.poll.options[1].text)
        assertEquals(0, special.poll.options[1].optionIndex)
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

    private fun diceSticker(id: Int, format: TdApi.StickerFormat = TdApi.StickerFormatTgs()) =
        TdApi.Sticker().apply {
            sticker = TdApi.File().apply {
                this.id = id
                size = 1024
                expectedSize = 1024
            }
            this.format = format
            width = 512
            height = 512
            thumbnail = null
        }

    private fun slotMachine(firstId: Int) = TdApi.DiceStickersSlotMachine(
        diceSticker(firstId),
        diceSticker(firstId + 1),
        diceSticker(firstId + 2),
        diceSticker(firstId + 3),
        diceSticker(firstId + 4)
    )

    @Test
    fun `dice maps the initial and final TDLib assets instead of a local random`() {
        val initial = TdApi.DiceStickersRegular(diceSticker(11))
        val final = TdApi.DiceStickersRegular(diceSticker(12))
        val content = TdApi.MessageDice(initial, final, "🎲", 4, 0)

        val dice = (mapper.special(content) as MessageSpecialContent.Dice).dice

        assertEquals("🎲", dice.emoji)
        assertEquals(4, dice.value)
        assertEquals(11, (dice.initialState as MessageDiceState.Regular).sticker.fileId)
        assertEquals(12, (dice.displayState as MessageDiceState.Regular).sticker.fileId)
        assertEquals("application/x-tgsticker", (dice.displayState as MessageDiceState.Regular).sticker.mimeType)
        assertEquals(1024L, mapper.files.snapshot(11)?.size)
        assertFalse(dice.isSlotMachine)
    }

    @Test
    fun `dice final state without assets never falls back to the initial throw`() {
        val initial = TdApi.DiceStickersRegular(diceSticker(21, TdApi.StickerFormatWebp()))
        val content = TdApi.MessageDice().apply {
            initialState = initial
            finalState = null
            emoji = "🎯"
            value = 6
            successAnimationFrameNumber = 0
        }
        val dice = (mapper.special(content) as MessageSpecialContent.Dice).dice

        assertNull(dice.displayState)
        assertEquals("image/webp", (dice.initialState as MessageDiceState.Regular).sticker.mimeType)
        assertEquals(6, dice.value)
    }

    @Test
    fun `slot machine maps all five stickers from both states`() {
        val content = TdApi.MessageDice(slotMachine(100), slotMachine(200), "🎰", 64, 0)

        val dice = (mapper.special(content) as MessageSpecialContent.Dice).dice
        val finalState = dice.displayState as MessageDiceState.SlotMachine

        assertTrue(dice.isSlotMachine)
        assertTrue(dice.isJackpot)
        assertEquals(listOf(200, 201, 202, 203, 204), finalState.layers.map { it.fileId })
        assertEquals(100, (dice.initialState as MessageDiceState.SlotMachine).background.fileId)
    }

    @Test
    fun `blank dice emoji has a visible fallback`() {
        val content = TdApi.MessageDice().apply {
            initialState = null
            finalState = null
            emoji = ""
            value = 0
            successAnimationFrameNumber = 0
        }
        val dice = (mapper.special(content) as MessageSpecialContent.Dice).dice
        assertEquals("🎲", dice.fallbackEmoji)
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
    fun `expired self destructing attachments keep their own placeholder instead of unsupported`() {
        val expired = listOf(
            TdApi.MessageExpiredPhoto() to MediaKind.Photo,
            TdApi.MessageExpiredVideo() to MediaKind.Video,
            TdApi.MessageExpiredVideoNote() to MediaKind.VideoNote,
            TdApi.MessageExpiredVoiceNote() to MediaKind.Voice
        )
        expired.forEach { (content, kind) ->
            assertNull("${content.javaClass.simpleName} must not become media", mapper.media(content))
            assertNull("${content.javaClass.simpleName} must not become unsupported", mapper.special(content))
            assertEquals(kind, expiredMediaKindOf(content))
        }
    }

    @Test
    fun `plain text and known media keep their own renderers`() {
        assertNull(mapper.special(TdApi.MessageText().apply { text = TdApi.FormattedText("hi", emptyArray()); linkPreview = null; linkPreviewOptions = null }))
        assertNull(mapper.special(TdApi.MessageSticker().apply { sticker = TdApi.Sticker() }))
    }
}
