package com.beykus.beygram.share

import com.beykus.beygram.domain.model.ForumTopic
import org.junit.Assert.*
import org.junit.Test

class ShareTopicPolicyTest {
    @Test fun ordinaryChatNeverAcceptsAnArbitraryTopic() {
        assertTrue(isShareTopicValid(0, Result.success(null)))
        assertFalse(isShareTopicValid(7, Result.success(null)))
        assertTrue(isShareTopicValid(0, null))
        assertFalse(isShareTopicValid(7, null))
    }

    @Test fun unavailableForumCannotSilentlyFallBackToGeneral() {
        assertFalse(isShareTopicValid(0, Result.failure(IllegalStateException())))
        assertFalse(isShareTopicValid(0, Result.success(emptyList())))
        assertFalse(isShareTopicValid(7, Result.success(emptyList())))
    }

    @Test fun onlyExactWritableForumTopicIsAccepted() {
        val result = Result.success(listOf(ForumTopic(7, "Open"), ForumTopic(8, "Closed", canWrite = false)))
        assertTrue(isShareTopicValid(7, result))
        assertFalse(isShareTopicValid(8, result))
        assertFalse(isShareTopicValid(0, result))
        assertFalse(isShareTopicValid(9, result))
    }
}
