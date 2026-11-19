package com.beykus.beygram.ui.screens.settings

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import com.beykus.beygram.R
import com.beykus.beygram.data.update.StableRelease
import com.beykus.beygram.data.update.StableUpdateReason
import com.beykus.beygram.data.update.UpdateCoordinator
import com.beykus.beygram.data.update.UpdateStep
import com.beykus.beygram.data.update.UpdateUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * The text of a failed step. A typed reason has its own text; any other error gets the text of its step,
 * so raw repository or server text never reaches the screen.
 */
internal fun updateFailureTextRes(reason: StableUpdateReason?, step: UpdateStep): Int =
    reason?.let(::updateReasonTextRes) ?: when (step) {
        UpdateStep.Check -> R.string.updates_error_check
        UpdateStep.Download -> R.string.updates_error_download
        UpdateStep.Verify -> R.string.updates_error_verify
        UpdateStep.Install -> R.string.updates_error_launch_installer
    }

/** The text of a typed update failure. Every reason has its own text. */
@StringRes
internal fun updateReasonTextRes(reason: StableUpdateReason): Int = when (reason) {
    StableUpdateReason.Network -> R.string.updates_error_network
    StableUpdateReason.Timeout -> R.string.updates_error_timeout
    StableUpdateReason.RateLimited -> R.string.updates_error_rate_limited
    StableUpdateReason.MissingRelease -> R.string.updates_error_missing_release
    StableUpdateReason.MissingAsset -> R.string.updates_error_missing_asset
    StableUpdateReason.ServerError -> R.string.updates_error_server
    StableUpdateReason.InvalidMetadata -> R.string.updates_error_invalid_metadata
    StableUpdateReason.NoFreeSpace -> R.string.updates_error_no_space
    StableUpdateReason.AlreadyInstalled -> R.string.updates_error_already_installed
    StableUpdateReason.Outdated -> R.string.updates_error_outdated
    StableUpdateReason.ChecksumMismatch -> R.string.updates_error_checksum
    StableUpdateReason.PackageMismatch -> R.string.updates_error_package
    StableUpdateReason.SignerMismatch -> R.string.updates_error_signer
    StableUpdateReason.InstallerLaunch -> R.string.updates_error_launch_installer
    StableUpdateReason.MissingFile -> R.string.updates_error_missing_file
    StableUpdateReason.Storage -> R.string.updates_error_storage
    StableUpdateReason.UntrustedRedirect -> R.string.updates_error_untrusted_redirect
}

/** Thin: the process-wide [UpdateCoordinator] owns the state and the work; this only forwards actions. */
class StableUpdatesViewModel(application: Application) : AndroidViewModel(application) {
    private val coordinator = UpdateCoordinator.get(application)
    val state: StateFlow<UpdateUiState> = coordinator.state

    fun check() = coordinator.check()
    fun download(release: StableRelease) = coordinator.download(release)
    fun cancelDownload() = coordinator.cancelDownload()
    fun install(release: StableRelease) = coordinator.install(release)
    fun openInstallPermission() = coordinator.openInstallPermission()
    fun onReturnedToApp() = coordinator.onReturnedToApp()
}
