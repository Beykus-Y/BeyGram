package com.beykus.beygram.notifications

import android.content.Context
import com.beykus.beygram.data.tdlib.TdLibBootstrap
import com.beykus.beygram.data.tdlib.TdLibTelegramRepository
import com.beykus.beygram.data.vpn.VpnBackend
import com.beykus.beygram.diagnostics.recordAppEvent
import com.beykus.beygram.diagnostics.report.EventKind
import com.beykus.beygram.diagnostics.report.Severity
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.domain.repository.TelegramRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

/** Process-level presenter; the service and UI share the same TDLib instance and subscriptions. */
class NotificationRuntime private constructor(private val context: Context) {
    val preferences = NotificationPreferencesStore(context)
    private val renderer = AndroidNotificationRenderer(context, preferences)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var repository: TelegramRepository? = null
    private val flushing = ConcurrentHashMap.newKeySet<Int>()
    private val invalidations = MutableStateFlow(0L)
    private val _service = MutableStateFlow(false)
    val serviceRunning = _service.asStateFlow()
    private val _serviceError = MutableStateFlow<UiText?>(null)
    val serviceError = _serviceError.asStateFlow()
    private val _authorized = MutableStateFlow(false)
    val authorized = _authorized.asStateFlow()
    fun serviceState(running: Boolean, error: UiText? = null) { _service.value = running; if (running || error != null) _serviceError.value = error }

