package com.beykus.beygram.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.navigation.MainTab
import com.beykus.beygram.navigation.TabVisibility
import com.beykus.beygram.ui.design.DesignSystem
import com.beykus.beygram.ui.design.GlassPanel
import com.beykus.beygram.ui.design.LocalGlassBackdrop
import com.beykus.beygram.ui.design.glassBackdropSource
import com.beykus.beygram.ui.design.glassHighlight
import com.beykus.beygram.ui.design.recordsGlassBackdrop
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.NavigationVariant
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.rememberMotionSpec
import com.beykus.beygram.ui.screens.playback.NowPlayingBar

private val TabPillWidth = 56.dp
private val TabPillHeight = 32.dp

/**
 * Where the bottom bar's selection indicator is, in dp. It is provided above the route content: the bar is rebuilt on
 * every tab change, but this holder outlives it, so the indicator keeps gliding from the old tab to the new one.
 */
internal class MainTabIndicator {
    val x = Animatable(0f)
    var placed = false
}

internal val LocalMainTabIndicator = staticCompositionLocalOf { MainTabIndicator() }

/** Подпись раздела в навигации: нижнем меню телефона или рейке широкого экрана. */
internal val MainTab.title: String
    @Composable get() = when (this) {
        MainTab.Chats -> stringResource(R.string.tabs_chats)
        MainTab.Calls -> stringResource(R.string.tabs_calls)
        MainTab.Contacts -> stringResource(R.string.tabs_contacts)
        MainTab.Settings -> stringResource(R.string.settings_title)
    }

/** Иконка раздела в навигации: нижнем меню телефона или рейке широкого экрана. */
internal val MainTab.icon: ImageVector
    get() = when (this) {
        MainTab.Chats -> Icons.Rounded.ChatBubble
        MainTab.Calls -> Icons.Rounded.Call
        MainTab.Contacts -> Icons.Rounded.Contacts
        MainTab.Settings -> Icons.Rounded.Settings
    }

/**
 * The measurements and the structure the selected design style gives the main navigation. Default reproduces the
 * original BeyGram bar exactly: a 56x32 pill, 12sp labels, a flat surface and no shadow.
 *
 * The spec joins the two axes: the style decides the contents of the bar ([pillWidth] and the rest) while the
 * placement decides where that bar is drawn ([floating]). Nothing here is style-specific layout logic, so a style
 * cannot grow its own second navigation.
 */
internal data class NavigationSpec(
    val pillWidth: Dp,
    val pillHeight: Dp,
    val pillRadius: Dp,
    val showSelectionPill: Boolean,
    val showLabels: Boolean,
    val labelSize: TextUnit,
    val labelWeight: FontWeight,
    val iconSize: Dp,
    val barTopPadding: Dp,
    val barBottomPadding: Dp,
    /** Shadow of the bar in the ordinary bottom slot. 0 keeps the flat Default bar exactly as it was. */
    val pinnedElevation: Dp,
    /** Shadow of the floating capsule, which has to read as a separate layer above the content. */
    val floatingElevation: Dp,
    val divider: Boolean,
    /** Side margin of the floating capsule. Glass and Signature do not borrow one value from each other. */
    val horizontalMargin: Dp,
    /** Gap the floating capsule keeps from the bottom edge, above the system navigation inset. */
    val bottomMargin: Dp,
    /** Outer radius of the floating capsule. A pinned bar ignores it and stays square at the window edges. */
    val dockRadius: Dp,
    /** Opacity multiplier of the selection pill: a quieter dock leaves the content as the loudest thing on screen. */
    val indicatorAlpha: Float,
    /** The resolved placement, read by both the bar and the scaffold; it is not the style's own stance. */
    val floating: Boolean,
    /** Weight of the selected label. Material 3 keeps the label weight and marks the selection with the indicator. */
    val selectedLabelWeight: FontWeight = FontWeight.SemiBold,
    /**
     * Colour role of the selected label. Material 3 draws it in the on-surface role while the icon takes the
     * on-indicator role; the other styles keep the accent ink for both.
     */
    val selectedLabelOnSurface: Boolean = false,
    /**
     * The selection is a short ink mark above the icon instead of a pill behind it ([markWidth] x [markHeight]); the
     * same shared indicator glides between tabs. Used by BeyGram Signature.
     */
    val indicatorMark: Boolean = false,
    val markWidth: Dp = 20.dp,
    val markHeight: Dp = 3.dp,
    /** The selected icon and label are drawn in the ink (text colour) rather than the accent. */
    val selectedInk: Boolean = false,
    /**
     * One hairline separates the bar from the content: on top of a pinned bar, around a floating capsule. The bar then
     * sits on the canvas colour when pinned, so the chrome reads as part of the screen and not as a second surface.
     */
    val hairline: Boolean = false,
    /**
     * Air between a tab label and the edges of its cell. Liquid Glass keeps some, so a long label does not run into the
     * curve of the capsule; the other styles keep their labels exactly as they were.
     */
    val labelSidePadding: Dp = 0.dp
) {
    /** The elevation that applies to the current placement. */
    val elevation: Dp get() = if (floating) floatingElevation else pinnedElevation
}

