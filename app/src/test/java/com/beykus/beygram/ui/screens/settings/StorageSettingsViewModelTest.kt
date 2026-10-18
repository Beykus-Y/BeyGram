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

    @Test fun laterSuccessClearsTheErrorOfAnEarlierFailure() {
        val repository = FakeRepository()
        val model = StorageSettingsViewModel(repository, FakeStore())

        model.changeSettings { it.copy(sizeLimit = StorageSizeLimit.OneGigabyte) }
        model.changeSettings { it.copy(retention = StorageRetention.Week) }
        repository.gates[0].complete(Result.failure(IllegalStateException("disk")))
        assertEquals(UiText.Res(R.string.storage_clear_failed), model.error)

        repository.gates[1].complete(Result.success(0L))
        assertNull(model.error)
    }

    private class FakeStore(private val saveFails: Boolean = false) : StorageSettingsSource {
        private val state = MutableStateFlow(StorageSettings())
        override val settings: StateFlow<StorageSettings> = state

        override fun update(transform: (StorageSettings) -> StorageSettings): Result<Unit> {
            if (saveFails) return Result.failure(IllegalStateException("disk full"))
            state.value = transform(state.value)
            return Result.success(Unit)
        }
    }

    /** Каждое применение ждёт свой сигнал из [gates], поэтому тест завершает их по одному. */
    private class FakeRepository : StorageRepository {
        val applied = mutableListOf<CachePolicy>()
        val gates = mutableListOf<CompletableDeferred<Result<Long>>>()
        var usageLoads = 0
        var clears = 0

        override suspend fun storageUsage(): Result<StorageUsage> {
            usageLoads++
            return Result.success(StorageUsage(bytesByCategory = emptyMap(), databaseBytes = 0L))
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
