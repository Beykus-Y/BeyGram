package com.beykus.beygram.data.tdlib

import com.beykus.beygram.data.media.TrafficOrigin
import com.beykus.beygram.data.media.TrafficUsageSession
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.MediaFileState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * File states plus their traffic accounting. [traffic] is injected by the repository so all files of
 * one Telegram account share a single session; standalone instances (tests, mapping helpers) start
 * with their own empty session.
 */
internal class MediaFileStore(
    private val traffic: TrafficUsageSession = TrafficUsageSession()
) {
    private val states = ConcurrentHashMap<Int, MutableStateFlow<MediaFileState>>()
    private val known = mutableSetOf<Int>()
    /** Last explicit origin for a not-yet-finished transfer, so a TDLib pause/resume stays attributed correctly. */
    private val origins = mutableMapOf<Int, TrafficOrigin>()
    private val lock = Any()
    private fun flow(id: Int) = states.computeIfAbsent(id) { MutableStateFlow(MediaFileState(id)) }
    fun observe(id: Int): StateFlow<MediaFileState> = flow(id).asStateFlow()

    fun snapshot(id: Int): MediaFileState? = synchronized(lock) { states[id]?.value }

    // History snapshots must not roll back a newer UpdateFile download/upload state.
    fun register(file: MediaFileState) = synchronized(lock) {
        if (known.add(file.id)) flow(file.id).value = file
    }

    fun update(file: MediaFileState) = synchronized(lock) {
        known.add(file.id)
        val previous = flow(file.id).value
        flow(file.id).value = file
        if (file.isDownloading) {
            // TDLib also preloads thumbnails and media without an explicit DownloadFile request.
            // Such transfers have no app origin, so they are counted as automatic.
            if (!traffic.isTracking(file.id)) {
                traffic.begin(file.id, origins[file.id] ?: TrafficOrigin.Auto, previous.downloadedBytes)
            }
            traffic.progress(file.id, file.downloadedBytes)
        } else {
            // Count the last progress value before the transfer disappears from the active map.
            if (traffic.isTracking(file.id)) {
                traffic.progress(file.id, file.downloadedBytes)
                traffic.finish(file.id)
            }
            if (file.localPath != null) origins.remove(file.id)
        }
    }

    fun beginDownload(id: Int): Boolean = beginDownload(id, TrafficOrigin.Manual)

    /** Starts a transfer that the auto-download policy requested; the manual button never uses this. */
    fun beginAutoDownload(id: Int): Boolean = beginDownload(id, TrafficOrigin.Auto)

    private fun beginDownload(id: Int, origin: TrafficOrigin): Boolean = synchronized(lock) {
        val state = flow(id)
        when {
            state.value.localPath != null -> false
            state.value.isDownloading -> {
                // A manual tap while an automatic transfer is running takes it over for the user.
                if (origin == TrafficOrigin.Manual) {
                    origins[id] = TrafficOrigin.Manual
                    traffic.promote(id)
                }
                false
            }
            else -> {
                val baseline = state.value.downloadedBytes
                state.value = state.value.copy(isDownloading = true, error = null)
                origins[id] = origin
                traffic.begin(id, origin, baseline)
                true
            }
        }
    }

    fun fail(id: Int, message: UiText) = synchronized(lock) {
        val state = flow(id)
        state.value = state.value.copy(isDownloading = false, error = message)
        origins.remove(id)
        traffic.finish(id)
    }

    /** Stops showing an automatic download as active after TDLib cancelled it; a manual retry is still possible. */
    fun cancel(id: Int) = synchronized(lock) {
        val state = flow(id)
        state.value = state.value.copy(isDownloading = false, error = null)
        // Keep the origin: a late progress update after cancellation must not relabel a manual transfer.
        traffic.finish(id)
    }
}
