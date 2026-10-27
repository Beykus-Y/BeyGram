package com.beykus.beygram.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beykus.beygram.navigation.MainTab

/**
 * Ширина окна, начиная с которой разделы приложения стоят вертикальной рейкой слева, а не
 * нижним меню. Совпадает с порогом двухпанельного списка чатов: на такой ширине место есть
 * и для рейки, и для списка, и для переписки.
 */
internal const val MainNavigationRailMinWidthDp = 600f

/** Ширина рейки без системных отступов: иконка и подпись раздела помещаются без обрезки. */
internal val MainNavigationRailWidth: Dp = 88.dp

/** Нужна ли вертикальная рейка при ширине окна [widthDp]. */
internal fun usesMainNavigationRail(widthDp: Float): Boolean = widthDp >= MainNavigationRailMinWidthDp

/** Тег рейки для тестов: нижнее меню и рейка используют одни и те же разделы. */
internal const val MainTabRailTag = "MainTabRail"

/**
 * Вертикальная рейка разделов для планшетов, развёрнутых складных телефонов и широких окон.
 * Это та же [MainTabItem], что и в нижнем меню, поэтому выбранное состояние, акцентная капсула
 * нажатия, размер цели 48 dp и семантика вкладки для TalkBack остаются одинаковыми.
 *
 * Рейка сама занимает ведущий системный отступ и рисует фон под строками состояния: содержимое
 * справа начинается уже после неё, поэтому начало экрана ему больше не нужно. Клавиатура (IME)
 * сюда не входит: она не должна сжимать или прокручивать навигацию.
 *
 * В коротком широком окне (split-screen, freeform) все вкладки не помещаются по высоте, поэтому
 * рейка прокручивается: нижние разделы остаются достижимыми, как и в прежнем горизонтальном меню.
 */
@Composable
internal fun MainTabRail(
    tabs: List<MainTab>,
    selected: MainTab?,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = beyGramPalette()
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(palette.surface)
            .windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)
            )
            .width(MainNavigationRailWidth)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
            // Один набор взаимоисключающих вкладок: TalkBack читает выбранный раздел.
            .selectableGroup()
            .testTag(MainTabRailTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEach { tab ->
            MainTabItem(
                tab = tab,
                selected = tab == selected,
                onSelect = { onSelect(tab) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
