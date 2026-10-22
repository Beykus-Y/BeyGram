package com.beykus.beygram.ui.screens.theme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.beykus.beygram.data.appearance.DEFAULT_MESSAGE_TEXT_SIZE_SP
import com.beykus.beygram.data.appearance.MAX_BUBBLE_CORNER_RADIUS_DP
import com.beykus.beygram.data.appearance.MIN_BUBBLE_CORNER_RADIUS_DP
import com.beykus.beygram.data.appearance.MessageLineSpacing
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.bubbleMetricsFor
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.theme.resolveTokens
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.beykus.beygram.ui.theme.contrastIssues

/** Colours offered for the accent and for outgoing bubbles. */
private val AccentChoices = listOf(0xFF6A4FDC, 0xFF3B5BDB, 0xFF1C7ED6, 0xFF0F9D8A, 0xFF2F9E44, 0xFFE8890C, 0xFFD6336C, 0xFFC2410C).map { it.toInt() }
private val BubbleChoices = listOf(0xFFE6DEFD, 0xFFDCE4F8, 0xFFD3EFFA, 0xFFD5F2E6, 0xFFDDF3C9, 0xFFFFE8C2, 0xFFFAD9E6, 0xFF2E2952).map { it.toInt() }

/** Palette labels are localized and presented as the swatch's accessible name. */
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

/**
 * Text and time colours offered in each theme. Each set reads on that theme's bubbles; a pick is still checked against the real bubbles.
 */
private val LightTextChoices = listOf(0xFF1A1714, 0xFF4B33B5).map { it.toInt() }
private val LightTextNames = listOf(R.string.theme_editor_colour_ink, R.string.theme_editor_colour_indigo)
private val DarkTextChoices = listOf(0xFFFFFFFF, 0xFFCFC4FF).map { it.toInt() }
private val DarkTextNames = listOf(R.string.theme_editor_colour_white, R.string.theme_editor_colour_lavender)
private val LightTimeChoices = listOf(0xFF67625B, 0xFF4B33B5, 0xFF1A1714).map { it.toInt() }
private val LightTimeNames = listOf(
    R.string.theme_editor_colour_grey, R.string.theme_editor_colour_indigo, R.string.theme_editor_colour_ink
)
private val DarkTimeChoices = listOf(0xFFC9C2BB, 0xFFCFC4FF, 0xFFFFFFFF).map { it.toInt() }
private val DarkTimeNames = listOf(
    R.string.theme_editor_colour_light_grey, R.string.theme_editor_colour_lavender, R.string.theme_editor_colour_white
)

