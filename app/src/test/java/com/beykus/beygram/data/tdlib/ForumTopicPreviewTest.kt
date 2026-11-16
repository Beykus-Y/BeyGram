package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class ForumTopicPreviewTest {
    private val photoFallback = UiText.Res(R.string.media_kind_photo)

    @Test fun textMessageIsShownWhenHistoryIsOld() {
        val projection = forumTopicLastMessagePreview(
            text = "  Hello  ",
            fileName = "",
            serviceText = null,
            fallback = UiText.Raw("")
        )
        assertEquals(UiText.Raw("Hello"), projection.preview)
        assertEquals("Hello", projection.plain)
    }

    @Test fun documentUsesOriginalFilenameAsPreview() {
        val projection = forumTopicLastMessagePreview(
            text = "",
            fileName = "report.pdf",
            serviceText = null,
            fallback = UiText.Raw("")
        )
        assertEquals(UiText.Raw("report.pdf"), projection.preview)
        assertEquals("report.pdf", projection.plain)
    }

    @Test fun serviceEventCanBePreviewed() {
        val projection = forumTopicLastMessagePreview(
            text = "",
            fileName = "",
            serviceText = "Topic renamed",
            fallback = UiText.Raw("")
        )
        assertEquals(UiText.Raw("Topic renamed"), projection.preview)
        assertEquals("Topic renamed", projection.plain)
    }

    @Test fun photoWithoutFilenameFallsBackToTheMediaLabel() {
        val projection = forumTopicLastMessagePreview(
            text = "",
            fileName = "",
            serviceText = null,
            fallback = photoFallback
        )
        assertEquals(photoFallback, projection.preview)
        assertEquals("", projection.plain)
    }
}
