package com.beykus.beygram.data.chatmemory

import com.beykus.beygram.data.settings.ChatMemorySettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatMemoryDeletionOrderTest {
    private val context = RuntimeEnvironment.getApplication()

    // A new account for every test, so that no database or preference is shared between tests.
    private val accountId = "order-${UUID.randomUUID()}"
    private val settings = ChatMemorySettingsStore.forAccount(context, accountId)
    private val recorder = ChatMemoryRecorder(
        context = context,
        accountId = accountId,
        settings = settings,
        factsOf = { ChatMemoryChatFacts(isSecret = false, hasProtectedContent = false, hasAutoDeleteTimer = false) },
        onVersionsKept = {}
    )

    @After
    fun tearDown() {
        ChatMemoryStore.deleteAccountFiles(context, accountId)
        ChatMemorySettingsStore.releaseAccount(accountId)
    }

    @Test
    fun deletionOfAMessThatIsStillBeingRecordedIsKept() {
        settings.update { it.copy(enabled = true) }
        // The message is queued for storing and deleted straight after. The deletion is queued behind the store, so a read
        // that waits for the queued writes sees it.
        recorder.recordSeen(listOf(observation(1L)))
        recorder.markDeleted(CHAT, listOf(1L), fallback = emptyMap())
        recorder.awaitWrites()

        assertEquals(listOf(1L), recorder.deletedBetween(CHAT, 1L, 1L).map { it.messageId })
    }

    private fun observation(messageId: Long) = MemoryObservation(
        chatId = CHAT,
        messageId = messageId,
        isOutgoing = false,
        dateSeconds = 1_700_000_000L,
        senderId = 7L,
        senderName = "Anna",
        snapshot = MemorySnapshot("hello", hasAttachment = false)
    )

    private companion object {
        const val CHAT = 42L
    }
}
