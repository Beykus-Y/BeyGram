package com.beykus.beygram.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPreviewPolicyTest {
    @Test
    fun secretChatsHideTitleTextAndSender() {
        assertTrue(NotificationPreviewPolicy.hideTitle(isSecret = true, metadataReady = true))
        assertTrue(
            NotificationPreviewPolicy.hideText(
                previews = true, previewAllowed = true,
                isSecret = true, metadataReady = true, hasProtectedContent = false,
            )
        )
        assertTrue(
            NotificationPreviewPolicy.hideSender(
                isSecret = true, metadataReady = true, hasProtectedContent = false,
            )
        )
    }

    @Test
    fun groupsWithoutMetadataStayAnonymous() {
        assertTrue(NotificationPreviewPolicy.hideTitle(isSecret = false, metadataReady = false))
        assertFalse(NotificationPreviewPolicy.hideTitle(isSecret = false, metadataReady = true))
    }

    @Test
    fun protectedContentKeepsTheTitleButHidesTheText() {
        assertFalse(NotificationPreviewPolicy.hideTitle(isSecret = false, metadataReady = true))
        assertTrue(
            NotificationPreviewPolicy.hideText(
                previews = true, previewAllowed = true,
                isSecret = false, metadataReady = true, hasProtectedContent = true,
            )
        )
        assertTrue(
            NotificationPreviewPolicy.hideSender(
                isSecret = false, metadataReady = true, hasProtectedContent = true,
            )
        )
    }

    @Test
    fun userPreviewsAndTelegramPermissionAreBothRequired() {
        assertTrue(
            NotificationPreviewPolicy.hideText(
                previews = false, previewAllowed = true,
                isSecret = false, metadataReady = true, hasProtectedContent = false,
            )
        )
        assertTrue(
            NotificationPreviewPolicy.hideText(
                previews = true, previewAllowed = false,
                isSecret = false, metadataReady = true, hasProtectedContent = false,
            )
        )
        assertFalse(
            NotificationPreviewPolicy.hideText(
                previews = true, previewAllowed = true,
                isSecret = false, metadataReady = true, hasProtectedContent = false,
            )
        )
    }
}
