package com.beykus.beygram.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.ui.design.LocalDesignSystem

/*
 * Style-aware stand-ins for the Material 3 buttons and switch. They keep the Material 3 signatures, so a screen only
 * swaps its import. Material 3 hard-codes a pill for every button and its own track-and-thumb switch, neither of which
 * the theme's shape scale reaches; without these, a Material 2 screen would keep modern pills next to its 4dp cards.
 * Every style except Material 2 gets exactly the Material 3 default, so Default stays pixel-identical.
 */

private val isMaterial2: Boolean
    @Composable get() = LocalDesignSystem.current.style == DesignStyle.Material2

/** The button shape of the style: Material's pill everywhere except Material 2, which uses its 4dp button corner. */
@Composable
fun styledButtonShape(): Shape =
    if (isMaterial2) RoundedCornerShape(LocalDesignSystem.current.shapes.button) else ButtonDefaults.shape

/** Contained buttons in Material 2 sit 2dp above the surface and lift to 8dp while pressed. */
@Composable
fun styledButtonElevation(): ButtonElevation =
    if (isMaterial2) {
        ButtonDefaults.buttonElevation(
            defaultElevation = LocalDesignSystem.current.surfaces.buttonElevation,
            pressedElevation = 8.dp,
            focusedElevation = 4.dp,
            hoveredElevation = 4.dp,
            disabledElevation = 0.dp
        )
    } else {
        ButtonDefaults.buttonElevation()
    }

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = styledButtonShape(),
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = styledButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) = androidx.compose.material3.Button(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
    content = content
)

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = if (isMaterial2) styledButtonShape() else ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) = androidx.compose.material3.OutlinedButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
    content = content
)

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = if (isMaterial2) styledButtonShape() else ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) = androidx.compose.material3.TextButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
    content = content
)

/**
 * The switch of the style. Material 2 draws the classic control: a thin 34×14dp track with a raised 20dp thumb that
 * overhangs it, accent-coloured when on. The other styles keep the Material 3 switch untouched. The control keeps the
 * switch role, the 48dp touch target and the disabled state in both forms.
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null
) {
    // BeyGram Signature: ink selects. An unchanged default switch takes an ink track with a canvas thumb when on, so the
    // accent stays for actions; a caller that passes its own colours keeps them.
    val resolvedColors = if (LocalDesignSystem.current.style == DesignStyle.Signature && colors == SwitchDefaults.colors()) {
        signatureSwitchColors()
    } else {
        colors
    }
    if (!isMaterial2) {
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            thumbContent = thumbContent,
            enabled = enabled,
            colors = resolvedColors,
            interactionSource = interactionSource
        )
        return
    }
    ClassicSwitch(checked, onCheckedChange, modifier, enabled, interactionSource)
}

/** The Signature switch: ink track and canvas thumb when on, a quiet outlined track when off. */
@Composable
private fun signatureSwitchColors(): SwitchColors {
    val palette = beyGramPalette()
    return SwitchDefaults.colors(
        checkedThumbColor = palette.screen,
        checkedTrackColor = palette.textPrimary,
        checkedBorderColor = palette.textPrimary,
        checkedIconColor = palette.textPrimary,
        uncheckedThumbColor = palette.textSecondary,
        uncheckedTrackColor = palette.surface2,
        // The rim takes the thumb's own ink, so the off control stays readable on a light surface2 track.
        uncheckedBorderColor = palette.textSecondary,
        disabledCheckedThumbColor = palette.screen,
        disabledCheckedTrackColor = palette.textPrimary.copy(alpha = 0.38f),
        disabledCheckedBorderColor = palette.textPrimary.copy(alpha = 0.0f),
        disabledUncheckedThumbColor = palette.textSecondary.copy(alpha = 0.38f),
        disabledUncheckedTrackColor = palette.surface2,
        disabledUncheckedBorderColor = palette.textSecondary.copy(alpha = 0.38f)
    )
}

@Composable
private fun ClassicSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
    enabled: Boolean,
    interactionSource: MutableInteractionSource?
) {
    val scheme = MaterialTheme.colorScheme
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val offThumb = if (scheme.surface.luminance() < 0.5f) scheme.onSurfaceVariant else scheme.surface
    val thumbTarget = when {
        checked -> scheme.primary
        else -> offThumb
    }.let { if (enabled) it else it.copy(alpha = 0.38f).compositeOver(scheme.surface) }
    val trackTarget = (if (checked) scheme.primary.copy(alpha = 0.5f) else scheme.onSurface.copy(alpha = 0.38f))
        .let { if (enabled) it else it.copy(alpha = it.alpha * 0.5f) }
    val thumb by animateColorAsState(thumbTarget, label = "classicSwitchThumb")
    val track by animateColorAsState(trackTarget, label = "classicSwitchTrack")
    val thumbOffset by animateDpAsState(if (checked) 16.dp else 0.dp, label = "classicSwitchOffset")

    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            interactionSource = source,
            indication = ripple(bounded = false, radius = 20.dp),
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .then(toggle)
            .size(width = 36.dp, height = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(width = 34.dp, height = 14.dp)
                .background(track, RoundedCornerShape(7.dp))
        )
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(20.dp)
                .shadow(if (enabled) 2.dp else 0.dp, CircleShape)
                .background(thumb, CircleShape)
        )
    }
}
