package com.beykus.beygram.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.navigation.MainTab
import com.beykus.beygram.navigation.TabVisibility
import com.beykus.beygram.ui.design.DesignSystem
import com.beykus.beygram.ui.design.GlassPanel
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
    val barElevation: Dp,
    val divider: Boolean,
    val floating: Boolean
)

internal fun navigationSpecFor(design: DesignSystem): NavigationSpec = when (design.components.navigation) {
    // Default: the exact bar BeyGram has always drawn.
    NavigationVariant.PillTabs -> NavigationSpec(
        pillWidth = TabPillWidth, pillHeight = TabPillHeight, pillRadius = 16.dp,
        showSelectionPill = true, showLabels = true, labelSize = 12.sp,
        labelWeight = FontWeight.Normal, iconSize = 24.dp,
        barTopPadding = 10.dp, barBottomPadding = 8.dp, barElevation = 0.dp,
        divider = false, floating = false
    )
    // Material 2: flat bar with a divider, coloured icon and label, no pill behind the selection.
    NavigationVariant.ClassicTabs -> NavigationSpec(
        pillWidth = TabPillWidth, pillHeight = TabPillHeight, pillRadius = 2.dp,
        showSelectionPill = false, showLabels = true, labelSize = 12.sp,
        labelWeight = FontWeight.Medium, iconSize = 24.dp,
        barTopPadding = 8.dp, barBottomPadding = 8.dp, barElevation = 8.dp,
        divider = true, floating = false
    )
    // Material 3: the navigation bar with a tonal container and a wider indicator pill.
    NavigationVariant.NavigationBar -> NavigationSpec(
        pillWidth = 64.dp, pillHeight = 32.dp, pillRadius = 16.dp,
        showSelectionPill = true, showLabels = true, labelSize = 12.sp,
        labelWeight = FontWeight.Medium, iconSize = 24.dp,
        barTopPadding = 12.dp, barBottomPadding = 12.dp, barElevation = 0.dp,
        divider = false, floating = false
    )
    // Liquid Glass: a floating translucent capsule with compact labels.
    NavigationVariant.FloatingGlass -> NavigationSpec(
        pillWidth = 60.dp, pillHeight = 30.dp, pillRadius = 20.dp,
        showSelectionPill = true, showLabels = true, labelSize = 11.sp,
        labelWeight = FontWeight.Medium, iconSize = 22.dp,
        barTopPadding = 10.dp, barBottomPadding = 10.dp, barElevation = 12.dp,
        divider = false, floating = true
    )
    // Signature: a floating dock with a confident, single selection capsule.
    NavigationVariant.SignatureDock -> NavigationSpec(
        pillWidth = 58.dp, pillHeight = 30.dp, pillRadius = 12.dp,
        showSelectionPill = true, showLabels = true, labelSize = 11.sp,
        labelWeight = FontWeight.Bold, iconSize = 23.dp,
        barTopPadding = 8.dp, barBottomPadding = 10.dp, barElevation = 6.dp,
        divider = false, floating = true
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
    val tint = if (selected) palette.accentInk else palette.textSecondary
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
                    .background(if (selected && selectionPill && spec.showSelectionPill) palette.accentSoft else Color.Transparent)
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
        }
        if (spec.showLabels) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = tab.title,
                color = tint,
                fontSize = spec.labelSize,
                fontWeight = if (selected) FontWeight.SemiBold else spec.labelWeight,
                maxLines = 1
            )
        }
    }
}

/** Background of the bar itself: the plain surface for Default, a tonal container for Material 3, a capsule for the floating styles. */
@Composable
private fun navigationBarColor(design: DesignSystem, fallback: Color): Color = when (design.components.navigation) {
    NavigationVariant.NavigationBar -> MaterialTheme.colorScheme.surfaceContainer
    NavigationVariant.FloatingGlass, NavigationVariant.SignatureDock -> fallback
    else -> fallback
}

/** Нижнее меню телефона: только видимые разделы, выше навигационной панели системы. */
@Composable
internal fun MainTabBar(
    tabs: List<MainTab>,
    selected: MainTab?,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val spec = remember(design) { navigationSpecFor(design) }
    val glass = design.surfaces.glass
    val barColor = navigationBarColor(design, palette.surface)
    val floating = spec.floating
    val barShape = RoundedCornerShape(if (floating) design.shapes.extraLarge else design.shapes.extraSmall)
    val outer = modifier.fillMaxWidth().then(
        if (floating) Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp) else Modifier
    )
    if (floating && glass != null) {
        GlassPanel(
            design = design,
            tint = barColor,
            rim = palette.textSecondary,
            shape = barShape,
            modifier = outer.shadow(glass.shadowElevation, barShape)
        ) {
            NavigationBarContent(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(start = 8.dp, top = spec.barTopPadding, end = 8.dp, bottom = spec.barBottomPadding),
                spec = spec,
                tabs = tabs,
                selected = selected,
                onSelect = onSelect
            )
        }
    } else {
        Box(
            outer.then(
                if (floating) Modifier.shadow(spec.barElevation, barShape).clip(barShape).background(barColor)
                else Modifier.background(barColor)
            )
        ) {
            NavigationBarContent(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(start = 8.dp, top = spec.barTopPadding, end = 8.dp, bottom = spec.barBottomPadding),
                spec = spec,
                tabs = tabs,
                selected = selected,
                onSelect = onSelect
            )
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
                if (selectedIndex >= 0 && spec.showSelectionPill) {
                    Box(
                        Modifier
                            .offset(x = indicator.x.value.dp)
                            .size(width = spec.pillWidth, height = spec.pillHeight)
                            .clip(RoundedCornerShape(spec.pillRadius))
                            .background(palette.accentSoft)
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (usesMainNavigationRail(maxWidth.value)) {
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
                        stateHolder.SaveableStateProvider(key) { content() }
                    }
                    NowPlayingBar(hideWhenImeVisible = true)
                }
            }
        } else if (spec.floating) {
            // The floating styles put the bar on top of the content, so the translucent material has something to
            // float above. The reserved gap covers the bar and the system navigation inset, so the last row stays
            // reachable instead of hiding under the panel.
            val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        stateHolder.SaveableStateProvider(key) { content() }
                    }
                    NowPlayingBar(hideWhenImeVisible = true)
                    Spacer(Modifier.height(design.spacing.navigationHeight + navigationBarInset))
                }
                MainTabBar(
                    tabs = tabs,
                    selected = selected,
                    onSelect = onSelect,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    stateHolder.SaveableStateProvider(key) { content() }
                }
                NowPlayingBar(hideWhenImeVisible = true)
                MainTabBar(tabs = tabs, selected = selected, onSelect = onSelect)
            }
        }
    }
}
