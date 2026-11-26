package com.beykus.beygram.data.update

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

private const val LOG_TAG = "BeyGramUpdates"

/**
 * The update state of the whole process. Screens only observe [state] and send actions, so a download keeps running
 * when the settings screen closes. Downloads and installs start only after a user action; the only unattended work is
 * the check of [checkAutomatically], which is orchestrated by [AutomaticUpdateChecks].
 *
 * Operations are serialized: a new one is refused while another runs, except that a user action aborts an unattended
 * check instead of being refused. After process death, [restore] rebuilds the state from disk and checks every file
 * again. An unfinished operation is never reported as running.
 */
class UpdateCoordinator private constructor(context: Context) {
    private val source: ApkUpdateSource = when (updateChannelFor(context.packageName)) {
        UpdateChannel.DevBeta -> DevUpdateRepository(context)
        UpdateChannel.Stable -> StableUpdateRepository(context)
    }
    private val store = UpdateStateStore(context)
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()
    private var operation: Job? = null
    /** True while [operation] is the unattended check, so a user action can take the slot from it. */
    private var operationIsAutomatic = false
    /** The status before an unattended check started, restored if a user action aborts it mid-flight. */
    private var statusBeforeAutomaticCheck: UpdateStatus? = null
    private var cancelRequested = false
    private val restoring: Job = scope.launch { restore() }

    companion object {
        @Volatile
        private var instance: UpdateCoordinator? = null

        /** The one coordinator of this process. */
        fun get(context: Context): UpdateCoordinator =
            instance ?: synchronized(this) {
                instance ?: UpdateCoordinator(context.applicationContext).also { instance = it }
            }
    }

    /** Looks for a newer release. Never downloads. A failed check keeps a verified ready file. */
    fun check() = start { runCheck(reportFailure = true) }

    /**
     * The unattended check. It is the same request as a manual one — one coordinator, one in-flight operation, one
     * persisted state — but it may never turn the screen into a failure state and may never claim that the installed
     * build is the latest after a failed attempt. A running user operation wins: this returns [Skipped] at once.
     */
    suspend fun checkAutomatically(): AutomaticCheckOutcome = withContext(Dispatchers.Main.immediate) {
        restoring.join()
        if (operation?.isActive == true) return@withContext AutomaticCheckOutcome.Skipped
        statusBeforeAutomaticCheck = mutableState.value.status
        val work = scope.async { runCheck(reportFailure = false) }
        operation = work
        operationIsAutomatic = true
        try {
            work.await()
        } catch (cancelled: CancellationException) {
            // A user action took the slot; the automatic attempt deliberately has no result.
            AutomaticCheckOutcome.Skipped
        } finally {
            operationIsAutomatic = false
            statusBeforeAutomaticCheck = null
        }
    }

    /** One check, shared by the manual action and the unattended one. */
    private suspend fun runCheck(reportFailure: Boolean): AutomaticCheckOutcome {
        if (!StableUpdateActionPolicy.canCheck(mutableState.value.status)) return AutomaticCheckOutcome.Skipped
        setStatus(UpdateStatus.Checking)
        try {
            val release = source.check(source.installedCode())
            val now = System.currentTimeMillis()
            store.saveLastCheckedAt(now)
            store.saveKnownRelease(release)
            // The manual-check summary stays a record of a check the user started; see UpdateCheckStore.
            if (reportFailure) recordCheck(if (release == null) UpdateCheckOutcome.Current else UpdateCheckOutcome.Available, release, now)
            mutableState.update {
                it.copy(
                    status = if (release == null) UpdateStatus.Current else UpdateStatus.Available(release),
                    knownRelease = release,
                    lastCheckedAt = now,
                    checkStale = false,
                )
            }
            return if (release == null) AutomaticCheckOutcome.Current else AutomaticCheckOutcome.Available(release)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w(LOG_TAG, "Update check failed", error)
            if (reportFailure) recordCheck(UpdateCheckOutcome.Failed, null, System.currentTimeMillis())
            if (reportFailure) {
                mutableState.update {
                    it.copy(
                        status = UpdateStatus.Failure(UpdateStep.Check, updateErrorReason(error), null),
                        checkStale = true,
                    )
                }
            } else {
                // An unattended failure confirms nothing and must not leave the card on "Checking": the previous
                // status is restored and the known offer is only marked as unconfirmed.
                mutableState.update { restoreAfterAutomaticFailure(it, statusBeforeAutomaticCheck) }
            }
            return AutomaticCheckOutcome.Failed(updateErrorReason(error))
        }
    }