internal fun navigationSpecFor(design: DesignSystem): NavigationSpec = when (design.components.navigation) {
    // Default: the exact bar BeyGram has always drawn.
    NavigationVariant.PillTabs -> NavigationSpec(
        pillWidth = TabPillWidth, pillHeight = TabPillHeight, pillRadius = 16.dp,
        showSelectionPill = true, showLabels = true, labelSize = 12.sp,
        labelWeight = FontWeight.Normal, iconSize = 24.dp,
        barTopPadding = 10.dp, barBottomPadding = 8.dp, pinnedElevation = 0.dp, floatingElevation = 8.dp,
        divider = false,
        horizontalMargin = 12.dp, bottomMargin = 8.dp, dockRadius = 16.dp, indicatorAlpha = 1f,
        floating = design.navigationFloating
    )
    // Material 2: flat bar with a divider, coloured icon and label, no pill behind the selection.
    NavigationVariant.ClassicTabs -> NavigationSpec(
        pillWidth = TabPillWidth, pillHeight = TabPillHeight, pillRadius = 2.dp,
        showSelectionPill = false, showLabels = true, labelSize = 12.sp,
        labelWeight = FontWeight.Medium, iconSize = 24.dp,
        barTopPadding = 8.dp, barBottomPadding = 8.dp, pinnedElevation = 8.dp, floatingElevation = 8.dp,
        divider = true,
        horizontalMargin = 12.dp, bottomMargin = 8.dp, dockRadius = 2.dp, indicatorAlpha = 1f,
        floating = design.navigationFloating
    )
    // Material 3 navigation bar: an 80dp surface-container bar, a 64x32 full-radius indicator 12dp from the top,
    // a 4dp gap to a Medium 12sp label and 16dp below it. The label keeps its weight when selected and reads in the
    // on-surface role; the icon takes the on-indicator role.
    NavigationVariant.NavigationBar -> NavigationSpec(
        pillWidth = 64.dp, pillHeight = 32.dp, pillRadius = 16.dp,
        showSelectionPill = true, showLabels = true, labelSize = 12.sp,
        labelWeight = FontWeight.Medium, iconSize = 24.dp,
        barTopPadding = 12.dp, barBottomPadding = 16.dp, pinnedElevation = 0.dp, floatingElevation = 6.dp,
        divider = false,
        horizontalMargin = 12.dp, bottomMargin = 8.dp, dockRadius = 20.dp, indicatorAlpha = 1f,
        floating = design.navigationFloating,
        selectedLabelWeight = FontWeight.Medium,
        selectedLabelOnSurface = true
    )
    // Liquid Glass: the widest air around the capsule, a soft lens-radius and a lighter selection wash, so the panel
    // reads as a layer of material rather than a second full-width bar.
    NavigationVariant.FloatingGlass -> NavigationSpec(
        pillWidth = 58.dp, pillHeight = 30.dp, pillRadius = 20.dp,
        showSelectionPill = true, showLabels = true, labelSize = 11.sp,
        labelWeight = FontWeight.Medium, iconSize = 22.dp,
        barTopPadding = 9.dp, barBottomPadding = 9.dp,
        pinnedElevation = 0.dp,
        floatingElevation = design.surfaces.glass?.shadowElevation ?: 12.dp,
        divider = false,
        horizontalMargin = 16.dp, bottomMargin = 10.dp, dockRadius = 24.dp, indicatorAlpha = 0.82f,
        floating = design.navigationFloating,
        labelSidePadding = 4.dp
    )
    // Signature: no pill. The selected tab is drawn in ink with a short mark above it; pinned, the bar sits on the
    // canvas behind one hairline, floating, the same tabs sit in a calm capsule with a hairline rim and little shadow.
    NavigationVariant.SignatureMark -> NavigationSpec(
        pillWidth = 56.dp, pillHeight = 28.dp, pillRadius = 2.dp,
        showSelectionPill = true, showLabels = true, labelSize = 11.sp,
        labelWeight = FontWeight.Medium, iconSize = 22.dp,
        barTopPadding = 10.dp, barBottomPadding = 9.dp, pinnedElevation = 0.dp, floatingElevation = 3.dp,
        divider = false,
        horizontalMargin = 14.dp, bottomMargin = 9.dp, dockRadius = 20.dp, indicatorAlpha = 1f,
        floating = design.navigationFloating,
        selectedLabelWeight = FontWeight.Bold,
        indicatorMark = true,
        selectedInk = true,
        hairline = true
    )
}

