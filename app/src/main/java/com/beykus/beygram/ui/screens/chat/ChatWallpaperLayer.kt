package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.beykus.beygram.data.appearance.ChatWallpaper
import com.beykus.beygram.data.appearance.WallpaperFiles
import com.beykus.beygram.data.appearance.wallpaperPreset

/** The wallpaper chosen in the appearance settings. MainActivity provides it next to the theme. */
internal val LocalChatWallpaper = compositionLocalOf<ChatWallpaper> { ChatWallpaper.None }

/** Draws the wallpaper behind the messages. With [ChatWallpaper.None], or while a picture loads, the theme background shows. */
@Composable
internal fun ChatWallpaperLayer(wallpaper: ChatWallpaper, modifier: Modifier = Modifier.fillMaxSize()) {
    when (wallpaper) {
        ChatWallpaper.None -> Unit
        is ChatWallpaper.Solid -> Box(modifier.background(Color(wallpaper.color)))
        is ChatWallpaper.Gradient -> Box(modifier.background(Brush.verticalGradient(listOf(Color(wallpaper.top), Color(wallpaper.bottom)))))
        is ChatWallpaper.Preset -> wallpaperPreset(wallpaper.id)?.let { preset ->
            PatternWallpaper(preset, wallpaper.intensityPercent, modifier)
        }
        is ChatWallpaper.Photo -> {
            val context = LocalContext.current
            val bitmap by produceState<ImageBitmap?>(null, wallpaper.fileName) {
                value = WallpaperFiles.load(context, wallpaper.fileName)?.asImageBitmap()
            }
            bitmap?.let {
                Box(modifier) {
                    Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (wallpaper.dimPercent > 0) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = wallpaper.dimPercent / 100f)))
                }
            }
        }
    }
}
