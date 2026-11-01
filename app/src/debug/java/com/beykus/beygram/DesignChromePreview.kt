package com.beykus.beygram

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.navigation.MainTab
import com.beykus.beygram.ui.components.DefaultNavigationDockGap
import com.beykus.beygram.ui.components.LocalNavigationDock
import com.beykus.beygram.ui.components.MainTabBar
import com.beykus.beygram.ui.components.NavigationDockGeometry
import com.beykus.beygram.ui.components.SearchField
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.createButtonPlacement
import com.beykus.beygram.ui.components.navigationSpecFor
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.LocalGlassBackdrop
import com.beykus.beygram.ui.design.glassBackdropSource
import com.beykus.beygram.ui.design.rememberDesign
import com.beykus.beygram.ui.design.rememberGlassBackdrop
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.beykus.beygram.ui.theme.resolveTokens

/**
 * Debug-only chrome preview: the real navigation bar, search field and toolbar drawn over a scrolling list, so a style
 * can be inspected where it matters most - floating glass over content, bar geometry and typography. It signs in,
 * touches no TDLib data and is never part of a release build.
 */
@Composable
internal fun DesignChromePreview(config: AppearanceConfig, systemDark: Boolean) {
    val design = rememberDesign(config)
    BeyGramTheme(
        darkTheme = config.mode.isDark(systemDark),
        tokens = resolveTokens(config, systemDark),
        design = design
    ) {
        val backdrop = rememberGlassBackdrop()
        val palette = beyGramPalette()
        val spec = remember(design) { navigationSpecFor(design) }
        // The harness reproduces the real composition: the content ends above the dock, the create button lives inside
        // the content area and the dock is measured, so the alignment and the clearance can be judged in a screenshot.
        var barHeight by remember { mutableStateOf(0.dp) }
        val dock = NavigationDockGeometry(
            floating = design.navigationFloating,
            height = barHeight,
            horizontalMargin = if (design.navigationFloating) spec.horizontalMargin else 0.dp,
            bottomMargin = if (design.navigationFloating) spec.bottomMargin else 0.dp
        )
        val fabPlacement = createButtonPlacement(dock)
        CompositionLocalProvider(
            LocalGlassBackdrop provides backdrop,
            LocalNavigationDock provides dock
        ) {
            Box(Modifier.fillMaxSize().background(palette.screen)) {
                Column(
                    Modifier.fillMaxSize()
                        .then(if (design.surfaces.glass != null) Modifier.glassBackdropSource(backdrop) else Modifier)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                ) {
                    ProfileToolbar("Chats", onBack = {})
                    SearchField(query = "", onChange = {}, placeholder = "Search")
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Column(Modifier.fillMaxSize()) {
                            repeat(10) { index -> ChatRow(index) }
                        }
                        FloatingActionButton(
                            onClick = {},
                            modifier = Modifier.align(Alignment.BottomEnd)
                                .padding(end = fabPlacement.endPadding, bottom = fabPlacement.bottomPadding),
                            containerColor = palette.accent,
                            contentColor = palette.onAccent,
                            shape = RoundedCornerShape(design.fab.cornerRadius),
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = design.fab.elevation)
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "New chat", modifier = Modifier.size(24.dp))
                        }
                    }
                    if (dock.floating) {
                        // Same fallback the scaffold uses until the bar has measured itself.
                        val reserved = dock.reservedHeight.takeIf { it > 0.dp }
                            ?: (design.spacing.navigationHeight + spec.bottomMargin + DefaultNavigationDockGap)
                        Spacer(Modifier.height(reserved))
                    }
                }
                MainTabBar(
                    tabs = MainTab.entries,
                    selected = MainTab.Chats,
                    onSelect = {},
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onPanelHeightChange = { barHeight = it }
                )
            }
        }
    }
}

@Composable
private fun ChatRow(index: Int) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val title = listOf("Anna", "Design team", "BeyGram", "Mum", "Dev log", "Music", "Alex")[index % 7]
    val preview = listOf(
        "See you at seven, bring the mockups",
        "Pushed the new navigation variants",
        "Release 0.0.4 is out",
        "Call me when you are free",
        "Frame time is stable on the old phone",
        "Now playing: ambient mix",
        "The glass samples the list behind it"
    )[index % 7]
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(palette.accentSoft), contentAlignment = Alignment.Center) {
                Text(title.take(1), color = palette.accentInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(preview, color = palette.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("12:4${index % 10}", color = palette.textSecondary, fontSize = 11.sp)
                if (index % 3 == 0) {
                    Box(
                        Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)).background(palette.accent),
                        contentAlignment = Alignment.Center
                    ) { Text("${index + 1}", color = palette.onAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                } else {
                    Spacer(Modifier.size(18.dp))
                }
            }
        }
        if (design.components.showListDividers) {
            Box(Modifier.padding(start = 72.dp).fillMaxWidth().height(1.dp).background(palette.divider))
        }
    }
}
