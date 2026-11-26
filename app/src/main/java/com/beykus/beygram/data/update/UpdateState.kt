package com.beykus.beygram.data.update

import java.io.File
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** One update activity. Only one runs at a time; a finished one leaves its outcome in the status. */
sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object Current : UpdateStatus
    data class Available(val release: StableRelease) : UpdateStatus
    data class Downloading(val release: StableRelease, val progress: DownloadProgress) : UpdateStatus
    /** All bytes arrived; the hash and package checks have not finished. */
    data class Verifying(val release: StableRelease) : UpdateStatus
    /** The file is hashed, checked and kept. Not yet handed to Android. */
    data class Ready(val release: StableRelease, val file: File) : UpdateStatus
    data class PreparingInstall(val release: StableRelease) : UpdateStatus
    /** Android does not allow this app to install packages yet. The user grants it in Settings and continues by tap. */
    data class PermissionRequired(val release: StableRelease, val file: File) : UpdateStatus
    /** The APK was handed to the Android installer. Only the installed version code proves the update. */
    data class AwaitingInstaller(val release: StableRelease, val file: File) : UpdateStatus
    data class Installed(val versionCode: Long) : UpdateStatus
    /** [reason] is null for an unclassified error; the screen then words the failure by [step]. */
    data class Failure(val step: UpdateStep, val reason: StableUpdateReason?, val release: StableRelease?) : UpdateStatus
}

enum class UpdateStep { Check, Download, Verify, Install }

/** Transient outcome of the last install attempt. */
enum class InstallNotice { LaunchFailed, NotInstalledYet, PermissionGranted }

/** A verified APK kept on disk, with the release it was verified for. */
data class ReadyApk(val release: StableRelease, val file: File)

/**
 * What one unattended check produced. Deliberately not a UI state: the scheduler needs the outcome to decide about
 * backoff, notification and banner, while [UpdateUiState] keeps describing what the user sees.
 */
sealed interface AutomaticCheckOutcome {
    data class Available(val release: StableRelease) : AutomaticCheckOutcome
    data object Current : AutomaticCheckOutcome
    /** A user operation held the updater, or the current state does not allow a check. Nothing was asked. */
    data object Skipped : AutomaticCheckOutcome
    data class Failed(val reason: StableUpdateReason?) : AutomaticCheckOutcome
}

data class UpdateUiState(
    val status: UpdateStatus = UpdateStatus.Idle,
    val ready: ReadyApk? = null,
    val installNotice: InstallNotice? = null,
    val knownRelease: StableRelease? = null,
    val lastCheckedAt: Long? = null,
    /** True when [knownRelease] comes from an earlier process or a failed check and has not been confirmed since. */
    val checkStale: Boolean = false,
)

/** Which guards apply to user actions. Pure, so the rules are tested without Android. */
internal object StableUpdateActionPolicy {
    fun canCheck(state: UpdateStatus): Boolean = when (state) {
        UpdateStatus.Checking,
        is UpdateStatus.Downloading,
        is UpdateStatus.Verifying,
        is UpdateStatus.PreparingInstall,
        is UpdateStatus.PermissionRequired,
        is UpdateStatus.AwaitingInstaller -> false
        else -> true
    }

    fun canDownload(state: UpdateStatus, release: StableRelease): Boolean = when (state) {
        is UpdateStatus.Available -> state.release == release
        is UpdateStatus.Failure -> state.release == release
        else -> false
    }

    fun canInstall(state: UpdateStatus, release: StableRelease): Boolean = when (state) {
        is UpdateStatus.Ready -> state.release == release
        is UpdateStatus.PermissionRequired -> state.release == release
        // A verified APK stays installable after a re-check (the coordinator also requires a matching ready file).
        is UpdateStatus.Available -> state.release == release
        is UpdateStatus.Failure -> state.release == null || state.release == release
        else -> false
    }
}

enum class UpdateChannel { Stable, DevBeta, ProfBeta }

// DEV beta updates come from its own prereleases; every other install uses the Stable channel.
internal fun updateChannelFor(packageName: String): UpdateChannel = when (packageName) {
    DEV_PACKAGE -> UpdateChannel.DevBeta
    PROF_PACKAGE -> UpdateChannel.ProfBeta
    else -> UpdateChannel.Stable
}

/** The typed reason of a failed step, or null when the error is not one the user can act on. */
fun updateErrorReason(error: Throwable): StableUpdateReason? = when (error) {
    is StableUpdateException -> error.reason
    is SocketTimeoutException -> StableUpdateReason.Timeout
    is UnknownHostException, is SocketException, is SSLException -> StableUpdateReason.Network
    else -> null
}
