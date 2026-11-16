package com.beykus.beygram.media.bgwave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tombstones, the per-peer invitation rate limit and retiring invitations whose Telegram message is gone. */
class BgWaveInviteHygieneTest {
    private val now = 1_000L

    private class FakeSession(val outbound: ArrayDeque<String> = ArrayDeque()) : BgWaveSessionPort {
        var closed = false
        override fun startOffer(nowSeconds: Long, ttlSeconds: Long) = true
        override fun handleRemote(uri: String, nowSeconds: Long) = true
        override fun pollOutbound(): String? = outbound.removeFirstOrNull()
        override fun status() = BgWaveSessionStatus("Idle", "None", "")
        override fun stop(emitEnd: Boolean, nowSeconds: Long) {}
        override fun reconnect(nowSeconds: Long) = true
        override fun close() { closed = true }
    }

    private fun sidOf(n: Int) = "%032x".format(n)

    private fun link(type: Int, id: String, nonce: Long = 1L, exp: Long = now + 300) = "bg://wave/v1/$type-$id-$nonce-$exp"

    private fun decode(uri: String, at: Long): BgWaveDecodeResult {
        val parts = uri.removePrefix(BgWaveLink.URI_PREFIX).removePrefix(BgWaveLink.HTTPS_PREFIX).split('-')
        if (parts.size != 4) return BgWaveDecodeResult.Rejected("MALFORMED")
        val exp = parts[3].toLong()
        if (at >= exp) return BgWaveDecodeResult.Rejected("EXPIRED")
        return BgWaveDecodeResult.Ok(parts[0].toInt(), parts[1], 0L, exp, 3L, parts[2].toLong(), 1)
    }

    private fun controller(session: FakeSession? = FakeSession(), maxInvites: Int = 50, maxClosed: Int = 512) = BgWaveController(
        createSession = { session },
        decode = ::decode,
        encodeControl = { type, id, at, nonce -> link(type, id, nonce, at + 300) },
        maxInvites = maxInvites,
        maxClosed = maxClosed,
    )

    private fun BgWaveController.offer(
        id: String, nonce: Long = 1L, exp: Long = now + 300, at: Long = now, sender: Long = 7L, messageId: Long = 0L,
    ) = onMessage("a", sender, sender, false, true, link(1, id, nonce, exp), at, messageId)

    @Test fun evictedInviteIsNotRevivedByAnUnexpiredOffer() {
        val c = controller(maxInvites = 1)
        c.offer(sidOf(1))
        c.offer(sidOf(2))
        assertFalse(c.inviteStates.value.containsKey(sidOf(1)))
        c.offer(sidOf(1), nonce = 2L)
        assertFalse(c.inviteStates.value.containsKey(sidOf(1)))
    }

    @Test fun fullTombstoneTableRefusesNewInvitesUntilOneExpires() {
        val c = controller(maxClosed = 1)
        c.offer(sidOf(1))
        assertNotNull(c.decline(sidOf(1), now))
        c.offer(sidOf(2))
        assertFalse(c.inviteStates.value.containsKey(sidOf(2)))
        // The closed offer expired at now + 300; with clock skew it is forgotten after now + 600.
        val later = now + 300 + BgWaveController.CLOCK_SKEW_SECONDS + 1
        c.offer(sidOf(3), exp = later + 300, at = later)
        assertTrue(c.inviteStates.value.containsKey(sidOf(3)))
    }

    @Test fun closedSessionStaysClosedWhileItsOfferIsUnexpired() {
        val c = controller()
        c.offer(sidOf(1))
        c.decline(sidOf(1), now)
        c.offer(sidOf(1), nonce = 5L, at = now + 10)
        assertEquals(BgWaveInviteStatus.Declined, c.inviteStates.value[sidOf(1)]?.status)
    }

