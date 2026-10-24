package com.beykus.beygram.ui.screens.chats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublicChatDiscoveryTest {
    @Test fun plainUsernameIsAcceptedWithOrWithoutAt() {
        assertEquals(PublicDiscoveryTarget.Username("durov"), publicDiscoveryTarget("@durov"))
        assertEquals(PublicDiscoveryTarget.Username("durov"), publicDiscoveryTarget("durov"))
    }

    @Test fun publicLinksAreNormalized() {
        assertEquals(PublicDiscoveryTarget.Username("durov"), publicDiscoveryTarget("t.me/durov"))
        assertEquals(PublicDiscoveryTarget.Username("durov"), publicDiscoveryTarget("https://t.me/durov"))
        assertEquals(PublicDiscoveryTarget.Username("durov"), publicDiscoveryTarget("tg://resolve?domain=durov"))
    }

    @Test fun inviteLinksKeepTheirCanonicalUrl() {
        assertEquals(PublicDiscoveryTarget.Invite("https://t.me/+AbCdEf"), publicDiscoveryTarget("t.me/+AbCdEf"))
        assertEquals(PublicDiscoveryTarget.Invite("https://t.me/+AbCdEf"), publicDiscoveryTarget("https://t.me/joinchat/AbCdEf"))
        assertEquals(PublicDiscoveryTarget.Invite("https://t.me/+AbCdEf"), publicDiscoveryTarget("tg://join?invite=AbCdEf"))
    }

    @Test fun nonChatTargetsAndJunkAreRejected() {
        listOf(
            "",
            " ",
            "not a username",
            "tg://login?token=secret",
            "tg://proxy?server=example.com&port=443&secret=abc",
            "https://example.com/durov",
            "t.me/durov/42"
        ).forEach { assertNull("Accepted invalid discovery input: $it", publicDiscoveryTarget(it)) }
    }
}
