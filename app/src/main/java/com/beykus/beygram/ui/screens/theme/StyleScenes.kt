package com.beykus.beygram.ui.screens.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.navigation.MainTab
import com.beykus.beygram.ui.components.LocalMainTabIndicator
import com.beykus.beygram.ui.components.MainTabBar
import com.beykus.beygram.ui.components.MainTabIndicator
import com.beykus.beygram.ui.screens.chats.ServerFolderChip
import com.beykus.beygram.ui.components.Switch
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.groupSurfaceColor
import com.beykus.beygram.ui.components.settingsIconTint
import com.beykus.beygram.ui.components.topAppBarSurface
import com.beykus.beygram.ui.design.ArchiveTreatment
import com.beykus.beygram.ui.design.GlassEdge
import com.beykus.beygram.ui.design.GlassPanel
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.LocalGlassBackdrop
import com.beykus.beygram.ui.design.SearchFieldVariant
import com.beykus.beygram.ui.design.TopBarVariant
import com.beykus.beygram.ui.design.groupCardShape
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The screen a style preview shows. Each one is drawn from synthetic strings only: the preview never reads chats,
 * contacts or the TDLib session, so a screenshot of the Appearance editor cannot leak anything personal.
 */
internal enum class StyleScene(@StringRes val labelRes: Int) {
    Chats(R.string.style_preview_scene_chats),
    Messages(R.string.style_preview_scene_messages),
    Settings(R.string.style_preview_scene_settings)
}

/**
 * One screen of the app in the style of [config], drawn at phone size and scaled down into the box it is given.
 *
 * The scene is composed from the same components and tokens the real screens use (the bottom bar, the folder chips,
 * glass panels, the Material 2 app bar surface, the group card shape), so the picture changes exactly when the app
 * would. It is a picture: touches are swallowed and the caller decides what TalkBack hears about it. Glass is drawn
 * without a backdrop sample on purpose, so the preview never promises a blur it cannot render at this scale.
 *
 * [virtualWidth] is the narrowest phone the scene is laid out for; a wider box shows a wider phone instead of
 * magnifying the content beyond its real size.
 */
@Composable
internal fun StyleScenePreview(
    config: AppearanceConfig,
    systemDark: Boolean,
    scene: StyleScene,
    modifier: Modifier = Modifier,
    virtualWidth: Dp = 360.dp
) {
    ScaledScene(virtualWidth = virtualWidth, modifier = modifier) {
        val density = LocalDensity.current
        // The miniature keeps the proportions of the standard font size; the labels around it follow the user's
        // font scale, so a 2x font does not turn the picture into a few clipped letters.
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
            Box(
                Modifier.fillMaxSize()
                    // A preview is not the window: system bar insets must not pad its bottom bar.
                    .consumeWindowInsets(WindowInsets.safeDrawing)
            ) {
                when (scene) {
                    StyleScene.Messages -> AppearanceChatPreview(
                        config = config,
                        compact = false,
                        animate = false,
                        replayKey = 0,
                        modifier = Modifier.fillMaxSize(),
                        systemDark = systemDark
                    )
                    StyleScene.Chats, StyleScene.Settings -> AppearanceThemeScope(config, systemDark) {
                        val indicator = remember { MainTabIndicator() }
                        CompositionLocalProvider(
                            LocalGlassBackdrop provides null,
                            LocalMainTabIndicator provides indicator
                        ) {
                            if (scene == StyleScene.Chats) ChatsScene() else SettingsScene()
                        }
                    }
                }
                // The topmost sibling takes the touch, so the real tabs and chips underneath never react; the
                // event still reaches the selectable card that holds the preview.
                Box(Modifier.matchParentSize().pointerInput(Unit) {
                    awaitPointerEventScope { while (true) awaitPointerEvent() }
                })
            }
        }
    }
}

/**
 * Measures [content] at a phone-like width of at least [virtualWidth] and draws it scaled into the bounded box of
 * [modifier]. The scale never exceeds 1, so the preview is a miniature and never an enlargement.
 */
@Composable
private fun ScaledScene(virtualWidth: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier.clipToBounds()) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        if (width == Constraints.Infinity || height == Constraints.Infinity || width == 0 || height == 0) {
            return@Layout layout(0, 0) {}
        }
        val scale = min(1f, width.toFloat() / virtualWidth.toPx())
        val sceneWidth = (width / scale).roundToInt()
        val sceneHeight = (height / scale).roundToInt()
        val placeables = measurables.map { it.measure(Constraints.fixed(sceneWidth, sceneHeight)) }
        layout(width, height) {
            placeables.forEach {
                it.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
    }
}

