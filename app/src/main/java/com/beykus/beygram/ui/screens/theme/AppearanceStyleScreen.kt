package com.beykus.beygram.ui.screens.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.data.appearance.NavigationPlacement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.rememberDesign

/** Height of the main preview. It shows a whole phone screen top to bottom without taking the page over. */
private val StylePreviewHeight = 300.dp
private val StyleCardWidth = 112.dp
private val StyleCardPhoneHeight = 168.dp
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
            StyleScenePreview(
                config = draft,
                systemDark = systemDark,
                scene = scene,
                virtualWidth = 380.dp,
                modifier = Modifier.fillMaxWidth().height(StylePreviewHeight)
                    .clip(AppearanceDimens.innerShape)
                    // The outline keeps the edge of the screen visible on AMOLED, where both are black.
                    .border(1.dp, palette.divider, AppearanceDimens.innerShape)
                    .clearAndSetSemantics { contentDescription = previewDescription }
            )
            AppearanceChoiceFlow(
                options = StyleScene.entries.map { stringResource(it.labelRes) to (it == scene) },
                onSelect = { scene = StyleScene.entries[it] }
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
            Column(Modifier.selectableGroup()) {
                NavigationPlacement.entries.forEach { placement ->
                    NavigationPlacementRow(
                        placement = placement,
                        selected = draft.navigationPlacement == placement,
                        onSelect = { onDraftChange(draft.copy(navigationPlacement = placement)) }
                    )
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
 * Says what the glass in the preview is. The preview never samples a backdrop, so it does not pretend to blur; when
 * the device or the chosen quality draws glass without blur anyway, the note says the preview is exactly that.
 */
@Composable
private fun GlassPreviewNote(draft: AppearanceConfig) {
    if (draft.designStyle != DesignStyle.LiquidGlass) return
    val design = rememberDesign(draft)
    val blurs = design.surfaces.glass?.samplesBackdrop == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Text(
        stringResource(if (blurs) R.string.style_preview_glass_note else R.string.style_preview_glass_fallback_note),
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
    val phoneShape = AppearanceDimens.innerShape
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
        Box(Modifier.fillMaxWidth().height(StyleCardPhoneHeight)) {
            StyleScenePreview(
                config = config.copy(designStyle = style),
                systemDark = systemDark,
                scene = StyleScene.Chats,
                modifier = Modifier.fillMaxSize()
                    .clip(phoneShape)
                    .border(1.dp, palette.divider, phoneShape)
                    .clearAndSetSemantics {}
            )
        }
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
 * One placement of the bottom bar. It is a separate axis from the style, so the row explains the position rather than
 * pretending to be another look; Auto stays the compatibility choice for installations that never touched it.
 */
@Composable
private fun NavigationPlacementRow(
    placement: NavigationPlacement,
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
        NavigationPlacementGlyph(placement = placement, selected = selected)
    }
}

/** A two-line sketch of the placement: the bar either sits on the bottom edge or floats above it with margins. */
@Composable
private fun NavigationPlacementGlyph(placement: NavigationPlacement, selected: Boolean) {
    val palette = beyGramPalette()
    val accent = if (selected) palette.accent else palette.textSecondary
    val floating = when (placement) {
        // Auto is not a placement of its own: the sketch shows both, so it never looks like a forced dock.
        NavigationPlacement.Auto -> true
        NavigationPlacement.Pinned -> false
        NavigationPlacement.Floating -> true
    }
    Box(Modifier.width(30.dp).height(22.dp).clip(AppearanceDimens.innerShape)) {
        Box(Modifier.fillMaxSize().background(palette.surface2.copy(alpha = 0.55f)))
        if (placement == NavigationPlacement.Auto) {
            // The pinned variant stays visible as a hairline next to the capsule.
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp)
                    .background(accent.copy(alpha = 0.45f))
            )
        }
        Box(
            Modifier.align(Alignment.BottomCenter)
                .padding(start = if (floating) 5.dp else 0.dp, end = if (floating) 5.dp else 0.dp)
                .padding(bottom = if (floating) 3.dp else 0.dp)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(if (floating) 3.dp else 0.dp))
                .background(accent)
        )
    }
}
