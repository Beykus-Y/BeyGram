package com.beykus.beygram.ui.screens.settings

import com.beykus.beygram.R
import com.beykus.beygram.data.storage.StorageRetention
import com.beykus.beygram.data.storage.StorageSettings
import com.beykus.beygram.data.storage.StorageSettingsSource
import com.beykus.beygram.data.storage.StorageSizeLimit
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.CachePolicy
import com.beykus.beygram.domain.model.StorageCategory
import com.beykus.beygram.domain.model.StorageUsage
import com.beykus.beygram.domain.repository.StorageRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorageSettingsViewModelTest {
    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun busyStaysOnUntilEveryPendingChangeFinishes() {
        val repository = FakeRepository()
        val model = StorageSettingsViewModel(repository, FakeStore())

        model.changeSettings { it.copy(sizeLimit = StorageSizeLimit.OneGigabyte) }
        // Вторая смена пришла, пока первая ещё применяется: экран остаётся занятым до конца обеих.
        model.changeSettings { it.copy(retention = StorageRetention.Week) }

        assertTrue(model.busy)
        assertEquals(2, repository.applied.size)
        // Первая смена освободила 100 байт, вторая уже ничего не освобождает: сумма не должна пропасть.
        repository.gates[0].complete(Result.success(100L))
        assertTrue(model.busy)
        assertEquals(0, repository.usageLoads)
        repository.gates[1].complete(Result.success(0L))
        assertFalse(model.busy)
        assertEquals(1, repository.usageLoads)
        assertEquals(100L, model.freedBytes)
    }

    @Test fun clearIsRefusedWhileALimitIsStillApplying() {
        val repository = FakeRepository()
        val model = StorageSettingsViewModel(repository, FakeStore())

        model.changeSettings { it.copy(sizeLimit = StorageSizeLimit.OneGigabyte) }
        model.clear(setOf(StorageCategory.Photos))

        assertEquals(0, repository.clears)
        repository.gates.single().complete(Result.success(0L))
        assertFalse(model.busy)
    }

    @Test fun failedSaveKeepsThePreviousSettingAndShowsAControlledError() {
        val store = FakeStore(saveFails = true)
        val repository = FakeRepository()
        val model = StorageSettingsViewModel(repository, store)

        model.changeSettings { it.copy(sizeLimit = StorageSizeLimit.OneGigabyte) }

        assertEquals(StorageSettings(), store.settings.value)
        assertEquals(UiText.Res(R.string.storage_settings_save_failed), model.error)
        assertEquals(emptyList<CachePolicy>(), repository.applied)
        assertFalse(model.busy)
    }

    @Test fun olderFailureIsIgnoredWhileANewerPolicyIsPending() {
        val repository = FakeRepository()
        val model = StorageSettingsViewModel(repository, FakeStore())

        model.changeSettings { it.copy(sizeLimit = StorageSizeLimit.OneGigabyte) }
        model.changeSettings { it.copy(retention = StorageRetention.Week) }
        repository.gates[0].complete(Result.failure(IllegalStateException("old policy")))
        // Only the latest policy is authoritative, so an earlier failure is never shown.
        assertNull(model.error)
        assertTrue(model.busy)

        repository.gates[1].complete(Result.success(0L))
        assertNull(model.error)
        assertFalse(model.busy)
    }

    @Test fun priorApplySuccessCannotHideARecentSaveFailure() {
        val store = FakeStore()
        val repository = FakeRepository()
        val model = StorageSettingsViewModel(repository, store)

        model.changeSettings { it.copy(sizeLimit = StorageSizeLimit.OneGigabyte) }
        store.failNextSave = true
        model.changeSettings { it.copy(retention = StorageRetention.Week) }
        assertEquals(UiText.Res(R.string.storage_settings_save_failed), model.error)

        repository.gates.single().complete(Result.success(0L))
        assertEquals(UiText.Res(R.string.storage_settings_save_failed), model.error)
        assertFalse(model.busy)
    }

    @Test fun olderFailureDoesNotOverwriteTheNewestPolicySuccess() {
        val repository = FakeRepository()
        val model = StorageSettingsViewModel(repository, FakeStore())

        model.changeSettings { it.copy(sizeLimit = StorageSizeLimit.OneGigabyte) }
        model.changeSettings { it.copy(retention = StorageRetention.Week) }

        repository.gates[1].complete(Result.success(0L))
        assertNull(model.error)
        repository.gates[0].complete(Result.failure(IllegalStateException("old policy failed")))
        assertNull(model.error)
        assertFalse(model.busy)
    }

    @Test fun olderSuccessDoesNotHideTheNewestPolicyFailure() {
        val repository = FakeRepository()
        val model = StorageSettingsViewModel(repository, FakeStore())

        model.changeSettings { it.copy(sizeLimit = StorageSizeLimit.OneGigabyte) }
        model.changeSettings { it.copy(retention = StorageRetention.Week) }

        repository.gates[1].complete(Result.failure(IllegalStateException("new policy failed")))
        assertEquals(UiText.Res(R.string.storage_clear_failed), model.error)
        repository.gates[0].complete(Result.success(100L))
        assertEquals(UiText.Res(R.string.storage_clear_failed), model.error)
        assertFalse(model.busy)
    }

    @Test fun olderRecalculationDoesNotOverwriteNewerUsage() {
        val repository = FakeRepository().apply { gateUsage = true }
        val model = StorageSettingsViewModel(repository, FakeStore())

        model.refresh()
        model.refresh()
        // Новый пересчёт ответил первым, старый пришёл позже и должен быть отброшен.
        repository.usageGates[1].complete(Result.success(usageOf(StorageCategory.Photos to 300L)))
        repository.usageGates[0].complete(Result.success(usageOf(StorageCategory.Photos to 100L)))

        assertEquals(300L, model.usage?.bytes(StorageCategory.Photos))
        assertFalse(model.loading)
    }

    @Test fun firstLoadFailureCanBeRetried() {
        val repository = FakeRepository().apply { gateUsage = true }
        val model = StorageSettingsViewModel(repository, FakeStore())

        model.refresh()
        repository.usageGates[0].complete(Result.failure(IllegalStateException("network")))

        assertNull(model.usage)
        assertFalse(model.loading)
        assertEquals(UiText.Res(R.string.storage_load_failed), model.error)

        // Повтор успешен: ошибка уходит, цифры появляются.
        model.refresh()
        repository.usageGates[1].complete(Result.success(usageOf(StorageCategory.Photos to 42L)))

        assertEquals(42L, model.usage?.bytes(StorageCategory.Photos))
        assertNull(model.error)
        assertFalse(model.loading)
    }

    @Test fun emptySelectionSurvivesRecalculation() {
        val repository = FakeRepository().apply { usage = usageOf(StorageCategory.Photos to 10L, StorageCategory.Videos to 5L) }
        val model = StorageSettingsViewModel(repository, FakeStore())

        model.refresh()
        assertEquals(setOf(StorageCategory.Photos, StorageCategory.Videos), model.selected)
        model.toggle(StorageCategory.Photos)
        model.toggle(StorageCategory.Videos)
        model.refresh()

        assertEquals(emptySet<StorageCategory>(), model.selected)
    }

    @Test fun newCategoriesAreNotAddedToAUserChoice() {
        val repository = FakeRepository().apply { usage = usageOf(StorageCategory.Photos to 10L, StorageCategory.Videos to 5L) }
        val model = StorageSettingsViewModel(repository, FakeStore())

        model.refresh()
        // Пользователь оставил только Фото: Видео снято.
        model.toggle(StorageCategory.Videos)
        assertEquals(setOf(StorageCategory.Photos), model.selected)
        // Появилась новая категория: в выбор она не должна попасть.
        repository.usage = usageOf(StorageCategory.Photos to 10L, StorageCategory.Videos to 5L, StorageCategory.Music to 7L)
        model.refresh()

        assertEquals(setOf(StorageCategory.Photos), model.selected)
    }

    @Test fun secondClearWaitsForPostClearRecount() {
        val repository = FakeRepository().apply { usage = usageOf(StorageCategory.Photos to 10L) }
        val model = StorageSettingsViewModel(repository, FakeStore())
        model.refresh()
        repository.gateUsage = true

        model.clear(setOf(StorageCategory.Photos))
        // The delete itself returned, but the new size query is still pending.
        assertNull(model.cleaningProgress)
        assertTrue(model.busy)
        assertTrue(model.usageStale)

        model.clear(setOf(StorageCategory.Photos))
        assertEquals(1, repository.clears)
        repository.usageGates.single().complete(Result.success(usageOf(StorageCategory.Photos to 0L)))

        assertFalse(model.busy)
        assertFalse(model.usageStale)
        assertEquals(0L, model.usage?.bytes(StorageCategory.Photos))
    }

    @Test fun failedRefreshKeepsOldNumbersMarkedStaleUntilRetry() {
        val repository = FakeRepository().apply { usage = usageOf(StorageCategory.Photos to 25L) }
        val model = StorageSettingsViewModel(repository, FakeStore())
        model.refresh()
        repository.gateUsage = true

        model.refresh()
        assertTrue(model.loading)
        assertTrue(model.usageStale)
        repository.usageGates[0].complete(Result.failure(IllegalStateException("disk")))

        assertEquals(25L, model.usage?.bytes(StorageCategory.Photos))
        assertTrue(model.usageStale)
        assertFalse(model.loading)
        model.clear(setOf(StorageCategory.Photos))
        assertEquals(0, repository.clears)

        model.refresh()
        repository.usageGates[1].complete(Result.success(usageOf(StorageCategory.Photos to 12L)))
        assertEquals(12L, model.usage?.bytes(StorageCategory.Photos))
        assertFalse(model.usageStale)
        assertNull(model.error)
    }

    @Test fun policyChangeInvalidatesAnOlderRefresh() {
        val repository = FakeRepository().apply { gateUsage = true }
        val model = StorageSettingsViewModel(repository, FakeStore())
        model.refresh()
        model.changeSettings { it.copy(sizeLimit = StorageSizeLimit.OneGigabyte) }
        repository.gates.single().complete(Result.success(0L))
        assertEquals(2, repository.usageGates.size)
        assertTrue(model.busy)

        repository.usageGates[0].complete(Result.success(usageOf(StorageCategory.Photos to 99L)))
        assertNull(model.usage)
        repository.usageGates[1].complete(Result.success(usageOf(StorageCategory.Photos to 8L)))
        assertEquals(8L, model.usage?.bytes(StorageCategory.Photos))
        assertFalse(model.busy)
    }

    private fun usageOf(vararg entries: Pair<StorageCategory, Long>) =
        StorageUsage(bytesByCategory = mapOf(*entries), databaseBytes = 0L)

    private class FakeStore(private val saveFails: Boolean = false) : StorageSettingsSource {
        var failNextSave = false
        private val state = MutableStateFlow(StorageSettings())
        override val settings: StateFlow<StorageSettings> = state

        override fun update(transform: (StorageSettings) -> StorageSettings): Result<Unit> {
            if (saveFails || failNextSave) {
                failNextSave = false
                return Result.failure(IllegalStateException("disk full"))
            }
            state.value = transform(state.value)
            return Result.success(Unit)
        }
    }

    /** Каждое применение ждёт свой сигнал из [gates], поэтому тест завершает их по одному. */
    private class FakeRepository : StorageRepository {
        val applied = mutableListOf<CachePolicy>()
        val gates = mutableListOf<CompletableDeferred<Result<Long>>>()
        val usageGates = mutableListOf<CompletableDeferred<Result<StorageUsage>>>()
        var gateUsage = false
        var usage = StorageUsage(bytesByCategory = emptyMap(), databaseBytes = 0L)
        var usageLoads = 0
        var clears = 0

        override suspend fun storageUsage(): Result<StorageUsage> {
            usageLoads++
            if (!gateUsage) return Result.success(usage)
            val gate = CompletableDeferred<Result<StorageUsage>>()
            usageGates += gate
            return gate.await()
        }

        override suspend fun clearStorage(categories: Set<StorageCategory>, onProgress: (done: Int, total: Int) -> Unit): Result<Long> {
            clears++
            return Result.success(0L)
        }

        override suspend fun applyCachePolicy(policy: CachePolicy): Result<Long> {
            applied += policy
            val gate = CompletableDeferred<Result<Long>>()
            gates += gate
            return gate.await()
        }
    }
}
