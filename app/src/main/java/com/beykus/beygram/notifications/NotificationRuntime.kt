package com.beykus.beygram.notifications

import android.content.Context
import com.beykus.beygram.data.tdlib.TdLibBootstrap
import com.beykus.beygram.data.tdlib.TdLibTelegramRepository
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
        scope.launch { source.myProfile.collect { profile -> profile.info?.id?.let(preferences::account) } }
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
    private fun flushDismissals() {
        val source = repository ?: return
        if (source.authState.value != AuthState.Ready) return
        preferences.pendingDismissals().forEach { (group, max) -> if (flushing.add(group)) scope.launch {
            var accepted = false
            try {
                // Bounded retry with backoff: a transient TDLib failure must not wait for an unrelated event.
                repeat(DISMISS_ATTEMPTS) { attempt ->
                    if (accepted) return@repeat
                    if (attempt > 0) delay(DISMISS_RETRY_MILLIS shl (attempt - 1))
                    accepted = source.dismissNotifications(group, max).isSuccess
                }
                if (accepted) preferences.acknowledge(group, max)
            } finally {
                flushing.remove(group)
                if (accepted && (preferences.pendingDismissals()[group] ?: 0) > max) flushDismissals()
            }
        } }
    }
    fun attachRealClient(): TelegramRepository? {
        if (!TdLibBootstrap(context).readiness().canCreateClient) return null
        return TdLibTelegramRepository.getInstance(context).also(::attach)
    }
    companion object {
        private const val DISMISS_ATTEMPTS = 4
        private const val DISMISS_RETRY_MILLIS = 2_000L
        @Volatile private var instance: NotificationRuntime? = null
        fun get(context: Context): NotificationRuntime = instance ?: synchronized(this) {
            instance ?: NotificationRuntime(context.applicationContext).also { instance = it }
        }
    }
}
