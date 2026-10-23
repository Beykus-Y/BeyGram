package com.beykus.beygram.ui.screens.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.ThemeMode
import com.beykus.beygram.data.appearance.ThemePreset
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.ChatWallpaperLayer
import com.beykus.beygram.ui.screens.chat.LocalBubbleMetrics
import com.beykus.beygram.ui.screens.chat.bubbleShape
import com.beykus.beygram.ui.theme.SystemColorsAvailable
import com.beykus.beygram.ui.theme.resolveTokens

@Composable
internal fun AppearanceColorsPage(
    draft: AppearanceConfig,
    systemDark: Boolean,
    onDraftChange: (AppearanceConfig) -> Unit,
    onOpenCustomColors: () -> Unit,
    onOpenPreview: () -> Unit,
    onResetAll: () -> Unit
) {
    val palette = beyGramPalette()
    val effectivePreset = draft.preset.takeUnless { it == ThemePreset.MaterialYou && !SystemColorsAvailable }
        ?: ThemePreset.Warm
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
        AppearancePaletteSample(draft, systemDark, onOpenPreview)

        AppearanceSectionLabel(stringResource(R.string.theme_palette_title))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PresetChoice(
                preset = ThemePreset.Warm,
                mode = draft.mode,
                systemDark = systemDark,
                selected = effectivePreset == ThemePreset.Warm,
                onClick = { onDraftChange(draft.copy(preset = ThemePreset.Warm)) }
            )
            PresetChoice(
                preset = ThemePreset.Graphite,
                mode = draft.mode,
                systemDark = systemDark,
                selected = effectivePreset == ThemePreset.Graphite,
                onClick = { onDraftChange(draft.copy(preset = ThemePreset.Graphite)) }
            )
            if (SystemColorsAvailable) {
                PresetChoice(
                    preset = ThemePreset.MaterialYou,
                    mode = draft.mode,
                    systemDark = systemDark,
                    selected = effectivePreset == ThemePreset.MaterialYou,
                    onClick = { onDraftChange(draft.copy(preset = ThemePreset.MaterialYou)) }
                )
            }
        }

        AppearanceSectionLabel(stringResource(R.string.theme_brightness_title))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrightnessChoice(
                    mode = ThemeMode.FollowSystem,
                    selected = draft.mode == ThemeMode.FollowSystem,
                    icon = Icons.Rounded.BrightnessAuto,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) { onDraftChange(draft.copy(mode = ThemeMode.FollowSystem)) }
                BrightnessChoice(
                    mode = ThemeMode.Light,
                    selected = draft.mode == ThemeMode.Light,
                    icon = Icons.Rounded.LightMode,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) { onDraftChange(draft.copy(mode = ThemeMode.Light)) }
            }
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrightnessChoice(
                    mode = ThemeMode.Dark,
                    selected = draft.mode == ThemeMode.Dark,
                    icon = Icons.Rounded.DarkMode,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) { onDraftChange(draft.copy(mode = ThemeMode.Dark)) }
                BrightnessChoice(
                    mode = ThemeMode.Amoled,
                    selected = draft.mode == ThemeMode.Amoled,
                    icon = Icons.Rounded.Contrast,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) { onDraftChange(draft.copy(mode = ThemeMode.Amoled)) }
            }
        }

        AppearanceSectionLabel(stringResource(R.string.appearance_section_advanced))
        AppearanceNavCard(
            title = stringResource(R.string.appearance_custom_colors_title),
            summary = stringResource(R.string.appearance_custom_colors_summary_detail),
            onClick = onOpenCustomColors,
            trailingIcon = Icons.Rounded.Tune,
            leading = { AppearanceIconTile(Icons.Rounded.Tune) }
        )

        TextButton(onClick = onResetAll, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.appearance_reset_all), color = palette.danger, fontSize = 13.sp)
        }
        Text(
            stringResource(R.string.appearance_colors_note),
            color = palette.textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun PresetChoice(
    preset: ThemePreset,
    mode: ThemeMode,
    systemDark: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    val swatch = resolveTokens(preset, mode, systemDark)
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) palette.accentSoft else palette.surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) palette.accent else palette.divider, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(preset.labelRes()),
                color = if (selected) palette.accentInk else palette.textPrimary,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
            Text(stringResource(preset.descriptionRes()), color = palette.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            PaletteSwatch(swatch.bg)
            PaletteSwatch(swatch.surface2)
            PaletteSwatch(swatch.accent)
        }
    }
}

@Composable
private fun BrightnessChoice(
    mode: ThemeMode,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AppearanceOptionTile(
        title = stringResource(mode.labelRes()),
        detail = mode.detailRes()?.let { stringResource(it) },
        selected = selected,
        onClick = onClick,
        icon = icon,
        modifier = modifier
    )
}

@Composable
private fun PaletteSwatch(color: Color) {
    val palette = beyGramPalette()
    Box(Modifier.size(20.dp).clip(CircleShape).background(color).border(1.dp, palette.divider, CircleShape))
}

/** A very small sample: the palette, bubble colours and wallpaper update without repeating the whole chat. */
@Composable
private fun AppearancePaletteSample(
    draft: AppearanceConfig,
    systemDark: Boolean,
    onClick: () -> Unit
) {
    AppearanceThemeScope(draft, systemDark) {
        val palette = beyGramPalette()
        val metrics = LocalBubbleMetrics.current
        Box(
            Modifier.fillMaxWidth().heightIn(min = 112.dp).clip(RoundedCornerShape(18.dp))
                .background(palette.screen)
                .clickable(role = Role.Button, onClick = onClick)
        ) {
            ChatWallpaperLayer(draft.wallpaper, modifier = Modifier.matchParentSize())
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier.clip(bubbleShape(outgoing = false, metrics)).background(palette.incomingBubble)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.theme_preview_incoming), color = palette.messageText, fontSize = 12.sp, lineHeight = 16.sp)
                }
                Box(
                    Modifier.align(Alignment.End).clip(bubbleShape(outgoing = true, metrics))
                        .background(palette.outgoingBubble).padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.theme_preview_outgoing), color = palette.messageText, fontSize = 12.sp, lineHeight = 16.sp)
                }
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).height(28.dp).clip(CircleShape).background(palette.surface2)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            stringResource(R.string.wallpaper_preview_input),
                            color = palette.textSecondary,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                    Box(
                        Modifier.padding(start = 8.dp).size(28.dp).clip(CircleShape).background(palette.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            null,
                            tint = palette.onAccent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
