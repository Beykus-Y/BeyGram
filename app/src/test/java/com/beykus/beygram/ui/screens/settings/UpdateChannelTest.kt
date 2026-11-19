package com.beykus.beygram.ui.screens.settings

import com.beykus.beygram.data.update.UpdateChannel
import com.beykus.beygram.data.update.updateChannelFor
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
