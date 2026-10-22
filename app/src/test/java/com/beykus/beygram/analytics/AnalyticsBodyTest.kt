package com.beykus.beygram.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsBodyTest {
    @Test
    fun revisionIsAppendedAsLastField() {
        val signature = """{"installation_id":"70f6c087-fdc1-4e11-9a81-dca4b8a1bd20","account_count":0}"""
        assertEquals(
            """{"installation_id":"70f6c087-fdc1-4e11-9a81-dca4b8a1bd20","account_count":0,"revision":5}""",
            AnalyticsPayload.body(signature, 5L)
        )
    }
}
