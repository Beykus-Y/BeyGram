package com.beykus.beygram.ui.screens.diagnostics

import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.diagnostics.resources.BoundedBuffer
import com.beykus.beygram.diagnostics.resources.RESOURCE_HISTORY_CAPACITY
import com.beykus.beygram.diagnostics.resources.RESOURCE_SAMPLE_INTERVAL_MILLIS
import com.beykus.beygram.diagnostics.resources.ResourceSample
import com.beykus.beygram.diagnostics.resources.ResourceSampler
import com.beykus.beygram.diagnostics.resources.ResourceSessionRecorder
import com.beykus.beygram.diagnostics.resources.formatResourceReport
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.Button
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.groupSurfaceColor
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassCard
import com.beykus.beygram.ui.design.glassScreenBackground
import com.beykus.beygram.ui.design.groupCardShape
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import com.beykus.beygram.ui.components.GlassScreenScaffold

/**
 * Экран «Ресурсы BeyGram». Опрос идёт только в состоянии RESUMED: при сворачивании
 * или переходе на другой экран корутина останавливается, а при возврате история
 * и сравнение со старым снимком начинаются заново.
 */
@Composable
fun ResourcesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var samples by remember { mutableStateOf(emptyList<ResourceSample>()) }
    val recorder = remember { ResourceSessionRecorder() }
    var recording by remember { mutableStateOf(false) }
    var reportSamples by remember { mutableStateOf<List<ResourceSample>?>(null) }
    val cpuCores = Runtime.getRuntime().availableProcessors()
    val stopRecording: () -> Unit = {
        recording = false
        val recorded = recorder.stop()
        if (recorded.isNotEmpty()) reportSamples = recorded
    }
    BackHandler(onBack = onBack)

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val sampler = ResourceSampler()
            val history = BoundedBuffer<ResourceSample>(RESOURCE_HISTORY_CAPACITY)
            samples = emptyList()
            try {
                while (true) {
                    history.add(withContext(Dispatchers.Default) { sampler.sample() })
                    samples = history.snapshot()
                    if (recording) {
                        recorder.add(samples.last())
                        if (recorder.isFull) stopRecording()
                    }
                    delay(RESOURCE_SAMPLE_INTERVAL_MILLIS)
                }
            } finally {
                // Сворачивание и уход с экрана прерывают запись: фон в неё не попадает.
                if (recording) {
                    recording = false
                    recorder.stop()
                }
            }
        }
    }

    val latest = samples.lastOrNull()
    val unavailable = stringResource(R.string.resources_unavailable)
    val measuring = stringResource(R.string.resources_measuring)
    // A chart needs at least two real samples; one point is a value, not a series.
    val hasSeries = samples.size >= 2

    reportSamples?.let { recorded ->
        val report = formatResourceReport(recorded, cpuCores)
        val minutes = (recorded.last().counters.timestampMillis - recorded.first().counters.timestampMillis) / 60_000
        BeyGramDialog(
            onDismissRequest = { reportSamples = null },
            title = stringResource(R.string.resources_report_title),
            content = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.resources_report_length, recorded.size, minutes.toInt()), fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.resources_report_note), fontSize = 13.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(report, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
                }
            },
            scrollableContent = false,
            confirmText = stringResource(R.string.resources_share),
            onConfirm = {
                val intent = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, report)
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.resources_share_chooser)))
                reportSamples = null
            },
            dismissText = stringResource(R.string.diagnostics_view_close),
            onDismiss = { reportSamples = null }
        )
    }

    val cpuValue = latest?.rates?.cpuPercent
    val cpuText = when {
        cpuValue != null -> String.format(Locale.getDefault(), "%.1f%%", cpuValue)
        // The first sample has no previous one to compare with, so its CPU rate is still being measured.
        latest != null && samples.size < 2 -> measuring
        else -> unavailable
    }
    val rates = latest?.rates
    val trafficRateText = when {
        latest == null -> unavailable
        rates == null -> measuring
        rates.rxBytesPerSecond == null && rates.txBytesPerSecond == null -> unavailable
        else -> stringResource(
            R.string.resources_traffic_rate,
            rateText(context, rates.rxBytesPerSecond?.toLong(), unavailable),
            rateText(context, rates.txBytesPerSecond?.toLong(), unavailable)
        )
    }

    GlassScreenScaffold(
        modifier = Modifier.fillMaxSize()
            .glassScreenBackground(design, palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
        toolbar = {
            ProfileToolbar(stringResource(R.string.resources_title), onBack)
        }
    ) { chrome ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        Spacer(Modifier.height(chrome.top))
            Text(stringResource(R.string.resources_intro), color = palette.textSecondary, fontSize = 13.sp, lineHeight = 17.sp)

            Button(
                onClick = {
                    if (recording) {
                        stopRecording()
                    } else {
                        recorder.start()
                        recording = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(if (recording) R.string.resources_record_stop else R.string.resources_record_start)
                )
            }
            if (recording) {
                Text(stringResource(R.string.resources_record_hint), color = palette.textSecondary, fontSize = 13.sp, lineHeight = 17.sp)
            }

            ResourceCard(stringResource(R.string.resources_cpu)) {
                MetricValue(value = cpuText, muted = cpuValue == null)
                if (hasSeries) {
                    MetricChart(
                        label = stringResource(R.string.resources_chart_cpu),
                        series = listOf(samples.map { it.rates?.cpuPercent }),
                        colors = listOf(palette.accent)
                    )
                }
            }

            ResourceCard(stringResource(R.string.resources_heap)) {
                MetricValue(
                    label = stringResource(R.string.resources_java_heap),
                    value = latest?.let {
                        stringResource(
                            R.string.resources_heap_value,
                            Formatter.formatShortFileSize(context, it.javaHeapUsedBytes),
                            Formatter.formatShortFileSize(context, it.javaHeapMaxBytes)
                        )
                    } ?: unavailable,
                    muted = latest == null
                )
                if (hasSeries) {
                    MetricChart(
                        label = stringResource(R.string.resources_chart_heap),
                        series = listOf(samples.map { it.javaHeapUsedBytes.toDouble() }),
                        colors = listOf(palette.accent)
                    )
                }
                HorizontalDivider(color = palette.divider)
                MetricValue(
                    label = stringResource(R.string.resources_native),
                    value = latest?.let { Formatter.formatShortFileSize(context, it.nativeHeapBytes) } ?: unavailable,
                    muted = latest == null
                )
                if (hasSeries) {
                    MetricChart(
                        label = stringResource(R.string.resources_chart_native),
                        series = listOf(samples.map { it.nativeHeapBytes.toDouble() }),
                        colors = listOf(palette.accent)
                    )
                }
            }

            ResourceCard(stringResource(R.string.resources_network)) {
                Text(trafficRateText, color = palette.textPrimary, fontSize = 15.sp, lineHeight = 20.sp)
                Text(
                    latest?.let {
                        stringResource(
                            R.string.resources_traffic_total,
                            rateText(context, it.counters.rxBytes, unavailable),
                            rateText(context, it.counters.txBytes, unavailable)
                        )
                    } ?: unavailable,
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                )
                if (hasSeries) {
                    MetricChart(
                        label = stringResource(R.string.resources_chart_network),
                        series = listOf(
                            samples.map { it.rates?.rxBytesPerSecond },
                            samples.map { it.rates?.txBytesPerSecond }
                        ),
                        colors = listOf(palette.accent, palette.textSecondary)
                    )
                }
                Text(stringResource(R.string.resources_network_note), color = palette.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

/** Карточка одного показателя: заголовок и содержимое на поверхности группы настроек. */
@Composable
private fun ResourceCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    Column(
        Modifier.fillMaxWidth()
            .glassCard(design, groupSurfaceColor(), palette.textSecondary, groupCardShape())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            title,
            color = palette.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() }
        )
        content()
    }
}

/** Значение показателя крупно, с подписью сверху. Пустое или ещё не измеренное значение выводится приглушённо. */
@Composable
private fun MetricValue(value: String, label: String? = null, muted: Boolean = false) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth()) {
        if (label != null) {
            Text(label, color = palette.textSecondary, fontSize = 13.sp, lineHeight = 17.sp)
        }
        Text(
            value,
            color = if (muted) palette.textSecondary else palette.textPrimary,
            fontSize = if (muted) 15.sp else 22.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = if (muted) 20.sp else 28.sp
        )
    }
}