    /** Starts or resumes a user-requested download. A verified partial of the same release is continued. */
    fun download(release: StableRelease) = start {
        if (!StableUpdateActionPolicy.canDownload(mutableState.value.status, release)) return@start
        cancelRequested = false
        val resume = store.partial()?.takeIf { it.release.binds(release) }
        val ready = mutableState.value.ready
        val handoff = store.handoff()
        withContext(Dispatchers.IO) {
            ApkRetentionPolicy.cleanUp(
                source.updateDir,
                protectedNames(ready?.file?.name, handoff?.name, resume?.release),
                System.currentTimeMillis(),
            )
        }
        setStatus(UpdateStatus.Downloading(release, DownloadProgress(0L, release.size, null, null)))
        val listener = object : DownloadListener {
            override suspend fun onResponse(startBytes: Long, validator: String?) {
                // A resumed reply without its own validator keeps the one the partial was started with.
                val kept = if (startBytes > 0L) validator ?: resume?.validator else validator
                store.savePartial(kept?.let { PartialDownload(release, it) })
                mutableState.update {
                    it.copy(status = UpdateStatus.Downloading(release, DownloadProgress(startBytes, release.size, null, null)))
                }
            }

            override suspend fun onProgress(progress: DownloadProgress) {
                mutableState.update {
                    if (it.status is UpdateStatus.Downloading) it.copy(status = UpdateStatus.Downloading(release, progress)) else it
                }
            }

            override suspend fun onVerifying() {
                setStatus(UpdateStatus.Verifying(release))
            }
        }
        try {
            val apk = source.download(release, resume, listener)
            store.savePartial(null)
            store.saveReady(StoredApk(release, apk.name))
            if (ready != null && ready.file != apk) removeUnhandedFile(ready.file)
            mutableState.update {
                it.copy(status = UpdateStatus.Ready(release, apk), ready = ReadyApk(release, apk), installNotice = null)
            }
        } catch (error: Exception) {
            if (cancelRequested) {
                // An explicit cancel ends the download and removes its partial file.
                deletePartial(release)
                setStatus(UpdateStatus.Available(release))
                return@start
            }
            if (error is CancellationException) throw error
            Log.w(LOG_TAG, "Update download failed", error)
            val reason = updateErrorReason(error)
            if (reason in DISCARD_PARTIAL_REASONS) deletePartial(release)
            setStatus(UpdateStatus.Failure(UpdateStep.Download, reason, release))
        }
    }

    /** Ends the running download. The network call is closed and the partial file is removed. */
    fun cancelDownload() {
        if (mutableState.value.status !is UpdateStatus.Downloading) return
        cancelRequested = true
        operation?.cancel()
    }

