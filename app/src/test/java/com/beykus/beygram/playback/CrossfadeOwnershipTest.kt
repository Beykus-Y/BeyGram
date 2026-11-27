package com.beykus.beygram.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The ordinary crossfade and the BG Mix handover both ramp the same primary player. These tests pin the single-owner
 * rule the engine passes to [TrackCrossfader]: only one of them may write the gain at any moment.
 */
class CrossfadeOwnershipTest {
    @Test
    fun theCrossfadeOwnsThePrimaryWhenNothingElseDoes() {
        assertNull(crossfadeOwnershipBlock(bgMixActive = false, handoverFading = false, deferredSeek = false))
    }

    @Test
    fun anActiveNativeSessionAlwaysBlocksTheOrdinaryCrossfade() {
        assertEquals(
            CrossfadeOwnershipBlock.BgMix,
            crossfadeOwnershipBlock(bgMixActive = true, handoverFading = false, deferredSeek = false)
        )
        // During the commit the driver is already active; the extra flag still names the handover phase.
        assertEquals(
            CrossfadeOwnershipBlock.BgMix,
            crossfadeOwnershipBlock(bgMixActive = true, handoverFading = true, deferredSeek = false)
        )
    }

    @Test
    fun theHandoverFadeKeepsOwnershipEvenIfTheDriverReportsInactive() {
        // The native owner is stopped while its fade/cleanup still restores the primary level.
        assertEquals(
            CrossfadeOwnershipBlock.Handover,
            crossfadeOwnershipBlock(bgMixActive = false, handoverFading = true, deferredSeek = true)
        )
    }

    @Test
    fun aDeferredGrowingSeekBlocksTheCrossfadeUntilItIsApplied() {
        assertEquals(
            CrossfadeOwnershipBlock.DeferredSeek,
            crossfadeOwnershipBlock(bgMixActive = false, handoverFading = false, deferredSeek = true)
        )
    }

    @Test
    fun preparationAloneDoesNotBlockOrdinaryTransitions() {
        // Staging is silent and may take long or fail; only the committed handover takes the primary over.
        assertNull(crossfadeOwnershipBlock(bgMixActive = false, handoverFading = false, deferredSeek = false))
    }
}