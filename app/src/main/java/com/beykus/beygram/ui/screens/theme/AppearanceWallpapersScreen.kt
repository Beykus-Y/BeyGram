package com.beykus.beygram.ui.screens.theme

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.ChatWallpaper
import com.beykus.beygram.data.appearance.WallpaperFiles
import com.beykus.beygram.data.appearance.WallpaperGradientChoices
import com.beykus.beygram.data.appearance.WallpaperPresetSpec
import com.beykus.beygram.data.appearance.WallpaperPresets
import com.beykus.beygram.data.appearance.WallpaperSolidChoices
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.PatternWallpaper
import com.beykus.beygram.ui.theme.LocalBeyGramTokens
import com.beykus.beygram.ui.theme.resolveTokens
import kotlinx.coroutines.launch

private enum class WallpaperCategory { Patterns, Colors, Photo }

@Composable
internal fun AppearanceWallpapersPage(
    draft: AppearanceConfig,
    systemDark: Boolean,
    onDraftChange: (AppearanceConfig) -> Unit,
    onOpenPreview: () -> Unit
) {
    val palette = beyGramPalette()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val selected = draft.wallpaper
    val initialCategory = when (selected) {
        is ChatWallpaper.Preset -> WallpaperCategory.Patterns
        is ChatWallpaper.Solid, is ChatWallpaper.Gradient, ChatWallpaper.None -> WallpaperCategory.Colors
        is ChatWallpaper.Photo -> WallpaperCategory.Photo
    }
    var categoryName by rememberSaveable { mutableStateOf(initialCategory.name) }
    val category = WallpaperCategory.entries.firstOrNull { it.name == categoryName } ?: initialCategory
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = WallpaperFiles.import(context, uri)
            if (name != null) {
                val dim = (selected as? ChatWallpaper.Photo)?.dimPercent ?: ChatWallpaper.DEFAULT_DIM_PERCENT
                onDraftChange(draft.copy(wallpaper = ChatWallpaper.Photo(name, dim)))
                categoryName = WallpaperCategory.Photo.name
            } else {
                Toast.makeText(context, R.string.wallpaper_import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val tokens = resolveTokens(draft, systemDark)

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = AppearanceDimens.pagePadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppearanceSectionLabel(stringResource(R.string.theme_preview_title), Modifier.weight(1f))
            TextButton(onClick = onOpenPreview) {
                Icon(Icons.Rounded.Fullscreen, null, modifier = Modifier.size(17.dp))
                Text(stringResource(R.string.appearance_preview_open), Modifier.padding(start = 5.dp), fontSize = 13.sp)
            }
        }
        Box(
            Modifier.fillMaxWidth().clip(AppearanceDimens.cardShape)
                .border(1.dp, palette.divider, AppearanceDimens.cardShape)
                .clickable(role = Role.Button, onClick = onOpenPreview)
        ) {
            AppearanceChatPreview(
                config = draft,
                compact = true,
                animate = false,
                replayKey = 0,
                systemDark = systemDark,
                showComposer = false,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                wallpaperLabel(selected),
                color = palette.textPrimary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (selected != ChatWallpaper.None) {
                TextButton(onClick = { onDraftChange(draft.copy(wallpaper = ChatWallpaper.None)) }) {
                    Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.wallpaper_reset), Modifier.padding(start = 4.dp), fontSize = 12.sp)
                }
            }
        }

        AppearanceChoiceFlow(
            options = WallpaperCategory.entries.map {
                stringResource(
                    when (it) {
                        WallpaperCategory.Patterns -> R.string.appearance_wallpaper_patterns
                        WallpaperCategory.Colors -> R.string.appearance_wallpaper_colors
                        WallpaperCategory.Photo -> R.string.appearance_wallpaper_photo
                    }
                ) to (category == it)
            },
            onSelect = { categoryName = WallpaperCategory.entries[it].name }
        )

        when (category) {
            WallpaperCategory.Patterns -> PatternsSection(
                selected = selected,
                draft = draft,
                tokens = tokens,
                onDraftChange = onDraftChange
            )
            WallpaperCategory.Colors -> ColorsSection(
                selected = selected,
                onSelect = { onDraftChange(draft.copy(wallpaper = it)) }
            )
            WallpaperCategory.Photo -> PhotoSection(
                selected = selected,
                onSelect = { onDraftChange(draft.copy(wallpaper = it)) },
                onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            )
        }
        Text(
            stringResource(R.string.appearance_wallpaper_note),
            color = palette.textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun PatternsSection(
    selected: ChatWallpaper,
    draft: AppearanceConfig,
    tokens: com.beykus.beygram.ui.theme.BeyGramTokens,
    onDraftChange: (AppearanceConfig) -> Unit
) {
    val palette = beyGramPalette()
    val selectedPreset = selected as? ChatWallpaper.Preset
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.wallpaper_curated_title),
            color = palette.textPrimary,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            stringResource(R.string.wallpaper_curated_subtitle),
            color = palette.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        if (selectedPreset != null) {
            AppearanceCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                AppearanceSliderRow(
                    title = stringResource(R.string.appearance_pattern_intensity),
                    valueText = stringResource(R.string.theme_animations_percent_value, selectedPreset.intensityPercent),
                    value = selectedPreset.intensityPercent.toFloat(),
                    range = 0f..ChatWallpaper.MAX_PATTERN_INTENSITY.toFloat()
                ) { value ->
                    onDraftChange(
                        draft.copy(
                            wallpaper = selectedPreset.copy(
                                intensityPercent = value.toInt().coerceIn(0, ChatWallpaper.MAX_PATTERN_INTENSITY)
                            )
                        )
                    )
                }
            }
        }
        WallpaperPresets.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { preset ->
                    PatternTile(
                        preset = preset,
                        tokens = tokens,
                        selected = selectedPreset?.id == preset.id,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        onDraftChange(
                            draft.copy(
                                wallpaper = ChatWallpaper.Preset(
                                    preset.id,
                                    selectedPreset?.intensityPercent ?: ChatWallpaper.DEFAULT_PATTERN_INTENSITY
                                )
                            )
                        )
                    }
                }
                if (row.size == 1) {
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PatternTile(
    preset: WallpaperPresetSpec,
    tokens: com.beykus.beygram.ui.theme.BeyGramTokens,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    val name = stringResource(wallpaperPresetNameRes(preset.id))
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(palette.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) palette.accent else palette.divider, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = name }
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 92.dp).padding(6.dp)
                .clip(RoundedCornerShape(11.dp))
        ) {
            CompositionLocalProvider(LocalBeyGramTokens provides tokens) {
                PatternWallpaper(
                    preset,
                    // A fixed thumbnail intensity keeps the grid cheap while the live value is edited above.
                    ChatWallpaper.DEFAULT_PATTERN_INTENSITY,
                    modifier = Modifier.matchParentSize()
                )
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape)
                        .background(palette.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Check, null, tint = palette.onAccent, modifier = Modifier.size(14.dp))
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        Text(
            name,
            color = if (selected) palette.accent else palette.textPrimary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp)
        )    }
}

