package com.beykus.beygram.ui.screens.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beykus.beygram.data.update.StableRelease
import com.beykus.beygram.data.update.StableUpdateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object Current : UpdateStatus
    data class Available(val release: StableRelease) : UpdateStatus
    data class Downloading(val release: StableRelease, val progress: Int) : UpdateStatus
    data class Ready(val release: StableRelease, val file: File) : UpdateStatus
    data class PreparingInstall(val release: StableRelease) : UpdateStatus
    data class Failure(val message: String, val release: StableRelease? = null) : UpdateStatus
}

class StableUpdatesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StableUpdateRepository(application.applicationContext)
    var status: UpdateStatus by mutableStateOf(UpdateStatus.Idle)
        private set
    var installHint: String? by mutableStateOf(null)
        private set
    private var runningJob: Job? = null

    fun check() {
        if (status is UpdateStatus.Checking || status is UpdateStatus.Downloading ||
            status is UpdateStatus.PreparingInstall) return
        installHint = null
        status = UpdateStatus.Checking
        runningJob = viewModelScope.launch {
            try {
                val release = repository.check(repository.installedCode())
                status = if (release == null) UpdateStatus.Current else UpdateStatus.Available(release)
            } catch (cancelled: CancellationException) {
                status = UpdateStatus.Idle
                throw cancelled
            } catch (error: Exception) {
                status = UpdateStatus.Failure(error.message ?: "Не удалось проверить GitHub Releases.")
            }
        }
    }

    fun download(release: StableRelease) {
        val previous = status
        if (previous !is UpdateStatus.Available && previous !is UpdateStatus.Failure) return
        if (previous is UpdateStatus.Available && previous.release != release) return
        if (previous is UpdateStatus.Failure && previous.release != release) return
        installHint = null
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
                status = UpdateStatus.Failure(error.message ?: "Ошибка загрузки APK.", release)
            }
        }
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
        installHint = "Проверяем APK перед установкой…"
        viewModelScope.launch {
            try {
                repository.verifyForInstall(file, release)
                installHint = if (repository.install(file)) {
                    "Подтвердите обновление в системном установщике Android."
                } else {
                    "Разрешите установку из BeyGram в настройках Android, затем вернитесь и нажмите «Установить»."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                installHint = error.message ?: "Не удалось открыть установщик Android."
            } finally {
                // The APK path is immutable and never used by subsequent downloads.
                status = UpdateStatus.Ready(release, file)
            }
        }
    }
}
