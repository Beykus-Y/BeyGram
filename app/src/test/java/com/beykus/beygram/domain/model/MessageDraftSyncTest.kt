package com.beykus.beygram.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageDraftSyncTest {
    private val cleared = MessageDraft()
    private val remote = MessageDraft("черновик с другого устройства")

    @Test fun aSyncedBackupYieldsToADraftClearedOnAnotherDevice() {
        val synced = StoredMessageDraft(MessageDraft("привет"), 1720000000000L, synced = true)
        assertFalse(MessageDraftSync.keepsUnsentLocal(synced, cleared, 0L))
    }

    @Test fun unsentEditsSurviveADraftClearedOnAnotherDevice() {
        val unsent = StoredMessageDraft(MessageDraft("привет"), 1720000000000L)
        assertTrue(MessageDraftSync.keepsUnsentLocal(unsent, cleared, 0L))
    }

    @Test fun aSyncedBackupYieldsToARemoteDraftEvenWhenItsTimestampIsOlder() {
        val synced = StoredMessageDraft(MessageDraft("старый"), 1720000002000L, synced = true)
        assertFalse(MessageDraftSync.keepsUnsentLocal(synced, remote, 1720000001000L))
    }

    @Test fun aNewerRemoteDraftReplacesUnsentEdits() {
        val unsent = StoredMessageDraft(MessageDraft("локально"), 1720000000000L)
        assertFalse(MessageDraftSync.keepsUnsentLocal(unsent, remote, 1720000001000L))
    }

    @Test fun unsentEditsWinOverARemoteDraftThatIsNotNewer() {
        val unsent = StoredMessageDraft(MessageDraft("локально"), 1720000000000L)
        assertTrue(MessageDraftSync.keepsUnsentLocal(unsent, remote, 1720000000000L))
        assertTrue(MessageDraftSync.keepsUnsentLocal(unsent, remote, 1719999999000L))
    }

    @Test fun withoutABackupThereIsNothingToKeep() {
        assertFalse(MessageDraftSync.keepsUnsentLocal(null, remote, 1720000000000L))
        assertFalse(MessageDraftSync.keepsUnsentLocal(null, cleared, 0L))
    }
}
