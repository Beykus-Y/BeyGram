package com.beykus.beygram.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.presence.LastSeenStyle
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar

/** Как показывать время последнего входа собеседника. Статусы «в сети» и «был(а) недавно» Telegram показывает как есть. */
@Composable
fun LastSeenSettingsScreen(style: LastSeenStyle, onStyleChange: (LastSeenStyle) -> Unit, onBack: () -> Unit) {
    val palette = beyGramPalette()
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.settings_last_seen_title), onBack)
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_last_seen_intro), color = palette.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(vertical = 4.dp)) {
                StyleRow(LastSeenStyle.Relative, style, stringResource(R.string.settings_last_seen_relative_title), stringResource(R.string.settings_last_seen_relative_detail)) { onStyleChange(LastSeenStyle.Relative) }
                StyleRow(LastSeenStyle.Exact, style, stringResource(R.string.settings_last_seen_exact_title), stringResource(R.string.settings_last_seen_exact_detail)) { onStyleChange(LastSeenStyle.Exact) }
            }
            Text(stringResource(R.string.settings_last_seen_footnote), color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun StyleRow(value: LastSeenStyle, selected: LastSeenStyle, title: String, detail: String, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected == value, onClick = onClick)
        Column(Modifier.padding(start = 6.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = palette.textSecondary, fontSize = 13.sp)
        }
    }
}
