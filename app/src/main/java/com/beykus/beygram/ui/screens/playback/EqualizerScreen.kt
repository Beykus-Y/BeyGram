package com.beykus.beygram.ui.screens.playback

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.playback.EqualizerCapabilities
import com.beykus.beygram.playback.EqualizerConnection
import com.beykus.beygram.playback.EqualizerPreset
import com.beykus.beygram.playback.EqualizerRuntimeState
import com.beykus.beygram.playback.EqualizerSettings
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Real equalizer control for the shared music player. The screen only draws bands that the platform effect currently
 * reports. A missing session or effect becomes an explanatory state, never a set of decorative sliders.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(
    settings: EqualizerSettings,
    runtime: EqualizerRuntimeState,
    onEnabledChange: (Boolean) -> Unit,
    onPresetChange: (EqualizerPreset) -> Unit,
    onLevelsChange: (Map<Int, Int>, Boolean) -> Unit,
    onReset: () -> Unit,
    onSaveCustom: (Map<Int, Int>) -> Unit,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    BackHandler(onBack = onBack)
    val capabilities = runtime.capabilities
    val unavailable = runtime.connection == EqualizerConnection.Unsupported ||
        runtime.connection == EqualizerConnection.Failed
    val levels = remember(capabilities, settings) { capabilities?.levelsFor(settings).orEmpty() }
    val levelsByFrequency = remember(capabilities, levels) {
        capabilities?.frequenciesHz?.zip(levels)?.toMap().orEmpty()
    }
    val latestLevelsByFrequency = rememberUpdatedState(levelsByFrequency)
    val waitingForMusic = capabilities == null && runtime.connection == EqualizerConnection.Idle

    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.equalizer_title), onBack)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.equalizer_intro),
                color = palette.textSecondary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            when (runtime.connection) {
                EqualizerConnection.Unsupported -> EqualizerNotice(
                    icon = Icons.Rounded.WarningAmber,
                    title = stringResource(R.string.equalizer_status_unsupported_title),
                    detail = stringResource(R.string.equalizer_status_unsupported_detail),
                    error = true
                )
                EqualizerConnection.Failed -> EqualizerNotice(
                    icon = Icons.Rounded.WarningAmber,
                    title = stringResource(R.string.equalizer_status_failed_title),
                    detail = stringResource(R.string.equalizer_status_failed_detail),
                    error = true
                )
                else -> Unit
            }

            if (waitingForMusic) {
                EqualizerNotice(
                    icon = Icons.Rounded.MusicNote,
                    title = stringResource(R.string.equalizer_status_waiting_title),
                    detail = stringResource(R.string.equalizer_status_waiting_detail),
                    error = false
                )
            }

            EqualizerSwitchRow(
                checked = settings.enabled && !unavailable,
                enabled = !unavailable,
                onCheckedChange = onEnabledChange,
                detail = when {
                    unavailable -> stringResource(R.string.equalizer_status_unavailable_detail)
                    capabilities == null && settings.enabled -> stringResource(R.string.equalizer_status_idle)
                    settings.enabled && settings.selectedPreset != EqualizerPreset.Flat && levels.any { it != 0 } ->
                        stringResource(R.string.equalizer_enabled_detail)
                    settings.enabled -> stringResource(R.string.equalizer_neutral_detail)
                    else -> stringResource(R.string.equalizer_disabled_detail)
                }
            )

            if (capabilities != null) {
                if (runtime.connection == EqualizerConnection.Attached) {
                    EqualizerStatus(
                        text = stringResource(R.string.equalizer_status_attached),
                        detail = stringResource(R.string.equalizer_status_bands, capabilities.numberOfBands),
                        active = true
                    )
                } else if (runtime.connection == EqualizerConnection.Idle) {
                    EqualizerStatus(
                        text = stringResource(R.string.equalizer_status_idle),
                        detail = null,
                        active = false
                    )
                }

                EqualizerSectionTitle(stringResource(R.string.equalizer_presets_title))
                PresetChips(
                    selected = settings.selectedPreset,
                    customAvailable = settings.hasCustomPreset,
                    onSelect = onPresetChange
                )

                val curveDescription = stringResource(
                    R.string.equalizer_curve_description,
                    capabilities.numberOfBands
                )
                EqualizerCurve(
                    capabilities = capabilities,
                    levelsMb = levels,
                    active = settings.enabled,
                    description = curveDescription,
                    onLevelsChange = onLevelsChange
                )
                FrequencyAxis(capabilities)

                EqualizerSectionTitle(stringResource(R.string.equalizer_bands_title))
                Text(
                    stringResource(
                        R.string.equalizer_range_note,
                        formatDb(capabilities.minLevelMb),
                        formatDb(capabilities.maxLevelMb)
                    ),
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    capabilities.frequenciesHz.forEachIndexed { index, frequencyHz ->
                        if (index > 0) HorizontalDivider(color = palette.divider)
                        BandLevelRow(
                            frequencyHz = frequencyHz,
                            levelMb = levels.getOrElse(index) { 0 },
                            capabilities = capabilities,
                            onLevelChange = { levelMb, persist ->
                                val next = latestLevelsByFrequency.value.toMutableMap()
                                next[frequencyHz] = levelMb
                                onLevelsChange(next, persist)
                            }
                        )
                    }
                }

                if (capabilities.boostIsLimited) {
                    Text(
                        stringResource(R.string.equalizer_gain_limit_note, formatDb(capabilities.maxLevelMb)),
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                FlowRow(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onReset, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.equalizer_reset))
                    }
                    OutlinedButton(
                        onClick = { onSaveCustom(latestLevelsByFrequency.value) },
                        enabled = levelsByFrequency.isNotEmpty(),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.equalizer_save_custom))
                    }
                }
                Text(
                    stringResource(R.string.equalizer_custom_hint),
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.equalizer_done))
                }
            }

            // Leaves room for the app-wide mini player when this settings screen is opened during playback.
            Spacer(Modifier.height(88.dp))
        }
    }
}

