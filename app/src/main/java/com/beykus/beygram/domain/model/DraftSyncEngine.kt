package com.beykus.beygram.domain.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** Persistence for one family of drafts (chats or topics). */
interface DraftStore<K> {
    fun read(key: K): StoredMessageDraft?
    fun write(key: K, value: StoredMessageDraft)
    /** Keys whose stored draft is not known to be held by TDLib. */
    fun unsyncedKeys(): List<K>
    fun clearAll()
}

/** A cloud draft. [value] null means the cloud holds a draft type this app cannot show; it is never overwritten or adopted. */
data class RemoteDraft(val value: MessageDraft?, val updatedAtMillis: Long)

/**
 * Local-first drafts reconciled with the cloud copy. Every mutable field is guarded by [guard] and no lock is held
 * across a suspension. [epoch] changes on sign-out, so results of requests started earlier are dropped.
 */
class DraftSyncEngine<K>(
    private val store: DraftStore<K>,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val push: suspend (K, MessageDraft) -> Boolean,
    private val fetch: suspend (K) -> RemoteDraft?,
    private val writeDelayMillis: Long = 400L
) {
    private val guard = Any()
    private val flows = HashMap<K, MutableStateFlow<MessageDraft>>()
    private val revisions = HashMap<K, Long>()
    private val dirty = HashSet<K>()
    private val editedWhileOpen = HashSet<K>()
    private val openCounts = HashMap<K, Int>()
    // A push in flight is not cancelled by a newer edit; its confirmation is simply ignored when the draft changed.
    private val pushing = HashMap<K, Job>()
    private val jobs = ConcurrentHashMap<K, Job>()
    private val locks = ConcurrentHashMap<K, Mutex>()
    private val reconciling = ConcurrentHashMap.newKeySet<K>()
    private var epoch = 0L

    private fun flowOf(key: K): MutableStateFlow<MessageDraft> =
        flows.getOrPut(key) { MutableStateFlow(store.read(key)?.value ?: MessageDraft()) }

    private fun revisionOf(key: K): Long = revisions[key] ?: 0L

    fun observe(key: K): StateFlow<MessageDraft> = synchronized(guard) { flowOf(key).asStateFlow() }

    fun update(key: K, transform: (MessageDraft) -> MessageDraft) {
        synchronized(guard) {
            val flow = flowOf(key)
            val changed = transform(flow.value)
            val next = changed.copy(entities = validTextEntities(changed.text, changed.entities))
            if (next == flow.value) return@synchronized
            flow.value = next
            store.write(key, StoredMessageDraft(next, now(), synced = false))
            dirty.add(key)
            revisions[key] = revisionOf(key) + 1
            if (key in openCounts) editedWhileOpen.add(key)
            schedule(key, writeDelayMillis)
        }
    }

    /** After a successful send: clears the draft only if the user has not typed since [sent] was taken. */
    fun clearIfUnchanged(key: K, sent: MessageDraft) = update(key) { if (it == sent) MessageDraft() else it }

    fun opened(key: K) {
        synchronized(guard) { openCounts[key] = (openCounts[key] ?: 0) + 1 }
    }

    fun closed(key: K) {
        synchronized(guard) {
            val remaining = (openCounts[key] ?: 0) - 1
            if (remaining > 0) {
                openCounts[key] = remaining
                return@synchronized
            }
            openCounts.remove(key)
            editedWhileOpen.remove(key)
            if (key in dirty) {
                schedule(key, 0L)
            } else {
                // Cloud changes ignored while the editor was open are read once, now.
                val launchEpoch = epoch
                val launchRevision = revisionOf(key)
                scope.launch {
                    val remote = fetchSafely(key) ?: return@launch
                    synchronized(guard) {
                        // A read that started before a newer local edit must not replace it.
                        if (epoch == launchEpoch && revisionOf(key) == launchRevision) receiveLocked(key, remote)
                    }
                }
            }
        }
    }

    fun receive(key: K, remote: RemoteDraft) {
        synchronized(guard) { receiveLocked(key, remote) }
    }

    /** Caller holds [guard]. */
    private fun receiveLocked(key: K, remote: RemoteDraft) {
        val value = remote.value ?: return
        val flow = flowOf(key)
        val local = store.read(key)
        if (flow.value == value) {
            dirty.remove(key)
            if (local != null && !local.synced) store.write(key, local.copy(synced = true))
            return
        }
        if (key in dirty || key in editedWhileOpen) return
        if (MessageDraftSync.keepsUnsentLocal(local, value, remote.updatedAtMillis)) {
            dirty.add(key)
            schedule(key, writeDelayMillis)
            return
        }
        flow.value = value
        store.write(key, StoredMessageDraft(value, remote.updatedAtMillis, synced = true))
    }

    /** Pushes pending edits now and reads back drafts that were unsynced when the process last stopped. */
    fun connectionReady() {
        synchronized(guard) {
            dirty.toList().forEach { schedule(it, 0L) }
            val launchEpoch = epoch
            store.unsyncedKeys().forEach { key ->
                if (key in dirty || !reconciling.add(key)) return@forEach
                val launchRevision = revisionOf(key)
                scope.launch {
                    try {
                        val remote = fetchSafely(key)
                        if (remote != null) {
                            synchronized(guard) {
                                if (epoch == launchEpoch && revisionOf(key) == launchRevision) receiveLocked(key, remote)
                            }
                        }
                    } finally {
                        synchronized(guard) {
                            if (epoch == launchEpoch) reconciling.remove(key)
                        }
                    }
                }
            }
        }
    }

    /** Drops every local draft and cancels pending work. Results of requests started before this call are ignored. */
    fun signedOut() {
        synchronized(guard) {
            epoch++
            jobs.values.forEach { it.cancel() }
            jobs.clear()
            pushing.values.forEach { it.cancel() }
            pushing.clear()
            revisions.keys.toList().forEach { revisions[it] = revisionOf(it) + 1 }
            dirty.clear()
            editedWhileOpen.clear()
            openCounts.clear()
            reconciling.clear()
            flows.values.forEach { it.value = MessageDraft() }
            store.clearAll()
        }
    }

    /** Caller holds [guard]. Replaces the waiting job for [key]; a push already in flight keeps running. */
    private fun schedule(key: K, waitMillis: Long) {
        val generation = revisionOf(key)
        val launchEpoch = epoch
        jobs.remove(key)?.cancel()
        val job = scope.launch {
            val self = checkNotNull(coroutineContext[Job])
            if (waitMillis > 0L) delay(waitMillis)
            locks.computeIfAbsent(key) { Mutex() }.withLock {
                val snapshot = synchronized(guard) {
                    val stale = !self.isActive || launchEpoch != epoch || generation != revisionOf(key) || key !in dirty
                    if (stale) {
                        null
                    } else {
                        jobs.remove(key, self)
                        pushing[key] = self
                        flowOf(key).value
                    }
                } ?: return@withLock
                val ok = try {
                    pushSafely(key, snapshot)
                } finally {
                    synchronized(guard) {
                        if (pushing[key] === self) pushing.remove(key)
                    }
                }
                // Failures leave the draft dirty and unsynced for the next edit or reconnect.
                if (!ok) return@withLock
                synchronized(guard) {
                    if (launchEpoch != epoch || generation != revisionOf(key) || flowOf(key).value != snapshot) {
                        return@synchronized
                    }
                    dirty.remove(key)
                    store.read(key)?.let { saved -> if (!saved.synced) store.write(key, saved.copy(synced = true)) }
                }
            }
        }
        jobs[key] = job
    }

    private suspend fun pushSafely(key: K, draft: MessageDraft): Boolean = try {
        push(key, draft)
    } catch (error: TimeoutCancellationException) {
        false
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        false
    }

    private suspend fun fetchSafely(key: K): RemoteDraft? = try {
        fetch(key)
    } catch (error: TimeoutCancellationException) {
        null
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        null
    }
}
