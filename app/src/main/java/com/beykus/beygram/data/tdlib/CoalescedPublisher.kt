package com.beykus.beygram.data.tdlib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    fun request() {
        if (!queued.compareAndSet(false, true)) return
        scope.launch {
            delay(delayMs)
            // Clear the flag before publishing, so a change that arrives later schedules the next run.
            queued.set(false)
            publish()
        }
    }
}
