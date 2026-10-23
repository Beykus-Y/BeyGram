package com.beykus.beygram.ui.screens.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.MAX_MESSAGE_ANIMATION_OFFSET_DP
import com.beykus.beygram.data.appearance.MAX_MESSAGE_ANIMATION_SCALE
import com.beykus.beygram.data.appearance.MAX_MESSAGE_ANIMATION_SPEED
import com.beykus.beygram.data.appearance.MAX_MESSAGE_ANIMATION_SPRINGINESS
import com.beykus.beygram.data.appearance.MIN_MESSAGE_ANIMATION_OFFSET_DP
import com.beykus.beygram.data.appearance.MIN_MESSAGE_ANIMATION_SCALE
import com.beykus.beygram.data.appearance.MIN_MESSAGE_ANIMATION_SPEED
import com.beykus.beygram.data.appearance.MIN_MESSAGE_ANIMATION_SPRINGINESS
import com.beykus.beygram.data.appearance.MessageAnimationControl
import com.beykus.beygram.data.appearance.MessageAnimationDirection
import com.beykus.beygram.data.appearance.MessageAnimationEasing
import com.beykus.beygram.data.appearance.MessageAnimationMotionDirection
import com.beykus.beygram.data.appearance.MessageAnimationPreset
import com.beykus.beygram.data.appearance.MessageAnimationTuning
import com.beykus.beygram.data.appearance.controls
import com.beykus.beygram.data.appearance.supportsIncoming
import com.beykus.beygram.data.appearance.validMessageAnimationOffsetDp
import com.beykus.beygram.data.appearance.validMessageAnimationScale
import com.beykus.beygram.data.appearance.validMessageAnimationSpeed
import com.beykus.beygram.data.appearance.validMessageAnimationSpringiness
import com.beykus.beygram.ui.components.beyGramPalette
import java.util.Locale
import kotlin.math.roundToInt

private enum class AnimationDirectionTab { Sent, Received }

