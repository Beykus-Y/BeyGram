package com.beykus.beygram.data.export

import com.beykus.beygram.domain.model.MediaKind
import java.io.InputStream

/** Chat metadata that is safe to write into the export header. */
internal data class ChatExportChat(
    val id: Long,
    val title: String,
    val kind: String
)

internal data class ChatExportMedia(
    val kind: MediaKind,
    val fileId: Int,
    val fileName: String,
    val mimeType: String,
    val size: Long = 0L,
    val durationSeconds: Int = 0,
    val width: Int = 0,
    val height: Int = 0
)

internal data class ChatExportMessage(
    val id: Long,
    val dateSeconds: Long,
    val isOutgoing: Boolean,
    val senderId: Long,
    val senderName: String,
    val text: String,
    val isEdited: Boolean = false,
    val serviceText: String? = null,
    val replyToMessageId: Long = 0L,
    /** TDLib Message.canBeSaved. False content is never written. */
    val canBeSaved: Boolean = true,
    val media: ChatExportMedia? = null
)

internal data class ChatExportPage(
    /** Newest first. */
    val messages: List<ChatExportMessage>,
    /** Pass as [ChatExportPageSource.page]'s cursor. Zero means the beginning was reached. */
    val nextBeforeMessageId: Long,
    val hasMore: Boolean
)

internal interface ChatExportPageSource {
    suspend fun page(beforeMessageId: Long, limit: Int): ChatExportPage

    /**
     * The newest message sent at or before [dateSeconds], or null when that is unknown. A date-bounded walk begins
     * with it, so history newer than the bound is never requested. The message is walked like any other page entry.
     */
    suspend fun newestMessageAtOrBefore(dateSeconds: Long): ChatExportMessage? = null
}

/** Opens media bytes for copying. Null means the file is unavailable and only its metadata will be exported. */
internal interface ChatExportMediaProvider {
    suspend fun open(media: ChatExportMedia): InputStream?
}
