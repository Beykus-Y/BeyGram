package com.beykus.beygram.ui.screens.profile

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.groupSurfaceColor
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassCard
import com.beykus.beygram.ui.design.groupCardShape

/**
 * Side inset of the content inside one group. A card keeps 14dp from its edge; Signature draws no card, so its text sits
 * flush with the section headings.
 */
@Composable
internal fun statisticsInset(): Dp =
    if (LocalDesignSystem.current.style == DesignStyle.Signature) 0.dp else 14.dp

/**
 * The container of a statistics group, drawn by the style in effect: a settings-style card in Default, an elevated 4dp
 * card in Material 2, a tonal container in Material 3 and a glass card with its rim in Liquid Glass. Signature draws no
 * container: its groups sit on the canvas and are separated by hairlines.
 */
@Composable
internal fun Modifier.statisticsGroupSurface(): Modifier {
    val design = LocalDesignSystem.current
    if (design.style == DesignStyle.Signature) return this
    val palette = beyGramPalette()
    val shape = groupCardShape()
    return shadow(design.surfaces.cardElevation, shape)
        .glassCard(design, groupSurfaceColor(), palette.textSecondary, shape)
}

/**
 * A section heading. Material 2 and Signature set headings as small tracked capitals; the other styles keep a sentence
 * case heading. TalkBack announces it as a heading in every style.
 */
@Composable
internal fun StatisticsSectionHeader(text: String, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    val main = LocalDesignSystem.current.mainScreen
    val upper = main.sectionLabelUppercase
    Text(
        if (upper) text.uppercase() else text,
        color = if (upper) palette.textSecondary else palette.textPrimary,
        fontSize = if (upper) 13.sp else 18.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = main.sectionLabelTracking,
        modifier = modifier.semantics { heading() }
    )
}
