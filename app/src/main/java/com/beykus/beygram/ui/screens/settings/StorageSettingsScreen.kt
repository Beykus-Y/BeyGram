package com.beykus.beygram.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.beykus.beygram.R
import com.beykus.beygram.data.storage.StorageRetention
import com.beykus.beygram.data.storage.StorageSettingsStore
import com.beykus.beygram.data.storage.StorageSizeLimit
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.StorageCategory
import com.beykus.beygram.domain.model.StorageUsage
import com.beykus.beygram.domain.repository.StorageRepository
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.text.resolve
import com.beykus.beygram.ui.text.storageSizeText
import com.beykus.beygram.ui.theme.AvatarPalette

private enum class ClearScope { Selected, All }

/** Память и кэш: сколько места занимают файлы, очистка выбранного и лимиты, которые приложение держит само. */
@Composable
fun StorageSettingsScreen(
    repository: StorageRepository,
    onBack: () -> Unit,
    accountId: String = "legacy"
) {
    val context = LocalContext.current
    val model: StorageSettingsViewModel = viewModel(key = "storage-settings:$accountId", factory = viewModelFactory {
        initializer { StorageSettingsViewModel(repository, StorageSettingsStore.getInstance(context.applicationContext)) }
    })
    val palette = beyGramPalette()
    val settings by model.settings.collectAsStateWithLifecycle()
    val usage = model.usage
    val cleaningProgress = model.cleaningProgress
    val busy = model.busy
    var pendingClear by remember { mutableStateOf<ClearScope?>(null) }
    LaunchedEffect(Unit) { model.refresh() }

    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.settings_storage_title), onBack)
        if (model.applying) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = palette.accent)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UsageCard(usage, model.loading, model.selected, busy, onToggle = model::toggle)
            ClearCard(
                usage = usage,
                selected = model.selected,
                busy = busy,
                cleaningProgress = cleaningProgress,
                freedBytes = model.freedBytes,
                error = model.error,
                onClearSelected = { pendingClear = ClearScope.Selected },
                onClearAll = { pendingClear = ClearScope.All }
            )
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.storage_limit_title), color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(stringResource(R.string.storage_limit_detail), color = palette.textSecondary, fontSize = 13.sp)
                StorageSizeLimit.entries.forEach { limit ->
                    ChoiceRow(limit.label(), selected = settings.sizeLimit == limit, enabled = cleaningProgress == null) {
                        if (settings.sizeLimit != limit) model.changeSettings { it.copy(sizeLimit = limit) }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.storage_retention_title), color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(stringResource(R.string.storage_retention_detail), color = palette.textSecondary, fontSize = 13.sp)
                StorageRetention.entries.forEach { retention ->
                    ChoiceRow(retention.label(), selected = settings.retention == retention, enabled = cleaningProgress == null) {
                        if (settings.retention != retention) model.changeSettings { it.copy(retention = retention) }
                    }
                }
            }
            Text(stringResource(R.string.storage_footnote), color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }

    pendingClear?.let { scope ->
        AlertDialog(
            onDismissRequest = { pendingClear = null },
            title = { Text(stringResource(if (scope == ClearScope.All) R.string.storage_clear_all_title else R.string.storage_clear_selected_title)) },
            text = { Text(stringResource(R.string.storage_clear_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingClear = null
                    model.clear(if (scope == ClearScope.All) usage?.categoriesWithFiles().orEmpty() else model.selected)
                }) { Text(stringResource(R.string.storage_clear_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pendingClear = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

@Composable
private fun UsageCard(usage: StorageUsage?, loading: Boolean, selected: Set<StorageCategory>, busy: Boolean, onToggle: (StorageCategory) -> Unit) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.storage_usage_title), color = palette.textSecondary, fontSize = 13.sp)
        if (usage == null) {
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = palette.accent, strokeWidth = 2.dp)
                    Text(stringResource(R.string.storage_usage_loading), color = palette.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
                }
            }
        } else {
            Text(storageSizeText(usage.totalBytes), color = palette.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            UsageBar(usage)
            StorageCategory.entries.forEach { category ->
                val bytes = usage.bytes(category)
                CategoryRow(category, bytes, checked = category in selected, enabled = !busy) { onToggle(category) }
            }
            SizeInfoRow(palette.accent, stringResource(R.string.storage_uploads), stringResource(R.string.storage_uploads_detail), usage.uploadBytes)
            SizeInfoRow(palette.textSecondary, stringResource(R.string.storage_database), stringResource(R.string.storage_database_detail), usage.databaseBytes)
        }
    }
}

@Composable
private fun UsageBar(usage: StorageUsage) {
    val palette = beyGramPalette()
    val segments = StorageCategory.entries.map { it.color() to usage.bytes(it) } + (palette.accent to usage.uploadBytes) + (palette.textSecondary to usage.databaseBytes)
    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(palette.surface2)) {
        segments.filter { it.second > 0L }.forEach { (color, bytes) ->
            Box(Modifier.weight(bytes.toFloat()).fillMaxHeight().background(color))
        }
    }
}

