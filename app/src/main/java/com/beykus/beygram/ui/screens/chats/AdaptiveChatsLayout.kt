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

/** Ширина списка в двухпанельном режиме: 300–380 dp, остальное достаётся переписке. */
internal fun chatListPaneWidthDp(widthDp: Float): Float =
    (widthDp * ChatsListPaneWidthFraction).coerceIn(ChatsListPaneMinWidthDp, ChatsListPaneMaxWidthDp)

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
 * Две панели: список чатов слева, открытый чат справа. На широком экране разделы стоят
 * вертикальной рейкой у ведущего края, а мини-плеер остаётся под панелями. Когда чат открыт,
 * навигация не показывается — как и нижнее меню на телефоне: у чата есть собственный плеер
 * и поле ввода.
 */
@Composable
internal fun ChatsTwoPaneScaffold(
    listPaneWidth: Dp,
    showNavigation: Boolean,
    navigationRail: @Composable () -> Unit,
    playerBar: @Composable () -> Unit,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit
) {
    if (!showNavigation) {
        ChatsPanes(
            modifier = Modifier.fillMaxSize(),
            listPaneWidth = listPaneWidth,
            listKeepsClearOfNavigationBar = true,
            list = list,
            detail = detail
        )
        return
    }
    Row(Modifier.fillMaxSize()) {
        navigationRail()
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                // Ведущий отступ заняла рейка; строка состояния и конец экрана остаются панелям.
                .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                // Нижнего меню нет, поэтому и панели, и плеер держатся выше системной навигации.
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
        ) {
            ChatsPanes(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                listPaneWidth = listPaneWidth,
                listKeepsClearOfNavigationBar = false,
                list = list,
                detail = detail
            )
            playerBar()
        }
    }
}

/** Список, разделитель и переписка. [listKeepsClearOfNavigationBar] нужен, когда внизу нет меню. */
@Composable
private fun ChatsPanes(
    modifier: Modifier,
    listPaneWidth: Dp,
    listKeepsClearOfNavigationBar: Boolean,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit
) {
    val palette = beyGramPalette()
    Row(modifier) {
        Box(
            Modifier
                .width(listPaneWidth)
                .fillMaxHeight()
                .then(
                    if (listKeepsClearOfNavigationBar) Modifier.windowInsetsPadding(
                        WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                    ) else Modifier
                )
        ) { list() }
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
