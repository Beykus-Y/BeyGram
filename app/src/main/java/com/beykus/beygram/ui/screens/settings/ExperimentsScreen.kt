package com.beykus.beygram.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.media.bgbeat.BgBeatSettingsStore
import com.beykus.beygram.media.bgmix.BgMixSettingsStore
import com.beykus.beygram.playback.TrackTransitionSettings
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassScreenBackground
import com.beykus.beygram.ui.screens.profile.ProfileToolbar

/**
 * Experimental features in their own screen, grouped by area. Music holds BG Mix and BG Beat and the two transition
 * settings of the regular player. Those two do not depend on BG Mix: they apply to every queue the regular player plays,
 * and BG Mix has transitions of its own, so they stay available when BG Mix is off.
 */
@Composable
fun ExperimentsScreen(onBack: () -> Unit) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val bgBeatSettings = remember { BgBeatSettingsStore.getInstance(context) }
    val bgBeatEnabled by bgBeatSettings.enabled.collectAsStateWithLifecycle()
    remember(context) { BgMixSettingsStore.load(context) }
    val bgMixEnabled by BgMixSettingsStore.enabled.collectAsStateWithLifecycle()
    remember(context) { TrackTransitionSettings.load(context) }
    val skipSilence by TrackTransitionSettings.skipSilence.collectAsStateWithLifecycle()
    val crossfade by TrackTransitionSettings.crossfade.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().glassScreenBackground(design, palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.settings_experiments_title), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.settings_experiments_intro),
                color = palette.textSecondary, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)
            )
            SettingsGroup(stringResource(R.string.settings_experiments_group_music)) {
                SettingsToggle(
                    icon = Icons.Rounded.SwapHoriz,
                    title = stringResource(R.string.settings_bgmix_title),
                    subtitle = stringResource(R.string.settings_bgmix_desc),
                    checked = bgMixEnabled,
                    onCheckedChange = { BgMixSettingsStore.setEnabled(context, it) }
                )
                SettingsRowDivider()
                SettingsToggle(
                    icon = Icons.Rounded.GraphicEq,
                    title = stringResource(R.string.settings_bgbeat_title),
                    subtitle = stringResource(R.string.settings_bgbeat_desc),
                    checked = bgBeatEnabled,
                    onCheckedChange = { bgBeatSettings.setEnabled(it) }
                )
            }
            SettingsGroup(stringResource(R.string.settings_experiments_group_transitions)) {
                SettingsToggle(
                    icon = Icons.Rounded.SkipNext,
                    title = stringResource(R.string.settings_skip_silence_title),
                    subtitle = stringResource(R.string.settings_skip_silence_desc),
                    checked = skipSilence,
                    onCheckedChange = { TrackTransitionSettings.setSkipSilence(context, it) }
                )
                SettingsRowDivider()
                SettingsToggle(
                    icon = Icons.Rounded.SwapHoriz,
                    title = stringResource(R.string.settings_crossfade_title),
                    subtitle = stringResource(R.string.settings_crossfade_desc),
                    checked = crossfade,
                    onCheckedChange = { TrackTransitionSettings.setCrossfade(context, it) }
                )
                if (crossfade) {
                    val crossfadeSeconds by TrackTransitionSettings.crossfadeSeconds.collectAsStateWithLifecycle()
                    CrossfadeLengthRow(
                        seconds = crossfadeSeconds,
                        onChange = { TrackTransitionSettings.setCrossfadeSeconds(context, it) }
                    )
                }
            }
            Text(
                stringResource(R.string.settings_experiments_transitions_note),
                color = palette.textSecondary, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
    }
}