/** The chat list: header, search, folders, archive, a few chats, the create button and the bottom bar. */
@Composable
private fun ChatsScene() {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val floating = design.navigationFloating
    var navHeight by remember { mutableStateOf(0.dp) }
    Box(Modifier.fillMaxSize().background(palette.screen)) {
        Column(Modifier.fillMaxSize()) {
            SceneTopBar {
                ChatsHeader()
            }
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    SceneArchiveRow()
                    SampleChats.forEachIndexed { index, chat ->
                        SceneChatRow(chat, index, divider = index < SampleChats.lastIndex)
                    }
                }
                if (!floating) {
                    SceneFab(Modifier.align(Alignment.BottomEnd).padding(16.dp))
                }
            }
            if (!floating) SceneBottomBar(MainTab.Chats) { navHeight = it }
        }
        if (floating) {
            SceneFab(
                Modifier.align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = navHeight + 26.dp)
            )
            Box(Modifier.align(Alignment.BottomCenter)) {
                SceneBottomBar(MainTab.Chats) { navHeight = it }
            }
        }
    }
}

/** Settings: an app bar, section labels and grouped rows with the style's switch, card shape and dividers. */
@Composable
private fun SettingsScene() {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val floating = design.navigationFloating
    Box(Modifier.fillMaxSize().background(palette.screen)) {
        Column(Modifier.fillMaxSize()) {
            SceneTopBar {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = palette.textPrimary, modifier = Modifier.size(24.dp))
                    }
                    Text(
                        stringResource(R.string.settings_title),
                        color = palette.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = design.mainScreen.titleWeight.takeIf { it != FontWeight.Bold } ?: FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().clipToBounds().padding(horizontal = design.spacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(Modifier.height(4.dp))
                SceneSectionLabel(R.string.style_preview_settings_general)
                SceneGroup {
                    SceneSettingRow(Icons.Rounded.Notifications, R.string.style_preview_settings_notifications, toggle = true)
                    SceneGroupDivider()
                    SceneSettingRow(Icons.AutoMirrored.Rounded.VolumeUp, R.string.style_preview_settings_sounds, toggle = false)
                    SceneGroupDivider()
                    SceneSettingRow(Icons.Rounded.Lock, R.string.style_preview_settings_privacy, toggle = null)
                }
                SceneSectionLabel(R.string.style_preview_settings_data)
                SceneGroup {
                    SceneSettingRow(Icons.Rounded.Download, R.string.style_preview_settings_autodownload, toggle = null)
                    SceneGroupDivider()
                    SceneSettingRow(Icons.Rounded.Storage, R.string.style_preview_settings_storage, toggle = null)
                }
            }
            if (!floating) SceneBottomBar(MainTab.Settings) {}
        }
        if (floating) {
            Box(Modifier.align(Alignment.BottomCenter)) { SceneBottomBar(MainTab.Settings) {} }
        }
    }
}

/** The header chrome of a screen in the style: glass pane, Material 2 elevated app bar or the plain surface. */
@Composable
private fun SceneTopBar(content: @Composable () -> Unit) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    if (design.components.topBar == TopBarVariant.Glass) {
        GlassPanel(
            design = design,
            tint = palette.surface,
            rim = palette.textSecondary,
            shape = RectangleShape,
            edge = GlassEdge.BottomHairline,
            modifier = Modifier.fillMaxWidth()
        ) { content() }
    } else {
        Box(Modifier.fillMaxWidth().topAppBarSurface()) { content() }
    }
}

@Composable
private fun ChatsHeader() {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val main = design.mainScreen
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            if (main.showBrandOverline) {
                Text(
                    stringResource(R.string.app_name).uppercase(),
                    color = palette.accent,
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 0.8.sp
                )
            }
            Text(
                stringResource(R.string.chats_title),
                color = palette.textPrimary,
                fontSize = main.titleSize,
                lineHeight = (main.titleSize.value + 3f).sp,
                fontWeight = main.titleWeight,
                letterSpacing = main.titleTracking,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(main.headerGap))
        SceneSearchField(Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ServerFolderChip(stringResource(R.string.style_preview_folder_all), selected = true, unreadCount = null, onClick = {})
            ServerFolderChip(stringResource(R.string.style_preview_folder_personal), selected = false, unreadCount = 3, onClick = {})
            ServerFolderChip(stringResource(R.string.style_preview_folder_groups), selected = false, unreadCount = null, onClick = {})
        }
    }
}

/** The search pill with the fill, outline and height rules of the real search field, without a text field. */
@Composable
private fun SceneSearchField(modifier: Modifier) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val shape = RoundedCornerShape(design.shapes.search)
    val fill = when (design.components.search) {
        SearchFieldVariant.Filled -> if (design.style == com.beykus.beygram.data.appearance.DesignStyle.Material3) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            palette.surface2
        }
        SearchFieldVariant.Outlined -> palette.surface
        SearchFieldVariant.Glass -> palette.surface.copy(alpha = design.surfaces.surfaceAlpha.coerceAtLeast(0.55f))
    }
    Row(
        modifier.fillMaxWidth().height(design.mainScreen.searchHeight).clip(shape).background(fill)
            .then(
                if (design.components.search == SearchFieldVariant.Outlined) Modifier.border(1.dp, palette.divider, shape)
                else Modifier
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.style_preview_search),
            color = palette.textSecondary,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1
        )
    }
}

