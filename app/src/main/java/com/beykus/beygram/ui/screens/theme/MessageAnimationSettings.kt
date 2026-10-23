package com.beykus.beygram.ui.screens.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.MAX_MESSAGE_ANIMATION_OFFSET_DP
import com.beykus.beygram.data.appearance.MAX_MESSAGE_ANIMATION_SCALE
import com.beykus.beygram.data.appearance.MAX_MESSAGE_ANIMATION_SPEED
import com.beykus.beygram.data.appearance.MIN_MESSAGE_ANIMATION_OFFSET_DP
import com.beykus.beygram.data.appearance.MIN_MESSAGE_ANIMATION_SCALE
import com.beykus.beygram.data.appearance.MIN_MESSAGE_ANIMATION_SPEED
import com.beykus.beygram.data.appearance.MessageAnimationConfig
import com.beykus.beygram.data.appearance.MessageAnimationDirection
import com.beykus.beygram.data.appearance.MessageAnimationPreset
import com.beykus.beygram.data.appearance.validMessageAnimationOffsetDp
import com.beykus.beygram.data.appearance.validMessageAnimationScale
import com.beykus.beygram.data.appearance.validMessageAnimationSpeed
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.MessageAnimationFrame
import com.beykus.beygram.ui.theme.LocalMessageLineSpacing
import com.beykus.beygram.ui.theme.LocalMessageTextSize
import java.util.Locale
import kotlin.math.roundToInt

/** "Message animations" subsection of Appearance: one switch, a live preview and independent sent/received presets. */
@Composable
internal fun MessageAnimationSettingsSection(
    config: MessageAnimationConfig,
    onChange: (MessageAnimationConfig) -> Unit
) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(14.dp)) {
        Row(
            Modifier.fillMaxWidth().toggleable(config.enabled, role = Role.Switch) { checked -> onChange(config.copy(enabled = checked)) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(stringResource(R.string.theme_animations_enabled), color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.theme_animations_enabled_detail), color = palette.textSecondary, fontSize = 13.sp)
            }
            Switch(checked = config.enabled, onCheckedChange = null)
        }
        if (!config.enabled) {
            Text(
                stringResource(R.string.theme_animations_disabled),
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }
        MessageAnimationPreview(config)
        MessageAnimationDirectionEditor(
            title = stringResource(R.string.theme_animations_sent),
            direction = config.sent,
            enabled = config.enabled,
            onChange = { onChange(config.copy(sent = it)) }
        )
        MessageAnimationDirectionEditor(
            title = stringResource(R.string.theme_animations_received),
            direction = config.received,
            enabled = config.enabled,
            onChange = { onChange(config.copy(received = it)) }
        )
        TextButton(
            onClick = { onChange(MessageAnimationConfig()) },
            enabled = config != MessageAnimationConfig(),
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(stringResource(R.string.theme_animations_reset))
        }
    }
}

@Composable
private fun MessageAnimationPreview(config: MessageAnimationConfig) {
    val palette = beyGramPalette()
    var replay by rememberSaveable { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(14.dp))
            .background(palette.screen).padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.theme_animations_preview),
                color = palette.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { replay++ }) {
                Icon(Icons.Rounded.Replay, stringResource(R.string.theme_animations_replay), tint = palette.accentInk)
            }
        }
        androidx.compose.runtime.key(config, replay) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MessageAnimationFrame(animate = config.enabled, outgoing = false, config = config) {
                    AnimationPreviewBubble(
                        text = stringResource(R.string.theme_preview_incoming),
                        outgoing = false
                    )
                }
                MessageAnimationFrame(animate = config.enabled, outgoing = true, config = config) {
                    AnimationPreviewBubble(
                        text = stringResource(R.string.theme_preview_outgoing),
                        outgoing = true
                    )
                }
            }
        }
        Text(
            stringResource(R.string.theme_animations_preview_note),
            color = palette.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun AnimationPreviewBubble(text: String, outgoing: Boolean) {
    val palette = beyGramPalette()
    val messageSize = LocalMessageTextSize.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier.widthIn(max = 230.dp).clip(RoundedCornerShape(15.dp))
                .background(if (outgoing) palette.outgoingBubble else palette.incomingBubble)
                .padding(horizontal = 11.dp, vertical = 7.dp)
        ) {
            Text(text, color = palette.messageText, fontSize = messageSize,
                lineHeight = (messageSize.value * LocalMessageLineSpacing.current).sp)
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.theme_preview_time), color = palette.messageTime, fontSize = 10.sp)
                if (outgoing) Icon(Icons.Rounded.DoneAll, null, tint = palette.accent, modifier = Modifier.padding(start = 3.dp).size(13.dp))
            }
        }
    }
}

