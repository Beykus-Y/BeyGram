package com.beykus.beygram.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.settings.StealthSettingsStore
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.groupSurfaceColor
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassCard
import com.beykus.beygram.ui.design.groupCardShape

@Composable
internal fun StealthQuickAccess(store: StealthSettingsStore, onOpenSettings: () -> Unit) {
    val settings by store.state.collectAsStateWithLifecycle()
    val preset = matchingPreset(settings)
    val palette = beyGramPalette()
    var expanded by remember(store) { mutableStateOf(false) }
    var saveFailed by remember(store) { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .glassCard(LocalDesignSystem.current, groupSurfaceColor(), palette.textSecondary, groupCardShape())
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(role = Role.Button) { expanded = !expanded }.padding(12.dp)
        ) {
            Text(stringResource(R.string.stealth_title), color = palette.textPrimary)
            Text(
                stringResource(if (settings.failClosed) R.string.stealth_unavailable else preset.labelRes()),
                color = palette.textSecondary
            )
        }
        if (expanded) {
            StealthPresetChoices(selected = preset.takeUnless { settings.failClosed }) { choice ->
                saveFailed = !store.update { stealthPreset(choice, it) }
            }
            if (saveFailed) {
                Text(
                    stringResource(R.string.stealth_save_failed),
                    color = palette.danger,
                    modifier = Modifier.padding(horizontal = 12.dp).semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            TextButton(onClick = onOpenSettings, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.settings_title))
            }
        }
    }
}
