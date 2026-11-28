package com.beykus.beygram.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.styledTopShape
import com.beykus.beygram.ui.design.usesGlassPanels

/**
 * BeyGram's bottom sheet for choices, settings, lists and small forms. A swipe down, a tap outside and Back close it
 * as usual. It is drawn with the fill of the other BeyGram sheets ([sheetSurfaceColor]), the BeyGram top corners and
 * its own drag handle, so it follows the active theme and style.
 *
 * In the Liquid Glass style the sheet is drawn **inside the app window** ([BeyGramModalHost]) rather than in a
 * `ModalBottomSheet` window, because only then can it sample the screen behind it and show the real material. Every
 * other style keeps Material's sheet unchanged.
 *
 * - [title] is optional. When it is set, it is the sheet heading and the pane title that screen readers announce.
 * - [scrollableContent] wraps [content] in a vertical scroll area. Set it to false when [content] is a lazy list, so the
 *   two scroll areas do not nest.
 * - [sheetState] opens the sheet fully. Pass it to wait for [BeyGramSheetState.isVisible] or to reopen the sheet from a
 *   running operation.
 * - [containerColor], [contentColor], [dragHandle] and [bottomPadding] keep the BeyGram look by default. Pass them only
 *   for a surface with colors of its own, such as the full player, which takes the tint of its cover.
 * - [glassEligible] is the opt-out for such a surface: a sheet that brings its own material keeps Material's sheet even
 *   in Liquid Glass, so its design does not change.
 * - Insets use the sheet's own defaults and the window's IME inset. A text field inside a sheet must be checked with
 *   the keyboard open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeyGramSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    sheetState: BeyGramSheetState = rememberBeyGramSheetState(),
    scrollableContent: Boolean = true,
    containerColor: Color = sheetSurfaceColor(),
    contentColor: Color = beyGramPalette().textPrimary,
    dragHandle: @Composable () -> Unit = { BeyGramSheetHandle() },
    bottomPadding: Dp = 12.dp,
    glassEligible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    if (com.beykus.beygram.LocalMainTabsCovered.current) {
        LaunchedEffect(Unit) { onDismissRequest() }
        return
    }
    val design = LocalDesignSystem.current
    val host = LocalBeyGramModalHost.current
    if (glassEligible && design.usesGlassPanels && host != null) {
        val route = rememberSheetRoute(
            state = sheetState,
            content = content,
            onDismissRequest = onDismissRequest,
            title = title,
            modifier = modifier,
            dragHandle = dragHandle,
            scrollableContent = scrollableContent,
            bottomPadding = bottomPadding,
            contentColor = contentColor
        )
        HostedSheet(route)
        return
    }
    val palette = beyGramPalette()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = if (title != null) modifier.semantics { paneTitle = title } else modifier,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = sheetState.skipPartiallyExpanded,
            // The veto lives on the BeyGram state so it applies to the hosted sheet and to this one alike.
            confirmValueChange = { next -> next != SheetValue.Hidden || sheetState.dismissAllowed() }
        ),
        shape = styledTopShape(28.dp),
        containerColor = containerColor,
        contentColor = contentColor,
        dragHandle = dragHandle
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (scrollableContent) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(bottom = bottomPadding)
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