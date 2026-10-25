package com.beykus.beygram.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.media.AutoDownloadNetwork
import com.beykus.beygram.data.media.AutoDownloadNetworkStatus
import com.beykus.beygram.data.media.TrafficOrigin
import com.beykus.beygram.data.media.TrafficSizeUnit
import com.beykus.beygram.data.media.TrafficUsageSnapshot
import com.beykus.beygram.data.media.TrafficUsageStore
import com.beykus.beygram.data.media.trafficSizeValue

/** Localized size for the traffic indicator; the numeric/unit split itself is covered by unit tests. */
@Composable
internal fun formatTrafficSize(bytes: Long): String {
    val value = trafficSizeValue(bytes)
    return when (value.unit) {
        TrafficSizeUnit.Bytes -> stringResource(R.string.traffic_size_bytes, value.amount.toLong())
        TrafficSizeUnit.Kilobytes -> stringResource(R.string.traffic_size_kb, value.amount.toLong())
        TrafficSizeUnit.Megabytes -> stringResource(R.string.traffic_size_mb, value.amount)
        TrafficSizeUnit.Gigabytes -> stringResource(R.string.traffic_size_gb, value.amount)
    }
}

@Composable
internal fun autoDownloadNetworkLabel(network: AutoDownloadNetwork): String = when (network) {
    AutoDownloadNetwork.Wifi -> stringResource(R.string.auto_download_network_wifi)
    AutoDownloadNetwork.WifiMetered -> stringResource(R.string.traffic_network_wifi_metered)
    AutoDownloadNetwork.Mobile -> stringResource(R.string.auto_download_network_mobile)
    AutoDownloadNetwork.Roaming -> stringResource(R.string.auto_download_network_roaming)
    AutoDownloadNetwork.Other -> stringResource(R.string.auto_download_network_other)
    AutoDownloadNetwork.None -> stringResource(R.string.traffic_network_offline)
}

/**
 * Summary card in auto-download settings: the live route, data-saver state, running transfers and
 * session totals. It contains no controls, so it never adds a small touch target.
 */
@Composable
internal fun TrafficUsageSummary(
    snapshot: TrafficUsageSnapshot,
    network: AutoDownloadNetwork,
    dataSaver: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = beyGramPalette()
    val networkLabel = autoDownloadNetworkLabel(network)
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.traffic_summary_title),
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            Text(
                networkLabel,
                color = palette.accentInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(palette.accentSoft)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        if (network != AutoDownloadNetwork.None) {
            Text(
                stringResource(if (network.metered) R.string.traffic_summary_metered else R.string.traffic_summary_unmetered),
                color = palette.textSecondary,
                fontSize = 13.sp
            )
        }
        Text(
            stringResource(if (dataSaver) R.string.traffic_summary_data_saver_on else R.string.traffic_summary_data_saver_off),
            color = palette.textSecondary,
            fontSize = 13.sp
        )
        if (dataSaver && network.metered && network != AutoDownloadNetwork.None) {
            Text(
                stringResource(R.string.traffic_summary_data_saver_blocks_auto),
                color = palette.textSecondary,
                fontSize = 13.sp
            )
        }
        Text(
            when {
                snapshot.autoActive > 0 && snapshot.manualActive > 0 ->
                    stringResource(R.string.traffic_summary_active_both, snapshot.autoActive, snapshot.manualActive)
                snapshot.autoActive > 0 -> stringResource(R.string.traffic_summary_auto_active, snapshot.autoActive)
                snapshot.manualActive > 0 -> stringResource(R.string.traffic_summary_manual_active, snapshot.manualActive)
                else -> stringResource(R.string.traffic_summary_idle)
            },
            color = palette.textPrimary,
            fontSize = 13.sp
        )
        HorizontalDivider(color = palette.divider, modifier = Modifier.padding(vertical = 2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TrafficTotal(
                label = stringResource(R.string.traffic_summary_auto_total),
                bytes = snapshot.autoBytes,
                modifier = Modifier.weight(1f)
            )
            TrafficTotal(
                label = stringResource(R.string.traffic_summary_manual_total),
                bytes = snapshot.manualBytes,
                modifier = Modifier.weight(1f)
            )
            TrafficTotal(
                label = stringResource(R.string.traffic_summary_total),
                bytes = snapshot.totalBytes,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            stringResource(R.string.traffic_summary_session_note),
            color = palette.textSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun TrafficTotal(label: String, bytes: Long, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    Column(modifier) {
        Text(label, color = palette.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatTrafficSize(bytes), color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Compact read-only badge for the media viewer. It subscribes to the account's session only while it
 * is composed, so hidden or completed media do not keep collecting state updates.
 */
@Composable
internal fun TrafficUsageBadge(
    accountId: String,
    fileId: Int,
    downloadedBytes: Long,
    totalBytes: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val usage by remember(accountId) { TrafficUsageStore.session(accountId).snapshot }.collectAsStateWithLifecycle()
    val network by AutoDownloadNetworkStatus.network.collectAsStateWithLifecycle()
    LaunchedEffect(context) { AutoDownloadNetworkStatus.ensureStarted(context) }
    val origin = usage.activeOrigins[fileId] ?: TrafficOrigin.Auto
    val originLabel = stringResource(
        if (origin == TrafficOrigin.Auto) R.string.traffic_origin_auto else R.string.traffic_origin_manual
    )
    val downloaded = formatTrafficSize(downloadedBytes)
    val networkLabel = autoDownloadNetworkLabel(network)
    val text = if (totalBytes > 0L) {
        stringResource(R.string.traffic_badge_progress, originLabel, downloaded, formatTrafficSize(totalBytes), networkLabel)
    } else {
        stringResource(R.string.traffic_badge_progress_unknown, originLabel, downloaded, networkLabel)
    }
    Text(
        text,
        color = Color.White,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
