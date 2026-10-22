package com.beykus.beygram.data.appearance

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageTextSizePolicyTest {
    @Test fun defaultMatchesPreviouslyShippedChatTypography() {
        assertEquals(16, AppearanceConfig().messageTextSizeSp)
    }
    @Test fun badPersistedValuesAreClampedAndEndpointsAreValid() {
        assertEquals(12, validMessageTextSize(-100))
        assertEquals(12, validMessageTextSize(12))
        assertEquals(19, validMessageTextSize(19))
        assertEquals(22, validMessageTextSize(22))
        assertEquals(22, validMessageTextSize(500))
    }
}
