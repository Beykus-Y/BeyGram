package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaViewerPolicyTest {
    private val plain = MessageMedia(MediaKind.Photo, fileId = 1, mimeType = "image/jpeg")
    private val secret = MessageMedia(MediaKind.Photo, fileId = 2, mimeType = "image/jpeg", isSecret = true)
    private val secretVideo = MessageMedia(MediaKind.Video, fileId = 3, mimeType = "video/mp4", isSecret = true)

    private fun concealedWhenSettledOn(pages: List<MessageMedia>, settled: Int): List<Boolean> =
        pages.mapIndexed { index, media -> MediaViewerPolicy.concealed(media, active = index == settled) }

    @Test fun inactiveSecretPageStaysConcealed() {
        assertTrue(MediaViewerPolicy.concealed(secret, active = false))
        assertTrue(MediaViewerPolicy.concealed(secretVideo, active = false))
    }

    @Test fun activeSecretPageIsShown() {
        assertFalse(MediaViewerPolicy.concealed(secret, active = true))
    }

    @Test fun inactivePlainPageStaysPreloaded() {
        assertFalse(MediaViewerPolicy.concealed(plain, active = false))
    }

    @Test fun secureFollowsSettledPage() {
        assertTrue(MediaViewerPolicy.secureWhileShowing(secret))
        assertFalse(MediaViewerPolicy.secureWhileShowing(plain))
    }

    @Test fun neighbourSecretPageStaysClosedWhileSwipeIsNotSettled() {
        // Свайп от первой страницы ко второй ещё не завершён: settledPage остаётся первым, вторая закрыта.
        assertEquals(listOf(false, true, false), concealedWhenSettledOn(listOf(plain, secret, plain), settled = 0))
    }

    @Test fun secretPageOpensOnlyAfterSwipeSettlesOnIt() {
        assertEquals(listOf(false, false, false), concealedWhenSettledOn(listOf(plain, secret, plain), settled = 1))
    }

    @Test fun secretPageClosesAgainAfterSwipingAway() {
        assertEquals(listOf(true, false), concealedWhenSettledOn(listOf(secret, plain), settled = 1))
    }
}
