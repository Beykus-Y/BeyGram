package com.beykus.beygram.data.tdlib

import com.beykus.beygram.data.archive.archivePreferencesName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TdMultiAccountPreferenceIsolationTest {
    @Test fun primaryNeverMovesItsExistingDraftOrArchivePreferences() {
        assertEquals("chat_message_drafts", tdDraftPreferencesName("legacy"))
        assertEquals("chats", archivePreferencesName("legacy"))
    }

    @Test fun distinctAccountsCannotShareDraftsOrArchiveSettings() {
        val a = "a-first"
        val b = "a-second"
        assertNotEquals(tdDraftPreferencesName(a), tdDraftPreferencesName(b))
        assertNotEquals(archivePreferencesName(a), archivePreferencesName(b))
        assertNotEquals(tdDraftPreferencesName("legacy"), tdDraftPreferencesName(a))
        assertNotEquals(archivePreferencesName("legacy"), archivePreferencesName(a))
    }

    @Test(expected = IllegalArgumentException::class)
    fun archivePrefRejectsPathEscapes() { archivePreferencesName("../legacy") }

    @Test(expected = IllegalArgumentException::class)
    fun draftPrefRejectsPathEscapes() { tdDraftPreferencesName("../legacy") }
}
