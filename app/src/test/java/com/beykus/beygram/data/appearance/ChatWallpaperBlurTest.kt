package com.beykus.beygram.data.appearance

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatWallpaperBlurTest {
    @Test
    fun blurredPhotoRoundTripsThroughStorage() {
        val photo = ChatWallpaper.Photo("cover.jpg", dimPercent = 30, blurred = true)

        assertEquals("blurphoto:30:cover.jpg", photo.encode())
        assertEquals(photo, chatWallpaperFromStored(photo.encode()))
    }

    @Test
    fun photoSavedBeforeBlurReadsAsNotBlurred() {
        assertEquals(ChatWallpaper.Photo("cover.jpg", dimPercent = 30), chatWallpaperFromStored("photo:30:cover.jpg"))
    }

    @Test
    fun damagedBlurredPhotoReadsAsNoWallpaper() {
        assertEquals(ChatWallpaper.None, chatWallpaperFromStored("blurphoto:x:cover.jpg"))
    }
}
