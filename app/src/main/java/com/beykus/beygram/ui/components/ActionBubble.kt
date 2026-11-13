package com.beykus.beygram.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassCard
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.motionEntrance
import com.beykus.beygram.ui.motion.rememberMotionSpec

private val BubbleSize = 52.dp
private val BubbleMinHeight = 84.dp
private val EntranceRise = 12.dp
private const val EntranceStepMillis = 18
private const val EntranceMaxSteps = 8
private const val PressedScale = 0.94f

/**
 * Round action button with a caption under it, shared by the attachment panel and the chat menu.
 * [entranceIndex] staggers the appearance; [active] fills the circle with the accent colour.
 */
@Composable
fun ActionBubble(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    entranceIndex: Int = 0
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val spec = rememberMotionSpec(MotionToken.MicroFeedback)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 1f - (1f - PressedScale) * spec.intensity else 1f,
        animationSpec = spec.animationSpec()
    )
    val delay = EntranceStepMillis * entranceIndex.coerceIn(0, EntranceMaxSteps)
    val circleShape = styledShape(BubbleSize / 2)
    val circleModifier = when {
        active -> Modifier.clip(circleShape).background(palette.accent)
        design.usesGlassPanels -> Modifier.glassCard(design, palette.surface2, palette.textSecondary, circleShape)
        else -> Modifier.clip(circleShape).background(palette.accentSoft)
    }
    val iconTint = when {
        active -> palette.onAccent
        design.usesGlassPanels -> palette.accent
        else -> palette.accentInk
    }
    Column(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = BubbleMinHeight)
            .motionEntrance(MotionToken.SheetEnter, EntranceRise, delay)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(BubbleSize).scale(pressScale).then(circleModifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = palette.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** A full-width sheet row with an icon on the left. Danger rows use the danger colour for icon and title. */
@Composable
fun SheetActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    val palette = beyGramPalette()
    val tint = if (danger) palette.danger else settingsIconTint()
    val textColor = if (danger) palette.danger else palette.textPrimary
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
