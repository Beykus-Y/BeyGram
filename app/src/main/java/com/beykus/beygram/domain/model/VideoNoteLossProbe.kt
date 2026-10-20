package com.beykus.beygram.domain.model

/**
 * One deliberately created test video note in Saved Messages. These identifiers are transient:
 * the diagnostic report never shares chat IDs, message IDs or remote file tokens.
 */
data class VideoNoteLossRemoteResult(
    val messageId: Long,
    val remoteCopyPath: String?,
    val declaredSide: Int,
    val remoteFileBytes: Long,
    val sourceEvidence: String,
    val independentDownloadVerified: Boolean
)
