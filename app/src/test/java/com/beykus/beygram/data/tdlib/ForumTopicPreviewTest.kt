package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import org.junit.Assert.assertEquals
import org.junit.Test

class ForumTopicPreviewTest {
    private fun message(
        text: String = "",
        media: MessageMedia? = null,
        serviceText: String? = null
    ) = ChatMessage(
        id = 15L, chatId = 12L, text = text, time = "",
        isOutgoing = false, media = media, serviceText = serviceText
    )

    @Test fun textMessageIsShownWhenHistoryIsOld() {
        assertEquals("Hello", forumTopicMessagePreview(message(text = "  Hello  ")))
    }

    @Test fun documentUsesOriginalFilenameAsPreview() {
        val media = MessageMedia(MediaKind.Document, 10, fileName = "report.pdf")
        assertEquals("report.pdf", forumTopicMessagePreview(message(media = media)))
    }

    @Test fun serviceEventCanBePreviewed() {
        assertEquals("Topic renamed", forumTopicMessagePreview(message(serviceText = "Topic renamed")))
    }

    @Test fun photoWithoutFilenameNeedsGenericRecentMessageLabel() {
        assertEquals("", forumTopicMessagePreview(message(media = MessageMedia(MediaKind.Photo, 11))))
    }
}
