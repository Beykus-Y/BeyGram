package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ProfilePhotoMedia
import org.drinkless.tdlib.TdApi

/** TDLib chatPhoto always contains JPEG sizes; its animation field is optional. */
internal fun TdApi.ChatPhoto.toProfilePhotoMedia(register: (TdApi.File) -> Unit): ProfilePhotoMedia? {
    val sizes = sizes.orEmpty().filter { it.photo != null && it.photo.id > 0 }
        .sortedBy { it.width.toLong() * it.height }
    val small = sizes.firstOrNull()?.photo
    val big = sizes.lastOrNull()?.photo
    val movie = animation?.file
    if (small == null && big == null && movie == null) return null
    listOfNotNull(small, big, movie).distinctBy { it.id }.forEach(register)
    val fallback = small ?: big ?: movie!!
    return ProfilePhotoMedia(
        photoId = id.takeIf { it > 0L } ?: -fallback.id.toLong(),
        smallFileId = (small ?: fallback).id,
        bigFileId = (big ?: fallback).id,
        animationFileId = movie?.id
    )
}
