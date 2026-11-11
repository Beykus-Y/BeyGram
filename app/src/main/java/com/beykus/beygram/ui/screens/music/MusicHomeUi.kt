package com.beykus.beygram.ui.screens.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.ui.components.TextButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.groupSurfaceColor
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassCard
import com.beykus.beygram.ui.design.styledShape

/*
 * Building blocks of the music screen, shared by every source tab: the same card, heading, tile, cover card and track
 * row as the settings, so each design style and theme reaches the screen without code of its own.
 */

/** Side margin of the music screen content. */
internal val MusicScreenGutter = 16.dp

/** Gap between tiles and cover cards. */
private val MusicGridGap = 10.dp

/** The group card of the current style: a glass card in Liquid Glass, the tonal or plain surface elsewhere. */
@Composable
internal fun Modifier.musicCard(): Modifier {
    val design = LocalDesignSystem.current
    return glassCard(design, groupSurfaceColor(), beyGramPalette().textSecondary, RoundedCornerShape(design.shapes.card))
}

/** A card that holds rows, such as the short track list. */
@Composable
internal fun MusicCardColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = MusicScreenGutter).musicCard().padding(vertical = 4.dp),
        content = content
    )
}

/**
 * Heading of a group, in the style of the settings headings and announced as a heading. [actionLabel] adds a button on
 * the right, such as «Все».
 */
@Composable
internal fun MusicGroupHeading(title: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 22.dp, end = 10.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (design.mainScreen.sectionLabelUppercase) title.uppercase() else title,
            color = palette.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = design.mainScreen.sectionLabelTracking,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        if (actionLabel != null) TextButton(onClick = onAction) {
            Text(actionLabel, color = palette.accent)
        }
    }
}

/** Lays [count] cells out in rows of [columns] with the grid gap; [cell] draws the cell at an index. */
@Composable
internal fun MusicGrid(count: Int, columns: Int, cell: @Composable (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = MusicScreenGutter),
        verticalArrangement = Arrangement.spacedBy(MusicGridGap)
    ) {
        for (start in 0 until count step columns) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicGridGap)) {
                for (index in start until start + columns) {
                    Box(Modifier.weight(1f)) { if (index < count) cell(index) }
                }
            }
        }
    }
}

/** Columns of cover cards for the available width: three on a phone, more on a wide screen. */
@Composable
internal fun MusicCoverGrid(count: Int, cell: @Composable (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = coverColumns(maxWidth)
        MusicGrid(count = count, columns = columns, cell = cell)
    }
}

/** Two tiles per row on a phone, up to four on a wide screen. */
@Composable
internal fun MusicTileGrid(count: Int, cell: @Composable (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (maxWidth / 200.dp).toInt().coerceIn(2, 4)
        MusicGrid(count = count, columns = columns, cell = cell)
    }
}

internal fun coverColumns(width: Dp): Int = (width / 124.dp).toInt().coerceIn(3, 8)

/** A quick-access tile: icon, name and how many tracks it holds. [count] null hides the count while it is looked up. */
@Composable
internal fun MusicQuickTile(icon: ImageVector, title: String, count: String?, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 72.dp)
            .musicCard()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(palette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = palette.accentInk, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (count != null) Text(
                count,
                color = palette.textSecondary,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * A playlist or album in the grid: a square cover, the name and one line under it. [cover] draws the square; it gets a
 * modifier that fills the cell.
 */
@Composable
internal fun MusicCoverCard(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    cover: @Composable (Modifier) -> Unit
) {
    val palette = beyGramPalette()
    Column(
        Modifier.fillMaxWidth()
            .clip(styledShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(bottom = 6.dp)
    ) {
        cover(Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            color = palette.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        if (subtitle != null) Text(
            subtitle,
            color = palette.textSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

/** Gray stand-ins for tiles and rows while a library is read, so the screen keeps its shape. */
@Composable
internal fun MusicLoadingSkeleton() {
    val palette = beyGramPalette()
    val block = palette.textSecondary.copy(alpha = 0.12f)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        MusicTileGrid(count = 4) {
            Box(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(LocalDesignSystem.current.shapes.card)).background(block))
        }
        Spacer(Modifier.height(20.dp))
        MusicCardColumn {
            repeat(3) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(44.dp).clip(styledShape(12.dp)).background(block))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(CircleShape).background(block))
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth(0.35f).height(10.dp).clip(CircleShape).background(block))
                    }
                }
            }
        }
    }
}
