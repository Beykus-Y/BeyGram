package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.mutableStateOf
import com.beykus.beygram.domain.model.ReactionOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageActionsStateTest {
    @Test fun changingTheTargetDiscardsThePreviousTargetsPromptsAndOptions() {
        val actions = ChatMessageActionsState(mutableStateOf(null), mutableStateOf(false), mutableStateOf(false))
        actions.open(10L)
        actions.reactionOptions = listOf(ReactionOption("👍"))
        actions.deleting = true
        actions.pinConfirm = true

        actions.open(10L)
        assertTrue(actions.deleting)

        actions.open(20L)
        assertEquals(20L, actions.targetId)
        assertTrue(actions.reactionOptions.isEmpty())
        assertFalse(actions.deleting)
        assertFalse(actions.pinConfirm)

        actions.close()
        assertNull(actions.targetId)
    }
}