@Composable
private fun EqualizerSwitchRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    detail: String
) {
    val palette = beyGramPalette()
    val title = stringResource(R.string.equalizer_enabled_title)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface)
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = if (enabled) palette.textPrimary else palette.textSecondary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = palette.textSecondary, fontSize = 13.sp, lineHeight = 17.sp)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun EqualizerNotice(
    icon: ImageVector,
    title: String,
    detail: String,
    error: Boolean
) {
    val palette = beyGramPalette()
    val background = if (error) palette.warningSurface else palette.accentSoft
    val foreground = if (error) palette.warningText else palette.accentInk
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(background)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, null, tint = foreground, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = foreground, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = foreground, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun EqualizerStatus(text: String, detail: String?, active: Boolean) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(8.dp).clip(CircleShape)
                .background(if (active) palette.accent else palette.textSecondary)
        )
        Column(Modifier.padding(start = 10.dp)) {
            Text(text, color = if (active) palette.accentInk else palette.textPrimary, fontSize = 14.sp, lineHeight = 19.sp)
            if (detail != null) {
                Text(detail, color = palette.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun EqualizerSectionTitle(text: String) {
    val palette = beyGramPalette()
    Text(
        text,
        color = palette.textPrimary,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetChips(
    selected: EqualizerPreset,
    customAvailable: Boolean,
    onSelect: (EqualizerPreset) -> Unit
) {
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EqualizerPreset.entries.forEach { preset ->
            PresetChip(
                label = presetLabel(preset),
                selected = preset == selected,
                enabled = preset != EqualizerPreset.Custom || customAvailable || preset == selected,
                onClick = { onSelect(preset) }
            )
        }
    }
}

@Composable
private fun PresetChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.heightIn(min = 48.dp).clip(shape)
            .background(
                when {
                    selected -> palette.accentSoft
                    enabled -> palette.surface
                    else -> palette.surface2
                }
            )
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) palette.accent else palette.divider,
                shape
            )
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = when {
                selected -> palette.accentInk
                enabled -> palette.textPrimary
                else -> palette.textSecondary
            },
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun presetLabel(preset: EqualizerPreset): String = when (preset) {
    EqualizerPreset.Flat -> stringResource(R.string.equalizer_preset_flat)
    EqualizerPreset.Bass -> stringResource(R.string.equalizer_preset_bass)
    EqualizerPreset.Vocal -> stringResource(R.string.equalizer_preset_vocal)
    EqualizerPreset.Pop -> stringResource(R.string.equalizer_preset_pop)
    EqualizerPreset.Rock -> stringResource(R.string.equalizer_preset_rock)
    EqualizerPreset.Electronic -> stringResource(R.string.equalizer_preset_electronic)
    EqualizerPreset.Classical -> stringResource(R.string.equalizer_preset_classical)
    EqualizerPreset.Jazz -> stringResource(R.string.equalizer_preset_jazz)
    EqualizerPreset.Custom -> stringResource(R.string.equalizer_preset_custom)
}

@Composable
private fun EqualizerCurve(
    capabilities: EqualizerCapabilities,
    levelsMb: List<Int>,
    active: Boolean,
    description: String,
    onLevelsChange: (Map<Int, Int>, Boolean) -> Unit
) {
    val palette = beyGramPalette()
    val latestLevels = rememberUpdatedState(levelsMb)
    val latestOnLevelsChange = rememberUpdatedState(onLevelsChange)
    val frequencies = capabilities.frequenciesHz
    val minLevel = capabilities.minLevelMb
    val maxLevel = capabilities.maxLevelMb
    val lineColor = if (active) palette.accent else palette.textSecondary
    val fillColor = lineColor.copy(alpha = if (active) 0.16f else 0.07f)
    val surface = palette.surface2
    val grid = palette.divider
    val shape = RoundedCornerShape(20.dp)

    Canvas(
        Modifier.fillMaxWidth().height(190.dp).clip(shape).background(surface)
            .pointerInput(frequencies, minLevel, maxLevel) {
                if (frequencies.isEmpty() || maxLevel <= minLevel) return@pointerInput
                var dragBand = -1
                var working = emptyList<Int>()
                fun levelAt(y: Float, height: Float): Int {
                    val fraction = (1f - (y / height).coerceIn(0f, 1f))
                    return quantizeLevelMb(minLevel + fraction * (maxLevel - minLevel)).coerceIn(minLevel, maxLevel)
                }
                fun publish(persist: Boolean) {
                    if (working.size != frequencies.size) return
                    latestOnLevelsChange.value(frequencies.zip(working).toMap(), persist)
                }
                fun updateLevel(y: Float, height: Float) {
                    if (dragBand !in working.indices) return
                    working = working.toMutableList().also { it[dragBand] = levelAt(y, height) }
                    publish(persist = false)
                }
                detectDragGestures(
                    onDragStart = { position ->
                        if (latestLevels.value.size == frequencies.size) {
                            dragBand = nearestBand(position.x, size.width.toFloat(), frequencies)
                            working = latestLevels.value.toList()
                            updateLevel(position.y, size.height.toFloat())
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        updateLevel(change.position.y, size.height.toFloat())
                    },
                    onDragEnd = { publish(persist = true) },
                    onDragCancel = { publish(persist = true) }
                )
            }
            .semantics { contentDescription = description }
    ) {
        val width = size.width
        val height = size.height
        val horizontalInset = 10.dp.toPx()
        val verticalInset = 16.dp.toPx()
        fun xFor(frequencyHz: Int): Float = horizontalInset + widthPosition(frequencyHz, frequencies) * (width - horizontalInset * 2)
        fun yFor(levelMb: Int): Float {
            val fraction = ((levelMb - minLevel).toFloat() / (maxLevel - minLevel).toFloat()).coerceIn(0f, 1f)
            return verticalInset + (1f - fraction) * (height - verticalInset * 2)
        }

        frequencies.forEach { frequency ->
            val x = xFor(frequency)
            drawLine(grid, Offset(x, verticalInset), Offset(x, height - verticalInset), strokeWidth = 1.dp.toPx())
        }
        if (0 in minLevel..maxLevel) {
            drawLine(
                grid,
                Offset(horizontalInset, yFor(0)),
                Offset(width - horizontalInset, yFor(0)),
                strokeWidth = 1.5.dp.toPx()
            )
        }

        val points = frequencies.mapIndexed { index, frequency ->
            Offset(xFor(frequency), yFor(levelsMb.getOrElse(index) { 0 }))
        }
        if (points.isNotEmpty()) {
            val line = Path()
            val fill = Path()
            line.moveTo(points.first().x, points.first().y)
            fill.moveTo(points.first().x, height - verticalInset)
            fill.lineTo(points.first().x, points.first().y)
            for (index in 0 until points.lastIndex) {
                val first = points[index]
                val second = points[index + 1]
                val middleX = (first.x + second.x) / 2f
                line.cubicTo(middleX, first.y, middleX, second.y, second.x, second.y)
                fill.cubicTo(middleX, first.y, middleX, second.y, second.x, second.y)
            }
            fill.lineTo(points.last().x, height - verticalInset)
            fill.close()
            drawPath(fill, fillColor)
            drawPath(line, lineColor, style = Stroke(width = 2.5.dp.toPx()))
            points.forEach { point ->
                drawCircle(surface, radius = 6.dp.toPx(), center = point)
                drawCircle(lineColor, radius = 4.dp.toPx(), center = point)
            }
        }
    }
}

@Composable
private fun FrequencyAxis(capabilities: EqualizerCapabilities) {
    val palette = beyGramPalette()
    val frequencies = capabilities.frequenciesHz
    if (frequencies.isEmpty()) return
    val indices = when {
        frequencies.size <= 3 -> frequencies.indices.toList()
        else -> listOf(0, frequencies.size / 2, frequencies.lastIndex).distinct()
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        indices.forEachIndexed { position, bandIndex ->
            Text(
                frequencyLabel(frequencies[bandIndex]),
                color = palette.textSecondary,
                fontSize = 11.sp,
                textAlign = when (position) {
                    0 -> TextAlign.Start
                    indices.lastIndex -> TextAlign.End
                    else -> TextAlign.Center
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BandLevelRow(
    frequencyHz: Int,
    levelMb: Int,
    capabilities: EqualizerCapabilities,
    onLevelChange: (Int, Boolean) -> Unit
) {
    val palette = beyGramPalette()
    val frequency = frequencyLabel(frequencyHz)
    var dragging by remember(frequencyHz) { mutableStateOf(false) }
    var localLevel by remember(frequencyHz) { mutableStateOf(levelMb) }
    LaunchedEffect(levelMb, dragging) {
        if (!dragging) localLevel = levelMb
    }
    val shownLevel = localLevel
    val levelText = stringResource(R.string.equalizer_level_db, formatDb(shownLevel))
    val description = stringResource(R.string.equalizer_band_description, frequency, levelText)
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                frequency,
                color = palette.textPrimary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                levelText,
                color = if (shownLevel != 0) palette.accentInk else palette.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = shownLevel.toFloat(),
            onValueChange = { value ->
                val next = quantizeLevelMb(value).coerceIn(capabilities.minLevelMb, capabilities.maxLevelMb)
                dragging = true
                localLevel = next
                onLevelChange(next, false)
            },
            onValueChangeFinished = {
                dragging = false
                onLevelChange(localLevel, true)
            },
            valueRange = capabilities.minLevelMb.toFloat()..capabilities.maxLevelMb.toFloat(),
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = description
            },
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.surface2,
                disabledThumbColor = palette.textSecondary,
                disabledActiveTrackColor = palette.divider,
                disabledInactiveTrackColor = palette.surface2
            )
        )
    }
}

/** Log-spaced position of [frequencyHz] between the lowest and highest real band. */
private fun widthPosition(frequencyHz: Int, frequencies: List<Int>): Float {
    if (frequencies.size <= 1) return 0.5f
    val minimum = frequencies.minOrNull()?.coerceAtLeast(1) ?: 1
    val maximum = frequencies.maxOrNull()?.coerceAtLeast(1) ?: 1
    if (maximum <= minimum) return 0.5f
    val position = (ln(frequencyHz.coerceAtLeast(1).toDouble()) - ln(minimum.toDouble())) /
        (ln(maximum.toDouble()) - ln(minimum.toDouble()))
    return position.toFloat().coerceIn(0f, 1f)
}

private fun nearestBand(x: Float, width: Float, frequencies: List<Int>): Int {
    if (frequencies.isEmpty() || width <= 0f) return 0
    val fraction = (x / width).coerceIn(0f, 1f)
    var best = 0
    var bestDistance = Float.MAX_VALUE
    frequencies.forEachIndexed { index, frequency ->
        val position = widthPosition(frequency, frequencies)
        val distance = abs(position - fraction)
        if (distance < bestDistance) {
            bestDistance = distance
            best = index
        }
    }
    return best
}

@Composable
private fun frequencyLabel(frequencyHz: Int): String {
    val context = LocalContext.current
    return if (frequencyHz >= 1_000) {
        val khz = frequencyHz / 1_000f
        val decimals = if (khz >= 10f || khz == khz.roundToInt().toFloat()) 0 else 1
        context.getString(
            R.string.equalizer_frequency_khz,
            String.format(Locale.getDefault(), "%.${decimals}f", khz)
        )
    } else {
        context.getString(R.string.equalizer_frequency_hz, frequencyHz)
    }
}

private fun quantizeLevelMb(value: Float): Int = (value / 10f).roundToInt() * 10

private fun formatDb(levelMb: Int): String {
    val normalized = if (levelMb in -4..4) 0 else levelMb
    val pattern = if (normalized > 0) "%+.1f" else "%.1f"
    return String.format(Locale.getDefault(), pattern, normalized / 100f)
}
