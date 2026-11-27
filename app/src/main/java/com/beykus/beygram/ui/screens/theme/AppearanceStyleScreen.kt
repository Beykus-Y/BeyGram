package com.beykus.beygram.ui.screens.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.beykus.beygram.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.ChatTopPanelLayout
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.data.appearance.NavigationPlacement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.designFor
import com.beykus.beygram.ui.design.recordsGlassBackdrop
import com.beykus.beygram.ui.design.rememberDesign
import com.beykus.beygram.data.appearance.LiquidGlassLensPreset
import androidx.compose.ui.graphics.Color

/**
 * The main preview is a whole phone screen. Its height is this share of the device's screen height, kept between the
 * two bounds: small enough that the page still shows the style cards below it, large enough to read the scene.
 */
private const val StylePreviewShareOfScreen = 0.7f
private val StylePreviewMinHeight = 440.dp
private val StylePreviewMaxHeight = 640.dp
private val StyleCardWidth = 112.dp
/** The selected mark is a circle of this size in its own strip above the picture. */
private val StyleCardMarkSize = 20.dp
/** Two caption lines at their 17sp line height (the caption text itself is 13sp). */
private val StyleCardCaptionHeight = 34.dp

/**
 * The style selector. The main preview draws one whole screen of the app (chats, a conversation or settings) in the
 * draft's style, palette, brightness and bar placement; below it each style is a small phone with the same chat list,
 * so the five options differ by header, search, folders, list rows, create button and bottom bar at a glance.
 * Every picture is built from the real components and tokens and synthetic text only.
 */
