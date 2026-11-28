package com.beykus.beygram.ui.screens.music

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.playback.AudioCoexistencePreferences
import com.beykus.beygram.playback.AudioCoexistenceSettings
import com.beykus.beygram.playback.CoexistenceAction
import com.beykus.beygram.playback.CoexistencePolicy
import com.beykus.beygram.playback.ExternalAudioInterference
import com.beykus.beygram.playback.MusicInterference
import com.beykus.beygram.ui.screens.settings.SettingsGroup
import kotlin.math.roundToInt

@Composable
internal fun AudioCoexistenceSettingsGroup() {
    val context = LocalContext.current
    LaunchedEffect(context) { AudioCoexistenceSettings.load(context) }
    val preferences by AudioCoexistenceSettings.state.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    SettingsGroup(stringResource(R.string.coexistence_title)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            GainSlider(R.string.coexistence_music_volume, preferences.musicPercent) { percent ->
                AudioCoexistenceSettings.update(context) { it.copy(musicPercent = percent) }
            }
            GainSlider(R.string.coexistence_speech_volume, preferences.speechPercent) { percent ->
                AudioCoexistenceSettings.update(context) { it.copy(speechPercent = percent) }
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.coexistence_hide_actions else R.string.coexistence_show_actions))
            }
            if (expanded) {
                for (action in CoexistenceAction.entries) {
                    val policy = preferences.policy(action)
                    ActionPolicy(action, policy) { updated ->
                        AudioCoexistenceSettings.update(context) {
                            it.copy(policies = it.policies + (action to updated))
                        }
                    }
                }
                Text(stringResource(R.string.coexistence_external_note))
                Text(stringResource(R.string.coexistence_recording_note))
            }
            TextButton(onClick = { AudioCoexistenceSettings.update(context) { AudioCoexistencePreferences() } }) {
                Text(stringResource(R.string.coexistence_reset))
            }
        }
    }
}

@Composable
private fun ActionPolicy(
    action: CoexistenceAction,
    policy: CoexistencePolicy,
    onChange: (CoexistencePolicy) -> Unit,
) {
    Text(stringResource(when (action) {
        CoexistenceAction.RecordVoice -> R.string.coexistence_record_voice
        CoexistenceAction.RecordVideoNote -> R.string.coexistence_record_circle
        CoexistenceAction.PlayVoice -> R.string.coexistence_play_voice
        CoexistenceAction.PlayVideoNote -> R.string.coexistence_play_circle
    }))
    PolicyMenu(
        R.string.coexistence_internal,
        listOf(R.string.coexistence_continue, R.string.coexistence_duck, R.string.coexistence_pause),
        policy.music.ordinal,
    ) { onChange(policy.copy(music = MusicInterference.entries[it])) }
    if (policy.music == MusicInterference.Duck) {
        GainSlider(R.string.coexistence_duck_volume, policy.musicPercent) {
            onChange(policy.copy(musicPercent = it))
        }
    }
    PolicyMenu(
        R.string.coexistence_external,
        listOf(R.string.coexistence_continue, R.string.coexistence_request_duck, R.string.coexistence_request_pause),
        policy.external.ordinal,
    ) { onChange(policy.copy(external = ExternalAudioInterference.entries[it])) }
}

@Composable
private fun PolicyMenu(label: Int, options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { open = true }) {
            Text(stringResource(R.string.coexistence_selection, stringResource(label), stringResource(options[selected])))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(text = { Text(stringResource(option)) }, onClick = {
                    open = false
                    onSelect(index)
                })
            }
        }
    }
}

@Composable
private fun GainSlider(label: Int, percent: Int, onChange: (Int) -> Unit) {
    val description = stringResource(label)
    Text(stringResource(R.string.coexistence_percent, description, percent))
    Slider(
        value = percent.toFloat(),
        onValueChange = { onChange(it.roundToInt()) },
        valueRange = 0f..100f,
        modifier = Modifier.semantics { contentDescription = description },
    )
}
