package com.beykus.beygram.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beykus.beygram.domain.model.ChatExportProgress
import com.beykus.beygram.domain.model.ChatExportRequest
import com.beykus.beygram.domain.model.ChatExportSummary
import com.beykus.beygram.domain.model.ChatExportTarget
import com.beykus.beygram.domain.repository.ConversationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface ChatExportUiState {
    data object Idle : ChatExportUiState
    data class Running(val progress: ChatExportProgress, val cancelling: Boolean = false) : ChatExportUiState
    data class Completed(val summary: ChatExportSummary) : ChatExportUiState
    data class Failed(val error: Throwable) : ChatExportUiState
    data object Cancelled : ChatExportUiState
}

/**
 * Runs the export in the app process while the chat UI is alive. This is the documented alternative to a
 * foreground service: the job survives rotation through the ViewModel, but not process death; the exporter
 * deletes or truncates the SAF document on every cancellation/failure, including ViewModel cancellation.
 */
internal class ChatExportViewModel : ViewModel() {
    private val _state = MutableStateFlow<ChatExportUiState>(ChatExportUiState.Idle)
    val state: StateFlow<ChatExportUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var activeTarget: ChatExportTarget? = null

    fun start(
        conversation: ConversationRepository,
        chatId: Long,
        target: ChatExportTarget,
        request: ChatExportRequest
    ) {
        if (job?.isActive == true) return
        activeTarget = target
        _state.value = ChatExportUiState.Running(ChatExportProgress())
        job = viewModelScope.launch {
            try {
                conversation.exportChat(chatId, target, request) { progress ->
                    _state.update { current ->
                        if (current is ChatExportUiState.Running && !current.cancelling) {
                            current.copy(progress = progress)
                        } else {
                            current
                        }
                    }
                }.onSuccess { summary ->
                    _state.value = ChatExportUiState.Completed(summary)
                }.onFailure { error ->
                    _state.value = ChatExportUiState.Failed(error)
                }
            } catch (cancelled: CancellationException) {
                _state.value = ChatExportUiState.Cancelled
                throw cancelled
            } catch (error: Exception) {
                _state.value = ChatExportUiState.Failed(error)
            } finally {
                if (activeTarget === target) activeTarget = null
            }
        }
    }

    fun cancel() {
        val running = job?.takeIf { it.isActive } ?: return
        val pendingTarget = activeTarget
        _state.update { current ->
            if (current is ChatExportUiState.Running) current.copy(cancelling = true) else current
        }
        running.cancel()
        if (pendingTarget != null) {
            // Covers cancellation before the exporter's first suspension: that path never opens the stream,
            // so it cannot rely on the streamer's cleanup. Discard is idempotent for the SAF target.
            viewModelScope.launch {
                running.join()
                if (_state.value !is ChatExportUiState.Completed) runCatching { pendingTarget.discard() }
                if (activeTarget === pendingTarget) activeTarget = null
            }
        }
    }

    fun reset() {
        _state.value = ChatExportUiState.Idle
    }
}