@Composable
private fun MessageAnimationDirectionEditor(
    title: String,
    direction: MessageAnimationDirection,
    enabled: Boolean,
    onChange: (MessageAnimationDirection) -> Unit
) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(title, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MessageAnimationPreset.entries.forEach { preset ->
                FilterChip(
                    selected = direction.preset == preset,
                    onClick = { if (enabled) onChange(direction.copy(preset = preset)) },
                    enabled = enabled,
                    label = { Text(stringResource(preset.labelRes())) }
                )
            }
        }
        if (direction.preset == MessageAnimationPreset.None) return@Column
        TuningSlider(
            title = stringResource(R.string.theme_animations_speed),
            valueText = String.format(Locale.getDefault(), "%.1f×", direction.tuning.speed),
            value = direction.tuning.speed,
            range = MIN_MESSAGE_ANIMATION_SPEED..MAX_MESSAGE_ANIMATION_SPEED,
            steps = 14,
            enabled = enabled
        ) { value ->
            val snapped = (value * 10f).roundToInt() / 10f
            onChange(direction.copy(tuning = direction.tuning.copy(speed = validMessageAnimationSpeed(snapped))))
        }
        TuningSlider(
            title = stringResource(R.string.theme_animations_intensity),
            valueText = stringResource(R.string.theme_animations_percent_value, (direction.tuning.intensity * 100f).roundToInt()),
            value = direction.tuning.intensity,
            range = 0f..1f,
            steps = 19,
            enabled = enabled
        ) { value ->
            val snapped = (value * 20f).roundToInt() / 20f
            onChange(direction.copy(tuning = direction.tuning.copy(intensity = snapped.coerceIn(0f, 1f))))
        }
        if (direction.preset == MessageAnimationPreset.Slide || direction.preset == MessageAnimationPreset.Custom) {
            TuningSlider(
                title = stringResource(R.string.theme_animations_offset),
                valueText = stringResource(R.string.theme_animations_offset_value, direction.tuning.offsetDp),
                value = direction.tuning.offsetDp.toFloat(),
                range = MIN_MESSAGE_ANIMATION_OFFSET_DP.toFloat()..MAX_MESSAGE_ANIMATION_OFFSET_DP.toFloat(),
                steps = MAX_MESSAGE_ANIMATION_OFFSET_DP - MIN_MESSAGE_ANIMATION_OFFSET_DP - 1,
                enabled = enabled
            ) { value ->
                onChange(direction.copy(tuning = direction.tuning.copy(offsetDp = validMessageAnimationOffsetDp(value.roundToInt()))))
            }
        }
        if (direction.preset == MessageAnimationPreset.Fade || direction.preset == MessageAnimationPreset.Spring || direction.preset == MessageAnimationPreset.Custom) {
            TuningSlider(
                title = stringResource(R.string.theme_animations_scale),
                valueText = stringResource(R.string.theme_animations_percent_value, (direction.tuning.scale * 100f).roundToInt()),
                value = direction.tuning.scale,
                range = MIN_MESSAGE_ANIMATION_SCALE..MAX_MESSAGE_ANIMATION_SCALE,
                steps = 19,
                enabled = enabled
            ) { value ->
                val snapped = (value * 100f).roundToInt() / 100f
                onChange(direction.copy(tuning = direction.tuning.copy(scale = validMessageAnimationScale(snapped))))
            }
        }
    }
}

@Composable
private fun TuningSlider(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            enabled = enabled
        )
    }
}

private fun MessageAnimationPreset.labelRes(): Int = when (this) {
    MessageAnimationPreset.None -> R.string.theme_animations_preset_none
    MessageAnimationPreset.Fade -> R.string.theme_animations_preset_fade
    MessageAnimationPreset.Slide -> R.string.theme_animations_preset_slide
    MessageAnimationPreset.Spring -> R.string.theme_animations_preset_spring
    MessageAnimationPreset.Custom -> R.string.theme_animations_preset_custom
}