@Composable
internal fun AppearanceStylePage(
    draft: AppearanceConfig,
    systemDark: Boolean,
    onDraftChange: (AppearanceConfig) -> Unit,
    onOpenPreview: () -> Unit
) {
    val palette = beyGramPalette()
    var scene by rememberSaveable { mutableStateOf(StyleScene.Chats) }
    val previewHeight = (LocalConfiguration.current.screenHeightDp * StylePreviewShareOfScreen).dp
        .coerceIn(StylePreviewMinHeight, StylePreviewMaxHeight)
    val styleLabel = stringResource(draft.designStyle.labelRes())
    val sceneLabel = stringResource(scene.labelRes)
    val previewDescription = stringResource(R.string.style_preview_description, styleLabel, sceneLabel)
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = AppearanceDimens.pagePadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
        AppearanceCard(contentPadding = PaddingValues(10.dp)) {
            // The scene choice comes first, so the three screens are one tap away above the phone.
            AppearanceChoiceFlow(
                options = StyleScene.entries.map { stringResource(it.labelRes) to (it == scene) },
                onSelect = { scene = StyleScene.entries[it] }
            )
            // The whole phone. Its outline is drawn around the screen itself, so it holds at any width.
            StyleScenePreview(
                config = draft,
                systemDark = systemDark,
                scene = scene,
                modifier = Modifier.fillMaxWidth().height(previewHeight)
                    .clearAndSetSemantics { contentDescription = previewDescription }
            )
            GlassPreviewNote(draft)
        }

        AppearanceSectionLabel(stringResource(R.string.appearance_section_style))
        val selectedIndex = DesignStyle.entries.indexOf(draft.designStyle).coerceAtLeast(0)
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 1).coerceAtLeast(0))
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(DesignStyle.entries, key = { it.name }) { style ->
                StyleCard(
                    style = style,
                    config = draft,
                    systemDark = systemDark,
                    selected = draft.designStyle == style,
                    onSelect = { onDraftChange(draft.copy(designStyle = style)) }
                )
            }
        }
        Text(
            stringResource(draft.designStyle.descriptionRes()),
            color = palette.textSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        AppearanceSectionLabel(stringResource(R.string.appearance_navigation_placement_section))
        AppearanceCard {
            Text(
                stringResource(R.string.appearance_navigation_placement_detail),
                color = palette.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            // What the style asks for on its own: the Auto row sketches the placement the draft really resolves to.
            val naturalFloating = remember(draft.designStyle, draft.glassQuality) {
                designFor(draft.designStyle, draft.glassQuality).navigationFloating
            }
            Column(Modifier.selectableGroup()) {
                NavigationPlacement.entries.forEach { placement ->
                    NavigationPlacementRow(
                        placement = placement,
                        naturalFloating = naturalFloating,
                        selected = draft.navigationPlacement == placement,
                        onSelect = { onDraftChange(draft.copy(navigationPlacement = placement)) }
                    )
                }
            }
        }

        AppearanceSectionLabel(stringResource(R.string.appearance_chat_top_panels))
        AppearanceCard {
            Column(Modifier.selectableGroup()) {
                ChatTopPanelLayout.entries.forEach { mode ->
                    val selected = mode == draft.chatTopPanelLayout
                    val label = when (mode) {
                        ChatTopPanelLayout.Current -> R.string.appearance_chat_top_panels_current
                        ChatTopPanelLayout.Separate -> R.string.appearance_chat_top_panels_separate
                        ChatTopPanelLayout.Combined -> R.string.appearance_chat_top_panels_combined
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(AppearanceDimens.innerShape)
                            .selectable(selected, role = Role.RadioButton) {
                                onDraftChange(draft.copy(chatTopPanelLayout = mode))
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier.size(18.dp)
                                .border(2.dp, if (selected) palette.accent else palette.divider, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) Box(Modifier.size(9.dp).clip(CircleShape).background(palette.accent))
                        }
                        Text(stringResource(label), color = palette.textPrimary, fontSize = 14.sp)
                    }
                }
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
                Column(Modifier.selectableGroup()) {
                    GlassQuality.entries.forEach { quality ->
                        QualityOptionRow(
                            quality = quality,
                            selected = draft.glassQuality == quality,
                            onSelect = { onDraftChange(draft.copy(glassQuality = quality)) }
                        )
                    }
                }
                // The lens needs API 33+ and only refracts the High level (Auto resolves to High), so below
                // those the switch would do nothing and is not offered.
                val lensApplies = draft.glassQuality == GlassQuality.High || draft.glassQuality == GlassQuality.Auto
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && lensApplies) {
                    LiquidGlassV2Row(
                        enabled = draft.liquidGlassV2,
                        onChange = { onDraftChange(draft.copy(liquidGlassV2 = it)) }
                    )
                    if (draft.liquidGlassV2) LiquidGlassPresetRow(
                        selected = draft.liquidGlassPreset,
                        onSelect = { onDraftChange(draft.copy(liquidGlassPreset = it)) }
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

/**
 * Says what the glass in the preview is when the device draws it without blur. The preview samples the same backdrop
 * the screens do, so on a device that blurs there is nothing to explain.
 */
@Composable
private fun GlassPreviewNote(draft: AppearanceConfig) {
    if (draft.designStyle != DesignStyle.LiquidGlass) return
    if (rememberDesign(draft).recordsGlassBackdrop) return
    Text(
        stringResource(R.string.style_preview_glass_fallback_note),
        color = beyGramPalette().textSecondary,
        fontSize = 12.sp,
        lineHeight = 17.sp
    )
}

/**
 * One style as a small phone with the chat list in that style. The card is the only selectable element: the picture
 * is hidden from TalkBack, so the option is announced once, with its name and one selected state.
 */
@Composable
private fun StyleCard(
    style: DesignStyle,
    config: AppearanceConfig,
    systemDark: Boolean,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val palette = beyGramPalette()
    val shape = AppearanceDimens.cardShape
    Column(
        Modifier.width(StyleCardWidth)
            .clip(shape)
            .background(if (selected) palette.accentSoft else palette.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) palette.accent else palette.divider, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // The selected mark has a strip of its own above the picture, at the end, so it never covers the thumbnail.
        Box(Modifier.fillMaxWidth().height(StyleCardMarkSize), contentAlignment = Alignment.CenterEnd) {
            if (selected) {
                Box(
                    Modifier.size(StyleCardMarkSize).clip(CircleShape).background(palette.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Check, null, tint = palette.onAccent, modifier = Modifier.size(13.dp))
                }
            }
        }
        // The thumbnail is the whole phone as well, in the same proportions as the main preview.
        StyleScenePreview(
            config = config.copy(designStyle = style),
            systemDark = systemDark,
            scene = StyleScene.Chats,
            modifier = Modifier.fillMaxWidth().aspectRatio(PhoneAspect).clearAndSetSemantics {}
        )
        // Two lines of caption are always reserved, so the card keeps its height; a longer name is cut with an ellipsis.
        Text(
            stringResource(style.labelRes()),
            color = if (selected) palette.accentInk else palette.textPrimary,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().heightIn(min = StyleCardCaptionHeight).padding(bottom = 2.dp)
        )
    }
}

/** The lens variant under the V2 switch: three choices, each shown with its label. */
@Composable
private fun LiquidGlassPresetRow(selected: LiquidGlassLensPreset, onSelect: (LiquidGlassLensPreset) -> Unit) {
    val palette = beyGramPalette()
    val shape = AppearanceDimens.innerShape
    Column(
        Modifier.fillMaxWidth().padding(start = 28.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            stringResource(R.string.appearance_style_lens_title),
            color = palette.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Text(
            stringResource(R.string.appearance_style_lens_detail),
            color = palette.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LiquidGlassLensPreset.entries.forEach { preset ->
                val isSelected = preset == selected
                val label = when (preset) {
                    LiquidGlassLensPreset.Standard -> stringResource(R.string.appearance_style_lens_standard)
                    LiquidGlassLensPreset.Clear -> stringResource(R.string.appearance_style_lens_clear)
                    LiquidGlassLensPreset.Bold -> stringResource(R.string.appearance_style_lens_bold)
                }
                Box(
                    Modifier.weight(1f)
                        .clip(shape)
                        .background(if (isSelected) palette.accent else Color.Transparent)
                        .border(1.dp, if (isSelected) palette.accent else palette.divider, shape)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(preset) })
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (isSelected) palette.accentInk else palette.textPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Experimental Liquid Glass V2 lens switch. Off by default; the glass above it stays the fallback. */
@Composable
private fun LiquidGlassV2Row(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val palette = beyGramPalette()
    val boxShape = RoundedCornerShape(4.dp)
    Row(
        Modifier.fillMaxWidth()
            .clip(AppearanceDimens.innerShape)
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(18.dp).clip(boxShape)
                .border(2.dp, if (enabled) palette.accent else palette.divider, boxShape),
            contentAlignment = Alignment.Center
        ) {
            if (enabled) Box(Modifier.size(9.dp).clip(CircleShape).background(palette.accent))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                stringResource(R.string.appearance_style_v2_title),
                color = if (enabled) palette.accentInk else palette.textPrimary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Medium
            )
            Text(
                stringResource(R.string.appearance_style_v2_detail),
                color = palette.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
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
            // The saved High choice stays as it is, but the row says what the device really draws instead of
            // promising a blur that Android below 12 cannot render.
            val detail = if (quality == GlassQuality.High && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                R.string.appearance_style_quality_high_unavailable
            } else {
                quality.detailRes()
            }
            Text(stringResource(detail), color = palette.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

/**
 * One placement of the panels. It is a separate axis from the style, so the row explains the position rather than
 * pretending to be another look; Auto stays the compatibility choice for installations that never touched it.
 */
@Composable
private fun NavigationPlacementRow(
    placement: NavigationPlacement,
    naturalFloating: Boolean,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth()
            .clip(AppearanceDimens.innerShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics(mergeDescendants = true) {}
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
                stringResource(placement.labelRes()),
                color = if (selected) palette.accentInk else palette.textPrimary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
            Text(
                stringResource(placement.detailRes()),
                color = palette.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
        NavigationPlacementGlyph(placement = placement, naturalFloating = naturalFloating, selected = selected)
    }
}

/**
 * A sketch of the two panels the placement governs: the pinned bar under a header, and the bottom dock. The bars sit
 * on the edges when attached, and float with margins and rounded corners when not; the Auto row draws the placement
 * the draft's style actually resolves to, so it never reads as a forced dock.
 */
@Composable
private fun NavigationPlacementGlyph(placement: NavigationPlacement, naturalFloating: Boolean, selected: Boolean) {
    val palette = beyGramPalette()
    val accent = if (selected) palette.accent else palette.textSecondary
    val quiet = accent.copy(alpha = 0.25f)
    val floating = when (placement) {
        NavigationPlacement.Auto -> naturalFloating
        NavigationPlacement.Pinned -> false
        NavigationPlacement.Floating -> true
    }
    Column(
        Modifier.width(44.dp).height(34.dp)
            .clip(AppearanceDimens.innerShape)
            .background(palette.surface2.copy(alpha = 0.55f))
            .padding(3.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(1.dp)).background(quiet))
        // The pinned bar: attached it touches the header without a seam, floating it keeps air around it.
        if (floating) {
            Box(
                Modifier.padding(top = 2.dp).padding(horizontal = 3.dp).fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(accent.copy(alpha = 0.45f))
            )
        } else {
            Box(Modifier.padding(top = 1.dp).fillMaxWidth().height(4.dp).background(accent.copy(alpha = 0.45f)))
        }
        Box(Modifier.padding(top = 2.dp).fillMaxWidth(0.7f).height(2.dp).clip(RoundedCornerShape(1.dp)).background(quiet))
        Box(Modifier.fillMaxWidth().weight(1f))
        // The dock on the bottom edge.
        if (floating) {
            Box(
                Modifier.padding(horizontal = 3.dp).fillMaxWidth().height(6.dp)
                    .clip(RoundedCornerShape(3.dp)).background(accent)
            )
        } else {
            Box(Modifier.fillMaxWidth().height(6.dp).background(accent))
        }
    }
}
