package com.beykus.beygram.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.R
import com.beykus.beygram.data.update.DevChangeRange
import com.beykus.beygram.data.update.DownloadProgress
import com.beykus.beygram.data.update.InstallNotice
import com.beykus.beygram.data.update.InstalledBuild
import com.beykus.beygram.data.update.StableRelease
import com.beykus.beygram.data.update.UpdateChannel
import com.beykus.beygram.data.update.UpdateStep
import com.beykus.beygram.data.update.UpdateUiState
import com.beykus.beygram.data.update.commitUrl
import com.beykus.beygram.data.update.devBuildInfoText
import com.beykus.beygram.data.update.devReleaseUrl
import com.beykus.beygram.data.update.installedBuild
import com.beykus.beygram.data.update.knownSha
import com.beykus.beygram.data.update.stableReleaseUrl
import com.beykus.beygram.data.update.updateChannelFor
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.Button
import com.beykus.beygram.ui.components.OutlinedButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassScreenBackground
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.fadeEnter
import com.beykus.beygram.ui.motion.fadeExit
import com.beykus.beygram.ui.motion.rememberMotionSpec
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
 * The update page: one status card that shows the installed build, the release on offer or in progress, and its
 * next action, then the release notes. The card's stage is [updateCardModel]; this file only draws it.
 */
