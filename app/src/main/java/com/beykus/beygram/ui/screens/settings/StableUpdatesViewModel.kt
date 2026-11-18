package com.beykus.beygram.ui.screens.settings

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beykus.beygram.R
import com.beykus.beygram.data.settings.AppLocale
import com.beykus.beygram.data.update.ApkUpdateSource
import com.beykus.beygram.data.update.DEV_PACKAGE
import com.beykus.beygram.data.update.DevUpdateRepository
import com.beykus.beygram.data.update.StableRelease
import com.beykus.beygram.data.update.StableUpdateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

private const val UPDATES_LOG_TAG = "BeyGramUpdates"

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object Current : UpdateStatus
    data class Available(val release: StableRelease) : UpdateStatus
    data class Downloading(val release: StableRelease, val progress: Int) : UpdateStatus
    data class Ready(val release: StableRelease, val file: File) : UpdateStatus
    data class PreparingInstall(val release: StableRelease) : UpdateStatus
    /** [message] is friendly text for the screen, never a raw error. A failed download keeps its [release] to retry it. */
    data class Failure(val message: String, val release: StableRelease? = null) : UpdateStatus
}

/** The outcome of the last install attempt. The screen words it; raw system or server text is never shown. */
enum class InstallNotice { ConfirmInInstaller, AllowUnknownSources, VerifyFailed, LaunchFailed }

/** Pure policy that guards all user-triggered network/file actions during install handoff. */
internal object StableUpdateActionPolicy {
    fun canCheck(state: UpdateStatus): Boolean =
        state !is UpdateStatus.Checking &&
            state !is UpdateStatus.Downloading &&
            state !is UpdateStatus.PreparingInstall

    fun canDownload(state: UpdateStatus, release: StableRelease): Boolean = when (state) {
        is UpdateStatus.Available -> state.release == release
        is UpdateStatus.Failure -> state.release == release
        else -> false
    }
}

internal enum class UpdateChannel { Stable, DevBeta }

// DEV beta updates come from its own prereleases; every other install uses the Stable channel.
internal fun updateChannelFor(packageName: String): UpdateChannel =
    if (packageName == DEV_PACKAGE) UpdateChannel.DevBeta else UpdateChannel.Stable

/**
 * The friendly text of a failed update step. Connection failures get the connection text; every other failure gets the
 * text of its step, so the repository's own messages, which can carry server text, never reach the screen.
 */
internal fun updateFailureTextRes(error: Throwable, @StringRes fallback: Int): Int = when (error) {
    is UnknownHostException, is SocketException, is SocketTimeoutException, is SSLException -> R.string.updates_error_network
    else -> fallback
}

class StableUpdatesViewModel(application: Application) : AndroidViewModel(application) {
    private fun localized(id: Int) = AppLocale.context(getApplication<Application>()).getString(id)
    private val repository: ApkUpdateSource = application.applicationContext.let { context ->
        if (updateChannelFor(context.packageName) == UpdateChannel.DevBeta) {
            DevUpdateRepository(context)
        } else {
            StableUpdateRepository(context)
        }
    }
    var status: UpdateStatus by mutableStateOf(UpdateStatus.Idle)
        private set
    /** The outcome of the last install attempt; null while nothing has been reported. */
    var installNotice: InstallNotice? by mutableStateOf(null)
        private set
    /** The newest release of the last successful check. A later failed check keeps it on screen. */
    var knownRelease: StableRelease? by mutableStateOf(null)
        private set
    /** Epoch milliseconds of the last successful check in this session; null until one has succeeded. */
    var lastCheckedAt: Long? by mutableStateOf(null)
        private set
    private var runningJob: Job? = null

    fun check() {
        if (!StableUpdateActionPolicy.canCheck(status)) return
        installNotice = null
        status = UpdateStatus.Checking
        runningJob = viewModelScope.launch {
            try {
                val release = repository.check(repository.installedCode())
                knownRelease = release
                lastCheckedAt = System.currentTimeMillis()
                status = if (release == null) UpdateStatus.Current else UpdateStatus.Available(release)
            } catch (cancelled: CancellationException) {
                status = UpdateStatus.Idle
                throw cancelled
            } catch (error: Exception) {
                status = UpdateStatus.Failure(friendlyError(error, R.string.updates_error_check))
            }
        }
    }

    fun download(release: StableRelease) {
        if (!StableUpdateActionPolicy.canDownload(status, release)) return
        installNotice = null
        status = UpdateStatus.Downloading(release, 0)
        runningJob = viewModelScope.launch {
            try {
                val downloaded = repository.download(release) { progress ->
                    withContext(Dispatchers.Main) {
                        status = UpdateStatus.Downloading(release, progress)
                    }
                }
                status = UpdateStatus.Ready(release, downloaded)
            } catch (cancelled: CancellationException) {
                status = UpdateStatus.Available(release)
                throw cancelled
            } catch (error: Exception) {
                status = UpdateStatus.Failure(friendlyError(error, R.string.updates_error_download), release)
            }
        }
    }

    /** Starts a new download after the installer refused the file as failing its check. The refused file is not reused. */
    fun redownload(release: StableRelease) {
        val ready = status as? UpdateStatus.Ready ?: return
        if (ready.release != release) return
        status = UpdateStatus.Available(release)
        download(release)
    }

    fun cancelDownload() {
        if (status is UpdateStatus.Downloading) runningJob?.cancel()
    }

    fun install(file: File, release: StableRelease) {
        val ready = status as? UpdateStatus.Ready ?: return
        if (ready.file != file || ready.release != release) return
        // Synchronously enter the install state before launching any suspendable work.
        // A repeated check or download cannot replace the state during this handoff.
        status = UpdateStatus.PreparingInstall(release)
        installNotice = null
        viewModelScope.launch {
            var verified = false
            try {
                repository.verifyForInstall(file, release)
                verified = true
                installNotice = if (repository.install(file)) {
                    InstallNotice.ConfirmInInstaller
                } else {
                    InstallNotice.AllowUnknownSources
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(UPDATES_LOG_TAG, "Install step failed", error)
                installNotice = if (verified) InstallNotice.LaunchFailed else InstallNotice.VerifyFailed
            } finally {
                // The APK path is immutable and never used by subsequent downloads.
                status = UpdateStatus.Ready(release, file)
            }
        }
    }

    /** Logs the technical detail and returns the friendly text of the failed step. Raw server or system text is never shown. */
    private fun friendlyError(error: Throwable, @StringRes fallback: Int): String {
        Log.w(UPDATES_LOG_TAG, "Update step failed", error)
        return localized(updateFailureTextRes(error, fallback))
    }
}
