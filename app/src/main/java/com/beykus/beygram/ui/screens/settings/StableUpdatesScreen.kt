package com.beykus.beygram.ui.screens.settings

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.R
import com.beykus.beygram.data.update.DownloadProgress
import com.beykus.beygram.data.update.InstallNotice
import com.beykus.beygram.data.update.StableRelease
import com.beykus.beygram.data.update.StableUpdateActionPolicy
import com.beykus.beygram.data.update.UpdateChannel
import com.beykus.beygram.data.update.UpdateStatus
import com.beykus.beygram.data.update.UpdateUiState
import com.beykus.beygram.data.update.updateChannelFor
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.Button
import com.beykus.beygram.ui.components.OutlinedButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassScreenBackground
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import java.time.Instant

/** Tone of the status badge. Its colours come from the palette, so every style and theme keeps its own. */
private enum class StatusTone { Neutral, Accent, Success, Warning }

/** Words, badge and announcement of the status line. [headline] is null only for the idle hint. */
private class StatusLineContent(
    val headline: String?,
    val detail: String?,
    val tone: StatusTone,
    val icon: ImageVector? = null,
    val spinning: Boolean = false,
    /** Results are announced to TalkBack; progress steps are not, so a download does not repeat itself aloud. */
    val announce: Boolean = false,
)

/**
 * The update page: the installed build with its status, the release on offer (or the one a failed check kept on screen),
 * and its notes. Each state names its next action, and every button acts on the process it belongs to.
 */
