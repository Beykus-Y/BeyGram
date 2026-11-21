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
import kotlinx.coroutines.CancellationException
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
    /** Сохранённые размеры не считаются актуальными, пока повторный подсчёт не завершился успешно. */
    var usageStale: Boolean by mutableStateOf(false)
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

    /** Номер последнего пересчёта. Ответ более раннего пересчёта отбрасывается, даже если пришёл позже. */
    private var loadGeneration = 0
    /** Не разрешаем новую очистку между завершением удаления и обновлением размеров. */
    private var reconciling by mutableStateOf(false)
    /** Пользователь сам менял выбор: тогда пересчёт сохраняет его и не добавляет новые категории. */
    private var selectionTouched = false

    /** Смены лимита или срока, которые ещё применяются. Очистка ждёт, пока они закончатся. */
    val applying: Boolean get() = pendingChanges > 0
    /** Экран занят, пока идёт очистка или применяется настройка. */
    val busy: Boolean get() = cleaningProgress != null || applying || reconciling

    fun refresh() {
        if (busy) return
        error = null
        viewModelScope.launch { loadUsage() }
    }

    fun toggle(category: StorageCategory) {
        if (busy || loading || usageStale) return
        selectionTouched = true
        selected = if (category in selected) selected - category else selected + category
    }

    fun clear(categories: Set<StorageCategory>) {
        if (busy || loading || usageStale || categories.isEmpty()) return
        val snapshot = categories.toSet()
        error = null
        freedBytes = null
        cleaningProgress = 0f
        // Отбросить пересчёт, начатый до удаления: его ответ может прийти после начала очистки.
        loadGeneration++
        loading = false
        usageStale = usage != null
        viewModelScope.launch {
            try {
                repository.clearStorage(snapshot) { done, total ->
                    cleaningProgress = if (total == 0) 1f else done.toFloat() / total
                }
                    .onSuccess { freedBytes = it }
                    .onFailure { error = it.toUiText(R.string.storage_clear_failed) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.toUiText(R.string.storage_clear_failed)
            } finally {
                // Busy must remain true until the post-clear size snapshot is reconciled (also on failure).
                reconciling = true
                cleaningProgress = null
                try {
                    loadUsage()
                } finally {
                    reconciling = false
                }
            }
        }
    }

    /**
     * Новый лимит или срок сразу сохраняются, а применяются в фоне: всё лишнее удаляется, и цифры пересчитываются.
     * Смены, которые пришли во время применения, не ждут друг друга: репозиторий применит только последнюю.
     */
    fun changeSettings(transform: (StorageSettings) -> StorageSettings) {
        if (cleaningProgress != null || reconciling) return
        if (store.update(transform).isFailure) {
            // Прежняя настройка осталась на месте; экран показывает ошибку и ничего не применяет.
            error = UiText.Res(R.string.storage_settings_save_failed)
            return
        }
        error = null
        // Серия смен суммирует освобождённое: вторая может ничего не освободить, потому что первая уже сделала всё.
        if (!applying) freedBytes = null
        pendingChanges++
        // Любой размер, считанный до изменения политики, теперь устарел.
        loadGeneration++
        loading = false
        usageStale = usage != null
        viewModelScope.launch {
            try {
                repository.applyCachePolicy(store.settings.value.policy())
                    .onSuccess { bytes ->
                        // Успех последнего применения снимает ошибку более раннего.
                        error = null
                        if (bytes > 0L) freedBytes = (freedBytes ?: 0L) + bytes
                    }
                    .onFailure { error = it.toUiText(R.string.storage_clear_failed) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.toUiText(R.string.storage_clear_failed)
            } finally {
                pendingChanges--
                // Даже когда apply уже завершился, новое удаление запрещено до свежего снимка.
                if (!applying) {
                    reconciling = true
                    try {
                        loadUsage()
                    } finally {
                        reconciling = false
                    }
                }
            }
        }
    }

    private suspend fun loadUsage() {
        val generation = ++loadGeneration
        loading = true
        if (usage != null) usageStale = true
        try {
            repository.storageUsage()
                .onSuccess { loaded ->
                    // Старый ответ не должен перезаписать более новый: снимок остаётся прежним, пока не придёт актуальный.
                    if (generation != loadGeneration) return@onSuccess
                    usage = loaded
                    usageStale = false
                    selected = reconcileSelection(loaded)
                }
                .onFailure { if (generation == loadGeneration) error = it.toUiText(R.string.storage_load_failed) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (generation == loadGeneration) error = failure.toUiText(R.string.storage_load_failed)
        } finally {
            // Отмена пересчёта тоже снимает индикатор, но только у последнего пересчёта.
            if (generation == loadGeneration) loading = false
        }
    }

    /** Сохраняет явный выбор (в том числе пустой) и не добавляет новые категории к нему. По умолчанию выбрано всё, где есть файлы. */
    private fun reconcileSelection(loaded: StorageUsage): Set<StorageCategory> {
        val available = loaded.categoriesWithFiles()
        return if (selectionTouched) selected.intersect(available) else available
    }

    private fun Throwable.toUiText(fallback: Int): UiText = (this as? UserMessageException)?.text ?: UiText.Res(fallback)
}