    @Test fun peerIsLimitedToFiveNewInvitesPerTenMinutes() {
        val c = controller()
        for (i in 1..6) c.offer(sidOf(i))
        assertEquals(BgWaveController.MAX_PEER_INVITES, c.inviteStates.value.size)
        assertFalse(c.inviteStates.value.containsKey(sidOf(6)))
        // Another peer is not affected.
        c.offer(sidOf(7), sender = 8L)
        assertTrue(c.inviteStates.value.containsKey(sidOf(7)))
        val later = now + BgWaveController.PEER_INVITE_WINDOW_SECONDS + 1
        c.offer(sidOf(8), exp = later + 300, at = later)
        assertTrue(c.inviteStates.value.containsKey(sidOf(8)))
    }

    @Test fun duplicateOfferDoesNotUseUpTheRateLimit() {
        val c = controller()
        for (n in 1L..10L) c.offer(sidOf(1), nonce = n)
        for (i in 2..5) c.offer(sidOf(i))
        assertEquals(5, c.inviteStates.value.size)
    }

    @Test fun deletedOfferMessageRetiresThePendingInvite() {
        val c = controller()
        c.offer(sidOf(1), messageId = 42L)
        c.onOfferMessageGone("a", 7L, 42L, null, now)
        assertEquals(BgWaveInviteStatus.Cancelled, c.inviteStates.value[sidOf(1)]?.status)
        c.offer(sidOf(1), nonce = 9L)
        assertEquals(BgWaveInviteStatus.Cancelled, c.inviteStates.value[sidOf(1)]?.status)
    }

    @Test fun editThatKeepsTheSameOfferKeepsTheInvite() {
        val c = controller()
        c.offer(sidOf(1), messageId = 42L)
        c.onOfferMessageGone("a", 7L, 42L, sidOf(1), now)
        assertEquals(BgWaveInviteStatus.Pending, c.inviteStates.value[sidOf(1)]?.status)
        assertTrue(c.hasOfferMessage("a", 7L, 42L))
    }

    @Test fun deletingTheMessageOfAJoinedInviteLeavesTheLiveSessionAlone() {
        val session = FakeSession(ArrayDeque(listOf(link(2, sidOf(1)))))
        val c = controller(session)
        c.offer(sidOf(1), messageId = 42L)
        assertNotNull(c.join(sidOf(1), now))
        c.onOfferMessageGone("a", 7L, 42L, null, now)
        assertEquals(BgWavePhase.Connecting, c.state.value?.phase)
        assertFalse(session.closed)
        assertEquals(BgWaveInviteStatus.Joined, c.inviteStates.value[sidOf(1)]?.status)
    }

    @Test fun hostDeletingUnansweredOfferCancelsItLocally() {
        val session = FakeSession(ArrayDeque(listOf(link(1, sidOf(1)))))
        val c = controller(session)
        assertNotNull(c.startHosting("a", 7L, 7L, now))
        c.offerSent(sidOf(1), true, now)
        c.noteOfferMessage("a", 7L, 55L, sidOf(1))
        c.onOfferMessageGone("a", 7L, 55L, null, now)
        assertEquals(BgWavePhase.Ended, c.state.value?.phase)
        assertTrue(session.closed)
    }

    @Test fun hostDeletingOfferAfterTheListenerAnsweredKeepsTheSession() {
        val session = FakeSession(ArrayDeque(listOf(link(1, sidOf(1)))))
        val c = controller(session)
        c.startHosting("a", 7L, 7L, now)
        c.offerSent(sidOf(1), true, now)
        c.noteOfferMessage("a", 7L, 55L, sidOf(1))
        c.onMessage("a", 7L, 7L, false, true, link(2, sidOf(1), 1L), now)
        assertEquals(BgWavePhase.Connecting, c.state.value?.phase)
        c.onOfferMessageGone("a", 7L, 55L, null, now)
        assertEquals(BgWavePhase.Connecting, c.state.value?.phase)
        assertFalse(session.closed)
    }
}
