package com.beykus.beygram.ui.screens.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.DesignSystem
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.NavigationVariant
import com.beykus.beygram.ui.design.rememberDesign
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.beykus.beygram.ui.theme.resolveTokens

private val StyleThumbnailSize = 96.dp

/**
 * The style selector. Each option draws a real miniature of that style with the user's current palette and brightness,
 * so the choice is made on what the interface actually looks like rather than on a name.
 */
@Composable
internal fun AppearanceStylePage(
    draft: AppearanceConfig,
    systemDark: Boolean,
    onDraftChange: (AppearanceConfig) -> Unit,
    onOpenPreview: () -> Unit
) {
    val palette = beyGramPalette()
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = AppearanceDimens.pagePadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppearanceSectionLabel(stringResource(R.string.theme_preview_title), Modifier.weight(1f))
            TextButton(onClick = onOpenPreview) {
                Icon(Icons.Rounded.Fullscreen, null, modifier = Modifier.size(17.dp))
                Text(
                    stringResource(R.string.appearance_preview_open),
                    modifier = Modifier.padding(start = 5.dp),
                    fontSize = 13.sp
                )
            }
        }
        AppearanceCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
            Box(Modifier.fillMaxWidth().height(150.dp).clip(AppearanceDimens.innerShape)) {
                StylePreviewSurface(draft, systemDark)
            }
        }

        AppearanceSectionLabel(stringResource(R.string.appearance_section_style))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DesignStyle.entries.forEach { style ->
                StyleOptionRow(
                    style = style,
                    config = draft,
                    systemDark = systemDark,
                    selected = draft.designStyle == style,
                    onSelect = { onDraftChange(draft.copy(designStyle = style)) }
                )
            }
        }

        if (draft.designStyle == DesignStyle.LiquidGlass) {
            AppearanceSectionLabel(stringResource(R.string.appearance_style_quality_section))
            AppearanceCard {
                Text(
                    stringResource(R.string.appearance_style_quality_detail),
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                GlassQuality.entries.forEach { quality ->
                    QualityOptionRow(
                        quality = quality,
                        selected = draft.glassQuality == quality,
                        onSelect = { onDraftChange(draft.copy(glassQuality = quality)) }
                    )
                }
            }
        }

        Text(
            stringResource(R.string.appearance_style_note),
            color = palette.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun StyleOptionRow(
    style: DesignStyle,
    config: AppearanceConfig,
    systemDark: Boolean,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val palette = beyGramPalette()
    val shape = RoundedCornerShape(LocalDesignSystem.current.shapes.card)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(if (selected) palette.accentSoft else palette.surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) palette.accent else palette.divider, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics(mergeDescendants = true) {}
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StyleThumbnail(style = style, config = config, systemDark = systemDark)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                stringResource(style.labelRes()),
                color = if (selected) palette.accentInk else palette.textPrimary,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(style.descriptionRes()),
                color = palette.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 3
            )
        }
        Box(
            Modifier.size(20.dp).clip(CircleShape)
                .border(2.dp, if (selected) palette.accent else palette.divider, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(palette.accent))
        }
    }
}

