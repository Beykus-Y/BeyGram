package com.beykus.beygram.data.appearance

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatWallpaperTest {
    @Test fun everyWallpaperReadsBackFromItsStoredString() {
        listOf(
            ChatWallpaper.None,
            ChatWallpaper.Solid(0xFFF3E9DC.toInt()),
            ChatWallpaper.Gradient(0xFF16323A.toInt(), 0xFF0B1418.toInt()),
            ChatWallpaper.Photo("wallpaper_1.jpg", 35)
        ).forEach { assertEquals(it, chatWallpaperFromStored(it.encode())) }
    }

    @Test fun missingOrDamagedValueIsNoWallpaper() {
        listOf(null, "", "solid", "solid:zz", "gradient:ff", "photo:20", "photo:x:a.jpg", "other:1").forEach {
            assertEquals(ChatWallpaper.None, chatWallpaperFromStored(it))
        }
    }

    @Test fun photoNameCannotLeaveTheWallpaperFolder() {
        assertEquals(ChatWallpaper.None, chatWallpaperFromStored("photo:20:../databases/x"))
        assertEquals(ChatWallpaper.None, chatWallpaperFromStored("photo:20:.."))
    }

    @Test fun dimIsKeptInRange() {
        assertEquals(ChatWallpaper.Photo("a.jpg", ChatWallpaper.MAX_DIM_PERCENT), chatWallpaperFromStored("photo:999:a.jpg"))
    }

    @Test fun defaultChoiceHasNoWallpaper() {
        assertEquals(ChatWallpaper.None, AppearanceConfig().wallpaper)
    }
}
