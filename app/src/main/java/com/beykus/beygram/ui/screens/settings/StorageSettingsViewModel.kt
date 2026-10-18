package com.beykus.beygram.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beykus.beygram.R
import com.beykus.beygram.data.storage.StorageSettings
import com.beykus.beygram.data.storage.StorageSettingsSource
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.StorageCategory
import com.beykus.beygram.domain.model.StorageUsage
import com.beykus.beygram.domain.repository.StorageRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Состояние экрана «Память и кэш». Экран сам вызывает [refresh] при каждом открытии, поэтому данные не устаревают. */
class StorageSettingsViewModel(
    private val repository: StorageRepository,
    private val store: StorageSettingsSource
) : ViewModel() {
    val settings: StateFlow<StorageSettings> = store.settings

    var usage: StorageUsage? by mutableStateOf(null)
        private set
    var loading: Boolean by mutableStateOf(true)
        private set
    /** Категории, которые уйдут в очистку выбранного. По умолчанию выбраны все, где есть файлы. */
    var selected: Set<StorageCategory> by mutableStateOf(emptySet())
        private set
    /** Доля готовой очистки от 0 до 1; null, когда очистки нет. */
    var cleaningProgress: Float? by mutableStateOf(null)
        private set
    /** Сколько смен лимита или срока ещё применяются. */
    private var pendingChanges: Int by mutableStateOf(0)
    /** Сколько освободили последняя очистка или смена лимита. */
    var freedBytes: Long? by mutableStateOf(null)
        private set
    var error: UiText? by mutableStateOf(null)
        private set

    /** Смены лимита или срока, которые ещё применяются. Очистка ждёт, пока они закончатся. */
    val applying: Boolean get() = pendingChanges > 0
    /** Экран занят, пока идёт очистка или применяется настройка. */
    val busy: Boolean get() = cleaningProgress != null || applying

    fun refresh() {
        if (cleaningProgress != null) return
        error = null
        viewModelScope.launch { loadUsage() }
    }

    fun toggle(category: StorageCategory) {
        if (cleaningProgress != null) return
        selected = if (category in selected) selected - category else selected + category
    }

    fun clear(categories: Set<StorageCategory>) {
        if (busy || categories.isEmpty()) return
        error = null
        freedBytes = null
        cleaningProgress = 0f
        viewModelScope.launch {
            repository.clearStorage(categories) { done, total ->
                cleaningProgress = if (total == 0) 1f else done.toFloat() / total
            }
                .onSuccess { freedBytes = it }
                .onFailure { error = it.toUiText(R.string.storage_clear_failed) }
            cleaningProgress = null
            loadUsage()
        }
    }

    /**
     * Новый лимит или срок сразу сохраняются, а применяются в фоне: всё лишнее удаляется, и цифры пересчитываются.
     * Смены, которые пришли во время применения, не ждут друг друга: репозиторий применит только последнюю.
     */
    fun changeSettings(transform: (StorageSettings) -> StorageSettings) {
        if (cleaningProgress != null) return
        if (store.update(transform).isFailure) {
            // Прежняя настройка осталась на месте; экран показывает ошибку и ничего не применяет.
            error = UiText.Res(R.string.storage_settings_save_failed)
            return
        }
        error = null
        freedBytes = null
        pendingChanges++
        viewModelScope.launch {
            try {
                repository.applyCachePolicy(store.settings.value.policy())
                    .onSuccess { freedBytes = it.takeIf { bytes -> bytes > 0L } }
                    .onFailure { error = it.toUiText(R.string.storage_clear_failed) }
            } finally {
                pendingChanges--
            }
            // Цифры пересчитываем один раз, когда применены все смены.
            if (!applying) loadUsage()
        }
    }

    private suspend fun loadUsage() {
        loading = true
        repository.storageUsage()
            .onSuccess { loaded ->
                usage = loaded
                selected = loaded.categoriesWithFiles()
            }
            .onFailure { error = it.toUiText(R.string.storage_load_failed) }
        loading = false
    }

    private fun Throwable.toUiText(fallback: Int): UiText = (this as? UserMessageException)?.text ?: UiText.Res(fallback)
}