@Composable
private fun QualityOptionRow(quality: GlassQuality, selected: Boolean, onSelect: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth()
            .clip(AppearanceDimens.innerShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(18.dp).clip(CircleShape)
                .border(2.dp, if (selected) palette.accent else palette.divider, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Box(Modifier.size(9.dp).clip(CircleShape).background(palette.accent))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                stringResource(quality.labelRes()),
                color = if (selected) palette.accentInk else palette.textPrimary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
            Text(stringResource(quality.detailRes()), color = palette.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

/** The thumbnail of one style, drawn by that style's own tokens with the palette the user already has. */
@Composable
private fun StyleThumbnail(style: DesignStyle, config: AppearanceConfig, systemDark: Boolean) {
    val design = rememberDesign(style, config.glassQuality)
    val tokens = resolveTokens(config.copy(designStyle = style), systemDark)
    BeyGramTheme(
        darkTheme = config.mode.isDark(systemDark),
        tokens = tokens,
        design = design
    ) {
        Box(
            Modifier.width(StyleThumbnailSize).height(64.dp)
                .clip(RoundedCornerShape(design.shapes.small))
                .background(beyGramPalette().screen)
        ) {
            StyleMiniature(design, compact = true)
        }
    }
}

/** A larger rendition of the selected style: header, two bubbles and the navigation it implies. */
@Composable
private fun StylePreviewSurface(config: AppearanceConfig, systemDark: Boolean) {
    val design = rememberDesign(config.designStyle, config.glassQuality)
    val tokens = resolveTokens(config, systemDark)
    BeyGramTheme(darkTheme = config.mode.isDark(systemDark), tokens = tokens, design = design) {
        Box(Modifier.fillMaxSize().background(beyGramPalette().screen)) {
            StyleMiniature(design, compact = false)
        }
    }
}

@Composable
private fun StyleMiniature(design: DesignSystem, compact: Boolean) {
    val palette = beyGramPalette()
    val pad = if (compact) 6.dp else 12.dp
    val bubbleHeight = if (compact) 9.dp else 18.dp
    val bubbleShape = RoundedCornerShape(design.shapes.small)
    Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(if (compact) 10.dp else 18.dp).clip(CircleShape).background(palette.accentSoft))
            Box(
                Modifier.width(if (compact) 26.dp else 48.dp).height(if (compact) 4.dp else 6.dp)
                    .clip(CircleShape).background(palette.surface2)
            )
            Spacer(Modifier.weight(1f))
            if (!compact) {
                Box(
                    Modifier.width(40.dp).height(14.dp).clip(RoundedCornerShape(design.shapes.chip))
                        .background(palette.surface2)
                )
            }
        }
        Box(
            Modifier.fillMaxWidth(0.68f).height(bubbleHeight)
                .clip(bubbleShape).background(palette.incomingBubble)
                .border(if (design.surfaces.treatment == com.beykus.beygram.ui.design.SurfaceTreatment.Elevated) 1.dp else 0.dp, palette.divider, bubbleShape)
        )
        Box(
            Modifier.fillMaxWidth(0.55f).height(bubbleHeight).align(Alignment.End)
                .clip(bubbleShape).background(palette.outgoingBubble)
        )
        if (!compact) {
            Box(
                Modifier.fillMaxWidth(0.4f).height(bubbleHeight)
                    .clip(bubbleShape).background(palette.incomingBubble)
            )
        }
        Spacer(Modifier.weight(1f))
        StyleNavigationStrip(design, compact)
    }
}

/** The navigation shape of the style, so the miniature shows the structural difference and not only the corners. */
@Composable
private fun StyleNavigationStrip(design: DesignSystem, compact: Boolean) {
    val palette = beyGramPalette()
    val height = if (compact) 12.dp else 22.dp
    val indicator = RoundedCornerShape(design.shapes.navigationIndicator)
    val dotCount = 4
    val dock = design.components.navigation == NavigationVariant.FloatingGlass ||
        design.components.navigation == NavigationVariant.SignatureDock
    Row(
        Modifier.fillMaxWidth().height(height)
            .clip(if (dock) RoundedCornerShape(design.shapes.extraSmall) else RoundedCornerShape(0.dp))
            .background(if (dock) palette.surface2 else palette.surface)
            .then(
                if (design.components.navigation == NavigationVariant.ClassicTabs) {
                    Modifier.border(0.5.dp, palette.divider, RoundedCornerShape(0.dp))
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        repeat(dotCount) { index ->
            val selected = index == 0 && design.components.navigation != NavigationVariant.ClassicTabs
            Box(
                Modifier.size(width = if (compact) 12.dp else 22.dp, height = if (compact) 6.dp else 12.dp)
                    .clip(indicator)
                    .background(if (selected) palette.accentSoft else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(if (compact) 3.dp else 5.dp).clip(CircleShape)
                        .background(if (selected) palette.accentInk else palette.textSecondary)
                )
            }
        }
    }
}
