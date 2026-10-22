package com.beykus.beygram.analytics

import android.content.Context
import android.util.Log
import com.beykus.beygram.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Фоновая синхронизация аналитики. Не просит ни кода, ни бота и не ждёт входа в Telegram:
 * регистрация идёт при первом запуске, а состояние меняется только при изменении версии,
 * канала, Android, списка собственных аккаунтов или по daily heartbeat.
 *
 * Все отправки проходят через один [lock]. Повтор того же логического состояния использует
 * сохранённые ревизию и тело, новое состояние получает большую ревизию.
 */
object AnalyticsSync {
    private const val TAG = "AnalyticsSync"
    private const val HEARTBEAT_MS = 24L * 60 * 60 * 1000
    private const val CHECK_INTERVAL_MS = 30L * 60 * 1000
    private const val ACCOUNT_DEBOUNCE_MS = 5_000L
    private const val UNAVAILABLE_DELAY_MS = 6L * 60 * 60 * 1000
    private const val MIN_BACKOFF_MS = 30_000L
    private const val MAX_BACKOFF_MS = 60L * 60 * 1000

    private val lock = Mutex()
    @Volatile private var store: AnalyticsStore? = null
    @Volatile private var api: AnalyticsApi? = null
    @Volatile private var scope: CoroutineScope? = null
    @Volatile private var accountDebounce: Job? = null
    /** Единственная отложенная повторная попытка; новая заменяет старую, цепочки не растут. */
    @Volatile private var retryJob: Job? = null
    /** Число подряд неудачных попыток; меняется только под [lock]. */
    private var failures = 0

    /** Вызывается из Application один раз. Ничего не создаёт в TDLib и не зависит от Activity. */
    @Synchronized
    fun install(context: Context, scope: CoroutineScope, baseUrl: String = BuildConfig.DIAGNOSTICS_API_BASE_URL) {
        if (this.scope != null) return
        store = AnalyticsStore(context)
        api = AnalyticsApi(baseUrl)
        this.scope = scope
        scope.launch {
            while (true) {
                sync()
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    /** Текущее значение переключателя. По умолчанию включено. */
    fun isEnabled(): Boolean = store?.enabled ?: true

    /**
     * Переключатель в настройках. Выключение удаляет серверную запись, а включение после
     * заблокированного состояния (401) сначала удаляет старую запись и регистрирует новую.
     */
    fun setEnabled(enabled: Boolean) {
        val store = store ?: return
        store.enabled = enabled
        if (!enabled) {
            // Set unconditionally: a sync that is already past its enabled check may create an identity right now.
            // The deletion then runs under [lock] on the next sync, and it is a no-op when no identity exists.
            store.pendingDeletion = true
        } else if (store.blocked) {
            store.blocked = false
            store.pendingDeletion = true
        }
        requestAfter(0L)
    }

    /** Список собственных авторизованных ID: пустой список при выходе из аккаунта. */
    fun onAccountsChanged(accountIds: List<Long>) {
        val store = store ?: return
        store.saveAccountIds(accountIds)
        requestAfter(ACCOUNT_DEBOUNCE_MS, debounce = true)
    }

    private fun requestAfter(delayMs: Long, debounce: Boolean = false) {
        val scope = scope ?: return
        if (debounce) {
            accountDebounce?.cancel()
            accountDebounce = scope.launch { delay(delayMs); sync() }
        } else {
            scope.launch { delay(delayMs); sync() }
        }
    }

    private suspend fun sync() {
        val store = store ?: return
        val api = api ?: return
        lock.withLock {
            if (store.pendingDeletion && !deleteRemote(store, api)) return
            if (!store.enabled || store.blocked) return

            val identity = store.createIdentity()
            val signature = AnalyticsPayload.signature(identity.installationId, store.accountIds)
            val pending = store.pending()?.takeIf { it.signature == signature }
            val due = store.sentSignature() != signature ||
                System.currentTimeMillis() - store.sentAt() >= HEARTBEAT_MS
            if (pending == null && !due) return

            val request = pending ?: newPending(store, signature).also { store.savePending(it) }
            when (val result = api.sync(identity.credential, request.body)) {
                AnalyticsResult.Ok -> {
                    store.markSent(request.signature, System.currentTimeMillis())
                    failures = 0
                }
                // Контракт отклонён: тот же payload бессмыслен, поэтому не держим его в очереди.
                AnalyticsResult.Invalid -> {
                    Log.w(TAG, "Analytics payload rejected by contract")
                    store.markSent(request.signature, System.currentTimeMillis())
                    failures = 0
                }
                AnalyticsResult.Unauthorized -> {
                    Log.w(TAG, "Analytics credential rejected; waiting for re-enable")
                    store.blocked = true
                    store.clearPending()
                }
                is AnalyticsResult.Retry -> retryLater(result.afterSeconds)
                AnalyticsResult.Unavailable -> scheduleRetry(UNAVAILABLE_DELAY_MS)
            }
        }
    }

    /** Возвращает true, когда серверная запись удалена или её уже нет, и локальная идентичность сброшена. */
    private fun deleteRemote(store: AnalyticsStore, api: AnalyticsApi): Boolean {
        val identity = store.identity()
        if (identity == null) {
            store.pendingDeletion = false
            return true
        }
        return when (val result = api.delete(identity.installationId, identity.credential)) {
            // 401 значит, что ни эта запись, ни этот credential серверу больше не нужны.
            AnalyticsResult.Ok, AnalyticsResult.Unauthorized -> {
                store.resetIdentity()
                failures = 0
                true
            }
            is AnalyticsResult.Retry -> {
                retryLater(result.afterSeconds)
                false
            }
            else -> {
                scheduleRetry(UNAVAILABLE_DELAY_MS)
                false
            }
        }
    }

    private fun newPending(store: AnalyticsStore, signature: String): AnalyticsPending {
        val revision = store.lastRevision() + 1
        return AnalyticsPending(revision, signature, AnalyticsPayload.body(signature, revision))
    }

    private fun retryLater(afterSeconds: Long?) {
        failures++
        val delayMs = afterSeconds?.let { (it * 1000L).coerceAtMost(MAX_BACKOFF_MS) } ?: backoffMs()
        scheduleRetry(delayMs)
    }

    /** Экспоненциальная задержка с джиттером ±20%, чтобы не бить по серверу синхронно. */
    private fun backoffMs(): Long {
        val exponent = (failures - 1).coerceIn(0, 7)
        val capped = (MIN_BACKOFF_MS shl exponent).coerceAtMost(MAX_BACKOFF_MS)
        return (capped * (0.8 + Random.nextDouble() * 0.4)).toLong()
    }

    /** Вызывается под [lock]; предыдущая отложенная попытка отменяется. */
    private fun scheduleRetry(delayMs: Long) {
        val scope = scope ?: return
        retryJob?.cancel()
        retryJob = scope.launch { delay(delayMs); sync() }
    }
}
