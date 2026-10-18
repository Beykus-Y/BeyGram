package com.beykus.beygram.ui.screens.theme

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.ChatWallpaper
import com.beykus.beygram.data.appearance.WallpaperFiles
import com.beykus.beygram.data.appearance.WallpaperGradientChoices
import com.beykus.beygram.data.appearance.WallpaperSolidChoices
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.launch

private val TileShape = RoundedCornerShape(14.dp)

/**
 * Chat wallpaper choice: none, a solid colour, a gradient or a photo from the gallery.
 * [saved] is the wallpaper already applied. A picture picked here stays a draft until the screen is applied.
 */
@Composable
internal fun WallpaperSection(selected: ChatWallpaper, saved: ChatWallpaper, onSelect: (ChatWallpaper) -> Unit) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // Only the applied picture is worth keeping: older drafts are dropped before the new copy is made.
            WallpaperFiles.deleteAllExcept(context, (saved as? ChatWallpaper.Photo)?.fileName)
            val name = WallpaperFiles.import(context, uri)
            if (name != null) onSelect(ChatWallpaper.Photo(name, (selected as? ChatWallpaper.Photo)?.dimPercent ?: ChatWallpaper.DEFAULT_DIM_PERCENT))
            else Toast.makeText(context, R.string.wallpaper_import_failed, Toast.LENGTH_SHORT).show()
        }
    }
    val noneLabel = stringResource(R.string.wallpaper_option_none)
    val solidLabel = stringResource(R.string.wallpaper_option_solid)
    val gradientLabel = stringResource(R.string.wallpaper_option_gradient)
    val photoLabel = stringResource(R.string.wallpaper_option_photo)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(selected == ChatWallpaper.None, noneLabel, { onSelect(ChatWallpaper.None) }) {
                Box(Modifier.size(56.dp).background(palette.surface2), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Block, null, tint = palette.textSecondary)
                }
            }
            WallpaperSolidChoices.forEach { color ->
                Tile(selected == ChatWallpaper.Solid(color), solidLabel, { onSelect(ChatWallpaper.Solid(color)) }) {
                    Box(Modifier.size(56.dp).background(Color(color)))
                }
            }
            WallpaperGradientChoices.forEach { (top, bottom) ->
                Tile(selected == ChatWallpaper.Gradient(top, bottom), gradientLabel, { onSelect(ChatWallpaper.Gradient(top, bottom)) }) {
                    Box(Modifier.size(56.dp).background(Brush.verticalGradient(listOf(Color(top), Color(bottom)))))
                }
            }
            Tile(selected is ChatWallpaper.Photo, photoLabel, {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Box(Modifier.size(56.dp).background(palette.accentSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Image, null, tint = palette.accentInk)
                }
            }
        }
        if (selected is ChatWallpaper.Photo) {
            Text(stringResource(R.string.wallpaper_dim), color = palette.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 4.dp))
            Slider(
                value = selected.dimPercent.toFloat(),
                onValueChange = { onSelect(selected.copy(dimPercent = it.toInt())) },
                valueRange = 0f..ChatWallpaper.MAX_DIM_PERCENT.toFloat(),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun Tile(isSelected: Boolean, description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val palette = beyGramPalette()
    Box(
        Modifier.clip(TileShape)
            .border(if (isSelected) 3.dp else 1.dp, if (isSelected) palette.accent else palette.divider, TileShape)
            .padding(3.dp).clip(RoundedCornerShape(11.dp))
            .semantics { contentDescription = description }
            .clickable(onClick = onClick)
    ) { content() }
}