@Composable
fun StableUpdatesScreen(onBack: () -> Unit, model: StableUpdatesViewModel = viewModel()) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val context = LocalContext.current
    val uiState by model.state.collectAsStateWithLifecycle()
    val status = uiState.status
    val isDevBeta = updateChannelFor(BuildConfig.APPLICATION_ID) == UpdateChannel.DevBeta
    val lifecycleOwner = LocalLifecycleOwner.current
    // Returning from Android Settings or the installer re-reads the install permission and the installed version.
    DisposableEffect(lifecycleOwner, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.onReturnedToApp()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // A running or failed check keeps the release of the last successful check visible.
    val release: StableRelease? = when (status) {
        is UpdateStatus.Available -> status.release
        is UpdateStatus.Downloading -> status.release
        is UpdateStatus.Verifying -> status.release
        is UpdateStatus.Ready -> status.release
        is UpdateStatus.PreparingInstall -> status.release
        is UpdateStatus.PermissionRequired -> status.release
        is UpdateStatus.AwaitingInstaller -> status.release
        is UpdateStatus.Failure -> status.release ?: uiState.knownRelease
        UpdateStatus.Checking -> uiState.knownRelease
        UpdateStatus.Idle, UpdateStatus.Current, is UpdateStatus.Installed -> null
    }
    val retryCheck = status is UpdateStatus.Failure && status.release == null
    Column(
        Modifier.fillMaxSize()
            .glassScreenBackground(design, palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        ProfileToolbar(stringResource(R.string.updates_title), onBack)
        Column(
            Modifier.fillMaxWidth().weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            SettingsGroup(stringResource(R.string.updates_group_installed)) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        BuildConfig.VERSION_NAME,
                        color = palette.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(if (isDevBeta) R.string.updates_dev_channel else R.string.updates_stable_only),
                        color = palette.textSecondary,
                        fontSize = 14.sp
                    )
                    StatusLine(status, isDevBeta)
                    uiState.lastCheckedAt?.let { millis ->
                        Text(
                            stringResource(R.string.updates_last_checked, formatUpdateDate(context, millis, withTime = true)),
                            color = palette.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Button(
                        onClick = model::check,
                        enabled = StableUpdateActionPolicy.canCheck(status),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(if (retryCheck) R.string.updates_retry_check else R.string.updates_check))
                    }
                }
            }
            release?.let { current ->
                ReleaseCard(model, uiState, current, isDevBeta)
                SettingsGroup(stringResource(R.string.updates_changes)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                        ReleaseNotesContent(current.notes)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusLine(status: UpdateStatus, isDevBeta: Boolean) {
    val palette = beyGramPalette()
    val line = when (status) {
        UpdateStatus.Idle -> StatusLineContent(
            headline = null,
            detail = stringResource(if (isDevBeta) R.string.updates_dev_manual_hint else R.string.updates_manual_hint),
            tone = StatusTone.Neutral
        )
        UpdateStatus.Checking -> StatusLineContent(
            headline = stringResource(R.string.updates_checking),
            detail = null,
            tone = StatusTone.Neutral,
            spinning = true
        )
        UpdateStatus.Current -> StatusLineContent(
            headline = stringResource(R.string.updates_state_current),
            detail = stringResource(R.string.updates_current),
            tone = StatusTone.Success,
            icon = Icons.Rounded.CheckCircle,
            announce = true
        )
        is UpdateStatus.Installed -> StatusLineContent(
            headline = stringResource(R.string.updates_state_installed),
            detail = null,
            tone = StatusTone.Success,
            icon = Icons.Rounded.CheckCircle,
            announce = true
        )
        is UpdateStatus.Available -> StatusLineContent(
            headline = stringResource(R.string.updates_state_available),
            detail = stringResource(R.string.updates_available, status.release.versionName),
            tone = StatusTone.Accent,
            icon = Icons.Rounded.SystemUpdate,
            announce = true
        )
        is UpdateStatus.Downloading -> StatusLineContent(
            headline = stringResource(R.string.updates_state_downloading, status.progress.percent),
            detail = downloadDetail(status.progress),
            tone = StatusTone.Accent,
            icon = Icons.Rounded.Download
        )
        is UpdateStatus.Verifying -> StatusLineContent(
            headline = stringResource(R.string.updates_state_verifying),
            // 100% of the bytes is not yet a verified APK; the install stays unavailable until the checks pass.
            detail = stringResource(R.string.updates_verifying_detail),
            tone = StatusTone.Accent,
            spinning = true
        )
        is UpdateStatus.PreparingInstall -> StatusLineContent(
            headline = stringResource(R.string.updates_preparing_install),
            detail = null,
            tone = StatusTone.Accent,
            spinning = true
        )
        is UpdateStatus.Ready -> StatusLineContent(
            headline = stringResource(R.string.updates_state_ready),
            detail = stringResource(R.string.updates_state_ready_detail),
            tone = StatusTone.Success,
            icon = Icons.Rounded.CheckCircle,
            announce = true
        )
        is UpdateStatus.PermissionRequired -> StatusLineContent(
            headline = stringResource(R.string.updates_state_permission),
            detail = stringResource(R.string.updates_allow_unknown_sources),
            tone = StatusTone.Accent,
            icon = Icons.Rounded.SystemUpdate,
            announce = true
        )
        is UpdateStatus.AwaitingInstaller -> StatusLineContent(
            headline = stringResource(R.string.updates_state_awaiting),
            detail = stringResource(R.string.updates_confirm_android_install),
            tone = StatusTone.Accent,
            icon = Icons.Rounded.SystemUpdate,
            announce = true
        )
        is UpdateStatus.Failure -> StatusLineContent(
            headline = stringResource(R.string.updates_state_failed),
            detail = stringResource(updateFailureTextRes(status.reason, status.step)),
            tone = StatusTone.Warning,
            icon = Icons.Rounded.ErrorOutline,
            announce = true
        )
    }
    val badge = when (line.tone) {
        StatusTone.Neutral -> palette.surface2
        StatusTone.Accent -> palette.accentSoft
        StatusTone.Success -> palette.successSurface
        StatusTone.Warning -> palette.warningSurface
    }
    val badgeContent = when (line.tone) {
        StatusTone.Neutral -> palette.textSecondary
        StatusTone.Accent -> palette.accentInk
        StatusTone.Success -> palette.successText
        StatusTone.Warning -> palette.warningText
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (line.spinning || line.icon != null) {
            Box(Modifier.size(36.dp).background(badge, CircleShape), contentAlignment = Alignment.Center) {
                if (line.spinning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = badgeContent, strokeWidth = 2.dp)
                }
                line.icon?.let { icon ->
                    Icon(icon, contentDescription = null, tint = badgeContent, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            line.headline?.let { headline ->
                val announced = if (line.announce) Modifier.errorAnnouncement() else Modifier
                Text(
                    headline,
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() }.then(announced)
                )
            }
            line.detail?.let { detail ->
                Text(
                    detail,
                    color = if (line.tone == StatusTone.Warning) palette.textPrimary else palette.textSecondary,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/** Bytes, and speed and time left once they are stable. Shown as text, so the numbers are never only a picture. */
@Composable
private fun downloadDetail(progress: DownloadProgress): String {
    val context = LocalContext.current
    val parts = mutableListOf(
        stringResource(
            R.string.updates_progress_bytes,
            Formatter.formatShortFileSize(context, progress.bytes),
            Formatter.formatShortFileSize(context, progress.total),
        )
    )
    val speed = progress.bytesPerSecond
    if (speed != null) {
        parts += stringResource(R.string.updates_speed, Formatter.formatShortFileSize(context, speed))
    }
    val eta = progress.etaSeconds
    if (eta != null) {
        parts += stringResource(R.string.updates_eta, etaText(eta))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun etaText(seconds: Long): String =
    if (seconds < 60L) {
        stringResource(R.string.updates_eta_seconds, seconds)
    } else {
        stringResource(R.string.updates_eta_minutes, (seconds + 59L) / 60L)
    }

@Composable
private fun ReleaseCard(model: StableUpdatesViewModel, state: UpdateUiState, release: StableRelease, isDevBeta: Boolean) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    // A failed check keeps the release it found before, and says so: those details may be out of date.
    val status = state.status
    val fromEarlierCheck = status is UpdateStatus.Failure && status.release == null
    SettingsGroup(stringResource(R.string.updates_group_available)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(release.versionName, color = palette.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(if (isDevBeta) R.string.updates_type_dev else R.string.updates_type_stable),
                color = palette.textSecondary,
                fontSize = 14.sp
            )
            Text(
                stringResource(R.string.updates_size, release.size / (1024f * 1024f)),
                color = palette.textSecondary,
                fontSize = 14.sp
            )
            formatPublishedDate(release.publishedAt, context)?.let { date ->
                Text(stringResource(R.string.updates_published, date), color = palette.textSecondary, fontSize = 14.sp)
            }
            Text(stringResource(R.string.updates_source), color = palette.textSecondary, fontSize = 14.sp)
            if (fromEarlierCheck) {
                Text(
                    stringResource(R.string.updates_stale_notice),
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            ReleaseActions(model, state, release)
        }
    }
}

/** The buttons of the release card. Each one acts on the process it is shown for; a failed check has none here. */
@Composable
private fun ReleaseActions(model: StableUpdatesViewModel, state: UpdateUiState, release: StableRelease) {
    val palette = beyGramPalette()
    val status = state.status
    // A verified APK for this release stays installable through a failed re-check and after a restart.
    val ready = state.ready?.takeIf { it.release == release }
    when {
        status is UpdateStatus.Downloading -> {
            LinearProgressIndicator(
                progress = { status.progress.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = palette.accent
            )
            OutlinedButton(onClick = model::cancelDownload, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.updates_cancel))
            }
        }
        status is UpdateStatus.Verifying || status is UpdateStatus.PreparingInstall ||
            status is UpdateStatus.AwaitingInstaller || status is UpdateStatus.Checking -> Unit
        status is UpdateStatus.PermissionRequired -> {
            state.installNotice?.let { notice -> InstallNoticeText(notice) }
            Button(onClick = model::openInstallPermission, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.updates_allow_install))
            }
            OutlinedButton(onClick = { model.install(release) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.updates_continue_install))
            }
        }
        ready != null -> {
            state.installNotice?.let { notice -> InstallNoticeText(notice) }
            Button(onClick = { model.install(release) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.updates_install))
            }
        }
        status is UpdateStatus.Available -> Button(onClick = { model.download(release) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.updates_download))
        }
        status is UpdateStatus.Failure && status.release != null ->
            OutlinedButton(onClick = { model.download(release) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.updates_retry_download))
            }
        else -> Unit
    }
}

@Composable
private fun InstallNoticeText(notice: InstallNotice) {
    val palette = beyGramPalette()
    val failed = notice == InstallNotice.LaunchFailed
    val text = when (notice) {
        InstallNotice.LaunchFailed -> stringResource(R.string.updates_error_launch_installer)
        InstallNotice.NotInstalledYet -> stringResource(R.string.updates_not_installed_yet)
        InstallNotice.PermissionGranted -> stringResource(R.string.updates_permission_granted)
    }
    Text(
        text,
        color = if (failed) palette.warningText else palette.textPrimary,
        fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth()
            .clip(styledShape(12.dp))
            .background(if (failed) palette.warningSurface else palette.surface2)
            .padding(12.dp)
            .errorAnnouncement()
    )
}

/** Release notes as a readable list: sections as headings, bullets and numbers as markers. The PR appendix is never shown. */
@Composable
private fun ReleaseNotesContent(markdown: String) {
    val palette = beyGramPalette()
    val notes = remember(markdown) { parseReleaseNotes(markdown) }
    if (notes.isEmpty()) {
        Text(stringResource(R.string.updates_no_notes), color = palette.textSecondary, fontSize = 15.sp)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        notes.forEach { note ->
            when (note.kind) {
                ReleaseNotesKind.Section -> Text(
                    note.text,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() }
                )
                ReleaseNotesKind.Bullet -> Row(verticalAlignment = Alignment.Top) {
                    Text("•", color = palette.accent, fontSize = 15.sp, modifier = Modifier.padding(end = 10.dp))
                    Text(note.text, color = palette.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                }
                ReleaseNotesKind.Numbered -> Row(verticalAlignment = Alignment.Top) {
                    Text(note.marker, color = palette.accent, fontSize = 15.sp, modifier = Modifier.padding(end = 10.dp))
                    Text(note.text, color = palette.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                }
                ReleaseNotesKind.Paragraph -> Text(note.text, color = palette.textPrimary, fontSize = 15.sp)
            }
        }
    }
}

/** A date and, when [withTime] is set, the time, in the platform's format for the reader's locale. */
private fun formatUpdateDate(context: Context, millis: Long, withTime: Boolean): String {
    val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH or
        (if (withTime) DateUtils.FORMAT_SHOW_TIME else 0)
    return DateUtils.formatDateTime(context, millis, flags)
}

/** The release date, or null when GitHub sent no readable date. */
private fun formatPublishedDate(publishedAt: String, context: Context): String? {
    if (publishedAt.isBlank()) return null
    val millis = runCatching { Instant.parse(publishedAt).toEpochMilli() }.getOrNull() ?: return null
    return formatUpdateDate(context, millis, withTime = false)
}
