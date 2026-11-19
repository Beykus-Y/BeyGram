package com.beykus.beygram.ui.screens.settings

import com.beykus.beygram.data.update.DownloadProgress
import com.beykus.beygram.data.update.InstallNotice
import com.beykus.beygram.data.update.StableRelease
import com.beykus.beygram.data.update.StableUpdateActionPolicy
import com.beykus.beygram.data.update.StableUpdateReason
import com.beykus.beygram.data.update.UpdateStatus
import com.beykus.beygram.data.update.UpdateStep
import com.beykus.beygram.data.update.UpdateUiState

/**
 * One coarse stage of the status card. The card animates between phases only: the percent of a download stays
 * inside [Downloading], so the animation does not restart with every step.
 */
internal enum class UpdatePhase {
    Idle, Checking, Current, Available, Downloading, Verifying, Ready, PreparingInstall,
    PermissionRequired, AwaitingInstaller, Installed, Failed
}

/** A button of the card. Each one maps to one action of the screen model. */
internal enum class UpdateAction { Check, RetryCheck, Download, RetryDownload, Cancel, Install, AllowInstall, ContinueInstall }

/**
 * What the status card shows for one [UpdateUiState]. Pure, so the mapping is tested on the JVM.
 *
 * [release] is the release the card is about: an offer, the release being downloaded or installed, or the release
 * a failed check or an earlier process kept. [stale] marks a release that comes from an earlier check and has not
 * been confirmed since.
 */
internal data class UpdateCardModel(
    val phase: UpdatePhase,
    val release: StableRelease?,
    val primary: UpdateAction?,
    val secondary: UpdateAction?,
    /** Byte progress of the download in [UpdatePhase.Downloading]; null otherwise. */
    val progress: DownloadProgress?,
    /** True while a determinate download bar or an indeterminate verifying bar is shown. */
    val showProgress: Boolean,
    /** True in [UpdatePhase.Verifying]: the bytes are in, so the bar has no fraction. */
    val indeterminateProgress: Boolean,
    val stale: Boolean,
    val failureStep: UpdateStep?,
    val failureReason: StableUpdateReason?,
    /** The compact re-check in the top bar. Shown only when an offer exists and no card button already checks. */
    val showTopCheck: Boolean,
    val installNotice: InstallNotice?,
    val lastCheckedAt: Long?,
)

internal fun updateCardModel(state: UpdateUiState): UpdateCardModel {
    val status = state.status
    val ownRelease = releaseOf(status)
    // Idle (after a restart), a running check and a failed check keep the last known offer, marked as stale.
    val keepsKnown = status == UpdateStatus.Idle || status == UpdateStatus.Checking ||
        (status is UpdateStatus.Failure && status.release == null)
    val release = ownRelease ?: if (keepsKnown) state.knownRelease else null
    val stale = ownRelease == null && release != null && state.checkStale
    val verifiedForRelease = release != null && state.ready?.release == release

    val primary: UpdateAction? = when (status) {
        UpdateStatus.Idle, UpdateStatus.Current -> UpdateAction.Check
        UpdateStatus.Checking -> null
        is UpdateStatus.Available -> if (verifiedForRelease) UpdateAction.Install else UpdateAction.Download
        is UpdateStatus.Downloading -> UpdateAction.Cancel
        is UpdateStatus.Verifying -> null
        is UpdateStatus.Ready -> UpdateAction.Install
        is UpdateStatus.PreparingInstall -> null
        is UpdateStatus.PermissionRequired -> UpdateAction.AllowInstall
        is UpdateStatus.AwaitingInstaller -> null
        is UpdateStatus.Installed -> null
        is UpdateStatus.Failure -> when {
            verifiedForRelease -> UpdateAction.Install
            status.release == null -> UpdateAction.RetryCheck
            else -> UpdateAction.RetryDownload
        }
    }
    val secondary: UpdateAction? = when (status) {
        is UpdateStatus.PermissionRequired -> UpdateAction.ContinueInstall
        is UpdateStatus.Failure -> if (verifiedForRelease && status.release == null) UpdateAction.RetryCheck else null
        else -> null
    }
    val progress = (status as? UpdateStatus.Downloading)?.progress
    val indeterminate = status is UpdateStatus.Verifying
    val failure = status as? UpdateStatus.Failure
    val actionsCheck = primary == UpdateAction.Check || primary == UpdateAction.RetryCheck ||
        secondary == UpdateAction.RetryCheck

    return UpdateCardModel(
        phase = phaseOf(status),
        release = release,
        primary = primary,
        secondary = secondary,
        progress = progress,
        showProgress = progress != null || indeterminate,
        indeterminateProgress = indeterminate,
        stale = stale,
        failureStep = failure?.step,
        failureReason = failure?.reason,
        showTopCheck = release != null && !actionsCheck && StableUpdateActionPolicy.canCheck(status),
        // A launch failure or a not-yet-installed result is explained next to the install button only.
        installNotice = if (primary == UpdateAction.Install || primary == UpdateAction.AllowInstall) state.installNotice else null,
        lastCheckedAt = state.lastCheckedAt,
    )
}

private fun releaseOf(status: UpdateStatus): StableRelease? = when (status) {
    is UpdateStatus.Available -> status.release
    is UpdateStatus.Downloading -> status.release
    is UpdateStatus.Verifying -> status.release
    is UpdateStatus.Ready -> status.release
    is UpdateStatus.PreparingInstall -> status.release
    is UpdateStatus.PermissionRequired -> status.release
    is UpdateStatus.AwaitingInstaller -> status.release
    is UpdateStatus.Failure -> status.release
    UpdateStatus.Idle, UpdateStatus.Checking, UpdateStatus.Current, is UpdateStatus.Installed -> null
}

private fun phaseOf(status: UpdateStatus): UpdatePhase = when (status) {
    UpdateStatus.Idle -> UpdatePhase.Idle
    UpdateStatus.Checking -> UpdatePhase.Checking
    UpdateStatus.Current -> UpdatePhase.Current
    is UpdateStatus.Available -> UpdatePhase.Available
    is UpdateStatus.Downloading -> UpdatePhase.Downloading
    is UpdateStatus.Verifying -> UpdatePhase.Verifying
    is UpdateStatus.Ready -> UpdatePhase.Ready
    is UpdateStatus.PreparingInstall -> UpdatePhase.PreparingInstall
    is UpdateStatus.PermissionRequired -> UpdatePhase.PermissionRequired
    is UpdateStatus.AwaitingInstaller -> UpdatePhase.AwaitingInstaller
    is UpdateStatus.Installed -> UpdatePhase.Installed
    is UpdateStatus.Failure -> UpdatePhase.Failed
}
