package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.screens.playback.NowPlayingBar

@Composable
internal fun ChatTopPanelCapsule(showPlayer: Boolean, pinned: @Composable () -> Unit) {
    val design = LocalDesignSystem.current
    val palette = beyGramPalette()
    FloatingPinnedCard(design, palette, verticalGaps = false) {
        CompositionLocalProvider(LocalChatChromeOnGlass provides true) {
            Layout(
                modifier = Modifier.fillMaxWidth(),
                contents = listOf(
                    { Box(Modifier.fillMaxWidth()) { if (showPlayer) NowPlayingBar(compact = true) } },
                    { HorizontalDivider(color = palette.divider) },
                    { Box(Modifier.fillMaxWidth()) { pinned() } }
                )
            ) { slots, constraints ->
                val rowConstraints = constraints.copy(minHeight = 0)
                val player = slots[0].single().measure(rowConstraints)
                val pin = slots[2].single().measure(rowConstraints)
                val divider = if (player.height > 0 && pin.height > 0) {
                    slots[1].single().measure(rowConstraints)
                } else null
                val dividerHeight = divider?.height ?: 0
                layout(maxOf(player.width, pin.width), player.height + dividerHeight + pin.height) {
                    player.placeRelative(0, 0)
                    divider?.placeRelative(0, player.height)
                    pin.placeRelative(0, player.height + dividerHeight)
                }
            }
        }
    }
}

/** Keeps the space around a floating panel tied to its animated height. */
internal fun Modifier.chatTopPanelSpacing() = layout { measurable, constraints ->
    val panel = measurable.measure(constraints)
    val gap = minOf(4.dp.roundToPx(), (constraints.maxHeight - panel.height) / 2)
    val padding = if (panel.height == 0) 0 else gap
    layout(panel.width, panel.height + padding * 2) { panel.placeRelative(0, padding) }
}