    /** Verifies the ready APK again and hands it to the installer. Used by the install button and by "continue". */
    fun install(release: StableRelease) = start {
        val ready = mutableState.value.ready?.takeIf { it.release == release } ?: return@start
        if (!StableUpdateActionPolicy.canInstall(mutableState.value.status, release)) return@start
        setStatus(UpdateStatus.PreparingInstall(release))
        try {
            source.verifyForInstall(ready.file, release)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w(LOG_TAG, "Install verification failed", error)
            removeUnhandedFile(ready.file)
            store.saveReady(null)
            mutableState.update {
                it.copy(
                    status = UpdateStatus.Failure(UpdateStep.Verify, updateErrorReason(error), release),
                    ready = null,
                    installNotice = null,
                )
            }
            return@start
        }
        val installed = source.installedCode()
        if (installed >= release.versionCode) {
            finishInstalled(installed)
            return@start
        }
        if (!source.canRequestInstalls()) {
            setStatus(UpdateStatus.PermissionRequired(release, ready.file))
            return@start
        }
        // The handoff is recorded before the installer starts, so a process death cannot lose it.
        store.saveHandoff(StoredApk(release, ready.file.name))
        try {
            source.launchInstaller(ready.file)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w(LOG_TAG, "Installer launch failed", error)
            store.saveHandoff(null)
            mutableState.update {
                it.copy(status = UpdateStatus.Ready(release, ready.file), installNotice = InstallNotice.LaunchFailed)
            }
            return@start
        }
        mutableState.update {
            it.copy(status = UpdateStatus.AwaitingInstaller(release, ready.file), installNotice = null)
        }
    }

    /** Opens the Android page that allows installs from BeyGram. Only the user's tap in PermissionRequired does this. */
    fun openInstallPermission() {
        if (mutableState.value.status !is UpdateStatus.PermissionRequired) return
        runCatching { source.openInstallPermissionSettings() }
            .onFailure { Log.w(LOG_TAG, "Install permission settings did not open", it) }
    }

    /**
     * Called on every resume of the screen. After the installer, the installed version code decides: a successful
     * start of the installer proves nothing. If the code did not change, the verified APK is kept and offered again.
     */
    fun onReturnedToApp() = start {
        when (val current = mutableState.value.status) {
            is UpdateStatus.AwaitingInstaller -> {
                val installed = source.installedCode()
                if (installed >= current.release.versionCode) {
                    finishInstalled(installed)
                } else {
                    mutableState.update {
                        it.copy(
                            status = UpdateStatus.Ready(current.release, current.file),
                            installNotice = InstallNotice.NotInstalledYet,
                        )
                    }
                }
            }
            // The permission is read again; the install itself still waits for the user's tap on "continue".
            is UpdateStatus.PermissionRequired -> if (source.canRequestInstalls()) {
                mutableState.update { it.copy(installNotice = InstallNotice.PermissionGranted) }
            }
            else -> Unit
        }
    }

    /** Keeps the outcome of a manual check for the Settings summary. A failed write never affects the check. */
    private fun recordCheck(outcome: UpdateCheckOutcome, release: StableRelease?, now: Long) {
        runCatching {
            UpdateCheckStore.save(
                appContext,
                UpdateCheckRecord(
                    packageName = appContext.packageName,
                    installedCode = source.installedCode(),
                    channel = if (updateChannelFor(appContext.packageName) == UpdateChannel.DevBeta) "dev" else "stable",
                    checkedAt = now,
                    outcome = outcome,
                    availableLabel = release?.let { if (it.dev != null) it.tag else it.versionName },
                    availableCode = release?.versionCode,
                ),
            )
        }
    }

    /**
     * Runs a user action. A user operation keeps the slot to itself, as before; an unattended check is aborted
     * instead, so the user's tap is never refused because a background check happened to be in flight.
     */
    private fun start(block: suspend () -> Unit) {
        val running = operation
        if (running?.isActive == true) {
            if (!operationIsAutomatic) return
            running.cancel()
            // The aborted check may have left the card on "Checking"; a user action must not inherit that spinner.
            statusBeforeAutomaticCheck?.let { previous ->
                if (mutableState.value.status == UpdateStatus.Checking) setStatus(previous)
            }
        }
        operationIsAutomatic = false
        operation = scope.launch {
            restoring.join()
            // The aborted automatic request must be gone before the user's own request starts.
            running?.join()
            block()
        }
    }

    private fun setStatus(status: UpdateStatus) {
        mutableState.update { it.copy(status = status) }
    }

