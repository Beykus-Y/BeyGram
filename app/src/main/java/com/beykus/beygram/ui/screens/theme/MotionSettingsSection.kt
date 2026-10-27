package com.beykus.beygram.ui.screens.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.settings.ChatAnimationSettings
import com.beykus.beygram.ui.accessibility.LocalSystemReducedMotion
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.motion.MOTION_INTENSITY_MAX
import com.beykus.beygram.ui.motion.MOTION_INTENSITY_MIN
import com.beykus.beygram.ui.motion.MOTION_SPEED_MAX
import com.beykus.beygram.ui.motion.MOTION_SPEED_MIN
import com.beykus.beygram.ui.motion.MotionCategory
import com.beykus.beygram.ui.motion.MotionPreset
import com.beykus.beygram.ui.motion.MotionSettings
import com.beykus.beygram.ui.screens.settings.SettingsRowDivider
import com.beykus.beygram.ui.screens.settings.SettingsRowInset
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Global motion controls: master switch, presets, speed, intensity, categories, and the two chat switches.
 * Presets, categories and the chat switches apply at once. Sliders keep their value locally while the finger moves and
 * reach storage once, when it lifts.
 */
@Composable
internal fun MotionSettingsSection(
    motion: MotionSettings,
    onMotionChange: (MotionSettings) -> Unit,
    chatAnimations: ChatAnimationSettings,
    onAnimatedEmojiChange: (Boolean) -> Unit,
    onMessageEffectsChange: (Boolean) -> Unit
) {
    val palette = beyGramPalette()
    val reduced = LocalSystemReducedMotion.current
    var speedDraft by remember(motion.speed) { mutableFloatStateOf(motion.speed) }
    var intensityDraft by remember(motion.intensityPercent) { mutableIntStateOf(motion.intensityPercent) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AppearanceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            AppearanceToggleRow(
                title = stringResource(R.string.motion_master_title),
                detail = stringResource(R.string.motion_master_detail),
                checked = motion.enabled
            ) { on ->
                onMotionChange(if (on) motion.enabledAgain() else motion.withPreset(MotionPreset.Off))
            }
            if (reduced) {
                Text(
                    stringResource(R.string.motion_reduced_warning),
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        AppearanceSectionLabel(stringResource(R.string.motion_section_preset))
        AppearanceCard(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
            MotionPreset.entries.forEachIndexed { index, preset ->
                if (index > 0) SettingsRowDivider(startInset = SettingsRowInset)
                MotionPresetRow(
                    title = stringResource(preset.labelRes()),
                    detail = stringResource(preset.descriptionRes()),
                    selected = motion.preset == preset,
                    onClick = { onMotionChange(motion.withPreset(preset)) }
                )
            }
        }

        AppearanceSectionLabel(stringResource(R.string.motion_section_tuning))
        AppearanceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            AppearanceSliderRow(
                title = stringResource(R.string.theme_animations_speed),
                valueText = String.format(Locale.getDefault(), "%.1f×", speedDraft),
                value = speedDraft,
                range = MOTION_SPEED_MIN..MOTION_SPEED_MAX,
                enabled = motion.enabled,
                onValueChange = { speedDraft = (it * 10f).roundToInt() / 10f },
                onValueChangeFinished = { onMotionChange(motion.withSpeed(speedDraft)) }
            )
            AppearanceSliderRow(
                title = stringResource(R.string.theme_animations_intensity),
                valueText = String.format(Locale.getDefault(), "%d%%", intensityDraft),
                value = intensityDraft.toFloat(),
                range = MOTION_INTENSITY_MIN.toFloat()..MOTION_INTENSITY_MAX.toFloat(),
                enabled = motion.enabled,
                onValueChange = { intensityDraft = ((it / 5f).roundToInt() * 5).coerceIn(MOTION_INTENSITY_MIN, MOTION_INTENSITY_MAX) },
                onValueChangeFinished = { onMotionChange(motion.withIntensity(intensityDraft)) }
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onMotionChange(MotionSettings()) }) {
                    Text(stringResource(R.string.motion_reset), fontSize = 13.sp)
                }
            }
        }

        AppearanceSectionLabel(stringResource(R.string.motion_section_categories))
        AppearanceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            MotionCategory.entries.forEachIndexed { index, category ->
                if (index > 0) SettingsRowDivider(startInset = SettingsRowInset)
                MotionCategoryRow(
                    title = stringResource(category.labelRes()),
                    checked = category !in motion.disabledCategories,
                    enabled = motion.enabled,
                    onCheckedChange = { on -> onMotionChange(motion.withCategoryEnabled(category, on)) }
                )
            }
        }

        AppearanceSectionLabel(stringResource(R.string.motion_section_chats))
        AppearanceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            AppearanceToggleRow(
                title = stringResource(R.string.chat_animations_emoji_title),
                detail = stringResource(R.string.chat_animations_emoji_detail),
                checked = chatAnimations.animatedEmoji,
                onCheckedChange = onAnimatedEmojiChange
            )
            SettingsRowDivider(startInset = SettingsRowInset)
            AppearanceToggleRow(
                title = stringResource(R.string.chat_animations_effects_title),
                detail = stringResource(R.string.chat_animations_effects_detail),
                checked = chatAnimations.messageEffects,
                onCheckedChange = onMessageEffectsChange
            )
        }
    }
}

@Composable
private fun MotionPresetRow(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(
                title,
                color = palette.textPrimary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium
            )
            Text(detail, color = palette.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun MotionCategoryRow(title: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = if (enabled) palette.textPrimary else palette.textSecondary,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Turning the master switch back on restores the saved speed and intensity under the matching fixed preset. */
private fun MotionSettings.enabledAgain(): MotionSettings {
    val match = MotionPreset.entries.firstOrNull {
        it != MotionPreset.Off && it != MotionPreset.Custom &&
            it.speed == speed && it.intensityPercent == intensityPercent
    } ?: MotionPreset.Custom
    return copy(preset = match)
}

private fun MotionPreset.labelRes(): Int = when (this) {
    MotionPreset.Off -> R.string.motion_preset_off
    MotionPreset.Subtle -> R.string.motion_preset_subtle
    MotionPreset.Balanced -> R.string.motion_preset_balanced
    MotionPreset.Expressive -> R.string.motion_preset_expressive
    MotionPreset.Custom -> R.string.motion_preset_custom
}

private fun MotionPreset.descriptionRes(): Int = when (this) {
    MotionPreset.Off -> R.string.motion_preset_off_desc
    MotionPreset.Subtle -> R.string.motion_preset_subtle_desc
    MotionPreset.Balanced -> R.string.motion_preset_balanced_desc
    MotionPreset.Expressive -> R.string.motion_preset_expressive_desc
    MotionPreset.Custom -> R.string.motion_preset_custom_desc
}

private fun MotionCategory.labelRes(): Int = when (this) {
    MotionCategory.Navigation -> R.string.motion_category_navigation
    MotionCategory.Tabs -> R.string.motion_category_tabs
    MotionCategory.Panels -> R.string.motion_category_panels
    MotionCategory.Lists -> R.string.motion_category_lists
    MotionCategory.Media -> R.string.motion_category_media
    MotionCategory.Chats -> R.string.motion_category_chats
    MotionCategory.Micro -> R.string.motion_category_micro
}
