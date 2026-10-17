package com.beykus.beygram.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.R
import com.beykus.beygram.data.update.StableRelease
import com.beykus.beygram.ui.components.beyGramPalette

@Composable
fun StableUpdatesScreen(onBack: () -> Unit, model: StableUpdatesViewModel = viewModel()) {
    val palette = beyGramPalette()
    val status = model.status
    Column(
        Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back),
                    tint = palette.textPrimary)
            }
            Text(stringResource(R.string.updates_title), fontSize = 26.sp, color = palette.textPrimary,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
        Text(stringResource(R.string.updates_installed_version, BuildConfig.VERSION_NAME),
            color = palette.textSecondary)
        Text(stringResource(R.string.updates_stable_only), color = palette.textSecondary, fontSize = 14.sp)
        Button(onClick = model::check,
            enabled = status !is UpdateStatus.Checking && status !is UpdateStatus.Downloading,
            modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.updates_check))
        }
        when (status) {
            UpdateStatus.Idle -> Text(stringResource(R.string.updates_manual_hint), color = palette.textSecondary)
            UpdateStatus.Checking -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp))
                    Text(stringResource(R.string.updates_checking), color = palette.textPrimary)
                }
            }
            UpdateStatus.Current -> Text(stringResource(R.string.updates_current), color = palette.textPrimary)
            is UpdateStatus.Available -> ReleaseInformation(status.release, onDownload = { model.download(status.release) })
            is UpdateStatus.Downloading -> {
                Text(stringResource(R.string.updates_downloading, status.progress), color = palette.textPrimary)
                LinearProgressIndicator(progress = { status.progress / 100f }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = model::cancelDownload, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.updates_cancel))
                }
            }
            is UpdateStatus.Ready -> {
                ReleaseInformation(status.release)
                Button(onClick = { model.install(status.file) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.updates_install))
                }
            }
            is UpdateStatus.Failure -> {
                Text(stringResource(R.string.updates_failed, status.message), color = palette.textPrimary)
                status.release?.let { release ->
                    OutlinedButton(onClick = { model.download(release) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.updates_retry_download))
                    }
                }
            }
        }
        model.installHint?.let { Text(it, color = palette.textSecondary, fontSize = 14.sp) }
        Spacer(modifier = Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun ReleaseInformation(release: StableRelease, onDownload: (() -> Unit)? = null) {
    val palette = beyGramPalette()
    Text(stringResource(R.string.updates_available, release.versionName), fontSize = 19.sp,
        fontWeight = FontWeight.Bold, color = palette.textPrimary)
    Text(stringResource(R.string.updates_size, release.size / (1024f * 1024f)),
        color = palette.textSecondary, fontSize = 14.sp)
    if (release.publishedAt.isNotBlank()) {
        Text(stringResource(R.string.updates_published, release.publishedAt.take(10)),
            color = palette.textSecondary, fontSize = 14.sp)
    }
    Text(stringResource(R.string.updates_changes), fontWeight = FontWeight.Bold, color = palette.textPrimary)
    Text(release.notes.ifBlank { stringResource(R.string.updates_no_notes) },
        color = palette.textPrimary, fontSize = 15.sp)
    if (onDownload != null) {
        Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.updates_download))
        }
    }
}
