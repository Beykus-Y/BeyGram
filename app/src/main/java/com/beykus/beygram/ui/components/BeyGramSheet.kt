package com.beykus.beygram.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.beykus.beygram.ui.design.styledTopShape

/**
 * BeyGram's bottom sheet for choices, settings, lists and small forms. It is built on Material's ModalBottomSheet, so a
 * swipe down, a tap outside and Back close it as usual. It is drawn with the fill of the other BeyGram sheets
 * ([sheetSurfaceColor]), the BeyGram top corners and its own drag handle, so it follows the active theme and style.
 *
 * - [title] is optional. When it is set, it is the sheet heading and the pane title that screen readers announce.
 * - [scrollableContent] wraps [content] in a vertical scroll area. Set it to false when [content] is a lazy list, so the
 *   two scroll areas do not nest.
 * - [sheetState] defaults to Material's state, which opens partially when the content is tall. Pass
 *   rememberModalBottomSheetState(skipPartiallyExpanded = true) to open the sheet fully.
 * - [containerColor], [contentColor] and [dragHandle] keep the BeyGram look by default. Pass them only for a surface
 *   with colors of its own, such as the full player, which takes the tint of its cover.
 * - Insets use the ModalBottomSheet defaults. A text field inside a sheet must be checked with the keyboard open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeyGramSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(),
    scrollableContent: Boolean = true,
    containerColor: Color = sheetSurfaceColor(),
    contentColor: Color = beyGramPalette().textPrimary,
    dragHandle: @Composable () -> Unit = { BeyGramDragHandle() },
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = beyGramPalette()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = if (title != null) modifier.semantics { paneTitle = title } else modifier,
        sheetState = sheetState,
        shape = styledTopShape(28.dp),
        containerColor = containerColor,
        contentColor = contentColor,
        dragHandle = dragHandle
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (scrollableContent) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(bottom = 12.dp)
        ) {
            if (title != null) {
                Text(
                    text = title,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary
                )
            }
            content()
        }
    }
}

/** A short rounded bar at the top of the sheet. It is decorative, so it has no semantics of its own. */
@Composable
private fun BeyGramDragHandle() {
    val palette = beyGramPalette()
    Box(
        Modifier
            .padding(top = 12.dp, bottom = 8.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(CircleShape)
            .background(palette.textSecondary.copy(alpha = 0.4f))
    )
}