/** The archive row in the style's treatment: raised card, tonal row or a flat navigation line. */
@Composable
private fun SceneArchiveRow() {
    val palette = beyGramPalette()
    val main = LocalDesignSystem.current.mainScreen
    val shape = RoundedCornerShape(main.archiveRadius)
    val background = when (main.archive) {
        ArchiveTreatment.Card, ArchiveTreatment.Tonal -> groupSurfaceColor()
        ArchiveTreatment.Flat -> Color.Transparent
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp)
            .heightIn(min = 56.dp)
            .then(if (main.archiveElevation > 0.dp) Modifier.shadow(main.archiveElevation, shape) else Modifier)
            .clip(shape).background(background)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(palette.surface2), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Archive, null, tint = palette.accentInk, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.chats_archive_title), color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.style_preview_archive_summary), color = palette.textSecondary, fontSize = 12.sp, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
    }
}

private class SampleChat(
    @StringRes val name: Int,
    @StringRes val message: Int,
    @StringRes val time: Int,
    val unread: Int
)

private val SampleChats = listOf(
    SampleChat(R.string.style_preview_chat1_name, R.string.style_preview_chat1_message, R.string.style_preview_chat1_time, 2),
    SampleChat(R.string.style_preview_chat2_name, R.string.style_preview_chat2_message, R.string.style_preview_chat2_time, 0),
    SampleChat(R.string.style_preview_chat3_name, R.string.style_preview_chat3_message, R.string.style_preview_chat3_time, 0),
    SampleChat(R.string.style_preview_chat4_name, R.string.style_preview_chat4_message, R.string.style_preview_chat4_time, 0)
)

/** One chat row: avatar size, title weight, badge corner and dividers all come from the style. */
@Composable
private fun SceneChatRow(chat: SampleChat, index: Int, divider: Boolean) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val main = design.mainScreen
    val name = stringResource(chat.name)
    val (avatarFill, avatarInk) = when (index % 3) {
        0 -> palette.accent to palette.onAccent
        1 -> palette.accentSoft to palette.accentInk
        else -> palette.surface2 to palette.textPrimary
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = design.spacing.listRowMinHeight)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(main.avatarSize).clip(CircleShape).background(avatarFill),
            contentAlignment = Alignment.Center
        ) {
            Text(name.take(1), color = avatarInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = main.rowTitleWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(stringResource(chat.time), color = palette.textSecondary, fontSize = 12.sp, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(chat.message),
                    color = if (chat.unread > 0 && main.unreadPreviewEmphasis) palette.textPrimary else palette.textSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (chat.unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.heightIn(min = 22.dp).clip(RoundedCornerShape(main.badgeRadius))
                            .background(palette.accent).padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(chat.unread.toString(), color = palette.onAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (divider && design.components.showListDividers) {
        Box(
            Modifier.padding(start = 16.dp + main.avatarSize + 12.dp).fillMaxWidth().height(1.dp)
                .background(palette.divider)
        )
    }
}

/** The create-chat button with the style's corner, elevation and tonal or accent fill. */
@Composable
private fun SceneFab(modifier: Modifier) {
    val palette = beyGramPalette()
    val fab = LocalDesignSystem.current.fab
    val shape = RoundedCornerShape(fab.cornerRadius)
    Box(
        modifier.size(56.dp).shadow(fab.elevation, shape).clip(shape)
            .background(if (fab.tonal) palette.accentSoft else palette.accent),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Edit,
            null,
            tint = if (fab.tonal) palette.accentInk else palette.onAccent,
            modifier = Modifier.size(24.dp)
        )
    }
}

/** The real bottom bar of the app, in the placement the draft chooses. */
@Composable
private fun SceneBottomBar(selected: MainTab, onHeight: (Dp) -> Unit) {
    MainTabBar(
        tabs = MainTab.entries,
        selected = selected,
        onSelect = {},
        onPanelHeightChange = onHeight
    )
}

@Composable
private fun SceneSectionLabel(@StringRes text: Int) {
    val palette = beyGramPalette()
    val main = LocalDesignSystem.current.mainScreen
    val label = stringResource(text)
    Text(
        if (main.sectionLabelUppercase) label.uppercase() else label,
        color = palette.accentInk,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = main.sectionLabelTracking,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
    )
}

@Composable
private fun SceneGroup(content: @Composable () -> Unit) {
    val design = LocalDesignSystem.current
    val shape = groupCardShape()
    Column(
        Modifier.fillMaxWidth()
            .then(if (design.surfaces.cardElevation > 0.dp) Modifier.shadow(design.surfaces.cardElevation, shape) else Modifier)
            .clip(shape).background(groupSurfaceColor())
            .padding(vertical = 4.dp)
    ) { content() }
}

@Composable
private fun SceneGroupDivider() {
    if (!LocalDesignSystem.current.components.showListDividers) return
    Box(Modifier.padding(start = 56.dp).fillMaxWidth().height(1.dp).background(beyGramPalette().divider))
}

/** A settings row; [toggle] null draws a chevron, otherwise the style's own switch in that state. */
@Composable
private fun SceneSettingRow(icon: ImageVector, @StringRes title: Int, toggle: Boolean?) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = settingsIconTint(), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(
            stringResource(title),
            color = palette.textPrimary,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (toggle != null) {
            Switch(checked = toggle, onCheckedChange = null)
        } else {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}
