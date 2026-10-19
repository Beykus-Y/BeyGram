package com.beykus.beygram.domain.model

/** An individual profile photo. The ID belongs to TDLib's chatPhoto, not to a message. */
data class ProfilePhotoMedia(
    val photoId: Long,
    val smallFileId: Int,
    val bigFileId: Int,
    /** MP4 animation supplied by Telegram; null is an ordinary still photo. */
    val animationFileId: Int? = null
) {
    val fileId: Int get() = animationFileId ?: bigFileId
}

/**
 * Offset counts *raw* records returned by TDLib, even when some records have no usable file;
 * this avoids skipping items when the server returns incomplete media metadata.
 */
data class ProfilePhotosPage(
    val photos: List<ProfilePhotoMedia>,
    val totalCount: Int,
    val nextOffset: Int,
    val hasMore: Boolean
)

/** Immutable, testable reconciliation of current avatar and server history. */
data class ProfilePhotosWindow(
    val photos: List<ProfilePhotoMedia> = emptyList(),
    val totalCount: Int = 0,
    val offset: Int = 0,
    val hasMore: Boolean = true
) {
    fun append(page: ProfilePhotosPage): ProfilePhotosWindow {
        if (page.nextOffset < offset) return this
        // Server metadata supersedes the initially displayed avatar: only ChatPhoto has the MP4 variant.
        // Matching file IDs also handles the fallback identity of groups without a known photo ID.
        val updated = photos.map { initial ->
            page.photos.firstOrNull { it.photoId == initial.photoId || (it.bigFileId > 0 && it.bigFileId == initial.bigFileId) } ?: initial
        }
        val merged = (updated + page.photos).distinctBy(ProfilePhotoMedia::photoId)
        return copy(
            photos = merged,
            totalCount = maxOf(page.totalCount, merged.size),
            offset = maxOf(offset, page.nextOffset),
            hasMore = page.hasMore && page.nextOffset > offset
        )
    }

    fun remove(photoId: Long): ProfilePhotosWindow = copy(
        photos = photos.filterNot { it.photoId == photoId },
        totalCount = (totalCount - 1).coerceAtLeast(0)
    )
}
