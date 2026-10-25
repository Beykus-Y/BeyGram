package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.MediaFileState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

internal class MediaFileStore {
    private val states = ConcurrentHashMap<Int, MutableStateFlow<MediaFileState>>()
    private val known = mutableSetOf<Int>()
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
        flow(file.id).value = file
    }

    fun beginDownload(id: Int): Boolean = synchronized(lock) {
        val state = flow(id)
        if (state.value.localPath != null || state.value.isDownloading) false else {
            state.value = state.value.copy(isDownloading = true, error = null)
            true
        }
    }

    fun fail(id: Int, message: UiText) = synchronized(lock) {
        val state = flow(id)
        state.value = state.value.copy(isDownloading = false, error = message)
    }

    /** Stops showing an automatic download as active after TDLib cancelled it; a manual retry is still possible. */
    fun cancel(id: Int) = synchronized(lock) {
        val state = flow(id)
        state.value = state.value.copy(isDownloading = false, error = null)
    }
}
