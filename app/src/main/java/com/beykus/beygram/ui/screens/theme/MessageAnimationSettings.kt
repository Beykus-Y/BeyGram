package com.beykus.beygram.ui.screens.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.MAX_MESSAGE_ANIMATION_OFFSET_DP
import com.beykus.beygram.data.appearance.MAX_MESSAGE_ANIMATION_SCALE
import com.beykus.beygram.data.appearance.MAX_MESSAGE_ANIMATION_SPEED
import com.beykus.beygram.data.appearance.MAX_MESSAGE_ANIMATION_SPRINGINESS
import com.beykus.beygram.data.appearance.MIN_MESSAGE_ANIMATION_OFFSET_DP
import com.beykus.beygram.data.appearance.MIN_MESSAGE_ANIMATION_SCALE
import com.beykus.beygram.data.appearance.MIN_MESSAGE_ANIMATION_SPEED
import com.beykus.beygram.data.appearance.MIN_MESSAGE_ANIMATION_SPRINGINESS
import com.beykus.beygram.data.appearance.MessageAnimationConfig
import com.beykus.beygram.data.appearance.MessageAnimationControl
import com.beykus.beygram.data.appearance.MessageAnimationDirection
import com.beykus.beygram.data.appearance.MessageAnimationEasing
import com.beykus.beygram.data.appearance.MessageAnimationMotionDirection
import com.beykus.beygram.data.appearance.MessageAnimationPreset
import com.beykus.beygram.data.appearance.controls
import com.beykus.beygram.data.appearance.supportsIncoming
import com.beykus.beygram.data.appearance.validMessageAnimationOffsetDp
import com.beykus.beygram.data.appearance.validMessageAnimationScale
import com.beykus.beygram.data.appearance.validMessageAnimationSpeed
import com.beykus.beygram.data.appearance.validMessageAnimationSpringiness
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.ComposerLaunchPreview
import com.beykus.beygram.ui.screens.chat.MessageAnimationFrame
import com.beykus.beygram.ui.theme.LocalMessageLineSpacing
import com.beykus.beygram.ui.theme.LocalMessageTextSize
import java.util.Locale
import kotlin.math.roundToInt

/**
 * "Message animations" subsection of Appearance. The common mode exposes one direction editor; the independent mode
 * exposes two. Tuning controls are collapsed and contextual to the selected preset.
 */
