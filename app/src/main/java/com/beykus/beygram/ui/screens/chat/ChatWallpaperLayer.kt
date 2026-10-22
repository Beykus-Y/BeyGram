package com.beykus.beygram.ui.screens.chat

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.beykus.beygram.data.appearance.ChatWallpaper
import com.beykus.beygram.data.appearance.WallpaperFiles
import com.beykus.beygram.data.appearance.wallpaperPreset

/** The wallpaper chosen in the appearance settings. MainActivity provides it next to the theme. */
internal val LocalChatWallpaper = compositionLocalOf<ChatWallpaper> { ChatWallpaper.None }

/** How far a photo wallpaper is blurred on Android 12 and newer. */
private val PhotoBlurRadius = 14.dp

/** Extra darkening, in percent, that stands in for the blur on Android 11 and older. */
private const val OLD_SYSTEM_EXTRA_DIM_PERCENT = 15

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
                // Blur needs Android 12. Older systems darken the photo a little more, so the messages still read on it.
                val blurPhoto = wallpaper.blurred && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                val dimPercent = if (wallpaper.blurred && !blurPhoto) {
                    minOf(wallpaper.dimPercent + OLD_SYSTEM_EXTRA_DIM_PERCENT, ChatWallpaper.MAX_DIM_PERCENT)
                } else {
                    wallpaper.dimPercent
                }
                Box(modifier) {
                    Image(
                        it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().then(if (blurPhoto) Modifier.blur(PhotoBlurRadius) else Modifier)
                    )
                    if (dimPercent > 0) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dimPercent / 100f)))
                }
            }
        }
    }
}