    private fun finishInstalled(installed: Long) {
        val ready = mutableState.value.ready
        store.saveHandoff(null)
        store.saveReady(null)
        ready?.let { removeUnhandedFile(it.file) }
        val known = mutableState.value.knownRelease?.takeIf { it.versionCode > installed }
        if (known == null) store.saveKnownRelease(null)
        mutableState.update {
            it.copy(
                status = UpdateStatus.Installed(installed),
                ready = null,
                installNotice = null,
                knownRelease = known,
            )
        }
    }

    /** Removes an APK unless the installer may still hold it. Handed-off APKs are immutable. */
    private fun removeUnhandedFile(file: File) {
        if (store.handoff()?.name == file.name) return
        file.delete()
    }

    private fun deletePartial(release: StableRelease) {
        File(source.updateDir, partialFileName(source.filePrefix, release)).delete()
        store.savePartial(null)
    }

    private fun protectedNames(readyName: String?, handoffName: String?, resumeRelease: StableRelease?): Set<String> =
        setOfNotNull(
            readyName,
            handoffName,
            resumeRelease?.let { partialFileName(source.filePrefix, it) },
        )

    /**
     * Rebuilds the state from disk. A ready APK is kept only when its size and SHA-256 still match and the
     * installed version is older. An installer handoff that finished is recognised by the installed version code.
     */
    private suspend fun restore() {
        val snapshot = withContext(Dispatchers.IO) { loadSnapshot() }
        mutableState.value = snapshot
    }

    private suspend fun loadSnapshot(): UpdateUiState {
        val now = System.currentTimeMillis()
        val installed = source.installedCode()
        val handoff = store.handoff()
        val installedNow = handoff != null && installed >= handoff.release.versionCode
        if (installedNow) store.saveHandoff(null)
        val pendingHandoff = if (installedNow) null else handoff

        var stored = store.ready()
        if (stored != null && !isUsableReady(stored, installed)) {
            removeUnhandedFile(File(source.updateDir, stored.name))
            store.saveReady(null)
            stored = null
        }

        val partial = store.partial()
        if (partial != null) {
            val file = File(source.updateDir, partialFileName(source.filePrefix, partial.release))
            val usable = installed < partial.release.versionCode && file.isFile &&
                now - file.lastModified() <= ApkRetentionPolicy.PARTIAL_MAX_AGE_MS
            if (!usable) {
                file.delete()
                store.savePartial(null)
            }
        }
        val keptPartial = store.partial()?.release
        ApkRetentionPolicy.cleanUp(
            source.updateDir,
            protectedNames(stored?.name, pendingHandoff?.name, keptPartial),
            now,
        )

        val known = store.knownRelease()?.takeIf { it.versionCode > installed }
        val ready = stored?.let { ReadyApk(it.release, File(source.updateDir, it.name)) }
        return UpdateUiState(
            status = when {
                installedNow -> UpdateStatus.Installed(installed)
                ready != null -> UpdateStatus.Ready(ready.release, ready.file)
                else -> UpdateStatus.Idle
            },
            ready = ready,
            knownRelease = known,
            lastCheckedAt = store.lastCheckedAt(),
            checkStale = known != null,
        )
    }

    private suspend fun isUsableReady(stored: StoredApk, installed: Long): Boolean {
        val file = File(source.updateDir, stored.name)
        return installed < stored.release.versionCode &&
            file.isFile && file.length() == stored.release.size &&
            sha256Of(file) == stored.release.sha256
    }
}


/**
 * State after an unattended check failed. The card must not stay on [UpdateStatus.Checking] (that would be a spinner
 * with no request behind it), and an already confirmed offer is kept but marked as not confirmed any more.
 */
internal fun restoreAfterAutomaticFailure(state: UpdateUiState, previous: UpdateStatus?): UpdateUiState {
    val restored = when {
        state.status != UpdateStatus.Checking -> state.status
        previous != null && previous != UpdateStatus.Checking -> previous
        else -> UpdateStatus.Idle
    }
    return state.copy(status = restored, checkStale = state.knownRelease != null)
}
