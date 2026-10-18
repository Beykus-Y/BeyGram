package com.beykus.beygram.data.tdlib

import com.beykus.beygram.diagnostics.recordAppEvent
import com.beykus.beygram.diagnostics.report.EventKind
import com.beykus.beygram.diagnostics.report.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Пока вошли в аккаунт, лимиты проверяются каждые шесть часов. */
private const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L

/**
 * Держит кэш в лимитах. [apply] запускается сразу после входа и потом по расписанию. Ошибка одной проверки
 * попадает в журнал и не останавливает следующие.
 */
internal class TdCacheMaintenance(private val scope: CoroutineScope, private val apply: suspend () -> Result<Long>) {
    private var job: Job? = null

    @Synchronized
    fun authorized() {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                apply().onFailure { error ->
                    recordAppEvent(EventKind.TDLIB, "cache_policy_failed", "Кэш: лимит не применился", Severity.WARNING, mapOf("cause" to error.javaClass.simpleName))
                }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    @Synchronized
    fun signedOut() {
        job?.cancel()
        job = null
    }
}
