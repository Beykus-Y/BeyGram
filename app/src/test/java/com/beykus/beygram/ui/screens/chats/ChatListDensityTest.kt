package com.beykus.beygram.ui.screens.chats

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatListDensityTest {
    @Test fun defaultIsTheRowPaddingBeyGramHasAlwaysHad() {
        assertEquals(10.dp, ChatListDensity().rowVerticalPadding)
    }
}
