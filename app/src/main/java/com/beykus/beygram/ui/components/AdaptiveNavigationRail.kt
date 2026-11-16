package com.beykus.beygram.ui.components

import com.beykus.beygram.ui.design.TopBarVariant
import com.beykus.beygram.ui.design.NavigationVariant
import com.beykus.beygram.ui.design.LocalDesignSystem
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.ui.design.usesGlassPanels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

/** Material 2 draws its rail 72 dp wide; the other styles keep 80 dp (the 88 dp default is the upper bound). */
internal fun mainNavigationRailWidth(style: DesignStyle): Dp = if (style == DesignStyle.Material2) 72.dp else 80.dp

/** Узкая рейка телефона в ландшафте: только иконки, ширина дешевле высоты. */
internal val MainNavigationSlimRailWidth: Dp = 56.dp

/** Нужна ли вертикальная рейка при ширине окна [widthDp]. */
internal fun usesMainNavigationRail(widthDp: Float): Boolean = widthDp >= MainNavigationRailMinWidthDp

/**
 * Действие «Новый чат», которое рейка носит наверху у Default, Material 2 и Material 3: плавающей кнопке рядом с узкой
 * рейкой места нет, она закрывала бы строки. Корень приложения кладёт сюда обработчик; без него слота нет.
 */
val LocalRailCreateChat = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Верхний слот рейки: каноническое место кнопки создания в Navigation Rail Material 3. */
@Composable
private fun RailCreateChatAction(slim: Boolean, onClick: () -> Unit) {
    val design = LocalDesignSystem.current
    val palette = beyGramPalette()
    val label = stringResource(R.string.new_chat_title)
    val flat = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
    when (design.style) {
        // Tonal, 56 dp on the labelled rail and 40 dp on the slim one.
        DesignStyle.Material3 -> FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(if (slim) 40.dp else 56.dp),
            containerColor = palette.accentSoft,
            contentColor = palette.accentInk,
            shape = RoundedCornerShape(if (slim) 12.dp else 16.dp),
            elevation = flat
        ) { Icon(Icons.Rounded.Edit, label, modifier = Modifier.size(24.dp)) }
        // A round mini button with the elevation Material 2 draws under every raised action.
        DesignStyle.Material2 -> FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp),
            containerColor = palette.accent,
            contentColor = palette.onAccent,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(design.fab.elevation)
        ) { Icon(Icons.Rounded.Edit, label, modifier = Modifier.size(20.dp)) }
        else -> IconButton(onClick = onClick) {
            Icon(Icons.Rounded.Edit, label, tint = palette.accentInk, modifier = Modifier.size(24.dp))
        }
    }
}

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
    modifier: Modifier = Modifier,
    /** Icons only, 56 dp wide: the form for a window shorter than 480 dp. */
    slim: Boolean = false,
    /** The slot at the top of the rail (the create-chat action in the styles that put it there). */
    topContent: (@Composable () -> Unit)? = null,
    /** The slot at the foot of the rail (the round mini-player cover of the slim rail). */
    bottomContent: (@Composable () -> Unit)? = null
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    // The rail is the tablet form of the bottom bar, so it takes the same surface: the tonal container of the Material 3
    // navigation bar, the elevated edge of Material 2 and the plain surface everywhere else (Default unchanged).
    val railColor = when {
        design.components.navigation == NavigationVariant.NavigationBar -> MaterialTheme.colorScheme.surfaceContainer
        // Signature chrome stands on the canvas behind one hairline, like every other bar of the style.
        design.style == DesignStyle.Signature -> palette.screen
        else -> palette.surface
    }
    val hairline = palette.divider.takeIf {
        design.style == DesignStyle.Signature || design.usesGlassPanels
    }
    val railElevation = if (design.components.topBar == TopBarVariant.Elevated) design.surfaces.barElevation else 0.dp
    // Glass and Signature carry the action in the search row of the list; every other style carries it here.
    val createChat = LocalRailCreateChat.current
        ?.takeIf { !(design.usesGlassPanels || design.style == DesignStyle.Signature) }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .then(
                if (railElevation > 0.dp) Modifier.zIndex(1f).shadow(railElevation, RectangleShape, clip = false)
                else Modifier
            )
            .background(railColor)
            .then(
                if (hairline != null) Modifier.drawWithContent {
                    drawContent()
                    val stroke = 1.dp.toPx()
                    drawLine(hairline, Offset(size.width - stroke / 2, 0f), Offset(size.width - stroke / 2, size.height), stroke)
                } else Modifier
            )
            .windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)
            )
            .width(if (slim) MainNavigationSlimRailWidth else mainNavigationRailWidth(design.style)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = if (slim) 6.dp else 12.dp)
                // Один набор взаимоисключающих вкладок: TalkBack читает выбранный раздел.
                .selectableGroup()
                .testTag(MainTabRailTag),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (slim) 2.dp else 4.dp)
        ) {
            if (createChat != null) RailCreateChatAction(slim, createChat)
            topContent?.invoke()
            tabs.forEach { tab ->
                MainTabItem(
                    tab = tab,
                    selected = tab == selected,
                    onSelect = { onSelect(tab) },
                    modifier = Modifier.fillMaxWidth(),
                    showLabel = !slim,
                    pillWidthOverride = if (slim) 48.dp else null,
                    verticalMark = true
                )
            }
        }
        bottomContent?.let { Box(Modifier.padding(bottom = 6.dp)) { it() } }
    }
}