@Composable
private fun CategoryRow(category: StorageCategory, bytes: Long, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val palette = beyGramPalette()
    val hasFiles = bytes > 0L
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled && hasFiles, role = Role.Checkbox) { onToggle() }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(category.color()))
        Text(
            stringResource(category.labelRes()),
            color = if (hasFiles) palette.textPrimary else palette.textSecondary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f).padding(start = 12.dp)
        )
        Text(storageSizeText(bytes), color = palette.textSecondary, fontSize = 14.sp)
        if (hasFiles) Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun SizeInfoRow(color: Color, title: String, detail: String, bytes: Long) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 15.sp)
            Text(detail, color = palette.textSecondary, fontSize = 12.sp)
        }
        Text(storageSizeText(bytes), color = palette.textSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun ClearCard(
    usage: StorageUsage?,
    selected: Set<StorageCategory>,
    busy: Boolean,
    cleaningProgress: Float?,
    freedBytes: Long?,
    error: UiText?,
    onClearSelected: () -> Unit,
    onClearAll: () -> Unit
) {
    val palette = beyGramPalette()
    val selectedBytes = usage?.let { current -> selected.sumOf { current.bytes(it) } } ?: 0L
    val filesBytes = usage?.filesBytes ?: 0L
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.storage_selected_size, storageSizeText(selectedBytes)), color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onClearSelected, enabled = !busy && selectedBytes > 0L, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.storage_clear_selected))
            }
            OutlinedButton(onClick = onClearAll, enabled = !busy && filesBytes > 0L, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.storage_clear_all))
            }
        }
        cleaningProgress?.let { progress ->
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = palette.accent)
            Text(stringResource(R.string.storage_clearing, (progress * 100).toInt()), color = palette.textSecondary, fontSize = 13.sp)
        }
        freedBytes?.let { Text(stringResource(R.string.storage_freed, storageSizeText(it)), color = palette.successText, fontSize = 13.sp) }
        error?.let { Text(it.resolve(), color = palette.danger, fontSize = 13.sp) }
    }
}

@Composable
private fun ChoiceRow(title: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.RadioButton, onClick = onSelect).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(title, color = palette.textPrimary, fontSize = 15.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun StorageSizeLimit.label(): String =
    gigabytes?.let { stringResource(R.string.storage_limit_gigabytes, it) } ?: stringResource(R.string.storage_limit_unlimited)

@Composable
private fun StorageRetention.label(): String = when (this) {
    StorageRetention.ThreeDays -> stringResource(R.string.storage_retention_three_days)
    StorageRetention.Week -> stringResource(R.string.storage_retention_week)
    StorageRetention.Month -> stringResource(R.string.storage_retention_month)
    StorageRetention.Forever -> stringResource(R.string.storage_retention_forever)
}

private fun StorageCategory.labelRes(): Int = when (this) {
    StorageCategory.Photos -> R.string.storage_category_photos
    StorageCategory.Videos -> R.string.storage_category_videos
    StorageCategory.Files -> R.string.storage_category_files
    StorageCategory.VoiceAndVideoNotes -> R.string.storage_category_voice_notes
    StorageCategory.Music -> R.string.storage_category_music
    StorageCategory.Other -> R.string.storage_category_other
}

/** Цвета категорий берутся из палитры аватарок, по одному на категорию. */
private fun StorageCategory.color(): Color = when (this) {
    StorageCategory.Photos -> AvatarPalette[4]
    StorageCategory.Videos -> AvatarPalette[1]
    StorageCategory.Files -> AvatarPalette[2]
    StorageCategory.VoiceAndVideoNotes -> AvatarPalette[5]
    StorageCategory.Music -> AvatarPalette[3]
    StorageCategory.Other -> AvatarPalette[0]
}
