package com.beykus.beygram.media.bgwave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BgWaveControllerTest {
    private val sid = "102132435465768798a9bacbdcedfe0f"
    private val now = 1_000L

    private class FakeSession(val outbound: ArrayDeque<String> = ArrayDeque(), var accept: Boolean = true) : BgWaveSessionPort {
        var state = "Idle"
        var closed = false
        val remote = mutableListOf<String>()
        override fun startOffer(nowSeconds: Long, ttlSeconds: Long) = true
        override fun handleRemote(uri: String, nowSeconds: Long): Boolean { remote += uri; return accept }
        override fun pollOutbound(): String? = outbound.removeFirstOrNull()
        override fun status() = BgWaveSessionStatus(state, "None", "")
        override fun stop(emitEnd: Boolean, nowSeconds: Long) {}
        override fun reconnect(nowSeconds: Long) = true
        override fun close() { closed = true }
    }

    /** Test links look like "bg://wave/v1/<type>-<sid>-<nonce>-<exp>"; the fake decoder reads them back. */
    private fun link(type: Int, nonce: Long = 1L, exp: Long = now + 300, id: String = sid) = "bg://wave/v1/$type-$id-$nonce-$exp"

    private fun decode(uri: String, at: Long): BgWaveDecodeResult {
        val parts = uri.removePrefix(BgWaveLink.URI_PREFIX).removePrefix(BgWaveLink.HTTPS_PREFIX).split('-')
        if (parts.size != 4) return BgWaveDecodeResult.Rejected("MALFORMED")
        val exp = parts[3].toLong()
        if (at >= exp) return BgWaveDecodeResult.Rejected("EXPIRED")
        return BgWaveDecodeResult.Ok(parts[0].toInt(), parts[1], 0L, exp, 3L, parts[2].toLong(), 1)
    }

    private fun controller(session: FakeSession? = FakeSession()) = BgWaveController(
        createSession = { session },
        decode = ::decode,
        encodeControl = { type, id, at, nonce -> link(type, nonce, at + 300, id) },
    )

    private fun BgWaveController.receive(
        msgLink: String, chat: Long = 7L, sender: Long = 7L, outgoing: Boolean = false, private: Boolean = true, account: String = "a", at: Long = now,
    ) = onMessage(account, chat, sender, outgoing, private, msgLink, at)

    @Test fun invitationFromThePeerOfAPrivateChatIsRemembered() {
        val c = controller()
        c.receive(link(1))
        assertEquals(BgWaveInviteStatus.Pending, c.inviteStates.value[sid]?.status)
    }

    @Test fun invitationFromGroupOrWrongSenderOrOwnMessageIsIgnored() {
        val c = controller()
        c.receive(link(1), private = false)
        c.receive(link(1), sender = 99L)
        c.receive(link(1), outgoing = true)
        assertTrue(c.inviteStates.value.isEmpty())
    }

    @Test fun expiredOrDamagedInvitationIsIgnored() {
        val c = controller()
        c.receive(link(1, exp = now - 1))
        c.receive("bg://wave/v1/garbage")
        assertTrue(c.inviteStates.value.isEmpty())
    }

    @Test fun replayedInvitationDoesNotResetItsStatus() {
        val c = controller()
        c.receive(link(1))
        c.decline(sid, now)
        c.receive(link(1))
        assertEquals(BgWaveInviteStatus.Declined, c.inviteStates.value[sid]?.status)
    }

    @Test fun joinSendsAnAnswerAndConnects() {
        val session = FakeSession(ArrayDeque(listOf(link(2))))
        val c = controller(session)
        c.receive(link(1))
        val answer = c.join(sid, now)
        assertTrue(answer!!.startsWith(BgWaveLink.HTTPS_PREFIX))
        assertEquals(listOf(link(1)), session.remote)
        assertEquals(BgWavePhase.Connecting, c.state.value?.phase)
        assertEquals(BgWaveInviteStatus.Joined, c.inviteStates.value[sid]?.status)
    }

    @Test fun joinOfAnExpiredInvitationFails() {
        val c = controller()
        c.receive(link(1))
        assertNull(c.join(sid, now + 1000))
        assertEquals(BgWaveInviteStatus.Expired, c.inviteStates.value[sid]?.status)
        assertEquals(BgWaveErrors.EXPIRED, c.state.value?.errorKey)
    }

    @Test fun joinWithoutNativeSupportReportsUnavailable() {
        val c = BgWaveController({ null }, ::decode, { t, i, a, n -> link(t, n, a + 300, i) })
        c.receive(link(1))
        assertNull(c.join(sid, now))
        assertEquals(BgWaveErrors.UNAVAILABLE, c.state.value?.errorKey)
    }

    @Test fun hostFlowOfferAnswerLive() {
        val session = FakeSession(ArrayDeque(listOf(link(1))))
        val c = controller(session)
        val offer = c.startHosting("a", 7L, 7L, now)
        assertNotNull(offer)
        assertEquals(BgWavePhase.Offering, c.state.value?.phase)
        c.offerSent(sid, true, now)
        assertEquals(BgWavePhase.AwaitingAnswer, c.state.value?.phase)
        c.receive(link(2))
        assertEquals(BgWavePhase.Connecting, c.state.value?.phase)
        session.state = "LiveP2P"
        c.poll(now)
        assertEquals(BgWavePhase.Live, c.state.value?.phase)
    }

    @Test fun answerFromAnotherChatSenderAccountOrSidIsIgnored() {
        for (case in 0..3) {
            val session = FakeSession(ArrayDeque(listOf(link(1))))
            val c = controller(session)
            c.startHosting("a", 7L, 7L, now)
            c.offerSent(sid, true, now)
            when (case) {
                0 -> c.receive(link(2), chat = 8L, sender = 8L)
                1 -> c.receive(link(2), account = "b")
                2 -> c.receive(link(2, id = "ffffffffffffffffffffffffffffffff"))
                3 -> c.receive(link(2), private = false)
            }
            assertEquals("case $case", BgWavePhase.AwaitingAnswer, c.state.value?.phase)
            assertTrue(session.remote.isEmpty())
        }
    }

    @Test fun repeatedAnswerIsAReplay() {
        val session = FakeSession(ArrayDeque(listOf(link(1))))
        val c = controller(session)
        c.startHosting("a", 7L, 7L, now)
        c.offerSent(sid, true, now)
        c.receive(link(2, nonce = 5))
        c.receive(link(2, nonce = 5))
        c.receive(link(2, nonce = 4))
        assertEquals(1, session.remote.size)
    }

    @Test fun failedOfferSendClosesTheSession() {
        val session = FakeSession(ArrayDeque(listOf(link(1))))
        val c = controller(session)
        c.startHosting("a", 7L, 7L, now)
        c.offerSent(sid, false, now)
        assertTrue(session.closed)
        assertEquals(BgWaveErrors.SEND_FAILED, c.state.value?.errorKey)
    }

    @Test fun cancelFromThePeerEndsTheWaveAndAnEndedWaveIsNeverReopened() {
        val session = FakeSession(ArrayDeque(listOf(link(1))))
        val c = controller(session)
        c.startHosting("a", 7L, 7L, now)
        c.offerSent(sid, true, now)
        c.receive(link(3))
        assertEquals(BgWavePhase.Ended, c.state.value?.phase)
        assertTrue(session.closed)
        c.receive(link(1, nonce = 9))
        assertNull(c.inviteStates.value[sid])
    }

    @Test fun cancelFromAStrangerDoesNotEndTheWave() {
        val session = FakeSession(ArrayDeque(listOf(link(1))))
        val c = controller(session)
        c.startHosting("a", 7L, 7L, now)
        c.offerSent(sid, true, now)
        c.receive(link(3), chat = 8L, sender = 8L)
        assertEquals(BgWavePhase.AwaitingAnswer, c.state.value?.phase)
    }

    @Test fun cancelOfAPendingInvitationMarksTheCard() {
        val c = controller()
        c.receive(link(1))
        c.receive(link(3))
        assertEquals(BgWaveInviteStatus.Cancelled, c.inviteStates.value[sid]?.status)
    }

    @Test fun unknownSessionSignalsAreDropped() {
        val c = controller()
        c.receive(link(2))
        c.receive(link(3))
        assertTrue(c.inviteStates.value.isEmpty())
        assertNull(c.state.value)
    }

    @Test fun stopReturnsCancelBeforeAnswerAndEndAfter() {
        val s1 = FakeSession(ArrayDeque(listOf(link(1))))
        val c1 = controller(s1)
        c1.startHosting("a", 7L, 7L, now); c1.offerSent(sid, true, now)
        assertTrue(c1.stop(now)!!.contains("3-$sid"))
        assertEquals(BgWavePhase.Ended, c1.state.value?.phase)

        val s2 = FakeSession(ArrayDeque(listOf(link(1))))
        val c2 = controller(s2)
        c2.startHosting("a", 7L, 7L, now); c2.offerSent(sid, true, now); c2.receive(link(2))
        assertTrue(c2.stop(now)!!.contains("4-$sid"))
        assertTrue(s2.closed)
    }

    @Test fun onlyOneWaveAtATime() {
        val c = controller(FakeSession(ArrayDeque(listOf(link(1)))))
        c.startHosting("a", 7L, 7L, now)
        assertNull(c.startHosting("a", 8L, 8L, now))
        assertEquals(BgWavePhase.Offering, c.state.value?.phase)
    }

    @Test fun declineSendsCancelOnce() {
        val c = controller()
        c.receive(link(1))
        assertNotNull(c.decline(sid, now))
        assertNull(c.decline(sid, now))
    }

    @Test fun inviteTableIsBounded() {
        val c = BgWaveController({ null }, ::decode, { t, i, a, n -> link(t, n, a + 300, i) }, maxInvites = 3)
        for (i in 0 until 6) c.receive(link(1, id = "%032x".format(i)))
        assertEquals(3, c.inviteStates.value.size)
        assertFalse(c.inviteStates.value.containsKey("%032x".format(0)))
    }

    @Test fun nativeStateNamesMapToPhases() {
        assertEquals(BgWavePhase.Live, BgWaveController.phaseOf("LiveP2P", BgWavePhase.Connecting))
        assertEquals(BgWavePhase.Connecting, BgWaveController.phaseOf("ConnectingP2P", BgWavePhase.Connecting))
        assertEquals(BgWavePhase.Failed, BgWaveController.phaseOf("P2PFailed", BgWavePhase.Connecting))
        assertEquals(BgWavePhase.Reconnecting, BgWaveController.phaseOf("Reconnecting", BgWavePhase.Live))
        assertEquals(BgWavePhase.Live, BgWaveController.phaseOf("Whatever", BgWavePhase.Live))
    }
}

