package com.beykus.beygram.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.R
import com.beykus.beygram.data.update.DevChangeRange
import com.beykus.beygram.data.update.InstalledBuild
import com.beykus.beygram.data.update.StableRelease
import com.beykus.beygram.data.update.commitUrl
import com.beykus.beygram.data.update.devBuildInfoText
import com.beykus.beygram.data.update.devReleaseUrl
import com.beykus.beygram.data.update.installedBuild
import com.beykus.beygram.data.update.knownSha
import com.beykus.beygram.data.update.stableReleaseUrl
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
    val status = model.status
    val isDevBeta = updateChannelFor(BuildConfig.APPLICATION_ID) == UpdateChannel.DevBeta
    // A running or failed check keeps the release of the last successful check visible.
    val release: StableRelease? = when (status) {
        is UpdateStatus.Available -> status.release
        is UpdateStatus.Downloading -> status.release
        is UpdateStatus.Ready -> status.release
        is UpdateStatus.PreparingInstall -> status.release
        is UpdateStatus.Failure -> status.release ?: model.knownRelease
        UpdateStatus.Checking -> model.knownRelease
        UpdateStatus.Idle, UpdateStatus.Current -> null
    }
    val retryCheck = status is UpdateStatus.Failure && status.release == null
    val installed = remember { installedBuild(BuildConfig.DEV_BUILD_NUMBER, BuildConfig.BUILD_GIT_SHA) }
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
                        if (isDevBeta) installedBuildLabel(installed) else BuildConfig.VERSION_NAME,
                        color = palette.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isDevBeta) {
                        installed.knownSha()?.let { sha ->
                            Text(
                                stringResource(R.string.updates_installed_details, BuildConfig.VERSION_CODE, sha.take(7)),
                                color = palette.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Text(
                        stringResource(if (isDevBeta) R.string.updates_dev_channel else R.string.updates_stable_only),
                        color = palette.textSecondary,
                        fontSize = 14.sp
                    )
                    StatusLine(status, isDevBeta, verifyFailed = model.installNotice == InstallNotice.VerifyFailed)
                    model.lastCheckedAt?.let { millis ->
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
                    if (isDevBeta) {
                        DevBuildActions(installed, release)
                    }
                }
            }
            release?.let { current ->
                ReleaseCard(model, status, current, isDevBeta)
                SettingsGroup(stringResource(R.string.updates_changes)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                        val dev = current.dev
                        if (isDevBeta && dev != null && dev.changes.entries.isNotEmpty()) {
                            DevChangesContent(dev.changes, current.tag)
                        } else {
                            ReleaseNotesContent(
                                current.notes,
                                truncated = current.notesTruncated,
                                releaseUrl = if (isDevBeta) devReleaseUrl(current.tag) else stableReleaseUrl(current.tag),
                                compact = true,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusLine(status: UpdateStatus, isDevBeta: Boolean, verifyFailed: Boolean) {
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
        is UpdateStatus.Available -> StatusLineContent(
            headline = stringResource(R.string.updates_state_available),
            detail = stringResource(R.string.updates_available, status.release.versionName),
            tone = StatusTone.Accent,
            icon = Icons.Rounded.SystemUpdate,
            announce = true
        )
        is UpdateStatus.Downloading -> StatusLineContent(
            headline = stringResource(R.string.updates_state_downloading, status.progress),
            detail = null,
            tone = StatusTone.Accent,
            icon = Icons.Rounded.Download
        )
        is UpdateStatus.PreparingInstall -> StatusLineContent(
            headline = stringResource(R.string.updates_preparing_install),
            detail = null,
            tone = StatusTone.Accent,
            spinning = true
        )
        is UpdateStatus.Ready -> StatusLineContent(
            headline = stringResource(R.string.updates_state_ready),
            // After a failed verification the file is not trusted; the notice below explains it.
            detail = if (verifyFailed) null else stringResource(R.string.updates_state_ready_detail),
            tone = if (verifyFailed) StatusTone.Warning else StatusTone.Success,
            icon = Icons.Rounded.CheckCircle,
            announce = true
        )
        is UpdateStatus.Failure -> StatusLineContent(
            headline = stringResource(R.string.updates_state_failed),
            detail = status.message,
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

@Composable
private fun ReleaseCard(model: StableUpdatesViewModel, status: UpdateStatus, release: StableRelease, isDevBeta: Boolean) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    // A failed check keeps the release it found before, and says so: those details may be out of date.
    val fromEarlierCheck = status is UpdateStatus.Failure && status.release == null
    SettingsGroup(stringResource(R.string.updates_group_available)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val dev = release.dev.takeIf { isDevBeta }
            Text(
                dev?.buildNumber?.let { stringResource(R.string.updates_release_build_title, it) } ?: release.versionName,
                color = palette.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(if (isDevBeta) R.string.updates_type_dev else R.string.updates_type_stable),
                color = palette.textSecondary,
                fontSize = 14.sp
            )
            dev?.buildNumber?.let { target ->
                // The installed number is known only for a CI build; otherwise the comparison says so.
                val installedNumber = (installedBuild(BuildConfig.DEV_BUILD_NUMBER, BuildConfig.BUILD_GIT_SHA) as? InstalledBuild.Ci)?.number
                Text(
                    if (installedNumber != null) {
                        stringResource(R.string.updates_release_comparison, installedNumber, target)
                    } else {
                        stringResource(R.string.updates_release_comparison_unknown, target)
                    },
                    color = palette.textSecondary,
                    fontSize = 14.sp
                )
            }
            dev?.commit?.let { commit ->
                Text(stringResource(R.string.updates_release_commit, commit.take(7)), color = palette.textSecondary, fontSize = 14.sp)
            }
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
            ReleaseActions(model, status, release)
        }
    }
}

/** The buttons of the release card. Each one acts on the process it is shown for; a failed check has none here. */
@Composable
private fun ReleaseActions(model: StableUpdatesViewModel, status: UpdateStatus, release: StableRelease) {
    val palette = beyGramPalette()
    when (status) {
        is UpdateStatus.Available -> Button(onClick = { model.download(release) }, modifier = Modifier.fillMaxWidth()) {
            Text(
                release.dev?.buildNumber?.let { stringResource(R.string.updates_download_build, it) }
                    ?: stringResource(R.string.updates_download)
            )
        }
        is UpdateStatus.Downloading -> {
            LinearProgressIndicator(
                progress = { status.progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = palette.accent
            )
            OutlinedButton(onClick = model::cancelDownload, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.updates_cancel))
            }
        }
        is UpdateStatus.Ready -> {
            model.installNotice?.let { notice -> InstallNoticeText(notice) }
            if (model.installNotice == InstallNotice.VerifyFailed) {
                // The installer refused this file, so installing it again would fail the same way.
                Button(onClick = { model.redownload(release) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.updates_redownload))
                }
            } else {
                Button(onClick = { model.install(status.file, status.release) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.updates_install))
                }
            }
        }
        is UpdateStatus.Failure -> {
            // Only a failed download has a release to retry; a failed check is retried by the button above.
            if (status.release != null) {
                OutlinedButton(onClick = { model.download(release) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.updates_retry_download))
                }
            }
        }
        UpdateStatus.Idle, UpdateStatus.Checking, UpdateStatus.Current, is UpdateStatus.PreparingInstall -> Unit
    }
}

@Composable
private fun InstallNoticeText(notice: InstallNotice) {
    val palette = beyGramPalette()
    val failed = notice == InstallNotice.VerifyFailed || notice == InstallNotice.LaunchFailed
    val text = when (notice) {
        InstallNotice.ConfirmInInstaller -> stringResource(R.string.updates_confirm_android_install)
        InstallNotice.AllowUnknownSources -> stringResource(R.string.updates_allow_unknown_sources)
        InstallNotice.VerifyFailed -> stringResource(R.string.updates_error_verify)
        InstallNotice.LaunchFailed -> stringResource(R.string.updates_error_launch_installer)
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

/** The installed build in the DEV installed block. Stable keeps the plain version name. */
@Composable
internal fun installedBuildLabel(installed: InstalledBuild): String {
    val versionName = BuildConfig.VERSION_NAME
    return when (installed) {
        is InstalledBuild.Ci -> stringResource(R.string.updates_installed_build, versionName, installed.number)
        is InstalledBuild.UnknownNumber -> stringResource(R.string.updates_installed_unknown_number, versionName)
        InstalledBuild.Local -> stringResource(R.string.updates_installed_local, versionName)
    }
}

/** DEV only: copies the build identity for a bug report, and opens the installed commit on GitHub when it is known. */
@Composable
private fun DevBuildActions(installed: InstalledBuild, release: StableRelease?) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val text = devBuildInfoText(
                packageName = BuildConfig.APPLICATION_ID,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
                installed = installed,
                availableBuild = release?.dev?.buildNumber,
                availableCommit = release?.dev?.commit,
            )
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("BeyGram DEV", text))
            Toast.makeText(context, R.string.updates_build_info_copied, Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.updates_copy_build_info))
    }
    commitUrl(installed.knownSha())?.let { url ->
        OutlinedButton(onClick = { openExternalUrl(context, url) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.updates_open_commit))
        }
    }
}

