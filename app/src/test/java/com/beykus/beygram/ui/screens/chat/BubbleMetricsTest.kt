package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class BubbleMetricsTest {
    @Test fun defaultsAreTheLookBeyGramHasAlwaysHad() {
        assertEquals(
            BubbleMetrics(
                cornerRadius = 20.dp, tailRadius = 6.dp, sideInset = 14.dp,
                textInset = 12.dp, textTopInset = 8.dp, itemSpacing = 8.dp, listPadding = 16.dp
            ),
            BubbleMetrics()
        )
    }
}
