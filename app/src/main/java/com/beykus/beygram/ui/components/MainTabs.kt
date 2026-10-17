package com.beykus.beygram.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.navigation.MainTab
import com.beykus.beygram.navigation.TabVisibility

/** Подпись раздела в нижнем меню. */
internal val MainTab.title: String
    @Composable get() = when (this) {
        MainTab.Chats -> stringResource(R.string.tabs_chats)
        MainTab.Calls -> stringResource(R.string.tabs_calls)
        MainTab.Contacts -> stringResource(R.string.tabs_contacts)
        MainTab.Settings -> stringResource(R.string.settings_title)
    }

/** Иконка раздела в нижнем меню. */
internal val MainTab.icon: ImageVector
    get() = when (this) {
        MainTab.Chats -> Icons.Rounded.ChatBubble
        MainTab.Calls -> Icons.Rounded.Call
        MainTab.Contacts -> Icons.Rounded.Contacts
        MainTab.Settings -> Icons.Rounded.Settings
    }

/** Один раздел нижнего меню: пилюля с иконкой и подпись под ней. Выбранный раздел подсвечен акцентом. */
@Composable
internal fun MainTabItem(
    tab: MainTab,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    pillHeight: Dp = 32.dp,
    iconSize: Dp = 24.dp
) {
    val palette = beyGramPalette()
    val tint = if (selected) palette.accentInk else palette.textSecondary
    Column(
        modifier = modifier.selectable(selected = selected, role = Role.Tab, onClick = onSelect),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(pillHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) palette.accentSoft else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
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

/** Нижнее меню: только видимые разделы, выше навигационной панели системы. */
@Composable
internal fun MainTabBar(tabs: List<MainTab>, selected: MainTab?, onSelect: (MainTab) -> Unit) {
    val palette = beyGramPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .navigationBarsPadding()
            .padding(start = 8.dp, top = 10.dp, end = 8.dp, bottom = 8.dp)
    ) {
        tabs.forEach { tab ->
            MainTabItem(
                tab = tab,
                selected = tab == selected,
                onSelect = { onSelect(tab) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Раздел с нижним меню: содержимое занимает место над баром. [key] сохраняет состояние экрана при переключении
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
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            stateHolder.SaveableStateProvider(key) { content() }
        }
        MainTabBar(tabs = MainTab.entries.filter { visibility.isVisible(it) }, selected = selected, onSelect = onSelect)
    }
}
