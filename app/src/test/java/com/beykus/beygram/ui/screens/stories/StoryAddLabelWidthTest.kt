package com.beykus.beygram.ui.screens.stories

import org.junit.Assert.assertEquals
import org.junit.Test

class StoryAddLabelWidthTest {
    @Test fun defaultFontScaleKeepsExistingCellWidth() {
        assertEquals(88f, addStoryCellWidthDp(1f), 0f)
    }

    @Test fun largerFontsGetMoreRoomForLocalizedLabel() {
        assertEquals(110f, addStoryCellWidthDp(1.25f), 0f)
        assertEquals(132f, addStoryCellWidthDp(1.5f), 0f)
    }

    @Test fun extremeFontScalesRemainBounded() {
        assertEquals(88f, addStoryCellWidthDp(0.85f), 0f)
        assertEquals(136f, addStoryCellWidthDp(2.0f), 0f)
    }
}
