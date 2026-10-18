package com.beykus.beygram.ui.screens.theme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.BubbleStyle
import com.beykus.beygram.data.appearance.ListDensity
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.bubbleMetricsFor
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.beykus.beygram.ui.theme.contrastIssues
import com.beykus.beygram.ui.theme.tokensFor

/** Colours offered for the accent and for outgoing bubbles. */
private val AccentChoices = listOf(0xFF6A4FDC, 0xFF3B5BDB, 0xFF1C7ED6, 0xFF0F9D8A, 0xFF2F9E44, 0xFFE8890C, 0xFFD6336C, 0xFFC2410C).map { it.toInt() }
private val BubbleChoices = listOf(0xFFE6DEFD, 0xFFDCE4F8, 0xFFD3EFFA, 0xFFD5F2E6, 0xFFDDF3C9, 0xFFFFE8C2, 0xFFFAD9E6, 0xFF2E2952).map { it.toInt() }

/**
 * The editor of the look: accent, outgoing bubble colour, bubble shape and list density.
 * The preview follows every change. The choice reaches the rest of the app only after «Применить»; leaving the screen drops it.
 */
@Composable
fun ThemeEditorScreen(saved: AppearanceConfig, current: AppearanceConfig, onSave: (AppearanceConfig) -> Unit, onBack: () -> Unit) {
    val palette = beyGramPalette()
    val systemDark = isSystemInDarkTheme()
    var accent by rememberSaveable { mutableStateOf(saved.accentColor) }
    var bubbleColor by rememberSaveable { mutableStateOf(saved.outgoingBubbleColor) }
    var style by rememberSaveable { mutableStateOf(saved.bubbleStyle) }
    var density by rememberSaveable { mutableStateOf(saved.density) }
    // Keeps the palette and brightness that the editor does not change.
    val draft = saved.copy(accentColor = accent, outgoingBubbleColor = bubbleColor, bubbleStyle = style, density = density)
    val tokens = tokensFor(draft, systemDark)
    val issues = contrastIssues(tokens, bubbleColor)
    val metrics = bubbleMetricsFor(style, density)
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.theme_editor_title), onBack)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle(stringResource(R.string.theme_preview_title))
            BeyGramTheme(darkTheme = draft.mode.isDark(systemDark), tokens = tokens) {
                ThemePreview(bubbleRadius = metrics.cornerRadius, spacing = metrics.itemSpacing)
            }
            if (issues.any) {
                Text(
                    stringResource(if (issues.bubbleText) R.string.theme_editor_warning_bubble else R.string.theme_editor_warning_accent),
                    color = palette.danger,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            SectionTitle(stringResource(R.string.theme_editor_accent))
            ColorChoices(AccentChoices, accent, stringResource(R.string.theme_editor_default)) { accent = it }
            SectionTitle(stringResource(R.string.theme_editor_bubble_color))
            ColorChoices(BubbleChoices, bubbleColor, stringResource(R.string.theme_editor_default)) { bubbleColor = it }

            SectionTitle(stringResource(R.string.theme_editor_bubble_shape))
            ChoiceGroup {
                ChoiceRow(stringResource(R.string.theme_editor_shape_rounded), style == BubbleStyle.Rounded) { style = BubbleStyle.Rounded }
                ChoiceRow(stringResource(R.string.theme_editor_shape_soft), style == BubbleStyle.Soft) { style = BubbleStyle.Soft }
                ChoiceRow(stringResource(R.string.theme_editor_shape_square), style == BubbleStyle.Square) { style = BubbleStyle.Square }
            }

            SectionTitle(stringResource(R.string.theme_editor_density))
            ChoiceGroup {
                ChoiceRow(stringResource(R.string.theme_editor_density_compact), density == ListDensity.Compact) { density = ListDensity.Compact }
                ChoiceRow(stringResource(R.string.theme_editor_density_comfortable), density == ListDensity.Comfortable) { density = ListDensity.Comfortable }
                ChoiceRow(stringResource(R.string.theme_editor_density_spacious), density == ListDensity.Spacious) { density = ListDensity.Spacious }
            }

            TextButton(
                onClick = {
                    accent = null
                    bubbleColor = null
                    style = BubbleStyle.Rounded
                    density = ListDensity.Comfortable
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.theme_editor_reset))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.theme_editor_cancel)) }
            Button(onClick = { onSave(draft); onBack() }, enabled = draft != current, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.theme_apply))
            }
        }
    }
}

@Composable
private fun ColorChoices(colors: List<Int>, selected: Int?, defaultLabel: String, onPick: (Int?) -> Unit) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            colors.take(4).forEachIndexed { index, color -> Swatch(color, index, selected == color) { onPick(color) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            colors.drop(4).forEachIndexed { index, color -> Swatch(color, index + 4, selected == color) { onPick(color) } }
        }
        Text(
            defaultLabel,
            color = if (selected == null) palette.accent else palette.textSecondary,
            fontSize = 14.sp,
            fontWeight = if (selected == null) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.clickable { onPick(null) }.padding(vertical = 2.dp)
        )
    }
}

@Composable
private fun Swatch(color: Int, index: Int, selected: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    val description = stringResource(R.string.theme_editor_swatch, index + 1)
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color(color))
            .border(if (selected) 3.dp else 1.dp, if (selected) palette.accent else palette.textSecondary.copy(alpha = 0.3f), CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description }
    )
}

@Composable
private fun ChoiceGroup(content: @Composable () -> Unit) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(vertical = 4.dp)) { content() }
}

@Composable
private fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, color = palette.textPrimary, fontSize = 15.sp, modifier = Modifier.padding(start = 6.dp))
    }
}
