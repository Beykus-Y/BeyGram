package com.beykus.beygram.ui.screens.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.theme.ThemeContrastIssues

/** Colours offered for the accent and for outgoing bubbles. */
private val AccentChoices = listOf(
    0xFF6A4FDC, 0xFF3B5BDB, 0xFF1C7ED6, 0xFF0F9D8A,
    0xFF2F9E44, 0xFFE8890C, 0xFFD6336C, 0xFFC2410C
).map { it.toInt() }
private val BubbleChoices = listOf(
    0xFFE6DEFD, 0xFFDCE4F8, 0xFFD3EFFA, 0xFFD5F2E6,
    0xFFDDF3C9, 0xFFFFE8C2, 0xFFFAD9E6, 0xFF2E2952
).map { it.toInt() }

private val AccentNames = listOf(
    R.string.theme_swatch_violet, R.string.theme_swatch_blue, R.string.theme_swatch_sky,
    R.string.theme_swatch_teal, R.string.theme_swatch_green, R.string.theme_swatch_orange,
    R.string.theme_swatch_pink, R.string.theme_swatch_terracotta
)
private val BubbleNames = listOf(
    R.string.theme_swatch_lavender, R.string.theme_swatch_ice_blue, R.string.theme_swatch_cyan,
    R.string.theme_swatch_mint, R.string.theme_swatch_pale_green, R.string.theme_swatch_peach,
    R.string.theme_swatch_blush, R.string.theme_swatch_dark_violet
)
private val LightTextChoices = listOf(0xFF1A1714, 0xFF4B33B5).map { it.toInt() }
private val LightTextNames = listOf(R.string.theme_editor_colour_ink, R.string.theme_editor_colour_indigo)
private val DarkTextChoices = listOf(0xFFFFFFFF.toInt(), 0xFFCFC4FF.toInt())
private val DarkTextNames = listOf(R.string.theme_editor_colour_white, R.string.theme_editor_colour_lavender)
private val LightTimeChoices = listOf(0xFF67625B, 0xFF4B33B5, 0xFF1A1714).map { it.toInt() }
private val LightTimeNames = listOf(
    R.string.theme_editor_colour_grey, R.string.theme_editor_colour_indigo, R.string.theme_editor_colour_ink
)
private val DarkTimeChoices = listOf(0xFFC9C2BB.toInt(), 0xFFCFC4FF.toInt(), 0xFFFFFFFF.toInt())
private val DarkTimeNames = listOf(
    R.string.theme_editor_colour_light_grey, R.string.theme_editor_colour_lavender, R.string.theme_editor_colour_white
)

@Composable
internal fun AppearanceCustomColorsPage(
    draft: AppearanceConfig,
    systemDark: Boolean,
    issues: ThemeContrastIssues,
    onDraftChange: (AppearanceConfig) -> Unit,
    onOpenPreview: () -> Unit
) {
    val palette = beyGramPalette()
    val dark = draft.mode.isDark(systemDark)
    var confirmReset by rememberSaveable { mutableStateOf(false) }
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

        if (issues.any) {
            ContrastWarning(issues)
        }

        AppearanceSectionLabel(stringResource(R.string.theme_editor_accent))
        ColorChoiceCard(
            colors = AccentChoices,
            labelResources = AccentNames,
            selected = draft.accentColor,
            defaultLabel = stringResource(R.string.theme_editor_default)
        ) { onDraftChange(draft.copy(accentColor = it)) }

        AppearanceSectionLabel(stringResource(R.string.theme_editor_bubble_color))
        ColorChoiceCard(
            colors = BubbleChoices,
            labelResources = BubbleNames,
            selected = draft.outgoingBubbleColor,
            defaultLabel = stringResource(R.string.theme_editor_default)
        ) { onDraftChange(draft.copy(outgoingBubbleColor = it)) }

        AppearanceSectionLabel(stringResource(R.string.theme_editor_message_text))
        ColorChoiceCard(
            colors = if (dark) DarkTextChoices else LightTextChoices,
            labelResources = if (dark) DarkTextNames else LightTextNames,
            selected = draft.messageTextColor,
            defaultLabel = stringResource(R.string.theme_editor_default)
        ) { onDraftChange(draft.copy(messageTextColor = it)) }

        AppearanceSectionLabel(stringResource(R.string.theme_editor_message_time))
        ColorChoiceCard(
            colors = if (dark) DarkTimeChoices else LightTimeChoices,
            labelResources = if (dark) DarkTimeNames else LightTimeNames,
            selected = draft.messageTimeColor,
            defaultLabel = stringResource(R.string.theme_editor_default)
        ) { onDraftChange(draft.copy(messageTimeColor = it)) }

        TextButton(onClick = { confirmReset = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.appearance_reset_custom_colors), color = palette.danger, fontSize = 13.sp)
        }
        Text(
            stringResource(R.string.appearance_custom_colors_note),
            color = palette.textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
    if (confirmReset) AppearanceResetDialog(
        title = stringResource(R.string.appearance_reset_custom_colors_title),
        message = stringResource(R.string.appearance_reset_custom_colors_message),
        onConfirm = {
            confirmReset = false
            onDraftChange(draft.resetCustomColors())
        },
        onDismiss = { confirmReset = false }
    )
}

@Composable
private fun ContrastWarning(issues: ThemeContrastIssues) {
    val palette = beyGramPalette()
    val text = stringResource(
        when {
            issues.bubbleText -> R.string.theme_editor_warning_bubble
            issues.messageColour -> R.string.theme_editor_warning_message_colour
            else -> R.string.theme_editor_warning_accent
        }
    )
    Row(
        Modifier.fillMaxWidth().clip(AppearanceDimens.innerShape).background(palette.warningSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Rounded.WarningAmber, null, tint = palette.warningText, modifier = Modifier.size(18.dp))
        Text(text, color = palette.warningText, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorChoiceCard(
    colors: List<Int>,
    labelResources: List<Int>,
    selected: Int?,
    defaultLabel: String,
    onPick: (Int?) -> Unit
) {
    require(colors.size == labelResources.size)
    AppearanceCard {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            colors.forEachIndexed { index, color ->
                ColorSwatch(
                    color = color,
                    label = stringResource(labelResources[index]),
                    selected = selected == color
                ) { onPick(color) }
            }
            // A colour picked for the opposite brightness can stay stored after the theme changes. Show it so the
            // selected state never becomes invisible and the contrast warning has a visible source.
            if (selected != null && selected !in colors) {
                ColorSwatch(
                    color = selected,
                    label = stringResource(R.string.appearance_current_color),
                    selected = true
                ) { onPick(selected) }
            }
        }
        AppearanceChoiceChip(
            text = defaultLabel,
            selected = selected == null,
            onClick = { onPick(null) }
        )
    }
}

/** A 48dp hit target around a 40dp colour; the selected state is visible without relying on colour alone. */
@Composable
internal fun ColorSwatch(color: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Box(
        Modifier.size(48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(if (selected) 42.dp else 40.dp).clip(CircleShape).background(Color(color))
                .border(
                    if (selected) 3.dp else 1.dp,
                    if (selected) palette.accent else palette.divider,
                    CircleShape
                )
        )
        if (selected) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape).background(palette.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Check, null, tint = palette.onAccent, modifier = Modifier.size(12.dp))
            }
        }
    }
}
