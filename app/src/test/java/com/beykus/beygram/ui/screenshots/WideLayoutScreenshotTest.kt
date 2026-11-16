package com.beykus.beygram.ui.screenshots

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.data.appearance.NavigationPlacement
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.navigation.MainTab
import com.beykus.beygram.navigation.TabVisibility
import com.beykus.beygram.ui.components.LocalWindowLayout
import com.beykus.beygram.ui.components.MainTabRail
import com.beykus.beygram.ui.components.MainTabsScaffold
import com.beykus.beygram.ui.components.WindowLayout
import com.beykus.beygram.ui.components.WindowNavigation
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.designFor
import com.beykus.beygram.ui.screens.chats.ChatListItem
import com.beykus.beygram.ui.screens.chats.ChatsDetailPlaceholder
import com.beykus.beygram.ui.screens.chats.ChatsTwoPaneScaffold
import com.beykus.beygram.ui.screens.chats.chatListPaneWidthDp
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens of the split layout (list on the left, empty chat pane on the right) in the three windows of the wide-layout
 * review: a phone in landscape (851x393), an unfolded phone (673x841) and a tablet (1280x800), for every style, light and
 * dark. The scene is the real two-pane scaffold, the real rail or list-pane bar and real chat rows over fixed data.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h900dp-xhdpi", application = Application::class)
class WideLayoutScreenshotTest(private val style: DesignStyle) {
    @get:Rule
    val compose = createComposeRule()

    @Test fun landscapePhoneLight() = capture(WindowLayout(851f, 393f), false, "landscape_light")
    @Test fun landscapePhoneDark() = capture(WindowLayout(851f, 393f), true, "landscape_dark")
    @Test fun foldLight() = capture(WindowLayout(673f, 841f), false, "fold_light")
    @Test fun foldDark() = capture(WindowLayout(673f, 841f), true, "fold_dark")
    @Test fun tabletLight() = capture(WindowLayout(1280f, 800f), false, "tablet_light")
    @Test fun tabletDark() = capture(WindowLayout(1280f, 800f), true, "tablet_dark")

    private fun capture(layout: WindowLayout, dark: Boolean, suffix: String) {
        compose.setContent { WideScene(style, layout, dark) }
        compose.waitForIdle()
        compose.onNodeWithTag(SceneTag).captureRoboImage("src/test/screenshots/Wide_${style.name}_$suffix.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = DesignStyle.entries.map { arrayOf<Any>(it) }
    }
}

private const val SceneTag = "wide-scene"

@Composable
private fun WideScene(style: DesignStyle, layout: WindowLayout, dark: Boolean) {
    // Balanced keeps Liquid Glass deterministic, as in the phone goldens.
    val design = designFor(style, GlassQuality.Balanced, NavigationPlacement.Pinned)
    BeyGramTheme(darkTheme = dark, design = design) {
        val palette = beyGramPalette()
        CompositionLocalProvider(LocalWindowLayout provides layout) {
            Box(Modifier.size(layout.widthDp.dp, layout.heightDp.dp).background(palette.screen).testTag(SceneTag)) {
                val list: @Composable () -> Unit = {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(SampleChats, key = { it.id }) { chat ->
                            ChatListItem(
                                chat = chat,
                                observeFile = { id -> MutableStateFlow(MediaFileState(id)) },
                                downloadFile = {},
                                onClick = {}
                            )
                        }
                    }
                }
                val stateHolder = rememberSaveableStateHolder()
                ChatsTwoPaneScaffold(
                    layout = layout,
                    listPaneWidth = chatListPaneWidthDp(layout.widthDp, layout.heightCompact).dp,
                    navigationRail = {
                        MainTabRail(
                            tabs = MainTab.entries,
                            selected = MainTab.Chats,
                            onSelect = {},
                            slim = layout.navigation == WindowNavigation.SlimRail
                        )
                    },
                    playerBar = {},
                    list = {
                        if (layout.navigation == WindowNavigation.BottomBarInListPane) {
                            MainTabsScaffold("wide", MainTab.Chats, TabVisibility(), {}, stateHolder) { list() }
                        } else {
                            list()
                        }
                    },
                    detail = { ChatsDetailPlaceholder() }
                )
            }
        }
    }
}
