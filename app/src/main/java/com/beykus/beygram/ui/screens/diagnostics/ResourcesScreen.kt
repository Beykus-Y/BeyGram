package com.beykus.beygram.ui.screens.diagnostics

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.diagnostics.resources.RESOURCE_HISTORY_CAPACITY
import com.beykus.beygram.diagnostics.resources.RESOURCE_SAMPLE_INTERVAL_MILLIS
import com.beykus.beygram.diagnostics.resources.BoundedBuffer
import com.beykus.beygram.diagnostics.resources.ResourceSample
import com.beykus.beygram.diagnostics.resources.ResourceSampler
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Экран «Ресурсы BeyGram». Опрос запускается в LaunchedEffect, поэтому останавливается
 * сразу после выхода с экрана. История живёт, пока экран в композиции.
 */
@Composable
fun ResourcesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val sampler = remember { ResourceSampler() }
    val history = remember { BoundedBuffer<ResourceSample>(RESOURCE_HISTORY_CAPACITY) }
    var samples by remember { mutableStateOf(emptyList<ResourceSample>()) }
    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        while (true) {
            history.add(withContext(Dispatchers.Default) { sampler.sample() })
            samples = history.snapshot()
            delay(RESOURCE_SAMPLE_INTERVAL_MILLIS)
        }
    }

    val latest = samples.lastOrNull()
    val unavailable = stringResource(R.string.resources_unavailable)

    Column(
        Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    ) {
        ProfileToolbar(stringResource(R.string.resources_title), onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.resources_intro), color = palette.textSecondary, fontSize = 13.sp, lineHeight = 17.sp)

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface).padding(16.dp)) {
                val cpu = latest?.rates?.cpuPercent
                Text(stringResource(R.string.resources_cpu), color = palette.textPrimary, fontSize = 16.sp)
                Text(
                    cpu?.let { String.format(Locale.getDefault(), "%.1f%%", it) } ?: unavailable,
                    color = palette.textSecondary, fontSize = 13.sp
                )
                ResourceChart(
                    series = samples.map { it.rates?.cpuPercent },
                    color = palette.accent,
                    modifier = Modifier.fillMaxWidth().height(96.dp)
                )
                Spacer(Modifier.height(12.dp))

                Text(stringResource(R.string.resources_heap), color = palette.textPrimary, fontSize = 16.sp)
                Text(
                    latest?.let {
                        stringResource(
                            R.string.resources_heap_value,
                            Formatter.formatShortFileSize(context, it.javaHeapUsedBytes),
                            Formatter.formatShortFileSize(context, it.javaHeapMaxBytes)
                        )
                    } ?: unavailable,
                    color = palette.textSecondary, fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))

                Text(stringResource(R.string.resources_native), color = palette.textPrimary, fontSize = 16.sp)
                Text(
                    latest?.let { Formatter.formatShortFileSize(context, it.nativeHeapBytes) } ?: unavailable,
                    color = palette.textSecondary, fontSize = 13.sp
                )
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface).padding(16.dp)) {
                Text(stringResource(R.string.resources_network), color = palette.textPrimary, fontSize = 16.sp)
                Text(
                    latest?.let {
                        stringResource(
                            R.string.resources_traffic_total,
                            rateText(context, it.counters.rxBytes, unavailable),
                            rateText(context, it.counters.txBytes, unavailable)
                        )
                    } ?: unavailable,
                    color = palette.textSecondary, fontSize = 13.sp
                )
                Text(
                    latest?.let {
                        stringResource(
                            R.string.resources_traffic_rate,
                            rateText(context, it.rates?.rxBytesPerSecond?.toLong(), unavailable),
                            rateText(context, it.rates?.txBytesPerSecond?.toLong(), unavailable)
                        )
                    } ?: unavailable,
                    color = palette.textSecondary, fontSize = 13.sp
                )
                ResourceChart(
                    series = samples.map { it.rates?.rxBytesPerSecond },
                    color = palette.accent,
                    modifier = Modifier.fillMaxWidth().height(96.dp)
                )
                Text(stringResource(R.string.resources_network_note), color = palette.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

private fun rateText(context: android.content.Context, bytes: Long?, unavailable: String): String =
    bytes?.let { Formatter.formatShortFileSize(context, it) } ?: unavailable

/** Линия по последним опросам. Пустые значения разрывают линию: это не ноль. */
@Composable
private fun ResourceChart(series: List<Double?>, color: Color, modifier: Modifier) {
    val peak = (series.filterNotNull().maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Canvas(modifier) {
        val path = Path()
        var drawing = false
        series.forEachIndexed { index, value ->
            if (value == null) {
                drawing = false
                return@forEachIndexed
            }
            val x = index * size.width / (RESOURCE_HISTORY_CAPACITY - 1)
            val y = size.height - (value / peak * size.height).toFloat()
            if (drawing) path.lineTo(x, y) else path.moveTo(x, y)
            drawing = true
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    }
}
