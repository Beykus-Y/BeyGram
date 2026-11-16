package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumTopicPagingTest {
    @Test fun aListingIsCompleteOnlyWhenTheServerHasNoNextPageAndNoCursorRepeated() {
        assertTrue(forumTopicsPageComplete(hasMorePages = false, cursorRepeated = false))
        assertFalse(forumTopicsPageComplete(hasMorePages = true, cursorRepeated = false))
        assertFalse(forumTopicsPageComplete(hasMorePages = false, cursorRepeated = true))
        assertFalse(forumTopicsPageComplete(hasMorePages = true, cursorRepeated = true))
    }

    @Test fun aReplyOnlyDraftStillShowsTheDraftMarker() {
        val draft = TdApi.DraftMessage()
        draft.replyTo = TdApi.InputMessageReplyToMessage()
        assertTrue(hasDraftContent(draft))
    }

    @Test fun aBlankTextDraftIsNotADraft() {
        val draft = TdApi.DraftMessage()
        draft.content = TdApi.DraftMessageContentText(TdApi.FormattedText("   ", null), null)
        assertFalse(hasDraftContent(draft))
        assertEquals("", forumTopicDraftPreview(draft))
    }

    @Test fun draftPreviewKeepsTheTypedTextWithoutSurroundingWhitespace() {
        val draft = TdApi.DraftMessage()
        draft.content = TdApi.DraftMessageContentText(TdApi.FormattedText("  unfinished thought  ", null), null)
        assertTrue(hasDraftContent(draft))
        assertEquals("unfinished thought", forumTopicDraftPreview(draft))
    }
}
