package com.beykus.beygram.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchFieldHeightPolicyTest {
    @Test fun standardFontPreservesCompactSearchField() {
        assertEquals(48f, searchFieldMinHeightDp(1f), 0f)
        assertEquals(48f, searchFieldMinHeightDp(0.85f), 0f)
    }

    @Test fun largeTextGetsMoreVerticalSpace() {
        assertEquals(60f, searchFieldMinHeightDp(1.25f), 0f)
        assertEquals(72f, searchFieldMinHeightDp(1.5f), 0f)
    }

    @Test fun extremeFontScaleIsBounded() {
        assertEquals(72f, searchFieldMinHeightDp(2.0f), 0f)
        assertEquals(72f, searchFieldMinHeightDp(3.0f), 0f)
    }
}