/**
 * Один раздел нижнего меню: пилюля с иконкой и подпись под ней. Выбранный раздел подсвечен акцентом.
 * Нажатие показывает короткую акцентную капсулу той же формы вместо стандартного прямоугольного ripple;
 * [interactive] = false оставляет только отрисовку для превью в настройках. [selectionPill] = false отдаёт
 * фон выбранного раздела общему индикатору нижнего меню, который уезжает между разделами.
 */
@Composable
internal fun MainTabItem(
    tab: MainTab,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    pillHeight: Dp = TabPillHeight,
    iconSize: Dp = 24.dp,
    interactive: Boolean = true,
    selectionPill: Boolean = true
) {
    val design = LocalDesignSystem.current
    val spec = remember(design) { navigationSpecFor(design) }
    val palette = beyGramPalette()
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressSpec = rememberMotionSpec(MotionToken.MicroFeedback)
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = pressSpec.animationSpec(),
        label = "mainTabPress"
    )
    val tint = when {
        !selected -> palette.textSecondary
        spec.selectedInk -> palette.textPrimary
        else -> palette.accentInk
    }
    val labelTint = if (selected && spec.selectedLabelOnSurface) palette.textPrimary else tint
    val itemModifier = if (interactive) {
        modifier.selectable(
            selected = selected,
            interactionSource = interactionSource,
            indication = null,
            role = Role.Tab,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onSelect()
            }
        )
    } else {
        // Превью не нажимается, поэтому не должно обещать действие ни ripple, ни семантикой клика.
        modifier.semantics(mergeDescendants = true) {
            role = Role.Tab
            this.selected = selected
        }
    }
    val resolvedIconSize = if (iconSize == 24.dp) spec.iconSize else iconSize
    val resolvedPillHeight = if (pillHeight == TabPillHeight) spec.pillHeight else pillHeight
    val indicatorShape = RoundedCornerShape(spec.pillRadius)
    Column(
        modifier = itemModifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(spec.pillWidth)
                .height(resolvedPillHeight)
                .graphicsLayer {
                    val scale = tabPressScale(pressProgress)
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(indicatorShape)
                    .background(
                        if (selected && selectionPill && spec.showSelectionPill && !spec.indicatorMark) {
                            palette.accentSoft.copy(alpha = palette.accentSoft.alpha * spec.indicatorAlpha)
                        } else {
                            Color.Transparent
                        }
                    )
            )
            if (pressProgress > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(indicatorShape)
                        .background(tabPressCapsuleColor(palette.accent, selected, pressProgress))
                        .testTag(MainTabPressCapsuleTag)
                )
            }
            Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(resolvedIconSize))
            if (selected && selectionPill && spec.indicatorMark) {
                // A standalone item (the settings preview, the rail) has no shared indicator and no bar padding above
                // it, so it draws its own mark inside the icon box, where no parent can clip it.
                SelectionMark(spec, palette.textPrimary, Modifier.align(Alignment.TopCenter))
            }
        }
        if (spec.showLabels) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = tab.title,
                modifier = Modifier.padding(horizontal = spec.labelSidePadding),
                color = labelTint,
                fontSize = tabLabelSize(spec.labelSize, LocalDensity.current.fontScale),
                fontWeight = if (selected) spec.selectedLabelWeight else spec.labelWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Largest font scale a tab label follows. Four labels share a phone-wide bar, so beyond this a word like "Contacts" no
 * longer fits its tab; the icon and the TalkBack name carry the tab at larger sizes. Up to 1.5x nothing changes.
 */