class BgWaveCardViewTest {
    private val sid = "aa"
    private fun invite(status: BgWaveInviteStatus, exp: Long = 500L) = BgWaveInvite("a", 7L, 7L, "u", exp, status)
    private fun session(phase: BgWavePhase, err: String? = null, s: String = sid) =
        BgWaveSessionState(BgWaveRole.Listener, "a", 7L, 7L, s, phase, err)

    @Test fun pendingInviteOffersJoinAndDecline() {
        val v = bgWaveCardView(sid, 500L, false, invite(BgWaveInviteStatus.Pending), null, 100L)
        assertEquals(listOf(BgWaveCardAction.Join, BgWaveCardAction.Decline), v.actions)
    }

    @Test fun joinIsHiddenWhileAnotherWaveRuns() {
        val v = bgWaveCardView(sid, 500L, false, invite(BgWaveInviteStatus.Pending), session(BgWavePhase.Live, s = "bb"), 100L)
        assertEquals(listOf(BgWaveCardAction.Decline), v.actions)
    }

    @Test fun expiredInviteHasNoButtons() {
        val v = bgWaveCardView(sid, 500L, false, invite(BgWaveInviteStatus.Pending), null, 600L)
        assertEquals(BgWaveCardStatus.Expired, v.status)
        assertTrue(v.actions.isEmpty())
    }