    @Synchronized fun attach(source: TelegramRepository) {
        if (repository != null) return
        repository = source
        scope.launch {
            combine(source.notifications, source.notificationPresentation, source.authState, preferences.state, invalidations) { notices, presentation, auth, config, _ ->
                runCatching { renderer.render(notices, config, presentation, auth == AuthState.Ready) }
                    .onFailure { android.util.Log.w("BeyGramNotify", "Notification rendering failed: ${it.javaClass.simpleName}") }
            }.collect()
        }
        scope.launch { source.authState.collect { auth ->
            when (auth) {
                AuthState.Ready -> { preferences.authorized = true; flushDismissals() }
                AuthState.WaitingForPhone -> preferences.authorized = false
                else -> Unit
            }
            _authorized.value = auth == AuthState.Ready
        } }
        scope.launch { source.connectionState.collect { flushDismissals() } }
    }
    fun refresh() {
        repository?.authState?.value?.let { auth ->
            if (auth == AuthState.Ready) preferences.authorized = true
            else if (auth == AuthState.WaitingForPhone) preferences.authorized = false
            _authorized.value = auth == AuthState.Ready
        }
        invalidations.update { it + 1 }
    }
    fun dismiss(groupId: Int, maxId: Int) {
        if (groupId <= 0 || maxId <= 0) return
        preferences.dismiss(groupId, maxId)
        renderer.cancelGroup(groupId)
        refresh(); flushDismissals()
    }
    /**
     * Sends a reply typed in a notification. An accepted send dismisses the group; a rejected one keeps the notification
     * and marks it. [finished] ends the broadcast in time, while a slower send keeps running.
     */
    fun reply(chatId: Long, groupId: Int, maxId: Int, replyToMessageId: Long, text: String, finished: () -> Unit) {
        // A blank reply sends nothing; re-posting the notification still clears the inline field.
        val send = scope.launch {
            // The submitted action is spent, so the notification is re-posted; re-posting needs an attached repository.
            if (repository == null) runCatching { attachRealClient() }
            if (text.isBlank()) refresh() else deliverReply(chatId, groupId, maxId, replyToMessageId, text)
        }
        scope.launch {
            withTimeoutOrNull(REPLY_BROADCAST_MILLIS) { send.join() }
            finished()
        }
    }
    private suspend fun deliverReply(chatId: Long, groupId: Int, maxId: Int, replyToMessageId: Long, text: String) {
        val failure = sendReply(chatId, replyToMessageId, text).exceptionOrNull()
        if (failure == null) {
            recordAppEvent(EventKind.NOTIFICATION, "reply_sent", "Ответ из уведомления отправлен")
            dismiss(groupId, maxId)
        } else {
            // The note belongs to the newest notice at this moment, so a notice that arrives later clears it.
            val newest = repository?.notifications?.value?.groups?.firstOrNull { it.id == groupId }?.notices?.maxOfOrNull { it.id } ?: maxId
            renderer.markReplyFailed(groupId, newest)
            recordAppEvent(EventKind.NOTIFICATION, "reply_failed", "Ответ из уведомления не отправлен", Severity.WARNING, mapOf("cause" to failure.javaClass.simpleName))
            refresh()
        }
    }
    private suspend fun sendReply(chatId: Long, replyToMessageId: Long, text: String): Result<Unit> = try {
        val source = repository ?: attachRealClient() ?: throw IllegalStateException("Telegram client is unavailable")
        // A reply that starts the process can arrive before TDLib has authorized, and TDLib rejects requests until then.
        // Without authorization in time the reply is not sent, and the notification is marked as failed.
        withTimeoutOrNull(REPLY_READY_MILLIS) { source.authState.first { it == AuthState.Ready } }
            ?: throw IllegalStateException("Telegram client is not authorized")
        // Replying to the notified message puts the answer in its forum topic, too.
        source.sendMessage(chatId, text, replyToMessageId.takeIf { it > 0L })
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
    private fun flushDismissals() {
        val source = repository ?: return
        if (source.authState.value != AuthState.Ready) return
        preferences.pendingDismissals().forEach { (group, max) -> if (flushing.add(group)) scope.launch {
            var accepted = false
            var tries = 0
            try {
                // Bounded retry with backoff: a transient TDLib failure must not wait for an unrelated event.
                repeat(DISMISS_ATTEMPTS) { attempt ->
                    if (accepted) return@repeat
                    if (attempt > 0) delay(DISMISS_RETRY_MILLIS shl (attempt - 1))
                    tries++
                    accepted = source.dismissNotifications(group, max).isSuccess
                }
                if (accepted) preferences.acknowledge(group, max)
            } finally {
                flushing.remove(group)
                // A newer dismissal that arrived during the retries goes out next, even if this round failed.
                if ((preferences.pendingDismissals()[group] ?: 0) > max) flushDismissals()
            }
            recordDismiss(accepted, tries)
        } }
    }
    private fun recordDismiss(accepted: Boolean, tries: Int) = when {
        !accepted -> recordAppEvent(EventKind.NOTIFICATION, "dismiss_failed", "Смахивание не отправлено, попыток: $tries", Severity.WARNING, mapOf("tries" to tries.toString()))
        tries > 1 -> recordAppEvent(EventKind.NOTIFICATION, "dismiss_retried", "Смахивание отправлено с попытки $tries", attrs = mapOf("tries" to tries.toString()))
        else -> Unit
    }
    fun attachRealClient(): TelegramRepository? {
        if (!TdLibBootstrap(context).readiness().canCreateClient) return null
        val repository = TdLibTelegramRepository.getInstance(context)
        // TDLib keeps the saved proxy, and only the backend restarts its local listener. A cold reply skips the application's
        // start-up path when background notifications are off, so the backend is created here before the client is used.
        VpnBackend.getInstance(context, repository.connectionState, scope, repository::setMihomoProxyEnabled, repository::isMihomoProxyEnabled)
        return repository.also(::attach)
    }
    companion object {
        private const val DISMISS_ATTEMPTS = 4
        private const val DISMISS_RETRY_MILLIS = 2_000L
        // Keeps a reply broadcast inside the system's receiver time limit; a slower send still completes.
        private const val REPLY_BROADCAST_MILLIS = 8_000L
        // Longest wait for TDLib's authorization after the broadcast has ended; the send itself is not bound to the broadcast.
        private const val REPLY_READY_MILLIS = 30_000L
        @Volatile private var instance: NotificationRuntime? = null
        fun get(context: Context): NotificationRuntime = instance ?: synchronized(this) {
            instance ?: NotificationRuntime(context.applicationContext).also { instance = it }
        }
    }
}
