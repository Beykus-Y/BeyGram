package com.beykus.beygram.ui.screens.music.yandex

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.yandex.YandexWave
import com.beykus.beygram.data.yandex.YandexWaveSettings
import com.beykus.beygram.ui.components.beyGramPalette

/** The label of each option of the «Моя волна» settings. The default of a group reads as «any». */
internal fun YandexWaveSettings.Activity.labelRes(): Int = when (this) {
    YandexWaveSettings.Activity.NONE -> R.string.yandex_wave_settings_default
    YandexWaveSettings.Activity.WORK -> R.string.yandex_wave_activity_work
    YandexWaveSettings.Activity.FOCUS -> R.string.yandex_wave_activity_focus
    YandexWaveSettings.Activity.WORKOUT -> R.string.yandex_wave_activity_workout
    YandexWaveSettings.Activity.RELAX -> R.string.yandex_wave_activity_relax
}

internal fun YandexWaveSettings.Mood.labelRes(): Int = when (this) {
    YandexWaveSettings.Mood.DEFAULT -> R.string.yandex_wave_settings_default
    YandexWaveSettings.Mood.CALM -> R.string.yandex_wave_mood_calm
    YandexWaveSettings.Mood.ENERGETIC -> R.string.yandex_wave_mood_energetic
}

internal fun YandexWaveSettings.Language.labelRes(): Int = when (this) {
    YandexWaveSettings.Language.DEFAULT -> R.string.yandex_wave_settings_default
    YandexWaveSettings.Language.RUSSIAN -> R.string.yandex_wave_language_russian
    YandexWaveSettings.Language.FOREIGN -> R.string.yandex_wave_language_foreign
}

internal fun YandexWave.Diversity.labelRes(): Int = when (this) {
    YandexWave.Diversity.DEFAULT -> R.string.yandex_wave_diversity_default
    YandexWave.Diversity.DISCOVER -> R.string.yandex_wave_diversity_discover
    YandexWave.Diversity.POPULAR -> R.string.yandex_wave_diversity_popular
}

/** The one-line summary of the settings on the card and in the player; with every group at its default it reads «По вашему вкусу». */
internal fun waveSummary(context: Context, settings: YandexWaveSettings): String {
    val parts = buildList {
        if (settings.activity != YandexWaveSettings.Activity.NONE) add(context.getString(settings.activity.labelRes()))
        if (settings.mood != YandexWaveSettings.Mood.DEFAULT) add(context.getString(settings.mood.labelRes()))
        if (settings.character != YandexWave.Diversity.DEFAULT) add(context.getString(settings.character.labelRes()))
        if (settings.language != YandexWaveSettings.Language.DEFAULT) add(context.getString(settings.language.labelRes()))
    }
    return if (parts.isEmpty()) context.getString(R.string.yandex_wave_summary_default) else parts.joinToString(" · ")
}

/**
 * The settings of «Моя волна»: four groups of chips, «Сбросить» and «Слушать». Nothing is saved before «Слушать»,
 * which saves the choice and starts the radio with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun YandexWaveSettingsSheet(
    initial: YandexWaveSettings,
    onListen: (YandexWaveSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = beyGramPalette()
    var draft by remember(initial) { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.yandex_wave_settings_title),
                color = palette.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            SettingGroup(stringResource(R.string.yandex_wave_settings_activity)) {
                YandexWaveSettings.Activity.entries.forEach { value ->
                    SettingChip(stringResource(value.labelRes()), selected = draft.activity == value) {
                        draft = draft.copy(activity = value)
                    }
                }
            }
            SettingGroup(stringResource(R.string.yandex_wave_settings_mood)) {
                YandexWaveSettings.Mood.entries.forEach { value ->
                    SettingChip(stringResource(value.labelRes()), selected = draft.mood == value) {
                        draft = draft.copy(mood = value)
                    }
                }
            }
            SettingGroup(stringResource(R.string.yandex_wave_settings_character)) {
                YandexWave.Diversity.entries.forEach { value ->
                    SettingChip(stringResource(value.labelRes()), selected = draft.character == value) {
                        draft = draft.copy(character = value)
                    }
                }
            }
            SettingGroup(stringResource(R.string.yandex_wave_settings_language)) {
                YandexWaveSettings.Language.entries.forEach { value ->
                    SettingChip(stringResource(value.labelRes()), selected = draft.language == value) {
                        draft = draft.copy(language = value)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { draft = YandexWaveSettings.DEFAULTS }) {
                    Text(stringResource(R.string.yandex_wave_settings_reset), color = palette.textSecondary)
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onListen(draft) }) { Text(stringResource(R.string.yandex_wave_settings_listen)) }
            }
        }
    }
}

@Composable
private fun SettingGroup(title: String, content: @Composable () -> Unit) {
    val palette = beyGramPalette()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = palette.textSecondary, fontSize = 13.sp)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