@Composable
private fun ColorsSection(selected: ChatWallpaper, onSelect: (ChatWallpaper) -> Unit) {
    val palette = beyGramPalette()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppearanceSectionLabel(stringResource(R.string.appearance_wallpaper_none_section))
        NoneTile(
            selected = selected == ChatWallpaper.None,
            onClick = { onSelect(ChatWallpaper.None) }
        )
        AppearanceSectionLabel(stringResource(R.string.appearance_wallpaper_solid_section))
        WallpaperSolidChoices.chunked(3).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { indexInRow, color ->
                    val description = stringResource(R.string.wallpaper_option_solid, rowIndex * 3 + indexInRow + 1)
                    ColorTile(
                        description = description,
                        selected = selected == ChatWallpaper.Solid(color),
                        brush = Brush.linearGradient(listOf(Color(color), Color(color))),
                        modifier = Modifier.weight(1f)
                    ) { onSelect(ChatWallpaper.Solid(color)) }
                }
                repeat(3 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
            }
        }
        AppearanceSectionLabel(stringResource(R.string.appearance_wallpaper_gradient_section))
        WallpaperGradientChoices.chunked(3).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { indexInRow, (top, bottom) ->
                    val description = stringResource(R.string.wallpaper_option_gradient, rowIndex * 3 + indexInRow + 1)
                    ColorTile(
                        description = description,
                        selected = selected == ChatWallpaper.Gradient(top, bottom),
                        brush = Brush.verticalGradient(listOf(Color(top), Color(bottom))),
                        modifier = Modifier.weight(1f)
                    ) { onSelect(ChatWallpaper.Gradient(top, bottom)) }
                }
                repeat(3 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
            }
        }
        Text(
            stringResource(R.string.appearance_wallpaper_colors_hint),
            color = palette.textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun NoneTile(selected: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) palette.accentSoft else palette.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) palette.accent else palette.divider, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Rounded.Block,
            null,
            tint = if (selected) palette.accentInk else palette.textSecondary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            stringResource(R.string.wallpaper_option_none),
            color = if (selected) palette.accentInk else palette.textPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (selected) Icon(Icons.Rounded.Check, null, tint = palette.accent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ColorTile(
    description: String,
    selected: Boolean,
    brush: Brush,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.aspectRatio(1.25f).clip(shape)
            .background(brush)
            .border(if (selected) 3.dp else 1.dp, if (selected) palette.accent else palette.divider, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description }
    ) {
        if (selected) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape)
                    .background(palette.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Check, null, tint = palette.onAccent, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun PhotoSection(
    selected: ChatWallpaper,
    onSelect: (ChatWallpaper) -> Unit,
    onPick: () -> Unit
) {
    val palette = beyGramPalette()
    val photo = selected as? ChatWallpaper.Photo
    if (photo == null) {
        AppearanceCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 108.dp).clip(AppearanceDimens.innerShape)
                    .background(palette.surface2),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Image, null, tint = palette.accentInk, modifier = Modifier.size(30.dp))
                    Text(
                        stringResource(R.string.appearance_wallpaper_photo_title),
                        color = palette.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.appearance_wallpaper_photo_detail),
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
            Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.appearance_wallpaper_choose_photo))
            }
        }
    } else {
        AppearanceCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Image, null, tint = palette.accentInk, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.wallpaper_option_photo),
                    color = palette.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
            AppearanceSliderRow(
                title = stringResource(R.string.wallpaper_dim),
                valueText = stringResource(R.string.theme_animations_percent_value, photo.dimPercent),
                value = photo.dimPercent.toFloat(),
                range = 0f..ChatWallpaper.MAX_DIM_PERCENT.toFloat()
            ) { value ->
                onSelect(photo.copy(dimPercent = value.toInt().coerceIn(0, ChatWallpaper.MAX_DIM_PERCENT)))
            }
            AppearanceToggleRow(
                title = stringResource(R.string.wallpaper_blur),
                detail = stringResource(R.string.wallpaper_blur_hint),
                checked = photo.blurred,
                onCheckedChange = { onSelect(photo.copy(blurred = it)) }
            )
            Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.appearance_wallpaper_replace_photo))
            }
        }
    }
}
