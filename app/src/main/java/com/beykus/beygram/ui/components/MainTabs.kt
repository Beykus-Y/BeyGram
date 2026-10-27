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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.navigation.MainTab
import com.beykus.beygram.navigation.TabVisibility
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
    Column(
        modifier = itemModifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(TabPillWidth)
                .height(pillHeight)
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected && selectionPill) palette.accentSoft else Color.Transparent)
            )
            if (pressProgress > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(tabPressCapsuleColor(palette.accent, selected, pressProgress))
                        .testTag(MainTabPressCapsuleTag)
                )
            }
            Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = tab.title,
            color = tint,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/** Нижнее меню телефона: только видимые разделы, выше навигационной панели системы. */
@Composable
internal fun MainTabBar(tabs: List<MainTab>, selected: MainTab?, onSelect: (MainTab) -> Unit) {
    val palette = beyGramPalette()
    val indicatorSpec = rememberMotionSpec(MotionToken.TabSelect)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .navigationBarsPadding()
            .padding(start = 8.dp, top = 10.dp, end = 8.dp, bottom = 8.dp)
    ) {
        val tabWidth = maxWidth / tabs.size.coerceAtLeast(1)
        val selectedIndex = tabs.indexOf(selected)
        val targetX = tabWidth * selectedIndex.coerceAtLeast(0) + (tabWidth - TabPillWidth) / 2
        // The indicator is owned above the route content, so a tab change moves it rather than starting a new one.
        val indicator = LocalMainTabIndicator.current
        LaunchedEffect(targetX.value, indicatorSpec) {
            if (selectedIndex < 0) return@LaunchedEffect
            if (indicator.placed) {
                indicator.x.animateTo(targetX.value, indicatorSpec.animationSpec())
            } else {
                indicator.x.snapTo(targetX.value)
                indicator.placed = true
            }
        }
        // The indicator sits behind the row, so the layout never moves.
        if (selectedIndex >= 0) {
            Box(
                Modifier
                    .offset(x = indicator.x.value.dp)
                    .size(width = TabPillWidth, height = TabPillHeight)
                    .clip(RoundedCornerShape(16.dp))
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
                    NowPlayingBar()
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    stateHolder.SaveableStateProvider(key) { content() }
                }
                NowPlayingBar()
                MainTabBar(tabs = tabs, selected = selected, onSelect = onSelect)
            }
        }
    }
}
