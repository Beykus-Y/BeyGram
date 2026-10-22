package com.beykus.beygram.ui.screens.theme

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.ChatWallpaper
import com.beykus.beygram.data.appearance.WallpaperFiles
import com.beykus.beygram.data.appearance.WallpaperGradientChoices
import com.beykus.beygram.data.appearance.WallpaperPresetSpec
import com.beykus.beygram.data.appearance.WallpaperPresets
import com.beykus.beygram.data.appearance.WallpaperSolidChoices
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.PatternWallpaper
import kotlinx.coroutines.launch

private val TileShape = RoundedCornerShape(14.dp)

private val PresetNameResources: Map<String, Int> = mapOf(
    "lavender-flow" to R.string.wallpaper_preset_lavender_flow,
    "arctic-blue" to R.string.wallpaper_preset_arctic_blue,
    "mint-air" to R.string.wallpaper_preset_mint_air,
    "sunset-peach" to R.string.wallpaper_preset_sunset_peach,
    "soft-rose" to R.string.wallpaper_preset_soft_rose,
    "blue-notes" to R.string.wallpaper_preset_blue_notes,
    "graphite-wave" to R.string.wallpaper_preset_graphite_wave,
    "forest-whisper" to R.string.wallpaper_preset_forest_whisper,
    "deep-night" to R.string.wallpaper_preset_deep_night,
    "violet-mesh" to R.string.wallpaper_preset_violet_mesh,
    "ocean-current" to R.string.wallpaper_preset_ocean_current,
    "amber-glow" to R.string.wallpaper_preset_amber_glow,
    "sky-paper" to R.string.wallpaper_preset_sky_paper,
    "terracotta-dust" to R.string.wallpaper_preset_terracotta_dust,
    "silver-mist" to R.string.wallpaper_preset_silver_mist,
    "emerald-tide" to R.string.wallpaper_preset_emerald_tide
)

/**
 * All chat wallpaper choices, including existing solid/gradient/photo options.
 * The user edits a draft; tapping Apply in ThemeSettingsScreen persists the choice.
 */
@Composable
internal fun WallpaperSection(
    selected: ChatWallpaper,
    saved: ChatWallpaper,
    onSelect: (ChatWallpaper) -> Unit,
    bubbleRadius: Dp = 16.dp,
    spacing: Dp = 8.dp
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = WallpaperFiles.import(context, uri)
            if (name != null) {
                WallpaperFiles.deleteAllExcept(context, setOfNotNull((saved as? ChatWallpaper.Photo)?.fileName, name))
                onSelect(ChatWallpaper.Photo(name, (selected as? ChatWallpaper.Photo)?.dimPercent ?: ChatWallpaper.DEFAULT_DIM_PERCENT))
            } else Toast.makeText(context, R.string.wallpaper_import_failed, Toast.LENGTH_SHORT).show()
        }
    }
    val noneLabel = stringResource(R.string.wallpaper_option_none)
    val photoLabel = stringResource(R.string.wallpaper_option_photo)
    val presetIntensity = (selected as? ChatWallpaper.Preset)?.intensityPercent ?: ChatWallpaper.DEFAULT_PATTERN_INTENSITY
    // Restore the selected wallpaper on entry. A single lazy row avoids rendering all
    // sixteen vector patterns during every wallpaper-intensity slider frame.
    val stripPosition = rememberLazyListState(
        initialFirstVisibleItemIndex = WallpaperPresets.indexOfFirst {
            it.id == (selected as? ChatWallpaper.Preset)?.id
        }.coerceAtLeast(0)
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.wallpaper_curated_title),
            color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            stringResource(R.string.wallpaper_curated_subtitle),
            color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.wallpaper_horizontal_hint), color = palette.textSecondary, fontSize = 12.sp,
                modifier = Modifier.weight(1f))
            if (selected != ChatWallpaper.None) {
                TextButton(onClick = { onSelect(ChatWallpaper.None) }) {
                    Text(stringResource(R.string.wallpaper_reset), color = palette.accent)
                }
            }
        }
        LazyRow(
            state = stripPosition,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 2.dp, end = 12.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(WallpaperPresets, key = { it.id }) { preset ->
                val name = stringResource(PresetNameResources.getValue(preset.id))
                PatternTile(
                    preset, name,
                    selected = (selected as? ChatWallpaper.Preset)?.id == preset.id,
                    intensity = presetIntensity,
                    onClick = { onSelect(ChatWallpaper.Preset(preset.id, presetIntensity)) }
                )
            }
        }
        if (selected is ChatWallpaper.Preset) {
            Text(
                stringResource(R.string.wallpaper_pattern_intensity, selected.intensityPercent),
                color = palette.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Slider(
                value = selected.intensityPercent.toFloat(),
                onValueChange = { onSelect(selected.copy(intensityPercent = it.toInt())) },
                valueRange = 0f..ChatWallpaper.MAX_PATTERN_INTENSITY.toFloat(),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Text(
            stringResource(R.string.wallpaper_classic_title),
            color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Tile(selected == ChatWallpaper.None, noneLabel, { onSelect(ChatWallpaper.None) }) {
                Box(Modifier.size(56.dp).background(palette.surface2), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Block, null, tint = palette.textSecondary)
                }
            }
            WallpaperSolidChoices.forEachIndexed { index, color ->
                Tile(
                    selected == ChatWallpaper.Solid(color),
                    stringResource(R.string.wallpaper_option_solid, index + 1),
                    { onSelect(ChatWallpaper.Solid(color)) }
                ) { Box(Modifier.size(56.dp).background(Color(color))) }
            }
            WallpaperGradientChoices.forEachIndexed { index, (top, bottom) ->
                Tile(
                    selected == ChatWallpaper.Gradient(top, bottom),
                    stringResource(R.string.wallpaper_option_gradient, index + 1),
                    { onSelect(ChatWallpaper.Gradient(top, bottom)) }
                ) {
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
            Text(
                stringResource(R.string.wallpaper_dim), color = palette.textSecondary,
                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Slider(
                value = selected.dimPercent.toFloat(),
                onValueChange = { onSelect(selected.copy(dimPercent = it.toInt())) },
                valueRange = 0f..ChatWallpaper.MAX_DIM_PERCENT.toFloat(),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

/** Single 88dp wallpaper card in a horizontal carousel; preserves 48dp+ touch target. */
@Composable
private fun PatternTile(
    preset: WallpaperPresetSpec,
    name: String,
    selected: Boolean,
    intensity: Int,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    Column(Modifier.width(88.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(width = 88.dp, height = 94.dp)
                .clip(TileShape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = name }
                .border(
                    if (selected) 3.dp else 1.dp,
                    if (selected) palette.accent else palette.divider,
                    TileShape
                ),
            contentAlignment = Alignment.Center
        ) {
            PatternWallpaper(preset, intensity, modifier = Modifier.matchParentSize())
            // Two tiny bubbles make contrast and placement visible even before opening the large preview.
            Box(
                Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 31.dp)
                    .size(width = 40.dp, height = 13.dp)
                    .clip(RoundedCornerShape(6.dp)).background(palette.incomingBubble)
            )
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 16.dp)
                    .size(width = 45.dp, height = 14.dp)
                    .clip(RoundedCornerShape(6.dp)).background(palette.outgoingBubble)
            )
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
                        .clip(RoundedCornerShape(50)).background(palette.accent),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Check, null, tint = palette.onAccent, modifier = Modifier.size(15.dp)) }
            }
        }
        Text(
            name, color = if (selected) palette.accent else palette.textSecondary,
            fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, lineHeight = 15.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
        )
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
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
    ) { content() }
}
