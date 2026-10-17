package com.beykus.beygram.data.tdlib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs [publish] once per burst: the first [request] schedules it [delayMs] later, and every request made before
 * that run joins it.
 */
internal class CoalescedPublisher(
    private val scope: CoroutineScope,
    private val delayMs: Long,
    private val publish: () -> Unit
) {
    private val queued = AtomicBoolean(false)
    private val publishing = Mutex()

    fun request() {
        if (!queued.compareAndSet(false, true)) return
        scope.launch {
            delay(delayMs)
            // Runs never overlap, so a slower run cannot write an older snapshot after a newer one.
            publishing.withLock {
                // Clear the flag once the run starts: changes made while the run waits for the lock join that run.
                queued.set(false)
                publish()
            }
        }
    }
}