    @Test fun runningSessionShowsItsPhaseAndStop() {
        val v = bgWaveCardView(sid, 500L, false, invite(BgWaveInviteStatus.Joined), session(BgWavePhase.Live), 9999L)
        assertEquals(BgWaveCardStatus.Live, v.status)
        assertEquals(listOf(BgWaveCardAction.Stop), v.actions)
    }

    @Test fun failedConnectionOffersRetry() {
        val v = bgWaveCardView(sid, 500L, false, null, session(BgWavePhase.Failed, BgWaveErrors.NO_CONNECTION), 100L)
        assertEquals(listOf(BgWaveCardAction.Retry, BgWaveCardAction.Stop), v.actions)
    }

    @Test fun ownSettledInviteIsPlainStatus() {
        assertEquals(BgWaveCardStatus.Sent, bgWaveCardView(sid, 500L, true, null, null, 100L).status)
        assertEquals(BgWaveCardStatus.Expired, bgWaveCardView(sid, 500L, true, null, null, 600L).status)
    }

    @Test fun untrackedIncomingInviteIsUnavailable() {
        assertEquals(BgWaveCardStatus.Unavailable, bgWaveCardView(sid, 500L, false, null, null, 100L).status)
    }
}

class BgWaveLinkTest {
    @Test fun wrapsUriIntoHttpsWithSamePayload() {
        assertEquals(BgWaveLink.HTTPS_PREFIX + "abc", BgWaveLink.toHttps("bg://wave/v1/abc"))
        assertEquals(BgWaveLink.HTTPS_PREFIX + "abc", BgWaveLink.toHttps(BgWaveLink.HTTPS_PREFIX + "abc"))
        assertNull(BgWaveLink.toHttps("https://evil.example/#bgwave-v1.abc"))
        assertNull(BgWaveLink.toHttps("bg://wave/v1/" + "a".repeat(BgWaveLink.MAX_LINK_CHARS)))
    }

    @Test fun findsOnlyHiddenWaveLinks() {
        val label = "Service"
        val entity = com.beykus.beygram.domain.model.MessageTextEntity(0, label.length, com.beykus.beygram.domain.model.TextStyleKind.Url, BgWaveLink.HTTPS_PREFIX + "abc")
        assertEquals(BgWaveLink.HTTPS_PREFIX + "abc", BgWaveLink.find(label, listOf(entity)))
        assertNull(BgWaveLink.find(label, emptyList()))
        assertNull(BgWaveLink.find(label, listOf(entity.copy(argument = "https://example.com"))))
    }
}
