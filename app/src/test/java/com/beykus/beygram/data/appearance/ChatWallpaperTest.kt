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


    @Test fun sixteenStablePresetsHaveUniqueIdsAndBothBrightnessPalettes() {
        org.junit.Assert.assertEquals(16, WallpaperPresets.size)
        org.junit.Assert.assertEquals(16, WallpaperPresets.map { it.id }.distinct().size)
        WallpaperPresets.forEach { preset ->
            org.junit.Assert.assertNotNull(wallpaperPreset(preset.id))
            org.junit.Assert.assertEquals(3, preset.colors(dark = false, amoled = false).size)
            org.junit.Assert.assertEquals(3, preset.colors(dark = true, amoled = false).size)
            org.junit.Assert.assertEquals(
                List(3) { 0xFF000000.toInt() },
                preset.colors(dark = true, amoled = true)
            )
        }
    }

    @Test fun everyPresetWithIntensityRoundTrips() {
        WallpaperPresets.forEach { spec ->
            listOf(0, 17, ChatWallpaper.MAX_PATTERN_INTENSITY).forEach { intensity ->
                val wallpaper = ChatWallpaper.Preset(spec.id, intensity)
                assertEquals(wallpaper, chatWallpaperFromStored(wallpaper.encode()))
            }
        }
    }

    @Test fun unknownPresetIsRejectedAndInvalidIntensityIsClamped() {
        assertEquals(ChatWallpaper.None, chatWallpaperFromStored("preset:unknown:20"))
        assertEquals(ChatWallpaper.None, chatWallpaperFromStored("preset:lavender-flow"))
        assertEquals(ChatWallpaper.None, chatWallpaperFromStored("preset:lavender-flow:zz"))
        assertEquals(ChatWallpaper.Preset("lavender-flow", 0), chatWallpaperFromStored("preset:lavender-flow:-7"))
        assertEquals(
            ChatWallpaper.Preset("lavender-flow", ChatWallpaper.MAX_PATTERN_INTENSITY),
            chatWallpaperFromStored("preset:lavender-flow:200")
        )
    }

    @Test fun defaultChoiceHasNoWallpaper() {
        assertEquals(ChatWallpaper.None, AppearanceConfig().wallpaper)
    }
}
