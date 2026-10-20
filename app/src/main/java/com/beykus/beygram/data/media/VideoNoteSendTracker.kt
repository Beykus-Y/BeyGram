package com.beykus.beygram.data.media

import com.beykus.beygram.R

/** Where an outgoing video note is on its way to the server, as TDLib reports it. */
internal sealed interface VideoNoteSendEvent {
    /** The message exists locally and is queued for upload. Not delivery. */
    data object Pending : VideoNoteSendEvent
    /** TDLib confirmed that the server stored the message. */
    data object Succeeded : VideoNoteSendEvent
    data class Failed(val code: Int) : VideoNoteSendEvent
}

/**
 * Follows video notes after `sendMessage` accepted them (issue #234, §13). The send loop calls
 * [expect] before handing a segment to TDLib; the next outgoing video-note message in that chat
 * binds to it, and the later TDLib updates are reported against the diagnostic session.
 * Accepted by the queue is not the same as stored on the server: only [VideoNoteSendEvent.Succeeded] is.
 */
internal class VideoNoteSendTracker(private val sink: (sessionName: String, event: VideoNoteSendEvent) -> Unit) {
    private val expecting = HashMap<Long, ArrayDeque<String>>()
    private val bound = HashMap<Pair<Long, Long>, String>()

    @Synchronized fun expect(chatId: Long, sessionName: String) {
        expecting.getOrPut(chatId) { ArrayDeque() }.addLast(sessionName)
    }

    /** Drops what is still waiting for [sessionName], for a segment TDLib rejected before creating a message. */
    @Synchronized fun forget(sessionName: String) {
        expecting.values.forEach { queue -> queue.removeAll { it == sessionName } }
    }

    fun onPending(chatId: Long, messageId: Long) {
        val session = synchronized(this) {
            val next = expecting[chatId]?.removeFirstOrNull() ?: return
            bound[chatId to messageId] = next
            next
        }
        sink(session, VideoNoteSendEvent.Pending)
    }

    fun onSucceeded(chatId: Long, oldMessageId: Long) {
        val session = synchronized(this) { bound.remove(chatId to oldMessageId) } ?: return
        sink(session, VideoNoteSendEvent.Succeeded)
    }

    fun onFailed(chatId: Long, oldMessageId: Long, code: Int) {
        val session = synchronized(this) { bound.remove(chatId to oldMessageId) } ?: return
        sink(session, VideoNoteSendEvent.Failed(code))
    }
}

/** App-wide tracker that writes into the recording reports. */
internal object VideoNoteSendTracking {
    val tracker = VideoNoteSendTracker { session, event ->
        when (event) {
            VideoNoteSendEvent.Pending -> VideoNoteDiagnostics.append(session, VideoNoteDiagnostics.text(R.string.vn_entry_td_pending))
            VideoNoteSendEvent.Succeeded -> VideoNoteDiagnostics.append(session, VideoNoteDiagnostics.text(R.string.vn_entry_td_stored), VideoNoteStatus.SENT)
            is VideoNoteSendEvent.Failed -> VideoNoteDiagnostics.append(session, VideoNoteDiagnostics.text(R.string.vn_entry_td_failed, event.code), VideoNoteStatus.SEND_FAILED)
        }
    }
}
