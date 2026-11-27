package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal fun videoNoteMetadataInnerEdge(diameter: Int, centerY: Float, overlap: Int, widestLabel: Int): Int {
    val radius = diameter / 2f
    val vertical = centerY - radius
    val contour = radius - sqrt((radius * radius - vertical * vertical).coerceAtLeast(0f))
    return maxOf((contour + overlap).roundToInt(), widestLabel).coerceAtMost(diameter / 2)
}

@Composable
internal fun VideoNoteMetadata(
    durationText: String,
    time: String,
    statusIcon: ImageVector?,
    statusDescription: String?,
    outgoing: Boolean,
    modifier: Modifier
) {
    Layout(
        modifier = modifier,
        contents = listOf(
            { VideoNoteMetadataPill(durationText) },
            { if (time.isNotBlank()) VideoNoteMetadataPill(time, statusIcon, statusDescription) }
        )
    ) { slots, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val gap = 6.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = ((width - gap) / 2).coerceAtLeast(0))
        val duration = slots[0].single().measure(loose)
        val timestamp = slots[1].singleOrNull()?.measure(loose)
        val pillHeight = maxOf(duration.height, timestamp?.height ?: 0)
        // The center of both labels is at 81% of the circle's height. The inner edges are mirrors:
        // both intrude by the same horizontal distance past the contour at that depth.
        val top = (height * 0.81f - pillHeight / 2).roundToInt().coerceIn(0, (height - pillHeight).coerceAtLeast(0))
        val edge = videoNoteMetadataInnerEdge(
            width, top + pillHeight / 2f, 12.dp.roundToPx(), maxOf(duration.width, timestamp?.width ?: 0)
        ).coerceAtMost((width - gap).coerceAtLeast(0) / 2)
        layout(width, height) {
            duration.placeRelative(if (outgoing) edge - duration.width else width - edge, top + (pillHeight - duration.height) / 2)
            timestamp?.placeRelative(if (outgoing) width - edge else edge - timestamp.width, top + (pillHeight - timestamp.height) / 2)
        }
    }
}

@Composable
private fun VideoNoteMetadataPill(text: String, icon: ImageVector? = null, iconLabel: String? = null) {
    Box(
        Modifier.heightIn(min = 24.dp).background(Color.Black.copy(alpha = 0.68f), CircleShape)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            if (icon != null) Icon(icon, contentDescription = iconLabel, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}
