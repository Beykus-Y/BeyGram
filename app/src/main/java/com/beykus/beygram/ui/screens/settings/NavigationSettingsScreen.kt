package com.beykus.beygram.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.navigation.MainTab
import com.beykus.beygram.navigation.TabVisibility
import com.beykus.beygram.ui.components.MainTabItem
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.icon
import com.beykus.beygram.ui.components.title
import com.beykus.beygram.ui.screens.profile.ProfileToolbar

/** Настройка нижнего меню: какие разделы показывать внизу экрана. Чаты показываются всегда. */
@Composable
fun NavigationSettingsScreen(visibility: TabVisibility, onVisibilityChange: (TabVisibility) -> Unit, onBack: () -> Unit) {
    val palette = beyGramPalette()
    BackHandler(onBack = onBack)
    val optional = listOf(MainTab.Calls, MainTab.Contacts, MainTab.Settings)
    val hiddenCount = optional.count { !visibility.isVisible(it) }
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.nav_title), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.nav_intro),
                color = palette.textSecondary, fontSize = 14.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)
            )
            Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).background(palette.surface).padding(vertical = 4.dp)) {
                ChatsLockedRow()
                optional.forEach { tab ->
                    HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 56.dp))
                    TabSwitchRow(tab, checked = visibility.isVisible(tab)) { visible ->
                        onVisibilityChange(visibility.withVisible(tab, visible))
                    }
                }
            }
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)).background(palette.surface).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.nav_preview_caption), color = palette.textSecondary, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MainTab.entries.filter { visibility.isVisible(it) }.forEach { tab ->
                        MainTabItem(tab, selected = tab == MainTab.Chats, onSelect = {}, pillHeight = 30.dp, iconSize = 22.dp, interactive = false)
                    }
                }
            }
            if (hiddenCount > 0) Text(
                if (hiddenCount == optional.size) stringResource(R.string.nav_hint_all_hidden)
                else stringResource(R.string.nav_hint_some_hidden),
                color = palette.textSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ChatsLockedRow() {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Lock, null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
        Text(MainTab.Chats.title, color = palette.textPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 14.dp))
        Text(stringResource(R.string.nav_locked_hint), color = palette.textSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun TabSwitchRow(tab: MainTab, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(tab.icon, null, tint = palette.accent, modifier = Modifier.size(22.dp))
        Text(tab.title, color = palette.textPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 14.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