internal const val MaxTabLabelFontScale = 1.5f

/** The label size in sp that renders no larger than [MaxTabLabelFontScale] times its nominal size. */
internal fun tabLabelSize(nominal: TextUnit, fontScale: Float): TextUnit =
    if (fontScale > MaxTabLabelFontScale) nominal * (MaxTabLabelFontScale / fontScale) else nominal

/** How far above the icon box the Signature mark sits, so it reads as a tab marker near the top edge of the bar. */
private fun markLift(spec: NavigationSpec): Dp = (spec.barTopPadding - 4.dp).coerceAtLeast(0.dp)

/** The Signature selection mark: a short rounded ink bar. */
@Composable
private fun SelectionMark(spec: NavigationSpec, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.size(width = spec.markWidth, height = spec.markHeight)
            .clip(RoundedCornerShape(spec.markHeight / 2))
            .background(color)
    )
}

/**
 * Background of the bar itself: the plain surface for Default, a tonal container for Material 3, a capsule for the
 * floating styles. A pinned Signature bar sits on the canvas, so the content and the chrome share one plane.
 */
@Composable
private fun navigationBarColor(design: DesignSystem, fallback: Color, canvas: Color): Color = when (design.components.navigation) {
    NavigationVariant.NavigationBar -> MaterialTheme.colorScheme.surfaceContainer
    NavigationVariant.SignatureMark -> if (design.navigationFloating) fallback else canvas
    else -> fallback
}

/**
 * Нижнее меню телефона: только видимые разделы, выше навигационной панели системы.
 *
 * Здесь живут оба размещения одного и того же бара: закреплённое на всю ширину в обычном нижнем слоте и плавающая
 * капсула поверх содержимого. Вкладки, обработчики и состояние выбора общие — меняется только адаптер поверхности.
 */
@Composable
internal fun MainTabBar(
    tabs: List<MainTab>,
    selected: MainTab?,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    /** Reports the measured height of the panel itself, without the floating margins around it. */
    onPanelHeightChange: ((Dp) -> Unit)? = null
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val spec = remember(design) { navigationSpecFor(design) }
    val glass = design.surfaces.glass
    val barColor = navigationBarColor(design, palette.surface, palette.screen)
    val floating = spec.floating
    val density = LocalDensity.current
    // Only the floating capsule is rounded: a pinned bar must keep square window edges even in Glass or Signature,
    // so no floating corner is left at the bottom of the screen.
    val barShape = RoundedCornerShape(if (floating) spec.dockRadius else 0.dp)
    val outer = modifier.fillMaxWidth().then(
        if (floating) {
            Modifier.padding(
                start = spec.horizontalMargin,
                end = spec.horizontalMargin,
                bottom = spec.bottomMargin
            )
        } else {
            Modifier
        }
    )
    val measurement = if (onPanelHeightChange != null) {
        // Placed before the insets and the paddings, so the reported size is the whole panel: font scale and a
        // three-button navigation bar change it, and the content gap must follow the real value, not a token.
        Modifier.onSizeChanged { onPanelHeightChange(with(density) { it.height.toDp() }) }
    } else {
        Modifier
    }
    val contentModifier = Modifier.fillMaxWidth().then(measurement).navigationBarsPadding()
        .padding(start = 8.dp, top = spec.barTopPadding, end = 8.dp, bottom = spec.barBottomPadding)
    val content: @Composable () -> Unit = {
        NavigationBarContent(
            modifier = contentModifier,
            spec = spec,
            tabs = tabs,
            selected = selected,
            onSelect = onSelect
        )
    }
    when {
        floating && glass != null -> GlassPanel(
            design = design,
            tint = barColor,
            rim = palette.textSecondary,
            // Gaps in the sampled content show the screen colour, never the sharp content under the dock.
            backdropBase = palette.screen,
            shape = barShape,
            modifier = outer.shadow(glass.shadowElevation, barShape)
        ) {
            content()
        }
        floating -> Box(
            outer.shadow(spec.elevation, barShape).clip(barShape).background(barColor)
                .then(if (spec.hairline) Modifier.border(1.dp, palette.divider, barShape) else Modifier)
        ) {
            content()
        }
        else -> Box(
            outer.then(
                if (spec.elevation > 0.dp) Modifier.shadow(spec.elevation, barShape) else Modifier
            ).background(barColor)
        ) {
            // A pinned glass bar keeps the material within the ordinary full-width bar: the same specular wash as the
            // dock under the tabs and the light edge as one hairline on top, instead of turning into a floating
            // capsule. Nothing is faked as blur: the surface under a pinned bar is the window, not the content.
            if (glass != null && glass.highlightAlpha > 0f) {
                Box(Modifier.matchParentSize().glassHighlight(glass.highlightAlpha * 0.6f))
            }
            content()
            if (spec.hairline) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
            }
            if (glass != null) {
                Box(
                    Modifier.fillMaxWidth().height(1.dp)
                        .background(palette.textSecondary.copy(alpha = glass.rimAlpha * 0.6f))
                )
            }
        }
    }
}

