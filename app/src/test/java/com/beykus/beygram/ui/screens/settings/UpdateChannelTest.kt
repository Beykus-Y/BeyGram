package com.beykus.beygram.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateChannelTest {
    @Test fun devPackageUsesDevBetaChannel() {
        assertEquals(UpdateChannel.DevBeta, updateChannelFor("com.beykus.beygram.dev"))
    }

    @Test fun productionAndUnknownPackagesUseStableChannel() {
        assertEquals(UpdateChannel.Stable, updateChannelFor("com.beykus.beygram"))
        assertEquals(UpdateChannel.Stable, updateChannelFor("com.beykus.beygram.other"))
    }
}