/**
 * The editor of the look: accent, bubble colours, bubble shape and corner radius, text size, line spacing and list density.
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
    var textSize by rememberSaveable { mutableIntStateOf(saved.messageTextSizeSp) }
    var cornerRadius by rememberSaveable { mutableStateOf(saved.bubbleCornerRadiusDp) }
    var lineSpacing by rememberSaveable { mutableStateOf(saved.messageLineSpacing) }
    var messageTextColor by rememberSaveable { mutableStateOf(saved.messageTextColor) }
    var messageTimeColor by rememberSaveable { mutableStateOf(saved.messageTimeColor) }
    // Keeps the palette and brightness that the editor does not change.
    val draft = saved.copy(accentColor = accent, outgoingBubbleColor = bubbleColor, bubbleStyle = style,
        density = density, messageTextSizeSp = textSize, bubbleCornerRadiusDp = cornerRadius, messageLineSpacing = lineSpacing,
        messageTextColor = messageTextColor, messageTimeColor = messageTimeColor)
    val tokens = resolveTokens(draft, systemDark)
    val issues = contrastIssues(tokens, bubbleColor, messageTextColor, messageTimeColor)
    val metrics = bubbleMetricsFor(style, density, cornerRadius)
    // The slider starts from the radius the shape gives, so the first drag moves from what the user already sees.
    val shapeRadius = bubbleMetricsFor(style, density).cornerRadius.value.toInt()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    fun requestBack() {
        if (draft != saved) confirmDiscard = true else onBack()
    }
    BackHandler(onBack = ::requestBack)

    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.theme_editor_title), ::requestBack)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle(stringResource(R.string.theme_preview_title))
            BeyGramTheme(darkTheme = draft.mode.isDark(systemDark), tokens = tokens, messageTextSizeSp = textSize,
                messageLineSpacing = lineSpacing) {
                ThemePreview(bubbleRadius = metrics.cornerRadius, spacing = metrics.itemSpacing)
            }
            if (issues.any) {
                Text(
                    stringResource(
                        when {
                            issues.bubbleText -> R.string.theme_editor_warning_bubble
                            issues.messageColour -> R.string.theme_editor_warning_message_colour
                            else -> R.string.theme_editor_warning_accent
                        }
                    ),
                    color = palette.danger,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            SectionTitle(stringResource(R.string.theme_editor_accent))
            ColorChoices(AccentChoices, AccentNames, accent, stringResource(R.string.theme_editor_default)) { accent = it }
            SectionTitle(stringResource(R.string.theme_editor_bubble_color))
            ColorChoices(BubbleChoices, BubbleNames, bubbleColor, stringResource(R.string.theme_editor_default)) { bubbleColor = it }
            val darkTheme = draft.mode.isDark(systemDark)
            SectionTitle(stringResource(R.string.theme_editor_message_text))
            ColorChoices(
                if (darkTheme) DarkTextChoices else LightTextChoices,
                if (darkTheme) DarkTextNames else LightTextNames,
                messageTextColor, stringResource(R.string.theme_editor_default)
            ) { messageTextColor = it }
            SectionTitle(stringResource(R.string.theme_editor_message_time))
            ColorChoices(
                if (darkTheme) DarkTimeChoices else LightTimeChoices,
                if (darkTheme) DarkTimeNames else LightTimeNames,
                messageTimeColor, stringResource(R.string.theme_editor_default)
            ) { messageTimeColor = it }

            SectionTitle(stringResource(R.string.theme_editor_bubble_shape))
            ChoiceGroup {
                ChoiceRow(stringResource(R.string.theme_editor_shape_rounded), style == BubbleStyle.Rounded) { style = BubbleStyle.Rounded }
                ChoiceRow(stringResource(R.string.theme_editor_shape_soft), style == BubbleStyle.Soft) { style = BubbleStyle.Soft }
                ChoiceRow(stringResource(R.string.theme_editor_shape_square), style == BubbleStyle.Square) { style = BubbleStyle.Square }
            }

            SectionTitle(stringResource(R.string.theme_editor_corner_radius, cornerRadius ?: shapeRadius))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface)
                .padding(horizontal = 16.dp, vertical = 6.dp)) {
                Slider(
                    value = (cornerRadius ?: shapeRadius).toFloat(),
                    onValueChange = { cornerRadius = it.toInt().coerceIn(MIN_BUBBLE_CORNER_RADIUS_DP, MAX_BUBBLE_CORNER_RADIUS_DP) },
                    valueRange = MIN_BUBBLE_CORNER_RADIUS_DP.toFloat()..MAX_BUBBLE_CORNER_RADIUS_DP.toFloat(),
                    steps = MAX_BUBBLE_CORNER_RADIUS_DP - MIN_BUBBLE_CORNER_RADIUS_DP - 1
                )
                Text(
                    stringResource(R.string.theme_editor_corner_radius_by_shape),
                    color = if (cornerRadius == null) palette.accent else palette.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (cornerRadius == null) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .selectable(selected = cornerRadius == null, role = Role.RadioButton) { cornerRadius = null }
                        .wrapContentHeight(Alignment.CenterVertically)
                )
            }

            SectionTitle(stringResource(R.string.theme_editor_text_size, textSize))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface)
                .padding(horizontal = 16.dp, vertical = 6.dp)) {
                Slider(
                    value = textSize.toFloat(),
                    onValueChange = { textSize = it.toInt().coerceIn(12, 22) },
                    valueRange = 12f..22f,
                    steps = 9
                )
                Text(stringResource(R.string.theme_editor_text_size_hint), color = palette.textSecondary, fontSize = 12.sp)
            }

            SectionTitle(stringResource(R.string.theme_editor_line_spacing))
            ChoiceGroup {
                ChoiceRow(stringResource(R.string.theme_editor_line_spacing_compact), lineSpacing == MessageLineSpacing.Compact) { lineSpacing = MessageLineSpacing.Compact }
                ChoiceRow(stringResource(R.string.theme_editor_line_spacing_normal), lineSpacing == MessageLineSpacing.Normal) { lineSpacing = MessageLineSpacing.Normal }
                ChoiceRow(stringResource(R.string.theme_editor_line_spacing_relaxed), lineSpacing == MessageLineSpacing.Relaxed) { lineSpacing = MessageLineSpacing.Relaxed }
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
                    textSize = DEFAULT_MESSAGE_TEXT_SIZE_SP
                    cornerRadius = null
                    lineSpacing = MessageLineSpacing.Normal
                    messageTextColor = null
                    messageTimeColor = null
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.theme_editor_reset))
            }
            Text(stringResource(R.string.theme_editor_save_note), color = palette.textSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = ::requestBack, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.theme_editor_cancel)) }
            Button(onClick = { onSave(draft); onBack() }, enabled = draft != current && !issues.blocking, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.theme_apply))
            }
        }
    }
    if (confirmDiscard) ThemeDiscardDialog(
        onKeepEditing = { confirmDiscard = false },
        onDiscard = { confirmDiscard = false; onBack() }
    )
}

/** FlowRow wraps automatically on 320dp screens, larger font settings and landscape. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorChoices(colors: List<Int>, labelResources: List<Int>, selected: Int?, defaultLabel: String, onPick: (Int?) -> Unit) {
    require(colors.size == labelResources.size)
    val palette = beyGramPalette()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            colors.forEachIndexed { index, color ->
                ColorSwatch(color, stringResource(labelResources[index]), selected == color) { onPick(color) }
            }
        }
        Text(
            defaultLabel,
            color = if (selected == null) palette.accent else palette.textSecondary,
            fontSize = 14.sp,
            fontWeight = if (selected == null) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .selectable(selected = selected == null, role = Role.RadioButton) { onPick(null) }
                .wrapContentHeight(Alignment.CenterVertically)
        )
    }
}

/** The hit target is 48dp, the visible circle remains 40dp for visual consistency. */
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
            Modifier.size(40.dp).clip(CircleShape).background(Color(color))
                .border(
                    if (selected) 3.dp else 1.dp,
                    if (selected) palette.accent else palette.textSecondary.copy(alpha = 0.3f),
                    CircleShape
                )
        )
    }
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