/** График с подписью под ним. Подпись читает TalkBack через сам график, поэтому не дублируется. */
@Composable
private fun MetricChart(label: String, series: List<List<Double?>>, colors: List<Color>) {
    val palette = beyGramPalette()
    ResourceChart(
        label = label,
        series = series,
        colors = colors,
        modifier = Modifier.fillMaxWidth().height(88.dp)
    )
    Text(
        label,
        color = palette.textSecondary,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = Modifier.clearAndSetSemantics { }
    )
}

private fun rateText(context: Context, bytes: Long?, unavailable: String): String =
    bytes?.let { Formatter.formatShortFileSize(context, it) } ?: unavailable

/**
 * Линии по последним опросам, по одной на каждую серию. Пустое значение разрывает линию
 * и не рисуется нулём. Одиночная точка рисуется кружком.
 */
@Composable
private fun ResourceChart(label: String, series: List<List<Double?>>, colors: List<Color>, modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = label }) {
        val peak = (series.flatten().filterNotNull().maxOrNull() ?: 0.0).coerceAtLeast(1.0)
        val strokePx = 2.dp.toPx()
        series.forEachIndexed { seriesIndex, values ->
            val path = Path()
            values.forEachIndexed { index, value ->
                if (value == null) return@forEachIndexed
                val x = index * size.width / (RESOURCE_HISTORY_CAPACITY - 1)
                val y = size.height - (value / peak * size.height).toFloat()
                val before = values.getOrNull(index - 1)
                val after = values.getOrNull(index + 1)
                when {
                    before == null && after == null -> drawCircle(colors[seriesIndex], strokePx, Offset(x, y))
                    before == null -> path.moveTo(x, y)
                    else -> path.lineTo(x, y)
                }
            }
            drawPath(path, colors[seriesIndex], style = Stroke(width = strokePx))
        }
    }
}