/**
 * DEV only: the builds after the installed one, newest first. An incomplete list says why and offers the GitHub
 * compare page; a truncated one links to the release. Only URLs made by the helpers in DevBuildIdentity are opened.
 */
@Composable
private fun DevChangesContent(changes: DevChangeRange, tag: String) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        changes.entries.forEachIndexed { index, entry ->
            Text(
                stringResource(R.string.updates_release_build_title, entry.buildNumber),
                color = palette.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = if (index == 0) 0.dp else 8.dp).semantics { heading() }
            )
            ReleaseNotesContent(entry.notes)
        }
        if (!changes.complete) {
            Text(stringResource(R.string.updates_changes_incomplete), color = palette.textSecondary, fontSize = 14.sp)
            changes.compareUrl?.let { url ->
                OutlinedButton(onClick = { openExternalUrl(context, url) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.updates_changes_compare))
                }
            }
        }
        if (changes.truncated) {
            Text(stringResource(R.string.updates_changes_truncated), color = palette.textSecondary, fontSize = 14.sp)
            devReleaseUrl(tag)?.let { url ->
                OutlinedButton(onClick = { openExternalUrl(context, url) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.updates_open_release))
                }
            }
        }
    }
}

/** Opens a GitHub page in a browser. A device without a handler for it simply does nothing. */
internal fun openExternalUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
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