@Composable
private fun NavigationBarContent(
    spec: NavigationSpec,
    tabs: List<MainTab>,
    selected: MainTab?,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = beyGramPalette()
    val indicatorSpec = rememberMotionSpec(MotionToken.TabSelect)
    BoxWithConstraints(modifier) {
        val availableWidth = maxWidth
        val tabWidth = availableWidth / tabs.size.coerceAtLeast(1)
        val selectedIndex = tabs.indexOf(selected)
        val targetX = tabWidth * selectedIndex.coerceAtLeast(0) + (tabWidth - spec.pillWidth) / 2
        // The indicator is owned above the route content, so a tab change moves it rather than starting a new one.
        val indicator = LocalMainTabIndicator.current
        LaunchedEffect(targetX.value, indicatorSpec) {
            if (selectedIndex < 0) return@LaunchedEffect
            if (!spec.showSelectionPill) return@LaunchedEffect
            if (indicator.placed) {
                indicator.x.animateTo(targetX.value, indicatorSpec.animationSpec())
            } else {
                indicator.x.snapTo(targetX.value)
                indicator.placed = true
            }
        }
        Column {
            if (spec.divider) HorizontalDivider(color = palette.divider)
            // The indicator and the row share one Box, so the indicator sits behind the icons and never adds height.
            Box(Modifier.fillMaxWidth()) {
                if (selectedIndex >= 0 && spec.showSelectionPill && spec.indicatorMark) {
                    // The mark rides the same indicator as the pill, centred on the tab and lifted towards the top edge.
                    SelectionMark(
                        spec,
                        palette.textPrimary,
                        Modifier.offset(
                            x = indicator.x.value.dp + (spec.pillWidth - spec.markWidth) / 2,
                            y = -markLift(spec)
                        )
                    )
                } else if (selectedIndex >= 0 && spec.showSelectionPill) {
                    Box(
                        Modifier
                            .offset(x = indicator.x.value.dp)
                            .size(width = spec.pillWidth, height = spec.pillHeight)
                            .clip(RoundedCornerShape(spec.pillRadius))
                            .background(palette.accentSoft.copy(alpha = palette.accentSoft.alpha * spec.indicatorAlpha))
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Тот же набор взаимоисключающих вкладок, что и в рейке широкого экрана.
                        .selectableGroup()
                ) {
                    tabs.forEach { tab ->
                        MainTabItem(
                            tab = tab,
                            selected = tab == selected,
                            onSelect = { onSelect(tab) },
                            modifier = Modifier.weight(1f),
                            selectionPill = false
                        )
                    }
                }
            }
        }
    }
}

/**
 * Раздел с навигацией: содержимое занимает место над нижним меню на телефоне или справа от
 * вертикальной рейки на широком экране. [key] сохраняет состояние экрана при переключении
 * разделов, поэтому список чатов возвращается на прежнюю позицию.
 */
@Composable
internal fun MainTabsScaffold(
    key: String,
    selected: MainTab?,
    visibility: TabVisibility,
    onSelect: (MainTab) -> Unit,
    stateHolder: SaveableStateHolder,
    content: @Composable () -> Unit
) {
    val tabs = MainTab.entries.filter { visibility.isVisible(it) }
    val design = LocalDesignSystem.current
    val spec = remember(design) { navigationSpecFor(design) }
    val floating = design.navigationFloating
    // The floating capsule is laid over the content, so the scaffold reserves its measured height instead of a fixed
    // token: a larger label, a bigger font scale or a taller system inset then moves the last row instead of hiding it.
    // Reset with the design: a bar measured in another placement or style must not reserve space for the old one.
    var measuredBarHeight by remember(design) { mutableStateOf(0.dp) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isRail = usesMainNavigationRail(maxWidth.value)
        // One description of the navigation for every screen below, so the create button and the mini-player can line up
        // with the dock instead of repeating its numbers.
        val dock = NavigationDockGeometry(
            floating = floating && !isRail,
            height = measuredBarHeight,
            horizontalMargin = if (floating && !isRail) spec.horizontalMargin else 0.dp,
            bottomMargin = if (floating && !isRail) spec.bottomMargin else 0.dp
        )
        // The placement applies to the phone bottom bar. A wide window keeps its navigation rail: turning it into a
        // phone dock would move the sections away from the leading edge and lose the space the rail exists for.
        if (isRail) {
            Row(Modifier.fillMaxSize()) {
                MainTabRail(tabs = tabs, selected = selected, onSelect = onSelect)
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // Начало экрана уже заняла рейка: разделу не нужен ещё один ведущий отступ.
                        .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                        // Нижнего меню больше нет, поэтому содержимое и плеер сами держатся
                        // выше системной навигации.
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        CompositionLocalProvider(LocalNavigationDock provides dock) {
                            stateHolder.SaveableStateProvider(key) { content() }
                        }
                    }
                    NowPlayingBar(hideWhenImeVisible = true)
                }
            }
        } else if (floating) {
            // The floating placement puts the bar on top of the content, so the translucent material has something to
            // float above. The reserved gap covers the measured bar and the system navigation inset, so the last row
            // stays reachable instead of hiding under the panel.
            val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val backdrop = LocalGlassBackdrop.current
            // Only the content is recorded. The bar sits outside this subtree, so the panel never samples a layer
            // that is being written in the same pass - that self-reference is a native crash in the render thread.
            // Nothing is recorded unless the dock really blurs it: Balanced, Lite and old Android pay nothing.
            val recordContent = if (design.recordsGlassBackdrop && backdrop != null) {
                Modifier.glassBackdropSource(backdrop)
            } else {
                Modifier
            }
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    // Only the screen content is recorded: the mini-player keeps sampling it, and the bar itself stays
                    // outside the recorded subtree, so a panel can never sample the layer it is being drawn into.
                    Box(Modifier.weight(1f).fillMaxWidth().then(recordContent)) {
                        CompositionLocalProvider(LocalNavigationDock provides dock) {
                            stateHolder.SaveableStateProvider(key) { content() }
                        }
                    }
                    NowPlayingBar(hideWhenImeVisible = true)
                    // Until the bar has measured itself the token is a safe approximation; afterwards the real height
                    // is used, so a large font or a 3-button navigation inset cannot put a row under the dock. The gap
                    // between the mini-player and the capsule is the same one the list gets.
                    val reserve = measuredBarHeight.takeIf { it > 0.dp }?.let {
                        it + spec.bottomMargin + DefaultNavigationDockGap
                    } ?: (design.spacing.navigationHeight + navigationBarInset + spec.bottomMargin + DefaultNavigationDockGap)
                    Spacer(Modifier.height(reserve))
                }
                MainTabBar(
                    tabs = tabs,
                    selected = selected,
                    onSelect = onSelect,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onPanelHeightChange = { measuredBarHeight = it }
                )
            }
        } else {
            // A pinned bar has nothing but the window behind it, so the whole content is not recorded here. Glass
            // chrome inside a screen (the chat list header) records only its own list, never this subtree.
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    CompositionLocalProvider(LocalNavigationDock provides dock) {
                        stateHolder.SaveableStateProvider(key) { content() }
                    }
                }
                NowPlayingBar(hideWhenImeVisible = true)
                MainTabBar(tabs = tabs, selected = selected, onSelect = onSelect)
            }
        }
    }
}
