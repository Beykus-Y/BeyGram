package com.beykus.beygram.ui.screens.settings

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.media.AutoDownloadNetwork
import com.beykus.beygram.data.media.AutoDownloadNetworkPolicy
import com.beykus.beygram.data.media.AutoDownloadNetworkStatus
import com.beykus.beygram.data.media.AutoDownloadRule
import com.beykus.beygram.data.media.AutoDownloadSettings
import com.beykus.beygram.data.media.TrafficUsageStore
import com.beykus.beygram.data.settings.AutoDownloadSettingsStore
import com.beykus.beygram.ui.components.TrafficUsageSummary
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar

private val CONFIGURED_NETWORKS = listOf(
    AutoDownloadNetwork.Wifi,
    AutoDownloadNetwork.Mobile,
    AutoDownloadNetwork.Roaming
)

private val SIZE_LIMITS = listOf(
    1L, 2L, 5L, 10L, 20L, 50L, 100L
).map { it * AutoDownloadSettings.MIB } + Long.MAX_VALUE

/**
 * Per-network automatic download limits. Manual download buttons stay available everywhere;
 * secret chats, disappearing media and hidden spoilers are never downloaded automatically.
 */
@Composable
fun AutoDownloadSettingsScreen(onBack: () -> Unit, accountId: String = "legacy") {
    val context = LocalContext.current
    val store = remember(context, accountId) { AutoDownloadSettingsStore.getInstance(context.applicationContext, accountId) }
    val settings by store.settings.collectAsStateWithLifecycle()
    val traffic by remember(context, accountId) {
        TrafficUsageStore.session(accountId).snapshot
    }.collectAsStateWithLifecycle()
    val liveNetwork by AutoDownloadNetworkStatus.network.collectAsStateWithLifecycle()
    LaunchedEffect(context) { AutoDownloadNetworkStatus.ensureStarted(context) }
    var network by remember { mutableStateOf(AutoDownloadNetwork.Wifi) }
    var saveFailed by remember { mutableStateOf(false) }
    val palette = beyGramPalette()

    fun update(transform: (AutoDownloadSettings) -> AutoDownloadSettings) {
        saveFailed = store.update(transform).isFailure
    }
    fun updatePolicy(transform: (AutoDownloadNetworkPolicy) -> AutoDownloadNetworkPolicy) {
        update { value -> value.withPolicy(network, transform(value.policyFor(network))) }
    }

    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.settings_auto_download_title), onBack)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.auto_download_intro), color = palette.textSecondary, fontSize = 13.sp)
            TrafficUsageSummary(snapshot = traffic, network = liveNetwork, dataSaver = settings.dataSaver)
            TabRow(
                selectedTabIndex = CONFIGURED_NETWORKS.indexOf(network),
                containerColor = palette.surface,
                contentColor = palette.accent
            ) {
                CONFIGURED_NETWORKS.forEach { item ->
                    Tab(
                        selected = item == network,
                        onClick = { network = item },
                        text = { Text(stringResource(item.labelRes()), fontSize = 13.sp) }
                    )
                }
            }
            val policy = settings.policyFor(network)
            CategoryRuleCard(
                title = stringResource(R.string.storage_category_photos),
                rule = policy.photos,
                onToggle = { enabled -> updatePolicy { it.copy(photos = it.photos.copy(enabled = enabled)) } },
                onLimit = { limit -> updatePolicy { it.copy(photos = it.photos.copy(maxBytes = limit)) } }
            )
            CategoryRuleCard(
                title = stringResource(R.string.storage_category_videos),
                rule = policy.videos,
                onToggle = { enabled -> updatePolicy { it.copy(videos = it.videos.copy(enabled = enabled)) } },
                onLimit = { limit -> updatePolicy { it.copy(videos = it.videos.copy(maxBytes = limit)) } }
            )
            CategoryRuleCard(
                title = stringResource(R.string.storage_category_files),
                rule = policy.files,
                onToggle = { enabled -> updatePolicy { it.copy(files = it.files.copy(enabled = enabled)) } },
                onLimit = { limit -> updatePolicy { it.copy(files = it.files.copy(maxBytes = limit)) } }
            )
            CategoryRuleCard(
                title = stringResource(R.string.storage_category_music),
                rule = policy.music,
                onToggle = { enabled -> updatePolicy { it.copy(music = it.music.copy(enabled = enabled)) } },
                onLimit = { limit -> updatePolicy { it.copy(music = it.music.copy(maxBytes = limit)) } }
            )

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SwitchRow(
                    title = stringResource(R.string.auto_download_data_saver),
                    checked = settings.dataSaver,
                    onCheckedChange = { enabled -> update { it.copy(dataSaver = enabled) } }
                )
                Text(stringResource(R.string.auto_download_data_saver_detail), color = palette.textSecondary, fontSize = 13.sp)
            }

            Text(stringResource(R.string.auto_download_secret_note), color = palette.textSecondary, fontSize = 13.sp)
            Text(stringResource(R.string.auto_download_manual_note), color = palette.textSecondary, fontSize = 13.sp)
            Text(stringResource(R.string.auto_download_streaming_note), color = palette.textSecondary, fontSize = 13.sp)
            if (saveFailed) {
                Text(stringResource(R.string.auto_download_save_failed), color = palette.danger, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun CategoryRuleCard(
    title: String,
    rule: AutoDownloadRule,
    onToggle: (Boolean) -> Unit,
    onLimit: (Long) -> Unit
) {
    val palette = beyGramPalette()
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SwitchRow(title = title, checked = rule.enabled, onCheckedChange = onToggle)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.auto_download_limit_title), color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { menuOpen = true }, enabled = rule.enabled) {
                Text(limitLabel(rule.maxBytes))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                SIZE_LIMITS.forEach { limit ->
                    DropdownMenuItem(
                        text = { Text(limitLabel(limit)) },
                        onClick = {
                            menuOpen = false
                            if (limit != rule.maxBytes) onLimit(limit)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun limitLabel(bytes: Long): String = when {
    bytes >= Long.MAX_VALUE -> stringResource(R.string.auto_download_limit_unlimited)
    else -> stringResource(R.string.auto_download_limit_mb, (bytes / AutoDownloadSettings.MIB).coerceAtLeast(1).toInt())
}

private fun AutoDownloadNetwork.labelRes(): Int = when (this) {
    AutoDownloadNetwork.Wifi -> R.string.auto_download_network_wifi
    AutoDownloadNetwork.Mobile -> R.string.auto_download_network_mobile
    AutoDownloadNetwork.Roaming -> R.string.auto_download_network_roaming
    else -> R.string.auto_download_network_other
}