@Composable
internal fun AppearanceAnimationsPage(
    draft: AppearanceConfig,
    systemDark: Boolean,
    onDraftChange: (AppearanceConfig) -> Unit,
    onOpenPreview: () -> Unit
) {
    val config = draft.messageAnimations
    var replayKey by rememberSaveable { mutableIntStateOf(0) }
    var tabName by rememberSaveable { mutableStateOf(AnimationDirectionTab.Sent.name) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val tab = AnimationDirectionTab.entries.firstOrNull { it.name == tabName } ?: AnimationDirectionTab.Sent
    val outgoing = config.direction(outgoing = true)
    val incoming = config.direction(outgoing = false)
    val activeOutgoing = config.linkedDirections || tab == AnimationDirectionTab.Sent
    val active = if (activeOutgoing) outgoing else incoming
    val controls = active.preset.controls()
    val presetSignature = listOf(
        config.enabled, config.linkedDirections,
        config.shared.preset, config.sent.preset, config.received.preset
    )
    LaunchedEffect(presetSignature) { replayKey++ }

    fun updateDirection(change: (MessageAnimationDirection) -> MessageAnimationDirection) {
        onDraftChange(
            draft.copy(
                messageAnimations = config.withDirection(activeOutgoing, change(active))
            )
        )
    }

    fun updateTuning(change: (MessageAnimationTuning) -> MessageAnimationTuning) {
        updateDirection { it.copy(tuning = change(it.tuning)) }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = AppearanceDimens.pagePadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AppearanceAnimationPreviewCard(
            draft = draft,
            systemDark = systemDark,
            replayKey = replayKey,
            onReplay = { replayKey++ },
            onOpenPreview = onOpenPreview
        )

        AppearanceCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            AppearanceToggleRow(
                title = stringResource(R.string.theme_animations_enabled),
                detail = stringResource(R.string.theme_animations_enabled_detail),
                checked = config.enabled
            ) {
                onDraftChange(draft.copy(messageAnimations = config.copy(enabled = it)))
            }
            AppearanceToggleRow(
                title = stringResource(R.string.theme_animations_linked),
                detail = stringResource(R.string.theme_animations_linked_detail),
                checked = config.linkedDirections
            ) {
                onDraftChange(draft.copy(messageAnimations = config.withLinkedDirections(it)))
            }
            if (!config.enabled) {
                Text(
                    stringResource(R.string.theme_animations_disabled),
                    color = beyGramPalette().textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        if (!config.linkedDirections) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DirectionTabCard(
                    title = stringResource(R.string.theme_animations_sent),
                    preset = outgoing.preset,
                    selected = tab == AnimationDirectionTab.Sent,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) { tabName = AnimationDirectionTab.Sent.name }
                DirectionTabCard(
                    title = stringResource(R.string.theme_animations_received),
                    preset = incoming.preset,
                    selected = tab == AnimationDirectionTab.Received,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) { tabName = AnimationDirectionTab.Received.name }
            }
        }

        AppearanceSectionLabel(
            stringResource(
                if (config.linkedDirections) R.string.theme_animations_shared
                else if (activeOutgoing) R.string.appearance_animations_effect_sent
                else R.string.appearance_animations_effect_received
            )
        )
        PresetGroups(
            activePreset = active.preset,
            allowComposer = activeOutgoing,
            onSelect = { preset ->
                updateDirection { it.copy(preset = preset) }
            }
        )
        if (active.preset == MessageAnimationPreset.ComposerLaunch) {
            Text(
                stringResource(R.string.theme_animations_composer_note),
                color = beyGramPalette().textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        AppearanceSectionLabel(stringResource(R.string.appearance_animations_tuning_section))
        AppearanceTuningCard(
            active = active,
            controls = controls,
            enabled = true,
            onPresetDirection = { direction ->
                updateDirection { it.copy(tuning = it.tuning.copy(motionDirection = direction)) }
                replayKey++
            },
            onPresetEasing = { easing ->
                updateDirection { it.copy(tuning = it.tuning.copy(easing = easing)) }
                replayKey++
            },
            onReset = {
                updateDirection { it.copy(tuning = MessageAnimationTuning()) }
                replayKey++
            },
            onTuningChange = { change -> updateTuning(change) },
            onSliderFinished = { replayKey++ }
        )

        TextButton(onClick = { confirmReset = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.theme_animations_reset), color = beyGramPalette().danger, fontSize = 13.sp)
        }
        Text(
            stringResource(R.string.appearance_animations_note),
            color = beyGramPalette().textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
    if (confirmReset) AppearanceResetDialog(
        title = stringResource(R.string.appearance_reset_animations_title),
        message = stringResource(R.string.appearance_reset_animations_message),
        onConfirm = {
            confirmReset = false
            onDraftChange(draft.resetAnimations())
            replayKey++
        },
        onDismiss = { confirmReset = false }
    )
}

@Composable
private fun AppearanceAnimationPreviewCard(
    draft: AppearanceConfig,
    systemDark: Boolean,
    replayKey: Int,
    onReplay: () -> Unit,
    onOpenPreview: () -> Unit
) {
    val palette = beyGramPalette()
    val config = draft.messageAnimations
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppearanceSectionLabel(stringResource(R.string.theme_animations_preview), Modifier.weight(1f))
            AppearanceStatusPill(
                stringResource(
                    if (config.enabled) R.string.appearance_animations_status_on
                    else R.string.appearance_animations_status_off
                ),
                active = config.enabled
            )
            IconButton(onClick = onReplay, enabled = config.enabled) {
                Icon(
                    Icons.Rounded.Replay,
                    stringResource(R.string.theme_animations_replay),
                    tint = if (config.enabled) palette.accent else palette.textSecondary,
                    modifier = Modifier.size(21.dp)
                )
            }
            IconButton(onClick = onOpenPreview) {
                Icon(
                    Icons.Rounded.Fullscreen,
                    stringResource(R.string.appearance_preview_open_description),
                    tint = palette.textSecondary,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().clip(AppearanceDimens.cardShape)
                .border(1.dp, palette.divider, AppearanceDimens.cardShape)
        ) {
            AppearanceChatPreview(
                config = draft,
                compact = true,
                animate = true,
                replayKey = replayKey,
                systemDark = systemDark,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            stringResource(R.string.theme_animations_preview_note),
            color = palette.textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun DirectionTabCard(
    title: String,
    preset: MessageAnimationPreset,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape)
            .background(if (selected) palette.accentSoft else palette.surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) palette.accent else palette.divider, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            title,
            color = if (selected) palette.accentInk else palette.textPrimary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            stringResource(preset.labelRes()),
            color = palette.textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PresetGroups(
    activePreset: MessageAnimationPreset,
    allowComposer: Boolean,
    onSelect: (MessageAnimationPreset) -> Unit
) {
    val groups = listOf(
        R.string.appearance_animations_group_basic to listOf(
            MessageAnimationPreset.None,
            MessageAnimationPreset.Fade,
            MessageAnimationPreset.Slide,
            MessageAnimationPreset.Spring,
            MessageAnimationPreset.Quick
        ),
        R.string.appearance_animations_group_signature to listOf(
            MessageAnimationPreset.Pop,
            MessageAnimationPreset.Flow,
            MessageAnimationPreset.Drift,
            MessageAnimationPreset.Tilt,
            MessageAnimationPreset.Sway,
            MessageAnimationPreset.ComposerLaunch
        ).filter { allowComposer || it.supportsIncoming() },
        R.string.appearance_animations_group_custom to emptyList()
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEach { (title, presets) ->
            Text(
                stringResource(title),
                color = beyGramPalette().textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
            if (presets.isEmpty()) {
                CustomPresetCard(
                    selected = activePreset == MessageAnimationPreset.Custom,
                    onClick = { onSelect(MessageAnimationPreset.Custom) }
                )
            } else {
                presets.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            PresetCard(
                                preset = preset,
                                selected = activePreset == preset,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            ) { onSelect(preset) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomPresetCard(selected: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) palette.accentSoft else palette.surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) palette.accent else palette.divider, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PresetThumbnail(MessageAnimationPreset.Custom, selected, Modifier.width(64.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(MessageAnimationPreset.Custom.labelRes()),
                color = if (selected) palette.accentInk else palette.textPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(MessageAnimationPreset.Custom.descriptionRes()),
                color = palette.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        if (selected) Icon(Icons.Rounded.Check, null, tint = palette.accent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PresetCard(
    preset: MessageAnimationPreset,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.heightIn(min = 91.dp).clip(shape)
            .background(if (selected) palette.accentSoft else palette.surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) palette.accent else palette.divider, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PresetThumbnail(preset, selected, Modifier.weight(1f))
            if (selected) Icon(Icons.Rounded.Check, null, tint = palette.accent, modifier = Modifier.size(17.dp))
        }
        Text(
            stringResource(preset.labelRes()),
            color = if (selected) palette.accentInk else palette.textPrimary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            stringResource(preset.descriptionRes()),
            color = palette.textSecondary,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PresetThumbnail(preset: MessageAnimationPreset, selected: Boolean, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    val finalColor = if (selected) palette.accent else palette.accentInk
    Box(modifier.fillMaxWidth().height(34.dp)) {
        when (preset) {
            MessageAnimationPreset.None -> {
                Box(
                    Modifier.align(Alignment.CenterStart).size(width = 48.dp, height = 17.dp)
                        .clip(RoundedCornerShape(7.dp)).border(1.dp, palette.textSecondary, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Block, null, tint = palette.textSecondary, modifier = Modifier.size(11.dp))
                }
            }
            MessageAnimationPreset.ComposerLaunch -> {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(end = 14.dp).size(width = 34.dp, height = 13.dp)
                        .clip(RoundedCornerShape(6.dp)).background(palette.accent)
                )
                Icon(
                    Icons.Rounded.NorthEast, null, tint = palette.accentInk,
                    modifier = Modifier.align(Alignment.Center).size(14.dp)
                )
                Box(
                    Modifier.align(Alignment.BottomStart).size(width = 42.dp, height = 11.dp)
                        .clip(RoundedCornerShape(5.dp)).background(palette.surface)
                        .border(1.dp, palette.divider, RoundedCornerShape(5.dp))
                )
            }
            MessageAnimationPreset.Custom -> {
                Box(
                    Modifier.align(Alignment.CenterStart).size(width = 48.dp, height = 19.dp)
                        .clip(RoundedCornerShape(7.dp)).background(palette.surface2)
                        .border(1.dp, palette.divider, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Tune, null, tint = palette.accentInk, modifier = Modifier.size(13.dp))
                }
            }
            else -> {
                val spec = preset.thumbnailSpec()
                // A faint start bubble plus the settled bubble makes the direction readable at a glance.
                Box(
                    Modifier.align(Alignment.CenterStart)
                        .offset(x = spec.ghostX.dp, y = spec.ghostY.dp)
                        .graphicsLayer {
                            scaleX = spec.ghostScale
                            scaleY = spec.ghostScale
                            alpha = spec.ghostAlpha
                            rotationZ = spec.rotationZ
                        }
                        .size(width = 44.dp, height = 17.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(palette.accent.copy(alpha = 0.22f))
                        .border(1.dp, palette.accent.copy(alpha = 0.55f), RoundedCornerShape(7.dp))
                )
                Box(
                    Modifier.align(Alignment.CenterStart)
                        .offset(x = spec.finalX.dp, y = spec.finalY.dp)
                        .graphicsLayer {
                            scaleX = spec.finalScale
                            scaleY = spec.finalScale
                            rotationZ = spec.rotationZ
                        }
                        .size(width = 44.dp, height = 17.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(finalColor)
                )
                if (spec.speedLines) {
                    // Three short trailing strokes stand for BeyGram Quick's almost-instant arrival.
                    androidx.compose.foundation.Canvas(
                        Modifier.align(Alignment.CenterStart).offset(x = 56.dp, y = 0.dp).size(18.dp, 18.dp)
                    ) {
                        val stroke = 1.5.dp.toPx()
                        listOf(5f, 9f, 6f).forEachIndexed { index, length ->
                            drawLine(
                                color = palette.accent.copy(alpha = 0.7f),
                                start = androidx.compose.ui.geometry.Offset(0f, (4f + index * 5f).dp.toPx()),
                                end = androidx.compose.ui.geometry.Offset(length.dp.toPx(), (4f + index * 5f).dp.toPx()),
                                strokeWidth = stroke,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ThumbnailSpec(
    val ghostX: Float,
    val ghostY: Float,
    val finalX: Float,
    val finalY: Float,
    val ghostScale: Float = 1f,
    val finalScale: Float = 1f,
    val ghostAlpha: Float = 0.55f,
    val rotationZ: Float = 0f,
    val speedLines: Boolean = false
)

private fun MessageAnimationPreset.thumbnailSpec(): ThumbnailSpec = when (this) {
    MessageAnimationPreset.Fade -> ThumbnailSpec(12f, 0f, 12f, 0f, ghostScale = 1f, ghostAlpha = 0.35f)
    MessageAnimationPreset.Slide -> ThumbnailSpec(-1f, 0f, 28f, 0f)
    MessageAnimationPreset.Spring -> ThumbnailSpec(4f, 6f, 24f, -3f, ghostScale = 0.78f, finalScale = 1.02f)
    MessageAnimationPreset.Pop -> ThumbnailSpec(11f, 0f, 17f, 0f, ghostScale = 0.68f, finalScale = 1.02f)
    MessageAnimationPreset.Flow -> ThumbnailSpec(-1f, -6f, 27f, 5f)
    MessageAnimationPreset.Drift -> ThumbnailSpec(-3f, -8f, 30f, 6f, ghostScale = 0.82f)
    MessageAnimationPreset.Quick -> ThumbnailSpec(19f, 0f, 20f, 0f, ghostAlpha = 0.28f, speedLines = true)
    MessageAnimationPreset.Tilt -> ThumbnailSpec(1f, 2f, 26f, -2f, ghostScale = 0.9f, rotationZ = -8f)
    MessageAnimationPreset.Sway -> ThumbnailSpec(1f, -3f, 26f, 3f, ghostScale = 0.9f, rotationZ = 8f)
    else -> ThumbnailSpec(10f, 0f, 10f, 0f)
}

@Composable
private fun AppearanceTuningCard(
    active: MessageAnimationDirection,
    controls: Set<MessageAnimationControl>,
    enabled: Boolean,
    onPresetDirection: (MessageAnimationMotionDirection) -> Unit,
    onPresetEasing: (MessageAnimationEasing) -> Unit,
    onReset: () -> Unit,
    onTuningChange: ((MessageAnimationTuning) -> MessageAnimationTuning) -> Unit,
    onSliderFinished: () -> Unit
) {
    val palette = beyGramPalette()
    var expanded by rememberSaveable(active.preset, enabled) { mutableStateOf(false) }
    val hasControls = controls.isNotEmpty() && active.preset != MessageAnimationPreset.None
    AppearanceCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(enabled = hasControls, role = Role.Button) { expanded = !expanded }
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.Tune, null, tint = if (hasControls) palette.accentInk else palette.textSecondary, modifier = Modifier.size(19.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    stringResource(R.string.appearance_animations_parameters),
                    color = if (hasControls) palette.textPrimary else palette.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    stringResource(
                        if (hasControls) R.string.appearance_animations_parameters_detail
                        else R.string.appearance_animations_no_parameters
                    ),
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            if (hasControls) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        AnimatedVisibility(
            visible = expanded && hasControls,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TuningGroup(stringResource(R.string.appearance_animations_group_dynamics)) {
                    if (MessageAnimationControl.Speed in controls) {
                        AppearanceSliderRow(
                            title = stringResource(R.string.theme_animations_speed),
                            valueText = String.format(Locale.getDefault(), "%.1f×", active.tuning.speed),
                            value = active.tuning.speed,
                            range = MIN_MESSAGE_ANIMATION_SPEED..MAX_MESSAGE_ANIMATION_SPEED,
                            enabled = enabled,
                            onValueChange = { value ->
                                val snapped = (value * 10f).roundToInt() / 10f
                                onTuningChange { it.copy(speed = validMessageAnimationSpeed(snapped)) }
                            },
                            onValueChangeFinished = onSliderFinished
                        )
                    }
                    if (MessageAnimationControl.Intensity in controls) {
                        AppearanceSliderRow(
                            title = stringResource(R.string.theme_animations_intensity),
                            valueText = stringResource(R.string.theme_animations_percent_value, (active.tuning.intensity * 100f).roundToInt()),
                            value = active.tuning.intensity,
                            range = 0f..1f,
                            enabled = enabled,
                            onValueChange = { value ->
                                val snapped = (value * 20f).roundToInt() / 20f
                                onTuningChange { it.copy(intensity = snapped.coerceIn(0f, 1f)) }
                            },
                            onValueChangeFinished = onSliderFinished
                        )
                    }
                }
                if (MessageAnimationControl.Scale in controls || MessageAnimationControl.Offset in controls) {
                    TuningGroup(stringResource(R.string.appearance_animations_group_geometry)) {
                        if (MessageAnimationControl.Scale in controls) {
                            AppearanceSliderRow(
                                title = stringResource(R.string.theme_animations_scale),
                                valueText = stringResource(R.string.theme_animations_percent_value, (active.tuning.scale * 100f).roundToInt()),
                                value = active.tuning.scale,
                                range = MIN_MESSAGE_ANIMATION_SCALE..MAX_MESSAGE_ANIMATION_SCALE,
                                enabled = enabled,
                                onValueChange = { value ->
                                    val snapped = (value * 100f).roundToInt() / 100f
                                    onTuningChange { it.copy(scale = validMessageAnimationScale(snapped)) }
                                },
                                onValueChangeFinished = onSliderFinished
                            )
                        }
                        if (MessageAnimationControl.Offset in controls) {
                            AppearanceSliderRow(
                                title = stringResource(R.string.theme_animations_offset),
                                valueText = stringResource(R.string.theme_animations_offset_value, active.tuning.offsetDp),
                                value = active.tuning.offsetDp.toFloat(),
                                range = MIN_MESSAGE_ANIMATION_OFFSET_DP.toFloat()..MAX_MESSAGE_ANIMATION_OFFSET_DP.toFloat(),
                                enabled = enabled,
                                onValueChange = { value ->
                                    onTuningChange { it.copy(offsetDp = validMessageAnimationOffsetDp(value.roundToInt())) }
                                },
                                onValueChangeFinished = onSliderFinished
                            )
                        }
                    }
                }
                if (MessageAnimationControl.Direction in controls) {
                    TuningGroup(stringResource(R.string.theme_animations_direction)) {
                        AppearanceChoiceFlow(
                            options = MessageAnimationMotionDirection.entries.map {
                                stringResource(it.labelRes()) to (active.tuning.motionDirection == it)
                            },
                            onSelect = { onPresetDirection(MessageAnimationMotionDirection.entries[it]) }
                        )
                    }
                }
                if (MessageAnimationControl.Easing in controls) {
                    TuningGroup(stringResource(R.string.theme_animations_easing)) {
                        AppearanceChoiceFlow(
                            options = MessageAnimationEasing.entries.map {
                                stringResource(it.labelRes()) to (active.tuning.easing == it)
                            },
                            onSelect = { onPresetEasing(MessageAnimationEasing.entries[it]) }
                        )
                    }
                }
                if (MessageAnimationControl.Springiness in controls) {
                    TuningGroup(stringResource(R.string.theme_animations_springiness)) {
                        AppearanceSliderRow(
                            title = stringResource(R.string.theme_animations_springiness),
                            valueText = stringResource(R.string.theme_animations_percent_value, (active.tuning.springiness * 100f).roundToInt()),
                            value = active.tuning.springiness,
                            range = MIN_MESSAGE_ANIMATION_SPRINGINESS..MAX_MESSAGE_ANIMATION_SPRINGINESS,
                            enabled = enabled,
                            onValueChange = { value ->
                                val snapped = (value * 20f).roundToInt() / 20f
                                onTuningChange { it.copy(springiness = validMessageAnimationSpringiness(snapped)) }
                            },
                            onValueChangeFinished = onSliderFinished
                        )
                    }
                }
                TextButton(onClick = onReset, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.appearance_animations_reset_tuning), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun TuningGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            title,
            color = beyGramPalette().textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium
        )
        content()
    }
}
