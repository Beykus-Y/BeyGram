package com.beykus.beygram.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** Где живёт навигация по разделам при данном размере окна. */
enum class NavigationPlacement {
    /** Нижнее меню (телефон в портрете). */
    BottomBar,

    /** Узкая рейка только с иконками (телефон в ландшафте): высота дороже ширины. */
    SlimRail,

    /** Рейка с подписями (планшет, широкое окно). */
    Rail,

    /** Нижнее меню стиля внутри панели списка (раскрытый складной, планшет 8" в портрете). */
    BottomBarInListPane
}

/**
 * Класс окна по двум осям. Раскладка выбирается не по ориентации, а по размеру окна, поэтому
 * split-screen и свободные окна работают так же правильно, как телефон в ландшафте.
 * Считается один раз в корне приложения и раздаётся через [LocalWindowLayout].
 */
@Immutable
data class WindowLayout(val widthDp: Float, val heightDp: Float) {
    /** Список чатов и чат стоят рядом. */
    val twoPane: Boolean get() = widthDp >= TwoPaneMinWidthDp

    /** Мало высоты: каждый хром-элемент должен быть компактным. */
    val heightCompact: Boolean get() = heightDp in 1f..(HeightCompactBelowDp - 0.001f)

    /** Широкое окно с обычной высотой: рейка с подписями. */
    val widthExpanded: Boolean get() = widthDp >= RailLabelledMinWidthDp

    val navigation: NavigationPlacement
        get() = when {
            !twoPane -> NavigationPlacement.BottomBar
            heightCompact -> NavigationPlacement.SlimRail
            widthExpanded -> NavigationPlacement.Rail
            else -> NavigationPlacement.BottomBarInListPane
        }

    /** Навигация стоит вертикальной рейкой у ведущего края окна. */
    val usesRail: Boolean
        get() = navigation == NavigationPlacement.SlimRail || navigation == NavigationPlacement.Rail

    companion object {
        const val TwoPaneMinWidthDp = 600f
        const val HeightCompactBelowDp = 480f
        const val RailLabelledMinWidthDp = 840f

        /** Телефон в портрете: значение по умолчанию для превью и тестов. */
        val Phone = WindowLayout(393f, 851f)
    }
}

val LocalWindowLayout = staticCompositionLocalOf { WindowLayout.Phone }
