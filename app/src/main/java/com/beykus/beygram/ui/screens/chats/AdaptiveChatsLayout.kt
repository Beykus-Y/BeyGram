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
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.zIndex
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.ui.components.LocalWindowLayout
import com.beykus.beygram.ui.design.GlassPanel
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.usesGlassPanels
import com.beykus.beygram.ui.screens.chat.ChatWallpaperLayer
import com.beykus.beygram.ui.screens.chat.LocalChatWallpaper
import com.beykus.beygram.ui.components.WindowNavigation
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
    val slim = layout.navigation == WindowNavigation.SlimRail
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

/**
 * Список, разделитель и переписка. Разделитель зависит от стиля: линия у Default и Signature, тень списка у Material 2,
 * два скруглённых контейнера на широком окне у Material 3, светлый градиент ободка у Стекла.
 */
@Composable
private fun ChatsPanes(
    modifier: Modifier,
    listPaneWidth: Dp,
    listColumn: @Composable () -> Unit,
    detail: @Composable () -> Unit
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val layout = LocalWindowLayout.current
    val separator = chatPaneSeparator(design.style, design.usesGlassPanels, layout.widthExpanded)
    val detailBase = Modifier
        .fillMaxHeight()
        // The chat owns the end inset; the start one already belongs to the list pane/rail.
        .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
    when (separator) {
        ChatPaneSeparator.Cards -> Row(modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
            val card = RoundedCornerShape(20.dp)
            Box(Modifier.width(listPaneWidth).fillMaxHeight().padding(start = 0.dp, end = 4.dp, bottom = 8.dp).clip(card)) { listColumn() }
            Box(Modifier.weight(1f).then(detailBase).padding(start = 4.dp, end = 8.dp, bottom = 8.dp).clip(card)) { detail() }
        }
        else -> {
            // On a wide window the divider can be dragged and the width is remembered; elsewhere it is a plain line.
            val resizable = layout.widthExpanded && !layout.heightCompact
            val context = androidx.compose.ui.platform.LocalContext.current
            var dragged by remember {
                mutableStateOf(
                    runCatching {
                        context.getSharedPreferences("wide_layout", android.content.Context.MODE_PRIVATE)
                            .getFloat("list_pane_dp", 0f)
                    }.getOrDefault(0f).takeIf { it > 0f }
                )
            }
            val density = androidx.compose.ui.platform.LocalDensity.current
            BoxWithConstraints(modifier) {
                val maxList = (maxWidth.value - ChatsListPaneMinWidthDp).coerceAtLeast(ChatsListPaneMinWidthDp)
                val paneWidth = if (resizable) (dragged ?: listPaneWidth.value).coerceIn(ChatsListPaneMinWidthDp, maxList).dp else listPaneWidth
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalChatPaneWidthDp provides (maxWidth.value - paneWidth.value - if (resizable) 12f else 1f)
                ) {
                Row(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.width(paneWidth).fillMaxHeight()
                            .then(
                                if (separator == ChatPaneSeparator.Shadow) Modifier.zIndex(1f).shadow(3.dp, RectangleShape, clip = false)
                                else Modifier
                            )
                    ) { listColumn() }
                    Box(
                        Modifier.fillMaxHeight().width(if (resizable) 12.dp else 1.dp)
                            .then(
                                if (resizable) Modifier.pointerInput(maxList) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            dragged?.let { w ->
                                                runCatching {
                                                    context.getSharedPreferences("wide_layout", android.content.Context.MODE_PRIVATE)
                                                        .edit().putFloat("list_pane_dp", w).apply()
                                                }
                                            }
                                        }
                                    ) { change, dx ->
                                        change.consume()
                                        val now = (dragged ?: paneWidth.value) + dx / density.density
                                        dragged = now.coerceIn(ChatsListPaneMinWidthDp, maxList)
                                    }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (separator) {
                            ChatPaneSeparator.Line -> Box(Modifier.fillMaxHeight().width(1.dp).background(palette.divider))
                            ChatPaneSeparator.TonalLine -> Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            ChatPaneSeparator.RimGradient -> Box(
                                Modifier.fillMaxHeight().width(1.dp).background(
                                    Brush.verticalGradient(
                                        listOf(
                                            palette.textSecondary.copy(alpha = 0.34f),
                                            palette.textSecondary.copy(alpha = 0.08f)
                                        )
                                    )
                                )
                            )
                            else -> Unit
                        }
                    }
                    Box(Modifier.weight(1f).then(detailBase)) { detail() }
                }
                }
            }
        }
    }
}

/** The width the chat pane really has (after a drag of the divider); null outside the split layout. */
internal val LocalChatPaneWidthDp = androidx.compose.runtime.compositionLocalOf<Float?> { null }

/** How the list pane and the chat pane are told apart. */
internal enum class ChatPaneSeparator { Line, TonalLine, Shadow, Cards, RimGradient }

internal fun chatPaneSeparator(style: DesignStyle, glass: Boolean, widthExpanded: Boolean): ChatPaneSeparator = when {
    glass -> ChatPaneSeparator.RimGradient
    style == DesignStyle.Material2 -> ChatPaneSeparator.Shadow
    style == DesignStyle.Material3 -> if (widthExpanded) ChatPaneSeparator.Cards else ChatPaneSeparator.TonalLine
    else -> ChatPaneSeparator.Line
}

/**
 * Пустая правая панель, пока чат не выбран. Она рисует те же обои, что и открытый чат, поэтому при выборе чата
 * меняется содержимое, а не сам фон панели. Подсказка одна: «Выберите чат»; в низком окне остаются значок и строка.
 */
@Composable
internal fun ChatsDetailPlaceholder(modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val compact = LocalWindowLayout.current.heightCompact
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.screen)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            ),
        contentAlignment = Alignment.Center
    ) {
        ChatWallpaperLayer(LocalChatWallpaper.current)
        val title = stringResource(R.string.chats_detail_empty_title)
        val content: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.ChatBubbleOutline,
                    contentDescription = null,
                    tint = if (design.style == DesignStyle.Signature) palette.textPrimary else palette.accentInk,
                    modifier = Modifier.size(if (compact) 18.dp else 22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    color = palette.textPrimary,
                    fontSize = if (compact) 14.sp else 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
            }
        }
        val padding = Modifier.padding(horizontal = 18.dp, vertical = if (compact) 8.dp else 12.dp)
        when {
            // Material 2: the classic empty state, a grey glyph and a secondary line without a plate.
            design.style == DesignStyle.Material2 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.ChatBubbleOutline, null, tint = palette.textSecondary, modifier = Modifier.size(if (compact) 32.dp else 48.dp))
                Spacer(Modifier.height(8.dp))
                Text(title, color = palette.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() })
            }
            // Signature: ink on the canvas, no plate, no accent glyph.
            design.style == DesignStyle.Signature -> Box(padding) { content() }
            // Glass: a glass pill over the wallpaper (blurred, frosted or dense by quality).
            design.usesGlassPanels -> GlassPanel(
                design = design,
                tint = palette.surface,
                rim = palette.textSecondary,
                backdropBase = palette.screen,
                shape = CircleShape
            ) { Box(padding) { content() } }
            // Default and Material 3: a pill on the surface (Material 3 on the higher tonal container).
            else -> Box(
                Modifier.clip(CircleShape).background(
                    if (design.style == DesignStyle.Material3) MaterialTheme.colorScheme.surfaceContainerHigh else palette.surface
                ).then(padding)
            ) { content() }
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
