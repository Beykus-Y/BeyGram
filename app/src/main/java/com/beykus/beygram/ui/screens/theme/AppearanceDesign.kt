package com.beykus.beygram.ui.screens.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.ui.components.beyGramPalette

/** Shared measurements for every Appearance page. They keep the section compact on phones. */
internal object AppearanceDimens {
    val pagePadding: Dp = 16.dp
    val sectionGap: Dp = 18.dp
    val cardGap: Dp = 10.dp
    val cardShape = RoundedCornerShape(20.dp)
    val innerShape = RoundedCornerShape(14.dp)
    val leadingTileShape = RoundedCornerShape(12.dp)
    val minTouchTarget: Dp = 48.dp
}

@Composable
internal fun AppearanceSectionLabel(text: String, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    Text(
        text,
        color = palette.textSecondary,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(horizontal = 4.dp)
    )
}

/** A raised group of controls. The caller owns the internal spacing so sections stay dense but readable. */
@Composable
internal fun AppearanceCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = beyGramPalette()
    Column(
        modifier.fillMaxWidth().clip(AppearanceDimens.cardShape).background(palette.surface)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(AppearanceDimens.cardGap),
        content = content
    )
}

@Composable
internal fun AppearanceIconTile(icon: ImageVector, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val palette = beyGramPalette()
    Box(
        modifier.size(42.dp).clip(AppearanceDimens.leadingTileShape).background(palette.accentSoft),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = palette.accentInk, modifier = Modifier.size(21.dp))
    }
}

/** A large, obvious route into one part of Appearance. */
@Composable
internal fun AppearanceNavCard(
    title: String,
    summary: String,
    onClick: () -> Unit,
    trailingIcon: ImageVector,
    leading: @Composable () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().clip(AppearanceDimens.cardShape)
            .background(palette.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $summary" }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
            Text(summary, color = palette.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Icon(trailingIcon, null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun AppearanceToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth()
            .then(
                if (enabled) Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                else Modifier
            )
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                color = if (enabled) palette.textPrimary else palette.textSecondary,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium
            )
            Text(detail, color = palette.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A chip that wraps instead of forcing a horizontal scroll on a narrow screen. */
@Composable
internal fun AppearanceChoiceChip(
    text: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.heightIn(min = AppearanceDimens.minTouchTarget)
            .clip(shape)
            .background(if (selected) palette.accentSoft else palette.surface2)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) palette.accent else palette.divider, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) palette.accentInk else palette.textPrimary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** A compact group of mutually exclusive labels that flows onto another line as needed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppearanceChoiceFlow(
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, (label, selected) ->
            AppearanceChoiceChip(label, selected) { onSelect(index) }
        }
    }
}

/**
 * A clean slider: no step dots, a readable current value and a title that is also available to TalkBack.
 * Snapping remains the caller's job so the saved value keeps the same precision as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onValueChangeFinished: () -> Unit = {},
    onValueChange: (Float) -> Unit
) {
    val palette = beyGramPalette()
    val description = stringResource(R.string.appearance_slider_description, title, valueText)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = palette.textPrimary, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = palette.accentInk, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
            enabled = enabled,
            valueRange = range,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.surface2,
                disabledThumbColor = palette.textSecondary,
                disabledActiveTrackColor = palette.divider,
                disabledInactiveTrackColor = palette.surface2
            ),
            thumb = {
                Box(
                    Modifier.size(20.dp).clip(CircleShape)
                        .background(if (enabled) palette.accent else palette.textSecondary)
                )
            },
            track = { state ->
                Box(
                    Modifier.fillMaxWidth().height(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                            .background(if (enabled) palette.surface2 else palette.surface2)
                    ) {
                        Box(
                            Modifier.fillMaxWidth(state.coercedValueAsFraction.coerceIn(0f, 1f))
                                .height(6.dp).clip(CircleShape)
                                .background(if (enabled) palette.accent else palette.textSecondary)
                        )
                    }
                }
            }
        )
    }
}

/** A 2-up tile for brightness and palette-independent choices; wraps its text rather than clipping it. */
@Composable
internal fun AppearanceOptionTile(
    title: String,
    detail: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val palette = beyGramPalette()
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (selected) palette.accentSoft else palette.surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) palette.accent else palette.divider, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) palette.accentInk else palette.textSecondary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title,
                color = if (selected) palette.accentInk else palette.textPrimary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
            if (detail != null) {
                Text(detail, color = palette.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
internal fun AppearanceStatusPill(text: String, active: Boolean) {
    val palette = beyGramPalette()
    Row(
        Modifier.clip(CircleShape)
            .background(if (active) palette.accentSoft else palette.surface2)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            Modifier.size(6.dp).clip(CircleShape)
                .background(if (active) palette.accent else palette.textSecondary)
        )
        Text(
            text,
            color = if (active) palette.accentInk else palette.textSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** The single place that explains whether the edits are saved and lets the user save them. */
@Composable
internal fun AppearanceApplyBar(
    hasChanges: Boolean,
    canApply: Boolean,
    blockedByContrast: Boolean,
    onApply: () -> Unit
) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().background(palette.surface)) {
        HorizontalDivider(color = palette.divider)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(
                    when {
                        blockedByContrast -> R.string.appearance_apply_blocked
                        hasChanges -> R.string.appearance_apply_hint
                        else -> R.string.appearance_applied
                    }
                ),
                color = if (blockedByContrast) palette.danger else palette.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Button(
                onClick = onApply,
                enabled = canApply,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.theme_apply), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun AppearanceResetDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.appearance_reset_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.theme_editor_cancel)) }
        }
    )
}
