package com.beykus.beygram.ui.screens.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.ui.components.NavigationPlacement
import com.beykus.beygram.ui.components.WindowLayout
import com.beykus.beygram.ui.components.beyGramPalette

/**
 * Ширина окна, начиная с которой список чатов и открытый чат помещаются рядом: планшет,
 * развёрнутый складной телефон или широкое окно. Телефон в портретной ориентации остаётся однопанельным.
 */
internal const val ChatsTwoPaneMinWidthDp = 600f

/** Доля окна, которую занимает список в двухпанельном режиме. */
private const val ChatsListPaneWidthFraction = 0.4f

/** Уже этого список чатов становится тесным: строка, поиск и папки перестают помещаться. */
private const val ChatsListPaneMinWidthDp = 300f

/** Шире этого список забирает место у переписки без пользы: строка чата и так короткая. */
private const val ChatsListPaneMaxWidthDp = 380f

/** Помещаются ли список и чат рядом при ширине [widthDp]. */
internal fun isTwoPaneChatsWidth(widthDp: Float): Boolean = widthDp >= ChatsTwoPaneMinWidthDp

/** Ширина списка при малой высоте окна: переписке нужнее ширина, чем списку. */
private const val ChatsListPaneMaxWidthShortWindowDp = 320f

/**
 * Ширина списка в двухпанельном режиме: 300–380 dp, остальное достаётся переписке. В коротком окне
 * (телефон в ландшафте) хватает 300–320 dp.
 */
internal fun chatListPaneWidthDp(widthDp: Float, heightCompact: Boolean = false): Float =
    (widthDp * ChatsListPaneWidthFraction).coerceIn(
        ChatsListPaneMinWidthDp,
        if (heightCompact) ChatsListPaneMaxWidthShortWindowDp else ChatsListPaneMaxWidthDp
    )

/**
 * Подсвеченный чат в списке. В однопанельном режиме строка не выделяется: открытый чат и так занимает
 * весь экран, а в двухпанельном список показывает, какая переписка действительно открыта справа.
 * [chatResolved] = false означает, что правая панель ещё не нашла чат и показывает заглушку
 * «Выберите чат»: в этот момент ни одна строка не должна оставаться подсвеченной или selected.
 * После возврата к списку, пока правая панель пуста, подсветки тоже нет.
 */
internal fun chatListHighlightedId(
    showChat: Boolean,
    selectedChatId: Long,
    chatResolved: Boolean = true
): Long? = selectedChatId.takeIf { showChat && chatResolved && it != 0L }

/**
 * Две панели: список чатов слева, открытый чат справа. Навигация по разделам видна всегда, пока чат
 * открыт: левая часть стабильна и не прыгает при выборе чата.
 *
 * Форма навигации зависит от окна ([WindowLayout.navigation]): узкая рейка у телефона в ландшафте,
 * рейка с подписями у широкого окна, нижнее меню стиля внутри панели списка (его строит вызывающий в [list])
 * у раскрытого складного. Мини-плеер живёт в левой колонке под списком и в чате отдельной строкой не показывается.
 */
@Composable
internal fun ChatsTwoPaneScaffold(
    layout: WindowLayout,
    listPaneWidth: Dp,
    navigationRail: @Composable () -> Unit,
    playerBar: @Composable () -> Unit,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit
) {
    if (!layout.usesRail) {
        // Нижнее меню и плеер стоят внутри [list] (панель списка): оно само держится выше системной навигации.
        ChatsPanes(
            modifier = Modifier.fillMaxSize(),
            listPaneWidth = listPaneWidth,
            listColumn = { Box(Modifier.fillMaxSize()) { list() } },
            detail = detail
        )
        return
    }
    val slim = layout.navigation == NavigationPlacement.SlimRail
    Row(Modifier.fillMaxSize()) {
        navigationRail()
        ChatsPanes(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                // Ведущий отступ заняла рейка; строка состояния и конец экрана остаются панелям.
                .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
            listPaneWidth = listPaneWidth,
            listColumn = {
                Column(
                    Modifier
                        .fillMaxSize()
                        // Нижнего меню нет, поэтому список и плеер держатся выше системной навигации.
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { list() }
                    // В узкой рейке плеер — круглая обложка внизу рейки, высоты он не отнимает.
                    if (!slim) playerBar()
                }
            },
            detail = detail
        )
    }
}

/** Список, разделитель и переписка. */
@Composable
private fun ChatsPanes(
    modifier: Modifier,
    listPaneWidth: Dp,
    listColumn: @Composable () -> Unit,
    detail: @Composable () -> Unit
) {
    val palette = beyGramPalette()
    Row(modifier) {
        Box(Modifier.width(listPaneWidth).fillMaxHeight()) { listColumn() }
        Box(Modifier.fillMaxHeight().width(1.dp).background(palette.divider))
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                // The chat owns the end inset; the start one already belongs to the list pane/rail.
                .consumeWindowInsets(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Start)
                )
        ) { detail() }
    }
}

/**
 * Пустая правая панель, пока чат не выбран. Это не ошибка и не загрузка: подсказка объясняет,
 * что список остаётся слева и переписка откроется рядом.
 */
@Composable
internal fun ChatsDetailPlaceholder(modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.screen)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(palette.surface2),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.ChatBubbleOutline,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.chats_detail_empty_title),
                color = palette.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.chats_detail_empty_hint),
                color = palette.textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Где живёт действие «Новый чат». */
internal enum class CreateChatPlacement {
    /** Плавающая кнопка над нижним меню: телефон и нижнее меню внутри панели списка. */
    Fab,

    /** Верх навигационной рейки: Default, Material 2, Material 3. */
    RailTop,

    /** Значок в строке поиска: Стекло и Signature, у которых шапка списка и есть панель действий. */
    Header
}

/**
 * Плавающая кнопка закрывает строки узкой панели, поэтому рядом с рейкой её нет. [headerStyle] = true у стиля, чья
 * шапка списка несёт действие сама (стеклянная панель, Signature).
 */
internal fun createChatPlacement(layout: WindowLayout, twoPaneList: Boolean, headerStyle: Boolean): CreateChatPlacement = when {
    !twoPaneList || !layout.usesRail -> CreateChatPlacement.Fab
    headerStyle -> CreateChatPlacement.Header
    else -> CreateChatPlacement.RailTop
}