@Composable
fun StableUpdatesScreen(onBack: () -> Unit, model: StableUpdatesViewModel = viewModel()) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val uiState by model.state.collectAsStateWithLifecycle()
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
    val card = updateCardModel(uiState)
    Column(
        Modifier.fillMaxSize()
            .glassScreenBackground(design, palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        ProfileToolbar(stringResource(R.string.updates_title), onBack) {
            // While an offer exists the re-check moves here; the card keeps its own action otherwise.
            if (card.showTopCheck) {
                IconButton(onClick = model::check) {
                    Icon(Icons.Rounded.Refresh, stringResource(R.string.updates_check), tint = palette.accent)
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            UpdateStatusCard(model, uiState, card, isDevBeta)
            card.release?.let { current ->
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

/** The one status card. Its body is keyed by the coarse phase only, so a download's percent never restarts the morph. */
@Composable
private fun UpdateStatusCard(model: StableUpdatesViewModel, state: UpdateUiState, card: UpdateCardModel, isDevBeta: Boolean) {
    val motion = rememberMotionSpec(MotionToken.BannerChange)
    // Reduced motion (or a disabled category) snaps: no fade and no size animation.
    val sizeTransform = if (motion.isInstant) {
        SizeTransform(clip = false) { _, _ -> snap<IntSize>() }
    } else {
        SizeTransform { _, _ -> motion.animationSpec<IntSize>() }
    }
    SettingsGroup(stringResource(R.string.updates_group_status)) {
        AnimatedContent(
            targetState = card.phase,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = { motion.fadeEnter() togetherWith motion.fadeExit() using sizeTransform },
            label = "updateStatusCard"
        ) { _ ->
            UpdateCardBody(model, state, card, isDevBeta)
        }
    }
}

@Composable
private fun UpdateCardBody(model: StableUpdatesViewModel, state: UpdateUiState, card: UpdateCardModel, isDevBeta: Boolean) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val line = cardStatusContent(card, isDevBeta)
    val release = card.release
    val installed = remember { installedBuild(BuildConfig.DEV_BUILD_NUMBER, BuildConfig.BUILD_GIT_SHA) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            StatusBadge(line)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                line.headline?.let { headline ->
                    val announced = if (line.announce) Modifier.errorAnnouncement() else Modifier
                    Text(
                        headline,
                        color = palette.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() }.then(announced)
                    )
                }
                // The new version when there is an offer; otherwise the installed one. DEV names builds by number.
                val targetBuild = release?.dev?.buildNumber?.takeIf { isDevBeta }
                Text(
                    when {
                        targetBuild != null -> stringResource(R.string.updates_release_build_title, targetBuild)
                        release != null -> release.versionName
                        isDevBeta -> installedBuildLabel(installed)
                        else -> BuildConfig.VERSION_NAME
                    },
                    color = palette.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        release?.let { offer ->
            val targetBuild = offer.dev?.buildNumber?.takeIf { isDevBeta }
            val installedNumber = (installed as? InstalledBuild.Ci)?.number
            Text(
                when {
                    targetBuild != null && installedNumber != null ->
                        stringResource(R.string.updates_release_comparison, installedNumber, targetBuild)
                    targetBuild != null -> stringResource(R.string.updates_release_comparison_unknown, targetBuild)
                    isDevBeta -> stringResource(R.string.updates_installed_compare, installedBuildLabel(installed))
                    else -> stringResource(R.string.updates_installed_compare, BuildConfig.VERSION_NAME)
                },
                color = palette.textSecondary,
                fontSize = 14.sp
            )
            offer.dev?.commit?.takeIf { isDevBeta }?.let { commit ->
                Text(stringResource(R.string.updates_release_commit, commit.take(7)), color = palette.textSecondary, fontSize = 14.sp)
            }
            val facts = listOfNotNull(
                stringResource(R.string.updates_size, offer.size / (1024f * 1024f)),
                formatPublishedDate(offer.publishedAt, context)?.let { date -> stringResource(R.string.updates_published, date) }
            )
            Text(facts.joinToString(" · "), color = palette.textSecondary, fontSize = 14.sp)
        }
        line.detail?.let { detail ->
            Text(
                detail,
                color = if (line.tone == StatusTone.Warning) palette.textPrimary else palette.textSecondary,
                fontSize = 14.sp
            )
        }
        if (card.stale) StaleNotice()
        if (card.showProgress) {
            // Read the texts before the semantics block: stringResource is composable, the semantics lambda is not.
            val percent = card.progress?.percent ?: 0
            val barText = if (card.indeterminateProgress) {
                stringResource(R.string.updates_state_verifying)
            } else {
                stringResource(R.string.updates_state_downloading, percent)
            }
            val barModifier = Modifier.fillMaxWidth().semantics { contentDescription = barText }
            if (card.indeterminateProgress) {
                LinearProgressIndicator(modifier = barModifier, color = palette.accent)
            } else {
                LinearProgressIndicator(progress = { percent / 100f }, modifier = barModifier, color = palette.accent)
            }
        }
        card.installNotice?.let { InstallNoticeText(it) }
        card.primary?.let { UpdateActionButton(it, release, model, isPrimary = true) }
        card.secondary?.let { UpdateActionButton(it, release, model, isPrimary = false) }
        card.lastCheckedAt?.let { millis ->
            Text(
                stringResource(R.string.updates_last_checked, formatUpdateDate(context, millis, withTime = true)),
                color = palette.textSecondary,
                fontSize = 13.sp
            )
        }
        UpdateDetails(release, isDevBeta, installed)
        if (isDevBeta) DevBuildActions(installed, release)
    }
}

/** The stage of the card: badge tone, icon or spinner, words. Every stage has its own text; none is a bare percent. */
@Composable
private fun cardStatusContent(card: UpdateCardModel, isDevBeta: Boolean): StatusLineContent = when (card.phase) {
    UpdatePhase.Idle -> StatusLineContent(
        headline = null,
        detail = stringResource(if (isDevBeta) R.string.updates_dev_manual_hint else R.string.updates_manual_hint),
        tone = StatusTone.Neutral,
        icon = Icons.Rounded.SystemUpdate
    )
    UpdatePhase.Checking -> StatusLineContent(
        headline = stringResource(R.string.updates_checking),
        detail = null,
        tone = StatusTone.Neutral,
        spinning = true
    )
    UpdatePhase.Current -> StatusLineContent(
        headline = stringResource(R.string.updates_state_current),
        detail = stringResource(R.string.updates_current),
        tone = StatusTone.Success,
        icon = Icons.Rounded.CheckCircle,
        announce = true
    )
    UpdatePhase.Available -> StatusLineContent(
        headline = stringResource(R.string.updates_state_available),
        detail = null,
        tone = StatusTone.Accent,
        icon = Icons.Rounded.SystemUpdate,
        announce = true
    )
    UpdatePhase.Downloading -> StatusLineContent(
        headline = stringResource(R.string.updates_state_downloading, card.progress?.percent ?: 0),
        detail = card.progress?.let { progress -> downloadDetail(progress) },
        tone = StatusTone.Accent,
        icon = Icons.Rounded.Download
    )
    UpdatePhase.Verifying -> StatusLineContent(
        headline = stringResource(R.string.updates_state_verifying),
        // 100% of the bytes is not yet a verified APK; the install stays unavailable until the checks pass.
        detail = stringResource(R.string.updates_verifying_detail),
        tone = StatusTone.Accent,
        icon = Icons.Rounded.Download
    )
    UpdatePhase.Ready -> StatusLineContent(
        headline = stringResource(R.string.updates_state_ready),
        detail = stringResource(R.string.updates_state_ready_detail),
        tone = StatusTone.Success,
        icon = Icons.Rounded.CheckCircle,
        announce = true
    )
    UpdatePhase.PreparingInstall -> StatusLineContent(
        headline = stringResource(R.string.updates_preparing_install),
        detail = null,
        tone = StatusTone.Accent,
        spinning = true
    )
    UpdatePhase.PermissionRequired -> StatusLineContent(
        headline = stringResource(R.string.updates_state_permission),
        detail = stringResource(R.string.updates_allow_unknown_sources),
        tone = StatusTone.Accent,
        icon = Icons.Rounded.SystemUpdate,
        announce = true
    )
    UpdatePhase.AwaitingInstaller -> StatusLineContent(
        headline = stringResource(R.string.updates_state_awaiting),
        detail = stringResource(R.string.updates_confirm_android_install),
        tone = StatusTone.Accent,
        icon = Icons.Rounded.SystemUpdate,
        announce = true
    )
    UpdatePhase.Installed -> StatusLineContent(
        headline = stringResource(R.string.updates_state_installed),
        detail = null,
        tone = StatusTone.Success,
        icon = Icons.Rounded.CheckCircle,
        announce = true
    )
    UpdatePhase.Failed -> StatusLineContent(
        headline = stringResource(R.string.updates_state_failed),
        detail = stringResource(updateFailureTextRes(card.failureReason, card.failureStep ?: UpdateStep.Download)),
        tone = StatusTone.Warning,
        icon = Icons.Rounded.ErrorOutline,
        announce = true
    )
}

@Composable
private fun StatusBadge(line: StatusLineContent) {
    val palette = beyGramPalette()
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
    // The headline says the state in words, so the icon or spinner is decorative.
    Box(Modifier.size(48.dp).background(badge, CircleShape), contentAlignment = Alignment.Center) {
        if (line.spinning) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = badgeContent, strokeWidth = 2.dp)
        }
        line.icon?.let { icon ->
            Icon(icon, contentDescription = null, tint = badgeContent, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun StaleNotice() {
    val palette = beyGramPalette()
    // A failed check or an earlier process kept this offer: it is labelled, never shown as fresh.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.updates_stale_badge),
            color = palette.warningText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(styledShape(8.dp)).background(palette.warningSurface).padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Text(stringResource(R.string.updates_stale_notice), color = palette.textSecondary, fontSize = 14.sp)
    }
}

/** One button of the card. Each acts on the release it was shown for; the model only offers actions that are allowed. */
@Composable
private fun UpdateActionButton(action: UpdateAction, release: StableRelease?, model: StableUpdatesViewModel, isPrimary: Boolean) {
    val buildNumber = release?.dev?.buildNumber
    val label = if (action == UpdateAction.Download && buildNumber != null) {
        stringResource(R.string.updates_download_build, buildNumber)
    } else {
        stringResource(actionTextRes(action))
    }
    val onClick: () -> Unit = {
        when (action) {
            UpdateAction.Check, UpdateAction.RetryCheck -> model.check()
            UpdateAction.Download, UpdateAction.RetryDownload -> release?.let { model.download(it) }
            UpdateAction.Cancel -> model.cancelDownload()
            UpdateAction.Install, UpdateAction.ContinueInstall -> release?.let { model.install(it) }
            UpdateAction.AllowInstall -> model.openInstallPermission()
        }
    }
    val modifier = Modifier.fillMaxWidth()
    // Cancel and retry are secondary to the main step, so they use the outlined style.
    val filled = isPrimary && action != UpdateAction.Cancel && action != UpdateAction.RetryDownload
    if (filled) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

private fun actionTextRes(action: UpdateAction): Int = when (action) {
    UpdateAction.Check -> R.string.updates_check
    UpdateAction.RetryCheck -> R.string.updates_retry_check
    UpdateAction.Download -> R.string.updates_download
    UpdateAction.RetryDownload -> R.string.updates_retry_download
    UpdateAction.Cancel -> R.string.updates_cancel
    UpdateAction.Install -> R.string.updates_install
    UpdateAction.AllowInstall -> R.string.updates_allow_install
    UpdateAction.ContinueInstall -> R.string.updates_continue_install
}

/** Technical facts, collapsed by default: channel, source, file, checksum and package. */
@Composable
private fun UpdateDetails(release: StableRelease?, isDevBeta: Boolean, installed: InstalledBuild) {
    val palette = beyGramPalette()
    val motion = rememberMotionSpec(MotionToken.BannerChange)
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .clip(styledShape(10.dp))
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.updates_details),
                color = palette.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = palette.textSecondary
            )
        }
        AnimatedVisibility(visible = expanded, enter = motion.fadeEnter(), exit = motion.fadeExit()) {
            Column(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DetailLine(stringResource(if (isDevBeta) R.string.updates_dev_channel else R.string.updates_stable_only))
                DetailLine(stringResource(R.string.updates_source))
                DetailLine(stringResource(R.string.updates_detail_package, BuildConfig.APPLICATION_ID))
                if (isDevBeta) {
                    installed.knownSha()?.let { sha ->
                        DetailLine(stringResource(R.string.updates_installed_details, BuildConfig.VERSION_CODE, sha.take(7)))
                    }
                    release?.versionCode?.let { code ->
                        DetailLine(stringResource(R.string.updates_detail_version_code, code))
                    }
                }
                release?.let { offer ->
                    DetailLine(stringResource(R.string.updates_detail_file, offer.apkName))
                    DetailLine(stringResource(R.string.updates_detail_sha, offer.sha256))
                }
            }
        }
    }
}

@Composable
private fun DetailLine(text: String) {
    Text(text, color = beyGramPalette().textSecondary, fontSize = 13.sp)
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
