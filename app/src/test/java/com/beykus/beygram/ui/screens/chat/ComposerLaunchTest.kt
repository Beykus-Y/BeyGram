package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.LiveMessageArrival
import com.beykus.beygram.domain.model.MessageIdReplacement
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.TextStyleKind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerLaunchTest {
    private fun message(
        id: Long,
        text: String = "Hello",
        outgoing: Boolean = true,
        replyId: Long? = null,
        state: MessageSendState = MessageSendState.Sending
    ) = ChatMessage(
        id = id,
        chatId = 1L,
        text = text,
        time = "12:00",
        isOutgoing = outgoing,
        sendState = state,
        reply = replyId?.let { com.beykus.beygram.domain.model.MessageReply(1L, it, com.beykus.beygram.domain.UiText.Raw("quoted")) }
    )

    private fun history(
        messages: List<ChatMessage>,
        arrivals: List<LiveMessageArrival> = emptyList(),
        replacements: List<MessageIdReplacement> = emptyList()
    ) = ChatHistory(
        messages = messages,
        liveArrivals = arrivals,
        liveArrivalSequence = arrivals.lastOrNull()?.sequence ?: 0L,
        recentReplacements = replacements
    )

    private fun launch(key: Long, text: String = "OK", awaitingSend: Boolean = true) =
        ComposerLaunch(
            key = key,
            text = text,
            entities = emptyList(),
            replyToMessageId = null,
            sourceBounds = Rect(10f, 20f, 110f, 60f),
            baselineSequence = 0L,
            speed = 1f
        ).also { it.awaitingSend = awaitingSend }

    @Test fun replacementChainsAreFollowedSafely() {
        val replacements = listOf(
            MessageIdReplacement(10L, 50L),
            MessageIdReplacement(50L, 500L)
        )
        assertEquals(500L, resolveMessageIdThroughReplacements(10L, replacements))
        assertEquals(500L, resolveMessageIdThroughReplacements(50L, replacements))
        assertEquals(7L, resolveMessageIdThroughReplacements(7L, replacements))
    }

    @Test fun aCycleCannotLoopForever() {
        val replacements = listOf(
            MessageIdReplacement(10L, 20L),
            MessageIdReplacement(20L, 10L)
        )
        assertEquals(10L, resolveMessageIdThroughReplacements(10L, replacements))
    }

    @Test fun onlyAnOutgoingArrivalAfterTheLaunchMatches() {
        val old = message(1L)
        val incoming = message(2L, outgoing = false)
        val matching = message(3L)
        val result = findComposerLaunchMessage(
            history(listOf(old, incoming, matching), listOf(LiveMessageArrival(1, 2), LiveMessageArrival(2, 3))),
            text = "Hello",
            replyToMessageId = null,
            baselineSequence = 1,
            assignedIds = emptySet()
        )
        assertEquals(3L, result?.id)
    }

    @Test fun textReplyAndAlreadyAssignedMessagesAreNotStolen() {
        val withoutReply = message(2L)
        val otherText = message(3L, text = "Other")
        val assigned = message(4L)
        val result = findComposerLaunchMessage(
            history(
                listOf(withoutReply, otherText, assigned),
                listOf(LiveMessageArrival(1, 2), LiveMessageArrival(2, 3), LiveMessageArrival(3, 4))
            ),
            text = "Hello",
            replyToMessageId = 9L,
            baselineSequence = 0,
            assignedIds = setOf(4L)
        )
        assertNull(result)
    }

    @Test fun aConfirmedSendIsFoundThroughItsReplacement() {
        val server = message(500L, state = MessageSendState.Sent)
        val result = findComposerLaunchMessage(
            history(
                listOf(server),
                listOf(LiveMessageArrival(1, 10)),
                listOf(MessageIdReplacement(10L, 500L))
            ),
            text = "Hello",
            replyToMessageId = null,
            baselineSequence = 0,
            assignedIds = emptySet()
        )
        assertEquals(500L, result?.id)
    }

    @Test fun geometryUsesMeasuredSourceTargetAndBubbleBounds() {
        val geometry = composerLaunchGeometry(
            source = Rect(100f, 100f, 300f, 140f),
            target = Rect(200f, 300f, 400f, 360f),
            bubbleSize = IntSize(160, 50)
        )!!
        assertEquals(200f, geometry.sourceCenter.x, 0f)
        assertEquals(120f, geometry.sourceCenter.y, 0f)
        assertEquals(300f, geometry.targetCenter.x, 0f)
        assertEquals(330f, geometry.targetCenter.y, 0f)
        assertEquals(0.7f, geometry.startScale, 0f)
        assertEquals(1.25f, geometry.finalScaleX, 0f)
        assertEquals(1.2f, geometry.finalScaleY, 0f)
        assertEquals(220f, geometry.baseLeft, 0f)
        assertEquals(305f, geometry.baseTop, 0f)
    }

    @Test fun geometryWaitsForAMeasuredBubble() {
        assertNull(composerLaunchGeometry(Rect.Zero, Rect(0f, 0f, 10f, 10f), IntSize.Zero))
    }

    @Test fun identicalQuickSendsAreAssignedInArrivalOrder() {
        val first = message(10L)
        val second = message(11L)
        val snapshot = history(
            listOf(first, second),
            listOf(LiveMessageArrival(1, 10), LiveMessageArrival(2, 11))
        )
        val firstMatch = findComposerLaunchMessage(snapshot, "Hello", null, 0L, emptySet())
        val secondMatch = findComposerLaunchMessage(snapshot, "Hello", null, 0L, setOf(10L))
        assertEquals(10L, firstMatch?.id)
        assertEquals(11L, secondMatch?.id)
    }

    @Test fun oversizedDraftIsMatchedByTheBoundedPayloadThatIsActuallySent() {
        val draft = "abcdefghijklmnopqrst"
        val entities = listOf(MessageTextEntity(8, 5, TextStyleKind.Bold))
        val bounded = composerLaunchPayload(draft, entities, 10)
        assertTrue(bounded.truncated)
        assertEquals("abcdefghij", bounded.text)
        assertEquals(2, bounded.entities.single().length)
        assertTrue(bounded.entities.all { it.offset + it.length <= bounded.text.length })

        val sent = message(2L, text = bounded.text)
        val history = history(listOf(sent), listOf(LiveMessageArrival(1, 2)))
        assertNotNull(findComposerLaunchMessage(history, bounded.text, null, 0L, emptySet()))
        assertNull(findComposerLaunchMessage(history, draft, null, 0L, emptySet()))
    }

    @Test fun aNewIdenticalDraftIsNotHiddenByAnAlreadySentLaunch() {
        val finished = launch(key = 1L, text = "OK", awaitingSend = false)
        // The user types "OK" again after the previous send completed, before its launch receives a message id.
        assertFalse(shouldHideComposerDraft(editing = false, draft = "OK", activeLaunchKey = 1L, launch = finished))
        assertFalse(shouldHideComposerDraft(editing = false, draft = "OK", activeLaunchKey = null, launch = finished))
    }

    @Test fun hidingIsBoundToTheCurrentSendAttempt() {
        val first = launch(key = 1L, text = "OK", awaitingSend = false)
        val second = launch(key = 2L, text = "OK", awaitingSend = true)
        assertFalse(shouldHideComposerDraft(false, "OK", activeLaunchKey = 2L, launch = first))
        assertTrue(shouldHideComposerDraft(false, "OK", activeLaunchKey = 2L, launch = second))
        assertFalse(shouldHideComposerDraft(true, "OK", activeLaunchKey = 2L, launch = second))
        assertFalse(shouldHideComposerDraft(false, "later", activeLaunchKey = 2L, launch = second))
    }
}
