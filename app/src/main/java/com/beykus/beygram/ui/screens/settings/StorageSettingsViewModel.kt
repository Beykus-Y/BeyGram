package com.beykus.beygram.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beykus.beygram.R
import com.beykus.beygram.data.storage.StorageSettings
import com.beykus.beygram.data.storage.StorageSettingsStore
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
    private val store: StorageSettingsStore
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
    /** Сколько освободили последняя очистка или смена лимита. */
    var freedBytes: Long? by mutableStateOf(null)
        private set
    var error: UiText? by mutableStateOf(null)
        private set

    fun refresh() {
        if (cleaningProgress != null) return
        viewModelScope.launch { loadUsage() }
    }

    fun toggle(category: StorageCategory) {
        if (cleaningProgress != null) return
        selected = if (category in selected) selected - category else selected + category
    }

    fun clear(categories: Set<StorageCategory>) {
        if (cleaningProgress != null || categories.isEmpty()) return
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

    /** Новый лимит или срок сразу применяются: всё лишнее удаляется, а цифры на экране пересчитываются. */
    fun changeSettings(transform: (StorageSettings) -> StorageSettings) {
        if (cleaningProgress != null) return
        store.update(transform)
        error = null
        freedBytes = null
        viewModelScope.launch {
            repository.applyCachePolicy(store.settings.value.policy())
                .onSuccess { freedBytes = it.takeIf { bytes -> bytes > 0L } }
                .onFailure { error = it.toUiText(R.string.storage_clear_failed) }
            loadUsage()
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