@Composable
internal fun MessageAnimationSettingsSection(
    config: MessageAnimationConfig,
    onChange: (MessageAnimationConfig) -> Unit
) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(14.dp)) {
        ToggleRow(
            title = stringResource(R.string.theme_animations_enabled),
            detail = stringResource(R.string.theme_animations_enabled_detail),
            checked = config.enabled,
            enabled = true
        ) { onChange(config.copy(enabled = it)) }
        ToggleRow(
            title = stringResource(R.string.theme_animations_linked),
            detail = stringResource(R.string.theme_animations_linked_detail),
            checked = config.linkedDirections,
            enabled = config.enabled
        ) { onChange(config.withLinkedDirections(it)) }
        if (!config.enabled) {
            Text(
                stringResource(R.string.theme_animations_disabled),
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }
        MessageAnimationPreview(config)
        if (config.linkedDirections) {
            DirectionEditor(
                key = "shared",
                title = stringResource(R.string.theme_animations_shared),
                direction = config.shared,
                outgoingAllowed = true,
                enabled = config.enabled,
                onChange = { onChange(config.copy(shared = it)) }
            )
        } else {
            DirectionEditor(
                key = "sent",
                title = stringResource(R.string.theme_animations_sent),
                direction = config.sent,
                outgoingAllowed = true,
                enabled = config.enabled,
                onChange = { onChange(config.copy(sent = it)) }
            )
            DirectionEditor(
                key = "received",
                title = stringResource(R.string.theme_animations_received),
                direction = config.received,
                outgoingAllowed = false,
                enabled = config.enabled,
                onChange = { onChange(config.copy(received = it)) }
            )
        }
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
private fun ToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth()
            .then(if (enabled) Modifier.toggleable(checked, role = Role.Switch) { onCheckedChange(it) } else Modifier)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = if (enabled) palette.textPrimary else palette.textSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = palette.textSecondary, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun MessageAnimationPreview(config: MessageAnimationConfig) {
    val palette = beyGramPalette()
    var replay by rememberSaveable { mutableIntStateOf(0) }
    val outgoing = config.direction(outgoing = true)
    val fromComposer = outgoing.preset == MessageAnimationPreset.ComposerLaunch
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
            if (fromComposer) {
                ComposerLaunchPreview(
                    text = stringResource(R.string.theme_animations_preview_outgoing),
                    time = stringResource(R.string.theme_preview_time_reply),
                    speed = outgoing.tuning.speed,
                    enabled = config.enabled,
                    replayKey = replay,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!fromComposer || !config.linkedDirections) {
                    MessageAnimationFrame(animate = config.enabled, outgoing = false, config = config) {
                        AnimationPreviewBubble(
                            text = stringResource(R.string.theme_preview_incoming),
                            outgoing = false
                        )
                    }
                } else {
                    // In linked mode Composer Launch is outgoing-only; show the Flow fallback used for incoming.
                    MessageAnimationFrame(animate = config.enabled, outgoing = false, config = config) {
                        AnimationPreviewBubble(
                            text = stringResource(R.string.theme_animations_preview_incoming_fallback),
                            outgoing = false
                        )
                    }
                }
                if (!fromComposer) {
                    MessageAnimationFrame(animate = config.enabled, outgoing = true, config = config) {
                        AnimationPreviewBubble(
                            text = stringResource(R.string.theme_animations_preview_outgoing),
                            outgoing = true
                        )
                    }
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
private fun DirectionEditor(
    key: String,
    title: String,
    direction: MessageAnimationDirection,
    outgoingAllowed: Boolean,
    enabled: Boolean,
    onChange: (MessageAnimationDirection) -> Unit
) {
    val palette = beyGramPalette()
    var advanced by rememberSaveable(key) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(title, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        PresetPicker(
            direction = direction,
            outgoingAllowed = outgoingAllowed,
            enabled = enabled,
            onChange = { onChange(direction.copy(preset = it)) }
        )
        if (direction.preset == MessageAnimationPreset.ComposerLaunch) {
            Text(
                stringResource(R.string.theme_animations_composer_note),
                color = palette.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
            )
        }
        val controls = direction.preset.controls()
        if (controls.isNotEmpty() && direction.preset != MessageAnimationPreset.None) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).clickable(enabled = enabled) { advanced = !advanced },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.theme_animations_advanced),
                    color = palette.accentInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = palette.textSecondary
                )
            }
            if (advanced) {
                if (MessageAnimationControl.Speed in controls) {
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
                }
                if (MessageAnimationControl.Intensity in controls) {
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
                }
                if (MessageAnimationControl.Scale in controls) {
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
                if (MessageAnimationControl.Offset in controls) {
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
                if (MessageAnimationControl.Direction in controls) {
                    ChoiceRow(
                        title = stringResource(R.string.theme_animations_direction),
                        options = MessageAnimationMotionDirection.entries,
                        selected = direction.tuning.motionDirection,
                        enabled = enabled,
                        label = { stringResource(it.labelRes()) }
                    ) { onChange(direction.copy(tuning = direction.tuning.copy(motionDirection = it))) }
                }
                if (MessageAnimationControl.Easing in controls) {
                    ChoiceRow(
                        title = stringResource(R.string.theme_animations_easing),
                        options = MessageAnimationEasing.entries,
                        selected = direction.tuning.easing,
                        enabled = enabled,
                        label = { stringResource(it.labelRes()) }
                    ) { onChange(direction.copy(tuning = direction.tuning.copy(easing = it))) }
                }
                if (MessageAnimationControl.Springiness in controls) {
                    TuningSlider(
                        title = stringResource(R.string.theme_animations_springiness),
                        valueText = stringResource(R.string.theme_animations_percent_value, (direction.tuning.springiness * 100f).roundToInt()),
                        value = direction.tuning.springiness,
                        range = MIN_MESSAGE_ANIMATION_SPRINGINESS..MAX_MESSAGE_ANIMATION_SPRINGINESS,
                        steps = 19,
                        enabled = enabled
                    ) { value ->
                        val snapped = (value * 20f).roundToInt() / 20f
                        onChange(direction.copy(tuning = direction.tuning.copy(springiness = validMessageAnimationSpringiness(snapped))))
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetPicker(
    direction: MessageAnimationDirection,
    outgoingAllowed: Boolean,
    enabled: Boolean,
    onChange: (MessageAnimationPreset) -> Unit
) {
    val presets = MessageAnimationPreset.entries.filter { outgoingAllowed || it.supportsIncoming() }
    LazyRow(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(presets, key = { it.name }) { preset ->
            PresetCard(
                preset = preset,
                selected = direction.preset == preset,
                enabled = enabled,
                onClick = { onChange(preset) }
            )
        }
    }
}

@Composable
private fun PresetCard(
    preset: MessageAnimationPreset,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    Column(
        Modifier.width(154.dp).height(112.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) palette.accentSoft else palette.surface2)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) palette.accent else palette.divider, RoundedCornerShape(13.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        PresetThumbnail(preset, selected)
        Text(
            stringResource(preset.labelRes()),
            color = if (enabled) palette.textPrimary else palette.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            stringResource(preset.descriptionRes()),
            color = palette.textSecondary,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PresetThumbnail(preset: MessageAnimationPreset, selected: Boolean) {
    val palette = beyGramPalette()
    Box(Modifier.fillMaxWidth().height(36.dp)) {
        if (preset == MessageAnimationPreset.ComposerLaunch) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(end = 6.dp).size(width = 46.dp, height = 17.dp)
                    .clip(RoundedCornerShape(8.dp)).background(palette.accent)
            )
            Icon(
                Icons.Rounded.NorthEast, null, tint = palette.accentInk,
                modifier = Modifier.align(Alignment.Center).size(17.dp)
            )
            Box(
                Modifier.align(Alignment.BottomStart).size(width = 66.dp, height = 14.dp)
                    .clip(RoundedCornerShape(7.dp)).background(palette.surface)
                    .border(1.dp, palette.divider, RoundedCornerShape(7.dp))
            )
            return@Box
        }
        val spec = when (preset) {
            MessageAnimationPreset.None -> listOf(0f, 0f, 1f, 0.45f, 0f, 0f)
            MessageAnimationPreset.Fade -> listOf(0f, 0f, 0.95f, 0.55f, 0f, 0f)
            MessageAnimationPreset.Slide -> listOf(10f, 0f, 1f, 0.7f, 0f, 0f)
            MessageAnimationPreset.Spring -> listOf(0f, 0f, 0.86f, 0.75f, 0f, 0f)
            MessageAnimationPreset.Pop -> listOf(0f, 0f, 1.05f, 0.8f, 0f, 0f)
            MessageAnimationPreset.Flow -> listOf(8f, -3f, 0.9f, 0.7f, 0f, 0f)
            MessageAnimationPreset.Drift -> listOf(12f, -6f, 0.9f, 0.65f, 0f, 0f)
            MessageAnimationPreset.Quick -> listOf(2f, 0f, 0.99f, 0.85f, 0f, 0f)
            MessageAnimationPreset.Tilt -> listOf(0f, 0f, 0.93f, 0.7f, 18f, 0f)
            MessageAnimationPreset.Sway -> listOf(3f, 0f, 0.94f, 0.7f, 0f, 6f)
            MessageAnimationPreset.ComposerLaunch, MessageAnimationPreset.Custom -> listOf(0f, 0f, 1f, 1f, 0f, 0f)
        }
        val x = spec[0]
        val y = spec[1]
        val scale = spec[2]
        val alpha = spec[3]
        val rotationY = spec[4]
        val rotationZ = spec[5]
        Box(
            Modifier.align(Alignment.Center).offset(x.dp, y.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    this.rotationY = rotationY
                    this.rotationZ = rotationZ
                    cameraDistance = 12f * density
                    transformOrigin = TransformOrigin.Center
                }
                .size(width = 54.dp, height = 22.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (selected) palette.accent else palette.accentInk)
        ) {
            if (preset == MessageAnimationPreset.Custom) {
                Icon(Icons.Rounded.Tune, null, tint = palette.onAccent, modifier = Modifier.align(Alignment.Center).size(14.dp))
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    enabled: Boolean,
    label: @Composable (T) -> String,
    onChange: (T) -> Unit
) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(title, color = palette.textPrimary, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { if (enabled) onChange(option) },
                    enabled = enabled,
                    label = { Text(label(option), fontSize = 11.sp) }
                )
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
    MessageAnimationPreset.Pop -> R.string.theme_animations_preset_pop
    MessageAnimationPreset.Flow -> R.string.theme_animations_preset_flow
    MessageAnimationPreset.Drift -> R.string.theme_animations_preset_drift
    MessageAnimationPreset.Quick -> R.string.theme_animations_preset_quick
    MessageAnimationPreset.Tilt -> R.string.theme_animations_preset_tilt
    MessageAnimationPreset.Sway -> R.string.theme_animations_preset_sway
    MessageAnimationPreset.ComposerLaunch -> R.string.theme_animations_preset_composer
    MessageAnimationPreset.Custom -> R.string.theme_animations_preset_custom
}

private fun MessageAnimationPreset.descriptionRes(): Int = when (this) {
    MessageAnimationPreset.None -> R.string.theme_animations_desc_none
    MessageAnimationPreset.Fade -> R.string.theme_animations_desc_fade
    MessageAnimationPreset.Slide -> R.string.theme_animations_desc_slide
    MessageAnimationPreset.Spring -> R.string.theme_animations_desc_spring
    MessageAnimationPreset.Pop -> R.string.theme_animations_desc_pop
    MessageAnimationPreset.Flow -> R.string.theme_animations_desc_flow
    MessageAnimationPreset.Drift -> R.string.theme_animations_desc_drift
    MessageAnimationPreset.Quick -> R.string.theme_animations_desc_quick
    MessageAnimationPreset.Tilt -> R.string.theme_animations_desc_tilt
    MessageAnimationPreset.Sway -> R.string.theme_animations_desc_sway
    MessageAnimationPreset.ComposerLaunch -> R.string.theme_animations_desc_composer
    MessageAnimationPreset.Custom -> R.string.theme_animations_desc_custom
}

private fun MessageAnimationMotionDirection.labelRes(): Int = when (this) {
    MessageAnimationMotionDirection.Auto -> R.string.theme_animations_direction_auto
    MessageAnimationMotionDirection.FromStart -> R.string.theme_animations_direction_start
    MessageAnimationMotionDirection.FromEnd -> R.string.theme_animations_direction_end
    MessageAnimationMotionDirection.FromTop -> R.string.theme_animations_direction_top
    MessageAnimationMotionDirection.FromBottom -> R.string.theme_animations_direction_bottom
}

private fun MessageAnimationEasing.labelRes(): Int = when (this) {
    MessageAnimationEasing.Smooth -> R.string.theme_animations_easing_smooth
    MessageAnimationEasing.Linear -> R.string.theme_animations_easing_linear
    MessageAnimationEasing.EaseIn -> R.string.theme_animations_easing_ease_in
    MessageAnimationEasing.EaseOut -> R.string.theme_animations_easing_ease_out
    MessageAnimationEasing.Expressive -> R.string.theme_animations_easing_expressive
}
