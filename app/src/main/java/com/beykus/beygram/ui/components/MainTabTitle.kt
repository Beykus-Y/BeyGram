package com.beykus.beygram.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.RectangleShape
import com.beykus.beygram.ui.design.GlassBackdrop
import com.beykus.beygram.ui.design.GlassEdge
import com.beykus.beygram.ui.design.glassBackdropSource
import com.beykus.beygram.ui.design.rememberGlassBackdrop
import com.beykus.beygram.ui.design.GlassPanel
import com.beykus.beygram.ui.design.LocalGlassBackdrop
import com.beykus.beygram.ui.design.usesGlassPanels
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.ui.design.LocalDesignSystem

/**
 * The large title of a main tab (Contacts, Calls, Settings). It reads the same title tokens as the chat list, so every
 * tab of the main screen speaks the style in effect: the dense 22sp Material 2 app bar title, the regular-weight
 * Material 3 headline, the quiet Glass title or the editorial Signature title. Default keeps its 30sp bold title.
 */
@Composable
fun MainTabTitle(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val main = design.mainScreen
    // Default's tab titles have never carried the chat list's negative tracking; keep them pixel-identical.
    val tracking = if (design.style == DesignStyle.Default) TextUnit.Unspecified else main.titleTracking
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            color = palette.textPrimary,
            fontSize = main.titleSize,
            fontWeight = main.titleWeight,
            letterSpacing = tracking,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        actions()
    }
}

/** Title weight of a list row (contact, call, member) in the style in effect; Default keeps its semi-bold names. */
val listRowTitleWeight: FontWeight
    @Composable get() = LocalDesignSystem.current.mainScreen.rowTitleWeight

/**
 * The separator under a list row. Material 2 separates rows with a real divider aligned to the text; the other styles
 * rely on spacing and draw nothing, which is what Default has always done.
 */
@Composable
fun StyledListDivider(startIndent: Dp = 82.dp) {
    if (LocalDesignSystem.current.components.showListDividers) {
        HorizontalDivider(color = beyGramPalette().divider, modifier = Modifier.fillMaxWidth().padding(start = startIndent))
    }
}

/**
 * The system insets of a main tab. Glass draws its header pane under the status bar itself ([MainTabHeader]), so the
 * top inset belongs to the pane there; every other style pads the whole tab as before.
 */
@Composable
fun Modifier.mainTabInsets(): Modifier {
    val design = LocalDesignSystem.current
    val glass = design.usesGlassPanels || design.style == DesignStyle.Material2
    return windowInsetsPadding(
        WindowInsets.safeDrawing.only(if (glass) WindowInsetsSides.Horizontal else WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    )
}

/** The fixed tab header shares the list's recording only when V2 can draw it. */
@Composable
private fun MainTabHeader(
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    content: @Composable () -> Unit
) {
    val design = LocalDesignSystem.current
    if (design.style == DesignStyle.Material2) {
        // The classic raised app bar: a surface with a 4dp shadow that also covers the status bar, as on nested screens.
        Box(
            Modifier.fillMaxWidth().zIndex(1f).shadow(4.dp).background(beyGramPalette().surface)
        ) {
            Box(Modifier.windowInsetsPadding(WindowInsets.statusBars)) { content() }
        }
        return
    }
    if (!design.usesGlassPanels) {
        content()
        return
    }
    val palette = beyGramPalette()
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        GlassPanel(
            design = design,
            tint = palette.surface,
            rim = palette.textSecondary,
            backdropBase = palette.screen,
            shape = RectangleShape,
            edge = GlassEdge.BottomHairline,
            lensOnFreeEdge = backdrop != null,
            modifier = modifier.fillMaxWidth()
        ) {
            Box(Modifier.windowInsetsPadding(WindowInsets.statusBars)) { content() }
        }
    }
}

/**
 * When the V2 lens is available, the scroll content starts below the measured heading and scrolls behind it.
 * Only the content is recorded. The header is a sibling, and the recorded content cannot read this source (#623).
 * All other styles, qualities and API levels keep the fixed header above the list.
 */
@Composable
internal fun MainTabLayout(
    modifier: Modifier,
    header: @Composable () -> Unit,
    content: @Composable ColumnScope.(headerInset: Dp) -> Unit
) {
    val glass = LocalDesignSystem.current.surfaces.glass
    val drawsLens = glass?.lensRefraction == true && glass.samplesBackdrop &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    if (!drawsLens) {
        Column(modifier) {
            MainTabHeader(content = header)
            content(0.dp)
        }
        return
    }

    val backdrop = rememberGlassBackdrop()
    val density = LocalDensity.current
    var headerHeight by remember { mutableIntStateOf(0) }
    Box(modifier) {
        Column(Modifier.fillMaxSize().glassBackdropSource(backdrop)) {
            CompositionLocalProvider(LocalGlassBackdrop provides null) {
                content(with(density) { headerHeight.toDp() })
            }
        }
        MainTabHeader(
            modifier = Modifier.onSizeChanged { headerHeight = it.height }
                // The header's blank space must not let taps through to a row scrolling underneath.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
            backdrop = backdrop,
            content = header
        )
    }
}
