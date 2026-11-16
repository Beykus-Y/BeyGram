package com.beykus.beygram.ui.screenshots

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.data.appearance.NavigationPlacement
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.navigation.MainTab
import com.beykus.beygram.navigation.TabVisibility
import com.beykus.beygram.ui.components.MainTabsScaffold
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.designFor
import com.beykus.beygram.ui.screens.chats.ChatListItem
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot goldens of the main screen for every design style and both manual bar placements (#634).
 *
 * The screen is the real navigation scaffold and real chat rows over fixed data, so a change to the bar, the
 * placement logic, the row geometry or a style's tokens shows up as a pixel diff. Goldens are recorded in CI on Linux
 * (see docs/screenshot-tests.md); Robolectric's native graphics are not a GPU, so blur and device rendering crashes are
 * still checked on a phone.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// SDK 34 runs on the JDK 17 used by CI; Robolectric needs JDK 21 for 35+. A plain Application keeps TDLib and the
// native media libraries out of the test process.
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xhdpi", application = Application::class)
class StyleMatrixScreenshotTest(
    private val style: DesignStyle,
    private val placement: NavigationPlacement
) {
    @get:Rule
    val compose = createComposeRule()

    private val name = "${style.name}_${placement.name}"

    @Test
    fun light() = capture(dark = false, fontScale = 1f, suffix = "light")

    @Test
    fun dark() = capture(dark = true, fontScale = 1f, suffix = "dark")

    /** The largest system text size: the bar grows, and the measured reserve has to grow with it. */
    @Test
    fun largeText() = capture(dark = false, fontScale = 2f, suffix = "font200")

    /** The last chat row ends above the tabs in both placements and at both text sizes, so nothing hides under the bar. */
    @Test
    fun lastRowStaysAboveTheBar() {
        for (fontScale in listOf(1f, 2f)) {
            setScreen(dark = false, fontScale = fontScale)
            compose.onNodeWithTag(ListTag).performScrollToIndex(SampleChats.lastIndex)
            compose.waitForIdle()
            val rowBottom = compose.onNodeWithTag(LastRowTag).fetchSemanticsNode().boundsInRoot.bottom
            val tabs = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
                .fetchSemanticsNodes()
            assertTrue("$name: no tabs found", tabs.size == MainTab.entries.size)
            val tabsTop = tabs.minOf { it.boundsInRoot.top }
            assertTrue(
                "$name at fontScale $fontScale: last row ends at $rowBottom, tabs start at $tabsTop",
                rowBottom <= tabsTop
            )
        }
    }

    private fun capture(dark: Boolean, fontScale: Float, suffix: String) {
        setScreen(dark, fontScale)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/${name}_$suffix.png")
    }

    private data class Look(val dark: Boolean, val fontScale: Float)

    private val look = mutableStateOf<Look?>(null)

    /** setContent may run once per rule, so later looks are swapped through a state the screen reads. */
    private fun setScreen(dark: Boolean, fontScale: Float) {
        if (look.value == null) {
            look.value = Look(dark, fontScale)
            compose.setContent {
                look.value?.let { MainScreenSample(style, placement, it.dark, it.fontScale) }
            }
        } else {
            look.value = Look(dark, fontScale)
        }
        compose.waitForIdle()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun cases(): List<Array<Any>> = DesignStyle.entries.flatMap { style ->
            listOf(NavigationPlacement.Pinned, NavigationPlacement.Floating).map { arrayOf<Any>(style, it) }
        }
    }
}

private const val ListTag = "screenshot-chat-list"
private const val LastRowTag = "screenshot-last-row"

@Composable
private fun MainScreenSample(style: DesignStyle, placement: NavigationPlacement, dark: Boolean, fontScale: Float) {
    // Balanced keeps Liquid Glass deterministic: High depends on the API level and the device, which a golden cannot.
    val design = designFor(style, GlassQuality.Balanced, placement)
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        BeyGramTheme(darkTheme = dark, design = design) {
            val palette = beyGramPalette()
            Box(Modifier.fillMaxSize().background(palette.screen)) {
                MainTabsScaffold(
                    key = "chats",
                    selected = MainTab.Chats,
                    visibility = TabVisibility(),
                    onSelect = {},
                    stateHolder = rememberSaveableStateHolder()
                ) {
                    LazyColumn(Modifier.fillMaxSize().testTag(ListTag)) {
                        itemsIndexed(SampleChats, key = { _, chat -> chat.id }) { index, chat ->
                            Box(if (index == SampleChats.lastIndex) Modifier.testTag(LastRowTag) else Modifier) {
                                ChatListItem(
                                    chat = chat,
                                    observeFile = { id -> MutableStateFlow(MediaFileState(id)) },
                                    downloadFile = {},
                                    onClick = {}
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Fixed chats: pinned, unread, muted, a long name and a marked-unread row, the cases the issue asks to cover. */
internal val SampleChats: List<ChatPreview> = listOf(
    chat(1, "Анна Смирнова", "АС", "Увидимся завтра в десять?", "12:41", unread = 2, color = 0xFFE57373, pinned = true),
    chat(2, "Рабочая группа", "РГ", "Иван: макеты готовы, смотрите", "12:30", unread = 128, color = 0xFF64B5F6, pinned = true),
    chat(3, "Максимилиан Александрович Константинопольский", "МК", "Очень длинное имя не должно ломать строку", "11:02", color = 0xFF81C784),
    chat(4, "Новости BeyGram", "NB", "Вышло обновление с новыми стилями", "10:15", unread = 5, color = 0xFFFFB74D, muted = true),
    chat(5, "Мама", "М", "Позвони, когда освободишься", "09:58", color = 0xFFBA68C8, markedUnread = true),
    chat(6, "Dmitry", "D", "ok, sent the file", "Вт", color = 0xFF4DB6AC),
    chat(7, "Семья", "С", "Фото", "Пн", color = 0xFFF06292),
    chat(8, "Книжный клуб", "КК", "Ольга: следующая встреча в субботу", "Вс", unread = 1, color = 0xFF9575CD),
    chat(9, "Олег", "О", "Спасибо!", "Сб", color = 0xFF7986CB),
    chat(10, "Музыка", "М", "Плейлист обновлён", "Пт", color = 0xFFA1887F),
    chat(11, "Поддержка", "П", "Мы ответим в течение дня", "28.10", color = 0xFF90A4AE),
    chat(12, "Последний чат", "ПЧ", "Эта строка должна быть видна над панелью", "27.10", unread = 3, color = 0xFFFF8A65)
)

private fun chat(
    id: Long,
    title: String,
    initials: String,
    message: String,
    time: String,
    unread: Int = 0,
    color: Long,
    pinned: Boolean = false,
    muted: Boolean = false,
    markedUnread: Boolean = false
) = ChatPreview(
    id = id,
    title = title,
    initials = initials,
    lastMessage = UiText.Raw(message),
    lastMessageTime = time,
    unreadCount = unread,
    avatarColorArgb = color,
    isPinned = pinned,
    isMuted = muted,
    isMarkedAsUnread = markedUnread
)
