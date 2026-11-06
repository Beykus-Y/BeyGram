package com.beykus.beygram.data.chatmemory

import com.beykus.beygram.data.settings.ChatMemorySettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatMemoryRecorderTest {
    private val context = RuntimeEnvironment.getApplication()

    // A new account for every test, so that no database or preference is shared between tests.
    private val accountId = "recorder-${UUID.randomUUID()}"
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
    fun clearingRemovesWhatWasKeptAndKeepsTheSwitches() {
        settings.update { it.copy(enabled = true) }
        recorder.recordSeen(
            listOf(
                MemoryObservation(
                    chatId = CHAT,
                    messageId = 1L,
                    isOutgoing = false,
                    dateSeconds = 1_700_000_000L,
                    senderId = 7L,
                    senderName = "Anna",
                    snapshot = MemorySnapshot("hello", hasAttachment = false)
                )
            )
        )
        // trimAndWait returns after the queued write has run, so the message is stored when the usage is read.
        recorder.trimAndWait()
        assertEquals(1, recorder.usage().messages)

        recorder.clearAllAndWait()

        assertEquals(0, recorder.usage().messages)
        assertTrue(settings.current.enabled)
    }

    @Test
    fun accountThatNeverKeptAnythingReportsNothingAndOpensNoDatabase() {
        assertEquals(0, recorder.usage().messages)
        assertEquals(0L, recorder.usage().bytes)
        assertFalse(ChatMemoryStore.hasDatabase(context, accountId))
    }

    private companion object {
        const val CHAT = 42L
    }
}
