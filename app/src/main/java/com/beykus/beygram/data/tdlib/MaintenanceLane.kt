package com.beykus.beygram.data.tdlib

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Очередь файловых операций TDLib: очистка и применение лимита выполняются одна за другой.
 * [latestOnly] сводит накопившиеся запросы к последнему: если за время ожидания пришёл более новый, старый пропускается.
 */
internal class MaintenanceLane {
    private val mutex = Mutex()
    private val newest = AtomicLong(0L)

    suspend fun <T> exclusive(action: suspend () -> T): T = mutex.withLock { action() }

    suspend fun <T> latestOnly(skipped: T, action: suspend () -> T): T {
        val ticket = newest.incrementAndGet()
        return mutex.withLock { if (ticket == newest.get()) action() else skipped }
    }
}
